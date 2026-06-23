

package net.minusmc.minusbounce.utils

import com.google.common.base.Predicate
import com.google.common.base.Predicates
import net.minecraft.entity.Entity
import net.minecraft.network.Packet
import net.minecraft.network.play.client.C03PacketPlayer
import net.minecraft.network.play.server.S08PacketPlayerPosLook
import net.minecraft.util.*
import net.minusmc.minusbounce.MinusBounce
import net.minusmc.minusbounce.event.*
import net.minusmc.minusbounce.features.module.modules.combat.BackTrack
import net.minusmc.minusbounce.features.module.modules.movement.Freeze
import net.minusmc.minusbounce.features.module.modules.player.Blink
import net.minusmc.minusbounce.features.special.LagRangePacketManager
import net.minusmc.minusbounce.utils.RaycastUtils.IEntityFilter
import net.minusmc.minusbounce.utils.RaycastUtils.raycastEntity
import net.minusmc.minusbounce.utils.extensions.*
import net.minusmc.minusbounce.utils.movement.MovementFixType
import java.util.*
import kotlin.math.*


object RotationUtils : MinecraftInstance(), Listenable {
    private val random = Random()
    private var keepLength = 0

    @JvmField
    var targetRotation: Rotation? = null

    @JvmField
    var offGroundTicks: Int = 0

    @JvmField
    var onGroundTicks: Int = 0

    var active: Boolean = false
    private var smoothed: Boolean = false
    private var silent: Boolean = false
    private var lastRotations: Rotation? = null
    private var rotations: Rotation? = null
    private var rotationSpeed: Float = 0f
    var type: MovementFixType = MovementFixType.NONE

    enum class RotationResetMode { NONE, CLIENT, SERVER }

    private data class RotationRequest(
        val owner: String,
        val rotation: Rotation,
        val keepLength: Int,
        val speed: Float,
        val fixType: MovementFixType,
        val silent: Boolean,
        val priority: Int,
        val resetMode: RotationResetMode,
        val resetThreshold: Float,
        val ticksUntilReset: Int,
    )

    private var requestTick = -1
    private var selectedRequest: RotationRequest? = null
    private var resettingRotation = false
    private var activeResetThreshold = 2f

    @JvmField
    var activeOwner: String = "None"

    @JvmField
    var previousRotation: Rotation? = null

    @JvmField
    var currentPipelineRotation: Rotation? = null

    @JvmField
    var actualServerRotation: Rotation? = null

    @JvmField
    var theoreticalServerRotation: Rotation? = null

    @JvmField
    var requestPriority: Int = -1

    private var x = random.nextDouble()
    private var y = random.nextDouble()
    private var z = random.nextDouble()

    @JvmStatic
    fun smooth() {
        if (!smoothed) {
            val current = lastRotations ?: targetRotation ?: mc.thePlayer?.rotation ?: return
            val requested = rotations ?: return
            targetRotation = limitAngleChange(current, requested, rotationSpeed.coerceAtLeast(0f))
        }

        smoothed = true
    }

    @EventTarget(priority = -5)
    fun onTick(event: PreUpdateEvent) {
        val player = mc.thePlayer ?: return
        val playerRotation = player.rotation

        if (actualServerRotation == null) {
            actualServerRotation = playerRotation
        }

        if (theoreticalServerRotation == null) {
            theoreticalServerRotation = actualServerRotation
        }

        if (targetRotation == null || lastRotations == null || rotations == null || !active) {
            targetRotation = playerRotation
            lastRotations = playerRotation
            rotations = playerRotation
            currentPipelineRotation = playerRotation
            theoreticalServerRotation = getEffectiveServerRotationValue()
        }

        if (active) {
            smooth()
            currentPipelineRotation = targetRotation
            theoreticalServerRotation = targetRotation
        }

        if (random.nextGaussian() > 0.8) x = Math.random()
        if (random.nextGaussian() > 0.8) y = Math.random()
        if (random.nextGaussian() > 0.8) z = Math.random()
    }

    @EventTarget(priority = -5)
    fun onMotion(event: PreMotionEvent) {

        if (event.onGround) {
            offGroundTicks = 0
            onGroundTicks++
        } else {
            onGroundTicks = 0
            offGroundTicks++
        }

        if (active && targetRotation != null) {
            keepLength--
            previousRotation = lastRotations

            if (this.silent) {
                targetRotation?.let {
                    event.yaw = it.yaw
                    event.pitch = it.pitch
                }
            } else {
                targetRotation!!.toPlayer(mc.thePlayer)
            }

            currentPipelineRotation = targetRotation
            theoreticalServerRotation = targetRotation

            mc.thePlayer.renderYawOffset = targetRotation!!.yaw
            mc.thePlayer.rotationYawHead = targetRotation!!.yaw
            lastRotations = targetRotation
        } else {
            previousRotation = lastRotations
            lastRotations = Rotation(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch)
            currentPipelineRotation = lastRotations
            theoreticalServerRotation = lastRotations
        }

        if (resettingRotation && rotations != null && targetRotation != null &&
            getRotationDifference(targetRotation!!, rotations) <= activeResetThreshold
        ) {
            finishActiveRequest(forceStop = true)
        } else if (keepLength <= 0) {
            finishActiveRequest()
        }

        smoothed = false
    }

    @EventTarget(priority = -5)
    fun onLook(event: LookEvent) {
        if (active && targetRotation != null && lastRotations != null) {
            event.yaw = targetRotation?.yaw ?: return
            event.pitch = targetRotation?.pitch ?: return
            event.lastYaw = lastRotations?.yaw ?: return
            event.lastPitch = lastRotations?.pitch ?: return
        }
    }

    @JvmStatic
    fun getServerRotationValue(): Rotation {
        return getActualServerRotationValue()
    }

    @JvmStatic
    fun getEffectiveServerRotationValue(): Rotation {
        return if (isServerRotationDelayed()) getTheoreticalServerRotationValue() else getActualServerRotationValue()
    }

    @JvmStatic
    fun getActualServerRotationValue(): Rotation {
        val rotation = actualServerRotation ?: mc.thePlayer?.rotation ?: Rotation(0f, 0f)
        actualServerRotation = rotation
        return rotation
    }

    @JvmStatic
    fun getTheoreticalServerRotationValue(): Rotation {
        val rotation = theoreticalServerRotation ?: getActualServerRotationValue()
        theoreticalServerRotation = rotation
        return rotation
    }

    @JvmStatic
    fun trackOutgoingPacket(packet: Packet<*>, sent: Boolean) {
        val rotation = rotationFromClientPacket(packet) ?: return
        theoreticalServerRotation = rotation
        if (sent) {
            actualServerRotation = rotation
        }
    }

    @JvmStatic
    fun trackIncomingPacket(packet: Packet<*>, accepted: Boolean) {
        val player = mc.thePlayer
        if (packet is S08PacketPlayerPosLook) {
            var yaw = packet.yaw
            var pitch = packet.pitch
            if (player != null) {
                val flags = packet.func_179834_f()
                if (flags.contains(S08PacketPlayerPosLook.EnumFlags.Y_ROT)) {
                    yaw += player.rotationYaw
                }
                if (flags.contains(S08PacketPlayerPosLook.EnumFlags.X_ROT)) {
                    pitch += player.rotationPitch
                }
            }
            val rotation = Rotation(yaw, MathHelper.clamp_float(pitch, -90f, 90f))
            theoreticalServerRotation = rotation
            if (accepted) {
                actualServerRotation = rotation
            }
        }
    }

    private fun rotationFromClientPacket(packet: Packet<*>): Rotation? {
        if (packet !is C03PacketPlayer || !packet.rotating) {
            return null
        }
        return Rotation(packet.yaw, MathHelper.clamp_float(packet.pitch, -90f, 90f))
    }

    private fun isServerRotationDelayed(): Boolean {
        if (LagRangePacketManager.queuedPackets() > 0) {
            return true
        }
        if (Blink.state && Blink.blinkedPackets.isNotEmpty()) {
            return true
        }
        val backTrack = MinusBounce.moduleManager[BackTrack::class.java]
        if (backTrack?.state == true && backTrack.packets.isNotEmpty()) {
            return true
        }
        val freeze = MinusBounce.moduleManager[Freeze::class.java]
        return freeze?.state == true
    }


    @JvmOverloads
    fun setRotations(
        rotation: Rotation,
        keepLength: Int = 2,
        speed: Float = 180f,
        fixType: MovementFixType = MovementFixType.FULL,
        silent: Boolean = true,
        priority: Int = defaultPriority(fixType, silent),
    ) {
        requestRotations(
            owner = "Legacy",
            rotation = rotation,
            keepLength = keepLength,
            speed = speed,
            fixType = fixType,
            silent = silent,
            priority = priority
        )
    }

    @JvmOverloads
    fun requestRotations(
        owner: String,
        rotation: Rotation,
        keepLength: Int = 2,
        speed: Float = 180f,
        fixType: MovementFixType = MovementFixType.FULL,
        silent: Boolean = true,
        priority: Int = defaultPriority(fixType, silent),
        resetMode: RotationResetMode = RotationResetMode.SERVER,
        resetThreshold: Float = 2f,
        ticksUntilReset: Int = 5,
    ) {
        rotation.isNan() ?: return
        resettingRotation = false
        val request = selectRequest(
            RotationRequest(
                owner,
                normalizeRotation(rotation),
                keepLength.coerceAtLeast(1),
                max(speed, 0f),
                if (silent) fixType else MovementFixType.NONE,
                silent,
                priority,
                resetMode,
                resetThreshold.coerceIn(0f, 180f),
                ticksUntilReset.coerceAtLeast(1),
            )
        )
        this.type = request.fixType
        this.rotationSpeed = request.speed
        this.rotations = request.rotation
        this.keepLength = request.keepLength
        this.silent = request.silent
        this.activeOwner = request.owner
        this.requestPriority = request.priority
        this.activeResetThreshold = request.resetThreshold
        active = true

        smooth()
    }

    private fun defaultPriority(fixType: MovementFixType, silent: Boolean): Int {
        return (when (fixType) {
            MovementFixType.FULL -> 30
            MovementFixType.NORMAL -> 20
            MovementFixType.NONE -> 10
        }) + if (silent) 1 else 0
    }

    private fun selectRequest(request: RotationRequest): RotationRequest {
        val tick = mc.thePlayer?.ticksExisted ?: 0
        if (requestTick != tick) {
            requestTick = tick
            selectedRequest = null
        }

        val selected = selectedRequest
        val current = lastRotations ?: targetRotation ?: mc.thePlayer?.rotation
        if (selected == null
            || request.priority > selected.priority
            || request.priority == selected.priority && current != null &&
            getRotationDifference(request.rotation, current) < getRotationDifference(selected.rotation, current)
        ) {
            selectedRequest = request
            return request
        }

        return selected
    }

    fun resetRotationOwner(owner: String) {
        if (activeOwner.equals(owner, true)) {
            finishActiveRequest()
        }
    }

    fun isOwner(owner: String): Boolean {
        return active && activeOwner.equals(owner, true)
    }

    fun getActiveRotation(): Rotation {
        return targetRotation ?: getServerRotationValue()
    }

    private fun finishActiveRequest() {
        finishActiveRequest(false)
    }

    private fun finishActiveRequest(forceStop: Boolean) {
        val playerRotation = mc.thePlayer?.rotation ?: return
        val selected = selectedRequest

        if (!forceStop && !resettingRotation && selected != null && selected.resetMode != RotationResetMode.NONE) {
            rotations = when (selected.resetMode) {
                RotationResetMode.SERVER -> getServerRotationValue()
                RotationResetMode.CLIENT -> playerRotation
                else -> playerRotation
            }
            keepLength = selected.ticksUntilReset
            activeResetThreshold = selected.resetThreshold
            resettingRotation = true
            activeOwner = "${selected.owner}-Reset"
            selectedRequest = null
            return
        }

        rotations = playerRotation
        keepLength = 0
        active = false
        activeOwner = "None"
        requestPriority = -1
        selectedRequest = null
        resettingRotation = false
    }

    private fun normalizeRotation(rotation: Rotation): Rotation {
        val base = lastRotations ?: targetRotation ?: mc.thePlayer.rotation
        val normalized = Rotation(
            base.yaw + MathHelper.wrapAngleTo180_float(rotation.yaw - base.yaw),
            MathHelper.clamp_float(rotation.pitch, -90f, 90f),
        )
        normalized.fixedSensitivity(r = base)
        return normalized
    }


    override fun handleEvents() = true

    fun rayTrace(range: Double, rotations: Rotation): Entity? {
        if (range == 3.0) {
            return mc.objectMouseOver.entityHit
        } else {
            val vec3 = mc.thePlayer.getPositionEyes(1.0f)
            val vec31 = getVectorForRotation(rotations)
            val vec32 = vec3.addVector(vec31.xCoord * range, vec31.yCoord * range, vec31.zCoord * range)
            var pointedEntity: Entity? = null
            val f = 1.0f
            val list: List<*> = mc.theWorld.getEntitiesInAABBexcluding(
                mc.renderViewEntity,
                mc.renderViewEntity.entityBoundingBox.addCoord(
                    vec31.xCoord * range, vec31.yCoord * range, vec31.zCoord * range
                ).expand(f.toDouble(), f.toDouble(), f.toDouble()),
                Predicates.and(EntitySelectors.NOT_SPECTATING, Predicate { obj: Entity? -> obj!!.canBeCollidedWith() })
            )
            var d2 = range
            val var11 = list.iterator()

            while (true) {
                while (var11.hasNext()) {
                    val o = var11.next()!!
                    val entity1 = o as Entity
                    val f1 = entity1.collisionBorderSize
                    val axisalignedbb = entity1.entityBoundingBox.expand(f1.toDouble(), f1.toDouble(), f1.toDouble())
                    val movingobjectposition = axisalignedbb.calculateIntercept(vec3, vec32)
                    if (axisalignedbb.isVecInside(vec3)) {
                        if (d2 >= 0.0) {
                            pointedEntity = entity1
                            d2 = 0.0
                        }
                    } else if (movingobjectposition != null) {
                        val d3 = vec3.distanceTo(movingobjectposition.hitVec)
                        if (d3 < d2 || d2 == 0.0) {
                            if (entity1 === mc.renderViewEntity.ridingEntity) {
                                if (d2 == 0.0) {
                                    pointedEntity = entity1
                                }
                            } else {
                                pointedEntity = entity1
                                d2 = d3
                            }
                        }
                    }
                }

                return pointedEntity
            }
        }
    }


    fun faceBlock(blockPos: BlockPos?): VecRotation? {
        if (blockPos == null) return null
        var vecRotation: VecRotation? = null

        for (x in 0.1..0.9) {
            for (y in 0.1..0.9) {
                for (z in 0.1..0.9) {
                    val eyesPos = Vec3(
                        mc.thePlayer.posX,
                        mc.thePlayer.entityBoundingBox.minY + mc.thePlayer.getEyeHeight(),
                        mc.thePlayer.posZ
                    )
                    val posVec = Vec3(blockPos).addVector(x, y, z)
                    val dist = eyesPos.distanceTo(posVec)
                    val diffX = posVec.xCoord - eyesPos.xCoord
                    val diffY = posVec.yCoord - eyesPos.yCoord
                    val diffZ = posVec.zCoord - eyesPos.zCoord
                    val diffXZ = MathHelper.sqrt_double(diffX * diffX + diffZ * diffZ).toDouble()
                    val rotation = Rotation(
                        MathHelper.wrapAngleTo180_float(Math.toDegrees(atan2(diffZ, diffX)).toFloat() - 90f),
                        MathHelper.wrapAngleTo180_float(-Math.toDegrees(atan2(diffY, diffXZ)).toFloat())
                    )
                    val rotationVector = getVectorForRotation(rotation)
                    val vector = eyesPos.addVector(
                        rotationVector.xCoord * dist, rotationVector.yCoord * dist, rotationVector.zCoord * dist
                    )
                    val obj = mc.theWorld.rayTraceBlocks(
                        eyesPos, vector, false, false, true
                    )
                    if (obj.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                        val currentVec = VecRotation(posVec, rotation)
                        if (vecRotation == null || getRotationDifference(currentVec.rotation) < getRotationDifference(
                                vecRotation.rotation
                            )
                        ) vecRotation = currentVec
                    }
                }
            }
        }

        return vecRotation
    }


    fun performRaytrace(
        blockPos: BlockPos,
        rotation: Rotation,
        reach: Float = mc.playerController.blockReachDistance,
    ): MovingObjectPosition? {
        val world = mc.theWorld ?: return null
        val player = mc.thePlayer ?: return null

        val eyes = player.eyes

        return blockPos.getBlock()?.collisionRayTrace(
            world, blockPos, eyes, eyes + (getVectorForRotation(rotation) * reach.toDouble())
        )
    }

    fun performRayTrace(blockPos: BlockPos, vec: Vec3, eyes: Vec3 = mc.thePlayer.eyes) =
        mc.theWorld?.let { blockPos.getBlock()?.collisionRayTrace(it, blockPos, eyes, vec) }


    fun faceBow(target: Entity, silent: Boolean) {
        var velocity = (72000 - mc.thePlayer.getItemInUseCount()) / 20.0f
        velocity = (velocity * velocity + velocity * 2.0f) / 3.0f
        if (velocity > 1.0f) {
            velocity = 1.0f
        }

        val d = mc.thePlayer.getDistanceToEntity(target) / 2.5
        val posX = target.posX + (target.posX - target.prevPosX) * d - mc.thePlayer.posX
        val posY =
            target.posY + (target.posY - target.prevPosY) * 1.0 + target.height * 0.5 - mc.thePlayer.posY - mc.thePlayer.getEyeHeight()
        val posZ = target.posZ + (target.posZ - target.prevPosZ) * d - mc.thePlayer.posZ

        val yaw = Math.toDegrees(atan2(posZ, posX)).toFloat() - 90.0f

        val hDistance = sqrt(posX * posX + posZ * posZ)
        val hDistanceSq = hDistance * hDistance
        val g = 0.006f

        val velocitySq = velocity * velocity
        val velocityPow4 = velocitySq * velocitySq

        val neededPitch = -Math.toDegrees(
            atan(
                (velocitySq - sqrt(
                    velocityPow4 - g * (g * hDistanceSq + 2.0 * posY * velocitySq)
                )) / (g * hDistance)
            )
        ).toFloat()

        setRotations(
            if (java.lang.Float.isNaN(neededPitch)) getRotations(target, 0.0, 0.0, 0.0) else Rotation(
                yaw, neededPitch
            ), silent = silent
        )
    }

    fun getRotations(ent: Entity, offsetX: Double, offsetY: Double, offsetZ: Double): Rotation {
        val eyeHeight = ent.eyeHeight.toDouble()
        var y = ent.posY
        val playerY = mc.thePlayer.posY + mc.thePlayer.getEyeHeight().toDouble()
        if (playerY >= y + eyeHeight) {
            y += eyeHeight
            y -= 0.4
        } else if (!(playerY < y)) {
            y = playerY - 0.4
        }

        var best: Vec3 = getBestHitVec(ent)
        var nearest = 15.0
        val boundingBox = ent.entityBoundingBox

        for (x1 in boundingBox.minX..boundingBox.maxX step 0.07) {
            for (z1 in boundingBox.minZ..boundingBox.maxZ step 0.07) {
                for (y1 in boundingBox.minY..boundingBox.maxY step 0.07) {
                    val pos = Vec3(x1, y1, z1)
                    if (mc.theWorld.rayTraceBlocks(mc.thePlayer.getPositionEyes(1.0F), pos) == null) {
                        val eyes = mc.thePlayer.getPositionEyes(1.0f)
                        val dist =
                            sqrt((x1 - eyes.xCoord).pow(2.0) + (y1 - eyes.yCoord).pow(2.0) + (z1 - eyes.zCoord).pow(2.0))
                        if (dist <= nearest) {
                            nearest = dist
                            best = pos
                        }
                    }
                }
            }
        }

        return getRotationFromPosition(best.xCoord + offsetX, y - offsetY, best.zCoord + offsetZ)
    }

    fun getRotationFromPosition(x: Double, y: Double, z: Double): Rotation {
        val xDiff = x - mc.thePlayer.posX
        val zDiff = z - mc.thePlayer.posZ
        val yDiff = y - mc.thePlayer.posY - 1.2
        val dist = MathHelper.sqrt_double(xDiff * xDiff + zDiff * zDiff).toDouble()
        val yaw = (atan2(zDiff, xDiff) * 180.0 / 3.141592653589793).toFloat() - 90.0f
        val pitch = (-(atan2(yDiff, dist) * 180.0 / 3.141592653589793)).toFloat()
        return Rotation(yaw, pitch)
    }

    fun getBestHitVec(entity: Entity): Vec3 {
        val positionEyes = mc.thePlayer.getPositionEyes(1.0f)
        val f11 = entity.collisionBorderSize
        val entityBoundingBox = entity.entityBoundingBox.expand(f11.toDouble(), f11.toDouble(), f11.toDouble())
        val ex = MathHelper.clamp_double(positionEyes.xCoord, entityBoundingBox.minX, entityBoundingBox.maxX)
        val ey = MathHelper.clamp_double(positionEyes.yCoord, entityBoundingBox.minY, entityBoundingBox.maxY)
        val ez = MathHelper.clamp_double(positionEyes.zCoord, entityBoundingBox.minZ, entityBoundingBox.maxZ)
        return Vec3(ex, ey - 0.4, ez)
    }


    @JvmOverloads
    fun toRotation(vec: Vec3, predict: Boolean = false, diff: Vec3? = null): Rotation {
        val eyesPos =
            Vec3(mc.thePlayer.posX, mc.thePlayer.entityBoundingBox.minY + mc.thePlayer.eyeHeight, mc.thePlayer.posZ)
        if (predict) eyesPos.addVector(mc.thePlayer.motionX, mc.thePlayer.motionY, mc.thePlayer.motionZ)

        val (diffX, diffY, diffZ) = diff ?: (vec - eyesPos)

        return Rotation(
            MathHelper.wrapAngleTo180_float(Math.toDegrees(atan2(diffZ, diffX)).toFloat() - 90f),
            MathHelper.wrapAngleTo180_float(
                (-Math.toDegrees(
                    atan2(
                        diffY, sqrt(diffX * diffX + diffZ * diffZ)
                    )
                )).toFloat()
            )
        )
    }


    fun getCenter(bb: AxisAlignedBB): Vec3 {
        return Vec3(
            bb.minX + (bb.maxX - bb.minX) * 0.5,
            bb.minY + (bb.maxY - bb.minY) * 0.5,
            bb.minZ + (bb.maxZ - bb.minZ) * 0.5
        )
    }


    fun searchCenter(
        bb: AxisAlignedBB,
        outborder: Boolean,
        random: Boolean,
        predict: Boolean,
        throughWalls: Boolean,
    ): VecRotation? {
        if (outborder) {
            val vec3 = Vec3(
                bb.minX + (bb.maxX - bb.minX) * (x * 0.3 + 1.0),
                bb.minY + (bb.maxY - bb.minY) * (y * 0.3 + 1.0),
                bb.minZ + (bb.maxZ - bb.minZ) * (z * 0.3 + 1.0)
            )
            return VecRotation(vec3, toRotation(vec3, predict))
        }

        val randomVec = Vec3(
            bb.minX + (bb.maxX - bb.minX) * (x * 0.8 + 0.2),
            bb.minY + (bb.maxY - bb.minY) * (y * 0.8 + 0.2),
            bb.minZ + (bb.maxZ - bb.minZ) * (z * 0.8 + 0.2)
        )
        val randomRotation = toRotation(randomVec, predict)

        var vecRotation: VecRotation? = null

        for (x in 0.0..1.0){
            for (y in 0.0..1.0){
                for (z in 0.0..1.0){
                    val vec3 = Vec3(
                        bb.minX + (bb.maxX - bb.minX) * x,
                        bb.minY + (bb.maxY - bb.minY) * y,
                        bb.minZ + (bb.maxZ - bb.minZ) * z
                    )
                    val rotation = toRotation(vec3, predict)

                    if (throughWalls || isVisible(vec3)) {
                        val currentVec = VecRotation(vec3, rotation)

                        if (vecRotation == null || (if (random) getRotationDifference(
                                currentVec.rotation,
                                randomRotation
                            ) < getRotationDifference(
                                vecRotation.rotation, randomRotation
                            ) else getRotationDifference(currentVec.rotation) < getRotationDifference(
                                vecRotation.rotation
                            ))
                        ) vecRotation = currentVec
                    }
                }
            }
        }

        return vecRotation
    }


    fun getRotationDifference(entity: Entity): Double {
        val rotation = toRotation(getCenter(entity.entityBoundingBox), true)
        return getRotationDifference(rotation, Rotation(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch))
    }


    fun getRotationDifference(rotation: Rotation) = getRotationDifference(rotation, getServerRotationValue())


    fun getRotationDifference(a: Rotation, b: Rotation?): Double {
        return hypot(getAngleDifference(a.yaw, b!!.yaw).toDouble(), (a.pitch - b.pitch).toDouble())
    }


    fun limitAngleChange(currentRotation: Rotation, targetRotation: Rotation, turnSpeed: Float): Rotation {
        val speed = turnSpeed.coerceAtLeast(0f)
        var yaw = currentRotation.yaw + getAngleDifference(targetRotation.yaw, currentRotation.yaw)
        var pitch = targetRotation.pitch.coerceIn(-90f, 90f)

        if (speed > 0.0F) {
            val yDiff = getAngleDifference(targetRotation.yaw, currentRotation.yaw)
            val pDiff = getAngleDifference(targetRotation.pitch, currentRotation.pitch)
            val yAdd = yDiff.coerceIn(-speed, speed)
            val pAdd = pDiff.coerceIn(-speed, speed)

            yaw = currentRotation.yaw + yAdd
            pitch = (currentRotation.pitch + pAdd).coerceIn(-90f, 90f)
        }

        return Rotation(yaw, pitch).apply { fixedSensitivity(r = currentRotation) }
    }


    fun getAngleDifference(a: Float, b: Float): Float {
        return ((a - b) % 360f + 540f) % 360f - 180f
    }


    @JvmStatic
    fun getVectorForRotation(rotation: Rotation): Vec3 {
        val rotX = rotation.yaw * Math.PI / 180f
        val rotY = rotation.pitch * Math.PI / 180f

        return Vec3(-cos(rotY) * sin(rotX), -sin(rotY), cos(rotY) * cos(rotX))
    }


    fun isFaced(targetEntity: Entity, blockReachDistance: Double): Boolean {
        return raycastEntity(blockReachDistance, object : IEntityFilter {
            override fun canRaycast(entity: Entity?): Boolean {
                return entity === targetEntity
            }
        }) != null
    }


    fun isVisible(vec3: Vec3?): Boolean {
        val eyesPos = Vec3(
            mc.thePlayer.posX, mc.thePlayer.entityBoundingBox.minY + mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ
        )
        return mc.theWorld.rayTraceBlocks(eyesPos, vec3) == null
    }
}
