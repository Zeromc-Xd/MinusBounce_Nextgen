package net.minusmc.minusbounce.features.module.modules.movement.noslows.other

import net.minecraft.item.ItemSword
import net.minecraft.network.play.client.C07PacketPlayerDigging
import net.minecraft.network.play.client.C0CPacketInput
import net.minecraft.util.BlockPos
import net.minecraft.util.EnumFacing
import net.minusmc.minusbounce.event.PreMotionEvent
import net.minusmc.minusbounce.features.module.modules.movement.noslows.NoSlowMode

class Polar: NoSlowMode("Polar"){
    override fun onPreMotion(event: PreMotionEvent) {
        if(noslow.isEating){
            if(!noslow.lastUsingItem){
                mc.netHandler.addToSendQueue(C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.UP))
            }
            mc.netHandler.addToSendQueue(C0CPacketInput(0.0F, 0.82f, false, false))
        } else {
            if(noslow.heldItem?.item is ItemSword){
                mc.netHandler.addToSendQueue(C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.UP))
                mc.netHandler.addToSendQueue(C0CPacketInput(0.0F, 0.82f, false, false))
            }
        }
    }
}