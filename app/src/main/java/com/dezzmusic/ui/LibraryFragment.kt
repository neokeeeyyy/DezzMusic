package com.dezzmusic.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
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
        }

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@LibraryFragment.adapter
        }
    }

    private fun setupQuickActions() {
        binding.btnFavorites.setOnClickListener {
            val intent = Intent(requireContext(), LibraryDetailActivity::class.java).apply {
                putExtra("type", "favorites")
            }
            startActivity(intent)
        }

        binding.btnRecent.setOnClickListener {
            val intent = Intent(requireContext(), LibraryDetailActivity::class.java).apply {
                putExtra("type", "recent")
            }
            startActivity(intent)
        }

        binding.btnMostPlayed.setOnClickListener {
            val intent = Intent(requireContext(), LibraryDetailActivity::class.java).apply {
                putExtra("type", "most_played")
            }
            startActivity(intent)
        }

        binding.btnPlaylists.setOnClickListener {
            val intent = Intent(requireContext(), LibraryDetailActivity::class.java).apply {
                putExtra("type", "playlists")
            }
            startActivity(intent)
        }
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
