package com.dezzmusic.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.dezzmusic.R
import com.dezzmusic.databinding.FragmentLibraryBinding
import com.dezzmusic.db.Song
import com.dezzmusic.MusicRepository
import kotlinx.coroutines.launch

class LibraryFragment : Fragment() {

    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: SongAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupQuickActions()
        loadSongs()
    }

    private fun setupRecyclerView() {
        adapter = SongAdapter { song ->
            val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
                putExtra("song_id", song.id)
                putExtra("chat_id", song.chatId)
                putExtra("message_id", song.messageId)
            }
            startActivity(intent)
            requireActivity().overridePendingTransition(R.anim.slide_in_bottom, R.anim.slide_out_top)
        }

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@LibraryFragment.adapter
        }
    }

    private fun setupQuickActions() {
        binding.btnFavorites.setOnClickListener {
            launchLibraryDetail("favorites")
        }

        binding.btnRecent.setOnClickListener {
            launchLibraryDetail("recent")
        }

        binding.btnMostPlayed.setOnClickListener {
            launchLibraryDetail("most_played")
        }

        binding.btnPlaylists.setOnClickListener {
            launchLibraryDetail("playlists")
        }
    }

    private fun launchLibraryDetail(type: String) {
        val intent = Intent(requireContext(), LibraryDetailActivity::class.java).apply {
            putExtra("type", type)
        }
        startActivity(intent)
        requireActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
    }

    private fun loadSongs() {
        viewLifecycleOwner.lifecycleScope.launch {
            val songs = MusicRepository.getInstance(requireContext()).getAllSongs()
            adapter.submitList(songs)
            binding.emptyState.visibility = if (songs.isEmpty()) View.VISIBLE else View.GONE
            binding.tvSongCount.text = "${songs.size} canciones"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
