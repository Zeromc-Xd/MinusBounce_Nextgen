package net.minusmc.minusbounce.features.module.modules.combat.killaura.blocking

import net.minusmc.minusbounce.features.module.modules.combat.killaura.KillAuraBlocking

class VanillaBlocking: KillAuraBlocking("Vanilla") {
    override fun onPostAttack(){
        killAura.startBlocking(true, true)
    }
}