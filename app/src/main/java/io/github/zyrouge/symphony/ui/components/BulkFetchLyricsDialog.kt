package io.github.zyrouge.symphony.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.zyrouge.symphony.R
import io.github.zyrouge.symphony.services.groove.Song
import io.github.zyrouge.symphony.services.groove.repositories.SongRepository
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import me.zyrouge.symphony.metaphony.AudioMetadataParser
import io.github.zyrouge.symphony.utils.LrcLibService
import io.github.zyrouge.symphony.utils.LyricsFileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.abs

private sealed class FetchStatus {
    object Pending : FetchStatus()
    object Searching : FetchStatus()
    object FoundSynced : FetchStatus()
    object FoundPlain : FetchStatus()
    object NotFound : FetchStatus()
    object Error : FetchStatus()
}

private fun pickBestTrack(
    results: List<LrcLibService.Track>,
    song: Song,
): LrcLibService.Track? {
    val durationSecs = (song.duration / 1000).toInt()
    val candidates = results.filter { track ->
        track.durationSecs == null || abs(track.durationSecs - durationSecs) <= 2
    }
    if (candidates.isEmpty()) return null
    return candidates.maxByOrNull { track ->
        var score = 0
        if (track.albumName != null && song.album != null &&
            track.albumName.equals(song.album, ignoreCase = true)
        ) score += 2
        if (track.syncedLyrics != null) score += 1
        score
    }
}

private suspend fun saveLyrics(context: ViewContext, song: Song, content: String): Boolean {
    val lyricsKey = song.path.substringBeforeLast('.', song.path)
    when (val source = context.symphony.groove.song.getLyricsSource(song)) {
        is SongRepository.LyricsSource.Embedded -> {
            val fd = context.activity.contentResolver
                .openFileDescriptor(song.uri, "rw")?.detachFd()
            if (fd != null) {
                AudioMetadataParser.write(song.filename, fd, mapOf("LYRICS" to content))
                return true
            }
        }
        is SongRepository.LyricsSource.Sidecar -> {
            val bakPath = song.path.substringBeforeLast('.') + ".lrc.bak"
            val existingBakUri = context.symphony.groove.exposer.uris[bakPath]
            if (source.ext == "lrc") {
                val createdBakUri = LyricsFileManager.writeSidecar(
                    context.activity,
                    source.uri,
                    content,
                    existingBakUri = existingBakUri,
                    backupFirst = true,
                )
                if (createdBakUri != null) {
                    context.symphony.groove.exposer.uris[bakPath] = createdBakUri
                }
            } else {
                val audioUri = context.symphony.groove.exposer.uris[song.path] ?: song.uri
                val basename = song.path.substringAfterLast('/').substringBeforeLast('.')
                val newUri = LyricsFileManager.createLrcSidecar(
                    context.activity, audioUri, basename, content
                )
                if (newUri != null) {
                    context.symphony.groove.exposer.uris[song.path.substringBeforeLast('.') + ".lrc"] = newUri
                }
            }
            context.symphony.database.lyricsCache.put(lyricsKey, content)
        }
        is SongRepository.LyricsSource.None -> {
            val audioUri = context.symphony.groove.exposer.uris[song.path] ?: song.uri
            val basename = song.path.substringAfterLast('/').substringBeforeLast('.')
            val newUri = LyricsFileManager.createLrcSidecar(
                context.activity, audioUri, basename, content
            )
            if (newUri != null) {
                context.symphony.groove.exposer.uris[song.path.substringBeforeLast('.') + ".lrc"] = newUri
            }
            context.symphony.database.lyricsCache.put(lyricsKey, content)
        }
    }
    return false
}

@Composable
fun BulkFetchLyricsDialog(
    context: ViewContext,
    songIds: List<String>,
    onDismissRequest: () -> Unit,
) {
    val songs = remember(songIds) {
        songIds.mapNotNull { context.symphony.groove.song.get(it) }
    }
    val statuses = remember(songs) {
        mutableStateMapOf<String, FetchStatus>().also { map ->
            songs.forEach { map[it.id] = FetchStatus.Pending }
        }
    }
    var isFetching by remember { mutableStateOf(false) }
    var isDone by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        isFetching = true
        coroutineScope.launch(Dispatchers.IO) {
            val pathsToRescan = mutableListOf<String>()
            songs.forEach { song ->
                statuses[song.id] = FetchStatus.Searching
                try {
                    val results = LrcLibService.search(song.title, song.artists.firstOrNull() ?: "")
                    if (results == null) {
                        statuses[song.id] = FetchStatus.Error
                        return@forEach
                    }
                    val best = pickBestTrack(results, song)
                    val content = best?.syncedLyrics ?: best?.plainLyrics
                    if (content == null) {
                        statuses[song.id] = FetchStatus.NotFound
                        return@forEach
                    }
                    if (saveLyrics(context, song, content)) pathsToRescan.add(song.path)
                    statuses[song.id] = if (best!!.syncedLyrics != null)
                        FetchStatus.FoundSynced else FetchStatus.FoundPlain
                } catch (e: Exception) {
                    statuses[song.id] = FetchStatus.Error
                }
            }
            if (pathsToRescan.isNotEmpty()) {
                context.symphony.groove.fetchPaths(pathsToRescan)
            }
            isFetching = false
            isDone = true
        }
    }

    val doneCount by remember {
        derivedStateOf { statuses.values.count { it !is FetchStatus.Pending && it !is FetchStatus.Searching } }
    }
    val syncedCount by remember { derivedStateOf { statuses.values.count { it is FetchStatus.FoundSynced } } }
    val plainCount by remember { derivedStateOf { statuses.values.count { it is FetchStatus.FoundPlain } } }
    val notFoundCount by remember { derivedStateOf { statuses.values.count { it is FetchStatus.NotFound } } }
    val errorCount by remember { derivedStateOf { statuses.values.count { it is FetchStatus.Error } } }

    ScaffoldDialog(
        onDismissRequest = { if (!isFetching) onDismissRequest() },
        title = { Text(stringResource(R.string.BulkFetchLyrics)) },
        topBar = if (isFetching) {
            {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    LinearProgressIndicator(
                        progress = { doneCount.toFloat() / songs.size.coerceAtLeast(1) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "$doneCount / ${songs.size}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        } else null,
        content = {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(songs, key = { it.id }) { song ->
                    SongFetchStatusRow(
                        song = song,
                        status = statuses[song.id] ?: FetchStatus.Pending,
                    )
                }
                if (isDone) {
                    item {
                        Text(
                            stringResource(
                                R.string.LyricsFetchSummary,
                                syncedCount.toString(),
                                plainCount.toString(),
                                notFoundCount.toString(),
                                errorCount.toString(),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }
            }
        },
        actions = {
            TextButton(
                enabled = isDone,
                onClick = onDismissRequest,
            ) {
                Text(stringResource(R.string.Close))
            }
        },
        contentHeight = ScaffoldDialogDefaults.PreferredMaxHeight,
    )
}

@Composable
private fun SongFetchStatusRow(song: Song, status: FetchStatus) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            when (status) {
                FetchStatus.Pending -> Icon(
                    Icons.Filled.HourglassEmpty,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                )
                FetchStatus.Searching -> CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
                FetchStatus.FoundSynced -> Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                FetchStatus.FoundPlain -> Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.secondary,
                )
                FetchStatus.NotFound -> Icon(
                    Icons.Filled.MusicOff,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
                FetchStatus.Error -> Icon(
                    Icons.Filled.Error,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                song.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val artistLabel = song.artists.joinToString(", ").ifBlank { null }
            if (artistLabel != null) {
                Text(
                    artistLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            when (status) {
                FetchStatus.Pending -> stringResource(R.string.LyricsFetchPending)
                FetchStatus.Searching -> stringResource(R.string.LyricsFetchSearching)
                FetchStatus.FoundSynced -> stringResource(R.string.LyricsFetchFoundSynced)
                FetchStatus.FoundPlain -> stringResource(R.string.LyricsFetchFoundPlain)
                FetchStatus.NotFound -> stringResource(R.string.LyricsFetchNotFound)
                FetchStatus.Error -> stringResource(R.string.LyricsFetchError)
            },
            style = MaterialTheme.typography.labelSmall,
            color = when (status) {
                FetchStatus.FoundSynced -> MaterialTheme.colorScheme.primary
                FetchStatus.FoundPlain -> MaterialTheme.colorScheme.secondary
                FetchStatus.Error -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            },
        )
    }
}
