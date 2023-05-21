package com.neilturner.aerialviews.models.prefs

import com.chibatching.kotpref.KotprefModel
import com.chibatching.kotpref.enumpref.enumValuePref
import com.neilturner.aerialviews.models.SearchType

object LocalVideoPrefs : KotprefModel() {
    override val kotprefName = "${context.packageName}_preferences"

    var enabled by booleanPref(false, "local_videos_enabled")
    var searchType by enumValuePref(SearchType.MEDIASTORE, "local_video_search_type")

    var filter_enabled by booleanPref(false, "local_videos_filter_enabled")
    var filter_folder_name by stringPref("", "local_videos_filter_folder_name")

    var legacy_volume by stringPref("", "local_videos_legacy_volume")
    var legacy_folder by stringPref("", "local_videos_legacy_folder")
}
