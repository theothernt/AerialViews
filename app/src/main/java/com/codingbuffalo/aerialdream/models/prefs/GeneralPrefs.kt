package com.codingbuffalo.aerialdream.models.prefs

import com.chibatching.kotpref.KotprefModel

object GeneralPrefs : KotprefModel() {
    //override val kotprefName = "${context.packageName}_preferences"

    var showLocation: Boolean by booleanPref(true)
    var showTime: Boolean by booleanPref(true)
    var muteVideos: Boolean by booleanPref(true)
    var alternateTextPosition: Boolean by booleanPref(false)

    var enableTunneling: Boolean by booleanPref(true)
    var reducedBuffers: Boolean by booleanPref(false)
    var exceedRenderer: Boolean by booleanPref(false)
}