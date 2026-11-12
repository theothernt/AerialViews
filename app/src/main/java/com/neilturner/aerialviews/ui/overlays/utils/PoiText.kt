package com.neilturner.aerialviews.ui.overlays.utils

/**
 * Resolves which point of interest is showing at a position in the video.
 */
object PoiText {
    /**
     * The POI in effect at [seconds] into the video, or [fallback] when nothing has started yet.
     *
     * This is the same lookup MetadataOverlay does with findLast { it <= time }: the POI whose
     * start time has passed, no POI at all resolving to 0. Newlines are flattened because the
     * overlay is a single line.
     */
    fun at(
        seconds: Long,
        poi: Map<Int, String>,
        fallback: String = "",
    ): String {
        val key = poi.keys.filter { it <= seconds }.maxOrNull() ?: 0
        return poi[key]?.replace("\n", " ") ?: fallback
    }
}
