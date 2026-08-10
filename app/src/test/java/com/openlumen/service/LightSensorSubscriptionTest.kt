package com.openlumen.service

import com.google.common.truth.Truth.assertThat
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test

class LightSensorSubscriptionTest {

    @Test fun `transient collection failure retries without a preference update`() = runBlocking {
        val attempts = AtomicInteger(0)
        val received = CompletableDeferred<Float>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val subscription = LightSensorSubscription(
            luxFlow = {
                flow {
                    if (attempts.incrementAndGet() == 1) error("registration failed")
                    emit(2f)
                    awaitCancellation()
                }
            },
            scope = scope,
            onLuxChanged = { received.complete(it) },
            retryDelay = {}
        )

        subscription.update(true)

        assertThat(withTimeout(2_000L) { received.await() }).isEqualTo(2f)
        assertThat(attempts.get()).isEqualTo(2)

        subscription.cancel()
        scope.cancel()
    }

    @Test fun `cancellation prevents a pending retry from starting`() = runBlocking {
        val attempts = AtomicInteger(0)
        val retryStarted = CompletableDeferred<Unit>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val subscription = LightSensorSubscription(
            luxFlow = {
                flow {
                    attempts.incrementAndGet()
                    error("registration failed")
                }
            },
            scope = scope,
            onLuxChanged = {},
            retryDelay = {
                retryStarted.complete(Unit)
                awaitCancellation()
            }
        )

        subscription.update(true)
        withTimeout(2_000L) { retryStarted.await() }
        subscription.cancel()

        assertThat(attempts.get()).isEqualTo(1)
        scope.cancel()
    }

    @Test fun `exhausted registration retries report unavailable`() = runBlocking {
        val unavailable = CompletableDeferred<Unit>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val subscription = LightSensorSubscription(
            luxFlow = { emptyFlow() },
            scope = scope,
            onLuxChanged = {},
            onUnavailable = { unavailable.complete(Unit) },
            retryDelay = {}
        )

        subscription.update(true)

        withTimeout(2_000L) { unavailable.await() }
        subscription.cancel()
        scope.cancel()
    }
}
