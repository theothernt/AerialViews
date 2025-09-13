package com.neilturner.aerialviews.providers

import android.content.Context
import com.neilturner.aerialviews.models.enums.ProviderSourceType
import com.neilturner.aerialviews.models.prefs.CustomMediaPrefs
import com.neilturner.aerialviews.models.videos.AerialMedia

class CustomMediaProvider(
    context: Context,
    private val prefs: CustomMediaPrefs,
) : MediaProvider(context) {
    override val type = ProviderSourceType.REMOTE
    val metadata = mutableMapOf<String, Pair<String, Map<Int, String>>>()
    val videos = mutableListOf<AerialMedia>()

    override val enabled: Boolean
        get() = prefs.enabled

    override suspend fun fetchMedia(): List<AerialMedia> {
        if (metadata.isEmpty()) buildVideoAndMetadata()
        return videos
    }

    override suspend fun fetchTest(): String {
        // Run series of tests to validate URLs, connection, content is JSON, etc.
        // If URL is valid and contains JSON, quick validation with Regex for manifest/entries format
        // If any valid URLs left, run buildVideoAndMetadata to parse and build list of videos

        // val urls = validateUrlsAndJsonContent()
        // if (urls.isNotEmpty()) buildVideoAndMetadata()

        // Should return result - count + message or summary?
        return ""
    }

    override suspend fun fetchMetadata(): MutableMap<String, Pair<String, Map<Int, String>>> {
        if (metadata.isEmpty()) buildVideoAndMetadata()
        return metadata
    }

    private fun validateUrlsAndJsonContent(): String {

        return ""
    }

    private fun buildVideoAndMetadata() {

        // Use retrofit
        // an example of the entries JSON format is the Comm2Video model object
        // an example of the manifest JSON format is:
        /*

            {
            sources: [
            {
            name: "Community Videos from Joshua Michaels & Hal Bergman",
            description: "Enjoy this contribution of aerial videos, if you want to support the artists and get more videos from them, press the More button below.",
            scenes: [
            "landscape"
            ],
            manifestUrl: "https://raw.githubusercontent.com/glouel/AerialCommunity/master/entries.json",
            license: "https://jetsoncreative.com/aeriallicense",
            more: "https://magicwindow.app/aerial",
            local: false,
            cacheable: true
            }
            ]
            }

        */
        
        // For each URL, parse it as a manifest JSON format first (as opposed to an entries JSON format)
        // Return list of success (new entries JSON format URLs) and failures (might be a bad URL or entries JSON format URL)

        // For each item, try and parse it as an entries JSON format
    }
}
