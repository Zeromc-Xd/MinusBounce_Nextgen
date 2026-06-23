/*
 * MinusBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/MinusMC/MinusBounce
 */
package net.minusmc.minusbounce.features.module.modules.player

import net.minecraft.util.EnumFacing
import net.minusmc.minusbounce.event.EventTarget
import net.minusmc.minusbounce.event.RayTraceRangeEvent
import net.minusmc.minusbounce.features.module.Module
import net.minusmc.minusbounce.features.module.ModuleCategory
import net.minusmc.minusbounce.features.module.ModuleInfo
import net.minusmc.minusbounce.value.BoolValue
import net.minusmc.minusbounce.value.FloatValue


@ModuleInfo(name = "Reach", description = "Increases your reach.", category = ModuleCategory.PLAYER)
class Reach : Module() {
    private val attackRange = FloatValue(
        "Attack-Range", 3f, 3f, 10f
    )
    private val buildRange = FloatValue(
        "Build-Range", 3f, 3f, 10f
    )
    private val fixServersSideMisplace = BoolValue(
        "Fix-Server-Side-Misplace", true
    )

    private var correctedRange: Double = 0.0

    @EventTarget
    fun onRayTrace(rayTraceRangeEvent: RayTraceRangeEvent) {
        correctedRange = attackRange.get() + 0.00256
        if (fixServersSideMisplace.get()) {
            val n = 0.010625f
            if (mc.thePlayer.horizontalFacing == EnumFacing.NORTH || mc.thePlayer.horizontalFacing == EnumFacing.WEST) {
                correctedRange += (n * 2.0f).toDouble()
            }
        }
        rayTraceRangeEvent.range = correctedRange.toFloat()
        rayTraceRangeEvent.blockReachDistance = buildRange.get() + 0.00256f
    }
}
