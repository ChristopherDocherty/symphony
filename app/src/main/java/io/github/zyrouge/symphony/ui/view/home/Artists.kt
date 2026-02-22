package io.github.zyrouge.symphony.ui.view.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import io.github.zyrouge.symphony.AlbumSortBy
import io.github.zyrouge.symphony.copy
import io.github.zyrouge.symphony.ui.components.ArtistGrid
import io.github.zyrouge.symphony.ui.components.LoaderScaffold
import io.github.zyrouge.symphony.ui.components.label
import io.github.zyrouge.symphony.ui.components.settings.SettingsOptionDialog
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class ArtistsPageState : HomePageState {
    var showSortDialog by mutableStateOf(false)

    @Composable
    override fun DropdownItems() {
        DropdownMenuItem(
            leadingIcon = { Icon(Icons.Filled.SortByAlpha, contentDescription = null) },
            text = { Text("Sort Artist Albums By") },
            onClick = { showSortDialog = true },
        )
    }

    @Composable
    override fun Dialogs(context: ViewContext) {
        val coroutineScope = rememberCoroutineScope()
        val currentSort by context.symphony.settings.data
            .map { it.uiArtistViewAlbumSortBy.by }
            .collectAsState(AlbumSortBy.ALBUM_YEAR)
        if (showSortDialog) {
            SettingsOptionDialog(
                title = { Text("Sort Artist Albums By") },
                value = currentSort,
                values = AlbumSortBy.entries
                    .filter { it != AlbumSortBy.UNRECOGNIZED }
                    .associateWith { it.label(context) },
                onDismissRequest = { showSortDialog = false },
                onChange = { sort ->
                    coroutineScope.launch {
                        context.symphony.settings.updateData {
                            it.copy {
                                uiArtistViewAlbumSortBy = uiArtistViewAlbumSortBy.copy { by = sort }
                            }
                        }
                    }
                    showSortDialog = false
                },
            )
        }
    }
}

@Composable
fun ArtistsView(context: ViewContext) {
    val isUpdating by context.symphony.groove.artist.isUpdating.collectAsState()
    val artistNames by context.symphony.groove.artist.all.collectAsState()
    val artistsCount by context.symphony.groove.artist.count.collectAsState()

    LoaderScaffold(context, isLoading = isUpdating) {
        ArtistGrid(
            context,
            artistName = artistNames,
            artistsCount = artistsCount,
        )
    }
}
