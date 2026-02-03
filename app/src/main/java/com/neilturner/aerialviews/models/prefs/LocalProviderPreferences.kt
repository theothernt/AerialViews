package com.neilturner.aerialviews.models.prefs

import com.neilturner.aerialviews.models.enums.ProviderMediaType
import com.neilturner.aerialviews.models.enums.SearchType

interface LocalProviderPreferences {
    val enabled: Boolean
    var searchType: SearchType?
    var mediaType: ProviderMediaType?

    var filterEnabled: Boolean
    var filterFolder: String

    var legacyVolumeLabel: String
    var legacyVolume: String
    var legacyFolder: String
    var legacySearchSubfolders: Boolean
}
