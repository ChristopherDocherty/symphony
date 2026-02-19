package io.github.zyrouge.symphony.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.zyrouge.symphony.services.groove.Song
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.zyrouge.symphony.metaphony.AudioMetadataParser

@Composable
fun SongMetadataEditorDialog(
    context: ViewContext,
    song: Song,
    onDismissRequest: () -> Unit,
) {
    var artist by remember { mutableStateOf(song.artists.joinToString(", ")) }
    var album by remember { mutableStateOf(song.album ?: "") }
    var genre by remember { mutableStateOf(song.genres.joinToString(", ")) }
    var year by remember { mutableStateOf(song.year?.toString() ?: "") }
    var trackNumber by remember { mutableStateOf(song.trackNumber?.toString() ?: "") }

    val coroutineScope = rememberCoroutineScope()

    ScaffoldDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Edit Metadata") },
        titleTrailing = {
            IconButton(
                onClick = {
                    coroutineScope.launch(Dispatchers.IO) {
                        val tags = buildMap {
                            put("ARTIST", artist)
                            put("ALBUM", album)
                            put("GENRE", genre)
                            if (year.isNotBlank()) put("DATE", year)
                            if (trackNumber.isNotBlank()) put("TRACKNUMBER", trackNumber)
                        }
                        val fd = context.activity.contentResolver
                            .openFileDescriptor(song.uri, "rw")
                            ?.detachFd()
                            ?: return@launch
                        AudioMetadataParser.write(song.filename, fd, tags)
                    }
                    onDismissRequest()
                }
            ) {
                Icon(Icons.Filled.Save, contentDescription = "Save metadata")
            }
        },
        content = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .padding(16.dp, 12.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text("Artist") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = album,
                    onValueChange = { album = it },
                    label = { Text("Album") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = genre,
                    onValueChange = { genre = it },
                    label = { Text("Genre") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = year,
                    onValueChange = { year = it },
                    label = { Text("Year") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = trackNumber,
                    onValueChange = { trackNumber = it },
                    label = { Text("Track Number") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}
