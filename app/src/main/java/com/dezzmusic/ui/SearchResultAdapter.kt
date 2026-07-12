package com.dezzmusic.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dezzmusic.databinding.ItemSearchResultBinding
import com.dezzmusic.telegram.MusicSearchResult

class SearchResultAdapter(
    private val onDownloadClick: (MusicSearchResult) -> Unit
) : ListAdapter<MusicSearchResult, SearchResultAdapter.ResultViewHolder>(ResultDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ResultViewHolder {
        val binding = ItemSearchResultBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ResultViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ResultViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ResultViewHolder(
        private val binding: ItemSearchResultBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.btnDownload.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onDownloadClick(getItem(position))
                }
            }
        }

        fun bind(result: MusicSearchResult) {
            binding.apply {
                tvTitle.text = result.title
                tvArtist.text = result.artist
                tvDuration.text = formatDuration(result.duration)
            }
        }

        private fun formatDuration(ms: Long): String {
            val seconds = (ms / 1000) % 60
            val minutes = (ms / (1000 * 60)) % 60
            return String.format("%02d:%02d", minutes, seconds)
        }
    }

    class ResultDiffCallback : DiffUtil.ItemCallback<MusicSearchResult>() {
        override fun areItemsTheSame(oldItem: MusicSearchResult, newItem: MusicSearchResult): Boolean {
            return oldItem.fileId == newItem.fileId
        }

        override fun areContentsTheSame(oldItem: MusicSearchResult, newItem: MusicSearchResult): Boolean {
            return oldItem == newItem
        }
    }
}
