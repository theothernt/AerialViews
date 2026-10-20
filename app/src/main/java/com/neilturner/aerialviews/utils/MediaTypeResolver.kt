package com.neilturner.aerialviews.utils

import com.neilturner.aerialviews.data.storage.FileHelper
import com.neilturner.aerialviews.models.enums.AerialMediaType

/**
 * Resolves the slideshow media type for a filename, or null if the file is not playable slideshow media.
 *
 * Audio deliberately returns null. Music is fetched separately via `MediaProvider.fetchMusic()` and
 * played as background music, never as slideshow media. Returning null forces callers to decide what
 * to do with an unrecognised file instead of falling through, because `AerialMedia.type` defaults to
 * [AerialMediaType.VIDEO] - so a file that skips the decision is silently queued as a video and
 * produces a black screen.
 *
 * Accepts a full path or bare filename; matching is by file extension only.
 */
fun aerialMediaTypeFor(filename: String): AerialMediaType? =
    when {
        FileHelper.isSupportedVideoType(filename) -> AerialMediaType.VIDEO
        FileHelper.isSupportedImageType(filename) -> AerialMediaType.IMAGE
        else -> null
    }
