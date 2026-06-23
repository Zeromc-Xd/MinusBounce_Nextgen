package net.minusmc.minusbounce.utils.timing

import net.minusmc.minusbounce.event.EventTarget
import net.minusmc.minusbounce.event.TickEvent
import net.minusmc.minusbounce.event.Listenable
import net.minusmc.minusbounce.utils.MinecraftInstance

object WaitTickUtils : MinecraftInstance(), Listenable {

    private val scheduledActions = mutableListOf<ScheduledAction>()

    fun scheduleTicks(ticks: Int, action: () -> Unit) {
        scheduledActions.add(ScheduledAction(runTimeTicks + ticks, action))
    }

    @EventTarget(priority = -1)
    fun onTick(event: TickEvent) {
        val currentTick = runTimeTicks
        val iterator = scheduledActions.iterator()

        while (iterator.hasNext()) {
            val scheduledAction = iterator.next()
            if (currentTick >= scheduledAction.ticks) {
                scheduledAction.action.invoke()
                iterator.remove()
            }
        }
    }

    private data class ScheduledAction(val ticks: Int, val action: () -> Unit)

    override fun handleEvents() = true
}