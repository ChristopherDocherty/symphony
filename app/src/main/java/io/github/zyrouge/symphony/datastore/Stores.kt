package io.github.zyrouge.symphony.datastore

import android.content.Context
import androidx.datastore.dataStore
import androidx.datastore.core.DataStore
import io.github.zyrouge.symphony.Settings

val Context.settingsDataStore: DataStore<Settings> by dataStore(
    fileName = "settings.pb",
    serializer = SettingsSerializer
)
