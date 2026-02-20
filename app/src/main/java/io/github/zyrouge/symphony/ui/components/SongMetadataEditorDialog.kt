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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.zyrouge.symphony.services.groove.SONG_TAG_FIELDS
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
    val fieldValues: SnapshotStateList<String> = remember {
        SONG_TAG_FIELDS.map { it.getValue(song) }.toMutableStateList()
    }
    val coroutineScope = rememberCoroutineScope()

    ScaffoldDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Edit Metadata") },
        titleTrailing = {
            IconButton(
                onClick = {
                    coroutineScope.launch(Dispatchers.IO) {
                        val tags = SONG_TAG_FIELDS.mapIndexedNotNull { i, field ->
                            val value = fieldValues[i]
                            if (value.isNotBlank()) field.tagKey to value else null
                        }.toMap()
                        val fd = context.activity.contentResolver
                            .openFileDescriptor(song.uri, "rw")
                            ?.detachFd()
                            ?: return@launch
                        AudioMetadataParser.write(song.filename, fd, tags)
                        context.symphony.groove.fetchPaths(listOf(song.path))
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
                SONG_TAG_FIELDS.forEachIndexed { i, field ->
                    OutlinedTextField(
                        value = fieldValues[i],
                        onValueChange = { fieldValues[i] = it },
                        label = { Text(field.label) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
    )
}
