package io.github.zyrouge.symphony.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.zyrouge.symphony.ui.helpers.ViewContext

@Composable
fun BulkAlbumEditDialog(
    context: ViewContext,
    albumIds: List<String>,
    onDismissRequest: () -> Unit,
) {
    val songs = remember(albumIds) {
        albumIds.flatMap { albumId ->
            context.symphony.groove.album.getSongIds(albumId)
                .mapNotNull { context.symphony.groove.song.get(it) }
        }
    }
    SongTagEditorDialog(context, songs, onDismissRequest)
}
