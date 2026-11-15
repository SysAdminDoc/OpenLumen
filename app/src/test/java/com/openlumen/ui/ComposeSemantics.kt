package com.openlumen.ui

import android.app.Activity
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import java.time.Duration
import org.robolectric.Shadows.shadowOf

/**
 * Just enough of a Compose test harness to read and click what a composable
 * shows under Robolectric, through the semantics tree TalkBack also reads.
 * The module has no compose ui-test dependency.
 */
internal object ComposeSemantics {

    /** Lets composition, layout and posted frame callbacks run. */
    fun settle(frames: Int = 10) {
        repeat(frames) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20)) }
    }

    fun nodes(activity: Activity): List<SemanticsNode> {
        val root = activity.window.decorView.findComposeRoot()
            ?: return emptyList()
        return root.semanticsOwner.getAllSemanticsNodes(mergingEnabled = true)
    }

    fun texts(activity: Activity): List<String> = nodes(activity).flatMap { it.texts() }

    /** Clicks the clickable node whose merged text is [label]. */
    fun click(activity: Activity, label: String) {
        val node = nodes(activity).firstOrNull { label in it.texts() && SemanticsActions.OnClick in it.config }
            ?: error("no clickable \"$label\" in ${texts(activity)}")
        checkNotNull(node.config[SemanticsActions.OnClick].action).invoke()
        settle()
    }

    private fun SemanticsNode.texts(): List<String> =
        config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }

    private fun View.findComposeRoot(): ViewRootForTest? {
        if (this is ViewRootForTest) return this
        if (this is ViewGroup) {
            for (i in 0 until childCount) getChildAt(i).findComposeRoot()?.let { return it }
        }
        return null
    }
}
