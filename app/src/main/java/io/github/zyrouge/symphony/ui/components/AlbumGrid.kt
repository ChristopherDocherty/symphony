package io.github.zyrouge.symphony.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
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
internal fun AlbumGridLayoutSheet(
    context: ViewContext,
    columns: ResponsiveGridColumns,
    onColumnsChange: (ResponsiveGridColumns) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val isVertical = LocalConfiguration.current.run { screenHeightDp > screenWidthDp }
    val maxWidth = LocalConfiguration.current.screenWidthDp
    val maxColumns = maxWidth / ResponsiveGridColumns.MIN_GRID_WIDTH
    val effectiveColumns by remember(isVertical, columns) {
        derivedStateOf {
            when {
                isVertical -> columns.vertical
                else -> columns.horizontal
            }
        }
    }
    var sliderValue by remember { mutableFloatStateOf(effectiveColumns.toFloat()) }
    val currentGroupBy by context.symphony.settings.data
        .map { it.albumGroupBy }
        .collectAsState(AlbumGroupBy.ALBUM_GROUP_NONE)
    val coroutineScope = rememberCoroutineScope()
    var showGroupByDropdown by remember { mutableStateOf(false) }

    val groupByOptions = listOf(
        AlbumGroupBy.ALBUM_GROUP_NONE to stringResource(R.string.GroupByNone),
        AlbumGroupBy.ALBUM_GROUP_YEAR to stringResource(R.string.GroupByYear),
        AlbumGroupBy.ALBUM_GROUP_ARTIST to stringResource(R.string.GroupByArtist),
        AlbumGroupBy.ALBUM_GROUP_NAME to stringResource(R.string.GroupByAlbumName),
    )
    val currentGroupByLabel = groupByOptions.firstOrNull { it.first == currentGroupBy }?.second
        ?: stringResource(R.string.GroupByNone)

    ScaffoldDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.GridColumns)) },
        content = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(top = 16.dp),
            ) {
                Slider(
                    value = sliderValue,
                    onChange = { sliderValue = it.toInt().toFloat() },
                    range = 1f..maxColumns.toFloat(),
                    label = { Text(it.toInt().toString()) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                Text(
                    stringResource(R.string.GroupBy),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
                Box {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showGroupByDropdown = true }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = currentGroupByLabel,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = null,
                            tint = LocalContentColor.current,
                        )
                    }
                    DropdownMenu(
                        expanded = showGroupByDropdown,
                        onDismissRequest = { showGroupByDropdown = false },
                    ) {
                        groupByOptions.forEach { (mode, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    coroutineScope.launch {
                                        context.symphony.settings.updateData { s ->
                                            s.copy { albumGroupBy = mode }
                                        }
                                    }
                                    showGroupByDropdown = false
                                },
                            )
                        }
                    }
                }
            }
        },
        actions = {
            TextButton(
                onClick = {
                    val nColumns = when {
                        isVertical -> columns.copy(vertical = sliderValue.toInt())
                        else -> columns.copy(horizontal = sliderValue.toInt())
                    }
                    onColumnsChange(nColumns)
                    onDismissRequest()
                }
            ) {
                Text(stringResource(R.string.Done))
            }
        },
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
