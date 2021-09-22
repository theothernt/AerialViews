package com.neilturner.aerialviews.models.prefs

import com.chibatching.kotpref.KotprefModel
import com.chibatching.kotpref.enumpref.enumValuePref
import com.neilturner.aerialviews.models.LocalVideoFilter

object AnyVideoPrefs : KotprefModel() {
    override val kotprefName = "${context.packageName}_preferences"

    var enabled by booleanPref(false, "local_videos_enabled")
    var filter by enumValuePref(LocalVideoFilter.ALL, "any_videos_filter")
    var filenameAsLocation by booleanPref(true, "any_videos_filename_location")

    var useAppleManifests by booleanPref(true, "any_videos_use_apple_manifests")
    var useCustomManifests by booleanPref(true, "any_videos_use_custom_manifests")
    var ignoreNonManifestVideos by booleanPref(true, "any_videos_ignore_non_manifest_videos")


}