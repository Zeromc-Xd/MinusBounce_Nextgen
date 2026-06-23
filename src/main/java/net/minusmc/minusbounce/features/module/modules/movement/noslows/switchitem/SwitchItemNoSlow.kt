package net.minusmc.minusbounce.features.module.modules.movement.noslows.other

import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement
import net.minecraft.network.play.client.C09PacketHeldItemChange
import net.minusmc.minusbounce.event.PostMotionEvent
import net.minusmc.minusbounce.event.PreMotionEvent
import net.minusmc.minusbounce.features.module.modules.movement.noslows.NoSlowMode
import net.minusmc.minusbounce.utils.PacketUtils

class SwitchItemNoSlow : NoSlowMode("SwitchItem") {
	override fun onPreMotion(event: PreMotionEvent) {
		PacketUtils.sendPacketNoEvent(C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem % 8 + 1))
		PacketUtils.sendPacketNoEvent(C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem))
	}

	override fun onPostMotion(event: PostMotionEvent) {
		PacketUtils.sendPacketNoEvent(C08PacketPlayerBlockPlacement(mc.thePlayer.heldItem))
	}
}