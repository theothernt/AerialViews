package com.neilturner.aerialviews.models

import com.neilturner.aerialviews.models.music.MusicPlaylist

data class MediaFetchResult(
    val mediaPlaylist: MediaPlaylist,
    val musicPlaylist: MusicPlaylist?,
)
