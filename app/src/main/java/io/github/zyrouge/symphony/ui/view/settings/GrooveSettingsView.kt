package io.github.zyrouge.symphony.ui.view.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.repeatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.RuleFolder
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.VisibilityOff // Added import
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import io.github.zyrouge.symphony.AlbumSortBy
import io.github.zyrouge.symphony.Symphony
import io.github.zyrouge.symphony.copy
import io.github.zyrouge.symphony.Settings
import io.github.zyrouge.symphony.services.groove.Groove
import io.github.zyrouge.symphony.ui.components.AdaptiveSnackbar
import io.github.zyrouge.symphony.ui.components.IconButtonPlaceholder
import io.github.zyrouge.symphony.ui.components.TopAppBarMinimalTitle
import io.github.zyrouge.symphony.ui.components.label
import io.github.zyrouge.symphony.ui.components.settings.ConsiderContributingTile
import io.github.zyrouge.symphony.ui.components.settings.SettingsMultiGrooveFolderTile
import io.github.zyrouge.symphony.ui.components.settings.SettingsMultiSystemFolderTile
import io.github.zyrouge.symphony.ui.components.settings.SettingsMultiTextOptionTile
import io.github.zyrouge.symphony.ui.components.settings.SettingsOptionTile
import io.github.zyrouge.symphony.ui.components.settings.SettingsSideHeading
import io.github.zyrouge.symphony.ui.components.settings.SettingsSimpleTile
import io.github.zyrouge.symphony.ui.components.settings.SettingsSliderTile
import io.github.zyrouge.symphony.ui.components.settings.SettingsSwitchTile
import io.github.zyrouge.symphony.ui.components.settings.SettingsTextInputTile
import io.github.zyrouge.symphony.ui.helpers.TransitionDurations
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import io.github.zyrouge.symphony.ui.view.SettingsViewRoute
import io.github.zyrouge.symphony.utils.ImagePreserver
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

@Serializable
data class GrooveSettingsViewRoute(val initialElement: String? = null)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrooveSettingsView(context: ViewContext, route: GrooveSettingsViewRoute) {
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()
    val songsFilterPattern by context.symphony.settingsOLD.songsFilterPattern.flow.collectAsState()
    val minSongDuration by context.symphony.settingsOLD.minSongDuration.flow.collectAsState()
    val blacklistFolders by context.symphony.settingsOLD.blacklistFolders.flow.collectAsState()
    val whitelistFolders by context.symphony.settingsOLD.whitelistFolders.flow.collectAsState()
    val artistTagSeparators by context.symphony.settingsOLD.artistTagSeparators.flow.collectAsState()
    val genreTagSeparators by context.symphony.settingsOLD.genreTagSeparators.flow.collectAsState()
    val mediaFolders by context.symphony.settingsOLD.mediaFolders.flow.collectAsState()
    val artworkQuality by context.symphony.settingsOLD.artworkQuality.flow.collectAsState()
    val caseSensitiveSorting by context.symphony.settingsOLD.caseSensitiveSorting.flow.collectAsState()
    val useMetaphony by context.symphony.settingsOLD.useMetaphony.flow.collectAsState()
    val isHideCompilations by context.symphony.settings.data.map(Settings::getUiAlbumGridHideCompilations).collectAsState(false)
    val artistAlbumsview by context.symphony.settings.data.map{it.uiArtistViewAlbumSortBy.by}.collectAsState(
        AlbumSortBy.ALBUM_YEAR)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = {
            SnackbarHost(snackbarHostState) {
                AdaptiveSnackbar(it)
            }
        },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    TopAppBarMinimalTitle {
                        Text("${context.symphony.t.Settings} - ${context.symphony.t.Groove}")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent,
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
                    val defaultSongsFilterPattern = ".*"
                    val minSongDurationRange = 0f..60f

                    ConsiderContributingTile(context)
                    SettingsSideHeading(context.symphony.t.Groove)
                    SpotlightTile(route.initialElement == SettingsViewRoute.ELEMENT_MEDIA_FOLDERS) {
                        SettingsMultiSystemFolderTile(
                            context,
                            icon = {
                                Icon(Icons.Filled.LibraryMusic, null)
                            },
                            title = {
                                Text(context.symphony.t.MediaFolders)
                            },
                            initialValues = mediaFolders,
                            onChange = { values ->
                                context.symphony.settingsOLD.mediaFolders.setValue(values)
                                refreshMediaLibrary(context.symphony)
                            }
                        )
                    }
                    HorizontalDivider()
                    SettingsTextInputTile(
                        context,
                        icon = {
                            Icon(Icons.Filled.FilterAlt, null)
                        },
                        title = {
                            Text(context.symphony.t.SongsFilterPattern)
                        },
                        value = songsFilterPattern ?: defaultSongsFilterPattern,
                        onReset = {
                            context.symphony.settingsOLD.songsFilterPattern.setValue(null)
                        },
                        onChange = { value ->
                            context.symphony.settingsOLD.songsFilterPattern.setValue(
                                when (value) {
                                    defaultSongsFilterPattern -> null
                                    else -> value
                                }
                            )
                            refreshMediaLibrary(context.symphony)
                        }
                    )
                    HorizontalDivider()
                    SettingsSliderTile(
                        context,
                        icon = {
                            Icon(Icons.Filled.FilterAlt, null)
                        },
                        title = {
                            Text(context.symphony.t.MinSongDurationFilter)
                        },
                        label = { value ->
                            Text(context.symphony.t.XSecs(value.toString()))
                        },
                        range = minSongDurationRange,
                        initialValue = minSongDuration.toFloat(),
                        onValue = { value ->
                            value.roundToInt().toFloat()
                        },
                        onChange = { value ->
                            context.symphony.settingsOLD.minSongDuration.setValue(value.toInt())
                        },
                        onReset = {
                            context.symphony.settingsOLD.minSongDuration.setValue(
                                context.symphony.settingsOLD.minSongDuration.defaultValue,
                            )
                        },
                    )
                    HorizontalDivider()
                    SettingsMultiGrooveFolderTile(
                        context,
                        icon = {
                            Icon(Icons.Filled.RuleFolder, null)
                        },
                        title = {
                            Text(context.symphony.t.BlacklistFolders)
                        },
                        explorer = context.symphony.groove.exposer.explorer,
                        initialValues = blacklistFolders,
                        onChange = { values ->
                            context.symphony.settingsOLD.blacklistFolders.setValue(values)
                            refreshMediaLibrary(context.symphony)
                        }
                    )
                    HorizontalDivider()
                    SettingsMultiGrooveFolderTile(
                        context,
                        icon = {
                            Icon(Icons.Filled.RuleFolder, null)
                        },
                        title = {
                            Text(context.symphony.t.WhitelistFolders)
                        },
                        explorer = context.symphony.groove.exposer.explorer,
                        initialValues = whitelistFolders,
                        onChange = { values ->
                            context.symphony.settingsOLD.whitelistFolders.setValue(values)
                            refreshMediaLibrary(context.symphony)
                        }
                    )
                    HorizontalDivider()
                    SettingsMultiTextOptionTile(
                        context,
                        icon = {
                            Icon(Icons.Filled.SpaceBar, null)
                        },
                        title = {
                            Text(context.symphony.t.ArtistTagValueSeparators)
                        },
                        values = artistTagSeparators.toList(),
                        onChange = {
                            context.symphony.settingsOLD.artistTagSeparators.setValue(it.toSet())
                            refreshMediaLibrary(context.symphony)
                        },
                    )
                    HorizontalDivider()
                    SettingsMultiTextOptionTile(
                        context,
                        icon = {
                            Icon(Icons.Filled.SpaceBar, null)
                        },
                        title = {
                            Text(context.symphony.t.GenreTagValueSeparators)
                        },
                        values = genreTagSeparators.toList(),
                        onChange = {
                            context.symphony.settingsOLD.genreTagSeparators.setValue(it.toSet())
                            refreshMediaLibrary(context.symphony)
                        },
                    )
                    HorizontalDivider()
                    SettingsOptionTile(
                        icon = {
                            Icon(Icons.Filled.Image, null)
                        },
                        title = {
                            Text(context.symphony.t.ArtworkQuality)
                        },
                        value = artworkQuality,
                        values = ImagePreserver.Quality.entries
                            .associateWith { it.label(context) },
                        onChange = { value ->
                            context.symphony.settingsOLD.artworkQuality.setValue(value)
                        }
                    )
                    HorizontalDivider()
                    SettingsSwitchTile(
                        icon = {
                            Icon(Icons.Filled.VisibilityOff, null)
                        },
                        title = {
                            Text("Hide Compilations")
                        },
                        value = isHideCompilations,
                        onChange = { value ->
                            coroutineScope.launch {
                                context.symphony.settings.updateData { it.copy { uiAlbumGridHideCompilations = value} }
                            }
                        }
                    )
                    HorizontalDivider()
                    SettingsSwitchTile(
                        icon = {
                            Icon(Icons.Filled.TextFields, null)
                        },
                        title = {
                            Text(context.symphony.t.CaseSensitiveSorting)
                        },
                        value = caseSensitiveSorting, // Changed from value to checked
                        onChange = { value ->
                            context.symphony.settingsOLD.caseSensitiveSorting.setValue(value)
                        }
                    )
                    HorizontalDivider()
                    SettingsSwitchTile(
                        icon = {
                            Icon(Icons.Filled.FindInPage, null)
                        },
                        title = {
                            Text(context.symphony.t.UseMetaphonyMetadataDecoder)
                        },
                        value = useMetaphony, // Changed from value to checked
                        onChange = { value ->
                            context.symphony.settingsOLD.useMetaphony.setValue(value)
                        }
                    )
                    HorizontalDivider()
                    SettingsSimpleTile(
                        icon = {
                            Icon(Icons.Filled.Storage, null)
                        },
                        title = {
                            Text(context.symphony.t.ClearSongCache)
                        },
                        onClick = {
                            refreshMediaLibrary(context.symphony, true)
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(
                                    context.symphony.t.SongCacheCleared,
                                    withDismissAction = true,
                                )
                            }
                        }
                    )
                    HorizontalDivider()
                    SettingsOptionTile(
                        icon = {
                            Icon(Icons.Filled.VisibilityOff, null)
                        },
                        title = {
                            Text("Artist View Album Sort")
                        },
                        value = artistAlbumsview,
                        values = AlbumSortBy.entries.associateWith { it.label(context) },
                        onChange = { value ->
                            coroutineScope.launch {
                                context.symphony.settings.updateData { it.copy { uiArtistViewAlbumSortBy = uiArtistViewAlbumSortBy.copy {by = value}} }
                            }
                        }
                    )
                }
            }
        }
    )
}

fun ImagePreserver.Quality.label(context: ViewContext) = when (this) {
    ImagePreserver.Quality.Low -> context.symphony.t.Low
    ImagePreserver.Quality.Medium -> context.symphony.t.Medium
    ImagePreserver.Quality.High -> context.symphony.t.High
    ImagePreserver.Quality.Loseless -> context.symphony.t.Loseless
}

private fun refreshMediaLibrary(symphony: Symphony, clearCache: Boolean = false) {
    symphony.radio.stop()
    symphony.groove.coroutineScope.launch {
        val options = Groove.FetchOptions(
            resetInMemoryCache = true,
            resetPersistentCache = clearCache,
        )
        symphony.groove.fetch(options)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SpotlightTile(isInSpotlight: Boolean, content: @Composable (() -> Unit)) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val highlightAlphaAnimated = remember { Animatable(0f) }
    val highlightColor = MaterialTheme.colorScheme.surfaceTint

    LaunchedEffect(isInSpotlight) {
        if (isInSpotlight) {
            bringIntoViewRequester.bringIntoView()
            delay(100)
            highlightAlphaAnimated.animateTo(
                targetValue = 0.3f,
                animationSpec = repeatable(
                    2,
                    TransitionDurations.Fast.asTween(easing = LinearEasing)
                ),
            )
            highlightAlphaAnimated.snapTo(0f)
        }
    }

    Box(
        modifier = Modifier
            .bringIntoViewRequester(bringIntoViewRequester)
            .drawWithContent {
                drawContent()
                drawRect(color = highlightColor, alpha = highlightAlphaAnimated.value)
            }
    ) {
        content()
    }
}
