package com.dezzmusic

import android.content.Context
import com.dezzmusic.db.MusicDatabase
import com.dezzmusic.db.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MusicRepository private constructor(context: Context) {

    private val database = MusicDatabase.getInstance(context)
    private val songDao = database.songDao()

    companion object {
        @Volatile
        private var instance: MusicRepository? = null

        fun getInstance(context: Context): MusicRepository {
            return instance ?: synchronized(this) {
                instance ?: MusicRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    suspend fun getAllSongs(): List<Song> = withContext(Dispatchers.IO) {
        songDao.getAllSongs()
    }

    suspend fun getFavoriteSongs(): List<Song> = withContext(Dispatchers.IO) {
        songDao.getFavoriteSongs()
    }

    suspend fun getMostPlayed(limit: Int = 50): List<Song> = withContext(Dispatchers.IO) {
        songDao.getMostPlayed(limit)
    }

    suspend fun getRecentlyPlayed(limit: Int = 50): List<Song> = withContext(Dispatchers.IO) {
        songDao.getRecentlyPlayed(limit)
    }

    suspend fun searchSongs(query: String): List<Song> = withContext(Dispatchers.IO) {
        songDao.searchSongs(query)
    }

    suspend fun getSongById(id: Long): Song? = withContext(Dispatchers.IO) {
        songDao.getSongById(id)
    }

    suspend fun insertSong(song: Song) = withContext(Dispatchers.IO) {
        songDao.insertSong(song)
    }

    suspend fun updateSong(song: Song) = withContext(Dispatchers.IO) {
        songDao.updateSong(song)
    }

    suspend fun deleteSong(song: Song) = withContext(Dispatchers.IO) {
        songDao.deleteSong(song)
    }

    suspend fun incrementPlayCount(songId: Long) = withContext(Dispatchers.IO) {
        songDao.incrementPlayCount(songId)
    }

    suspend fun setFavorite(songId: Long, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        songDao.setFavorite(songId, isFavorite)
    }

    suspend fun getSongCount(): Int = withContext(Dispatchers.IO) {
        songDao.getSongCount()
    }
}
