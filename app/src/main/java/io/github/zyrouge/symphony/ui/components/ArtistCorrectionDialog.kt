package io.github.zyrouge.symphony.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.zyrouge.symphony.R

@Composable
fun ArtistCorrectionDialog(
    localName: String,
    canonicalName: String,
    onDismiss: () -> Unit,
    onFix: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ArtistCorrectionTitle)) },
        text = {
            Text(stringResource(R.string.ArtistCorrectionBody, canonicalName, localName))
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onFix()
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.ArtistCorrectionFix))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.Cancel))
            }
        },
    )
}
