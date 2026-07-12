package com.dezzmusic.ui

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
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
        bindMusicService()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupButtons() {
        binding.btnPlayPause.setOnClickListener {
            musicService?.togglePlayPause()
            updatePlayPauseButton()
        }

        binding.btnNext.setOnClickListener {
            musicService?.playNext()
            updateSongInfo()
        }

        binding.btnPrevious.setOnClickListener {
            musicService?.playPrevious()
            updateSongInfo()
        }

        binding.btnFavorite.setOnClickListener {
            currentSong?.let { song ->
                song.isFavorite = !song.isFavorite
                lifecycleScope.launch {
                    MusicRepository.getInstance(this@PlayerActivity).updateSong(song)
                    updateFavoriteButton()
                }
            }
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
