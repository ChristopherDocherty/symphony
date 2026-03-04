package io.github.zyrouge.symphony.services.database.store

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction

@Entity("lastfm_play_counts")
data class LastFmPlayCountEntry(
    @PrimaryKey val key: String,  // "artist_lowercase|track_lowercase"
    val count: Long,
)

@Dao
abstract class LastFmPlayCountStore {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(vararg entry: LastFmPlayCountEntry)

    @Query("SELECT * FROM lastfm_play_counts")
    abstract suspend fun all(): List<LastFmPlayCountEntry>

    @Query("DELETE FROM lastfm_play_counts")
    abstract suspend fun clear(): Int

    /** Atomically replaces all play count entries. */
    @Transaction
    open suspend fun replace(entries: List<LastFmPlayCountEntry>) {
        clear()
        if (entries.isNotEmpty()) upsert(*entries.toTypedArray())
    }
}
