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

    private fun fetch(url: String): JSONObject? {
        val req = Request.Builder().url(url).build()
        val body = HttpClient.newCall(req).execute().body?.string() ?: return null
        return JSONObject(body)
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}
