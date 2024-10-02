package com.neilturner.aerialviews.providers

import android.content.Context
import android.net.Uri
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.AerialMediaSource
import com.neilturner.aerialviews.models.enums.AerialMediaType
import com.neilturner.aerialviews.models.enums.ProviderMediaType
import com.neilturner.aerialviews.models.enums.ProviderSourceType
import com.neilturner.aerialviews.models.prefs.WebDavMediaPrefs
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.models.videos.VideoMetadata
import com.neilturner.aerialviews.utils.FileHelper
import com.neilturner.aerialviews.utils.toStringOrEmpty
import com.thegrizzlylabs.sardineandroid.Sardine
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

class WebDavMediaProvider(
    context: Context,
    private val prefs: WebDavMediaPrefs,
) : MediaProvider(context) {
    override val type = ProviderSourceType.LOCAL

    override val enabled: Boolean
        get() = prefs.enabled

    override suspend fun fetchMedia(): List<AerialMedia> = fetchWebDavMedia().first

    override suspend fun fetchTest(): String = fetchWebDavMedia().second

    override suspend fun fetchMetadata(): List<VideoMetadata> = emptyList()

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
                    prefs.password,
                )
            } catch (ex: Exception) {
                Timber.e(ex)
                return Pair(emptyList(), ex.message.toString())
            }

        // Create WebDAV URL, add to media list, adding media type
        webDavMedia.first.forEach { url ->
            val uri = Uri.parse(url)
            val item = AerialMedia(uri)

            if (FileHelper.isSupportedVideoType(url)) {
                item.type = AerialMediaType.VIDEO
            } else if (FileHelper.isSupportedImageType(url)) {
                item.type = AerialMediaType.IMAGE
            }
            item.source = AerialMediaSource.WEBDAV
            media.add(item)
        }

        Timber.i("Media found: ${media.size}")
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
                client.setCredentials(userName, password, true)
            } catch (ex: Exception) {
                Timber.e(ex)
                return@withContext Pair(
                    selected,
                    "Failed to create WebDAV client",
                )
            }

            val baseUrl = scheme.lowercase() + "://" + hostName + pathName
            val files = listFilesAndFoldersRecursively(client, baseUrl)

            // Only pick videos
            if (prefs.mediaType != ProviderMediaType.PHOTOS) {
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

            var message = String.format(res.getString(R.string.webdav_media_test_summary1), files.size.toString()) + "\n"
            message += String.format(res.getString(R.string.webdav_media_test_summary2), excluded.toString()) + "\n"
            if (prefs.mediaType != ProviderMediaType.PHOTOS) {
                message += String.format(res.getString(R.string.webdav_media_test_summary3), videos.toString()) + "\n"
            }
            if (prefs.mediaType != ProviderMediaType.VIDEOS) {
                message += String.format(res.getString(R.string.webdav_media_test_summary4), images.toString()) + "\n"
            }
            message += String.format(res.getString(R.string.webdav_media_test_summary5), selected.size.toString())
            return@withContext Pair(selected, message)
        }

    private fun listFilesAndFoldersRecursively(
        client: Sardine,
        url: String = "",
    ): List<String> {
        val files = mutableListOf<String>()
        try {
            val resources = client.list(url).drop(1)
            for (resource in resources) {
                if (FileHelper.isDotOrHiddenFile(resource.name)) {
                    continue
                }

                if (resource.isDirectory && prefs.searchSubfolders) {
                    files.addAll(listFilesAndFoldersRecursively(client, "$url/${resource.name}"))
                } else if (!resource.isDirectory) {
                    files.add("$url/${resource.name}")
                }
            }
        } catch (ex: Exception) {
            Timber.e(ex)
        }
        return files
    }
}
