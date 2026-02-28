package io.github.zyrouge.symphony.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.zyrouge.symphony.services.groove.SONG_TAG_FIELDS
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.zyrouge.symphony.metaphony.AudioMetadataParser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkSongEditDialog(
    context: ViewContext,
    songIds: List<String>,
    onDismissRequest: () -> Unit,
) {
    val songs = remember(songIds) {
        songIds.mapNotNull { context.symphony.groove.song.get(it) }
    }

    val fieldDistincts: List<List<String>> = remember(songs) {
        SONG_TAG_FIELDS.map { field -> songs.map { field.getValue(it) }.distinct() }
    }

    val fieldValues: SnapshotStateList<String> = remember(songs) {
        fieldDistincts.map { distinct ->
            if (distinct.size == 1) distinct.first() else ""
        }.toMutableStateList()
    }

    val fieldPlaceholders: List<String> = remember(songs) {
        fieldDistincts.map { distinct ->
            if (distinct.size == 1) "" else "(multiple values)"
        }
    }

    var expandedFieldIndex by remember { mutableStateOf(-1) }

    var isSaving by remember { mutableStateOf(false) }
    var saveProgress by remember { mutableIntStateOf(0) }
    val coroutineScope = rememberCoroutineScope()

    ScaffoldDialog(
        onDismissRequest = { if (!isSaving) onDismissRequest() },
        title = { Text("Edit ${songs.size} songs") },
        titleTrailing = {
            IconButton(
                enabled = !isSaving,
                onClick = {
                    isSaving = true
                    saveProgress = 0
                    coroutineScope.launch(Dispatchers.IO) {
                        songs.forEachIndexed { index, song ->
                            val tags = SONG_TAG_FIELDS.mapIndexedNotNull { i, field ->
                                val value = fieldValues[i].trim()
                                if (value.isBlank()) null else field.tagKey to value
                            }.toMap()
                            if (tags.isNotEmpty()) {
                                val fd = context.activity.contentResolver
                                    .openFileDescriptor(song.uri, "rw")
                                    ?.detachFd()
                                    ?: return@forEachIndexed
                                AudioMetadataParser.write(song.filename, fd, tags)
                            }
                            withContext(Dispatchers.Main) {
                                saveProgress = index + 1
                            }
                        }
                        context.symphony.groove.fetchPaths(songs.map { it.path })
                        withContext(Dispatchers.Main) {
                            isSaving = false
                            onDismissRequest()
                        }
                    }
                }
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Save, contentDescription = "Save metadata")
                }
            }
        },
        topBar = if (isSaving) {
            {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    LinearProgressIndicator(
                        progress = { saveProgress.toFloat() / songs.size.coerceAtLeast(1) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Writing $saveProgress of ${songs.size}…",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        } else null,
        content = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .padding(16.dp, 12.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                SONG_TAG_FIELDS.forEachIndexed { i, field ->
                    val distinctValues = fieldDistincts[i]
                    val hasDropdown = distinctValues.size in 2..5
                    if (hasDropdown) {
                        ExposedDropdownMenuBox(
                            expanded = expandedFieldIndex == i,
                            onExpandedChange = {
                                if (!isSaving) expandedFieldIndex = if (it) i else -1
                            },
                        ) {
                            OutlinedTextField(
                                value = fieldValues[i],
                                onValueChange = { if (!isSaving) fieldValues[i] = it },
                                label = { Text(field.label) },
                                placeholder = if (fieldPlaceholders[i].isNotEmpty()) {
                                    { Text(fieldPlaceholders[i]) }
                                } else null,
                                enabled = !isSaving,
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedFieldIndex == i)
                                },
                                modifier = Modifier
                                    .menuAnchor(MenuAnchorType.PrimaryEditable)
                                    .fillMaxWidth(),
                            )
                            ExposedDropdownMenu(
                                expanded = expandedFieldIndex == i,
                                onDismissRequest = { expandedFieldIndex = -1 },
                            ) {
                                distinctValues.forEach { value ->
                                    DropdownMenuItem(
                                        text = { Text(value.ifBlank { "(empty)" }) },
                                        onClick = {
                                            fieldValues[i] = value
                                            expandedFieldIndex = -1
                                        },
                                    )
                                }
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = fieldValues[i],
                            onValueChange = { if (!isSaving) fieldValues[i] = it },
                            label = { Text(field.label) },
                            placeholder = if (fieldPlaceholders[i].isNotEmpty()) {
                                { Text(fieldPlaceholders[i]) }
                            } else null,
                            enabled = !isSaving,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
    )
}
