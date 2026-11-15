package com.openlumen.viewmodel

import com.google.common.truth.Truth.assertThat
import com.openlumen.shizuku.ShizukuGrantOutcome
import com.openlumen.shizuku.ShizukuState
import org.junit.Test

/**
 * C254 on a device: a Deny in Shizuku's prompt, then the Shizuku server
 * stopping, left "OpenLumen was not allowed in Shizuku" under "Shizuku is
 * installed but not running". The refusal answered a server that had gone.
 */
class ShizukuAttemptTest {

    private val refused = ShizukuAttempt(ShizukuGrantOutcome.PermissionRefused, ShizukuState.PERMISSION_NEEDED)

    @Test fun `a refusal holds while Shizuku still asks`() {
        assertThat(refused.keptIn(ShizukuState.PERMISSION_NEEDED)).isEqualTo(refused)
    }

    @Test fun `a refusal is dropped once the Shizuku server stops`() {
        assertThat(refused.keptIn(ShizukuState.NOT_RUNNING)).isNull()
    }

    @Test fun `a failed grant is dropped when Shizuku changes its answer`() {
        val failed = ShizukuAttempt(ShizukuGrantOutcome.Failed("pm grant exited with 1"), ShizukuState.READY)
        assertThat(failed.keptIn(ShizukuState.READY)).isEqualTo(failed)
        assertThat(failed.keptIn(ShizukuState.PERMISSION_DENIED)).isNull()
    }
}
