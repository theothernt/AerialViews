package com.neilturner.aerialviews.providers.immich

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.AerialMediaSource
import com.neilturner.aerialviews.models.enums.AerialMediaType
import com.neilturner.aerialviews.models.enums.ImmichAuthType
import com.neilturner.aerialviews.models.enums.ProviderMediaType
import com.neilturner.aerialviews.models.enums.ProviderSourceType
import com.neilturner.aerialviews.models.prefs.ImmichMediaPrefs
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.providers.MediaProvider
import com.neilturner.aerialviews.utils.FileHelper
import com.neilturner.aerialviews.utils.JsonHelper.buildSerializer
import com.neilturner.aerialviews.utils.ServerConfig
import com.neilturner.aerialviews.utils.SslHelper
import com.neilturner.aerialviews.utils.UrlParser
import kotlinx.serialization.json.Json
import retrofit2.Retrofit
import timber.log.Timber

class ImmichMediaProvider(
    context: Context,
    private val prefs: ImmichMediaPrefs,
) : MediaProvider(context) {
    override val type = ProviderSourceType.REMOTE
    override val enabled: Boolean
        get() = prefs.enabled
    private lateinit var server: String

    private val immichClient by lazy {
        server = UrlParser.parseServerUrl(prefs.url)
        val serverConfig = ServerConfig(server, prefs.validateSsl)
        val okHttpClient = SslHelper().createOkHttpClient(serverConfig)
        Timber.i("Connecting to $server")

        Retrofit
            .Builder()
            .baseUrl(server)
            .client(okHttpClient)
            .addConverterFactory(buildSerializer())
            .build()
            .create(ImmichApi::class.java)
    }

    override suspend fun fetchMedia(): List<AerialMedia> = fetchImmichMedia().first

    override suspend fun fetchTest(): String = fetchImmichMedia().second

    override suspend fun fetchMetadata(): MutableMap<String, Pair<String, Map<Int, String>>> = mutableMapOf()

    private suspend fun fetchImmichMedia(): Pair<List<AerialMedia>, String> {
        val media = mutableListOf<AerialMedia>()
        var excluded = 0
        var videos = 0
        var images = 0

        if (prefs.url.isEmpty()) {
            return Pair(media, "Hostname and port not specified")
        }

        if (prefs.authType == ImmichAuthType.SHARED_LINK) {
            if (prefs.pathName.isEmpty()) {
                return Pair(media, "Path name is empty")
            }

            if (prefs.password.isEmpty()) {
                return Pair(media, "Password is empty")
            }
        } else {
            if (prefs.apiKey.isEmpty()) {
                return Pair(media, "API key is empty")
            }

            // Name needed?
            if (prefs.selectedAlbumIds.isEmpty()) {
                return Pair(media, "Please select an album")
            }
        }

        val immichMedia =
            try {
                if (prefs.authType == ImmichAuthType.SHARED_LINK) {
                    getSharedAlbumFromAPI()
                } else {
                    getSelectedAlbumFromAPI()
                }
            } catch (e: Exception) {
                Timber.e(e)
                return Pair(emptyList(), e.message.toString())
            }

        // Get favorites if enabled and using API key authentication
        val favoriteAssets =
            if (prefs.authType == ImmichAuthType.API_KEY && prefs.includeFavorites) {
                try {
                    getFavoriteAssetsFromAPI()
                } catch (e: Exception) {
                    Timber.w(e, "Failed to fetch favorite assets, continuing without them")
                    emptyList()
                }
            } else {
                emptyList()
            }

        // Get rated assets if enabled and using API key authentication
        val ratedAssets =
            if (prefs.authType == ImmichAuthType.API_KEY && prefs.includeRatings.isNotEmpty()) {
                try {
                    getRatedAssetsFromAPI()
                } catch (e: Exception) {
                    Timber.w(e, "Failed to fetch rated assets, continuing without them")
                    emptyList()
                }
            } else {
                emptyList()
            }

        if (immichMedia.assets.isEmpty() && favoriteAssets.isEmpty() && ratedAssets.isEmpty()) {
            return Pair(media, "No files found")
        }

        // Combine album assets and favorite assets, removing duplicates
        val allAssets = (immichMedia.assets + favoriteAssets + ratedAssets).distinctBy { it.id }

        allAssets.forEach lit@{ asset ->
            val uri = getAssetUri(asset.id)
            val poi = mutableMapOf<Int, String>()
            val description = asset.exifInfo?.description ?: ""
            val filename = asset.originalPath

            Timber.i("Description: $description, Filename: $filename")

            try {
                if (asset.exifInfo?.country != null &&
                    asset.exifInfo.country.isNotBlank()
                ) {
                    Timber.i("fetchImmichMedia: ${asset.id} country = ${asset.exifInfo.country}")
                    val location =
                        listOf(
                            asset.exifInfo.country,
                            asset.exifInfo.state,
                            asset.exifInfo.city,
                        ).filter { !it.isNullOrBlank() }.joinToString(separator = ", ")
                    poi[poi.size] = location
                }
            } catch (e: Exception) {
                Timber.e(e, "Error parsing location EXIF data")
            }

            val item =
                AerialMedia(uri, description, poi).apply {
                    source = AerialMediaSource.IMMICH
                }

            when {
                FileHelper.isSupportedVideoType(filename) -> {
                    item.type = AerialMediaType.VIDEO
                    videos++
                    if (prefs.mediaType != ProviderMediaType.PHOTOS) {
                        media.add(item)
                    }
                }
                FileHelper.isSupportedImageType(filename) -> {
                    item.type = AerialMediaType.IMAGE
                    images++
                    if (prefs.mediaType != ProviderMediaType.VIDEOS) {
                        media.add(item)
                    }
                }
                else -> {
                    excluded++
                    return@lit
                }
            }
        }

        var message =
            String.format(
                context.getString(R.string.immich_media_test_summary1),
                media.size.toString(),
            ) + "\n"
        message += String.format(
            context.getString(R.string.immich_media_test_summary2),
            excluded.toString(),
        ) + "\n"
        if (prefs.mediaType != ProviderMediaType.PHOTOS) {
            message += String.format(
                context.getString(R.string.immich_media_test_summary3),
                videos.toString(),
            ) + "\n"
        }
        if (prefs.mediaType != ProviderMediaType.VIDEOS) {
            message += String.format(
                context.getString(R.string.immich_media_test_summary4),
                images.toString(),
            ) + "\n"
        }

        Timber.i("Media found: ${media.size}")
        return Pair(media, message)
    }

    private suspend fun getSharedAlbumFromAPI(): Album {
        try {
            val cleanedKey = cleanSharedLinkKey(prefs.pathName)
            Timber.d("Fetching shared album with key: $cleanedKey")
            val response = immichClient.getSharedAlbum(key = cleanedKey, password = prefs.password)
            Timber.d("Shared album API response: ${response.raw()}")
            if (response.isSuccessful) {
                val album = response.body()
                Timber.d("Shared album fetched successfully: ${album?.toString()}")
                return album ?: throw Exception("Empty response body")
            } else {
                val errorBody = response.errorBody()?.string()
                Timber.e("API error: ${response.code()} - ${response.message()}")
                Timber.e("Error body: $errorBody")
                throw Exception("API error: ${response.code()} - ${response.message()}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error fetching shared album: ${e.message}")
            throw e
        }
    }

    private suspend fun getSelectedAlbumFromAPI(): Album {
        try {
            val selectedAlbumIds = prefs.selectedAlbumIds
            if (selectedAlbumIds.isEmpty()) {
                throw Exception("No albums selected")
            }

            Timber.d("Attempting to fetch ${selectedAlbumIds.size} selected albums")
            Timber.d("Selected Album IDs: $selectedAlbumIds")
            Timber.d("API Key (first 5 chars): ${prefs.apiKey.take(5)}...")

            val allAssets = mutableListOf<Asset>()
            var combinedAlbumName = ""

            for ((index, albumId) in selectedAlbumIds.withIndex()) {
                val response = immichClient.getAlbum(apiKey = prefs.apiKey, albumId = albumId)
                Timber.d("API Request for album $albumId - URL: ${response.raw().request.url}")

                if (response.isSuccessful) {
                    val album = response.body()
                    if (album != null) {
                        Timber.d("Successfully fetched album: ${album.name}, assets: ${album.assets.size}")
                        allAssets.addAll(album.assets)
                        combinedAlbumName += if (index == 0) album.name else ", ${album.name}"
                    } else {
                        Timber.e("Received null album from successful response for album ID: $albumId")
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    Timber.e("Failed to fetch album $albumId. Code: ${response.code()}, Error: $errorBody")
                    // Continue with other albums instead of failing completely
                }
            }

            if (allAssets.isEmpty()) {
                throw Exception("No assets found in any of the selected albums")
            }

            // Remove duplicate assets based on ID
            val uniqueAssets = allAssets.distinctBy { it.id }
            Timber.d("Combined ${allAssets.size} assets from ${selectedAlbumIds.size} albums, ${uniqueAssets.size} unique assets")

            // Return a combined album with all assets
            return Album(
                id = "combined", // Use a special ID for the combined album
                name = combinedAlbumName,
                description = "Combined album from ${selectedAlbumIds.size} selected albums",
                assetCount = uniqueAssets.size,
                assets = uniqueAssets,
            )
        } catch (e: Exception) {
            Timber.e(e, "Exception while fetching selected albums")
            throw Exception("Failed to fetch selected albums", e)
        }
    }

    private suspend fun getFavoriteAssetsFromAPI(): List<Asset> {
        try {
            Timber.d("Fetching favorite assets")
            val searchRequest = SearchMetadataRequest(isFavorite = true)
            val response = immichClient.getFavoriteAssets(apiKey = prefs.apiKey, searchRequest = searchRequest)
            if (response.isSuccessful) {
                val searchResponse = response.body()
                val assets = searchResponse?.assets?.items ?: emptyList()
                Timber.d("Successfully fetched ${assets.size} favorite assets")
                return assets
            } else {
                val errorBody = response.errorBody()?.string()
                Timber.e("Failed to fetch favorites. Code: ${response.code()}, Error: $errorBody")
                throw Exception("Failed to fetch favorite assets: ${response.code()} - ${response.message()}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Exception while fetching favorite assets")
            throw Exception("Failed to fetch favorite assets", e)
        }
    }

    private suspend fun getRatedAssetsFromAPI(): List<Asset> {
        val ratedAssets = mutableListOf<Asset>()
        try {
            val ratings = prefs.includeRatings
            if (ratings.isNotEmpty()) {
                for (rating in ratings) {
                    Timber.d("Fetching rated assets with rating: $rating")
                    val searchRequest = SearchMetadataRequest(rating = rating.toInt())
                    val response = immichClient.getFavoriteAssets(apiKey = prefs.apiKey, searchRequest = searchRequest)
                    if (response.isSuccessful) {
                        val searchResponse = response.body()
                        val assets = searchResponse?.assets?.items ?: emptyList()
                        Timber.d("Successfully fetched ${assets.size} rated assets")
                        ratedAssets.addAll(assets)
                    } else {
                        val errorBody = response.errorBody()?.string()
                        Timber.e("Failed to fetch rated assets. Code: ${response.code()}, Error: $errorBody")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Exception while fetching rated assets")
        }
        return ratedAssets
    }

    suspend fun fetchAlbums(): Result<List<Album>> =
        try {
            val response = immichClient.getAlbums(apiKey = prefs.apiKey)
            if (response.isSuccessful) {
                Result.success(response.body() ?: emptyList())
            } else {
                val errorBody = response.errorBody()?.string() ?: ""
                val errorMessage =
                    try {
                        Json.decodeFromString<ErrorResponse>(errorBody).message
                    } catch (e: Exception) {
                        Timber.e(e, "Error parsing error body: $errorBody")
                        response.message()
                    }
                Result.failure(Exception("${response.code()} - $errorMessage"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }

    private fun cleanSharedLinkKey(input: String): String {
        return input
            .trim()
            .replace(Regex("^/+|/+$"), "") // Remove leading and trailing slashes
            .replace(Regex("^share/|^/share/"), "") // Remove "share/" or "/share/" from the beginning
    }

    private fun getAssetUri(id: String): Uri {
        val cleanedKey = cleanSharedLinkKey(prefs.pathName)
        return when (prefs.authType) {
            ImmichAuthType.SHARED_LINK -> "$server/api/assets/$id/original?key=$cleanedKey&password=${prefs.password}".toUri()
            ImmichAuthType.API_KEY -> "$server/api/assets/$id/original".toUri()
            null -> throw IllegalStateException("Invalid authentication type")
        }
    }
}
