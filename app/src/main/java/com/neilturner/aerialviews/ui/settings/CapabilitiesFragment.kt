package com.neilturner.aerialviews.ui.settings

import android.content.res.Resources
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
import com.neilturner.aerialviews.utils.FirebaseHelper

class CapabilitiesFragment : PreferenceFragmentCompat() {
    private lateinit var resources: Resources

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.settings_capabilities, rootKey)
        resources = context?.resources!!

        updateCapabilities()
    }

    override fun onResume() {
        super.onResume()
        FirebaseHelper.logScreenView("Capabilities", TAG)
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
        summary += String.format(resources.getString(R.string.capabilities_model), DeviceHelper.deviceName()) + "\n"
        summary += String.format(resources.getString(R.string.capabilities_android), DeviceHelper.androidVersion())
        return summary
    }

    private fun buildDisplaySummary(): String {
        var summary = ""
        var supportsHDR10 = resources.getString(R.string.capabilities_no)
        var supportsDolbyVision = resources.getString(R.string.capabilities_no)

        val display = getDisplay(activity)
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
        val display = getDisplay(activity)
        var summary = String.format(resources.getString(R.string.capabilities_ui_resolution), display.renderOutput)
        if (display.physicalOutput != null) {
            summary += "\n" + String.format(resources.getString(R.string.capabilities_max_video_resolution), display.physicalOutput)
        }
        return summary
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

        Log.i("", "Decoders found: ${allCodecs.count()}")
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

    companion object {
        private const val TAG = "CapabilitiesFragment"
    }
}
