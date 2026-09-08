package com.openlumen.engine

import com.google.common.truth.Truth.assertThat
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
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

    @Test fun `a probe timeout leaves availability unknown so the next call retries`() = runBlocking {
        // Closed issue #16: a first-time Magisk prompt outlives the probe
        // timeout, the process is destroyed with exit -1, and caching that as
        // a definitive "no root" made the app permanently believe an actually
        // rooted device was unrooted.
        Su.resetCache()
        var calls = 0

        val first = Su.probeAvailabilityForTest {
            calls++
            Su.SuResult(exitCode = -1, stdout = "", stderr = "")
        }

        assertThat(first).isFalse()
        assertThat(Su.peekCachedAvailable()).isNull()

        // The user has now answered the prompt, so the retry must actually run.
        val second = Su.probeAvailabilityForTest {
            calls++
            Su.SuResult(exitCode = 0, stdout = "uid=0(root) gid=0(root)", stderr = "")
        }

        assertThat(second).isTrue()
        assertThat(calls).isEqualTo(2)
        assertThat(Su.peekCachedAvailable()).isTrue()
    }

    @Test fun `su missing from PATH is also inconclusive`() = runBlocking {
        // Exit 127 is "su not on PATH", which a hidden-root setup can report
        // transiently. Same treatment as the timeout.
        Su.resetCache()

        Su.probeAvailabilityForTest { Su.SuResult(exitCode = 127, stdout = "", stderr = "") }

        assertThat(Su.peekCachedAvailable()).isNull()
    }

    @Test fun `a real denial is cached so every apply does not respawn su`() = runBlocking {
        // The unrooted case must still cache, otherwise a slider drag spawns a
        // su subprocess per preference emission.
        Su.resetCache()
        var calls = 0

        repeat(2) {
            Su.probeAvailabilityForTest {
                calls++
                Su.SuResult(exitCode = 1, stdout = "su: permission denied", stderr = "")
            }
        }

        assertThat(calls).isEqualTo(1)
        assertThat(Su.peekCachedAvailable()).isFalse()
    }

    @Test fun `inconclusive exit codes match the engine invalidation contract`() {
        // Su.isInconclusive and Su.resetCacheIfSuLikelyFailed must agree, or a
        // timeout would be inconclusive on one path and definitive on the other.
        for (code in listOf(127, -1)) {
            assertThat(Su.isInconclusive(code)).isTrue()
        }
        for (code in listOf(0, 1, 2, 255)) {
            assertThat(Su.isInconclusive(code)).isFalse()
        }
    }

    @Test fun `concurrent availability callers share one probe`() = runBlocking {
        Su.resetCache()
        val probeCount = AtomicInteger()

        val results = (1..8).map {
            async {
                Su.probeAvailabilityForTest {
                    probeCount.incrementAndGet()
                    delay(20L)
                    Su.SuResult(exitCode = 0, stdout = "uid=0(root)", stderr = "")
                }
            }
        }.awaitAll()

        assertThat(results).containsExactlyElementsIn(List(8) { true })
        assertThat(probeCount.get()).isEqualTo(1)
    }
}
