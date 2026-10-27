package com.neilturner.aerialviews.services

/**
 * Builds the key that decides whether the cached playlist is still valid.
 *
 * The key describes the *cached list*, not the current view of it. Time-of-day settings are
 * deliberately excluded: the cache stores the full playlist and the filter is applied when it is
 * read, so a day/night change must not throw the cache away.
 */
object PlaylistSettingsHash {
    /** Bump when the meaning or layout of the cached list changes. */
    const val CACHE_FORMAT_VERSION = 2

    data class Settings(
        val removeDuplicates: Boolean,
        val ignoreNonManifestVideos: Boolean,
        val shuffleVideos: Boolean,
        val shuffleMusic: Boolean,
        val repeatMusic: Boolean,
        val filterRemote: Boolean,
    )

    fun build(
        settings: Settings,
        providerHashes: List<String>,
    ): String {
        val generalParts =
            buildList {
                add(CACHE_FORMAT_VERSION.toString())
                add(settings.removeDuplicates.toString())
                add(settings.ignoreNonManifestVideos.toString())
                add(settings.shuffleVideos.toString())
                add(settings.shuffleMusic.toString())
                add(settings.repeatMusic.toString())
                // The derived network mode, not just the wifiOnly setting: the cache holds exactly
                // the playlist that will be played, so crossing the WiFi boundary must invalidate it.
                add(settings.filterRemote.toString())
            }
        val generalHash = generalParts.joinToString("|")
        return (listOf(generalHash) + providerHashes).joinToString("||").hashCode().toString()
    }
}
