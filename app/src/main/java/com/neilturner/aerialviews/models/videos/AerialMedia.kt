package com.neilturner.aerialviews.models.videos

import android.net.Uri
import com.neilturner.aerialviews.models.enums.AerialMediaSource
import com.neilturner.aerialviews.models.enums.AerialMediaType

data class AerialMedia(
    val uri: Uri,
    var description: String = "",
    var poi: Map<Int, String> = emptyMap(),
    var type: AerialMediaType = AerialMediaType.VIDEO,
    var source: AerialMediaSource = AerialMediaSource.DEFAULT
)
