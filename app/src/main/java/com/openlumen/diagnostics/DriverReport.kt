package com.openlumen.diagnostics

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import com.openlumen.BuildConfig
import com.openlumen.engine.DriverProbe
import com.openlumen.engine.engines.KcalEngine
import com.openlumen.engine.engines.SurfaceFlingerEngine
import com.openlumen.prefs.Preferences
import java.lang.reflect.InvocationTargetException
import java.time.Instant

/**
 * Builds a human-readable, paste-friendly device + driver report.
 *
 * Goals:
 * - Contains zero PII. No location, no installed app list, no usernames, no
 *   device identifiers stronger than `Build.MODEL`. Specifically NOT
 *   `Settings.Secure.ANDROID_ID` or `Build.SERIAL`.
 * - Stable enough to grep across bug reports — section headers and ordering
 *   should not change without bumping the visible report version.
 * - Self-explanatory so a reporter doesn't have to know what each field means.
 *
 * Tied to roadmap candidate C02 (In-app driver report export).
 */
object DriverReport {

    /** Bump when the layout changes so we can spot stale-format reports. */
    private const val REPORT_VERSION = 2
    private const val QUERY_ADVANCED_PROTECTION_MODE =
        "android.permission.QUERY_ADVANCED_PROTECTION_MODE"

    fun build(
        context: Context,
        prefs: Preferences,
        probes: List<DriverProbe.Probe>
    ): String = buildString {
        appendHeader()
        appendApp(context)
        appendDevice()
        appendPermissions(context)
        appendAdvancedProtection(context)
        appendProbes(probes)
        appendConfig(prefs)
        appendDiagnostics(context)
        appendFooter()
    }

    /**
     * Append the tail of the local diagnostics log (last ~3 KB) so a paste
     * captures recent event history without including the full bounded log.
     * Tied to roadmap candidate C52.
     */
    private fun StringBuilder.appendDiagnostics(context: Context) {
        appendLine("Recent diagnostics")
        appendLine("---")
        val full = DiagnosticsLog.read(context)
        if (full.isBlank()) {
            appendLine("(no events recorded)")
        } else if (full.length <= DIAG_TAIL_BYTES) {
            appendLine(full.trimEnd())
        } else {
            // Take the tail and drop the (almost certainly) partial first
            // line so each printed line is complete and grep-friendly.
            // substringAfter('\n', defaultValue=tail) returns the whole
            // tail if the cut happened to land on a line boundary.
            val tail = full.substring(full.length - DIAG_TAIL_BYTES)
            appendLine(tail.substringAfter('\n', tail).trimEnd())
        }
        appendLine()
    }

    private const val DIAG_TAIL_BYTES = 3000

    private fun StringBuilder.appendHeader() {
        appendLine("OpenLumen driver report v$REPORT_VERSION")
        appendLine("===")
        appendLine("Generated: ${Instant.now()}")
        appendLine()
    }

    private fun StringBuilder.appendApp(context: Context) {
        appendLine("App")
        appendLine("---")
        appendLine("Version: ${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})")
        appendLine("Package: ${context.packageName}")
        appendLine("Build type: ${BuildConfig.BUILD_TYPE}")
        appendLine()
    }

    private fun StringBuilder.appendDevice() {
        appendLine("Device")
        appendLine("---")
        appendLine("Manufacturer: ${Build.MANUFACTURER}")
        appendLine("Brand: ${Build.BRAND}")
        appendLine("Model: ${Build.MODEL}")
        appendLine("Device: ${Build.DEVICE}")
        appendLine("Product: ${Build.PRODUCT}")
        appendLine("Hardware: ${Build.HARDWARE}")
        val soc = if (Build.VERSION.SDK_INT >= 31) {
            "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}"
        } else {
            "unknown (API <31)"
        }
        appendLine("SoC: $soc")
        appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("Fingerprint: ${Build.FINGERPRINT}")
        appendLine()
    }

    private fun StringBuilder.appendPermissions(context: Context) {
        appendLine("Permissions")
        appendLine("---")
        val overlay = if (Settings.canDrawOverlays(context)) "granted" else "not granted"
        appendLine("SYSTEM_ALERT_WINDOW: $overlay")
        appendLine("WRITE_SECURE_SETTINGS: ${grantStatus(context, Manifest.permission.WRITE_SECURE_SETTINGS)}")
        if (Build.VERSION.SDK_INT >= 36) {
            appendLine("QUERY_ADVANCED_PROTECTION_MODE: ${grantStatus(context, QUERY_ADVANCED_PROTECTION_MODE)}")
        } else {
            appendLine("QUERY_ADVANCED_PROTECTION_MODE: n/a (API <36)")
        }
        if (Build.VERSION.SDK_INT >= 33) {
            appendLine("POST_NOTIFICATIONS: ${grantStatus(context, Manifest.permission.POST_NOTIFICATIONS)}")
        } else {
            appendLine("POST_NOTIFICATIONS: n/a (API <33)")
        }
        val exactAlarm = if (Build.VERSION.SDK_INT >= 31) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            if (am?.canScheduleExactAlarms() == true) "allowed" else "not allowed"
        } else {
            "implicit (API <31)"
        }
        appendLine("Exact alarms: $exactAlarm")
        appendLine()
    }

    private fun StringBuilder.appendAdvancedProtection(context: Context) {
        appendLine("Advanced Protection")
        appendLine("---")
        appendLine("State: ${advancedProtectionStatus(context)}")
        appendLine("OpenLumen impact: none; no AccessibilityService or UsageStats backend is used.")
        appendLine()
    }

    private fun StringBuilder.appendProbes(probes: List<DriverProbe.Probe>) {
        appendLine("Engine probes")
        appendLine("---")
        if (probes.isEmpty()) {
            appendLine("(no probe results yet — open Driver tab and tap 'Re-probe', then re-generate the report)")
        } else {
            probes.forEach { p ->
                val mark = if (p.available) "AVAILABLE" else "not available"
                appendLine("- ${p.engine.kind.name} (rank ${p.engine.kind.rank}, root=${p.engine.kind.requiresRoot}): $mark")
                // Per-engine diagnostic detail (C03, C04). Helps a reviewer see
                // exactly which SurfaceFlinger transaction code or KCAL sysfs
                // surface the probe picked on this device.
                when (val e = p.engine) {
                    is SurfaceFlingerEngine -> {
                        e.activeTransactionCode?.let {
                            appendLine("    transaction code: $it")
                        }
                    }
                    is KcalEngine -> {
                        e.activeBasePath?.let {
                            appendLine("    kcal sysfs: $it")
                        }
                    }
                    else -> Unit
                }
            }
        }
        appendLine()
    }

    private fun StringBuilder.appendConfig(prefs: Preferences) {
        appendLine("Configuration")
        appendLine("---")
        appendLine("Engine choice: ${prefs.engine.name}")
        appendLine("Filter enabled: ${prefs.enabled}")
        appendLine("Active preset: ${prefs.activePresetKey}")
        appendLine("Schedule mode: ${prefs.schedule.mode.name}")
        val coords = if (prefs.schedule.latitude == null || prefs.schedule.longitude == null) {
            "unset"
        } else {
            "set (coordinates intentionally redacted from this report)"
        }
        appendLine("Solar location: $coords")
        appendLine("Light sensor trigger: ${prefs.lightSensorEnabled}")
        appendLine()
    }

    private fun StringBuilder.appendFooter() {
        appendLine("---")
        appendLine("Generated by OpenLumen. No identifiers, no location, no app list,")
        appendLine("no telemetry. Safe to paste into a public issue.")
    }

    private fun grantStatus(context: Context, perm: String): String =
        when (context.checkSelfPermission(perm)) {
            PackageManager.PERMISSION_GRANTED -> "granted"
            else -> "not granted"
        }

    private fun advancedProtectionStatus(context: Context): String {
        if (Build.VERSION.SDK_INT < 36) return "n/a (API <36)"
        return try {
            val serviceName = runCatching {
                Context::class.java.getField("ADVANCED_PROTECTION_SERVICE").get(null) as? String
            }.getOrNull() ?: "advanced_protection"
            val service = context.getSystemService(serviceName)
                ?: return "unknown (system service unavailable)"
            val enabled = service.javaClass
                .getMethod("isAdvancedProtectionEnabled")
                .invoke(service) as? Boolean
                ?: return "unknown (unexpected API return)"
            if (enabled) "enabled" else "disabled"
        } catch (t: Throwable) {
            val cause = (t as? InvocationTargetException)?.targetException ?: t
            when (cause) {
                is SecurityException -> "unknown (QUERY_ADVANCED_PROTECTION_MODE not granted)"
                else -> "unknown (${cause.javaClass.simpleName})"
            }
        }
    }
}
