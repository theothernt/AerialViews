package com.neilturner.aerialviews.models.prefs

import com.chibatching.kotpref.KotprefModel

object NetworkVideoPrefs : KotprefModel() {
    override val kotprefName = "${context.packageName}_preferences"

    var enabled by booleanPref(false, "network_videos_enabled")
    var userName by stringPref("", "network_videos_username")
    var password by stringPref("", "network_videos_password")
    var hostName by stringPref("", "network_videos_hostname")
    var shareName by stringPref("", "network_videos_sharename")
    var domainName by stringPref("WORKGROUP", "network_videos_domainname")
}
