package com.neilturner.aerialviews.models.prefs

import com.chibatching.kotpref.KotprefModel
import com.chibatching.kotpref.enumpref.enumValuePref
import com.neilturner.aerialviews.models.LocationType

object InterfacePrefs : KotprefModel() {

    override val kotprefName = "${context.packageName}_preferences"

    var clockStyle by booleanPref(true, "show_clock")
    var clockSize by stringPref("36", "clock_size")
    var clockForceLatinDigits by booleanPref(false, "clock_force_latin_digits")

    var locationStyle by enumValuePref(LocationType.POI, "location_style")
    var locationSize by stringPref("18", "location_size")

    var alternateTextPosition by booleanPref(false, "alt_text_position")

    var localeMenu by stringPref("default", "locale_menu")
    var localeScreensaver by stringPref("default", "locale_screensaver")
}
