package io.github.zyrouge.symphony.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.zyrouge.symphony.R
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MULTIPLE_VALUES_PLACEHOLDER = "(multiple values)"

@Composable
fun BulkWishlistEditDialog(
    context: ViewContext,
    albumIds: List<String>,
    onDismissRequest: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val albums = remember(albumIds) {
        albumIds.mapNotNull { context.symphony.groove.wishlist.get(it) }
    }

    val distinctArtists = remember(albums) { albums.map { it.artist }.distinct() }
    val distinctNames = remember(albums) { albums.map { it.name }.distinct() }
    val distinctYears = remember(albums) { albums.map { it.year?.toString() ?: "" }.distinct() }
    val distinctPriorities = remember(albums) {
        albums.map { it.priority.takeIf { p -> p >= 0 }?.toString() ?: "" }.distinct()
    }
    val distinctPending = remember(albums) { albums.map { it.pending }.distinct() }

    var artist by remember {
        mutableStateOf(if (distinctArtists.size == 1) distinctArtists[0] else "")
    }
    var albumName by remember {
        mutableStateOf(if (distinctNames.size == 1) distinctNames[0] else "")
    }
    var yearText by remember {
        mutableStateOf(if (distinctYears.size == 1) distinctYears[0] else "")
    }
    var priorityText by remember {
        mutableStateOf(if (distinctPriorities.size == 1) distinctPriorities[0] else "")
    }
    var pending by remember {
        mutableStateOf(if (distinctPending.size == 1) distinctPending[0] else false)
    }

    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    ScaffoldDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.BulkEditWishlist)) },
        content = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.Artist)) },
                    value = artist,
                    onValueChange = { artist = it },
                    placeholder = if (distinctArtists.size > 1) {
                        { Text(MULTIPLE_VALUES_PLACEHOLDER) }
                    } else null,
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.Album)) },
                    value = albumName,
                    onValueChange = { albumName = it },
                    placeholder = if (distinctNames.size > 1) {
                        { Text(MULTIPLE_VALUES_PLACEHOLDER) }
                    } else null,
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.Year)) },
                    value = yearText,
                    onValueChange = { yearText = it },
                    placeholder = if (distinctYears.size > 1) {
                        { Text(MULTIPLE_VALUES_PLACEHOLDER) }
                    } else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.Priority)) },
                    value = priorityText,
                    onValueChange = { priorityText = it },
                    placeholder = if (distinctPriorities.size > 1) {
                        { Text(MULTIPLE_VALUES_PLACEHOLDER) }
                    } else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.Pending),
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = pending,
                        onCheckedChange = { pending = it },
                    )
                }
                errorMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(msg, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        actions = {
            TextButton(onClick = onDismissRequest, enabled = !isSaving) {
                Text(stringResource(R.string.Cancel))
            }
            TextButton(
                enabled = !isSaving,
                onClick = {
                    isSaving = true
                    errorMessage = null
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            for (album in albums) {
                                context.symphony.groove.wishlist.update(
                                    id = album.id,
                                    artist = artist.takeIf { it.isNotBlank() } ?: album.artist,
                                    name = albumName.takeIf { it.isNotBlank() } ?: album.name,
                                    year = yearText.toIntOrNull() ?: album.year,
                                    priority = priorityText.toIntOrNull() ?: album.priority,
                                    pending = pending,
                                    artUrl = null,
                                )
                            }
                            withContext(Dispatchers.Main) {
                                onDismissRequest()
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                errorMessage = e.message ?: "Unknown error"
                                isSaving = false
                            }
                        }
                    }
                },
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.Done))
                }
            }
        },
    )
}
