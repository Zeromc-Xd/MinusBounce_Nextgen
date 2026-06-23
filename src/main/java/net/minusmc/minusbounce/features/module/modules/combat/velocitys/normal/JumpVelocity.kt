package net.minusmc.minusbounce.features.module.modules.combat.velocitys.normal

import net.minecraft.network.play.server.S12PacketEntityVelocity
import net.minecraft.util.MathHelper
import net.minusmc.minusbounce.event.PacketEvent
import net.minusmc.minusbounce.features.module.modules.combat.velocitys.VelocityMode

class JumpVelocity : VelocityMode("Jump") {
    override fun onPacket(event: PacketEvent) {
        mc.thePlayer ?: return
        if (event.packet is S12PacketEntityVelocity) {
            if (mc.thePlayer.hurtTime > 0 && mc.thePlayer.onGround) {
                mc.thePlayer.motionY = 0.42

                val yaw = mc.thePlayer.rotationYaw * 0.017453292F
                mc.thePlayer.motionX -= MathHelper.sin(yaw) * 0.2
                mc.thePlayer.motionZ += MathHelper.cos(yaw) * 0.2
            }
        }
    }
}