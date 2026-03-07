package io.github.zyrouge.symphony.services.database.store

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity("lastfm_corrections")
data class LastFmCorrectionEntry(
    @PrimaryKey val artistName: String,   // local name (key)
    val correctedName: String?,           // null = checked, no correction found
    val fetchedAt: Long,
)

@Dao
interface LastFmCorrectionStore {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(vararg entry: LastFmCorrectionEntry)

    @Query("SELECT * FROM lastfm_corrections")
    suspend fun all(): List<LastFmCorrectionEntry>

    @Query("DELETE FROM lastfm_corrections")
    suspend fun clear(): Int
}
