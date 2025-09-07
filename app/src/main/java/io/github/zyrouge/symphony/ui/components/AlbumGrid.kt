package io.github.zyrouge.symphony.ui.components

import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
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
import io.github.zyrouge.symphony.AlbumSortBy
import io.github.zyrouge.symphony.Settings
import io.github.zyrouge.symphony.copy
import io.github.zyrouge.symphony.services.groove.Groove
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

enum class AlbumGridType {
    Default,
    Artist,
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumGrid(
    context: ViewContext,
    albumIds: List<String>,
    albumsCount: Int? = null,
    type: AlbumGridType = AlbumGridType.Default,
) {
    val sortBy by type.getLastUsedSortBy(context).collectAsState(AlbumSortBy.ALBUM_NAME)
    val sortReverse by type.getLastUsedReverse(context).collectAsState(false)
    val isHideCompilations by context.symphony.settings.data.map(Settings::getUiAlbumGridHideCompilations).collectAsState(false)
    val sortedAlbumIds by remember(albumIds, sortBy, sortReverse, isHideCompilations) {
        derivedStateOf {
            context.symphony.groove.album.getAlbums(albumIds, sortBy, sortReverse, isHideCompilations)
        }
    }
    val horizontalGridColumns by context.symphony.settingsOLD.lastUsedAlbumsHorizontalGridColumns.flow.collectAsState()
    val verticalGridColumns by context.symphony.settingsOLD.lastUsedAlbumsVerticalGridColumns.flow.collectAsState()
    val gridColumns by remember(horizontalGridColumns, verticalGridColumns) {
        derivedStateOf {
            ResponsiveGridColumns(horizontalGridColumns, verticalGridColumns)
        }
    }
    var showModifyLayoutSheet by remember { mutableStateOf(false) }

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
                sorts = AlbumSortBy.entries.associateWith { x ->
                    ViewContext.parameterizedFn { x.label(it) }
                },
                onSortChange = {
                    coroutineScope.launch {
                        type.setLastUsedSortBy(context, it)
                    }
                },
                label = {
                    Text(context.symphony.t.XAlbums((albumsCount ?: sortedAlbumIds.size).toString()))
                },
                onShowModifyLayout = {
                    showModifyLayoutSheet = true
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
                    content = { Text(context.symphony.t.DamnThisIsSoEmpty) }
                )

                else -> ResponsiveGrid(gridColumns) {
                    itemsIndexed(
                        sortedAlbumIds,
                        key = { i, x -> "$i-$x" },
                        contentType = { _, _ -> Groove.Kind.ALBUM }
                    ) { _, albumId ->
                        context.symphony.groove.album.get(albumId)?.let { album ->
                            AlbumTile(context, album)
                        }
                    }
                }
            }

            if (showModifyLayoutSheet) {
                ResponsiveGridSizeAdjustBottomSheet(
                    context,
                    columns = gridColumns,
                    onColumnsChange = {
                        context.symphony.settingsOLD.lastUsedAlbumsHorizontalGridColumns.setValue(
                            it.horizontal
                        )
                        context.symphony.settingsOLD.lastUsedAlbumsVerticalGridColumns.setValue(
                            it.vertical
                        )
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
    AlbumSortBy.ALBUM_CUSTOM -> context.symphony.t.Custom
    AlbumSortBy.ALBUM_NAME -> context.symphony.t.Album
    AlbumSortBy.ALBUM_ARTIST_NAME -> context.symphony.t.Artist
    AlbumSortBy.ALBUM_TRACKS_COUNT -> context.symphony.t.TrackCount
    AlbumSortBy.ALBUM_YEAR -> context.symphony.t.Year
    AlbumSortBy.UNRECOGNIZED -> "???"
}
