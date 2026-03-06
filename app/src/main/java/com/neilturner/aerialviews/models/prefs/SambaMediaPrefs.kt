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

    override var enabled by booleanPref(false, "samba_videos2_enabled")
    override var mediaType by nullableEnumValuePref(ProviderMediaType.VIDEOS_PHOTOS, "samba_media2_type")
    override var hostName by stringPref("", "samba_videos2_hostname")
    override var domainName by stringPref("WORKGROUP", "samba_videos2_domainname")
    override var shareName by stringPref("", "samba_videos2_sharename")
    override var userName by stringPref("", "samba_videos2_username")
    override var password by stringPref("", "samba_videos2_password")
    override var searchSubfolders by booleanPref(false, "samba_videos2_search_subfolders")
    override var enableEncryption by booleanPref(false, "samba_videos2_enable_encryption")
    override val smbDialects by stringSetPref("samba_videos2_smb_dialects") {
        val smbDialects = context.resources.getStringArray(R.array.samba_videos_smb_dialects_default)
        val set = sortedSetOf<String>()
        set.sortedDescending()
        set.addAll(smbDialects)
        set
        // Must be sorted descending to get correct order for use in SmbConfig.withDialects(x,y,z)
    }

    // Wake on LAN is shared across both Samba accounts
    override var wakeOnLanEnabled by booleanPref(false, "samba_media_wake_on_lan_enabled")
    override var wakeOnLanMacAddress by stringPref("", "samba_media_wake_on_lan_mac_address")
    override var wakeOnLanTimeout by stringPref("120", "samba_media_wake_on_lan_timeout")
}
