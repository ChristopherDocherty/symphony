package io.github.zyrouge.symphony.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import android.util.Log // For logging
import androidx.lifecycle.ViewModelProvider
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import io.github.zyrouge.symphony.copy


class AlbumGridViewModel(
    private val context: ViewContext,
) : ViewModel() {

    fun saveScrollOffset(offset: Float) {
        viewModelScope.launch {
            Log.d("AlbumGridViewModel", "Saving scroll offset: $offset")
            try {
                    context.symphony.settings.updateData {
                            currentSettings ->
                        currentSettings.copy {
                            albumGridScrollOffsetY = offset
                        }
                    }

                Log.d("AlbumGridViewModel", "Scroll offset save attempt completed successfully for offset: $offset")
            } catch (e: Exception) {
                Log.e("AlbumGridViewModel", "Failed to save scroll offset: $offset", e)
            }
        }
    }
}

class AlbumGridViewModelFactory(
    private val viewContext: ViewContext,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AlbumGridViewModel::class.java)) {
            return AlbumGridViewModel(viewContext) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}