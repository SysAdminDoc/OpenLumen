package com.openlumen.shizuku

import android.os.Build
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What one per-app saturation change came to (C287). */
sealed interface AppSaturationOutcome {
    data object Applied : AppSaturationOutcome

    /** Android before 10 has no per-app color transform. */
    data object Unsupported : AppSaturationOutcome

    /** Shizuku was not running with OpenLumen allowed. */
    data class NotReady(val state: ShizukuState) : AppSaturationOutcome

    /** The command ran, or tried to, and the system did not take the level. */
    data class Failed(val detail: String) : AppSaturationOutcome
}

/**
 * Per-app saturation (C287), through the system's own per-app color
 * transform.
 *
 * `cmd color_display set-layer-saturation LEVEL CALLER TARGET` has
 * ColorDisplayService desaturate every layer the target app draws, so the app
 * is gray wherever it shows up and nothing has to watch which app is in
 * front. The shell user holds the permission behind it, and shell is what
 * Shizuku runs as. The shell command is used rather than the binder call
 * under it because that call's transaction code is a hidden field an app is
 * blocked from reading, and a guessed code on an OEM build could land on the
 * device-wide saturation instead.
 *
 * Levels are attributed to this app as the caller, and the system applies
 * the lowest level any caller asked for, so this never overrides another
 * app's request. The system holds them in memory only: a restart drops them
 * all, and [AppSaturationRestorer] puts them back once Shizuku is running.
 */
class AppSaturation(
    private val gateway: ShizukuGateway,
    private val callerPackage: String,
    private val shizukuState: () -> ShizukuState,
    private val sdkInt: Int = Build.VERSION.SDK_INT,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    val supported: Boolean get() = sdkInt >= Build.VERSION_CODES.Q

    suspend fun set(targetPackage: String, level: Int): AppSaturationOutcome {
        if (!supported) return AppSaturationOutcome.Unsupported
        val state = shizukuState()
        if (state != ShizukuState.READY) return AppSaturationOutcome.NotReady(state)
        val result = try {
            withContext(ioDispatcher) {
                gateway.run(command(level, callerPackage, targetPackage), COMMAND_TIMEOUT_MS)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            return AppSaturationOutcome.Failed(error.message ?: error.javaClass.simpleName)
        }
        return when {
            result.exitCode == 0 -> AppSaturationOutcome.Applied
            result.exitCode == ShellResult.TIMED_OUT ->
                AppSaturationOutcome.Failed("color_display did not answer in ${COMMAND_TIMEOUT_MS / 1000} s")
            result.output.isNotBlank() -> AppSaturationOutcome.Failed(result.output)
            else -> AppSaturationOutcome.Failed("color_display exited with ${result.exitCode}")
        }
    }

    /** Set every saved level again. Returns the ones that did not take, with why. */
    suspend fun reapply(levels: Map<String, Int>): Map<String, AppSaturationOutcome> =
        levels.mapValues { (target, level) -> set(target, level) }
            .filterValues { it != AppSaturationOutcome.Applied }

    companion object {
        /** The level that leaves an app as it is. */
        const val FULL = 100
        const val COMMAND_TIMEOUT_MS = 10_000L

        fun command(level: Int, caller: String, target: String): List<String> = listOf(
            "cmd", "color_display", "set-layer-saturation",
            level.coerceIn(0, FULL).toString(), caller, target
        )
    }
}

/**
 * What the saved list becomes after one change (C287). Kept apart from the
 * view model so the rules can be tested on their own.
 */
object AppSaturationList {
    /** Whether a change may start: removals of listed apps, and additions while there is room. */
    fun accepts(saved: Map<String, Int>, packageName: String, removal: Boolean, maxApps: Int): Boolean =
        if (removal) packageName in saved else packageName in saved || saved.size < maxApps

    /**
     * The list to save, or null to leave it as it is. A level is saved only
     * once the system took it, so the list never names an app that is not
     * what it says. A removal that failed keeps its row, the only way back to
     * that app's full color before a restart. The one exception is Shizuku
     * gone from the phone: nothing can set any level again, the system drops
     * the old one at the next restart, and a row that refused to go would be
     * stuck there.
     */
    fun after(
        saved: Map<String, Int>,
        packageName: String,
        level: Int,
        removal: Boolean,
        outcome: AppSaturationOutcome
    ): Map<String, Int>? = when {
        outcome == AppSaturationOutcome.Applied ->
            if (removal) saved - packageName else saved + (packageName to level)
        removal && forgetsWithoutShizuku(outcome) -> saved - packageName
        else -> null
    }

    /** A removal that comes off the list although the system was not reached. */
    fun forgetsWithoutShizuku(outcome: AppSaturationOutcome): Boolean =
        outcome == AppSaturationOutcome.NotReady(ShizukuState.NOT_INSTALLED)
}

/**
 * Puts saved levels back each time Shizuku becomes ready (C287). After a
 * restart the system has forgotten them, and Shizuku being up is the first
 * moment anything can set them again. A process that was already running
 * when Shizuku started does not get the binder straight away (Shizuku
 * 13.6 on API 37 sent it only to its own manager then). It arrives when
 * OpenLumen next comes to the foreground, and this puts the levels back
 * at that point.
 *
 * [shizukuState] is only asked for once something is saved, so a phone with
 * no apps on the list never talks to Shizuku from here.
 */
class AppSaturationRestorer(
    private val levels: Flow<Map<String, Int>>,
    private val shizukuState: () -> StateFlow<ShizukuState>,
    private val saturation: AppSaturation,
    private val report: (applied: Int, failed: Map<String, AppSaturationOutcome>) -> Unit
) {
    fun start(scope: CoroutineScope): Job = scope.launch {
        if (!saturation.supported) return@launch
        levels.map { it.isNotEmpty() }.distinctUntilChanged().collectLatest { anySaved ->
            if (!anySaved) return@collectLatest
            shizukuState().filter { it == ShizukuState.READY }.collect {
                val saved = levels.first()
                val failed = saturation.reapply(saved)
                report(saved.size - failed.size, failed)
            }
        }
    }
}
