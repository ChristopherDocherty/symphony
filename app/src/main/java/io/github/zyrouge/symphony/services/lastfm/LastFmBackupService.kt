package io.github.zyrouge.symphony.services.lastfm

import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.viewModelScope
import io.github.zyrouge.symphony.Symphony
import io.github.zyrouge.symphony.copy
import io.github.zyrouge.symphony.services.database.store.LastFmPlayCountEntry
import io.github.zyrouge.symphony.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class LastFmBackupService(private val symphony: Symphony) : Symphony.Hooks {

    private val playCountsCache = ConcurrentHashMap<String, Long>()

    private val _isInitialPullInProgress = MutableStateFlow(false)
    val isInitialPullInProgress = _isInitialPullInProgress.asStateFlow()

    private val _initialPullProgress = MutableStateFlow(0L to 0L)
    val initialPullProgress = _initialPullProgress.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()

    fun getSongScrobbleCount(artist: String, track: String): Long {
        val key = "${artist.lowercase()}|${track.lowercase()}"
        return playCountsCache[key] ?: 0L
    }

    override fun onSymphonyReady() {
        symphony.viewModelScope.launch(Dispatchers.IO) {
            loadPlayCountsFromDatabase()
        }
        val settings = symphony.settingsState.value
        if (!settings.lastFmBackupEnabled) return
        if (settings.lastFmSessionKey.isBlank() || settings.lastFmApiKey.isBlank() || settings.lastFmUsername.isBlank()) return
        if (settings.lastFmBackupDir.isBlank()) return
        symphony.viewModelScope.launch(Dispatchers.IO) {
            if (!settings.lastFmBackupInitialComplete) {
                runInitialPull()
            } else {
                val nowSeconds = System.currentTimeMillis() / 1000
                if (nowSeconds - settings.lastFmBackupLastSync > 24 * 60 * 60) {
                    runDailySync()
                }
            }
        }
    }

    private suspend fun loadPlayCountsFromDatabase() {
        try {
            val entries = symphony.database.lastFmPlayCounts.all()
            for (entry in entries) {
                playCountsCache[entry.key] = entry.count
            }
        } catch (err: Exception) {
            Logger.error("LastFmBackupService", "loadPlayCountsFromDatabase failed", err)
        }
    }

    fun startInitialPull() {
        if (_isInitialPullInProgress.value) return
        symphony.viewModelScope.launch(Dispatchers.IO) {
            runInitialPull()
        }
    }

    fun syncNow() {
        if (_isSyncing.value) return
        symphony.viewModelScope.launch(Dispatchers.IO) {
            runDailySync()
        }
    }

    fun rebuildPlayCounts() {
        symphony.viewModelScope.launch(Dispatchers.IO) {
            rebuildPlayCountsFromAllFiles()
        }
    }

    fun resetBackupState() {
        symphony.viewModelScope.launch(Dispatchers.IO) {
            symphony.settings.updateData { it.copy {
                lastFmBackupInitialComplete = false
                lastFmBackupTotal = 0
                lastFmBackupFetchedCount = 0
                lastFmBackupOldestFetched = 0
                lastFmBackupNewestSeen = 0
                lastFmBackupLastSync = 0
            }}
        }
    }

    private suspend fun runInitialPull() {
        if (_isInitialPullInProgress.value) return
        _isInitialPullInProgress.value = true
        try {
            val settings = symphony.settingsState.value
            val apiKey = settings.lastFmApiKey
            val username = settings.lastFmUsername
            val dirString = settings.lastFmBackupDir
            if (apiKey.isBlank() || username.isBlank() || dirString.isBlank()) return

            val dirUri = Uri.parse(dirString)
            var oldestFetched = settings.lastFmBackupOldestFetched
            var fetchedCount = settings.lastFmBackupFetchedCount
            var total = settings.lastFmBackupTotal
            var newestSeen = settings.lastFmBackupNewestSeen

            _initialPullProgress.value = fetchedCount to total

            if (total == 0L) {
                val firstPage = LastFmScrobbler.getRecentTracksPage(apiKey, username, 1, 200) ?: return
                total = firstPage.total
                val validTracks = firstPage.tracks.filter { it.timestampSeconds > 0 }

                if (validTracks.isNotEmpty()) {
                    if (newestSeen == 0L) newestSeen = validTracks.first().timestampSeconds
                    if (appendToCsv(dirUri, validTracks)) {
                        fetchedCount += validTracks.size
                        oldestFetched = validTracks.last().timestampSeconds
                        archiveIfNeeded(dirUri)
                    }
                }

                symphony.settings.updateData { it.copy {
                    lastFmBackupNewestSeen = newestSeen
                    lastFmBackupTotal = total
                    lastFmBackupFetchedCount = fetchedCount
                    lastFmBackupOldestFetched = oldestFetched
                }}
                _initialPullProgress.value = fetchedCount to total
                delay(100)
            }

            var pullCompleted = false
            while (oldestFetched > 0) {
                val page = LastFmScrobbler.getRecentTracksPage(
                    apiKey, username, 1, 200, to = oldestFetched - 1
                ) ?: break
                val validTracks = page.tracks.filter { it.timestampSeconds > 0 }
                if (validTracks.isEmpty()) {
                    pullCompleted = true
                    break
                }

                if (!appendToCsv(dirUri, validTracks)) break
                fetchedCount += validTracks.size
                oldestFetched = validTracks.last().timestampSeconds
                archiveIfNeeded(dirUri)

                symphony.settings.updateData { it.copy {
                    lastFmBackupFetchedCount = fetchedCount
                    lastFmBackupOldestFetched = oldestFetched
                }}
                _initialPullProgress.value = fetchedCount to total
                delay(100)
            }

            if (pullCompleted) {
                symphony.settings.updateData { it.copy {
                    lastFmBackupInitialComplete = true
                    lastFmBackupLastSync = System.currentTimeMillis() / 1000
                }}
            }
        } catch (err: Exception) {
            Logger.error("LastFmBackupService", "runInitialPull failed", err)
        } finally {
            _isInitialPullInProgress.value = false
        }
    }

    private suspend fun runDailySync() {
        if (_isSyncing.value) return
        _isSyncing.value = true
        try {
            val settings = symphony.settingsState.value
            val apiKey = settings.lastFmApiKey
            val username = settings.lastFmUsername
            val dirString = settings.lastFmBackupDir
            val newestSeen = settings.lastFmBackupNewestSeen
            if (apiKey.isBlank() || username.isBlank() || dirString.isBlank()) return
            // Guard: initialComplete can be true with newestSeen=0 if state was corrupted.
            if (newestSeen == 0L) return

            val dirUri = Uri.parse(dirString)
            val allNewTracks = mutableListOf<LastFmRecentTrack>()
            var currentPage = 1
            val limit = 200
            var syncCompleted = false

            while (true) {
                val page = LastFmScrobbler.getRecentTracksPage(
                    apiKey, username, currentPage, limit, from = newestSeen
                ) ?: break
                val validTracks = page.tracks.filter { it.timestampSeconds > newestSeen }
                allNewTracks.addAll(validTracks)
                if (currentPage >= page.totalPages || page.tracks.size < limit) {
                    syncCompleted = true
                    break
                }
                currentPage++
                delay(100)
            }

            if (allNewTracks.isEmpty()) {
                symphony.settings.updateData { it.copy {
                    lastFmBackupLastSync = System.currentTimeMillis() / 1000
                }}
                return
            }

            // Abandon on partial fetch — don't write or advance newestSeen.
            if (!syncCompleted) {
                Logger.warn("LastFmBackupService", "daily sync incomplete (API error), will retry next cycle")
                return
            }

            appendToCsv(dirUri, allNewTracks.reversed())
            archiveIfNeeded(dirUri)

            val newCounts = mutableMapOf<String, Long>()
            for (track in allNewTracks) {
                val key = "${track.artist.lowercase()}|${track.track.lowercase()}"
                newCounts[key] = (newCounts[key] ?: 0L) + 1L
            }
            val entries = newCounts.map { (k, v) ->
                LastFmPlayCountEntry(k, (playCountsCache[k] ?: 0L) + v)
            }
            symphony.database.lastFmPlayCounts.upsert(*entries.toTypedArray())
            for ((k, v) in newCounts) {
                playCountsCache[k] = (playCountsCache[k] ?: 0L) + v
            }

            val maxTs = allNewTracks.maxOf { it.timestampSeconds }
            symphony.settings.updateData { it.copy {
                lastFmBackupNewestSeen = maxTs
                lastFmBackupLastSync = System.currentTimeMillis() / 1000
            }}
        } catch (err: Exception) {
            Logger.error("LastFmBackupService", "runDailySync failed", err)
        } finally {
            _isSyncing.value = false
        }
    }

    private suspend fun rebuildPlayCountsFromAllFiles() {
        try {
            val dirString = symphony.settingsState.value.lastFmBackupDir
            if (dirString.isBlank()) return
            val dirUri = Uri.parse(dirString)

            val cr = symphony.applicationContext.contentResolver
            val counts = mutableMapOf<String, Long>()

            val rootDocId = DocumentsContract.getTreeDocumentId(dirUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(dirUri, rootDocId)
            cr.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                ),
                null, null, null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val docId = cursor.getString(0)
                    val name = cursor.getString(1)
                    val mime = cursor.getString(2)
                    val fileUri = DocumentsContract.buildDocumentUriUsingTree(dirUri, docId)
                    when {
                        name == "scrobbles.csv" -> {
                            cr.openInputStream(fileUri)?.use { stream ->
                                parseCsvCounts(stream.readBytes(), counts)
                            }
                        }
                        name.startsWith("scrobbles") && mime == "application/zip" -> {
                            cr.openInputStream(fileUri)?.use { stream ->
                                ZipInputStream(stream).use { zis ->
                                    var entry = zis.nextEntry
                                    while (entry != null) {
                                        if (!entry.isDirectory && entry.name.endsWith(".csv")) {
                                            parseCsvCounts(zis.readBytes(), counts)
                                        }
                                        zis.closeEntry()
                                        entry = zis.nextEntry
                                    }
                                }
                            }
                        }
                    }
                }
            }

            symphony.database.lastFmPlayCounts.replace(counts.map { (k, v) -> LastFmPlayCountEntry(k, v) })
            playCountsCache.clear()
            playCountsCache.putAll(counts)
        } catch (err: Exception) {
            Logger.error("LastFmBackupService", "rebuildPlayCountsFromAllFiles failed", err)
        }
    }

    private fun appendToCsv(dirUri: Uri, tracks: List<LastFmRecentTrack>): Boolean {
        if (tracks.isEmpty()) return true
        return try {
            val cr = symphony.applicationContext.contentResolver
            var csvUri = findFileUri(dirUri, "scrobbles.csv")

            if (csvUri == null) {
                val rootDocId = DocumentsContract.getTreeDocumentId(dirUri)
                val parentUri = DocumentsContract.buildDocumentUriUsingTree(dirUri, rootDocId)
                csvUri = DocumentsContract.createDocument(cr, parentUri, "text/csv", "scrobbles.csv")
                    ?: return false
                cr.openOutputStream(csvUri, "wt")?.use { os ->
                    os.write("timestamp,artist,track,album\n".toByteArray())
                } ?: return false
            }

            cr.openOutputStream(csvUri, "wa")?.use { os ->
                for (track in tracks) {
                    val line = "${track.timestampSeconds},${escapeCsv(track.artist)},${escapeCsv(track.track)},${escapeCsv(track.album)}\n"
                    os.write(line.toByteArray())
                }
            } ?: return false

            true
        } catch (err: Exception) {
            Logger.error("LastFmBackupService", "appendToCsv failed", err)
            false
        }
    }

    /**
     * If scrobbles.csv exceeds [ARCHIVE_THRESHOLD_BYTES], reads its content, finds the
     * timestamp range, writes a zip named scrobbles_YYYYMMDD_YYYYMMDD.zip, then deletes
     * the active CSV so a fresh one starts on the next append.
     */
    private fun archiveIfNeeded(dirUri: Uri) {
        try {
            val cr = symphony.applicationContext.contentResolver
            val csvUri = findFileUri(dirUri, "scrobbles.csv") ?: return

            val size = cr.query(
                csvUri,
                arrayOf(DocumentsContract.Document.COLUMN_SIZE),
                null, null, null,
            )?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else 0L } ?: 0L

            if (size < ARCHIVE_THRESHOLD_BYTES) return

            val csvText = cr.openInputStream(csvUri)?.use { it.readBytes() }?.toString(Charsets.UTF_8) ?: return

            // Deduplicate rows (exact-string match preserving first-seen order).
            // Duplicate rows can appear when the app is killed after a SAF write but before
            // the checkpoint DataStore write completes, causing the same page to be re-fetched
            // and re-appended on next resume.
            val allLines = csvText.lines()
            val header = allLines.firstOrNull() ?: return
            val uniqueDataLines = allLines.drop(1).filter { it.isNotBlank() }.distinct()
            val dedupedBytes = (listOf(header) + uniqueDataLines).joinToString("\n").toByteArray()

            // Find timestamp range to name the archive
            var minTs = Long.MAX_VALUE
            var maxTs = Long.MIN_VALUE
            uniqueDataLines.forEach { line ->
                val ts = line.substringBefore(',').toLongOrNull() ?: return@forEach
                if (ts > 0) {
                    if (ts < minTs) minTs = ts
                    if (ts > maxTs) maxTs = ts
                }
            }
            if (minTs == Long.MAX_VALUE) return

            val fmt = SimpleDateFormat("yyyyMMdd", Locale.US)
            val oldDate = fmt.format(Date(minTs * 1000))
            val newDate = fmt.format(Date(maxTs * 1000))
            var zipName = "scrobbles_${oldDate}_${newDate}.zip"
            if (findFileUri(dirUri, zipName) != null) {
                zipName = "scrobbles_${oldDate}_${newDate}_${System.currentTimeMillis()}.zip"
            }

            val rootDocId = DocumentsContract.getTreeDocumentId(dirUri)
            val parentUri = DocumentsContract.buildDocumentUriUsingTree(dirUri, rootDocId)

            // Track the new zip URI so we can clean it up if the write fails
            val zipUri = DocumentsContract.createDocument(cr, parentUri, "application/zip", zipName) ?: return
            try {
                cr.openOutputStream(zipUri)?.use { os ->
                    ZipOutputStream(os).use { zos ->
                        zos.putNextEntry(ZipEntry("scrobbles.csv"))
                        zos.write(dedupedBytes)
                        zos.closeEntry()
                    }
                }
                DocumentsContract.deleteDocument(cr, csvUri)
            } catch (err: Exception) {
                // Delete the partially written zip so rebuild doesn't choke on it
                runCatching { DocumentsContract.deleteDocument(cr, zipUri) }
                throw err
            }
        } catch (err: Exception) {
            Logger.warn("LastFmBackupService", "archiveIfNeeded failed", err)
        }
    }

    private fun findFileUri(dirUri: Uri, name: String): Uri? {
        val cr = symphony.applicationContext.contentResolver
        val rootDocId = DocumentsContract.getTreeDocumentId(dirUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(dirUri, rootDocId)
        cr.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            ),
            null, null, null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == name) {
                    return DocumentsContract.buildDocumentUriUsingTree(dirUri, cursor.getString(0))
                }
            }
        }
        return null
    }

    private fun parseCsvCounts(bytes: ByteArray, counts: MutableMap<String, Long>) {
        bytes.toString(Charsets.UTF_8).lineSequence().drop(1).forEach { line ->
            if (line.isBlank()) return@forEach
            val parts = parseCsvLine(line)
            if (parts.size >= 3) {
                val key = "${parts[1].lowercase()}|${parts[2].lowercase()}"
                counts[key] = (counts[key] ?: 0L) + 1L
            }
        }
    }

    private fun escapeCsv(s: String): String {
        return if (s.contains(',') || s.contains('"') || s.contains('\n')) {
            '"' + s.replace("\"", "\"\"") + '"'
        } else s
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> {
                    sb.append('"')
                    i += 2
                    continue
                }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    result.add(sb.toString())
                    sb.clear()
                }
                else -> sb.append(c)
            }
            i++
        }
        result.add(sb.toString())
        return result
    }

    companion object {
        private const val ARCHIVE_THRESHOLD_BYTES = 5_000_000L // ~50k rows
    }
}
