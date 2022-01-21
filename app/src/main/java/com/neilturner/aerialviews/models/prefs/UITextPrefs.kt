package com.neilturner.aerialviews.models.prefs

import com.chibatching.kotpref.KotprefModel

object UITextPrefs  : KotprefModel() {

    override val kotprefName = "${context.packageName}_preferences"

    var showLocation by booleanPref(true, "show_location")
    var showClock by booleanPref(true, "show_clock")
    var usePoiText by booleanPref(true, "use_poi_as_location")
    var alternateTextPosition by booleanPref(false, "alt_text_position")
}