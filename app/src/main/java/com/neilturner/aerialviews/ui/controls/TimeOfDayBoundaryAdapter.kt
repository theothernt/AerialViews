package com.neilturner.aerialviews.ui.controls

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.neilturner.aerialviews.R

enum class TimeOfDayGroup {
    DAY,
    NIGHT,
}

sealed class TimeOfDayRow {
    data class Header(
        val title: String,
    ) : TimeOfDayRow()

    data class Option(
        val group: TimeOfDayGroup,
        val value: String,
        val label: String,
        var isSelected: Boolean = false,
    ) : TimeOfDayRow()
}

class TimeOfDayBoundaryAdapter(
    private val items: MutableList<TimeOfDayRow>,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    class HeaderViewHolder(
        view: View,
    ) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(android.R.id.text1)
    }

    class OptionViewHolder(
        view: View,
    ) : RecyclerView.ViewHolder(view) {
        val selectionArea: View = view.findViewById(R.id.selection_area)
        val checkbox: CheckBox = view.findViewById(R.id.item_checkbox)
        val name: TextView = view.findViewById(R.id.item_name)
    }

    override fun getItemViewType(position: Int): Int =
        when (items[position]) {
            is TimeOfDayRow.Header -> VIEW_TYPE_HEADER
            is TimeOfDayRow.Option -> VIEW_TYPE_OPTION
        }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): RecyclerView.ViewHolder =
        when (viewType) {
            VIEW_TYPE_HEADER -> {
                val view =
                    LayoutInflater
                        .from(parent.context)
                        .inflate(android.R.layout.simple_list_item_1, parent, false)
                HeaderViewHolder(view)
            }

            else -> {
                val view =
                    LayoutInflater
                        .from(parent.context)
                        .inflate(R.layout.dialog_time_of_day_row, parent, false)
                OptionViewHolder(view)
            }
        }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
    ) {
        when (val item = items[position]) {
            is TimeOfDayRow.Header -> {
                val headerHolder = holder as HeaderViewHolder
                headerHolder.title.text = item.title
            }

            is TimeOfDayRow.Option -> {
                val optionHolder = holder as OptionViewHolder
                optionHolder.name.text = item.label
                optionHolder.checkbox.isChecked = item.isSelected

                optionHolder.selectionArea.setOnClickListener {
                    toggleSelection(optionHolder.bindingAdapterPosition)
                }
                optionHolder.checkbox.setOnClickListener {
                    toggleSelection(optionHolder.bindingAdapterPosition)
                }
            }
        }
    }

    private fun toggleSelection(position: Int) {
        if (position == RecyclerView.NO_POSITION) return
        val row = items[position] as? TimeOfDayRow.Option ?: return
        row.isSelected = !row.isSelected
        notifyItemChanged(position)
    }

    fun getSelectedFor(group: TimeOfDayGroup): Set<String> =
        items
            .asSequence()
            .filterIsInstance<TimeOfDayRow.Option>()
            .filter { it.group == group && it.isSelected }
            .map { it.value }
            .toSet()

    override fun getItemCount(): Int = items.size

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_OPTION = 1
    }
}
