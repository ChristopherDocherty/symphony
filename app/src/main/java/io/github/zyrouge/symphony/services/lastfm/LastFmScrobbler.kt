package io.github.zyrouge.symphony.services.lastfm

import io.github.zyrouge.symphony.utils.HttpClient
import io.github.zyrouge.symphony.utils.Logger
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest

data class LastFmRecentTrack(
    val artist: String,
    val track: String,
    val album: String,
    val timestampSeconds: Long,
)

data class RecentTracksPage(
    val tracks: List<LastFmRecentTrack>,
    val totalPages: Int,
    val total: Long,
)

data class LastFmAlbumResult(
    val name: String,
    val artist: String,
    val coverUrl: String?,
    val mbid: String,
)

data class LastFmAlbumTrack(
    val name: String,
    val durationSeconds: Int,
    val rank: Int,
)

data class LastFmAlbumInfo(
    val name: String,
    val artist: String,
    val coverUrl: String?,
    val tracks: List<LastFmAlbumTrack>,
)

object LastFmScrobbler {

    fun sign(params: Map<String, String>, apiSecret: String): String {
        val sb = StringBuilder()
        params.entries
            .filter { it.key != "format" }
            .sortedBy { it.key }
            .forEach { (k, v) -> sb.append(k).append(v) }
        sb.append(apiSecret)
        val digest = MessageDigest.getInstance("MD5").digest(sb.toString().toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun getToken(apiKey: String, apiSecret: String): String? {
        return try {
            val params = mapOf(
                "method" to "auth.getToken",
                "api_key" to apiKey,
            )
            val sig = sign(params, apiSecret)
            val url = buildString {
                append("https://ws.audioscrobbler.com/2.0/")
                append("?method=auth.getToken")
                append("&api_key=${enc(apiKey)}")
                append("&api_sig=${enc(sig)}")
                append("&format=json")
            }
            val body = fetch(url) ?: return null
            body.optString("token").takeIf { it.isNotEmpty() }
        } catch (err: Exception) {
            Logger.warn("LastFmScrobbler", "getToken failed", err)
            null
        }
    }

    fun buildAuthUrl(apiKey: String, token: String): String {
        return "https://www.last.fm/api/auth/?api_key=${enc(apiKey)}&token=${enc(token)}"
    }

    fun getSession(apiKey: String, apiSecret: String, token: String): String? {
        return try {
            val params = mapOf(
                "method" to "auth.getSession",
                "api_key" to apiKey,
                "token" to token,
            )
            val sig = sign(params, apiSecret)
            val url = buildString {
                append("https://ws.audioscrobbler.com/2.0/")
                append("?method=auth.getSession")
                append("&api_key=${enc(apiKey)}")
                append("&token=${enc(token)}")
                append("&api_sig=${enc(sig)}")
                append("&format=json")
            }
            val body = fetch(url) ?: return null
            if (body.has("error")) return null
            body.getJSONObject("session").optString("key").takeIf { it.isNotEmpty() }
        } catch (err: Exception) {
            Logger.warn("LastFmScrobbler", "getSession failed", err)
            null
        }
    }

    fun scrobble(
        apiKey: String,
        apiSecret: String,
        sessionKey: String,
        artist: String,
        track: String,
        album: String?,
        timestampSeconds: Long,
    ): Boolean {
        return try {
            val params = mutableMapOf(
                "method" to "track.scrobble",
                "api_key" to apiKey,
                "sk" to sessionKey,
                "artist[0]" to artist,
                "track[0]" to track,
                "timestamp[0]" to timestampSeconds.toString(),
            )
            if (!album.isNullOrBlank()) params["album[0]"] = album
            val sig = sign(params, apiSecret)
            val formBody = FormBody.Builder().apply {
                for ((k, v) in params) add(k, v)
                add("api_sig", sig)
                add("format", "json")
            }.build()
            val req = Request.Builder()
                .url("https://ws.audioscrobbler.com/2.0/")
                .post(formBody)
                .build()
            val responseBody = HttpClient.newCall(req).execute().body?.string() ?: return false
            val json = JSONObject(responseBody)
            !json.has("error")
        } catch (err: Exception) {
            Logger.warn("LastFmScrobbler", "scrobble failed", err)
            false
        }
    }

    fun getRecentTracksPage(
        apiKey: String,
        username: String,
        page: Int,
        limit: Int = 200,
        from: Long? = null,
        to: Long? = null,
    ): RecentTracksPage? {
        return try {
            val url = buildString {
                append("https://ws.audioscrobbler.com/2.0/")
                append("?method=user.getRecentTracks")
                append("&api_key=${enc(apiKey)}")
                append("&user=${enc(username)}")
                append("&limit=$limit")
                append("&page=$page")
                append("&format=json")
                if (from != null) append("&from=$from")
                if (to != null) append("&to=$to")
            }
            val body = fetch(url) ?: return null
            if (body.has("error")) return null
            val recenttracks = body.getJSONObject("recenttracks")
            val attr = recenttracks.optJSONObject("@attr")
            val totalPages = attr?.optString("totalPages")?.toIntOrNull() ?: 1
            val total = attr?.optString("total")?.toLongOrNull() ?: 0L
            val tracksArray = recenttracks.getJSONArray("track")
            val tracks = mutableListOf<LastFmRecentTrack>()
            for (i in 0 until tracksArray.length()) {
                val t = tracksArray.getJSONObject(i)
                val ts = t.optJSONObject("date")?.optString("uts")?.toLongOrNull() ?: 0L
                tracks.add(
                    LastFmRecentTrack(
                        artist = t.optJSONObject("artist")?.optString("#text") ?: t.optString("artist"),
                        track = t.optString("name"),
                        album = t.optJSONObject("album")?.optString("#text") ?: "",
                        timestampSeconds = ts,
                    )
                )
            }
            RecentTracksPage(tracks, totalPages, total)
        } catch (err: Exception) {
            Logger.warn("LastFmScrobbler", "getRecentTracksPage failed", err)
            null
        }
    }

    fun getRecentTracks(apiKey: String, username: String, limit: Int = 20): List<LastFmRecentTrack>? {
        return try {
            val url = buildString {
                append("https://ws.audioscrobbler.com/2.0/")
                append("?method=user.getRecentTracks")
                append("&api_key=${enc(apiKey)}")
                append("&user=${enc(username)}")
                append("&limit=$limit")
                append("&format=json")
            }
            val body = fetch(url) ?: return null
            if (body.has("error")) return null
            val tracks = body.getJSONObject("recenttracks").getJSONArray("track")
            val result = mutableListOf<LastFmRecentTrack>()
            for (i in 0 until tracks.length()) {
                val t = tracks.getJSONObject(i)
                val ts = t.optJSONObject("date")?.optString("uts")?.toLongOrNull() ?: 0L
                result.add(
                    LastFmRecentTrack(
                        artist = t.optJSONObject("artist")?.optString("#text") ?: t.optString("artist"),
                        track = t.optString("name"),
                        album = t.optJSONObject("album")?.optString("#text") ?: "",
                        timestampSeconds = ts,
                    )
                )
            }
            result
        } catch (err: Exception) {
            Logger.warn("LastFmScrobbler", "getRecentTracks failed", err)
            null
        }
    }

    fun getArtistCorrection(apiKey: String, artistName: String): String? {
        return try {
            val url = buildString {
                append("https://ws.audioscrobbler.com/2.0/")
                append("?method=artist.getCorrection")
                append("&artist=${enc(artistName)}")
                append("&api_key=${enc(apiKey)}")
                append("&format=json")
            }
            val body = fetch(url) ?: return null
            val correction = body.optJSONObject("corrections")
                ?.optJSONObject("correction")
                ?.optJSONObject("artist")
                ?.optString("name")
                ?.takeIf { it.isNotEmpty() }
                ?: return null
            if (correction != artistName) correction else null
        } catch (err: Exception) {
            Logger.warn("LastFmScrobbler", "getArtistCorrection failed for $artistName", err)
            null
        }
    }

    fun searchAlbums(apiKey: String, query: String): List<LastFmAlbumResult>? {
        return try {
            val url = buildString {
                append("https://ws.audioscrobbler.com/2.0/")
                append("?method=album.search")
                append("&album=${enc(query)}")
                append("&api_key=${enc(apiKey)}")
                append("&limit=30")
                append("&format=json")
            }
            val body = fetch(url) ?: return null
            if (body.has("error")) return null
            val albummatches = body.getJSONObject("results").getJSONObject("albummatches")
            val albumsArray = albummatches.optJSONArray("album")
                ?: albummatches.optJSONObject("album")?.let {
                    org.json.JSONArray().apply { put(it) }
                }
                ?: return emptyList()
            val results = mutableListOf<LastFmAlbumResult>()
            for (i in 0 until albumsArray.length()) {
                val a = albumsArray.getJSONObject(i)
                val images = a.optJSONArray("image")
                var coverUrl: String? = null
                if (images != null) {
                    for (j in 0 until images.length()) {
                        val img = images.getJSONObject(j)
                        if (img.optString("size") == "medium") {
                            coverUrl = img.optString("#text").takeIf { it.isNotBlank() }
                            break
                        }
                    }
                }
                results.add(
                    LastFmAlbumResult(
                        name = a.optString("name"),
                        artist = a.optString("artist"),
                        coverUrl = coverUrl,
                        mbid = a.optString("mbid"),
                    )
                )
            }
            results
        } catch (err: Exception) {
            Logger.warn("LastFmScrobbler", "searchAlbums failed", err)
            null
        }
    }

    fun getAlbumInfo(apiKey: String, artist: String, album: String): LastFmAlbumInfo? {
        return try {
            val url = buildString {
                append("https://ws.audioscrobbler.com/2.0/")
                append("?method=album.getInfo")
                append("&artist=${enc(artist)}")
                append("&album=${enc(album)}")
                append("&api_key=${enc(apiKey)}")
                append("&format=json")
            }
            val body = fetch(url) ?: return null
            if (body.has("error")) return null
            val albumObj = body.getJSONObject("album")
            val images = albumObj.optJSONArray("image")
            var coverUrl: String? = null
            if (images != null) {
                for (j in 0 until images.length()) {
                    val img = images.getJSONObject(j)
                    if (img.optString("size") == "medium") {
                        coverUrl = img.optString("#text").takeIf { it.isNotBlank() }
                        break
                    }
                }
            }
            val tracks = mutableListOf<LastFmAlbumTrack>()
            val tracksObj = albumObj.optJSONObject("tracks")
            if (tracksObj != null) {
                val tracksArray = tracksObj.optJSONArray("track")
                    ?: tracksObj.optJSONObject("track")?.let {
                        org.json.JSONArray().apply { put(it) }
                    }
                if (tracksArray != null) {
                    for (i in 0 until tracksArray.length()) {
                        val t = tracksArray.getJSONObject(i)
                        val rank = t.optJSONObject("@attr")?.optString("rank")?.toIntOrNull() ?: 0
                        val duration = t.optString("duration").toIntOrNull() ?: 0
                        tracks.add(LastFmAlbumTrack(name = t.optString("name"), durationSeconds = duration, rank = rank))
                    }
                    tracks.sortBy { it.rank }
                }
            }
            LastFmAlbumInfo(
                name = albumObj.optString("name"),
                artist = albumObj.optString("artist"),
                coverUrl = coverUrl,
                tracks = tracks,
            )
        } catch (err: Exception) {
            Logger.warn("LastFmScrobbler", "getAlbumInfo failed", err)
            null
        }
    }

    fun scrobbleBatch(
        apiKey: String,
        apiSecret: String,
        sessionKey: String,
        tracks: List<Triple<String, String, Long>>,
        album: String,
    ): Boolean {
        if (tracks.isEmpty()) return true
        return try {
            for (chunk in tracks.chunked(50)) {
                val params = mutableMapOf(
                    "method" to "track.scrobble",
                    "api_key" to apiKey,
                    "sk" to sessionKey,
                )
                for ((i, triple) in chunk.withIndex()) {
                    params["artist[$i]"] = triple.first
                    params["track[$i]"] = triple.second
                    params["timestamp[$i]"] = triple.third.toString()
                    params["album[$i]"] = album
                }
                val sig = sign(params, apiSecret)
                val formBody = FormBody.Builder().apply {
                    for ((k, v) in params) add(k, v)
                    add("api_sig", sig)
                    add("format", "json")
                }.build()
                val req = Request.Builder()
                    .url("https://ws.audioscrobbler.com/2.0/")
                    .post(formBody)
                    .build()
                val responseBody = HttpClient.newCall(req).execute().body?.string() ?: return false
                val json = JSONObject(responseBody)
                if (json.has("error")) return false
            }
            true
        } catch (err: Exception) {
            Logger.warn("LastFmScrobbler", "scrobbleBatch failed", err)
            false
        }
    }

    private fun fetch(url: String): JSONObject? {
        val req = Request.Builder().url(url).build()
        val body = HttpClient.newCall(req).execute().body?.string() ?: return null
        return JSONObject(body)
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}
