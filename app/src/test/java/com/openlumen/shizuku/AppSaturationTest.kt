package com.openlumen.shizuku

import android.os.Build
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * C287. Plain JVM tests: Shizuku sits behind [ShizukuGateway], so a fake
 * stands in for the server and for `cmd color_display`.
 */
class AppSaturationTest {

    private val gateway = FakeShizukuGateway().apply { permission = true }
    private var shizukuState = ShizukuState.READY

    private fun saturation(sdkInt: Int = Build.VERSION_CODES.VANILLA_ICE_CREAM) = AppSaturation(
        gateway = gateway,
        callerPackage = CALLER,
        shizukuState = { shizukuState },
        sdkInt = sdkInt,
        ioDispatcher = Dispatchers.Unconfined
    )

    @Test fun `a level runs color_display for that app, with this app as the caller`() = runBlocking<Unit> {
        val outcome = saturation().set(TARGET, 40)

        assertThat(outcome).isEqualTo(AppSaturationOutcome.Applied)
        assertThat(gateway.commands).containsExactly(
            listOf("cmd", "color_display", "set-layer-saturation", "40", CALLER, TARGET)
        )
    }

    @Test fun `levels outside 0 to 100 are clamped before they reach the system`() {
        assertThat(AppSaturation.command(-5, CALLER, TARGET)[3]).isEqualTo("0")
        assertThat(AppSaturation.command(250, CALLER, TARGET)[3]).isEqualTo("100")
    }

    @Test fun `before Android 10 nothing runs`() = runBlocking<Unit> {
        val old = saturation(sdkInt = Build.VERSION_CODES.P)

        assertThat(old.supported).isFalse()
        assertThat(old.set(TARGET, 0)).isEqualTo(AppSaturationOutcome.Unsupported)
        assertThat(gateway.commands).isEmpty()
    }

    @Test fun `Shizuku not ready says where it stands and runs nothing`() = runBlocking<Unit> {
        shizukuState = ShizukuState.NOT_RUNNING

        assertThat(saturation().set(TARGET, 0))
            .isEqualTo(AppSaturationOutcome.NotReady(ShizukuState.NOT_RUNNING))
        assertThat(gateway.commands).isEmpty()
    }

    @Test fun `the system's error text is the failure`() = runBlocking<Unit> {
        gateway.onRun = { ShellResult(255, "Error: unknown package") }

        assertThat(saturation().set(TARGET, 0))
            .isEqualTo(AppSaturationOutcome.Failed("Error: unknown package"))
    }

    @Test fun `a silent non-zero exit and a timeout are failures too`() = runBlocking<Unit> {
        gateway.onRun = { ShellResult(3, "") }
        assertThat(saturation().set(TARGET, 0))
            .isEqualTo(AppSaturationOutcome.Failed("color_display exited with 3"))

        gateway.onRun = { ShellResult(ShellResult.TIMED_OUT, "") }
        assertThat(saturation().set(TARGET, 0))
            .isEqualTo(AppSaturationOutcome.Failed("color_display did not answer in 10 s"))
    }

    @Test fun `a gateway that throws is a failure, not a crash`() = runBlocking<Unit> {
        gateway.onRun = { throw IllegalStateException("binder died") }

        assertThat(saturation().set(TARGET, 0)).isEqualTo(AppSaturationOutcome.Failed("binder died"))
    }

    @Test fun `reapply sets every level and returns only the ones that did not take`() = runBlocking<Unit> {
        gateway.onRun = { command ->
            if (command.last() == "org.example.gone") ShellResult(255, "Error: no such app") else ShellResult(0, "")
        }

        val failed = saturation().reapply(mapOf(TARGET to 0, "org.example.gone" to 50))

        assertThat(gateway.commands.map { it.last() }).containsExactly(TARGET, "org.example.gone")
        assertThat(failed).containsExactly("org.example.gone", AppSaturationOutcome.Failed("Error: no such app"))
    }

    // What gets saved

    @Test fun `a level is saved only once the system took it`() {
        val saved = mapOf(TARGET to 30)

        assertThat(AppSaturationList.after(saved, TARGET, 60, removal = false, AppSaturationOutcome.Applied))
            .isEqualTo(mapOf(TARGET to 60))
        assertThat(AppSaturationList.after(saved, "org.example.new", 0, removal = false, AppSaturationOutcome.Applied))
            .isEqualTo(mapOf(TARGET to 30, "org.example.new" to 0))
        assertThat(AppSaturationList.after(saved, TARGET, 60, removal = false, AppSaturationOutcome.Failed("x")))
            .isNull()
        assertThat(
            AppSaturationList.after(saved, TARGET, 60, removal = false, AppSaturationOutcome.NotReady(ShizukuState.NOT_INSTALLED))
        ).isNull()
    }

    @Test fun `a removal the system refused keeps its row`() {
        val saved = mapOf(TARGET to 0)

        assertThat(AppSaturationList.after(saved, TARGET, 100, removal = true, AppSaturationOutcome.Applied))
            .isEmpty()
        assertThat(AppSaturationList.after(saved, TARGET, 100, removal = true, AppSaturationOutcome.Failed("x")))
            .isNull()
        assertThat(
            AppSaturationList.after(saved, TARGET, 100, removal = true, AppSaturationOutcome.NotReady(ShizukuState.NOT_RUNNING))
        ).isNull()
    }

    @Test fun `with Shizuku gone from the phone a removal still takes the row off`() {
        val outcome = AppSaturationOutcome.NotReady(ShizukuState.NOT_INSTALLED)

        assertThat(AppSaturationList.after(mapOf(TARGET to 0), TARGET, 100, removal = true, outcome)).isEmpty()
        assertThat(AppSaturationList.forgetsWithoutShizuku(outcome)).isTrue()
        assertThat(AppSaturationList.forgetsWithoutShizuku(AppSaturationOutcome.NotReady(ShizukuState.NOT_RUNNING)))
            .isFalse()
    }

    @Test fun `a full list takes changes to its apps but no new ones`() {
        val full = (1..3).associate { "org.example.app$it" to 0 }

        assertThat(AppSaturationList.accepts(full, "org.example.app1", removal = false, maxApps = 3)).isTrue()
        assertThat(AppSaturationList.accepts(full, "org.example.app4", removal = false, maxApps = 3)).isFalse()
        assertThat(AppSaturationList.accepts(full, "org.example.app4", removal = true, maxApps = 3)).isFalse()
        assertThat(AppSaturationList.accepts(full, "org.example.app2", removal = true, maxApps = 3)).isTrue()
        assertThat(AppSaturationList.accepts(emptyMap(), "org.example.app4", removal = false, maxApps = 3)).isTrue()
    }

    // The restorer

    @Test fun `each time Shizuku becomes ready the saved levels go back`() = runBlocking<Unit> {
        val levels = MutableStateFlow(mapOf(TARGET to 20))
        val state = MutableStateFlow(ShizukuState.NOT_RUNNING)
        val reports = mutableListOf<Pair<Int, Map<String, AppSaturationOutcome>>>()
        val restorer = AppSaturationRestorer(
            levels = levels,
            shizukuState = { state },
            saturation = saturation(),
            report = { applied, failed -> reports += applied to failed }
        )

        val job = restorer.start(this + Dispatchers.Unconfined)
        assertThat(gateway.commands).isEmpty()

        state.value = ShizukuState.READY
        assertThat(gateway.commands).containsExactly(
            listOf("cmd", "color_display", "set-layer-saturation", "20", CALLER, TARGET)
        )

        // Shizuku restarts, so the levels are set again.
        state.value = ShizukuState.NOT_RUNNING
        state.value = ShizukuState.READY
        assertThat(gateway.commands).hasSize(2)
        assertThat(reports).containsExactly(1 to emptyMap<String, AppSaturationOutcome>(), 1 to emptyMap<String, AppSaturationOutcome>())
        job.cancel()
    }

    @Test fun `with nothing saved the restorer never asks about Shizuku`() = runBlocking<Unit> {
        var asked = false
        val restorer = AppSaturationRestorer(
            levels = MutableStateFlow(emptyMap()),
            shizukuState = { asked = true; MutableStateFlow(ShizukuState.READY) },
            saturation = saturation(),
            report = { _, _ -> }
        )

        val job = restorer.start(this + Dispatchers.Unconfined)

        assertThat(asked).isFalse()
        assertThat(gateway.commands).isEmpty()
        job.cancel()
    }

    @Test fun `the first app saved starts the restorer watching`() = runBlocking<Unit> {
        val levels = MutableStateFlow(emptyMap<String, Int>())
        val restorer = AppSaturationRestorer(
            levels = levels,
            shizukuState = { MutableStateFlow(ShizukuState.READY) },
            saturation = saturation(),
            report = { _, _ -> }
        )

        val job = restorer.start(this + Dispatchers.Unconfined)
        assertThat(gateway.commands).isEmpty()

        levels.value = mapOf(TARGET to 0)

        assertThat(gateway.commands.map { it.last() }).containsExactly(TARGET)
        job.cancel()
    }

    @Test fun `before Android 10 the restorer does nothing`() = runBlocking<Unit> {
        var asked = false
        val job = AppSaturationRestorer(
            levels = MutableStateFlow(mapOf(TARGET to 0)),
            shizukuState = { asked = true; MutableStateFlow(ShizukuState.READY) },
            saturation = saturation(sdkInt = Build.VERSION_CODES.P),
            report = { _, _ -> }
        ).start(this + Dispatchers.Unconfined)

        job.join()
        assertThat(asked).isFalse()
    }

    private companion object {
        const val CALLER = "com.openlumen.debug"
        const val TARGET = "com.example.reader"
    }
}
