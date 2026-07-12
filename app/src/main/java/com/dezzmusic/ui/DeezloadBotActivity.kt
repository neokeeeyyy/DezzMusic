package com.dezzmusic.ui

import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.dezzmusic.databinding.ActivityDeezloadBotBinding
import com.dezzmusic.db.Song
import com.dezzmusic.MusicRepository
import com.dezzmusic.telegram.TelegramManager
import com.dezzmusic.telegram.MusicSearchResult
import kotlinx.coroutines.launch

class DeezloadBotActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDeezloadBotBinding
    private lateinit var adapter: SearchResultAdapter
    private var searchJob: kotlinx.coroutines.Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeezloadBotBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupSearch()
        setupRecyclerView()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
    }

    private fun setupSearch() {
        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = binding.etSearch.text.toString().trim()
                if (query.isNotEmpty()) {
                    searchMusic(query)
                }
                true
            } else {
                false
            }
        }

        binding.btnSearch.setOnClickListener {
            val query = binding.etSearch.text.toString().trim()
            if (query.isNotEmpty()) {
                searchMusic(query)
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = SearchResultAdapter { result ->
            downloadSong(result)
        }

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@DeezloadBotActivity)
            adapter = this@DeezloadBotActivity.adapter
        }
    }

    private fun searchMusic(query: String) {
        searchJob?.cancel()
        searchJob = lifecycleScope.launch {
            binding.progressBar.visibility = android.view.View.VISIBLE
            binding.emptyState.visibility = android.view.View.GONE

            val results = TelegramManager.getInstance(this@DeezloadBotActivity).searchMusic(query)
            adapter.submitList(results)

            binding.progressBar.visibility = android.view.View.GONE
            if (results.isEmpty()) {
                binding.emptyState.visibility = android.view.View.VISIBLE
            }
        }
    }

    private fun downloadSong(result: MusicSearchResult) {
        lifecycleScope.launch {
            Toast.makeText(this@DeezloadBotActivity, "Descargando ${result.title}...", Toast.LENGTH_SHORT).show()

            val song = Song(
                id = System.currentTimeMillis(),
                title = result.title,
                artist = result.artist,
                duration = result.duration,
                path = "",
                albumArt = null,
                telegramFileId = result.fileId,
                chatId = result.chatId,
                messageId = result.messageId,
                isDownloaded = false
            )

            val success = TelegramManager.getInstance(this@DeezloadBotActivity).downloadSong(song)
            if (success) {
                MusicRepository.getInstance(this@DeezloadBotActivity).insertSong(song)
                Toast.makeText(this@DeezloadBotActivity, "Descarga completa", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@DeezloadBotActivity, "Error al descargar", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        searchJob?.cancel()
    }
}
