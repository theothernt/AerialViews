package com.codingbuffalo.aerialdream.models.videos

import android.net.Uri

abstract class Video {
    private val accessibilityLabel: String? = null

    open val location: String?
        get() = accessibilityLabel

    abstract fun uri(option: String): Uri?
}