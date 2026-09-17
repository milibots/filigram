package com.filigram.cinema

import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import com.filigram.cinema.databinding.ItemMovieCardBinding

class MovieCardAdapter(
    private val items: MutableList<MovieItem> = mutableListOf(),
    private val onItemClick: (MovieItem) -> Unit,
    private val onItemLongClick: ((MovieItem) -> Unit)? = null
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
        val isHorizontal = (parent as? RecyclerView)?.layoutManager?.canScrollHorizontally() == true
        if (isHorizontal) {
            val vitrinWidth = parent.context.resources.getDimensionPixelSize(R.dimen.movie_card_vitrin_width)
            binding.root.layoutParams = ViewGroup.LayoutParams(vitrinWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        return MovieViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MovieViewHolder, position: Int) {
        val item = items[position]

        // 1. Top Title & Year Pill
        val validYear = item.year?.trim()?.takeIf { it.isNotBlank() && it != "null" && it != "0" }
        val displayTitle = when {
            validYear != null && !item.title.contains(validYear) -> "${item.title} $validYear"
            else -> item.title
        }
        holder.binding.cardTopTitle.text = displayTitle

        // 2. Type-based Presentation: Stacked Deck for Serials, Single Card for Movies
        val isSeries = (item.type == 1)
        val density = holder.itemView.context.resources.displayMetrics.density
        val marginEnd14 = (14 * density).toInt()

        val cardLp = holder.binding.cardContainer.layoutParams as? FrameLayout.LayoutParams

        if (isSeries) {
            holder.binding.cardStackBack1.visibility = View.VISIBLE
            holder.binding.cardStackBack2.visibility = View.VISIBLE
            if (cardLp != null && cardLp.marginEnd != marginEnd14) {
                cardLp.marginEnd = marginEnd14
                holder.binding.cardContainer.layoutParams = cardLp
            }
            ImageLoader.load(item.image, holder.binding.cardPosterBack1)
            ImageLoader.load(item.image, holder.binding.cardPosterBack2)
        } else {
            holder.binding.cardStackBack1.visibility = View.GONE
            holder.binding.cardStackBack2.visibility = View.GONE
            if (cardLp != null && cardLp.marginEnd != 0) {
                cardLp.marginEnd = 0
                holder.binding.cardContainer.layoutParams = cardLp
            }
        }

        // 3. Foreground Poster Image
        ImageLoader.load(item.image, holder.binding.cardPoster)

        // 4. Subtle Dub/Sub ribbon
        if (item.hasDub) {
            holder.binding.cardBadge.text = "دوبله"
            holder.binding.ribbonContainer.visibility = View.VISIBLE
        } else if (item.hasSub) {
            holder.binding.cardBadge.text = "زیرنویس"
            holder.binding.ribbonContainer.visibility = View.VISIBLE
        } else {
            holder.binding.ribbonContainer.visibility = View.GONE
        }

        // 5. Ratings & Play Button in Floating Bottom Bar
        val imdbVal = item.rating?.trim()?.takeIf { it.isNotBlank() && it != "null" }
        if (imdbVal != null) {
            holder.binding.cardImdbRate.text = imdbVal
            holder.binding.cardImdbContainer.visibility = View.VISIBLE
        } else {
            holder.binding.cardImdbRate.text = if (isSeries) "سریال" else "سینما"
            holder.binding.cardImdbContainer.visibility = View.VISIBLE
        }

        // 6. Comprehensive Click Listeners (Every component forwards click reliably)
        val clickAction = View.OnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            AppLogger.d("MovieCardAdapter", "کلیک روی آیتم: ${item.title} (شناسه: ${item.id}, نوع: ${item.type})")
            onItemClick(item)
        }
        holder.itemView.setOnClickListener(clickAction)
        holder.binding.cardRoot.setOnClickListener(clickAction)
        holder.binding.deckContainer.setOnClickListener(clickAction)
        holder.binding.cardContainer.setOnClickListener(clickAction)
        holder.binding.cardPoster.setOnClickListener(clickAction)
        holder.binding.cardStackBack1.setOnClickListener(clickAction)
        holder.binding.cardStackBack2.setOnClickListener(clickAction)
        holder.binding.topPillContainer.setOnClickListener(clickAction)
        holder.binding.cardTopTitle.setOnClickListener(clickAction)
        holder.binding.bottomPill.setOnClickListener(clickAction)
        holder.binding.btnPlayCard.setOnClickListener(clickAction)
        holder.binding.cardImdbContainer.setOnClickListener(clickAction)

        val longClickAction = View.OnLongClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            if (onItemLongClick != null) {
                onItemLongClick.invoke(item)
                true
            } else {
                false
            }
        }
        holder.binding.cardContainer.setOnLongClickListener(longClickAction)
        holder.itemView.setOnLongClickListener(longClickAction)

        // 8. Smooth Entry Animation
        holder.itemView.alpha = 0f
        holder.itemView.scaleX = 0.92f
        holder.itemView.scaleY = 0.92f
        val delay = (position % 8) * 35L
        holder.itemView.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(260)
            .setStartDelay(delay)
            .setInterpolator(DecelerateInterpolator(1.5f))
            .start()
    }

    override fun getItemCount(): Int = items.size
}
