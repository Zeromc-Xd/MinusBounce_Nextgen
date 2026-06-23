package net.minusmc.minusbounce.features.module.modules.movement.noslows.intave

import net.minecraft.network.play.client.C07PacketPlayerDigging
import net.minecraft.util.BlockPos
import net.minecraft.util.EnumFacing
import net.minusmc.minusbounce.event.PreMotionEvent
import net.minusmc.minusbounce.features.module.modules.movement.noslows.NoSlowMode
import net.minusmc.minusbounce.utils.PacketUtils


class Intave2: NoSlowMode("Intave2") {
    override fun onPreMotion(event: PreMotionEvent) {
        if(noslow.isEating) {
            if (mc.thePlayer.itemInUseDuration == 1 || mc.thePlayer.itemInUseDuration == 32) {
                PacketUtils.sendPacket(
                    C07PacketPlayerDigging(
                        C07PacketPlayerDigging.Action.RELEASE_USE_ITEM,
                        BlockPos(mc.thePlayer),
                        EnumFacing.UP
                    )
                )
            }
        }
    }
}