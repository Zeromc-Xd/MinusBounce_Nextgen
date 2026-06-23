package net.minusmc.minusbounce.features.module.modules.combat.velocitys.intave

import net.minecraft.entity.EntityLivingBase
import net.minecraft.potion.Potion
import net.minecraft.potion.PotionEffect
import net.minusmc.minusbounce.event.AttackEvent
import net.minusmc.minusbounce.event.PostVelocityEvent
import net.minusmc.minusbounce.features.module.modules.combat.velocitys.VelocityMode
import net.minusmc.minusbounce.utils.ClientUtils
import net.minusmc.minusbounce.value.BoolValue
import net.minusmc.minusbounce.value.FloatValue


class IntavePlusVelocity : VelocityMode("Intave") {
    private var xzOnHit = FloatValue("XZ-on-hit", 0.6f, 0f, 1f)
    private var xzOnSprintHit = FloatValue("XZ-on-sprint-hit", 0.6f, 0f, 1f)
    private var reduceUnnecessarySlowdown = BoolValue("Reduce-unnecessary-slowdown", false)
    private var jump = BoolValue("Jump", false)
    private var notWhileSpeed = BoolValue("Not-while-speed", false)
    private var notWhileJumpBoost = BoolValue("Not-while-jump-boost", false)
    private var debug = BoolValue("Debug", false)

    private var reduced = false

    override fun onEnable() {
        reduced = false
    }

    override fun onPostVelocity(event: PostVelocityEvent) {
        if (noAction()) return

        if (jump.get() && mc.thePlayer.onGround) {
            mc.thePlayer.jump()
        }
        reduced = false
    }

    override fun onAttack(event: AttackEvent) {
        if (event.targetEntity is EntityLivingBase && mc.thePlayer.hurtTime > 0) {
            if (noAction()) return
            if (reduceUnnecessarySlowdown.get() && reduced) return

            if (mc.thePlayer.isSprinting) {
                mc.thePlayer.motionX *= xzOnSprintHit.get()
                mc.thePlayer.motionZ *= xzOnSprintHit.get()
            } else {
                mc.thePlayer.motionX *= xzOnHit.get()
                mc.thePlayer.motionZ *= xzOnHit.get()
            }
            reduced = true
            if (debug.get()) ClientUtils.displayChatMessage(
                String.format(
                    "Reduced %.3f %.3f",
                    mc.thePlayer.motionX,
                    mc.thePlayer.motionZ
                )
            )
        }
    }

    private fun noAction(): Boolean {
        return mc.thePlayer.activePotionEffects.parallelStream()
            .anyMatch { effect: PotionEffect ->
                (notWhileSpeed.get() && effect.potionID == Potion.moveSpeed.getId()
                        || notWhileJumpBoost.get() && effect.potionID == Potion.jump.getId())
            }
    }
}
