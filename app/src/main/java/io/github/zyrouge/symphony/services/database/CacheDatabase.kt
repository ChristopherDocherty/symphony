package io.github.zyrouge.symphony.services.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import io.github.zyrouge.symphony.Symphony
import io.github.zyrouge.symphony.services.database.store.LastFmCacheEntry
import io.github.zyrouge.symphony.services.database.store.LastFmCacheStore
import io.github.zyrouge.symphony.services.database.store.LastFmPlayCountEntry
import io.github.zyrouge.symphony.services.database.store.LastFmPlayCountStore
import io.github.zyrouge.symphony.services.database.store.SongCacheStore
import io.github.zyrouge.symphony.services.groove.Song
import io.github.zyrouge.symphony.utils.RoomConvertors

@Database(
    entities = [Song::class, LastFmCacheEntry::class, LastFmPlayCountEntry::class],
    version = 7,
    autoMigrations = [
        AutoMigration(1, 2, CacheDatabase.Migration1To2::class),
        AutoMigration(2, 3),
        AutoMigration(3, 4),
        AutoMigration(4, 5, CacheDatabase.Migration4To5::class),
        AutoMigration(5, 6),
        AutoMigration(6, 7),
    ]
)
@TypeConverters(RoomConvertors::class)
abstract class CacheDatabase : RoomDatabase() {
    abstract fun songs(): SongCacheStore
    abstract fun lastFmCache(): LastFmCacheStore
    abstract fun lastFmPlayCounts(): LastFmPlayCountStore

    companion object {
        fun create(symphony: Symphony) = Room
            .databaseBuilder(
                symphony.applicationContext,
                CacheDatabase::class.java,
                "cache"
            )
            .fallbackToDestructiveMigration()
            .build()
    }

    @DeleteColumn("songs", "minBitrate")
    @DeleteColumn("songs", "maxBitrate")
    @DeleteColumn("songs", "bitsPerSample")
    @DeleteColumn("songs", "samples")
    @DeleteColumn("songs", "codec")
    class Migration1To2 : AutoMigrationSpec

    @DeleteColumn("songs", "is_compilation")
    class Migration4To5 : AutoMigrationSpec
}
