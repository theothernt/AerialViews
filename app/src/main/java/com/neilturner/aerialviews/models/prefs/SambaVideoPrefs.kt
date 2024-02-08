package com.neilturner.aerialviews.models.prefs

import com.chibatching.kotpref.KotprefModel
import com.chibatching.kotpref.enumpref.enumValuePref
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.MediaType

object SambaVideoPrefs : KotprefModel() {
    override val kotprefName = "${context.packageName}_preferences"

    var enabled by booleanPref(false, "samba_videos_enabled")
    var mediaType by enumValuePref(MediaType.VIDEOS, "samba_media_type")
    var hostName by stringPref("", "samba_videos_hostname")
    var domainName by stringPref("WORKGROUP", "samba_videos_domainname")
    var shareName by stringPref("", "samba_videos_sharename")
    var userName by stringPref("", "samba_videos_username")
    var password by stringPref("", "samba_videos_password")
    var searchSubfolders by booleanPref(false, "samba_videos_search_subfolders")
    var enableEncryption by booleanPref(false, "samba_videos_enable_encryption")
    val smbDialects by stringSetPref("samba_videos_smb_dialects") {
        val smbDialects = context.resources.getStringArray(R.array.samba_videos_smb_dialects_default)
        val set = sortedSetOf<String>()
        set.sortedDescending()
        set.addAll(smbDialects)
        set
        // Must be sorted descending to get correct order for use in SmbConfig.withDialects(x,y,z)
    }
}
