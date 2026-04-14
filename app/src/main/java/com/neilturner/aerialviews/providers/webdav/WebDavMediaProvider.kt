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
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.URLEncoder

internal class WebDavMediaProvider(
    context: Context,
    private val prefs: WebDavProviderPreferences,
    private val clientFactory: () -> WebDavListingClient = { SardineWebDavClient() },
) : MediaProvider(context) {
    override val type = ProviderSourceType.LOCAL

    override val enabled: Boolean
        get() = prefs.enabled

    override suspend fun fetch(): ProviderFetchResult = fetchWebDavMedia()

    override suspend fun fetchMusic(): List<MusicTrack> {
        if (!prefs.musicEnabled || prefs.hostName.isEmpty() || prefs.pathName.isEmpty()) {
            return emptyList()
        }

        return withContext(Dispatchers.IO) {
            val client =
                try {
                    clientFactory().apply {
                        setCredentials(prefs.userName, prefs.password, true)
                    }
                } catch (ex: Exception) {
                    Timber.e(ex, "WebDavMediaProvider: failed to create WebDAV client for music")
                    return@withContext emptyList<MusicTrack>()
                }

            val endpoint =
                try {
                    buildWebDavEndpoint(prefs.scheme, prefs.hostName, prefs.pathName)
                } catch (ex: IllegalArgumentException) {
                    Timber.e(ex, "WebDavMediaProvider: failed to build endpoint for music")
                    return@withContext emptyList<MusicTrack>()
                }

            val baseUrl = endpoint.baseUrl
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

    private suspend fun fetchWebDavMedia(): ProviderFetchResult {
        return when (val testResult = testConnectionInternal()) {
            is WebDavConnectionTestResult.SuccessSummary -> {
                val media = testResult.files.mapNotNull { url ->
                    val uri = addCredentialsToUrl(url, prefs.userName, prefs.password).toUri()
                    val item = AerialMedia(uri)

                    when {
                        FileHelper.isSupportedVideoType(url) -> item.type = AerialMediaType.VIDEO
                        FileHelper.isSupportedImageType(url) -> item.type = AerialMediaType.IMAGE
                        else -> return@mapNotNull null
                    }

                    item.source = AerialMediaSource.WEBDAV
                    item
                }

                Timber.i("Media found: ${media.size}")
                ProviderFetchResult.Success(media = media, summary = testResult.summary)
            }

            is WebDavConnectionTestResult.ValidationError -> ProviderFetchResult.Error(testResult.message)
            is WebDavConnectionTestResult.ConnectionError -> ProviderFetchResult.Error(testResult.message)
            is WebDavConnectionTestResult.AuthError -> ProviderFetchResult.Error(testResult.message)
            is WebDavConnectionTestResult.PathError -> ProviderFetchResult.Error(testResult.message)
        }
    }

    private suspend fun testConnectionInternal(): WebDavConnectionTestResult {
        val endpoint =
            try {
                buildWebDavEndpoint(prefs.scheme, prefs.hostName, prefs.pathName)
            } catch (ex: IllegalArgumentException) {
                return WebDavConnectionTestResult.ValidationError(ex.message ?: "Invalid WebDAV settings")
            }

        return findWebDavMedia(
            endpoint = endpoint,
            userName = prefs.userName,
            password = prefs.password,
        )
    }

    private suspend fun findWebDavMedia(
        endpoint: WebDavEndpoint,
        userName: String,
        password: String,
    ): WebDavConnectionTestResult =
        withContext(Dispatchers.IO) {
            val res = context.resources
            val selected = mutableListOf<String>()
            val excluded: Int
            val images: Int

            val client =
                try {
                    clientFactory().apply {
                        setCredentials(userName, password, true)
                    }
                } catch (ex: Exception) {
                    Timber.e(ex)
                    return@withContext WebDavConnectionTestResult.ConnectionError("Failed to create WebDAV client")
                }

            val files =
                try {
                    listFilesAndFoldersRecursively(client, endpoint.baseUrl).map { it.first }
                } catch (ex: Exception) {
                    Timber.e(ex)
                    return@withContext formatWebDavConnectionError(endpoint, ex)
                }

            if (prefs.includeVideos) {
                selected.addAll(files.filter { FileHelper.isSupportedVideoType(it) })
            }
            val videos = selected.size

            if (prefs.includePhotos) {
                selected.addAll(files.filter { FileHelper.isSupportedImageType(it) })
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

            return@withContext WebDavConnectionTestResult.SuccessSummary(
                files = selected,
                summary = message,
            )
        }

    private fun listFilesAndFoldersRecursively(
        client: WebDavListingClient,
        url: String = "",
    ): List<Pair<String, Long>> {
        val filesWithDates = mutableListOf<Pair<String, Long>>()
        val directories = ArrayDeque<String>()
        var rootVerified = false

        directories.add(url)

        while (directories.isNotEmpty()) {
            val currentUrl = directories.removeFirst()

            try {
                val resources = client.list(currentUrl).drop(1)
                rootVerified = true
                for (resource in resources) {
                    if (FileHelper.isDotOrHiddenFile(resource.name)) {
                        continue
                    }

                    if (resource.isDirectory && prefs.searchSubfolders) {
                        directories.add("$currentUrl/${resource.name}")
                    } else if (!resource.isDirectory) {
                        filesWithDates.add(Pair("$currentUrl/${resource.name}", resource.modifiedTimeMs))
                    }
                }
            } catch (ex: Exception) {
                if (!rootVerified) {
                    throw ex
                }
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

internal data class WebDavResourceInfo(
    val name: String,
    val isDirectory: Boolean,
    val modifiedTimeMs: Long = 0L,
)

internal interface WebDavListingClient {
    fun setCredentials(
        userName: String,
        password: String,
        preemptive: Boolean,
    )

    fun list(url: String): List<WebDavResourceInfo>
}

internal class SardineWebDavClient(
    private val delegate: OkHttpSardine = OkHttpSardine(),
) : WebDavListingClient {
    override fun setCredentials(
        userName: String,
        password: String,
        preemptive: Boolean,
    ) {
        delegate.setCredentials(userName, password, preemptive)
    }

    override fun list(url: String): List<WebDavResourceInfo> =
        delegate
            .list(url)
            .map { resource ->
                WebDavResourceInfo(
                    name = resource.name,
                    isDirectory = resource.isDirectory,
                    modifiedTimeMs = resource.modified?.time ?: 0L,
                )
            }
}
