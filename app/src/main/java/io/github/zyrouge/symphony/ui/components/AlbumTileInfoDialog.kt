package io.github.zyrouge.symphony.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.VisibilityOff
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
fun AlbumTileInfoDialog(context: ViewContext, onDismissRequest: () -> Unit) {
    val scope = rememberCoroutineScope()
    val settings by context.symphony.settingsState.collectAsState()

    ScaffoldDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.AlbumTileInfo)) },
        content = {
            Column {
            SettingsSwitchTile(
                icon = { Icon(Icons.Filled.VisibilityOff, null) },
                title = { Text(stringResource(R.string.MinimalistMode)) },
                value = settings.albumTileMinimalistMode,
                onChange = { value ->
                    scope.launch {
                        context.symphony.settings.updateData {
                            it.copy { albumTileMinimalistMode = value }
                        }
                    }
                },
            )
            HorizontalDivider()
            SettingsSwitchTile(
                icon = { Icon(Icons.Filled.Album, null) },
                title = { Text(stringResource(R.string.ShowAlbumName)) },
                value = settings.albumTileShowName,
                onChange = { value ->
                    scope.launch {
                        context.symphony.settings.updateData {
                            it.copy { albumTileShowName = value }
                        }
                    }
                },
            )
            HorizontalDivider()
            SettingsSwitchTile(
                icon = { Icon(Icons.Filled.Person, null) },
                title = { Text(stringResource(R.string.ShowArtistName)) },
                value = settings.albumTileShowArtist,
                onChange = { value ->
                    scope.launch {
                        context.symphony.settings.updateData {
                            it.copy { albumTileShowArtist = value }
                        }
                    }
                },
            )
            HorizontalDivider()
            SettingsSwitchTile(
                icon = { Icon(Icons.Filled.Radio, null) },
                title = { Text(stringResource(R.string.ShowScrobbleCount)) },
                value = settings.albumTileShowScrobbleCount,
                onChange = { value ->
                    scope.launch {
                        context.symphony.settings.updateData {
                            it.copy { albumTileShowScrobbleCount = value }
                        }
                    }
                },
            )
            HorizontalDivider()
            SettingsSwitchTile(
                icon = { Icon(Icons.Filled.DateRange, null) },
                title = { Text(stringResource(R.string.ShowReleaseYear)) },
                value = settings.albumTileShowReleaseYear,
                onChange = { value ->
                    scope.launch {
                        context.symphony.settings.updateData {
                            it.copy { albumTileShowReleaseYear = value }
                        }
                    }
                },
            )
            HorizontalDivider()
            SettingsSwitchTile(
                icon = { Icon(Icons.Filled.CalendarMonth, null) },
                title = { Text(stringResource(R.string.ShowReleaseMonth)) },
                value = settings.albumTileShowReleaseMonth,
                onChange = { value ->
                    scope.launch {
                        context.symphony.settings.updateData {
                            it.copy { albumTileShowReleaseMonth = value }
                        }
                    }
                },
            )
            } // Column
        },
        actions = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.Done))
            }
        },
    )
}
