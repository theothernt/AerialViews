package com.neilturner.aerialviews.ui.settings

import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.services.CodecType
import com.neilturner.aerialviews.services.HDRFormat
import com.neilturner.aerialviews.services.getCodecs
import com.neilturner.aerialviews.services.getDisplay

class CapabilitiesFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_capabilities, rootKey)
        updateCapabilities()
    }

    private fun updateCapabilities()
    {
        val display = findPreference<ListPreference>("capabilities_display") as Preference
        val resolution = findPreference<ListPreference>("capabilities_resolution") as Preference
        val codecs = findPreference<ListPreference>("capabilities_codecs") as Preference

        display.summary = buildDisplaySummary()
        codecs.summary = buildCodecsSummary()
        resolution.summary = buildResolutionSummary()
    }

    private fun buildDisplaySummary(): String {
        var summary = ""
        var supportsHDR10 = "False"
        var supportsDolbyVision = "False"

        val display = getDisplay(activity)
        if (display.supportsHDR && display.hdrFormats.isNotEmpty()) {
            if (display.hdrFormats.contains(HDRFormat.DOLBY_VISION))
                supportsDolbyVision = "True"
            if (display.hdrFormats.contains(HDRFormat.HDR10))
                supportsHDR10 = "True"
        }

        summary += "Supports HDR10: $supportsHDR10\n"
        summary += "Supports Dolby Vision: $supportsDolbyVision\n"

        return summary
    }

    private fun buildResolutionSummary(): String {
        val display = getDisplay(activity)
        var summary = "Render Output: ${display.renderOutput}"
        if (display.physicalOutput != null) {
            summary += "\nPhysical Output: ${display.physicalOutput}"
        }
        return summary
    }

    private fun buildCodecsSummary(): String {
        var summary = ""
        val supportsAVC = "True"
        var supportsHEVC = "False"
        var supportsDolbyVision = "False"

        getCodecs().forEach{ codec ->
            if (codec.name.lowercase().contains("hevc") && codec.codingFunction == CodecType.DECODER)
                supportsHEVC = "True"

            if (codec.name.lowercase().contains("dolby") && codec.codingFunction == CodecType.DECODER)
                supportsDolbyVision = "True"
        }

        summary += "Supports AVC: $supportsAVC\n"
        summary += "Supports HEVC: $supportsHEVC\n"
        summary += "Supports Dolby Vision: $supportsDolbyVision\n"
        return summary
    }
}