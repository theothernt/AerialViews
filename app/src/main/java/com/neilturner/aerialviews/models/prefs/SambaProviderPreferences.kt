package com.neilturner.aerialviews.models.prefs

import com.neilturner.aerialviews.models.enums.ProviderMediaType

interface SambaProviderPreferences {
    var enabled: Boolean
    var mediaType: ProviderMediaType?
    var hostName: String
    var domainName: String
    var shareName: String
    var userName: String
    var password: String
    var searchSubfolders: Boolean
    var enableEncryption: Boolean
    val smbDialects: Set<String>

    // Shared Wake on LAN settings
    var wakeOnLanEnabled: Boolean
    var wakeOnLanMacAddress: String
    var wakeOnLanTimeout: String
}
