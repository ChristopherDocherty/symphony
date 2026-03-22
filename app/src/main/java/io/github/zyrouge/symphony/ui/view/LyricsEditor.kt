package io.github.zyrouge.symphony.ui.view

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.zyrouge.symphony.R
import io.github.zyrouge.symphony.services.groove.repositories.SongRepository
import io.github.zyrouge.symphony.ui.components.IconButtonPlaceholder
import io.github.zyrouge.symphony.ui.components.TopAppBarMinimalTitle
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import io.github.zyrouge.symphony.ui.view.nowPlaying.NowPlayingSeekBar
import io.github.zyrouge.symphony.ui.view.nowPlaying.defaultHorizontalPadding
import io.github.zyrouge.symphony.utils.LrcLibService
import io.github.zyrouge.symphony.utils.LrcSerializer
import io.github.zyrouge.symphony.utils.LyricsFileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import me.zyrouge.symphony.metaphony.AudioMetadataParser

@Serializable
data class LyricsEditorViewRoute(val songId: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsEditorView(context: ViewContext, songId: String) {
    val song = context.symphony.groove.song.get(songId) ?: return
    val coroutineScope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) }
    var rawText by remember { mutableStateOf("") }
    var timingLines by remember { mutableStateOf<List<String>>(emptyList()) }
    val timingTimestamps = remember { mutableStateOf<SnapshotStateList<Long?>>(mutableListOf<Long?>().toMutableStateList()) }
    var timingCurrentIndex by remember { mutableIntStateOf(0) }
    var isSaving by remember { mutableStateOf(false) }
    var showLrcLibDialog by remember { mutableStateOf(false) }

    LaunchedEffect(songId) {
        val lyrics = withContext(Dispatchers.IO) {
            context.symphony.groove.song.getLyrics(song)
        } ?: ""
        rawText = lyrics
        val lines = LrcSerializer.toLines(lyrics)
        timingLines = lines
        val ts: SnapshotStateList<Long?> = lines.map { null }.toMutableStateList()
        timingTimestamps.value = ts
    }

    fun save() {
        isSaving = true
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val content = when (selectedTab) {
                    0 -> rawText
                    else -> LrcSerializer.serialize(timingLines, timingTimestamps.value)
                }

                val lyricsKey = song.path.substringBeforeLast('.', song.path)
                when (val source = context.symphony.groove.song.getLyricsSource(song)) {
                    is SongRepository.LyricsSource.Embedded -> {
                        val fd = context.activity.contentResolver
                            .openFileDescriptor(song.uri, "rw")
                            ?.detachFd()
                        if (fd != null) {
                            AudioMetadataParser.write(song.filename, fd, mapOf("LYRICS" to content))
                            context.symphony.groove.fetchPaths(listOf(song.path))
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
                            // Track backup in uris so second save skips backup creation
                            if (createdBakUri != null) {
                                context.symphony.groove.exposer.uris[bakPath] = createdBakUri
                            }
                        } else {
                            val audioUri = context.symphony.groove.exposer.uris[song.path] ?: song.uri
                            val basename = song.path.substringAfterLast('/').substringBeforeLast('.')
                            val newUri = LyricsFileManager.createLrcSidecar(
                                context.activity,
                                audioUri,
                                basename,
                                content,
                            )
                            // Track new .lrc in uris so subsequent saves find it
                            if (newUri != null) {
                                val lrcPath = song.path.substringBeforeLast('.') + ".lrc"
                                context.symphony.groove.exposer.uris[lrcPath] = newUri
                            }
                        }
                        context.symphony.database.lyricsCache.put(lyricsKey, content)
                    }
                    is SongRepository.LyricsSource.None -> {
                        val audioUri = context.symphony.groove.exposer.uris[song.path] ?: song.uri
                        val basename = song.path.substringAfterLast('/').substringBeforeLast('.')
                        val newUri = LyricsFileManager.createLrcSidecar(
                            context.activity,
                            audioUri,
                            basename,
                            content,
                        )
                        // Track new .lrc in uris so subsequent saves find it
                        if (newUri != null) {
                            val lrcPath = song.path.substringBeforeLast('.') + ".lrc"
                            context.symphony.groove.exposer.uris[lrcPath] = newUri
                        }
                        context.symphony.database.lyricsCache.put(lyricsKey, content)
                    }
                }
                withContext(Dispatchers.Main) {
                    isSaving = false
                    Toast.makeText(
                        context.activity,
                        context.activity.getString(R.string.LyricsSaved),
                        Toast.LENGTH_SHORT,
                    ).show()
                    context.navController.popBackStack()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isSaving = false
                    Toast.makeText(
                        context.activity,
                        "Save failed: ${e.localizedMessage}",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = { context.navController.popBackStack() }) {
                        Icon(Icons.Filled.ExpandMore, null, modifier = Modifier.size(32.dp))
                    }
                },
                title = {
                    TopAppBarMinimalTitle {
                        Text(stringResource(R.string.LyricsEditor))
                    }
                },
                actions = {
                    IconButton(onClick = { showLrcLibDialog = true }) {
                        Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.SearchLrcLib))
                    }
                    if (isSaving) {
                        IconButtonPlaceholder()
                    } else {
                        IconButton(onClick = { save() }) {
                            Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.LyricsSave))
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
    ) { contentPadding ->
        Column(modifier = Modifier.padding(contentPadding).fillMaxSize()) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.PlainText)) },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = {
                        val lines = LrcSerializer.toLines(rawText)
                        if (lines != timingLines) {
                            timingLines = lines
                            timingTimestamps.value = lines.map { null }.toMutableStateList()
                            timingCurrentIndex = 0
                        }
                        selectedTab = 1
                    },
                    text = { Text(stringResource(R.string.TimingMode)) },
                )
            }
            when (selectedTab) {
                0 -> Box(modifier = Modifier.weight(1f)) {
                    PlainTextTab(
                        rawText = rawText,
                        onTextChange = { rawText = it },
                    )
                }
                else -> TimingTab(
                    context = context,
                    lines = timingLines,
                    timestamps = timingTimestamps.value,
                    currentIndex = timingCurrentIndex,
                    onCurrentIndexChange = { timingCurrentIndex = it },
                    onReset = {
                        val fresh: SnapshotStateList<Long?> =
                            timingLines.map { null }.toMutableStateList()
                        timingTimestamps.value = fresh
                        timingCurrentIndex = 0
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    if (showLrcLibDialog) {
        LrcLibSearchDialog(
            context = context,
            initialTrackName = song.title,
            initialArtistName = song.artists.firstOrNull() ?: "",
            songDurationMs = song.duration,
            onDismiss = { showLrcLibDialog = false },
            onLyricsSelected = { lyrics ->
                rawText = lyrics
                selectedTab = 0
                showLrcLibDialog = false
            },
        )
    }
}

@Composable
private fun PlainTextTab(rawText: String, onTextChange: (String) -> Unit) {
    val scrollState = rememberScrollState()
    val contentColor = LocalContentColor.current
    BasicTextField(
        value = rawText,
        onValueChange = onTextChange,
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(horizontal = defaultHorizontalPadding, vertical = 12.dp),
        textStyle = TextStyle(color = contentColor),
        cursorBrush = SolidColor(contentColor),
    )
}

@Composable
private fun TimingTab(
    context: ViewContext,
    lines: List<String>,
    timestamps: SnapshotStateList<Long?>,
    currentIndex: Int,
    onCurrentIndexChange: (Int) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isPlaying by context.symphony.radio.observatory.isPlaying.collectAsState()
    val listState = rememberLazyListState()
    val allMarked = currentIndex >= lines.size

    LaunchedEffect(currentIndex) {
        if (currentIndex < lines.size) {
            listState.animateScrollToItem(currentIndex)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                itemsIndexed(lines) { i, line ->
                    val isActive = i == currentIndex
                    val hasTimestamp = timestamps.getOrNull(i) != null
                    Text(
                        text = line.ifBlank { "·" },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = defaultHorizontalPadding, vertical = 6.dp),
                        style = when {
                            isActive -> MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                            hasTimestamp -> MaterialTheme.typography.bodyMedium.copy(
                                color = LocalContentColor.current,
                            )
                            else -> MaterialTheme.typography.bodyMedium.copy(
                                color = LocalContentColor.current.copy(alpha = 0.5f),
                            )
                        },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        NowPlayingSeekBar(context)
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = defaultHorizontalPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = {
                if (isPlaying) context.symphony.radio.pause()
                else context.symphony.radio.resume()
            }) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                )
            }

            OutlinedButton(onClick = onReset) {
                Text(stringResource(R.string.ResetTiming))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (!allMarked) {
            Button(
                onClick = {
                    val pos = context.symphony.radio.currentPlaybackPosition?.played ?: 0L
                    if (currentIndex < timestamps.size) {
                        timestamps[currentIndex] = pos
                    }
                    onCurrentIndexChange(currentIndex + 1)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = defaultHorizontalPadding),
            ) {
                Text(stringResource(R.string.MarkNextLine))
            }
        } else {
            Text(
                stringResource(R.string.AllLinesMarked),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = defaultHorizontalPadding, vertical = 12.dp),
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.primary,
                ),
            )
        }

        Spacer(modifier = Modifier.height(defaultHorizontalPadding))
    }
}

private sealed class LrcLibDialogState {
    object Idle : LrcLibDialogState()
    object Searching : LrcLibDialogState()
    data class Results(val tracks: List<LrcLibService.Track>) : LrcLibDialogState()
    data class Fetching(val tracks: List<LrcLibService.Track>, val selected: LrcLibService.Track) : LrcLibDialogState()
    data class Preview(val tracks: List<LrcLibService.Track>, val track: LrcLibService.Track) : LrcLibDialogState()
    data class Error(val message: String) : LrcLibDialogState()
}

@Composable
private fun LrcLibSearchDialog(
    context: ViewContext,
    initialTrackName: String,
    initialArtistName: String,
    songDurationMs: Long,
    onDismiss: () -> Unit,
    onLyricsSelected: (String) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    var dialogState by remember { mutableStateOf<LrcLibDialogState>(LrcLibDialogState.Idle) }
    var trackName by remember { mutableStateOf(initialTrackName) }
    var artistName by remember { mutableStateOf(initialArtistName) }

    fun search() {
        dialogState = LrcLibDialogState.Searching
        coroutineScope.launch(Dispatchers.IO) {
            val results = LrcLibService.search(trackName, artistName)
            withContext(Dispatchers.Main) {
                dialogState = when {
                    results == null -> LrcLibDialogState.Error(context.activity.getString(R.string.LrcLibSearchFailed))
                    results.isEmpty() -> LrcLibDialogState.Error(context.activity.getString(R.string.NoLrcLibResults))
                    else -> LrcLibDialogState.Results(results)
                }
            }
        }
    }

    fun selectTrack(tracks: List<LrcLibService.Track>, track: LrcLibService.Track) {
        dialogState = LrcLibDialogState.Fetching(tracks, track)
        coroutineScope.launch(Dispatchers.IO) {
            val full = LrcLibService.getById(track.id)
            withContext(Dispatchers.Main) {
                dialogState = if (full != null) {
                    LrcLibDialogState.Preview(tracks, full)
                } else {
                    LrcLibDialogState.Error(context.activity.getString(R.string.LrcLibSearchFailed))
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.LrcLibSearch)) },
        text = {
            when (val state = dialogState) {
                is LrcLibDialogState.Idle -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val songSecs = (songDurationMs / 1000).toInt()
                        Text(
                            "%d:%02d".format(songSecs / 60, songSecs % 60),
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = LocalContentColor.current.copy(alpha = 0.7f),
                            ),
                        )
                        OutlinedTextField(
                            value = trackName,
                            onValueChange = { trackName = it },
                            label = { Text(stringResource(R.string.TrackName)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = artistName,
                            onValueChange = { artistName = it },
                            label = { Text(stringResource(R.string.Artist)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                is LrcLibDialogState.Searching, is LrcLibDialogState.Fetching -> {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is LrcLibDialogState.Results -> {
                    LazyColumn {
                        itemsIndexed(state.tracks) { i, track ->
                            if (i > 0) HorizontalDivider()
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectTrack(state.tracks, track) }
                                    .padding(vertical = 10.dp),
                            ) {
                                Text(
                                    track.trackName,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                )
                                Text(
                                    buildString {
                                        append(track.artistName)
                                        track.albumName?.let { append(" · $it") }
                                        track.durationSecs?.let { append(" · ${it / 60}:${"%02d".format(it % 60)}") }
                                    },
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = LocalContentColor.current.copy(alpha = 0.7f),
                                    ),
                                )
                            }
                        }
                    }
                }
                is LrcLibDialogState.Preview -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            state.track.trackName,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        )
                        Text(
                            state.track.artistName,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = LocalContentColor.current.copy(alpha = 0.7f),
                            ),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Button(
                            onClick = { onLyricsSelected(state.track.syncedLyrics!!) },
                            enabled = state.track.syncedLyrics != null,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.UseSyncedLyrics))
                        }
                        OutlinedButton(
                            onClick = { onLyricsSelected(state.track.plainLyrics!!) },
                            enabled = state.track.plainLyrics != null,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.UsePlainLyrics))
                        }
                    }
                }
                is LrcLibDialogState.Error -> {
                    Text(state.message)
                }
            }
        },
        confirmButton = {
            when (val state = dialogState) {
                is LrcLibDialogState.Idle -> {
                    Button(onClick = { search() }, enabled = trackName.isNotBlank()) {
                        Text(stringResource(R.string.Search))
                    }
                }
                is LrcLibDialogState.Preview -> {
                    TextButton(onClick = { dialogState = LrcLibDialogState.Results(state.tracks) }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = null)
                    }
                }
                is LrcLibDialogState.Error -> {
                    TextButton(onClick = { dialogState = LrcLibDialogState.Idle }) {
                        Text(stringResource(R.string.Back))
                    }
                }
                else -> {}
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.Cancel))
            }
        },
    )
}
