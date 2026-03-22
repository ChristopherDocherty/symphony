package io.github.zyrouge.symphony.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.zyrouge.symphony.R
import io.github.zyrouge.symphony.SongSortBy
import io.github.zyrouge.symphony.copy
import io.github.zyrouge.symphony.services.groove.Groove
import io.github.zyrouge.symphony.services.groove.Song
import io.github.zyrouge.symphony.services.radio.Radio
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import io.github.zyrouge.symphony.ui.view.SettingsViewRoute
import io.github.zyrouge.symphony.ui.view.home.SongsPageState
import io.github.zyrouge.symphony.ui.view.settings.GrooveSettingsViewRoute
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

enum class SongListType {
    Default,
    Playlist,
    Album,
}

@Composable
fun SongList(
    context: ViewContext,
    songIds: List<String>,
    songsCount: Int? = null,
    leadingContent: (LazyListScope.() -> Unit)? = null,
    trailingContent: (LazyListScope.() -> Unit)? = null,
    trailingOptionsContent: (@Composable ColumnScope.(Int, Song, () -> Unit) -> Unit)? = null,
    cardThumbnailLabel: (@Composable (Int, Song) -> Unit)? = null,
    cardThumbnailLabelStyle: SongCardThumbnailLabelStyle = SongCardThumbnailLabelStyle.Default,
    type: SongListType = SongListType.Default,
    disableHeartIcon: Boolean = false,
    enableAddMediaFoldersHint: Boolean = false,
    pageState: SongsPageState? = null,
) {
    val sortBy by type.getLastUsedSortBy(context).collectAsState(SongSortBy.SONG_TITLE)
    val sortReverse by type.getLastUsedSortReverse(context).collectAsState(false)
    val sortedSongIds by remember(songIds, sortBy, sortReverse) {
        derivedStateOf {
            context.symphony.groove.song.sort(songIds, sortBy, sortReverse)
        }
    }
    val albumCount by remember(songIds) {
        derivedStateOf {
            songIds.mapNotNull { context.symphony.groove.song.get(it)?.album }.distinct().size
        }
    }
    val coroutineScope = rememberCoroutineScope()

    SideEffect {
        pageState?.sortedSongIds = sortedSongIds
    }

    Box(modifier = Modifier.fillMaxSize()) {
        MediaSortBarScaffold(
            mediaSortBar = {
                MediaSortBar(
                    context,
                    reverse = sortReverse,
                    onReverseChange = {
                        coroutineScope.launch {
                            type.setLastUsedSortReverse(context, it)
                        }
                    },
                    sort = sortBy,
                    sorts = SongSortBy.entries
                        .filter { it != SongSortBy.UNRECOGNIZED }
                        .associateWith { x -> ViewContext.parameterizedFn { x.label(it) } },
                    onSortChange = { newSort ->
                        coroutineScope.launch {
                            type.setLastUsedSortBy(context, newSort)
                        }
                    },
                    label = {
                        Text(stringResource(R.string.XAlbums, (albumCount).toString()) + ", " +
                            stringResource(R.string.XSongs, (songsCount ?: songIds.size).toString()))
                    },
                    onShufflePlay = {
                        context.symphony.radio.shorty.playQueue(sortedSongIds, shuffle = true)
                    }
                )
            },
            content = {
                when {
                    songIds.isEmpty() -> IconTextBody(
                        icon = { modifier ->
                            Icon(Icons.Filled.MusicNote, null, modifier = modifier)
                        },
                        content = {
                            Text(stringResource(R.string.DamnThisIsSoEmpty))
                            if (enableAddMediaFoldersHint) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    stringResource(R.string.HintAddMediaFolders),
                                    style = MaterialTheme.typography.labelMedium,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .clickable {
                                            context.navController.navigate(
                                                GrooveSettingsViewRoute(SettingsViewRoute.ELEMENT_MEDIA_FOLDERS)
                                            )
                                        }
                                        .padding(2.dp),
                                )
                            }
                        }
                    )

                    else -> {
                        val lazyListState = rememberLazyListState()

                        LazyColumn(
                            state = lazyListState,
                            modifier = Modifier.drawScrollBar(lazyListState)
                        ) {
                            leadingContent?.invoke(this)
                            itemsIndexed(
                                sortedSongIds,
                                key = { i, x -> "$i-$x" },
                                contentType = { _, _ -> Groove.Kind.SONG }
                            ) { i, songId ->
                                context.symphony.groove.song.get(songId)?.let { song ->
                                    SongCard(
                                        context,
                                        song = song,
                                        thumbnailLabel = cardThumbnailLabel?.let {
                                            { it(i, song) }
                                        },
                                        thumbnailLabelStyle = cardThumbnailLabelStyle,
                                        disableHeartIcon = disableHeartIcon,
                                        trailingOptionsContent = trailingOptionsContent?.let {
                                            { onDismissRequest -> it(i, song, onDismissRequest) }
                                        },
                                        pageState = pageState,
                                    ) {
                                        context.symphony.radio.shorty.playQueue(
                                            sortedSongIds,
                                            Radio.PlayOptions(index = i)
                                        )
                                    }
                                }
                            }
                            trailingContent?.invoke(this)
                        }
                    }
                }
            }
        )

        if (pageState?.isMultiSelectMode == true) {
            SongMultiSelectBottomBar(
                modifier = Modifier.align(Alignment.BottomCenter),
                selectedCount = pageState.selectedSongIds.size,
                onSelectAll = { pageState.selectedSongIds = pageState.sortedSongIds.toSet() },
                onEdit = { pageState.showBulkEditDialog = true },
                onAutoNumber = { pageState.showAutoNumberDialog = true },
                onRename = { pageState.showRenameDialog = true },
                onFetchLyrics = { pageState.showBulkFetchLyricsDialog = true },
                onExit = { pageState.exitMultiSelect() },
            )
        }

        if (pageState?.showBulkFetchLyricsDialog == true && pageState.selectedSongIds.isNotEmpty()) {
            BulkFetchLyricsDialog(
                context = context,
                songIds = sortedSongIds.filter { it in pageState.selectedSongIds },
                onDismissRequest = {
                    pageState.showBulkFetchLyricsDialog = false
                    pageState.exitMultiSelect()
                },
            )
        }

        if (pageState?.showBulkEditDialog == true && pageState.selectedSongIds.isNotEmpty()) {
            BulkSongEditDialog(
                context = context,
                songIds = sortedSongIds.filter { it in pageState.selectedSongIds },
                onDismissRequest = { pageState.showBulkEditDialog = false },
            )
        }

        if (pageState?.showAutoNumberDialog == true && pageState.selectedSongIds.isNotEmpty()) {
            AutoNumberWizardDialog(
                context = context,
                songIds = sortedSongIds.filter { it in pageState.selectedSongIds },
                onDismissRequest = {
                    pageState.showAutoNumberDialog = false
                    pageState.exitMultiSelect()
                },
            )
        }

        if (pageState?.showRenameDialog == true && pageState.selectedSongIds.isNotEmpty()) {
            RenameFromTagsDialog(
                context = context,
                songIds = sortedSongIds.filter { it in pageState.selectedSongIds },
                onDismissRequest = {
                    pageState.showRenameDialog = false
                    pageState.exitMultiSelect()
                },
            )
        }
    }
}

@Composable
private fun SongMultiSelectBottomBar(
    modifier: Modifier = Modifier,
    selectedCount: Int,
    onSelectAll: () -> Unit,
    onEdit: () -> Unit,
    onAutoNumber: () -> Unit,
    onRename: () -> Unit,
    onFetchLyrics: () -> Unit,
    onExit: () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "$selectedCount selected",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 8.dp),
            )
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = onSelectAll) {
                Text("Select all")
            }
            IconButton(onClick = onEdit, enabled = selectedCount > 0) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit selected")
            }
            IconButton(onClick = onAutoNumber, enabled = selectedCount > 0) {
                Icon(Icons.Filled.FormatListNumbered, contentDescription = "Autonumber")
            }
            IconButton(onClick = onRename, enabled = selectedCount > 0) {
                Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = "Rename from tags")
            }
            IconButton(onClick = onFetchLyrics, enabled = selectedCount > 0) {
                Icon(Icons.Filled.LibraryMusic, contentDescription = "Fetch lyrics")
            }
            IconButton(onClick = onExit) {
                Icon(Icons.Filled.Close, contentDescription = "Exit select mode")
            }
        }
    }
}

fun SongSortBy.label(context: ViewContext) = when (this) {
    SongSortBy.SONG_CUSTOM -> context.activity.getString(R.string.Custom)
    SongSortBy.SONG_TITLE -> context.activity.getString(R.string.Title)
    SongSortBy.SONG_ARTIST -> context.activity.getString(R.string.Artist)
    SongSortBy.SONG_ALBUM -> context.activity.getString(R.string.Album)
    SongSortBy.SONG_DURATION -> context.activity.getString(R.string.Duration)
    SongSortBy.SONG_COMPOSER -> context.activity.getString(R.string.Composer)
    SongSortBy.SONG_YEAR -> context.activity.getString(R.string.Year)
    SongSortBy.SONG_FILENAME -> context.activity.getString(R.string.Filename)
    SongSortBy.SONG_TRACK_NUMBER -> context.activity.getString(R.string.TrackNumber)
    SongSortBy.SONG_DATE_ADDED -> "Date Added"
    SongSortBy.SONG_DATE_MODIFIED -> "Date Modified"
    SongSortBy.SONG_SCROBBLE_COUNT -> context.activity.getString(R.string.ScrobbleCount)
    SongSortBy.UNRECOGNIZED -> "???"
}

fun SongListType.getLastUsedSortBy(context: ViewContext) = when (this) {
    SongListType.Default -> context.symphony.settings.data.map { it.uiDefaultSongSort.by}
    SongListType.Album -> context.symphony.settings.data.map { it.uiAlbumViewSongsSort.by}
    SongListType.Playlist -> context.symphony.settings.data.map { it.uiPlaylistViewSongsSort.by}
}

suspend fun SongListType.setLastUsedSortBy(context: ViewContext, sort: SongSortBy) =
    when (this) {
        SongListType.Default -> {
            context.symphony.settings.updateData { it.copy { uiDefaultSongSort = uiDefaultSongSort.copy { by = sort} }}
        }
        SongListType.Playlist -> {
            context.symphony.settings.updateData { it.copy { uiPlaylistViewSongsSort = uiPlaylistViewSongsSort.copy {by = sort}} }
        }
        SongListType.Album -> {
            context.symphony.settings.updateData { it.copy { uiAlbumViewSongsSort = uiAlbumViewSongsSort.copy {by =sort} }}
        }
    }

fun SongListType.getLastUsedSortReverse(context: ViewContext) = when (this) {
    SongListType.Default -> context.symphony.settings.data.map { it.uiDefaultSongSort.reverse}
    SongListType.Album -> context.symphony.settings.data.map { it.uiAlbumViewSongsSort.reverse}
    SongListType.Playlist -> context.symphony.settings.data.map { it.uiPlaylistViewSongsSort.reverse}
}

suspend fun SongListType.setLastUsedSortReverse(context: ViewContext, value: Boolean) = when (this) {
    SongListType.Default -> {
        context.symphony.settings.updateData { it.copy { uiDefaultSongSort = uiDefaultSongSort.copy { reverse = value} }}
    }
    SongListType.Playlist -> {
        context.symphony.settings.updateData { it.copy { uiPlaylistViewSongsSort = uiPlaylistViewSongsSort.copy {reverse = value}} }
    }
    SongListType.Album -> {
        context.symphony.settings.updateData { it.copy { uiAlbumViewSongsSort = uiAlbumViewSongsSort.copy {reverse = value} }}
    }

}

@Composable
fun GroupedDuplicateSongList(
    context: ViewContext,
    songIds: List<String>,
    pageState: SongsPageState? = null,
) {
    val sortBy by SongListType.Default.getLastUsedSortBy(context).collectAsState(SongSortBy.SONG_TITLE)
    val sortReverse by SongListType.Default.getLastUsedSortReverse(context).collectAsState(false)
    val sortedSongIds by remember(songIds, sortBy, sortReverse) {
        derivedStateOf {
            context.symphony.groove.song.sort(songIds, sortBy, sortReverse)
        }
    }
    val groups by remember(sortedSongIds) {
        derivedStateOf {
            val result = linkedMapOf<String, MutableList<Song>>()
            for (id in sortedSongIds) {
                val song = context.symphony.groove.song.get(id) ?: continue
                val key = "${song.artists.firstOrNull()?.lowercase() ?: ""}|${song.title.lowercase()}"
                result.getOrPut(key) { mutableListOf() }.add(song)
            }
            result.values.toList()
        }
    }
    val albumCount by remember(songIds) {
        derivedStateOf {
            songIds.mapNotNull { context.symphony.groove.song.get(it)?.album }.distinct().size
        }
    }
    var expandedGroups by remember { mutableStateOf(emptySet<String>()) }
    val coroutineScope = rememberCoroutineScope()

    SideEffect {
        pageState?.sortedSongIds = sortedSongIds
    }

    Box(modifier = Modifier.fillMaxSize()) {
        MediaSortBarScaffold(
            mediaSortBar = {
                MediaSortBar(
                    context,
                    reverse = sortReverse,
                    onReverseChange = {
                        coroutineScope.launch {
                            SongListType.Default.setLastUsedSortReverse(context, it)
                        }
                    },
                    sort = sortBy,
                    sorts = SongSortBy.entries
                        .filter { it != SongSortBy.UNRECOGNIZED }
                        .associateWith { x -> ViewContext.parameterizedFn { x.label(it) } },
                    onSortChange = { newSort ->
                        coroutineScope.launch {
                            SongListType.Default.setLastUsedSortBy(context, newSort)
                        }
                    },
                    label = {
                        Text(
                            stringResource(R.string.XAlbums, albumCount.toString()) + ", " +
                                stringResource(R.string.XSongs, songIds.size.toString())
                        )
                    },
                    onShufflePlay = {
                        context.symphony.radio.shorty.playQueue(sortedSongIds, shuffle = true)
                    }
                )
            },
            content = {
                when {
                    songIds.isEmpty() -> IconTextBody(
                        icon = { modifier -> Icon(Icons.Filled.MusicNote, null, modifier = modifier) },
                        content = { Text(stringResource(R.string.DamnThisIsSoEmpty)) }
                    )
                    else -> {
                        val lazyListState = rememberLazyListState()
                        LazyColumn(
                            state = lazyListState,
                            modifier = Modifier.drawScrollBar(lazyListState)
                        ) {
                            groups.forEach { groupSongs ->
                                val groupKey = "${groupSongs.first().artists.firstOrNull()?.lowercase() ?: ""}|${groupSongs.first().title.lowercase()}"
                                if (groupSongs.size == 1) {
                                    item(key = groupSongs.first().id, contentType = Groove.Kind.SONG) {
                                        SongCard(
                                            context,
                                            song = groupSongs.first(),
                                            pageState = pageState,
                                        ) {
                                            context.symphony.radio.shorty.playQueue(
                                                sortedSongIds,
                                                Radio.PlayOptions(index = sortedSongIds.indexOf(groupSongs.first().id))
                                            )
                                        }
                                    }
                                } else {
                                    item(key = "group-$groupKey", contentType = "duplicate_group") {
                                        DuplicateSongGroupRow(
                                            context = context,
                                            songs = groupSongs,
                                            isExpanded = groupKey in expandedGroups,
                                            onToggleExpand = {
                                                expandedGroups = if (groupKey in expandedGroups)
                                                    expandedGroups - groupKey
                                                else
                                                    expandedGroups + groupKey
                                            },
                                            allSortedSongIds = sortedSongIds,
                                            pageState = pageState,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        )

        if (pageState?.isMultiSelectMode == true) {
            SongMultiSelectBottomBar(
                modifier = Modifier.align(Alignment.BottomCenter),
                selectedCount = pageState.selectedSongIds.size,
                onSelectAll = { pageState.selectedSongIds = pageState.sortedSongIds.toSet() },
                onEdit = { pageState.showBulkEditDialog = true },
                onAutoNumber = { pageState.showAutoNumberDialog = true },
                onRename = { pageState.showRenameDialog = true },
                onFetchLyrics = { pageState.showBulkFetchLyricsDialog = true },
                onExit = { pageState.exitMultiSelect() },
            )
        }

        if (pageState?.showBulkFetchLyricsDialog == true && pageState.selectedSongIds.isNotEmpty()) {
            BulkFetchLyricsDialog(
                context = context,
                songIds = sortedSongIds.filter { it in pageState.selectedSongIds },
                onDismissRequest = {
                    pageState.showBulkFetchLyricsDialog = false
                    pageState.exitMultiSelect()
                },
            )
        }

        if (pageState?.showBulkEditDialog == true && pageState.selectedSongIds.isNotEmpty()) {
            BulkSongEditDialog(
                context = context,
                songIds = sortedSongIds.filter { it in pageState.selectedSongIds },
                onDismissRequest = { pageState.showBulkEditDialog = false },
            )
        }

        if (pageState?.showAutoNumberDialog == true && pageState.selectedSongIds.isNotEmpty()) {
            AutoNumberWizardDialog(
                context = context,
                songIds = sortedSongIds.filter { it in pageState.selectedSongIds },
                onDismissRequest = {
                    pageState.showAutoNumberDialog = false
                    pageState.exitMultiSelect()
                },
            )
        }

        if (pageState?.showRenameDialog == true && pageState.selectedSongIds.isNotEmpty()) {
            RenameFromTagsDialog(
                context = context,
                songIds = sortedSongIds.filter { it in pageState.selectedSongIds },
                onDismissRequest = {
                    pageState.showRenameDialog = false
                    pageState.exitMultiSelect()
                },
            )
        }
    }
}

@Composable
private fun DuplicateSongGroupRow(
    context: ViewContext,
    songs: List<Song>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    allSortedSongIds: List<String>,
    pageState: SongsPageState?,
) {
    val firstSong = songs.first()
    val settings by context.symphony.settingsState.collectAsState()
    val scrobbleCount = remember(settings.songShowScrobbleCount, firstSong) {
        if (settings.songShowScrobbleCount) {
            context.symphony.lastFmBackup.getSongScrobbleCount(
                firstSong.artists.firstOrNull() ?: "",
                firstSong.title,
            )
        } else 0L
    }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleExpand() }
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                context.symphony.groove.artist.createArtworkImageRequestForSongs(songs.map { it.id }).build(),
                null,
                modifier = Modifier
                    .size(45.dp)
                    .clip(RoundedCornerShape(10.dp)),
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    firstSong.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (firstSong.artists.isNotEmpty()) {
                    Text(
                        firstSong.artists.joinToString(),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (scrobbleCount > 0L) {
                    Text(
                        stringResource(R.string.LastFmScrobbles, scrobbleCount),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                stringResource(R.string.NFiles, songs.size.toString()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(visible = isExpanded) {
            Column {
                songs.forEach { song ->
                    SongCard(
                        context,
                        song = song,
                        pageState = pageState,
                    ) {
                        context.symphony.radio.shorty.playQueue(
                            allSortedSongIds,
                            Radio.PlayOptions(index = allSortedSongIds.indexOf(song.id))
                        )
                    }
                }
            }
        }
    }
}
