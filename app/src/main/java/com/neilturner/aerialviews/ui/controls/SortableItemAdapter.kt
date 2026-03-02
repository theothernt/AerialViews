package com.neilturner.aerialviews.ui.controls

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.neilturner.aerialviews.R

data class SelectableItem(
    val id: String,
    val name: String,
    var isSelected: Boolean = false,
)

class ItemSelectionAdapter(
    private val items: MutableList<SelectableItem>,
    private val onSelectionChanged: () -> Unit,
    private val onMoveUp: (Int) -> Unit,
    private val onMoveDown: (Int) -> Unit,
) : RecyclerView.Adapter<ItemSelectionAdapter.ViewHolder>() {
    class ViewHolder(
        view: View,
    ) : RecyclerView.ViewHolder(view) {
        val selectionArea: View = view.findViewById(R.id.selection_area)
        val checkbox: CheckBox = view.findViewById(R.id.item_checkbox)
        val name: TextView = view.findViewById(R.id.item_name)
        val moveUpButton: Button = view.findViewById(R.id.button_move_up)
        val moveDownButton: Button = view.findViewById(R.id.button_move_down)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val view =
            LayoutInflater
                .from(parent.context)
                .inflate(R.layout.dialog_sortable_item_row, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) {
        val item = items[position]

        holder.name.text = item.name
        holder.checkbox.isChecked = item.isSelected

        // Show buttons for all items
        holder.moveUpButton.visibility = View.VISIBLE
        holder.moveDownButton.visibility = View.VISIBLE

        // Enable/Disable based on list position
        holder.moveUpButton.isEnabled = position > 0
        holder.moveDownButton.isEnabled = position < items.size - 1

        // Mirror selection area focus to the whole row for full-width highlight.
        holder.itemView.isActivated = holder.selectionArea.hasFocus()
        holder.selectionArea.onFocusChangeListener =
            View.OnFocusChangeListener { _, hasFocus ->
                holder.itemView.isActivated = hasFocus
            }

        // Toggle selection from the selection area.
        holder.selectionArea.setOnClickListener {
            toggleSelection(holder.bindingAdapterPosition)
        }

        // Handle checkbox clicks
        holder.checkbox.setOnClickListener {
            toggleSelection(holder.bindingAdapterPosition)
        }

        // Handle move buttons
        holder.moveUpButton.setOnClickListener {
            onMoveUp(holder.bindingAdapterPosition)
        }

        holder.moveDownButton.setOnClickListener {
            onMoveDown(holder.bindingAdapterPosition)
        }
    }

    private fun toggleSelection(position: Int) {
        if (position == RecyclerView.NO_POSITION) return
        val item = items[position]
        item.isSelected = !item.isSelected
        onSelectionChanged()
        notifyItemChanged(position)
    }

    fun moveUp(position: Int) {
        if (position <= 0) return

        val item = items[position]
        val prevItem = items[position - 1]

        // Swap in list
        items[position] = prevItem
        items[position - 1] = item

        // Notify adapter
        notifyItemMoved(position, position - 1)
        notifyItemChanged(position)
        notifyItemChanged(position - 1)
    }

    fun moveDown(position: Int) {
        if (position >= items.size - 1) return

        val item = items[position]
        val nextItem = items[position + 1]

        // Swap in list
        items[position] = nextItem
        items[position + 1] = item

        // Notify adapter
        notifyItemMoved(position, position + 1)
        notifyItemChanged(position)
        notifyItemChanged(position + 1)
    }

    override fun getItemCount() = items.size
}
