package com.codingbuffalo.aerialdream.models.prefs

import com.chibatching.kotpref.KotprefModel

object GeneralPrefs : KotprefModel() {
    override val kotprefName = "${context.packageName}_preferences"

    var showLocation: Boolean by booleanPref(true, "show_location")
    var showClock: Boolean by booleanPref(true, "show_clock")
    var alternateTextPosition: Boolean by booleanPref(false, "alt_text_position")

    var muteVideos: Boolean by booleanPref(true, "mute_videos")
    var shuffleVideos: Boolean by booleanPref(true, "shuffle_videos")
    var removeDuplicates: Boolean by booleanPref(true, "remove_duplicates")

    var enableTunneling: Boolean by booleanPref(true, "enable_tunneling")
    var reducedBuffers: Boolean by booleanPref(false, "reduced_buffers")
    var exceedRenderer: Boolean by booleanPref(false, "exceed_renderer")
}