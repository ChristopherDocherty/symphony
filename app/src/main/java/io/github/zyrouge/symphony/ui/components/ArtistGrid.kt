package io.github.zyrouge.symphony.ui.components

import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
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
import io.github.zyrouge.symphony.AlbumFilter
import io.github.zyrouge.symphony.AlbumSortBy
import io.github.zyrouge.symphony.ArtistSortBy
import io.github.zyrouge.symphony.SongSortBy
import io.github.zyrouge.symphony.copy
import io.github.zyrouge.symphony.services.groove.Groove
import io.github.zyrouge.symphony.services.groove.repositories.ArtistRepository
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistGrid(
    context: ViewContext,
    artistName: List<String>,
    artistsCount: Int? = null,
) {
    val settings by context.symphony.settingsState.collectAsState()
    val sortBy by context.symphony.settings.data.map { it.uiDefaultArtistSort.by }.collectAsState(
        ArtistSortBy.ARTIST_NAME)
    val sortReverse by context.symphony.settings.data.map { it.uiDefaultArtistSort.reverse }.collectAsState(false)
    val albumFilter by context.symphony.settings.data
        .map { it.uiArtistViewAlbumFilter }
        .collectAsState(AlbumFilter.getDefaultInstance())
    val sortedArtistNames by remember(artistName, sortBy, sortReverse, albumFilter) {
        derivedStateOf {
            val filtered = context.symphony.groove.artist.filterByTrackCount(artistName)
            val sorted = context.symphony.groove.artist.sort(filtered, sortBy, sortReverse)
            sorted.filter { name ->
                val albumIds = context.symphony.groove.artist.getAlbumIds(name)
                if (albumIds.isEmpty()) return@filter true
                context.symphony.groove.album.getAlbums(albumIds, AlbumSortBy.ALBUM_NAME, false, albumFilter).isNotEmpty()
            }
        }
    }
    val horizontalGridColumns = settings.artistsHorizontalGridColumns
    val verticalGridColumns = settings.artistsVerticalGridColumns
    val gridColumns by remember(horizontalGridColumns, verticalGridColumns) {
        derivedStateOf {
            ResponsiveGridColumns(horizontalGridColumns, verticalGridColumns)
        }
    }
    var showModifyLayoutSheet by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    MediaSortBarScaffold(
        mediaSortBar = {
            MediaSortBar(
                context,
                reverse = sortReverse,
                onReverseChange = { value->
                    scope.launch{
                        context.symphony.settings.updateData { it.copy { uiDefaultArtistSort = uiDefaultArtistSort.copy { reverse = value} }}
                    }
                },
                sort = sortBy,
                sorts = ArtistSortBy.entries
                    .filter { it != ArtistSortBy.UNRECOGNIZED }
                    .associateWith { x -> ViewContext.parameterizedFn { x.label(it) } },
                onSortChange = {value ->
                    scope.launch {
                        context.symphony.settings.updateData { it.copy { uiDefaultArtistSort = uiDefaultArtistSort.copy { by = value} }}

                    }
                },
                label = {
                    Text(context.symphony.t.XArtists((artistsCount ?: artistName.size).toString()))
                },
                onShowModifyLayout = {
                    showModifyLayoutSheet = true
                },
                onShowFilterDialog = { showFilterDialog = true },
            )
        },
        content = {
            when {
                artistName.isEmpty() -> IconTextBody(
                    icon = { modifier ->
                        Icon(
                            Icons.Filled.Person,
                            null,
                            modifier = modifier,
                        )
                    },
                    content = { Text(context.symphony.t.DamnThisIsSoEmpty) }
                )

                else -> ResponsiveGrid(gridColumns) {
                    itemsIndexed(
                        sortedArtistNames,
                        key = { i, x -> "$i-$x" },
                        contentType = { _, _ -> Groove.Kind.ARTIST }
                    ) { _, artistName ->
                        context.symphony.groove.artist.get(artistName)?.let { artist ->
                            ArtistTile(context, artist)
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
                                    artistsHorizontalGridColumns = cols.horizontal
                                    artistsVerticalGridColumns = cols.vertical
                                }
                            }
                        }
                    },
                    onDismissRequest = {
                        showModifyLayoutSheet = false
                    }
                )
            }

            if (showFilterDialog) {
                ArtistAlbumFilterDialog(context, onDismissRequest = { showFilterDialog = false })
            }
        }
    )
}

private fun ArtistSortBy.label(context: ViewContext) = when (this) {
    ArtistSortBy.ARTIST_CUSTOM -> context.symphony.t.Custom
    ArtistSortBy.ARTIST_NAME -> context.symphony.t.Artist
    ArtistSortBy.ARTIST_ALBUMS_COUNT -> context.symphony.t.AlbumCount
    ArtistSortBy.ARTIST_TRACKS_COUNT -> context.symphony.t.TrackCount
    ArtistSortBy.UNRECOGNIZED -> "???"
}
