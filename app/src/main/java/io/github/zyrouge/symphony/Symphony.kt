package io.github.zyrouge.symphony

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.datastore.core.DataStore
import io.github.zyrouge.symphony.datastore.SettingsDefaults
import io.github.zyrouge.symphony.datastore.SettingsMigration
import io.github.zyrouge.symphony.datastore.settingsDataStore
import io.github.zyrouge.symphony.services.Permissions
import io.github.zyrouge.symphony.services.database.Database
import io.github.zyrouge.symphony.services.groove.Groove
import io.github.zyrouge.symphony.services.lastfm.LastFmBackupService
import io.github.zyrouge.symphony.services.lastfm.LastFmService
import io.github.zyrouge.symphony.services.radio.Radio
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class Symphony(application: Application) : AndroidViewModel(application), Symphony.Hooks {
    interface Hooks {
        fun onSymphonyReady() {}
        fun onSymphonyDestroy() {}
        fun onSymphonyActivityReady() {}
        fun onSymphonyActivityPause() {}
        fun onSymphonyActivityDestroy() {}
    }

    val permission = Permissions(this)

    /** Proto DataStore — use for writes: [settings].updateData { } */
    val settings: DataStore<Settings> = applicationContext.settingsDataStore

    /**
     * Eagerly-collected StateFlow of the current settings.
     * Use for synchronous reads in service code: [settingsState].value.fieldName
     * Use for reactive reads in Compose: [settingsState].map { it.fieldName }.collectAsState()
     *
     * Starts with [SettingsDefaults.INSTANCE] (all correct defaults) until the
     * DataStore has loaded from disk, which typically happens within a few ms.
     */
    val settingsState: StateFlow<Settings> = applicationContext.settingsDataStore.data
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsDefaults.INSTANCE)

    val database = Database(this)
    val groove = Groove(this)
    val radio = Radio(this)
    val lastFm = LastFmService(this)
    val lastFmBackup = LastFmBackupService(this)

    val applicationContext get() = getApplication<Application>().applicationContext
    var closeApp: (() -> Unit)? = null
    private var isReady = false
    private var hooks = listOf(this, radio, groove, lastFm, lastFmBackup)

    init {
        viewModelScope.launch {
            SettingsMigration.migrate(applicationContext)
        }
    }

    internal fun emitReady() {
        if (isReady) {
            return
        }
        isReady = true
        notifyHooks { onSymphonyReady() }
    }

    internal fun emitDestroy() {
        notifyHooks { onSymphonyDestroy() }
    }

    internal fun emitActivityReady() {
        emitReady()
        notifyHooks { onSymphonyActivityReady() }
    }

    internal fun emitActivityPause() {
        notifyHooks { onSymphonyActivityPause() }
    }

    internal fun emitActivityDestroy() {
        notifyHooks { onSymphonyActivityDestroy() }
    }

    override fun onCleared() {
        super.onCleared()
        emitDestroy()
    }

    private fun notifyHooks(fn: Hooks.() -> Unit) {
        hooks.forEach { fn.invoke(it) }
    }
}
