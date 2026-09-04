package com.openlumen.engine.engines

import android.provider.Settings
import com.google.common.truth.Truth.assertThat
import com.openlumen.engine.Daltonizer
import com.openlumen.engine.EngineResult
import com.openlumen.engine.LumenMatrix
import com.openlumen.engine.Presets
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * C282. The rootless driver carries one colour temperature, so a preset whose
 * whole point is mixing channels used to normalise to white and come out as a
 * neutral 6500 K Night Light: a visible no-op reported as success. It now
 * selects AOSP's own correction mode instead, and stops pretending a neutral
 * transform is a tint.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SecureSettingsCorrectionTest {

    private val context get() = RuntimeEnvironment.getApplication()
    private val resolver get() = context.contentResolver

    @Before fun grantSecureSettings() {
        shadowOf(context).grantPermissions(android.Manifest.permission.WRITE_SECURE_SETTINGS)
    }

    private fun secure(key: String, default: Int = MISSING) =
        Settings.Secure.getInt(resolver, key, default)

    @Test fun `grayscale selects the system monochromacy correction`() = runBlocking {
        val result = SecureSettingsEngine().apply(context, Presets.GRAY)

        assertThat(result).isEqualTo(EngineResult.Success)
        assertThat(secure(SecureSettingsEngine.KEY_CORRECTION_ENABLED)).isEqualTo(1)
        assertThat(secure(SecureSettingsEngine.KEY_CORRECTION_MODE))
            .isEqualTo(Daltonizer.MONOCHROMACY.secureValue)
    }

    @Test fun `each colour-vision preset selects its own correction mode`() = runBlocking {
        val expected = mapOf(
            "protan" to Daltonizer.PROTANOMALY,
            "deutan" to Daltonizer.DEUTERANOMALY,
            "tritan" to Daltonizer.TRITANOMALY
        )
        for ((key, mode) in expected) {
            SecureSettingsEngine().apply(context, Presets.byKey(key)!!.matrix)

            assertThat(secure(SecureSettingsEngine.KEY_CORRECTION_MODE))
                .isEqualTo(mode.secureValue)
            assertThat(secure(SecureSettingsEngine.KEY_CORRECTION_ENABLED)).isEqualTo(1)
        }
    }

    @Test fun `a neutral preset writes no colour temperature and no Night Light`() = runBlocking {
        SecureSettingsEngine().apply(context, Presets.GRAY)

        // Grayscale carries no tint. Switching Night Light on at the neutral
        // temperature is what made this preset look like it worked while
        // changing nothing on screen.
        assertThat(secure(SecureSettingsEngine.KEY_NIGHT_ACTIVATED)).isEqualTo(MISSING)
        assertThat(secure(SecureSettingsEngine.KEY_NIGHT_TEMPERATURE)).isEqualTo(MISSING)
    }

    @Test fun `a warm preset still writes its colour temperature`() = runBlocking {
        SecureSettingsEngine().apply(context, Presets.NIGHT)

        assertThat(secure(SecureSettingsEngine.KEY_NIGHT_ACTIVATED)).isEqualTo(1)
        assertThat(secure(SecureSettingsEngine.KEY_NIGHT_TEMPERATURE))
            .isLessThan(SecureSettingsEngine.DEFAULT_TEMPERATURE)
    }

    @Test fun `clearing puts the user's own correction back`() = runBlocking {
        // The user runs deuteranomaly correction all the time. OpenLumen must
        // hand it back, not leave its own choice or switch correction off.
        Settings.Secure.putInt(
            resolver,
            SecureSettingsEngine.KEY_CORRECTION_MODE,
            Daltonizer.DEUTERANOMALY.secureValue
        )
        Settings.Secure.putInt(resolver, SecureSettingsEngine.KEY_CORRECTION_ENABLED, 1)

        val engine = SecureSettingsEngine()
        engine.apply(context, Presets.GRAY)
        assertThat(secure(SecureSettingsEngine.KEY_CORRECTION_MODE))
            .isEqualTo(Daltonizer.MONOCHROMACY.secureValue)

        engine.clear(context)

        assertThat(secure(SecureSettingsEngine.KEY_CORRECTION_MODE))
            .isEqualTo(Daltonizer.DEUTERANOMALY.secureValue)
        assertThat(secure(SecureSettingsEngine.KEY_CORRECTION_ENABLED)).isEqualTo(1)
    }

    @Test fun `clearing switches off a correction the user did not have on`() = runBlocking {
        val engine = SecureSettingsEngine()
        engine.apply(context, Presets.PROTAN)
        engine.clear(context)

        assertThat(secure(SecureSettingsEngine.KEY_CORRECTION_ENABLED, default = 0)).isEqualTo(0)
    }

    @Test fun `a session that never corrected leaves the user's correction alone`() = runBlocking {
        Settings.Secure.putInt(
            resolver,
            SecureSettingsEngine.KEY_CORRECTION_MODE,
            Daltonizer.TRITANOMALY.secureValue
        )
        Settings.Secure.putInt(resolver, SecureSettingsEngine.KEY_CORRECTION_ENABLED, 1)

        val engine = SecureSettingsEngine()
        engine.apply(context, Presets.NIGHT)
        engine.clear(context)

        assertThat(secure(SecureSettingsEngine.KEY_CORRECTION_ENABLED)).isEqualTo(1)
        assertThat(secure(SecureSettingsEngine.KEY_CORRECTION_MODE))
            .isEqualTo(Daltonizer.TRITANOMALY.secureValue)
    }

    @Test fun `a correction changed by hand while running is not overwritten`() = runBlocking {
        val engine = SecureSettingsEngine()
        engine.apply(context, Presets.GRAY)

        // The user opens Settings and picks their own correction. Ours is gone;
        // clear() must not put anything back over their choice.
        Settings.Secure.putInt(
            resolver,
            SecureSettingsEngine.KEY_CORRECTION_MODE,
            Daltonizer.PROTANOMALY.secureValue
        )

        engine.clear(context)

        assertThat(secure(SecureSettingsEngine.KEY_CORRECTION_MODE))
            .isEqualTo(Daltonizer.PROTANOMALY.secureValue)
        assertThat(secure(SecureSettingsEngine.KEY_CORRECTION_ENABLED)).isEqualTo(1)
    }

    @Test fun `moving from a tinted preset to a neutral one takes the tint off`() = runBlocking {
        val engine = SecureSettingsEngine()
        engine.apply(context, Presets.NIGHT)
        assertThat(secure(SecureSettingsEngine.KEY_NIGHT_ACTIVATED)).isEqualTo(1)

        engine.apply(context, Presets.GRAY)

        assertThat(secure(SecureSettingsEngine.KEY_NIGHT_ACTIVATED)).isEqualTo(0)
        assertThat(secure(SecureSettingsEngine.KEY_CORRECTION_ENABLED)).isEqualTo(1)
    }

    @Test fun `a correction preset followed by a warm one still hands the user's correction back`() = runBlocking {
        // C294. The user runs deuteranomaly correction for a real visual
        // impairment. A session that selects a correction preset and then moves
        // to a warm one writes the correction off, and clear() used to see a row
        // it no longer recognised and leave it off permanently.
        Settings.Secure.putInt(
            resolver,
            SecureSettingsEngine.KEY_CORRECTION_MODE,
            Daltonizer.DEUTERANOMALY.secureValue
        )
        Settings.Secure.putInt(resolver, SecureSettingsEngine.KEY_CORRECTION_ENABLED, 1)

        val engine = SecureSettingsEngine()
        engine.apply(context, Presets.PROTAN)
        engine.apply(context, Presets.NIGHT)

        // The correction comes back the moment OpenLumen stops needing the row.
        // It has no reason to stay off while a warm tint runs, and leaving our
        // own mode sitting in the row meant a user who re-enabled correction in
        // Settings got Protanomaly instead of their own Deuteranomaly.
        assertThat(secure(SecureSettingsEngine.KEY_CORRECTION_ENABLED)).isEqualTo(1)
        assertThat(secure(SecureSettingsEngine.KEY_CORRECTION_MODE))
            .isEqualTo(Daltonizer.DEUTERANOMALY.secureValue)

        engine.clear(context)

        assertThat(secure(SecureSettingsEngine.KEY_CORRECTION_ENABLED)).isEqualTo(1)
        assertThat(secure(SecureSettingsEngine.KEY_CORRECTION_MODE))
            .isEqualTo(Daltonizer.DEUTERANOMALY.secureValue)
    }

    @Test fun `a Night Light the user turns off mid-session is not switched back on`() {
        runBlocking {
            // The hand-back asks the same ownership question clear() asks. The
            // user took the row back while a warm preset was running, so the
            // next neutral apply must leave their choice alone rather than
            // replaying the snapshot taken when the session started.
            Settings.Secure.putInt(resolver, SecureSettingsEngine.KEY_NIGHT_AUTO_MODE, 0)
            Settings.Secure.putInt(resolver, SecureSettingsEngine.KEY_NIGHT_TEMPERATURE, 4000)
            Settings.Secure.putInt(resolver, SecureSettingsEngine.KEY_NIGHT_ACTIVATED, 1)

            val engine = SecureSettingsEngine()
            engine.apply(context, Presets.NIGHT)

            Settings.Secure.putInt(resolver, SecureSettingsEngine.KEY_NIGHT_ACTIVATED, 0)
            Settings.Secure.putInt(resolver, SecureSettingsEngine.KEY_NIGHT_TEMPERATURE, 3600)

            engine.apply(context, Presets.OFF)

            assertThat(secure(SecureSettingsEngine.KEY_NIGHT_ACTIVATED)).isEqualTo(0)
            assertThat(secure(SecureSettingsEngine.KEY_NIGHT_TEMPERATURE)).isEqualTo(3600)
        }
    }

    @Test fun `re-acquiring Night Light reads what the user has now, not the old snapshot`() {
        runBlocking {
            // The schedule hands the row back every morning and takes it again
            // every evening, so the record has to be re-read on the way in.
            Settings.Secure.putInt(resolver, SecureSettingsEngine.KEY_NIGHT_AUTO_MODE, 0)
            Settings.Secure.putInt(resolver, SecureSettingsEngine.KEY_NIGHT_TEMPERATURE, 6500)
            Settings.Secure.putInt(resolver, SecureSettingsEngine.KEY_NIGHT_ACTIVATED, 0)

            val engine = SecureSettingsEngine()
            engine.apply(context, Presets.NIGHT)
            engine.apply(context, Presets.OFF)

            // Daytime: the user sets up their own Night Light.
            Settings.Secure.putInt(resolver, SecureSettingsEngine.KEY_NIGHT_TEMPERATURE, 4200)
            Settings.Secure.putInt(resolver, SecureSettingsEngine.KEY_NIGHT_ACTIVATED, 1)

            engine.apply(context, Presets.NIGHT)
            engine.clear(context)

            assertThat(secure(SecureSettingsEngine.KEY_NIGHT_ACTIVATED)).isEqualTo(1)
            assertThat(secure(SecureSettingsEngine.KEY_NIGHT_TEMPERATURE)).isEqualTo(4200)
        }
    }

    @Test fun `handing Night Light back under a solar auto mode leaves the flag to the system`() {
        runBlocking {
            // auto_mode 2 is the user's own sunset-to-sunrise rule. Once it is
            // restored the system recomputes activation from the current time,
            // so replaying a sample of the flag taken the previous evening
            // would fight it.
            Settings.Secure.putInt(resolver, SecureSettingsEngine.KEY_NIGHT_AUTO_MODE, 2)
            Settings.Secure.putInt(resolver, SecureSettingsEngine.KEY_NIGHT_TEMPERATURE, 4000)
            Settings.Secure.putInt(resolver, SecureSettingsEngine.KEY_NIGHT_ACTIVATED, 1)

            val engine = SecureSettingsEngine()
            engine.apply(context, Presets.NIGHT)
            assertThat(secure(SecureSettingsEngine.KEY_NIGHT_AUTO_MODE))
                .isEqualTo(SecureSettingsEngine.AUTO_MODE_MANUAL)

            engine.apply(context, Presets.OFF)

            assertThat(secure(SecureSettingsEngine.KEY_NIGHT_AUTO_MODE)).isEqualTo(2)
            assertThat(secure(SecureSettingsEngine.KEY_NIGHT_TEMPERATURE)).isEqualTo(4000)
        }
    }

    @Test fun `a neutral preset applied twice leaves the user's own Night Light on`() = runBlocking {
        // C293. The user runs system Night Light themselves. Grayscale carries
        // no tint, so this driver never owns that row and must not switch it
        // off — not on the first apply, and not on the ramp step, slider move
        // or schedule-off identity apply that follows.
        Settings.Secure.putInt(resolver, SecureSettingsEngine.KEY_NIGHT_TEMPERATURE, 4000)
        Settings.Secure.putInt(resolver, SecureSettingsEngine.KEY_NIGHT_ACTIVATED, 1)

        val engine = SecureSettingsEngine()
        engine.apply(context, Presets.GRAY)
        engine.apply(context, Presets.GRAY)

        assertThat(secure(SecureSettingsEngine.KEY_NIGHT_ACTIVATED)).isEqualTo(1)

        engine.clear(context)

        assertThat(secure(SecureSettingsEngine.KEY_NIGHT_ACTIVATED)).isEqualTo(1)
        assertThat(secure(SecureSettingsEngine.KEY_NIGHT_TEMPERATURE)).isEqualTo(4000)
    }

    @Test fun `switching from a warm preset to a neutral one hands Night Light back`() = runBlocking {
        // C293. Our own tint comes off, but the row goes back to the user's
        // value rather than to a flat off, because from that moment it is
        // theirs again and clear() would no longer recognise it.
        Settings.Secure.putInt(resolver, SecureSettingsEngine.KEY_NIGHT_TEMPERATURE, 4000)
        Settings.Secure.putInt(resolver, SecureSettingsEngine.KEY_NIGHT_ACTIVATED, 1)

        val engine = SecureSettingsEngine()
        engine.apply(context, Presets.NIGHT)
        engine.apply(context, Presets.GRAY)

        assertThat(secure(SecureSettingsEngine.KEY_NIGHT_ACTIVATED)).isEqualTo(1)
        assertThat(secure(SecureSettingsEngine.KEY_NIGHT_TEMPERATURE)).isEqualTo(4000)

        engine.clear(context)

        assertThat(secure(SecureSettingsEngine.KEY_NIGHT_ACTIVATED)).isEqualTo(1)
        assertThat(secure(SecureSettingsEngine.KEY_NIGHT_TEMPERATURE)).isEqualTo(4000)
    }

    @Test fun `the emergency reset switches the correction off too`() = runBlocking {
        SecureSettingsEngine().apply(context, Presets.GRAY)

        val cleared = SecureSettingsEngine.clearKnownSecureState(context)

        assertThat(cleared).contains(SecureSettingsEngine.KEY_CORRECTION_ENABLED)
        assertThat(secure(SecureSettingsEngine.KEY_CORRECTION_ENABLED)).isEqualTo(0)
    }

    @Test fun `a custom matrix with no named mode selects no correction`() = runBlocking {
        SecureSettingsEngine().apply(
            context,
            LumenMatrix(hasColorMatrix = true, matrixRr = 0.5f, matrixRg = 0.5f)
        )

        assertThat(secure(SecureSettingsEngine.KEY_CORRECTION_ENABLED)).isEqualTo(MISSING)
    }

    private companion object {
        /** Sentinel for "this row was never written". */
        const val MISSING = -999
    }
}
