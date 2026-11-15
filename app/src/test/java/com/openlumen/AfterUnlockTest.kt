package com.openlumen

import android.app.Application
import android.content.Intent
import android.os.Looper
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * C287. A process the locked-boot path started lives on past unlock with no
 * second `Application.onCreate`, so the per-app saturation restorer has to
 * wait for the unlock broadcast instead.
 */
@RunWith(RobolectricTestRunner::class)
class AfterUnlockTest {

    private val app: Application = RuntimeEnvironment.getApplication()
    private var unlocked = false
    private var runs = 0

    private fun unlock() {
        unlocked = true
        app.sendBroadcast(Intent(Intent.ACTION_USER_UNLOCKED))
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test fun `already unlocked runs at once`() {
        unlocked = true

        AfterUnlock.run(app, { unlocked }) { runs++ }

        assertThat(runs).isEqualTo(1)
    }

    @Test fun `started locked waits for the unlock broadcast, then runs once`() {
        AfterUnlock.run(app, { unlocked }) { runs++ }
        shadowOf(Looper.getMainLooper()).idle()
        assertThat(runs).isEqualTo(0)

        unlock()
        assertThat(runs).isEqualTo(1)

        // The receiver is gone after the first one.
        unlock()
        assertThat(runs).isEqualTo(1)
    }

    @Test fun `unlocked just after the check still runs, and only once`() {
        var checks = 0
        // Locked at the first look, unlocked by the second.
        AfterUnlock.run(app, { checks++ > 0 }) { runs++ }
        assertThat(runs).isEqualTo(1)

        unlock()
        assertThat(runs).isEqualTo(1)
    }
}
