package com.neilturner.aerialviews.models.prefs

import com.chibatching.kotpref.KotprefModel
import com.chibatching.kotpref.enumpref.enumValuePref
import com.neilturner.aerialviews.models.LocationType
import com.neilturner.aerialviews.models.OverlayType

object InterfacePrefs : KotprefModel() {

    override val kotprefName = "${context.packageName}_preferences"

    var clockStyle by booleanPref(true, "show_clock")
    var clockSize by stringPref("36", "clock_size")
    var clockForceLatinDigits by booleanPref(false, "clock_force_latin_digits")

    var locationStyle by enumValuePref(LocationType.POI, "location_style")
    var locationSize by stringPref("18", "location_size")

    var slotBottomLeft1 by enumValuePref(OverlayType.EMPTY, "slot_bottom_left1")
    var slotBottomLeft2 by enumValuePref(OverlayType.EMPTY, "slot_bottom_left2")

    var slotBottomRight1 by enumValuePref(OverlayType.EMPTY, "slot_bottom_right1")
    var slotBottomRight2 by enumValuePref(OverlayType.EMPTY, "slot_bottom_right2")

//    var messageStyle by enumValuePref(MessageType.OFF, "message_style")
//    var messageSize by stringPref("18", "message_size")
//    var messageLine1 by stringPref("", "message_line1")
//    var messageLine2 by stringPref("", "message_line2")

    var fontTypeface by stringPref("open-sans", "font_typeface")
    var fontWeight by stringPref("300", "font_weight")

    var alternateTextPosition by booleanPref(false, "alt_text_position")

    var localeMenu by stringPref("default", "locale_menu")
    var localeScreensaver by stringPref("default", "locale_screensaver")
}
