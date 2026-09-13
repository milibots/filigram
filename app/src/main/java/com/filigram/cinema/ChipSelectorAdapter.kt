package com.filigram.cinema

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.filigram.cinema.databinding.ItemChipSelectorBinding

class ChipSelectorAdapter(
    private var items: List<String>,
    private var selectedIndex: Int = 0,
    private val onItemSelected: (Int) -> Unit
) : RecyclerView.Adapter<ChipSelectorAdapter.ChipViewHolder>() {

    fun updateItems(newItems: List<String>, defaultIndex: Int = 0) {
        items = newItems
        selectedIndex = defaultIndex
        notifyDataSetChanged()
    }

    fun setSelectedIndex(index: Int) {
        val old = selectedIndex
        selectedIndex = index
        notifyItemChanged(old)
        notifyItemChanged(selectedIndex)
    }

    inner class ChipViewHolder(val binding: ItemChipSelectorBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChipViewHolder {
        val binding = ItemChipSelectorBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ChipViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChipViewHolder, position: Int) {
        holder.binding.chipText.text = items[position]
        holder.binding.chipText.isSelected = (position == selectedIndex)

        holder.itemView.setOnClickListener {
            val prev = selectedIndex
            selectedIndex = holder.adapterPosition
            notifyItemChanged(prev)
            notifyItemChanged(selectedIndex)
            onItemSelected(selectedIndex)
        }
    }

    override fun getItemCount(): Int = items.size
}
