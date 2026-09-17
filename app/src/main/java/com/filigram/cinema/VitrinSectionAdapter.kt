package com.filigram.cinema

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.filigram.cinema.databinding.ItemVitrinSectionBinding

class VitrinSectionAdapter(
    private val sections: MutableList<VitrinSection> = mutableListOf(),
    private val onItemClick: (MovieItem) -> Unit,
    private val onItemLongClick: ((MovieItem) -> Unit)? = null
) : RecyclerView.Adapter<VitrinSectionAdapter.SectionViewHolder>() {

    fun updateData(newSections: List<VitrinSection>) {
        sections.clear()
        sections.addAll(newSections)
        notifyDataSetChanged()
    }

    fun appendData(moreSections: List<VitrinSection>) {
        val startPos = sections.size
        sections.addAll(moreSections)
        notifyItemRangeInserted(startPos, moreSections.size)
    }

    inner class SectionViewHolder(val binding: ItemVitrinSectionBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SectionViewHolder {
        val binding = ItemVitrinSectionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SectionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SectionViewHolder, position: Int) {
        val section = sections[position]
        holder.binding.sectionTitle.text = section.title

        val adapter = MovieCardAdapter(section.items.toMutableList(), onItemClick, onItemLongClick)
        holder.binding.rvSectionMovies.layoutManager = LinearLayoutManager(
            holder.itemView.context,
            LinearLayoutManager.HORIZONTAL,
            false
        )
        holder.binding.rvSectionMovies.adapter = adapter
    }

    override fun getItemCount(): Int = sections.size
}
