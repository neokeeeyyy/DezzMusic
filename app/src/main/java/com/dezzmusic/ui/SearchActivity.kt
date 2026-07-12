package com.dezzmusic.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.dezzmusic.databinding.ActivitySearchBinding
import com.dezzmusic.MusicRepository
import com.dezzmusic.telegram.TelegramManager
import kotlinx.coroutines.launch

class SearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchBinding
    private lateinit var adapter: SongAdapter
    private var searchJob: kotlinx.coroutines.Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupSearch()
        setupRecyclerView()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchJob?.cancel()
                searchJob = lifecycleScope.launch {
                    val query = s?.toString()?.trim() ?: ""
                    if (query.isNotEmpty()) {
                        searchSongs(query)
                    } else {
                        adapter.submitList(emptyList())
                        binding.emptyState.visibility = android.view.View.VISIBLE
                    }
                }
            }
        })

        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = binding.etSearch.text.toString().trim()
                if (query.isNotEmpty()) {
                    lifecycleScope.launch {
                        searchSongs(query)
                    }
                }
                true
            } else {
                false
            }
        }
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
            layoutManager = LinearLayoutManager(this@SearchActivity)
            adapter = this@SearchActivity.adapter
        }
    }

    private suspend fun searchSongs(query: String) {
        // Search local database
        val localResults = MusicRepository.getInstance(this).searchSongs(query)
        adapter.submitList(localResults)
        binding.emptyState.visibility = if (localResults.isEmpty()) {
            android.view.View.VISIBLE
        } else {
            android.view.View.GONE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        searchJob?.cancel()
    }
}
