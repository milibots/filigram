package com.filigram.cinema

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.recyclerview.widget.RecyclerView
import com.filigram.cinema.databinding.ItemMovieCardBinding

class MovieCardAdapter(
    private val items: MutableList<MovieItem> = mutableListOf(),
    private val onItemClick: (MovieItem) -> Unit
) : RecyclerView.Adapter<MovieCardAdapter.MovieViewHolder>() {

    fun updateData(newItems: List<MovieItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun appendData(moreItems: List<MovieItem>) {
        val startPos = items.size
        items.addAll(moreItems)
        notifyItemRangeInserted(startPos, moreItems.size)
    }

    fun getItems(): List<MovieItem> = items.toList()

    inner class MovieViewHolder(val binding: ItemMovieCardBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MovieViewHolder {
        val binding = ItemMovieCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MovieViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MovieViewHolder, position: Int) {
        val item = items[position]
        holder.binding.cardTitle.text = item.title

        if (item.hasDub) {
            holder.binding.cardBadge.text = "دوبله"
            holder.binding.cardBadge.visibility = View.VISIBLE
        } else if (item.hasSub) {
            holder.binding.cardBadge.text = "زیرنویس"
            holder.binding.cardBadge.visibility = View.VISIBLE
        } else {
            holder.binding.cardBadge.visibility = View.GONE
        }

        ImageLoader.load(item.image, holder.binding.cardPoster)

        // Staggered fade+scale entrance animation
        holder.itemView.alpha = 0f
        holder.itemView.scaleX = 0.88f
        holder.itemView.scaleY = 0.88f
        val delay = (position % 12) * 35L
        holder.itemView.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(280)
            .setStartDelay(delay)
            .setInterpolator(DecelerateInterpolator(1.5f))
            .start()

        // Press scale down / release scale up
        holder.itemView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(100)
                        .setInterpolator(DecelerateInterpolator()).start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(180)
                        .setInterpolator(OvershootInterpolator(2f)).start()
                }
            }
            false
        }

        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount(): Int = items.size
}
