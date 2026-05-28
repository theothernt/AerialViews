@file:Suppress("MemberVisibilityCanBePrivate")

package com.neilturner.aerialviews.ui.helpers

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.TypedValue
import java.util.Locale
import kotlin.math.min

object DeviceHelper {
    // Based on
    // https://stackoverflow.com/a/27836910/247257

    fun androidVersion(): String = "v${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

    fun deviceName(): String {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        val hardware = Build.HARDWARE
        val board = Build.BOARD
        return if (model
                .lowercase(Locale.getDefault())
                .startsWith(manufacturer.lowercase(Locale.getDefault()))
        ) {
            "${capitalize(model)} ($hardware, $board)"
        } else {
            "${capitalize(manufacturer)} $model ($hardware, $board)"
        }
    }

    fun getCpuInfo(): String = Build.SUPPORTED_ABIS.joinToString(", ")

    fun getMemoryInfo(context: Context): String {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memoryInfo = android.app.ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        val totalMemory = memoryInfo.totalMem / (1024 * 1024)
        val availableMemory = memoryInfo.availMem / (1024 * 1024)
        return "Total: ${totalMemory}MB, Available: ${availableMemory}MB (Threshold: ${memoryInfo.threshold / (1024 * 1024)}MB, Low Memory: ${memoryInfo.lowMemory})"
    }

    fun getDisplayInfo(context: Context): String {
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay
        } ?: return "Unknown"

        val metrics = context.resources.displayMetrics
        val resolution = "${metrics.widthPixels}x${metrics.heightPixels} (${metrics.densityDpi}dpi)"
        
        val modes = display.supportedModes
        val refreshRates = modes.map { "${it.refreshRate}Hz" }.distinct().joinToString(", ")
        
        val hdrCapabilities = display.hdrCapabilities
        val hdrTypes = hdrCapabilities.supportedHdrTypes.map {
            when (it) {
                android.view.Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> "Dolby Vision"
                android.view.Display.HdrCapabilities.HDR_TYPE_HDR10 -> "HDR10"
                android.view.Display.HdrCapabilities.HDR_TYPE_HLG -> "HLG"
                android.view.Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS -> "HDR10+"
                else -> "Type $it"
            }
        }.joinToString(", ")

        return "Resolution: $resolution, Refresh Rates: [$refreshRates], HDR: [$hdrTypes]"
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

    @Suppress("SameReturnValue")
    fun isDevice(): Boolean = true

    fun isTV(context: Context): Boolean = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

    fun isFireTV(): Boolean = deviceName().contains("AFT", true)

    fun isNvidiaShield(): Boolean = deviceName().contains("NVIDIA", true)

    fun isGoogleTV(): Boolean = deviceName().contains("Google Chromecast", true)

    fun hasHevcSupport(): Boolean = !isEmulator()

    fun hasAvifSupport(): Boolean =
        ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) && !isEmulator()) // Might need to test for TV & 13+ or Phone & 12+
}
