package com.dezzmusic.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dezzmusic.music.MusicService

class MusicNotificationReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_PLAY_PAUSE = "com.dezzmusic.PLAY_PAUSE"
        const val ACTION_NEXT = "com.dezzmusic.NEXT"
        const val ACTION_PREVIOUS = "com.dezzmusic.PREVIOUS"
        const val ACTION_STOP = "com.dezzmusic.STOP"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val serviceIntent = Intent(context, MusicService::class.java).apply {
            action = intent.action
        }
        context.startService(serviceIntent)
    }
}
