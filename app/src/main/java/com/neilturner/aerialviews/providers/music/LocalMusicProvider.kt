package com.neilturner.aerialviews.providers.music

import android.content.Context
import android.provider.MediaStore
import androidx.core.net.toUri
import com.neilturner.aerialviews.providers.music.MusicProvider
import com.neilturner.aerialviews.models.music.MusicTrack
import com.neilturner.aerialviews.utils.FileHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

class LocalMusicProvider(
    context: Context,
    private val musicEnabled: () -> Boolean,
) : MusicProvider(context) {
    override val enabled: Boolean
        get() = musicEnabled()

    override suspend fun fetchMusic(): List<MusicTrack> = withContext(Dispatchers.IO) {
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
                            val title = cursor.getString(titleIndex).takeIf { it.isNotBlank() }
                                ?: FileHelper.stripAudioFileExtension(filePath.substringAfterLast('/'))
                            val artist = cursor.getString(artistIndex).orEmpty()
                            val album = cursor.getString(albumIndex).orEmpty()
                            val duration = cursor.getLong(durationIndex)

                            tracks.add(
                                MusicTrack(
                                    uri = filePath.toUri(),
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
}
