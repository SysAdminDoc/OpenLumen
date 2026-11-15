package com.openlumen.shizuku

import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider
import java.util.concurrent.TimeUnit

/**
 * [ShizukuGateway] over the Shizuku client library.
 *
 * The binder arrives through the ShizukuProvider entry in the manifest, which
 * the Shizuku server (or Sui) calls once it is running. Nothing here starts
 * Shizuku or reaches it over the network: it is a local binder or nothing.
 */
class RikkaShizukuGateway(context: Context) : ShizukuGateway {
    private val appContext = context.applicationContext

    override fun isManagerInstalled(): Boolean = try {
        appContext.packageManager.getPackageInfo(ShizukuProvider.MANAGER_APPLICATION_ID, 0)
        true
    } catch (missing: PackageManager.NameNotFoundException) {
        false
    }

    override fun pingBinder(): Boolean = Shizuku.pingBinder()

    override fun isPreV11(): Boolean = Shizuku.isPreV11()

    override fun hasPermission(): Boolean =
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED

    override fun permissionDeniedForGood(): Boolean = Shizuku.shouldShowRequestPermissionRationale()

    override fun requestPermission(requestCode: Int) = Shizuku.requestPermission(requestCode)

    override fun serverUid(): Int? = if (Shizuku.pingBinder()) Shizuku.getUid() else null

    override fun serverVersion(): Int? = if (Shizuku.pingBinder()) Shizuku.getVersion() else null

    override fun observe(
        onBinderChanged: () -> Unit,
        onPermissionResult: (requestCode: Int, granted: Boolean) -> Unit
    ) {
        // The library keeps these listeners for the life of the process,
        // which matches the singleton that owns this gateway. Sticky, so a
        // binder that arrived before this call still counts.
        Shizuku.addBinderReceivedListenerSticky { onBinderChanged() }
        Shizuku.addBinderDeadListener { onBinderChanged() }
        Shizuku.addRequestPermissionResultListener { requestCode, grantResult ->
            onPermissionResult(requestCode, grantResult == PackageManager.PERMISSION_GRANTED)
        }
    }

    override fun run(command: List<String>, timeoutMillis: Long): ShellResult {
        // Shizuku.newProcess is private in API 13; the server's AIDL behind
        // it is public and is what that method calls.
        val service = IShizukuService.Stub.asInterface(Shizuku.getBinder())
            ?: throw IllegalStateException("Shizuku is not running")
        val process = service.newProcess(command.toTypedArray(), null, null)
            ?: throw IllegalStateException("Shizuku refused to start the command")
        try {
            // pm reads nothing from stdin; close it so it never waits on us.
            runCatching { process.outputStream?.close() }
            val finished = process.waitForTimeout(timeoutMillis, TimeUnit.MILLISECONDS.name)
            if (!finished) return ShellResult(ShellResult.TIMED_OUT, "")
            val output = buildString {
                append(readAll(process.inputStream))
                append(readAll(process.errorStream))
            }.trim()
            return ShellResult(process.exitValue(), output)
        } finally {
            runCatching { process.destroy() }
        }
    }

    private fun readAll(descriptor: ParcelFileDescriptor?): String {
        if (descriptor == null) return ""
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { stream ->
            stream.readBytes().toString(Charsets.UTF_8)
        }
    }
}
