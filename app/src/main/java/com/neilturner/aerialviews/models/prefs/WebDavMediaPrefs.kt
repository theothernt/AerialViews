package com.neilturner.aerialviews.models.prefs

import com.chibatching.kotpref.KotprefModel
import com.chibatching.kotpref.enumpref.nullableEnumValuePref
import com.neilturner.aerialviews.models.enums.ProviderMediaType
import com.neilturner.aerialviews.models.enums.SchemeType

object WebDavMediaPrefs : KotprefModel(), WebDavProviderPreferences {
    override val kotprefName = "${context.packageName}_preferences"

    override var enabled by booleanPref(false, "webdav_media_enabled")
    override var mediaType by nullableEnumValuePref(ProviderMediaType.VIDEOS_PHOTOS, "webdav_media_type")
    override var scheme by nullableEnumValuePref(SchemeType.HTTP, "webdav_media_scheme")
    override var hostName by stringPref("", "webdav_media_hostname")
    override var pathName by stringPref("", "webdav_media_pathname")
    override var userName by stringPref("", "webdav_media_username")
    override var password by stringPref("", "webdav_media_password")
    override var searchSubfolders by booleanPref(false, "webdav_media_search_subfolders")
}

object WebDavMediaPrefs2 : KotprefModel(), WebDavProviderPreferences {
    override val kotprefName = "${context.packageName}_preferences"

    override var enabled by booleanPref(false, "webdav_media2_enabled")
    override var mediaType by nullableEnumValuePref(ProviderMediaType.VIDEOS_PHOTOS, "webdav_media2_type")
    override var scheme by nullableEnumValuePref(SchemeType.HTTP, "webdav_media2_scheme")
    override var hostName by stringPref("", "webdav_media2_hostname")
    override var pathName by stringPref("", "webdav_media2_pathname")
    override var userName by stringPref("", "webdav_media2_username")
    override var password by stringPref("", "webdav_media2_password")
    override var searchSubfolders by booleanPref(false, "webdav_media2_search_subfolders")
}
