package com.neilturner.aerialviews.models.music

import android.net.Uri

data class MusicTrack(
    val uri: Uri,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val duration: Long = 0L,
)
