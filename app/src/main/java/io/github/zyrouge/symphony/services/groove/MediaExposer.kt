package io.github.zyrouge.symphony.services.groove

import android.net.Uri
import io.github.zyrouge.symphony.Symphony
import io.github.zyrouge.symphony.utils.ActivityUtils
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
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class MediaExposer(private val symphony: Symphony) {
    internal val uris = ConcurrentHashMap<String, Uri>()
    var explorer = SimpleFileSystem.Folder()
    private val _isUpdating = MutableStateFlow(false)
    val isUpdating = _isUpdating.asStateFlow()

    data class ScanProgress(val completed: Int, val total: Int)
    private val _scanProgress = MutableStateFlow<ScanProgress?>(null)
    val scanProgress = _scanProgress.asStateFlow()

    private val scanCompletedFiles = AtomicInteger(0)
    private val scanTotalFiles = AtomicInteger(0)

    private fun emitScanProgress() {
        val completed = scanCompletedFiles.get()
        val total = scanTotalFiles.get()
        _scanProgress.update { ScanProgress(completed, total) }
    }

    private fun emitUpdate(value: Boolean) = _isUpdating.update {
        value
    }

    private data class ScanConfig(
        val filter: MediaFilter,
        val songParseOptions: Song.ParseOptions,
    ) {
        companion object {
            suspend fun create(symphony: Symphony): ScanConfig {
                val s = symphony.settingsState.value
                val filter = MediaFilter(
                    s.songsFilterPattern.takeIf { it.isNotEmpty() },
                    s.blacklistFoldersList.toSortedSet(),
                    s.whitelistFoldersList.toSortedSet()
                )
                return ScanConfig(
                    filter = filter,
                    songParseOptions = Song.ParseOptions.create(symphony),
                )
            }
        }
    }

    private class CachePruner private constructor(
        songCacheData: Map<String, Song>,
        artworkKeys: Collection<String>,
        lyricsKeys: Collection<String>,
        val existingDirArtwork: Map<String, Uri>,
    ) {
        val songCache: ConcurrentHashMap<String, Song> = ConcurrentHashMap(songCacheData)
        private val unusedSongIds = concurrentSetOf(songCacheData.values.map { it.id })
        private val unusedArtwork = concurrentSetOf(artworkKeys)
        private val unusedLyrics = concurrentSetOf(lyricsKeys)
        private val unusedDirArt = concurrentSetOf(existingDirArtwork.keys)

        fun markSongSeen(song: Song) {
            unusedSongIds.remove(song.id)
            song.coverFile?.let { unusedArtwork.remove(it) }
        }

        fun removeArtwork(file: String) = unusedArtwork.remove(file)
        fun markLyricsSeen(key: String) = unusedLyrics.remove(key)
        fun markDirArtworkSeen(parentPath: String) = unusedDirArt.remove(parentPath)

        suspend fun prune(symphony: Symphony) {
            try {
                symphony.database.songCache.delete(unusedSongIds)
            } catch (err: Exception) {
                Logger.warn("MediaExposer", "trim song cache failed", err)
            }
            for (x in unusedArtwork) {
                try {
                    symphony.database.artworkCache.get(x).delete()
                } catch (err: Exception) {
                    Logger.warn("MediaExposer", "delete artwork cache file failed", err)
                }
            }
            try {
                symphony.database.lyricsCache.delete(unusedLyrics)
            } catch (err: Exception) {
                Logger.warn("MediaExposer", "trim lyrics cache failed", err)
            }
            try {
                symphony.database.directoryArtworkCache.delete(unusedDirArt)
            } catch (err: Exception) {
                Logger.warn("MediaExposer", "trim directory artwork cache failed", err)
            }
        }

        companion object {
            suspend fun create(symphony: Symphony): CachePruner {
                val songCache = symphony.database.songCache.entriesPathMapped()
                return CachePruner(
                    songCacheData = songCache,
                    artworkKeys = symphony.database.artworkCache.all(),
                    lyricsKeys = symphony.database.lyricsCache.keys(),
                    existingDirArtwork = symphony.database.directoryArtworkCache.entries(),
                )
            }

            // Must delete stale rows BEFORE snapshotting the cache so scanAudioFile
            // sees them as absent and re-parses instead of returning stale metadata.
            suspend fun createForPaths(symphony: Symphony, forceRescanIds: Set<String>): CachePruner {
                if (forceRescanIds.isNotEmpty()) {
                    symphony.database.songCache.delete(forceRescanIds)
                }
                return create(symphony)
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun fetch() {
        emitUpdate(true)
        val allCollectedSongs = mutableListOf<Song>()
        try {
            val context = symphony.applicationContext
            val folderUris = symphony.settingsState.value.mediaFoldersList.map { android.net.Uri.parse(it) }.toSet()
            val config = ScanConfig.create(symphony)
            val pruner = CachePruner.create(symphony)

            scanCompletedFiles.set(0)
            scanTotalFiles.set(0)
            emitScanProgress()

            val allFiles = ConcurrentLinkedQueue<Pair<SimplePath, DocumentFileX>>()
            for (uri in folderUris) {
                ActivityUtils.makePersistableReadWriteUri(context, uri)
                val docFile = DocumentFileX.fromTreeUri(context, uri) ?: continue
                val path = SimplePath(DocumentFileX.getParentPathOfTreeUri(uri) ?: docFile.name)
                collectFiles(config.filter, path, docFile, allFiles)
            }
            val (imageFiles, scanFiles) = allFiles.partition { (_, file) -> file.mimeType.startsWith("image/") }
            for ((path, file) in imageFiles) {
                uris[path.pathString] = file.uri
                explorer.addChildFile(path)
            }
            scanTotalFiles.set(scanFiles.size)
            emitScanProgress()

            val pendingInserts = ConcurrentLinkedQueue<Song>()
            coroutineScope {
                scanFiles.map { (path, file) ->
                    async(Dispatchers.IO) { scanMediaFile(config, pruner, pendingInserts, path, file) }
                }.awaitAll().filterNotNull().forEach { allCollectedSongs.add(it) }
            }
            if (pendingInserts.isNotEmpty()) {
                symphony.database.songCache.insert(*pendingInserts.toTypedArray())
            }

            emitSongs(allCollectedSongs)

            val imagesByDir = imageFiles.groupBy { (path, _) -> path.parent?.pathString }
            for ((parentPath, dirFiles) in imagesByDir) {
                if (parentPath == null) continue
                val winner = dirFiles.find { (path, _) -> path.name.equals("cover.jpg", ignoreCase = true) }
                    ?: dirFiles.first()
                pruner.markDirArtworkSeen(parentPath)
                if (pruner.existingDirArtwork[parentPath] != winner.second.uri) {
                    symphony.database.directoryArtworkCache.insert(parentPath, winner.second.uri)
                }
            }

            pruner.prune(symphony)

        } catch (err: Exception) {
            Logger.error("MediaExposer", "fetch failed", err)
            emitSongs(emptyList())
        }
        _scanProgress.update { null }
        emitUpdate(false)
        emitFinish()
    }

    private suspend fun collectFiles(
        filter: MediaFilter,
        path: SimplePath,
        dir: DocumentFileX,
        into: ConcurrentLinkedQueue<Pair<SimplePath, DocumentFileX>>,
    ) {
        if (!filter.isWhitelisted(path.pathString)) return
        try {
            coroutineScope {
                dir.list().map { child ->
                    val childPath = path.join(child.name)
                    if (child.isDirectory) {
                        async(Dispatchers.IO) { collectFiles(filter, childPath, child, into) }
                    } else {
                        into.add(childPath to child)
                        null
                    }
                }.filterNotNull().awaitAll()
            }
        } catch (err: Exception) {
            Logger.error("MediaExposer", "collectFiles failed for ${path.pathString}", err)
        }
    }

    suspend fun fetchPaths(paths: List<String>) {
        emitUpdate(true)
        scanCompletedFiles.set(0)
        scanTotalFiles.set(paths.size)
        emitScanProgress()
        try {
            val context = symphony.applicationContext
            val existingSongs = symphony.groove.song.values()
            val existingSongsByPath = existingSongs.associateBy { it.path }

            val idsToInvalidate = paths.mapNotNull { existingSongsByPath[it]?.id }.toSet()
            val config = ScanConfig.create(symphony)
            val pruner = CachePruner.createForPaths(symphony, idsToInvalidate)

            val pendingInserts = ConcurrentLinkedQueue<Song>()
            val updatedSongs = coroutineScope {
                paths.mapNotNull { path ->
                    // uris is only populated during a full scan; fall back to the URI stored in
                    // the song (persisted in Room) so fetchPaths works after loading from cache.
                    val uri = uris[path] ?: existingSongsByPath[path]?.uri ?: return@mapNotNull null
                    val docFile = DocumentFileX.fromSingleUri(context, uri) ?: return@mapNotNull null
                    async(Dispatchers.IO) {
                        scanMediaFile(config, pruner, pendingInserts, SimplePath(path), docFile)
                    }
                }.awaitAll().filterNotNull()
            }
            if (pendingInserts.isNotEmpty()) {
                symphony.database.songCache.insert(*pendingInserts.toTypedArray())
            }

            val updatedByPath = updatedSongs.associateBy { it.path }
            val mergedSongs = existingSongs.filter { it.path !in updatedByPath } + updatedSongs

            symphony.groove.album.reset()
            symphony.groove.albumArtist.reset()
            symphony.groove.artist.reset()
            symphony.groove.genre.reset()
            symphony.groove.song.reset()

            emitSongs(mergedSongs)
        } catch (err: Exception) {
            Logger.error("MediaExposer", "fetchPaths failed", err)
        }
        _scanProgress.update { null }
        emitUpdate(false)
        emitFinish()
    }

    suspend fun loadFromCache() {
        emitUpdate(true)
        try {
            // uris map will not be populated when loading from cache, lyrics are fetched from lyricsCache
            explorer = SimpleFileSystem.Folder()

            val cachedSongs = symphony.database.songCache.entriesPathMapped().values.toList()

            if (cachedSongs.isEmpty()) {
                Logger.warn("MediaExposer", "No songs found in cache to load.")
                emitSongs(emptyList())
            } else {
                emitSongs(cachedSongs)
            }
        } catch (err: Exception) {
            Logger.error("MediaExposer", "loadFromCache failed", err)
            emitSongs(emptyList())
        }
        emitUpdate(false)
        emitFinish()
    }

    private suspend fun scanMediaFile(config: ScanConfig, pruner: CachePruner, pendingInserts: ConcurrentLinkedQueue<Song>, path: SimplePath, file: DocumentFileX): Song? {
        val completed = scanCompletedFiles.incrementAndGet()
        if (completed % 25 == 0 || completed == scanTotalFiles.get()) {
            emitScanProgress()
        }
        try {
            when {
                path.extension == "lrc" || path.extension == "txt" -> {
                    scanLrcFile(pruner, path, file)
                    return null
                }
                file.mimeType == MIMETYPE_M3U -> {
                    scanM3UFile(path, file)
                    return null
                }
                file.mimeType.startsWith("audio/") -> return scanAudioFile(config, pruner, pendingInserts, path, file)
                else -> return null
            }
        } catch (err: Exception) {
            val pathString = path.pathString
            Logger.error("MediaExposer", "scan media file failed for $pathString", err)
            return null
        }
    }

    private suspend fun scanAudioFile(config: ScanConfig, pruner: CachePruner, pendingInserts: ConcurrentLinkedQueue<Song>, path: SimplePath, file: DocumentFileX): Song? {
        val pathString = path.pathString
        uris[pathString] = file.uri
        val lastModified = file.lastModified
        val cached = pruner.songCache[pathString]
        val cacheHit = cached != null &&
                cached.dateModified == lastModified &&
                (cached.coverFile?.let { symphony.database.artworkCache.get(it).exists() } != false)

        val song = when {
            cacheHit -> cached!!
            else -> Song.parse(path, file, config.songParseOptions)
        }

        if (song.duration.milliseconds < symphony.settingsState.value.minSongDuration.seconds) {
            return null
        }

        if (!cacheHit) {
            pendingInserts.add(song)
            cached?.coverFile?.let { oldCoverFile ->
                if (oldCoverFile != song.coverFile) {
                    if (symphony.database.artworkCache.get(oldCoverFile).delete()) {
                        pruner.removeArtwork(oldCoverFile)
                    }
                }
            }
        }
        pruner.markSongSeen(song)
        val lyricsKey = song.path.substringBeforeLast('.', song.path)
        pruner.markLyricsSeen(lyricsKey)
        explorer.addChildFile(path)
        return song
    }

    private suspend fun scanLrcFile(
        pruner: CachePruner,
        path: SimplePath,
        file: DocumentFileX,
    ) {
        uris[path.pathString] = file.uri
        explorer.addChildFile(path)
        try {
            val lyricsContent = symphony.applicationContext.contentResolver.openInputStream(file.uri)?.bufferedReader()?.use { it.readText() }
            if (lyricsContent != null) {
                val key = path.pathString.substringBeforeLast('.', path.pathString)
                symphony.database.lyricsCache.put(key, lyricsContent)
                pruner.markLyricsSeen(key)
            }
        } catch (e: Exception) {
            Logger.error("MediaExposer", "Failed to read or cache LRC file: ${path.pathString}", e)
        }
    }

    private fun scanM3UFile(
        path: SimplePath,
        file: DocumentFileX,
    ) {
        uris[path.pathString] = file.uri
        explorer.addChildFile(path)

        val playlist = Playlist.parse(symphony, null, file.uri)
        symphony.groove.playlist.add(playlist)
    }

    suspend fun reset() {
        emitUpdate(true)
        uris.clear()
        explorer = SimpleFileSystem.Folder()
        symphony.database.songCache.clear()
        symphony.database.artworkCache.clear()
        symphony.database.lyricsCache.clear()
        symphony.database.directoryArtworkCache.clear()
        emitSongs(emptyList())
        emitUpdate(false)
        emitFinish()
    }

    private fun emitSongs(songs: List<Song>) {
        symphony.groove.song.setSongs(songs)
        symphony.groove.album.rebuildFromSongs(songs)
        symphony.groove.albumArtist.rebuildFromSongs(songs)
        symphony.groove.artist.rebuildFromSongs(songs)
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

    fun getSidecarUri(audioPath: String): Pair<Uri, String>? {
        val base = audioPath.substringBeforeLast('.')
        uris["$base.lrc"]?.let { return it to "lrc" }
        uris["$base.txt"]?.let { return it to "txt" }
        return null
    }

    companion object {
        const val MIMETYPE_M3U = "audio/x-mpegurl"
    }
}
