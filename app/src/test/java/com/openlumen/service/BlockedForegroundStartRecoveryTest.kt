package com.openlumen.service

import android.content.Intent
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BlockedForegroundStartRecoveryTest {

    @Test
    fun `blocked start retries only for the recovery intent after overlay grant`() {
        val intent = Intent().setAction(LumenServiceStarter.ACTION_START_BLOCKED)

        assertThat(BlockedForegroundStartRecovery.isPending(intent)).isTrue()
        assertThat(BlockedForegroundStartRecovery.shouldRetry(intent, true)).isTrue()
        assertThat(BlockedForegroundStartRecovery.shouldRetry(intent, false)).isFalse()
    }

    @Test
    fun `ordinary app entry never triggers a recovery retry`() {
        val intent = Intent()

        assertThat(BlockedForegroundStartRecovery.isPending(intent)).isFalse()
        assertThat(BlockedForegroundStartRecovery.shouldRetry(intent, true)).isFalse()
    }
}
