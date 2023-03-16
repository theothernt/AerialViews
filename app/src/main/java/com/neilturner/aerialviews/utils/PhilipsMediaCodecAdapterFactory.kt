package com.neilturner.aerialviews.utils

import android.media.MediaExtractor
import com.google.android.exoplayer2.mediacodec.MediaCodecAdapter
import com.google.android.exoplayer2.mediacodec.SynchronousMediaCodecAdapter
import java.io.IOException

// https://github.com/bdwixx/AerialViews/blob/philips-exoplayer-fix/app/src/main/java/com/neilturner/aerialviews/utils/PhilipsMediaCodecAdapterFactory.java

// Based on DefaultMediaCodecAdapterFactory
// No support for ASynchronousMediaCodecAdapter bc its inaccessible outside of the exoplayer package :(
// By default ASynchronousMediaCodecAdapter is only used above Android 12
class PhilipsMediaCodecAdapterFactory : MediaCodecAdapter.Factory {
    @Throws(IOException::class)
    override fun createAdapter(configuration: MediaCodecAdapter.Configuration): MediaCodecAdapter {
        if (mediaUrl == null) {
            return SynchronousMediaCodecAdapter.Factory().createAdapter(configuration)
        }
        try {
            val extractor = MediaExtractor()
            extractor.setDataSource(mediaUrl!!)
            mediaUrl = null
            val trackFormat = extractor.getTrackFormat(0)
            val codecData = trackFormat.getByteBuffer("csd-0")
            if (codecData != null && codecData.limit() != 0) {
                configuration.mediaFormat.setByteBuffer("csd-0", codecData)
                // After reading, position is at end and the codec starts
                // reading at current pos resulting in failed playback
                // so we need to set position manually
                codecData.position(0)
            }
        } catch (e: IOException) {
            throw RuntimeException(e)
        } finally {
            mediaUrl = null
        }
        return SynchronousMediaCodecAdapter.Factory().createAdapter(configuration)
    }

    companion object {
        private const val TAG = "PhilipsMediaCodecAdapter"
        var mediaUrl: String? = null
    }
}
