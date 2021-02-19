package com.codingbuffalo.aerialdream.models.videos

import android.net.Uri

class SimpleVideo(private val videoUri: Uri, override val location: String?) : Video() {

    override fun uri(option: String): Uri {
        return videoUri
    }
}