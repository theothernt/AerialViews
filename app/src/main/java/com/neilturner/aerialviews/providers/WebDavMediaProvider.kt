package com.neilturner.aerialviews.providers

import android.content.Context
import android.util.Log
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.ProviderMediaType
import com.neilturner.aerialviews.models.enums.ProviderSourceType
import com.neilturner.aerialviews.models.prefs.WebDavMediaPrefs
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.models.videos.VideoMetadata
import com.neilturner.aerialviews.utils.FileHelper
import com.neilturner.aerialviews.utils.toStringOrEmpty
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WebDavMediaProvider(context: Context, private val prefs: WebDavMediaPrefs) : MediaProvider(context) {
    override val type = ProviderSourceType.LOCAL

    override val enabled: Boolean
        get() = prefs.enabled

    override suspend fun fetchMedia(): List<AerialMedia> {
        return fetchWebDavMedia().first
    }

    override suspend fun fetchTest(): String {
        return fetchWebDavMedia().second
    }

    override suspend fun fetchMetadata(): List<VideoMetadata> {
        return emptyList()
    }

    private suspend fun fetchWebDavMedia(): Pair<List<AerialMedia>, String> {
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

        val webDavMedia =
            try {
                findWebDavMedia(
                    prefs.scheme.toStringOrEmpty(),
                    prefs.hostName,
                    prefs.pathName,
                    prefs.userName,
                    prefs.password
                )
            } catch (e: Exception) {
                Log.e(TAG, e.message.toString())
                return Pair(emptyList(), e.message.toString())
            }

        Log.i(TAG, "Media found: ${media.size}")
        return Pair(media, webDavMedia.second)
    }

    private suspend fun findWebDavMedia(
        scheme: String,
        hostName: String,
        pathName: String,
        userName: String,
        password: String,
    ): Pair<List<String>, String> =
        withContext(Dispatchers.IO) {
            val res = context.resources
            val selected = mutableListOf<String>()
            val excluded: Int
            val images: Int

            // WebDAV client
            val client: OkHttpSardine
            try {
                client = OkHttpSardine()
                client.setCredentials(userName, password)
            } catch (e: Exception) {
                Log.e(TAG, e.message.toString())
                return@withContext Pair(
                    selected,
                    "Failed to create WebDAV client",
                )
            }

            val url = scheme.lowercase() + "://" + hostName + pathName
            val resources = client.list(url)
            val files = resources.map { it.name }

            // Only pick videos
            if (prefs.mediaType != ProviderMediaType.IMAGES) {
                selected.addAll(
                    files.filter { item ->
                        FileHelper.isSupportedVideoType(item)
                    },
                )
            }
            val videos = selected.size

            // Only pick images
            if (prefs.mediaType != ProviderMediaType.VIDEOS) {
                selected.addAll(
                    files.filter { item ->
                        FileHelper.isSupportedImageType(item)
                    },
                )
            }
            images = selected.size - videos
            excluded = files.size - selected.size

            var message = String.format(res.getString(R.string.webdav_media_test_summary1), files.size) + "\n"
            message += String.format(res.getString(R.string.webdav_media_test_summary2), excluded) + "\n"
            if (prefs.mediaType != ProviderMediaType.IMAGES) {
                message += String.format(res.getString(R.string.webdav_media_test_summary3), videos) + "\n"
            }
            if (prefs.mediaType != ProviderMediaType.VIDEOS) {
                message += String.format(res.getString(R.string.webdav_media_test_summary4), images) + "\n"
            }
            message += String.format(res.getString(R.string.webdav_media_test_summary5), selected.size)
            return@withContext Pair(selected, message)
        }

    companion object {
        private const val TAG = "WebDavMediaProvider"
    }
}
