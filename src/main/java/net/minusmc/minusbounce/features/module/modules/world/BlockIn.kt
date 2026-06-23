/*
 * MinusBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/MinusMC/MinusBounce
 */
package net.minusmc.minusbounce.features.module.modules.world

import net.minecraft.item.ItemBlock
import net.minecraft.util.BlockPos
import net.minecraft.util.EnumFacing
import net.minecraft.util.MovingObjectPosition
import net.minusmc.minusbounce.event.EventTarget
import net.minusmc.minusbounce.event.PreUpdateEvent
import net.minusmc.minusbounce.features.module.Module
import net.minusmc.minusbounce.features.module.ModuleCategory
import net.minusmc.minusbounce.features.module.ModuleInfo
import net.minusmc.minusbounce.utils.ClientUtils
import net.minusmc.minusbounce.utils.InventoryUtils
import net.minusmc.minusbounce.utils.Rotation
import net.minusmc.minusbounce.utils.RotationUtils
import net.minusmc.minusbounce.utils.block.BlockUtils
import net.minusmc.minusbounce.utils.extensions.rotation
import net.minusmc.minusbounce.utils.misc.RandomUtils
import net.minusmc.minusbounce.utils.movement.MovementFixType
import net.minusmc.minusbounce.value.FloatRangeValue
import net.minusmc.minusbounce.value.IntegerValue


@ModuleInfo(name = "BlockIn", description = "Make You BlockIn.", category = ModuleCategory.WORLD)
class BlockIn : Module() {
    private val speed = FloatRangeValue("Speed", 90f, 90f, 0f, 180f)
    private val placeDelay = IntegerValue("Delay", 50, 0, 500)
    private var lastPlace: Long = 0
    
    override fun onDisable() {
        lastPlace = 0
    }

    override fun onEnable() {
        lastPlace = 0
    }

    private fun fail() {
        ClientUtils.displayChatMessage("No blocks found.")
        state = false
        return
    }

    @EventTarget
    fun onPreUpdate(event: PreUpdateEvent) {
        val blockSlot = InventoryUtils.findBlockInHotbar() ?: return
        if (blockSlot != -1) {
            mc.thePlayer.inventory.currentItem = blockSlot - 36
        }

        mc.thePlayer.heldItem ?: fail()
        if (mc.thePlayer.heldItem.item !is ItemBlock) fail()

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastPlace < placeDelay.get()) return

        var placed = 0
        for (blockPos in BlockUtils.getSurroundBlocks(mc.thePlayer)) {
            if (currentTime - lastPlace < placeDelay.get()) return
            if (!BlockUtils.isReplaceable(blockPos)) continue
            val info = BlockUtils.searchBlock(blockPos, true) ?: continue

            if (!isObjectMouseOverBlock(info.placeInfo.enumFacing, info.placeInfo.blockPos)){
                RotationUtils.setRotations(info.rotation, speed = RandomUtils.nextFloat(speed.getMinValue(), speed.getMaxValue()), fixType = MovementFixType.NORMAL)
                return
            } else if (mc.playerController.onPlayerRightClick(
                    mc.thePlayer, mc.theWorld,
                    mc.thePlayer.inventory.getCurrentItem(),
                    mc.objectMouseOver.blockPos,
                    mc.objectMouseOver.sideHit,
                    mc.objectMouseOver.hitVec
                )
            ) {
                mc.thePlayer.swingItem()
                mc.itemRenderer.resetEquippedProgress()

                lastPlace = currentTime
                placed++
            }
        }
        if (placed == 0) state = false
    }

    @JvmOverloads
    fun isObjectMouseOverBlock(
        facing: EnumFacing,
        block: BlockPos,
        rotation: Rotation? = RotationUtils.targetRotation ?: mc.thePlayer.rotation,
        obj: MovingObjectPosition? = BlockUtils.rayTrace(rotation) ?: mc.thePlayer.rayTrace(5.0, 1.0F),
    ): Boolean{
        if (obj != null) {
            return obj.sideHit == facing && obj.blockPos == block
        }

        return false
    }
}