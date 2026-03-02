package io.github.zyrouge.symphony.ui.view

import android.widget.Toast
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
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
    // Raw text content for plain-text tab (LRC with timestamps as-is)
    var rawText by remember { mutableStateOf("") }
    // Lines (timestamp-stripped) for timing tab
    var timingLines by remember { mutableStateOf<List<String>>(emptyList()) }
    // Timestamps per line (ms), null means no timestamp assigned yet
    val timingTimestamps = remember { mutableStateOf<SnapshotStateList<Long?>>(mutableListOf<Long?>().toMutableStateList()) }
    var timingCurrentIndex by remember { mutableIntStateOf(0) }
    var isSaving by remember { mutableStateOf(false) }

    // Load initial lyrics
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
                // Build final LRC content from the active tab
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
                            // .txt sidecar: create a new .lrc alongside it
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
                        // Sync lines from the raw text when switching to timing tab
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

    // Auto-scroll to current line
    LaunchedEffect(currentIndex) {
        if (currentIndex < lines.size) {
            listState.animateScrollToItem(currentIndex)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Lines list
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

        // Playback controls
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
            // Play/Pause
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

            // Reset button
            OutlinedButton(onClick = onReset) {
                Text(stringResource(R.string.ResetTiming))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Mark Next Line button
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
