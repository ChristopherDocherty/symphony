package io.github.zyrouge.symphony.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.zyrouge.symphony.R
import io.github.zyrouge.symphony.services.groove.WishlistListing
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AddWishlistListingDialog(
    context: ViewContext,
    albumId: String,
    existingListing: WishlistListing?,
    existingIndex: Int?,
    onDismissRequest: () -> Unit,
) {
    val isEdit = existingListing != null
    val coroutineScope = rememberCoroutineScope()

    var url by remember { mutableStateOf(existingListing?.url ?: "") }
    var priceText by remember { mutableStateOf(existingListing?.price?.let { "%.2f".format(it) } ?: "") }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val canConfirm = !isSaving && url.isNotBlank() && priceText.toDoubleOrNull() != null

    ScaffoldDialog(
        onDismissRequest = { if (!isSaving) onDismissRequest() },
        title = {
            Text(
                if (isEdit) stringResource(R.string.EditListing)
                else stringResource(R.string.AddListing)
            )
        },
        content = {
            Column(
                modifier = Modifier
                    .padding(16.dp, 12.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it; errorMessage = null },
                    label = { Text(stringResource(R.string.ListingUrl)) },
                    placeholder = { Text("https://…") },
                    enabled = !isSaving,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it; errorMessage = null },
                    label = { Text(stringResource(R.string.Price)) },
                    enabled = !isSaving,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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
                    val price = priceText.toDoubleOrNull() ?: return@Button
                    isSaving = true
                    errorMessage = null
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val album = context.symphony.groove.wishlist.get(albumId)
                            if (album != null) {
                                val newListing = WishlistListing(url.trim(), price)
                                val updatedListings = album.listings.toMutableList()
                                if (isEdit && existingIndex != null) {
                                    updatedListings[existingIndex] = newListing
                                } else {
                                    updatedListings.add(newListing)
                                }
                                context.symphony.groove.wishlist.updateListings(albumId, updatedListings)
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
