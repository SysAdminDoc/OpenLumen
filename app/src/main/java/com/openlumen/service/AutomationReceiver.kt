package com.openlumen.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.openlumen.diagnostics.DiagnosticsLog
import com.openlumen.engine.DisplayEmergencyReset
import com.openlumen.engine.EngineResult
import com.openlumen.engine.engines.SecureSettingsEngine
import com.openlumen.prefs.AutomationToken
import com.openlumen.prefs.PreferencesStore
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Exported entrypoint for ADB and automation tools.
 *
 * LumenService stays non-exported so external callers cannot bind to the
 * foreground service directly. This receiver accepts the documented local
 * automation actions and re-enters the app under OpenLumen's UID, where the
 * service can update prefs and, for TURN_OFF, hard-clear root display backends.
 *
 * **Authentication (roadmap C250).** A broadcast receiver cannot identify its
 * sender. `Binder.getCallingUid()` returns the *receiving* app's UID, because a
 * manifest receiver runs on the main looper after the binder transaction has
 * already ended, and `BroadcastReceiver.getSentFromUid()` (API 34+) reports a
 * real UID only when the sender opted in through
 * `BroadcastOptions.setShareIdentityEnabled`, which Tasker, Termux and `am
 * broadcast` do not do. Earlier builds compared `Binder.getCallingUid()` against
 * the app's own UID, which is trivially always equal, so every local app was
 * trusted. The replacement is a shared secret the user copies out of the app,
 * gated behind an opt-in preference that is off on a fresh install.
 *
 * [LumenService.ACTION_TURN_OFF] is deliberately exempt from both checks. It is
 * the documented emergency escape hatch for a display left in an unreadable
 * state, it only ever moves the filter toward off, and requiring a token the
 * user would have to read off that unreadable screen would defeat the point.
 *
 * Rate limiting: any local app can spam value-setting intents and thrash the
 * display engine with rapid su subprocess spawns. Intents arriving within
 * [THROTTLE_MS] of the previous forwarded intent for the same action are
 * silently dropped. This keeps legitimate Tasker sequences responsive while
 * blocking abuse.
 */
@AndroidEntryPoint
class AutomationReceiver : BroadcastReceiver() {

    @Inject lateinit var prefs: PreferencesStore

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action?.takeIf { it in supportedActions } ?: return

        // Read only the two documented extras rather than replaceExtras(intent).
        // This receiver is exported, so the inbound bundle is untrusted: a hostile
        // local app could attach arbitrary or oversized extras that we would
        // otherwise copy verbatim into the service intent. The service itself
        // still validates the values (preset key existence, NaN/range on VALUE);
        // this just bounds the surface to what we actually consume.
        val presetKey = intent.getStringExtra(LumenService.EXTRA_PRESET_KEY)
        val value = if (intent.hasExtra(LumenService.EXTRA_VALUE)) {
            intent.getFloatExtra(LumenService.EXTRA_VALUE, Float.NaN)
        } else {
            null
        }
        val presentedToken = intent.getStringExtra(EXTRA_TOKEN)

        // Throttle before anything expensive. Reading preferences means a
        // DataStore hit plus a JSON decode, migrations and sanitize, and this
        // receiver is exported: a hostile app can broadcast in a tight loop.
        // Doing the rate check first keeps a flood down to a map lookup, and
        // caps how many goAsync() results can be pending at once.
        val now = SystemClock.elapsedRealtime()

        // A broadcast that cannot possibly be authorised is refused here,
        // before the preference read, and counts against its own budget. This
        // is what keeps a flood cheap: sending an action that needs a token
        // with no token, or one that is not even the right shape, costs a
        // string check rather than a DataStore hit. It cannot delay a real
        // command, because a real command carries a well-formed token and
        // never enters this branch.
        if (isCheaplyRejectable(action, presentedToken)) {
            val lastRejected = lastRejectedMs.get()
            if (now - lastRejected < REJECT_THROTTLE_MS) {
                val count = throttleCount.incrementAndGet()
                if (count % 20 == 1L) {
                    Log.d(tag, "throttled malformed $action ($count total)")
                }
                return
            }
            lastRejectedMs.set(now)
        }

        val lastForwarded = lastForwardedMs.getOrDefault(action, NEVER)
        if (now - lastForwarded < THROTTLE_MS) {
            val count = throttleCount.incrementAndGet()
            if (count % 20 == 1L) {
                Log.d(tag, "throttled $action ($count total)")
            }
            return
        }
        // Reserved, not spent. The slot is taken now so that a burst of valid
        // commands is still capped synchronously, and handed back below if
        // this one turns out not to be authorised: otherwise anything able to
        // broadcast could hold every real command out by failing the token
        // check every 150 ms.
        lastForwardedMs[action] = now

        // Null when this receiver was not reached through a real broadcast
        // dispatch, which is the case in a unit test. The work below is what
        // matters; the pending result only keeps the process alive for it.
        val pending: PendingResult? = goAsync()
        CoroutineScope(Dispatchers.Default + SupervisorJob()).launch {
            try {
                val current = withTimeoutOrNull(PREFERENCES_TIMEOUT_MS) { prefs.flow.first() }
                val decision = authorize(
                    action = action,
                    presentedToken = presentedToken,
                    automationEnabled = current?.automationEnabled ?: false,
                    storedToken = current?.automationToken.orEmpty()
                )
                if (decision != Decision.Allowed) {
                    // Hand the slot back. A rejection must not cost a
                    // legitimate command its turn.
                    if (lastForwarded == NEVER) {
                        lastForwardedMs.remove(action)
                    } else {
                        lastForwardedMs[action] = lastForwarded
                    }
                    reject(context, action, decision)
                    return@launch
                }

                // TURN_OFF is unauthenticated by design, and the service's
                // handler hard-clears the root backends, which spawns `su`.
                // Repeating that when the filter is already off gives any local
                // app a way to spin root-shell launches and Magisk prompts, so
                // drop the redundant one.
                //
                // "Recorded as off" is not the same as "the display is clear",
                // though. A killed process leaves the secure rows set, and this
                // path records the filter off even when the clear achieved
                // nothing, so keying the short-circuit on the preference alone
                // made one failed attempt disable the hatch for good.
                if (action == LumenService.ACTION_TURN_OFF &&
                    current?.enabled == false &&
                    !SecureSettingsEngine.anyTransformIsOn(context)
                ) {
                    Log.d(tag, "TURN_OFF ignored: filter is already off and nothing is tinted")
                    return@launch
                }

                val forward = Intent(context, LumenService::class.java).setAction(action)
                presetKey?.let { forward.putExtra(LumenService.EXTRA_PRESET_KEY, it) }
                value?.let { forward.putExtra(LumenService.EXTRA_VALUE, it) }

                // Claim the turn-off before starting the service, so the
                // acknowledgement can only refer to this request.
                val turnOffNonce = if (action == LumenService.ACTION_TURN_OFF) {
                    TurnOffAcknowledgement.requestTurnOff(context)
                } else {
                    null
                }
                val result = LumenServiceStarter.start(
                    context,
                    forward,
                    tag,
                    exemption = LumenServiceStarter.Exemption.NONE,
                    source = "automation"
                )
                if (!result.started) {
                    Log.w(tag, "automation service start failed: ${result.error?.message ?: "unknown"}")
                    if (action == LumenService.ACTION_TURN_OFF) {
                        clearDisplayWithoutService(context) {
                            prefs.update { it.copy(enabled = false) }
                        }
                    }
                } else if (action == LumenService.ACTION_TURN_OFF) {
                    // A start that did not throw is not a turn-off that
                    // happened. The service still has to reach startForeground
                    // within a few seconds, and the work runs on a scope
                    // onDestroy cancels, so it can be killed with the display
                    // still tinted and nothing reporting a failure.
                    if (turnOffNonce == null || !TurnOffAcknowledgement.awaitAcknowledgement(context, turnOffNonce)) {
                        Log.w(tag, "turn-off was not acknowledged; clearing without the service")
                        clearDisplayWithoutService(context) {
                            prefs.update { it.copy(enabled = false) }
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.e(tag, "automation receiver failed: ${t.message}", t)
            } finally {
                pending?.finish()
            }
        }
    }

    private fun reject(context: Context, action: String, decision: Decision) {
        Log.w(tag, "rejected automation $action: ${decision.reason}")
        val count = rejectionCount.incrementAndGet()
        // A hostile app can spam this path, so keep the on-disk log bounded
        // while still leaving the first occurrence visible in diagnostics.
        if (count % 20 == 1L) {
            DiagnosticsLog.log(
                context,
                DiagnosticsLog.Level.WARN,
                DiagnosticsLog.Category.SERVICE,
                "automation rejected ($count total): ${decision.reason}"
            )
        }
    }

    /** Why an inbound automation broadcast was accepted or dropped. */
    internal enum class Decision(val reason: String) {
        Allowed("allowed"),
        DisabledByUser("external control is turned off"),
        NoTokenConfigured("no automation token has been generated"),
        MissingToken("no token supplied"),
        BadToken("token did not match")
    }

    companion object {
        const val tag = "OpenLumen/Automation"
        const val THROTTLE_MS = 200L

        /**
         * How long a refused-on-sight broadcast suppresses the next one.
         *
         * Longer than [THROTTLE_MS] because nothing legitimate is being
         * delayed: only broadcasts with a missing or misshapen token reach
         * this budget, and a real caller's token is neither.
         */
        const val REJECT_THROTTLE_MS = 1_000L

        /**
         * The stamp for an action nothing has forwarded yet.
         *
         * Not zero: elapsedRealtime is near zero for the first moments after
         * a boot, so a zero default reads as "forwarded just now" and
         * throttles the first command of every action on a freshly started
         * device.
         */
        const val NEVER = Long.MIN_VALUE / 2

        /**
         * Clear the display when the escape hatch could not reach the service
         * (C296).
         *
         * `TURN_OFF` normally forwards to [LumenService], which owns the
         * engines. But the state this hatch exists for is the one where nothing
         * is running: the process was killed with the persistent secure rows
         * still set, so the display stays tinted with no OpenLumen alive. From
         * the background that service start is refused, and on Android 15 the
         * `SYSTEM_ALERT_WINDOW` exemption additionally requires a visible
         * overlay window, which a dead process does not have. Logging the
         * refusal and finishing left the user staring at a tint the documented
         * command could not remove.
         *
         * [DisplayEmergencyReset] needs no service and no engine instance, so
         * call it here. The display comes first; [recordFilterOff] runs after,
         * because a boot receiver that still reads `enabled = true` would put
         * the tint straight back.
         */
        internal suspend fun clearDisplayWithoutService(
            context: Context,
            recordFilterOff: suspend () -> Unit
        ) {
            // Give the secure driver first refusal. It adopts the durable
            // ownership record, so it can put back what the user had instead of
            // zeroing rows they may have owned all along, and it deletes the
            // record on the way out.
            val hadRecord = SecureSettingsEngine.hasOwnershipRecord(context)
            val restored = runCatching { SecureSettingsEngine().clear(context) }
                .onFailure { Log.w(tag, "record-based clear failed: ${it.message}") }
                .getOrNull()

            // The root drivers keep no such record. The secure rows only need
            // the blunt sweep when nothing owned them: a row still on after a
            // successful restore belongs to the user, and zeroing it here would
            // undo the restore we just performed.
            val bluntSecure = !(hadRecord && restored is EngineResult.Success) &&
                SecureSettingsEngine.anyTransformIsOn(context)
            val cleared = runCatching {
                DisplayEmergencyReset.clearRootTransforms(
                    context = context.takeIf { bluntSecure },
                    roots = true
                )
            }.onFailure { Log.e(tag, "emergency clear failed: ${it.message}", it) }.getOrNull()

            DiagnosticsLog.log(
                context,
                DiagnosticsLog.Level.WARN,
                DiagnosticsLog.Category.SERVICE,
                "turn off could not start the service; cleared the display directly: " +
                    "restored=${restored is EngineResult.Success} " +
                    "secure=${cleared?.secureSettingsKeys?.joinToString().orEmpty().ifBlank { "none" }} " +
                    "SF=${cleared?.surfaceFlingerCodes?.joinToString().orEmpty().ifBlank { "none" }} " +
                    "KCAL=${cleared?.kcalPaths?.joinToString().orEmpty().ifBlank { "none" }}"
            )
            runCatching { recordFilterOff() }
                .onFailure { Log.w(tag, "could not record the filter as off: ${it.message}") }
        }

        /** String extra carrying the shared secret shown in the app's automation section. */
        const val EXTRA_TOKEN = "com.openlumen.extra.TOKEN"

        private const val PREFERENCES_TIMEOUT_MS = 8_000L

        /**
         * Decide whether an inbound broadcast may act.
         *
         * Pure so the whole matrix is unit-testable without a Context: see
         * `AutomationReceiverTest`.
         */
        internal fun authorize(
            action: String,
            presentedToken: String?,
            automationEnabled: Boolean,
            storedToken: String
        ): Decision {
            // Emergency escape hatch: only ever turns the filter off, and must
            // stay reachable when the screen is too tinted to read a token off.
            if (action == LumenService.ACTION_TURN_OFF) return Decision.Allowed
            if (!automationEnabled) return Decision.DisabledByUser
            if (storedToken.isEmpty()) return Decision.NoTokenConfigured
            if (presentedToken.isNullOrEmpty()) return Decision.MissingToken
            return if (AutomationToken.matches(presentedToken, storedToken)) {
                Decision.Allowed
            } else {
                Decision.BadToken
            }
        }

        val supportedActions = setOf(
            LumenService.ACTION_TURN_OFF,
            LumenService.ACTION_TURN_ON,
            LumenService.ACTION_TOGGLE,
            LumenService.ACTION_REEVALUATE,
            LumenService.ACTION_CYCLE_PRESET,
            LumenService.ACTION_SET_PRESET,
            LumenService.ACTION_RESTORE_PREVIOUS,
            LumenService.ACTION_SET_INTENSITY,
            LumenService.ACTION_SET_DIM
        )

        val lastForwardedMs = ConcurrentHashMap<String, Long>()
        /**
         * Far enough in the past that the first broadcast is never read as
         * following a rejection. A plain AtomicLong starts at zero, and
         * elapsedRealtime is also near zero just after boot, so zero would
         * mean "just rejected" for the one moment the emergency path is most
         * likely to be used.
         */
        val lastRejectedMs = AtomicLong(Long.MIN_VALUE / 2)

        /** Actions that never present a token, so no token check applies. */
        internal fun isUnauthenticated(action: String?): Boolean =
            action == LumenService.ACTION_TURN_OFF

        /**
         * True when a broadcast can be refused without reading anything.
         *
         * The emergency turn-off never presents a token and has to stay
         * reachable, so it is never in this class. Everything else needs one,
         * and a missing or misshapen token is a definite no: a real caller
         * copied a token out of the app and it is the right length and
         * alphabet.
         */
        internal fun isCheaplyRejectable(action: String?, presentedToken: String?): Boolean {
            if (isUnauthenticated(action)) return false
            return presentedToken.isNullOrEmpty() || !AutomationToken.isWellFormed(presentedToken)
        }
        val throttleCount = AtomicLong()
        val rejectionCount = AtomicLong()
    }
}
