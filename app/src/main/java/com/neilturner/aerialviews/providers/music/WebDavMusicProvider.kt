package com.neilturner.aerialviews.providers.music

import android.content.Context
import androidx.core.net.toUri
import com.neilturner.aerialviews.models.enums.AerialMediaSource
import com.neilturner.aerialviews.models.music.MusicTrack
import com.neilturner.aerialviews.models.prefs.WebDavProviderPreferences
import com.neilturner.aerialviews.utils.FileHelper
import com.thegrizzlylabs.sardineandroid.Sardine
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.URLEncoder

class WebDavMusicProvider(
    context: Context,
    private val prefs: WebDavProviderPreferences,
) : MusicProvider(context) {
    override val enabled: Boolean
        get() = prefs.enabled && prefs.musicEnabled

    override suspend fun fetchMusic(): List<MusicTrack> {
        if (prefs.hostName.isEmpty() || prefs.pathName.isEmpty()) {
            return emptyList()
        }

        return withContext(Dispatchers.IO) {
            val client =
                try {
                    OkHttpSardine().apply {
                        setCredentials(prefs.userName, prefs.password, true)
                    }
                } catch (ex: Exception) {
                    Timber.e(ex, "WebDavMusicProvider: failed to create WebDAV client")
                    return@withContext emptyList<MusicTrack>()
                }

            val baseUrl = prefs.scheme.toString().lowercase() + "://" + prefs.hostName + prefs.pathName
            val files = listAudioFilesRecursively(client, baseUrl)

            files.map { url ->
                MusicTrack(
                    uri = addCredentialsToUrl(url).toUri(),
                    source = AerialMediaSource.WEBDAV,
                    title = FileHelper.stripAudioFileExtension(url.substringAfterLast('/')),
                )
            }
        }
    }

    private fun listAudioFilesRecursively(
        client: Sardine,
        url: String,
    ): List<String> {
        val filesWithDates = mutableListOf<Pair<String, Long>>()
        val directories = ArrayDeque<String>()
        directories.add(url)

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
                    } else if (!resource.isDirectory && FileHelper.isSupportedAudioType(resource.name)) {
                        val modifiedTime = resource.modified?.time ?: 0L
                        filesWithDates.add(Pair("$currentUrl/${resource.name}", modifiedTime))
                    }
                }
            } catch (ex: Exception) {
                Timber.e(ex, "WebDavMusicProvider: failed to list $currentUrl")
            }
        }

        return filesWithDates
            .sortedByDescending { it.second }
            .map { it.first }
    }

    private fun addCredentialsToUrl(url: String): String {
        if (prefs.userName.isEmpty()) {
            return url
        }

        val encodedUser = URLEncoder.encode(prefs.userName, "utf-8")
        val encodedPass = if (prefs.password.isNotEmpty()) ":" + URLEncoder.encode(prefs.password, "utf-8") else ""
        val schemeSeparatorIndex = url.indexOf("://")
        if (schemeSeparatorIndex == -1) {
            return url
        }

        val prefix = url.substring(0, schemeSeparatorIndex + 3)
        val suffix = url.substring(schemeSeparatorIndex + 3)
        return prefix + encodedUser + encodedPass + "@" + suffix
    }
}
