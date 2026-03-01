package io.github.zyrouge.symphony.services.lastfm

import androidx.lifecycle.viewModelScope
import io.github.zyrouge.symphony.Symphony
import io.github.zyrouge.symphony.services.database.store.LastFmCacheEntry
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

class LastFmService(private val symphony: Symphony) : Symphony.Hooks {

    data class RefreshProgress(val completed: Int, val total: Int)

    // in-memory caches — keyed by albumId and artistName respectively
    private val albumScrobbles = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val artistScrobbles = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    private val _refreshProgress = MutableStateFlow<RefreshProgress?>(null)
    val refreshProgress = _refreshProgress.asStateFlow()

    fun getAlbumScrobbleCount(albumId: String): Long = albumScrobbles[albumId] ?: 0L
    fun getArtistScrobbleCount(artistName: String): Long = artistScrobbles[artistName] ?: 0L

    override fun onSymphonyReady() {
        symphony.viewModelScope.launch(Dispatchers.IO) {
            loadFromDatabase()
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
