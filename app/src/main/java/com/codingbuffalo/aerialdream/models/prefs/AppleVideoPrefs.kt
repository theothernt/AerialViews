package com.codingbuffalo.aerialdream.models.prefs

import com.chibatching.kotpref.KotprefModel
import com.chibatching.kotpref.enumpref.enumValuePref
import com.codingbuffalo.aerialdream.models.AppleVideoQuality
import com.codingbuffalo.aerialdream.models.AppleVideoLocation

object AppleVideoPrefs : KotprefModel() {
    override val kotprefName = "${context.packageName}_preferences"

    var enabled by booleanPref(true, "apple_videos_enabled")
    var quality by enumValuePref(AppleVideoQuality.VIDEO_1080_SDR, "apple_videos_quality")
    var location by enumValuePref(AppleVideoLocation.REMOTE, "apple_videos_location")
}