package io.github.zyrouge.symphony.ui.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.zyrouge.symphony.services.groove.Groove
import io.github.zyrouge.symphony.ui.components.IconButtonPlaceholderSize
import io.github.zyrouge.symphony.ui.components.NewPlaylistDialog
import io.github.zyrouge.symphony.ui.components.SongCard
import io.github.zyrouge.symphony.ui.components.TopAppBarMinimalTitle
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import io.github.zyrouge.symphony.ui.view.nowPlaying.NothingPlayingBody
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Serializable
object QueueViewRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueView(context: ViewContext) {
    val coroutineScope = rememberCoroutineScope()
    val queue by context.symphony.radio.observatory.queue.collectAsState()
    val queueIndex by context.symphony.radio.observatory.queueIndex.collectAsState()
    var showSaveDialog by remember { mutableStateOf(false) }

    data class QueueEntry(val key: Int, val songId: String)

    val localQueue = remember { mutableStateListOf<QueueEntry>() }
    var dragOriginIndex by remember { mutableStateOf(-1) }
    var dragCurrentIndex by remember { mutableStateOf(-1) }

    // Sync local queue from the StateFlow when not dragging
    LaunchedEffect(queue) {
        if (dragOriginIndex == -1) {
            localQueue.clear()
            queue.forEachIndexed { i, songId ->
                localQueue.add(QueueEntry(i, songId))
            }
        }
    }

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = queueIndex,
    )
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        localQueue.apply { add(to.index, removeAt(from.index)) }
        if (dragOriginIndex == -1) dragOriginIndex = from.index
        dragCurrentIndex = to.index
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    TopAppBarMinimalTitle(
                        modifier = Modifier.padding(start = IconButtonPlaceholderSize)
                    ) {
                        Text(context.symphony.t.Queue)
                    }
                },
                colors = TopAppBarDefaults.mediumTopAppBarColors(
                    containerColor = Color.Transparent
                ),
                navigationIcon = {
                    IconButton(
                        onClick = {
                            context.navController.popBackStack()
                        }
                    ) {
                        Icon(
                            Icons.Filled.ExpandMore,
                            null,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            showSaveDialog = !showSaveDialog
                        }
                    ) {
                        Icon(Icons.Default.Save, null)
                    }
                    IconButton(
                        onClick = {
                            context.symphony.radio.stop()
                        }
                    ) {
                        Icon(Icons.Filled.ClearAll, null)
                    }
                }
            )
        },
        content = { contentPadding ->
            Box(
                modifier = Modifier
                    .padding(contentPadding)
                    .fillMaxSize()
            ) {
                if (queue.isEmpty()) {
                    NothingPlayingBody(context)
                } else {
                    LazyColumn(state = listState) {
                        itemsIndexed(
                            localQueue,
                            key = { _, entry -> entry.key },
                            contentType = { _, _ -> Groove.Kind.SONG },
                        ) { i, entry ->
                            ReorderableItem(reorderState, key = entry.key) {
                                context.symphony.groove.song.get(entry.songId)?.let { song ->
                                    Box {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Box(modifier = Modifier.weight(1f)) {
                                                SongCard(
                                                    context,
                                                    song,
                                                    autoHighlight = false,
                                                    highlighted = i == queueIndex,
                                                    disableOptions = true,
                                                    thumbnailLabel = {
                                                        Text((i + 1).toString())
                                                    },
                                                    onClick = {
                                                        context.symphony.radio.jumpTo(i)
                                                        coroutineScope.launch {
                                                            listState.animateScrollToItem(i)
                                                        }
                                                    },
                                                )
                                            }
                                            Icon(
                                                Icons.Filled.DragHandle,
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .draggableHandle(
                                                        onDragStopped = {
                                                            if (dragOriginIndex != -1 && dragOriginIndex != dragCurrentIndex) {
                                                                context.symphony.radio.queue.move(
                                                                    dragOriginIndex,
                                                                    dragCurrentIndex,
                                                                )
                                                            }
                                                            dragOriginIndex = -1
                                                            dragCurrentIndex = -1
                                                        }
                                                    )
                                                    .padding(horizontal = 16.dp)
                                                    .size(24.dp),
                                            )
                                        }
                                        if (i < queueIndex) {
                                            Box(
                                                modifier = Modifier
                                                    .matchParentSize()
                                                    .background(
                                                        MaterialTheme.colorScheme.background.copy(
                                                            alpha = 0.3f
                                                        )
                                                    )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    )

    if (showSaveDialog) {
        NewPlaylistDialog(
            context,
            initialSongIds = queue.toList(),
            onDone = { playlist ->
                showSaveDialog = false
                context.symphony.groove.playlist.add(playlist)
            },
            onDismissRequest = {
                showSaveDialog = false
            }
        )
    }
}
