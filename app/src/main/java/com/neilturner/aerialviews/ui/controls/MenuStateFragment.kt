package com.neilturner.aerialviews.ui.controls

import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.neilturner.aerialviews.ui.helpers.DeviceHelper
import com.neilturner.aerialviews.utils.FirebaseHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

abstract class MenuStateFragment : PreferenceFragmentCompat() {
    private var position = -1

    override fun onResume() {
        super.onResume()

        if (!DeviceHelper.isTV(requireContext())) {
            return
        }

        lifecycleScope.launch {
            delay(60)
            tryRequestFocus()
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
            Timber.Forest.e(ex)
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

    override fun onDisplayPreferenceDialog(preference: Preference) {
        if (preference is EditTextPreference && DeviceHelper.isTV(requireContext())) {
            val dialogFragmentTag = "androidx.preference.PreferenceFragment.DIALOG"
            if (parentFragmentManager.findFragmentByTag(dialogFragmentTag) != null) {
                return
            }

            val dialogFragment = TvEditTextPreferenceDialogFragmentCompat.newInstance(preference.key)
            @Suppress("DEPRECATION")
            dialogFragment.setTargetFragment(this, 0)
            dialogFragment.show(parentFragmentManager, dialogFragmentTag)
        } else {
            super.onDisplayPreferenceDialog(preference)
        }
    }
}
