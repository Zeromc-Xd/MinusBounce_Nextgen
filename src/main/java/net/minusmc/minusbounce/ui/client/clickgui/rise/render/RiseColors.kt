package net.minusmc.minusbounce.ui.client.clickgui.rise.render

import java.awt.Color
import kotlin.math.roundToInt
import kotlin.math.sin

object RiseColors {
    val background = Color(23, 26, 33, 254)
    val secondary = Color(18, 20, 25, 255)
    val text = Color(255, 255, 255, 255)
    val secondaryText = Color(255, 255, 255, 220)
    val trinaryText = Color(255, 255, 255, 130)
    val overlay = Color(0, 0, 0, 50)
    val first = Color(71, 148, 253)
    val second = Color(71, 253, 160)

    fun withAlpha(color: Color, alpha: Int) = Color(color.red, color.green, color.blue, alpha.coerceIn(0, 255))

    fun accent(x: Double, y: Double): Color {
        val factor = sin(System.currentTimeMillis() / 600.0 + x * 0.005 + y * 0.2) * 0.5 + 0.5
        return mix(first, second, factor)
    }

    fun mix(a: Color, b: Color, factor: Double): Color {
        val f = factor.coerceIn(0.0, 1.0)
        return Color(
            (a.red + (b.red - a.red) * f).roundToInt().coerceIn(0, 255),
            (a.green + (b.green - a.green) * f).roundToInt().coerceIn(0, 255),
            (a.blue + (b.blue - a.blue) * f).roundToInt().coerceIn(0, 255)
        )
    }
}
