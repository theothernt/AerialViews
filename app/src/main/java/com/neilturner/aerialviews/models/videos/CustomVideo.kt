package com.neilturner.aerialviews.models.videos

import android.net.Uri
import androidx.core.net.toUri
import com.neilturner.aerialviews.models.enums.VideoQuality
import com.neilturner.aerialviews.utils.filenameWithoutExtension
import kotlinx.serialization.Serializable

@Serializable
class CustomVideo : AbstractVideo() {
    override fun uriAtQuality(quality: VideoQuality?): Uri {
        val url = when (quality) {
            VideoQuality.VIDEO_1080_SDR -> video1080hdr // Map HDR to SDR as 10bit colour space alone is not HDR
            VideoQuality.VIDEO_4K_SDR -> video4ksdr
            else -> video1080h264
        }.toString()
        return url.toUri()
    }

    override fun allUrls(): List<String> {
        val urls = mutableSetOf<String>()
        enumValues<VideoQuality>().forEach { quality ->
            uriAtQuality(quality).let { uri ->
                urls.add(uri.filenameWithoutExtension.lowercase())
            }
        }
        return urls.toList()
    }
}

@Serializable
data class CustomVideos(
    val assets: List<CustomVideo>? = null
)

@Serializable
data class ManifestSource(
    val name: String,
    val description: String? = null,
    val scenes: List<String>? = null,
    val manifestUrl: String,
    val license: String? = null,
    val more: String? = null,
    val local: Boolean = false,
    val cacheable: Boolean = true
)

@Serializable
data class Manifest(
    val sources: List<ManifestSource>
)
