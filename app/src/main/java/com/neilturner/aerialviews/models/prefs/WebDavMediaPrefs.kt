package com.neilturner.aerialviews.models.prefs

import com.chibatching.kotpref.KotprefModel
import com.chibatching.kotpref.enumpref.nullableEnumValuePref
import com.neilturner.aerialviews.models.enums.ProviderMediaType

object WebDavMediaPrefs : KotprefModel() {
    override val kotprefName = "${context.packageName}_preferences"

    var enabled by booleanPref(false, "webdav_media_enabled")
    var mediaType by nullableEnumValuePref(ProviderMediaType.VIDEOS, "webdav_media_type")
    var scheme by stringPref("", "webdav_media_scheme")
    var hostName by stringPref("", "webdav_media_hostname")
    var pathName by stringPref("", "webdav_media_pathname")
    var port by stringPref("", "webdav_media_port")
    var userName by stringPref("", "webdav_media_username")
    var password by stringPref("", "webdav_media_password")
    var searchSubfolders by booleanPref(false, "webdav_media_search_subfolders")
}
