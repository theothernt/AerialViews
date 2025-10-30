package com.neilturner.aerialviews.models.prefs

import com.chibatching.kotpref.KotprefModel
import com.neilturner.aerialviews.R

object ProjectivyPrefs : KotprefModel() {
    override val kotprefName = "${context.packageName}_preferences"

    // Projectivy sharing
    val sharedProviders by stringSetPref("projectivy_share_videos") {
        context.resources.getStringArray(R.array.projectivy_share_videos_default).toSet()
    }
}
