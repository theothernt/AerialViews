package com.neilturner.aerialviews.providers

import android.content.Context
import android.net.Uri
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.AerialMediaSource
import com.neilturner.aerialviews.models.enums.AerialMediaType
import com.neilturner.aerialviews.models.enums.ImmichAuthType
import com.neilturner.aerialviews.models.enums.ProviderMediaType
import com.neilturner.aerialviews.models.enums.ProviderSourceType
import com.neilturner.aerialviews.models.immich.Album
import com.neilturner.aerialviews.models.prefs.ImmichMediaPrefs
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.models.videos.VideoMetadata
import com.neilturner.aerialviews.utils.FileHelper
import com.neilturner.aerialviews.utils.toStringOrEmpty
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query
import timber.log.Timber
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

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
        parsePrefs()
        if (enabled) {
            getApiInterface()
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

    private fun parsePrefs() {
        server = prefs.scheme?.toStringOrEmpty()?.lowercase() + "://" + prefs.hostName
    }

    private fun getApiInterface() {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(
                chain: Array<out X509Certificate>?,
                authType: String?
            ) {
            }

            override fun checkServerTrusted(
                chain: Array<out X509Certificate>?,
                authType: String?
            ) {
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })

        val sslContext = SSLContext.getInstance("SSL").apply {
            init(null, trustAllCerts, SecureRandom())
        }

        val okHttpClient = OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .build()

        Timber.i("Connecting to $server")
        try {
            apiInterface = Retrofit.Builder()
                .baseUrl(server)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ImmichService::class.java)
        } catch (e: Exception) {
            Timber.e(e, e.message.toString())
        }
    }

    override suspend fun fetchMedia(): List<AerialMedia> = fetchImmichMedia().first
    override suspend fun fetchTest(): String = fetchImmichMedia().second
    override suspend fun fetchMetadata(): List<VideoMetadata> = emptyList()

    private suspend fun fetchImmichMedia(): Pair<List<AerialMedia>, String> {
        val media = mutableListOf<AerialMedia>()

        if (prefs.hostName.isEmpty()) {
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
            val filename = Uri.parse(asset.originalPath)
            val poi = mutableMapOf<Int, String>()

            val description = asset.exifInfo?.description.toString()
            if (!asset.exifInfo?.country.isNullOrEmpty()) {
                Timber.i("fetchImmichMedia: ${asset.id} country = ${asset.exifInfo?.country}")
                val location =
                    listOf(
                        asset.exifInfo?.country,
                        asset.exifInfo?.state,
                        asset.exifInfo?.city,
                    ).filter { !it.isNullOrBlank() }.joinToString(separator = ", ")
                poi[poi.size] = location
            }
            if (description.isNotEmpty()) {
                poi[poi.size] = description
            }

            val item = AerialMedia(uri, description, poi)
            item.source = AerialMediaSource.IMMICH
            if (FileHelper.isSupportedVideoType(asset.originalPath.toString())) {
                item.type = AerialMediaType.VIDEO
                videos++
            } else if (FileHelper.isSupportedImageType(asset.originalPath.toString())) {
                item.type = AerialMediaType.IMAGE
                images++
            } else {
                excluded++
                return@lit
            }
            media.add(item)
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

    private suspend fun getSharedAlbumFromAPI(): Album {
        try {
            Timber.d("Fetching shared album with key: ${prefs.pathName}")
            val response =
                apiInterface.getSharedAlbum(key = prefs.pathName, password = prefs.password)
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

    suspend fun fetchAlbums(): List<Album> {
        try {
            val response = apiInterface.getAlbums(apiKey = prefs.apiKey)
            if (response.isSuccessful) {
                val albums = response.body()
                Timber.d("API Response: ${albums?.toString()}")
                albums?.forEach { album ->
                    Timber.d("Album: id=${album.id}, name=${album.name}, assetCount=${album.assetCount}, type=${album.type}")
                }
                return albums ?: emptyList()
            } else {
                Timber.e("Error fetching albums: ${response.code()} - ${response.message()}")
                Timber.e("Error body: ${response.errorBody()?.string()}")
                throw Exception("Failed to fetch albums: ${response.code()}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Exception while fetching albums")
            throw e
        }
    }

    private fun getAssetUri(id: String): Uri {
        return when (prefs.authType) {
            ImmichAuthType.SHARED_LINK -> Uri.parse("$server/api/assets/$id/original?key=${prefs.pathName}&password=${prefs.password}")
            ImmichAuthType.API_KEY -> {
                Uri.parse("$server/api/assets/$id/original")
            }

            null -> throw IllegalStateException("Invalid authentication type")
        }
    }

}