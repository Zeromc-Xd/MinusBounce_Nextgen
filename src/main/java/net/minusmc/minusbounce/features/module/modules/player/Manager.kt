/*
 * MinusBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/MinusMC/MinusBounce
 */
package net.minusmc.minusbounce.features.module.modules.player

import net.minecraft.client.gui.inventory.GuiInventory
import net.minecraft.init.Items
import net.minecraft.item.*
import net.minusmc.minusbounce.event.EventTarget
import net.minusmc.minusbounce.event.MoveInputEvent
import net.minusmc.minusbounce.event.PreUpdateEvent
import net.minusmc.minusbounce.features.module.Module
import net.minusmc.minusbounce.features.module.ModuleCategory
import net.minusmc.minusbounce.features.module.ModuleInfo
import net.minusmc.minusbounce.utils.InventoryUtils
import net.minusmc.minusbounce.utils.timer.MSTimer
import net.minusmc.minusbounce.value.BoolValue
import net.minusmc.minusbounce.value.FloatValue
import net.minusmc.minusbounce.value.IntegerValue
import net.minusmc.minusbounce.value.ListValue

@Suppress("UNUSED_PARAMETER")
@ModuleInfo(
    name = "Manager",
    spacedName = "Manager",
    description = "Automatically throws away useless items",
    category = ModuleCategory.PLAYER
)
class Manager : Module() {
    private val startTimer = MSTimer()
    private val mode = ListValue("Mode", arrayOf("OpenInv", "Spoof"))

    private val noMove = BoolValue("NoMove", true)
    private val throwGarbage = BoolValue("ThrowGarbage", true)

    private val startDelay = FloatValue("StartDelay", 150.0f, 0.0f, 1000.0f)
    private val speed = FloatValue("Speed", 150.0f, 0.0f, 1000.0f)

    private val sword = BoolValue("Sword", true)
    private val swordSlot = IntegerValue("SwordSlot", 1, 1, 9) { sword.value }

    private val axe = BoolValue("Axe", true)
    private val axeSlot = IntegerValue("AxeSlot", 2, 1, 9) { axe.value }

    private val pickaxe = BoolValue("Pickaxe", true)
    private val pickaxeSlot = IntegerValue("Pickaxe", 3, 1, 9) { pickaxe.value }

    private val shovel = BoolValue("Shovel", false)
    private val shovelSlot = IntegerValue("ShovelSlot", 4, 1, 9) { shovel.value }

    private val bow = BoolValue("Bow", false)
    private val bowSlot = IntegerValue("BowSlot", 5, 1, 9) { bow.value }

    private val blocks = BoolValue("Blocks", true)
    private val blockSlot = IntegerValue("BlockSlot", 6, 1, 9) { blocks.value }

    private val projectiles = BoolValue("Projectiles", true)
    private val projectileSlot = IntegerValue("ProjectileSlot", 7, 1, 9) { projectiles.value }

    private val waterBucket = BoolValue("WaterBucket", true)
    private val waterBucketSlot = IntegerValue("WaterBucketSlot", 8, 1, 9) { waterBucket.value }

    @EventTarget
    fun onMoveButton(e: MoveInputEvent) {
        if (InventoryUtils.isInventoryOpen && noMove.get()) {
            e.forward = 0.0f
            e.strafe = 0.0f
            e.jump = false
            e.sneak = false
        }
    }

    @EventTarget
    fun onUpdate(e: PreUpdateEvent) {
        if (mode.get() == "OpenInv") {
            if (mc.currentScreen == null) {
                startTimer.reset()
            }
            if (!startTimer.hasTimeElapsed(startDelay.get().toDouble(), false)) {
                return
            }
        }

        if (mode.get() == "Spoof" && mc.currentScreen != null) {
            return
        }

        if (mode.get() == "OpenInv" && mc.currentScreen !is GuiInventory) {
            return
        }

        for (i in 9..44) {
            if (mc.thePlayer.inventoryContainer.getSlot(i).hasStack) {
                val `is` = mc.thePlayer.inventoryContainer.getSlot(i).stack

                if (InventoryUtils.timer.hasTimeElapsed(speed.value.toDouble(), false)) {
                    if ((swordSlot.value != 0 && `is`.item is ItemSword && `is` == InventoryUtils.bestSword() && mc.thePlayer.inventoryContainer.inventory.contains(
                            InventoryUtils.bestSword()
                        )) && mc.thePlayer.inventoryContainer.getSlot((35 + swordSlot.value)).stack != `is` && sword.get()
                    ) {
                        InventoryUtils.openInv(mode.get())
                        mc.playerController.windowClick(
                            mc.thePlayer.inventoryContainer.windowId, i, (swordSlot.value - 1), 2, mc.thePlayer
                        )
                        InventoryUtils.timer.reset()

                        if (speed.value != 0.0f) break
                    } else if (bowSlot.value != 0 && `is`.item is ItemBow && `is` == InventoryUtils.bestBow() && mc.thePlayer.inventoryContainer.inventory.contains(
                            InventoryUtils.bestBow()
                        ) && mc.thePlayer.inventoryContainer.getSlot((35 + bowSlot.value)).stack != `is` && bow.get()
                    ) {
                        InventoryUtils.openInv(mode.get())
                        mc.playerController.windowClick(
                            mc.thePlayer.inventoryContainer.windowId, i, (bowSlot.value - 1), 2, mc.thePlayer
                        )
                        InventoryUtils.timer.reset()

                        if (speed.value != 0f) break
                    } else if ((pickaxeSlot.value != 0 && `is`.item is ItemPickaxe && `is` == InventoryUtils.bestPick() && `is` != InventoryUtils.bestWeapon() && mc.thePlayer.inventoryContainer.inventory.contains(
                            InventoryUtils.bestPick()
                        )) && mc.thePlayer.inventoryContainer.getSlot((35 + pickaxeSlot.value)).stack != `is` && pickaxe.get()
                    ) {
                        InventoryUtils.openInv(mode.get())
                        mc.playerController.windowClick(
                            mc.thePlayer.inventoryContainer.windowId, i, (pickaxeSlot.value - 1), 2, mc.thePlayer
                        )
                        InventoryUtils.timer.reset()

                        if (speed.value != 0f) break
                    } else if ((axeSlot.value != 0 && `is`.item is ItemAxe && `is` == InventoryUtils.bestAxe() && `is` != InventoryUtils.bestWeapon() && mc.thePlayer.inventoryContainer.inventory.contains(
                            InventoryUtils.bestAxe()
                        )) && mc.thePlayer.inventoryContainer.getSlot((35 + axeSlot.value)).stack != `is` && axe.get()
                    ) {
                        InventoryUtils.openInv(mode.get())
                        mc.playerController.windowClick(
                            mc.thePlayer.inventoryContainer.windowId, i, (axeSlot.value - 1), 2, mc.thePlayer
                        )
                        InventoryUtils.timer.reset()

                        if (speed.value != 0f) break
                    } else if ((shovelSlot.value != 0 && `is`.item is ItemSpade && `is` == InventoryUtils.bestShovel() && `is` != InventoryUtils.bestWeapon() && mc.thePlayer.inventoryContainer.inventory.contains(
                            InventoryUtils.bestShovel()
                        )) && mc.thePlayer.inventoryContainer.getSlot((35 + shovelSlot.value)).stack != `is` && shovel.get()
                    ) {
                        InventoryUtils.openInv(mode.get())
                        mc.playerController.windowClick(
                            mc.thePlayer.inventoryContainer.windowId, i, (shovelSlot.value - 1), 2, mc.thePlayer
                        )
                        InventoryUtils.timer.reset()

                        if (speed.value != 0f) break
                    } else if ((blockSlot.value != 0 && `is`.item is ItemBlock && `is` == InventoryUtils.getBlockSlotInventory() && mc.thePlayer.inventoryContainer.inventory.contains(
                            InventoryUtils.getBlockSlotInventory()
                        )) && mc.thePlayer.inventoryContainer.getSlot((35 + blockSlot.value)).stack != `is` && blocks.get()
                    ) {
                        if (mc.thePlayer.inventoryContainer.getSlot((35 + blockSlot.value)).stack != null && mc.thePlayer.inventoryContainer.getSlot(
                                (35 + blockSlot.value)
                            ).stack.item is ItemBlock && !InventoryUtils.invalidBlocks.contains(
                                (mc.thePlayer.inventoryContainer.getSlot((35 + blockSlot.value)).stack.item as ItemBlock).getBlock()
                            )
                        ) return
                        InventoryUtils.openInv(mode.get())
                        mc.playerController.windowClick(
                            mc.thePlayer.inventoryContainer.windowId, i, (blockSlot.value - 1), 2, mc.thePlayer
                        )
                        InventoryUtils.timer.reset()

                        if (speed.value != 0f) break
                    } else if ((projectileSlot.value != 0) && `is` == InventoryUtils.getProjectileSlotInventory() && mc.thePlayer.inventoryContainer.inventory.contains(
                            InventoryUtils.getProjectileSlotInventory()
                        ) && mc.thePlayer.inventoryContainer.getSlot((35 + projectileSlot.value)).stack != `is` && projectiles.get()
                    ) {
                        if (mc.thePlayer.inventoryContainer.getSlot((35 + projectileSlot.value)).stack != null && (mc.thePlayer.inventoryContainer.getSlot(
                                (35 + projectileSlot.value)
                            ).stack.item is ItemSnowball || mc.thePlayer.inventoryContainer.getSlot(
                                (35 + projectileSlot.value)
                            ).stack.item is ItemEgg || mc.thePlayer.inventoryContainer.getSlot(
                                (35 + projectileSlot.value)
                            ).stack.item is ItemFishingRod)
                        ) return
                        InventoryUtils.openInv(mode.get())
                        mc.playerController.windowClick(
                            mc.thePlayer.inventoryContainer.windowId,
                            i,
                            (projectileSlot.value - 1),
                            2,
                            mc.thePlayer
                        )
                        InventoryUtils.timer.reset()

                        if (speed.value != 0f) break
                    } else if (waterBucketSlot.value != 0 && `is`.item === Items.water_bucket && `is` == InventoryUtils.getBucketSlotInventory() && mc.thePlayer.inventoryContainer.inventory.contains(
                            InventoryUtils.getBucketSlotInventory()
                        ) && mc.thePlayer.inventoryContainer.getSlot((35 + shovelSlot.value)).stack != `is` && waterBucket.get()
                    ) {
                        if (mc.thePlayer.inventoryContainer.getSlot((35 + waterBucketSlot.value)).stack != null && mc.thePlayer.inventoryContainer.getSlot(
                                (35 + waterBucketSlot.value)
                            ).stack.item === Items.water_bucket
                        ) return
                        InventoryUtils.openInv(mode.get())
                        mc.playerController.windowClick(
                            mc.thePlayer.inventoryContainer.windowId,
                            i,
                            (waterBucketSlot.value - 1),
                            2,
                            mc.thePlayer
                        )
                        InventoryUtils.timer.reset()

                        if (speed.value != 0f) break
                    } else if (InventoryUtils.isBadStack(`is`, true, keepTools = true) && throwGarbage.get()) {
                        InventoryUtils.openInv(mode.get())
                        mc.playerController.windowClick(mc.thePlayer.inventoryContainer.windowId, i, 1, 4, mc.thePlayer)
                        InventoryUtils.timer.reset()

                        if (speed.value != 0f) break
                    } else {
                        if (InventoryUtils.timer.time > 75L) {
                            InventoryUtils.closeInv(mode.get())
                        }
                    }
                }
            }
        }
    }
}
