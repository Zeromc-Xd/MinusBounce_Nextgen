package net.minusmc.minusbounce.features.module.modules.movement

import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement
import net.minecraft.util.BlockPos
import net.minecraft.util.EnumFacing
import net.minusmc.minusbounce.event.EventTarget
import net.minusmc.minusbounce.event.PreUpdateEvent
import net.minusmc.minusbounce.features.module.Module
import net.minusmc.minusbounce.features.module.ModuleCategory
import net.minusmc.minusbounce.features.module.ModuleInfo
import net.minusmc.minusbounce.utils.InventoryUtils
import net.minusmc.minusbounce.utils.PacketUtils


@ModuleInfo("WallClimb", "Wall Climb", "Look! Spiderman", ModuleCategory.MOVEMENT)
class WallClimb: Module() {
    private var active = false

    @EventTarget
    fun onUpdate(e: PreUpdateEvent){
        if (mc.thePlayer.isCollidedHorizontally) {
            if (mc.thePlayer.onGround) {
                active = true
            }

            if (active) {
                val block = InventoryUtils.findBlockInHotbar() ?: return

                if (block != -1) {
                    mc.thePlayer.inventory.currentItem = block - 36
                    if (mc.thePlayer.inventory.currentItem == block) {
                        PacketUtils.sendPacket(
                            C08PacketPlayerBlockPlacement(
                                BlockPos(mc.thePlayer).down(), EnumFacing.UP.index, mc.thePlayer.inventory.getCurrentItem(), 0.0f, 1.0f, 0.0f
                            )
                        )
                    }

                    mc.thePlayer.motionY = 0.42
                }
            }
        } else {
            active = false
        }
    }
}