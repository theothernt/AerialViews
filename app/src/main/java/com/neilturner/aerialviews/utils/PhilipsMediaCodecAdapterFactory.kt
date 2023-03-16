package com.neilturner.aerialviews.utils

import android.media.MediaExtractor
import android.util.Log
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
        if (mediaUri == null) {
            return SynchronousMediaCodecAdapter.Factory().createAdapter(configuration)
        }
        try {
            val me = MediaExtractor()
            me.setDataSource(mediaUri!!)
            mediaUri = null
            val tf = me.getTrackFormat(0)
            val csd0 = tf.getByteBuffer("csd-0")
            if (csd0 != null && csd0.limit() != 0) {
                configuration.mediaFormat.setByteBuffer("csd-0", csd0)
                Log.d(TAG, "csd-0 set")

                // TODO: Remove this, its for testing only
                val sb = StringBuilder()
                while (csd0.hasRemaining()) {
                    val b = csd0.get()
                    sb.append(String.format("%02x", b.toInt() and 0xff))
                }
                Log.d(TAG, "csd-0: $sb")
                // After reading, position is at end and the codec starts
                // reading at current pos resulting in failed playback
                // so we need to set position manually
                csd0.position(0)
            }
        } catch (e: IOException) {
            throw RuntimeException(e)
        } finally {
            mediaUri = null
        }
        Log.d(TAG, "configuration.format: " + configuration.format)
        return SynchronousMediaCodecAdapter.Factory().createAdapter(configuration)
    }

    companion object {
        private const val TAG = "PMCodecAdapterFactory"
        var mediaUri: String? = null
    }
}