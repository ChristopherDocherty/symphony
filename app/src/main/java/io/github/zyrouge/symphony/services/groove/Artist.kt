package io.github.zyrouge.symphony.services.groove

import androidx.compose.runtime.Immutable
import io.github.zyrouge.symphony.Symphony
import kotlinx.coroutines.flow.first // Import first
import kotlinx.coroutines.flow.map

@Immutable
data class Artist(
    val name: String,
    var numberOfAlbums: Int,
    var numberOfTracks: Int,
) {
    fun createArtworkImageRequest(symphony: Symphony) =
        symphony.groove.artist.createArtworkImageRequest(name)

    fun getSongIds(symphony: Symphony) = symphony.groove.artist.getSongIds(name)

    suspend fun getSortedSongIds(symphony: Symphony): List<String> {
        val currentSettings = symphony.settings.data.first()
        return symphony.groove.song.sort(
            getSongIds(symphony),
            currentSettings.uiDefaultSongSort.by,
            currentSettings.uiDefaultSongSort.reverse,
        )
    }

    fun getAlbumIds(symphony: Symphony) = symphony.groove.artist.getAlbumIds(name)
}
