@file:Suppress("MemberVisibilityCanBePrivate")

package com.neilturner.aerialviews.utils

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.TypedValue
import java.util.Locale
import kotlin.math.min

object DeviceHelper {
    // Based on
    // https://stackoverflow.com/a/27836910/247257

    fun androidVersion(): String = "v${Build.VERSION.RELEASE}"

    fun deviceName(): String {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        return if (model
                .lowercase(Locale.getDefault())
                .startsWith(manufacturer.lowercase(Locale.getDefault()))
        ) {
            capitalize(model)
        } else {
            capitalize(manufacturer) + " " + model
        }
    }

    private fun capitalize(s: String): String {
        if (s.isEmpty()) {
            return ""
        }
        val first = s[0]
        return if (Character.isUpperCase(first)) {
            s
        } else {
            first.uppercaseChar().toString() + s.substring(1)
        }
    }

    fun canAccessScreensaverSettings(): Boolean =
        !(
            isFireTV() ||
                isGoogleTV()
        )

    // https://stackoverflow.com/a/55355049/247257
    fun isEmulator(): Boolean =
        (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")) ||
            Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.startsWith("unknown") ||
            Build.HARDWARE.contains("goldfish") ||
            Build.HARDWARE.contains("ranchu") ||
            Build.MODEL.contains("google_sdk") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK built for x86") ||
            Build.MANUFACTURER.contains("Genymotion") ||
            Build.PRODUCT.contains("sdk_google") ||
            Build.PRODUCT.contains("google_sdk") ||
            Build.PRODUCT.contains("sdk") ||
            Build.PRODUCT.contains("sdk_x86") ||
            Build.PRODUCT.contains("sdk_gphone64_arm64") ||
            Build.PRODUCT.contains("vbox86p") ||
            Build.PRODUCT.contains("emulator") ||
            Build.PRODUCT.contains("simulator")

    fun isPhone(context: Context): Boolean {
        val metrics = context.resources.displayMetrics
        val smallestSize = min(metrics.widthPixels, metrics.heightPixels)
        val tabletSize =
            TypedValue
                .applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    600f,
                    context.resources.displayMetrics,
                ).toInt()
        return smallestSize < tabletSize
    }

    fun isDevice(): Boolean = true

    fun isTV(context: Context): Boolean = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

    fun isFireTV(): Boolean = deviceName().contains("AFT", true)

    fun isNvidiaShield(): Boolean = deviceName().contains("NVIDIA", true)

    fun isGoogleTV(): Boolean = deviceName().contains("Google Chromecast", true)

    fun hasHevcSupport(): Boolean = ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) && !isEmulator())

    fun hasAvifSupport(): Boolean =
        ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) && !isEmulator()) // Might need to test for TV & 13+ or Phone & 12+
}
