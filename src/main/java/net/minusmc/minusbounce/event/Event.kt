/*
 * MinusBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/MinusMC/MinusBounce
 */
package net.minusmc.minusbounce.event

open class Event {
    var stopRunEvent: Boolean = false
}

open class CancellableEvent : Event() {

    /**
     * Let you know if the event is cancelled
     *
     * @return state of cancel
     */
    var isCancelled: Boolean = false

    /**
     * Allows you to cancel a event
     */
    fun cancelEvent() {
        isCancelled = true
    }

    /**
     * Allows you to stop cancel a event
     */
    fun stopCancel(){
        isCancelled = false
    }

}

enum class EventState(val stateName: String) {
    SEND("SEND"), RECEIVE("RECEIVE") // PacketEvent
}
