package com.openlumen.shizuku

/**
 * A Shizuku that does what each test sets, shared by the grant and per-app
 * saturation tests.
 */
internal class FakeShizukuGateway : ShizukuGateway {
    var managerInstalled = true
    var binderAlive = true
    var preV11 = false
    var permission = false
    var deniedForGood = false
    var permissionThrows = false

    /** What the user answers in Shizuku's prompt; null leaves it unanswered. */
    var promptAnswer: Boolean? = null

    /** What running a command does; the default grants the permission. */
    var onRun: (List<String>) -> ShellResult = { ShellResult(0, "") }

    val requests = mutableListOf<Int>()
    val commands = mutableListOf<List<String>>()
    var binderListener: (() -> Unit)? = null
    var permissionListener: ((Int, Boolean) -> Unit)? = null

    override fun isManagerInstalled() = managerInstalled
    override fun pingBinder() = binderAlive
    override fun isPreV11() = preV11
    override fun hasPermission(): Boolean {
        if (permissionThrows) throw IllegalStateException("binder died")
        return permission
    }
    override fun permissionDeniedForGood() = deniedForGood
    override fun requestPermission(requestCode: Int) {
        requests += requestCode
        val answer = promptAnswer ?: return
        permission = answer
        deniedForGood = !answer
        permissionListener?.invoke(requestCode, answer)
    }
    override fun serverUid(): Int? = if (binderAlive) 2000 else null
    override fun serverVersion(): Int? = if (binderAlive) 13 else null
    override fun observe(
        onBinderChanged: () -> Unit,
        onPermissionResult: (requestCode: Int, granted: Boolean) -> Unit
    ) {
        binderListener = onBinderChanged
        permissionListener = onPermissionResult
    }
    override fun run(command: List<String>, timeoutMillis: Long): ShellResult {
        commands += command
        return onRun(command)
    }
}
