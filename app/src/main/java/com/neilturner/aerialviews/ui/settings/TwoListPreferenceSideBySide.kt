package com.neilturner.aerialviews.ui.settings

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.neilturner.aerialviews.R

class TwoListPreferenceSideBySide @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : Preference(context, attrs, defStyleAttr) {

    private var _leftPreference: ListPreference? = null
    private var _rightPreference: ListPreference? = null
    
    private var leftTitle: String = ""
    private var leftSummary: String = ""
    private var rightTitle: String = ""
    private var rightSummary: String = ""

    init {
        layoutResource = R.layout.preference_two_list_side_by_side
        isSelectable = false
    }

    fun setLeftPreference(preference: ListPreference) {
        _leftPreference = preference
        leftTitle = preference.title?.toString() ?: ""
        leftSummary = preference.summary?.toString() ?: ""
        notifyChanged()
    }

    fun setRightPreference(preference: ListPreference) {
        _rightPreference = preference
        rightTitle = preference.title?.toString() ?: ""
        rightSummary = preference.summary?.toString() ?: ""
        notifyChanged()
    }

    fun updateLeftSummary(summary: String) {
        leftSummary = summary
        notifyChanged()
    }

    fun updateRightSummary(summary: String) {
        rightSummary = summary
        notifyChanged()
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        val leftContainer = holder.findViewById(R.id.left_preference_container)
        val rightContainer = holder.findViewById(R.id.right_preference_container)
        val leftTitleView = holder.findViewById(R.id.left_title) as TextView
        val leftSummaryView = holder.findViewById(R.id.left_summary) as TextView
        val rightTitleView = holder.findViewById(R.id.right_title) as TextView
        val rightSummaryView = holder.findViewById(R.id.right_summary) as TextView

        // Set up left preference
        leftTitleView.text = leftTitle
        leftSummaryView.text = leftSummary
        leftSummaryView.visibility = if (leftSummary.isNotEmpty()) View.VISIBLE else View.GONE

        // Set up right preference
        rightTitleView.text = rightTitle
        rightSummaryView.text = rightSummary
        rightSummaryView.visibility = if (rightSummary.isNotEmpty()) View.VISIBLE else View.GONE

        // Set click listeners
        leftContainer.setOnClickListener {
            _leftPreference?.performClick()
        }

        rightContainer.setOnClickListener {
            _rightPreference?.performClick()
        }

        // Handle focus for Android TV
        leftContainer.isFocusable = true
        rightContainer.isFocusable = true
        leftContainer.isClickable = true
        rightContainer.isClickable = true

        // Ensure proper focus handling
        leftContainer.setOnFocusChangeListener { view, hasFocus ->
            view.isSelected = hasFocus
        }

        rightContainer.setOnFocusChangeListener { view, hasFocus ->
            view.isSelected = hasFocus
        }
    }
}
