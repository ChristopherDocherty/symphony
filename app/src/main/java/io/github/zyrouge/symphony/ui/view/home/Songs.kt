package io.github.zyrouge.symphony.ui.view.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.IndeterminateCheckBox
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.res.stringResource
import io.github.zyrouge.symphony.R
import io.github.zyrouge.symphony.copy
import io.github.zyrouge.symphony.ui.components.LoaderScaffold
import io.github.zyrouge.symphony.ui.components.SongList
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class SongsPageState(private val context: ViewContext) : HomePageState {
    var isMultiSelectMode by mutableStateOf(false)
    var selectedSongIds by mutableStateOf<Set<String>>(emptySet())
    var showBulkEditDialog by mutableStateOf(false)
    var showAutoNumberDialog by mutableStateOf(false)
    var showRenameDialog by mutableStateOf(false)

    // Updated from SongList via SideEffect — not observed by state
    var sortedSongIds: List<String> = emptyList()

    fun enterMultiSelect(songId: String? = null) {
        isMultiSelectMode = true
        selectedSongIds = if (songId != null) setOf(songId) else emptySet()
    }

    fun exitMultiSelect() {
        isMultiSelectMode = false
        selectedSongIds = emptySet()
    }

    fun toggleSelection(songId: String) {
        selectedSongIds = if (songId in selectedSongIds)
            selectedSongIds - songId
        else
            selectedSongIds + songId
    }

    @Composable
    override fun DropdownItems() {
        val coroutineScope = rememberCoroutineScope()
        val showScrobbleCount by context.symphony.settings.data
            .map { it.songShowScrobbleCount }
            .collectAsState(false)

        if (isMultiSelectMode) {
            DropdownMenuItem(
                leadingIcon = { Icon(Icons.Filled.SelectAll, null) },
                text = { Text("Select all") },
                onClick = { selectedSongIds = sortedSongIds.toSet() },
            )
            DropdownMenuItem(
                leadingIcon = { Icon(Icons.Filled.IndeterminateCheckBox, null) },
                text = { Text("Deselect all") },
                onClick = { selectedSongIds = emptySet() },
            )
            DropdownMenuItem(
                leadingIcon = { Icon(Icons.Filled.Close, null) },
                text = { Text("Exit select mode") },
                onClick = { exitMultiSelect() },
            )
        } else {
            DropdownMenuItem(
                leadingIcon = {
                    Icon(
                        if (showScrobbleCount) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                        null,
                    )
                },
                text = { Text(stringResource(R.string.ShowScrobbleCount)) },
                onClick = {
                    coroutineScope.launch {
                        context.symphony.settings.updateData { it.copy { songShowScrobbleCount = !showScrobbleCount } }
                    }
                },
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
        // Dialogs are managed inside SongList
    }
}

@Composable
fun SongsView(context: ViewContext, pageState: SongsPageState? = null) {
    val isUpdating by context.symphony.groove.song.isUpdating.collectAsState()
    val songIds by context.symphony.groove.song.all.collectAsState()
    val hiddenAlbumIds by context.symphony.settings.data
        .map { it.hiddenAlbumIdsList.toSet() }
        .collectAsState(emptySet())
    val visibleSongIds by remember(songIds, hiddenAlbumIds) {
        derivedStateOf {
            if (hiddenAlbumIds.isEmpty()) songIds
            else {
                val hiddenSongIds = context.symphony.groove.album.getHiddenSongIds(hiddenAlbumIds)
                songIds.filterNot { it in hiddenSongIds }
            }
        }
    }

    LoaderScaffold(context, isLoading = isUpdating) {
        SongList(
            context,
            songIds = visibleSongIds,
            songsCount = visibleSongIds.size,
            enableAddMediaFoldersHint = true,
            pageState = pageState,
        )
    }
}
