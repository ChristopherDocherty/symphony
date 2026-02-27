package io.github.zyrouge.symphony.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.zyrouge.symphony.R
import io.github.zyrouge.symphony.SongSortBy
import io.github.zyrouge.symphony.services.groove.Album
import io.github.zyrouge.symphony.services.groove.Song
import io.github.zyrouge.symphony.ui.helpers.ViewContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SelectAlbumDiscDialog(
    context: ViewContext,
    album: Album,
    onDismissRequest: () -> Unit,
) {
    var albumSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var discNumbersForDisplay by remember { mutableStateOf<List<Int>>(emptyList()) }
    var selectedDiscNumbers by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(album) {
        isLoading = true
        val allSongIds = album.getSortedSongIds(context.symphony, SongSortBy.SONG_TRACK_NUMBER, false)
        val fetchedSongs = context.symphony.groove.song.get(allSongIds)
        albumSongs = fetchedSongs
        discNumbersForDisplay = fetchedSongs
            .mapNotNull { it.discNumber }
            .distinct()
            .sorted()
        isLoading = false
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(stringResource(R.string.SelectDiscs))
        },
        text = {
            when {
                isLoading -> Text(stringResource(R.string.LoadingDiscs))
                discNumbersForDisplay.isNotEmpty() -> {
                    FlowRow(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        discNumbersForDisplay.forEach { discNum ->
                            val selected = discNum in selectedDiscNumbers
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    selectedDiscNumbers = if (selected) {
                                        selectedDiscNumbers - discNum
                                    } else {
                                        selectedDiscNumbers + discNum
                                    }
                                },
                                label = { Text(stringResource(R.string.DiscX, discNum)) },
                                leadingIcon = {
                                    if (selected) {
                                        Icon(Icons.Default.Check, contentDescription = null)
                                    } else {
                                        Icon(Icons.Default.Album, contentDescription = null)
                                    }
                                },
                            )
                        }
                    }
                }
                else -> Text(stringResource(R.string.NoDiscInformation))
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val songIdsToPlay = albumSongs
                        .filter { it.discNumber != null && it.discNumber in selectedDiscNumbers }
                        .map { it.id }
                    if (songIdsToPlay.isNotEmpty()) {
                        context.symphony.radio.shorty.playQueue(songIdsToPlay)
                    }
                    onDismissRequest()
                },
                enabled = selectedDiscNumbers.isNotEmpty() || discNumbersForDisplay.isEmpty()
            ) {
                Text(stringResource(R.string.Play))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.Cancel))
            }
        },
    )
}
