package com.openlumen.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NavigationRailMetricsTest {

    @Test fun `normal font scale keeps a readable baseline rail`() {
        val metrics = navigationRailMetrics(1f)

        assertThat(metrics.width.value).isEqualTo(120f)
        assertThat(metrics.itemHeight.value).isEqualTo(76f)
    }

    @Test fun `large font scale widens rail and gives labels more height`() {
        val metrics = navigationRailMetrics(2f)

        assertThat(metrics.width.value).isEqualTo(208f)
        assertThat(metrics.itemHeight.value).isEqualTo(112f)
    }

    @Test fun `invalid font scale falls back to the baseline`() {
        assertThat(navigationRailMetrics(Float.NaN))
            .isEqualTo(navigationRailMetrics(1f))
    }
}
