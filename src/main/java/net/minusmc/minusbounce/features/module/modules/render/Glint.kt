/*
 * MinusBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/MinusMC/MinusBounce
 */
package net.minusmc.minusbounce.features.module.modules.render

import net.minusmc.minusbounce.features.module.Module
import net.minusmc.minusbounce.features.module.ModuleCategory
import net.minusmc.minusbounce.features.module.ModuleInfo
import net.minusmc.minusbounce.utils.render.ColorUtils
import net.minusmc.minusbounce.value.IntegerValue
import net.minusmc.minusbounce.value.ListValue
import java.awt.Color

@ModuleInfo(name = "Glint", description = "skid", category = ModuleCategory.RENDER)
class Glint : Module() {

    private val modeValue = ListValue("Mode", arrayOf("Rainbow", "Custom"), "Custom")
    private val redValue = IntegerValue("Red", 255, 0, 255).displayable { modeValue.get().equals("Custom", ignoreCase = true) }
    private val greenValue = IntegerValue("Green", 0, 0, 255).displayable { modeValue.get().equals("Custom", ignoreCase = true) }
    private val blueValue = IntegerValue("Blue", 0, 0, 255).displayable { modeValue.get().equals("Custom", ignoreCase = true) }

    fun getColor(): Color {
        val mode = modeValue.get().lowercase()
        return when (mode) {
            "rainbow" -> ColorUtils.rainbow()
            else -> Color(redValue.get(), greenValue.get(), blueValue.get())
        }
    }
}