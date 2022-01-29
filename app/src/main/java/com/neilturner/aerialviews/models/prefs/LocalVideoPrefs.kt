package com.neilturner.aerialviews.models.prefs

import com.chibatching.kotpref.KotprefModel

object LocalVideoPrefs : KotprefModel() {
    override val kotprefName = "${context.packageName}_preferences"

    var enabled by booleanPref(false, "local_videos_enabled")
}
