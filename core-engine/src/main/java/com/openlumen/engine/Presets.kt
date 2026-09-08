package com.openlumen.engine

/**
 * Canonical preset library. Mirrors CF.Lumen's named filters; values were hand-tuned
 * by reference to the v3.74 build's behavior on a Pixel 6.
 */
object Presets {
    val RED      = LumenMatrix(r = 1.00f, g = 0.10f, b = 0.00f)
    val AMBER    = LumenMatrix(r = 1.00f, g = 0.55f, b = 0.10f)
    val SALMON   = LumenMatrix(r = 1.00f, g = 0.40f, b = 0.35f)
    val SEPIA    = LumenMatrix(r = 0.90f, g = 0.72f, b = 0.50f)
    /**
     * True desaturation, not a dimming. Every output channel is the Rec. 709
     * luminance of the input, which is what "grayscale" means; releases through
     * 0.7.1 shipped a flat 0.55 scale on all three channels, so the preset
     * darkened the screen by 45% and left the colour alone. The scalar
     * fallback keeps that darkening for drivers with no cross-channel terms,
     * because it is the closest a per-channel scale can get, and those drivers
     * now say so through [com.openlumen.engine.EngineCapability].
     */
    val GRAY     = LumenMatrix(
        r = 0.55f,
        g = 0.55f,
        b = 0.55f,
        daltonizer = Daltonizer.MONOCHROMACY,
        hasColorMatrix = true,
        matrixRr = 0.2126f,
        matrixRg = 0.7152f,
        matrixRb = 0.0722f,
        matrixGr = 0.2126f,
        matrixGg = 0.7152f,
        matrixGb = 0.0722f,
        matrixBr = 0.2126f,
        matrixBg = 0.7152f,
        matrixBb = 0.0722f
    )
    val NIGHT    = LumenMatrix(r = 1.00f, g = 0.78f, b = 0.55f) // ~3200K
    val DEEP     = LumenMatrix(r = 1.00f, g = 0.45f, b = 0.20f, dim = 0.30f) // pre-bedtime
    val PWM      = LumenMatrix(r = 1.00f, g = 0.82f, b = 0.60f, dim = 0.20f) // warm tint + overlay dim at high backlight
    val OFF      = LumenMatrix.IDENTITY

    /**
     * Color-vision-deficiency remap presets.
     *
     * Matrix-capable engines receive the DaltonLens Viénot 1999 linear-RGB
     * matrices for protan/deutan and the documented single-matrix tritan
     * approximation. The secure-settings driver cannot take a matrix but can
     * select AOSP's equivalent correction mode, which is what [Daltonizer]
     * names. Scalar-only engines keep the older coarse channel-scale fallbacks
     * so these presets still do something on overlay and KCAL, and say through
     * [com.openlumen.engine.EngineCapability] that they are approximating.
     */
    val PROTAN   = LumenMatrix(
        daltonizer = Daltonizer.PROTANOMALY,
        r = 0.85f,
        g = 1.00f,
        b = 0.95f,
        hasColorMatrix = true,
        matrixRr = 0.11238f,
        matrixRg = 0.88762f,
        matrixRb = 0.00000f,
        matrixGr = 0.11238f,
        matrixGg = 0.88762f,
        matrixGb = -0.00000f,
        matrixBr = 0.00401f,
        matrixBg = -0.00401f,
        matrixBb = 1.00000f
    )
    val DEUTAN   = LumenMatrix(
        daltonizer = Daltonizer.DEUTERANOMALY,
        r = 1.00f,
        g = 0.85f,
        b = 0.95f,
        hasColorMatrix = true,
        matrixRr = 0.29275f,
        matrixRg = 0.70725f,
        matrixRb = 0.00000f,
        matrixGr = 0.29275f,
        matrixGg = 0.70725f,
        matrixGb = -0.00000f,
        matrixBr = -0.02234f,
        matrixBg = 0.02234f,
        matrixBb = 1.00000f
    )
    val TRITAN   = LumenMatrix(
        daltonizer = Daltonizer.TRITANOMALY,
        r = 1.00f,
        g = 0.95f,
        b = 0.85f,
        hasColorMatrix = true,
        matrixRr = 1.00000f,
        matrixRg = 0.14461f,
        matrixRb = -0.14461f,
        matrixGr = 0.00000f,
        matrixGg = 0.85924f,
        matrixGb = 0.14076f,
        matrixBr = -0.00000f,
        matrixBg = 0.85924f,
        matrixBb = 0.14076f
    )

    data class Entry(val key: String, val displayName: String, val matrix: LumenMatrix)

    val ALL: List<Entry> = listOf(
        Entry("off",      "Off",         OFF),
        Entry("night",    "Night",       NIGHT),
        Entry("amber",    "Amber",       AMBER),
        Entry("red",      "Red",         RED),
        Entry("salmon",   "Salmon",      SALMON),
        Entry("sepia",    "Sepia",       SEPIA),
        Entry("gray",     "Grayscale",   GRAY),
        Entry("deep",     "Deep Sleep",  DEEP),
        Entry("pwm",      "PWM Comfort", PWM),
        Entry("protan",   "Protan",      PROTAN),
        Entry("deutan",   "Deutan",      DEUTAN),
        Entry("tritan",   "Tritan",      TRITAN)
    )

    fun byKey(key: String): Entry? = ALL.firstOrNull { it.key == key }
}
