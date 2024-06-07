package com.neilturner.aerialviews.models.prefs

import com.chibatching.kotpref.KotprefModel
import com.chibatching.kotpref.enumpref.nullableEnumValuePref
import com.neilturner.aerialviews.models.enums.ProviderMediaType
import com.neilturner.aerialviews.models.enums.SearchType

object LocalMediaPrefs : KotprefModel() {
    override val kotprefName = "${context.packageName}_preferences"

    var enabled by booleanPref(false, "local_videos_enabled")
    var searchType by nullableEnumValuePref(SearchType.MEDIA_STORE, "local_videos_search_type")
    var mediaType by nullableEnumValuePref(ProviderMediaType.VIDEOS, "local_media_type")

    var filterEnabled by booleanPref(false, "local_videos_media_store_filter_enabled")
    var filterFolder by stringPref("", "local_videos_media_store_filter_folder")

    var legacyVolumeLabel by stringPref("", "local_videos_legacy_volume_label")
    var legacyVolume by stringPref("", "local_videos_legacy_volume")
    var legacyFolder by stringPref("", "local_videos_legacy_folder")
}
