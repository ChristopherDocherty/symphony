package io.github.zyrouge.symphony.services.groove

import androidx.compose.runtime.Immutable
import io.github.zyrouge.symphony.Symphony
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import kotlin.time.Duration

@Immutable
data class Album(
    val id: String,
    val name: String,
    val artists: MutableSet<String>,
    val albumArtists: MutableSet<String>,
    var startYear: Int?,
    var endYear: Int?,
    var numberOfTracks: Int,
    var duration: Duration,
    var is_compilation: Boolean = false,
    var date: LocalDate? = null,
) {
    fun createArtworkImageRequest(symphony: Symphony) =
        symphony.groove.album.createArtworkImageRequest(id)

    fun getSongIds(symphony: Symphony) = symphony.groove.album.getSongIds(id)
    suspend fun getSortedSongIds(symphony: Symphony): List<String> {
        val currentSettings = symphony.settings.data.first()
        return symphony.groove.song.sort(
            getSongIds(symphony),
            currentSettings.uiArtistViewSongsSort.by,
            currentSettings.uiArtistViewSongsSort.reverse,
        )
    }
}
