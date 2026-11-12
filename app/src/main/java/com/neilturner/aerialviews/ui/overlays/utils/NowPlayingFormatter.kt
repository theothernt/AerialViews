package com.neilturner.aerialviews.ui.overlays.utils

import com.neilturner.aerialviews.models.enums.NowPlayingFormat

/**
 * Track line text, shared by the XML and Compose now playing overlays.
 */
object NowPlayingFormatter {
    private const val SEPARATOR = " · "

    fun format(
        artist: String,
        song: String,
        format: NowPlayingFormat?,
        shortenTrackName: Boolean,
    ): String {
        val processedSong = if (shortenTrackName) TrackNameShortener.shortenTrackName(song) else song

        return when (format) {
            NowPlayingFormat.SONG_ARTIST -> {
                if (processedSong.isNotBlank() && artist.isNotBlank()) {
                    "$processedSong$SEPARATOR$artist"
                } else {
                    processedSong.ifBlank { artist }
                }
            }

            NowPlayingFormat.ARTIST_SONG -> {
                if (artist.isNotBlank() && processedSong.isNotBlank()) {
                    "$artist$SEPARATOR$processedSong"
                } else {
                    artist.ifBlank { processedSong }
                }
            }

            NowPlayingFormat.ARTIST -> {
                artist
            }

            NowPlayingFormat.SONG -> {
                processedSong
            }

            else -> {
                ""
            }
        }
    }
}
