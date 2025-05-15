package com.neilturner.aerialviews.utils

import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceFragmentCompat
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
            delay(50)
            if (position != -1) {
                val item = listView.findViewHolderForAdapterPosition(position)?.itemView
                item?.requestFocus()
            }
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
                while (view.parent != null && view.parent !== listView && view.parent is android.view.View) {
                    view = view.parent as android.view.View
                }

                // Only try to get position if the view is a direct child of the RecyclerView
                if (view.parent === listView) {
                    position = listView.layoutManager?.getPosition(view) ?: -1
                }
            } catch (e: Exception) {
                Timber.e(e)
            }
        }
    }
}
