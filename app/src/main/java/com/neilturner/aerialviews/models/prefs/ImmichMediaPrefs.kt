package com.neilturner.aerialviews.models.prefs

import com.chibatching.kotpref.KotprefModel
import com.chibatching.kotpref.enumpref.nullableEnumValuePref
import com.neilturner.aerialviews.models.enums.ImmichAuthType
import com.neilturner.aerialviews.models.enums.ImmichImageType
import com.neilturner.aerialviews.models.enums.ImmichVideoType
import com.neilturner.aerialviews.models.enums.ProviderMediaType

interface ImmichAssetPrefs {
    val includeVideos: Boolean
    val includePhotos: Boolean
}

interface ImmichUrlPrefs {
    val pathName: String
    val password: String
    val authType: ImmichAuthType?
    val imageType: ImmichImageType?
    val videoType: ImmichVideoType?
}

interface ImmichRepositoryPrefs {
    val url: String
    val validateSsl: Boolean
    val pathName: String
    val password: String
    val apiKey: String
    val selectedAlbumIds: Set<String>
    val includeFavorites: String
    val includeRatings: Set<String>
    val includeRandom: String
    val includeRecent: String
    val mediaType: ProviderMediaType?
}

object ImmichMediaPrefs : KotprefModel(), ImmichUrlPrefs, ImmichAssetPrefs, ImmichRepositoryPrefs {
    override val kotprefName = "${context.packageName}_preferences"

    var enabled by booleanPref(false, "immich_media_enabled")
    val mediaSelection by stringSetPref("immich_media_selection") {
        MediaSelection.defaultSelection
    }
    override val mediaType: ProviderMediaType?
        get() = MediaSelection.toMediaType(mediaSelection)
    override val includeVideos: Boolean
        get() = MediaSelection.includesVideos(mediaSelection)
    override val includePhotos: Boolean
        get() = MediaSelection.includesPhotos(mediaSelection)
    override var pathName by stringPref("", "immich_media_pathname")
    override var password by stringPref("", "immich_media_password")
    override var url by stringPref("", "immich_media_url")
    override var validateSsl by booleanPref(true, "immich_media_validate_ssl")
    override var authType by nullableEnumValuePref(ImmichAuthType.SHARED_LINK, "immich_media_auth_type")
    override var apiKey by stringPref("", "immich_media_api_key")
    override val selectedAlbumIds by stringSetPref(emptySet(), "immich_media_selected_album_ids")
    override var includeFavorites by stringPref("DISABLED", "immich_media_include_favorites")
    override val includeRatings by stringSetPref(emptySet(), "immich_media_include_ratings")
    override var includeRandom by stringPref("DISABLED", "immich_media_include_random")
    override var includeRecent by stringPref("DISABLED", "immich_media_include_recent")
    override var imageType by nullableEnumValuePref(ImmichImageType.PREVIEW, "immich_media_image_type")
    override var videoType by nullableEnumValuePref(ImmichVideoType.TRANSCODED, "immich_media_video_type")

    fun settingsHash(): String = settingsHashWithPrefix("immich_media_")
}
