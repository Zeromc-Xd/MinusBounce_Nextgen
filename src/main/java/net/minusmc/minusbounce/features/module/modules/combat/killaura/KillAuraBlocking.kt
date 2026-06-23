package net.minusmc.minusbounce.features.module.modules.combat.killaura

import net.minusmc.minusbounce.MinusBounce
import net.minusmc.minusbounce.event.PacketEvent
import net.minusmc.minusbounce.event.PreUpdateEvent
import net.minusmc.minusbounce.event.SlowDownEvent
import net.minusmc.minusbounce.features.module.modules.combat.KillAura
import net.minusmc.minusbounce.utils.MinecraftInstance

abstract class KillAuraBlocking(val modeName: String): MinecraftInstance() {
	protected val killAura: KillAura
        get() = MinusBounce.moduleManager[KillAura::class.java]!!
		 
	protected var blockingStatus: Boolean
		get() = killAura.blockingStatus
		set(value) {
			killAura.blockingStatus = value
		}

	open fun onPreMotion() {}

	open fun onPostMotion() {}

	open fun onPreAttack() {}

	open fun onPostAttack() {}

	open fun onPreUpdate(event: PreUpdateEvent) {}

	open fun onPacket(event: PacketEvent) {}

	open fun onDisable() {}

	open fun onSlowDown(event: SlowDownEvent) {}
}