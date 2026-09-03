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

    /**
     * What this driver can actually express. Presets are written against the
     * most capable driver, so a less capable one silently approximates them —
     * the grayscale preset on a driver that carries only a colour temperature
     * used to come out as no change at all. Compare against
     * [LumenMatrix.requiredCapabilities] to find out what a given preset will
     * lose before applying it.
     */
    val capabilities: Set<EngineCapability>

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

/**
 * The parts of a [LumenMatrix] a driver may or may not be able to honour.
 * Deliberately coarse: these are the three differences a user can see, not an
 * inventory of every field.
 */
enum class EngineCapability {
    /**
     * Cross-channel terms. Needed for anything that mixes one channel into
     * another, which is every grayscale and colour-vision preset. A driver
     * without it can only scale each channel on its own.
     */
    COLOR_MATRIX,

    /**
     * A per-channel curve rather than one flat multiplier.
     */
    PER_CHANNEL_GAMMA,

    /**
     * Darkening past the panel's own minimum backlight.
     */
    SUB_MINIMUM_DIM,

    /**
     * Selecting one of the system's built-in colour-correction modes. A driver
     * with this but without [COLOR_MATRIX] reaches grayscale and colour-vision
     * correction through AOSP's matrices instead of OpenLumen's, so the result
     * is correct but not pixel-identical to the root drivers.
     */
    SYSTEM_COLOR_CORRECTION
}

/**
 * Which capabilities this matrix actually needs to render as intended. A
 * preset that needs nothing renders the same on every driver.
 */
fun LumenMatrix.requiredCapabilities(): Set<EngineCapability> = buildSet {
    if (hasColorMatrix && !isDiagonalMatrix()) add(EngineCapability.COLOR_MATRIX)
    if (gammaR != 1f || gammaG != 1f || gammaB != 1f) add(EngineCapability.PER_CHANNEL_GAMMA)
    if (effectiveDim > 0f) add(EngineCapability.SUB_MINIMUM_DIM)
}

/**
 * What [engine] cannot honour about this matrix. Empty means the preset renders
 * as designed. A driver that offers [EngineCapability.SYSTEM_COLOR_CORRECTION]
 * covers a matrix requirement the preset can name a mode for, because the
 * system applies its own equivalent.
 */
fun LumenMatrix.unsupportedBy(engine: Set<EngineCapability>): Set<EngineCapability> {
    val missing = requiredCapabilities() - engine
    if (
        EngineCapability.COLOR_MATRIX in missing &&
        daltonizer != Daltonizer.NONE &&
        EngineCapability.SYSTEM_COLOR_CORRECTION in engine
    ) {
        return missing - EngineCapability.COLOR_MATRIX
    }
    return missing
}

enum class EngineKind(val displayName: String, val requiresRoot: Boolean, val rank: Int) {
    COLOR_DISPLAY_MANAGER("System Night Light (secure settings)", requiresRoot = false, rank = 100),
    SURFACE_FLINGER       ("SurfaceFlinger color matrix (root)", requiresRoot = true,  rank = 90),
    KCAL                  ("KCAL kernel driver (root)",          requiresRoot = true,  rank = 70),
    OVERLAY               ("Overlay (rootless fallback)",         requiresRoot = false, rank = 10);
}
