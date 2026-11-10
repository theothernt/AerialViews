package com.neilturner.aerialviews.models.music

import android.net.Uri
import com.neilturner.aerialviews.models.enums.AerialMediaSource

data class MusicTrack(
    val uri: Uri,
    val source: AerialMediaSource = AerialMediaSource.UNKNOWN,
)
