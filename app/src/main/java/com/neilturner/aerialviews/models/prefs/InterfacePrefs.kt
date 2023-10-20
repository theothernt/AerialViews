package com.neilturner.aerialviews.models.prefs

import com.chibatching.kotpref.KotprefModel
import com.chibatching.kotpref.enumpref.enumValuePref
import com.neilturner.aerialviews.models.enums.ClockType
import com.neilturner.aerialviews.models.enums.DateType
import com.neilturner.aerialviews.models.enums.LocationType
import com.neilturner.aerialviews.models.enums.OverlayType

object InterfacePrefs : KotprefModel() {
    override val kotprefName = "${context.packageName}_preferences"

    var clockFormat by enumValuePref(ClockType.DEFAULT, "clock_format")
    var clockSize by stringPref("18", "clock_size")
    var clockForceLatinDigits by booleanPref(false, "clock_force_latin_digits")

    var dateFormat by enumValuePref(DateType.COMPACT, "date_format")
    var dateCustom by stringPref("yyyy-MM-dd", "date_custom")
    var dateSize by stringPref("18", "date_size")

    var locationStyle by enumValuePref(LocationType.POI, "location_style")
    var locationSize by stringPref("18", "location_size")

    var slotBottomLeft1 by enumValuePref(OverlayType.CLOCK, "slot_bottom_left1")
    var slotBottomLeft2 by enumValuePref(OverlayType.EMPTY, "slot_bottom_left2")
    var slotBottomRight1 by enumValuePref(OverlayType.LOCATION, "slot_bottom_right1")
    var slotBottomRight2 by enumValuePref(OverlayType.EMPTY, "slot_bottom_right2")

    var slotTopLeft1 by enumValuePref(OverlayType.EMPTY, "slot_top_left1")
    var slotTopLeft2 by enumValuePref(OverlayType.EMPTY, "slot_top_left2")
    var slotTopRight1 by enumValuePref(OverlayType.EMPTY, "slot_top_right1")
    var slotTopRight2 by enumValuePref(OverlayType.EMPTY, "slot_top_right2")

//    var messageStyle by enumValuePref(MessageType.OFF, "message_style")
//    var messageSize by stringPref("18", "message_size")

    var fontTypeface by stringPref("open-sans", "font_typeface")
    var fontWeight by stringPref("300", "font_weight")

    var alternateTextPosition by booleanPref(false, "alt_text_position")

    var localeMenu by stringPref("default", "locale_menu")
    var localeScreensaver by stringPref("default", "locale_screensaver")
}
