package com.neilturner.aerialviews.models.prefs

import com.chibatching.kotpref.KotprefModel

object AnyVideoPrefs : KotprefModel() {
    override val kotprefName = "${context.packageName}_preferences"

    var enabled by booleanPref(false, "local_videos_enabled")
    var filenameAsLocation by booleanPref(true, "any_videos_filename_location")

    var useAppleManifests by booleanPref(true, "any_videos_use_apple_manifests")
    var useCustomManifests by booleanPref(true, "any_videos_use_custom_manifests")
    var ignoreNonManifestVideos by booleanPref(false, "any_videos_ignore_non_manifest_videos")
}