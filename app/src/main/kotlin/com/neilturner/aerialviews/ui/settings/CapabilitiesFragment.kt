@file:Suppress("unused")

package com.neilturner.aerialviews.ui.settings

import android.os.Bundle
import android.util.Log
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.services.CodecType
import com.neilturner.aerialviews.services.HDRFormat
import com.neilturner.aerialviews.services.getCodecs
import com.neilturner.aerialviews.services.getDisplay
import com.neilturner.aerialviews.utils.DeviceHelper

@Suppress("BooleanMethodIsAlwaysInverted")
class CapabilitiesFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_capabilities, rootKey)
        updateCapabilities()
    }

    private fun updateCapabilities() {
        val device = findPreference<Preference>("capabilities_device")
        val display = findPreference<Preference>("capabilities_display")
        val resolution = findPreference<Preference>("capabilities_resolution")
        val codecs = findPreference<Preference>("capabilities_codecs")
        val decoders = findPreference<Preference>("capabilities_decoders")

        device?.summary = buildDeviceSummary()
        display?.summary = buildDisplaySummary()
        codecs?.summary = buildCodecSummary()
        decoders?.summary = buildDecoderSummary()
        resolution?.summary = buildResolutionSummary()
    }

    private fun buildDeviceSummary(): String {
        var summary = ""

        summary += "Model: ${DeviceHelper.deviceName()}\n"
        summary += "Android: ${DeviceHelper.androidVersion()}"

        return summary
    }

    private fun buildDisplaySummary(): String {
        var summary = ""
        var supportsHDR10 = "No"
        var supportsDolbyVision = "No"

        val display = getDisplay(activity)
        if (display.supportsHDR && display.hdrFormats.isNotEmpty()) {
            if (display.hdrFormats.contains(HDRFormat.DOLBY_VISION)) {
                supportsDolbyVision = "Yes"
            }
            if (display.hdrFormats.contains(HDRFormat.HDR10)) {
                supportsHDR10 = "Yes"
            }
        }

        summary += "Supports HDR10: $supportsHDR10\n"
        summary += "Supports Dolby Vision: $supportsDolbyVision"

        return summary
    }

    private fun buildResolutionSummary(): String {
        val display = getDisplay(activity)
        var summary = "UI resolution: ${display.renderOutput}"
        if (display.physicalOutput != null) {
            summary += "\nMax. video resolution: ${display.physicalOutput}"
        }
        return summary
    }

    private fun buildCodecSummary(): String {
        var summary = ""
        var foundAVC = "Not Found"
        var foundHEVC = "Not Found"
        var foundDolbyVision = "Not Found"

        getCodecs().forEach { codec ->
            if (isCodecOfType(codec.mimeTypes, "avc")) {
                foundAVC = "Found"
            }

            if (isCodecOfType(codec.mimeTypes, "hevc")) {
                foundHEVC = "Found"
            }

            if (isCodecOfType(codec.mimeTypes, "dolby")) {
                foundDolbyVision = "Found"
            }
        }

        summary += "AVC: $foundAVC\n"
        summary += "HEVC: $foundHEVC\n"
        summary += "Dolby Vision: $foundDolbyVision"

        return summary
    }

    private fun buildDecoderSummary(): String {
        var summary = ""

        val allCodecs = getCodecs().filter {
            it.codingFunction == CodecType.DECODER && isVideoCodec(it.mimeTypes)
        }

        if (allCodecs.isNotEmpty()) {
            summary = allCodecs.joinToString(", ", "", "", -1, "") { it.name }
        }

        Log.i("", "Decoders found: ${allCodecs.count()}")
        return summary
    }

    private fun isVideoCodec(codecs: Array<String>): Boolean {
        val videoCodecs = codecs.filter {
            it.contains("video", true) &&
                (
                    it.contains("avc", true) ||
                        it.contains("hevc", true) ||
                        it.contains("dolby", true)
                    )
        }
        return videoCodecs.isNotEmpty()
    }

    private fun isCodecOfType(codecs: Array<String>, type: String): Boolean {
        val videoCodecs = codecs.filter {
            it.contains("video", true) &&
                it.contains(type, true)
        }
        return videoCodecs.isNotEmpty()
    }
}
