package net.minusmc.minusbounce.features.module.modules.combat.velocitys.normal

import net.minusmc.minusbounce.event.KnockBackEvent
import net.minusmc.minusbounce.event.MoveInputEvent
import net.minusmc.minusbounce.features.module.modules.combat.velocitys.VelocityMode
import net.minusmc.minusbounce.value.BoolValue
import net.minusmc.minusbounce.value.IntegerValue

class AttackReduceVelocity : VelocityMode("AttackReduce") {
    private val forwardValue = BoolValue("Forward", true)
    private val reduceYValue = BoolValue("ReduceY", true)
    private val powerValue = IntegerValue("Power", 1, 1, 10)
    private val keepSprintValue = BoolValue("KeepSprint", true)

    override fun onKnockBack(event: KnockBackEvent) {
        event.reduceY = reduceYValue.get()
        event.power = powerValue.get()
        event.sprint = keepSprintValue.get()
    }

    override fun onInput(event: MoveInputEvent) {
        val player = mc.thePlayer ?: return
        if (forwardValue.get() && mc.objectMouseOver?.entityHit != null && player.hurtTime > 0) {
            event.forward = 1f
        }
    }
}
