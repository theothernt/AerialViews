package com.neilturner.aerialviews.providers

import android.content.Context
import com.neilturner.aerialviews.models.enums.ProviderSourceType
import com.neilturner.aerialviews.models.music.MusicTrack
import com.neilturner.aerialviews.models.videos.AerialMedia

sealed class ProviderFetchResult {
    data class Success(
        val media: List<AerialMedia>,
        val summary: String,
    ) : ProviderFetchResult()

    data class Error(
        val message: String,
    ) : ProviderFetchResult()
}

abstract class MediaProvider(
    val context: Context,
) {
    abstract val type: ProviderSourceType

    abstract val enabled: Boolean

    open suspend fun prepare() {}

    abstract suspend fun fetch(): ProviderFetchResult

    open suspend fun fetchMusic(): List<MusicTrack> = emptyList()

    abstract suspend fun fetchMetadata(media: List<AerialMedia>): List<AerialMedia>

    // type
    // VideoType.LOCAL, REMOTE
    // LOCAL/LAN vs REMOVE/HTTPS/WEBDAV
}
