package com.neilturner.aerialviews.models.prefs

import com.chibatching.kotpref.KotprefModel
import com.chibatching.kotpref.enumpref.nullableEnumValuePref
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.VideoQuality

object AmazonVideoPrefs : KotprefModel(), ProviderPreferences {
    override val kotprefName = "${context.packageName}_preferences"

    override var enabled by booleanPref(true, "amazon_videos_enabled")
    override var quality by nullableEnumValuePref(VideoQuality.VIDEO_1080_SDR, "amazon_videos_quality")
    var count by stringPref("-1", "amazon_videos_count")

    override val scene by stringSetPref("amazon_videos_scene_type") {
        context.resources.getStringArray(R.array.amazon_video_scene_type_default).toSet()
    }

    override val timeOfDay by stringSetPref("amazon_videos_time_of_day") {
        context.resources.getStringArray(R.array.video_time_of_day_default).toSet()
    }
}
