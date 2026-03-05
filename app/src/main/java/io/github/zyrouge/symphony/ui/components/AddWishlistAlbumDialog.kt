package io.github.zyrouge.symphony.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.zyrouge.symphony.R
import io.github.zyrouge.symphony.copy
import io.github.zyrouge.symphony.services.groove.WishlistAlbum
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import io.github.zyrouge.symphony.utils.ActivityUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AddWishlistAlbumDialog(
    context: ViewContext,
    existingAlbum: WishlistAlbum?,
    onDismissRequest: () -> Unit,
) {
    val isEdit = existingAlbum != null
    val coroutineScope = rememberCoroutineScope()
    val settings by context.symphony.settingsState.collectAsState()

    var artist by remember { mutableStateOf(existingAlbum?.artist ?: "") }
    var albumName by remember { mutableStateOf(existingAlbum?.name ?: "") }
    var yearText by remember { mutableStateOf(existingAlbum?.year?.toString() ?: "") }
    var priorityText by remember { mutableStateOf(existingAlbum?.priority?.toString() ?: "0") }
    var artUrl by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var copiedToClipboard by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current

    val dirPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            ActivityUtils.makePersistableReadWriteUri(context.activity, it)
            coroutineScope.launch {
                context.symphony.settings.updateData { s ->
                    s.copy { wishlistDir = it.toString() }
                }
            }
        }
    }

    val isNoDirConfigured = settings.wishlistDir.isBlank()
    val canConfirm = !isSaving && artist.isNotBlank() && albumName.isNotBlank() && !isNoDirConfigured

    ScaffoldDialog(
        onDismissRequest = { if (!isSaving) onDismissRequest() },
        title = {
            Text(
                if (isEdit) stringResource(R.string.EditWishlistAlbum)
                else stringResource(R.string.AddToWishlist)
            )
        },
        content = {
            Column(
                modifier = Modifier
                    .padding(16.dp, 12.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (isNoDirConfigured) {
                    Text(
                        stringResource(R.string.WishlistNoDirConfigured),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(
                        onClick = { dirPicker.launch(null) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.ConfigureDirectory))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it; errorMessage = null },
                    label = { Text(stringResource(R.string.Artist)) },
                    enabled = !isSaving,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = albumName,
                    onValueChange = { albumName = it; errorMessage = null },
                    label = { Text(stringResource(R.string.Album)) },
                    enabled = !isSaving,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = yearText,
                    onValueChange = { yearText = it.filter { c -> c.isDigit() }; errorMessage = null },
                    label = { Text(stringResource(R.string.Year)) },
                    enabled = !isSaving,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = priorityText,
                    onValueChange = { priorityText = it.filter { c -> c.isDigit() }; errorMessage = null },
                    label = { Text(stringResource(R.string.Priority)) },
                    enabled = !isSaving,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(albumName))
                        copiedToClipboard = true
                        uriHandler.openUri("https://covers.musichoarders.xyz/")
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Search, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.SearchOnCovers))
                }

                if (copiedToClipboard) {
                    Text(
                        stringResource(R.string.CoverSearchHint, albumName),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                OutlinedTextField(
                    value = artUrl,
                    onValueChange = { artUrl = it; errorMessage = null },
                    label = { Text(stringResource(R.string.WishlistAlbumArtUrl)) },
                    placeholder = { Text("https://…") },
                    enabled = !isSaving,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                errorMessage?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        actions = {
            TextButton(onClick = onDismissRequest, enabled = !isSaving) {
                Text(stringResource(R.string.Cancel))
            }
            Button(
                onClick = {
                    if (isNoDirConfigured) {
                        dirPicker.launch(null)
                        return@Button
                    }
                    val year = yearText.toIntOrNull()
                    val priority = priorityText.toIntOrNull() ?: 0
                    isSaving = true
                    errorMessage = null
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            if (isEdit) {
                                context.symphony.groove.wishlist.update(
                                    existingAlbum.id, artist, albumName, year, priority,
                                    artUrl.trim().takeIf { it.isNotBlank() },
                                )
                            } else {
                                context.symphony.groove.wishlist.add(
                                    artist, albumName, year, priority,
                                    artUrl.trim().takeIf { it.isNotBlank() },
                                )
                            }
                            withContext(Dispatchers.Main) {
                                isSaving = false
                                onDismissRequest()
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                isSaving = false
                                errorMessage = e.localizedMessage ?: e.toString()
                            }
                        }
                    }
                },
                enabled = canConfirm,
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.Done))
                }
            }
        },
    )
}
