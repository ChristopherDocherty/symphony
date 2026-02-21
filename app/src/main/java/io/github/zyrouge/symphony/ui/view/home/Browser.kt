package io.github.zyrouge.symphony.ui.view.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import io.github.zyrouge.symphony.copy
import io.github.zyrouge.symphony.ui.components.LoaderScaffold
import io.github.zyrouge.symphony.ui.components.SongExplorerList
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import io.github.zyrouge.symphony.utils.SimplePath
import kotlinx.coroutines.launch

@Composable
fun BrowserView(context: ViewContext) {
    val scope = rememberCoroutineScope()
    val isUpdating by context.symphony.groove.song.isUpdating.collectAsState()
    val id by context.symphony.groove.song.id.collectAsState()
    val explorer = context.symphony.groove.song.explorer
    val settings by context.symphony.settingsState.collectAsState()
    val lastUsedFolderPath = remember(settings.browserPath) {
        settings.browserPath.takeIf { it.isNotEmpty() }
    }

    LoaderScaffold(context, isLoading = isUpdating) {
        SongExplorerList(
            context,
            initialPath = lastUsedFolderPath?.let { SimplePath(it) },
            key = id,
            explorer = explorer,
            onPathChange = { path ->
                scope.launch {
                    context.symphony.settings.updateData { s ->
                        s.copy { browserPath = path.pathString }
                    }
                }
            }
        )
    }
}
