package io.github.zyrouge.symphony.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Album // Added import
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoLibrary
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
import androidx.compose.runtime.collectAsState
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
    val settings by context.symphony.settingsState.collectAsState()
    val minimalistMode = settings.albumTileMinimalistMode
    val scrobbles = if (!minimalistMode && settings.albumTileShowScrobbleCount) context.symphony.lastFm.getAlbumScrobbleCount(album.id) else 0L

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
        showOptions = !minimalistMode,
        showContent = !minimalistMode,
        content = {
            if (settings.albumTileShowName) {
                Text(
                    album.name,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (settings.albumTileShowArtist && album.artists.isNotEmpty()) {
                val artistLabel = if (album.albumArtists.contains("Various Artists")) {
                    "Various Artists"
                } else {
                    album.artists.joinToString()
                }
                Text(
                    artistLabel,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
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
            val showYear = settings.albumTileShowReleaseYear
            val showMonth = settings.albumTileShowReleaseMonth
            if (showYear || showMonth) {
                val year = album.date?.year ?: album.startYear
                val month = album.date?.monthValue
                val dateStr = when {
                    showYear && showMonth && year != null && month != null ->
                        "%02d/%d".format(month, year)
                    showYear && year != null -> year.toString()
                    showMonth && month != null -> "%02d".format(month)
                    else -> null
                }
                if (dateStr != null) {
                    Text(
                        dateStr,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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
    var showCoverArtDialog by remember { mutableStateOf(false) }
    var showArtManagerDialog by remember { mutableStateOf(false) }
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
                    Icon(Icons.Filled.Image, null)
                },
                text = { Text(stringResource(R.string.UpdateCoverArt)) },
                onClick = {
                    onDismissRequest()
                    showCoverArtDialog = true
                }
            )
            DropdownMenuItem(
                leadingIcon = {
                    Icon(Icons.Filled.PhotoLibrary, null)
                },
                text = { Text(stringResource(R.string.ManageCoverArt)) },
                onClick = {
                    onDismissRequest()
                    showArtManagerDialog = true
                }
            )
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

    if (showCoverArtDialog) {
        AlbumCoverArtDialog(
            context = context,
            album = album,
            onDismissRequest = {
                showCoverArtDialog = false
            }
        )
    }

    if (showArtManagerDialog) {
        AlbumArtManagerDialog(
            context = context,
            albumId = album.id,
            onDismissRequest = {
                showArtManagerDialog = false
            }
        )
    }
}
