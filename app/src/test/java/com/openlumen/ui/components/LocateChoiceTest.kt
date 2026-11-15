package com.openlumen.ui.components

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * PR #26. In the contributed dialog, "Use my location" also switched on
 * automatic updates, so a one-time lookup quietly became a location read on
 * every app open.
 */
class LocateChoiceTest {

    @Test fun `use my location fills the fields without turning automatic on`() {
        assertThat(autoLocationAfterLocate(current = false, fromSwitch = false, found = true)).isFalse()
    }

    @Test fun `the switch turns automatic on once a fix arrives`() {
        assertThat(autoLocationAfterLocate(current = false, fromSwitch = true, found = true)).isTrue()
    }

    @Test fun `a switch with no fix stays off`() {
        assertThat(autoLocationAfterLocate(current = false, fromSwitch = true, found = false)).isFalse()
    }

    @Test fun `a lookup never turns automatic off`() {
        for (fromSwitch in listOf(true, false)) {
            for (found in listOf(true, false)) {
                assertThat(autoLocationAfterLocate(current = true, fromSwitch, found)).isTrue()
            }
        }
    }
}
