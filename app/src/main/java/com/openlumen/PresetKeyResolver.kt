package com.openlumen

import com.openlumen.engine.Presets

/** Single app-level view of the built-in preset catalog for every command path. */
object PresetKeyResolver {
    val knownKeys: Set<String> = Presets.ALL.map { it.key }.toSet()

    fun isKnown(key: String): Boolean = key in knownKeys

    fun isSelectable(key: String): Boolean = key == "custom" || isKnown(key)
}
