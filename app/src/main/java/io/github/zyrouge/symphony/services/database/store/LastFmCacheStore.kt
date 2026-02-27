package io.github.zyrouge.symphony.services.database.store

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity("lastfm_cache")
data class LastFmCacheEntry(
    @PrimaryKey val key: String,   // "album:{albumId}" or "artist:{artistName}"
    val scrobbleCount: Long,
    val fetchedAt: Long,
)

@Dao
interface LastFmCacheStore {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(vararg entry: LastFmCacheEntry)

    @Query("SELECT * FROM lastfm_cache")
    suspend fun all(): List<LastFmCacheEntry>

    @Query("DELETE FROM lastfm_cache")
    suspend fun clear(): Int
}
