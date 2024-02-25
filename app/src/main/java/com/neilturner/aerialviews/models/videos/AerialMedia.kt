package com.neilturner.aerialviews.models.videos

import android.net.Uri
import com.neilturner.aerialviews.models.enums.MediaItemType

data class AerialMedia(val uri: Uri, var location: String = "", var poi: Map<Int, String> = emptyMap(), var type: MediaItemType = MediaItemType.VIDEO)
