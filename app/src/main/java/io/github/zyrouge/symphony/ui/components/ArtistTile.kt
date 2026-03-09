package io.github.zyrouge.symphony.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.zyrouge.symphony.AlbumFilter
import io.github.zyrouge.symphony.AlbumSortBy
import io.github.zyrouge.symphony.R
import io.github.zyrouge.symphony.services.groove.Artist
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import io.github.zyrouge.symphony.ui.view.ArtistViewRoute
import io.github.zyrouge.symphony.utils.escapeTextForLastFmUrl
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Composable
fun ArtistTile(context: ViewContext, artist: Artist, onClickOverride: (() -> Unit)? = null) {
    val scope = rememberCoroutineScope()
    val settings by context.symphony.settingsState.collectAsState()
    val minimalistMode = settings.albumTileMinimalistMode
    val scrobbles = if (!minimalistMode && settings.artistTileShowScrobbleCount) context.symphony.lastFm.getArtistScrobbleCount(artist.name) else 0L

    val albumFilter by context.symphony.settings.data
        .map { it.uiArtistViewAlbumFilter }
        .collectAsState(AlbumFilter.getDefaultInstance())
    val hiddenAlbumIds by context.symphony.settings.data
        .map { it.hiddenAlbumIdsList.toSet() }
        .collectAsState(emptySet())

    val filteredAlbumCount by remember(artist.name, albumFilter, hiddenAlbumIds) {
        derivedStateOf {
            val albumIds = context.symphony.groove.artist.getAlbumIds(artist.name)
            context.symphony.groove.album.getAlbums(albumIds, AlbumSortBy.ALBUM_NAME, false, albumFilter, hiddenAlbumIds).size
        }
    }
    val filteredTrackCount by remember(artist.name, albumFilter, hiddenAlbumIds) {
        derivedStateOf {
            val albumIds = context.symphony.groove.artist.getAlbumIds(artist.name)
            val filteredAlbumSet = context.symphony.groove.album.getAlbums(albumIds, AlbumSortBy.ALBUM_NAME, false, albumFilter, hiddenAlbumIds).toHashSet()
            context.symphony.groove.artist.getSongIds(artist.name)
                .count { songId ->
                    val song = context.symphony.groove.song.get(songId) ?: return@count true
                    val albumId = context.symphony.groove.album.getIdFromSong(song) ?: return@count true
                    albumId in filteredAlbumSet
                }
        }
    }

    SquareGrooveTile(
        image = artist.createArtworkImageRequest(context.symphony).build(),
        options = { expanded, onDismissRequest ->
            ArtistDropdownMenu(
                context,
                artist,
                expanded = expanded,
                onDismissRequest = onDismissRequest,
            )
        },
        showOptions = !minimalistMode,
        showContent = !minimalistMode,
        content = {
            if (settings.artistTileShowName) {
                Text(
                    artist.name,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (settings.artistTileShowAlbumCount) {
                Text(
                    stringResource(R.string.XAlbums, filteredAlbumCount.toString()),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (settings.artistTileShowTrackCount) {
                Text(
                    stringResource(R.string.XSongs, filteredTrackCount.toString()),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (scrobbles > 0) {
                Text(
                    stringResource(R.string.LastFmScrobbles, scrobbles.toString()),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        onPlay = {
            scope.launch {
                context.symphony.radio.shorty.playQueue(artist.getSortedSongIds(context.symphony))
            }
        },
        onClick = onClickOverride ?: {
            context.navController.navigate(ArtistViewRoute(artist.name))
        }
    )
}

@Composable
fun ArtistDropdownMenu(
    context: ViewContext,
    artist: Artist,
    expanded: Boolean,
    onDismissRequest: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
    ) {
        DropdownMenuItem(
            leadingIcon = {
                Icon(Icons.Filled.Shuffle, null)
            },
            text = {
                Text(stringResource(R.string.ShufflePlay))
            },
            onClick = {
                onDismissRequest()
                scope.launch {
                    context.symphony.radio.shorty.playQueue(
                        artist.getSortedSongIds(context.symphony),
                        shuffle = true
                    )
                }
            }
        )
        DropdownMenuItem(
            leadingIcon = {
                Icon(painter=painterResource(R.drawable.last_fm), "last.fm icon",
                    modifier = Modifier.size(24.dp))
            },
            text = {
                Text("Open on last.fm")
            },
            onClick = {
                onDismissRequest()
                uriHandler.openUri("https://last.fm/user/chrisd_99/library/music/${escapeTextForLastFmUrl( artist.name)}")
            }
        )
    }
}
