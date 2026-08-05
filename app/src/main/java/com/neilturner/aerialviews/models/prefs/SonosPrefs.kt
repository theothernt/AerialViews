package com.neilturner.aerialviews.models.prefs

import com.chibatching.kotpref.KotprefModel

object SonosPrefs : KotprefModel() {
    override val kotprefName = "${context.packageName}_preferences"

    var enabled by booleanPref(false, "sonos_enabled")
    var ipAddress by stringPref("", "sonos_ip_address")
    var pollInterval by intPref(5, "sonos_poll_interval")
}
