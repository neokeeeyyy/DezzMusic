package com.dezzmusic.music

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Binder
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.dezzmusic.R
import com.dezzmusic.db.Song
import com.dezzmusic.MusicRepository
import com.dezzmusic.ui.MusicNotificationReceiver
import com.dezzmusic.ui.PlayerActivity
import kotlinx.coroutines.*

class MusicService : Service() {

    private val binder = MusicBinder()
    private var exoPlayer: ExoPlayer? = null
    private lateinit var mediaSession: MediaSessionCompat
    private var currentSong: Song? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        initPlayer()
        initMediaSession()
    }

    private fun initPlayer() {
        exoPlayer = ExoPlayer.Builder(this).build().apply {
            addListener(object : androidx.media3.common.Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        androidx.media3.common.Player.STATE_ENDED -> playNext()
                        androidx.media3.common.Player.STATE_READY -> updateNotification()
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    updatePlaybackState()
                    updateNotification()
                }
            })
        }
    }

    private fun initMediaSession() {
        mediaSession = MediaSessionCompat(this, "DezzMusic").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    togglePlayPause()
                }

                override fun onPause() {
                    togglePlayPause()
                }

                override fun onSkipToNext() {
                    playNext()
                }

                override fun onSkipToPrevious() {
                    playPrevious()
                }
            })
            isActive = true
        }
    }

    fun playSong(song: Song) {
        currentSong = song
        scope.launch {
            try {
                val audioUrl = getAudioUrl(song)
                if (audioUrl != null) {
                    val mediaItem = MediaItem.fromUri(audioUrl)
                    exoPlayer?.apply {
                        setMediaItem(mediaItem)
                        prepare()
                        play()
                    }
                    updatePlaybackState()
                    updateNotification()
                    recordPlay(song)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun getAudioUrl(song: Song): String? {
        return if (song.path.isNotEmpty()) {
            song.path
        } else if (song.telegramFileId != null) {
            val telegramManager = com.dezzmusic.telegram.TelegramManager.getInstance(this)
            telegramManager.getDownloadUrl(song.telegramFileId)
        } else {
            null
        }
    }

    private fun recordPlay(song: Song) {
        scope.launch {
            MusicRepository.getInstance(this@MusicService).incrementPlayCount(song.id)
        }
    }

    fun togglePlayPause() {
        exoPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
            } else {
                player.play()
            }
            updatePlaybackState()
        }
    }

    fun playNext() {
        val playlist = currentPlaylist
        if (playlist.isNotEmpty()) {
            val currentIndex = playlist.indexOfFirst { it.id == currentSong?.id }
            val nextIndex = (currentIndex + 1) % playlist.size
            playSong(playlist[nextIndex])
        }
    }

    fun playPrevious() {
        val playlist = currentPlaylist
        if (playlist.isNotEmpty()) {
            val currentIndex = playlist.indexOfFirst { it.id == currentSong?.id }
            val prevIndex = if (currentIndex > 0) currentIndex - 1 else playlist.size - 1
            playSong(playlist[prevIndex])
        }
    }

    fun seekTo(position: Int) {
        exoPlayer?.seekTo(position.toLong())
    }

    fun isPlaying(): Boolean = exoPlayer?.isPlaying == true

    fun getCurrentPosition(): Int = exoPlayer?.currentPosition?.toInt() ?: 0

    fun getDuration(): Int = exoPlayer?.duration?.toInt() ?: 0

    private fun updatePlaybackState() {
        val state = if (isPlaying()) {
            PlaybackStateCompat.STATE_PLAYING
        } else {
            PlaybackStateCompat.STATE_PAUSED
        }

        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(state, getCurrentPosition().toLong(), 1f)
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackStateCompat.ACTION_SEEK_TO
                )
                .build()
        )
    }

    private fun updateNotification() {
        val song = currentSong ?: return
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, PlayerActivity::class.java).apply {
                putExtra("song_id", song.id)
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseAction = if (isPlaying()) {
            NotificationCompat.Action(
                R.drawable.ic_pause,
                "Pause",
                getPendingIntentForAction(MusicNotificationReceiver.ACTION_PLAY_PAUSE)
            )
        } else {
            NotificationCompat.Action(
                R.drawable.ic_play,
                "Play",
                getPendingIntentForAction(MusicNotificationReceiver.ACTION_PLAY_PAUSE)
            )
        }

        val notification = NotificationCompat.Builder(this, com.dezzmusic.TeleMusicApp.CHANNEL_PLAYER)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentTitle(song.title)
            .setContentText(song.artist)
            .setLargeIcon(BitmapFactory.decodeResource(resources, R.drawable.ic_music_note))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(playPauseAction)
            .addAction(
                NotificationCompat.Action(
                    R.drawable.ic_skip_previous,
                    "Previous",
                    getPendingIntentForAction(MusicNotificationReceiver.ACTION_PREVIOUS)
                )
            )
            .addAction(
                NotificationCompat.Action(
                    R.drawable.ic_skip_next,
                    "Next",
                    getPendingIntentForAction(MusicNotificationReceiver.ACTION_NEXT)
                )
            )
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()

        startForeground(1, notification)
    }

    private fun getPendingIntentForAction(action: String): PendingIntent {
        val intent = Intent(this, MusicNotificationReceiver::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private var currentPlaylist: List<Song> = emptyList()

    fun setPlaylist(songs: List<Song>) {
        currentPlaylist = songs
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        exoPlayer?.release()
        mediaSession.release()
    }
}
