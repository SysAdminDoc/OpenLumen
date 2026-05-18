package com.openlumen.prefs

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean
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

internal const val MAX_IMPORT_FILE_BYTES = 64 * 1024

internal fun readImportBytes(input: InputStream, maxBytes: Int = MAX_IMPORT_FILE_BYTES): ByteArray {
    require(maxBytes >= 0) { "maxBytes must be non-negative" }
    val out = ByteArrayOutputStream(minOf(maxBytes, 4096))
    val buffer = ByteArray(4096)
    var total = 0
    while (true) {
        val bytesUntilFailure = maxBytes - total + 1
        val n = input.read(buffer, 0, minOf(buffer.size, bytesUntilFailure))
        if (n < 0) break
        total += n
        if (total > maxBytes) {
            error("Import file exceeds $maxBytes bytes")
        }
        out.write(buffer, 0, n)
    }
    return out.toByteArray()
}

data class ImportSummary(
    val preferences: Preferences,
    val droppedDuplicateNames: List<String>
)

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
    suspend fun importFrom(uri: Uri): Result<ImportSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val text = readAllFromUri(uri)
            val imported = decodeWithMigration(text)
            var appliedSummary: ImportSummary? = null
            update { current ->
                sanitizeImport(imported, enabled = current.enabled).also { summary ->
                    appliedSummary = summary
                }.preferences
            }
            checkNotNull(appliedSummary) { "Import summary was not produced" }
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
    suspend fun previewImport(uri: Uri): Result<ImportSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val text = readAllFromUri(uri)
            sanitizeImport(decodeWithMigration(text))
        }
    }

    private fun readAllFromUri(uri: Uri): String {
        return context.contentResolver.openInputStream(uri).use { input ->
            checkNotNull(input) { "openInputStream returned null for $uri" }
            readImportBytes(input).toString(Charsets.UTF_8)
        }
    }

    private fun decodeOrDefault(raw: String): Preferences =
        runCatching { decodeWithMigration(raw) }
            .getOrElse { t ->
                // Decode failure here means the persisted JSON is corrupted —
                // most likely cause is a power loss mid-write, an OEM backup
                // restoration mishap, or a developer who hand-edited the
                // DataStore file. We don't surface this to the user
                // (recovering to defaults is the right behavior) but we DO
                // log it once per process so a contributor pulling a driver
                // report has a breadcrumb instead of "config inexplicably
                // reset itself". Silent defaulting was the previous
                // behavior, which made this class of bug invisible.
                if (decodeFailureLogged.compareAndSet(false, true)) {
                    Log.w(
                        TAG,
                        "persisted prefs decode failed (${t.javaClass.simpleName}); " +
                            "reverting to defaults: ${t.message?.take(120)}"
                    )
                }
                Preferences()
            }

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
        previousPresetKey = p.previousPresetKey?.let { sanitizePresetKeyOrNull(it) },
        presetIntensity = p.presetIntensity.finiteIn(0f, 1f, default = 1f),
        dim = p.dim.finiteIn(0f, 0.95f, default = 0f),
        customMatrix = sanitizeMatrix(p.customMatrix),
        schedule = sanitizeSchedule(p.schedule),
        lightSensorLuxThreshold = p.lightSensorLuxThreshold.finiteIn(0f, 200f, default = 2f),
        favoritePresetKeys = sanitizeFavorites(p.favoritePresetKeys),
        transitionDurationMs = p.transitionDurationMs.coerceIn(0L, Preferences.TRANSITION_MAX_MS),
        contrast = p.contrast.finiteIn(Preferences.CONTRAST_MIN, Preferences.CONTRAST_MAX, default = 1.0f),
        savedProfiles = sanitizeProfiles(p.savedProfiles)
    )

    private fun sanitizeImport(p: Preferences, enabled: Boolean = p.enabled): ImportSummary =
        ImportSummary(
            preferences = sanitize(p, enabled = enabled),
            droppedDuplicateNames = droppedDuplicateProfileNames(p.savedProfiles)
        )

    /**
     * Clamp every field of [m] into its valid range, replacing
     * NaN/Infinity with the channel default. Used both for the top-level
     * [Preferences.customMatrix] and for every snapshot inside
     * [Preferences.savedProfiles] — defense-in-depth so an imported
     * profile can't carry NaN values that only surface when the user
     * later loads it.
     */
    private fun sanitizeMatrix(m: MatrixDto): MatrixDto = m.copy(
        r = m.r.finiteIn(0f, 1f, default = 1f),
        g = m.g.finiteIn(0f, 1f, default = 0.78f),
        b = m.b.finiteIn(0f, 1f, default = 0.55f),
        biasR = m.biasR.finiteIn(-1f, 1f, default = 0f),
        biasG = m.biasG.finiteIn(-1f, 1f, default = 0f),
        biasB = m.biasB.finiteIn(-1f, 1f, default = 0f),
        dim = m.dim.finiteIn(0f, 0.95f, default = 0f),
        gammaR = m.gammaR.finiteIn(0.1f, 5f, default = 1f),
        gammaG = m.gammaG.finiteIn(0.1f, 5f, default = 1f),
        gammaB = m.gammaB.finiteIn(0.1f, 5f, default = 1f),
        matrixRr = m.matrixRr.finiteIn(MATRIX_COEFF_MIN, MATRIX_COEFF_MAX, default = 1f),
        matrixRg = m.matrixRg.finiteIn(MATRIX_COEFF_MIN, MATRIX_COEFF_MAX, default = 0f),
        matrixRb = m.matrixRb.finiteIn(MATRIX_COEFF_MIN, MATRIX_COEFF_MAX, default = 0f),
        matrixGr = m.matrixGr.finiteIn(MATRIX_COEFF_MIN, MATRIX_COEFF_MAX, default = 0f),
        matrixGg = m.matrixGg.finiteIn(MATRIX_COEFF_MIN, MATRIX_COEFF_MAX, default = 1f),
        matrixGb = m.matrixGb.finiteIn(MATRIX_COEFF_MIN, MATRIX_COEFF_MAX, default = 0f),
        matrixBr = m.matrixBr.finiteIn(MATRIX_COEFF_MIN, MATRIX_COEFF_MAX, default = 0f),
        matrixBg = m.matrixBg.finiteIn(MATRIX_COEFF_MIN, MATRIX_COEFF_MAX, default = 0f),
        matrixBb = m.matrixBb.finiteIn(MATRIX_COEFF_MIN, MATRIX_COEFF_MAX, default = 1f)
    )

    private fun sanitizeSchedule(s: ScheduleDto): ScheduleDto = s.copy(
        startHour = s.startHour.coerceIn(0, 23),
        startMinute = s.startMinute.coerceIn(0, 59),
        endHour = s.endHour.coerceIn(0, 23),
        endMinute = s.endMinute.coerceIn(0, 59),
        latitude = s.latitude.finiteInOrNull(-90.0, 90.0),
        longitude = s.longitude.finiteInOrNull(-180.0, 180.0),
        sunsetOffsetMin = s.sunsetOffsetMin.coerceIn(-180, 180),
        sunriseOffsetMin = s.sunriseOffsetMin.coerceIn(-180, 180)
    )

    private fun sanitizePresetKey(key: String): String =
        sanitizePresetKeyOrNull(key) ?: "custom"

    private fun sanitizePresetKeyOrNull(key: String): String? =
        key.takeIf { it.isNotBlank() && it.length <= 64 && it.none { ch -> ch.isISOControl() } }

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

    /**
     * Bounds: name non-blank + ≤ 48 chars; library size capped at 32
     * entries; duplicate names drop earlier occurrences (last-write-wins,
     * matching `Profiles.saveCurrentAs` semantics).
     *
     * Defense-in-depth: each snapshot's matrix, schedule, intensity, dim,
     * contrast, transition, lux threshold, and preset keys are clamped
     * here so a maliciously crafted imported profile blob can't carry
     * NaN/out-of-range values that only blow up when the user later
     * loads the profile.
     */
    private fun sanitizeProfiles(list: List<NamedProfile>): List<NamedProfile> {
        if (list.isEmpty()) return list
        val seen = mutableSetOf<String>()
        return list.asReversed()
            .asSequence()
            .mapNotNull { p ->
                val name = sanitizedProfileNameOrNull(p.name)
                if (name == null || !seen.add(name)) null
                else p.copy(name = name, snapshot = sanitizeSnapshot(p.snapshot))
            }
            .take(Preferences.MAX_PROFILES)
            .toList()
            .asReversed()
    }

    private fun sanitizeSnapshot(s: ProfileSnapshot): ProfileSnapshot = s.copy(
        activePresetKey = sanitizePresetKey(s.activePresetKey),
        customMatrix = sanitizeMatrix(s.customMatrix),
        presetIntensity = s.presetIntensity.finiteIn(0f, 1f, default = 1f),
        dim = s.dim.finiteIn(0f, 0.95f, default = 0f),
        schedule = sanitizeSchedule(s.schedule),
        lightSensorLuxThreshold = s.lightSensorLuxThreshold.finiteIn(0f, 200f, default = 2f),
        favoritePresetKeys = sanitizeFavorites(s.favoritePresetKeys),
        transitionDurationMs = s.transitionDurationMs.coerceIn(0L, Preferences.TRANSITION_MAX_MS),
        contrast = s.contrast.finiteIn(Preferences.CONTRAST_MIN, Preferences.CONTRAST_MAX, default = 1.0f)
    )

    private fun Float.finiteIn(min: Float, max: Float, default: Float): Float =
        if (isFinite()) coerceIn(min, max) else default

    private fun Double?.finiteInOrNull(min: Double, max: Double): Double? =
        this?.takeIf { it.isFinite() && it in min..max }

    private companion object {
        const val TAG = "OpenLumen/Prefs"
        const val MAX_FAVORITES = 8
        const val MATRIX_COEFF_MIN = -4f
        const val MATRIX_COEFF_MAX = 4f

        /**
         * Process-wide latch so a repeated decode failure (the DataStore
         * flow can re-read the same corrupt blob many times) only writes
         * one log line. Reset on process restart, which is when a clean
         * blob would be re-written anyway.
         */
        val decodeFailureLogged = AtomicBoolean(false)
    }
}

internal fun droppedDuplicateProfileNames(list: List<NamedProfile>): List<String> {
    if (list.size < 2) return emptyList()
    val keptFromEnd = mutableSetOf<String>()
    val droppedEarlier = mutableListOf<String>()
    list.asReversed()
        .asSequence()
        .mapNotNull { sanitizedProfileNameOrNull(it.name) }
        .forEach { name ->
            if (!keptFromEnd.add(name)) {
                droppedEarlier += name
            }
        }
    return droppedEarlier.asReversed().distinct()
}

private fun sanitizedProfileNameOrNull(raw: String): String? =
    raw.trim()
        .take(Preferences.MAX_PROFILE_NAME_LENGTH)
        .takeIf { it.isNotBlank() }
