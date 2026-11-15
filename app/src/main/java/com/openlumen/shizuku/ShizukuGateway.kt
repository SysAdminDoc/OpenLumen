package com.openlumen.shizuku

/**
 * The parts of Shizuku the WRITE_SECURE_SETTINGS grant uses (C254).
 *
 * Behind an interface so [ShizukuGrant] can be exercised without a Shizuku
 * server: the real library keeps all of its state in statics that only a
 * running server can fill.
 */
interface ShizukuGateway {
    /**
     * Whether the stock Shizuku manager is installed. Only used to tell "not
     * installed" from "not running" once no binder has arrived: Sui and some
     * forks run a server with no stock manager at all.
     */
    fun isManagerInstalled(): Boolean

    /** A live binder from a Shizuku (or Sui) server has reached this process. */
    fun pingBinder(): Boolean

    /** The server predates API 11, which has no permission flow a client can drive. */
    fun isPreV11(): Boolean

    /** The user has allowed OpenLumen in Shizuku. */
    fun hasPermission(): Boolean

    /**
     * The user picked Deny in Shizuku's prompt. Shizuku will not show the
     * prompt again; only its own app can change the answer.
     */
    fun permissionDeniedForGood(): Boolean

    /** Ask Shizuku to show its prompt. The answer arrives on the listener. */
    fun requestPermission(requestCode: Int)

    /** The server's uid (0 under root, 2000 under adb), or null when not running. */
    fun serverUid(): Int?

    /** The server's API version, or null when not running. */
    fun serverVersion(): Int?

    /**
     * Register for binder and permission events. Called once, by the
     * [ShizukuGrant] that owns this gateway.
     */
    fun observe(
        onBinderChanged: () -> Unit,
        onPermissionResult: (requestCode: Int, granted: Boolean) -> Unit
    )

    /**
     * Run [command] as the Shizuku server's user and wait at most
     * [timeoutMillis] for it to exit. Blocking; never call it on the main
     * thread.
     */
    fun run(command: List<String>, timeoutMillis: Long): ShellResult
}

data class ShellResult(val exitCode: Int, val output: String) {
    companion object {
        /** Exit code reported when the command was still running at the timeout. */
        const val TIMED_OUT = -1
    }
}
