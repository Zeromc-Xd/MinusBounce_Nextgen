package net.minusmc.minusbounce.utils.block

import net.minecraft.block.BlockAir
import net.minusmc.minusbounce.utils.extensions.times
import net.minecraft.block.BlockLiquid
import net.minecraft.item.ItemBlock
import net.minecraft.network.play.client.C0APacketAnimation
import net.minecraft.util.AxisAlignedBB
import net.minecraft.util.BlockPos
import net.minecraft.util.EnumFacing
import net.minecraft.util.MovingObjectPosition
import net.minecraft.util.Vec3
import net.minusmc.minusbounce.utils.MinecraftInstance
import net.minusmc.minusbounce.utils.Rotation
import net.minusmc.minusbounce.utils.RotationUtils
import net.minusmc.minusbounce.utils.extensions.eyes
import net.minusmc.minusbounce.utils.extensions.plus
import java.util.ArrayDeque
import kotlin.math.abs

object ScaffoldBlockPlacer : MinecraftInstance() {
    data class PlacementTarget(
        val placeAt: BlockPos,
        val placeOn: BlockPos,
        val facing: EnumFacing,
        val hitVec: Vec3,
        val rotation: Rotation,
        val source: String,
    )

    fun fromRayTrace(
        rayTrace: MovingObjectPosition,
        placeAt: BlockPos,
        placeOn: BlockPos,
        facing: EnumFacing,
        rotation: Rotation,
        source: String,
    ): PlacementTarget? {
        if (rayTrace.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return null
        if (rayTrace.blockPos != placeOn || rayTrace.sideHit != facing) return null
        return PlacementTarget(placeAt, placeOn, facing, rayTrace.hitVec, rotation, source)
    }

    fun findBestWithSupport(
        placeAt: BlockPos,
        currentRotation: Rotation,
        range: Double,
        aimMode: String,
        allowSupport: Boolean,
        supportDepth: Int,
        constructFailResult: Boolean,
    ): PlacementTarget? {
        findBestTarget(placeAt, currentRotation, range, aimMode, constructFailResult)?.let {
            return it
        }

        if (!allowSupport || supportDepth <= 0) {
            return null
        }

        return findSupportTarget(placeAt, currentRotation, range, aimMode, supportDepth, constructFailResult)
    }

    fun findBestTarget(
        placeAt: BlockPos,
        currentRotation: Rotation,
        range: Double,
        aimMode: String,
        constructFailResult: Boolean,
        source: String = "Direct",
    ): PlacementTarget? {
        val world = mc.theWorld ?: return null
        val player = mc.thePlayer ?: return null

        if (!world.worldBorder.contains(placeAt) || !BlockUtils.isReplaceable(placeAt)) {
            return null
        }

        if (player.entityBoundingBox.intersectsWith(fullBox(placeAt))) {
            return null
        }

        val eyes = player.eyes
        var best: PlacementTarget? = null
        var bestScore = Double.MAX_VALUE

        for (offset in EnumFacing.values()) {
            val placeOn = placeAt.offset(offset)
            val facing = offset.opposite
            if (!isPlacementNeighbor(placeOn)) continue

            for (hitVec in hitVectors(placeOn, facing, aimMode)) {
                val distance = eyes.distanceTo(hitVec)
                if (distance > range + 0.05) continue

                val rotation = RotationUtils.toRotation(hitVec, false)
                rotation.fixedSensitivity()

                val traced = rayTrace(rotation, range)
                val target = if (
                    traced != null &&
                    traced.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK &&
                    traced.blockPos == placeOn &&
                    traced.sideHit == facing
                ) {
                    PlacementTarget(placeAt, placeOn, facing, traced.hitVec, rotation, source)
                } else if (constructFailResult && aimMode.equals("none", true)) {
                    PlacementTarget(placeAt, placeOn, facing, hitVec, rotation, "$source-Constructed")
                } else {
                    null
                } ?: continue

                val score = RotationUtils.getRotationDifference(rotation, currentRotation) +
                    distance * 0.05 +
                    faceEdgePenalty(hitVec, placeOn, facing)

                if (score < bestScore) {
                    best = target
                    bestScore = score
                }
            }
        }

        return best
    }

    fun resolveForRotation(
        target: PlacementTarget,
        rotation: Rotation,
        range: Double,
        rayTraceMode: String,
        constructFailResult: Boolean,
    ): PlacementTarget? {
        val traced = rayTrace(rotation, range)

        if (
            traced != null &&
            traced.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK &&
            traced.blockPos == target.placeOn &&
            traced.sideHit == target.facing
        ) {
            return target.copy(hitVec = traced.hitVec, rotation = rotation, source = "${target.source}-Verified")
        }

        if (rayTraceMode.equals("client", true)) {
            val mouseOver = mc.objectMouseOver
            if (
                mouseOver != null &&
                mouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK &&
                mouseOver.blockPos == target.placeOn &&
                mouseOver.sideHit == target.facing
            ) {
                return target.copy(hitVec = mouseOver.hitVec, rotation = rotation, source = "${target.source}-Client")
            }
        }

        if (rayTraceMode.equals("none", true) || constructFailResult) {
            return target.copy(hitVec = faceCenter(target.placeOn, target.facing), rotation = rotation, source = "${target.source}-Constructed")
        }

        return null
    }

    fun place(target: PlacementTarget, swing: Boolean = true): Boolean {
        val player = mc.thePlayer ?: return false
        val world = mc.theWorld ?: return false
        val itemStack = player.inventory.getCurrentItem() ?: return false
        if (itemStack.item !is ItemBlock) return false

        val placed = mc.playerController.onPlayerRightClick(
            player,
            world,
            itemStack,
            target.placeOn,
            target.facing,
            target.hitVec
        )

        if (placed && swing) {
            mc.netHandler.addToSendQueue(C0APacketAnimation())
        }

        if (itemStack.stackSize == 0) {
            player.inventory.mainInventory[player.inventory.currentItem] = null
        }

        return placed
    }

    fun faceCenter(pos: BlockPos, facing: EnumFacing): Vec3 {
        return Vec3(pos).addVector(
            0.5 + facing.directionVec.x * 0.5,
            0.5 + facing.directionVec.y * 0.5,
            0.5 + facing.directionVec.z * 0.5
        )
    }

    private fun findSupportTarget(
        placeAt: BlockPos,
        currentRotation: Rotation,
        range: Double,
        aimMode: String,
        supportDepth: Int,
        constructFailResult: Boolean,
    ): PlacementTarget? {
        val world = mc.theWorld ?: return null
        val player = mc.thePlayer ?: return null
        val queue = ArrayDeque<Pair<BlockPos, Int>>()
        val visited = hashSetOf<BlockPos>()

        queue.add(placeAt to 0)
        visited.add(placeAt)

        while (queue.isNotEmpty()) {
            val (current, depth) = queue.removeFirst()

            if (depth > 0) {
                findBestTarget(
                    current,
                    currentRotation,
                    range,
                    aimMode,
                    constructFailResult,
                    source = "Support"
                )?.let {
                    return it
                }
            }

            if (depth >= supportDepth) continue

            for (side in EnumFacing.values()) {
                val next = current.offset(side)
                if (!visited.add(next)) continue
                if (!world.worldBorder.contains(next)) continue
                if (!BlockUtils.isReplaceable(next)) continue
                if (player.entityBoundingBox.intersectsWith(fullBox(next))) continue
                if (player.eyes.distanceTo(Vec3(next).addVector(0.5, 0.5, 0.5)) > range + supportDepth + 0.5) continue

                queue.add(next to depth + 1)
            }
        }

        return null
    }

    private fun isPlacementNeighbor(pos: BlockPos): Boolean {
        val world = mc.theWorld ?: return false
        val state = world.getBlockState(pos) ?: return false
        val block = state.block ?: return false

        return block !is BlockAir &&
            block !is BlockLiquid &&
            block.canCollideCheck(state, false) &&
            world.worldBorder.contains(pos)
    }

    private fun rayTrace(rotation: Rotation, range: Double): MovingObjectPosition? {
        val player = mc.thePlayer ?: return null
        val world = mc.theWorld ?: return null
        val eyes = player.eyes
        val end = eyes + (RotationUtils.getVectorForRotation(rotation) * range)

        return world.rayTraceBlocks(eyes, end, false, false, true)
    }

    private fun hitVectors(pos: BlockPos, facing: EnumFacing, aimMode: String): List<Vec3> {
        if (aimMode.equals("center", true) || aimMode.equals("none", true)) {
            return listOf(faceCenter(pos, facing))
        }

        val values = if (aimMode.equals("tryrotation", true)) {
            doubleArrayOf(0.35, 0.5, 0.65)
        } else {
            doubleArrayOf(0.15, 0.35, 0.5, 0.65, 0.85)
        }

        val vectors = ArrayList<Vec3>()
        for (a in values) {
            for (b in values) {
                vectors.add(facePoint(pos, facing, a, b))
            }
        }

        return vectors
    }

    private fun facePoint(pos: BlockPos, facing: EnumFacing, a: Double, b: Double): Vec3 {
        val minX = pos.x.toDouble()
        val minY = pos.y.toDouble()
        val minZ = pos.z.toDouble()

        return when (facing) {
            EnumFacing.DOWN -> Vec3(minX + a, minY, minZ + b)
            EnumFacing.UP -> Vec3(minX + a, minY + 1.0, minZ + b)
            EnumFacing.NORTH -> Vec3(minX + a, minY + b, minZ)
            EnumFacing.SOUTH -> Vec3(minX + a, minY + b, minZ + 1.0)
            EnumFacing.WEST -> Vec3(minX, minY + a, minZ + b)
            EnumFacing.EAST -> Vec3(minX + 1.0, minY + a, minZ + b)
        }
    }

    private fun faceEdgePenalty(hitVec: Vec3, pos: BlockPos, facing: EnumFacing): Double {
        val localX = hitVec.xCoord - pos.x
        val localY = hitVec.yCoord - pos.y
        val localZ = hitVec.zCoord - pos.z

        val a = when (facing.axis) {
            EnumFacing.Axis.X -> localZ
            EnumFacing.Axis.Y -> localX
            EnumFacing.Axis.Z -> localX
        }
        val b = when (facing.axis) {
            EnumFacing.Axis.X -> localY
            EnumFacing.Axis.Y -> localZ
            EnumFacing.Axis.Z -> localY
        }

        return (abs(a - 0.5) + abs(b - 0.5)) * 0.1
    }

    private fun fullBox(pos: BlockPos): AxisAlignedBB {
        return AxisAlignedBB(
            pos.x.toDouble(),
            pos.y.toDouble(),
            pos.z.toDouble(),
            pos.x + 1.0,
            pos.y + 1.0,
            pos.z + 1.0
        )
    }
}
