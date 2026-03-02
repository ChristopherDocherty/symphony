package io.github.zyrouge.symphony.services.groove

import io.github.zyrouge.symphony.Symphony
import io.github.zyrouge.symphony.services.groove.repositories.AlbumArtistRepository
import io.github.zyrouge.symphony.services.groove.repositories.AlbumRepository
import io.github.zyrouge.symphony.services.groove.repositories.ArtistRepository
import io.github.zyrouge.symphony.services.groove.repositories.GenreRepository
import io.github.zyrouge.symphony.services.groove.repositories.PlaylistRepository
import io.github.zyrouge.symphony.services.groove.repositories.SongRepository
import io.github.zyrouge.symphony.services.groove.repositories.WishlistRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

class Groove(private val symphony: Symphony) : Symphony.Hooks {
    enum class Kind {
        SONG,
        ALBUM,
        ARTIST,
        ALBUM_ARTIST,
        GENRE,
        PLAYLIST,
    }

    val coroutineScope = CoroutineScope(Dispatchers.Default)
    var readyDeferred = CompletableDeferred<Boolean>()

    val exposer = MediaExposer(symphony)
    val song = SongRepository(symphony)
    val album = AlbumRepository(symphony)
    val artist = ArtistRepository(symphony)
    val albumArtist = AlbumArtistRepository(symphony)
    val genre = GenreRepository(symphony)
    val playlist = PlaylistRepository(symphony)
    val wishlist = WishlistRepository(symphony)

    private suspend fun fetch() {
        coroutineScope.launch {
            exposer.fetch()
            playlist.fetch()
            wishlist.fetch()
        }.join()
    }

    private suspend fun fetchFromCache() {
        coroutineScope.launch {
            exposer.loadFromCache()
            playlist.fetch()
            wishlist.fetch()
        }.join()
    }

    private suspend fun reset() {
        coroutineScope.launch {
            awaitAll(
                async { exposer.reset() },
                async { albumArtist.reset() },
                async { album.reset() },
                async { artist.reset() },
                async { genre.reset() },
                async { playlist.reset() },
                async { wishlist.reset() },
                async { song.reset() },
            )
        }.join()
    }

    private suspend fun clearCache() {
        symphony.database.songCache.clear()
        symphony.database.artworkCache.clear()
        symphony.database.lyricsCache.clear()
        symphony.database.playlists.deleteAll()
    }

    data class FetchOptions(
        val resetInMemoryCache: Boolean = false,
        val resetPersistentCache: Boolean = false,
    )

    fun fetch(options: FetchOptions) {
        coroutineScope.launch {
            if (options.resetInMemoryCache) {
                reset()
            }
            if (options.resetPersistentCache) {
                clearCache()
            }
            fetch()
        }
    }

    fun fetchPaths(paths: List<String>) {
        coroutineScope.launch {
            exposer.fetchPaths(paths)
        }
    }

    override fun onSymphonyReady() {
        coroutineScope.launch {
            fetchFromCache()
            readyDeferred.complete(true)
        }
    }
}
