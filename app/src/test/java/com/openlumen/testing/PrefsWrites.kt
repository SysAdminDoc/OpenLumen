package com.openlumen.testing

import android.os.Looper
import com.openlumen.prefs.Preferences
import com.openlumen.prefs.PreferencesStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.robolectric.Shadows.shadowOf

/**
 * Writes preferences from a worker thread while the main thread keeps its
 * looper turning.
 *
 * DataStore runs each transform in its caller's context, so a write that an
 * activity or service launched on Main only finishes when the main looper
 * runs, and every later write queues behind it. A runBlocking write on the
 * main thread waits on that queue with the looper stopped and never returns.
 *
 * Robolectric also empties the main queue between tests, and a transform
 * dropped there holds the store, which every test in the sandbox shares, for
 * the rest of the run. A test that starts an activity or service closes it
 * and calls this with `{ it }` in `@After`, so nothing is left waiting.
 */
internal fun PreferencesStore.updateWhileLooping(
    timeoutMs: Long = 10_000,
    transform: (Preferences) -> Preferences
) {
    val write = CoroutineScope(Dispatchers.Default).async { update(transform) }
    val looper = shadowOf(Looper.getMainLooper())
    val deadline = System.currentTimeMillis() + timeoutMs
    while (!write.isCompleted) {
        check(System.currentTimeMillis() < deadline) { "preferences write did not finish in $timeoutMs ms" }
        looper.idle()
        Thread.sleep(10)
    }
    runBlocking { write.await() }
}
