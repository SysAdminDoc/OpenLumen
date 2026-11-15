package com.openlumen.service

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.openlumen.PresetKeyResolver
import com.openlumen.engine.ColorEngine
import com.openlumen.engine.DriverProbe
import com.openlumen.engine.EngineCapability
import com.openlumen.engine.EngineKind
import com.openlumen.engine.EngineResult
import com.openlumen.engine.Kelvin
import com.openlumen.engine.LumenMatrix
import com.openlumen.prefs.DirectBootState
import com.openlumen.prefs.DirectBootStateStore
import com.openlumen.prefs.EngineKindDto
import com.openlumen.prefs.MatrixDto
import com.openlumen.prefs.Preferences
import com.openlumen.prefs.PreferencesStore
import com.openlumen.prefs.ScheduleDto
import com.openlumen.prefs.ScheduleModeDto
import com.openlumen.schedule.isActive
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * PR #27 (issue #20) through the real engine controller and Direct Boot
 * mirror: the hand-over between the day filter and the evening preset fades,
 * and a reboot inside or outside the window restores what was on screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DayFilterHandOverTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val night3400 = Kelvin.toRgb(3400)
    private val prefs = Preferences(
        enabled = true,
        engine = EngineKindDto.Overlay,
        activePresetKey = Preferences.CUSTOM_PRESET_KEY,
        customMatrix = MatrixDto(r = night3400.r, g = night3400.g, b = night3400.b),
        schedule = ScheduleDto(
            mode = ScheduleModeDto.FixedTime,
            startHour = 20,
            endHour = 7,
            dayFilter = true,
            dayKelvin = 5500
        )
    )
    private val zone = ZoneId.of("America/New_York")
    private val noon = ZonedDateTime.of(2026, 11, 14, 12, 0, 0, 0, zone)
    private val evening = ZonedDateTime.of(2026, 11, 14, 21, 0, 0, 0, zone)

    private fun phaseAt(now: ZonedDateTime): FilterPhase {
        val mode = mapScheduleMode(prefs.schedule, nextAlarmAt = null)
        return filterPhase(prefs, mode, isActive(mode, now), lightActive = false)
    }

    private val dayMatrix = phaseMatrix(prefs, FilterPhase.DAY, noon)
    private val nightMatrix = phaseMatrix(prefs, FilterPhase.NIGHT, evening)

    private fun controllerFor(engine: ColorEngine) = EngineController(
        context = context,
        probe = DriverProbe(engines = listOf(engine)),
        prefs = PreferencesStore(context, PresetKeyResolver.knownKeys),
        scope = scope,
        isUserUnlocked = { true },
        logTag = "test"
    )

    private fun awaitLastApplied(engine: PhaseRecordingEngine, target: LumenMatrix) {
        val deadline = System.currentTimeMillis() + 10_000
        while (engine.applied.lastOrNull() != target && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
        }
        assertThat(engine.applied.last()).isEqualTo(target)
    }

    private fun intermediateSteps(engine: PhaseRecordingEngine): List<LumenMatrix> =
        engine.applied.filter { it != dayMatrix && it != nightMatrix }

    @Test fun `the two phases really are different targets`() {
        // Positive control for every test below.
        assertThat(phaseAt(noon)).isEqualTo(FilterPhase.DAY)
        assertThat(phaseAt(evening)).isEqualTo(FilterPhase.NIGHT)
        assertThat(dayMatrix).isNotEqualTo(nightMatrix)
    }

    @Test fun `day to evening fades over the transition length`() {
        val engine = PhaseRecordingEngine()
        val controller = controllerFor(engine)

        runBlocking {
            controller.ensureEngineFor(prefs)
            controller.applyIfNeeded(true, dayMatrix, transitionDurationMs = 0)
            controller.applyIfNeeded(true, nightMatrix, transitionDurationMs = 1_000, ramp = true)
        }
        awaitLastApplied(engine, nightMatrix)

        assertThat(intermediateSteps(engine)).isNotEmpty()
    }

    @Test fun `the same move without a phase change is a slider jump`() {
        // Positive control for the fade above: an active to active change of
        // target must stay instant for slider drags.
        val engine = PhaseRecordingEngine()
        val controller = controllerFor(engine)

        runBlocking {
            controller.ensureEngineFor(prefs)
            controller.applyIfNeeded(true, dayMatrix, transitionDurationMs = 0)
            controller.applyIfNeeded(true, nightMatrix, transitionDurationMs = 1_000, ramp = false)
        }

        assertThat(engine.applied).containsExactly(dayMatrix, nightMatrix).inOrder()
    }

    @Test fun `a reboot restores the day filter outside the window and the preset inside it`() {
        val mirror = DirectBootMirror(DirectBootStateStore(context), "test")

        for (now in listOf(noon, evening)) {
            val phase = phaseAt(now)
            val matrix = phaseMatrix(prefs, phase, now)
            runBlocking { mirror.mirror(prefs, active = phase != FilterPhase.OFF, matrix = matrix) }
            val state = runBlocking { mirror.readSnapshot() }

            // LockedBootReceiver starts the restore only when both are set.
            assertThat(state.enabled).isTrue()
            assertThat(state.active).isTrue()
            val engine = PhaseRecordingEngine()
            runBlocking { controllerFor(engine).restoreDirectBootState(state) }
            assertThat(engine.applied).containsExactly(matrix)
        }
    }

    @Test fun `unlocking after a restore that went stale fades into the current phase`() {
        // A reboot at 19:59 restores the day filter; the user unlocks at 21:00.
        // The service instance is new, so this is the first phase it sees.
        val engine = PhaseRecordingEngine()
        val controller = controllerFor(engine)
        val tracker = FilterPhaseTracker()
        val restored = DirectBootState(
            enabled = true,
            active = true,
            engine = EngineKindDto.Overlay,
            matrix = dayMatrix.toMatrixDto(),
            amoledBlackClamp = dayMatrix.amoledClamp
        )

        runBlocking {
            controller.restoreDirectBootState(restored)
            val phase = phaseAt(evening)
            controller.applyIfNeeded(
                true,
                phaseMatrix(prefs, phase, evening),
                transitionDurationMs = 1_000,
                ramp = tracker.changedTo(phase)
            )
        }
        awaitLastApplied(engine, nightMatrix)

        assertThat(engine.applied.first()).isEqualTo(dayMatrix)
        assertThat(intermediateSteps(engine)).isNotEmpty()
    }
}

private class PhaseRecordingEngine : ColorEngine {
    override val kind: EngineKind = EngineKind.OVERLAY
    override val capabilities: Set<EngineCapability> = emptySet()
    val applied = CopyOnWriteArrayList<LumenMatrix>()

    override suspend fun isAvailable(context: Context) = true

    override suspend fun apply(context: Context, matrix: LumenMatrix): EngineResult {
        applied += matrix
        return EngineResult.Success
    }

    override suspend fun clear(context: Context): EngineResult = EngineResult.Success
}
