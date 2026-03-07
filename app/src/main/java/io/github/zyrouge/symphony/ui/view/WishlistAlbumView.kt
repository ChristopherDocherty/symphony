package io.github.zyrouge.symphony.ui.view

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import io.github.zyrouge.symphony.R
import io.github.zyrouge.symphony.ui.components.AddWishlistAlbumDialog
import io.github.zyrouge.symphony.ui.components.AnimatedNowPlayingBottomBar
import io.github.zyrouge.symphony.ui.components.ConfirmationDialog
import io.github.zyrouge.symphony.ui.components.GenericGrooveBanner
import io.github.zyrouge.symphony.ui.components.IconButtonPlaceholder
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
                    IconButtonPlaceholder()
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
                            options = { expanded, onDismissRequest ->
                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = onDismissRequest,
                                ) {
                                    DropdownMenuItem(
                                        leadingIcon = { Icon(Icons.Filled.Edit, null) },
                                        text = { Text(stringResource(R.string.EditWishlistAlbum)) },
                                        onClick = {
                                            onDismissRequest()
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
                                            onDismissRequest()
                                            showDeleteDialog = true
                                        },
                                    )
                                }
                            },
                            content = {
                                Column {
                                    Text(it.name)
                                    Text(
                                        it.artist,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                        ),
                                    )
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
                            },
                        )
                    }
                }
            }
        },
        bottomBar = {
            AnimatedNowPlayingBottomBar(context)
        },
    )

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
