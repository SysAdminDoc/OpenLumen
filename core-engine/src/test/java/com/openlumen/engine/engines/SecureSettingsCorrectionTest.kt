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
