package com.dezzmusic.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.dezzmusic.databinding.FragmentChatsBinding
import com.dezzmusic.db.Song
import com.dezzmusic.telegram.TelegramManager
import kotlinx.coroutines.launch

class ChatsFragment : Fragment() {

    private var _binding: FragmentChatsBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: SongAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
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
            adapter = this@ChatsFragment.adapter
        }
    }

    private fun loadSongs() {
        viewLifecycleOwner.lifecycleScope.launch {
            val songs = TelegramManager.getInstance(requireContext()).getAvailableSongs()
            adapter.submitList(songs)
            binding.emptyState.visibility = if (songs.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
