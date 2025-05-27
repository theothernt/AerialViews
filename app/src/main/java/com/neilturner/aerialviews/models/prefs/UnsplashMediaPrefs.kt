package com.neilturner.aerialviews.models.prefs

import com.chibatching.kotpref.KotprefModel
import com.chibatching.kotpref.enumpref.nullableEnumValuePref
import com.neilturner.aerialviews.models.enums.ProviderMediaType

object UnsplashMediaPrefs : KotprefModel() {
    override val kotprefName = "${context.packageName}_preferences"

    var enabled by booleanPref(false, "unsplash_media_enabled")
    var mediaType by nullableEnumValuePref(ProviderMediaType.PHOTOS, "unsplash_media_type")
    var accessKey by stringPref("", "unsplash_media_access_key")
    var searchQuery by stringPref("", "unsplash_media_search_query")
    var orientation by stringPref("landscape", "unsplash_media_orientation")
    var photosPerPage by stringPref("30", "unsplash_media_photos_per_page")
    var orderBy by stringPref("relevant", "unsplash_media_order_by")
}
