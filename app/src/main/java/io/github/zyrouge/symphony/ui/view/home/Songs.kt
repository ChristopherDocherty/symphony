package io.github.zyrouge.symphony.ui.view.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import io.github.zyrouge.symphony.ui.components.LoaderScaffold
import io.github.zyrouge.symphony.ui.components.SongList
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import kotlinx.coroutines.flow.map

@Composable
fun SongsView(context: ViewContext) {
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
        )
    }
}
