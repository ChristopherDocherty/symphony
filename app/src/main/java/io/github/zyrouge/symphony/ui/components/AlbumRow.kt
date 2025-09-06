package io.github.zyrouge.symphony.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import io.github.zyrouge.symphony.AlbumSortBy
import io.github.zyrouge.symphony.services.groove.Groove
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import kotlinx.coroutines.flow.map

@Composable
fun AlbumRow(context: ViewContext, albumIds: List<String>) {
    BoxWithConstraints {
        val maxSize = min(
            this@BoxWithConstraints.maxHeight,
            this@BoxWithConstraints.maxWidth,
        ).div(2f)
        val width = min(maxSize, 200.dp)

        val sortBy by context.symphony.settings.data.map { it.uiArtistViewAlbumSortBy.by }.collectAsState(
            AlbumSortBy.ALBUM_YEAR)
        val sortReverse by context.symphony.settings.data.map { it.uiArtistViewAlbumSortBy.reverse}.collectAsState(false)
        val sortedAlbumIds = remember(albumIds, sortBy, sortReverse) {
            context.symphony.groove.album.sort(
                albumIds = albumIds,
                by = sortBy,
                reverse = sortReverse
            )
        }

        LazyRow {
            itemsIndexed(
                sortedAlbumIds, // Use sorted list here
                key = { i, x -> "$i-$x" },
                contentType = { _, _ -> Groove.Kind.ALBUM }
            ) { _, albumId ->
                context.symphony.groove.album.get(albumId)?.let { album ->
                    Box(modifier = Modifier.width(width)) {
                        AlbumTile(context, album)
                    }
                }
            }
        }
    }
}

