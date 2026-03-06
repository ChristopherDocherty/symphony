package io.github.zyrouge.symphony.ui.view.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.zyrouge.symphony.R
import io.github.zyrouge.symphony.services.groove.Song
import io.github.zyrouge.symphony.services.radio.Radio
import io.github.zyrouge.symphony.ui.components.SongCard
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import kotlinx.coroutines.delay

class ZapPageState : HomePageState {
    var isZapping by mutableStateOf(false)
    var playedSongs by mutableStateOf(listOf<Song>())
    var skipTick by mutableIntStateOf(0)

    @Composable
    override fun DropdownItems() {}

    @Composable
    override fun Dialogs(context: ViewContext) {}
}

@Composable
fun ZapView(context: ViewContext, pageState: ZapPageState? = null) {
    val state = pageState ?: ZapPageState()
    val allSongIds by context.symphony.groove.song.all.collectAsState()

    LaunchedEffect(state.isZapping, state.skipTick) {
        if (!state.isZapping) {
            context.symphony.radio.zapMode = false
            context.symphony.radio.stop()
            return@LaunchedEffect
        }
        context.symphony.radio.zapMode = true
        val playedIds = state.playedSongs.map { it.id }.toSet()
        val remaining = allSongIds.filter { it !in playedIds }
        val pool = remaining.ifEmpty { allSongIds }
        if (pool.isEmpty()) return@LaunchedEffect
        val songId = pool.random()
        val song = context.symphony.groove.song.get(songId) ?: return@LaunchedEffect
        state.playedSongs = listOf(song) + state.playedSongs
        val startPosition = if (song.duration > 0) {
            (song.duration * (0.1 + Math.random() * 0.8)).toLong()
        } else null
        context.symphony.radio.stop()
        context.symphony.radio.queue.add(
            songId,
            options = Radio.PlayOptions(startPosition = startPosition),
        )
        delay(5_000L)
        if (state.isZapping) {
            state.skipTick++
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp),
        ) {
            if (state.playedSongs.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillParentMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(stringResource(R.string.ZapEmpty))
                    }
                }
            } else {
                itemsIndexed(
                    state.playedSongs,
                    key = { i, s -> "$i-${s.id}" },
                ) { _, song ->
                    SongCard(context = context, song = song, onClick = {})
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
        ) {
            Button(
                modifier = Modifier.weight(1f),
                onClick = { state.isZapping = !state.isZapping },
            ) {
                Text(
                    if (state.isZapping) stringResource(R.string.ZapStop)
                    else stringResource(R.string.ZapStart)
                )
            }
            OutlinedButton(
                modifier = Modifier.weight(1f),
                enabled = state.isZapping,
                onClick = { state.skipTick++ },
            ) {
                Text(stringResource(R.string.ZapSkip))
            }
        }
    }
}
