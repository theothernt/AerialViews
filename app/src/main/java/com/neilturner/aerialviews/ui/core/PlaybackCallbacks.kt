package com.neilturner.aerialviews.ui.core

import com.neilturner.aerialviews.models.enums.AerialMediaType

/**
 * Guards player callbacks that outlive the item which scheduled them.
 *
 * A video that was skipped, trimmed or looping can keep reporting after the screen has moved on,
 * and acting on those reports advances the item now on screen - for example a photo whose
 * slideshow timer has not elapsed yet.
 */
object PlaybackCallbacks {
    /** Whether a video player event belongs to the item currently on screen. */
    fun videoEventAppliesTo(currentType: AerialMediaType?): Boolean = currentType == AerialMediaType.VIDEO
}
