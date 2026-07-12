package com.dezzmusic.db

import android.content.Context
import androidx.room.*

@Database(entities = [Song::class, Playlist::class, PlaylistSong::class], version = 1, exportSchema = false)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun playlistDao(): PlaylistDao

    companion object {
        @Volatile
        private var instance: MusicDatabase? = null

        fun getInstance(context: Context): MusicDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MusicDatabase::class.java,
                    "dezzmusic.db"
                ).build().also { instance = it }
            }
        }
    }
}

@Entity(tableName = "songs")
data class Song(
    @PrimaryKey
    val id: Long,
    val title: String,
    val artist: String,
    val duration: Long,
    val path: String,
    val albumArt: String?,
    val telegramFileId: String?,
    val chatId: Long,
    val messageId: Long,
    val dateAdded: Long = System.currentTimeMillis(),
    var playCount: Int = 0,
    var lastPlayed: Long = 0,
    var isFavorite: Boolean = false,
    var isDownloaded: Boolean = false
)

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String?,
    val createdAt: Long = System.currentTimeMillis(),
    var songCount: Int = 0
)

@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "songId"]
)
data class PlaylistSong(
    val playlistId: Long,
    val songId: Long,
    val position: Int
)

@Dao
interface SongDao {
    @Query("SELECT * FROM songs ORDER BY title ASC")
    suspend fun getAllSongs(): List<Song>

    @Query("SELECT * FROM songs WHERE isFavorite = 1 ORDER BY title ASC")
    suspend fun getFavoriteSongs(): List<Song>

    @Query("SELECT * FROM songs ORDER BY playCount DESC LIMIT :limit")
    suspend fun getMostPlayed(limit: Int = 50): List<Song>

    @Query("SELECT * FROM songs ORDER BY lastPlayed DESC LIMIT :limit")
    suspend fun getRecentlyPlayed(limit: Int = 50): List<Song>

    @Query("SELECT * FROM songs WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%'")
    suspend fun searchSongs(query: String): List<Song>

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getSongById(id: Long): Song?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: Song)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<Song>)

    @Update
    suspend fun updateSong(song: Song)

    @Delete
    suspend fun deleteSong(song: Song)

    @Query("UPDATE songs SET playCount = playCount + 1, lastPlayed = :timestamp WHERE id = :songId")
    suspend fun incrementPlayCount(songId: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE songs SET isFavorite = :isFavorite WHERE id = :songId")
    suspend fun setFavorite(songId: Long, isFavorite: Boolean)

    @Query("SELECT COUNT(*) FROM songs")
    suspend fun getSongCount(): Int
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY name ASC")
    suspend fun getAllPlaylists(): List<Playlist>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getPlaylistById(id: Long): Playlist?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Update
    suspend fun updatePlaylist(playlist: Playlist)

    @Delete
    suspend fun deletePlaylist(playlist: Playlist)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addSongToPlaylist(playlistSong: PlaylistSong)

    @Query("SELECT s.* FROM songs s INNER JOIN playlist_songs ps ON s.id = ps.songId WHERE ps.playlistId = :playlistId ORDER BY ps.position ASC")
    suspend fun getPlaylistSongs(playlistId: Long): List<Song>

    @Delete
    suspend fun removeSongFromPlaylist(playlistSong: PlaylistSong)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun clearPlaylist(playlistId: Long)
}
