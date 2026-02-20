package io.github.zyrouge.symphony.ui.view.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.IndeterminateCheckBox
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.zyrouge.symphony.ui.components.AlbumFilterDialog
import io.github.zyrouge.symphony.ui.components.AlbumGrid
import io.github.zyrouge.symphony.ui.components.BulkAlbumEditDialog
import io.github.zyrouge.symphony.ui.components.LoaderScaffold
import io.github.zyrouge.symphony.ui.helpers.ViewContext

class AlbumsPageState : HomePageState {
    var showFilterDialog by mutableStateOf(false)
    var isMultiSelectMode by mutableStateOf(false)
    var selectedAlbumIds by mutableStateOf<Set<String>>(emptySet())
    var showBulkEditDialog by mutableStateOf(false)

    // Updated from AlbumGrid via SideEffect — not observed by state
    var sortedAlbumIds: List<String> = emptyList()

    fun enterMultiSelect(albumId: String? = null) {
        isMultiSelectMode = true
        selectedAlbumIds = if (albumId != null) setOf(albumId) else emptySet()
    }

    fun exitMultiSelect() {
        isMultiSelectMode = false
        selectedAlbumIds = emptySet()
    }

    fun toggleSelection(albumId: String) {
        selectedAlbumIds = if (albumId in selectedAlbumIds)
            selectedAlbumIds - albumId
        else
            selectedAlbumIds + albumId
    }

    @Composable
    override fun DropdownItems() {
        if (isMultiSelectMode) {
            DropdownMenuItem(
                leadingIcon = { Icon(Icons.Filled.SelectAll, null) },
                text = { Text("Select all") },
                onClick = { selectedAlbumIds = sortedAlbumIds.toSet() },
            )
            DropdownMenuItem(
                leadingIcon = { Icon(Icons.Filled.IndeterminateCheckBox, null) },
                text = { Text("Deselect all") },
                onClick = { selectedAlbumIds = emptySet() },
            )
            DropdownMenuItem(
                leadingIcon = { Icon(Icons.Filled.Close, null) },
                text = { Text("Exit select mode") },
                onClick = { exitMultiSelect() },
            )
        } else {
            DropdownMenuItem(
                leadingIcon = { Icon(Icons.Filled.FilterAlt, contentDescription = "filter") },
                text = { Text("Filter") },
                onClick = { showFilterDialog = true },
            )
            DropdownMenuItem(
                leadingIcon = { Icon(Icons.Filled.CheckBox, null) },
                text = { Text("Select") },
                onClick = { enterMultiSelect() },
            )
        }
    }

    @Composable
    override fun Dialogs(context: ViewContext) {
        if (showFilterDialog) {
            AlbumFilterDialog(
                context = context,
                onDismissRequest = { showFilterDialog = false },
            )
        }
        if (showBulkEditDialog && selectedAlbumIds.isNotEmpty()) {
            BulkAlbumEditDialog(
                context = context,
                albumIds = selectedAlbumIds.toList(),
                onDismissRequest = { showBulkEditDialog = false },
            )
        }
    }
}

@Composable
fun AlbumsView(context: ViewContext, pageState: AlbumsPageState? = null) {
    val isUpdating by context.symphony.groove.album.isUpdating.collectAsState()
    val albumIds by context.symphony.groove.album.all.collectAsState()
    val albumsCount by context.symphony.groove.album.count.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        LoaderScaffold(context, isLoading = isUpdating) {
            AlbumGrid(
                context,
                albumIds = albumIds,
                albumsCount = albumsCount,
                pageState = pageState,
            )
        }
        if (pageState?.isMultiSelectMode == true) {
            MultiSelectBottomBar(
                modifier = Modifier.align(Alignment.BottomCenter),
                selectedCount = pageState.selectedAlbumIds.size,
                onSelectAll = { pageState.selectedAlbumIds = pageState.sortedAlbumIds.toSet() },
                onEdit = { pageState.showBulkEditDialog = true },
                onExit = { pageState.exitMultiSelect() },
            )
        }
    }
}

@Composable
private fun MultiSelectBottomBar(
    modifier: Modifier = Modifier,
    selectedCount: Int,
    onSelectAll: () -> Unit,
    onEdit: () -> Unit,
    onExit: () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "$selectedCount selected",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 8.dp),
            )
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = onSelectAll) {
                Text("Select all")
            }
            IconButton(onClick = onEdit, enabled = selectedCount > 0) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit selected")
            }
            IconButton(onClick = onExit) {
                Icon(Icons.Filled.Close, contentDescription = "Exit select mode")
            }
        }
    }
}
