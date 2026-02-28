package io.github.zyrouge.symphony.ui.view

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.zyrouge.symphony.ui.components.AnimatedNowPlayingBottomBar
import io.github.zyrouge.symphony.ui.components.IconTextBody
import io.github.zyrouge.symphony.ui.components.PlaylistDropdownMenu
import io.github.zyrouge.symphony.ui.components.SongCard
import io.github.zyrouge.symphony.ui.components.SongList
import io.github.zyrouge.symphony.ui.components.SongListType
import io.github.zyrouge.symphony.ui.components.TopAppBarMinimalTitle
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import io.github.zyrouge.symphony.ui.view.home.SongsPageState
import io.github.zyrouge.symphony.ui.theme.ThemeColors
import io.github.zyrouge.symphony.utils.mutate
import kotlinx.serialization.Serializable
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.ui.res.stringResource
import io.github.zyrouge.symphony.R

@Serializable
data class PlaylistViewRoute(val playlistId: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistView(context: ViewContext, route: PlaylistViewRoute) {
    val allPlaylistIds by context.symphony.groove.playlist.all.collectAsState()
    val updateId by context.symphony.groove.playlist.updateId.collectAsState()
    var updateCounter by remember { mutableIntStateOf(0) }
    val playlist by remember(route.playlistId, updateId) {
        derivedStateOf { context.symphony.groove.playlist.get(route.playlistId) }
    }
    val songIds by remember(playlist) {
        derivedStateOf { playlist?.getSongIds(context.symphony) ?: emptyList() }
    }
    val isViable by remember(allPlaylistIds, route.playlistId) {
        derivedStateOf { allPlaylistIds.contains(route.playlistId) }
    }
    var showOptionsMenu by remember { mutableStateOf(false) }
    val isFavoritesPlaylist by remember(playlist) {
        derivedStateOf {
            playlist?.let { context.symphony.groove.playlist.isFavoritesPlaylist(it) } == true
        }
    }
    var editMode by remember { mutableStateOf(false) }

    data class EditEntry(val key: Int, val songId: String)
    val localSongs = remember { mutableStateListOf<EditEntry>() }

    LaunchedEffect(editMode) {
        if (editMode) {
            localSongs.clear()
            songIds.forEachIndexed { i, id -> localSongs.add(EditEntry(i, id)) }
        }
    }

    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        localSongs.apply { add(to.index, removeAt(from.index)) }
    }

    val incrementUpdateCounter = {
        updateCounter = if (updateCounter > 25) 0 else updateCounter + 1
    }
    val songPageState = remember { SongsPageState() }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = { context.navController.popBackStack() }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                title = {
                    TopAppBarMinimalTitle {
                        Text(
                            stringResource(R.string.Playlist)
                                    + (playlist?.let { " - ${it.title}" } ?: "")
                        )
                    }
                },
                actions = {
                    if (isViable) {
                        if (editMode) {
                            IconButton(onClick = {
                                playlist?.let {
                                    context.symphony.groove.playlist.update(
                                        it.id,
                                        localSongs.map { e -> e.songId },
                                    )
                                }
                                editMode = false
                            }) {
                                Icon(Icons.Filled.Check, null)
                            }
                        } else {
                            if (!isFavoritesPlaylist) {
                                IconButton(onClick = { editMode = true }) {
                                    Icon(Icons.Filled.Edit, null)
                                }
                            }
                            IconButton(onClick = { showOptionsMenu = true }) {
                                Icon(Icons.Filled.MoreVert, null)
                                PlaylistDropdownMenu(
                                    context,
                                    playlist!!,
                                    expanded = showOptionsMenu,
                                    onSongsChanged = { incrementUpdateCounter() },
                                    onRename = { incrementUpdateCounter() },
                                    onDelete = { context.navController.popBackStack() },
                                    onDismissRequest = { showOptionsMenu = false }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                ),
            )
        },
        content = { contentPadding ->
            Box(
                modifier = Modifier
                    .padding(contentPadding)
                    .fillMaxSize()
            ) {
                when {
                    !isViable -> UnknownPlaylist(context, route.playlistId)
                    editMode -> LazyColumn(state = listState) {
                        itemsIndexed(
                            localSongs,
                            key = { _, entry -> entry.key },
                        ) { _, entry ->
                            ReorderableItem(reorderState, key = entry.key) {
                                context.symphony.groove.song.get(entry.songId)?.let { song ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        IconButton(onClick = { localSongs.remove(entry) }) {
                                            Icon(
                                                Icons.Filled.DeleteForever,
                                                null,
                                                tint = ThemeColors.Red,
                                            )
                                        }
                                        Box(modifier = Modifier.weight(1f)) {
                                            SongCard(
                                                context,
                                                song,
                                                disableOptions = true,
                                                onClick = {},
                                            )
                                        }
                                        Icon(
                                            Icons.Filled.DragHandle,
                                            null,
                                            modifier = Modifier
                                                .draggableHandle()
                                                .padding(horizontal = 16.dp)
                                                .size(24.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    else -> SongList(
                        context,
                        songIds = songIds,
                        type = SongListType.Playlist,
                        disableHeartIcon = isFavoritesPlaylist,
                        pageState = songPageState,
                        trailingOptionsContent = { _, song, onDismissRequest ->
                            playlist?.takeIf {
                                !context.symphony.groove.playlist.isBuiltInPlaylist(it)
                            }?.let {
                                DropdownMenuItem(
                                    leadingIcon = {
                                        Icon(
                                            Icons.Filled.DeleteForever,
                                            null,
                                            tint = ThemeColors.Red,
                                        )
                                    },
                                    text = { Text(stringResource(R.string.RemoveFromPlaylist)) },
                                    onClick = {
                                        onDismissRequest()
                                        context.symphony.groove.playlist.update(
                                            it.id,
                                            songIds.mutate { remove(song.id) },
                                        )
                                    }
                                )
                            }
                        },
                    )
                }
            }
        },
        bottomBar = {
            AnimatedNowPlayingBottomBar(context)
        }
    )
}

@Composable
private fun UnknownPlaylist(context: ViewContext, playlistId: String) {
    IconTextBody(
        icon = { modifier ->
            Icon(
                Icons.AutoMirrored.Filled.QueueMusic,
                null,
                modifier = modifier
            )
        },
        content = {
            Text(stringResource(R.string.UnknownPlaylistX, playlistId))
        }
    )
}
