package net.minusmc.minusbounce.ui.client.clickgui.rise.render

import kotlin.math.pow

enum class RiseEasing {
    LINEAR,
    EASE_IN_EXPO,
    EASE_OUT_EXPO;

    fun transform(value: Double): Double {
        val v = value.coerceIn(0.0, 1.0)
        return when (this) {
            LINEAR -> v
            EASE_IN_EXPO -> if (v <= 0.0) 0.0 else 2.0.pow(10.0 * v - 10.0)
            EASE_OUT_EXPO -> if (v >= 1.0) 1.0 else 1.0 - 2.0.pow(-10.0 * v)
        }
    }
}
