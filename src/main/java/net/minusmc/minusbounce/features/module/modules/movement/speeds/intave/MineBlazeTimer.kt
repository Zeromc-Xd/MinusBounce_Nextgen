package net.minusmc.minusbounce.features.module.modules.movement.speeds.intave

import net.minusmc.minusbounce.features.module.modules.movement.speeds.SpeedMode
import net.minusmc.minusbounce.features.module.modules.movement.speeds.SpeedType
import net.minusmc.minusbounce.utils.MovementUtils.isMoving
import net.minusmc.minusbounce.utils.extensions.tryJump

class MineBlazeTimer : SpeedMode("MineBlazeTimer", SpeedType.INTAVE) {
    override fun onUpdate() {
        val thePlayer = mc.thePlayer ?: return

        mc.timer.timerSpeed = 1f

        if (!isMoving || thePlayer.isInWater || thePlayer.isInLava || thePlayer.isOnLadder || thePlayer.isRiding)
            return

        if (thePlayer.onGround)
            thePlayer.tryJump()
        else {
            if (thePlayer.fallDistance <= 0.1)
                mc.timer.timerSpeed = 1.7f
            else if (thePlayer.fallDistance < 1.3)
                mc.timer.timerSpeed = 0.8f
            else
                mc.timer.timerSpeed = 1f
        }
    }

}