package net.minusmc.minusbounce.features.module.modules.movement.noslows.ncp

import net.minecraft.network.play.client.C09PacketHeldItemChange
import net.minusmc.minusbounce.event.PreMotionEvent
import net.minusmc.minusbounce.features.module.modules.movement.noslows.NoSlowMode
import net.minusmc.minusbounce.utils.BadPacketUtils
import net.minusmc.minusbounce.utils.PacketUtils


class NewNCPNoSlow : NoSlowMode("NewNCP") {
    private var disable = 0
    override fun onPreMotion(event: PreMotionEvent) {
        disable++
        if (this.disable > 10 && !BadPacketUtils.bad(false, true, true, false, false)
        ) {
            PacketUtils.sendPacketNoEvent(C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem % 8 + 1))
            PacketUtils.sendPacketNoEvent(C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem))
        }
    }
}