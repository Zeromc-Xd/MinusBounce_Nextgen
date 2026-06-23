package net.minusmc.minusbounce.features.module.modules.combat.killaura.blocking

import net.minusmc.minusbounce.features.module.modules.combat.killaura.KillAuraBlocking

class PolarBlocking: KillAuraBlocking("Polar") {
    override fun onDisable() {
        mc.gameSettings.keyBindUseItem.pressed = false
    }

    override fun onPostAttack() {
        mc.gameSettings.keyBindUseItem.pressed =
            !(mc.thePlayer.hurtTime < 8 && mc.thePlayer.hurtTime != 1 && mc.thePlayer.fallDistance > 0)
    }
}