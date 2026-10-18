package com.blefinder.android.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class Saved(val pinned: Set<String> = emptySet(), val names: Map<String, String> = emptyMap()) {
    fun nameFor(address: String, systemName: String): String = names[address] ?: systemName
}

private val Context.savedStore by preferencesDataStore("saved_devices")

/** Pins and custom names, keyed by device address. */
class SavedDevices(context: Context) {
    private val store = context.applicationContext.savedStore

    val state: Flow<Saved> = store.data.map { prefs ->
        Saved(
            pinned = prefs[PINNED].orEmpty(),
            names = prefs.asMap().entries
                .filter { it.key.name.startsWith(NAME_PREFIX) }
                .associate { it.key.name.removePrefix(NAME_PREFIX) to it.value as String },
        )
    }

    suspend fun setPinned(address: String, pinned: Boolean) {
        store.edit { prefs ->
            val current = prefs[PINNED].orEmpty()
            prefs[PINNED] = if (pinned) current + address else current - address
        }
    }

    /** A blank name goes back to the name Android reports. */
    suspend fun rename(address: String, name: String) {
        store.edit { prefs ->
            val key = stringPreferencesKey(NAME_PREFIX + address)
            if (name.isBlank()) prefs.remove(key) else prefs[key] = name.trim()
        }
    }

    private companion object {
        val PINNED = stringSetPreferencesKey("pinned")
        const val NAME_PREFIX = "name:"
    }
}
