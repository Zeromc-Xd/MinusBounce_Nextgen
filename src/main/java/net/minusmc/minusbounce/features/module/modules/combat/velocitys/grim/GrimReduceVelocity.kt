package net.minusmc.minusbounce.features.module.modules.combat.velocitys.grim

import net.minecraft.network.play.server.S12PacketEntityVelocity
import net.minecraft.potion.Potion
import net.minusmc.minusbounce.event.EventState
import net.minusmc.minusbounce.event.MoveInputEvent
import net.minusmc.minusbounce.event.PacketEvent
import net.minusmc.minusbounce.event.WorldEvent
import net.minusmc.minusbounce.features.module.modules.combat.velocitys.VelocityMode
import net.minusmc.minusbounce.value.IntegerValue

class GrimReduceVelocity : VelocityMode("GrimReduce") {
    private val jumpLimit = IntegerValue("JumpLimit", 2, 1, 5)

    private var hasReceivedVelocity = false
    private var jumpCount = 0

    override fun onEnable() = resetState()

    override fun onDisable() = resetState()

    override fun onWorld(event: WorldEvent) = resetState()

    override fun onPacket(event: PacketEvent) {
        if (event.eventType != EventState.RECEIVE || event.isCancelled) return

        val player = mc.thePlayer ?: return
        val packet = event.packet as? S12PacketEntityVelocity ?: return

        if (packet.entityID == player.entityId) {
            hasReceivedVelocity = true
        }
    }

    override fun onInput(event: MoveInputEvent) {
        if (!hasReceivedVelocity) return

        val player = mc.thePlayer ?: run {
            resetState()
            return
        }

        if (player.onGround &&
            player.hurtTime >= 8 &&
            player.isSprinting &&
            mc.currentScreen == null &&
            !player.isPotionActive(Potion.jump) &&
            !player.isInWater &&
            !player.isInLava &&
            !player.isInWeb
        ) {
            if (jumpCount >= jumpLimit.get()) {
                resetState()
            } else {
                jumpCount++
                event.jump = true
            }
        } else if (player.hurtTime <= 1) {
            resetState()
        }
    }

    private fun resetState() {
        hasReceivedVelocity = false
        jumpCount = 0
    }
}
