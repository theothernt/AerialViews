package com.neilturner.aerialviews.ui.settings

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.neilturner.aerialviews.R

/**
 * A custom preference that displays two dropdown menus side by side in a single row.
 */
class DualDropdownPreference
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : Preference(context, attrs, defStyleAttr) {
        private var leftPreference: ListPreference? = null
        private var rightPreference: ListPreference? = null

        init {
            layoutResource = R.layout.preference_dual_dropdown_row
        }

        /**
         * Set the left dropdown preference
         */
        fun setLeftPreference(preference: ListPreference) {
            leftPreference = preference
            notifyChanged()
        }

        /**
         * Set the right dropdown preference
         */
        fun setRightPreference(preference: ListPreference) {
            rightPreference = preference
            notifyChanged()
        }

        override fun onBindViewHolder(holder: PreferenceViewHolder) {
            super.onBindViewHolder(holder)

            val leftContainer = holder.findViewById(R.id.left_dropdown_container) as FrameLayout
            val rightContainer = holder.findViewById(R.id.right_dropdown_container) as FrameLayout

            // Clear previous views
            leftContainer.removeAllViews()
            rightContainer.removeAllViews()

            // Add the left preference
            leftPreference?.let { pref ->
                // Create a view for the preference
                val inflater = LayoutInflater.from(context)
                val preferenceHolder =
                    PreferenceViewHolder.createInstanceForTests(
                        inflater.inflate(
                            androidx.preference.R.layout.preference_material,
                            leftContainer,
                            false,
                        ),
                    )
                pref.onBindViewHolder(preferenceHolder)

                // Add the view to our container
                val view = preferenceHolder.itemView
                if (view.parent != null) {
                    (view.parent as ViewGroup).removeView(view)
                }
                leftContainer.addView(view)
            }

            // Add the right preference
            rightPreference?.let { pref ->
                // Create a view for the preference
                val inflater = LayoutInflater.from(context)
                val preferenceHolder =
                    PreferenceViewHolder.createInstanceForTests(
                        inflater.inflate(
                            androidx.preference.R.layout.preference_material,
                            rightContainer,
                            false,
                        ),
                    )
                pref.onBindViewHolder(preferenceHolder)

                // Add the view to our container
                val view = preferenceHolder.itemView
                if (view.parent != null) {
                    (view.parent as ViewGroup).removeView(view)
                }
                rightContainer.addView(view)
            }
        }

        override fun onClick() {
            // This preference doesn't have its own click action
        }
    }
