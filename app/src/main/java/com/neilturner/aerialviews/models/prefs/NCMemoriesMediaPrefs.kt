package com.neilturner.aerialviews.models.prefs

import com.chibatching.kotpref.KotprefModel
import com.chibatching.kotpref.enumpref.nullableEnumValuePref
import com.neilturner.aerialviews.models.enums.NCMemoriesImageType
import com.neilturner.aerialviews.models.enums.NCMemoriesVideoType

interface NCMemoriesImagePrefs {
    val includeVideos: Boolean
    val includePhotos: Boolean
}

interface NCMemoriesUrlPrefs {
    val username: String
    val password: String
    val imageType: NCMemoriesImageType?
    val videoType: NCMemoriesVideoType?
}

object NCMemoriesMediaPrefs : KotprefModel(), NCMemoriesUrlPrefs, NCMemoriesImagePrefs {
    override val kotprefName = "${context.packageName}_preferences"

    var enabled by booleanPref(false, "ncmemories_media_enabled")
    val mediaSelection by stringSetPref("ncmemories_media_selection") {
        MediaSelection.defaultSelection
    }
    val mediaType
        get() = MediaSelection.toMediaType(mediaSelection)
    override val includeVideos: Boolean
        get() = MediaSelection.includesVideos(mediaSelection)
    override val includePhotos: Boolean
        get() = MediaSelection.includesPhotos(mediaSelection)
    override var username by stringPref("", "ncmemories_media_username")
    override var password by stringPref("", "ncmemories_media_password")
    var url by stringPref("", "ncmemories_media_url")
    var validateSsl by booleanPref(true, "ncmemories_media_validate_ssl")
    val selectedAlbumIds by stringSetPref(emptySet(), "ncmemories_media_selected_album_ids")
    var includeFavorites by stringPref("DISABLED", "ncmemories_media_include_favorites")
    var includeRecent by stringPref("DISABLED", "ncmemories_media_include_recent")
    val favoritesName by stringPref("Favorites", "ncmemories_media_favorites_name")
    val recentName by stringPref("Recent", "ncmemories_media_recent_name")
    override var imageType by nullableEnumValuePref(
        NCMemoriesImageType.PREVIEW,
        "ncmemories_media_image_type",
    )
    override var videoType by nullableEnumValuePref(
        NCMemoriesVideoType.TRANSCODED,
        "ncmemories_media_video_type",
    )
    var isTestConnection: Boolean = false

    fun settingsHash(): String = settingsHashWithPrefix("ncmemories_media_")
}
