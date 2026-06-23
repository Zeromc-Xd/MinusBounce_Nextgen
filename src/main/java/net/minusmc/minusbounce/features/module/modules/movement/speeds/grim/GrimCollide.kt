package net.minusmc.minusbounce.features.module.modules.movement.speeds.grim

import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.item.EntityArmorStand
import net.minusmc.minusbounce.event.PreMotionEvent
import net.minusmc.minusbounce.features.module.modules.movement.speeds.SpeedMode
import net.minusmc.minusbounce.features.module.modules.movement.speeds.SpeedType
import net.minusmc.minusbounce.utils.MovementUtils
import net.minusmc.minusbounce.value.BoolValue
import net.minusmc.minusbounce.value.FloatValue
import kotlin.math.cos
import kotlin.math.sin

class GrimCollide: SpeedMode("GrimCollide", SpeedType.GRIM) {

    private val jump = BoolValue("Jump", false)
    private val boostSpeed = FloatValue("BoostSpeed", 0.01f, 0.01f, 0.08f)

    override fun onUpdate() {
        if (MovementUtils.isMoving && mc.thePlayer.onGround && !mc.gameSettings.keyBindJump.pressed && jump.get())
            mc.thePlayer.jump()
    }

    override fun onPreMotion(event: PreMotionEvent) {
        if (!MovementUtils.isMoving)
            return

        val playerBox = mc.thePlayer.entityBoundingBox.expand(1.0, 1.0, 1.0)

        val collisions = mc.theWorld.loadedEntityList.count {
            it != mc.thePlayer && it is EntityLivingBase &&
                    it !is EntityArmorStand && playerBox.intersectsWith(it.entityBoundingBox)
        }

        val yaw = MovementUtils.direction()
        val boost = boostSpeed.get().toDouble() * collisions
        mc.thePlayer.addVelocity(-sin(yaw) * boost, 0.0, cos(yaw) * boost)
    }
}