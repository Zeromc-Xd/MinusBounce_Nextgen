package net.minusmc.minusbounce.features.module.modules.combat.velocitys

import net.minusmc.minusbounce.MinusBounce
import net.minusmc.minusbounce.event.*
import net.minusmc.minusbounce.features.module.modules.combat.Velocity
import net.minusmc.minusbounce.utils.ClassUtils
import net.minusmc.minusbounce.utils.MinecraftInstance
import net.minusmc.minusbounce.value.Value


abstract class VelocityMode(val modeName: String): MinecraftInstance() {
	protected val velocity: Velocity
		get() = MinusBounce.moduleManager[Velocity::class.java]!!

	open val values: List<Value<*>>
		get() = ClassUtils.getValues(this.javaClass, this)

	open fun onEnable() {}

	open fun onDisable() {}

	open fun onMove() {}

    open fun onUpdate() {}
    open fun onPacket(event: PacketEvent) {}
	open fun onRender(event: Render3DEvent) {}
    open fun onJump(event: JumpEvent) {}
    open fun onPreMotion(event: PreMotionEvent) {}
    open fun onStrafe(event: StrafeEvent) {}
	open fun onInput(event: MoveInputEvent) {}
	open fun onAttack(event: AttackEvent) {}
	open fun onKnockBack(event: KnockBackEvent) {}
	open fun onEntityDamage(event: EntityDamageEvent) {}
	open fun onPostVelocity(event: PostVelocityEvent) {}
	open fun onWorld(event: WorldEvent) {}

	open fun onPreUpdate() {
	}
	open fun gameLoop() {}
	open fun onTick() {}
}
