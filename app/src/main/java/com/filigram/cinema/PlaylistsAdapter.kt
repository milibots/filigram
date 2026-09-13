package com.filigram.cinema

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.filigram.cinema.databinding.ItemPlaylistCardBinding

class PlaylistsAdapter(
    private val playlists: List<Playlist>,
    private val onPlaylistClick: (Playlist) -> Unit
) : RecyclerView.Adapter<PlaylistsAdapter.PlaylistViewHolder>() {

    inner class PlaylistViewHolder(val binding: ItemPlaylistCardBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaylistViewHolder {
        val binding = ItemPlaylistCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PlaylistViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PlaylistViewHolder, position: Int) {
        val pl = playlists[position]
        holder.binding.cardPlaylistTitle.text = pl.title
        holder.binding.cardPlaylistEnTitle.text = pl.englishTitle
        holder.binding.cardPlaylistDesc.text = pl.description
        holder.binding.cardPlaylistCount.text = "${pl.items.size} عنوان"

        ImageLoader.load(pl.cover, holder.binding.cardPlaylistCover)

        holder.itemView.setOnClickListener {
            onPlaylistClick(pl)
        }

        holder.binding.btnViewPlaylist.setOnClickListener {
            onPlaylistClick(pl)
        }
    }

    override fun getItemCount(): Int = playlists.size
}
