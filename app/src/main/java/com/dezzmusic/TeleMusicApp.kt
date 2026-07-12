package com.dezzmusic

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.dezzmusic.db.MusicDatabase

class TeleMusicApp : Application() {

    lateinit var database: MusicDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = MusicDatabase.getInstance(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playerChannel = NotificationChannel(
                CHANNEL_PLAYER,
                "Reproductor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controles de reproducción"
                setShowBadge(false)
            }

            val downloadChannel = NotificationChannel(
                CHANNEL_DOWNLOAD,
                "Descargas",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Progreso de descargas"
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(playerChannel)
            manager.createNotificationChannel(downloadChannel)
        }
    }

    companion object {
        const val CHANNEL_PLAYER = "player_channel"
        const val CHANNEL_DOWNLOAD = "download_channel"

        lateinit var instance: TeleMusicApp
            private set
    }
}
