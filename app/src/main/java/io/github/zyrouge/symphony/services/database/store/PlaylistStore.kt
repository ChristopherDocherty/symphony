package io.github.zyrouge.symphony.services.database.store

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.MapColumn
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import io.github.zyrouge.symphony.services.groove.Playlist

@Dao
interface PlaylistStore {
    @Insert
    suspend fun insert(vararg playlist: Playlist): List<Long>


    @Upsert
    suspend fun upsert(vararg playlist: Playlist): List<Long>

    @Update
    suspend fun update(vararg playlist: Playlist): Int

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun delete(playlistId: String): Int

    @Query("DELETE FROM playlists")
    suspend fun deleteAll(): Int

    @Query("UPDATE playlists SET ignored = 1 WHERE id = :playlistId")
    suspend fun softDelete(playlistId: String): Int

    @Query("SELECT id FROM playlists WHERE ignored = 1")
    suspend fun ignoredIds(): List<String>

    @Query("SELECT * FROM playlists WHERE ignored = 0")
    suspend fun entries(): Map<@MapColumn("id") String, Playlist>
}
