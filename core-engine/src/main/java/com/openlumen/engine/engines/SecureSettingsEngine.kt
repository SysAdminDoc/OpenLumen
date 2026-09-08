package com.openlumen.engine.engines

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.openlumen.engine.ColorEngine
import com.openlumen.engine.Daltonizer
import com.openlumen.engine.EngineCapability
import com.openlumen.engine.EngineKind
import com.openlumen.engine.EngineResult
import com.openlumen.engine.Kelvin
import com.openlumen.engine.LumenMatrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Restores system Night Light only while the observed state still matches the
 * values OpenLumen last wrote. Any external change transfers ownership back to
 * the system and must be left untouched.
 */
internal fun shouldRestoreNightDisplay(
    currentActive: Boolean,
    currentTemperature: Int,
    lastAppliedTemperature: Int
): Boolean = currentActive && currentTemperature == lastAppliedTemperature

/**
 * Same ownership rule for the Extra Dim transform: only hand back a level we
 * are still the author of.
 *
 * [lastAppliedLevel] of 0 means the last thing we wrote was a deactivation, so
 * ownership holds while the transform is still off. Treating that case as
 * "always ours" would let a session that never dimmed overwrite an Extra Dim
 * level the user turned on afterwards.
 */
internal fun shouldRestoreReduceBrightColors(
    currentActive: Boolean,
    currentLevel: Int,
    lastAppliedLevel: Int
): Boolean = if (lastAppliedLevel > 0) {
    currentActive && currentLevel == lastAppliedLevel
} else {
    !currentActive
}

/**
 * Same ownership question for the system colour correction: only put the user's
 * mode back if what is on screen is still the correction this session selected.
 * Anything else means they changed it while the filter was running, and their
 * choice wins.
 *
 * There is deliberately no "we last wrote a deactivation" branch here, unlike
 * [shouldRestoreReduceBrightColors]. Switching the correction off releases
 * ownership of the row on the spot, so a session never reaches [clear] holding
 * a correction it already handed back. A branch that ignored [currentMode]
 * would also overwrite a mode the user picked while the toggle was off, which
 * is a normal thing to do on AOSP's correction screen.
 */
internal fun shouldRestoreColorCorrection(
    currentActive: Boolean,
    currentMode: Int,
    lastAppliedMode: Int
): Boolean = currentActive && currentMode == lastAppliedMode

/**
 * Rootless framework-level driver, written against `Settings.Secure`.
 *
 * ### Why this is not a `ColorDisplayManager` driver any more
 *
 * Releases through 0.7.1 reflected on `android.hardware.display.ColorDisplayManager`
 * and called `setNightDisplayActivated` / `setNightDisplayColorTemperature`. That
 * could never work on a user install. Every setter on
 * `ColorDisplayService.BinderService` carries
 * `@EnforcePermission(CONTROL_DISPLAY_COLOR_TRANSFORMS)`, and that permission is
 * declared `signature|privileged` in `frameworks/base/core/res/AndroidManifest.xml`
 * with no `development` flag, so `pm grant` cannot grant it and a sideloaded app can
 * never hold it. `WRITE_SECURE_SETTINGS` is not an accepted alternative on those
 * calls. The class also first appears at API 29, not 28, and its `@hide` members are
 * hidden-API blocklisted from API 29 onward, so the reflection would have failed
 * before the permission check even mattered.
 *
 * The path that does work is the one `ColorDisplayService` uses internally. It
 * registers `ContentObserver`s on `night_display_activated`,
 * `night_display_color_temperature`, `night_display_auto_mode`,
 * `reduce_bright_colors_activated` and `reduce_bright_colors_level`, and its own
 * privileged binder methods are `Secure.putIntForUser` calls against those same
 * keys. Writing them with `WRITE_SECURE_SETTINGS` — which *is* `pm grant`-able
 * because its protection level includes `development` — produces the identical
 * HWC-level transform. `Settings.Secure.putInt(ContentResolver, String, int)` is
 * public SDK, so there is no reflection and no non-SDK interface involved.
 *
 * Grant with:
 *   `adb shell pm grant com.openlumen android.permission.WRITE_SECURE_SETTINGS`
 *
 * ### Capabilities
 *
 * Night Light carries chromaticity as a colour temperature, so per-channel gamma
 * and arbitrary cross-channel terms cannot ride it. Extra Dim
 * (`reduce_bright_colors_*`, API 31+) carries the dim factor, and unlike the
 * overlay driver it reaches below the panel's minimum backlight without root. It
 * is gated on the device resource `config_reduceBrightColorsAvailable`, so it is
 * probed separately and reported as its own capability state.
 *
 * Grayscale and the colour-vision presets are the exception to the cross-channel
 * limit (C282). `ColorDisplayService` also observes
 * `accessibility_display_daltonizer_enabled` and `accessibility_display_daltonizer`,
 * so this driver selects AOSP's own correction mode for those four presets
 * instead of trying to squeeze them into a temperature. Before that they
 * normalised to white and became a neutral 6500 K Night Light: a visible no-op
 * reported as success. AOSP's matrices are Machado 2009 where OpenLumen's
 * matrix-capable path uses Viénot 1999, so the two do not render identically;
 * [EngineCapability.SYSTEM_COLOR_CORRECTION] is how that is disclosed.
 */
class SecureSettingsEngine : ColorEngine {
    override val kind = EngineKind.COLOR_DISPLAY_MANAGER

    /**
     * Night Light carries one colour temperature, so there are no cross-channel
     * terms and no per-channel curve here. What this driver does have that the
     * root drivers do not is the system's own correction modes, which cover
     * grayscale and colour-vision through AOSP's matrices. Extra Dim supplies
     * sub-minimum dim, but only where the device ships it, so callers should
     * still check `acceptedKeys`.
     */
    override val capabilities: Set<EngineCapability>
        get() = buildSet {
            add(EngineCapability.SYSTEM_COLOR_CORRECTION)
            // Extra Dim is the one capability here a device can lack, and
            // apply already drops the dim silently when it does. Claiming it
            // unconditionally meant the Presets screen promised Deep Sleep and
            // PWM Comfort in full on devices that cannot darken past the panel
            // minimum. An empty accepted set means no probe has run yet rather
            // than a device that refused the rows, and a probe that accepts
            // nothing leaves the driver unavailable, so it keeps the declared
            // shape.
            if (
                acceptedKeys.isEmpty() ||
                KEY_REDUCE_BRIGHT_ACTIVATED in acceptedKeys
            ) {
                add(EngineCapability.SUB_MINIMUM_DIM)
            }
        }

    private val tag = "OpenLumen/SecureSettings"

    @Volatile private var ownership: Ownership? = null

    /**
     * True once this session has written the Extra Dim rows. Sessions that
     * never dim must not touch them at all, in either direction.
     */
    @Volatile private var dimOwned: Boolean = false

    /**
     * True once this session has switched the system colour correction on, and
     * once it has switched Night Light on. Same ownership rule as [dimOwned]:
     * a setting the user had before OpenLumen started is never switched off.
     */
    @Volatile private var correctionOwned: Boolean = false

    @Volatile private var nightOwned: Boolean = false

    /** Last record written to disk, so a ramp does not rewrite it 600 times. */
    @Volatile private var lastPersistedRecord: String? = null

    /**
     * Which of the two transforms the last probe accepted. Read by
     * `DriverReport` so a device report records what this device actually
     * honoured rather than the driver's name.
     */
    @Volatile var acceptedKeys: List<String> = emptyList()
        private set

    override suspend fun isAvailable(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < MIN_API) return@withContext false
        if (!hasSecureSettingsGrant(context)) {
            Log.d(tag, "WRITE_SECURE_SETTINGS not granted; secure-settings driver unavailable")
            acceptedKeys = emptyList()
            return@withContext false
        }
        // Probe by writing each key's *current* value back. That exercises the
        // real permission check and the provider's key allowlist without
        // changing anything the user can see.
        val nightOk = rewriteInPlace(context, KEY_NIGHT_ACTIVATED)
        val dimOk = supportsReduceBrightColors(context) &&
            rewriteInPlace(context, KEY_REDUCE_BRIGHT_ACTIVATED)
        acceptedKeys = buildList {
            if (nightOk) add(KEY_NIGHT_ACTIVATED)
            if (dimOk) add(KEY_REDUCE_BRIGHT_ACTIVATED)
        }
        nightOk
    }

    override suspend fun apply(context: Context, matrix: LumenMatrix): EngineResult =
        withContext(Dispatchers.IO) {
            if (!hasSecureSettingsGrant(context)) {
                Log.w(tag, "apply: WRITE_SECURE_SETTINGS not granted")
                return@withContext EngineResult.Failure("WRITE_SECURE_SETTINGS not granted")
            }

            val temperature = temperatureFor(matrix)
            val dimLevel = reduceBrightColorsLevel(matrix.effectiveDim)
            val dimSupported = supportsReduceBrightColors(context)
            // A grayscale or colour-vision preset has no tint to carry: its
            // chromaticity normalises to white, so asking for a colour
            // temperature produces the neutral 6500 K and switching Night Light
            // on at that temperature changes nothing the user can see. The
            // correction mode is what actually renders those presets here.
            // A preset that names a correction mode is rendered by that mode,
            // not by a colour temperature. The colour-vision presets carry
            // scalar fallbacks for drivers with no cross-channel terms, and
            // those fallbacks are not neutral, so this used to switch Night
            // Light on underneath the correction at a temperature nobody asked
            // for. Protan's fallback reads as cool, which pushed the screen
            // bluer than no filter at all (C298).
            val tinted = matrix.daltonizer == Daltonizer.NONE && !isNeutralChromaticity(matrix)
            val correction = matrix.daltonizer

            try {
                val cr = context.contentResolver
                // A previous process may have died holding these rows. Adopt its
                // record first so the leftover tint is recognised as ours rather
                // than captured as the user's own state (C339).
                if (ownership == null) adoptPersistedOwnership(context)
                val previousNightTemperature = ownership?.lastAppliedTemperature
                val previousCorrectionMode = ownership?.lastAppliedCorrectionMode
                if (ownership == null) {
                    ownership = Ownership(
                        original = SystemState(
                            nightActive = Settings.Secure.getInt(cr, KEY_NIGHT_ACTIVATED, 0) == 1,
                            nightTemperature = Settings.Secure.getInt(
                                cr,
                                KEY_NIGHT_TEMPERATURE,
                                DEFAULT_TEMPERATURE
                            ),
                            nightAutoMode = Settings.Secure.getInt(cr, KEY_NIGHT_AUTO_MODE, 0),
                            dimActive = Settings.Secure.getInt(cr, KEY_REDUCE_BRIGHT_ACTIVATED, 0) == 1,
                            dimLevel = Settings.Secure.getInt(cr, KEY_REDUCE_BRIGHT_LEVEL, 0),
                            correctionActive =
                                Settings.Secure.getInt(cr, KEY_CORRECTION_ENABLED, 0) == 1,
                            correctionMode = Settings.Secure.getInt(
                                cr,
                                KEY_CORRECTION_MODE,
                                Daltonizer.NONE.secureValue
                            )
                        ),
                        lastAppliedTemperature = previousNightTemperature ?: temperature,
                        lastAppliedDimLevel = 0,
                        lastAppliedCorrectionMode =
                            previousCorrectionMode ?: Daltonizer.NONE.secureValue
                    )
                }
                // Every `lastApplied` value below is recorded immediately after
                // the write it describes, never before. Setting them up front
                // meant a write that threw partway left the record claiming a
                // row we had not touched, and the ownership checks then refused
                // to restore anything for the rest of the session.

                if (tinted) {
                    // Taking the row now, so read what the user has right now.
                    // A session hands Night Light back whenever it moves to a
                    // neutral preset, and they are free to change it before the
                    // schedule turns warm again.
                    if (!nightOwned) recaptureNightDisplay(cr)
                    // The system's own sunset/sunrise rule would fight us for the
                    // activation flag, so park it on manual for the duration and
                    // hand the user's choice back when we let go.
                    Settings.Secure.putInt(cr, KEY_NIGHT_AUTO_MODE, AUTO_MODE_MANUAL)
                    Settings.Secure.putInt(cr, KEY_NIGHT_TEMPERATURE, temperature)
                    Settings.Secure.putInt(cr, KEY_NIGHT_ACTIVATED, 1)
                    ownership = ownership?.copy(lastAppliedTemperature = temperature)
                    nightOwned = true
                } else if (nightOwned) {
                    // C293: this used to read `correctionOwned || nightOwned`.
                    // `correctionOwned` says nothing about Night Light, so the
                    // second apply of a neutral preset (a ramp step, a slider,
                    // or the identity apply every schedule-off performs) switched
                    // off a Night Light the user had turned on themselves.
                    handBackNightDisplay(cr)
                    nightOwned = false
                }

                if (correction != Daltonizer.NONE) {
                    if (!correctionOwned) recaptureColorCorrection(cr)
                    Settings.Secure.putInt(cr, KEY_CORRECTION_MODE, correction.secureValue)
                    Settings.Secure.putInt(cr, KEY_CORRECTION_ENABLED, 1)
                    ownership = ownership?.copy(
                        lastAppliedCorrectionMode = correction.secureValue
                    )
                    correctionOwned = true
                } else if (correctionOwned) {
                    // Moving to a preset that names no correction mode. Give the
                    // row back rather than only switching it off: a correction
                    // the user runs for a visual impairment has no reason to
                    // stay off while OpenLumen shows a warm tint, and leaving
                    // our own mode in the row meant re-enabling it in Settings
                    // handed them our choice instead of theirs.
                    handBackColorCorrection(cr)
                    correctionOwned = false
                }

                if (dimSupported) {
                    if (dimLevel > 0) {
                        Settings.Secure.putInt(cr, KEY_REDUCE_BRIGHT_LEVEL, dimLevel)
                        Settings.Secure.putInt(cr, KEY_REDUCE_BRIGHT_ACTIVATED, 1)
                        ownership = ownership?.copy(lastAppliedDimLevel = dimLevel)
                        dimOwned = true
                    } else if (dimOwned) {
                        // Turning off dim we ourselves switched on. If this
                        // session never dimmed, leave the row alone entirely —
                        // writing 0 here would silently disable an Extra Dim
                        // setting the user had on before OpenLumen started.
                        Settings.Secure.putInt(cr, KEY_REDUCE_BRIGHT_ACTIVATED, 0)
                        ownership = ownership?.copy(lastAppliedDimLevel = 0)
                    }
                } else if (dimLevel > 0) {
                    Log.i(tag, "device has no Extra Dim transform; dim is not applied by this driver")
                }

            } catch (t: Throwable) {
                Log.w(tag, "secure-settings apply failed: ${t.message}")
                return@withContext EngineResult.Failure(
                    "secure-settings apply failed: ${t.message ?: "unknown error"}"
                )
            } finally {
                // Even a partial apply has to leave a record: the rows it did
                // write outlive this process and something has to be able to
                // put them back.
                if (ownership != null) persistOwnership(context)
            }
            EngineResult.Success
        }

    override suspend fun clear(context: Context): EngineResult = withContext(Dispatchers.IO) {
        if (!hasSecureSettingsGrant(context)) {
            return@withContext EngineResult.Failure("WRITE_SECURE_SETTINGS not granted")
        }
        // Same reason as apply: without the durable record a process that did
        // not write the tint cannot tell it apart from a setting the user made,
        // so a filter left on by a killed process could never be turned off
        // (C339).
        val owned = ownership
            ?: adoptPersistedOwnership(context)
            ?: return@withContext EngineResult.Success
        try {
            val cr = context.contentResolver
            // Release Night Light through the same helper apply() uses, so the
            // two paths cannot disagree about the ownership question or about
            // writing auto mode before the activation flag. Gated on
            // `nightOwned`: a row this session already handed back is the
            // user's again, and re-seizing it here switched off a Night Light
            // they had turned on since.
            if (nightOwned) {
                handBackNightDisplay(cr)
            } else {
                // apply() parks auto mode on manual while it holds the row, so
                // a hand-back that declined to touch activation can still owe
                // the user their schedule back.
                val currentAutoMode =
                    Settings.Secure.getInt(cr, KEY_NIGHT_AUTO_MODE, AUTO_MODE_MANUAL)
                if (currentAutoMode == AUTO_MODE_MANUAL &&
                    owned.original.nightAutoMode != AUTO_MODE_MANUAL
                ) {
                    Settings.Secure.putInt(cr, KEY_NIGHT_AUTO_MODE, owned.original.nightAutoMode)
                }
            }

            if (dimOwned && supportsReduceBrightColors(context)) {
                val currentDimActive = Settings.Secure.getInt(cr, KEY_REDUCE_BRIGHT_ACTIVATED, 0) == 1
                val currentDimLevel = Settings.Secure.getInt(cr, KEY_REDUCE_BRIGHT_LEVEL, 0)
                if (
                    shouldRestoreReduceBrightColors(
                        currentActive = currentDimActive,
                        currentLevel = currentDimLevel,
                        lastAppliedLevel = owned.lastAppliedDimLevel
                    )
                ) {
                    Settings.Secure.putInt(cr, KEY_REDUCE_BRIGHT_LEVEL, owned.original.dimLevel)
                    Settings.Secure.putInt(
                        cr,
                        KEY_REDUCE_BRIGHT_ACTIVATED,
                        if (owned.original.dimActive) 1 else 0
                    )
                } else {
                    Log.i(tag, "Extra Dim changed outside OpenLumen; leaving it untouched")
                }
            }
            if (correctionOwned) {
                val currentMode =
                    Settings.Secure.getInt(cr, KEY_CORRECTION_MODE, Daltonizer.NONE.secureValue)
                val currentActive = Settings.Secure.getInt(cr, KEY_CORRECTION_ENABLED, 0) == 1
                if (
                    shouldRestoreColorCorrection(
                        currentActive = currentActive,
                        currentMode = currentMode,
                        lastAppliedMode = owned.lastAppliedCorrectionMode
                    )
                ) {
                    Settings.Secure.putInt(cr, KEY_CORRECTION_MODE, owned.original.correctionMode)
                    Settings.Secure.putInt(
                        cr,
                        KEY_CORRECTION_ENABLED,
                        if (owned.original.correctionActive) 1 else 0
                    )
                } else {
                    Log.i(tag, "colour correction changed outside OpenLumen; leaving it untouched")
                }
            }

            ownership = null
            dimOwned = false
            correctionOwned = false
            nightOwned = false
            clearPersistedOwnership(context)
        } catch (t: Throwable) {
            Log.w(tag, "secure-settings clear failed: ${t.message}")
            return@withContext EngineResult.Failure(
                "secure-settings clear failed: ${t.message ?: "unknown error"}"
            )
        }
        EngineResult.Success
    }

    /**
     * Re-read the Night Light rows into the ownership record.
     *
     * The record is captured once when a session first applies anything, but
     * the session gives Night Light back every time it moves to a neutral
     * preset, which the schedule does on its own each morning. Without this the
     * evening re-acquire would still be holding the previous day's snapshot and
     * would write it over whatever the user has set since.
     */
    private fun recaptureNightDisplay(cr: android.content.ContentResolver) {
        ownership = ownership?.let { owned ->
            owned.copy(
                original = owned.original.copy(
                    nightActive = Settings.Secure.getInt(cr, KEY_NIGHT_ACTIVATED, 0) == 1,
                    nightTemperature = Settings.Secure.getInt(
                        cr,
                        KEY_NIGHT_TEMPERATURE,
                        DEFAULT_TEMPERATURE
                    ),
                    nightAutoMode = Settings.Secure.getInt(cr, KEY_NIGHT_AUTO_MODE, 0)
                )
            )
        }
    }

    /** Same, for the correction rows. */
    private fun recaptureColorCorrection(cr: android.content.ContentResolver) {
        ownership = ownership?.let { owned ->
            owned.copy(
                original = owned.original.copy(
                    correctionActive =
                        Settings.Secure.getInt(cr, KEY_CORRECTION_ENABLED, 0) == 1,
                    correctionMode = Settings.Secure.getInt(
                        cr,
                        KEY_CORRECTION_MODE,
                        Daltonizer.NONE.secureValue
                    )
                )
            )
        }
    }

    /**
     * Give Night Light back, if what is on the rows is still what we put there.
     *
     * This asks the same ownership question [clear] asks, and it has to: a user
     * who turns Night Light off in system Settings while a warm preset is
     * running has taken the row back, and the next neutral apply must not
     * switch it on again from a stale snapshot.
     */
    private fun handBackNightDisplay(cr: android.content.ContentResolver) {
        val owned = ownership ?: return
        val currentActive = Settings.Secure.getInt(cr, KEY_NIGHT_ACTIVATED, 0) == 1
        val currentTemperature =
            Settings.Secure.getInt(cr, KEY_NIGHT_TEMPERATURE, DEFAULT_TEMPERATURE)
        if (
            !shouldRestoreNightDisplay(
                currentActive = currentActive,
                currentTemperature = currentTemperature,
                lastAppliedTemperature = owned.lastAppliedTemperature
            )
        ) {
            Log.i(tag, "Night Light changed outside OpenLumen; leaving it as the user set it")
            return
        }
        Settings.Secure.putInt(cr, KEY_NIGHT_TEMPERATURE, owned.original.nightTemperature)
        if (owned.original.nightAutoMode == AUTO_MODE_MANUAL) {
            Settings.Secure.putInt(
                cr,
                KEY_NIGHT_ACTIVATED,
                if (owned.original.nightActive) 1 else 0
            )
        } else {
            // Their own sunset/sunrise rule owns the flag, so a sample of it
            // taken hours ago says nothing about now. Leave the screen
            // untinted and let ColorDisplayService decide.
            Settings.Secure.putInt(cr, KEY_NIGHT_ACTIVATED, 0)
        }
        // Auto mode goes back last, so the system's recompute is the final
        // word on the activation flag.
        Settings.Secure.putInt(cr, KEY_NIGHT_AUTO_MODE, owned.original.nightAutoMode)
    }

    /** Give the colour correction back, if the row is still the one we selected. */
    private fun handBackColorCorrection(cr: android.content.ContentResolver) {
        val owned = ownership ?: return
        val currentActive = Settings.Secure.getInt(cr, KEY_CORRECTION_ENABLED, 0) == 1
        val currentMode =
            Settings.Secure.getInt(cr, KEY_CORRECTION_MODE, Daltonizer.NONE.secureValue)
        if (
            !shouldRestoreColorCorrection(
                currentActive = currentActive,
                currentMode = currentMode,
                lastAppliedMode = owned.lastAppliedCorrectionMode
            )
        ) {
            Log.i(tag, "colour correction changed outside OpenLumen; leaving it untouched")
            return
        }
        Settings.Secure.putInt(cr, KEY_CORRECTION_MODE, owned.original.correctionMode)
        Settings.Secure.putInt(
            cr,
            KEY_CORRECTION_ENABLED,
            if (owned.original.correctionActive) 1 else 0
        )
    }

    /**
     * Mirror of [ownership] on disk, so a tint survives the process that made
     * it (C339).
     *
     * These rows are persistent system settings: a process killed by the OS
     * leaves them exactly as it set them, and the next process has no way to
     * tell a leftover tint from something the user chose. It would capture the
     * tint as their original, decline to touch it, and report the filter off
     * while the screen stayed orange. The KCAL driver keeps a record for the
     * same reason.
     *
     * Written after every apply that owns a row, deleted by a successful
     * [clear]. Anything unreadable is discarded and the driver falls back to
     * behaving as though nothing were owned, which is the old behaviour.
     */
    private fun persistOwnership(context: Context) {
        val owned = ownership
        val holdsSomething = nightOwned || dimOwned || correctionOwned
        if (owned == null || !holdsSomething) {
            clearPersistedOwnership(context)
            return
        }
        runCatching {
            val encoded =
                listOf(
                    RECORD_VERSION,
                    if (owned.original.nightActive) 1 else 0,
                    owned.original.nightTemperature,
                    owned.original.nightAutoMode,
                    if (owned.original.dimActive) 1 else 0,
                    owned.original.dimLevel,
                    if (owned.original.correctionActive) 1 else 0,
                    owned.original.correctionMode,
                    owned.lastAppliedTemperature,
                    owned.lastAppliedDimLevel,
                    owned.lastAppliedCorrectionMode,
                    if (nightOwned) 1 else 0,
                    if (dimOwned) 1 else 0,
                    if (correctionOwned) 1 else 0
                ).joinToString(" ")
            // A transition ramp applies up to 600 times, and every step here
            // holds the same record. Skip the identical rewrite.
            if (encoded == lastPersistedRecord) return
            // Write to a sibling and rename: writeText truncates first, so a
            // kill landing mid-write leaves a short file that the reader has to
            // reject, losing the record in the one case it exists for.
            val file = ownershipFile(context)
            val temp = java.io.File(file.parentFile, "$OWNERSHIP_FILE.tmp")
            temp.writeText(encoded)
            if (!temp.renameTo(file)) {
                file.writeText(encoded)
                temp.delete()
            }
            lastPersistedRecord = encoded
        }.onFailure { Log.w(tag, "could not persist secure-settings ownership: ${it.message}") }
    }

    /**
     * Load a record a previous process left behind into this instance. Returns
     * the adopted ownership, or null when there is nothing usable to adopt.
     */
    private fun adoptPersistedOwnership(context: Context): Ownership? {
        val record = runCatching {
            val file = ownershipFile(context)
            if (!file.isFile || file.length() > MAX_RECORD_BYTES) null else file.readText()
        }.getOrNull() ?: return null
        val parts = record.trim().split(' ')
        if (parts.size != RECORD_FIELDS) return null
        val values = parts.map { it.toIntOrNull() ?: return null }
        if (values[0] != RECORD_VERSION) return null
        val adopted = Ownership(
            original = SystemState(
                nightActive = values[1] == 1,
                nightTemperature = values[2],
                nightAutoMode = values[3],
                dimActive = values[4] == 1,
                dimLevel = values[5],
                correctionActive = values[6] == 1,
                correctionMode = values[7]
            ),
            lastAppliedTemperature = values[8],
            lastAppliedDimLevel = values[9],
            lastAppliedCorrectionMode = values[10]
        )
        ownership = adopted
        // A record is a claim about rows this process did not write, so check it
        // against what is actually there before believing it. Without this, a
        // record saying "we own Night Light" made apply() skip the re-capture
        // and replay a dead session's snapshot over a setting the user had
        // changed since.
        val cr = context.contentResolver
        nightOwned = values[11] == 1 && shouldRestoreNightDisplay(
            currentActive = Settings.Secure.getInt(cr, KEY_NIGHT_ACTIVATED, 0) == 1,
            currentTemperature =
                Settings.Secure.getInt(cr, KEY_NIGHT_TEMPERATURE, DEFAULT_TEMPERATURE),
            lastAppliedTemperature = adopted.lastAppliedTemperature
        )
        dimOwned = values[12] == 1 && shouldRestoreReduceBrightColors(
            currentActive = Settings.Secure.getInt(cr, KEY_REDUCE_BRIGHT_ACTIVATED, 0) == 1,
            currentLevel = Settings.Secure.getInt(cr, KEY_REDUCE_BRIGHT_LEVEL, 0),
            lastAppliedLevel = adopted.lastAppliedDimLevel
        )
        correctionOwned = values[13] == 1 && shouldRestoreColorCorrection(
            currentActive = Settings.Secure.getInt(cr, KEY_CORRECTION_ENABLED, 0) == 1,
            currentMode =
                Settings.Secure.getInt(cr, KEY_CORRECTION_MODE, Daltonizer.NONE.secureValue),
            lastAppliedMode = adopted.lastAppliedCorrectionMode
        )
        Log.i(
            tag,
            "adopted a secure-settings ownership record from a previous session " +
                "(night=$nightOwned dim=$dimOwned correction=$correctionOwned)"
        )
        return adopted
    }

    private fun clearPersistedOwnership(context: Context) {
        lastPersistedRecord = null
        runCatching { ownershipFile(context).delete() }
    }

    private fun ownershipFile(context: Context) =
        java.io.File(context.filesDir, OWNERSHIP_FILE)

    private fun hasSecureSettingsGrant(context: Context): Boolean =
        context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Write a key's existing value straight back. Returns false when the
     * provider rejects the write, which is the only reliable signal that the
     * grant is not actually in force on this build.
     */
    private fun rewriteInPlace(context: Context, key: String): Boolean = runCatching {
        val cr = context.contentResolver
        val current = Settings.Secure.getInt(cr, key, 0)
        if (!Settings.Secure.putInt(cr, key, current)) return@runCatching false
        // Read back rather than trusting the write's return value. A provider
        // that accepts the call and drops the row would otherwise pass the
        // probe and then apply nothing, which is exactly the failure mode the
        // old class-presence check had.
        Settings.Secure.getInt(cr, key, Int.MIN_VALUE) == current
    }.getOrElse {
        Log.d(tag, "probe write rejected for $key: ${it.message}")
        false
    }

    /**
     * Extra Dim is not on every device: `ColorDisplayManager.isReduceBrightColorsAvailable`
     * reads `config_reduceBrightColorsAvailable`, which we look up by name through
     * public `Resources` APIs rather than reflecting on the hidden helper.
     */
    internal fun supportsReduceBrightColors(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < REDUCE_BRIGHT_COLORS_MIN_API) return false
        return runCatching {
            // context.resources, not Resources.getSystem(): AOSP's own
            // isReduceBrightColorsAvailable reads through a Context, so a
            // runtime resource overlay that flips this config is honoured.
            val res = context.resources
            val id = res.getIdentifier(CONFIG_REDUCE_BRIGHT_AVAILABLE, "bool", "android")
            id != 0 && res.getBoolean(id)
        }.getOrDefault(false)
    }

    /**
     * True when the transform carries no colour cast, so there is no
     * temperature worth writing. Grayscale and the colour-vision presets are
     * all neutral: their channel scales are equal or their chromaticity
     * normalises to white, and asking [Kelvin] for a temperature returns the
     * neutral one. Writing that and switching Night Light on is a visible no-op
     * dressed up as a working filter.
     */
    internal fun isNeutralChromaticity(matrix: LumenMatrix): Boolean {
        val c = normalizeChromaticity(matrix.scalarRgb())
        return kotlin.math.abs(c[0] - c[1]) <= NEUTRAL_EPSILON &&
            kotlin.math.abs(c[1] - c[2]) <= NEUTRAL_EPSILON
    }

    private fun temperatureFor(matrix: LumenMatrix): Int {
        val chromaticity = normalizeChromaticity(matrix.scalarRgb())
        return kelvinFromRgbScale(chromaticity[0], chromaticity[1], chromaticity[2])
    }

    /**
     * Approximate inverse: given a user-tuned (r,g,b) scale on [0,1], pick the closest
     * Kelvin value in the range AOSP supports (typically 1000-10000 K).
     * Night Light is scalar-only compared with the framebuffer matrix path, but searching
     * against the same forward model used by the Kelvin picker keeps warm presets close
     * to their intended temperature and accounts for green-channel shape.
     */
    internal fun kelvinFromRgbScale(r: Float, g: Float, b: Float): Int = Kelvin.fromRgb(r, g, b)

    private fun normalizeChromaticity(rgb: FloatArray): FloatArray {
        val peak = rgb.maxOrNull()?.takeIf { it > 0f } ?: return floatArrayOf(1f, 1f, 1f)
        return FloatArray(3) { index -> (rgb[index] / peak).coerceIn(0f, 1f) }
    }

    private data class SystemState(
        val nightActive: Boolean,
        val nightTemperature: Int,
        val nightAutoMode: Int,
        val dimActive: Boolean,
        val dimLevel: Int,
        val correctionActive: Boolean,
        val correctionMode: Int
    )

    private data class Ownership(
        val original: SystemState,
        val lastAppliedTemperature: Int,
        val lastAppliedDimLevel: Int,
        val lastAppliedCorrectionMode: Int = Daltonizer.NONE.secureValue
    )

    companion object {
        /** Durable mirror of the ownership record (C339). */
        private const val OWNERSHIP_FILE = "secure-settings-ownership"

        /** Bump when the field list changes; an older record is discarded. */
        private const val RECORD_VERSION = 1

        /** Version plus seven captured values, three last-applied, three flags. */
        private const val RECORD_FIELDS = 14

        /** Fourteen small integers; anything larger is corrupt. */
        private const val MAX_RECORD_BYTES = 256L

        /** `ColorDisplayManager` and the Night Light secure keys both land in Q. */
        const val MIN_API: Int = 29

        /** `reduce_bright_colors_*` arrives in S. */
        const val REDUCE_BRIGHT_COLORS_MIN_API: Int = 31

        // Settings.Secure keys. Every one of these is @hide as a Java constant,
        // but the string is just a provider row name and putInt/getInt take a
        // plain String, so nothing here touches a non-SDK interface.
        const val KEY_NIGHT_ACTIVATED = "night_display_activated"
        const val KEY_NIGHT_TEMPERATURE = "night_display_color_temperature"
        const val KEY_NIGHT_AUTO_MODE = "night_display_auto_mode"
        const val KEY_REDUCE_BRIGHT_ACTIVATED = "reduce_bright_colors_activated"
        const val KEY_REDUCE_BRIGHT_LEVEL = "reduce_bright_colors_level"
        const val KEY_CORRECTION_ENABLED = "accessibility_display_daltonizer_enabled"
        const val KEY_CORRECTION_MODE = "accessibility_display_daltonizer"

        private const val CONFIG_REDUCE_BRIGHT_AVAILABLE = "config_reduceBrightColorsAvailable"

        /** `AUTO_MODE_DISABLED` in ColorDisplayManager: activation follows the flag we write. */
        const val AUTO_MODE_MANUAL: Int = 0

        /** Neutral fallback when the row has never been written on this device. */
        const val DEFAULT_TEMPERATURE: Int = 6500

        /** `setReduceBrightColorsStrength` documents 0-100 inclusive, where 100 is full strength. */
        const val MAX_REDUCE_BRIGHT_LEVEL: Int = 100

        /**
         * How close the three normalised channels have to be before the
         * transform counts as carrying no tint. One 8-bit step is about 0.004,
         * so this is a couple of steps: tight enough that a real warm preset is
         * never called neutral, loose enough to absorb the rounding in the
         * chromaticity projection.
         */
        private const val NEUTRAL_EPSILON: Float = 0.01f

        /**
         * Map the matrix dim factor (0..0.95) onto the Extra Dim percentage.
         * Rounds to the nearest percent so a slider at 0 stays genuinely off.
         */
        internal fun reduceBrightColorsLevel(dim: Float): Int {
            if (!dim.isFinite() || dim <= 0f) return 0
            return Math.round(dim.coerceIn(0f, 1f) * MAX_REDUCE_BRIGHT_LEVEL)
                .coerceIn(0, MAX_REDUCE_BRIGHT_LEVEL)
        }

        /**
         * Blunt reset for the emergency-off path, mirroring
         * `SurfaceFlingerEngine.clearKnownColorTransforms` and
         * `KcalEngine.clearKnownPaths`.
         *
         * Ownership lives in an engine instance, so it does not survive a
         * process kill — but unlike the old reflection driver, this one leaves
         * *persistent* rows behind. Without this, a filter left on when the
         * process died would keep tinting the display with no OpenLumen
         * running, and the documented ADB escape hatch would clear
         * SurfaceFlinger and KCAL while silently skipping the transform that
         * was actually on screen.
         *
         * Deliberately does not restore a previous user setting: nothing on
         * this path knows what it was. It switches the transforms off, which is
         * the direction the emergency hatch exists to move in.
         */
        /**
         * True while any of the persistent rows this driver writes is on.
         *
         * Cheap: three provider reads, no `su`. Used by the emergency hatch to
         * tell "there is nothing to clear" apart from "the filter is recorded
         * as off but the display is still tinted", which is exactly the state a
         * killed process leaves behind.
         */
        fun anyTransformIsOn(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < MIN_API) return false
            val cr = context.contentResolver
            return runCatching {
                listOf(
                    KEY_NIGHT_ACTIVATED,
                    KEY_REDUCE_BRIGHT_ACTIVATED,
                    KEY_CORRECTION_ENABLED
                ).any { Settings.Secure.getInt(cr, it, 0) == 1 }
            }.getOrDefault(false)
        }

        /**
         * Whether a durable ownership record is waiting to be adopted.
         *
         * The emergency hatch uses this to decide whether the secure rows have
         * an owner that can restore them properly, or whether the blunt sweep
         * is the only option left.
         */
        fun hasOwnershipRecord(context: Context): Boolean =
            runCatching { java.io.File(context.filesDir, OWNERSHIP_FILE).isFile }
                .getOrDefault(false)

        fun clearKnownSecureState(context: Context): List<String> {
            if (Build.VERSION.SDK_INT < MIN_API) return emptyList()
            if (
                context.checkSelfPermission(
                    android.Manifest.permission.WRITE_SECURE_SETTINGS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return emptyList()
            }
            val cleared = mutableListOf<String>()
            // The rows are going down without regard to the record, so the
            // record must not survive to be adopted by the next apply.
            runCatching { java.io.File(context.filesDir, OWNERSHIP_FILE).delete() }
            val cr = context.contentResolver
            for (
                key in listOf(
                    KEY_NIGHT_ACTIVATED,
                    KEY_REDUCE_BRIGHT_ACTIVATED,
                    KEY_CORRECTION_ENABLED
                )
            ) {
                runCatching {
                    if (Settings.Secure.getInt(cr, key, 0) == 1) {
                        Settings.Secure.putInt(cr, key, 0)
                        cleared += key
                    }
                }
            }
            return cleared
        }
    }
}
