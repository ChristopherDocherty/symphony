package io.github.zyrouge.symphony.ui.view.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import io.github.zyrouge.symphony.copy
import io.github.zyrouge.symphony.ui.components.LoaderScaffold
import io.github.zyrouge.symphony.ui.components.SongTreeList
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import kotlinx.coroutines.launch

@Composable
fun TreeView(context: ViewContext) {
    val scope = rememberCoroutineScope()
    val isUpdating by context.symphony.groove.song.isUpdating.collectAsState()
    val songIds by context.symphony.groove.song.all.collectAsState()
    val songsCount by context.symphony.groove.song.count.collectAsState()
    val settings by context.symphony.settingsState.collectAsState()
    val disabledTreePaths = settings.disabledTreePathsList

    LoaderScaffold(context, isLoading = isUpdating) {
        SongTreeList(
            context,
            songIds = songIds,
            songsCount = songsCount,
            initialDisabled = disabledTreePaths,
            onDisable = { paths ->
                scope.launch {
                    context.symphony.settings.updateData { s ->
                        s.copy {
                            disabledTreePaths.clear()
                            disabledTreePaths.addAll(paths)
                        }
                    }
                }
            },
        )
    }
}
