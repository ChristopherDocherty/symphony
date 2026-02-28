package io.github.zyrouge.symphony.services.radio

import io.github.zyrouge.symphony.LoopMode
import io.github.zyrouge.symphony.Symphony
import io.github.zyrouge.symphony.copy
import io.github.zyrouge.symphony.utils.Logger
import io.github.zyrouge.symphony.utils.concurrentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class RadioQueue(private val symphony: Symphony, private val scope: CoroutineScope) {

    val originalQueue = concurrentListOf<String>()
    val currentQueue = concurrentListOf<String>()

    var currentSongIndex = -1
        internal set(value) {
            field = value
            symphony.radio.onUpdate.dispatch(Radio.Events.Queue.IndexChanged)
        }

    val currentShuffleMode: StateFlow<Boolean> = symphony.settings.data
            .map { it.playbackOptions.shuffle }
            .distinctUntilChanged()
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = runBlocking { symphony.settings.data.first().playbackOptions.shuffle }
            )

    val currentLoopMode: StateFlow<LoopMode> =
        symphony.settings.data
            .map { it.playbackOptions.loopMode }
            .distinctUntilChanged()
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = runBlocking { symphony.settings.data.first().playbackOptions.loopMode }
            )

    fun setLoopMode(newMode: LoopMode) {
        scope.launch {
            symphony.settings.updateData {
                it.copy { playbackOptions = playbackOptions.copy { loopMode = newMode } }
            }
        }
        Logger.warn("RadioQueue", "setLoopMode -> wrote $newMode")
    }

    fun toggleLoopMode() {
        val order = listOf(LoopMode.OFF, LoopMode.QUEUE, LoopMode.SINGLE_SONG)
        val next = order[(order.indexOf(currentLoopMode.value) + 1) % order.size]
        setLoopMode(next)
    }

    val currentSongId: String?
        get() = getSongIdAt(currentSongIndex)

    fun hasSongAt(index: Int) = index > -1 && index < currentQueue.size
    fun getSongIdAt(index: Int): String? {
        if (index < 0) return null
        return try {
            currentQueue[index]
        } catch (_: IndexOutOfBoundsException) {
            null
        }
    }

    fun reset() {
        originalQueue.clear()
        currentQueue.clear()
        currentSongIndex = -1
        symphony.radio.onUpdate.dispatch(Radio.Events.Queue.Cleared)
    }

    fun add(
        songIds: List<String>,
        index: Int? = null,
        options: Radio.PlayOptions = Radio.PlayOptions(),
    ) {
        index?.let {
            originalQueue.addAll(it, songIds)
            currentQueue.addAll(it, songIds)
            if (it <= currentSongIndex) {
                currentSongIndex += songIds.size
            }
        } ?: run {
            originalQueue.addAll(songIds)
            currentQueue.addAll(songIds)
        }
        afterAdd(options)
    }

    fun add(
        songId: String,
        index: Int? = null,
        options: Radio.PlayOptions = Radio.PlayOptions(),
    ) = add(listOf(songId), index, options)

    private fun afterAdd(options: Radio.PlayOptions) {
        if (!symphony.radio.hasPlayer) {
            symphony.radio.play(options)
        }
        symphony.radio.onUpdate.dispatch(Radio.Events.Queue.Modified)
    }

    fun remove(index: Int) {
        originalQueue.removeAt(index)
        currentQueue.removeAt(index)
        symphony.radio.onUpdate.dispatch(Radio.Events.Queue.Modified)
        if (currentSongIndex == index) {
            symphony.radio.play(Radio.PlayOptions(index = currentSongIndex))
        } else if (index < currentSongIndex) {
            currentSongIndex--
        }
    }

    fun move(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return
        val item = currentQueue.removeAt(fromIndex)
        currentQueue.add(toIndex, item)
        val originalItem = originalQueue.removeAt(fromIndex)
        originalQueue.add(toIndex, originalItem)
        val newSongIndex = when {
            currentSongIndex == fromIndex -> toIndex
            fromIndex < toIndex && currentSongIndex in (fromIndex + 1)..toIndex -> currentSongIndex - 1
            fromIndex > toIndex && currentSongIndex in toIndex until fromIndex -> currentSongIndex + 1
            else -> currentSongIndex
        }
        if (newSongIndex != currentSongIndex) {
            currentSongIndex = newSongIndex
        }
        symphony.radio.onUpdate.dispatch(Radio.Events.Queue.Modified)
    }

    fun remove(indices: List<Int>) {
        var deflection = 0
        var currentSongRemoved = false
        val sortedIndices = indices.sortedDescending()
        for (i in sortedIndices) {
            val index = i - deflection
            originalQueue.removeAt(index)
            currentQueue.removeAt(index)
            when {
                i < currentSongIndex -> deflection++
                i == currentSongIndex -> currentSongRemoved = true
            }
        }
        currentSongIndex -= deflection
        symphony.radio.onUpdate.dispatch(Radio.Events.Queue.Modified)
        if (currentSongRemoved) {
            symphony.radio.play(Radio.PlayOptions(index = currentSongIndex))
        }
    }



    fun toggleShuffleMode() = setShuffleMode(!currentShuffleMode.value)

    fun setShuffleMode(newShuffleMode: Boolean) {
        scope.launch {
            symphony.settings.updateData {
                it.copy { playbackOptions = playbackOptions.copy { shuffle = newShuffleMode } }
            }
        }
        if (currentQueue.isNotEmpty()) {
            val currentSongId = getSongIdAt(currentSongIndex) ?: getSongIdAt(0)!!
            currentSongIndex = if (newShuffleMode) {
                val newQueue = originalQueue.toMutableList()
                newQueue.removeAt(currentSongIndex)
                newQueue.shuffle()
                newQueue.add(0, currentSongId)
                currentQueue.clear()
                currentQueue.addAll(newQueue)
                0
            } else {
                currentQueue.clear()
                currentQueue.addAll(originalQueue)
                originalQueue.indexOfFirst { it == currentSongId }
            }
        }
        symphony.radio.onUpdate.dispatch(Radio.Events.Queue.Modified)
    }

    fun isEmpty() = originalQueue.isEmpty()

    data class Serialized(
        val currentSongIndex: Int,
        val playedDuration: Long,
        val originalQueue: List<String>,
        val currentQueue: List<String>,
        val shuffled: Boolean,
    ) {
        fun serialize() =
            listOf(
                currentSongIndex.toString(),
                playedDuration.toString(),
                originalQueue.joinToString(","),
                currentQueue.joinToString(","),
                shuffled.toString(),
            ).joinToString(";")

        companion object {
            fun create(queue: RadioQueue, playbackPosition: RadioPlayer.PlaybackPosition) =
                Serialized(
                    currentSongIndex = queue.currentSongIndex,
                    playedDuration = playbackPosition.played,
                    originalQueue = queue.originalQueue.toList(),
                    currentQueue = queue.currentQueue.toList(),
                    shuffled = queue.currentShuffleMode.value,
                )

            fun parse(data: String): Serialized? {
                try {
                    val semi = data.split(";")
                    return Serialized(
                        currentSongIndex = semi[0].toInt(),
                        playedDuration = semi[1].toLong(),
                        originalQueue = semi[2].split(","),
                        currentQueue = semi[3].split(","),
                        shuffled = semi[4].toBoolean(),
                    )
                } catch (_: Exception) {
                }
                return null
            }
        }
    }

    fun restore(serialized: Serialized) {
        if (serialized.originalQueue.isNotEmpty()) {
            symphony.radio.stop(ended = false)
            originalQueue.clear()
            originalQueue.addAll(serialized.originalQueue)
            currentQueue.clear()
            currentQueue.addAll(serialized.currentQueue)
            symphony.radio.onUpdate.dispatch(Radio.Events.Queue.Modified)

            scope.launch {
                symphony.settings.updateData {
                    it.copy { playbackOptions = playbackOptions.copy { shuffle = serialized.shuffled } }
                }
            }

            afterAdd(
                Radio.PlayOptions(
                    index = serialized.currentSongIndex,
                    autostart = false,
                    startPosition = serialized.playedDuration,
                )
            )
        }
    }
}
