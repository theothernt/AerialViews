package com.neilturner.aerialviews.models.music

import android.net.Uri
import com.neilturner.aerialviews.models.enums.AerialMediaSource

data class MusicTrack(
    val uri: Uri,
    val source: AerialMediaSource = AerialMediaSource.UNKNOWN,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val duration: Long = 0L,
)
