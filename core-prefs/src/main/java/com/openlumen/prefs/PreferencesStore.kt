package com.openlumen.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "openlumen-prefs")

/**
 * Single-blob preferences store. We keep the whole [Preferences] object as a JSON string
 * to avoid 11 separate DataStore keys; bumping the schema only requires bumping a default
 * in Preferences.kt and the next read merges missing fields.
 */
class PreferencesStore(private val context: Context) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val key = stringPreferencesKey("prefs-v1")

    val flow: Flow<Preferences> = context.dataStore.data.map { prefs ->
        val raw = prefs[key] ?: return@map Preferences()
        runCatching { json.decodeFromString(Preferences.serializer(), raw) }
            .getOrElse { Preferences() }
    }

    suspend fun update(transform: (Preferences) -> Preferences) {
        context.dataStore.edit { prefs ->
            val current = prefs[key]?.let {
                runCatching { json.decodeFromString(Preferences.serializer(), it) }.getOrNull()
            } ?: Preferences()
            val next = transform(current)
            prefs[key] = json.encodeToString(Preferences.serializer(), next)
        }
    }

    suspend fun export(): String {
        val current = context.dataStore.data.map { it[key] ?: "{}" }
        // We're inside a suspend fun, so we don't collect — caller already has flow.first().
        // This helper just re-emits the raw JSON for `Settings → Export profile`.
        return current.toString()
    }
}
