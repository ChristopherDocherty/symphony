package io.github.zyrouge.symphony.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.zyrouge.symphony.AlbumFilter
import io.github.zyrouge.symphony.AlbumGroupBy
import io.github.zyrouge.symphony.AlbumSortBy
import io.github.zyrouge.symphony.R
import io.github.zyrouge.symphony.copy
import io.github.zyrouge.symphony.services.groove.Groove
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import io.github.zyrouge.symphony.ui.view.home.AlbumsPageState
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupedAlbumGrid(
    context: ViewContext,
    albumIds: List<String>,
    groupBy: AlbumGroupBy,
    pageState: AlbumsPageState? = null,
) {
    val sortBy by AlbumGridType.Default.getLastUsedSortBy(context).collectAsState(AlbumSortBy.ALBUM_NAME)
    val sortReverse by AlbumGridType.Default.getLastUsedReverse(context).collectAsState(false)
    val albumFilter by context.symphony.settings.data.map { it.uiAlbumGridAlbumFilter }.collectAsState(AlbumFilter.getDefaultInstance())
    val hiddenAlbumIds by context.symphony.settings.data
        .map { it.hiddenAlbumIdsList.toSet() }
        .collectAsState(emptySet())
    val showHiddenAlbums by context.symphony.settings.data
        .map { it.showHiddenAlbums }
        .collectAsState(false)
    val sortedAlbumIds by remember(albumIds, sortBy, sortReverse, albumFilter, hiddenAlbumIds, showHiddenAlbums) {
        derivedStateOf {
            context.symphony.groove.album.getAlbums(albumIds, sortBy, sortReverse, albumFilter, hiddenAlbumIds, showHiddenAlbums)
        }
    }
    val groupedAlbums by remember(sortedAlbumIds, groupBy) {
        derivedStateOf {
            groupAlbums(context, sortedAlbumIds, groupBy)
        }
    }
    val settings by context.symphony.settingsState.collectAsState()
    val horizontalGridColumns = settings.albumsHorizontalGridColumns
    val verticalGridColumns = settings.albumsVerticalGridColumns
    val gridColumns by remember(horizontalGridColumns, verticalGridColumns) {
        derivedStateOf {
            ResponsiveGridColumns(horizontalGridColumns, verticalGridColumns)
        }
    }
    var showModifyLayoutSheet by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }

    SideEffect {
        pageState?.sortedAlbumIds = sortedAlbumIds
    }

    val coroutineScope = rememberCoroutineScope()
    MediaSortBarScaffold(
        mediaSortBar = {
            MediaSortBar(
                context,
                reverse = sortReverse,
                onReverseChange = {
                    coroutineScope.launch {
                        AlbumGridType.Default.setLastUsedReverse(context, it)
                    }
                },
                sort = sortBy,
                sorts = AlbumSortBy.entries
                    .filter { it != AlbumSortBy.UNRECOGNIZED }
                    .associateWith { x -> ViewContext.parameterizedFn { x.label(it) } },
                onSortChange = {
                    coroutineScope.launch {
                        AlbumGridType.Default.setLastUsedSortBy(context, it)
                    }
                },
                label = {
                    Text(stringResource(R.string.XAlbums, sortedAlbumIds.size.toString()))
                },
                onShowModifyLayout = {
                    showModifyLayoutSheet = true
                },
                onShowFilterDialog = {
                    showFilterDialog = true
                },
            )
        },
        content = {
            when {
                sortedAlbumIds.isEmpty() -> IconTextBody(
                    icon = { modifier ->
                        Icon(
                            Icons.Filled.Album,
                            null,
                            modifier = modifier,
                        )
                    },
                    content = { Text(stringResource(R.string.DamnThisIsSoEmpty)) }
                )

                else -> ResponsiveGrid(gridColumns) { _ ->
                    groupedAlbums.forEach { (sectionTitle, ids) ->
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            GroupSectionHeader(sectionTitle)
                        }
                        items(ids, key = { it }, contentType = { Groove.Kind.ALBUM }) { albumId ->
                            context.symphony.groove.album.get(albumId)?.let { album ->
                                AlbumTile(context, album, pageState)
                            }
                        }
                    }
                }
            }

            if (showFilterDialog) {
                AlbumFilterDialog(
                    context = context,
                    onDismissRequest = { showFilterDialog = false },
                )
            }

            if (showModifyLayoutSheet) {
                AlbumGridLayoutSheet(
                    context,
                    columns = gridColumns,
                    onColumnsChange = { cols ->
                        coroutineScope.launch {
                            context.symphony.settings.updateData { s ->
                                s.copy {
                                    albumsHorizontalGridColumns = cols.horizontal
                                    albumsVerticalGridColumns = cols.vertical
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

@Composable
private fun GroupSectionHeader(title: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
    }
}

private fun groupAlbums(
    context: ViewContext,
    sortedIds: List<String>,
    groupBy: AlbumGroupBy,
): List<Pair<String, List<String>>> {
    val unknownLabel = context.activity.getString(R.string.Unknown)
    return when (groupBy) {
        AlbumGroupBy.ALBUM_GROUP_YEAR -> {
            val grouped = sortedIds.groupBy { id ->
                context.symphony.groove.album.get(id)?.startYear?.toString() ?: unknownLabel
            }
            val yearKeys = grouped.keys
                .filter { it != unknownLabel }
                .sortedByDescending { it.toIntOrNull() ?: 0 }
            val ordered = if (unknownLabel in grouped) yearKeys + unknownLabel else yearKeys
            ordered.map { key -> key to (grouped[key] ?: emptyList()) }
        }
        AlbumGroupBy.ALBUM_GROUP_ARTIST -> {
            val grouped = sortedIds.groupBy { id ->
                val album = context.symphony.groove.album.get(id)
                val artistName = album?.albumArtists?.firstOrNull()
                    ?: album?.artists?.firstOrNull()
                    ?: ""
                val stripped = if (artistName.uppercase().startsWith("THE ")) artistName.drop(4) else artistName
                val firstChar = stripped.firstOrNull()?.uppercaseChar()
                if (firstChar != null && firstChar.isLetter()) firstChar.toString() else "#"
            }
            val letterKeys = grouped.keys.filter { it != "#" }.sorted()
            val ordered = if ("#" in grouped) letterKeys + "#" else letterKeys
            ordered.map { key -> key to (grouped[key] ?: emptyList()) }
        }
        AlbumGroupBy.ALBUM_GROUP_NAME -> {
            val grouped = sortedIds.groupBy { id ->
                val album = context.symphony.groove.album.get(id)
                val firstChar = album?.name?.firstOrNull()?.uppercaseChar()
                if (firstChar != null && firstChar.isLetter()) firstChar.toString() else "#"
            }
            val letterKeys = grouped.keys.filter { it != "#" }.sorted()
            val ordered = if ("#" in grouped) letterKeys + "#" else letterKeys
            ordered.map { key -> key to (grouped[key] ?: emptyList()) }
        }
        else -> listOf("" to sortedIds)
    }
}
