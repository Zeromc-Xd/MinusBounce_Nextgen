/*
 * MinusBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/MinusMC/MinusBounce
 */
package net.minusmc.minusbounce.utils.block

import net.minecraft.util.BlockPos
import net.minecraft.util.EnumFacing
import net.minecraft.util.Vec3
import net.minecraft.block.BlockAir
import net.minusmc.minusbounce.utils.block.BlockUtils.air

class PlaceInfo(val blockPos: BlockPos, val enumFacing: EnumFacing,
                var vec3: Vec3 = Vec3(blockPos.x + 0.5, blockPos.y + 0.5, blockPos.z + 0.5)) {

    companion object {

        /**
         * Allows you to find a specific place info for your [blockPos]
         */
        fun get(blockPos: BlockPos): PlaceInfo? =
            when {
                BlockUtils.isClickable(blockPos.add(1, 0, 0)) -> PlaceInfo(blockPos.add(1, 0, 0), EnumFacing.WEST)
                BlockUtils.isClickable(blockPos.add(-1, 0, 0)) -> PlaceInfo(blockPos.add(-1, 0, 0), EnumFacing.EAST)
                BlockUtils.isClickable(blockPos.add(0, -1, 0)) -> PlaceInfo(blockPos.add(0, -1, 0), EnumFacing.UP)
                BlockUtils.isClickable(blockPos.add(0, 0, -1)) -> PlaceInfo(blockPos.add(0, 0, -1), EnumFacing.SOUTH)
                BlockUtils.isClickable(blockPos.add(0, 0, 1)) -> PlaceInfo(blockPos.add(0, 0, 1), EnumFacing.NORTH)
                else -> null
            }

        fun get(pos: Vec3): PlaceInfo? =
            when {
                !air(
                    BlockPos(pos).add(1, 0, 0)
                ) -> PlaceInfo(BlockPos(pos).add(1, 0, 0), EnumFacing.WEST)
                !air(
                    BlockPos(pos).add(-1, 0, 0)
                ) -> PlaceInfo(BlockPos(pos).add(-1, 0, 0), EnumFacing.EAST)
                !air(
                    BlockPos(pos).add(0, -1, 0)
                ) -> PlaceInfo(BlockPos(pos).add(0, -1, 0), EnumFacing.UP)
                !air(
                    BlockPos(pos).add(0, 0, -1)
                ) -> PlaceInfo(BlockPos(pos).add(0, 0, -1), EnumFacing.SOUTH)
                !air(
                    BlockPos(pos).add(0, 0, 1)
                ) -> PlaceInfo(BlockPos(pos).add(0, 0, 1), EnumFacing.NORTH)
                else -> null
            }
    }
}
