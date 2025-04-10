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
import com.neilturner.aerialviews.utils.ServerConfig
import com.neilturner.aerialviews.utils.SslHelper
import com.neilturner.aerialviews.utils.UrlParser
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Converter
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import timber.log.Timber

class ImmichMediaProvider(
    context: Context,
    private val prefs: ImmichMediaPrefs,
) : MediaProvider(context) {
    override val type = ProviderSourceType.REMOTE
    override val enabled: Boolean
        get() = prefs.enabled
    private lateinit var server: String

    private lateinit var apiInterface: ImmichService

    override suspend fun fetchMedia(): List<AerialMedia> = fetchImmichMedia().first

    override suspend fun fetchTest(): String = fetchImmichMedia().second

    override suspend fun fetchMetadata(): MutableMap<String, Pair<String, Map<Int, String>>> =
        mutableMapOf<String, Pair<String, Map<Int, String>>>()

    private suspend fun fetchImmichMedia(): Pair<List<AerialMedia>, String> {
        val media = mutableListOf<AerialMedia>()
        var excluded = 0
        var videos = 0
        var images = 0

        if (prefs.url.isEmpty()) {
            return Pair(media, "Hostname and port not specified")
        }

        // validate SSL certs

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
            if (prefs.selectedAlbumId.isEmpty()) {
                return Pair(media, "Password is empty")
            }
        }

        setupApiInterface()

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

        if (immichMedia.assets.isEmpty()) {
            return Pair(media, "No files found")
        }

        immichMedia.assets.forEach lit@{ asset ->
            val uri = getAssetUri(asset.id)
            val poi = mutableMapOf<Int, String>()
            val description = asset.exifInfo?.description ?: ""
            val filename = asset.originalPath

            Timber.i("Description: $description, Filename: $filename")

            // Trying to fix ClassCastException
            // Not sure what is causing it or exactly where it is
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
            val response = apiInterface.getSharedAlbum(key = cleanedKey, password = prefs.password)
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
            val selectedAlbumId = prefs.selectedAlbumId
            Timber.d("Attempting to fetch selected album")
            Timber.d("Selected Album ID: $selectedAlbumId")
            Timber.d("API Key (first 5 chars): ${prefs.apiKey.take(5)}...")
            val response = apiInterface.getAlbum(apiKey = prefs.apiKey, albumId = selectedAlbumId)
            Timber.d("API Request URL: ${response.raw().request.url}")
            Timber.d("API Request Method: ${response.raw().request.method}")
            Timber.d("API Request Headers: ${response.raw().request.headers}")
            if (response.isSuccessful) {
                val album = response.body()
                if (album != null) {
                    Timber.d("Successfully fetched album: ${album.name}, assets: ${album.assets.size}")
                    return album
                } else {
                    Timber.e("Received null album from successful response")
                    throw Exception("Received null album from API")
                }
            } else {
                val errorBody = response.errorBody()?.string()
                Timber.e("Failed to fetch album. Code: ${response.code()}, Error: $errorBody")
                throw Exception("Failed to fetch selected album: ${response.code()} - ${response.message()}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Exception while fetching selected album")
            throw Exception("Failed to fetch selected album", e)
        }
    }

    suspend fun fetchAlbums(): Result<List<Album>> =
        try {
            setupApiInterface()
            val response = apiInterface.getAlbums(apiKey = prefs.apiKey)
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

    private fun setupApiInterface() {
        try {
            server = UrlParser.parseServerUrl(prefs.url)
            val serverConfig = ServerConfig(server, prefs.validateSsl)
            val okHttpClient = SslHelper().createOkHttpClient(serverConfig)

            Timber.i("Connecting to $server")
            apiInterface =
                Retrofit
                    .Builder()
                    .baseUrl(server)
                    .client(okHttpClient)
                    .addConverterFactory(buildSerializer())
                    .build()
                    .create(ImmichService::class.java)
        } catch (e: Exception) {
            Timber.e(e, "Error creating Immich API interface: ${e.message}")
        }
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

    fun buildSerializer(): Converter.Factory {
        val contentType = "application/json".toMediaType()

        val json =
            Json {
                ignoreUnknownKeys = true
                isLenient = true
            }

        return json.asConverterFactory(contentType)
    }
}
