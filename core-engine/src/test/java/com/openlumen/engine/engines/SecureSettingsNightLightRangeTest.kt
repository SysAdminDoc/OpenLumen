package com.openlumen.engine.engines

import android.content.Context
import android.provider.Settings
import com.google.common.truth.Truth.assertThat
import com.openlumen.engine.ColorEngine
import com.openlumen.engine.DriverProbe
import com.openlumen.engine.EngineCapability
import com.openlumen.engine.EngineKind
import com.openlumen.engine.EngineResult
import com.openlumen.engine.Kelvin
import com.openlumen.engine.LumenMatrix
import com.openlumen.engine.Presets
import com.openlumen.engine.withGrayscale
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * C364. `ColorDisplayService` clamps whatever temperature this driver writes
 * to `config_nightDisplayColorTemperatureMin`/`Max`, 2596-4082 K on AOSP. Red
 * came out at the 2596 K end, and a mild 5500 K day filter at 4082 K, warmer
 * than the user picked, with nothing saying so.
 */
@RunWith(RobolectricTestRunner::class)
class SecureSettingsNightLightRangeTest {

    private val context get() = RuntimeEnvironment.getApplication()
    private val aosp = 2596..4082

    @Before fun grantSecureSettings() {
        shadowOf(context).grantPermissions(android.Manifest.permission.WRITE_SECURE_SETTINGS)
        Settings.Secure.putInt(context.contentResolver, SecureSettingsEngine.KEY_NIGHT_AUTO_MODE, 0)
        Settings.Secure.putInt(context.contentResolver, SecureSettingsEngine.KEY_NIGHT_ACTIVATED, 0)
    }

    private fun engine(nightLight: Boolean = true, range: IntRange? = aosp) = SecureSettingsEngine(
        { cr, key, default -> Settings.Secure.getInt(cr, key, default) },
        extraDimShipped = { true },
        nightDisplayShipped = { nightLight },
        nightDisplayRange = { range }
    )

    private fun probed(nightLight: Boolean = true, range: IntRange? = aosp) =
        engine(nightLight, range).also { runBlocking { it.isAvailable(context) } }

    private fun kelvin(k: Int): LumenMatrix = Kelvin.toRgb(k).let { LumenMatrix(r = it.r, g = it.g, b = it.b) }

    @Test fun `the framework's own config is read by name`() {
        // No stand-in: these are the integers AOSP ships in config.xml, so the
        // names and the resource type are the ones the system reads.
        val real = SecureSettingsEngine()
        assertThat(real.readNightLightRange(context)).isEqualTo(aosp)
    }

    @Test fun `the probe records the range`() {
        assertThat(probed().nightLightRange).isEqualTo(aosp)
    }

    @Test fun `a device without Night Light has no range to report`() {
        assertThat(probed(nightLight = false).nightLightRange).isNull()
    }

    @Test fun `Red is warmer than the system allows and shows at the warm end`() {
        val clamp = probed().nightLightClamp(Presets.RED)

        assertThat(clamp).isNotNull()
        assertThat(clamp!!.askedKelvin).isLessThan(2596)
        assertThat(clamp.shownKelvin).isEqualTo(2596)
        assertThat(clamp.range).isEqualTo(aosp)
    }

    @Test fun `a 5500 K day filter shows at the cool end`() {
        val clamp = probed().nightLightClamp(kelvin(5500))

        assertThat(clamp).isNotNull()
        assertThat(clamp!!.shownKelvin).isEqualTo(4082)
    }

    @Test fun `Night sits inside the range and shows as asked`() {
        // Control for the two above: the check must come from the range, not
        // from every tint being flagged.
        assertThat(probed().nightLightClamp(Presets.NIGHT)).isNull()
    }

    @Test fun `a few Kelvin past the end is not worth a note`() {
        // 4100 K shows at 4082 K, about one mired off; 4400 K is eighteen.
        assertThat(probed().nightLightClamp(kelvin(4100))).isNull()
        assertThat(probed().nightLightClamp(kelvin(4400))).isNotNull()
    }

    @Test fun `presets Night Light never draws are never flagged`() {
        val engine = probed()
        assertThat(engine.nightLightClamp(Presets.GRAY)).isNull()
        assertThat(engine.nightLightClamp(Presets.PROTAN)).isNull()
    }

    @Test fun `with no range known nothing is flagged`() {
        assertThat(probed(range = null).nightLightClamp(Presets.RED)).isNull()
    }

    @Test fun `Auto runs Red and a 5500 K day filter on Overlay when it is there`() {
        val probes = listOf(
            DriverProbe.Probe(probed(), available = true),
            DriverProbe.Probe(Stub(EngineKind.OVERLAY), available = true)
        )

        assertThat(DriverProbe.bestAvailableKind(probes, listOf(Presets.RED)))
            .isEqualTo(EngineKind.OVERLAY)
        assertThat(DriverProbe.bestAvailableKind(probes, listOf(Presets.NIGHT, kelvin(5500))))
            .isEqualTo(EngineKind.OVERLAY)
        assertThat(DriverProbe.bestAvailableKind(probes, listOf(Presets.NIGHT)))
            .isEqualTo(EngineKind.COLOR_DISPLAY_MANAGER)
    }

    @Test fun `Auto keeps Night Light when nothing else can draw`() {
        val probes = listOf(
            DriverProbe.Probe(probed(), available = true),
            DriverProbe.Probe(Stub(EngineKind.OVERLAY), available = false)
        )

        assertThat(DriverProbe.bestAvailableKind(probes, listOf(Presets.RED)))
            .isEqualTo(EngineKind.COLOR_DISPLAY_MANAGER)
    }

    @Test fun `root still comes first`() {
        val probes = listOf(
            DriverProbe.Probe(Stub(EngineKind.SURFACE_FLINGER), available = true),
            DriverProbe.Probe(probed(), available = true),
            DriverProbe.Probe(Stub(EngineKind.OVERLAY), available = true)
        )

        assertThat(DriverProbe.bestAvailableKind(probes, listOf(Presets.RED)))
            .isEqualTo(EngineKind.SURFACE_FLINGER)
    }

    @Test fun `grayscale on a warm preset stays on Night Light`() {
        // The overlay has no colour matrix, so handing Red plus gray to it
        // would drop the gray to fix the warmth. A clamped warm gray is the
        // closer of the two.
        val grayRed = Presets.RED.withGrayscale(1f)
        val cdm = probed()
        val probes = listOf(
            DriverProbe.Probe(cdm, available = true),
            DriverProbe.Probe(Stub(EngineKind.OVERLAY, overlayCapabilities), available = true)
        )

        assertThat(DriverProbe.shiftsWarmth(cdm, listOf(grayRed))).isTrue()
        assertThat(DriverProbe.bestAvailableKind(probes, listOf(grayRed)))
            .isEqualTo(EngineKind.COLOR_DISPLAY_MANAGER)
        assertThat(DriverProbe.bestAvailableKind(probes, listOf(Presets.RED)))
            .isEqualTo(EngineKind.OVERLAY)
    }

    @Test fun `a colour-vision preset with a 5500 K day filter stays on Night Light`() {
        // The day filter alone would go to the overlay, but the overlay
        // cannot draw the correction the evening needs.
        val cdm = probed()
        val probes = listOf(
            DriverProbe.Probe(cdm, available = true),
            DriverProbe.Probe(Stub(EngineKind.OVERLAY, overlayCapabilities), available = true)
        )

        assertThat(DriverProbe.bestAvailableKind(probes, listOf(kelvin(5500))))
            .isEqualTo(EngineKind.OVERLAY)
        assertThat(DriverProbe.bestAvailableKind(probes, listOf(Presets.PROTAN, kelvin(5500))))
            .isEqualTo(EngineKind.COLOR_DISPLAY_MANAGER)
    }

    @Test fun `an overlay granted after the probe takes over on the next resolve`() {
        val overlay = Stub(EngineKind.OVERLAY, overlayCapabilities, available = false)
        val cdm = probed()
        val driverProbe = DriverProbe(listOf(cdm, overlay))
        val cached = runBlocking { driverProbe.probeAll(context) }
        assertThat(DriverProbe.bestAvailableKind(cached, listOf(Presets.RED)))
            .isEqualTo(EngineKind.COLOR_DISPLAY_MANAGER)

        overlay.available = true
        val refreshed = runBlocking { driverProbe.withFreshOverlay(context, cached) }

        assertThat(DriverProbe.bestAvailableKind(refreshed, listOf(Presets.RED)))
            .isEqualTo(EngineKind.OVERLAY)
        assertThat(refreshed.first { it.engine === cdm }).isSameInstanceAs(cached.first { it.engine === cdm })
    }

    @Test fun `a pinned Night Light is still the driver described`() {
        val cdm = probed()
        val probes = listOf(
            DriverProbe.Probe(cdm, available = true),
            DriverProbe.Probe(Stub(EngineKind.OVERLAY), available = true)
        )

        assertThat(
            DriverProbe.activeEngine(
                probes,
                pinned = EngineKind.COLOR_DISPLAY_MANAGER,
                targets = listOf(Presets.RED)
            )
        ).isSameInstanceAs(cdm)
    }

    /** What [OverlayEngine] declares. */
    private val overlayCapabilities = OverlayEngine().capabilities

    private class Stub(
        override val kind: EngineKind,
        override val capabilities: Set<EngineCapability> = emptySet(),
        @Volatile var available: Boolean = true
    ) : ColorEngine {
        override suspend fun isAvailable(context: Context) = available
        override suspend fun apply(context: Context, matrix: LumenMatrix) = EngineResult.Success
        override suspend fun clear(context: Context) = EngineResult.Success
    }
}
