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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

private val Context.dataStore by preferencesDataStore(name = "openlumen-prefs")

/**
 * Single-blob preferences store. We keep the whole [Preferences] object as a JSON string
 * to avoid N separate DataStore keys; bumping the schema only requires adding a new
 * field with a default in Preferences.kt and the next read merges missing fields (kotlinx
 * .serialization with `ignoreUnknownKeys = true` also tolerates removed fields).
 *
 * Versioned migrations (C29) run on every read and every import via
 * [PreferencesMigrations.migrate]. A pre-C29 blob has no `schemaVersion` key
 * in its JSON; we detect that at the JSON layer and pass `schemaVersion = 0`
 * into the migration runner so it can stamp it correctly.
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
        decodeOrDefault(raw)
    }

    suspend fun update(transform: (Preferences) -> Preferences) {
        context.dataStore.edit { prefs ->
            val current = prefs[key]?.let { decodeOrDefault(it) } ?: Preferences()
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
     *  - Run schema migrations against the imported blob.
     *  - Clamp every numeric field into its valid range before persisting.
     *  - Always preserve the user's current `enabled` state — importing should not
     *    silently flip the filter on/off behind the user's back.
     */
    suspend fun importFrom(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val text = readAllFromUri(uri)
            val imported = decodeWithMigration(text)
            update { current -> sanitize(imported, enabled = current.enabled) }
        }
    }

    /**
     * Decode a profile from a URI without applying it. Tied to roadmap candidate
     * **C30** (Profile import preview). The UI uses this to show the user
     * exactly what would change before committing.
     *
     * Same safety properties as [importFrom] — size-capped read, migrations,
     * sanitization. The caller is responsible for handing the returned object
     * to [update] (or discarding it) based on user confirmation.
     */
    suspend fun previewImport(uri: Uri): Result<Preferences> = withContext(Dispatchers.IO) {
        runCatching {
            val text = readAllFromUri(uri)
            sanitize(decodeWithMigration(text))
        }
    }

    private fun readAllFromUri(uri: Uri): String {
        return context.contentResolver.openInputStream(uri).use { input ->
            checkNotNull(input) { "openInputStream returned null for $uri" }
            val sb = StringBuilder()
            input.bufferedReader(Charsets.UTF_8).use { reader ->
                val buf = CharArray(4096)
                while (true) {
                    val n = reader.read(buf)
                    if (n < 0) break
                    sb.append(buf, 0, n)
                    if (sb.length > MAX_IMPORT_BYTES) {
                        error("Import file exceeds $MAX_IMPORT_BYTES bytes")
                    }
                }
                sb.toString()
            }
        }
    }

    private fun decodeOrDefault(raw: String): Preferences =
        runCatching { decodeWithMigration(raw) }
            .getOrElse { Preferences() }

    /**
     * Decode JSON and run schema migrations. The schema version detection is
     * done at the raw-JSON layer so a pre-C29 blob (no `schemaVersion` field)
     * reaches the migration runner as v0 rather than the default-stamped v1.
     */
    private fun decodeWithMigration(raw: String): Preferences {
        val element = json.parseToJsonElement(raw)
        val schemaVersionOnDisk: Int = (element as? JsonObject)
            ?.get("schemaVersion")
            ?.let { it as? JsonPrimitive }
            ?.let { it.intOrNull }
            ?: 0
        val decoded = json.decodeFromString(Preferences.serializer(), raw)
        val versioned = if (schemaVersionOnDisk == 0) decoded.copy(schemaVersion = 0) else decoded
        return sanitize(PreferencesMigrations.migrate(versioned))
    }

    private fun sanitize(p: Preferences, enabled: Boolean = p.enabled): Preferences = p.copy(
        enabled = enabled,
        schemaVersion = if (p.schemaVersion <= 0) Preferences.CURRENT_SCHEMA_VERSION else p.schemaVersion,
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
        lightSensorLuxThreshold = p.lightSensorLuxThreshold.finiteIn(0f, 200f, default = 2f),
        favoritePresetKeys = sanitizeFavorites(p.favoritePresetKeys),
        transitionDurationMs = p.transitionDurationMs.coerceIn(0L, Preferences.TRANSITION_MAX_MS)
    )

    private fun sanitizePresetKey(key: String): String =
        key.takeIf { it.isNotBlank() && it.length <= 64 && it.none { ch -> ch.isISOControl() } }
            ?: "custom"

    /**
     * Favorites list is bounded and de-duplicated. We don't cross-check against
     * the live preset catalog here because that would couple core-prefs to
     * core-engine; use-site code in the app validates against
     * `com.openlumen.engine.Presets.byKey()` when consuming the list.
     */
    private fun sanitizeFavorites(keys: List<String>): List<String> =
        keys.asSequence()
            .map { sanitizePresetKey(it) }
            .distinct()
            .take(MAX_FAVORITES)
            .toList()

    private fun Float.finiteIn(min: Float, max: Float, default: Float): Float =
        if (isFinite()) coerceIn(min, max) else default

    private fun Double?.finiteInOrNull(min: Double, max: Double): Double? =
        this?.takeIf { it.isFinite() && it in min..max }

    private companion object {
        const val MAX_IMPORT_BYTES = 64 * 1024
        const val MAX_FAVORITES = 8
    }
}
