package net.minusmc.minusbounce.utils.movement

import net.minecraft.util.MathHelper
import net.minusmc.minusbounce.event.JumpEvent
import net.minusmc.minusbounce.event.MoveInputEvent
import net.minusmc.minusbounce.event.StrafeEvent
import net.minusmc.minusbounce.utils.MinecraftInstance
import net.minusmc.minusbounce.utils.Rotation
import net.minusmc.minusbounce.utils.RotationUtils
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin

object MovementCorrection : MinecraftInstance() {
    @JvmStatic
    fun correctInput(event: MoveInputEvent) {
        if (!RotationUtils.active) return
        correctInput(event, RotationUtils.type, RotationUtils.targetRotation ?: return)
    }

    @JvmStatic
    fun correctStrafe(event: StrafeEvent) {
        if (!RotationUtils.active) return
        correctStrafe(event, RotationUtils.type, RotationUtils.targetRotation ?: return)
    }

    @JvmStatic
    fun correctJump(event: JumpEvent) {
        if (!RotationUtils.active) return
        correctJump(event, RotationUtils.type, RotationUtils.targetRotation ?: return)
    }

    fun correctInput(event: MoveInputEvent, type: MovementFixType, rotation: Rotation) {
        if (!type.correctInput || event.movementCorrected) return
        val angle = Math.toRadians((mc.thePlayer.rotationYaw - rotation.yaw).toDouble())
        val cos = cos(angle).toFloat()
        val sin = sin(angle).toFloat()
        val forward = event.forward
        val strafe = event.strafe
        event.forward = MathHelper.clamp_float(round(forward * cos + strafe * sin), -1f, 1f)
        event.strafe = MathHelper.clamp_float(round(strafe * cos - forward * sin), -1f, 1f)
        event.movementCorrected = true
    }

    fun correctStrafe(event: StrafeEvent, type: MovementFixType, rotation: Rotation) {
        if (!type.correctYaw || event.movementCorrected) return
        event.yaw = rotation.yaw
        event.movementCorrected = true
    }

    fun correctJump(event: JumpEvent, type: MovementFixType, rotation: Rotation) {
        if (!type.correctYaw || event.movementCorrected) return
        event.yaw = rotation.yaw
        event.movementCorrected = true
    }
}
