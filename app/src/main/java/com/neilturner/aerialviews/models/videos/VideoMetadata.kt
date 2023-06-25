package com.neilturner.aerialviews.models.videos

import android.net.Uri

data class VideoMetadata(val uri: List<Uri>, val Location: String, val poi: Map<Int, String> = emptyMap())
