package net.minusmc.minusbounce.features.module.modules.combat.velocitys.normal

import net.minusmc.minusbounce.event.MoveInputEvent
import net.minusmc.minusbounce.features.module.modules.combat.velocitys.VelocityMode
import net.minusmc.minusbounce.value.BoolValue
import net.minusmc.minusbounce.value.IntegerValue

class JumpReset : VelocityMode("JumpReset") {
    private val jumpByReceivedHits = BoolValue("JumpByReceivedHits", false)
    private val hitsUntilJump = IntegerValue("HitsUntilJump", 2, 0, 10) { jumpByReceivedHits.get() }

    private val jumpByDelay = BoolValue("JumpByDelay", true)
    private val ticksUntilJump = IntegerValue("UntilJump", 2, 0,20, "ticks") { jumpByDelay.get() }

    private var limitUntilJump = 0

    override fun onInput (event: MoveInputEvent) {
        // To be able to alter velocity when receiving knockback, player must be sprinting.
        if (mc.thePlayer.hurtTime != 9 || !mc.thePlayer.onGround || !mc.thePlayer.isSprinting || !isCooldownOver()) {
            updateLimit()
            return
        }

        event.jump = true
        limitUntilJump = 0
    }

    private fun isCooldownOver(): Boolean {
        return when {
            jumpByReceivedHits.get() -> limitUntilJump >= hitsUntilJump.get()
            jumpByDelay.get() -> limitUntilJump >= ticksUntilJump.get()
            else -> true // If none of the options are enabled, it will go automatic
        }
    }

    private fun updateLimit() {
        if (jumpByReceivedHits.get()) {
            if (mc.thePlayer.hurtTime == 9) {
                limitUntilJump++
            }
            return
        }

        limitUntilJump++
    }
}
