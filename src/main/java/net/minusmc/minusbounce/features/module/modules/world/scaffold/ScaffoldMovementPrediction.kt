package net.minusmc.minusbounce.features.module.modules.world.scaffold

import net.minecraft.util.AxisAlignedBB
import net.minecraft.util.Vec3
import net.minusmc.minusbounce.utils.MinecraftInstance
import java.util.ArrayDeque
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object ScaffoldMovementPrediction : MinecraftInstance() {
    private val lastPlacementOffsets = ArrayDeque<Vec3>(4)

    fun reset() {
        lastPlacementOffsets.clear()
    }

    fun onPlace(line: ScaffoldMovementPlanner.MovementLine?, fallOffPosition: Vec3?) {
        if (line == null || fallOffPosition == null) return
        val player = mc.thePlayer ?: return
        val offset = Vec3(player.posX - fallOffPosition.xCoord, player.posY - fallOffPosition.yCoord, player.posZ - fallOffPosition.zCoord)
        val angle = atan2(line.directionZ, line.directionX)
        lastPlacementOffsets.addLast(rotate(offset, angle))
        while (lastPlacementOffsets.size > 4) lastPlacementOffsets.removeFirst()
    }

    fun getPredictedPlacementPos(
        line: ScaffoldMovementPlanner.MovementLine?,
        bootstrapBackoff: Double,
        cutoffDistance: Double,
        warmupPlacements: Int
    ): Vec3? {
        if (line == null) return null
        val player = mc.thePlayer ?: return null
        val fallOffPosition = getFallOffPositionOnLine(line) ?: return null
        val toEdgeX = fallOffPosition.xCoord - player.posX
        val toEdgeZ = fallOffPosition.zCoord - player.posZ
        if (sqrt(toEdgeX * toEdgeX + toEdgeZ * toEdgeZ) <= cutoffDistance) return null

        val distance = sqrt(toEdgeX * toEdgeX + toEdgeZ * toEdgeZ)
        val bootstrap = if (bootstrapBackoff <= 0.0 || distance <= 1.0E-12) {
            fallOffPosition
        } else {
            Vec3(
                fallOffPosition.xCoord - toEdgeX / distance * bootstrapBackoff,
                fallOffPosition.yCoord,
                fallOffPosition.zCoord - toEdgeZ / distance * bootstrapBackoff
            )
        }

        val average = averageOffset() ?: return bootstrap
        val angle = atan2(line.directionZ, line.directionX)
        val rotated = rotate(average, -angle)
        val predicted = Vec3(
            fallOffPosition.xCoord + rotated.xCoord,
            fallOffPosition.yCoord + rotated.yCoord,
            fallOffPosition.zCoord + rotated.zCoord
        )
        val blend = if (warmupPlacements <= 0) 1.0 else (lastPlacementOffsets.size.toDouble() / warmupPlacements).coerceIn(0.0, 1.0)
        return Vec3(
            bootstrap.xCoord + (predicted.xCoord - bootstrap.xCoord) * blend,
            bootstrap.yCoord + (predicted.yCoord - bootstrap.yCoord) * blend,
            bootstrap.zCoord + (predicted.zCoord - bootstrap.zCoord) * blend
        )
    }

    fun getFallOffPositionOnLine(line: ScaffoldMovementPlanner.MovementLine): Vec3? {
        val player = mc.thePlayer ?: return null
        val start = line.nearestPoint(Vec3(player.posX, player.posY, player.posZ))
        if (!isSupported(start)) return start

        var supportedDistance = 0.0
        var unsupportedDistance = 0.1
        while (unsupportedDistance <= 3.0 && isSupported(positionAt(start, line, unsupportedDistance))) {
            supportedDistance = unsupportedDistance
            unsupportedDistance += 0.1
        }
        if (unsupportedDistance > 3.0) return null

        repeat(9) {
            val middle = (supportedDistance + unsupportedDistance) * 0.5
            if (isSupported(positionAt(start, line, middle))) {
                supportedDistance = middle
            } else {
                unsupportedDistance = middle
            }
        }
        return positionAt(start, line, unsupportedDistance)
    }

    private fun positionAt(start: Vec3, line: ScaffoldMovementPlanner.MovementLine, distance: Double) = Vec3(
        start.xCoord + line.directionX * distance,
        start.yCoord,
        start.zCoord + line.directionZ * distance
    )

    private fun isSupported(position: Vec3): Boolean {
        val player = mc.thePlayer ?: return false
        val world = mc.theWorld ?: return false
        val shifted = player.entityBoundingBox.offset(position.xCoord - player.posX, 0.0, position.zCoord - player.posZ)
        val probe = AxisAlignedBB(
            shifted.minX + 0.001,
            shifted.minY - 0.65,
            shifted.minZ + 0.001,
            shifted.maxX - 0.001,
            shifted.minY - 0.001,
            shifted.maxZ - 0.001
        )
        return world.getCollidingBoundingBoxes(player, probe).isNotEmpty()
    }

    private fun averageOffset(): Vec3? {
        if (lastPlacementOffsets.isEmpty()) return null
        var x = 0.0
        var y = 0.0
        var z = 0.0
        lastPlacementOffsets.forEach {
            x += it.xCoord
            y += it.yCoord
            z += it.zCoord
        }
        val size = lastPlacementOffsets.size.toDouble()
        return Vec3(x / size, y / size, z / size)
    }

    private fun rotate(vector: Vec3, angle: Double): Vec3 {
        val cos = cos(angle)
        val sin = sin(angle)
        return Vec3(
            vector.xCoord * cos + vector.zCoord * sin,
            vector.yCoord,
            vector.zCoord * cos - vector.xCoord * sin
        )
    }
}
