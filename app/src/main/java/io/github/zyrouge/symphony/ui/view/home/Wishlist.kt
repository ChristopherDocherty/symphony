package io.github.zyrouge.symphony.ui.view.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.IndeterminateCheckBox
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.zyrouge.symphony.R
import io.github.zyrouge.symphony.ui.components.AddWishlistAlbumDialog
import io.github.zyrouge.symphony.ui.components.BulkWishlistEditDialog
import io.github.zyrouge.symphony.ui.components.LoaderScaffold
import io.github.zyrouge.symphony.ui.components.WishlistGrid
import io.github.zyrouge.symphony.ui.helpers.ViewContext

class WishlistPageState : HomePageState {
    var showAddDialog by mutableStateOf(false)
    var isMultiSelectMode by mutableStateOf(false)
    var selectedAlbumIds by mutableStateOf<Set<String>>(emptySet())
    var showBulkEditDialog by mutableStateOf(false)

    // Updated from WishlistGrid via SideEffect — not observed by state
    var sortedAlbumIds: List<String> = emptyList()
    // Updated from WishlistGrid via onSizeChanged on the bottom bar
    var bottomBarHeightPx by mutableIntStateOf(0)

    fun enterMultiSelect(albumId: String? = null) {
        isMultiSelectMode = true
        selectedAlbumIds = if (albumId != null) setOf(albumId) else emptySet()
    }

    fun exitMultiSelect() {
        isMultiSelectMode = false
        selectedAlbumIds = emptySet()
        bottomBarHeightPx = 0
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
                leadingIcon = { Icon(Icons.Filled.CheckBox, null) },
                text = { Text("Select") },
                onClick = { enterMultiSelect() },
            )
        }
    }

    @Composable
    override fun Dialogs(context: ViewContext) {
        if (showAddDialog) {
            AddWishlistAlbumDialog(
                context = context,
                existingAlbum = null,
                onDismissRequest = { showAddDialog = false },
            )
        }
        if (showBulkEditDialog && selectedAlbumIds.isNotEmpty()) {
            BulkWishlistEditDialog(
                context = context,
                albumIds = selectedAlbumIds.toList(),
                onDismissRequest = { showBulkEditDialog = false },
            )
        }
    }
}

@Composable
fun WishlistView(context: ViewContext, pageState: WishlistPageState? = null) {
    val isUpdating by context.symphony.groove.wishlist.isUpdating.collectAsState()
    val albumIds by context.symphony.groove.wishlist.all.collectAsState()

    val bottomBarHeight = with(LocalDensity.current) {
        (pageState?.bottomBarHeightPx ?: 0).toDp()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LoaderScaffold(context, isLoading = isUpdating) {
            WishlistGrid(context, albumIds, pageState)
        }
        ExtendedFloatingActionButton(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp + bottomBarHeight),
            onClick = { pageState?.showAddDialog = true },
            icon = { Icon(Icons.Filled.Add, null) },
            text = { Text(stringResource(R.string.AddToWishlist)) },
        )
    }
}
