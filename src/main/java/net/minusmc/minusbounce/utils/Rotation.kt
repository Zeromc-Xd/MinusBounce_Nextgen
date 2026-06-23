/*
 * MinusBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/MinusMC/MinusBounce
 */
package net.minusmc.minusbounce.utils

import net.minecraft.entity.player.EntityPlayer
import net.minecraft.util.Vec3
import net.minusmc.minusbounce.utils.MinecraftInstance.Companion.mc
import net.minusmc.minusbounce.utils.block.PlaceInfo
import kotlin.math.*

/**
 * Rotations
 *
 */
data class Rotation(var yaw: Float, var pitch: Float) {

    /**
     * Set rotations to [player]
     */
    fun toPlayer(p: EntityPlayer) {
        isNan() ?: return

        fixedSensitivity(mc.gameSettings.mouseSensitivity)

        p.rotationYaw = yaw
        p.rotationPitch = pitch
    }


    fun deltaTo(target: Rotation): Rotation {
        return Rotation(wrapAngle(target.yaw - yaw), target.pitch - pitch)
    }

    fun angleTo(target: Rotation): Float {
        val delta = deltaTo(target)
        return hypot(delta.yaw, delta.pitch)
    }

    fun interpolateTo(target: Rotation, factor: Float): Rotation {
        val clamped = factor.coerceIn(0f, 1f)
        val delta = deltaTo(target)
        return Rotation(yaw + delta.yaw * clamped, (pitch + delta.pitch * clamped).coerceIn(-90f, 90f))
    }

    fun normalize(base: Rotation): Rotation {
        return Rotation(base.yaw + wrapAngle(yaw - base.yaw), pitch.coerceIn(-90f, 90f))
    }

    private fun wrapAngle(value: Float): Float {
        var angle = value
        while (angle <= -180f) angle += 360f
        while (angle > 180f) angle -= 360f
        return angle
    }

    /**
     * Patch gcd exploit in [Rotation]
     *
     */
    @JvmOverloads
    fun fixedSensitivity(
        s: Float = mc.gameSettings.mouseSensitivity,
        r : Rotation = MinecraftInstance.serverRotation
    ) {
        yaw = getFixedSensitivityAngle(s, yaw, r.yaw)
        pitch = getFixedSensitivityAngle(s, pitch, r.pitch).coerceIn(-90.0f, 90.0f)
    }

    private fun getFixedSensitivityAngle(
        s: Float,
        targetAngle: Float,
        startAngle: Float = 0f,
        gcd: Float = (s * 0.6f + 0.2f).pow(3) * 1.2f
    ) = startAngle + ((targetAngle - startAngle) / gcd).roundToInt() * gcd

    /**
     * Nan checks
     */
    fun isNan() = if(yaw.isNaN() || pitch.isNaN() || pitch !in -90.0..90.0) null else false

    override fun toString(): String {
        return "Rotation(yaw=$yaw, pitch=$pitch)"
    }
}

/**
 * Rotation with vector
 */
data class VecRotation(val vec: Vec3, val rotation: Rotation)

/**
 * Rotation with place info
 */
data class PlaceRotation(val placeInfo: PlaceInfo, val rotation: Rotation)
