package com.neilturner.aerialviews.models.prefs

import com.chibatching.kotpref.KotprefModel
import com.chibatching.kotpref.enumpref.enumValuePref
import com.neilturner.aerialviews.models.LocationInformationStyle

object UITextPrefs  : KotprefModel() {

    override val kotprefName = "${context.packageName}_preferences"

    var showClock by booleanPref(true, "show_clock")
    var showLocation by booleanPref(true, "show_location")
    var locationInfoStyle by enumValuePref(LocationInformationStyle.SHORT_LOCATION, "location_information_style")
    var alternateTextPosition by booleanPref(false, "alt_text_position")
}