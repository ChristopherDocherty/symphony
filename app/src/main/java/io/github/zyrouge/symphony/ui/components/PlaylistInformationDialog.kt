package io.github.zyrouge.symphony.ui.components

import androidx.compose.runtime.Composable
import io.github.zyrouge.symphony.services.groove.Playlist
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import androidx.compose.ui.res.stringResource
import io.github.zyrouge.symphony.R

@Composable
fun PlaylistInformationDialog(
    context: ViewContext,
    playlist: Playlist,
    onDismissRequest: () -> Unit,
) {
    InformationDialog(
        context,
        content = {
            InformationKeyValue(stringResource(R.string.Id)) {
                LongPressCopyableText(context, playlist.id)
            }
            InformationKeyValue(stringResource(R.string.Title)) {
                LongPressCopyableText(context, playlist.title)
            }
            InformationKeyValue(stringResource(R.string.TrackCount)) {
                LongPressCopyableText(context, playlist.numberOfTracks.toString())
            }
            InformationKeyValue(stringResource(R.string.PlaylistStoreLocation)) {
                LongPressCopyableText(
                    context,
                    when {
                        playlist.isLocal -> stringResource(R.string.LocalStorage)
                        else -> stringResource(R.string.AppBuiltIn)
                    }
                )
            }
            playlist.path?.let {
                InformationKeyValue(stringResource(R.string.Path)) {
                    LongPressCopyableText(context, it)
                }
            }
        },
        onDismissRequest = onDismissRequest,
    )
}
