package com.neilturner.aerialviews.models.videos

import android.net.Uri
import com.neilturner.aerialviews.models.enums.AerialMediaSource
import com.neilturner.aerialviews.models.enums.AerialMediaType
import com.neilturner.aerialviews.models.enums.SceneType
import com.neilturner.aerialviews.models.enums.TimeOfDay

data class AerialMedia(
    val uri: Uri,
    var description: String = "",
    var poi: Map<Int, String> = emptyMap(),
    var type: AerialMediaType = AerialMediaType.VIDEO,
    var source: AerialMediaSource = AerialMediaSource.UNKNOWN,
    var timeOfDay: TimeOfDay = TimeOfDay.UNKNOWN,
    var scene: SceneType = SceneType.UNKNOWN
)
