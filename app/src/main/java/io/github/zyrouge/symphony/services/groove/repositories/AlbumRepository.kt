package io.github.zyrouge.symphony.services.groove.repositories

import io.github.zyrouge.symphony.AlbumSortBy
import io.github.zyrouge.symphony.Symphony
import io.github.zyrouge.symphony.services.groove.Album
import io.github.zyrouge.symphony.services.groove.Song
import io.github.zyrouge.symphony.ui.helpers.Assets
import io.github.zyrouge.symphony.ui.helpers.createHandyImageRequest
import io.github.zyrouge.symphony.utils.ConcurrentSet
import io.github.zyrouge.symphony.utils.FuzzySearchOption
import io.github.zyrouge.symphony.utils.FuzzySearcher
import io.github.zyrouge.symphony.utils.concurrentSetOf
import io.github.zyrouge.symphony.utils.joinToStringIfNotEmpty
import io.github.zyrouge.symphony.utils.withCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds

class AlbumRepository(private val symphony: Symphony) {

    private val cache = ConcurrentHashMap<String, Album>()
    private val songIdsCache = ConcurrentHashMap<String, ConcurrentSet<String>>()
    private val searcher = FuzzySearcher<String>(
        options = listOf(
            FuzzySearchOption({ v -> get(v)?.name?.let { compareString(it) } }, 3),
            FuzzySearchOption({ v -> get(v)?.artists?.let { compareCollection(it) } })
        )
    )

    val isUpdating get() = symphony.groove.exposer.isUpdating
    private val _all = MutableStateFlow<List<String>>(emptyList())
    val all = _all.asStateFlow()
    private val _count = MutableStateFlow(0)
    val count = _count.asStateFlow()

    private fun emitCount() = _count.update {
        cache.size
    }

    internal fun onSong(song: Song) {
        val albumId = getIdFromSong(song) ?: return
        songIdsCache.compute(albumId) { _, value ->
            value?.apply { add(song.id) } ?: concurrentSetOf(song.id)
        }
        cache.compute(albumId) { _, value ->
            value?.apply {
                artists.addAll(song.artists)
                song.year?.let {
                    startYear = startYear?.let { old -> min(old, it) } ?: it
                    endYear = endYear?.let { old -> max(old, it) } ?: it
                }
                if(song.date != null){
                    date = song.date
                }
                if (song.is_compilation) {
                    is_compilation = true
                }
                numberOfTracks++
                duration += song.duration.milliseconds
            } ?: run {
                _all.update {
                    it + albumId
                }
                emitCount()
                Album(
                    id = albumId,
                    name = song.album!!,
                    artists = mutableSetOf<String>().apply {
                        addAll(song.artists)
                    },
                    albumArtists = mutableSetOf<String>().apply {
                        addAll(song.albumArtists)
                    },
                    startYear = song.year,
                    endYear = song.year,
                    numberOfTracks = 1,
                    duration = song.duration.milliseconds,
                    is_compilation = song.is_compilation,
                    date = song.date,
                )
            }
        }
    }

    internal fun rebuildFromSongs(songs: List<Song>) {
        val newAlbumIds = mutableSetOf<String>()
        songs.forEach { song ->
            val albumId = getIdFromSong(song) ?: return@forEach
            songIdsCache.compute(albumId) { _, value ->
                value?.apply { add(song.id) } ?: concurrentSetOf(song.id)
            }
            cache.compute(albumId) { _, value ->
                value?.apply {
                    artists.addAll(song.artists)
                    song.year?.let {
                        startYear = startYear?.let { old -> min(old, it) } ?: it
                        endYear = endYear?.let { old -> max(old, it) } ?: it
                    }
                    if(song.date != null){
                        date = song.date
                    }
                    if (song.is_compilation) {
                        is_compilation = true
                    }
                    numberOfTracks++
                    duration += song.duration.milliseconds
                } ?: run {
                    newAlbumIds.add(albumId)
                    Album(
                        id = albumId,
                        name = song.album!!,
                        artists = mutableSetOf<String>().apply {
                            addAll(song.artists)
                        },
                        albumArtists = mutableSetOf<String>().apply {
                            addAll(song.albumArtists)
                        },
                        startYear = song.year,
                        endYear = song.year,
                        numberOfTracks = 1,
                        duration = song.duration.milliseconds,
                        is_compilation = song.is_compilation,
                    )
                }
            }
        }
        if (newAlbumIds.isNotEmpty()) {
            _all.update {
                it + newAlbumIds
            }
        }
        emitCount()
    }

    fun reset() {
        cache.clear()
        songIdsCache.clear()
        _all.update {
            emptyList()
        }
        emitCount()
    }

    fun getIdFromSong(song: Song): String? {
        if (song.album == null) {
            return null
        }
        val artists = song.albumArtists.sorted().joinToString("-")
        return "${song.album}-${artists}"
    }

    fun getArtworkUri(albumId: String) = songIdsCache[albumId]?.firstOrNull()
        ?.let { symphony.groove.song.getArtworkUri(it) }
        ?: symphony.groove.song.getDefaultArtworkUri()

    fun createArtworkImageRequest(albumId: String) = createHandyImageRequest(
        symphony.applicationContext,
        image = getArtworkUri(albumId),
        fallback = Assets.placeholderDarkId,
    )

    fun search(albumIds: List<String>, terms: String, limit: Int = 7) = searcher
        .search(terms, albumIds, maxLength = limit)

    fun sort(albumIds: List<String>, by: AlbumSortBy, reverse: Boolean): List<String> {
        val sensitive = symphony.settingsOLD.caseSensitiveSorting.value
        val sorted = when (by) {
            AlbumSortBy.ALBUM_CUSTOM -> albumIds
            AlbumSortBy.ALBUM_NAME -> albumIds.sortedBy { get(it)?.name?.withCase(sensitive) }
            AlbumSortBy.ALBUM_ARTIST_NAME -> albumIds.sortedBy {
                get(it)?.artists?.joinToStringIfNotEmpty(sensitive)
            }

            AlbumSortBy.ALBUM_TRACKS_COUNT -> albumIds.sortedBy { get(it)?.numberOfTracks }
            AlbumSortBy.ALBUM_YEAR -> albumIds.sortedBy { get(it)?.date }
            AlbumSortBy.UNRECOGNIZED -> albumIds
        }
        return if (reverse) sorted.reversed() else sorted
    }

    fun count() = cache.size
    fun ids() = cache.keys.toList()
    fun values() = cache.values.toList()

    fun get(albumId: String) = cache[albumId]
    fun get(albumIds: List<String>) = albumIds.mapNotNull { get(it) }.toList()
    fun getSongIds(albumId: String) = songIdsCache[albumId]?.toList() ?: emptyList()
}
