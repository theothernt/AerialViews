package com.neilturner.aerialviews.providers

import android.content.Context
import com.neilturner.aerialviews.models.enums.ProviderSourceType
import com.neilturner.aerialviews.models.videos.AerialMedia

abstract class MediaProvider(
    val context: Context,
) {
    abstract val type: ProviderSourceType

    abstract val enabled: Boolean

    abstract suspend fun fetchMedia(): List<AerialMedia>

    abstract suspend fun fetchTest(): String

    abstract suspend fun fetchMetadata(): MutableMap<String, Pair<String, Map<Int, String>>>

    // type
    // VideoType.LOCAL, REMOTE
    // LOCAL/LAN vs REMOVE/HTTPS/WEBDAV
}
