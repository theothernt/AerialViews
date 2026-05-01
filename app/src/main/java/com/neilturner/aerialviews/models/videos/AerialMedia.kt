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
    // Alternate URIs that belong to the same logical slot (e.g. other members of a
    // temporal cluster from the Immich smart slideshow). When non-empty, the renderer
    // picks one at random — including `uri` itself — every time the slot is shown,
    // so across playlist loops the user sees variety within the cluster.
    var clusterAlternates: List<Uri> = emptyList(),
)

data class AerialMediaMetadata(
    var shortDescription: String = "",
    var pointsOfInterest: Map<Int, String> = emptyMap(),
    var timeOfDay: TimeOfDay = TimeOfDay.UNKNOWN,
    var scene: SceneType = SceneType.UNKNOWN,
    var exif: AerialExifMetadata = AerialExifMetadata(),
    var albumName: String = "",
    var title: String = "",
)

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
