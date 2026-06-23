package net.minusmc.minusbounce.features.module.modules.movement.speeds.normal

import net.minusmc.minusbounce.event.StrafeEvent
import net.minusmc.minusbounce.features.module.modules.movement.speeds.SpeedMode
import net.minusmc.minusbounce.features.module.modules.movement.speeds.SpeedType
import net.minusmc.minusbounce.utils.MovementUtils
import net.minusmc.minusbounce.utils.Rotation
import net.minusmc.minusbounce.utils.RotationUtils
import net.minusmc.minusbounce.utils.movement.MovementFixType
import net.minusmc.minusbounce.value.BoolValue
import net.minusmc.minusbounce.value.ListValue

class LegitSpeed: SpeedMode("Legit", SpeedType.NORMAL) {

    private val exploit = ListValue("ExploitMode", arrayOf("Rotate", "Speed"), "Speed")
    private val cpuSPEED = BoolValue("SpeedUpExploit", true)
    private val jumpSpeed = BoolValue("NoJumpDelay", true)

    override fun onPreUpdate() {
        when(exploit.get()){
            "Rotate" -> if(!mc.thePlayer.onGround) {
                RotationUtils.setRotations(
                    Rotation(
                        mc.thePlayer.rotationYaw + 45f,
                        mc.thePlayer.rotationPitch
                    ),
                    speed = 10f,
                    fixType = MovementFixType.NORMAL,
                    keepLength = 0
                )
            }
            "Speed" -> MovementUtils.useDiagonalSpeed()
        }
        if (jumpSpeed.get()) {
            mc.thePlayer.jumpTicks = 0
        }

        if (cpuSPEED.get()) {
            mc.timer.timerSpeed = 1.004f
        }
    }

    override fun onStrafe(event: StrafeEvent) {
        if (mc.thePlayer.onGround) {
            mc.thePlayer.jump()
        }
    }
}