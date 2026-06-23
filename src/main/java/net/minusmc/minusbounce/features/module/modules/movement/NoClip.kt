package net.minusmc.minusbounce.features.module.modules.movement

import net.minecraft.block.BlockAir
import net.minecraft.util.AxisAlignedBB
import net.minecraft.util.MovingObjectPosition
import net.minusmc.minusbounce.event.BlockBBEvent
import net.minusmc.minusbounce.event.EventTarget
import net.minusmc.minusbounce.event.PreUpdateEvent
import net.minusmc.minusbounce.event.PushOutEvent
import net.minusmc.minusbounce.features.module.Module
import net.minusmc.minusbounce.features.module.ModuleCategory
import net.minusmc.minusbounce.features.module.ModuleInfo
import net.minusmc.minusbounce.utils.InventoryUtils
import net.minusmc.minusbounce.utils.PlayerUtils
import net.minusmc.minusbounce.utils.Rotation
import net.minusmc.minusbounce.utils.RotationUtils
import net.minusmc.minusbounce.utils.movement.MovementFixType
import net.minusmc.minusbounce.value.BoolValue


@ModuleInfo("NoClip", "No Clip", "Oops!", ModuleCategory.MOVEMENT)
class NoClip : Module() {
    private val block = BoolValue("Block", false)

    override fun onDisable() {
        mc.thePlayer.noClip = false
    }

    @EventTarget
    fun onBlock(event: BlockBBEvent){
        if (PlayerUtils.insideBlock()) {
            event.boundingBox = null

            // Sets The Bounding Box To The Players Y Position.
            if (event.block !is BlockAir && !mc.gameSettings.keyBindSneak.isKeyDown) {
                val x = event.x.toDouble()
                val y = event.y.toDouble()
                val z = event.z.toDouble()

                if (y < mc.thePlayer.posY) {
                    event.boundingBox = AxisAlignedBB.fromBounds(-15.0, -1.0, -15.0, 15.0, 1.0, 15.0).offset(x, y, z)
                }
            }
        }
    }

    @EventTarget
    fun onPush(event: PushOutEvent){
        event.cancelEvent()
    }

    @EventTarget
    fun onUpdate(event: PreUpdateEvent){
        mc.thePlayer.noClip = true

        if (block.get()) {
            val slot = InventoryUtils.findBlockInHotbar() ?: return

            if (slot != -1) {
                mc.thePlayer.inventory.currentItem = slot - 36
            }

            if(PlayerUtils.insideBlock()) return

            RotationUtils.setRotations(
                rotation = Rotation(mc.thePlayer.rotationYaw, 90F),
                speed = 36 + Math.random().toFloat(),
                fixType = MovementFixType.NORMAL
            )

            if (RotationUtils.targetRotation?.yaw!! >= 89 && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK && mc.thePlayer.posY == mc.objectMouseOver.blockPos.up().y.toDouble()) {
                if(mc.playerController.onPlayerRightClick(
                    mc.thePlayer, mc.theWorld, mc.thePlayer.inventory.getCurrentItem(),
                    mc.objectMouseOver.blockPos, mc.objectMouseOver.sideHit, mc.objectMouseOver.hitVec
                )){
                    mc.thePlayer.swingItem()
                }
            }
        }
    }
}