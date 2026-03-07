package io.github.zyrouge.symphony.services.lastfm

import androidx.lifecycle.viewModelScope
import io.github.zyrouge.symphony.Symphony
import io.github.zyrouge.symphony.services.database.store.LastFmCacheEntry
import io.github.zyrouge.symphony.services.database.store.LastFmCorrectionEntry
import io.github.zyrouge.symphony.services.radio.Radio
import io.github.zyrouge.symphony.utils.HttpClient
import io.github.zyrouge.symphony.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

class LastFmService(private val symphony: Symphony) : Symphony.Hooks {

    data class RefreshProgress(val completed: Int, val total: Int)

    // in-memory caches — keyed by albumId and artistName respectively
    private val albumScrobbles = ConcurrentHashMap<String, Long>()
    private val artistScrobbles = ConcurrentHashMap<String, Long>()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    private val _refreshProgress = MutableStateFlow<RefreshProgress?>(null)
    val refreshProgress = _refreshProgress.asStateFlow()

    // Artist name correction state
    private val artistCorrections = ConcurrentHashMap<String, String>() // localName → canonicalName
    private val checkedArtists = ConcurrentHashMap.newKeySet<String>()  // all checked (incl. no-correction)

    private val _correctionScanProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val correctionScanProgress = _correctionScanProgress.asStateFlow()  // (done, total) or null=idle

    private val _correctionResults = MutableStateFlow<Map<String, String>?>(null)
    val correctionResults = _correctionResults.asStateFlow()  // null=not scanned, empty=clean

    private val _artistCorrectionAlert = MutableStateFlow<Pair<String, String>?>(null)
    val artistCorrectionAlert = _artistCorrectionAlert.asStateFlow()

    fun getAlbumScrobbleCount(albumId: String): Long = albumScrobbles[albumId] ?: 0L
    fun getArtistScrobbleCount(artistName: String): Long = artistScrobbles[artistName] ?: 0L

    override fun onSymphonyReady() {
        symphony.viewModelScope.launch(Dispatchers.IO) {
            loadFromDatabase()
        }
        symphony.radio.onUpdate.subscribe { event ->
            if (event is Radio.Events.Queue.IndexChanged) {
                symphony.viewModelScope.launch(Dispatchers.IO) {
                    checkCurrentSongArtists()
                }
            }
        }
    }

    private suspend fun loadFromDatabase() {
        try {
            val entries = symphony.database.lastFmCache.all()
            for (entry in entries) {
                when {
                    entry.key.startsWith("album:") -> {
                        val albumId = entry.key.removePrefix("album:")
                        albumScrobbles[albumId] = entry.scrobbleCount
                    }
                    entry.key.startsWith("artist:") -> {
                        val artistName = entry.key.removePrefix("artist:")
                        artistScrobbles[artistName] = entry.scrobbleCount
                    }
                }
            }
        } catch (err: Exception) {
            Logger.error("LastFmService", "loadFromDatabase failed", err)
        }
        try {
            val correctionEntries = symphony.database.lastFmCorrections.all()
            for (entry in correctionEntries) {
                checkedArtists.add(entry.artistName)
                if (entry.correctedName != null) {
                    artistCorrections[entry.artistName] = entry.correctedName
                }
            }
        } catch (err: Exception) {
            Logger.error("LastFmService", "loadCorrectionsFromDatabase failed", err)
        }
    }

    private fun checkCurrentSongArtists() {
        val apiKey = symphony.settingsState.value.lastFmApiKey
        if (apiKey.isBlank()) return
        val index = symphony.radio.queue.currentSongIndex
        if (index < 0) return
        val queue = symphony.radio.queue.currentQueue
        if (index >= queue.size) return
        val songId = queue[index]
        val song = symphony.groove.song.get(songId) ?: return
        for (artist in song.artists) {
            val canonical = artistCorrections[artist]
            if (canonical != null) {
                _artistCorrectionAlert.value = Pair(artist, canonical)
                return
            }
            if (artist !in checkedArtists) {
                symphony.viewModelScope.launch(Dispatchers.IO) {
                    val corrected = LastFmScrobbler.getArtistCorrection(apiKey, artist)
                    val entry = LastFmCorrectionEntry(
                        artistName = artist,
                        correctedName = corrected,
                        fetchedAt = System.currentTimeMillis(),
                    )
                    try {
                        symphony.database.lastFmCorrections.upsert(entry)
                    } catch (err: Exception) {
                        Logger.error("LastFmService", "failed to persist correction for $artist", err)
                    }
                    checkedArtists.add(artist)
                    if (corrected != null) {
                        artistCorrections[artist] = corrected
                    }
                    checkCurrentSongArtists()
                }
                return
            }
        }
        _artistCorrectionAlert.value = null
    }

    fun dismissCorrectionAlert() {
        _artistCorrectionAlert.value = null
    }

    fun scanArtistCorrections() {
        if (_correctionScanProgress.value != null) return
        val apiKey = symphony.settingsState.value.lastFmApiKey
        if (apiKey.isBlank()) return
        symphony.viewModelScope.launch(Dispatchers.IO) {
            val allArtists = symphony.groove.artist.ids()
            val unchecked = allArtists.filter { it !in checkedArtists }
            _correctionScanProgress.value = Pair(0, unchecked.size)
            unchecked.forEachIndexed { idx, artist ->
                val corrected = LastFmScrobbler.getArtistCorrection(apiKey, artist)
                val entry = LastFmCorrectionEntry(
                    artistName = artist,
                    correctedName = corrected,
                    fetchedAt = System.currentTimeMillis(),
                )
                try {
                    symphony.database.lastFmCorrections.upsert(entry)
                } catch (err: Exception) {
                    Logger.error("LastFmService", "failed to persist correction for $artist", err)
                }
                checkedArtists.add(artist)
                if (corrected != null) {
                    artistCorrections[artist] = corrected
                }
                delay(200)
                _correctionScanProgress.value = Pair(idx + 1, unchecked.size)
            }
            _correctionResults.value = artistCorrections.toMap()
            _correctionScanProgress.value = null
        }
    }

    fun fixArtistName(localName: String, canonicalName: String) {
        symphony.viewModelScope.launch(Dispatchers.IO) {
            val songIds = symphony.groove.artist.getSongIds(localName)
            val affectedPaths = mutableListOf<String>()
            for (songId in songIds) {
                val song = symphony.groove.song.get(songId) ?: continue
                val tags = mutableMapOf<String, String>()
                tags["ARTIST"] = song.artists.map { if (it == localName) canonicalName else it }.joinToString(", ")
                if (song.albumArtists.contains(localName)) {
                    tags["ALBUMARTIST"] = song.albumArtists.map { if (it == localName) canonicalName else it }.joinToString(", ")
                }
                val fd = symphony.applicationContext.contentResolver
                    .openFileDescriptor(song.uri, "rw")
                    ?.detachFd()
                    ?: continue
                try {
                    me.zyrouge.symphony.metaphony.AudioMetadataParser.write(song.filename, fd, tags)
                    affectedPaths.add(song.path)
                } catch (err: Exception) {
                    Logger.error("LastFmService", "failed to write tags for ${song.path}", err)
                }
            }
            if (affectedPaths.isNotEmpty()) {
                symphony.groove.fetchPaths(affectedPaths)
            }
            artistCorrections.remove(localName)
            artistCorrections[canonicalName] = canonicalName
            checkedArtists.add(canonicalName)
            try {
                symphony.database.lastFmCorrections.upsert(
                    LastFmCorrectionEntry(
                        artistName = canonicalName,
                        correctedName = null,
                        fetchedAt = System.currentTimeMillis(),
                    )
                )
            } catch (err: Exception) {
                Logger.error("LastFmService", "failed to persist correction for $canonicalName", err)
            }
            _artistCorrectionAlert.value = null
            _correctionResults.value = artistCorrections.toMap()
        }
    }

    fun refresh() {
        if (_isRefreshing.value) return
        symphony.viewModelScope.launch(Dispatchers.IO) {
            val apiKey = symphony.settingsState.value.lastFmApiKey
            val username = symphony.settingsState.value.lastFmUsername
            if (apiKey.isBlank() || username.isBlank()) return@launch

            _isRefreshing.value = true

            val albumIds = symphony.groove.album.ids()
            val artistNames = symphony.groove.artist.ids()
            val total = albumIds.size + artistNames.size
            var completed = 0
            _refreshProgress.value = RefreshProgress(0, total)

            // Fetch albums — query each (artist, album) pair and sum for multi-artist albums
            for (albumId in albumIds) {
                val album = symphony.groove.album.get(albumId) ?: continue
                var totalCount: Long? = null
                for (artist in album.artists) {
                    val count = fetchAlbumScrobbles(artist, album.name, username, apiKey)
                    if (count != null) totalCount = (totalCount ?: 0L) + count
                    delay(100) // 10 req/sec
                }
                if (totalCount != null) {
                    albumScrobbles[albumId] = totalCount
                    symphony.database.lastFmCache.upsert(
                        LastFmCacheEntry("album:$albumId", totalCount, System.currentTimeMillis())
                    )
                }
                completed++
                _refreshProgress.value = RefreshProgress(completed, total)
            }

            // Fetch artists
            for (artistName in artistNames) {
                val count = fetchArtistScrobbles(artistName, username, apiKey)
                if (count != null) {
                    artistScrobbles[artistName] = count
                    symphony.database.lastFmCache.upsert(
                        LastFmCacheEntry("artist:$artistName", count, System.currentTimeMillis())
                    )
                }
                completed++
                _refreshProgress.value = RefreshProgress(completed, total)
                delay(100)
            }

            _refreshProgress.value = null
            _isRefreshing.value = false
        }
    }

    fun refreshForAlbums(albumIds: List<String>) {
        if (_isRefreshing.value) return
        symphony.viewModelScope.launch(Dispatchers.IO) {
            val apiKey = symphony.settingsState.value.lastFmApiKey
            val username = symphony.settingsState.value.lastFmUsername
            if (apiKey.isBlank() || username.isBlank()) return@launch

            _isRefreshing.value = true

            val artistNames = albumIds
                .flatMap { symphony.groove.album.get(it)?.artists ?: emptyList() }
                .toSet()
            val total = albumIds.size + artistNames.size
            var completed = 0
            _refreshProgress.value = RefreshProgress(0, total)

            for (albumId in albumIds) {
                val album = symphony.groove.album.get(albumId) ?: continue
                var totalCount: Long? = null
                for (artist in album.artists) {
                    val count = fetchAlbumScrobbles(artist, album.name, username, apiKey)
                    if (count != null) totalCount = (totalCount ?: 0L) + count
                    delay(100) // 10 req/sec
                }
                if (totalCount != null) {
                    albumScrobbles[albumId] = totalCount
                    symphony.database.lastFmCache.upsert(
                        LastFmCacheEntry("album:$albumId", totalCount, System.currentTimeMillis())
                    )
                }
                completed++
                _refreshProgress.value = RefreshProgress(completed, total)
            }

            for (artistName in artistNames) {
                val count = fetchArtistScrobbles(artistName, username, apiKey)
                if (count != null) {
                    artistScrobbles[artistName] = count
                    symphony.database.lastFmCache.upsert(
                        LastFmCacheEntry("artist:$artistName", count, System.currentTimeMillis())
                    )
                }
                completed++
                _refreshProgress.value = RefreshProgress(completed, total)
                delay(100)
            }

            _refreshProgress.value = null
            _isRefreshing.value = false
        }
    }

    private fun fetchAlbumScrobbles(artist: String, album: String, username: String, apiKey: String): Long? {
        return try {
            val url = buildString {
                append("https://ws.audioscrobbler.com/2.0/")
                append("?method=album.getInfo")
                append("&api_key=${enc(apiKey)}")
                append("&artist=${enc(artist)}")
                append("&album=${enc(album)}")
                append("&username=${enc(username)}")
                append("&format=json")
            }
            val json = fetch(url) ?: return null
            if (json.has("error")) return 0L
            json.getJSONObject("album").getString("userplaycount").toLongOrNull()
        } catch (err: Exception) {
            Logger.warn("LastFmService", "fetchAlbumScrobbles failed for $album", err)
            null
        }
    }

    private fun fetchArtistScrobbles(artist: String, username: String, apiKey: String): Long? {
        return try {
            val url = buildString {
                append("https://ws.audioscrobbler.com/2.0/")
                append("?method=artist.getInfo")
                append("&api_key=${enc(apiKey)}")
                append("&artist=${enc(artist)}")
                append("&username=${enc(username)}")
                append("&format=json")
            }
            val json = fetch(url) ?: return null
            if (json.has("error")) return 0L
            json.getJSONObject("artist").getJSONObject("stats").getString("userplaycount").toLongOrNull()
        } catch (err: Exception) {
            Logger.warn("LastFmService", "fetchArtistScrobbles failed for $artist", err)
            null
        }
    }

    private fun fetch(url: String): JSONObject? {
        val req = Request.Builder().url(url).build()
        val body = HttpClient.newCall(req).execute().body?.string() ?: return null
        return JSONObject(body)
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}
