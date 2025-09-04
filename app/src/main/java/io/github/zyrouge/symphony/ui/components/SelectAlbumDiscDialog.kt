package io.github.zyrouge.symphony.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.zyrouge.symphony.services.groove.Album
import io.github.zyrouge.symphony.services.groove.Song
import io.github.zyrouge.symphony.ui.helpers.ViewContext

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
        val allSongIds = album.getSortedSongIds(context.symphony)
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
            Text("Select Discs")
        },
        text = {
            if (isLoading) {
                Text("Loading discs...")
            } else if (discNumbersForDisplay.isNotEmpty()) {
                LazyColumn {
                    items(discNumbersForDisplay) { discNum ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .toggleable(
                                    value = discNum in selectedDiscNumbers,
                                    onValueChange = { checked ->
                                        selectedDiscNumbers = if (checked) {
                                            selectedDiscNumbers + discNum
                                        } else {
                                            selectedDiscNumbers - discNum
                                        }
                                    },
                                    role = Role.Checkbox
                                )
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = discNum in selectedDiscNumbers,
                                onCheckedChange = null
                            )
                            Text(
                                text = "Disc $discNum",
                                modifier = Modifier.padding(start = 16.dp)
                            )
                        }
                    }
                }
            } else {
                Text("No disc information available")
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
                Text(context.symphony.t.Play)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(context.symphony.t.Cancel)
            }
        }
    )
}
