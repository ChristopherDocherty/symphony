package io.github.zyrouge.symphony.ui.components

import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import io.github.zyrouge.symphony.AlbumFilter
import io.github.zyrouge.symphony.AlbumSortBy
import io.github.zyrouge.symphony.copy
import io.github.zyrouge.symphony.services.groove.Groove
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import io.github.zyrouge.symphony.ui.view.home.AlbumsPageState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import io.github.zyrouge.symphony.R

enum class AlbumGridType {
    Default,
    Artist,
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumGrid(
    context: ViewContext,
    albumIds: List<String>,
    type: AlbumGridType = AlbumGridType.Default,
    pageState: AlbumsPageState? = null,
) {
    val sortBy by type.getLastUsedSortBy(context).collectAsState(AlbumSortBy.ALBUM_NAME)
    val sortReverse by type.getLastUsedReverse(context).collectAsState(false)
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
                       type.setLastUsedReverse(context, it)
                    }
                },
                sort = sortBy,
                sorts = AlbumSortBy.entries
                    .filter { it != AlbumSortBy.UNRECOGNIZED }
                    .associateWith { x -> ViewContext.parameterizedFn { x.label(it) } },
                onSortChange = {
                    coroutineScope.launch {
                        type.setLastUsedSortBy(context, it)
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

                else -> ResponsiveGrid(gridColumns) {
                    itemsIndexed(
                        sortedAlbumIds,
                        key = { i, x -> "$i-$x" },
                        contentType = { _, _ -> Groove.Kind.ALBUM }
                    ) { _, albumId ->
                        context.symphony.groove.album.get(albumId)?.let { album ->
                            AlbumTile(context, album, pageState)
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
                ResponsiveGridSizeAdjustBottomSheet(
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

fun AlbumGridType.getLastUsedSortBy(context: ViewContext) : Flow<AlbumSortBy> = when (this) {
    AlbumGridType.Default -> context.symphony.settings.data.map { it.uiDefaultAlbumSortBy.by}
    AlbumGridType.Artist -> context.symphony.settings.data.map { it.uiArtistViewAlbumSortBy.by}
}

suspend fun AlbumGridType.setLastUsedSortBy(context: ViewContext, sort: AlbumSortBy) =
    when (this) {
        AlbumGridType.Default -> {
            context.symphony.settings.updateData { it.copy { uiDefaultAlbumSortBy = uiDefaultAlbumSortBy .copy { by = sort} }}
        }
        AlbumGridType.Artist -> {
            context.symphony.settings.updateData { it.copy {  uiArtistViewAlbumSortBy = uiArtistViewAlbumSortBy.copy {by = sort}} }
        }
    }

fun AlbumGridType.getLastUsedReverse(context: ViewContext) : Flow<Boolean> = when (this) {
    AlbumGridType.Default -> context.symphony.settings.data.map { it.uiDefaultAlbumSortBy.reverse}
    AlbumGridType.Artist -> context.symphony.settings.data.map { it.uiArtistViewAlbumSortBy.reverse}
}

suspend fun AlbumGridType.setLastUsedReverse(context: ViewContext, value: Boolean) =
    when (this) {
        AlbumGridType.Default -> {
            context.symphony.settings.updateData { it.copy { uiDefaultAlbumSortBy = uiDefaultAlbumSortBy.copy { reverse = value} }}
        }
        AlbumGridType.Artist -> {
            context.symphony.settings.updateData { it.copy { uiArtistViewAlbumSortBy = uiArtistViewAlbumSortBy.copy {reverse = value}} }
        }
    }

fun AlbumSortBy.label(context: ViewContext) = when (this) {
    AlbumSortBy.ALBUM_CUSTOM -> context.activity.getString(R.string.Custom)
    AlbumSortBy.ALBUM_NAME -> context.activity.getString(R.string.Album)
    AlbumSortBy.ALBUM_ARTIST_NAME -> context.activity.getString(R.string.Artist)
    AlbumSortBy.ALBUM_TRACKS_COUNT -> context.activity.getString(R.string.TrackCount)
    AlbumSortBy.ALBUM_YEAR -> context.activity.getString(R.string.Year)
    AlbumSortBy.ALBUM_SCROBBLE_COUNT -> context.activity.getString(R.string.ScrobbleCount)
    AlbumSortBy.UNRECOGNIZED -> "???"
}
