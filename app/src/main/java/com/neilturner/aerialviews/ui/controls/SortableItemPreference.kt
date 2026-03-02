package com.neilturner.aerialviews.ui.controls

import android.app.AlertDialog
import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import android.widget.LinearLayout
import android.widget.TextView
import androidx.preference.DialogPreference
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.neilturner.aerialviews.R
import java.util.Locale

class SortableItemPreference
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = androidx.preference.R.attr.dialogPreferenceStyle,
    ) : DialogPreference(context, attrs, defStyleAttr) {
        private val items = mutableListOf<SelectableItem>()
        private var defaultSelection = ""

        init {
            context.obtainStyledAttributes(attrs, androidx.preference.R.styleable.ListPreference, defStyleAttr, 0).apply {
                try {
                    val entries = getTextArray(androidx.preference.R.styleable.ListPreference_entries)
                    val entryValues = getTextArray(androidx.preference.R.styleable.ListPreference_entryValues)

                    if (entries != null && entryValues != null && entries.size == entryValues.size) {
                        for (i in entries.indices) {
                            val id = entryValues[i].toString()
                            items.add(SelectableItem(id, entries[i].toString()))
                        }
                    }
                } finally {
                    recycle()
                }
            }
        }

        override fun onGetDefaultValue(
            a: TypedArray,
            index: Int,
        ): Any {
            a.getString(index)?.let { return it }

            val resourceId = a.peekValue(index)?.resourceId ?: 0
            if (resourceId != 0) {
                return runCatching {
                    a.resources.getStringArray(resourceId).joinToString(",")
                }.getOrDefault("")
            }

            return ""
        }

        override fun onSetInitialValue(defaultValue: Any?) {
            defaultSelection = (defaultValue as? String).orEmpty()
            loadState(getPersistedString(defaultSelection).orEmpty())
        }

        override fun onClick() {
            showDialog()
        }

        private fun showDialog() {
            val dialogView =
                RecyclerView(context).apply {
                    layoutManager = LinearLayoutManager(context)
                    setPadding(0, 16, 0, 16)
                }

            lateinit var adapter: ItemSelectionAdapter
            adapter =
                ItemSelectionAdapter(
                    items = items,
                    onSelectionChanged = {
                        // Optional: feedback on selection change
                    },
                    onMoveUp = { position ->
                        adapter.moveUp(position)
                    },
                    onMoveDown = { position ->
                        adapter.moveDown(position)
                    },
                )

            dialogView.adapter = adapter

            // Container for optional message + recyclerview
            val container =
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL

                    // Only add message view if dialogMessage is set
                    val message = dialogMessage
                    if (!message.isNullOrEmpty()) {
                        val messageView =
                            TextView(context).apply {
                                text = message
                                setPadding(48, 32, 48, 16)
                            }
                        addView(messageView)
                    }

                    addView(dialogView)
                }

            AlertDialog
                .Builder(context)
                .setTitle(dialogTitle ?: title ?: "")
                .setView(container)
                .setPositiveButton(R.string.button_ok) { _, _ ->
                    saveState()
                }.setNegativeButton(R.string.button_cancel) { _, _ ->
                    loadState(getPersistedString(defaultSelection).orEmpty())
                }.show()
        }

        private fun saveState() {
            val selectedIds =
                items
                    .filter { it.isSelected }
                    .joinToString(",") { it.id }

            persistString(selectedIds)
            notifyChanged()
        }

        private fun loadState(savedIds: String) {
            // Reset all items first
            items.forEach {
                it.isSelected = false
            }

            // Build a case-insensitive lookup map so XML/default casing is not strict.
            val itemsMap = items.associateBy { it.id.lowercase(Locale.ROOT) }
            val newItemsList = mutableListOf<SelectableItem>()

            // 1. Add selected items in saved order
            if (savedIds.isNotEmpty()) {
                val idList = savedIds.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                idList.forEach { id ->
                    itemsMap[id.lowercase(Locale.ROOT)]?.let { item ->
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
        }
    }
