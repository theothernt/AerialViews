package com.neilturner.aerialviews.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object PermissionHelper {
    // TIRAMISU / 33 / 13
    // R / 30 / 11

    // Images + Videos = Read permission

    fun hasMediaReadPermission(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

    fun getReadMediaPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_IMAGES,
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    fun isReadMediaPermissionGranted(results: Map<String, Boolean>): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            results.getOrDefault(Manifest.permission.READ_MEDIA_VIDEO, false) &&
                results.getOrDefault(Manifest.permission.READ_MEDIA_IMAGES, false)
        } else {
            try {
                results.getValue(Manifest.permission.READ_EXTERNAL_STORAGE)
            } catch (ex: NoSuchElementException) {
                false
            }
        }

    // Text/Document = Read/Write permission

    fun hasDocumentReadPermission(context: Context): Boolean {
        // Android 11 / 30 and above
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            true
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasDocumentWritePermission(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            true
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

    fun getWriteDocumentPermission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // WRITE_EXTERNAL_STORAGE is useless above Android 10
            // So request existing permission anyway
            Manifest.permission.READ_EXTERNAL_STORAGE
        } else {
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        }

    fun hasNotificationListenerPermission(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun hasSystemOverlayPermission(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
            //ContextCompat.checkSelfPermission(context, Settings.ACTION_MANAGE_OVERLAY_PERMISSION) == PackageManager.PERMISSION_GRANTED
        } else {
            false
        }

    @Suppress("SameReturnValue")
    fun getReadDocumentPermission(): String = Manifest.permission.READ_EXTERNAL_STORAGE
}
