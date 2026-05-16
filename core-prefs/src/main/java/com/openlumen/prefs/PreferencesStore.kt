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

    /**
     * Reads from the URI returned by ACTION_OPEN_DOCUMENT and replaces the active prefs.
     *
     * Defensive: an imported file might be corrupted or malicious. We:
     *  - Cap the read at 64 KB so a giant blob can't OOM us.
     *  - Decode with `ignoreUnknownKeys` (already set).
     *  - Clamp every numeric field into its valid range before persisting.
     *  - Always preserve the user's current `enabled` state — importing should not
     *    silently flip the filter on/off behind the user's back.
     */
    suspend fun importFrom(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri).use { input ->
                checkNotNull(input) { "openInputStream returned null for $uri" }
                val sb = StringBuilder()
                input.bufferedReader(Charsets.UTF_8).use { reader ->
                    val buf = CharArray(4096)
                    while (true) {
                        val n = reader.read(buf)
                        if (n < 0) break
                        sb.append(buf, 0, n)
                        if (sb.length > MAX_IMPORT_BYTES) {
                            error("Import file exceeds ${MAX_IMPORT_BYTES} bytes")
                        }
                    }
                    sb.toString()
                }
            }
            val raw = json.decodeFromString(Preferences.serializer(), text)
            update { current -> sanitize(raw, preserveEnabled = current.enabled) }
        }
    }

    private fun sanitize(p: Preferences, preserveEnabled: Boolean): Preferences = p.copy(
        enabled = preserveEnabled,
        presetIntensity = p.presetIntensity.coerceIn(0f, 1f),
        dim = p.dim.coerceIn(0f, 0.95f),
        customMatrix = p.customMatrix.copy(
            r = p.customMatrix.r.coerceIn(0f, 1f),
            g = p.customMatrix.g.coerceIn(0f, 1f),
            b = p.customMatrix.b.coerceIn(0f, 1f),
            biasR = p.customMatrix.biasR.coerceIn(-1f, 1f),
            biasG = p.customMatrix.biasG.coerceIn(-1f, 1f),
            biasB = p.customMatrix.biasB.coerceIn(-1f, 1f),
            dim = p.customMatrix.dim.coerceIn(0f, 0.95f),
            gammaR = p.customMatrix.gammaR.coerceIn(0.1f, 5f),
            gammaG = p.customMatrix.gammaG.coerceIn(0.1f, 5f),
            gammaB = p.customMatrix.gammaB.coerceIn(0.1f, 5f)
        ),
        schedule = p.schedule.copy(
            startHour = p.schedule.startHour.coerceIn(0, 23),
            startMinute = p.schedule.startMinute.coerceIn(0, 59),
            endHour = p.schedule.endHour.coerceIn(0, 23),
            endMinute = p.schedule.endMinute.coerceIn(0, 59),
            latitude = if (p.schedule.latitude in -90.0..90.0) p.schedule.latitude else Double.NaN,
            longitude = if (p.schedule.longitude in -180.0..180.0) p.schedule.longitude else Double.NaN,
            sunsetOffsetMin = p.schedule.sunsetOffsetMin.coerceIn(-180, 180),
            sunriseOffsetMin = p.schedule.sunriseOffsetMin.coerceIn(-180, 180)
        ),
        lightSensorLuxThreshold = p.lightSensorLuxThreshold.coerceAtLeast(0f)
    )

    private companion object {
        const val MAX_IMPORT_BYTES = 64 * 1024
    }
}
