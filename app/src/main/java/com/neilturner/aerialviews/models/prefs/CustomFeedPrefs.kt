package com.neilturner.aerialviews.models.prefs

import com.chibatching.kotpref.KotprefModel
import com.chibatching.kotpref.enumpref.nullableEnumValuePref
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.VideoQuality

object CustomFeedPrefs : KotprefModel() {
    override val kotprefName = "${context.packageName}_preferences"

    var enabled by booleanPref(false, "custom_media_enabled")
    var quality by nullableEnumValuePref(VideoQuality.VIDEO_1080_SDR, "custom_media_quality")

    var urls by stringPref("", "custom_media_urls")
    var urlsCache by stringPref("", "custom_media_urls_cache")
    var urlsSummary by stringPref("", "custom_media_urls_summary")
}
