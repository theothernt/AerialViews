package com.codingbuffalo.aerialdream.data

import android.net.Uri

class SimpleVideo(private val videoUri: Uri, override val location: String?) : Video() {

//    override fun getLocation(): String? {
//        return location
//    }

    override fun getUri(option: String): Uri? {
        return videoUri
    }
}