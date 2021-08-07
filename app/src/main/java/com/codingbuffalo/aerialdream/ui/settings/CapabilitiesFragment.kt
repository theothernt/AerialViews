package com.codingbuffalo.aerialdream.ui.settings

import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.codingbuffalo.aerialdream.R
import com.codingbuffalo.aerialdream.services.CodecType
import com.codingbuffalo.aerialdream.services.getCodecs
import com.codingbuffalo.aerialdream.services.getDisplay

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
        resolution.summary = buildResolutionSummary()
        codecs.summary = buildCodecsSummary()
    }

    private fun buildDisplaySummary(): String {
        val display = getDisplay(activity)
        var summary = "Supports HDR: ${display.supportsHDR}"
        if (display.supportsHDR && display.hdrFormats.isNotEmpty()) {
            summary += "\nHDR Formats: ${display.hdrFormats.joinToString(", ")}"
        }
        return summary
    }

    private fun buildResolutionSummary(): String {
        val display = getDisplay(activity)
        var summary = "Render Output: ${display.renderOutput}"
        if (display.physicalOutput != null) {
            summary += "\nPhysical Output: ${display.physicalOutput}"
            //summary += "\nActive Display Mode: ${display.physicalOutput.id}"
        }
        return summary
    }

    private fun buildCodecsSummary(): String {
        var summary = ""
        getCodecs().forEach{ codec ->
            if ((codec.name.contains("hevc") || codec.name.contains("dolby")) &&
                    codec.codingFunction == CodecType.DECODER) {
                summary += "${codec.name}\n"
                //summary += "\n${codec.mimeTypes.joinToString(", ")}"
            }
        }
        return summary
    }
}