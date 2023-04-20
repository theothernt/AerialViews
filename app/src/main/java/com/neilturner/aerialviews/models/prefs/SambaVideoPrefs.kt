package com.neilturner.aerialviews.models.prefs

import com.chibatching.kotpref.KotprefModel
import com.neilturner.aerialviews.R
import java.util.TreeSet

object SambaVideoPrefs : KotprefModel() {
    override val kotprefName = "${context.packageName}_preferences"

    var enabled by booleanPref(false, "samba_videos_enabled")
    var userName by stringPref("", "samba_videos_username")
    var password by stringPref("", "samba_videos_password")
    var hostName by stringPref("", "samba_videos_hostname")
    var shareName by stringPref("", "samba_videos_sharename")
    var domainName by stringPref("WORKGROUP", "samba_videos_domainname")

    var enableEncryption by booleanPref(false, "samba_videos_enable_encryption")

    val smbDialects by stringSetPref("samba_videos_smb_dialects") {
        val smbDialects = context.resources.getStringArray(R.array.samba_videos_smb_dialects_default)
        val set = TreeSet<String>()
        set.addAll(smbDialects)
        set
        // Must be sorted to get correct order for use in SmbConfig.withDialects(x,y,z)
        // prefs.smbDialects.sortedByDescending { it }
        // SMB2Dialect.valueOf(it)
    }
}
