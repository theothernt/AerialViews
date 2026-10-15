package com.neilturner.aerialviews.ui.core

import com.neilturner.aerialviews.models.enums.VideoScale

/**
 * Geometry for the "portrait video rotation fix" advanced setting.
 *
 * Some TV firmware renders portrait video sideways. This works out the
 * rotation and scale to apply to the player view to compensate.
 *
 * The rotation only ever matters for quarter turns, which swap the view's
 * layout axes, so the view is scaled to keep filling the viewport.
 *
 * The fix is skipped when cropping is selected: cropping already scales the
 * frame up to cover the viewport, so rotating on top of it neither composes
 * nor produces a usable result.
 *
 * @param containerRotationDegrees the rotation declared by the container, read
 *   from Media3's `Format.rotationDegrees`, or null when the format is not yet
 *   known. Media3 applies any container rotation itself and reports the result
 *   in `VideoSize`, so a non-zero value means the video is already the right
 *   way up and rotating it again would undo the player's work.
 * @param forceLandscape applies the rotation even when the video reports a
 *   landscape shape. Some devices are handed portrait footage that has been
 *   stored landscape with its rotation metadata stripped, which no container
 *   field distinguishes from genuinely landscape video.
 */
internal object PortraitVideoOrientation {
    /** The transform to apply to the player view, or null to leave it alone. */
    data class Fix(
        val rotation: Float,
        val scale: Float,
    )

    /** Parses the stored preference value, falling back to "disabled". */
    fun parseDegrees(raw: String?): Float = raw?.toFloatOrNull() ?: 0f

    @Suppress("LongParameterList")
    fun resolve(
        videoWidth: Int,
        videoHeight: Int,
        containerRotationDegrees: Int?,
        enabledDegrees: Float,
        useTextureView: Boolean,
        forceLandscape: Boolean,
        videoScale: VideoScale?,
        containerWidth: Float,
        containerHeight: Float,
    ): Fix? {
        // Unknown or already handled by the player: never rotate blind.
        if (containerRotationDegrees == null) return null
        if (containerRotationDegrees != 0) return null
        if (useTextureView) return null
        if (enabledDegrees == 0f) return null
        if (containerWidth <= 0f || containerHeight <= 0f) return null
        // Cropping scales and clips to fill the screen, which does not compose with
        // rotating the view, so the fix steps aside rather than fight it.
        if (videoScale == VideoScale.SCALE_TO_FIT_WITH_CROPPING) return null
        if (!forceLandscape && videoHeight <= videoWidth) return null

        val scale =
            when (enabledDegrees) {
                // A letterboxed frame is smaller than the viewport, so the swapped axes need
                // the container ratio to keep filling the screen. A frame that already matches
                // the container has no letterbox to compensate for.
                90f, 270f -> {
                    if (videoHeight > videoWidth) {
                        containerWidth / containerHeight
                    } else {
                        1f
                    }
                }

                else -> {
                    1f
                }
            }
        return Fix(rotation = enabledDegrees, scale = scale)
    }
}
