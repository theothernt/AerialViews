package com.neilturner.aerialviews.utils

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.android.exoplayer2.DefaultRenderersFactory
import com.google.android.exoplayer2.mediacodec.MediaCodecAdapter

// https://github.com/bdwixx/AerialViews/blob/philips-exoplayer-fix/app/src/main/java/com/neilturner/aerialviews/utils/CustomRendererFactory.java

class CustomRendererFactory(context: Context?) :
    DefaultRenderersFactory(context!!) {
    private var pmcaf: PhilipsMediaCodecAdapterFactory? = null
    override fun getCodecAdapterFactory(): MediaCodecAdapter.Factory {
        Log.d(TAG, "Manufacturer: " + Build.MANUFACTURER)
        if (Build.MANUFACTURER != "Philips") {
            Log.d(TAG, "Using default MediaCodecAdapter")
            return super.getCodecAdapterFactory()
        }
        if (pmcaf == null) {
            pmcaf = PhilipsMediaCodecAdapterFactory()
        }
        Log.d(TAG, "Using custom/philips MediaCodecAdapter")
        return pmcaf as PhilipsMediaCodecAdapterFactory
    }

    companion object {
        private const val TAG = "CustomRendererFactory"
    }
}