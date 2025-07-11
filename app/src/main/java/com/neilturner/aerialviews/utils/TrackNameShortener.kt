package com.neilturner.aerialviews.utils

/**
 * Utility class for shortening track names by removing common suffixes.
 */
object TrackNameShortener {
    // Regex patterns for shortening track names
    private val trackShortenerPatterns =
        listOf(
            Regex("""\s*\([^)]*\)\s*$"""), // Remove parenthetical content at end: (remastered), (radio edit), etc.
            Regex("""\s*\[[^\]]*\]\s*$"""), // Remove bracket content at end: [explicit], [clean], etc.
            Regex("""\s*-\s*[^-]*\s*$"""), // Remove dash-separated content at end: - Original Mix, - Radio Edit, etc.
            Regex("""\s*(feat\.?|featuring)\s+.*$""", RegexOption.IGNORE_CASE), // Remove featuring artists
        )

    /**
     * Shortens a track name by removing common suffixes like remixes, features, etc.
     *
     * @param trackName The original track name
     * @return The shortened track name, or the original if it would become empty
     */
    fun shortenTrackName(trackName: String): String {
        var shortened = trackName.trim()
        var previousLength: Int

        // Keep applying patterns until no more changes are made
        do {
            previousLength = shortened.length

            // Apply each regex pattern to progressively shorten the track name
            for (pattern in trackShortenerPatterns) {
                shortened = pattern.replace(shortened, "").trim()
                if (shortened.isEmpty()) {
                    // If we've removed everything, return the original
                    return trackName
                }
            }
        } while (shortened.length != previousLength)

        return shortened
    }
}
