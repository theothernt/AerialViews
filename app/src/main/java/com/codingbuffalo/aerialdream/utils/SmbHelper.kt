package com.codingbuffalo.aerialdream.utils

import android.util.Log
import com.codingbuffalo.aerialdream.models.prefs.NetworkVideoPrefs

object SmbHelper {

    fun fixShareName() {
        val shareName = NetworkVideoPrefs.shareName

        if (shareName.first() != '/') {
            NetworkVideoPrefs.shareName = "/$shareName"
            Log.i(TAG, "Fixing ShareName - removing leading slash")
        }


        if (shareName.last() == '/') {
            NetworkVideoPrefs.shareName = shareName.dropLast(1)
            Log.i(TAG, "Fixing ShareName - removing trailing slash")
        }

    }

    private const val TAG = "SmbHelper"
}

