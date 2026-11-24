package com.neilturner.aerialviews.ui.controls

import android.text.InputType
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import com.neilturner.aerialviews.ui.helpers.DeviceHelper
import com.neilturner.aerialviews.utils.FirebaseHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.time.Duration.Companion.milliseconds

// Fields that hold prose rather than values, where IME suggestions are genuinely useful
private val prosePreferenceKeys =
    setOf(
        "message_line1",
        "message_line2",
        "message_line3",
        "message_line4",
        "countdown_label",
        "countdown_target_message",
        "weather_location_custom_name",
    )

abstract class MenuStateFragment :
    PreferenceFragmentCompat(),
    PreferenceFragmentCompat.OnPreferenceDisplayDialogCallback {
    private var position = -1

    override fun onResume() {
        super.onResume()

        preferenceScreen?.disableValueFieldSuggestions()

        if (!DeviceHelper.isTV(requireContext())) {
            return
        }

        lifecycleScope.launch {
            delay(60.milliseconds)
            tryRequestFocus()
        }
    }

    // Text preferences hold values (host names, paths, URLs, credentials, date formats), so IME
    // dictionary suggestions are noise there. They also carry a spell-check popup whose "Add to
    // dictionary" item starts ACTION_USER_DICTIONARY_INSERT without resolving it first, which
    // throws ActivityNotFoundException on devices with no handler for it.
    private fun PreferenceGroup.disableValueFieldSuggestions() {
        for (index in 0 until preferenceCount) {
            when (val preference = getPreference(index)) {
                is PreferenceGroup -> {
                    preference.disableValueFieldSuggestions()
                }

                is EditTextPreference -> {
                    if (preference.key !in prosePreferenceKeys) {
                        preference.setOnBindEditTextListener { editText ->
                            val inputType = editText.inputType
                            if (inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT) {
                                editText.inputType = inputType or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                            }
                        }
                    }
                }
            }
        }
    }

    private fun tryRequestFocus() {
        try {
            if (position != -1 && listView != null && listView.adapter != null && listView.layoutManager != null) {
                val item = listView.findViewHolderForAdapterPosition(position)?.itemView
                item?.requestFocus()
            }
        } catch (ex: Exception) {
            FirebaseHelper.crashlyticsException(ex)
            Timber.e(ex)
        }
    }

    override fun onPause() {
        super.onPause()

        if (!DeviceHelper.isTV(requireContext())) {
            return
        }

        val focusedView = listView.findFocus()
        focusedView?.let {
            try {
                var view = it
                // Walk up the view hierarchy until we find a view that's a direct child of the RecyclerView
                while (view.parent != null && view.parent !== listView && view.parent is View) {
                    view = view.parent as View
                }

                // Only try to get position if the view is a direct child of the RecyclerView
                if (view.parent === listView) {
                    position = listView.layoutManager?.getPosition(view) ?: -1
                }
            } catch (ex: Exception) {
                FirebaseHelper.crashlyticsException(ex)
            }
        }
    }

    // Preferences with their own dialog layout are not known to PreferenceFragmentCompat
    override fun onPreferenceDisplayDialog(
        caller: PreferenceFragmentCompat,
        preference: Preference,
    ): Boolean {
        if (preference is OverlayColourPreference) {
            preference.showDialog()
            return true
        }
        return false
    }

    // The colour dialog is built from the Activity context, so it must not outlive this fragment
    override fun onDestroyView() {
        (preferenceManager.findPreference<OverlayColourPreference>("overlay_colour"))?.dismissDialog()
        super.onDestroyView()
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        if (preference is EditTextPreference && DeviceHelper.isTV(requireContext())) {
            val dialogFragmentTag = "androidx.preference.PreferenceFragment.DIALOG"
            if (parentFragmentManager.findFragmentByTag(dialogFragmentTag) != null) {
                return
            }

            val dialogFragment = TvEditTextPreference.newInstance(preference.key)
            @Suppress("DEPRECATION")
            dialogFragment.setTargetFragment(this, 0)
            dialogFragment.show(parentFragmentManager, dialogFragmentTag)
        } else {
            super.onDisplayPreferenceDialog(preference)
        }
    }
}
