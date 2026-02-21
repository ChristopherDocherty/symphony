package io.github.zyrouge.symphony.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import io.github.zyrouge.symphony.PlaylistSortBy
import io.github.zyrouge.symphony.copy
import io.github.zyrouge.symphony.services.groove.Groove
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistGrid(
    context: ViewContext,
    playlistIds: Set<String>,
    playlistsCount: Int? = null,
    leadingContent: @Composable () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val settings by context.symphony.settingsState.collectAsState()
    val sortBy = settings.playlistsSortBy
    val sortReverse = settings.playlistsSortReverse
    val sortedPlaylistIds by remember(playlistIds, sortBy, sortReverse) {
        derivedStateOf {
            context.symphony.groove.playlist.sort(playlistIds.toList(), sortBy, sortReverse)
        }
    }
    val horizontalGridColumns = settings.playlistsHorizontalGridColumns
    val verticalGridColumns = settings.playlistsVerticalGridColumns
    val gridColumns by remember(horizontalGridColumns, verticalGridColumns) {
        derivedStateOf {
            ResponsiveGridColumns(horizontalGridColumns, verticalGridColumns)
        }
    }
    var showModifyLayoutSheet by remember { mutableStateOf(false) }

    MediaSortBarScaffold(
        mediaSortBar = {
            Column {
                leadingContent()
                MediaSortBar(
                    context,
                    reverse = sortReverse,
                    onReverseChange = {
                        scope.launch {
                            context.symphony.settings.updateData { s ->
                                s.copy { playlistsSortReverse = it }
                            }
                        }
                    },
                    sort = sortBy,
                    sorts = PlaylistSortBy.entries
                        .filter { it != PlaylistSortBy.UNRECOGNIZED }
                        .associateWith { x -> ViewContext.parameterizedFn { x.label(it) } },
                    onSortChange = {
                        scope.launch {
                            context.symphony.settings.updateData { s ->
                                s.copy { playlistsSortBy = it }
                            }
                        }
                    },
                    label = {
                        Text(
                            context.symphony.t.XPlaylists(
                                (playlistsCount ?: playlistIds.size).toString()
                            )
                        )
                    },
                    onShowModifyLayout = {
                        showModifyLayoutSheet = true
                    },
                )
            }
        },
        content = {
            when {
                playlistIds.isEmpty() -> IconTextBody(
                    icon = { modifier ->
                        Icon(
                            Icons.AutoMirrored.Filled.QueueMusic,
                            null,
                            modifier = modifier,
                        )
                    },
                    content = {
                        Text(context.symphony.t.DamnThisIsSoEmpty)
                    }
                )

                else -> ResponsiveGrid(gridColumns) {
                    itemsIndexed(
                        sortedPlaylistIds,
                        key = { i, x -> "$i-$x" },
                        contentType = { _, _ -> Groove.Kind.PLAYLIST }
                    ) { _, playlistId ->
                        context.symphony.groove.playlist.get(playlistId)?.let { playlist ->
                            PlaylistTile(context, playlist)
                        }
                    }
                }
            }

            if (showModifyLayoutSheet) {
                ResponsiveGridSizeAdjustBottomSheet(
                    context,
                    columns = gridColumns,
                    onColumnsChange = { cols ->
                        scope.launch {
                            context.symphony.settings.updateData { s ->
                                s.copy {
                                    playlistsHorizontalGridColumns = cols.horizontal
                                    playlistsVerticalGridColumns = cols.vertical
                                }
                            }
                        }
                    },
                    onDismissRequest = {
                        showModifyLayoutSheet = false
                    }
                )
            }
        }
    )
}

private fun PlaylistSortBy.label(context: ViewContext) = when (this) {
    PlaylistSortBy.PLAYLIST_SORT_CUSTOM -> context.symphony.t.Custom
    PlaylistSortBy.PLAYLIST_SORT_TITLE -> context.symphony.t.Title
    PlaylistSortBy.PLAYLIST_SORT_TRACKS_COUNT -> context.symphony.t.TrackCount
    else -> "???"
}
