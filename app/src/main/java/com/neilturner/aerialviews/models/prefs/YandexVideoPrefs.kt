package com.neilturner.aerialviews.models.prefs

import com.chibatching.kotpref.KotprefModel
import com.chibatching.kotpref.enumpref.nullableEnumValuePref
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.VideoQuality

object YandexVideoPrefs : KotprefModel(), ProviderPreferences {
    override val kotprefName = "${context.packageName}_preferences"

    override var enabled by booleanPref(true, "yandex_videos_enabled")
    override var quality by nullableEnumValuePref(VideoQuality.VIDEO_4K_SDR, "yandex_videos_quality")
    var count by stringPref("-1", "yandex_videos_count")

    override val scene by stringSetPref("yandex_videos_scene_type") {
        context.resources.getStringArray(R.array.yandex_videos_scene_type_default).toSet()
    }

    override val timeOfDay by stringSetPref("yandex_videos_time_of_day") {
        context.resources.getStringArray(R.array.yandex_videos_time_of_day_default).toSet()
    }

    override fun settingsHash(): String = settingsHashWithPrefix("yandex_videos_")
}
