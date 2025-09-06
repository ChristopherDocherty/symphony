package io.github.zyrouge.symphony.services.groove

import androidx.compose.runtime.Immutable
import io.github.zyrouge.symphony.Symphony
import kotlinx.coroutines.flow.first

@Immutable
data class Genre(
    val name: String,
    var numberOfTracks: Int,
) {
    fun getSongIds(symphony: Symphony) = symphony.groove.genre.getSongIds(name)
}
