package io.github.zyrouge.symphony.ui.view.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Wysiwyg
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.github.zyrouge.symphony.NowPlayingControlsLayout
import io.github.zyrouge.symphony.NowPlayingLyricsLayout
import io.github.zyrouge.symphony.copy
import io.github.zyrouge.symphony.ui.components.IconButtonPlaceholder
import io.github.zyrouge.symphony.ui.components.TopAppBarMinimalTitle
import io.github.zyrouge.symphony.ui.components.settings.SettingsOptionTile
import io.github.zyrouge.symphony.ui.components.settings.SettingsSideHeading
import io.github.zyrouge.symphony.ui.components.settings.SettingsSwitchTile
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
object NowPlayingSettingsViewRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingSettingsView(context: ViewContext) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val settings by context.symphony.settingsState.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    TopAppBarMinimalTitle {
                        Text("${context.symphony.t.Settings} - ${context.symphony.t.NowPlaying}")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                ),
                navigationIcon = {
                    IconButton(
                        onClick = {
                            context.navController.popBackStack()
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                actions = {
                    IconButtonPlaceholder()
                },
            )
        },
        content = { contentPadding ->
            Box(
                modifier = Modifier
                    .padding(contentPadding)
                    .fillMaxSize()
            ) {
                Column(modifier = Modifier.verticalScroll(scrollState)) {
                    SettingsSideHeading(context.symphony.t.NowPlaying)
                    SettingsOptionTile(
                        icon = {
                            Icon(Icons.Filled.Dashboard, null)
                        },
                        title = {
                            Text(context.symphony.t.ControlsLayout)
                        },
                        value = settings.nowPlayingControlsLayout,
                        values = NowPlayingControlsLayout.entries
                            .filter { it != NowPlayingControlsLayout.UNRECOGNIZED }
                            .associateWith { it.label(context) },
                        onChange = { value ->
                            scope.launch {
                                context.symphony.settings.updateData { it.copy { nowPlayingControlsLayout = value } }
                            }
                        }
                    )
                    HorizontalDivider()
                    SettingsOptionTile(
                        icon = {
                            Icon(Icons.AutoMirrored.Outlined.Article, null)
                        },
                        title = {
                            Text(context.symphony.t.LyricsLayout)
                        },
                        value = settings.nowPlayingLyricsLayout,
                        values = NowPlayingLyricsLayout.entries
                            .filter { it != NowPlayingLyricsLayout.UNRECOGNIZED }
                            .associateWith { it.label(context) },
                        onChange = { value ->
                            scope.launch {
                                context.symphony.settings.updateData { it.copy { nowPlayingLyricsLayout = value } }
                            }
                        }
                    )
                    HorizontalDivider()
                    SettingsSwitchTile(
                        icon = {
                            Icon(Icons.AutoMirrored.Filled.Wysiwyg, null)
                        },
                        title = {
                            Text(context.symphony.t.ShowAudioInformation)
                        },
                        value = settings.nowPlayingAdditionalInfo,
                        onChange = { value ->
                            scope.launch {
                                context.symphony.settings.updateData { it.copy { nowPlayingAdditionalInfo = value } }
                            }
                        }
                    )
                    HorizontalDivider()
                    SettingsSwitchTile(
                        icon = {
                            Icon(Icons.Filled.Forward30, null)
                        },
                        title = {
                            Text(context.symphony.t.ShowSeekControls)
                        },
                        value = settings.nowPlayingSeekControls,
                        onChange = { value ->
                            scope.launch {
                                context.symphony.settings.updateData { it.copy { nowPlayingSeekControls = value } }
                            }
                        }
                    )
                    HorizontalDivider()
                    SettingsSwitchTile(
                        icon = {
                            Icon(Icons.Filled.Lyrics, null)
                        },
                        title = {
                            Text(context.symphony.t.KeepScreenAwakeOnLyrics)
                        },
                        value = settings.lyricsKeepScreenAwake,
                        onChange = { value ->
                            scope.launch {
                                context.symphony.settings.updateData { it.copy { lyricsKeepScreenAwake = value } }
                            }
                        }
                    )
                }
            }
        }
    )
}

fun NowPlayingControlsLayout.label(context: ViewContext) = when (this) {
    NowPlayingControlsLayout.CONTROLS_COMPACT_LEFT -> context.symphony.t.CompactLeft
    NowPlayingControlsLayout.CONTROLS_COMPACT_RIGHT -> context.symphony.t.CompactRight
    NowPlayingControlsLayout.CONTROLS_TRADITIONAL -> context.symphony.t.Traditional
    NowPlayingControlsLayout.UNRECOGNIZED -> "???"
}

fun NowPlayingLyricsLayout.label(context: ViewContext) = when (this) {
    NowPlayingLyricsLayout.LYRICS_REPLACE_ARTWORK -> context.symphony.t.ReplaceArtwork
    NowPlayingLyricsLayout.LYRICS_SEPARATE_PAGE -> context.symphony.t.SeparatePage
    NowPlayingLyricsLayout.UNRECOGNIZED -> "???"
}
