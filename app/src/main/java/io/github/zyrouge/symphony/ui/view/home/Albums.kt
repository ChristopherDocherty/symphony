package io.github.zyrouge.symphony.ui.view.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.zyrouge.symphony.ui.components.AlbumFilterDialog
import io.github.zyrouge.symphony.ui.components.AlbumGrid
import io.github.zyrouge.symphony.ui.components.LoaderScaffold
import io.github.zyrouge.symphony.ui.helpers.ViewContext

class AlbumsPageState : HomePageState {
    var showFilterDialog by mutableStateOf(false)

    @Composable
    override fun DropdownItems() {
        DropdownMenuItem(
            leadingIcon = { Icon(Icons.Filled.FilterAlt, contentDescription = "filter") },
            text = { Text("Filter") },
            onClick = { showFilterDialog = true }
        )
    }

    @Composable
    override fun Dialogs(context: ViewContext) {
        if (showFilterDialog) {
            AlbumFilterDialog(
                context = context,
                onDismissRequest = { showFilterDialog = false }
            )
        }
    }
}

@Composable
fun AlbumsView(context: ViewContext) {
    val isUpdating by context.symphony.groove.album.isUpdating.collectAsState()
    val albumIds by context.symphony.groove.album.all.collectAsState()
    val albumsCount by context.symphony.groove.album.count.collectAsState()

    LoaderScaffold(context, isLoading = isUpdating) {
        AlbumGrid(
            context,
            albumIds = albumIds,
            albumsCount = albumsCount,
        )
    }
}
