package com.neilturner.aerialviews.models.prefs

import com.chibatching.kotpref.KotprefModel
import com.neilturner.aerialviews.R

object ProjectivyPrefs : KotprefModel() {
    override val kotprefName = "${context.packageName}_preferences"

    val sharedProviders by stringSetPref("projectivy_shared_providers") {
        context.resources.getStringArray(R.array.projectivy_shared_providers_default).toSet()
    }
}
