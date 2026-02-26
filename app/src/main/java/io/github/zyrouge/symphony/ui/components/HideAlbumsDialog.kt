package io.github.zyrouge.symphony.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun HideAlbumsDialog(
    count: Int,
    isHide: Boolean,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val action = if (isHide) "Hide" else "Unhide"
    val body = if (isHide)
        "Hide $count album(s) from all browse views?"
    else
        "Unhide $count album(s)?"

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("$action albums") },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismissRequest()
            }) {
                Text(action)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        },
    )
}
