package com.filigram.cinema

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.filigram.cinema.databinding.ItemHeroSlideBinding

class HeroBannerAdapter(
    private val items: List<MovieItem>,
    private val onItemClick: (MovieItem) -> Unit
) : RecyclerView.Adapter<HeroBannerAdapter.BannerViewHolder>() {

    inner class BannerViewHolder(val binding: ItemHeroSlideBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BannerViewHolder {
        val binding = ItemHeroSlideBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BannerViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BannerViewHolder, position: Int) {
        val item = items[position]
        holder.binding.bannerTitle.text = item.title
        holder.binding.bannerBadge.text = if (item.type == 1) "سریال" else "فیلم"

        ImageLoader.load(item.image, holder.binding.bannerImage)

        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount(): Int = items.size
}
