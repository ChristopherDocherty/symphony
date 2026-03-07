package io.github.zyrouge.symphony.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.memory.MemoryCache
import io.github.zyrouge.symphony.R
import io.github.zyrouge.symphony.services.groove.Album
import io.github.zyrouge.symphony.services.groove.WishlistAlbum
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
    var showWishlistPicker by remember { mutableStateOf(false) }
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
                    onClick = { showWishlistPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isDownloading,
                ) {
                    Icon(Icons.Filled.Bookmarks, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.PickFromWishlist))
                }

                HorizontalDivider()

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

                            val maxBytes = 1_572_864 // 1.5 MB
                            val bos = ByteArrayOutputStream()
                            var quality = 100
                            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, bos)
                            while (bos.size() > maxBytes && quality > 10) {
                                bos.reset()
                                quality -= 5
                                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, bos)
                            }
                            Log.d(TAG, "Saving cover at quality=$quality, size=${bos.size()} bytes")
                            context.activity.contentResolver.openOutputStream(coverUri, "wt")?.use { fos ->
                                fos.write(bos.toByteArray())
                            } ?: error("Failed to open output stream for cover.jpg")
                            Log.d(TAG, "Write successful")

                            // Invalidate Coil's cache for this URI so the new image is shown immediately
                            context.activity.imageLoader.memoryCache?.remove(MemoryCache.Key(coverUri.toString()))
                            context.activity.imageLoader.diskCache?.remove(coverUri.toString())

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

    if (showWishlistPicker) {
        WishlistArtPickerDialog(
            context = context,
            onDismissRequest = { showWishlistPicker = false },
            onPick = { wishlistAlbum ->
                showWishlistPicker = false
                isDownloading = true
                errorMessage = null
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val song = firstSong ?: error("No songs found for this album")

                        val bytes = context.activity.contentResolver
                            .openInputStream(wishlistAlbum.artworkUri!!)?.readBytes()
                            ?: error("Could not read wishlist artwork")

                        val songUri = song.uri
                        val authority = songUri.authority ?: error("Song URI has no authority")
                        val treeDocId = DocumentsContract.getTreeDocumentId(songUri)
                        val treeUri = DocumentsContract.buildTreeDocumentUri(authority, treeDocId)
                        val songDocId = DocumentsContract.getDocumentId(songUri)
                        val parentDocId = songDocId.substringBeforeLast("/")
                        val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId)

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
                                    existingCoverUri = DocumentsContract.buildDocumentUriUsingTree(
                                        treeUri, cursor.getString(0)
                                    )
                                    break
                                }
                            }
                        }

                        val coverUri = existingCoverUri ?: DocumentsContract.createDocument(
                            context.activity.contentResolver, parentDocUri, "image/jpeg", "cover.jpg",
                        ) ?: error("Failed to create cover.jpg document")

                        context.activity.contentResolver.openOutputStream(coverUri, "wt")?.use {
                            it.write(bytes)
                        } ?: error("Failed to open output stream for cover.jpg")

                        // Invalidate Coil's cache for this URI so the new image is shown immediately
                        context.activity.imageLoader.memoryCache?.remove(MemoryCache.Key(coverUri.toString()))
                        context.activity.imageLoader.diskCache?.remove(coverUri.toString())

                        val cacheKey = SimplePath(song.path).parent?.pathString
                        if (cacheKey != null) {
                            context.symphony.database.directoryArtworkCache.insert(cacheKey, coverUri)
                        }
                        val songPaths = context.symphony.groove.album.getSongIds(album.id)
                            .mapNotNull { context.symphony.groove.song.get(it)?.path }
                        context.symphony.groove.fetchPaths(songPaths)

                        withContext(Dispatchers.Main) {
                            isDownloading = false
                            onDismissRequest()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to copy wishlist artwork", e)
                        withContext(Dispatchers.Main) {
                            isDownloading = false
                            errorMessage = "Failed: ${e.message}"
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun WishlistArtPickerDialog(
    context: ViewContext,
    onDismissRequest: () -> Unit,
    onPick: (WishlistAlbum) -> Unit,
) {
    val allIds by context.symphony.groove.wishlist.all.collectAsState()
    val updateId by context.symphony.groove.wishlist.updateId.collectAsState()
    val albums = remember(allIds, updateId) {
        allIds.mapNotNull { context.symphony.groove.wishlist.get(it) }
            .filter { it.artworkUri != null }
    }

    ScaffoldDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.PickFromWishlist)) },
        content = {
            if (albums.isEmpty()) {
                Box(modifier = Modifier.padding(16.dp)) {
                    IconTextBody(
                        icon = { modifier ->
                            Icon(Icons.Filled.Bookmarks, null, modifier = modifier)
                        },
                        content = { Text(stringResource(R.string.NoWishlistArtwork)) },
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.padding(8.dp),
                ) {
                    items(albums, key = { it.id }) { album ->
                        Column(
                            modifier = Modifier
                                .padding(4.dp)
                                .clickable { onPick(album) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            AsyncImage(
                                remember(updateId, album.id) {
                                    context.symphony.groove.wishlist.createArtworkImageRequest(album.id)
                                },
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp)),
                            )
                            Spacer(Modifier.size(4.dp))
                            Text(
                                album.name,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                album.artist,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        },
        actions = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.Cancel))
            }
        },
    )
}
