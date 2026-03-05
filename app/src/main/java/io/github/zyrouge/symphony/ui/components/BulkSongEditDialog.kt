package io.github.zyrouge.symphony.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.zyrouge.symphony.ui.helpers.ViewContext

@Composable
fun BulkSongEditDialog(
    context: ViewContext,
    songIds: List<String>,
    onDismissRequest: () -> Unit,
) {
    val songs = remember(songIds) {
        songIds.mapNotNull { context.symphony.groove.song.get(it) }
    }
    SongTagEditorDialog(context, songs, onDismissRequest)
}
