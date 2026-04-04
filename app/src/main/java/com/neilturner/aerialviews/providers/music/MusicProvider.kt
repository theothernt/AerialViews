package com.neilturner.aerialviews.providers.music

import android.content.Context
import com.neilturner.aerialviews.models.music.MusicTrack

abstract class MusicProvider(
    val context: Context,
) {
    abstract val enabled: Boolean

    abstract suspend fun fetchMusic(): List<MusicTrack>
}
