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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.zyrouge.symphony.AlbumFilter
import io.github.zyrouge.symphony.AlbumSortBy
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
import androidx.compose.ui.res.stringResource
import io.github.zyrouge.symphony.R

@Serializable
data class ArtistViewRoute(val artistName: String, val bypassAlbumFilter: Boolean = false)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistView(context: ViewContext, route: ArtistViewRoute) {
    val allArtistNames by context.symphony.groove.artist.all.collectAsState()
    val allSongIds by context.symphony.groove.song.all.collectAsState()
    val allAlbumIds by context.symphony.groove.album.all.collectAsState()
    val hiddenAlbumIds by context.symphony.settings.data
        .map { it.hiddenAlbumIdsList.toSet() }
        .collectAsState(emptySet())
    val artist by remember(allArtistNames) {
        derivedStateOf { context.symphony.groove.artist.get(route.artistName) }
    }
    val rawSongIds by remember(artist, allSongIds) {
        derivedStateOf { artist?.getSongIds(context.symphony) ?: listOf() }
    }
    val rawAlbumIds by remember(artist, allAlbumIds) {
        derivedStateOf { artist?.getAlbumIds(context.symphony) ?: listOf() }
    }
    val hiddenSongIds by remember(hiddenAlbumIds) {
        derivedStateOf { context.symphony.groove.album.getHiddenSongIds(hiddenAlbumIds) }
    }
    val songIds by remember(rawSongIds, hiddenSongIds) {
        derivedStateOf {
            if (hiddenSongIds.isEmpty()) rawSongIds
            else rawSongIds.filterNot { it in hiddenSongIds }
        }
    }
    val albumIds by remember(rawAlbumIds, hiddenAlbumIds) {
        derivedStateOf {
            if (hiddenAlbumIds.isEmpty()) rawAlbumIds
            else rawAlbumIds.filterNot { it in hiddenAlbumIds }
        }
    }
    val albumFilter by context.symphony.settings.data
        .map { if (route.bypassAlbumFilter) AlbumFilter.getDefaultInstance() else it.uiArtistViewAlbumFilter }
        .collectAsState(AlbumFilter.getDefaultInstance())
    val filteredAlbumIds by remember(albumIds, albumFilter) {
        derivedStateOf {
            context.symphony.groove.album.getAlbums(
                albumIds = albumIds,
                by = AlbumSortBy.ALBUM_CUSTOM,
                reverse = false,
                filter = albumFilter,
            )
        }
    }
    val filteredSongIds by remember(songIds, filteredAlbumIds) {
        derivedStateOf {
            val filteredAlbumSet = filteredAlbumIds.toHashSet()
            songIds.filter { songId ->
                val song = context.symphony.groove.song.get(songId) ?: return@filter true
                val albumId = context.symphony.groove.album.getIdFromSong(song) ?: return@filter true
                albumId in filteredAlbumSet
            }
        }
    }
    val isViable by remember(allArtistNames) {
        derivedStateOf { allArtistNames.contains(route.artistName) }
    }
    val songPageState = remember { SongsPageState(context) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = { context.navController.popBackStack() }
                    ) {
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
                        songIds = filteredSongIds,
                        leadingContent = {
                            item {
                                ArtistHero(context, artist!!)
                            }
                            if (albumIds.isNotEmpty()) {
                                item {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    AlbumRow(context, albumIds, bypassFilter = route.bypassAlbumFilter)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    HorizontalDivider()
                                }
                            }
                        },
                        pageState = songPageState,
                    )
                } else UnknownArtist(context, route.artistName)
            }
        },
        bottomBar = {
            AnimatedNowPlayingBottomBar(context)
        }
    )
}

@Composable
private fun ArtistHero(context: ViewContext, artist: Artist) {
    val settings by context.symphony.settingsState.collectAsState()
    GenericGrooveBanner(
        image = artist.createArtworkImageRequest(context.symphony).build(),
        options = { expanded, onDismissRequest ->
            ArtistDropdownMenu(
                context,
                artist,
                expanded = expanded,
                onDismissRequest = onDismissRequest
            )
        },
        showOverlay = !settings.albumTileMinimalistMode,
        content = {
            Text(artist.name)
        }
    )
}

@Composable
private fun UnknownArtist(context: ViewContext, artistName: String) {
    IconTextBody(
        icon = { modifier ->
            Icon(
                Icons.Filled.PriorityHigh,
                null,
                modifier = modifier
            )
        },
        content = {
            Text(stringResource(R.string.UnknownArtistX, artistName))
        }
    )
}
