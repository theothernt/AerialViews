package com.neilturner.aerialviews.utils

import android.content.Context
import android.util.Log
import com.google.android.exoplayer2.DefaultRenderersFactory
import com.google.android.exoplayer2.mediacodec.MediaCodecAdapter

// https://github.com/bdwixx/AerialViews/blob/philips-exoplayer-fix/app/src/main/java/com/neilturner/aerialviews/utils/CustomRendererFactory.java

class CustomRendererFactory(context: Context) : DefaultRenderersFactory(context) {
    override fun getCodecAdapterFactory(): MediaCodecAdapter.Factory {
        Log.d(TAG, "Using Custom/Philips MediaCodecAdapter")
        return PhilipsMediaCodecAdapterFactory()
    }

    companion object {
        private const val TAG = "CustomRendererFactory"
    }
}
