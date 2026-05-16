package com.openlumen.prefs

import kotlinx.serialization.Serializable

/** Serializable mirror of LumenMatrix (core-engine has no kotlinx-serialization dep). */
@Serializable
data class MatrixDto(
    val r: Float = 1f,
    val g: Float = 1f,
    val b: Float = 1f,
    val biasR: Float = 0f,
    val biasG: Float = 0f,
    val biasB: Float = 0f,
    val dim: Float = 0f,
    val gammaR: Float = 1f,
    val gammaG: Float = 1f,
    val gammaB: Float = 1f
)

@Serializable
data class ScheduleDto(
    val mode: ScheduleModeDto = ScheduleModeDto.AlwaysOff,
    val startHour: Int = 22,
    val startMinute: Int = 0,
    val endHour: Int = 7,
    val endMinute: Int = 0,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val sunsetOffsetMin: Int = 0,
    val sunriseOffsetMin: Int = 0
)

@Serializable
enum class ScheduleModeDto { AlwaysOn, AlwaysOff, FixedTime, Solar }

@Serializable
enum class EngineKindDto { Auto, ColorDisplayManager, SurfaceFlinger, Kcal, Overlay }

@Serializable
data class Preferences(
    val enabled: Boolean = false,
    val activePresetKey: String = "night",
    val customMatrix: MatrixDto = MatrixDto(r = 1f, g = 0.78f, b = 0.55f),
    /** 0.0 = identity (no shift), 1.0 = full preset strength. Lerps RGB toward 1.0. */
    val presetIntensity: Float = 1f,
    /** 0.0 = no extra dim, 0.95 = max dim (Android 12+ overlay cap). */
    val dim: Float = 0f,
    val schedule: ScheduleDto = ScheduleDto(),
    val engine: EngineKindDto = EngineKindDto.Auto,
    val lightSensorEnabled: Boolean = false,
    val lightSensorLuxThreshold: Float = 2f,
    val firstRunComplete: Boolean = false
)
