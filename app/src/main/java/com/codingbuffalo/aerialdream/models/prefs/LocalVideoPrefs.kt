package com.codingbuffalo.aerialdream.models.prefs

import com.chibatching.kotpref.KotprefModel
import com.chibatching.kotpref.enumpref.enumValuePref
import com.codingbuffalo.aerialdream.models.LocalVideoFilter

object LocalVideoPrefs : KotprefModel() {
    override val kotprefName = "${context.packageName}_preferences"

    var enabled: Boolean by booleanPref(true, "local_videos_enabled")
    var filter by enumValuePref(LocalVideoFilter.ALL, "local_videos_filter")
}