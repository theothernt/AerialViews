package com.neilturner.aerialviews.models.prefs

import com.chibatching.kotpref.KotprefModel

object GeneralPrefs : KotprefModel() {
    override val kotprefName = "${context.packageName}_preferences"

    var muteVideos by booleanPref(true, "mute_videos")
    var shuffleVideos by booleanPref(true, "shuffle_videos")
    var removeDuplicates by booleanPref(true, "remove_duplicates")
    var enableSkipVideos by booleanPref(true, "enable_skip_videos")
    var playbackSpeed by stringPref("1", "playback_speed")

    var enableTunneling by booleanPref(true, "enable_tunneling")
    var exceedRenderer by booleanPref(false, "exceed_renderer")
    var bufferingStrategy by stringPref("DEFAULT", "performance_buffering_strategy")

    var filenameAsLocation by booleanPref(true, "any_videos_filename_location")
    var useAppleManifests by booleanPref(true, "any_videos_use_apple_manifests")
    var useCustomManifests by booleanPref(true, "any_videos_use_custom_manifests")
    var ignoreNonManifestVideos by booleanPref(false, "any_videos_ignore_non_manifest_videos")
}
