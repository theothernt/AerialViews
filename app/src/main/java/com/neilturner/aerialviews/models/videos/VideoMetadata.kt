package com.neilturner.aerialviews.models.videos

data class VideoMetadata(
    val urls: List<String>,
    val description: String,
    val poi: Map<Int, String> = emptyMap(),
)
