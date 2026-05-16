package com.openlumen.prefs

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "openlumen-prefs")

/**
 * Single-blob preferences store. We keep the whole [Preferences] object as a JSON string
 * to avoid N separate DataStore keys; bumping the schema only requires adding a new
 * field with a default in Preferences.kt and the next read merges missing fields (kotlinx
 * .serialization with `ignoreUnknownKeys = true` also tolerates removed fields).
 */
class PreferencesStore(private val context: Context) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
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

    /** Pretty-prints the current preferences to the URI returned by ACTION_CREATE_DOCUMENT. */
    suspend fun exportTo(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val current = flow.first()
            val body = json.encodeToString(Preferences.serializer(), current)
            context.contentResolver.openOutputStream(uri, "wt").use { out ->
                checkNotNull(out) { "openOutputStream returned null for $uri" }
                out.write(body.toByteArray(Charsets.UTF_8))
            }
        }
    }

    /** Reads from the URI returned by ACTION_OPEN_DOCUMENT and replaces the active prefs. */
    suspend fun importFrom(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri).use { input ->
                checkNotNull(input) { "openInputStream returned null for $uri" }
                input.bufferedReader(Charsets.UTF_8).readText()
            }
            val imported = json.decodeFromString(Preferences.serializer(), text)
            update { imported }
        }
    }
}
