package net.minusmc.minusbounce.features.module.modules.combat.killaura.blocking

import net.minusmc.minusbounce.features.module.modules.combat.killaura.KillAuraBlocking

class AfterTickBlocking: KillAuraBlocking("AfterTick") {
    override fun onPreAttack() {
        killAura.stopBlocking(false)
    }
    
    override fun onPostMotion() {
        killAura.startBlocking(true, true)
    }

}
