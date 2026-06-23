package net.minusmc.minusbounce.features.module.modules.movement.speeds.intave

import net.minusmc.minusbounce.features.module.modules.movement.speeds.SpeedMode
import net.minusmc.minusbounce.features.module.modules.movement.speeds.SpeedType
import net.minusmc.minusbounce.utils.MovementUtils.isMoving
import net.minusmc.minusbounce.utils.MovementUtils.strafe
import net.minusmc.minusbounce.utils.extensions.tryJump
import net.minusmc.minusbounce.value.BoolValue
import net.minusmc.minusbounce.value.FloatValue

/*
* Working on Intave: 14
* Tested on: mc.mineblaze.net
* Credit: @thatonecoder & @larryngton / Intave14
*/
class MineBlazeHop : SpeedMode("MineBlazeHop", SpeedType.INTAVE) {
    val boost = BoolValue("Boost", true)
    val strafeStrength = FloatValue("StrafeStrength", 0.29f, 0.1f, 0.29f)
    val groundTimer = FloatValue("GroundTimer", 0.5f, 0.1f, 5f)
    val airTimer = FloatValue("AirTimer", 1.09f, 0.1f, 5f)

    override fun onUpdate() {
        val player = mc.thePlayer ?: return

        if (!isMoving || player.isInWater || player.isInLava || player.isInWeb || player.isOnLadder) return

        if (player.onGround) {
            player.tryJump()

            if (player.isSprinting) strafe(strafeStrength.get())

            mc.timer.timerSpeed = groundTimer.get()
        } else {
            mc.timer.timerSpeed = airTimer.get()
        }

        if (boost.get() && player.motionY > 0.003 && player.isSprinting) {
            player.motionX *= 1.0015
            player.motionZ *= 1.0015
        }
    }
}