package com.neilturner.aerialviews.providers.webdav

import android.content.Context
import androidx.core.net.toUri
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.AerialMediaSource
import com.neilturner.aerialviews.models.enums.AerialMediaType
import com.neilturner.aerialviews.models.enums.ProviderSourceType
import com.neilturner.aerialviews.models.music.MusicTrack
import com.neilturner.aerialviews.models.prefs.WebDavProviderPreferences
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.providers.MediaProvider
import com.neilturner.aerialviews.providers.ProviderFetchResult
import com.neilturner.aerialviews.utils.FileHelper
import com.neilturner.aerialviews.utils.toStringOrEmpty
import com.thegrizzlylabs.sardineandroid.Sardine
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.URLEncoder

class WebDavMediaProvider(
    context: Context,
    private val prefs: WebDavProviderPreferences,
) : MediaProvider(context) {
    override val type = ProviderSourceType.LOCAL

    override val enabled: Boolean
        get() = prefs.enabled

    override suspend fun fetch(): ProviderFetchResult {
        val result = fetchWebDavMedia()
        return ProviderFetchResult.Success(media = result.first, summary = result.second)
    }

    override suspend fun fetchMusic(): List<MusicTrack> {
        if (!prefs.musicEnabled || prefs.hostName.isEmpty() || prefs.pathName.isEmpty()) {
            return emptyList()
        }

        return withContext(Dispatchers.IO) {
            val client =
                try {
                    OkHttpSardine().apply {
                        setCredentials(prefs.userName, prefs.password, true)
                    }
                } catch (ex: Exception) {
                    Timber.e(ex, "WebDavMediaProvider: failed to create WebDAV client for music")
                    return@withContext emptyList<MusicTrack>()
                }

            val baseUrl = prefs.scheme.toString().lowercase() + "://" + prefs.hostName + prefs.pathName
            listFilesAndFoldersRecursively(client, baseUrl)
                .filter { FileHelper.isSupportedAudioType(it.first) }
                .map { fileInfo ->
                    val url = fileInfo.first
                    MusicTrack(
                        uri = addCredentialsToUrl(url, prefs.userName, prefs.password).toUri(),
                        source = AerialMediaSource.WEBDAV,
                    )
                }
        }
    }

    override suspend fun fetchMetadata(media: List<AerialMedia>): List<AerialMedia> = media

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
            val uri = addCredentialsToUrl(url, prefs.userName, prefs.password).toUri()
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
            val files = listFilesAndFoldersRecursively(client, baseUrl).map { it.first }

            // Only pick videos
            if (prefs.includeVideos) {
                selected.addAll(
                    files.filter { item ->
                        FileHelper.isSupportedVideoType(item)
                    },
                )
            }
            val videos = selected.size

            // Only pick images
            if (prefs.includePhotos) {
                selected.addAll(
                    files.filter { item ->
                        FileHelper.isSupportedImageType(item)
                    },
                )
            }
            images = selected.size - videos
            excluded = files.size - selected.size

            var message =
                String.format(
                    res.getString(R.string.webdav_media_test_summary1),
                    files.size.toString(),
                ) + "\n"
            message += String.format(
                res.getString(R.string.webdav_media_test_summary2),
                excluded.toString(),
            ) + "\n"
            if (prefs.includeVideos) {
                message += String.format(
                    res.getString(R.string.webdav_media_test_summary3),
                    videos.toString(),
                ) + "\n"
            }
            if (prefs.includePhotos) {
                message += String.format(
                    res.getString(R.string.webdav_media_test_summary4),
                    images.toString(),
                ) + "\n"
            }
            message +=
                String.format(
                    res.getString(R.string.webdav_media_test_summary5),
                    selected.size.toString(),
                )
            return@withContext Pair(selected, message)
        }

    private fun listFilesAndFoldersRecursively(
        client: Sardine,
        url: String = "",
    ): List<Pair<String, Long>> {
        val filesWithDates = mutableListOf<Pair<String, Long>>()
        val directories = ArrayDeque<String>()

        // Start with the initial URL
        directories.add(url)

        // Process directories until the queue is empty
        while (directories.isNotEmpty()) {
            val currentUrl = directories.removeFirst()

            try {
                val resources = client.list(currentUrl).drop(1)
                for (resource in resources) {
                    if (FileHelper.isDotOrHiddenFile(resource.name)) {
                        continue
                    }

                    if (resource.isDirectory && prefs.searchSubfolders) {
                        directories.add("$currentUrl/${resource.name}")
                    } else if (!resource.isDirectory) {
                        val modifiedTime = resource.modified?.time ?: 0L
                        filesWithDates.add(Pair("$currentUrl/${resource.name}", modifiedTime))
                    }
                }
            } catch (ex: Exception) {
                Timber.e(ex)
            }
        }

        return filesWithDates.sortedByDescending { it.second }
    }

    private fun addCredentialsToUrl(
        url: String,
        userName: String,
        password: String,
    ): String {
        if (userName.isEmpty()) {
            return url
        }

        val encodedUser = URLEncoder.encode(userName, "utf-8")
        val encodedPass = if (password.isNotEmpty()) ":" + URLEncoder.encode(password, "utf-8") else ""
        val userInfo = "$encodedUser$encodedPass@"
        val schemeSeparatorIndex = url.indexOf("://")
        if (schemeSeparatorIndex == -1) {
            return url
        }

        val prefix = url.substring(0, schemeSeparatorIndex + 3)
        val suffix = url.substring(schemeSeparatorIndex + 3)
        return prefix + userInfo + suffix
    }
}
