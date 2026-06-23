package net.minusmc.minusbounce.features.module.modules.combat.velocitys.grim

import net.minecraft.network.play.client.C03PacketPlayer.C06PacketPlayerPosLook
import net.minecraft.network.play.client.C07PacketPlayerDigging
import net.minecraft.network.play.server.S08PacketPlayerPosLook
import net.minecraft.network.play.server.S12PacketEntityVelocity
import net.minecraft.network.play.server.S27PacketExplosion
import net.minecraft.util.BlockPos
import net.minecraft.util.EnumFacing
import net.minusmc.minusbounce.event.PacketEvent
import net.minusmc.minusbounce.event.PreMotionEvent
import net.minusmc.minusbounce.features.module.modules.combat.velocitys.VelocityMode
import net.minusmc.minusbounce.utils.PacketUtils
import net.minusmc.minusbounce.utils.RotationUtils
import net.minusmc.minusbounce.utils.timer.MSTimer

class GrimC07Velocity : VelocityMode("GrimLatest") {
    private var canCancel = false

    private var flagTimer = MSTimer()

    override fun onEnable() {
        canCancel = false
        flagTimer.reset()
    }

    override fun onTick() {
        if (!flagTimer.hasTimePassed(50)) {
            canCancel = false
            return
        }

        if (canCancel) {
            mc.netHandler.addToSendQueue(
                C06PacketPlayerPosLook(
                    mc.thePlayer.posX,
                    mc.thePlayer.posY,
                    mc.thePlayer.posZ,
                    mc.thePlayer.rotationYaw,
                    mc.thePlayer.rotationPitch,
                    mc.thePlayer.onGround
                )
            )
            mc.netHandler.addToSendQueue(
                C07PacketPlayerDigging(
                    C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK, 
                    BlockPos(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ), 
                    EnumFacing.DOWN
                )
            )
            canCancel = false
        }
    }

    override fun onPacket(event: PacketEvent) {
        val packet = event.packet

        if (packet is S08PacketPlayerPosLook) {
            flagTimer.reset()
        }

        if (!flagTimer.hasTimePassed(50)) {
            canCancel = false
            return
        }

        if (packet is S12PacketEntityVelocity && packet.entityID == mc.thePlayer.entityId) {
            event.cancelEvent()
            canCancel = true
        } else if (packet is S27PacketExplosion) {
            event.cancelEvent()
            canCancel = true
        }
    }
}