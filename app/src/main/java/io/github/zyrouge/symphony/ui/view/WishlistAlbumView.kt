package io.github.zyrouge.symphony.ui.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import android.net.Uri
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import io.github.zyrouge.symphony.R
import io.github.zyrouge.symphony.ui.components.AddWishlistAlbumDialog
import io.github.zyrouge.symphony.ui.components.AddWishlistListingDialog
import io.github.zyrouge.symphony.ui.components.AnimatedNowPlayingBottomBar
import io.github.zyrouge.symphony.ui.components.ConfirmationDialog
import io.github.zyrouge.symphony.ui.components.GenericGrooveBanner
import io.github.zyrouge.symphony.ui.components.TopAppBarMinimalTitle
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import io.github.zyrouge.symphony.ui.theme.ThemeColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
data class WishlistAlbumViewRoute(val albumId: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishlistAlbumView(context: ViewContext, route: WishlistAlbumViewRoute) {
    val coroutineScope = rememberCoroutineScope()
    val updateId by context.symphony.groove.wishlist.updateId.collectAsState()
    val album = remember(updateId) { context.symphony.groove.wishlist.get(route.albumId) }

    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }
    var showAddListingDialog by remember { mutableStateOf(false) }
    var editListingIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(album) {
        if (album == null) context.navController.popBackStack()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = { context.navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                title = {
                    TopAppBarMinimalTitle {
                        Text(album?.name ?: "")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent,
                ),
                actions = {
                    Box {
                        IconButton(onClick = { showOptionsMenu = true }) {
                            Icon(Icons.Filled.MoreVert, null)
                        }
                        DropdownMenu(
                            expanded = showOptionsMenu,
                            onDismissRequest = { showOptionsMenu = false },
                        ) {
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Filled.Edit, null) },
                                text = { Text(stringResource(R.string.EditWishlistAlbum)) },
                                onClick = {
                                    showOptionsMenu = false
                                    showEditDialog = true
                                },
                            )
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.Delete,
                                        null,
                                        tint = ThemeColors.Red,
                                    )
                                },
                                text = { Text(stringResource(R.string.DeleteWishlistAlbum)) },
                                onClick = {
                                    showOptionsMenu = false
                                    showDeleteDialog = true
                                },
                            )
                        }
                    }
                },
            )
        },
        content = { contentPadding ->
            Box(
                modifier = Modifier
                    .padding(contentPadding)
                    .fillMaxSize()
            ) {
                album?.let {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        GenericGrooveBanner(
                            image = remember(updateId, it.id) {
                                context.symphony.groove.wishlist.createArtworkImageRequest(it.id)
                            },
                            options = { _, _ -> },
                            content = {},
                            showOverlay = false,
                        )
                        Column(modifier = Modifier.padding(20.dp, 16.dp, 20.dp, 16.dp)) {
                            Text(
                                it.name,
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                            Text(
                                it.artist,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                            if (it.pending) {
                                Text(
                                    stringResource(R.string.Pending),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            } else {
                                val meta = buildString {
                                    it.year?.let { y -> append(y.toString()) }
                                    if (it.priority >= 0) {
                                        if (isNotEmpty()) append(" · ")
                                        append("P${it.priority}")
                                    }
                                }
                                if (meta.isNotEmpty()) {
                                    Text(
                                        meta,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.WhereToBuy),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                            TextButton(onClick = { showAddListingDialog = true }) {
                                Icon(
                                    Icons.Filled.Add,
                                    null,
                                    modifier = Modifier.padding(end = 4.dp),
                                )
                                Text(stringResource(R.string.AddListing))
                            }
                        }

                        val uriHandler = LocalUriHandler.current
                        val sortedListings = remember(updateId, it.id) {
                            it.listings.mapIndexed { index, listing -> index to listing }
                                .sortedBy { (_, l) -> l.price }
                        }

                        if (sortedListings.isEmpty()) {
                            Text(
                                stringResource(R.string.NoListings),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                            )
                        } else {
                            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                                sortedListings.forEach { (originalIndex, listing) ->
                                    val siteName = remember(listing.url) {
                                        try {
                                            Uri.parse(listing.url).host?.removePrefix("www.") ?: listing.url
                                        } catch (e: Exception) {
                                            listing.url
                                        }
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            TextButton(
                                                onClick = { uriHandler.openUri(listing.url) },
                                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                                            ) {
                                                Text(
                                                    siteName,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                )
                                            }
                                            Text(
                                                "£%.2f".format(listing.price),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        IconButton(onClick = { editListingIndex = originalIndex }) {
                                            Icon(Icons.Filled.Edit, null)
                                        }
                                        IconButton(
                                            onClick = {
                                                val updated = it.listings.toMutableList()
                                                updated.removeAt(originalIndex)
                                                coroutineScope.launch(Dispatchers.IO) {
                                                    context.symphony.groove.wishlist.updateListings(it.id, updated)
                                                }
                                            }
                                        ) {
                                            Icon(Icons.Filled.Delete, null, tint = ThemeColors.Red)
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        },
        bottomBar = {
            AnimatedNowPlayingBottomBar(context)
        },
    )

    if (showAddListingDialog) {
        album?.let {
            AddWishlistListingDialog(
                context = context,
                albumId = it.id,
                existingListing = null,
                existingIndex = null,
                onDismissRequest = { showAddListingDialog = false },
            )
        }
    }

    editListingIndex?.let { idx ->
        album?.listings?.getOrNull(idx)?.let { listing ->
            AddWishlistListingDialog(
                context = context,
                albumId = album.id,
                existingListing = listing,
                existingIndex = idx,
                onDismissRequest = { editListingIndex = null },
            )
        }
    }

    if (showEditDialog) {
        album?.let {
            AddWishlistAlbumDialog(
                context = context,
                existingAlbum = it,
                onDismissRequest = { showEditDialog = false },
            )
        }
    }

    if (showDeleteDialog) {
        album?.let {
            ConfirmationDialog(
                context = context,
                title = { Text(stringResource(R.string.DeleteWishlistAlbum)) },
                description = { Text("${it.artist} – ${it.name}") },
                onResult = { confirmed ->
                    showDeleteDialog = false
                    if (confirmed) {
                        coroutineScope.launch(Dispatchers.IO) {
                            context.symphony.groove.wishlist.delete(it.id)
                            withContext(Dispatchers.Main) {
                                context.navController.popBackStack()
                            }
                        }
                    }
                },
            )
        }
    }
}
