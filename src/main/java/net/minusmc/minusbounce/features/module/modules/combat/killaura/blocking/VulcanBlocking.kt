package net.minusmc.minusbounce.features.module.modules.combat.killaura.blocking

import net.minecraft.network.play.client.C09PacketHeldItemChange
import net.minusmc.minusbounce.event.PreUpdateEvent
import net.minusmc.minusbounce.features.module.modules.combat.killaura.KillAuraBlocking

class VulcanBlocking: KillAuraBlocking("VulcanTest") {
    private var canBlock = false
    override fun onPreUpdate(event: PreUpdateEvent) {
        if(canBlock){
            killAura.startBlocking(true, true)
        }

        canBlock = false
    }

    override fun onPostAttack() {
        canBlock = true
    }
}