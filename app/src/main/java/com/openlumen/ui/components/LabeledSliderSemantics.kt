package com.openlumen.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription

/**
 * Gives an adjustable control a stable name in addition to its current value.
 *
 * A visible sibling Text is not guaranteed to become the label of a Slider's
 * semantics node, especially when the layout is rearranged for compact or
 * large-screen form factors.
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
    contentDescription = name
    stateDescription = valueDescription
}
