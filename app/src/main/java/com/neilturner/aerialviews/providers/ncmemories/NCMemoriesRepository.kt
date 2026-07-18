package com.neilturner.aerialviews.providers.ncmemories

import com.neilturner.aerialviews.data.network.JsonHelper.buildSerializer
import com.neilturner.aerialviews.data.network.ServerConfig
import com.neilturner.aerialviews.data.network.SslHelper
import com.neilturner.aerialviews.data.network.UrlParser
import com.neilturner.aerialviews.models.enums.ProviderMediaType
import com.neilturner.aerialviews.models.prefs.NCMemoriesMediaPrefs
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import okhttp3.Credentials
import retrofit2.Retrofit
import timber.log.Timber
import kotlin.collections.joinToString
import kotlin.collections.map

class NCMemoriesRepository(
    private val prefs: NCMemoriesMediaPrefs,
) {
    lateinit var server: String
    lateinit var credential: String

    private val client by lazy {

        server = UrlParser.parseServerUrl(prefs.url)
        credential = Credentials.basic(prefs.username, prefs.password)

        val serverConfig = ServerConfig(server, prefs.validateSsl)
        val okHttpClient = SslHelper().createOkHttpClient(serverConfig)
        Timber.i("Connecting to $server")

        Retrofit
            .Builder()
            .baseUrl(server)
            .client(okHttpClient)
            .addConverterFactory(buildSerializer())
            .build()
            .create(NCMemoriesApi::class.java)
    }

    suspend fun getSelectedAlbums(): List<Image> =
        coroutineScope {
            try {
                val selectedAlbumIds = prefs.selectedAlbumIds
                if (selectedAlbumIds.isEmpty()) {
                    return@coroutineScope emptyList()
                }

                Timber.d("Attempting to fetch ${selectedAlbumIds.size} selected albums")

                // Fetch cluster names for further days querying
                val selectedAlbumsNames = fetchClusterNames(selectedAlbumIds)

                // Prepare days query for every selected album
                val albumDaysResponsesDeferred =
                    selectedAlbumsNames.map { album ->
                        async {
                            Pair(
                                album,
                                client.getDays(
                                    credential = credential,
                                    clusterId = album.clusterId,
                                    vid = getTypeFilter(),
                                )
                            )
                        }
                    }
                val albumDaysResponses = albumDaysResponsesDeferred.awaitAll()

                // Filter successful album responses
                val albumDaysSuccess = albumDaysResponses.filter { it.second.isSuccessful }
                if (albumDaysSuccess.size != albumDaysResponses.size) {
                    Timber.e("Failed to fetch days for all selected albums")
                    // Continue with successful albums
                }

                // Prepare images subquery for every selected album
                val albumImagesResponsesDeferred =
                    albumDaysSuccess.map { albumDaysResponse ->
                        async {
                            Pair(
                                albumDaysResponse.first.name,
                                client.getImages(
                                    credential = credential,
                                    clusterId = albumDaysResponse.first.clusterId,
                                    dayIds = albumDaysResponse.second.body()?.map { it.dayId }
                                        ?.joinToString(",") ?: "",
                                    vid = getTypeFilter(),
                                )
                            )
                        }
                    }
                val albumImagesResponses = albumImagesResponsesDeferred.awaitAll()

                // Collect all unique images to combined list
                val allImages = mutableListOf<Image>()

                val albumsWithImages = mutableListOf<String>()
                val albumNamesByImageId = mutableMapOf<Int, MutableSet<String>>()

                for (albumImagesResponse in albumImagesResponses) {
                    val albumName = albumImagesResponse.first
                    val response = albumImagesResponse.second
                    Timber.d("Completed API request for album $albumName - URL: ${response.raw().request.url}")

                    if (response.isSuccessful) {
                        val albumImages = response.body()
                        if (albumImages != null) {
                            Timber.d("Successfully fetched album: ${albumName}, images: ${albumImages.size}")
                            // Count completed albums
                            albumsWithImages.add(albumName)

                            // Add album images to combined list
                            allImages.addAll(albumImages.map { it.copy(albumName = albumName) })

                            // Save album name for image ID lookup
                            albumImages.forEach { image ->
                                albumNamesByImageId.getOrPut(image.fileId) { mutableSetOf() }
                                    .add(albumName)
                            }
                        } else {
                            Timber.e("Received empty image list from successful days response for album: $albumName")
                        }
                    } else {
                        val errorBody = response.errorBody()?.string()
                        Timber.e("Failed to fetch images in $albumName. Code: ${response.code()}, Error: $errorBody")
                        // Continue with other albums instead of failing completely
                    }
                }

                if (allImages.isEmpty()) {
                    throw Exception("No images found in any of the selected albums")
                }

                // Remove duplicate images based on image ID
                val fullAlbumsCount = albumsWithImages.size
                val isSingleAlbumSelection = (fullAlbumsCount == 1) // Shortcut for single album for all images

                val uniqueImages =
                    allImages
                        .distinctBy { it.fileId }
                        .map { image ->
                            // Album name lookup for remaining unique images
                            val albumNamesLookup = albumNamesByImageId[image.fileId].orEmpty()
                            val resolvedAlbumName =
                                when {
                                    isSingleAlbumSelection -> albumNamesLookup.singleOrNull()
                                    albumNamesLookup.size == 1 -> albumNamesLookup.first()
                                    else -> null
                                }
                            image.copy(albumName = resolvedAlbumName)
                        }
                Timber.d(
                    "Combined ${allImages.size} images from $fullAlbumsCount found albums " +
                            "(${selectedAlbumIds.size} selected): ${uniqueImages.size} unique images remain",
                )

                // fetch EXIF image info
                // skip if just testing connection
                val uniqueImagesFull = when(prefs.isTestConnection) {
                    false -> {
                        fetchExifInfo(uniqueImages)
                    }
                    true -> {
                        uniqueImages
                    }
                }

                // Return a combined album with unique images
                return@coroutineScope uniqueImagesFull
            } catch (e: Exception) {
                Timber.e(e, "Exception while fetching selected albums")
                throw Exception("Failed to fetch selected albums", e)
            }
        }

    private fun getTypeFilter(): Int? =
        when (prefs.mediaType) {
            ProviderMediaType.VIDEOS -> 1
            ProviderMediaType.PHOTOS -> null
            ProviderMediaType.VIDEOS_PHOTOS -> null
            else -> null
        }

    suspend fun getOptionalImages(imageSourceName: String, count: Int?): List<Image> =
        coroutineScope {
            try {
                count ?: return@coroutineScope emptyList()
                Timber.d("Fetching $count $imageSourceName images")

                // define header value for favorites
                val fav = when(imageSourceName) {
                    prefs.favoritesName -> 1
                    else -> null
                }

                // fetch days for image source
                val daysResponseDeferred = async {
                    client.getDays(
                        credential = credential,
                        fav = fav,
                        vid = getTypeFilter(),
                    )
                }

                val daysResponse = daysResponseDeferred.await()
                if (daysResponse.isSuccessful) {
                    val allDays = daysResponse.body() ?: emptyList()

                    if (allDays.isEmpty()) {
                        throw Exception("No days found in $imageSourceName")
                    }

                    // fetch images from days
                    Timber.d("Attempting to fetch ${allDays.size} $imageSourceName days")

                    val allImagesResponseDeferred = async {
                        client.getImages(
                            credential = credential,
                            dayIds = allDays
                                .map { it.dayId }
                                .joinToString(","),
                            fav = fav,
                            vid = getTypeFilter(),
                        )
                    }

                    val allImagesResponse = allImagesResponseDeferred.await()
                    if (allImagesResponse.isSuccessful) {
                        val allImages = allImagesResponse.body() ?: emptyList()

                        if (allImages.isEmpty()) {
                            throw Exception("No images found in $imageSourceName")
                        }

                        val limitedImages = allImages
                            .take(count)
                            .map { it.copy(albumName = imageSourceName) }

                        // fetch EXIF image info
                        // skip if just testing connection
                        val limitedImagesFull = when(prefs.isTestConnection) {
                            false -> {
                                fetchExifInfo(limitedImages)
                            }
                            true -> {
                                limitedImages
                            }
                        }

                        Timber.d("Successfully fetched ${limitedImagesFull.size} $imageSourceName images (from ${allImages.size} total)")
                        return@coroutineScope limitedImagesFull
                    } else {
                        val errorBody = allImagesResponse.errorBody()?.string()
                        Timber.e("Failed to fetch $imageSourceName images from days. Code: ${allImagesResponse.code()}, Error: $errorBody")
                        throw Exception("Failed to fetch $imageSourceName images from days: ${allImagesResponse.code()} - ${allImagesResponse.message()}")
                    }
                } else {
                    val errorBody = daysResponse.errorBody()?.string()
                    Timber.e("Failed to fetch $imageSourceName days. Code: ${daysResponse.code()}, Error: $errorBody")
                    throw Exception("Failed to fetch $imageSourceName days: ${daysResponse.code()} - ${daysResponse.message()}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Exception while fetching $imageSourceName images")
                throw Exception("Failed to fetch $imageSourceName images", e)
            }
        }

    private suspend fun fetchExifInfo(images: List<Image>): List<Image> =
        coroutineScope {
            val imagesWithExif = mutableListOf<Image>()

            val imageInfoDeferreds =
                images.map { image ->
                    async {
                        Pair(
                            image,
                            client.getFullImageInfo(
                                credential = credential,
                                fileId = image.fileId
                            )
                        )
                    }
                }

            val imageInfoResponses = imageInfoDeferreds.awaitAll()

            for (imageInfoResponsePair in imageInfoResponses) {
                val imageId = imageInfoResponsePair.first.fileId
                val albumName = imageInfoResponsePair.first.albumName
                val imageInfoResponse = imageInfoResponsePair.second
                Timber.d("API Request for image $imageId - URL: ${imageInfoResponse.raw().request.url}")

                if (imageInfoResponse.isSuccessful) {
                    val exifResponse = imageInfoResponse.body()
                    if (exifResponse != null) {
                        Timber.d("Successfully fetched image EXIF: ${exifResponse.baseName}")

                        // reapply album name after EXIF request
                        imagesWithExif.add(exifResponse.copy(albumName = albumName))
                    } else {
                        Timber.e("Received null image from successful response for image ID: $imageId")
                    }
                } else {
                    val errorBody = imageInfoResponse.errorBody()?.string()
                    Timber.e("Failed to fetch image info $imageId. Code: ${imageInfoResponse.code()}, Error: $errorBody")
                    // Continue with other images
                }
            }
            return@coroutineScope imagesWithExif
        }

    suspend fun fetchAlbumList(): Result<List<Album>> =
        coroutineScope {
            try {
                val albumListQueryDeferred = async { client.getAlbumList(credential) }

                val response = albumListQueryDeferred.await()
                val fetchedAlbums =
                    if (response.isSuccessful) {
                        response.body() ?: emptyList()
                    } else {
                        val errorBody = response.errorBody()?.string() ?: ""
                        val errorMessage =
                            try {
                                Json.decodeFromString<ErrorResponse>(errorBody).message
                            } catch (e: Exception) {
                                Timber.e(e, "Error parsing error body: $errorBody")
                                response.message()
                            }
                        return@coroutineScope Result.failure(Exception("${response.code()} - $errorMessage"))
                    }

                Timber.d(
                    "Fetched ${fetchedAlbums.size} albums",
                )

                Result.success(fetchedAlbums)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun fetchClusterNames(selectedAlbumIDs: MutableSet<String>): List<Album> =
        coroutineScope {
            try {
                val albumListQueryDeferred = async { client.getAlbumList(credential) }

                val albumListQuery = albumListQueryDeferred.await()
                if (albumListQuery.isSuccessful) {
                    val serverAlbumList = albumListQuery.body() ?: emptyList()
                    val selectedAlbumList = if (!serverAlbumList.isEmpty()) {
                            serverAlbumList.filter { it.albumId.toString() in selectedAlbumIDs }
                        } else {
                            emptyList()
                        }
                    return@coroutineScope selectedAlbumList
                } else {
                    val errorBody = albumListQuery.errorBody()?.string()
                    Timber.e("Failed to fetch album list. Code: ${albumListQuery.code()}, Error: $errorBody")
                    throw Exception("Failed to fetch album list: ${albumListQuery.code()} - ${albumListQuery.message()}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Exception while fetching cluster names for albums")
                throw Exception("Failed to fetch cluster names for albums", e)
            }
        }
}
