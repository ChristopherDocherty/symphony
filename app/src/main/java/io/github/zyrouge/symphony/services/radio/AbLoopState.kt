package io.github.zyrouge.symphony.services.radio

import kotlinx.coroutines.flow.MutableStateFlow

class AbLoopState {
    val isActive = MutableStateFlow(false)
    val startMs = MutableStateFlow(0L)
    val endMs = MutableStateFlow(0L)

    fun activate(songDuration: Long) {
        startMs.value = 0L
        endMs.value = songDuration
        isActive.value = true
    }

    fun deactivate() {
        isActive.value = false
    }
}
