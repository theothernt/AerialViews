package com.neilturner.aerialviews.models.prefs

import com.chibatching.kotpref.KotprefModel
import com.chibatching.kotpref.enumpref.nullableEnumValuePref
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.VideoQuality

object ProjectivyPrefs : KotprefModel() {
    override val kotprefName = "${context.packageName}_preferences"

    val sharedProviders by stringSetPref("projectivy_shared_providers") {
        context.resources.getStringArray(R.array.projectivy_shared_providers_default).toSet()
    }
}

object ProjectivyApplePrefs : KotprefModel() {
    override val kotprefName = "${context.packageName}_preferences"

    var enabled = ProjectivyPrefs.sharedProviders.contains("APPLE")
    var quality by nullableEnumValuePref(VideoQuality.VIDEO_1080_SDR, "projectivy_apple_videos_quality")
    var count by stringPref("-1", "projectivy_apple_videos_count")

    val scene by stringSetPref("projectivy_apple_videos_scene_type") {
        context.resources.getStringArray(R.array.video_scene_type_default).toSet()
    }

    val timeOfDay by stringSetPref("projectivy_apple_videos_time_of_day") {
        context.resources.getStringArray(R.array.video_time_of_day_default).toSet()
    }
}

object ProjectivyAmazonPrefs : KotprefModel() {
    override val kotprefName = "${context.packageName}_preferences"

    var enabled = ProjectivyPrefs.sharedProviders.contains("AMAZON")
    var quality by nullableEnumValuePref(VideoQuality.VIDEO_1080_SDR, "projectivy_amazon_videos_quality")
    var count by stringPref("-1", "projectivy_amazon_videos_count")

    val scene by stringSetPref("projectivy_amazon_videos_scene_type") {
        context.resources.getStringArray(R.array.amazon_video_scene_type_default).toSet()
    }

    val timeOfDay by stringSetPref("projectivy_amazon_videos_time_of_day") {
        context.resources.getStringArray(R.array.video_time_of_day_default).toSet()
    }
}

object ProjectivyComm1Prefs : KotprefModel() {
    override val kotprefName = "${context.packageName}_preferences"

    var enabled = ProjectivyPrefs.sharedProviders.contains("COMM1")
    var quality by nullableEnumValuePref(VideoQuality.VIDEO_1080_SDR, "projectivy_comm1_videos_quality")
    var count by stringPref("-1", "projectivy_comm1_videos_count")

    val scene by stringSetPref("projectivy_comm1_videos_scene_type") {
        context.resources.getStringArray(R.array.video_scene_type_default).toSet()
    }

    val timeOfDay by stringSetPref("projectivy_comm1_videos_time_of_day") {
        context.resources.getStringArray(R.array.video_time_of_day_default).toSet()
    }
}

object ProjectivyComm2Prefs : KotprefModel() {
    override val kotprefName = "${context.packageName}_preferences"

    var enabled = ProjectivyPrefs.sharedProviders.contains("COMM2")
    var quality by nullableEnumValuePref(VideoQuality.VIDEO_1080_SDR, "projectivy_comm2_videos_quality")
    var count by stringPref("-1", "projectivy_comm2_videos_count")

    val scene by stringSetPref("projectivy_comm2_videos_scene_type") {
        context.resources.getStringArray(R.array.comm2_videos_scene_type_default).toSet()
    }

    val timeOfDay by stringSetPref("projectivy_comm2_videos_time_of_day") {
        context.resources.getStringArray(R.array.video_time_of_day_default).toSet()
    }
}
