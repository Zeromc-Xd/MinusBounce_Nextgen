/*
 * MinusBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/MinusMC/MinusBounce
 */
package net.minusmc.minusbounce.utils

import net.minecraft.network.Packet
import net.minusmc.minusbounce.event.*
import net.minecraft.network.play.client.*

object BadPacketUtils : MinecraftInstance(), Listenable {
    private var slot = false
    private var attack = false
    private var swing = false
    private var block = false
    private var inventory = false

    fun bad(): Boolean {
        return bad(true, true, true, true, true)
    }

    fun bad(slot: Boolean, attack: Boolean, swing: Boolean, block: Boolean, inventory: Boolean): Boolean {
        return (this.slot && slot) ||
                (this.attack && attack) ||
                (this.swing && swing) ||
                (this.block && block) ||
                (this.inventory && inventory)
    }

    fun reset() {
        slot = false
        swing = false
        attack = false
        block = false
        inventory = false
    }

    @EventTarget
    fun onPacket(event: PacketEvent){
        when (val packet = event.packet) {
            is C09PacketHeldItemChange -> slot = true
            is C0APacketAnimation -> swing = true
            is C02PacketUseEntity -> attack = true
            is C08PacketPlayerBlockPlacement, is C07PacketPlayerDigging -> block = true
            is C0EPacketClickWindow, is C0DPacketCloseWindow -> inventory = true
            is C16PacketClientStatus -> {
                if (packet.status == C16PacketClientStatus.EnumState.OPEN_INVENTORY_ACHIEVEMENT) {
                    inventory = true
                }
            }
            is C03PacketPlayer -> reset()
            else -> null
        }
    }

    override fun handleEvents() = true
}