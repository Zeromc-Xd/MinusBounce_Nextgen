package net.minusmc.minusbounce.features.module.modules.combat.killaura.blocking

import net.minusmc.minusbounce.features.module.modules.combat.killaura.KillAuraBlocking
import net.minusmc.minusbounce.utils.extensions.getDistanceToEntityBox

class Intave: KillAuraBlocking("IntaveTest") {
    override fun onPreAttack() {
//        if(blockingStatus && !killAura.swing){
//            mc.netHandler.addToSendQueue(C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos(mc.thePlayer), EnumFacing.DOWN))
//            mc.gameSettings.keyBindUseItem.pressed = false
//            blockingStatus = false
//        }
    }

    override fun onPostAttack() {
        val target = mc.objectMouseOver?.entityHit ?: return

        if(mc.thePlayer.getDistanceToEntityBox(target) <= killAura.autoBlockRangeValue.get()){
            killAura.startBlocking(true, true)
        }
    }
}