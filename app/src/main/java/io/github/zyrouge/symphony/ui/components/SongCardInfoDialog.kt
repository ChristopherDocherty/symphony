package io.github.zyrouge.symphony.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import io.github.zyrouge.symphony.R
import io.github.zyrouge.symphony.copy
import io.github.zyrouge.symphony.ui.components.settings.SettingsSwitchTile
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import kotlinx.coroutines.launch

@Composable
fun SongCardInfoDialog(context: ViewContext, onDismissRequest: () -> Unit) {
    val scope = rememberCoroutineScope()
    val settings by context.symphony.settingsState.collectAsState()

    ScaffoldDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.SongCardInfo)) },
        content = {
            Column {
                SettingsSwitchTile(
                    icon = { Icon(Icons.Filled.CalendarMonth, null) },
                    title = { Text(stringResource(R.string.ShowYear)) },
                    value = settings.songTileShowYear,
                    onChange = { value ->
                        scope.launch {
                            context.symphony.settings.updateData {
                                it.copy { songTileShowYear = value }
                            }
                        }
                    },
                )
                HorizontalDivider()
                SettingsSwitchTile(
                    icon = { Icon(Icons.Filled.Radio, null) },
                    title = { Text(stringResource(R.string.ShowScrobbleCount)) },
                    value = settings.songShowScrobbleCount,
                    onChange = { value ->
                        scope.launch {
                            context.symphony.settings.updateData {
                                it.copy { songShowScrobbleCount = value }
                            }
                        }
                    },
                )
            }
        },
        actions = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.Done))
            }
        },
    )
}
