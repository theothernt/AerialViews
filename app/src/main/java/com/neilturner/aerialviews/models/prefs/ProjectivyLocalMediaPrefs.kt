package com.neilturner.aerialviews.models.prefs

import com.chibatching.kotpref.KotprefModel
import com.chibatching.kotpref.enumpref.nullableEnumValuePref
import com.neilturner.aerialviews.models.enums.ProviderMediaType
import com.neilturner.aerialviews.models.enums.SearchType

object ProjectivyLocalMediaPrefs : KotprefModel(), LocalMediaProviderPreferences {
    override val kotprefName = "${context.packageName}_preferences"

    override val enabled: Boolean
        get() = ProjectivyPrefs.sharedProviders.contains("LOCAL")

    override var searchType by nullableEnumValuePref(SearchType.MEDIA_STORE, "projectivy_local_videos_search_type")
    override var mediaType by nullableEnumValuePref(ProviderMediaType.VIDEOS, "projectivy_local_media_type")

    override var filterEnabled by booleanPref(false, "projectivy_local_videos_media_store_filter_enabled")
    override var filterFolder by stringPref("", "projectivy_local_videos_media_store_filter_folder")

    override var legacyVolumeLabel by stringPref("", "projectivy_local_videos_legacy_volume_label")
    override var legacyVolume by stringPref("", "projectivy_local_videos_legacy_volume")
    override var legacyFolder by stringPref("", "projectivy_local_videos_legacy_folder")
    override var legacySearchSubfolders by booleanPref(false, "projectivy_local_videos_legacy_search_subfolders")
}
