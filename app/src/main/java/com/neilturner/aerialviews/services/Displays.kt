@file:Suppress("MemberVisibilityCanBePrivate")

package com.neilturner.aerialviews.services

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.WindowManager
import androidx.media3.common.util.Util
import android.view.Display as NativeDisplay

// https://github.com/technogeek00/android-device-media-information/blob/master/app/src/main/java/com/zacharycava/devicemediainspector/sources/Displays.kt

enum class HDRFormat {
    DOLBY_VISION,
    HDR10,
    HDR10_PLUS,
    HLG,
    UNKNOWN,
}

fun hdrTypeToFormat(value: Int): HDRFormat =
    when (value) {
        NativeDisplay.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> HDRFormat.DOLBY_VISION
        NativeDisplay.HdrCapabilities.HDR_TYPE_HDR10 -> HDRFormat.HDR10
        NativeDisplay.HdrCapabilities.HDR_TYPE_HDR10_PLUS -> HDRFormat.HDR10_PLUS
        NativeDisplay.HdrCapabilities.HDR_TYPE_HLG -> HDRFormat.HLG
        else -> HDRFormat.UNKNOWN
    }

enum class PowerState {
    OFF,
    ON,
    DOZE,
    DOZE_SUSPEND,
    ON_SUSPEND,
    UNKNOWN,
}

fun displayStateToPowerState(value: Int): PowerState =
    when (value) {
        NativeDisplay.STATE_OFF -> PowerState.OFF
        NativeDisplay.STATE_ON -> PowerState.ON
        NativeDisplay.STATE_DOZE -> PowerState.DOZE
        NativeDisplay.STATE_DOZE_SUSPEND -> PowerState.DOZE_SUSPEND
        NativeDisplay.STATE_ON_SUSPEND -> PowerState.ON_SUSPEND
        else -> PowerState.UNKNOWN
    }

class OutputDescription(
    val id: Int,
    val width: Int,
    val height: Int,
    val refreshRate: Float,
) {
    override fun toString(): String {
        val formattedRefreshRate = "%.2f".format(refreshRate.toDouble())
        return "${width}x$height @ ${formattedRefreshRate}hz"
    }
}

@Suppress("DEPRECATION")
@SuppressLint("UnsafeOptInUsageError")
class Display(
    source: NativeDisplay,
    windowManager: WindowManager,
    context: Context,
) {
    val name: String = source.name
    val id: Int = source.displayId
    val mode: NativeDisplay.Mode?

    // val valid: Boolean = source.isValid
    val state: PowerState = displayStateToPowerState(source.state)

    // flags
    val isPresentation: Boolean
    val isPrivate: Boolean
    val isRound: Boolean
    val isSecure: Boolean
    val supportsProtectedBuffers: Boolean

    val renderOutput: OutputDescription
    val physicalOutput: OutputDescription?
    val supportedModes: List<OutputDescription>

    // HDR information
    val supportsHDR: Boolean
    val minimumLuminance: Float?
    val maximumLuminance: Float?
    val hdrFormats: List<HDRFormat>

    // Wide Gamut information
    val supportsWideColorGamut: Boolean
    val wideColorGamut: String?

    init {
        // compute the actual flags
        val flags = source.flags
        isPresentation = (flags and NativeDisplay.FLAG_PRESENTATION) == NativeDisplay.FLAG_PRESENTATION
        isPrivate = (flags and NativeDisplay.FLAG_PRIVATE) == NativeDisplay.FLAG_PRIVATE
        isSecure = (flags and NativeDisplay.FLAG_SECURE) == NativeDisplay.FLAG_SECURE
        supportsProtectedBuffers = (flags and NativeDisplay.FLAG_SUPPORTS_PROTECTED_BUFFERS) ==
            NativeDisplay.FLAG_SUPPORTS_PROTECTED_BUFFERS

        isRound =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                (flags and NativeDisplay.FLAG_ROUND) == NativeDisplay.FLAG_ROUND
            } else {
                false
            }

        // Get render sizes
        renderOutput =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val metrics = windowManager.currentWindowMetrics
                OutputDescription(-1, metrics.bounds.width(), metrics.bounds.height(), source.refreshRate)
            } else {
                val renderPoint = Point()
                @Suppress("DEPRECATION")
                source.getSize(renderPoint)
                OutputDescription(-1, renderPoint.x, renderPoint.y, source.refreshRate)
            }

        mode =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                source.mode
            } else {
                null
            }

        // If available on device get display mode
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val mode = source.mode
            // Use ExoPlayer util to get display dimensions as it covers more edge cases
            val displaySize = Util.getCurrentDisplayModeSize(context, source)
            physicalOutput =
                OutputDescription(mode.modeId, displaySize.x, displaySize.y, mode.refreshRate)
            supportedModes =
                source.supportedModes.map {
                    OutputDescription(
                        it.modeId,
                        it.physicalWidth,
                        it.physicalHeight,
                        it.refreshRate,
                    )
                }
        } else {
            physicalOutput = null
            supportedModes = listOf()
        }

        // Check HDR Capabilities if available on device
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // source.mode.supportedHdrTypes
            val capabilities = source.hdrCapabilities

            supportsHDR =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    source.isHdr
                } else {
                    capabilities != null
                }

            hdrFormats =
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    capabilities?.supportedHdrTypes?.map { hdrTypeToFormat(it) } ?: listOf()
                } else {
                    source.mode.supportedHdrTypes.map { hdrTypeToFormat(it) }
                }

            minimumLuminance = capabilities?.desiredMinLuminance
            maximumLuminance = capabilities?.desiredMaxLuminance
        } else {
            supportsHDR = false
            minimumLuminance = null
            maximumLuminance = null
            hdrFormats = listOf()
        }

        // Check Wide Gamut Space Support
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            supportsWideColorGamut = source.isWideColorGamut

            wideColorGamut =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    source.preferredWideGamutColorSpace?.name
                } else {
                    null
                }
        } else {
            supportsWideColorGamut = false
            wideColorGamut = null
        }
    }
}

fun getDisplay(activity: Activity?): Display {
    val displayManager = activity?.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    val display = displayManager.displays[0]
    val context = activity.applicationContext
    val windowManager = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    return Display(display, windowManager, context)
}
