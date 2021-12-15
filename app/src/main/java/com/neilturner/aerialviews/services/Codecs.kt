@file:Suppress("unused")

package com.neilturner.aerialviews.services

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build

// https://github.com/technogeek00/android-device-media-information/blob/master/app/src/main/java/com/zacharycava/devicemediainspector/sources/Codecs.kt

enum class CodecType {
    ENCODER, DECODER
}

class Codec(source: MediaCodecInfo) {
    val name: String = source.name
    val codingFunction: CodecType = if (source.isEncoder) CodecType.ENCODER else CodecType.DECODER
    val mimeTypes: Array<String> = source.supportedTypes

    val canonicalName: String = if (Build.VERSION.SDK_INT >= 29) source.canonicalName else ""
    val isAlias: Boolean = if (Build.VERSION.SDK_INT >= 29) source.isAlias else false
    val isVendorProvided: Boolean = if (Build.VERSION.SDK_INT >= 29) source.isVendor else false
    val isSoftwareOnly: Boolean = if (Build.VERSION.SDK_INT >= 29) source.isSoftwareOnly else false
    val isHardwareAccelerated: Boolean = if (Build.VERSION.SDK_INT >= 29) source.isHardwareAccelerated else false
}

fun getCodecs(): List<Codec> {
    val codecs = MediaCodecList(MediaCodecList.ALL_CODECS)
    return codecs.codecInfos.map { Codec(it) }
}
