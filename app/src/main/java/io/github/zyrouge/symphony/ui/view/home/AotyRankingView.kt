package io.github.zyrouge.symphony.ui.view.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.zyrouge.symphony.AlbumFilter
import io.github.zyrouge.symphony.AlbumSortBy
import io.github.zyrouge.symphony.R
import io.github.zyrouge.symphony.services.groove.Song
import io.github.zyrouge.symphony.ui.components.GroupSectionHeader
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.zyrouge.symphony.metaphony.AudioMetadataParser
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private sealed interface AotyFlatItem {
    data class Header(val year: String) : AotyFlatItem
    data class Entry(val year: String, val albumId: String) : AotyFlatItem
}

@Composable
fun AotyRankingView(
    context: ViewContext,
    albumIds: List<String>,
    onExit: () -> Unit,
) {
    val albumFilter by context.symphony.settings.data
        .map { it.uiAlbumGridAlbumFilter }
        .collectAsState(AlbumFilter.getDefaultInstance())
    val hiddenAlbumIds by context.symphony.settings.data
        .map { it.hiddenAlbumIdsList.toSet() }
        .collectAsState(emptySet())
    val showHiddenAlbums by context.symphony.settings.data
        .map { it.showHiddenAlbums }
        .collectAsState(false)
    val filteredAlbumIds by remember(albumIds, albumFilter, hiddenAlbumIds, showHiddenAlbums) {
        derivedStateOf {
            context.symphony.groove.album.getAlbums(
                albumIds, AlbumSortBy.ALBUM_NAME, false, albumFilter, hiddenAlbumIds, showHiddenAlbums,
            )
        }
    }

    val initialGroups = remember(filteredAlbumIds) { buildAotyGroups(context, filteredAlbumIds) }
    val items = remember(initialGroups) {
        mutableStateListOf<AotyFlatItem>().also { list ->
            initialGroups.forEach { (year, ids) ->
                list.add(AotyFlatItem.Header(year))
                ids.forEach { list.add(AotyFlatItem.Entry(year, it)) }
            }
        }
    }

    var isSaving by remember { mutableStateOf(false) }
    var saveProgress by remember { mutableIntStateOf(0) }
    var saveTotal by remember { mutableIntStateOf(0) }
    val coroutineScope = rememberCoroutineScope()

    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromItem = items.getOrNull(from.index)
        val toItem = items.getOrNull(to.index)
        if (fromItem is AotyFlatItem.Entry
            && toItem is AotyFlatItem.Entry
            && fromItem.year == toItem.year
        ) {
            items.apply { add(to.index, removeAt(from.index)) }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp),
        ) {
            itemsIndexed(
                items,
                key = { _, item ->
                    when (item) {
                        is AotyFlatItem.Header -> "header_${item.year}"
                        is AotyFlatItem.Entry -> item.albumId
                    }
                },
            ) { index, item ->
                when (item) {
                    is AotyFlatItem.Header -> GroupSectionHeader(item.year)
                    is AotyFlatItem.Entry -> {
                        val existingAoty = remember(item.albumId) {
                            context.symphony.groove.album.getSongIds(item.albumId)
                                .firstNotNullOfOrNull { context.symphony.groove.song.get(it)?.customTags["AOTY"] }
                        }
                        ReorderableItem(reorderState, key = item.albumId) {
                            context.symphony.groove.album.get(item.albumId)?.let { album ->
                                Surface(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            existingAoty?.let { "#$it" } ?: "—",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(end = 12.dp),
                                        )
                                        AsyncImage(
                                            album.createArtworkImageRequest(context.symphony).build(),
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(RoundedCornerShape(8.dp)),
                                        )
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 12.dp),
                                        ) {
                                            Text(
                                                album.name,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            val artistName = album.albumArtists
                                                .ifEmpty { album.artists }
                                                .joinToString(", ")
                                            if (artistName.isNotEmpty()) {
                                                Text(
                                                    artistName,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                        Icon(
                                            Icons.Filled.DragHandle,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .draggableHandle()
                                                .padding(horizontal = 12.dp)
                                                .size(24.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            tonalElevation = 8.dp,
            shadowElevation = 8.dp,
        ) {
            Column {
                if (isSaving) {
                    LinearProgressIndicator(
                        progress = { saveProgress.toFloat() / saveTotal.coerceAtLeast(1) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.AotyRanking),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = onExit, enabled = !isSaving) {
                        Text(stringResource(R.string.Cancel))
                    }
                    Button(
                        onClick = {
                            isSaving = true
                            saveProgress = 0
                            val rankCounters = mutableMapOf<String, Int>()
                            val songsWithRank = mutableListOf<Pair<Song, String>>()
                            for (flatItem in items) {
                                when (flatItem) {
                                    is AotyFlatItem.Header -> rankCounters[flatItem.year] = 1
                                    is AotyFlatItem.Entry -> {
                                        val rank = rankCounters.getOrDefault(flatItem.year, 1)
                                        rankCounters[flatItem.year] = rank + 1
                                        val songs = context.symphony.groove.album.getSongIds(flatItem.albumId)
                                            .mapNotNull { context.symphony.groove.song.get(it) }
                                        val existingAoty = songs.firstNotNullOfOrNull { it.customTags["AOTY"]?.toIntOrNull() }
                                        if (existingAoty != rank) {
                                            val rankStr = rank.toString()
                                            songs.forEach { song -> songsWithRank.add(song to rankStr) }
                                        }
                                    }
                                }
                            }
                            saveTotal = songsWithRank.size
                            coroutineScope.launch(Dispatchers.IO) {
                                songsWithRank.forEachIndexed { index, (song, rankStr) ->
                                    val fd = context.activity.contentResolver
                                        .openFileDescriptor(song.uri, "rw")
                                        ?.detachFd()
                                        ?: run {
                                            withContext(Dispatchers.Main) { saveProgress = index + 1 }
                                            return@forEachIndexed
                                        }
                                    AudioMetadataParser.write(song.filename, fd, mapOf("AOTY" to rankStr))
                                    withContext(Dispatchers.Main) { saveProgress = index + 1 }
                                }
                                context.symphony.groove.fetchPaths(songsWithRank.map { (song, _) -> song.path })
                                withContext(Dispatchers.Main) {
                                    isSaving = false
                                    onExit()
                                }
                            }
                        },
                        enabled = !isSaving,
                        modifier = Modifier.padding(start = 8.dp),
                    ) {
                        Text(stringResource(R.string.SaveRanking))
                    }
                }
            }
        }
    }
}

private fun buildAotyGroups(
    context: ViewContext,
    albumIds: List<String>,
): List<Pair<String, List<String>>> {
    val unknownLabel = "Unknown"
    val grouped = albumIds.groupBy { id ->
        context.symphony.groove.album.get(id)?.startYear?.toString() ?: unknownLabel
    }
    val yearKeys = grouped.keys
        .filter { it != unknownLabel }
        .sortedByDescending { it.toIntOrNull() ?: 0 }
    val ordered = if (unknownLabel in grouped) yearKeys + unknownLabel else yearKeys
    return ordered.map { year ->
        val ids = grouped[year] ?: emptyList()
        val sorted = ids.sortedWith(compareBy { id ->
            context.symphony.groove.album.getSongIds(id)
                .mapNotNull { context.symphony.groove.song.get(it) }
                .firstNotNullOfOrNull { it.customTags["AOTY"]?.toIntOrNull() }
                ?: Int.MAX_VALUE
        })
        year to sorted
    }
}
