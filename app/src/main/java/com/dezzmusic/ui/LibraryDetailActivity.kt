package com.dezzmusic.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.dezzmusic.databinding.ActivityLibraryDetailBinding
import com.dezzmusic.MusicRepository
import kotlinx.coroutines.launch

class LibraryDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLibraryDetailBinding
    private lateinit var adapter: SongAdapter
    private var type: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLibraryDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        type = intent.getStringExtra("type") ?: ""
        setupToolbar()
        setupRecyclerView()
        loadData()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        when (type) {
            "favorites" -> {
                binding.toolbar.title = "Favoritos"
                binding.emptyState.text = "No hay favoritos"
            }
            "recent" -> {
                binding.toolbar.title = "Recientes"
                binding.emptyState.text = "No hay recientes"
            }
            "most_played" -> {
                binding.toolbar.title = "Más escuchados"
                binding.emptyState.text = "No hay reproducciones"
            }
            "playlists" -> {
                binding.toolbar.title = "Listas"
                binding.emptyState.text = "No hay listas"
            }
        }

        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = SongAdapter { song ->
            val intent = android.content.Intent(this, PlayerActivity::class.java).apply {
                putExtra("song_id", song.id)
                putExtra("chat_id", song.chatId)
                putExtra("message_id", song.messageId)
            }
            startActivity(intent)
        }

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@LibraryDetailActivity)
            adapter = this@LibraryDetailActivity.adapter
        }
    }

    private fun loadData() {
        lifecycleScope.launch {
            val songs = when (type) {
                "favorites" -> MusicRepository.getInstance(this@LibraryDetailActivity).getFavoriteSongs()
                "recent" -> MusicRepository.getInstance(this@LibraryDetailActivity).getRecentlyPlayed()
                "most_played" -> MusicRepository.getInstance(this@LibraryDetailActivity).getMostPlayed()
                else -> emptyList()
            }

            adapter.submitList(songs)
            binding.emptyState.visibility = if (songs.isEmpty()) {
                android.view.View.VISIBLE
            } else {
                android.view.View.GONE
            }
        }
    }
}
