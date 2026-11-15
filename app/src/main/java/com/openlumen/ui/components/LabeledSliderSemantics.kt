package com.openlumen.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString

/**
 * Gives an adjustable control a stable name in addition to its current value.
 *
 * A visible sibling Text is not guaranteed to become the label of a Slider's
 * semantics node, especially when the layout is rearranged for compact or
 * large-screen form factors.
 *
 * The name goes in as text, not contentDescription. A Material 3 Slider's
 * node merges its descendants and has children (thumb and track), and Compose
 * gives such a node's contentDescription its own child node in the
 * accessibility tree. TalkBack 17 stopped on that child separately, after the
 * slider, so the slider itself read only "0 percent, slider". As text the
 * name stays on the slider's own node: "0 percent. Dim below minimum. Slider".
 */
internal fun Modifier.labeledSliderSemantics(
    name: String,
    valueDescription: String
): Modifier = semantics {
    applyLabeledSliderSemantics(name, valueDescription)
}

/** Shared property writer, kept separate so the semantics contract is unit-testable. */
internal fun SemanticsPropertyReceiver.applyLabeledSliderSemantics(
    name: String,
    valueDescription: String
) {
    text = AnnotatedString(name)
    stateDescription = valueDescription
}
