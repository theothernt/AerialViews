package com.codingbuffalo.aerialdream.models.prefs

import com.chibatching.kotpref.KotprefModel
import com.chibatching.kotpref.enumpref.enumValuePref
import com.codingbuffalo.aerialdream.models.AppleVideoQuality
import com.codingbuffalo.aerialdream.models.AppleVideoSource

object AppleVideoPrefs : KotprefModel() {
    var enabled: Boolean by booleanPref(true)
    var quality by enumValuePref(AppleVideoQuality.VIDEO_1080_SDR)
    var source by enumValuePref(AppleVideoSource.REMOTE)
}