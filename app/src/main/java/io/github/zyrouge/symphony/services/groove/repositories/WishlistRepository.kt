package io.github.zyrouge.symphony.services.groove.repositories

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import coil.request.ImageRequest
import io.github.zyrouge.symphony.Symphony
import io.github.zyrouge.symphony.WishlistSortBy
import io.github.zyrouge.symphony.services.groove.WishlistAlbum
import io.github.zyrouge.symphony.services.groove.WishlistListing
import io.github.zyrouge.symphony.ui.helpers.Assets
import io.github.zyrouge.symphony.utils.withCase
import io.github.zyrouge.symphony.utils.HttpClient
import io.github.zyrouge.symphony.utils.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class WishlistRepository(private val symphony: Symphony) {
    private val cache = ConcurrentHashMap<String, WishlistAlbum>()

    private val _isUpdating = MutableStateFlow(false)
    val isUpdating = _isUpdating.asStateFlow()
    private val _all = MutableStateFlow<List<String>>(emptyList())
    val all = _all.asStateFlow()
    private val _updateId = MutableStateFlow(0L)
    val updateId = _updateId.asStateFlow()

    private fun emitUpdateId() = _updateId.update { System.currentTimeMillis() }

    suspend fun fetch() {
        val dirString = symphony.settingsState.value.wishlistDir
        if (dirString.isBlank()) {
            cache.clear()
            _all.update { emptyList() }
            return
        }
        _isUpdating.update { true }
        try {
            val rootTreeUri = Uri.parse(dirString)
            val rootDocId = DocumentsContract.getTreeDocumentId(rootTreeUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(rootTreeUri, rootDocId)
            val cr = symphony.applicationContext.contentResolver
            val albums = mutableListOf<WishlistAlbum>()

            cr.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                ),
                null, null, null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val docId = cursor.getString(0)
                    val mimeType = cursor.getString(1)
                    if (mimeType != DocumentsContract.Document.MIME_TYPE_DIR) continue

                    val subChildrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(rootTreeUri, docId)
                    var jsonDocId: String? = null
                    var coverDocId: String? = null

                    cr.query(
                        subChildrenUri,
                        arrayOf(
                            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        ),
                        null, null, null,
                    )?.use { sub ->
                        while (sub.moveToNext()) {
                            val childId = sub.getString(0)
                            val childName = sub.getString(1)
                            when (childName) {
                                "album.json" -> jsonDocId = childId
                                "cover.jpg" -> coverDocId = childId
                            }
                        }
                    }

                    jsonDocId?.let { jsonId ->
                        try {
                            val jsonUri = DocumentsContract.buildDocumentUriUsingTree(rootTreeUri, jsonId)
                            val json = cr.openInputStream(jsonUri)?.use { it.bufferedReader().readText() }
                                ?: return@let
                            val obj = JSONObject(json)
                            val id = obj.getString("id")
                            val artist = obj.getString("artist")
                            val albumName = obj.getString("name")
                            val year = if (obj.has("year")) obj.getInt("year") else null
                            val priority = obj.optInt("priority", 0)
                            val listingsArray = obj.optJSONArray("listings")
                            val listings = mutableListOf<WishlistListing>()
                            if (listingsArray != null) {
                                for (i in 0 until listingsArray.length()) {
                                    val l = listingsArray.getJSONObject(i)
                                    listings.add(WishlistListing(l.getString("url"), l.getDouble("price")))
                                }
                            }
                            val artworkUri = coverDocId?.let {
                                DocumentsContract.buildDocumentUriUsingTree(rootTreeUri, it)
                            }
                            albums.add(
                                WishlistAlbum(
                                    id = id,
                                    artist = artist,
                                    name = albumName,
                                    year = year,
                                    priority = priority,
                                    dirDocId = docId,
                                    artworkUri = artworkUri,
                                    listings = listings,
                                )
                            )
                        } catch (e: Exception) {
                            Logger.error("WishlistRepository", "failed to parse album.json in $docId", e)
                        }
                    }
                }
            }

            cache.clear()
            albums.forEach { cache[it.id] = it }
            _all.update { albums.map { it.id } }
        } catch (e: Exception) {
            Logger.error("WishlistRepository", "fetch failed", e)
        } finally {
            _isUpdating.update { false }
        }
    }

    fun reset() {
        cache.clear()
        _all.update { emptyList() }
    }

    suspend fun add(artist: String, name: String, year: Int?, priority: Int, artUrl: String?) {
        val dirString = symphony.settingsState.value.wishlistDir
        if (dirString.isBlank()) return
        val cr = symphony.applicationContext.contentResolver
        val rootTreeUri = Uri.parse(dirString)
        val rootDocId = DocumentsContract.getTreeDocumentId(rootTreeUri)
        val rootDocUri = DocumentsContract.buildDocumentUriUsingTree(rootTreeUri, rootDocId)

        val dirName = "$artist - $name"
        val newDirUri = DocumentsContract.createDocument(
            cr, rootDocUri, DocumentsContract.Document.MIME_TYPE_DIR, dirName,
        ) ?: error("Failed to create directory")
        val newDirDocId = DocumentsContract.getDocumentId(newDirUri)

        val id = UUID.randomUUID().toString()
        writeAlbumJson(cr, newDirUri, id, artist, name, year, priority)

        var artworkUri: Uri? = null
        if (!artUrl.isNullOrBlank()) {
            try {
                artworkUri = downloadArt(cr, rootTreeUri, newDirDocId, newDirUri, artUrl)
            } catch (e: Exception) {
                Logger.error("WishlistRepository", "failed to download art for new album", e)
            }
        }

        val album = WishlistAlbum(
            id = id,
            artist = artist,
            name = name,
            year = year,
            priority = priority,
            dirDocId = newDirDocId,
            artworkUri = artworkUri,
        )
        cache[id] = album
        _all.update { it + id }
    }

    suspend fun update(id: String, artist: String, name: String, year: Int?, priority: Int, artUrl: String?) {
        val existing = cache[id] ?: return
        val dirString = symphony.settingsState.value.wishlistDir
        if (dirString.isBlank()) return
        val cr = symphony.applicationContext.contentResolver
        val rootTreeUri = Uri.parse(dirString)

        val dirDocUri = DocumentsContract.buildDocumentUriUsingTree(rootTreeUri, existing.dirDocId)
        val subChildrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(rootTreeUri, existing.dirDocId)

        var jsonDocUri: Uri? = null
        cr.query(
            subChildrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            ),
            null, null, null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val childId = cursor.getString(0)
                val childName = cursor.getString(1)
                if (childName == "album.json") {
                    jsonDocUri = DocumentsContract.buildDocumentUriUsingTree(rootTreeUri, childId)
                    break
                }
            }
        }

        if (jsonDocUri == null) {
            jsonDocUri = DocumentsContract.createDocument(cr, dirDocUri, "application/json", "album.json")
        }

        jsonDocUri?.let { uri ->
            val json = buildAlbumJson(id, artist, name, year, priority, existing.listings)
            cr.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray()) }
        }

        var artworkUri = existing.artworkUri
        if (!artUrl.isNullOrBlank()) {
            try {
                artworkUri = downloadArt(cr, rootTreeUri, existing.dirDocId, dirDocUri, artUrl)
            } catch (e: Exception) {
                Logger.error("WishlistRepository", "failed to download art for update", e)
            }
        }

        val updated = existing.copy(
            artist = artist,
            name = name,
            year = year,
            priority = priority,
            artworkUri = artworkUri,
            listings = existing.listings,
        )
        cache[id] = updated
        emitUpdateId()
    }

    suspend fun delete(id: String) {
        val album = cache[id] ?: return
        val dirString = symphony.settingsState.value.wishlistDir
        if (dirString.isBlank()) return
        val cr = symphony.applicationContext.contentResolver
        val rootTreeUri = Uri.parse(dirString)
        val dirDocUri = DocumentsContract.buildDocumentUriUsingTree(rootTreeUri, album.dirDocId)
        try {
            DocumentsContract.deleteDocument(cr, dirDocUri)
        } catch (e: Exception) {
            Logger.error("WishlistRepository", "failed to delete directory for $id", e)
        }
        cache.remove(id)
        _all.update { it - id }
    }

    fun get(id: String) = cache[id]

    suspend fun updateListings(id: String, listings: List<WishlistListing>) {
        val existing = cache[id] ?: return
        val dirString = symphony.settingsState.value.wishlistDir
        if (dirString.isBlank()) return
        val cr = symphony.applicationContext.contentResolver
        val rootTreeUri = Uri.parse(dirString)
        val dirDocUri = DocumentsContract.buildDocumentUriUsingTree(rootTreeUri, existing.dirDocId)
        val subChildrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(rootTreeUri, existing.dirDocId)

        var jsonDocUri: Uri? = null
        cr.query(
            subChildrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            ),
            null, null, null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == "album.json") {
                    jsonDocUri = DocumentsContract.buildDocumentUriUsingTree(rootTreeUri, cursor.getString(0))
                    break
                }
            }
        }
        if (jsonDocUri == null) {
            jsonDocUri = DocumentsContract.createDocument(cr, dirDocUri, "application/json", "album.json")
        }
        jsonDocUri?.let { uri ->
            val json = buildAlbumJson(existing.id, existing.artist, existing.name, existing.year, existing.priority, listings)
            cr.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray()) }
        }
        cache[id] = existing.copy(listings = listings)
        emitUpdateId()
    }

    fun sort(albumIds: List<String>, by: WishlistSortBy, reverse: Boolean): List<String> {
        val sensitive = symphony.settingsState.value.caseSensitiveSorting
        val sorted = when (by) {
            WishlistSortBy.WISHLIST_SORT_ARTIST -> albumIds.sortedBy { cache[it]?.artist?.withCase(sensitive) }
            WishlistSortBy.WISHLIST_SORT_NAME -> albumIds.sortedBy { cache[it]?.name?.withCase(sensitive) }
            WishlistSortBy.WISHLIST_SORT_YEAR -> albumIds.sortedBy { cache[it]?.year }
            WishlistSortBy.WISHLIST_SORT_PRIORITY -> albumIds.sortedBy { val p = cache[it]?.priority ?: -1; if (p < 0) Int.MAX_VALUE else p }
            else -> albumIds
        }
        return if (reverse) sorted.reversed() else sorted
    }

    fun createArtworkImageRequest(id: String): ImageRequest {
        val album = cache[id]
        return if (album?.artworkUri != null) {
            ImageRequest.Builder(symphony.applicationContext)
                .data(album.artworkUri)
                .build()
        } else {
            Assets.createPlaceholderImageRequest(symphony).build()
        }
    }

    private fun buildAlbumJson(
        id: String,
        artist: String,
        name: String,
        year: Int?,
        priority: Int,
        listings: List<WishlistListing> = emptyList(),
    ): String {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("artist", artist)
        obj.put("name", name)
        year?.let { obj.put("year", it) }
        obj.put("priority", priority)
        if (listings.isNotEmpty()) {
            val arr = JSONArray()
            listings.forEach { listing ->
                val l = JSONObject()
                l.put("url", listing.url)
                l.put("price", listing.price)
                arr.put(l)
            }
            obj.put("listings", arr)
        }
        return obj.toString()
    }

    private fun writeAlbumJson(
        cr: android.content.ContentResolver,
        dirUri: Uri,
        id: String,
        artist: String,
        name: String,
        year: Int?,
        priority: Int,
    ) {
        val jsonUri = DocumentsContract.createDocument(cr, dirUri, "application/json", "album.json")
            ?: error("Failed to create album.json")
        val json = buildAlbumJson(id, artist, name, year, priority)
        cr.openOutputStream(jsonUri, "wt")?.use { it.write(json.toByteArray()) }
            ?: error("Failed to write album.json")
    }

    private fun downloadArt(
        cr: android.content.ContentResolver,
        rootTreeUri: Uri,
        dirDocId: String,
        dirDocUri: Uri,
        url: String,
    ): Uri {
        val req = Request.Builder().url(url).build()
        val bytes = HttpClient.newCall(req).execute().use { resp ->
            resp.body?.bytes() ?: error("Empty response body")
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: error("Could not decode image")

        val maxBytes = 1_572_864
        val bos = ByteArrayOutputStream()
        var quality = 100
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, bos)
        while (bos.size() > maxBytes && quality > 10) {
            bos.reset()
            quality -= 5
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, bos)
        }

        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(rootTreeUri, dirDocId)
        var existingCoverUri: Uri? = null
        cr.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            ),
            null, null, null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == "cover.jpg") {
                    existingCoverUri = DocumentsContract.buildDocumentUriUsingTree(
                        rootTreeUri, cursor.getString(0)
                    )
                    break
                }
            }
        }

        val coverUri = existingCoverUri ?: DocumentsContract.createDocument(
            cr, dirDocUri, "image/jpeg", "cover.jpg",
        ) ?: error("Failed to create cover.jpg")

        cr.openOutputStream(coverUri, "wt")?.use { it.write(bos.toByteArray()) }
            ?: error("Failed to open output stream for cover.jpg")

        return coverUri
    }
}
