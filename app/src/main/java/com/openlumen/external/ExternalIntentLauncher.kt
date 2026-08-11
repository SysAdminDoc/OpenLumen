package com.openlumen.external

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent

/** Outcome of attempting to hand work to another Android activity. */
internal enum class ExternalIntentResult {
    Launched,
    Unavailable,
    Failed
}

/**
 * Defensive launcher for settings, share, and other external activities.
 *
 * OEM builds and managed profiles can remove an activity after the app has
 * constructed an otherwise valid intent. Resolve before launching and turn
 * both platform exceptions into a result the caller can show inline.
 */
internal object ExternalIntentLauncher {
    fun launch(
        context: Context,
        intent: Intent,
        canResolve: () -> Boolean = {
            intent.resolveActivity(context.packageManager) != null
        },
        startActivity: (Intent) -> Unit = context::startActivity
    ): ExternalIntentResult {
        val resolved = try {
            canResolve()
        } catch (_: ActivityNotFoundException) {
            return ExternalIntentResult.Failed
        } catch (_: SecurityException) {
            return ExternalIntentResult.Failed
        } catch (_: RuntimeException) {
            return ExternalIntentResult.Failed
        }
        if (!resolved) return ExternalIntentResult.Unavailable

        return try {
            startActivity(intent)
            ExternalIntentResult.Launched
        } catch (_: ActivityNotFoundException) {
            ExternalIntentResult.Failed
        } catch (_: SecurityException) {
            ExternalIntentResult.Failed
        } catch (_: RuntimeException) {
            ExternalIntentResult.Failed
        }
    }

    fun share(
        context: Context,
        sendIntent: Intent,
        chooserTitle: CharSequence
    ): ExternalIntentResult {
        val chooser = Intent.createChooser(sendIntent, chooserTitle)
        return launch(
            context = context,
            intent = chooser,
            canResolve = { sendIntent.resolveActivity(context.packageManager) != null }
        )
    }
}
