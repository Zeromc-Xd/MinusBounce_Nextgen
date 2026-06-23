package net.minusmc.minusbounce.features.module.modules.world.scaffold

import net.minecraft.util.BlockPos
import net.minecraft.util.Vec3
import net.minusmc.minusbounce.utils.MinecraftInstance
import net.minusmc.minusbounce.utils.MovementUtils
import java.util.ArrayDeque
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

object ScaffoldMovementPlanner : MinecraftInstance() {
    data class MovementLine(
        val x: Double,
        val y: Double,
        val z: Double,
        val directionX: Double,
        val directionZ: Double
    ) {
        fun nearestPoint(point: Vec3): Vec3 {
            val dx = point.xCoord - x
            val dz = point.zCoord - z
            val lengthSquared = directionX * directionX + directionZ * directionZ
            if (lengthSquared <= 1.0E-12) return Vec3(x, point.yCoord, z)
            val distance = (dx * directionX + dz * directionZ) / lengthSquared
            return Vec3(x + directionX * distance, point.yCoord, z + directionZ * distance)
        }
    }

    private val lastPlacedBlocks = ArrayDeque<BlockPos>(4)
    private var lastPosition: BlockPos? = null

    fun getOptimalMovementLine(forward: Float, strafe: Float, yaw: Float): MovementLine? {
        if (forward == 0f && strafe == 0f) return null
        val direction = chooseDirection(MovementUtils.getRawDirection(yaw, strafe, forward))
        val blockUnderPlayer = findBlockPlayerStandsOn() ?: return null
        val previousLine = fitLineThroughLastPlacedBlocks()
        val baseX: Double
        val baseZ: Double

        if (previousLine != null && previousLine.directionX * direction.first + previousLine.directionZ * direction.second >= 0.5) {
            baseX = previousLine.x
            baseZ = previousLine.z
        } else {
            baseX = blockUnderPlayer.x + 0.5
            baseZ = blockUnderPlayer.z + 0.5
        }

        return MovementLine(baseX, mc.thePlayer.posY, baseZ, direction.first, direction.second)
    }

    fun trackPlacedBlock(target: BlockPos) {
        if (lastPlacedBlocks.peekLast() == target) return
        while (lastPlacedBlocks.size >= 4) lastPlacedBlocks.removeFirst()
        lastPlacedBlocks.addLast(target)
    }

    fun reset() {
        lastPlacedBlocks.clear()
        lastPosition = null
    }

    private fun chooseDirection(yaw: Float): Pair<Double, Double> {
        val snappedYaw = round(yaw / 45f) * 45f
        val radians = Math.toRadians(snappedYaw.toDouble())
        return -sin(radians) to cos(radians)
    }

    private fun fitLineThroughLastPlacedBlocks(): MovementLine? {
        if (lastPlacedBlocks.size < 2) return null
        val blocks = lastPlacedBlocks.toList()
        val first = blocks[blocks.lastIndex - 1]
        val second = blocks.last()
        val dx = (second.x - first.x).toDouble()
        val dz = (second.z - first.z).toDouble()
        val length = sqrt(dx * dx + dz * dz)
        if (length <= 1.0E-12) return null
        return MovementLine(
            (first.x + second.x) * 0.5 + 0.5,
            mc.thePlayer.posY,
            (first.z + second.z) * 0.5 + 0.5,
            dx / length,
            dz / length
        )
    }

    private fun findBlockPlayerStandsOn(): BlockPos? {
        val player = mc.thePlayer ?: return null
        val world = mc.theWorld ?: return null
        val candidates = linkedSetOf<BlockPos>()
        val offsets = doubleArrayOf(0.301, 0.0, -0.301)

        for (xOffset in offsets) {
            for (zOffset in offsets) {
                val pos = BlockPos(player.posX + xOffset, player.posY - 1.0, player.posZ + zOffset)
                val state = world.getBlockState(pos)
                if (state.block.getCollisionBoundingBox(world, pos, state) != null) candidates.add(pos)
            }
        }

        val lastPlaced = lastPlacedBlocks.peekLast()
        if (lastPlaced != null && lastPlaced in candidates) return lastPlaced
        val stable = lastPosition
        if (stable != null && stable in candidates) return stable
        return candidates.firstOrNull()?.also { lastPosition = it }
    }
}
