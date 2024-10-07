package com.neilturner.aerialviews.models.prefs

import com.chibatching.kotpref.KotprefModel
import com.chibatching.kotpref.enumpref.nullableEnumValuePref
import com.neilturner.aerialviews.models.enums.ImmichAuthType
import com.neilturner.aerialviews.models.enums.ProviderMediaType
import com.neilturner.aerialviews.models.enums.SchemeType

object ImmichMediaPrefs : KotprefModel() {
    override val kotprefName = "${context.packageName}_preferences"

    var enabled by booleanPref(false, "immich_media_enabled")
    var mediaType by nullableEnumValuePref(ProviderMediaType.VIDEOS, "immich_media_type")
    var scheme by nullableEnumValuePref(SchemeType.HTTP, "immich_media_scheme")
    var hostName by stringPref("", "immich_media_hostname")
    var pathName by stringPref("", "immich_media_pathname")
    var password by stringPref("", "immich_media_password")
    var authType by nullableEnumValuePref(ImmichAuthType.SHARED_LINK, "immich_media_auth_type")
    var apiKey by stringPref("", "immich_media_api_key")
    var selectedAlbumId by stringPref("", "immich_media_selected_album_id")
}
