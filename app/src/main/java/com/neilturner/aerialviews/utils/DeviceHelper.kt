package com.neilturner.aerialviews.utils

import android.os.Build
import java.util.Locale

object DeviceHelper {

    // Based on
    // https://stackoverflow.com/a/27836910/247257

    fun androidVersion(): String {
        return "v${Build.VERSION.RELEASE}"
    }

    fun deviceName(): String {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        return if (model.lowercase(Locale.getDefault())
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

    // https://stackoverflow.com/a/55355049/247257
    fun isEmulator(): Boolean = (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")) ||
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

    fun isFireTV(): Boolean = deviceName().contains("AFT", true)

    fun isNvidaShield(): Boolean = deviceName().contains("NVIDIA", true)

    fun isGoogleTV(): Boolean = deviceName().contains("Google Chromecast", true)

    fun hasHevcSupport(): Boolean = ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) && !isEmulator())
}
