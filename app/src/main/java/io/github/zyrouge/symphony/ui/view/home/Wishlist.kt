package io.github.zyrouge.symphony.ui.view.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.zyrouge.symphony.R
import io.github.zyrouge.symphony.ui.components.AddWishlistAlbumDialog
import io.github.zyrouge.symphony.ui.components.LoaderScaffold
import io.github.zyrouge.symphony.ui.components.WishlistGrid
import io.github.zyrouge.symphony.ui.helpers.ViewContext

class WishlistPageState : HomePageState {
    var showAddDialog by mutableStateOf(false)

    @Composable
    override fun DropdownItems() {
        // No dropdown items needed; FAB handles add
    }

    @Composable
    override fun Dialogs(context: ViewContext) {
        if (showAddDialog) {
            AddWishlistAlbumDialog(
                context = context,
                existingAlbum = null,
                onDismissRequest = { showAddDialog = false },
            )
        }
    }
}

@Composable
fun WishlistView(context: ViewContext, pageState: WishlistPageState? = null) {
    val isUpdating by context.symphony.groove.wishlist.isUpdating.collectAsState()
    val albumIds by context.symphony.groove.wishlist.all.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        LoaderScaffold(context, isLoading = isUpdating) {
            WishlistGrid(context, albumIds)
        }
        ExtendedFloatingActionButton(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            onClick = { pageState?.showAddDialog = true },
            icon = { Icon(Icons.Filled.Add, null) },
            text = { Text(stringResource(R.string.AddToWishlist)) },
        )
    }
}
