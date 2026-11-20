package com.neilturner.aerialviews.models.videos

import android.net.Uri
import androidx.core.net.toUri
import com.neilturner.aerialviews.models.enums.VideoQuality
import com.neilturner.aerialviews.utils.filenameWithoutExtension
import kotlinx.serialization.Serializable
import kotlin.enums.enumEntries

@Serializable
class YandexVideo : AbstractVideo() {
    override fun uriAtQuality(quality: VideoQuality?): Uri {
        val url = video4ksdr ?: video1080sdr ?: video1080h264 ?: ""
        return url.toUri()
    }

    override fun allUrls(): List<String> {
        val urls = mutableSetOf<String>()
        enumEntries<VideoQuality>().forEach { quality ->
            uriAtQuality(quality).let { uri -> urls.add(uri.filenameWithoutExtension.lowercase()) }
        }
        return urls.toList()
    }
}

@Serializable
class YandexVideos {
    val assets: List<YandexVideo>? = null
}
