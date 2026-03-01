package io.github.zyrouge.symphony.services.groove.repositories

import io.github.zyrouge.symphony.AlbumFilter
import io.github.zyrouge.symphony.AlbumSortBy
import io.github.zyrouge.symphony.Symphony
import io.github.zyrouge.symphony.services.groove.ALBUM_DEBUG_FILTER_FIELDS
import io.github.zyrouge.symphony.services.groove.ALBUM_STRING_FILTER_FIELDS
import io.github.zyrouge.symphony.services.groove.ARTWORK_SOURCE_DIRECTORY
import io.github.zyrouge.symphony.services.groove.ARTWORK_SOURCE_EMBEDDED
import io.github.zyrouge.symphony.services.groove.ARTWORK_SOURCE_NONE
import io.github.zyrouge.symphony.services.groove.BITRATE_RANGE_HIGH
import io.github.zyrouge.symphony.services.groove.BITRATE_RANGE_LOW
import io.github.zyrouge.symphony.services.groove.BITRATE_RANGE_MEDIUM
import io.github.zyrouge.symphony.services.groove.BITRATE_RANGE_UNKNOWN
import io.github.zyrouge.symphony.services.groove.BITRATE_RANGE_VERY_HIGH
import io.github.zyrouge.symphony.services.groove.BLANK_TAG_VALUE
import io.github.zyrouge.symphony.services.groove.Album
import io.github.zyrouge.symphony.services.groove.Song
import io.github.zyrouge.symphony.ui.helpers.Assets
import io.github.zyrouge.symphony.ui.helpers.createHandyImageRequest
import io.github.zyrouge.symphony.utils.ConcurrentSet
import io.github.zyrouge.symphony.utils.FuzzySearchOption
import io.github.zyrouge.symphony.utils.FuzzySearcher
import io.github.zyrouge.symphony.utils.SimplePath
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
    private val customTagValuesCache = ConcurrentHashMap<String, ConcurrentHashMap<String, MutableSet<String>>>()
    // Debug filter data: minimum known bitrate (bps) per album; absent = no bitrate data in any song
    private val minBitrateCache = ConcurrentHashMap<String, Long>()
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

    private fun indexCustomTags(albumId: String, song: Song) {
        if (song.customTags.isEmpty()) return
        customTagValuesCache.compute(albumId) { _, tagMap ->
            (tagMap ?: ConcurrentHashMap()).apply {
                song.customTags.forEach { (tagName, value) ->
                    compute(tagName) { _, values -> (values ?: mutableSetOf()).apply { add(value) } }
                }
            }
        }
    }

    fun getAvailableTagValues(tagName: String): List<String> {
        val values = customTagValuesCache.values.flatMapTo(mutableSetOf()) { it[tagName] ?: emptySet() }
        val hasBlank = cache.keys.any { albumId ->
            customTagValuesCache[albumId]?.containsKey(tagName) != true
        }
        return buildList {
            if (hasBlank) add(BLANK_TAG_VALUE)
            addAll(values.sorted())
        }
    }

    internal fun onSong(song: Song) {
        val albumId = getIdFromSong(song) ?: return
        songIdsCache.compute(albumId) { _, value ->
            value?.apply { add(song.id) } ?: concurrentSetOf(song.id)
        }
        indexCustomTags(albumId, song)
        song.bitrate?.let { bitrate ->
            minBitrateCache.merge(albumId, bitrate, ::minOf)
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
            indexCustomTags(albumId, song)
            song.bitrate?.let { bitrate ->
                minBitrateCache.merge(albumId, bitrate, ::minOf)
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
                        date=song.date
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
        customTagValuesCache.clear()
        minBitrateCache.clear()
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

    fun getHiddenSongIds(hiddenAlbumIds: Set<String>): Set<String> =
        hiddenAlbumIds.flatMapTo(mutableSetOf()) { songIdsCache[it]?.toList() ?: emptyList() }

    fun getAlbums(
        albumIds: List<String>,
        by: AlbumSortBy,
        reverse: Boolean,
        filter: AlbumFilter = AlbumFilter.getDefaultInstance(),
        hiddenAlbumIds: Set<String> = emptySet(),
        showHidden: Boolean = false,
    ): List<String> {
        val sensitive = symphony.settingsState.value.caseSensitiveSorting

        val visibleIds = if (showHidden || hiddenAlbumIds.isEmpty()) albumIds
                         else albumIds.filterNot { it in hiddenAlbumIds }

        val debugMode = symphony.settingsState.value.debugMode
        val filteredAlbumIds = visibleIds.filter { albumId ->
            ALBUM_STRING_FILTER_FIELDS.all { field ->
                val selected = field.getSelected(filter)
                if (selected.isEmpty()) return@all true
                val albumValues = customTagValuesCache[albumId]?.get(field.tagName) ?: emptySet()
                (BLANK_TAG_VALUE in selected && albumValues.isEmpty()) || albumValues.any { it in selected }
            } && (!debugMode || run {
                // Bitrate range filter
                val bitrateSelected = filter.bitrateRangeList
                val bitrateOk = bitrateSelected.isEmpty() || getBitrateRange(albumId) in bitrateSelected
                // Artwork source filter
                val artworkSelected = filter.artworkSourceList
                val artworkOk = artworkSelected.isEmpty() || getArtworkSource(albumId) in artworkSelected
                bitrateOk && artworkOk
            })
        }
        val sorted = when (by) {
            AlbumSortBy.ALBUM_CUSTOM -> filteredAlbumIds
            AlbumSortBy.ALBUM_NAME -> filteredAlbumIds.sortedBy { get(it)?.name?.withCase(sensitive) }
            AlbumSortBy.ALBUM_ARTIST_NAME -> filteredAlbumIds.sortedBy {
                get(it)?.artists?.let { artists ->
                    if (artists.isEmpty()) null
                    else artists.joinToString { name ->
                        if (name.startsWith("The ", ignoreCase = true)) name.substring(4) else name
                    }.withCase(sensitive)
                }
            }

            AlbumSortBy.ALBUM_TRACKS_COUNT -> filteredAlbumIds.sortedBy { get(it)?.numberOfTracks }
            AlbumSortBy.ALBUM_YEAR -> filteredAlbumIds.sortedBy { get(it)?.date }
            AlbumSortBy.ALBUM_SCROBBLE_COUNT -> filteredAlbumIds.sortedBy {
                symphony.lastFm.getAlbumScrobbleCount(it)
            }
            AlbumSortBy.UNRECOGNIZED -> filteredAlbumIds
        }
        return if (reverse) sorted.reversed() else sorted
    }

    fun count() = cache.size
    fun ids() = cache.keys.toList()
    fun values() = cache.values.toList()

    fun get(albumId: String) = cache[albumId]
    fun get(albumIds: List<String>) = albumIds.mapNotNull { get(it) }.toList()
    fun getSongIds(albumId: String) = songIdsCache[albumId]?.toList() ?: emptyList()
    fun getCustomTagValues(albumId: String, tagName: String): Set<String> =
        customTagValuesCache[albumId]?.get(tagName) ?: emptySet()

    // ── Debug filter helpers ────────────────────────────────────────────────

    /**
     * Returns the bitrate bucket for [albumId] based on the minimum known bitrate
     * across all songs in the album (i.e. the "worst quality" track determines the
     * bucket).  Albums where no song has bitrate metadata return [BITRATE_RANGE_UNKNOWN].
     */
    fun getBitrateRange(albumId: String): String {
        val minBitrate = minBitrateCache[albumId] ?: return BITRATE_RANGE_UNKNOWN
        return when {
            minBitrate < 150_000L -> BITRATE_RANGE_LOW
            minBitrate < 256_000L -> BITRATE_RANGE_MEDIUM
            minBitrate < 320_000L -> BITRATE_RANGE_HIGH
            else -> BITRATE_RANGE_VERY_HIGH
        }
    }

    /**
     * Returns the artwork source for [albumId] by inspecting the first song in the
     * album.  Priority matches [SongRepository.getArtworkUri]:
     * 1. Directory-level colocated file  → [ARTWORK_SOURCE_DIRECTORY]
     * 2. Embedded cover in the audio file → [ARTWORK_SOURCE_EMBEDDED]
     * 3. No artwork                        → [ARTWORK_SOURCE_NONE]
     */
    fun getArtworkSource(albumId: String): String {
        val firstSongId = songIdsCache[albumId]?.firstOrNull() ?: return ARTWORK_SOURCE_NONE
        val firstSong = symphony.groove.song.get(firstSongId) ?: return ARTWORK_SOURCE_NONE
        val parentDir = SimplePath(firstSong.path).parent?.pathString
        if (parentDir != null && symphony.database.directoryArtworkCache.get(parentDir) != null) {
            return ARTWORK_SOURCE_DIRECTORY
        }
        return if (firstSong.coverFile != null) ARTWORK_SOURCE_EMBEDDED else ARTWORK_SOURCE_NONE
    }
}
