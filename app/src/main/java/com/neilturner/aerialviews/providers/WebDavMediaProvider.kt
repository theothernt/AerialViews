package com.neilturner.aerialviews.providers

import android.content.Context
import com.neilturner.aerialviews.models.enums.ProviderSourceType
import com.neilturner.aerialviews.models.prefs.WebDavMediaPrefs
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.models.videos.VideoMetadata

class WebDavMediaProvider(context: Context, private val prefs: WebDavMediaPrefs) : MediaProvider(context) {
    override val type = ProviderSourceType.LOCAL

    override val enabled: Boolean
        get() = prefs.enabled

    override suspend fun fetchMedia(): List<AerialMedia> {
        return fetchWebDavMedia().first
    }

    override suspend fun fetchTest(): String {
        return fetchWebDavMedia().second
    }

    override suspend fun fetchMetadata(): List<VideoMetadata> {
        return emptyList()
    }

    private suspend fun fetchWebDavMedia(): Pair<List<AerialMedia>, String> {
        val media = mutableListOf<AerialMedia>()

        // Check hostname
        // Validate IP address or hostname?
        if (prefs.hostName.isEmpty()) {
            return Pair(media, "Hostname not specified")
        }

        return Pair(media, "No")
    }

    companion object {
        private const val TAG = "WebDavMediaProvider"
    }
}
