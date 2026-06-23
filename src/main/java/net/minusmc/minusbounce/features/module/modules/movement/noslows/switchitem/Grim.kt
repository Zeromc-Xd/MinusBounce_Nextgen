package net.minusmc.minusbounce.features.module.modules.movement.noslows.other

import net.minecraft.network.play.client.C09PacketHeldItemChange
import net.minusmc.minusbounce.event.PreMotionEvent
import net.minusmc.minusbounce.features.module.modules.movement.noslows.NoSlowMode
import net.minusmc.minusbounce.utils.PacketUtils

class Grim : NoSlowMode("Grim") {
    override fun onPreMotion(event: PreMotionEvent) {
        val slot = mc.thePlayer.inventory.currentItem
        PacketUtils.sendPacketNoEvent(C09PacketHeldItemChange(slot % 8 + 1))
        PacketUtils.sendPacketNoEvent(C09PacketHeldItemChange(slot % 7 + 2))
        PacketUtils.sendPacketNoEvent(C09PacketHeldItemChange(slot))
    }
}