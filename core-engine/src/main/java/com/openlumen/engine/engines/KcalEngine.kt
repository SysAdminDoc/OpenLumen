package com.openlumen.engine.engines

import android.content.Context
import com.openlumen.engine.ColorEngine
import com.openlumen.engine.EngineKind
import com.openlumen.engine.LumenMatrix
import com.openlumen.engine.Su
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Writes to the KCAL kernel driver's sysfs nodes. Requires:
 *   - root
 *   - Qualcomm SoC
 *   - a custom kernel that exposes /sys/devices/platform/kcal_ctrl.0/kcal*
 *
 * KCAL is a scalar-per-channel driver (no cross-channel matrix). We map LumenMatrix to
 * RGB triplets in 0-256 and combine the dim factor into the per-channel scalar.
 */
class KcalEngine : ColorEngine {
    override val kind = EngineKind.KCAL

    private companion object {
        const val BASE = "/sys/devices/platform/kcal_ctrl.0"
        const val NODE_RGB = "$BASE/kcal"          // "R G B"  range 0-256
        const val NODE_MIN = "$BASE/kcal_min"      // global minimum
        const val NODE_ENABLE = "$BASE/kcal_enable" // 0/1
    }

    override suspend fun isAvailable(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (!Su.isAvailable()) return@withContext false
        Su.runCommand("test -f $NODE_RGB && test -f $NODE_ENABLE && echo ok")
            .stdout.contains("ok")
    }

    override suspend fun apply(context: Context, matrix: LumenMatrix) = withContext(Dispatchers.IO) {
        val s = matrix.scaledRgb()
        val r = (s[0] * 256f).toInt().coerceIn(0, 256)
        val g = (s[1] * 256f).toInt().coerceIn(0, 256)
        val b = (s[2] * 256f).toInt().coerceIn(0, 256)
        Su.runShell(
            """
            echo '1' > $NODE_ENABLE
            echo '20' > $NODE_MIN
            echo '$r $g $b' > $NODE_RGB
            """.trimIndent()
        )
        Unit
    }

    override suspend fun clear(context: Context) = withContext(Dispatchers.IO) {
        Su.runShell(
            """
            echo '256 256 256' > $NODE_RGB
            echo '0' > $NODE_ENABLE
            """.trimIndent()
        )
        Unit
    }
}
