package com.neilturner.aerialviews.providers
import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.AerialMediaSource
import com.neilturner.aerialviews.models.enums.AerialMediaType
import com.neilturner.aerialviews.models.enums.ImmichAuthType
import com.neilturner.aerialviews.models.enums.ProviderMediaType
import com.neilturner.aerialviews.models.enums.ProviderSourceType
import com.neilturner.aerialviews.models.immich.Album
import com.neilturner.aerialviews.models.immich.ErrorResponse
import com.neilturner.aerialviews.models.prefs.ImmichMediaPrefs
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.models.videos.VideoMetadata
import com.neilturner.aerialviews.utils.FileHelper
import com.neilturner.aerialviews.utils.ServerConfig
import com.neilturner.aerialviews.utils.SslHelper
import com.neilturner.aerialviews.utils.UrlParser
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query
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

    init {
        if (enabled) {
            setupApiInterface()
        }
    }

    override suspend fun fetchMedia(): List<AerialMedia> = fetchImmichMedia().first

    override suspend fun fetchMetadata(): List<VideoMetadata> = emptyList()

    override suspend fun fetchTest(): String {
        if (prefs.url.isEmpty()) {
            return "Server URL not specified"
        }

        return try {
            when (prefs.authType) {
                ImmichAuthType.SHARED_LINK -> {
                    val cleanedKey = cleanSharedLinkKey(prefs.pathName)
                    val response = apiInterface.getSharedAlbum(key = cleanedKey, password = prefs.password)
                    handleResponse(response, "Shared link test successful")
                }
                ImmichAuthType.API_KEY -> {
                    val response = apiInterface.getAlbums(apiKey = prefs.apiKey)
                    handleResponse(response, "API key test successful")
                }
                null -> "Invalid authentication type"
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    private suspend fun fetchImmichMedia(): Pair<List<AerialMedia>, String> {
        val media = mutableListOf<AerialMedia>()

        if (prefs.url.isEmpty()) {
            return Pair(media, "Hostname and port not specified")
        }

        val immichMedia = try {
            when (prefs.authType) {
                ImmichAuthType.SHARED_LINK -> getSharedAlbumFromAPI()
                ImmichAuthType.API_KEY -> getSelectedAlbumFromAPI()
                null -> return Pair(emptyList(), "Invalid authentication type")
            }
        } catch (e: Exception) {
            Timber.e(e, e.message.toString())
            return Pair(emptyList(), e.message.toString())
        }

        var excluded = 0
        var videos = 0
        var images = 0

        immichMedia.assets.forEach lit@{ asset ->
            val uri = getAssetUri(asset.id)
            val poi = mutableMapOf<Int, String>()

            val description = asset.exifInfo?.description.toString()
            if (!asset.exifInfo?.country.isNullOrEmpty()) {
                Timber.i("fetchImmichMedia: ${asset.id} country = ${asset.exifInfo.country}")
                val location =
                    listOf(
                        asset.exifInfo.country,
                        asset.exifInfo.state,
                        asset.exifInfo.city,
                    ).filter { !it.isNullOrBlank() }.joinToString(separator = ", ")
                poi[poi.size] = location
            }
            if (description.isNotEmpty()) {
                poi[poi.size] = description
            }

            val item = AerialMedia(uri, description, poi)
            item.source = AerialMediaSource.IMMICH

            when {
                FileHelper.isSupportedVideoType(asset.originalPath.toString()) -> {
                    item.type = AerialMediaType.VIDEO
                    videos++
                    if (prefs.mediaType != ProviderMediaType.PHOTOS) {
                        media.add(item)
                    }
                }
                FileHelper.isSupportedImageType(asset.originalPath.toString()) -> {
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

        var message = String.format(
            context.getString(R.string.immich_media_test_summary1),
            media.size.toString()
        ) + "\n"
        message += String.format(
            context.getString(R.string.immich_media_test_summary2),
            excluded.toString()
        ) + "\n"
        if (prefs.mediaType != ProviderMediaType.PHOTOS) {
            message += String.format(
                context.getString(R.string.immich_media_test_summary3),
                videos.toString()
            ) + "\n"
        }
        if (prefs.mediaType != ProviderMediaType.VIDEOS) {
            message += String.format(
                context.getString(R.string.immich_media_test_summary4),
                images.toString()
            ) + "\n"
        }

        Timber.i("Media found: ${media.size}")
        return Pair(media, message)
    }

    private fun handleResponse(response: Response<*>, successMessage: String): String {
        return if (response.isSuccessful) {
            successMessage
        } else {
            val errorBody = response.errorBody()?.string()
            val errorMessage = try {
                Gson().fromJson(errorBody, ErrorResponse::class.java).message
            } catch (e: Exception) {
                response.message()
            }
            "Error ${response.code()} - $errorMessage"
        }
    }

    private suspend fun getSharedAlbumFromAPI(): Album {
        try {
            val cleanedKey = cleanSharedLinkKey(prefs.pathName)
            Timber.d("Fetching shared album with key: $cleanedKey")
            val response = apiInterface.getSharedAlbum(key = cleanedKey, password = prefs.password)
            Timber.d("Shared album API response: ${response.raw().toString()}")
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

    suspend fun fetchAlbums(): Result<List<Album>> {
        return try {
            val response = apiInterface.getAlbums(apiKey = prefs.apiKey)
            if (response.isSuccessful) {
                Result.success(response.body() ?: emptyList())
            } else {
                val errorBody = response.errorBody()?.string()
                val errorMessage = try {
                    Gson().fromJson(errorBody, ErrorResponse::class.java).message
                } catch (e: Exception) {
                    response.message()
                }
                Result.failure(Exception("${response.code()} - $errorMessage"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun setupApiInterface() {
        try {
            server = UrlParser.parseServerUrl(prefs.url)
            val serverConfig = ServerConfig(server, prefs.validateSsl)
            val okHttpClient = SslHelper().createOkHttpClient(serverConfig)

            Timber.i("Connecting to $server")
            apiInterface = Retrofit.Builder()
                .baseUrl(server)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ImmichService::class.java)
        } catch (e: Exception) {
            Timber.e(e, "Error creating Immich API interface: ${e.message}")
            throw e
        }
    }

    private fun cleanSharedLinkKey(input: String): String {
        return input.trim()
            .replace(Regex("^/+|/+$"), "") // Remove leading and trailing slashes
            .replace(Regex("^share/|^/share/"), "") // Remove "share/" or "/share/" from the beginning
    }

    private fun getAssetUri(id: String): Uri {
        val cleanedKey = cleanSharedLinkKey(prefs.pathName)
        return when (prefs.authType) {
            ImmichAuthType.SHARED_LINK -> Uri.parse("$server/api/assets/$id/original?key=$cleanedKey&password=${prefs.password}")
            ImmichAuthType.API_KEY -> Uri.parse("$server/api/assets/$id/original")
            null -> throw IllegalStateException("Invalid authentication type")
        }
    }

    private interface ImmichService {
        @GET("/api/shared-links/me")
        suspend fun getSharedAlbum(
            @Query("key") key: String,
            @Query("password") password: String?,
        ): Response<Album>

        @GET("/api/albums")
        suspend fun getAlbums(@Header("x-api-key") apiKey: String): Response<List<Album>>

        @GET("/api/albums/{id}")
        suspend fun getAlbum(
            @Header("x-api-key") apiKey: String,
            @Path("id") albumId: String
        ): Response<Album>
    }
}