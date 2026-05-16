package com.openlumen.schedule

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OfflineCitiesTest {

    @Test fun `ALL is non-empty and alphabetically sorted by displayName`() {
        val all = OfflineCities.ALL
        assertThat(all).isNotEmpty()
        val names = all.map { it.displayName }
        assertThat(names).isInOrder()
    }

    @Test fun `every city has valid coordinates and non-blank timezone`() {
        for (c in OfflineCities.ALL) {
            assertThat(c.latitude).isIn(com.google.common.collect.Range.closed(-90.0, 90.0))
            assertThat(c.longitude).isIn(com.google.common.collect.Range.closed(-180.0, 180.0))
            assertThat(c.timezone).isNotEmpty()
            assertThat(c.timezone).contains("/")
        }
    }

    @Test fun `search matches case-insensitively on substring`() {
        val results = OfflineCities.search("ber")
        // Expect to see Berlin and Canberra-equivalent matches if any.
        val names = results.map { it.displayName }
        assertThat(names.any { it.contains("Berlin", ignoreCase = true) }).isTrue()
    }

    @Test fun `search with blank query returns full list capped at limit`() {
        val first10 = OfflineCities.search("", limit = 10)
        assertThat(first10).hasSize(10)
        // First entry should match the alphabetic-first city in ALL.
        assertThat(first10.first()).isEqualTo(OfflineCities.ALL.first())
    }

    @Test fun `nearest picks the right city for a precise coordinate`() {
        // Exactly New York's coordinates → New York wins.
        val ny = OfflineCities.nearest(40.7128, -74.0060)
        assertThat(ny?.name).isEqualTo("New York")
    }

    @Test fun `nearest rejects out-of-range coordinates`() {
        assertThat(OfflineCities.nearest(91.0, 0.0)).isNull()
        assertThat(OfflineCities.nearest(0.0, 181.0)).isNull()
        assertThat(OfflineCities.nearest(Double.NaN, 0.0)).isNull()
    }

    @Test fun `haversine self-distance is zero`() {
        val d = OfflineCities.haversineKm(40.7, -74.0, 40.7, -74.0)
        assertThat(d).isWithin(1e-6).of(0.0)
    }
}
