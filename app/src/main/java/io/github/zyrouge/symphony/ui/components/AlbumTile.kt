package io.github.zyrouge.symphony.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Album // Added import
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import io.github.zyrouge.symphony.R
import io.github.zyrouge.symphony.services.groove.Album
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import io.github.zyrouge.symphony.ui.view.AlbumViewRoute
import io.github.zyrouge.symphony.ui.view.ArtistViewRoute
import io.github.zyrouge.symphony.ui.components.SelectAlbumDiscDialog // Added import
import io.github.zyrouge.symphony.ui.view.home.AlbumsPageState
import io.github.zyrouge.symphony.utils.escapeTextForLastFmUrl
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource

@Composable
fun AlbumTile(context: ViewContext, album: Album, pageState: AlbumsPageState? = null) {
    val scope = rememberCoroutineScope()
    val isMultiSelectMode = pageState?.isMultiSelectMode == true
    val isSelected = pageState?.selectedAlbumIds?.contains(album.id) == true

    SquareGrooveTile(
        image = album.createArtworkImageRequest(context.symphony).build(),
        options = { expanded, onDismissRequest ->
            AlbumDropdownMenu(
                context,
                album,
                expanded = expanded,
                onDismissRequest = onDismissRequest,
            )
        },
        content = {
            Text(
                album.name,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (album.artists.isNotEmpty()) {
                Text(
                    album.artists.joinToString(),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        onPlay = {
            scope.launch {
                context.symphony.radio.shorty.playQueue(album.getSortedSongIds(context.symphony))
            }
        },
        onClick = {
            if (isMultiSelectMode) {
                pageState?.toggleSelection(album.id)
            } else {
                context.navController.navigate(AlbumViewRoute(album.id))
            }
        },
        onLongClick = {
            if (!isMultiSelectMode) {
                pageState?.enterMultiSelect(album.id)
            }
        },
        isSelected = isSelected,
        isMultiSelectMode = isMultiSelectMode,
    )
}

@Composable
fun AlbumDropdownMenu(
    context: ViewContext,
    album: Album,
    expanded: Boolean,
    onDismissRequest: () -> Unit,
) {
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var showSelectDiscDialog by remember { mutableStateOf(false) }
    var showEnqueueSubmenu by remember { mutableStateOf(false) }
    var showLastFmSubmenu by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    val scope = rememberCoroutineScope()
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = {
            showEnqueueSubmenu = false
            showLastFmSubmenu = false
            onDismissRequest()
        }
    ) {
        if (showEnqueueSubmenu) {
            DropdownMenuItem(
                leadingIcon = {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                },
                text = { Text("Enqueue") },
                onClick = { showEnqueueSubmenu = false }
            )
            HorizontalDivider()
            DropdownMenuItem(
                leadingIcon = {
                    Icon(Icons.Filled.Shuffle, null)
                },
                text = { Text(stringResource(R.string.ShufflePlay)) },
                onClick = {
                    onDismissRequest()
                    scope.launch {
                        context.symphony.radio.shorty.playQueue(
                            album.getSortedSongIds(context.symphony),
                            shuffle = true,
                        )
                    }
                }
            )
            DropdownMenuItem(
                leadingIcon = {
                    Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null)
                },
                text = { Text(stringResource(R.string.PlayNext)) },
                onClick = {
                    onDismissRequest()
                    scope.launch {
                        context.symphony.radio.queue.add(
                            album.getSortedSongIds(context.symphony),
                            context.symphony.radio.queue.currentSongIndex + 1
                        )
                    }
                }
            )
            DropdownMenuItem(
                leadingIcon = {
                    Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null)
                },
                text = { Text(stringResource(R.string.AddToQueue)) },
                onClick = {
                    onDismissRequest()
                    scope.launch {
                        context.symphony.radio.queue.add(album.getSortedSongIds(context.symphony))
                    }
                }
            )
            DropdownMenuItem(
                leadingIcon = {
                    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null)
                },
                text = { Text(stringResource(R.string.AddToPlaylist)) },
                onClick = {
                    onDismissRequest()
                    showAddToPlaylistDialog = true
                }
            )
        } else if (showLastFmSubmenu) {
            DropdownMenuItem(
                leadingIcon = {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                },
                text = { Text("Last.fm") },
                onClick = { showLastFmSubmenu = false }
            )
            HorizontalDivider()
            DropdownMenuItem(
                leadingIcon = {
                    Icon(Icons.Filled.Album, null)
                },
                text = { Text("Album") },
                onClick = {
                    onDismissRequest()
                    uriHandler.openUri("https://last.fm/user/chrisd_99/library/music/${escapeTextForLastFmUrl(album.artists.first())}/${escapeTextForLastFmUrl(album.name)}")
                }
            )
            album.artists.forEach { artistName ->
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(Icons.Filled.Person, null)
                    },
                    text = { Text("Artist: $artistName") },
                    onClick = {
                        onDismissRequest()
                        uriHandler.openUri("https://last.fm/user/chrisd_99/library/music/${escapeTextForLastFmUrl(artistName)}")
                    }
                )
            }
        } else {
            DropdownMenuItem(
                leadingIcon = {
                    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null)
                },
                text = { Text("Enqueue") },
                trailingIcon = {
                    Icon(Icons.Filled.KeyboardArrowRight, null)
                },
                onClick = { showEnqueueSubmenu = true }
            )
            DropdownMenuItem(
                leadingIcon = {
                    Icon(Icons.Filled.Album, null)
                },
                text = {
                    Text("Play from disc...")
                },
                onClick = {
                    onDismissRequest()
                    showSelectDiscDialog = true
                }
            )
            album.artists.forEach { artistName ->
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(Icons.Filled.Person, null)
                    },
                    text = {
                        Text("${stringResource(R.string.ViewArtist)}: $artistName")
                    },
                    onClick = {
                        onDismissRequest()
                        context.navController.navigate(ArtistViewRoute(artistName))
                    }
                )
            }
            DropdownMenuItem(
                leadingIcon = {
                    Icon(painter=painterResource(R.drawable.last_fm), "last.fm icon")
                },
                text = { Text("Last.fm") },
                trailingIcon = {
                    Icon(Icons.Filled.KeyboardArrowRight, null)
                },
                onClick = { showLastFmSubmenu = true }
            )
        }
    }

    if (showAddToPlaylistDialog) {
        AddToPlaylistDialog(
            context,
            songIds = album.getSongIds(context.symphony),
            onDismissRequest = {
                showAddToPlaylistDialog = false
            }
        )
    }

    if (showSelectDiscDialog) {
        SelectAlbumDiscDialog(
            context = context,
            album = album,
            onDismissRequest = {
                showSelectDiscDialog = false
            }
        )
    }
}
