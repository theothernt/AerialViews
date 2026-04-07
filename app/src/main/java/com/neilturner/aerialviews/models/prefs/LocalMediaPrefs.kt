package com.neilturner.aerialviews.models.prefs

import com.chibatching.kotpref.KotprefModel
import com.chibatching.kotpref.enumpref.nullableEnumValuePref
import com.neilturner.aerialviews.models.enums.SearchType

object LocalMediaPrefs : KotprefModel(), LocalProviderPreferences {
    override val kotprefName = "${context.packageName}_preferences"

    override var enabled by booleanPref(false, "local_videos_enabled")
    override var searchType by nullableEnumValuePref(SearchType.MEDIA_STORE, "local_videos_search_type")
    override val mediaSelection by stringSetPref("local_media_selection") {
        MediaSelection.defaultSelection
    }
    override val mediaType
        get() = MediaSelection.toMediaType(mediaSelection)
    override val musicEnabled: Boolean
        get() = MediaSelection.includesMusic(mediaSelection)
    override val includeVideos: Boolean
        get() = MediaSelection.includesVideos(mediaSelection)
    override val includePhotos: Boolean
        get() = MediaSelection.includesPhotos(mediaSelection)

    override var filterEnabled by booleanPref(false, "local_videos_media_store_filter_enabled")
    override var filterFolder by stringPref("", "local_videos_media_store_filter_folder")

    override var legacyVolumeLabel by stringPref("", "local_videos_legacy_volume_label")
    override var legacyVolume by stringPref("", "local_videos_legacy_volume")
    override var legacyFolder by stringPref("", "local_videos_legacy_folder")
    override var legacySearchSubfolders by booleanPref(false, "local_videos_legacy_search_subfolders")
}
