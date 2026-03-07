package io.github.zyrouge.symphony.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.zyrouge.symphony.GenreSortBy
import io.github.zyrouge.symphony.copy
import io.github.zyrouge.symphony.services.groove.Groove
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import io.github.zyrouge.symphony.ui.view.GenreViewRoute
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import io.github.zyrouge.symphony.R

private object GenreTile {
    val colors = listOf(
        0xFFEF4444,
        0xFFF97316,
        0xFFF59E0B,
        0xFF16A34A,
        0xFF06B6B4,
        0xFF8B5CF6,
        0xFFD946EF,
        0xFFF43F5E,
        0xFF6366F1,
        0xFFA855F7,
    ).map { Color(it) }

    fun colorAt(index: Int) = colors[index % colors.size]

    private val artworkReleaseTypePriority = listOf(
        "Studio Album",
        "EP",
        "Single",
        "Live Album",
        "Rarities/ B-Sides",
        "Greatest Hits",
        "Various Artists",
    )

    fun getCollageAlbumIds(context: ViewContext, genreName: String): List<String> {
        val symphony = context.symphony
        val songIds = symphony.groove.genre.getSongIds(genreName)
        val seen = mutableSetOf<String>()
        return songIds
            .mapNotNull { songId ->
                val song = symphony.groove.song.get(songId) ?: return@mapNotNull null
                symphony.groove.album.getIdFromSong(song)
            }
            .filter { seen.add(it) }
            .sortedBy { albumId ->
                val releaseTypes = symphony.groove.album.getCustomTagValues(albumId, "RELEASETYPE")
                releaseTypes
                    .mapNotNull { artworkReleaseTypePriority.indexOf(it).takeIf { i -> i >= 0 } }
                    .minOrNull() ?: Int.MAX_VALUE
            }
            .take(3)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenreGrid(
    context: ViewContext,
    genreNames: List<String>,
    genresCount: Int? = null,
) {
    val scope = rememberCoroutineScope()
    val settings by context.symphony.settingsState.collectAsState()
    val sortBy = settings.genresSortBy
    val sortReverse = settings.genresSortReverse
    val sortedGenreNames by remember(genreNames, sortBy, sortReverse) {
        derivedStateOf {
            context.symphony.groove.genre.sort(genreNames, sortBy, sortReverse)
        }
    }
    val horizontalGridColumns = settings.genresHorizontalGridColumns
    val verticalGridColumns = settings.genresVerticalGridColumns
    val gridColumns by remember(horizontalGridColumns, verticalGridColumns) {
        derivedStateOf {
            ResponsiveGridColumns(horizontalGridColumns, verticalGridColumns)
        }
    }
    var showModifyLayoutSheet by remember { mutableStateOf(false) }

    MediaSortBarScaffold(
        mediaSortBar = {
            Box(modifier = Modifier.padding(bottom = 4.dp)) {
                MediaSortBar(
                    context,
                    reverse = sortReverse,
                    onReverseChange = {
                        scope.launch {
                            context.symphony.settings.updateData { s ->
                                s.copy { genresSortReverse = it }
                            }
                        }
                    },
                    sort = sortBy,
                    sorts = GenreSortBy.entries
                        .filter { it != GenreSortBy.UNRECOGNIZED }
                        .associateWith { x -> ViewContext.parameterizedFn { x.label(it) } },
                    onSortChange = {
                        scope.launch {
                            context.symphony.settings.updateData { s ->
                                s.copy { genresSortBy = it }
                            }
                        }
                    },
                    label = {
                        Text(
                            stringResource(R.string.XGenres, 
                                (genresCount ?: genreNames.size).toString()
                            )
                        )
                    },
                    onShowModifyLayout = {
                        showModifyLayoutSheet = true
                    },
                )
            }
        },
        content = {
            when {
                genreNames.isEmpty() -> IconTextBody(
                    icon = { modifier ->
                        Icon(
                            Icons.Filled.MusicNote,
                            null,
                            modifier = modifier,
                        )
                    },
                    content = { Text(stringResource(R.string.DamnThisIsSoEmpty)) }
                )

                else -> ResponsiveGrid(gridColumns) { gridData ->
                    itemsIndexed(
                        sortedGenreNames,
                        key = { i, x -> "$i-$x" },
                        contentType = { _, _ -> Groove.Kind.GENRE }
                    ) { i, genreName ->
                        context.symphony.groove.genre.get(genreName)?.let { genre ->
                            val albumIds = remember(genre.name) {
                                GenreTile.getCollageAlbumIds(context, genre.name)
                            }
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight()
                                    .padding(
                                        start = if (i % gridData.columnsCount == 0) 12.dp else 0.dp,
                                        end = if ((i - 1) % gridData.columnsCount == 0) 12.dp else 8.dp,
                                        bottom = 8.dp,
                                    ),
                                colors = CardDefaults.cardColors(
                                    containerColor = GenreTile.colorAt(i),
                                    contentColor = Color.White,
                                ),
                                onClick = {
                                    context.navController.navigate(GenreViewRoute(genre.name))
                                }
                            ) {
                                Column {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(88.dp)
                                    ) {
                                        for (slot in 0 until 3) {
                                            val albumId = albumIds.getOrNull(slot)
                                            if (albumId != null) {
                                                AsyncImage(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .fillMaxHeight(),
                                                    model = context.symphony.groove.album
                                                        .createArtworkImageRequest(albumId).build(),
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                )
                                            } else {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Text(
                                            genre.name,
                                            textAlign = TextAlign.Center,
                                            style = MaterialTheme.typography.bodyMedium
                                                .copy(fontWeight = FontWeight.Bold),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            stringResource(R.string.XSongs, genre.numberOfTracks.toString()),
                                            textAlign = TextAlign.Center,
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (showModifyLayoutSheet) {
                ResponsiveGridSizeAdjustBottomSheet(
                    context,
                    columns = gridColumns,
                    onColumnsChange = { cols ->
                        scope.launch {
                            context.symphony.settings.updateData { s ->
                                s.copy {
                                    genresHorizontalGridColumns = cols.horizontal
                                    genresVerticalGridColumns = cols.vertical
                                }
                            }
                        }
                    },
                    onDismissRequest = {
                        showModifyLayoutSheet = false
                    }
                )
            }
        }
    )
}

private fun GenreSortBy.label(context: ViewContext) = when (this) {
    GenreSortBy.GENRE_SORT_CUSTOM -> context.activity.getString(R.string.Custom)
    GenreSortBy.GENRE_SORT_GENRE -> context.activity.getString(R.string.Genre)
    GenreSortBy.GENRE_SORT_TRACKS_COUNT -> context.activity.getString(R.string.TrackCount)
    else -> "???"
}
