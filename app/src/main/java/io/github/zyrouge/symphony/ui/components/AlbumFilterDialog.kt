package io.github.zyrouge.symphony.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.zyrouge.symphony.AlbumFilter
import io.github.zyrouge.symphony.Settings
import io.github.zyrouge.symphony.SongSortBy
import io.github.zyrouge.symphony.services.groove.Album
import io.github.zyrouge.symphony.services.groove.Song
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import kotlinx.coroutines.flow.map

@Composable
fun AlbumFilterDialog(
    context: ViewContext,
    onDismissRequest: () -> Unit,
) {
    var isLoading by remember { mutableStateOf(true) }

    val releaseTypes by context.symphony.settings.data.map { it.uiAlbumGridAlbumFilter.releaseTypeList}.collectAsState(emptyList<String>())

//    LaunchedEffect() {
//        isLoading = true
//        isLoading = false
//    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text("Album Filter")
        },
        text = {
            if (isLoading) {
                Text("Loading filters...")
            } else {
                LazyColumn {
                    items(5) { discNum ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = false,
                                onCheckedChange = null
                            )
                            Text(
                                text = "Disc $discNum",
                                modifier = Modifier.padding(start = 16.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                },
                enabled = true,
            ) {
                Text("apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        }
    )
}
