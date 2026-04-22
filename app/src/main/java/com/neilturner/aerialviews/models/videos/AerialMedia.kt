package com.neilturner.aerialviews.models.videos

import android.net.Uri
import com.neilturner.aerialviews.models.enums.AerialMediaSource
import com.neilturner.aerialviews.models.enums.AerialMediaType
import com.neilturner.aerialviews.models.enums.SceneType
import com.neilturner.aerialviews.models.enums.TimeOfDay

data class AerialMedia(
    val uri: Uri,
    var type: AerialMediaType = AerialMediaType.VIDEO,
    var source: AerialMediaSource = AerialMediaSource.UNKNOWN,
    var metadata: AerialMediaMetadata = AerialMediaMetadata(),
    // Other members of the same logical slot (e.g. temporal-cluster siblings from the
    // Immich smart slideshow). Each carries its own URI plus — when face-aware crop is
    // enabled — its own per-photo subject rectangle, so the renderer can randomize
    // across cluster members without inheriting a mis-aligned face bbox from the primary.
    var clusterAlternates: List<ClusterAlternate> = emptyList(),
)

data class ClusterAlternate(
    val uri: Uri,
    val subjectRect: NormalizedRect? = null,
)

data class AerialMediaMetadata(
    var shortDescription: String = "",
    var pointsOfInterest: Map<Int, String> = emptyMap(),
    var timeOfDay: TimeOfDay = TimeOfDay.UNKNOWN,
    var scene: SceneType = SceneType.UNKNOWN,
    var exif: AerialExifMetadata = AerialExifMetadata(),
    var albumName: String = "",
    var title: String = "",
    // Normalized (0..1 of image width/height) rect around a primary subject in the photo,
    // used by portrait crop/pan logic to keep the subject on-screen.
    var subjectRect: NormalizedRect? = null,
)

/** Rectangle in normalized image coordinates where each field is 0..1 of image width/height. */
data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val centerY: Float get() = (top + bottom) / 2f
    val height: Float get() = bottom - top
    val area: Float get() = (right - left) * (bottom - top)
}

data class AerialExifMetadata(
    var date: String? = null,
    var offset: String? = null,
    var latitude: Double? = null,
    var longitude: Double? = null,
    var city: String? = null,
    var state: String? = null,
    var country: String? = null,
    var description: String? = null,
)
