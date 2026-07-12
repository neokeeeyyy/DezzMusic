package com.dezzmusic.ui

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Configuration
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.dezzmusic.R
import com.dezzmusic.databinding.ActivityPlayerBinding
import com.dezzmusic.db.Song
import com.dezzmusic.music.MusicService
import com.dezzmusic.MusicRepository
import kotlinx.coroutines.*

class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private var musicService: MusicService? = null
    private var isBound = false
    private var currentSong: Song? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var updateJob: Job? = null
    private var playlist: List<Song> = emptyList()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            isBound = true
            setupPlayer()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupButtons()
        setupCoverFlow()
        bindMusicService()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        setupCoverFlow()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar?.setNavigationOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_top, R.anim.slide_out_bottom)
        }
    }

    private fun setupButtons() {
        binding.btnPlayPause.setOnClickListener {
            musicService?.togglePlayPause()
            updatePlayPauseButton()
            animateButton(it)
        }

        binding.btnNext.setOnClickListener {
            musicService?.playNext()
            updateSongInfo()
            animateButton(it)
        }

        binding.btnPrevious.setOnClickListener {
            musicService?.playPrevious()
            updateSongInfo()
            animateButton(it)
        }

        binding.btnFavorite.setOnClickListener {
            currentSong?.let { song ->
                song.isFavorite = !song.isFavorite
                lifecycleScope.launch {
                    MusicRepository.getInstance(this@PlayerActivity).updateSong(song)
                    updateFavoriteButton()
                }
            }
            animateButton(it)
        }

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    musicService?.seekTo(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupCoverFlow() {
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            binding.coverFlow?.visibility = View.VISIBLE
            loadCoverFlowItems()
        } else {
            binding.coverFlow?.visibility = View.GONE
        }
    }

    private fun loadCoverFlowItems() {
        lifecycleScope.launch {
            val allSongs = MusicRepository.getInstance(this@PlayerActivity).getAllSongs()
            playlist = allSongs
            musicService?.setPlaylist(allSongs)

            val items = allSongs.map { song ->
                CoverFlowView.CoverFlowItem(
                    title = song.title,
                    artist = song.artist,
                    cover = null
                )
            }

            binding.coverFlow?.setItems(items)
            binding.coverFlow?.setOnItemSelectedListener { index ->
                if (index in playlist.indices) {
                    musicService?.playSong(playlist[index])
                    currentSong = playlist[index]
                    updateSongInfo()
                    updatePlayPauseButton()
                }
            }

            val currentIndex = playlist.indexOfFirst { it.id == currentSong?.id }
            if (currentIndex >= 0) {
                binding.coverFlow?.setSelectedIndex(currentIndex)
            }
        }
    }

    private fun animateButton(view: View) {
        view.animate()
            .scaleX(0.85f)
            .scaleY(0.85f)
            .setDuration(50)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .start()
            }
            .start()
    }

    private fun bindMusicService() {
        val intent = Intent(this, MusicService::class.java)
        bindService(intent, connection, BIND_AUTO_CREATE)
    }

    private fun setupPlayer() {
        val songId = intent.getLongExtra("song_id", -1)
        if (songId != -1L) {
            lifecycleScope.launch {
                currentSong = MusicRepository.getInstance(this@PlayerActivity).getSongById(songId)
                updateSongInfo()
                musicService?.playSong(currentSong!!)
            }
        }

        updatePlayPauseButton()
        startProgressUpdate()
    }

    private fun updateSongInfo() {
        currentSong?.let { song ->
            binding.tvTitle.text = song.title
            binding.tvArtist.text = song.artist
            binding.seekBar.max = song.duration.toInt()
            updateFavoriteButton()
        }
    }

    private fun updatePlayPauseButton() {
        if (musicService?.isPlaying() == true) {
            binding.btnPlayPause.setImageResource(R.drawable.ic_pause)
        } else {
            binding.btnPlayPause.setImageResource(R.drawable.ic_play)
        }
    }

    private fun updateFavoriteButton() {
        if (currentSong?.isFavorite == true) {
            binding.btnFavorite.setImageResource(R.drawable.ic_favorite_filled)
        } else {
            binding.btnFavorite.setImageResource(R.drawable.ic_favorite_outline)
        }
    }

    private fun startProgressUpdate() {
        updateJob = lifecycleScope.launch {
            while (isActive) {
                musicService?.let { service ->
                    val position = service.getCurrentPosition()
                    binding.seekBar.progress = position
                    binding.tvCurrentTime.text = formatTime(position.toLong())
                    binding.tvTotalTime.text = formatTime(service.getDuration().toLong())
                }
                delay(1000)
            }
        }
    }

    private fun formatTime(ms: Long): String {
        val seconds = (ms / 1000) % 60
        val minutes = (ms / (1000 * 60)) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    override fun onDestroy() {
        super.onDestroy()
        updateJob?.cancel()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }
}
