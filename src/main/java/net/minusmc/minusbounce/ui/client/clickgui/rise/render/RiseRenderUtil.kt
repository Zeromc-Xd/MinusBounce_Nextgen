package net.minusmc.minusbounce.ui.client.clickgui.rise.render

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.ScaledResolution
import net.minusmc.minusbounce.utils.render.RenderUtils
import net.minusmc.minusbounce.utils.render.ShadowUtils
import org.lwjgl.opengl.GL11
import java.awt.Color
import kotlin.math.roundToInt

object RiseRenderUtil {
    private val mc = Minecraft.getMinecraft()
    private var transformOffsetX = 0.0
    private var transformOffsetY = 0.0
    private var transformScale = 1.0

    fun setScaleTransform(x: Double, y: Double, scale: Double, animationScale: Double, animationX: Double, animationY: Double) {
        val interfaceScale = scale.coerceAtLeast(0.05)
        val frameScale = animationScale.coerceAtLeast(0.01)
        transformScale = interfaceScale * frameScale
        transformOffsetX = x * (1.0 - interfaceScale) + interfaceScale * animationX
        transformOffsetY = y * (1.0 - interfaceScale) + interfaceScale * animationY
    }

    fun clearScaleTransform() {
        transformOffsetX = 0.0
        transformOffsetY = 0.0
        transformScale = 1.0
    }

    private fun transformedX(x: Double) = x * transformScale + transformOffsetX
    private fun transformedY(y: Double) = y * transformScale + transformOffsetY

    fun roundedRectangle(x: Double, y: Double, width: Double, height: Double, radius: Double, color: Color) {
        RiseRoundedQuadShader.draw(x, y, width, height, radius, color)
    }

    fun roundedRectangle(x: Float, y: Float, width: Float, height: Float, radius: Float, color: Color) {
        roundedRectangle(x.toDouble(), y.toDouble(), width.toDouble(), height.toDouble(), radius.toDouble(), color)
    }

    fun leftRoundedRectangle(x: Double, y: Double, width: Double, height: Double, radius: Double, color: Color) {
        RiseRoundedQuadShader.draw(
            x,
            y,
            width,
            height,
            radius,
            color,
            leftTop = true,
            rightTop = false,
            rightBottom = false,
            leftBottom = true
        )
    }

    fun rectangle(x: Double, y: Double, width: Double, height: Double, color: Color) {
        RenderUtils.drawRect(x.toFloat(), y.toFloat(), (x + width).toFloat(), (y + height).toFloat(), color.rgb)
    }

    fun circle(x: Double, y: Double, radius: Double, color: Color) {
        roundedRectangle(x, y, radius, radius, radius / 2.0, color)
    }

    fun horizontalGradient(x: Double, y: Double, width: Double, height: Double, start: Color, end: Color) {
        GL11.glDisable(GL11.GL_TEXTURE_2D)
        GL11.glEnable(GL11.GL_BLEND)
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
        GL11.glShadeModel(GL11.GL_SMOOTH)
        GL11.glBegin(GL11.GL_QUADS)
        color(start)
        GL11.glVertex2d(x, y)
        GL11.glVertex2d(x, y + height)
        color(end)
        GL11.glVertex2d(x + width, y + height)
        GL11.glVertex2d(x + width, y)
        GL11.glEnd()
        GL11.glShadeModel(GL11.GL_FLAT)
        GL11.glDisable(GL11.GL_BLEND)
        GL11.glEnable(GL11.GL_TEXTURE_2D)
        GL11.glColor4f(1f, 1f, 1f, 1f)
    }

    fun fastShadow(x: Double, y: Double, width: Double, height: Double, radius: Double) {
        roundedRectangle(x - 2.0, y - 2.0, width + 4.0, height + 4.0, radius + 2.0, Color(0, 0, 0, 26))
        roundedRectangle(x - 1.0, y - 1.0, width + 2.0, height + 2.0, radius + 1.0, Color(0, 0, 0, 38))
    }

    fun dropShadow(strength: Float, x: Double, y: Double, width: Double, height: Double, alpha: Int, radius: Double) {
        try {
            ShadowUtils.shadow(strength, {
                roundedRectangle(x, y, width, height, radius, Color(0, 0, 0, alpha.coerceIn(0, 255)))
            }, {
                roundedRectangle(x, y, width, height, radius, Color.WHITE)
            })
        } catch (_: Throwable) {
            for (i in strength.roundToInt() downTo 1 step 3) {
                roundedRectangle(x - i / 2.0, y - i / 2.0, width + i, height + i, radius + i / 3.0, Color(0, 0, 0, (alpha / (i + 1)).coerceIn(1, 90)))
            }
        }
    }

    fun bloom(strength: Float, draw: () -> Unit, cut: () -> Unit = draw) {
        try {
            ShadowUtils.shadow(strength, draw, cut)
        } catch (_: Throwable) {
            draw()
        }
    }

    fun scissor(x: Double, y: Double, width: Double, height: Double) {
        val sr = ScaledResolution(mc)
        val tx = transformedX(x)
        val ty = transformedY(y)
        val tw = (width * transformScale).coerceAtLeast(0.0)
        val th = (height * transformScale).coerceAtLeast(0.0)
        GL11.glScissor(
            (tx * sr.scaleFactor).roundToInt(),
            ((sr.scaledHeight - ty - th) * sr.scaleFactor).roundToInt(),
            (tw * sr.scaleFactor).roundToInt(),
            (th * sr.scaleFactor).roundToInt()
        )
    }

    private fun color(color: Color) {
        GL11.glColor4f(color.red / 255f, color.green / 255f, color.blue / 255f, color.alpha / 255f)
    }
}
