package com.openlumen.engine.engines

import android.content.ContentResolver
import android.provider.Settings
import com.google.common.truth.Truth.assertThat
import com.openlumen.engine.EngineCapability
import com.openlumen.engine.EngineResult
import com.openlumen.engine.Presets
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * C255. Android 12 stopped apps that target it from reading `@hide` settings
 * that are not on the readable list, and the Extra Dim rows are not on it. On
 * a Galaxy S22 running Android 16 the read threw a `SecurityException` while
 * apply was recording what the user had, so every apply failed, Night Light
 * included, and the app said the filter was on over an untouched screen.
 *
 * Robolectric serves these rows from a map and never refuses a read, which is
 * how this got past every test here. These stand in for the device.
 */
@RunWith(RobolectricTestRunner::class)
class SecureSettingsHiddenRowsTest {

    private val context get() = RuntimeEnvironment.getApplication()
    private val resolver get() = context.contentResolver

    @Before fun grantSecureSettings() {
        shadowOf(context).grantPermissions(android.Manifest.permission.WRITE_SECURE_SETTINGS)
        Settings.Secure.putInt(resolver, SecureSettingsEngine.KEY_NIGHT_AUTO_MODE, 0)
        Settings.Secure.putInt(resolver, SecureSettingsEngine.KEY_NIGHT_ACTIVATED, 0)
    }

    private val hiddenExtraDim: (ContentResolver, String, Int) -> Int = { cr, key, default ->
        if (key.startsWith("reduce_bright_colors")) {
            throw SecurityException(
                "Settings key: <$key> is not readable. From S+, settings keys annotated " +
                    "with @hide are restricted to system_server and system apps only"
            )
        }
        Settings.Secure.getInt(cr, key, default)
    }

    private fun deviceEngine() = SecureSettingsEngine(
        hiddenExtraDim,
        extraDimShipped = { true },
        nightDisplayShipped = { true }
    )

    private fun secure(key: String) = Settings.Secure.getInt(resolver, key, -1)

    private val dimmedNight = Presets.NIGHT.copy(dim = 0.5f)

    @Test fun `Night Light applies when the Extra Dim rows cannot be read`() = runBlocking {
        val result = deviceEngine().apply(context, dimmedNight)

        assertThat(result).isEqualTo(EngineResult.Success)
        assertThat(secure(SecureSettingsEngine.KEY_NIGHT_ACTIVATED)).isEqualTo(1)
    }

    @Test fun `a row that cannot be read is never written`() = runBlocking {
        deviceEngine().apply(context, dimmedNight)

        // Nothing to restore from, so nothing taken.
        assertThat(secure(SecureSettingsEngine.KEY_REDUCE_BRIGHT_ACTIVATED)).isEqualTo(-1)
        assertThat(secure(SecureSettingsEngine.KEY_REDUCE_BRIGHT_LEVEL)).isEqualTo(-1)
    }

    @Test fun `readable rows on the same device are still dimmed`() = runBlocking {
        // The control: the same preset on a device whose rows answer. Without
        // it the test above would pass on a driver that never dims at all.
        val engine = SecureSettingsEngine(
            { cr, key, default -> Settings.Secure.getInt(cr, key, default) },
            extraDimShipped = { true }
        )

        engine.apply(context, dimmedNight)

        assertThat(secure(SecureSettingsEngine.KEY_REDUCE_BRIGHT_ACTIVATED)).isEqualTo(1)
    }

    @Test fun `the probe accepts Night Light and does not promise dim`() = runBlocking {
        val engine = deviceEngine()

        assertThat(engine.isAvailable(context)).isTrue()
        assertThat(engine.acceptedKeys).containsExactly(SecureSettingsEngine.KEY_NIGHT_ACTIVATED)
        assertThat(engine.capabilities).doesNotContain(EngineCapability.SUB_MINIMUM_DIM)
    }

    @Test fun `a correction the user changes is still released`() = runBlocking {
        val engine = deviceEngine()
        engine.apply(context, Presets.PROTAN)

        // The user picks another mode in accessibility settings. The check
        // used to read the Extra Dim rows first, throw, and never get here.
        Settings.Secure.putInt(resolver, SecureSettingsEngine.KEY_CORRECTION_MODE, 12)
        val released = engine.releaseForeignRows(context)

        assertThat(released.keys).contains(SecureSettingsEngine.KEY_CORRECTION_MODE)
    }

    @Test fun `the emergency check still sees a correction behind the hidden rows`() {
        Settings.Secure.putInt(resolver, SecureSettingsEngine.KEY_CORRECTION_ENABLED, 1)

        // Night Light is off, so the Extra Dim row is read next and throws.
        // One catch around all three answered false here.
        val on = SecureSettingsEngine.anyTransformIsOn(context) { cr, key ->
            hiddenExtraDim(cr, key, 0)
        }

        assertThat(on).isTrue()
    }

    @Test fun `a record left by a killed process is cleared`() = runBlocking {
        SecureSettingsEngine({ cr, key, default -> Settings.Secure.getInt(cr, key, default) })
            .apply(context, Presets.NIGHT)

        // A new process adopts the record before it can turn the filter off,
        // outside the catch clear() puts around its writes, so nothing on
        // that path may read the hidden rows.
        val result = deviceEngine().clear(context)

        assertThat(result).isEqualTo(EngineResult.Success)
        assertThat(secure(SecureSettingsEngine.KEY_NIGHT_ACTIVATED)).isEqualTo(0)
    }
}
