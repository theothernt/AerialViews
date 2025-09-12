package com.neilturner.aerialviews.models.prefs

import com.chibatching.kotpref.KotprefModel
import com.chibatching.kotpref.enumpref.nullableEnumValuePref
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.VideoQuality

object CustomMediaPrefs : KotprefModel() {
    override val kotprefName = "${context.packageName}_preferences"

    var enabled by booleanPref(true, "custom_media_enabled")
    var quality by nullableEnumValuePref(VideoQuality.VIDEO_1080_SDR, "custom_media_quality")

    val scene by stringSetPref("custom_media_scene_type") {
        context.resources.getStringArray(R.array.video_scene_type_default).toSet()
    }

    val timeOfDay by stringSetPref("custom_media_time_of_day") {
        context.resources.getStringArray(R.array.video_time_of_day_default).toSet()
    }
}
