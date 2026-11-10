package com.neilturner.aerialviews.models.prefs

import com.neilturner.aerialviews.models.enums.ProviderMediaType

object MediaSelection {
    const val VIDEOS = "VIDEOS"
    const val PHOTOS = "PHOTOS"
    const val MUSIC = "MUSIC"

    val defaultSelection = setOf(VIDEOS, PHOTOS)

    fun includesVideos(selection: Set<String>): Boolean = selection.contains(VIDEOS)

    fun includesPhotos(selection: Set<String>): Boolean = selection.contains(PHOTOS)

    fun includesMusic(selection: Set<String>): Boolean = selection.contains(MUSIC)

    fun toMediaType(selection: Set<String>): ProviderMediaType? =
        when {
            includesVideos(selection) && includesPhotos(selection) -> ProviderMediaType.VIDEOS_PHOTOS
            includesVideos(selection) -> ProviderMediaType.VIDEOS
            includesPhotos(selection) -> ProviderMediaType.PHOTOS
            else -> null
        }
}
