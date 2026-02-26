package io.github.zyrouge.symphony.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextDecoration
import io.github.zyrouge.symphony.services.groove.ALBUM_STRING_FILTER_FIELDS
import io.github.zyrouge.symphony.services.groove.Song
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import io.github.zyrouge.symphony.ui.view.AlbumArtistViewRoute
import io.github.zyrouge.symphony.ui.view.AlbumViewRoute
import io.github.zyrouge.symphony.ui.view.ArtistViewRoute
import io.github.zyrouge.symphony.ui.view.GenreViewRoute
import io.github.zyrouge.symphony.utils.ActivityUtils
import io.github.zyrouge.symphony.utils.DurationUtils
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.round
import androidx.compose.ui.res.stringResource
import io.github.zyrouge.symphony.R

@Composable
fun SongInformationDialog(context: ViewContext, song: Song, onDismissRequest: () -> Unit) {
    var showMetadataEditorDialog by remember { mutableStateOf(false) }

    InformationDialog(
        context,
        titleTrailing = {
            IconButton(onClick = { showMetadataEditorDialog = true }) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit metadata")
            }
        },
        content = {
            InformationKeyValue(stringResource(R.string.TrackName)) {
                LongPressCopyableText(context, song.title)
            }
            if (song.artists.isNotEmpty()) {
                InformationKeyValue(stringResource(R.string.Artist)) {
                    LongPressCopyableAndTappableText(context, song.artists) {
                        onDismissRequest()
                        context.navController.navigate(ArtistViewRoute(it))
                    }
                }
            }
            if (song.albumArtists.isNotEmpty()) {
                InformationKeyValue(stringResource(R.string.AlbumArtist)) {
                    LongPressCopyableAndTappableText(context, song.albumArtists) {
                        onDismissRequest()
                        context.navController.navigate(AlbumArtistViewRoute(it))
                    }
                }
            }
            if (song.composers.isNotEmpty()) {
                InformationKeyValue(stringResource(R.string.Composer)) {
                    // TODO composers page maybe?
                    LongPressCopyableAndTappableText(context, song.composers) {
                        onDismissRequest()
                        context.navController.navigate(ArtistViewRoute(it))
                    }
                }
            }
            context.symphony.groove.album.getIdFromSong(song)?.let { albumId ->
                InformationKeyValue(stringResource(R.string.Album)) {
                    LongPressCopyableAndTappableText(context, setOf(song.album!!)) {
                        onDismissRequest()
                        context.navController.navigate(AlbumViewRoute(albumId))
                    }
                }
            }
            if (song.genres.isNotEmpty()) {
                InformationKeyValue(stringResource(R.string.Genre)) {
                    LongPressCopyableAndTappableText(context, song.genres) {
                        onDismissRequest()
                        context.navController.navigate(GenreViewRoute(it))
                    }
                }
            }
            song.date?.let {
                InformationKeyValue(stringResource(R.string.Date)) {
                    LongPressCopyableText(context, it.toString())
                }
            }
            song.year?.let {
                InformationKeyValue(stringResource(R.string.Year)) {
                    LongPressCopyableText(context, it.toString())
                }
            }
            song.trackNumber?.let {
                InformationKeyValue(stringResource(R.string.TrackNumber)) {
                    LongPressCopyableText(context, it.toString())
                }
            }
            song.trackTotal?.let {
                InformationKeyValue(stringResource(R.string.TrackCount)) {
                    LongPressCopyableText(context, it.toString())
                }
            }
            song.discNumber?.let {
                InformationKeyValue(stringResource(R.string.DiscNumber)) {
                    LongPressCopyableText(context, it.toString())
                }
            }
            song.discTotal?.let {
                InformationKeyValue(stringResource(R.string.DiscTotal)) {
                    LongPressCopyableText(context, it.toString())
                }
            }
            ALBUM_STRING_FILTER_FIELDS.forEach { field ->
                val value = song.customTags[field.tagName]
                if (!value.isNullOrEmpty()) {
                    InformationKeyValue(field.label) {
                        LongPressCopyableText(context, value)
                    }
                }
            }
            InformationKeyValue(stringResource(R.string.Duration)) {
                LongPressCopyableText(context, DurationUtils.formatMs(song.duration))
            }
            song.encoder?.let {
                InformationKeyValue(stringResource(R.string.Encoder)) {
                    LongPressCopyableText(context, it)
                }
            }
            song.channels?.let {
                InformationKeyValue(stringResource(R.string.AudioChannels)) {
                    LongPressCopyableText(context, it.toString())
                }
            }
            song.bitrateK?.let {
                InformationKeyValue(stringResource(R.string.Bitrate)) {
                    val text = buildString {
                        append(stringResource(R.string.XKbps, it.toString()))
                    }
                    LongPressCopyableText(context, text)
                }
            }
            song.samplingRateK?.let {
                InformationKeyValue(stringResource(R.string.SamplingRate)) {
                    LongPressCopyableText(context, stringResource(R.string.XKHz, it.toString()))
                }
            }
            InformationKeyValue(stringResource(R.string.Filename)) {
                LongPressCopyableText(context, song.filename)
            }
            InformationKeyValue(stringResource(R.string.Path)) {
                LongPressCopyableText(context, song.path)
            }
            InformationKeyValue(stringResource(R.string.Size)) {
                LongPressCopyableText(context, "${round((song.size / 1024 / 1024).toDouble())} MB")
            }
            InformationKeyValue(stringResource(R.string.LastModified)) {
                LongPressCopyableText(
                    context,
                    SimpleDateFormat.getInstance().format(Date(song.dateModified * 1000)),
                )
            }
            InformationKeyValue(stringResource(R.string.Id)) {
                LongPressCopyableText(context, song.id)
            }
        },
        onDismissRequest = onDismissRequest,
    )

    if (showMetadataEditorDialog) {
        SongMetadataEditorDialog(
            context = context,
            song = song,
            onDismissRequest = { showMetadataEditorDialog = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LongPressCopyableAndTappableText(
    context: ViewContext,
    values: Set<String>,
    onTap: (String) -> Unit,
) {
    val textStyle = LocalTextStyle.current.copy(
        textDecoration = TextDecoration.Underline,
    )

    FlowRow {
        values.forEachIndexed { i, it ->
            Text(
                it,
                style = textStyle,
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = { _ ->
                            ActivityUtils.copyToClipboardAndNotify(context.symphony, it)
                        },
                        onTap = { _ ->
                            onTap(it)
                        },
                    )
                },
            )
            if (i != values.size - 1) {
                Text(", ")
            }
        }
    }
}
