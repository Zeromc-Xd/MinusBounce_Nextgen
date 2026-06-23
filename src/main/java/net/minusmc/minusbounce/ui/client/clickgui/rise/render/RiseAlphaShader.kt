package net.minusmc.minusbounce.ui.client.clickgui.rise.render

import net.minecraft.client.renderer.GlStateManager

object RiseAlphaShader {
    var alpha = 1f

    fun run(draw: () -> Unit) {
        GlStateManager.pushMatrix()
        GlStateManager.color(1f, 1f, 1f, alpha.coerceIn(0f, 1f))
        draw()
        GlStateManager.color(1f, 1f, 1f, 1f)
        GlStateManager.popMatrix()
    }
}
