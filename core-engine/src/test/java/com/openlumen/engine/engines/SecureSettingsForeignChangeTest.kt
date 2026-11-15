package com.openlumen.engine.engines

import android.provider.Settings
import com.google.common.truth.Truth.assertThat
import com.openlumen.engine.Daltonizer
import com.openlumen.engine.Presets
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * C346. The seven `Settings.Secure` rows this driver writes are shared state:
 * Quick Settings, the system's own sunset schedule, Digital Wellbeing at
 * bedtime and every other holder of `WRITE_SECURE_SETTINGS` write them too.
 *
 * Ownership used to be checked only at [SecureSettingsEngine.clear], which is
 * too late. `apply` re-takes a tinted row on every ramp step and slider move
 * and records the value it just wrote as the one it owns, so a change the user
 * made in between was overwritten and the evidence of it erased in the same
 * pass. These tests drive the callback the `ContentObserver` makes and assert
 * that the row is handed over for good.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SecureSettingsForeignChangeTest {

    private val context get() = RuntimeEnvironment.getApplication()
    private val resolver get() = context.contentResolver
    private val record get() = File(context.filesDir, "secure-settings-ownership")

    @Before fun grantSecureSettings() {
        shadowOf(context).grantPermissions(android.Manifest.permission.WRITE_SECURE_SETTINGS)
    }

    private fun secure(key: String, default: Int = -1) =
        Settings.Secure.getInt(resolver, key, default)

    private fun seedUserNightLight(active: Int, temperature: Int) {
        Settings.Secure.putInt(resolver, SecureSettingsEngine.KEY_NIGHT_AUTO_MODE, 0)
        Settings.Secure.putInt(resolver, SecureSettingsEngine.KEY_NIGHT_TEMPERATURE, temperature)
        Settings.Secure.putInt(resolver, SecureSettingsEngine.KEY_NIGHT_ACTIVATED, active)
    }

    @Test fun `an untouched session owns its rows and reports nothing foreign`() = runBlocking {
        seedUserNightLight(active = 0, temperature = 6500)
        val engine = SecureSettingsEngine()
        engine.apply(context, Presets.NIGHT)

        assertThat(engine.ownedKeys()).contains(SecureSettingsEngine.KEY_NIGHT_ACTIVATED)
        assertThat(engine.ownedKeys()).contains(SecureSettingsEngine.KEY_NIGHT_TEMPERATURE)
        // The positive control. Without it a release that fired on every
        // callback would pass every assertion below for the wrong reason.
        assertThat(engine.releaseForeignRows(context)).isEmpty()
        assertThat(engine.ownedKeys()).contains(SecureSettingsEngine.KEY_NIGHT_ACTIVATED)
    }

    @Test fun `a temperature the user changes is released and never taken back`() = runBlocking {
        seedUserNightLight(active = 0, temperature = 6500)
        val engine = SecureSettingsEngine()
        engine.apply(context, Presets.NIGHT)
        val ours = secure(SecureSettingsEngine.KEY_NIGHT_TEMPERATURE)

        // The user drags the Night Light slider in system Settings. This is
        // what the observer sees.
        Settings.Secure.putInt(resolver, SecureSettingsEngine.KEY_NIGHT_TEMPERATURE, 4000)
        val released = engine.releaseForeignRows(context)

        assertThat(released.keys).containsExactly(SecureSettingsEngine.KEY_NIGHT_ACTIVATED)
        // The diagnostics entry says what the row became, not only that it moved.
        assertThat(released.getValue(SecureSettingsEngine.KEY_NIGHT_ACTIVATED))
            .contains("${SecureSettingsEngine.KEY_NIGHT_TEMPERATURE}=4000")
        assertThat(engine.ownedKeys()).doesNotContain(SecureSettingsEngine.KEY_NIGHT_ACTIVATED)

        // The next ramp step. Before C346 this wrote `ours` straight back over
        // the value the user had just chosen; that is the whole defect, and it
        // is what goes red if the released-row guard in `apply` is removed.
        engine.apply(context, Presets.NIGHT)
        assertThat(secure(SecureSettingsEngine.KEY_NIGHT_TEMPERATURE)).isEqualTo(4000)
        assertThat(secure(SecureSettingsEngine.KEY_NIGHT_TEMPERATURE)).isNotEqualTo(ours)
    }

    @Test fun `a released row is left alone by the clear that follows`() = runBlocking {
        seedUserNightLight(active = 1, temperature = 5000)
        val engine = SecureSettingsEngine()
        engine.apply(context, Presets.NIGHT)

        Settings.Secure.putInt(resolver, SecureSettingsEngine.KEY_NIGHT_TEMPERATURE, 4000)
        assertThat(engine.releaseForeignRows(context)).isNotEmpty()

        engine.clear(context)

        // Not 5000: the pre-OpenLumen snapshot is not put back over a value the
        // user set more recently than it.
        assertThat(secure(SecureSettingsEngine.KEY_NIGHT_TEMPERATURE)).isEqualTo(4000)
        assertThat(record.isFile).isFalse()
    }

    @Test fun `turning the filter off and on again takes the row back`() = runBlocking {
        seedUserNightLight(active = 0, temperature = 6500)
        val engine = SecureSettingsEngine()
        engine.apply(context, Presets.NIGHT)
        Settings.Secure.putInt(resolver, SecureSettingsEngine.KEY_NIGHT_TEMPERATURE, 4000)
        engine.releaseForeignRows(context)

        // The user answers the notice by switching the filter off and back on.
        // A hand-back that outlived that would leave the driver permanently
        // unable to tint.
        engine.clear(context)
        engine.apply(context, Presets.NIGHT)

        assertThat(secure(SecureSettingsEngine.KEY_NIGHT_TEMPERATURE)).isNotEqualTo(4000)
        assertThat(engine.ownedKeys()).contains(SecureSettingsEngine.KEY_NIGHT_ACTIVATED)
    }

    @Test fun `a correction the user switches off is released`() = runBlocking {
        val engine = SecureSettingsEngine()
        engine.apply(context, Presets.GRAY)
        assertThat(engine.ownedKeys()).contains(SecureSettingsEngine.KEY_CORRECTION_MODE)

        // Switching colour correction off in Accessibility while a colour-vision
        // preset is running. DetoxDroid #142 and #150 are both sides of this.
        Settings.Secure.putInt(resolver, SecureSettingsEngine.KEY_CORRECTION_ENABLED, 0)
        val released = engine.releaseForeignRows(context)

        assertThat(released.keys).containsExactly(SecureSettingsEngine.KEY_CORRECTION_MODE)
        assertThat(released.getValue(SecureSettingsEngine.KEY_CORRECTION_MODE))
            .contains("${SecureSettingsEngine.KEY_CORRECTION_ENABLED}=0")
        assertThat(engine.ownedKeys()).doesNotContain(SecureSettingsEngine.KEY_CORRECTION_MODE)

        engine.apply(context, Presets.GRAY)
        assertThat(secure(SecureSettingsEngine.KEY_CORRECTION_ENABLED)).isEqualTo(0)
    }

    @Test fun `rows nobody claims are not watched`() = runBlocking {
        val engine = SecureSettingsEngine()
        // Nothing applied yet, so nothing is owned and there is nothing to
        // observe. Registering on rows we do not hold would report every
        // ordinary change the user makes as if it concerned us.
        assertThat(engine.ownedKeys()).isEmpty()
        assertThat(engine.releaseForeignRows(context)).isEmpty()

        // A warm preset carries no correction mode, so that row stays theirs.
        seedUserNightLight(active = 0, temperature = 6500)
        engine.apply(context, Presets.NIGHT)
        assertThat(engine.ownedKeys()).doesNotContain(SecureSettingsEngine.KEY_CORRECTION_MODE)
    }

    @Test fun `the foreign predicates are the restore predicates negated`() {
        // The two questions must not be able to drift apart: a row we would
        // restore from is a row we still own, by definition.
        for (active in listOf(true, false)) {
            for (temperature in listOf(3000, 4000)) {
                assertThat(nightDisplayIsForeign(active, temperature, 4000))
                    .isEqualTo(!shouldRestoreNightDisplay(active, temperature, 4000))
                assertThat(reduceBrightColorsIsForeign(active, temperature, 0))
                    .isEqualTo(!shouldRestoreReduceBrightColors(active, temperature, 0))
                assertThat(colorCorrectionIsForeign(active, temperature, Daltonizer.NONE.secureValue))
                    .isEqualTo(
                        !shouldRestoreColorCorrection(
                            active,
                            temperature,
                            Daltonizer.NONE.secureValue
                        )
                    )
            }
        }
    }
}
