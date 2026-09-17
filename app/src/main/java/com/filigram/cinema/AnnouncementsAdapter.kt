package com.filigram.cinema

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.filigram.cinema.databinding.ItemAnnouncementBinding

class AnnouncementsAdapter(
    private val items: MutableList<Announcement>,
    private val onItemClick: (Announcement) -> Unit
) : RecyclerView.Adapter<AnnouncementsAdapter.AnnouncementViewHolder>() {

    inner class AnnouncementViewHolder(val binding: ItemAnnouncementBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AnnouncementViewHolder {
        val binding = ItemAnnouncementBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AnnouncementViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AnnouncementViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context

        holder.binding.txtAnnouncementTitle.text = item.title
        holder.binding.txtAnnouncementBody.text = item.text
        holder.binding.txtAnnouncementDate.text = item.getFormattedDate()

        if (item.isRead) {

            holder.binding.cardAnnouncement.strokeColor = Color.parseColor("#222222")
            holder.binding.cardAnnouncement.strokeWidth = 1
            holder.binding.cardAnnouncement.setCardBackgroundColor(Color.parseColor("#0A0A0A"))
            holder.binding.txtStatusBadge.setBackgroundResource(R.drawable.bg_badge_read_tag)
            holder.binding.txtStatusBadge.text = "خوانده شده"
            holder.binding.txtStatusBadge.setTextColor(Color.parseColor("#777777"))
            holder.binding.txtAnnouncementTitle.setTextColor(Color.parseColor("#CCCCCC"))
            holder.binding.imgAnnouncementIcon.setColorFilter(Color.parseColor("#666666"))
        } else {

            holder.binding.cardAnnouncement.strokeColor = ContextCompat.getColor(context, R.color.gold)
            holder.binding.cardAnnouncement.strokeWidth = 2
            holder.binding.cardAnnouncement.setCardBackgroundColor(Color.parseColor("#121008"))
            holder.binding.txtStatusBadge.setBackgroundResource(R.drawable.bg_badge_unread_tag)
            holder.binding.txtStatusBadge.text = "جدید"
            holder.binding.txtStatusBadge.setTextColor(Color.parseColor("#E50914"))
            holder.binding.txtAnnouncementTitle.setTextColor(ContextCompat.getColor(context, R.color.gold))
            holder.binding.imgAnnouncementIcon.setColorFilter(ContextCompat.getColor(context, R.color.gold))
        }

        holder.itemView.setOnClickListener {
            onItemClick(item)
            notifyItemChanged(position)
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<Announcement>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
