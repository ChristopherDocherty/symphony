package io.github.zyrouge.symphony.ui.view

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import io.github.zyrouge.symphony.R
import io.github.zyrouge.symphony.services.groove.Groove
import io.github.zyrouge.symphony.ui.components.AnimatedNowPlayingBottomBar
import io.github.zyrouge.symphony.ui.components.ArtistTile
import io.github.zyrouge.symphony.ui.components.IconButtonPlaceholder
import io.github.zyrouge.symphony.ui.components.IconTextBody
import io.github.zyrouge.symphony.ui.components.ResponsiveGrid
import io.github.zyrouge.symphony.ui.components.ResponsiveGridColumns
import io.github.zyrouge.symphony.ui.components.TopAppBarMinimalTitle
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

@Serializable
data class GenreViewRoute(val genreName: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenreView(context: ViewContext, route: GenreViewRoute) {
    val allGenreNames by context.symphony.groove.genre.all.collectAsState()
    val allSongIds by context.symphony.groove.song.all.collectAsState()
    val hiddenAlbumIds by context.symphony.settings.data
        .map { it.hiddenAlbumIdsList.toSet() }
        .collectAsState(emptySet())
    val settings by context.symphony.settingsState.collectAsState()
    val genre by remember(allGenreNames) {
        derivedStateOf { context.symphony.groove.genre.get(route.genreName) }
    }
    val rawSongIds by remember(genre, allSongIds) {
        derivedStateOf { genre?.getSongIds(context.symphony) ?: listOf() }
    }
    val songIds by remember(rawSongIds, hiddenAlbumIds) {
        derivedStateOf {
            if (hiddenAlbumIds.isEmpty()) rawSongIds
            else {
                val hiddenSongIds = context.symphony.groove.album.getHiddenSongIds(hiddenAlbumIds)
                rawSongIds.filterNot { it in hiddenSongIds }
            }
        }
    }
    val artistNames by remember(songIds, settings.minArtistTrackCount) {
        derivedStateOf {
            val names = songIds.flatMap { songId ->
                context.symphony.groove.song.get(songId)?.artists ?: emptySet()
            }.distinct().sorted()
            context.symphony.groove.artist.filterByTrackCount(names, settings.minArtistTrackCount)
        }
    }
    val gridColumns by remember(settings.artistsHorizontalGridColumns, settings.artistsVerticalGridColumns) {
        derivedStateOf {
            ResponsiveGridColumns(settings.artistsHorizontalGridColumns, settings.artistsVerticalGridColumns)
        }
    }
    val isViable by remember(allGenreNames) {
        derivedStateOf { allGenreNames.contains(route.genreName) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = { context.navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                title = {
                    TopAppBarMinimalTitle {
                        Text(stringResource(R.string.Genre) + (genre?.let { " - ${it.name}" } ?: ""))
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                ),
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
                when {
                    isViable -> ResponsiveGrid(gridColumns) {
                        itemsIndexed(
                            artistNames,
                            key = { i, x -> "$i-$x" },
                            contentType = { _, _ -> Groove.Kind.ARTIST }
                        ) { _, name ->
                            context.symphony.groove.artist.get(name)?.let { artist ->
                                ArtistTile(
                                    context,
                                    artist,
                                    onClickOverride = {
                                        context.navController.navigate(
                                            GenreArtistViewRoute(name, route.genreName)
                                        )
                                    }
                                )
                            }
                        }
                    }
                    else -> UnknownGenre(context, route.genreName)
                }
            }
        },
        bottomBar = {
            AnimatedNowPlayingBottomBar(context)
        }
    )
}

@Composable
private fun UnknownGenre(context: ViewContext, genre: String) {
    IconTextBody(
        icon = { modifier ->
            Icon(Icons.Filled.Tune, null, modifier = modifier)
        },
        content = {
            Text(stringResource(R.string.UnknownGenreX, genre))
        }
    )
}
