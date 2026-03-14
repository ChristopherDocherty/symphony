package io.github.zyrouge.symphony.services.database

import io.github.zyrouge.symphony.Symphony
import io.github.zyrouge.symphony.services.database.store.ArtworkCacheStore
import io.github.zyrouge.symphony.services.database.store.DirectoryArtworkCacheStore
import io.github.zyrouge.symphony.services.database.store.LyricsCacheStore

class Database(symphony: Symphony) {
    private val cache = CacheDatabase.create(symphony)
    private val persistent = PersistentDatabase.create(symphony)

    val artworkCache = ArtworkCacheStore(symphony)
    val lyricsCache = LyricsCacheStore(symphony)
    val lyricsMtimeCache = LyricsCacheStore(symphony, "lyrics_mtime")
    val directoryArtworkCache =DirectoryArtworkCacheStore(symphony)
    val songCache get() = cache.songs()
    val lastFmCache get() = cache.lastFmCache()
    val lastFmPlayCounts get() = cache.lastFmPlayCounts()
    val lastFmCorrections get() = cache.lastFmCorrections()
    val playlists get() = persistent.playlists()
}
