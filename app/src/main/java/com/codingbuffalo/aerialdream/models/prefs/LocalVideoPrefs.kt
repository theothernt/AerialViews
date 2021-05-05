package com.codingbuffalo.aerialdream.models.prefs

import com.chibatching.kotpref.KotprefModel
import com.chibatching.kotpref.enumpref.enumValuePref
import com.codingbuffalo.aerialdream.models.LocalVideoFilter

object LocalVideoPrefs : KotprefModel() {
    var enabled: Boolean by booleanPref(true)
    var filter by enumValuePref(LocalVideoFilter.ALL)
}