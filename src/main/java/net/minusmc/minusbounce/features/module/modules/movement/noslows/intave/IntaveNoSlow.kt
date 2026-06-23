package net.minusmc.minusbounce.features.module.modules.movement.noslows.intave

import net.minecraft.item.ItemSword
import net.minecraft.network.play.client.C07PacketPlayerDigging
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement
import net.minecraft.util.BlockPos
import net.minecraft.util.EnumFacing
import net.minusmc.minusbounce.event.PreMotionEvent
import net.minusmc.minusbounce.features.module.modules.movement.noslows.NoSlowMode
import net.minusmc.minusbounce.utils.MovementUtils


class Intave: NoSlowMode("Intave") {
    private var lastUsingItem = false

    override fun onPreMotion(event: PreMotionEvent) {
        if (!mc.thePlayer.isUsingItem || noslow.heldItem == null) {
            lastUsingItem = false
            return
        }

        if(MovementUtils.isMoving){
            if(noslow.isEating){
                if(!lastUsingItem){
                    mc.netHandler.addToSendQueue(C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.UP))
                }
            } else {
                if(noslow.heldItem?.item is ItemSword){
                    mc.netHandler.addToSendQueue(C08PacketPlayerBlockPlacement(noslow.heldItem))
                }
            }
        }

        lastUsingItem = true
    }

    override fun onDisable() {
        lastUsingItem = false
    }
}