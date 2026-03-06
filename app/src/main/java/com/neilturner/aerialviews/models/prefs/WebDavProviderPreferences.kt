package com.neilturner.aerialviews.models.prefs

import com.neilturner.aerialviews.models.enums.ProviderMediaType
import com.neilturner.aerialviews.models.enums.SchemeType

interface WebDavProviderPreferences {
    var enabled: Boolean
    var mediaType: ProviderMediaType?
    var scheme: SchemeType?
    var hostName: String
    var pathName: String
    var userName: String
    var password: String
    var searchSubfolders: Boolean
}
