/*
 * MinusBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/MinusMC/MinusBounce
 */
package net.minusmc.minusbounce.utils

import com.google.common.base.Predicate
import com.google.common.base.Predicates
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.util.*
import net.minusmc.minusbounce.utils.extensions.eyes
import java.util.*
import kotlin.math.max

object RaycastUtils : MinecraftInstance() {
    /**
     * Modified mouse object pickup
     */

    fun runWithModifiedRaycastResult(range: Float, wallRange: Float, action: (MovingObjectPosition) -> Unit) {
        val rotation = RotationUtils.targetRotation ?: serverRotation
        runWithModifiedRaycastResult(rotation, range.toDouble(), wallRange.toDouble(), action)
    }


    fun runWithModifiedRaycastResult(rotation: Rotation, range: Double, wallRange: Double, action: (MovingObjectPosition) -> Unit) {
        val entity = mc.renderViewEntity ?: return
        if (mc.theWorld == null) return

        val prevPointedEntity = mc.pointedEntity
        val prevObjectMouseOver = mc.objectMouseOver

        try {
            mc.pointedEntity = null

            val buildReach = if (mc.playerController.currentGameType.isCreative) 5.0 else 4.5
            val traceRange = max(max(range, wallRange), buildReach)

            val vec3 = entity.eyes
            val vec31 = RotationUtils.getVectorForRotation(rotation)
            val vec32 = vec3.addVector(vec31.xCoord * traceRange, vec31.yCoord * traceRange, vec31.zCoord * traceRange)

            mc.objectMouseOver = entity.worldObj.rayTraceBlocks(vec3, vec32, false, false, true)
                ?: MovingObjectPosition(MovingObjectPosition.MovingObjectType.MISS, vec32, null, BlockPos(vec32))

            var d1 = traceRange
            var flag = false

            if (mc.playerController.extendedReach()) {
                d1 = 6.0
            } else {
                flag = true
            }

            mc.objectMouseOver?.let {
                d1 = it.hitVec.distanceTo(vec3)
            }

            var pointedEntity: Entity? = null
            var vec33: Vec3? = null

            val list = mc.theWorld.getEntities(EntityLivingBase::class.java) {
                it != null && (it !is EntityPlayer || !it.isSpectator) && it.canBeCollidedWith() && it != entity
            }

            var d2 = d1

            for (entity1 in list) {
                val f1 = entity1.collisionBorderSize
                val box = entity1.entityBoundingBox.expand(f1.toDouble(), f1.toDouble(), f1.toDouble())
                val intercept = box.calculateIntercept(vec3, vec32)

                if (box.isVecInside(vec3)) {
                    if (d2 >= 0) {
                        pointedEntity = entity1
                        vec33 = intercept?.hitVec ?: vec3
                        d2 = 0.0
                    }
                } else if (intercept != null) {
                    val d3 = vec3.distanceTo(intercept.hitVec)

                    if (!RotationUtils.isVisible(intercept.hitVec)) {
                        if (d3 <= wallRange && (d3 < d2 || d2 == 0.0)) {
                            pointedEntity = entity1
                            vec33 = intercept.hitVec
                            d2 = d3
                        }
                        continue
                    }

                    if (d3 < d2 || d2 == 0.0) {
                        if (entity1 === entity.ridingEntity && !entity.canRiderInteract()) {
                            if (d2 == 0.0) {
                                pointedEntity = entity1
                                vec33 = intercept.hitVec
                            }
                        } else {
                            pointedEntity = entity1
                            vec33 = intercept.hitVec
                            d2 = d3
                        }
                    }
                }
            }

            if (pointedEntity != null && vec33 != null && flag && vec3.distanceTo(vec33) > range) {
                pointedEntity = null
                mc.objectMouseOver = MovingObjectPosition(
                    MovingObjectPosition.MovingObjectType.MISS,
                    vec33,
                    null,
                    BlockPos(vec33)
                )
            }

            if (pointedEntity != null && vec33 != null && (d2 < d1 || mc.objectMouseOver == null)) {
                mc.objectMouseOver = MovingObjectPosition(pointedEntity, vec33)
                mc.pointedEntity = pointedEntity
            }

            action(mc.objectMouseOver ?: MovingObjectPosition(MovingObjectPosition.MovingObjectType.MISS, vec32, null, BlockPos(vec32)))
        } finally {
            mc.objectMouseOver = prevObjectMouseOver
            mc.pointedEntity = prevPointedEntity
        }
    }

    fun raycastEntity(range: Double, entityFilter: IEntityFilter): Entity? {
        return raycastEntity(
            range, serverRotation.yaw, serverRotation.pitch,
            entityFilter
        )
    }
    fun raycastEntity(range: Double, yaw: Float, pitch: Float, entityFilter: IEntityFilter): Entity? {
        val renderViewEntity = mc.renderViewEntity
        if (renderViewEntity != null && mc.theWorld != null) {
            var blockReachDistance = range
            val eyePosition = renderViewEntity.getPositionEyes(1f)
            val yawCos = MathHelper.cos(-yaw * 0.017453292f - Math.PI.toFloat())
            val yawSin = MathHelper.sin(-yaw * 0.017453292f - Math.PI.toFloat())
            val pitchCos = -MathHelper.cos(-pitch * 0.017453292f)
            val pitchSin = MathHelper.sin(-pitch * 0.017453292f)
            val entityLook = Vec3((yawSin * pitchCos).toDouble(), pitchSin.toDouble(), (yawCos * pitchCos).toDouble())
            val vector = eyePosition.addVector(
                entityLook.xCoord * blockReachDistance,
                entityLook.yCoord * blockReachDistance,
                entityLook.zCoord * blockReachDistance
            )
            val entityList = mc.theWorld.getEntitiesInAABBexcluding(
                renderViewEntity,
                renderViewEntity.entityBoundingBox.addCoord(
                    entityLook.xCoord * blockReachDistance,
                    entityLook.yCoord * blockReachDistance,
                    entityLook.zCoord * blockReachDistance
                ).expand(1.0, 1.0, 1.0),
                Predicates.and(EntitySelectors.NOT_SPECTATING,
                    Predicate { obj: Entity? -> obj!!.canBeCollidedWith() })
            )
            var pointedEntity: Entity? = null
            for (entity in entityList) {
                if (!entityFilter.canRaycast(entity)) continue
                val collisionBorderSize = entity.collisionBorderSize
                val axisAlignedBB = entity.entityBoundingBox.expand(
                    collisionBorderSize.toDouble(),
                    collisionBorderSize.toDouble(),
                    collisionBorderSize.toDouble()
                )
                val movingObjectPosition = axisAlignedBB.calculateIntercept(eyePosition, vector)
                if (axisAlignedBB.isVecInside(eyePosition)) {
                    if (blockReachDistance >= 0.0) {
                        pointedEntity = entity
                        blockReachDistance = 0.0
                    }
                } else if (movingObjectPosition != null) {
                    val eyeDistance = eyePosition.distanceTo(movingObjectPosition.hitVec)
                    if (eyeDistance < blockReachDistance || blockReachDistance == 0.0) {
                        if (entity === renderViewEntity.ridingEntity && !renderViewEntity.canRiderInteract()) {
                            if (blockReachDistance == 0.0) pointedEntity = entity
                        } else {
                            pointedEntity = entity
                            blockReachDistance = eyeDistance
                        }
                    }
                }
            }
            return pointedEntity
        }
        return null
    }

    interface IEntityFilter {
        fun canRaycast(entity: Entity?): Boolean
    }
}
