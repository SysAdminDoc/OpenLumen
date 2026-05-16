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
        // Backward tolerance for early builds that used NaN as the "location unset"
        // sentinel. New writes use null for unset coordinates so exported profiles
        // remain normal JSON.
        allowSpecialFloatingPointValues = true
        encodeDefaults = true
        prettyPrint = true
    }
    private val key = stringPreferencesKey("prefs-v1")

    val flow: Flow<Preferences> = context.dataStore.data.map { prefs ->
        val raw = prefs[key] ?: return@map Preferences()
        runCatching { json.decodeFromString(Preferences.serializer(), raw) }
            .map { sanitize(it) }
            .getOrElse { Preferences() }
    }

    suspend fun update(transform: (Preferences) -> Preferences) {
        context.dataStore.edit { prefs ->
            val current = prefs[key]?.let {
                runCatching { json.decodeFromString(Preferences.serializer(), it) }.getOrNull()
            }?.let { sanitize(it) } ?: Preferences()
            val next = sanitize(transform(current))
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
            update { current -> sanitize(raw, enabled = current.enabled) }
        }
    }

    private fun sanitize(p: Preferences, enabled: Boolean = p.enabled): Preferences = p.copy(
        enabled = enabled,
        activePresetKey = sanitizePresetKey(p.activePresetKey),
        presetIntensity = p.presetIntensity.finiteIn(0f, 1f, default = 1f),
        dim = p.dim.finiteIn(0f, 0.95f, default = 0f),
        customMatrix = p.customMatrix.copy(
            r = p.customMatrix.r.finiteIn(0f, 1f, default = 1f),
            g = p.customMatrix.g.finiteIn(0f, 1f, default = 0.78f),
            b = p.customMatrix.b.finiteIn(0f, 1f, default = 0.55f),
            biasR = p.customMatrix.biasR.finiteIn(-1f, 1f, default = 0f),
            biasG = p.customMatrix.biasG.finiteIn(-1f, 1f, default = 0f),
            biasB = p.customMatrix.biasB.finiteIn(-1f, 1f, default = 0f),
            dim = p.customMatrix.dim.finiteIn(0f, 0.95f, default = 0f),
            gammaR = p.customMatrix.gammaR.finiteIn(0.1f, 5f, default = 1f),
            gammaG = p.customMatrix.gammaG.finiteIn(0.1f, 5f, default = 1f),
            gammaB = p.customMatrix.gammaB.finiteIn(0.1f, 5f, default = 1f)
        ),
        schedule = p.schedule.copy(
            startHour = p.schedule.startHour.coerceIn(0, 23),
            startMinute = p.schedule.startMinute.coerceIn(0, 59),
            endHour = p.schedule.endHour.coerceIn(0, 23),
            endMinute = p.schedule.endMinute.coerceIn(0, 59),
            latitude = p.schedule.latitude.finiteInOrNull(-90.0, 90.0),
            longitude = p.schedule.longitude.finiteInOrNull(-180.0, 180.0),
            sunsetOffsetMin = p.schedule.sunsetOffsetMin.coerceIn(-180, 180),
            sunriseOffsetMin = p.schedule.sunriseOffsetMin.coerceIn(-180, 180)
        ),
        lightSensorLuxThreshold = p.lightSensorLuxThreshold.finiteIn(0f, 200f, default = 2f)
    )

    private fun sanitizePresetKey(key: String): String =
        key.takeIf { it.isNotBlank() && it.length <= 64 && it.none { ch -> ch.isISOControl() } }
            ?: "custom"

    private fun Float.finiteIn(min: Float, max: Float, default: Float): Float =
        if (isFinite()) coerceIn(min, max) else default

    private fun Double?.finiteInOrNull(min: Double, max: Double): Double? =
        this?.takeIf { it.isFinite() && it in min..max }

    private companion object {
        const val MAX_IMPORT_BYTES = 64 * 1024
    }
}
