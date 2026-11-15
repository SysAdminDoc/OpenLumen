package com.openlumen.service

import android.content.Intent
import android.os.Bundle
import com.google.common.truth.Truth.assertThat
import com.openlumen.prefs.EngineKindDto
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BlockedForegroundStartRecoveryTest {

    @Test
    fun `only a fresh handover intent is pending`() {
        val handover = Intent().setAction(LumenServiceStarter.ACTION_START_BLOCKED)

        assertThat(BlockedForegroundStartRecovery.isPending(handover)).isTrue()
        assertThat(BlockedForegroundStartRecovery.isPending(Intent())).isFalse()
        assertThat(BlockedForegroundStartRecovery.isPending(null)).isFalse()
        // Recents replays the intent the task started with; that is not a
        // new refused start (C225).
        val fromRecents = Intent(handover).addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)
        assertThat(BlockedForegroundStartRecovery.isPending(fromRecents)).isFalse()
    }

    @Test
    fun `the overlay gates the start only for drivers that may use it`() {
        assertThat(BlockedForegroundStartRecovery.overlayNeeded(EngineKindDto.Auto)).isTrue()
        assertThat(BlockedForegroundStartRecovery.overlayNeeded(EngineKindDto.Overlay)).isTrue()
        for (root in listOf(EngineKindDto.SurfaceFlinger, EngineKindDto.Kcal, EngineKindDto.ColorDisplayManager)) {
            assertThat(BlockedForegroundStartRecovery.overlayNeeded(root)).isFalse()
        }

        assertThat(BlockedForegroundStartRecovery.canStartNow(overlayNeeded = true, overlayGranted = false)).isFalse()
        assertThat(BlockedForegroundStartRecovery.canStartNow(overlayNeeded = true, overlayGranted = true)).isTrue()
        // A pinned root driver used to wait for an overlay grant it never needed.
        assertThat(BlockedForegroundStartRecovery.canStartNow(overlayNeeded = false, overlayGranted = false)).isTrue()
    }

    @Test
    fun `the notice names its caller and comes back from saved state`() {
        val tile = BlockedStartNotice.from(LumenServiceStarter.blockedStartIntent(
            org.robolectric.RuntimeEnvironment.getApplication(), "tile"
        ))!!
        assertThat(tile.source).isEqualTo(BlockedStartNotice.Source.Tile)
        assertThat(tile.visible).isFalse()
        assertThat(BlockedStartNotice(1, "widget_toggle").source).isEqualTo(BlockedStartNotice.Source.Widget)
        assertThat(BlockedStartNotice(1, "widget_preset").source).isEqualTo(BlockedStartNotice.Source.Widget)
        assertThat(BlockedStartNotice(1, null).source).isEqualTo(BlockedStartNotice.Source.Other)

        val waiting = tile.copy(phase = BlockedStartNotice.Phase.NeedsOverlay)
        val saved = Bundle().also(waiting::save)

        assertThat(BlockedStartNotice.restore(saved)).isEqualTo(waiting)
        assertThat(BlockedStartNotice.restore(Bundle())).isNull()
        assertThat(BlockedStartNotice.from(Intent())).isNull()
    }
}
