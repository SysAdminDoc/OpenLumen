package com.openlumen.shizuku

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * C254. Plain JVM tests: Shizuku sits behind [ShizukuGateway], so a fake
 * stands in for the server and the package manager's answer.
 */
class ShizukuGrantTest {

    private class FakeGateway : ShizukuGateway {
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

    private var secureSettings = false
    private val gateway = FakeGateway()

    private fun grantUnderTest() = ShizukuGrant(
        gateway = gateway,
        packageName = PACKAGE,
        secureSettingsGranted = { secureSettings },
        ioDispatcher = Dispatchers.Unconfined
    )

    /** The default command behaves like pm: it grants and exits 0. */
    private fun commandGrants() {
        gateway.onRun = { secureSettings = true; ShellResult(0, "") }
    }

    // State mapping

    @Test fun `no binder and no manager reads as not installed`() {
        gateway.binderAlive = false
        gateway.managerInstalled = false
        assertThat(ShizukuGrant.readState(gateway)).isEqualTo(ShizukuState.NOT_INSTALLED)
    }

    @Test fun `no binder with the manager installed reads as not running`() {
        gateway.binderAlive = false
        assertThat(ShizukuGrant.readState(gateway)).isEqualTo(ShizukuState.NOT_RUNNING)
    }

    @Test fun `a live binder counts even with no stock manager, as under Sui`() {
        gateway.managerInstalled = false
        gateway.permission = true
        assertThat(ShizukuGrant.readState(gateway)).isEqualTo(ShizukuState.READY)
    }

    @Test fun `a pre-11 server is outdated whatever it says about permission`() {
        gateway.preV11 = true
        gateway.permission = true
        assertThat(ShizukuGrant.readState(gateway)).isEqualTo(ShizukuState.OUTDATED)
    }

    @Test fun `running and allowed reads as ready`() {
        gateway.permission = true
        assertThat(ShizukuGrant.readState(gateway)).isEqualTo(ShizukuState.READY)
    }

    @Test fun `running and never asked reads as permission needed`() {
        assertThat(ShizukuGrant.readState(gateway)).isEqualTo(ShizukuState.PERMISSION_NEEDED)
    }

    @Test fun `running and denied reads as permission denied`() {
        gateway.deniedForGood = true
        assertThat(ShizukuGrant.readState(gateway)).isEqualTo(ShizukuState.PERMISSION_DENIED)
    }

    @Test fun `a binder that dies mid-check reads as not running`() {
        gateway.permissionThrows = true
        assertThat(ShizukuGrant.readState(gateway)).isEqualTo(ShizukuState.NOT_RUNNING)
    }

    @Test fun `binder events update the published state`() {
        gateway.binderAlive = false
        val grant = grantUnderTest()
        assertThat(grant.state.value).isEqualTo(ShizukuState.NOT_RUNNING)

        gateway.binderAlive = true
        gateway.permission = true
        gateway.binderListener!!.invoke()

        assertThat(grant.state.value).isEqualTo(ShizukuState.READY)
    }

    // Grant flow

    @Test fun `ready runs exactly pm grant for this package and reports granted`() = runBlocking {
        gateway.permission = true
        commandGrants()

        val outcome = grantUnderTest().grant()

        assertThat(outcome).isEqualTo(ShizukuGrantOutcome.Granted)
        assertThat(gateway.commands).containsExactly(
            listOf("pm", "grant", PACKAGE, "android.permission.WRITE_SECURE_SETTINGS")
        )
        assertThat(gateway.requests).isEmpty()
    }

    @Test fun `a clean exit without the permission is a failure, not a grant`() = runBlocking {
        gateway.permission = true
        gateway.onRun = { ShellResult(0, "") }

        val outcome = grantUnderTest().grant()

        assertThat(outcome).isInstanceOf(ShizukuGrantOutcome.Failed::class.java)
    }

    @Test fun `the command's own error text reaches the failure`() = runBlocking {
        gateway.permission = true
        gateway.onRun = {
            ShellResult(255, "java.lang.SecurityException: Package has not requested permission")
        }

        val outcome = grantUnderTest().grant()

        assertThat((outcome as ShizukuGrantOutcome.Failed).detail)
            .contains("has not requested permission")
    }

    @Test fun `a command that never finishes is reported as a timeout`() = runBlocking {
        gateway.permission = true
        gateway.onRun = { ShellResult(ShellResult.TIMED_OUT, "") }

        val outcome = grantUnderTest().grant()

        assertThat((outcome as ShizukuGrantOutcome.Failed).detail).contains("did not finish")
    }

    @Test fun `a command that throws is a failure`() = runBlocking {
        gateway.permission = true
        gateway.onRun = { throw IllegalStateException("Shizuku is not running") }

        val outcome = grantUnderTest().grant()

        assertThat(outcome).isEqualTo(ShizukuGrantOutcome.Failed("Shizuku is not running"))
    }

    @Test fun `permission needed asks Shizuku first, then grants once allowed`() = runBlocking {
        gateway.promptAnswer = true
        commandGrants()

        val outcome = grantUnderTest().grant()

        assertThat(outcome).isEqualTo(ShizukuGrantOutcome.Granted)
        assertThat(gateway.requests).containsExactly(ShizukuGrant.PERMISSION_REQUEST_CODE)
        assertThat(gateway.commands).hasSize(1)
    }

    @Test fun `a refusal in Shizuku runs nothing`() = runBlocking {
        gateway.promptAnswer = false
        commandGrants()

        val outcome = grantUnderTest().grant()

        assertThat(outcome).isEqualTo(ShizukuGrantOutcome.PermissionRefused)
        assertThat(gateway.commands).isEmpty()
        assertThat(secureSettings).isFalse()
    }

    @Test fun `not running asks nothing and runs nothing`() = runBlocking {
        gateway.binderAlive = false
        commandGrants()

        val outcome = grantUnderTest().grant()

        assertThat(outcome).isEqualTo(ShizukuGrantOutcome.NotReady(ShizukuState.NOT_RUNNING))
        assertThat(gateway.requests).isEmpty()
        assertThat(gateway.commands).isEmpty()
    }

    @Test fun `denied for good does not ask again`() = runBlocking {
        gateway.deniedForGood = true
        gateway.promptAnswer = true

        val outcome = grantUnderTest().grant()

        assertThat(outcome).isEqualTo(ShizukuGrantOutcome.NotReady(ShizukuState.PERMISSION_DENIED))
        assertThat(gateway.requests).isEmpty()
        assertThat(gateway.commands).isEmpty()
    }

    // A prompt left open. On a device: tap Grant, press Home with Shizuku's
    // prompt up, open OpenLumen again, and the button sat on "Waiting for
    // Shizuku" for as long as the process lived.

    @Test fun `a prompt left unanswered stops the wait once the app is back`() = runBlocking {
        gateway.promptAnswer = null
        commandGrants()
        val grant = grantUnderTest()
        val outcome = async(start = CoroutineStart.UNDISPATCHED) { grant.grant() }

        grant.stopWaitingIfUnanswered()

        assertThat(outcome.await()).isEqualTo(ShizukuGrantOutcome.NotReady(ShizukuState.PERMISSION_NEEDED))
        assertThat(gateway.commands).isEmpty()
    }

    @Test fun `an answer that lands inside the grace still grants`() = runBlocking {
        gateway.promptAnswer = null
        commandGrants()
        val grant = ShizukuGrant(
            gateway = gateway,
            packageName = PACKAGE,
            secureSettingsGranted = { secureSettings },
            ioDispatcher = Dispatchers.Unconfined,
            answerGraceMillis = 200
        )
        val outcome = async(start = CoroutineStart.UNDISPATCHED) { grant.grant() }
        val stop = async(start = CoroutineStart.UNDISPATCHED) { grant.stopWaitingIfUnanswered() }

        // Allow closes the prompt, which brings OpenLumen back, and the
        // answer comes in right behind it.
        gateway.permission = true
        gateway.permissionListener!!.invoke(ShizukuGrant.PERMISSION_REQUEST_CODE, true)
        stop.await()

        assertThat(outcome.await()).isEqualTo(ShizukuGrantOutcome.Granted)
    }

    @Test fun `an answer given after the wait stopped still reaches the state`() = runBlocking {
        gateway.promptAnswer = null
        val grant = grantUnderTest()
        val outcome = async(start = CoroutineStart.UNDISPATCHED) { grant.grant() }
        grant.stopWaitingIfUnanswered()
        outcome.await()

        gateway.permission = true
        gateway.permissionListener!!.invoke(ShizukuGrant.PERMISSION_REQUEST_CODE, true)

        assertThat(grant.state.value).isEqualTo(ShizukuState.READY)
    }

    @Test fun `with no prompt pending the app coming back changes nothing`() = runBlocking {
        val grant = grantUnderTest()
        grant.stopWaitingIfUnanswered()
        assertThat(grant.state.value).isEqualTo(ShizukuState.PERMISSION_NEEDED)
        assertThat(gateway.requests).isEmpty()
    }

    @Test fun `already granted touches nothing`() = runBlocking {
        secureSettings = true
        gateway.permission = true

        val outcome = grantUnderTest().grant()

        assertThat(outcome).isEqualTo(ShizukuGrantOutcome.Granted)
        assertThat(gateway.requests).isEmpty()
        assertThat(gateway.commands).isEmpty()
    }

    @Test fun `snapshot carries the server identity only while it runs`() {
        gateway.permission = true
        assertThat(grantUnderTest().snapshot())
            .isEqualTo(ShizukuSnapshot(ShizukuState.READY, serverUid = 2000, serverVersion = 13))

        gateway.binderAlive = false
        assertThat(grantUnderTest().snapshot())
            .isEqualTo(ShizukuSnapshot(ShizukuState.NOT_RUNNING, serverUid = null, serverVersion = null))
    }

    private companion object {
        const val PACKAGE = "com.openlumen.debug"
    }
}
