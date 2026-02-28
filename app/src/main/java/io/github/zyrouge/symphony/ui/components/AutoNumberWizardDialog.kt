package io.github.zyrouge.symphony.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.zyrouge.symphony.services.groove.Groove
import io.github.zyrouge.symphony.services.groove.Song
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.zyrouge.symphony.metaphony.AudioMetadataParser
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun AutoNumberWizardDialog(
    context: ViewContext,
    songIds: List<String>,
    onDismissRequest: () -> Unit,
) {
    data class Entry(val key: String, val song: Song)

    val orderedSongs = remember(songIds) {
        songIds.mapNotNull { context.symphony.groove.song.get(it) }
            .mapIndexed { i, song -> Entry("$i-${song.id}", song) }
            .toMutableStateList()
    }

    var showNumberingDialog by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var saveProgress by remember { mutableIntStateOf(0) }
    val coroutineScope = rememberCoroutineScope()

    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        orderedSongs.apply { add(to.index, removeAt(from.index)) }
    }

    ScaffoldDialog(
        onDismissRequest = { if (!isSaving) onDismissRequest() },
        title = { Text("Autonumber ${orderedSongs.size} songs") },
        titleTrailing = {
            IconButton(
                onClick = { showNumberingDialog = true },
                enabled = !isSaving,
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.ArrowForward, contentDescription = "Set numbering")
                }
            }
        },
        topBar = if (isSaving) {
            {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    LinearProgressIndicator(
                        progress = { saveProgress.toFloat() / orderedSongs.size.coerceAtLeast(1) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Writing $saveProgress of ${orderedSongs.size}…",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        } else null,
        content = {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(
                    items = orderedSongs,
                    key = { _, entry -> entry.key },
                    contentType = { _, _ -> Groove.Kind.SONG },
                ) { i, entry ->
                    ReorderableItem(reorderState, key = entry.key) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        ) {
                            Text(
                                "${i + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.width(28.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    entry.song.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (entry.song.artists.isNotEmpty()) {
                                    Text(
                                        entry.song.artists.joinToString(),
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            Icon(
                                Icons.Filled.DragHandle,
                                contentDescription = null,
                                modifier = Modifier
                                    .draggableHandle()
                                    .padding(start = 8.dp)
                                    .size(24.dp),
                            )
                        }
                    }
                }
            }
        },
        contentHeight = 1f,
    )

    if (showNumberingDialog) {
        AutoNumberSettingsDialog(
            onDismissRequest = { showNumberingDialog = false },
            onConfirm = { startNumber, discNumber ->
                showNumberingDialog = false
                isSaving = true
                saveProgress = 0
                val songs = orderedSongs.map { it.song }
                coroutineScope.launch(Dispatchers.IO) {
                    songs.forEachIndexed { index, song ->
                        val tags = buildMap {
                            put("TRACKNUMBER", (startNumber + index).toString())
                            if (discNumber != null) put("DISCNUMBER", discNumber.toString())
                        }
                        val fd = context.activity.contentResolver
                            .openFileDescriptor(song.uri, "rw")
                            ?.detachFd()
                            ?: return@forEachIndexed
                        AudioMetadataParser.write(song.filename, fd, tags)
                        withContext(Dispatchers.Main) { saveProgress = index + 1 }
                    }
                    context.symphony.groove.fetchPaths(songs.map { it.path })
                    withContext(Dispatchers.Main) {
                        isSaving = false
                        onDismissRequest()
                    }
                }
            },
        )
    }
}

@Composable
private fun AutoNumberSettingsDialog(
    onDismissRequest: () -> Unit,
    onConfirm: (startNumber: Int, discNumber: Int?) -> Unit,
) {
    var startNumberText by remember { mutableStateOf("1") }
    var discNumberText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Configure numbering") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = startNumberText,
                    onValueChange = { startNumberText = it },
                    label = { Text("Starting track number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = discNumberText,
                    onValueChange = { discNumberText = it },
                    label = { Text("Disc number (optional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val start = startNumberText.trim().toIntOrNull() ?: 1
                val disc = discNumberText.trim().toIntOrNull()
                onConfirm(start, disc)
            }) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        },
    )
}
