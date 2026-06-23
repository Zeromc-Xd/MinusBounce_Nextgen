package net.minusmc.minusbounce.features.module.modules.movement.nowebs

import net.minusmc.minusbounce.event.JumpEvent
import net.minusmc.minusbounce.event.MoveInputEvent
import net.minusmc.minusbounce.utils.ClassUtils
import net.minusmc.minusbounce.utils.MinecraftInstance
import net.minusmc.minusbounce.value.Value


abstract class NoWebMode(val modeName: String): MinecraftInstance() {
    open val values: List<Value<*>>
        get() = ClassUtils.getValues(this.javaClass, this)

    open fun onUpdate() {}
    open fun onJump(event: JumpEvent) {}
    open fun onTick() {}
    open fun onInput(event: MoveInputEvent) {}
}