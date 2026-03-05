package io.github.zyrouge.symphony.ui.components

import android.provider.DocumentsContract
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.zyrouge.symphony.R
import io.github.zyrouge.symphony.copy
import io.github.zyrouge.symphony.services.groove.Groove
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import io.github.zyrouge.symphony.utils.FilenameTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RenameFromTagsDialog(
    context: ViewContext,
    songIds: List<String>,
    onDismissRequest: () -> Unit,
) {
    val songs = remember(songIds) {
        songIds.mapNotNull { context.symphony.groove.song.get(it) }
    }

    val savedTemplate by context.symphony.settings.data
        .map { it.renameFromTagsTemplate }
        .collectAsState(initial = "")

    var templateField by remember(savedTemplate) {
        mutableStateOf(TextFieldValue(savedTemplate))
    }

    var isRenaming by remember { mutableStateOf(false) }
    var renameProgress by remember { mutableIntStateOf(0) }
    val coroutineScope = rememberCoroutineScope()

    val previewRows = remember(templateField.text, songs) {
        songs.map { song ->
            val newBasename = FilenameTemplate.apply(templateField.text, song)
            val ext = song.filename.substringAfterLast('.', "")
            val newFilename = if (newBasename.isNotBlank() && ext.isNotEmpty()) "$newBasename.$ext"
            else if (newBasename.isNotBlank()) newBasename
            else ""
            song.filename to newFilename
        }
    }

    ScaffoldDialog(
        onDismissRequest = { if (!isRenaming) onDismissRequest() },
        title = { Text(stringResource(R.string.RenameXSongsFromTags, songs.size.toString())) },
        titleTrailing = {
            IconButton(
                enabled = !isRenaming && templateField.text.isNotBlank(),
                onClick = {
                    isRenaming = true
                    renameProgress = 0
                    val template = templateField.text
                    coroutineScope.launch(Dispatchers.IO) {
                        val contentResolver = context.activity.contentResolver
                        songs.forEachIndexed { index, song ->
                            val newBasename = FilenameTemplate.apply(template, song)
                                .takeIf { it.isNotBlank() } ?: run {
                                withContext(Dispatchers.Main) { renameProgress = index + 1 }
                                return@forEachIndexed
                            }
                            val ext = song.filename.substringAfterLast('.', "")
                            val newFilename = if (ext.isNotEmpty()) "$newBasename.$ext" else newBasename
                            if (newFilename == song.filename) {
                                withContext(Dispatchers.Main) { renameProgress = index + 1 }
                                return@forEachIndexed
                            }

                            DocumentsContract.renameDocument(
                                contentResolver, song.uri, newFilename
                            ) ?: run {
                                withContext(Dispatchers.Main) { renameProgress = index + 1 }
                                return@forEachIndexed
                            }

                            context.symphony.groove.exposer.uris.remove(song.path)

                            // Rename sidecar files by deriving their doc IDs from the audio doc ID.
                            // This works even when exposer.uris is empty (cache-only startup).
                            val audioDocId = DocumentsContract.getDocumentId(song.uri)
                            val audioStem = audioDocId.substringBeforeLast('.')
                            for (sidecarExt in listOf("lrc", "txt")) {
                                val sidecarDocId = "$audioStem.$sidecarExt"
                                val sidecarUri = DocumentsContract.buildDocumentUriUsingTree(
                                    song.uri, sidecarDocId
                                )
                                val renamed = try {
                                    DocumentsContract.renameDocument(
                                        contentResolver, sidecarUri, "$newBasename.$sidecarExt"
                                    )
                                } catch (_: Exception) {
                                    null
                                }
                                if (renamed != null) {
                                    val oldSidecarPath =
                                        song.path.substringBeforeLast('.') + ".$sidecarExt"
                                    context.symphony.groove.exposer.uris.remove(oldSidecarPath)
                                }
                            }

                            withContext(Dispatchers.Main) { renameProgress = index + 1 }
                        }

                        context.symphony.settings.updateData {
                            it.copy { renameFromTagsTemplate = template }
                        }
                        context.symphony.groove.fetch(Groove.FetchOptions())

                        withContext(Dispatchers.Main) {
                            isRenaming = false
                            onDismissRequest()
                        }
                    }
                },
            ) {
                if (isRenaming) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = stringResource(R.string.RenameFromTags))
                }
            }
        },
        topBar = if (isRenaming) {
            {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    LinearProgressIndicator(
                        progress = { renameProgress.toFloat() / songs.size.coerceAtLeast(1) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        stringResource(R.string.RenamingProgress, renameProgress.toString(), songs.size.toString()),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        } else null,
        content = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
            ) {
                item {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        OutlinedTextField(
                            value = templateField,
                            onValueChange = { if (!isRenaming) templateField = it },
                            label = { Text(stringResource(R.string.RenameTemplate)) },
                            placeholder = { Text("%tracknumber% - %title%") },
                            enabled = !isRenaming,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy((-4).dp),
                        ) {
                            FilenameTemplate.PLACEHOLDERS.keys.forEach { placeholder ->
                                SuggestionChip(
                                    enabled = !isRenaming,
                                    onClick = {
                                        val current = templateField
                                        val insertion = placeholder
                                        val newText = current.text.substring(0, current.selection.end) +
                                                insertion +
                                                current.text.substring(current.selection.end)
                                        val newCursor = current.selection.end + insertion.length
                                        templateField = TextFieldValue(
                                            text = newText,
                                            selection = TextRange(newCursor),
                                        )
                                    },
                                    label = { Text(placeholder, style = MaterialTheme.typography.labelSmall) },
                                )
                            }
                        }
                    }
                }
                items(previewRows) { (oldName, newName) ->
                    val unchanged = newName.isBlank() || newName == oldName
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        Text(
                            oldName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(
                                alpha = if (unchanged) 0.4f else 1f
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "→",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        )
                        Text(
                            newName.ifBlank { oldName },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(
                                alpha = if (unchanged) 0.4f else 1f
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        },
        contentHeight = 1f,
    )
}
