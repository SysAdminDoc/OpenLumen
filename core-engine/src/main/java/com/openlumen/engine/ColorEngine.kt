package com.openlumen.engine

import android.content.Context

/**
 * Abstraction over the four ways OpenLumen can shift the on-screen color of an Android display.
 *
 * Engines are listed in [EngineKind] roughly best-to-worst on image quality and power cost:
 *
 * - [EngineKind.COLOR_DISPLAY_MANAGER] — system Night Light and Extra Dim, driven through
 *   `Settings.Secure` under WRITE_SECURE_SETTINGS. No root needed. API 29+. Framebuffer level.
 *   The enum name is historical: releases through 0.7.1 reflected on `ColorDisplayManager`,
 *   which can never work on a user install (see `SecureSettingsEngine`). The identifier is
 *   kept because it is persisted in `EngineKindDto`.
 * - [EngineKind.SURFACE_FLINGER]      — `service call SurfaceFlinger 1015` via `su`.
 *   Any SoC, framebuffer level, requires root.
 * - [EngineKind.KCAL]                 — `/sys/devices/platform/kcal_ctrl.0/kcal*` writes via `su`.
 *   Qualcomm + custom kernel only. Panel-driver level.
 * - [EngineKind.OVERLAY]              — TYPE_APPLICATION_OVERLAY with PorterDuff blend.
 *   Universal fallback. No root. Capped at ~80% opacity by Android 12+ untrusted-touch rules.
 */
interface ColorEngine {
    val kind: EngineKind

    /** Probe whether this engine can actually run on the current device, right now. Cheap. */
    suspend fun isAvailable(context: Context): Boolean

    /**
     * Apply [matrix] to the display. Idempotent; safe to call from any thread.
     *
     * A failed/no-op operation must be returned as [EngineResult.Failure] rather
     * than swallowed. The service uses this result to keep its deduplication
     * state retryable and to invalidate a cached Auto selection.
     */
    suspend fun apply(context: Context, matrix: LumenMatrix): EngineResult

    /**
     * Restore the identity transform. Engines must be safe to call clear()
     * without prior apply().
     */
    suspend fun clear(context: Context): EngineResult
}

sealed interface EngineResult {
    data object Success : EngineResult

    data class Failure(val message: String) : EngineResult
}

enum class EngineKind(val displayName: String, val requiresRoot: Boolean, val rank: Int) {
    COLOR_DISPLAY_MANAGER("System Night Light (secure settings)", requiresRoot = false, rank = 100),
    SURFACE_FLINGER       ("SurfaceFlinger color matrix (root)", requiresRoot = true,  rank = 90),
    KCAL                  ("KCAL kernel driver (root)",          requiresRoot = true,  rank = 70),
    OVERLAY               ("Overlay (rootless fallback)",         requiresRoot = false, rank = 10);
}
