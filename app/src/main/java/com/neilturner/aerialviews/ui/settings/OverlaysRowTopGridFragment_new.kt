package com.neilturner.aerialviews.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.OverlayType
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.utils.SlotHelper

class OverlaysRowTopGridFragment : Fragment() {

    // Preference containers (using correct IDs from layout)
    private lateinit var preferenceTopLeft: LinearLayout
    private lateinit var preferenceTopRight: LinearLayout
    private lateinit var preferenceBottomLeft: LinearLayout
    private lateinit var preferenceBottomRight: LinearLayout

    // Title and summary views
    private lateinit var titleTopLeft: TextView
    private lateinit var summaryTopLeft: TextView
    private lateinit var titleTopRight: TextView
    private lateinit var summaryTopRight: TextView
    private lateinit var titleBottomLeft: TextView
    private lateinit var summaryBottomLeft: TextView
    private lateinit var titleBottomRight: TextView
    private lateinit var summaryBottomRight: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_overlays_row_top_grid, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize views
        initializeViews(view)
        
        // Set up click listeners
        setupClickListeners()
        
        // Load initial values
        updateSummaries()
        
        // Set initial focus
        preferenceTopLeft.requestFocus()
    }

    private fun initializeViews(view: View) {
        // Preference containers
        preferenceTopLeft = view.findViewById(R.id.preference_top_left)
        preferenceTopRight = view.findViewById(R.id.preference_top_right)
        preferenceBottomLeft = view.findViewById(R.id.preference_bottom_left)
        preferenceBottomRight = view.findViewById(R.id.preference_bottom_right)

        // Title views
        titleTopLeft = view.findViewById(R.id.title_top_left)
        titleTopRight = view.findViewById(R.id.title_top_right)
        titleBottomLeft = view.findViewById(R.id.title_bottom_left)
        titleBottomRight = view.findViewById(R.id.title_bottom_right)

        // Summary views
        summaryTopLeft = view.findViewById(R.id.summary_top_left)
        summaryTopRight = view.findViewById(R.id.summary_top_right)
        summaryBottomLeft = view.findViewById(R.id.summary_bottom_left)
        summaryBottomRight = view.findViewById(R.id.summary_bottom_right)

        // Set titles
        titleTopLeft.text = getString(R.string.appearance_top_left_lower_slot)
        titleTopRight.text = getString(R.string.appearance_top_right_lower_slot)
        titleBottomLeft.text = getString(R.string.appearance_top_left_upper_slot)
        titleBottomRight.text = getString(R.string.appearance_top_right_upper_slot)
    }

    private fun setupClickListeners() {
        preferenceTopLeft.setOnClickListener {
            showOverlaySelectionDialog("slot_top_left1") {
                updateSummaries()
            }
        }

        preferenceTopRight.setOnClickListener {
            showOverlaySelectionDialog("slot_top_right1") {
                updateSummaries()
            }
        }

        preferenceBottomLeft.setOnClickListener {
            showOverlaySelectionDialog("slot_top_left2") {
                updateSummaries()
            }
        }

        preferenceBottomRight.setOnClickListener {
            showOverlaySelectionDialog("slot_top_right2") {
                updateSummaries()
            }
        }
    }    private fun updateSummaries() {
        summaryTopLeft.text = SlotHelper.getOverlaySummary("slot_top_left1")
        summaryTopRight.text = SlotHelper.getOverlaySummary("slot_top_right1")
        summaryBottomLeft.text = SlotHelper.getOverlaySummary("slot_top_left2")
        summaryBottomRight.text = SlotHelper.getOverlaySummary("slot_top_right2")
    }

    private fun showOverlaySelectionDialog(slotKey: String, onSelectionChanged: () -> Unit) {
        val overlayData = SlotHelper.entriesAndValues(requireContext())
        val overlayEntries = overlayData.first
        val overlayValues = overlayData.second
        val currentValue = when (slotKey) {
            "slot_top_left1" -> GeneralPrefs.slotTopLeft1?.toString() ?: ""
            "slot_top_left2" -> GeneralPrefs.slotTopLeft2?.toString() ?: ""
            "slot_top_right1" -> GeneralPrefs.slotTopRight1?.toString() ?: ""
            "slot_top_right2" -> GeneralPrefs.slotTopRight2?.toString() ?: ""
            else -> ""
        }

        val currentIndex = overlayValues.indexOf(currentValue).takeIf { it >= 0 } ?: 0

        AlertDialog.Builder(requireContext())
            .setTitle(getSlotTitle(slotKey))
            .setSingleChoiceItems(overlayEntries, currentIndex) { dialog, which ->
                val selectedValue = overlayValues[which]
                savePreference(slotKey, selectedValue)
                onSelectionChanged()
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun getSlotTitle(slotKey: String): String {
        return when (slotKey) {
            "slot_top_left1" -> getString(R.string.appearance_top_left_lower_slot)
            "slot_top_left2" -> getString(R.string.appearance_top_left_upper_slot)
            "slot_top_right1" -> getString(R.string.appearance_top_right_lower_slot)
            "slot_top_right2" -> getString(R.string.appearance_top_right_upper_slot)
            else -> ""
        }
    }

    private fun savePreference(slotKey: String, value: String) {
        val overlayType = try {
            OverlayType.valueOf(value)
        } catch (e: Exception) {
            OverlayType.EMPTY
        }
        
        when (slotKey) {
            "slot_top_left1" -> GeneralPrefs.slotTopLeft1 = overlayType
            "slot_top_left2" -> GeneralPrefs.slotTopLeft2 = overlayType
            "slot_top_right1" -> GeneralPrefs.slotTopRight1 = overlayType
            "slot_top_right2" -> GeneralPrefs.slotTopRight2 = overlayType
        }
    }

    override fun onResume() {
        super.onResume()
        updateSummaries()
    }
}
