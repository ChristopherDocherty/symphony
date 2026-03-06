package io.github.zyrouge.symphony.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.zyrouge.symphony.R
import io.github.zyrouge.symphony.WishlistGroupBy
import io.github.zyrouge.symphony.WishlistSortBy
import io.github.zyrouge.symphony.copy
import io.github.zyrouge.symphony.services.groove.WishlistAlbum
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import io.github.zyrouge.symphony.ui.theme.ThemeColors
import io.github.zyrouge.symphony.ui.view.WishlistAlbumViewRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Composable
fun WishlistAlbumTile(
    context: ViewContext,
    album: WishlistAlbum,
    updateId: Long,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    SquareGrooveTile(
        image = remember(updateId, album.id) {
            context.symphony.groove.wishlist.createArtworkImageRequest(album.id)
        },
        options = { expanded, onDismissRequest ->
            DropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest) {
                DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Filled.Edit, null) },
                    text = { Text(stringResource(R.string.EditWishlistAlbum)) },
                    onClick = {
                        onDismissRequest()
                        onEdit()
                    },
                )
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(Icons.Filled.Delete, null, tint = ThemeColors.Red)
                    },
                    text = { Text(stringResource(R.string.DeleteWishlistAlbum)) },
                    onClick = {
                        onDismissRequest()
                        onDelete()
                    },
                )
            }
        },
        content = {
            Column(modifier = Modifier.padding(horizontal = 2.dp)) {
                Text(
                    album.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    album.artist,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = buildString {
                    album.year?.let { append(it.toString()) }
                    if (album.priority != 0) {
                        if (isNotEmpty()) append(" · ")
                        append("P${album.priority}")
                    }
                }
                if (meta.isNotEmpty()) {
                    Text(
                        meta,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        onPlay = {},
        onClick = { context.navController.navigate(WishlistAlbumViewRoute(album.id)) },
        onLongClick = null,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishlistGrid(context: ViewContext, albumIds: List<String>) {
    val coroutineScope = rememberCoroutineScope()
    val updateId by context.symphony.groove.wishlist.updateId.collectAsState()
    val settings by context.symphony.settingsState.collectAsState()
    val sortBy = settings.wishlistSortBy
    val sortReverse = settings.wishlistSortReverse
    val groupBy by context.symphony.settings.data
        .map { it.wishlistGroupBy }
        .collectAsState(WishlistGroupBy.WISHLIST_GROUP_NONE)
    val sortedAlbumIds by remember(albumIds, sortBy, sortReverse, updateId) {
        derivedStateOf {
            context.symphony.groove.wishlist.sort(albumIds, sortBy, sortReverse)
        }
    }
    val horizontalGridColumns = settings.wishlistHorizontalGridColumns
        .takeIf { it > 0 } ?: ResponsiveGridColumns.DEFAULT_HORIZONTAL_COLUMNS
    val verticalGridColumns = settings.wishlistVerticalGridColumns
        .takeIf { it > 0 } ?: ResponsiveGridColumns.DEFAULT_VERTICAL_COLUMNS
    val gridColumns by remember(horizontalGridColumns, verticalGridColumns) {
        derivedStateOf { ResponsiveGridColumns(horizontalGridColumns, verticalGridColumns) }
    }
    var showModifyLayoutSheet by remember { mutableStateOf(false) }
    var editAlbumId by remember { mutableStateOf<String?>(null) }
    var deleteAlbumId by remember { mutableStateOf<String?>(null) }
    val noPriorityLabel = stringResource(R.string.NoPriority)
    val groupedAlbumIds by remember(sortedAlbumIds, groupBy, noPriorityLabel) {
        derivedStateOf {
            if (groupBy == WishlistGroupBy.WISHLIST_GROUP_PRIORITY) {
                groupWishlistByPriority(context, sortedAlbumIds, noPriorityLabel)
            } else null
        }
    }

    MediaSortBarScaffold(
        mediaSortBar = {
            MediaSortBar(
                context,
                reverse = sortReverse,
                onReverseChange = {
                    coroutineScope.launch {
                        context.symphony.settings.updateData { s ->
                            s.copy { wishlistSortReverse = it }
                        }
                    }
                },
                sort = sortBy,
                sorts = WishlistSortBy.entries
                    .filter { it != WishlistSortBy.UNRECOGNIZED }
                    .associateWith { x -> ViewContext.parameterizedFn { x.label(it) } },
                onSortChange = {
                    coroutineScope.launch {
                        context.symphony.settings.updateData { s ->
                            s.copy { wishlistSortBy = it }
                        }
                    }
                },
                label = {
                    Text(stringResource(R.string.XWishlistAlbums, sortedAlbumIds.size.toString()))
                },
                onShowModifyLayout = { showModifyLayoutSheet = true },
            )
        },
        content = {
            when {
                albumIds.isEmpty() -> IconTextBody(
                    icon = { modifier ->
                        Icon(Icons.Filled.Bookmark, null, modifier = modifier)
                    },
                    content = {
                        Text(stringResource(R.string.WishlistEmpty))
                    },
                )

                groupedAlbumIds != null -> ResponsiveGrid(gridColumns) { _ ->
                    groupedAlbumIds!!.forEach { (sectionTitle, ids) ->
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            GroupSectionHeader(sectionTitle)
                        }
                        items(ids, key = { it }) { id ->
                            context.symphony.groove.wishlist.get(id)?.let { album ->
                                WishlistAlbumTile(
                                    context = context,
                                    album = album,
                                    updateId = updateId,
                                    onEdit = { editAlbumId = id },
                                    onDelete = { deleteAlbumId = id },
                                )
                            }
                        }
                    }
                }

                else -> ResponsiveGrid(gridColumns) {
                    itemsIndexed(sortedAlbumIds, key = { i, x -> "$i-$x" }) { _, id ->
                        context.symphony.groove.wishlist.get(id)?.let { album ->
                            WishlistAlbumTile(
                                context = context,
                                album = album,
                                updateId = updateId,
                                onEdit = { editAlbumId = id },
                                onDelete = { deleteAlbumId = id },
                            )
                        }
                    }
                }
            }

            if (showModifyLayoutSheet) {
                WishlistGridLayoutSheet(
                    context,
                    columns = gridColumns,
                    onColumnsChange = { cols ->
                        coroutineScope.launch {
                            context.symphony.settings.updateData { s ->
                                s.copy {
                                    wishlistHorizontalGridColumns = cols.horizontal
                                    wishlistVerticalGridColumns = cols.vertical
                                }
                            }
                        }
                    },
                    onDismissRequest = { showModifyLayoutSheet = false },
                )
            }

            editAlbumId?.let { id ->
                context.symphony.groove.wishlist.get(id)?.let { album ->
                    AddWishlistAlbumDialog(
                        context = context,
                        existingAlbum = album,
                        onDismissRequest = { editAlbumId = null },
                    )
                }
            }

            deleteAlbumId?.let { id ->
                context.symphony.groove.wishlist.get(id)?.let { album ->
                    ConfirmationDialog(
                        context = context,
                        title = { Text(stringResource(R.string.DeleteWishlistAlbum)) },
                        description = { Text("${album.artist} – ${album.name}") },
                        onResult = { confirmed ->
                            deleteAlbumId = null
                            if (confirmed) {
                                coroutineScope.launch(Dispatchers.IO) {
                                    context.symphony.groove.wishlist.delete(id)
                                }
                            }
                        },
                    )
                }
            }
        },
    )
}

@Composable
private fun WishlistGridLayoutSheet(
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
        .map { it.wishlistGroupBy }
        .collectAsState(WishlistGroupBy.WISHLIST_GROUP_NONE)
    val coroutineScope = rememberCoroutineScope()
    var showGroupByDropdown by remember { mutableStateOf(false) }

    val groupByOptions = listOf(
        WishlistGroupBy.WISHLIST_GROUP_NONE to stringResource(R.string.GroupByNone),
        WishlistGroupBy.WISHLIST_GROUP_PRIORITY to stringResource(R.string.GroupByPriority),
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
                                            s.copy { wishlistGroupBy = mode }
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

private fun groupWishlistByPriority(
    context: ViewContext,
    sortedIds: List<String>,
    noPriorityLabel: String,
): List<Pair<String, List<String>>> {
    val grouped = sortedIds.groupBy { id ->
        val priority = context.symphony.groove.wishlist.get(id)?.priority ?: 0
        if (priority == 0) noPriorityLabel else "P$priority"
    }
    val numericKeys = grouped.keys
        .filter { it != noPriorityLabel }
        .sortedBy { it.removePrefix("P").toIntOrNull() ?: Int.MAX_VALUE }
    val ordered = if (noPriorityLabel in grouped) numericKeys + noPriorityLabel else numericKeys
    return ordered.map { key -> key to (grouped[key] ?: emptyList()) }
}

private fun WishlistSortBy.label(context: ViewContext) = when (this) {
    WishlistSortBy.WISHLIST_SORT_CUSTOM -> context.activity.getString(R.string.Custom)
    WishlistSortBy.WISHLIST_SORT_NAME -> context.activity.getString(R.string.Album)
    WishlistSortBy.WISHLIST_SORT_ARTIST -> context.activity.getString(R.string.Artist)
    WishlistSortBy.WISHLIST_SORT_YEAR -> context.activity.getString(R.string.Year)
    WishlistSortBy.WISHLIST_SORT_PRIORITY -> context.activity.getString(R.string.Priority)
    else -> "???"
}
