package com.neilturner.aerialviews.models.prefs

import com.chibatching.kotpref.KotprefModel
import com.chibatching.kotpref.enumpref.enumValuePref
import com.neilturner.aerialviews.models.enums.VideoQuality

object Comm1VideoPrefs : KotprefModel() {
    override val kotprefName = "${context.packageName}_preferences"

    var enabled by booleanPref(true, "comm1_videos_enabled")
    var quality by enumValuePref(VideoQuality.VIDEO_1080_SDR, "comm1_videos_quality")
}
