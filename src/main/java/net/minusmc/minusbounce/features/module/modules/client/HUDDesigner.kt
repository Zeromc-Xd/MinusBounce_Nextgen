/*
 * MinusBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/MinusMC/MinusBounce
 */
package net.minusmc.minusbounce.features.module.modules.client

import net.minecraft.client.gui.ScaledResolution
import net.minusmc.minusbounce.features.module.Module
import net.minusmc.minusbounce.features.module.ModuleCategory
import net.minusmc.minusbounce.features.module.ModuleInfo
import net.minusmc.minusbounce.ui.client.hud.designer.GuiHudDesigner

@ModuleInfo(name = "HUDDesigner", description = "Edit HUD", category = ModuleCategory.CLIENT, onlyEnable = true)
class HUDDesigner : Module() {
    override fun onEnable() {
        val sr = ScaledResolution(mc)
        val hud = GuiHudDesigner()
        mc.currentScreen = hud

        mc.setIngameNotInFocus()
        hud.setWorldAndResolution(mc, sr.scaledWidth, sr.scaledHeight)
        mc.skipRenderWorld = false
    }
}