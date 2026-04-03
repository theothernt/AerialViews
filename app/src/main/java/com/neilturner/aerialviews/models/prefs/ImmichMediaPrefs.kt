package com.neilturner.aerialviews.models.prefs

import com.chibatching.kotpref.KotprefModel
import com.chibatching.kotpref.enumpref.nullableEnumValuePref
import com.neilturner.aerialviews.models.enums.ImmichAuthType
import com.neilturner.aerialviews.models.enums.ImmichImageType
import com.neilturner.aerialviews.models.enums.ImmichVideoType
import com.neilturner.aerialviews.models.enums.ProviderMediaType

interface ImmichUrlPrefs {
    val pathName: String
    val password: String
    val authType: ImmichAuthType?
    val imageType: ImmichImageType?
    val videoType: ImmichVideoType?
}

object ImmichMediaPrefs : KotprefModel(), ImmichUrlPrefs {
    override val kotprefName = "${context.packageName}_preferences"

    var enabled by booleanPref(false, "immich_media_enabled")
    var mediaType by nullableEnumValuePref(ProviderMediaType.VIDEOS_PHOTOS, "immich_media_type")
    override var pathName by stringPref("", "immich_media_pathname")
    override var password by stringPref("", "immich_media_password")
    var url by stringPref("", "immich_media_url")
    var validateSsl by booleanPref(true, "immich_media_validate_ssl")
    override var authType by nullableEnumValuePref(ImmichAuthType.SHARED_LINK, "immich_media_auth_type")
    var apiKey by stringPref("", "immich_media_api_key")
    val selectedAlbumIds by stringSetPref(emptySet(), "immich_media_selected_album_ids")
    var includeFavorites by stringPref("DISABLED", "immich_media_include_favorites")
    val includeRatings by stringSetPref(emptySet(), "immich_media_include_ratings")
    var includeRandom by stringPref("DISABLED", "immich_media_include_random")
    var includeRecent by stringPref("DISABLED", "immich_media_include_recent")
    override var imageType by nullableEnumValuePref(ImmichImageType.PREVIEW, "immich_media_image_type")
    override var videoType by nullableEnumValuePref(ImmichVideoType.TRANSCODED, "immich_media_video_type")
}
