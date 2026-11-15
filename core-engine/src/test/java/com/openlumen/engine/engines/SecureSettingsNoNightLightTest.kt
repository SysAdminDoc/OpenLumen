package com.openlumen.engine.engines

import android.provider.Settings
import com.google.common.truth.Truth.assertThat
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
 * C255. A Galaxy S22 on Android 16 ships Extra Dim and colour correction but
 * no Night Light (`config_nightDisplayAvailable` is false; Samsung has Eye
 * Comfort Shield instead). The rows still accept writes, so the probe passed,
 * auto picked this driver over the overlay, and every tint was a no-op while
 * the app said the filter was on.
 */
@RunWith(RobolectricTestRunner::class)
class SecureSettingsNoNightLightTest {

    private val context get() = RuntimeEnvironment.getApplication()
    private val resolver get() = context.contentResolver

    @Before fun grantSecureSettings() {
        shadowOf(context).grantPermissions(android.Manifest.permission.WRITE_SECURE_SETTINGS)
        Settings.Secure.putInt(resolver, SecureSettingsEngine.KEY_NIGHT_AUTO_MODE, 0)
        Settings.Secure.putInt(resolver, SecureSettingsEngine.KEY_NIGHT_ACTIVATED, 0)
    }

    private fun engine(nightLight: Boolean) = SecureSettingsEngine(
        { cr, key, default -> Settings.Secure.getInt(cr, key, default) },
        extraDimShipped = { true },
        nightDisplayShipped = { nightLight }
    )

    private fun secure(key: String) = Settings.Secure.getInt(resolver, key, -1)

    @Test fun `the probe turns the driver down when the device has no Night Light`() = runBlocking {
        assertThat(engine(nightLight = false).isAvailable(context)).isFalse()
    }

    @Test fun `the same device with Night Light passes the probe`() = runBlocking {
        // The control, so the test above cannot pass on a probe that always says no.
        assertThat(engine(nightLight = true).isAvailable(context)).isTrue()
    }

    @Test fun `a pinned tint says why instead of reporting success`() = runBlocking {
        val engine = engine(nightLight = false)
        engine.isAvailable(context)

        val result = engine.apply(context, Presets.AMBER)

        assertThat(result).isInstanceOf(EngineResult.Failure::class.java)
        assertThat(secure(SecureSettingsEngine.KEY_NIGHT_ACTIVATED)).isEqualTo(0)
    }

    @Test fun `a pinned correction still renders without Night Light`() = runBlocking {
        // Colour correction is a separate system feature and is present, so
        // pinning the driver for it keeps working.
        val engine = engine(nightLight = false)
        engine.isAvailable(context)

        val result = engine.apply(context, Presets.PROTAN)

        assertThat(result).isEqualTo(EngineResult.Success)
        assertThat(secure(SecureSettingsEngine.KEY_CORRECTION_ENABLED)).isEqualTo(1)
    }
}
