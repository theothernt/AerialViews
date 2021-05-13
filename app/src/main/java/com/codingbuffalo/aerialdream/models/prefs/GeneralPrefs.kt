package com.codingbuffalo.aerialdream.models.prefs

import com.chibatching.kotpref.KotprefModel

object GeneralPrefs : KotprefModel() {
    override val kotprefName = "${context.packageName}_preferences"

    var shuffleVideos: Boolean by booleanPref(true, "shuffle_videos")
    var showLocation: Boolean by booleanPref(true, "show_location")
    var showTime: Boolean by booleanPref(true, "show_time")
    var muteVideos: Boolean by booleanPref(true, "mute_videos")
    var alternateTextPosition: Boolean by booleanPref(false, "alt_text_position")

    var enableTunneling: Boolean by booleanPref(true, "enable_tunneling")
    var reducedBuffers: Boolean by booleanPref(false, "reduced_buffers")
    var exceedRenderer: Boolean by booleanPref(false, "exceed_renderer")
}