package net.minusmc.minusbounce.features.module.modules.combat.killaura.blocking

import net.minusmc.minusbounce.features.module.modules.combat.killaura.KillAuraBlocking

class ReBlock: KillAuraBlocking("ReBlock") {
    override fun onPostAttack(){
        killAura.startBlocking(false, true)
    }
}