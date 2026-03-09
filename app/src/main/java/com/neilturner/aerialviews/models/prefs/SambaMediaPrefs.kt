package com.neilturner.aerialviews.models.prefs

import com.chibatching.kotpref.KotprefModel
import com.chibatching.kotpref.enumpref.nullableEnumValuePref
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.ProviderMediaType

object SambaMediaPrefs : KotprefModel(), SambaProviderPreferences {
    override val kotprefName = "${context.packageName}_preferences"

    override var enabled by booleanPref(false, "samba_videos_enabled")
    override var mediaType by nullableEnumValuePref(ProviderMediaType.VIDEOS_PHOTOS, "samba_media_type")
    override var hostName by stringPref("", "samba_videos_hostname")
    override var domainName by stringPref("WORKGROUP", "samba_videos_domainname")
    override var shareName by stringPref("", "samba_videos_sharename")
    override var userName by stringPref("", "samba_videos_username")
    override var password by stringPref("", "samba_videos_password")
    override var searchSubfolders by booleanPref(false, "samba_videos_search_subfolders")
    override var enableEncryption by booleanPref(false, "samba_videos_enable_encryption")
    override val smbDialects by stringSetPref("samba_videos_smb_dialects") {
        val smbDialects = context.resources.getStringArray(R.array.samba_videos_smb_dialects_default)
        val set = sortedSetOf<String>()
        set.sortedDescending()
        set.addAll(smbDialects)
        set
        // Must be sorted descending to get correct order for use in SmbConfig.withDialects(x,y,z)
    }

    // Wake on LAN
    override var wakeOnLanEnabled by booleanPref(false, "samba_media_wake_on_lan_enabled")
    override var wakeOnLanMacAddress by stringPref("", "samba_media_wake_on_lan_mac_address")
    override var wakeOnLanTimeout by stringPref("120", "samba_media_wake_on_lan_timeout")
}

object SambaMediaPrefs2 : KotprefModel(), SambaProviderPreferences {
    override val kotprefName = "${context.packageName}_preferences"

    private var hostNameRaw by stringPref("", "samba_videos2_hostname")
    private var domainNameRaw by stringPref("", "samba_videos2_domainname")
    private var shareNameRaw by stringPref("", "samba_videos2_sharename")
    private var userNameRaw by stringPref("", "samba_videos2_username")
    private var passwordRaw by stringPref("", "samba_videos2_password")

    override var enabled by booleanPref(false, "samba_videos2_enabled")
    override var mediaType: ProviderMediaType?
        get() = SambaMediaPrefs.mediaType
        set(value) {
            SambaMediaPrefs.mediaType = value
        }
    override var hostName: String
        get() = hostNameRaw.ifBlank { SambaMediaPrefs.hostName }
        set(value) {
            hostNameRaw = value
        }
    override var domainName: String
        get() = domainNameRaw.ifBlank { SambaMediaPrefs.domainName }
        set(value) {
            domainNameRaw = value
        }
    override var shareName: String
        get() = shareNameRaw.ifBlank { SambaMediaPrefs.shareName }
        set(value) {
            shareNameRaw = value
        }
    override var userName: String
        get() = userNameRaw.ifBlank { SambaMediaPrefs.userName }
        set(value) {
            userNameRaw = value
        }
    override var password: String
        get() = passwordRaw.ifBlank { SambaMediaPrefs.password }
        set(value) {
            passwordRaw = value
        }
    override var searchSubfolders: Boolean
        get() = SambaMediaPrefs.searchSubfolders
        set(value) {
            SambaMediaPrefs.searchSubfolders = value
        }
    override var enableEncryption: Boolean
        get() = SambaMediaPrefs.enableEncryption
        set(value) {
            SambaMediaPrefs.enableEncryption = value
        }
    override val smbDialects: Set<String>
        get() = SambaMediaPrefs.smbDialects

    // Wake on LAN is shared across both Samba accounts
    override var wakeOnLanEnabled by booleanPref(false, "samba_media_wake_on_lan_enabled")
    override var wakeOnLanMacAddress by stringPref("", "samba_media_wake_on_lan_mac_address")
    override var wakeOnLanTimeout by stringPref("120", "samba_media_wake_on_lan_timeout")
}
