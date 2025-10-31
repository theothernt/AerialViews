package com.neilturner.aerialviews.models.prefs

import com.neilturner.aerialviews.models.enums.VideoQuality

interface ProviderPreferences {
    val enabled: Boolean
    val quality: VideoQuality?
    val scene: Set<String>
    val timeOfDay: Set<String>
}

