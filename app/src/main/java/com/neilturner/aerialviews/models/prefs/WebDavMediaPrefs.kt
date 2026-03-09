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

    private var schemeRaw by stringPref("", "webdav_media2_scheme")
    private var hostNameRaw by stringPref("", "webdav_media2_hostname")
    private var pathNameRaw by stringPref("", "webdav_media2_pathname")
    private var userNameRaw by stringPref("", "webdav_media2_username")
    private var passwordRaw by stringPref("", "webdav_media2_password")

    override var enabled by booleanPref(false, "webdav_media2_enabled")
    override var mediaType: ProviderMediaType?
        get() = WebDavMediaPrefs.mediaType
        set(value) {
            WebDavMediaPrefs.mediaType = value
        }
    override var scheme: SchemeType?
        get() =
            schemeRaw
                .takeIf { it.isNotBlank() }
                ?.let { runCatching { SchemeType.valueOf(it) }.getOrNull() }
                ?: WebDavMediaPrefs.scheme
        set(value) {
            schemeRaw = value?.name.orEmpty()
        }
    override var hostName: String
        get() = hostNameRaw.ifBlank { WebDavMediaPrefs.hostName }
        set(value) {
            hostNameRaw = value
        }
    override var pathName: String
        get() = pathNameRaw.ifBlank { WebDavMediaPrefs.pathName }
        set(value) {
            pathNameRaw = value
        }
    override var userName: String
        get() = userNameRaw.ifBlank { WebDavMediaPrefs.userName }
        set(value) {
            userNameRaw = value
        }
    override var password: String
        get() = passwordRaw.ifBlank { WebDavMediaPrefs.password }
        set(value) {
            passwordRaw = value
        }
    override var searchSubfolders: Boolean
        get() = WebDavMediaPrefs.searchSubfolders
        set(value) {
            WebDavMediaPrefs.searchSubfolders = value
        }
}
