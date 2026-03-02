package io.github.zyrouge.symphony.ui.components

import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.zyrouge.symphony.R
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import io.github.zyrouge.symphony.utils.SimplePath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val ART_MANAGER_TAG = "ArtManager"

private data class AlbumArtItem(
    val docId: String,
    val uri: Uri,
    val name: String,
    val size: Long?,
)

@Composable
fun AlbumArtManagerDialog(
    context: ViewContext,
    albumId: String,
    onDismissRequest: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<AlbumArtItem>?>(null) }
    var treeUri by remember { mutableStateOf<Uri?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val firstSong = remember(albumId) {
        context.symphony.groove.album.getSongIds(albumId)
            .firstOrNull()
            ?.let { context.symphony.groove.song.get(it) }
    }

    fun loadItems() {
        val song = firstSong ?: run {
            error = "No songs found for this album"
            return
        }
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val songUri = song.uri
                val authority = songUri.authority ?: error("No authority on song URI")
                val treeDocId = DocumentsContract.getTreeDocumentId(songUri)
                val tree = DocumentsContract.buildTreeDocumentUri(authority, treeDocId)
                val songDocId = DocumentsContract.getDocumentId(songUri)
                val parentDocId = songDocId.substringBeforeLast("/")

                withContext(Dispatchers.Main) { treeUri = tree }

                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentDocId)
                val result = mutableListOf<AlbumArtItem>()
                context.activity.contentResolver.query(
                    childrenUri,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_SIZE,
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                    ),
                    null, null, null,
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val mimeType = cursor.getString(3) ?: continue
                        if (!mimeType.startsWith("image/")) continue
                        val docId = cursor.getString(0)
                        val name = cursor.getString(1)
                        val size = if (cursor.isNull(2)) null else cursor.getLong(2)
                        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, docId)
                        result.add(AlbumArtItem(docId, uri, name, size))
                    }
                }
                withContext(Dispatchers.Main) {
                    items = result.sortedBy { it.name }
                }
            } catch (e: Exception) {
                Log.e(ART_MANAGER_TAG, "Failed to load images", e)
                withContext(Dispatchers.Main) {
                    error = e.message
                }
            }
        }
    }

    LaunchedEffect(albumId) { loadItems() }

    fun deleteItem(item: AlbumArtItem) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                DocumentsContract.deleteDocument(context.activity.contentResolver, item.uri)
                val cacheKey = firstSong?.let { SimplePath(it.path).parent?.pathString }
                if (cacheKey != null) {
                    val cachedUri = context.symphony.database.directoryArtworkCache.get(cacheKey)
                    if (cachedUri == item.uri) {
                        context.symphony.database.directoryArtworkCache.delete(listOf(cacheKey))
                    }
                }
                val songPaths = context.symphony.groove.album.getSongIds(albumId)
                    .mapNotNull { context.symphony.groove.song.get(it)?.path }
                context.symphony.groove.fetchPaths(songPaths)
                withContext(Dispatchers.Main) {
                    items = items?.filter { it.docId != item.docId }
                }
            } catch (e: Exception) {
                Log.e(ART_MANAGER_TAG, "Delete failed", e)
            }
        }
    }

    ScaffoldDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.ManageCoverArt)) },
        content = {
            when {
                error != null -> Box(Modifier.padding(16.dp)) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                }
                items == null -> Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
                items!!.isEmpty() -> Box(Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.NoImagesFound),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> LazyColumn {
                    items(items!!, key = { it.docId }) { item ->
                        ArtFileRow(item, onDelete = { deleteItem(item) })
                        HorizontalDivider()
                    }
                }
            }
        },
        actions = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.Done))
            }
        },
    )
}

@Composable
private fun ArtFileRow(
    item: AlbumArtItem,
    onDelete: () -> Unit,
) {
    var showConfirm by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(
            model = item.uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(72.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
            )
            item.size?.let { bytes ->
                Text(
                    formatBytes(bytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = { showConfirm = true }) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text(stringResource(R.string.DeleteImage)) },
            text = { Text(stringResource(R.string.DeleteImageConfirm, item.name)) },
            confirmButton = {
                TextButton(onClick = { showConfirm = false; onDelete() }) {
                    Text(stringResource(R.string.Delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) {
                    Text(stringResource(R.string.Cancel))
                }
            },
        )
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1_024 -> "%.1f KB".format(bytes / 1_024.0)
    else -> "$bytes B"
}
