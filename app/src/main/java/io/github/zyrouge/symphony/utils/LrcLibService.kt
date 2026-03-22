package io.github.zyrouge.symphony.utils

import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

object LrcLibService {
    private const val BASE = "https://lrclib.net/api"
    private const val USER_AGENT = "Symphony/1.0 (io.github.zyrouge.symphony)"

    data class Track(
        val id: Long,
        val trackName: String,
        val artistName: String,
        val albumName: String?,
        val durationSecs: Int?,
        val plainLyrics: String?,
        val syncedLyrics: String?,
    )

    fun search(trackName: String, artistName: String): List<Track>? {
        return try {
            val url = "$BASE/search?track_name=${enc(trackName)}&artist_name=${enc(artistName)}"
            val body = fetch(url) ?: return null
            // response is a JSON array
            val arr = JSONArray(body)
            List(arr.length()) { i -> parseTrack(arr.getJSONObject(i)) }
        } catch (e: Exception) {
            Logger.warn("LrcLibService", "search failed", e)
            null
        }
    }

    fun getById(id: Long): Track? {
        return try {
            val url = "$BASE/get/$id"
            val body = fetch(url) ?: return null
            parseTrack(JSONObject(body))
        } catch (e: Exception) {
            Logger.warn("LrcLibService", "getById failed for $id", e)
            null
        }
    }

    private fun parseTrack(obj: JSONObject) = Track(
        id = obj.getLong("id"),
        trackName = obj.getString("trackName"),
        artistName = obj.optString("artistName", ""),
        albumName = obj.optString("albumName").ifEmpty { null },
        durationSecs = if (obj.has("duration") && !obj.isNull("duration")) obj.getInt("duration") else null,
        plainLyrics = obj.optString("plainLyrics").ifEmpty { null },
        syncedLyrics = obj.optString("syncedLyrics").ifEmpty { null },
    )

    private fun fetch(url: String): String? {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()
        return HttpClient.newCall(req).execute().body?.string()
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}
