package net.minusmc.minusbounce.features.module.modules.player

import net.minecraft.client.gui.inventory.GuiInventory
import net.minusmc.minusbounce.event.EventTarget
import net.minusmc.minusbounce.event.MoveInputEvent
import net.minusmc.minusbounce.event.PreUpdateEvent
import net.minusmc.minusbounce.features.module.Module
import net.minusmc.minusbounce.features.module.ModuleCategory
import net.minusmc.minusbounce.features.module.ModuleInfo
import net.minusmc.minusbounce.utils.InventoryUtils
import net.minusmc.minusbounce.utils.InventoryUtils.closeInv
import net.minusmc.minusbounce.utils.InventoryUtils.drop
import net.minusmc.minusbounce.utils.InventoryUtils.getProtection
import net.minusmc.minusbounce.utils.InventoryUtils.isBadStack
import net.minusmc.minusbounce.utils.InventoryUtils.isBestArmor
import net.minusmc.minusbounce.utils.InventoryUtils.openInv
import net.minusmc.minusbounce.utils.InventoryUtils.shiftClick
import net.minusmc.minusbounce.utils.timer.MSTimer
import net.minusmc.minusbounce.value.FloatValue
import net.minusmc.minusbounce.value.ListValue


@Suppress("UNUSED_PARAMETER")
@ModuleInfo(name = "AutoArmor", spacedName = "Auto Armor", description = "Automatically equips armors for you.", category = ModuleCategory.PLAYER)
class AutoArmor : Module() {
    private val startTimer = MSTimer()
    private val mode = ListValue("Mode", arrayOf("OpenInv", "Spoof"))
    private val startDelay = FloatValue("StartDelay", 150.0f, 0.0f, 1000.0f)
    private val speed = FloatValue("Speed", 150f, 0f, 1000f)

    @EventTarget
    fun onMoveButton(event: MoveInputEvent) {
        if (InventoryUtils.isInventoryOpen) {
            event.forward = 0.0f
            event.strafe = 0.0f
            event.jump = false
            event.sneak = false
        }
    }

    @EventTarget
    fun onUpdate(e: PreUpdateEvent) {
        if (mode.get() == "Spoof" && mc.currentScreen != null) {
            return
        }

        if (mode.get() == "OpenInv") {
            if (mc.currentScreen == null) {
                startTimer.reset()
            }
            if (!startTimer.hasTimeElapsed(startDelay.get().toDouble(), false)) {
                return
            }
        }

        if (InventoryUtils.timer.hasTimeElapsed(speed.value.toDouble(), false)) {
            if (mode.get() == "OpenInv"
                && mc.currentScreen !is GuiInventory
            ) return

            for (type in 1..4) {
                if (mc.thePlayer.inventoryContainer.getSlot(4 + type).hasStack) {
                    val `is` = mc.thePlayer.inventoryContainer.getSlot(4 + type).stack

                    if (isBestArmor(`is`, type)) continue

                    openInv(mode.get())
                    drop(4 + type)

                    InventoryUtils.timer.reset()
                    if (speed.get() != 0f) break
                }
            }
            for (type in 1..4) {
                if (InventoryUtils.timer.time > speed.get()) {
                    for (i in 9..44) {
                        if (mc.thePlayer.inventoryContainer.getSlot(i).hasStack) {
                            val `is` = mc.thePlayer.inventoryContainer.getSlot(i).stack
                            if (getProtection(`is`) > 0.0f) {
                                if (isBestArmor(`is`, type)
                                    && !isBadStack(`is`, preferSword = true, keepTools = true)
                                ) {
                                    openInv(mode.get())
                                    shiftClick(i)

                                    InventoryUtils.timer.reset()
                                    if (speed.get() != 0f) break
                                }
                            }
                        }
                    }
                }
            }
        }
        if (InventoryUtils.timer.time > 75) {
            closeInv(mode.get())
        }
    }
}