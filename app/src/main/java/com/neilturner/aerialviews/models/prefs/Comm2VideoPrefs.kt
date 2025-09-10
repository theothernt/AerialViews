package com.neilturner.aerialviews.models.prefs

import com.chibatching.kotpref.KotprefModel
import com.chibatching.kotpref.enumpref.nullableEnumValuePref
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.VideoQuality

object Comm2VideoPrefs : KotprefModel() {
    override val kotprefName = "${context.packageName}_preferences"

    var enabled by booleanPref(true, "comm2_videos_enabled")
    var quality by nullableEnumValuePref(VideoQuality.VIDEO_1080_SDR, "comm2_videos_quality")

    val scene by stringSetPref("comm2_videos_scene_type") {
        context.resources.getStringArray(R.array.video_scene_type_default).toSet()
    }

    val timeOfDay by stringSetPref("comm2_videos_time_of_day") {
        context.resources.getStringArray(R.array.video_time_of_day_default).toSet()
    }
}
