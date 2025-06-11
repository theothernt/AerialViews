package com.neilturner.aerialviews.ui.preferences

import android.app.AlertDialog
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.OverlayType
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.utils.SlotHelper

class HorizontalSlotsPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : Preference(context, attrs, defStyleAttr) {

    private var leftSlot1Summary: TextView? = null
    private var leftSlot2Summary: TextView? = null
    private var rightSlot1Summary: TextView? = null
    private var rightSlot2Summary: TextView? = null

    init {
        layoutResource = R.layout.preference_horizontal_slots
        isSelectable = false
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        // Get references to summary TextViews
        leftSlot1Summary = holder.findViewById(R.id.left_slot1_summary) as? TextView
        leftSlot2Summary = holder.findViewById(R.id.left_slot2_summary) as? TextView
        rightSlot1Summary = holder.findViewById(R.id.right_slot1_summary) as? TextView
        rightSlot2Summary = holder.findViewById(R.id.right_slot2_summary) as? TextView

        // Update summaries with current values
        updateSummaries()

        // Set click listeners for each slot
        holder.findViewById(R.id.left_slot1_container)?.setOnClickListener {
            showSlotDialog("slot_top_left1", GeneralPrefs.slotTopLeft1) { newValue ->
                GeneralPrefs.slotTopLeft1 = newValue
                updateSummaries()
                notifyDependencyChange(false)
            }
        }

        holder.findViewById(R.id.left_slot2_container)?.setOnClickListener {
            showSlotDialog("slot_top_left2", GeneralPrefs.slotTopLeft2) { newValue ->
                GeneralPrefs.slotTopLeft2 = newValue
                updateSummaries()
                notifyDependencyChange(false)
            }
        }

        holder.findViewById(R.id.right_slot1_container)?.setOnClickListener {
            showSlotDialog("slot_top_right1", GeneralPrefs.slotTopRight1) { newValue ->
                GeneralPrefs.slotTopRight1 = newValue
                updateSummaries()
                notifyDependencyChange(false)
            }
        }

        holder.findViewById(R.id.right_slot2_container)?.setOnClickListener {
            showSlotDialog("slot_top_right2", GeneralPrefs.slotTopRight2) { newValue ->
                GeneralPrefs.slotTopRight2 = newValue
                updateSummaries()
                notifyDependencyChange(false)
            }
        }
    }

    private fun updateSummaries() {
        val overlayData = SlotHelper.entriesAndValues(context)
        val entries = overlayData.first

        leftSlot1Summary?.text = getOverlayDisplayName(GeneralPrefs.slotTopLeft1, entries)
        leftSlot2Summary?.text = getOverlayDisplayName(GeneralPrefs.slotTopLeft2, entries)
        rightSlot1Summary?.text = getOverlayDisplayName(GeneralPrefs.slotTopRight1, entries)
        rightSlot2Summary?.text = getOverlayDisplayName(GeneralPrefs.slotTopRight2, entries)
    }

    private fun getOverlayDisplayName(overlayType: OverlayType?, entries: Array<String>): String {
        val type = overlayType ?: OverlayType.entries.first()
        val index = OverlayType.valueOf(type.toString()).ordinal
        return entries.getOrNull(index) ?: entries.first()
    }

    private fun showSlotDialog(
        slotKey: String,
        currentValue: OverlayType?,
        onValueSelected: (OverlayType) -> Unit
    ) {
        val overlayData = SlotHelper.entriesAndValues(context)
        val entries = overlayData.first
        val values = overlayData.second

        val currentType = currentValue ?: OverlayType.entries.first()
        val currentIndex = values.indexOf(currentType.toString())

        AlertDialog.Builder(context)
            .setTitle(getSlotTitle(slotKey))
            .setSingleChoiceItems(entries, currentIndex) { dialog, which ->
                val selectedValue = OverlayType.valueOf(values[which])
                onValueSelected(selectedValue)
                
                // Remove duplicates from other slots
                removeDuplicateOverlays(slotKey, selectedValue)
                
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun getSlotTitle(slotKey: String): String {
        return when (slotKey) {
            "slot_top_left1" -> context.getString(R.string.appearance_slot1_title)
            "slot_top_left2" -> context.getString(R.string.appearance_slot2_title)
            "slot_top_right1" -> context.getString(R.string.appearance_slot1_title)
            "slot_top_right2" -> context.getString(R.string.appearance_slot2_title)
            else -> ""
        }
    }

    private fun removeDuplicateOverlays(changedKey: String, newValue: OverlayType) {
        // This mirrors the logic from SlotHelper.removeDuplicateOverlays
        // but adapted for our custom preference
        
        if (newValue == OverlayType.EMPTY) return

        val slotsToCheck = mapOf(
            "slot_top_left1" to { GeneralPrefs.slotTopLeft1 },
            "slot_top_left2" to { GeneralPrefs.slotTopLeft2 },
            "slot_top_right1" to { GeneralPrefs.slotTopRight1 },
            "slot_top_right2" to { GeneralPrefs.slotTopRight2 }
        )

        slotsToCheck.forEach { (key, getter) ->
            if (key != changedKey && getter() == newValue) {
                when (key) {
                    "slot_top_left1" -> GeneralPrefs.slotTopLeft1 = OverlayType.EMPTY
                    "slot_top_left2" -> GeneralPrefs.slotTopLeft2 = OverlayType.EMPTY
                    "slot_top_right1" -> GeneralPrefs.slotTopRight1 = OverlayType.EMPTY
                    "slot_top_right2" -> GeneralPrefs.slotTopRight2 = OverlayType.EMPTY
                }
            }
        }
        
        updateSummaries()
    }
}
