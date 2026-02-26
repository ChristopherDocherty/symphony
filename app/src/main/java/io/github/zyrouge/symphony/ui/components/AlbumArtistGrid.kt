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
import io.github.zyrouge.symphony.AlbumArtistSortBy
import io.github.zyrouge.symphony.copy
import io.github.zyrouge.symphony.services.groove.Groove
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import io.github.zyrouge.symphony.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumArtistGrid(
    context: ViewContext,
    albumArtistNames: List<String>,
    albumArtistsCount: Int? = null,
) {
    val scope = rememberCoroutineScope()
    val settings by context.symphony.settingsState.collectAsState()
    val sortBy = settings.albumArtistsSortBy
    val sortReverse = settings.albumArtistsSortReverse
    val sortedAlbumArtistNames by remember(albumArtistNames, sortBy, sortReverse) {
        derivedStateOf {
            context.symphony.groove.albumArtist.sort(albumArtistNames, sortBy, sortReverse)
        }
    }
    val horizontalGridColumns = settings.albumArtistsHorizontalGridColumns
    val verticalGridColumns = settings.albumArtistsVerticalGridColumns
    val gridColumns by remember(horizontalGridColumns, verticalGridColumns) {
        derivedStateOf {
            ResponsiveGridColumns(horizontalGridColumns, verticalGridColumns)
        }
    }
    var showModifyLayoutSheet by remember { mutableStateOf(false) }

    MediaSortBarScaffold(
        mediaSortBar = {
            MediaSortBar(
                context,
                reverse = sortReverse,
                onReverseChange = { value ->
                    scope.launch {
                        context.symphony.settings.updateData { s -> s.copy { albumArtistsSortReverse = value } }
                    }
                },
                sort = sortBy,
                sorts = AlbumArtistSortBy.entries
                    .filter { it != AlbumArtistSortBy.UNRECOGNIZED }
                    .associateWith { x -> ViewContext.parameterizedFn { x.label(context) } },
                onSortChange = { value ->
                    scope.launch {
                        context.symphony.settings.updateData { s -> s.copy { albumArtistsSortBy = value } }
                    }
                },
                label = {
                    Text(
                        stringResource(R.string.XArtists, 
                            (albumArtistsCount ?: albumArtistNames.size).toString()
                        )
                    )
                },
                onShowModifyLayout = {
                    showModifyLayoutSheet = true
                },
            )
        },
        content = {
            when {
                albumArtistNames.isEmpty() -> IconTextBody(
                    icon = { modifier ->
                        Icon(
                            Icons.Filled.Person,
                            null,
                            modifier = modifier,
                        )
                    },
                    content = { Text(stringResource(R.string.DamnThisIsSoEmpty)) }
                )

                else -> ResponsiveGrid(gridColumns) {
                    itemsIndexed(
                        sortedAlbumArtistNames,
                        key = { i, x -> "$i-$x" },
                        contentType = { _, _ -> Groove.Kind.ARTIST }
                    ) { _, albumArtistName ->
                        context.symphony.groove.albumArtist.get(albumArtistName)
                            ?.let { albumArtist ->
                                AlbumArtistTile(context, albumArtist)
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
                                    albumArtistsHorizontalGridColumns = cols.horizontal
                                    albumArtistsVerticalGridColumns = cols.vertical
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

private fun AlbumArtistSortBy.label(context: ViewContext) = when (this) {
    AlbumArtistSortBy.ALBUM_ARTIST_CUSTOM -> context.activity.getString(R.string.Custom)
    AlbumArtistSortBy.ALBUM_ARTIST_SORT_NAME -> context.activity.getString(R.string.Artist)
    AlbumArtistSortBy.ALBUM_ARTIST_ALBUMS_COUNT -> context.activity.getString(R.string.AlbumCount)
    AlbumArtistSortBy.ALBUM_ARTIST_TRACKS_COUNT -> context.activity.getString(R.string.TrackCount)
    AlbumArtistSortBy.UNRECOGNIZED -> "???"
}
