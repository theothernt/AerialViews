package com.neilturner.aerialviews.providers

import android.content.Context
import android.net.Uri
import android.util.Log
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.AerialMediaType
import com.neilturner.aerialviews.models.enums.ProviderMediaType
import com.neilturner.aerialviews.models.enums.ProviderSourceType
import com.neilturner.aerialviews.models.immich.Album
import com.neilturner.aerialviews.models.prefs.ImmichMediaPrefs
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.models.videos.VideoMetadata
import com.neilturner.aerialviews.utils.FileHelper
import com.neilturner.aerialviews.utils.toStringOrEmpty
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

class ImmichMediaProvider(context: Context, private val prefs: ImmichMediaPrefs) :
    MediaProvider(context) {
    override val type = ProviderSourceType.REMOTE
    override val enabled: Boolean
        get() = prefs.enabled

    private lateinit var server: String
    private lateinit var key: String
    private lateinit var apiInterface: ImmichService

    init {
        parsePrefs()
        getApiInterface()
    }

    private object RetrofitInstance {
        fun getInstance(host: String): Retrofit {
            return Retrofit.Builder().baseUrl(host)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
    }

    private interface ImmichService {
        @GET("/api/shared-links/me")
        suspend fun getAlbum(
            @Query("key") key: String,
            @Query("password") password: String?
        ): Response<Album>
    }

    private fun parsePrefs() {
        server = prefs.scheme?.toStringOrEmpty()?.lowercase() + "://" + prefs.hostName

        val pattern = Regex("(^/)?(share/)?(.*)")
        val replacement = "$3"
        key = pattern.replace(prefs.pathName, replacement)
    }

    private fun getApiInterface() {
        Log.d(TAG, "Connecting to $server")
        apiInterface =
            RetrofitInstance.getInstance(server).create(ImmichService::class.java)
    }

    override suspend fun fetchMedia(): List<AerialMedia> {
        return fetchImmichMedia().first
    }

    override suspend fun fetchTest(): String {
        return fetchImmichMedia().second
    }

    override suspend fun fetchMetadata(): List<VideoMetadata> {
        return emptyList()
    }

    private suspend fun fetchImmichMedia(): Pair<List<AerialMedia>, String> {
        val media = mutableListOf<AerialMedia>()

        // Check hostname
        // Validate IP address or hostname?
        if (prefs.hostName.isEmpty()) {
            return Pair(media, "Hostname and port not specified")
        }

        // Check path name
        if (prefs.pathName.isEmpty()) {
            return Pair(media, "Path name not specified")
        }

        val immichMedia =
            try {
                getAlbumFromAPI()
            } catch (e: Exception) {
                Log.e(TAG, e.message.toString())
                return Pair(emptyList(), e.message.toString())
            }

        var excluded = 0
        var videos = 0
        var images = 0

        // Create Immich URL, add to media list, adding media type
        immichMedia.assets.forEach lit@{ asset ->
            val uri = getAssetUri(asset.id)
            val filename = Uri.parse(asset.originalPath)
            val poi = mutableMapOf<Int,String>()

            val description = asset.exifInfo?.description.toString()
            if (!asset.exifInfo?.country.isNullOrEmpty()) {
                Log.i(TAG, "fetchImmichMedia: ${asset.id} country = ${asset.exifInfo?.country}")
                val location = listOf(
                    asset.exifInfo?.country,
                    asset.exifInfo?.state,
                    asset.exifInfo?.city
                ).filter { !it.isNullOrBlank() }.joinToString(separator = ", ")
                poi[poi.size]=location
            }
            if (description.isNotEmpty()) {
                poi[poi.size]=description
            }

            val item = AerialMedia(uri, filename, description, poi)
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

        var message = String.format(context.getString(R.string.immich_media_test_summary1), media.size.toString()) + "\n"
        message += String.format(context.getString(R.string.immich_media_test_summary2), excluded.toString()) + "\n"
        if (prefs.mediaType != ProviderMediaType.PHOTOS) {
            message += String.format(context.getString(R.string.immich_media_test_summary3), videos.toString()) + "\n"
        }
        if (prefs.mediaType != ProviderMediaType.VIDEOS) {
            message += String.format(context.getString(R.string.immich_media_test_summary4), images.toString()) + "\n"
        }

        Log.i(TAG, "Media found: ${media.size}")
        return Pair(media, message)
    }

    private suspend fun getAlbumFromAPI(): Album {
        lateinit var album: Album
        try {
            val response = apiInterface.getAlbum(key = key, password = prefs.password)
            val body = response.body()
            if (body != null) {
                album = body
            }
        } catch (exception: Exception) {
            Log.i(TAG, exception.message.toString())
        }
        return album
    }

    private fun getAssetUri(id: String): Uri {
        return Uri.parse(server + "/api/assets/${id}/original?key=${key}&password=${prefs.password}")
    }

    companion object {
        private const val TAG = "ImmichVideoProvider"
    }
}
