package com.neilturner.aerialviews.providers.music

import android.content.Context
import android.provider.MediaStore
import androidx.core.net.toUri
import com.neilturner.aerialviews.models.enums.AerialMediaSource
import com.neilturner.aerialviews.models.enums.SearchType
import com.neilturner.aerialviews.models.music.MusicTrack
import com.neilturner.aerialviews.models.prefs.LocalProviderPreferences
import com.neilturner.aerialviews.utils.FileHelper
import com.neilturner.aerialviews.utils.StorageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

class LocalMusicProvider(
    context: Context,
    private val prefs: LocalProviderPreferences,
) : MusicProvider(context) {
    override val enabled: Boolean
        get() = prefs.enabled && prefs.musicEnabled

    override suspend fun fetchMusic(): List<MusicTrack> =
        if (prefs.searchType == SearchType.FOLDER_ACCESS) {
            folderAccessMusic()
        } else {
            mediaStoreMusic()
        }

    private suspend fun mediaStoreMusic(): List<MusicTrack> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<MusicTrack>()

        val projection = arrayOf(
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
        )

        try {
            context.contentResolver
                .query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, null, null, null)
                ?.use { cursor ->
                    val dataIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                    val titleIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val albumIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

                    while (cursor.moveToNext()) {
                        val filePath = cursor.getString(dataIndex)
                        if (FileHelper.isSupportedAudioType(filePath)) {
                            val uri = filePath.toUri()
                            if (prefs.filterEnabled && FileHelper.shouldFilter(uri, prefs.filterFolder)) {
                                continue
                            }

                            val title = cursor.getString(titleIndex).takeIf { it.isNotBlank() }
                                ?: FileHelper.stripAudioFileExtension(filePath.substringAfterLast('/'))
                            val artist = cursor.getString(artistIndex).orEmpty()
                            val album = cursor.getString(albumIndex).orEmpty()
                            val duration = cursor.getLong(durationIndex)

                            tracks.add(
                                MusicTrack(
                                    uri = uri,
                                    source = AerialMediaSource.LOCAL,
                                    title = title,
                                    artist = artist,
                                    album = album,
                                    duration = duration,
                                ),
                            )
                        }
                    }
                }
        } catch (ex: Exception) {
            Timber.e(ex, "Exception querying MediaStore for audio files: ${ex.message}")
        }

        Timber.i("LocalMusicProvider: found ${tracks.size} audio tracks")
        return@withContext tracks
    }

    private suspend fun folderAccessMusic(): List<MusicTrack> =
        withContext(Dispatchers.IO) {
            val folders = mutableListOf<String>()
            val found = mutableListOf<File>()

            if (prefs.legacyVolume.isEmpty() || prefs.legacyFolder.isEmpty()) {
                return@withContext emptyList()
            }

            if (prefs.legacyVolume.contains("/all", false)) {
                val vols = StorageHelper.getStoragePaths(context)
                vols.keys.forEach { entry ->
                    folders.add("$entry${prefs.legacyFolder}")
                }
            } else {
                folders.add("${prefs.legacyVolume}${prefs.legacyFolder}")
            }

            for (folder in folders) {
                val directory = File(folder)
                if (!directory.exists() || !directory.isDirectory) {
                    continue
                }

                if (prefs.legacySearchSubfolders) {
                    found.addAll(listAudioFilesRecursively(directory))
                } else {
                    directory.listFiles()?.forEach { file ->
                        if (!file.isDirectory && !FileHelper.isDotOrHiddenFile(file.name)) {
                            found.add(file)
                        }
                    }
                }
            }

            val tracks =
                found
                    .filter { FileHelper.isSupportedAudioType(it.name) }
                    .sortedByDescending { it.lastModified() }
                    .map { file ->
                        MusicTrack(
                            uri = file.toUri(),
                            source = AerialMediaSource.LOCAL,
                            title = FileHelper.stripAudioFileExtension(file.name),
                        )
                    }

            Timber.i("LocalMusicProvider: found ${tracks.size} folder audio tracks")
            return@withContext tracks
        }

    private fun listAudioFilesRecursively(directory: File): List<File> {
        val files = mutableListOf<File>()
        directory.listFiles()?.forEach { file ->
            if (FileHelper.isDotOrHiddenFile(file.name)) {
                return@forEach
            }

            if (file.isDirectory) {
                files.addAll(listAudioFilesRecursively(file))
            } else {
                files.add(file)
            }
        }
        return files
    }
}
