package com.openlumen.engine

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Audit fix: when an engine's apply/clear fails with `exit==127`
 * (su not on PATH) or `exit==-1` (forcibly destroyed on timeout),
 * the process-wide [Su] availability cache must drop so the next
 * driver probe re-checks instead of insisting on a stale `true`.
 *
 * Without this, a user who loses root mid-session (Magisk denied,
 * uninstalled, or the su binary went missing) keeps a cached
 * "yes su works" answer, every engine apply silently fails, the
 * Driver tab still labels the engine "Available", and the filter
 * stops working without any visible cause.
 */
class SuCacheInvalidationTest {

    @Before fun seedCache() {
        Su.setCachedAvailableForTest(true)
    }

    @After fun clearCache() {
        Su.setCachedAvailableForTest(null)
    }

    @Test fun `exit 127 invalidates the cache`() {
        assertThat(Su.peekCachedAvailable()).isTrue()
        Su.resetCacheIfSuLikelyFailed(127)
        assertThat(Su.peekCachedAvailable()).isNull()
    }

    @Test fun `timeout exit -1 invalidates the cache`() {
        assertThat(Su.peekCachedAvailable()).isTrue()
        Su.resetCacheIfSuLikelyFailed(-1)
        assertThat(Su.peekCachedAvailable()).isNull()
    }

    @Test fun `exit 0 leaves the cache alone`() {
        Su.resetCacheIfSuLikelyFailed(0)
        assertThat(Su.peekCachedAvailable()).isTrue()
    }

    @Test fun `non-su exit codes do not invalidate`() {
        // A failed `service call SurfaceFlinger` returning exit 255 should
        // NOT invalidate the su cache — only the engine's own cached
        // working state. This is the boundary the engines rely on.
        Su.resetCacheIfSuLikelyFailed(1)
        Su.resetCacheIfSuLikelyFailed(2)
        Su.resetCacheIfSuLikelyFailed(255)
        assertThat(Su.peekCachedAvailable()).isTrue()
    }

    @Test fun `resetCache drops the slot`() {
        Su.resetCache()
        assertThat(Su.peekCachedAvailable()).isNull()
    }

    @Test fun `seeding false also clears on su-like failure`() {
        Su.setCachedAvailableForTest(false)
        Su.resetCacheIfSuLikelyFailed(127)
        assertThat(Su.peekCachedAvailable()).isNull()
    }
}
