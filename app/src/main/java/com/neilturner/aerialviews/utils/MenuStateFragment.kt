package com.neilturner.aerialviews.utils

import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceFragmentCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

abstract class MenuStateFragment : PreferenceFragmentCompat() {

    private var position = -1

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            delay(50)
            if (position != -1) {
                val item = listView.findViewHolderForAdapterPosition(position)?.itemView
                item?.requestFocus()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        val view = listView.findFocus()
        if (view != null && DeviceHelper.isTV(requireContext())) {
            try {
                position = listView.layoutManager?.getPosition(view) ?: -1
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}