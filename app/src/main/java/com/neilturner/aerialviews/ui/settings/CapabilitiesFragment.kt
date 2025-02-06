package com.neilturner.aerialviews.ui.settings

import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import androidx.preference.Preference
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.services.CodecType
import com.neilturner.aerialviews.services.Display
import com.neilturner.aerialviews.services.HDRFormat
import com.neilturner.aerialviews.services.OutputDescription
import com.neilturner.aerialviews.services.getCodecs
import com.neilturner.aerialviews.services.getDisplay
import com.neilturner.aerialviews.utils.DeviceHelper
import com.neilturner.aerialviews.utils.FirebaseHelper
import com.neilturner.aerialviews.utils.MenuStateFragment
import com.neilturner.aerialviews.utils.roundTo
import timber.log.Timber

class CapabilitiesFragment : MenuStateFragment() {
    private lateinit var resources: Resources
    private lateinit var display: Display

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.settings_capabilities, rootKey)
        resources = context?.resources!!
        display = getDisplay(activity)

        updateCapabilities()
    }

    override fun onResume() {
        super.onResume()
        FirebaseHelper.logScreenView("Capabilities", this)
    }

    private fun updateCapabilities() {
        val device = findPreference<Preference>("capabilities_device")
        val display = findPreference<Preference>("capabilities_display")
        val resolution = findPreference<Preference>("capabilities_resolution")
        val codecs = findPreference<Preference>("capabilities_codecs")
        val decoders = findPreference<Preference>("capabilities_decoders")
        val resolutions = findPreference<Preference>("capabilities_resolutions")
        val refreshRates = findPreference<Preference>("capabilities_refresh_rates")

        device?.summary = buildDeviceSummary()
        display?.summary = buildDisplaySummary()
        codecs?.summary = buildCodecSummary()
        decoders?.summary = buildDecoderSummary()
        resolution?.summary = buildResolutionSummary()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            resolutions?.summary = buildResolutionsSummary()
            refreshRates?.summary = buildRefreshRatesSummary()

            resolutions?.isVisible = true
            refreshRates?.isVisible = true
        }
    }

    private fun buildDeviceSummary(): String {
        var summary = ""
        summary += String.format(resources.getString(R.string.capabilities_model), DeviceHelper.deviceName()) + "\n"
        summary += String.format(resources.getString(R.string.capabilities_android), DeviceHelper.androidVersion())
        return summary
    }

    private fun buildDisplaySummary(): String {
        var summary = ""
        var supportsHDR10 = resources.getString(R.string.capabilities_no)
        var supportsDolbyVision = resources.getString(R.string.capabilities_no)

        if (display.supportsHDR && display.hdrFormats.isNotEmpty()) {
            if (display.hdrFormats.contains(HDRFormat.DOLBY_VISION)) {
                supportsDolbyVision = resources.getString(R.string.capabilities_yes)
            }
            if (display.hdrFormats.contains(HDRFormat.HDR10)) {
                supportsHDR10 = resources.getString(R.string.capabilities_yes)
            }
        }

        summary += String.format(resources.getString(R.string.capabilities_supports_hdr10), supportsHDR10) + "\n"
        summary += String.format(resources.getString(R.string.capabilities_supports_dolby_vision), supportsDolbyVision)
        return summary
    }

    private fun buildResolutionSummary(): String {
        var summary = String.format(resources.getString(R.string.capabilities_ui_resolution), display.renderOutput)
        if (display.physicalOutput != null) {
            summary += "\n" + String.format(resources.getString(R.string.capabilities_max_video_resolution), display.physicalOutput)
        }
        return summary
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun buildRefreshRatesSummary(): String {
        val activeMode = display.mode
        val sortedModes = display.supportedModes.sortedByDescending { it.refreshRate }
        val filteredModes = mutableListOf<OutputDescription>()
        for (mode in sortedModes) {
            if (mode.width == activeMode?.physicalWidth &&
                mode.height == activeMode.physicalHeight
            ) {
                filteredModes.add(mode)
            }
        }

        return filteredModes.map { it.refreshRate.roundTo(2).toString() + "Hz"}.distinct().joinToString(", ")
    }

    private fun buildResolutionsSummary(): String {
        val sortedModes = display.supportedModes.sortedByDescending { it.height }
        return sortedModes.map { it.width.toString() + "x" + it.height.toString() }.distinct().joinToString(", ")
    }

    private fun buildCodecSummary(): String {
        var summary = ""
        var foundAVC = resources.getString(R.string.capabilities_not_found)
        var foundHEVC = resources.getString(R.string.capabilities_not_found)
        var foundDolbyVision = resources.getString(R.string.capabilities_not_found)

        getCodecs().forEach { codec ->
            if (isCodecOfType(codec.mimeTypes, "avc")) {
                foundAVC = resources.getString(R.string.capabilities_found)
            }

            if (isCodecOfType(codec.mimeTypes, "hevc")) {
                foundHEVC = resources.getString(R.string.capabilities_found)
            }

            if (isCodecOfType(codec.mimeTypes, "dolby")) {
                foundDolbyVision = resources.getString(R.string.capabilities_found)
            }
        }

        summary += String.format(resources.getString(R.string.capabilities_avc), foundAVC) + "\n"
        summary += String.format(resources.getString(R.string.capabilities_hevc), foundHEVC) + "\n"
        summary += String.format(resources.getString(R.string.capabilities_dolby_vision), foundDolbyVision)
        return summary
    }

    private fun buildDecoderSummary(): String {
        var summary = ""

        val allCodecs =
            getCodecs().filter {
                it.codingFunction == CodecType.DECODER && isVideoCodec(it.mimeTypes)
            }

        if (allCodecs.isNotEmpty()) {
            summary = allCodecs.joinToString(", ", "", "", -1, "") { it.name }
        }

        Timber.i("Decoders found: ${allCodecs.count()}")
        return summary
    }

    private fun isVideoCodec(codecs: Array<String>): Boolean {
        val videoCodecs =
            codecs.filter {
                it.contains("video", true) &&
                    (
                        it.contains("avc", true) ||
                            it.contains("hevc", true) ||
                            it.contains("dolby", true)
                    )
            }
        return videoCodecs.isNotEmpty()
    }

    private fun isCodecOfType(
        codecs: Array<String>,
        type: String,
    ): Boolean {
        val videoCodecs =
            codecs.filter {
                it.contains("video", true) &&
                    it.contains(type, true)
            }
        return videoCodecs.isNotEmpty()
    }
}
