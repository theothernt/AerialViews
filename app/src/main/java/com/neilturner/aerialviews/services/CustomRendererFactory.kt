package com.neilturner.aerialviews.services

import android.annotation.SuppressLint
import android.content.Context
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter
import timber.log.Timber

// https://github.com/bdwixx/AerialViews/blob/philips-exoplayer-fix/app/src/main/java/com/neilturner/aerialviews/utils/CustomRendererFactory.java

@SuppressLint("UnsafeOptInUsageError")
class CustomRendererFactory(
    context: Context,
) : DefaultRenderersFactory(context) {
    @SuppressLint("UnsafeOptInUsageError")
    override fun getCodecAdapterFactory(): MediaCodecAdapter.Factory {
        Timber.i("Using Custom/Philips MediaCodecAdapter")
        return PhilipsMediaCodecAdapterFactory()
    }
}
