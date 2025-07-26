package com.neilturner.aerialviews.models.prefs

import com.chibatching.kotpref.KotprefModel
import com.chibatching.kotpref.enumpref.nullableEnumValuePref
import com.neilturner.aerialviews.models.enums.ImmichAuthType
import com.neilturner.aerialviews.models.enums.ProviderMediaType

object ImmichMediaPrefs : KotprefModel() {
    override val kotprefName = "${context.packageName}_preferences"

    var enabled by booleanPref(false, "immich_media_enabled")
    var mediaType by nullableEnumValuePref(ProviderMediaType.VIDEOS_PHOTOS, "immich_media_type")
    var pathName by stringPref("", "immich_media_pathname")
    var password by stringPref("", "immich_media_password")
    var url by stringPref("", "immich_media_url")
    var validateSsl by booleanPref(true, "immich_media_validate_ssl")
    var authType by nullableEnumValuePref(ImmichAuthType.SHARED_LINK, "immich_media_auth_type")
    var apiKey by stringPref("", "immich_media_api_key")
    val selectedAlbumIds by stringSetPref(emptySet(), "immich_media_selected_album_ids")
    var includeFavorites by booleanPref(false, "immich_media_include_favorites")
    val includeRatings by stringSetPref(emptySet(), "immich_media_include_ratings")
    var randomLimit by stringPref("DISABLED", "immich_media_random_limit")
}
