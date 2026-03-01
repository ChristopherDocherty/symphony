package io.github.zyrouge.symphony.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
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
import androidx.compose.ui.unit.dp
import io.github.zyrouge.symphony.R
import io.github.zyrouge.symphony.services.groove.Album
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import io.github.zyrouge.symphony.utils.HttpClient
import io.github.zyrouge.symphony.utils.SimplePath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

private const val TAG = "CoverArt"

@Composable
fun AlbumCoverArtDialog(
    context: ViewContext,
    album: Album,
    onDismissRequest: () -> Unit,
) {
    var imageUrl by remember { mutableStateOf("") }
    var isDownloading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var copiedToClipboard by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current

    val firstSong = remember(album.id) {
        context.symphony.groove.album.getSongIds(album.id)
            .firstOrNull()
            ?.let { context.symphony.groove.song.get(it) }
    }

    val searchQuery = album.name

    ScaffoldDialog(
        onDismissRequest = { if (!isDownloading) onDismissRequest() },
        title = { Text(stringResource(R.string.UpdateCoverArt)) },
        content = {
            Column(
                modifier = Modifier
                    .padding(16.dp, 12.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(searchQuery))
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
                        stringResource(R.string.CoverSearchHint, searchQuery),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                OutlinedTextField(
                    value = imageUrl,
                    onValueChange = { imageUrl = it; errorMessage = null },
                    label = { Text(stringResource(R.string.ImageUrl)) },
                    placeholder = { Text("https://…") },
                    enabled = !isDownloading,
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
            TextButton(onClick = onDismissRequest, enabled = !isDownloading) {
                Text(stringResource(R.string.Cancel))
            }
            Button(
                onClick = {
                    val song = firstSong
                    if (song == null) {
                        errorMessage = "No songs found for this album"
                        return@Button
                    }
                    isDownloading = true
                    errorMessage = null
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val url = imageUrl.trim()
                            Log.d(TAG, "songPath=${song.path}, songUri=${song.uri}")

                            // Download image
                            Log.d(TAG, "Downloading from $url")
                            val req = Request.Builder().url(url).build()
                            val bytes = HttpClient.newCall(req).execute().use { resp ->
                                Log.d(TAG, "HTTP ${resp.code}, contentType=${resp.body?.contentType()}")
                                resp.body?.bytes() ?: error("Empty response body")
                            }
                            Log.d(TAG, "Downloaded ${bytes.size} bytes")
                            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                ?: error("Could not decode image")
                            Log.d(TAG, "Decoded bitmap ${bitmap.width}x${bitmap.height}")

                            // Resolve album directory using SAF — song.uri is a tree-based document URI
                            val songUri = song.uri
                            val authority = songUri.authority ?: error("Song URI has no authority")
                            val treeDocId = DocumentsContract.getTreeDocumentId(songUri)
                            val treeUri = DocumentsContract.buildTreeDocumentUri(authority, treeDocId)
                            val songDocId = DocumentsContract.getDocumentId(songUri)
                            val parentDocId = songDocId.substringBeforeLast("/")
                            Log.d(TAG, "authority=$authority treeDocId=$treeDocId songDocId=$songDocId parentDocId=$parentDocId")

                            val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId)

                            // Check if cover.jpg already exists in the directory
                            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
                            var existingCoverUri: Uri? = null
                            context.activity.contentResolver.query(
                                childrenUri,
                                arrayOf(
                                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                                ),
                                null, null, null,
                            )?.use { cursor ->
                                while (cursor.moveToNext()) {
                                    if (cursor.getString(1) == "cover.jpg") {
                                        val childId = cursor.getString(0)
                                        existingCoverUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                                        Log.d(TAG, "Found existing cover.jpg id=$childId uri=$existingCoverUri")
                                        break
                                    }
                                }
                            }

                            val coverUri = existingCoverUri ?: DocumentsContract.createDocument(
                                context.activity.contentResolver,
                                parentDocUri,
                                "image/jpeg",
                                "cover.jpg",
                            ) ?: error("Failed to create cover.jpg document")
                            Log.d(TAG, "Writing cover to $coverUri")

                            context.activity.contentResolver.openOutputStream(coverUri, "wt")?.use { fos ->
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos)
                            } ?: error("Failed to open output stream for cover.jpg")
                            Log.d(TAG, "Write successful")

                            // Cache key must match what SongRepository.getArtworkUri() uses
                            val cacheKey = SimplePath(song.path).parent?.pathString
                            Log.d(TAG, "Inserting directoryArtworkCache key=$cacheKey uri=$coverUri")
                            if (cacheKey != null) {
                                context.symphony.database.directoryArtworkCache.insert(cacheKey, coverUri)
                            }

                            val songPaths = context.symphony.groove.album.getSongIds(album.id)
                                .mapNotNull { context.symphony.groove.song.get(it)?.path }
                            Log.d(TAG, "Rescanning ${songPaths.size} song paths")
                            context.symphony.groove.fetchPaths(songPaths)

                            withContext(Dispatchers.Main) {
                                isDownloading = false
                                onDismissRequest()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed", e)
                            withContext(Dispatchers.Main) {
                                isDownloading = false
                                errorMessage = "Failed: ${e.message}"
                            }
                        }
                    }
                },
                enabled = !isDownloading && imageUrl.isNotBlank(),
            ) {
                if (isDownloading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Image, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.Apply))
                }
            }
        },
    )
}
