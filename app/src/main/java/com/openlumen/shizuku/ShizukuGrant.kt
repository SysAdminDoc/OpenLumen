package com.openlumen.shizuku

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

/** Where Shizuku stands for this app, in the order the grant needs it. */
enum class ShizukuState(val reportLabel: String) {
    NOT_INSTALLED("not installed"),
    NOT_RUNNING("installed, not running"),
    /** A pre-API 11 server. Nothing this app can drive. */
    OUTDATED("server too old (before API 11)"),
    PERMISSION_NEEDED("running, OpenLumen not allowed yet"),
    PERMISSION_DENIED("running, OpenLumen denied in Shizuku"),
    READY("running, OpenLumen allowed")
}

sealed interface ShizukuGrantOutcome {
    /** WRITE_SECURE_SETTINGS is held, checked against the package manager. */
    data object Granted : ShizukuGrantOutcome

    /**
     * Shizuku was not in a state that could run the grant, which includes a
     * prompt left open and unanswered.
     */
    data class NotReady(val state: ShizukuState) : ShizukuGrantOutcome

    /** The user answered Shizuku's prompt with Deny. */
    data object PermissionRefused : ShizukuGrantOutcome

    /** The command ran, or tried to, and the permission is still missing. */
    data class Failed(val detail: String) : ShizukuGrantOutcome
}

/** What the driver report prints about Shizuku. */
data class ShizukuSnapshot(val state: ShizukuState, val serverUid: Int?, val serverVersion: Int?)

/**
 * The optional Shizuku tier (C254): grants this app WRITE_SECURE_SETTINGS
 * from the phone itself, which is the one step of the System Night Light
 * driver that otherwise needs a computer.
 *
 * It runs `pm grant` as Shizuku's user. That command works on every API
 * level this app supports, where the binder route behind it moved from
 * IPackageManager to IPermissionManager and changed its signature twice.
 * Shell cannot reach SurfaceFlinger's colour backdoor, so this replaces the
 * adb grant and nothing more.
 *
 * Success is decided by [secureSettingsGranted], never by the command's exit
 * code: the package manager is the authority on what was granted.
 */
class ShizukuGrant(
    private val gateway: ShizukuGateway,
    private val packageName: String,
    private val secureSettingsGranted: () -> Boolean,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val answerGraceMillis: Long = ANSWER_GRACE_MS
) {
    /** Shizuku's answer: true allowed, false denied, null left unanswered. */
    private val pendingPermission = AtomicReference<CompletableDeferred<Boolean?>?>(null)

    private val _state = MutableStateFlow(readState(gateway))
    val state: StateFlow<ShizukuState> = _state.asStateFlow()

    init {
        gateway.observe(
            onBinderChanged = { refresh() },
            onPermissionResult = { requestCode, granted ->
                if (requestCode == PERMISSION_REQUEST_CODE) {
                    pendingPermission.getAndSet(null)?.complete(granted)
                }
                refresh()
            }
        )
    }

    /** Read the state again; the user may have started or allowed Shizuku while away. */
    fun refresh(): ShizukuState = readState(gateway).also { _state.value = it }

    fun snapshot(): ShizukuSnapshot {
        val current = refresh()
        val running = current != ShizukuState.NOT_INSTALLED && current != ShizukuState.NOT_RUNNING
        return ShizukuSnapshot(
            state = current,
            serverUid = if (running) runCatching { gateway.serverUid() }.getOrNull() else null,
            serverVersion = if (running) runCatching { gateway.serverVersion() }.getOrNull() else null
        )
    }

    /**
     * Grant WRITE_SECURE_SETTINGS through Shizuku, asking for Shizuku's own
     * permission first when it has not been given. Suspends while Shizuku's
     * prompt is up.
     */
    suspend fun grant(): ShizukuGrantOutcome {
        if (secureSettingsGranted()) return ShizukuGrantOutcome.Granted
        var current = refresh()
        if (current == ShizukuState.PERMISSION_NEEDED) {
            val allowed = try {
                awaitPermission()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: RuntimeException) {
                // The binder died between the state read and the request.
                return ShizukuGrantOutcome.NotReady(refresh())
            }
            current = refresh()
            if (allowed == false) return ShizukuGrantOutcome.PermissionRefused
            // Unanswered: the check below says where Shizuku stands now.
        }
        if (current != ShizukuState.READY) return ShizukuGrantOutcome.NotReady(current)

        val result = try {
            withContext(ioDispatcher) { gateway.run(grantCommand(packageName), COMMAND_TIMEOUT_MS) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            return ShizukuGrantOutcome.Failed(error.message ?: error.javaClass.simpleName)
        }
        if (secureSettingsGranted()) return ShizukuGrantOutcome.Granted
        val detail = when {
            result.exitCode == ShellResult.TIMED_OUT -> "pm grant did not finish in ${COMMAND_TIMEOUT_MS / 1000} s"
            result.output.isNotBlank() -> result.output
            else -> "pm grant exited with ${result.exitCode} and the permission is still missing"
        }
        return ShizukuGrantOutcome.Failed(detail)
    }

    /**
     * OpenLumen is in front again while Shizuku's prompt may still be open in
     * Shizuku's own task, after Home or a switch to another app. Waiting on
     * would leave the button stuck on "Waiting for Shizuku" with no way to
     * ask again. An answer that closed the prompt lands well inside the
     * grace, so only a prompt still unanswered after it stops the wait. A
     * later answer still reaches [state].
     */
    suspend fun stopWaitingIfUnanswered() {
        val pending = pendingPermission.get() ?: return
        delay(answerGraceMillis)
        if (pendingPermission.compareAndSet(pending, null)) pending.complete(null)
    }

    private suspend fun awaitPermission(): Boolean? {
        val answer = CompletableDeferred<Boolean?>()
        pendingPermission.getAndSet(answer)?.cancel()
        try {
            gateway.requestPermission(PERMISSION_REQUEST_CODE)
            return answer.await()
        } finally {
            pendingPermission.compareAndSet(answer, null)
        }
    }

    companion object {
        const val PERMISSION_REQUEST_CODE = 0x4C55
        const val COMMAND_TIMEOUT_MS = 15_000L
        const val ANSWER_GRACE_MS = 1_000L
        const val WRITE_SECURE_SETTINGS = "android.permission.WRITE_SECURE_SETTINGS"

        fun grantCommand(packageName: String): List<String> =
            listOf("pm", "grant", packageName, WRITE_SECURE_SETTINGS)

        /**
         * Map what Shizuku reports to one state. The binder comes first,
         * because Sui and some forks run a server with no stock manager
         * installed, and a missing manager says nothing about them.
         */
        internal fun readState(gateway: ShizukuGateway): ShizukuState {
            val alive = runCatching { gateway.pingBinder() }.getOrDefault(false)
            if (!alive) {
                val installed = runCatching { gateway.isManagerInstalled() }.getOrDefault(false)
                return if (installed) ShizukuState.NOT_RUNNING else ShizukuState.NOT_INSTALLED
            }
            return try {
                when {
                    gateway.isPreV11() -> ShizukuState.OUTDATED
                    gateway.hasPermission() -> ShizukuState.READY
                    gateway.permissionDeniedForGood() -> ShizukuState.PERMISSION_DENIED
                    else -> ShizukuState.PERMISSION_NEEDED
                }
            } catch (error: RuntimeException) {
                // The library rethrows a dead binder's RemoteException as a
                // RuntimeException: the server went away after the ping.
                ShizukuState.NOT_RUNNING
            }
        }
    }
}
