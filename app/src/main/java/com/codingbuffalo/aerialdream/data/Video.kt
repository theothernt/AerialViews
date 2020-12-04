package com.codingbuffalo.aerialdream.data

import android.net.Uri

abstract class Video {
    private val accessibilityLabel: String? = null

    open val location: String?
        get() = accessibilityLabel

    abstract fun getUri(option: String): Uri?
}