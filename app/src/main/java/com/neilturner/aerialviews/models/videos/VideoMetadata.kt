package com.neilturner.aerialviews.models.videos

data class VideoMetadata(val uri: List<String>, val Location: String, val poi: Map<Int, String> = emptyMap())
