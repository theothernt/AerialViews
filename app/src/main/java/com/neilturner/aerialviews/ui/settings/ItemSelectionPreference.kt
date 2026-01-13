package com.neilturner.aerialviews.ui.settings

import android.app.AlertDialog
import android.content.Context

import android.util.AttributeSet
import android.widget.TextView
import android.widget.Toast
import androidx.preference.Preference

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.neilturner.aerialviews.R
import androidx.core.content.withStyledAttributes

class ItemSelectionPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.preferenceStyle
) : Preference(context, attrs, defStyleAttr) {

    private val items = mutableListOf<SelectableItem>()
    private var mDialogTitle: CharSequence? = null
    private var mDialogMessage: CharSequence? = null

    init {
        // Load items from XML attributes
        val a = context.obtainStyledAttributes(attrs, androidx.preference.R.styleable.ListPreference, defStyleAttr, 0)

        val entries = a.getTextArray(androidx.preference.R.styleable.ListPreference_entries)
        val entryValues = a.getTextArray(androidx.preference.R.styleable.ListPreference_entryValues)


        a.recycle()

        // Load dialog attributes
        context.withStyledAttributes(
            attrs,
            androidx.preference.R.styleable.DialogPreference,
            defStyleAttr,
            0
        ) {
            mDialogTitle = getString(androidx.preference.R.styleable.DialogPreference_dialogTitle)
            mDialogMessage =
                getString(androidx.preference.R.styleable.DialogPreference_dialogMessage)
        }

        if (entries != null && entryValues != null && entries.size == entryValues.size) {
            for (i in entries.indices) {
                // Ensure ID is an Int for now as per current logic, or adapt if strings needed
                // Assuming entryValues are numeric strings based on user request "1", "2" etc.
                val idStr = entryValues[i].toString()
                val id = idStr.toIntOrNull() ?: i // Fallback to index if not numeric
                items.add(SelectableItem(id, entries[i].toString()))
            }
        } else {
            // Fallback sample items if attributes missing
            for (i in 1..6) {
                items.add(SelectableItem(i, "Item $i"))
            }
        }

        // Load saved state
        loadState()
        updateSummary()
    }

    override fun onClick() {
        showDialog()
    }

    private fun showDialog() {
        val dialogView = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            setPadding(0, 16, 0, 16)
        }

        lateinit var adapter: ItemSelectionAdapter
        
        adapter = ItemSelectionAdapter(
            items = items,
            onSelectionChanged = {
                // Determine if we need to show any specific feedback
            },
            onMoveUp = { position ->
                adapter.moveUp(position)
            },
            onMoveDown = { position ->
                adapter.moveDown(position)
            }
        )

        dialogView.adapter = adapter

        // Create instruction text view
        val instructionsView = TextView(context).apply {
            text = mDialogMessage ?: ""
            setPadding(48, 32, 48, 16)
        }

        // Container for instructions + recyclerview
        val container = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            addView(instructionsView)
            addView(dialogView)
        }

        AlertDialog.Builder(context)
            .setTitle(title ?: "")
            .setView(container)
            .setPositiveButton(R.string.button_ok) { _, _ ->
                saveState()
                updateSummary()
            }
            .setNegativeButton(R.string.button_cancel) { _, _ ->
                // Reload state to discard changes
                loadState()
            }
            .show()
    }

    private fun saveState() {
        val selectedIds = items.filter { it.isSelected }
            .joinToString(",") { it.id.toString() }
        
        persistString(selectedIds)
        
        Toast.makeText(context, "Saved order: $selectedIds", Toast.LENGTH_SHORT).show()
    }

    private fun loadState() {
        val savedIds = getPersistedString("") ?: ""
        
        // Create a map for quick lookup
        val itemsMap = items.associateBy { it.id }
        val newItemsList = mutableListOf<SelectableItem>()
        
        // 1. Add selected items in saved order
        if (savedIds.isNotEmpty()) {
            val idList = savedIds.split(",").mapNotNull { it.toIntOrNull() }
            idList.forEach { id ->
                itemsMap[id]?.let { item ->
                    item.isSelected = true
                    newItemsList.add(item)
                }
            }
        }
        
        // 2. Add remaining unselected items
        items.forEach { item ->
            if (!item.isSelected) {
                newItemsList.add(item)
            }
        }
        
        // 3. Update the main list
        items.clear()
        items.addAll(newItemsList)
        
        // Reset sort orders based on new list position (optional, but good for consistency)
        items.forEachIndexed { index, item ->
            item.sortOrder = if (item.isSelected) index else -1
        }
    }

    private fun updateSummary() {
        val selectedItems = items.filter { it.isSelected }
        
        summary = when {
            selectedItems.isEmpty() -> "No items selected. Tap to select items."
            items.isNotEmpty() && selectedItems.size == items.size -> "All items selected: " + selectedItems.joinToString(" → ") { it.name }
            else -> "Selected: " + selectedItems.joinToString(" → ") { it.name }
        }
    }
}
