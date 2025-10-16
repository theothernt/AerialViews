package com.neilturner.aerialviews.models.prefs

import com.chibatching.kotpref.KotprefModel
import com.chibatching.kotpref.enumpref.nullableEnumValuePref
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.VideoQuality

object AmazonVideoPrefs : KotprefModel() {
    override val kotprefName = "${context.packageName}_preferences"

    var enabled by booleanPref(true, "amazon_videos_enabled")
    var quality by nullableEnumValuePref(VideoQuality.VIDEO_1080_SDR, "amazon_videos_quality")

    val scene by stringSetPref("amazon_videos_scene_type") {
        context.resources.getStringArray(R.array.amazon_video_scene_type_default).toSet()
    }

    val timeOfDay by stringSetPref("amazon_videos_time_of_day") {
        context.resources.getStringArray(R.array.video_time_of_day_default).toSet()
    }
}
