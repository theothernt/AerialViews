package com.neilturner.aerialviews.models.prefs

import com.neilturner.aerialviews.models.enums.ProviderMediaType
import com.neilturner.aerialviews.models.enums.SearchType

interface LocalProviderPreferences {
    val enabled: Boolean
    var searchType: SearchType?
    val mediaSelection: Set<String>
    val mediaType: ProviderMediaType?
    val musicEnabled: Boolean
    val includeVideos: Boolean
    val includePhotos: Boolean

    var filterEnabled: Boolean
    var filterFolder: String

    var legacyVolumeLabel: String
    var legacyVolume: String
    var legacyFolder: String
    var legacySearchSubfolders: Boolean
}
