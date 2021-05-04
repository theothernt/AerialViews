package com.codingbuffalo.aerialdream.models.prefs

import com.chibatching.kotpref.KotprefModel
import com.chibatching.kotpref.enumpref.enumValuePref
import com.codingbuffalo.aerialdream.models.VideoQuality
import com.codingbuffalo.aerialdream.models.VideoSource

object Apple2019Prefs : KotprefModel() {
    //override val kotprefName = "${context.packageName}_preferences"

    var quality by enumValuePref(VideoQuality.VIDEO_1080_SDR)
    var source by enumValuePref(VideoSource.REMOTE)

    var useLocation: Boolean by booleanPref(key = "show_location")

}