package io.github.zyrouge.symphony.services.groove

import androidx.compose.runtime.Immutable
import io.github.zyrouge.symphony.Symphony
import kotlinx.coroutines.flow.first

@Immutable
data class AlbumArtist(
    val name: String,
    var numberOfAlbums: Int,
    var numberOfTracks: Int,
) {
    fun createArtworkImageRequest(symphony: Symphony) =
        symphony.groove.albumArtist.createArtworkImageRequest(name)

    fun getSongIds(symphony: Symphony) = symphony.groove.albumArtist.getSongIds(name)
    suspend fun getSortedSongIds(symphony: Symphony) : List<String> {
        val currentSettings = symphony.settings.data.first()
        return symphony.groove.song.sort(
            getSongIds(symphony),
            currentSettings.uiArtistViewSongsSort.by,
            currentSettings.uiArtistViewSongsSort.reverse,
        )
    }

    fun getAlbumIds(symphony: Symphony) = symphony.groove.albumArtist.getAlbumIds(name)
}
