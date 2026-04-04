package com.neilturner.aerialviews.models.prefs

import com.chibatching.kotpref.KotprefModel
import com.chibatching.kotpref.enumpref.nullableEnumValuePref
import com.neilturner.aerialviews.models.enums.MusicSourceType

object MusicPrefs : KotprefModel() {
    override val kotprefName = "${context.packageName}_preferences"

    var shuffle by booleanPref(true, "music_shuffle")
    var repeat by booleanPref(false, "music_repeat")
}
