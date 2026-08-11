package com.openlumen.external

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExternalIntentLauncherTest {

    @Test fun `unresolved intent is reported without attempting launch`() {
        var started = false

        val result = ExternalIntentLauncher.launch(
            context = fakeContext(),
            intent = Intent("com.openlumen.test.MISSING"),
            canResolve = { false },
            startActivity = { started = true }
        )

        assertThat(result).isEqualTo(ExternalIntentResult.Unavailable)
        assertThat(started).isFalse()
    }

    @Test fun `activity not found during launch is reported`() {
        val result = ExternalIntentLauncher.launch(
            context = fakeContext(),
            intent = Intent("com.openlumen.test.SETTINGS"),
            canResolve = { true },
            startActivity = { throw ActivityNotFoundException("removed by OEM") }
        )

        assertThat(result).isEqualTo(ExternalIntentResult.Failed)
    }

    @Test fun `security exception during launch is reported`() {
        val result = ExternalIntentLauncher.launch(
            context = fakeContext(),
            intent = Intent("com.openlumen.test.SETTINGS"),
            canResolve = { true },
            startActivity = { throw SecurityException("managed profile") }
        )

        assertThat(result).isEqualTo(ExternalIntentResult.Failed)
    }

    private fun fakeContext(): Context = org.robolectric.RuntimeEnvironment.getApplication()
}
