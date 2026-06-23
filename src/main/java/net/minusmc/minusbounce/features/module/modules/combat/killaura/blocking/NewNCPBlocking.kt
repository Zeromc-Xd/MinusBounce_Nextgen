package net.minusmc.minusbounce.features.module.modules.combat.killaura.blocking

import net.minecraft.network.play.client.C09PacketHeldItemChange
import net.minusmc.minusbounce.features.module.modules.combat.killaura.KillAuraBlocking

class NewNCPBlocking: KillAuraBlocking("NewNCP") {
    override fun onPreAttack() {
        if(blockingStatus){
            val slot = mc.thePlayer.inventory.currentItem
            mc.netHandler.addToSendQueue(C09PacketHeldItemChange(slot % 8 + 1))
            mc.netHandler.addToSendQueue(C09PacketHeldItemChange(slot))
            blockingStatus = false
        }
    }

    override fun onPostAttack(){
        killAura.startBlocking(true, true)
    }
}