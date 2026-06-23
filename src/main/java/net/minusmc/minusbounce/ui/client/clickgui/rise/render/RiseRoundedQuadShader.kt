package net.minusmc.minusbounce.ui.client.clickgui.rise.render

import net.minecraft.client.renderer.GlStateManager
import net.minusmc.minusbounce.utils.render.shader.Shader
import org.lwjgl.opengl.GL11
import java.awt.Color
import kotlin.math.min

object RiseRoundedQuadShader : Shader("rise-rounded-quad.frag") {
    override fun setupUniforms() {
        setupUniform("u_size")
        setupUniform("u_radius")
        setupUniform("u_color")
        setupUniform("u_edges")
    }

    override fun updateUniforms() = Unit

    fun draw(
        x: Double,
        y: Double,
        width: Double,
        height: Double,
        radius: Double,
        color: Color,
        leftTop: Boolean = true,
        rightTop: Boolean = true,
        rightBottom: Boolean = true,
        leftBottom: Boolean = true
    ) {
        if (width <= 0.0 || height <= 0.0 || color.alpha <= 0) return

        val renderRadius = radius.coerceIn(0.0, min(width, height) / 2.0).toFloat()
        val blendEnabled = GL11.glIsEnabled(GL11.GL_BLEND)

        startShader()
        setUniformf("u_size", width.toFloat(), height.toFloat())
        setUniformf("u_radius", renderRadius)
        setUniformf(
            "u_color",
            color.red / 255f,
            color.green / 255f,
            color.blue / 255f,
            color.alpha / 255f
        )
        setUniformf(
            "u_edges",
            if (leftTop) 1f else 0f,
            if (rightTop) 1f else 0f,
            if (rightBottom) 1f else 0f,
            if (leftBottom) 1f else 0f
        )

        GlStateManager.enableBlend()
        drawQuad(x.toFloat(), y.toFloat(), width.toFloat(), height.toFloat())
        stopShader()

        if (!blendEnabled) GlStateManager.disableBlend()
        GlStateManager.resetColor()
    }
}
