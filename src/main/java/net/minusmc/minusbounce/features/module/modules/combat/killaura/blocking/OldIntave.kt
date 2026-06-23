package net.minusmc.minusbounce.features.module.modules.combat.killaura.blocking

import net.minecraft.network.play.client.C09PacketHeldItemChange
import net.minusmc.minusbounce.features.module.modules.combat.killaura.KillAuraBlocking

class OldIntave: KillAuraBlocking("OldIntave") {
    override fun onPreAttack(){
        val slot = mc.thePlayer.inventory.currentItem
        mc.netHandler.addToSendQueue(C09PacketHeldItemChange(slot % 8 + 1))
        mc.netHandler.addToSendQueue(C09PacketHeldItemChange(slot))
    }

    override fun onPostAttack(){
        killAura.startBlocking(true, true)
    }
}