package io.github.zyrouge.symphony.ui.view

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PriorityHigh
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.zyrouge.symphony.R
import io.github.zyrouge.symphony.services.groove.Artist
import io.github.zyrouge.symphony.ui.components.AlbumRow
import io.github.zyrouge.symphony.ui.components.AnimatedNowPlayingBottomBar
import io.github.zyrouge.symphony.ui.components.ArtistDropdownMenu
import io.github.zyrouge.symphony.ui.components.GenericGrooveBanner
import io.github.zyrouge.symphony.ui.components.IconButtonPlaceholder
import io.github.zyrouge.symphony.ui.components.IconTextBody
import io.github.zyrouge.symphony.ui.components.SongList
import io.github.zyrouge.symphony.ui.components.TopAppBarMinimalTitle
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import io.github.zyrouge.symphony.ui.view.home.SongsPageState
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

@Serializable
data class GenreArtistViewRoute(val artistName: String, val genreName: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenreArtistView(context: ViewContext, route: GenreArtistViewRoute) {
    val allArtistNames by context.symphony.groove.artist.all.collectAsState()
    val allGenreNames by context.symphony.groove.genre.all.collectAsState()
    val allSongIds by context.symphony.groove.song.all.collectAsState()
    val hiddenAlbumIds by context.symphony.settings.data
        .map { it.hiddenAlbumIdsList.toSet() }
        .collectAsState(emptySet())
    val artist by remember(allArtistNames) {
        derivedStateOf { context.symphony.groove.artist.get(route.artistName) }
    }
    val genre by remember(allGenreNames) {
        derivedStateOf { context.symphony.groove.genre.get(route.genreName) }
    }
    val songIds by remember(artist, genre, allSongIds, hiddenAlbumIds) {
        derivedStateOf {
            val genreSongIds = genre?.getSongIds(context.symphony)?.toHashSet()
                ?: return@derivedStateOf listOf()
            val artistSongIds = artist?.getSongIds(context.symphony)
                ?: return@derivedStateOf listOf()
            val hiddenSongIds = if (hiddenAlbumIds.isEmpty()) emptySet()
                else context.symphony.groove.album.getHiddenSongIds(hiddenAlbumIds)
            artistSongIds.filter { it in genreSongIds && it !in hiddenSongIds }
        }
    }
    val albumIds by remember(songIds, hiddenAlbumIds) {
        derivedStateOf {
            songIds.mapNotNull { songId ->
                context.symphony.groove.song.get(songId)?.let { song ->
                    context.symphony.groove.album.getIdFromSong(song)
                }
            }.distinct().filterNot { it in hiddenAlbumIds }
        }
    }
    val isViable by remember(allArtistNames) {
        derivedStateOf { allArtistNames.contains(route.artistName) }
    }
    val songPageState = remember { SongsPageState() }

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
                        Text(
                            stringResource(R.string.Artist) + (artist?.let { " - ${it.name}" } ?: ""),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
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
                if (isViable) {
                    SongList(
                        context,
                        songIds = songIds,
                        leadingContent = {
                            item {
                                GenreArtistHero(context, artist!!)
                            }
                            if (albumIds.isNotEmpty()) {
                                item {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    AlbumRow(context, albumIds)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    HorizontalDivider()
                                }
                            }
                        },
                        pageState = songPageState,
                    )
                } else {
                    IconTextBody(
                        icon = { modifier ->
                            Icon(Icons.Filled.PriorityHigh, null, modifier = modifier)
                        },
                        content = {
                            Text(stringResource(R.string.UnknownArtistX, route.artistName))
                        }
                    )
                }
            }
        },
        bottomBar = {
            AnimatedNowPlayingBottomBar(context)
        }
    )
}

@Composable
private fun GenreArtistHero(context: ViewContext, artist: Artist) {
    val settings by context.symphony.settingsState.collectAsState()
    GenericGrooveBanner(
        image = artist.createArtworkImageRequest(context.symphony).build(),
        options = { expanded, onDismissRequest ->
            ArtistDropdownMenu(
                context,
                artist,
                expanded = expanded,
                onDismissRequest = onDismissRequest,
            )
        },
        showOverlay = !settings.albumTileMinimalistMode,
        content = {
            Text(artist.name)
        }
    )
}
