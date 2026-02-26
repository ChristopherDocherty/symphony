package io.github.zyrouge.symphony.ui.view.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CenterFocusWeak
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.HeadsetOff
import androidx.compose.material.icons.filled.PlayArrow
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
import io.github.zyrouge.symphony.copy
import io.github.zyrouge.symphony.ui.components.IconButtonPlaceholder
import io.github.zyrouge.symphony.ui.components.TopAppBarMinimalTitle
import io.github.zyrouge.symphony.ui.components.settings.SettingsSideHeading
import io.github.zyrouge.symphony.ui.components.settings.SettingsSliderTile
import io.github.zyrouge.symphony.ui.components.settings.SettingsSwitchTile
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource
import io.github.zyrouge.symphony.R

@Serializable
object PlayerSettingsViewRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSettingsView(context: ViewContext) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val settings by context.symphony.settingsState.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    TopAppBarMinimalTitle {
                        Text("${stringResource(R.string.Settings)} - ${stringResource(R.string.Player)}")
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
                    val seekDurationRange = 3f..60f

                    SettingsSideHeading(stringResource(R.string.Player))
                    SettingsSwitchTile(
                        icon = {
                            Icon(Icons.Filled.GraphicEq, null)
                        },
                        title = {
                            Text(stringResource(R.string.FadePlaybackInOut))
                        },
                        value = settings.fadePlayback,
                        onChange = { value ->
                            scope.launch {
                                context.symphony.settings.updateData { it.copy { fadePlayback = value } }
                            }
                        }
                    )
                    HorizontalDivider()
                    SettingsSliderTile(
                        context,
                        icon = {
                            Icon(Icons.Filled.GraphicEq, null)
                        },
                        title = {
                            Text(stringResource(R.string.FadePlaybackInOut))
                        },
                        label = { value ->
                            Text(stringResource(R.string.XSecs, value.toString()))
                        },
                        range = 0.5f..6f,
                        initialValue = settings.fadePlaybackDuration.let { if (it == 0f) 1f else it },
                        onValue = { value ->
                            value.times(2).roundToInt().toFloat().div(2)
                        },
                        onChange = { value ->
                            scope.launch {
                                context.symphony.settings.updateData { it.copy { fadePlaybackDuration = value } }
                            }
                        },
                        onReset = {
                            scope.launch {
                                context.symphony.settings.updateData { it.copy { fadePlaybackDuration = 1f } }
                            }
                        },
                    )
                    HorizontalDivider()
                    SettingsSwitchTile(
                        icon = {
                            Icon(Icons.Filled.CenterFocusWeak, null)
                        },
                        title = {
                            Text(stringResource(R.string.RequireAudioFocus))
                        },
                        value = settings.requireAudioFocus,
                        onChange = { value ->
                            scope.launch {
                                context.symphony.settings.updateData { it.copy { requireAudioFocus = value } }
                            }
                        }
                    )
                    HorizontalDivider()
                    SettingsSwitchTile(
                        icon = {
                            Icon(Icons.Filled.CenterFocusWeak, null)
                        },
                        title = {
                            Text(stringResource(R.string.IgnoreAudioFocusLoss))
                        },
                        value = settings.ignoreAudioFocusLoss,
                        onChange = { value ->
                            scope.launch {
                                context.symphony.settings.updateData { it.copy { ignoreAudioFocusLoss = value } }
                            }
                        }
                    )
                    HorizontalDivider()
                    SettingsSwitchTile(
                        icon = {
                            Icon(Icons.Filled.Headset, null)
                        },
                        title = {
                            Text(stringResource(R.string.PlayOnHeadphonesConnect))
                        },
                        value = settings.playOnHeadphonesConnect,
                        onChange = { value ->
                            scope.launch {
                                context.symphony.settings.updateData { it.copy { playOnHeadphonesConnect = value } }
                            }
                        }
                    )
                    HorizontalDivider()
                    SettingsSwitchTile(
                        icon = {
                            Icon(Icons.Filled.HeadsetOff, null)
                        },
                        title = {
                            Text(stringResource(R.string.PauseOnHeadphonesDisconnect))
                        },
                        value = settings.pauseOnHeadphonesDisconnect,
                        onChange = { value ->
                            scope.launch {
                                context.symphony.settings.updateData { it.copy { pauseOnHeadphonesDisconnect = value } }
                            }
                        }
                    )
                    HorizontalDivider()
                    SettingsSliderTile(
                        context,
                        icon = {
                            Icon(Icons.Filled.FastRewind, null)
                        },
                        title = {
                            Text(stringResource(R.string.FastRewindDuration))
                        },
                        label = { value ->
                            Text(stringResource(R.string.XSecs, value.toString()))
                        },
                        range = seekDurationRange,
                        initialValue = settings.seekBackDuration.let { if (it == 0) 15 else it }.toFloat(),
                        onValue = { value ->
                            value.roundToInt().toFloat()
                        },
                        onChange = { value ->
                            scope.launch {
                                context.symphony.settings.updateData { it.copy { seekBackDuration = value.toInt() } }
                            }
                        },
                        onReset = {
                            scope.launch {
                                context.symphony.settings.updateData { it.copy { seekBackDuration = 15 } }
                            }
                        },
                    )
                    HorizontalDivider()
                    SettingsSliderTile(
                        context,
                        icon = {
                            Icon(Icons.Filled.FastForward, null)
                        },
                        title = {
                            Text(stringResource(R.string.FastForwardDuration))
                        },
                        label = { value ->
                            Text(stringResource(R.string.XSecs, value.toString()))
                        },
                        range = seekDurationRange,
                        initialValue = settings.seekForwardDuration.let { if (it == 0) 30 else it }.toFloat(),
                        onValue = { value ->
                            value.roundToInt().toFloat()
                        },
                        onChange = { value ->
                            scope.launch {
                                context.symphony.settings.updateData { it.copy { seekForwardDuration = value.toInt() } }
                            }
                        },
                        onReset = {
                            scope.launch {
                                context.symphony.settings.updateData { it.copy { seekForwardDuration = 30 } }
                            }
                        },
                    )
                    HorizontalDivider()
                    SettingsSwitchTile(
                        icon = {
                            Icon(Icons.Filled.PlayArrow, null)
                        },
                        title = {
                            Text(stringResource(R.string.GaplessPlayback))
                        },
                        value = settings.gaplessPlayback,
                        onChange = { value ->
                            scope.launch {
                                context.symphony.settings.updateData { it.copy { gaplessPlayback = value } }
                            }
                        },
                    )
                }
            }
        }
    )
}
