/*
 * MinusBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/MinusMC/MinusBounce
 */
package net.minusmc.minusbounce.utils

import net.minecraft.potion.Potion
import net.minusmc.minusbounce.event.EventTarget
import net.minusmc.minusbounce.event.Listenable
import net.minusmc.minusbounce.event.MoveEvent
import net.minusmc.minusbounce.event.PreUpdateEvent
import kotlin.math.*

object MovementUtils : MinecraftInstance(), Listenable {
    private var lastX = -999999.0
    private var lastZ = -999999.0

    @EventTarget
    fun onUpdate(event: PreUpdateEvent) {
        if(mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, mc.thePlayer.entityBoundingBox.offset(0.0, -1.0, 0.0)).isEmpty()){
            AABBOffGroundticks++
        } else {
            AABBOffGroundticks = 0
        }
    }

    fun strafe(speed: Float, yaw: Float) {
        mc.thePlayer.motionX = -sin(yaw.toDouble()) * speed.toDouble()
        mc.thePlayer.motionZ = cos(yaw.toDouble()) * speed.toDouble()
    }

    val speed: Float
        get() = getSpeed(mc.thePlayer.motionX, mc.thePlayer.motionZ).toFloat()

    val isMoving: Boolean
        get() = (
            mc.gameSettings.keyBindForward.isKeyDown
            || mc.gameSettings.keyBindBack.isKeyDown
            || mc.gameSettings.keyBindRight.isKeyDown
            || mc.gameSettings.keyBindLeft.isKeyDown
        )

    private fun getSpeed(motionX: Double, motionZ: Double): Double {
        return sqrt(motionX * motionX + motionZ * motionZ)
    }

    fun strafe() {
        strafe(speed)
    }

    fun strafe(speed: Float) {
        if (!isMoving) return
        val yaw = getDirection()
        mc.thePlayer.motionX = -sin(yaw) * speed
        mc.thePlayer.motionZ = cos(yaw) * speed
    }

    fun useDiagonalSpeed() {
        val gameSettings = arrayOf(
            mc.gameSettings.keyBindForward,
            mc.gameSettings.keyBindRight,
            mc.gameSettings.keyBindBack,
            mc.gameSettings.keyBindLeft
        )

        var down = 0

        gameSettings.forEach { keyBinding ->
            down += if (keyBinding.isKeyDown) 1 else 0
        }

        val active = down == 1

        if (!active) return

        val groundIncrease = (0.1299999676734952 - 0.12739998266255503) + 1E-7 - 1E-8
        val airIncrease = (0.025999999334873708 - 0.025479999685988748) - 1E-8
        val increase = if (mc.thePlayer.onGround) groundIncrease else airIncrease

        moveFlying(increase)
    }

    fun moveFlying(increase: Double) {
        if (!isMoving) return
        val yaw = direction()
        mc.thePlayer.motionX += -sin(yaw.toFloat()) * increase
        mc.thePlayer.motionZ += cos(yaw.toFloat()) * increase
    }

    /**
     * Gets the players' movement yaw
     */
    fun direction(): Double {
        var rotationYaw = if (RotationUtils.active && RotationUtils.type.correctYaw) {
            RotationUtils.targetRotation?.yaw ?: mc.thePlayer.rotationYaw
        } else {
            mc.thePlayer.rotationYaw
        }

        if (mc.thePlayer.moveForward < 0) {
            rotationYaw += 180f
        }

        var forward = 1f

        if (mc.thePlayer.moveForward < 0) {
            forward = -0.5f
        } else if (mc.thePlayer.moveForward > 0) {
            forward = 0.5f
        }

        if (mc.thePlayer.moveStrafing > 0) {
            rotationYaw -= 70 * forward
        }

        if (mc.thePlayer.moveStrafing < 0) {
            rotationYaw += 70 * forward
        }

        return Math.toRadians(rotationYaw.toDouble())
    }

    fun strafe(speed: Float, yaw: Float, forward: Float, strafe: Float) {
        if (!isMoving) return
        val yaw = getDirectionRotation(yaw, strafe, forward)
        mc.thePlayer.motionX = -sin(yaw) * speed
        mc.thePlayer.motionZ = cos(yaw) * speed
    }

    fun forward(length: Double) {
        val yaw = MathUtils.toRadians(mc.thePlayer.rotationYaw)
        mc.thePlayer.setPosition(
            mc.thePlayer.posX + -sin(yaw) * length, mc.thePlayer.posY,
            mc.thePlayer.posZ + cos(yaw) * length
        )
    }

    fun getDirection() = getRawDirection()

    fun getRawDirection(): Double {
        return getDirectionRotation(mc.thePlayer.rotationYaw, mc.thePlayer.moveStrafing, mc.thePlayer.moveForward)
    }

    fun getRawDirection(yaw: Float, strafe: Float, forward: Float): Float {
        var rotationYaw = yaw
        if (forward < 0f) rotationYaw += 180f
        var f = 1f
        if (forward < 0f) f = -0.5f
        else if (forward > 0f) f = 0.5f
        if (strafe > 0f) rotationYaw -= 90f * f
        if (strafe < 0f) rotationYaw += 90f * f
        return rotationYaw
    }

    fun getDirectionRotation(yaw: Float, strafe: Float, forward: Float): Double {
        return MathUtils.toRadians(getRawDirection(yaw, strafe, forward)).toDouble()
    }

    fun getPlayerDirection(): Float {
        var direction = mc.thePlayer.rotationYaw

        if (mc.thePlayer.moveForward > 0) {
            if (mc.thePlayer.moveStrafing > 0) {
                direction -= 45f
            } else if (mc.thePlayer.moveStrafing < 0) {
                direction += 45f
            }
        } else if (mc.thePlayer.moveForward < 0) {
            if (mc.thePlayer.moveStrafing > 0) {
                direction -= 135f
            } else if (mc.thePlayer.moveStrafing < 0) {
                direction += 135f
            } else {
                direction -= 180f
            }
        } else {
            if (mc.thePlayer.moveStrafing > 0) {
                direction -= 90f
            } else if (mc.thePlayer.moveStrafing < 0) {
                direction += 90f
            }
        }

        return direction
    }

    fun getXZDist(speed: Float, cYaw: Float): DoubleArray {
        val arr = DoubleArray(2)
        val yaw = getDirectionRotation(cYaw, mc.thePlayer.moveStrafing, mc.thePlayer.moveForward)
        arr[0] = -sin(yaw) * speed
        arr[1] = cos(yaw) * speed
        return arr
    }

    fun getPredictionYaw(x: Double, z: Double): Float {
        if (mc.thePlayer == null) {
            lastX = -999999.0
            lastZ = -999999.0
            return 0f
        }
        if (lastX == -999999.0) lastX = mc.thePlayer.prevPosX
        if (lastZ == -999999.0) lastZ = mc.thePlayer.prevPosZ
        val returnValue = (atan2(z - lastZ, x - lastX) * 180f / Math.PI).toFloat()
        lastX = x
        lastZ = z
        return returnValue
    }

    val jumpEffect: Int
        get() = if (mc.thePlayer.isPotionActive(Potion.jump)) mc.thePlayer.getActivePotionEffect(Potion.jump).amplifier + 1 else 0
    
    val speedEffect: Int
        get() = if (mc.thePlayer.isPotionActive(Potion.moveSpeed)) mc.thePlayer.getActivePotionEffect(Potion.moveSpeed).amplifier + 1 else 0

    fun getBaseMoveSpeed(): Double {
        return getBaseMoveSpeed(0.2873)
    }

    fun getBaseMoveSpeed(customSpeed: Double): Double {
        var baseSpeed = if (PlayerUtils.isOnIce) 0.258977700006 else customSpeed
        if (mc.thePlayer.isPotionActive(Potion.moveSpeed)) {
            val amplifier = mc.thePlayer.getActivePotionEffect(Potion.moveSpeed).amplifier
            baseSpeed *= 1.0 + 0.2 * (amplifier + 1)
        }
        return baseSpeed
    }

    fun getJumpBoostModifier(baseJumpHeight: Float): Double {
        return getJumpBoostModifier(baseJumpHeight, true)
    }

    fun getJumpBoostModifier(baseJumpHeight: Float, potionJump: Boolean): Double {
        var baseJumpHeight = baseJumpHeight
        if (mc.thePlayer.isPotionActive(Potion.jump) && potionJump) {
            val amplifier = mc.thePlayer.getActivePotionEffect(Potion.jump).amplifier
            baseJumpHeight += (amplifier + 1) * 0.1f
        }
        return baseJumpHeight.toDouble()
    }

    fun setMotion(event: MoveEvent, speed: Double, motion: Double, smoothStrafe: Boolean) {
        var forward = mc.thePlayer.movementInput.moveForward.toDouble()
        var strafe = mc.thePlayer.movementInput.moveStrafe.toDouble()
        var yaw = mc.thePlayer.rotationYaw.toDouble()
        val direction = if (smoothStrafe) 45 else 90
        if (forward == 0.0 && strafe == 0.0) {
            event.x = 0.0
            event.z = 0.0
        } else {
            if (forward != 0.0) {
                if (strafe > 0.0) {
                    yaw += (if (forward > 0.0) -direction else direction).toDouble()
                } else if (strafe < 0.0) {
                    yaw += (if (forward > 0.0) direction else -direction).toDouble()
                }
                strafe = 0.0
                if (forward > 0.0) {
                    forward = 1.0
                } else if (forward < 0.0) {
                    forward = -1.0
                }
            }
            val cos = cos(MathUtils.toRadians(yaw + 90.0f))
            val sin = sin(MathUtils.toRadians(yaw + 90.0f))
            event.x = (forward * speed * cos + strafe * speed * sin) * motion
            event.z = (forward * speed * sin - strafe * speed * cos) * motion
        }
    }

    fun setMotion(speed: Double, smoothStrafe: Boolean) {
        var forward = mc.thePlayer.movementInput.moveForward.toDouble()
        var strafe = mc.thePlayer.movementInput.moveStrafe.toDouble()
        var yaw = mc.thePlayer.rotationYaw
        val direction = if (smoothStrafe) 45 else 90
        if (forward == 0.0 && strafe == 0.0) {
            mc.thePlayer.motionX = 0.0
            mc.thePlayer.motionZ = 0.0
        } else {
            if (forward != 0.0) {
                if (strafe > 0.0) {
                    yaw += (if (forward > 0.0) -direction else direction).toFloat()
                } else if (strafe < 0.0) {
                    yaw += (if (forward > 0.0) direction else -direction).toFloat()
                }
                strafe = 0.0
                if (forward > 0.0) {
                    forward = 1.0
                } else if (forward < 0.0) {
                    forward = -1.0
                }
            }
            val cos = cos(MathUtils.toRadians(yaw + 90.0f))
            val sin = sin(MathUtils.toRadians(yaw + 90.0f))
            mc.thePlayer.motionX = forward * speed * -sin + strafe * speed * cos
            mc.thePlayer.motionZ = forward * speed * cos - strafe * speed * -sin
        }
    }

    fun setSpeed(event: MoveEvent, speed: Double, yaw: Float, forward: Double, strafe: Double) {
        var forward = forward
        var strafe = strafe
        var yaw = yaw
        if (forward == 0.0 && strafe == 0.0) {
            event.z = 0.0
            event.x = 0.0
        } else {
            if (forward != 0.0) {
                if (strafe > 0.0) {
                    yaw += (if (forward > 0.0) -45 else 45).toFloat()
                } else if (strafe < 0.0) {
                    yaw += (if (forward > 0.0) 45 else -45).toFloat()
                }
                strafe = 0.0
                if (forward > 0.0) {
                    forward = 1.0
                } else if (forward < 0.0) {
                    forward = -1.0
                }
            }
            if (strafe > 0.0) {
                strafe = 1.0
            } else if (strafe < 0.0) {
                strafe = -1.0
            }
            val cos = cos(MathUtils.toRadians(yaw + 90.0f))
            val sin = sin(MathUtils.toRadians(yaw + 90.0f))
            event.x = forward * speed * cos + strafe * speed * sin
            event.z = forward * speed * sin - strafe * speed * cos
        }
    }

    val isGoingDiagonally: Boolean
        get() = abs(mc.thePlayer.motionX) > 0.04 && abs(mc.thePlayer.motionZ) > 0.04

    fun resetMotion(y: Boolean = false) {
        if (y) mc.thePlayer.motionY = 0.0
        mc.thePlayer.motionX = 0.0
        mc.thePlayer.motionZ = 0.0
    }

    val movingYaw: Float
        get() = (getDirection() * 180f / Math.PI).toFloat()

    override fun handleEvents() = true
}
