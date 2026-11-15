package com.openlumen.ui.components

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.google.common.truth.Truth.assertThat
import com.openlumen.R
import com.openlumen.service.BlockedStartNotice
import com.openlumen.ui.ComposeSemantics
import com.openlumen.ui.theme.OpenLumenTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/** C225: the Home notice for a refused tile or widget start, on its own. */
@RunWith(RobolectricTestRunner::class)
class BlockedStartNoticeCardTest {

    private var retries = 0

    private fun show(notice: BlockedStartNotice): ComponentActivity {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent {
            OpenLumenTheme(darkTheme = true) {
                BlockedStartNoticeCard(notice = notice, onRetry = { retries++ })
            }
        }
        ComposeSemantics.settle()
        return activity
    }

    @Test fun `a start refused again from the app offers retry only, and each tap retries once`() {
        val activity = show(BlockedStartNotice(1, "widget_preset", BlockedStartNotice.Phase.StartFailed))
        val texts = ComposeSemantics.texts(activity)

        assertThat(texts).containsAtLeast(
            activity.getString(R.string.blocked_start_title),
            activity.getString(R.string.blocked_start_reason_widget),
            activity.getString(R.string.blocked_start_failed)
        )
        assertThat(texts).doesNotContain(activity.getString(R.string.perm_overlay_grant))
        assertThat(texts).doesNotContain(activity.getString(R.string.blocked_start_needs_overlay))

        ComposeSemantics.click(activity, activity.getString(R.string.blocked_start_retry))
        assertThat(retries).isEqualTo(1)
    }

    @Test fun `a settings screen that will not open is said on the notice`() {
        val activity = show(BlockedStartNotice(1, null, BlockedStartNotice.Phase.NeedsOverlay))
        assertThat(ComposeSemantics.texts(activity)).contains(activity.getString(R.string.blocked_start_reason_other))
        val failed = activity.getString(R.string.external_intent_failed)

        // Nothing resolves the overlay settings intent here.
        ComposeSemantics.click(activity, activity.getString(R.string.perm_overlay_grant))

        val node = ComposeSemantics.nodes(activity)
            .single { failed in it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { t -> t.text } }
        assertThat(node.config.getOrNull(SemanticsProperties.LiveRegion)).isEqualTo(LiveRegionMode.Polite)
        assertThat(retries).isEqualTo(0)
    }
}
