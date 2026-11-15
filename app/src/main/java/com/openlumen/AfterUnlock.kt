package com.openlumen

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Run [action] once the user has unlocked: now if they have, otherwise on
 * `ACTION_USER_UNLOCKED`.
 *
 * A process that [LockedBootReceiver][com.openlumen.service.LockedBootReceiver]
 * started before unlock keeps running after it, so `Application.onCreate`
 * does not come round again, and anything it skipped for being locked would
 * otherwise never start that boot (C287's restorer, which reads
 * credential-protected preferences).
 */
internal object AfterUnlock {
    fun run(context: Context, isUnlocked: () -> Boolean, action: () -> Unit) {
        if (isUnlocked()) {
            action()
            return
        }
        val done = AtomicBoolean(false)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(received: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_USER_UNLOCKED && done.compareAndSet(false, true)) {
                    context.unregisterReceiver(this)
                    action()
                }
            }
        }
        // A protected broadcast from the system, which reaches a receiver
        // that is not exported.
        val filter = IntentFilter(Intent.ACTION_USER_UNLOCKED)
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
        // Unlocked between the check and the registration: that broadcast has gone.
        if (isUnlocked() && done.compareAndSet(false, true)) {
            context.unregisterReceiver(receiver)
            action()
        }
    }
}
