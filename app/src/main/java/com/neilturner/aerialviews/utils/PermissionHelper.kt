package com.neilturner.aerialviews.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object PermissionHelper {

    // Images + Videos = Read permission
    // Text/Document = Read/Write permission

    fun hasMediaReadPermission(context: Context): Boolean {
        // Android 13 / 33 and above
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasDocumentReadPermission(context: Context): Boolean {
        // Android 11 / 30 and above
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            true
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasDocumentWritePermission(context: Context): Boolean {
        // Android 11 / 30 and above
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            true
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun getReadMediaPermissions(): Array<String> {
        // Android 13 / 33 and above
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_IMAGES
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    fun isReadMediaPermissionGranted(results: Map<String, Boolean>): Boolean {
        // Android 13 / 33 and above
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            results.getValue(Manifest.permission.READ_MEDIA_VIDEO) &&
                results.getValue(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            results.getValue(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    fun getWriteDocumentPermission(): String {
        // Android 11 / 30 and above
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // WRITE_EXTERNAL_STORAGE is useless above Android 10
            // So request existing permission anyway
            Manifest.permission.READ_EXTERNAL_STORAGE
        } else {
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        }
    }

    fun getReadDocumentPermission(): String {
        return Manifest.permission.READ_EXTERNAL_STORAGE
    }
}
