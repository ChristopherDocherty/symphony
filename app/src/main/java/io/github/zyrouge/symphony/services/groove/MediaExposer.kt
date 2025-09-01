package io.github.zyrouge.symphony.services.groove

import android.net.Uri
import io.github.zyrouge.symphony.Symphony
import io.github.zyrouge.symphony.utils.ActivityUtils
import io.github.zyrouge.symphony.utils.ConcurrentSet
import io.github.zyrouge.symphony.utils.DocumentFileX
import io.github.zyrouge.symphony.utils.Logger
import io.github.zyrouge.symphony.utils.SimpleFileSystem
import io.github.zyrouge.symphony.utils.SimplePath
import io.github.zyrouge.symphony.utils.concurrentSetOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger // Added import
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class MediaExposer(private val symphony: Symphony) {
    internal val uris = ConcurrentHashMap<String, Uri>()
    var explorer = SimpleFileSystem.Folder()
    private val _isUpdating = MutableStateFlow<Float?>(null) // Changed type and initial value
    val isUpdating = _isUpdating.asStateFlow()

    private fun emitUpdate(value: Float?) = _isUpdating.update { // Changed parameter type
        value
    }

    private data class ScanCycle(
        val songCache: ConcurrentHashMap<String, Song>,
        val songCacheUnused: ConcurrentSet<String>,
        val artworkCacheUnused: ConcurrentSet<String>,
        val lyricsCacheUnused: ConcurrentSet<String>,
        val directoryArtworkCacheUnused: ConcurrentSet<String>,
        val filter: MediaFilter,
        val songParseOptions: Song.ParseOptions,
    ) {
        companion object {
            suspend fun create(symphony: Symphony): ScanCycle {
                val songCache = ConcurrentHashMap(symphony.database.songCache.entriesPathMapped())
                val songCacheUnused = concurrentSetOf(songCache.map { it.value.id })
                val artworkCacheUnused = concurrentSetOf(symphony.database.artworkCache.all())
                val lyricsCacheUnused = concurrentSetOf(symphony.database.lyricsCache.keys())
                val directoryArtworkCacheUnused = concurrentSetOf(symphony.database.directoryArtworkCache.keys())
                val filter = MediaFilter(
                    symphony.settings.songsFilterPattern.value,
                    symphony.settings.blacklistFolders.value.toSortedSet(),
                    symphony.settings.whitelistFolders.value.toSortedSet()
                )
                return ScanCycle(
                    songCache = songCache,
                    songCacheUnused = songCacheUnused,
                    artworkCacheUnused = artworkCacheUnused,
                    lyricsCacheUnused = lyricsCacheUnused,
                    directoryArtworkCacheUnused = directoryArtworkCacheUnused,
                    filter = filter,
                    songParseOptions = Song.ParseOptions.create(symphony),
                )
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun fetch() {
        emitUpdate(0.0f) // Emit start
        val allCollectedSongs = mutableListOf<Song>()
        var finished = false
        try {
            val context = symphony.applicationContext
            val folderUris = symphony.settings.mediaFolders.value
            val cycle = ScanCycle.create(symphony)

            val totalFolders = folderUris.size
            val processedRootFoldersCount = AtomicInteger(0) // For multi-folder progress

            if (totalFolders == 0) {
                 emitUpdate(0.9f) // Progress if no folders, then 1.0f later
            }

            coroutineScope {
                val deferredSongLists = folderUris.mapIndexedNotNull { _, uri -> // index not used directly here
                    ActivityUtils.makePersistableReadableUri(context, uri)
                    DocumentFileX.fromTreeUri(context, uri)?.let { docFile ->
                        val path = SimplePath(DocumentFileX.getParentPathOfTreeUri(uri) ?: docFile.name)
                        async(Dispatchers.IO) {
                            progressFractionCpunter
                            val songs = if (totalFolders == 1) {
                                scanMediaTree(cycle, path, docFile, progressFractionConsumer = { fraction ->
                                    emitUpdate(fraction * 0.9f)
                                })
                            } else {
                                scanMediaTree(cycle, path, docFile, null)
                            }

                            if (totalFolders > 1) {
                                val currentProcessed = processedRootFoldersCount.incrementAndGet()
                                emitUpdate(currentProcessed.toFloat() / totalFolders.toFloat() * 0.9f)
                            }
                            songs
                        }
                    }
                }
                deferredSongLists.awaitAll().forEach { songList ->
                    allCollectedSongs.addAll(songList)
                }
            }

            emitSongs(allCollectedSongs)
            trimCache(cycle)
            emitUpdate(1.0f) // Emit work complete (covers the final 10% for song emission/cache trim)
            finished = true
        } catch (err: Exception) {
            Logger.error("MediaExposer", "fetch failed", err)
            emitSongs(emptyList())
            emitUpdate(1.0f) // Emit work complete even on error, before idling
            finished = true
        } finally {
            if (!finished) { // Ensure 1.0f is emitted if an exception occurred before it
                emitUpdate(1.0f)
            }
            emitUpdate(null) // Emit idle
            emitFinish() // Call finish after idling
        }
    }

    private suspend fun scanMediaTree(
        cycle: ScanCycle,
        path: SimplePath,
        file: DocumentFileX,
        progressFractionConsumer: ((Float) -> Unit)? = null // New parameter
    ): List<Song> {
        try {
            if (!cycle.filter.isWhitelisted(path.pathString)) {
                progressFractionConsumer?.invoke(1.0f) // Consider filtered path as "processed" for this level's progress
                return emptyList()
            }
            val songsFound = mutableListOf<Song>()
            val children = file.list()
            val totalChildren = children.size
            val processedChildrenCount = AtomicInteger(0)

            if (totalChildren == 0) {
                progressFractionConsumer?.invoke(1.0f)
                return emptyList()
            }

            coroutineScope {
                val deferredSongLists = children.map { childFile ->
                    async(Dispatchers.IO) {
                        val childPath = path.join(childFile.name)
                        val songsFromChild = when {
                            childFile.isDirectory -> scanMediaTree(cycle, childPath, childFile, null) // Recursive calls don't pass consumer
                            else -> scanMediaFile(cycle, childPath, childFile)
                        }
                        // Report progress after this child is done
                        val currentProcessed = processedChildrenCount.incrementAndGet()
                        progressFractionConsumer?.invoke(currentProcessed.toFloat() / totalChildren.toFloat())
                        songsFromChild
                    }
                }
                deferredSongLists.awaitAll().forEach { songList ->
                    songsFound.addAll(songList)
                }
            }
            // Ensure 1.0f is reported if all children processed (might be slightly off due to float division)
            // or if loop finishes. The last increment should ideally make it 1.0f.
            if (processedChildrenCount.get() == totalChildren && totalChildren > 0) {
                 progressFractionConsumer?.invoke(1.0f)
            }
            return songsFound
        } catch (err: Exception) {
            Logger.error("MediaExposer", "scan media tree failed for ${path.pathString}", err)
            progressFractionConsumer?.invoke(1.0f) // Ensure progress indicates completion on error
            return emptyList()
        }
    }

    suspend fun loadFromCache() {
        emitUpdate(0.0f) // Emit start
        var finished = false
        try {
            explorer = SimpleFileSystem.Folder()
            val cachedSongs = symphony.database.songCache.entriesPathMapped().values.toList()

            if (cachedSongs.isEmpty()) {
                Logger.warn("MediaExposer", "No songs found in cache to load.")
                emitSongs(emptyList())
            } else {
                emitSongs(cachedSongs)
            }
            emitUpdate(1.0f) // Emit work complete
            finished = true
        } catch (err: Exception) {
            Logger.error("MediaExposer", "loadFromCache failed", err)
            emitSongs(emptyList())
            emitUpdate(1.0f) // Emit work complete even on error
            finished = true
        } finally {
             if (!finished) {
                emitUpdate(1.0f)
            }
            emitUpdate(null) // Emit idle
            emitFinish() // Call finish after idling
        }
    }

    private suspend fun scanMediaFile(cycle: ScanCycle, path: SimplePath, file: DocumentFileX): List<Song> {
        try {
            when {
                path.extension == "lrc" -> {
                    scanLrcFile(cycle, path, file)
                    return emptyList()
                }
                file.mimeType == MIMETYPE_M3U -> {
                    scanM3UFile(cycle, path, file)
                    return emptyList()
                }
                file.mimeType.startsWith("audio/") -> {
                    val song = scanAudioFile(cycle, path, file)
                    return song?.let { listOf(it) } ?: emptyList()
                }
                file.mimeType.startsWith("image/") -> {
                    scanImageFile(cycle, path, file)
                    return emptyList()
                }
                else -> return emptyList()
            }
        } catch (err: Exception) {
            val pathString = path.pathString
            Logger.error("MediaExposer", "scan media file failed for $pathString", err)
            return emptyList()
        }
    }

    private suspend fun scanAudioFile(cycle: ScanCycle, path: SimplePath, file: DocumentFileX): Song? {
        val pathString = path.pathString
        uris[pathString] = file.uri
        val lastModified = file.lastModified
        val cached = cycle.songCache[pathString]
        val cacheHit = cached != null &&
                cached.dateModified == lastModified &&
                (cached.coverFile?.let { cycle.artworkCacheUnused.contains(it) } != false)

        val song = when {
            cacheHit -> cached!!
            else -> Song.parse(path, file, cycle.songParseOptions)
        }

        if (song.duration.milliseconds < symphony.settings.minSongDuration.value.seconds) {
            return null
        }

        if (!cacheHit) {
            symphony.database.songCache.insert(song)
            cached?.coverFile?.let {
                if (symphony.database.artworkCache.get(it).delete()) {
                    cycle.artworkCacheUnused.remove(it)
                }
            }
        }
        cycle.songCacheUnused.remove(song.id)
        song.coverFile?.let {
            cycle.artworkCacheUnused.remove(it)
        }
        val lyricsKey = song.path.substringBeforeLast('.', song.path)
        cycle.lyricsCacheUnused.remove(lyricsKey)
        explorer.addChildFile(path)
        return song
    }

    private suspend fun scanLrcFile(
        cycle: ScanCycle,
        path: SimplePath,
        file: DocumentFileX,
    ) {
        uris[path.pathString] = file.uri
        explorer.addChildFile(path)
        try {
            val lyricsContent = symphony.applicationContext.contentResolver.openInputStream(file.uri)?.bufferedReader()?.use { it.readText() }
            if (lyricsContent != null) {
                val key = path.pathString.substringBeforeLast(".lrc", path.pathString)
                symphony.database.lyricsCache.put(key, lyricsContent)
                cycle.lyricsCacheUnused.remove(key)
            }
        } catch (e: Exception) {
            Logger.error("MediaExposer", "Failed to read or cache LRC file: ${path.pathString}", e)
        }
    }

    private fun scanM3UFile(
        @Suppress("Unused") cycle: ScanCycle,
        path: SimplePath,
        file: DocumentFileX,
    ) {
        uris[path.pathString] = file.uri
        explorer.addChildFile(path)
    }

    private fun scanImageFile(
        cycle: ScanCycle,
        path: SimplePath,
        file: DocumentFileX,
    ) {
        val pathString = path.pathString
        uris[pathString] = file.uri
        explorer.addChildFile(path)

        val parentPath = path.parent?.pathString ?: return
        if (symphony.database.directoryArtworkCache.get(parentPath) == null) {
            symphony.database.directoryArtworkCache.insert(parentPath, file.uri)
        }
        cycle.directoryArtworkCacheUnused.remove(parentPath)
    }

    private suspend fun trimCache(cycle: ScanCycle) {
        try {
            symphony.database.songCache.delete(cycle.songCacheUnused)
        } catch (err: Exception) {
            Logger.warn("MediaExposer", "trim song cache failed", err)
        }
        for (x in cycle.artworkCacheUnused) {
            try {
                symphony.database.artworkCache.get(x).delete()
            } catch (err: Exception) {
                Logger.warn("MediaExposer", "delete artwork cache file failed", err)
            }
        }
        try {
            symphony.database.lyricsCache.delete(cycle.lyricsCacheUnused)
        } catch (err: Exception) {
            Logger.warn("MediaExposer", "trim lyrics cache failed", err)
        }
        try {
            symphony.database.directoryArtworkCache.delete(cycle.directoryArtworkCacheUnused)
        } catch (err: Exception) {
            Logger.warn("MediaExposer", "trim directory artwork cache failed", err)
        }
    }

    suspend fun reset() {
        emitUpdate(0.0f) // Emit start
        var finished = false
        try {
            uris.clear()
            explorer = SimpleFileSystem.Folder()
            symphony.database.songCache.clear()
            symphony.database.artworkCache.clear()
            symphony.database.lyricsCache.clear()
            symphony.database.directoryArtworkCache.clear()
            emitSongs(emptyList())
            emitUpdate(1.0f) // Emit work complete
            finished = true
        } catch (e: Exception) { // Added general exception catch for safety, though original didn't have it explicitly here.
            Logger.error("MediaExposer", "reset failed", e)
            emitUpdate(1.0f) // Emit work complete even on error
            finished = true
        }
        finally {
             if (!finished) {
                emitUpdate(1.0f)
            }
            emitUpdate(null) // Emit idle
            emitFinish() // Call finish after idling
        }
    }

    private fun emitSongs(songs: List<Song>) {
        symphony.groove.album.rebuildFromSongs(songs)
        symphony.groove.artist.rebuildFromSongs(songs)
        symphony.groove.song.setSongs(songs)
        symphony.groove.genre.rebuildFromSongs(songs)
    }

    private fun emitFinish() {
        symphony.groove.playlist.onScanFinish()
    }

    private class MediaFilter(
        pattern: String?,
        private val blacklisted: Set<String>,
        private val whitelisted: Set<String>,
    ) {
        private val regex = pattern?.let { Regex(it, RegexOption.IGNORE_CASE) }

        fun isWhitelisted(path: String): Boolean {
            regex?.let {
                if (!it.containsMatchIn(path)) {
                    return false
                }
            }
            val bFilter = blacklisted.findLast {
                path.startsWith(it)
            }
            if (bFilter == null) {
                return true
            }
            val wFilter = whitelisted.findLast {
                it.startsWith(bFilter) && path.startsWith(it)
            }
            return wFilter != null
        }
    }

    companion object {
        const val MIMETYPE_M3U = "audio/x-mpegurl"
    }
}
