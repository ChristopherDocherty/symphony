package io.github.zyrouge.symphony.ui.view.settings

import android.net.Uri
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
import io.github.zyrouge.symphony.ArtworkQuality
import io.github.zyrouge.symphony.Symphony
import io.github.zyrouge.symphony.copy
import io.github.zyrouge.symphony.services.groove.Groove
import io.github.zyrouge.symphony.ui.components.AdaptiveSnackbar
import io.github.zyrouge.symphony.ui.components.IconButtonPlaceholder
import io.github.zyrouge.symphony.ui.components.TopAppBarMinimalTitle
import io.github.zyrouge.symphony.ui.components.label
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource
import io.github.zyrouge.symphony.R

@Serializable
data class GrooveSettingsViewRoute(val initialElement: String? = null)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrooveSettingsView(context: ViewContext, route: GrooveSettingsViewRoute) {
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()
    val settings by context.symphony.settingsState.collectAsState()

    val songsFilterPattern = settings.songsFilterPattern.takeIf { it.isNotEmpty() }
    val minSongDuration = settings.minSongDuration
    val blacklistFolders = settings.blacklistFoldersList.toSet()
    val whitelistFolders = settings.whitelistFoldersList.toSet()
    val artistTagSeparators = settings.artistTagSeparatorsList.toSet()
    val genreTagSeparators = settings.genreTagSeparatorsList.toSet()
    val mediaFolders = settings.mediaFoldersList.map { Uri.parse(it) }.toSet()
    val artworkQuality = settings.artworkQuality
    val caseSensitiveSorting = settings.caseSensitiveSorting
    val useMetaphony = settings.useMetaphony
    val artistAlbumsview by context.symphony.settings.data.map { it.uiArtistViewAlbumSortBy.by }.collectAsState(
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
                        Text("${stringResource(R.string.Settings)} - ${stringResource(R.string.Groove)}")
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

                    SettingsSideHeading(stringResource(R.string.Groove))
                    SpotlightTile(route.initialElement == SettingsViewRoute.ELEMENT_MEDIA_FOLDERS) {
                        SettingsMultiSystemFolderTile(
                            context,
                            icon = {
                                Icon(Icons.Filled.LibraryMusic, null)
                            },
                            title = {
                                Text(stringResource(R.string.MediaFolders))
                            },
                            initialValues = mediaFolders,
                            onChange = { values ->
                                coroutineScope.launch {
                                    context.symphony.settings.updateData {
                                        it.copy {
                                            this.mediaFolders.clear()
                                            this.mediaFolders.addAll(values.map { uri -> uri.toString() })
                                        }
                                    }
                                    refreshMediaLibrary(context.symphony)
                                }
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
                            Text(stringResource(R.string.SongsFilterPattern))
                        },
                        value = songsFilterPattern ?: defaultSongsFilterPattern,
                        onReset = {
                            coroutineScope.launch {
                                context.symphony.settings.updateData { it.copy { this.songsFilterPattern = "" } }
                                refreshMediaLibrary(context.symphony)
                            }
                        },
                        onChange = { value ->
                            coroutineScope.launch {
                                context.symphony.settings.updateData {
                                    it.copy {
                                        this.songsFilterPattern = when (value) {
                                            defaultSongsFilterPattern -> ""
                                            else -> value
                                        }
                                    }
                                }
                                refreshMediaLibrary(context.symphony)
                            }
                        }
                    )
                    HorizontalDivider()
                    SettingsSliderTile(
                        context,
                        icon = {
                            Icon(Icons.Filled.FilterAlt, null)
                        },
                        title = {
                            Text(stringResource(R.string.MinSongDurationFilter))
                        },
                        label = { value ->
                            Text(stringResource(R.string.XSecs, value.toString()))
                        },
                        range = minSongDurationRange,
                        initialValue = minSongDuration.toFloat(),
                        onValue = { value ->
                            value.roundToInt().toFloat()
                        },
                        onChange = { value ->
                            coroutineScope.launch {
                                context.symphony.settings.updateData { it.copy { this.minSongDuration = value.toInt() } }
                            }
                        },
                        onReset = {
                            coroutineScope.launch {
                                context.symphony.settings.updateData { it.copy { this.minSongDuration = 0 } }
                            }
                        },
                    )
                    HorizontalDivider()
                    SettingsMultiGrooveFolderTile(
                        context,
                        icon = {
                            Icon(Icons.Filled.RuleFolder, null)
                        },
                        title = {
                            Text(stringResource(R.string.BlacklistFolders))
                        },
                        explorer = context.symphony.groove.exposer.explorer,
                        initialValues = blacklistFolders,
                        onChange = { values ->
                            coroutineScope.launch {
                                context.symphony.settings.updateData {
                                    it.copy {
                                        this.blacklistFolders.clear()
                                        this.blacklistFolders.addAll(values)
                                    }
                                }
                                refreshMediaLibrary(context.symphony)
                            }
                        }
                    )
                    HorizontalDivider()
                    SettingsMultiGrooveFolderTile(
                        context,
                        icon = {
                            Icon(Icons.Filled.RuleFolder, null)
                        },
                        title = {
                            Text(stringResource(R.string.WhitelistFolders))
                        },
                        explorer = context.symphony.groove.exposer.explorer,
                        initialValues = whitelistFolders,
                        onChange = { values ->
                            coroutineScope.launch {
                                context.symphony.settings.updateData {
                                    it.copy {
                                        this.whitelistFolders.clear()
                                        this.whitelistFolders.addAll(values)
                                    }
                                }
                                refreshMediaLibrary(context.symphony)
                            }
                        }
                    )
                    HorizontalDivider()
                    SettingsMultiTextOptionTile(
                        context,
                        icon = {
                            Icon(Icons.Filled.SpaceBar, null)
                        },
                        title = {
                            Text(stringResource(R.string.ArtistTagValueSeparators))
                        },
                        values = artistTagSeparators.toList(),
                        onChange = { newValues ->
                            coroutineScope.launch {
                                context.symphony.settings.updateData {
                                    it.copy {
                                        this.artistTagSeparators.clear()
                                        this.artistTagSeparators.addAll(newValues.toSet())
                                    }
                                }
                                refreshMediaLibrary(context.symphony)
                            }
                        },
                    )
                    HorizontalDivider()
                    SettingsMultiTextOptionTile(
                        context,
                        icon = {
                            Icon(Icons.Filled.SpaceBar, null)
                        },
                        title = {
                            Text(stringResource(R.string.GenreTagValueSeparators))
                        },
                        values = genreTagSeparators.toList(),
                        onChange = { newValues ->
                            coroutineScope.launch {
                                context.symphony.settings.updateData {
                                    it.copy {
                                        this.genreTagSeparators.clear()
                                        this.genreTagSeparators.addAll(newValues.toSet())
                                    }
                                }
                                refreshMediaLibrary(context.symphony)
                            }
                        },
                    )
                    HorizontalDivider()
                    SettingsOptionTile(
                        icon = {
                            Icon(Icons.Filled.Image, null)
                        },
                        title = {
                            Text(stringResource(R.string.ArtworkQuality))
                        },
                        value = artworkQuality,
                        values = ArtworkQuality.entries
                            .filter { it != ArtworkQuality.UNRECOGNIZED }
                            .associateWith { it.label(context) },
                        onChange = { value ->
                            coroutineScope.launch {
                                context.symphony.settings.updateData { it.copy { this.artworkQuality = value } }
                            }
                        }
                    )
                    HorizontalDivider()
                    SettingsSwitchTile(
                        icon = {
                            Icon(Icons.Filled.TextFields, null)
                        },
                        title = {
                            Text(stringResource(R.string.CaseSensitiveSorting))
                        },
                        value = caseSensitiveSorting,
                        onChange = { value ->
                            coroutineScope.launch {
                                context.symphony.settings.updateData { it.copy { this.caseSensitiveSorting = value } }
                            }
                        }
                    )
                    HorizontalDivider()
                    SettingsSwitchTile(
                        icon = {
                            Icon(Icons.Filled.FindInPage, null)
                        },
                        title = {
                            Text(stringResource(R.string.UseMetaphonyMetadataDecoder))
                        },
                        value = useMetaphony,
                        onChange = { value ->
                            coroutineScope.launch {
                                context.symphony.settings.updateData { it.copy { this.useMetaphony = value } }
                            }
                        }
                    )
                    HorizontalDivider()
                    SettingsSimpleTile(
                        icon = {
                            Icon(Icons.Filled.Storage, null)
                        },
                        title = {
                            Text(stringResource(R.string.ClearSongCache))
                        },
                        onClick = {
                            refreshMediaLibrary(context.symphony, true)
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(
                                    context.activity.getString(R.string.SongCacheCleared),
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
                        values = AlbumSortBy.entries
                            .filter { it != AlbumSortBy.UNRECOGNIZED }
                            .associateWith { it.label(context) },
                        onChange = { value ->
                            coroutineScope.launch {
                                context.symphony.settings.updateData { it.copy { uiArtistViewAlbumSortBy = uiArtistViewAlbumSortBy.copy { by = value } } }
                            }
                        }
                    )
                }
            }
        }
    )
}

fun ArtworkQuality.label(context: ViewContext) = when (this) {
    ArtworkQuality.ARTWORK_LOW -> context.activity.getString(R.string.Low)
    ArtworkQuality.ARTWORK_MEDIUM -> context.activity.getString(R.string.Medium)
    ArtworkQuality.ARTWORK_HIGH -> context.activity.getString(R.string.High)
    ArtworkQuality.ARTWORK_LOSELESS -> context.activity.getString(R.string.Loseless)
    ArtworkQuality.UNRECOGNIZED -> "???"
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
