package io.github.zyrouge.symphony.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.MusicNote
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
fun ArtistTileInfoDialog(context: ViewContext, onDismissRequest: () -> Unit) {
    val scope = rememberCoroutineScope()
    val settings by context.symphony.settingsState.collectAsState()

    ScaffoldDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.ArtistTileInfo)) },
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
                    icon = { Icon(Icons.Filled.Person, null) },
                    title = { Text(stringResource(R.string.ShowArtistName)) },
                    value = settings.artistTileShowName,
                    onChange = { value ->
                        scope.launch {
                            context.symphony.settings.updateData {
                                it.copy { artistTileShowName = value }
                            }
                        }
                    },
                )
                HorizontalDivider()
                SettingsSwitchTile(
                    icon = { Icon(Icons.Filled.Album, null) },
                    title = { Text(stringResource(R.string.AlbumCount)) },
                    value = settings.artistTileShowAlbumCount,
                    onChange = { value ->
                        scope.launch {
                            context.symphony.settings.updateData {
                                it.copy { artistTileShowAlbumCount = value }
                            }
                        }
                    },
                )
                HorizontalDivider()
                SettingsSwitchTile(
                    icon = { Icon(Icons.Filled.MusicNote, null) },
                    title = { Text(stringResource(R.string.TrackCount)) },
                    value = settings.artistTileShowTrackCount,
                    onChange = { value ->
                        scope.launch {
                            context.symphony.settings.updateData {
                                it.copy { artistTileShowTrackCount = value }
                            }
                        }
                    },
                )
                HorizontalDivider()
                SettingsSwitchTile(
                    icon = { Icon(Icons.Filled.Radio, null) },
                    title = { Text(stringResource(R.string.ShowScrobbleCount)) },
                    value = settings.artistTileShowScrobbleCount,
                    onChange = { value ->
                        scope.launch {
                            context.symphony.settings.updateData {
                                it.copy { artistTileShowScrobbleCount = value }
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
