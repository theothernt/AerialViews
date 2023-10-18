package com.neilturner.aerialviews.models.prefs

import com.chibatching.kotpref.KotprefModel
import com.chibatching.kotpref.enumpref.enumValuePref
import com.neilturner.aerialviews.models.enums.FilenameAsLocation

object GeneralPrefs : KotprefModel() {
    override val kotprefName = "${context.packageName}_preferences"

    var checkForHevcSupport by booleanPref(false, "check_for_hevc_support")

    var muteVideos by booleanPref(true, "mute_videos")
    var shuffleVideos by booleanPref(true, "shuffle_videos")
    var removeDuplicates by booleanPref(true, "remove_duplicates")
    var enableSkipVideos by booleanPref(true, "enable_skip_videos")
    var enablePlaybackSpeedChange by booleanPref(false, "enable_playback_speed_change")
    var playbackSpeed by stringPref("1", "playback_speed")
    var maxVideoLength by stringPref("0", "playback_max_video_length")

    var enableTunneling by booleanPref(true, "enable_tunneling")

    // var bufferingStrategy by stringPref("DEFAULT", "performance_buffering_strategy")
    var refreshRateSwitching by booleanPref(false, "refresh_rate_switching")
    var philipsDolbyVisionFix by booleanPref(false, "philips_dolby_vision_fix")

    // var filenameAsLocation by booleanPref(true, "filename_as_location")
    var filenameAsLocation by enumValuePref(FilenameAsLocation.DISABLED, "filename_as_location")

    // var useAppleManifests by booleanPref(true, "any_videos_use_apple_manifests")
    // var useCustomManifests by booleanPref(true, "any_videos_use_custom_manifests")
    var ignoreNonManifestVideos by booleanPref(false, "any_videos_ignore_non_manifest_videos")
}
