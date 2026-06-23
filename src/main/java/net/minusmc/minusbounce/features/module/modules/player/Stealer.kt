/*
 * MinusBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/MinusMC/MinusBounce
 */
@file:Suppress("UNUSED_PARAMETER")

package net.minusmc.minusbounce.features.module.modules.player

import net.minecraft.block.BlockFalling
import net.minecraft.block.BlockSlime
import net.minecraft.block.BlockTNT
import net.minecraft.client.gui.inventory.GuiChest
import net.minecraft.init.Items
import net.minecraft.inventory.ContainerChest
import net.minecraft.item.*
import net.minecraft.network.play.server.S30PacketWindowItems
import net.minusmc.minusbounce.event.*
import net.minusmc.minusbounce.features.module.Module
import net.minusmc.minusbounce.features.module.ModuleCategory
import net.minusmc.minusbounce.features.module.ModuleInfo
import net.minusmc.minusbounce.utils.misc.RandomUtils
import net.minusmc.minusbounce.utils.timer.MSTimer
import net.minusmc.minusbounce.value.BoolValue
import net.minusmc.minusbounce.value.FloatValue
import org.lwjgl.opengl.Display
import java.util.*
import kotlin.math.ceil
import kotlin.math.floor


@ModuleInfo(name = "Stealer", spacedName = "Stealer", description = "Automatically steals all items from a chest.", category = ModuleCategory.PLAYER)
class Stealer : Module() {
    private val timer = MSTimer()
    private val startTimer = MSTimer()

    private val startDelay = FloatValue("StartDelay", 50f, 0f, 1000f)
    private val minDelay = FloatValue("MinDelay", 5f, 0f, 1000f)
    private val maxDelay = FloatValue("MaxDelay", 5f, 0f, 1000f)
    private val randomize = BoolValue("Randomize", false)
    private val stealTrashItems = BoolValue("TrashItems", false)
    private val autoClose = BoolValue("AutoClose", true)
    private val chestName = BoolValue("CheckChestName", false)
    private val disableOnWorldChange = BoolValue("DisableOnWorldChange", false)
    
    private var decidedTimer = 0
    private var gotItems = false
    private var ticksInChest = 0
    private var lastInChest = false

    override fun onDisable() {
        gotItems = false
    }

    @EventTarget
    fun onReceivePacket(e: PacketEvent) {
        mc.thePlayer ?: return
        if (mc.thePlayer.ticksExisted <= 60) {
            return
        }

        if (e.packet is S30PacketWindowItems) {
            gotItems = true
        }
    }

    @EventTarget
    fun onMotion(e: PreMotionEvent) {
        if (mc.thePlayer.ticksExisted <= 60) {
            return
        }

        if (mc.currentScreen is GuiChest && Display.isVisible() && (!chestName.get() || ((mc.currentScreen as GuiChest).lowerChestInventory.displayName.unformattedText.contains(
                "chest"
            )))
        ) {
            mc.mouseHelper.mouseXYChange()
            mc.mouseHelper.ungrabMouseCursor()
            mc.mouseHelper.grabMouseCursor()
        }

        if (mc.currentScreen is GuiChest) {
            ticksInChest++

            if (ticksInChest * 50 > 255) {
                ticksInChest = 10
            }
        } else {
            ticksInChest--
            gotItems = false

            if (ticksInChest < 0) {
                ticksInChest = 0
            }
        }
    }

    @EventTarget
    fun onRenderGui(e: PreUpdateEvent) {
        if (mc.thePlayer.ticksExisted <= 60) {
            return
        }

        if (!lastInChest) {
            startTimer.reset()
        }

        lastInChest = mc.currentScreen is GuiChest

        if (mc.currentScreen is GuiChest) {
            if (chestName.get()) {
                val name = (mc.currentScreen as GuiChest).lowerChestInventory.displayName.unformattedText

                if (!name.lowercase(Locale.getDefault()).contains("chest")) {
                    return
                }
            }

            if (!startTimer.hasTimeElapsed(startDelay.get().toDouble(), false)) return

            if (decidedTimer == 0) {
                val delayFirst = floor(minDelay.value.coerceAtMost(maxDelay.value)).toInt()
                val delaySecond = ceil(minDelay.value.coerceAtLeast(maxDelay.value)).toInt()
                decidedTimer = RandomUtils.nextInt(delayFirst, delaySecond)
            }

            if (timer.hasTimeElapsed(decidedTimer.toDouble(), false)) {
                val chest = mc.thePlayer.openContainer as ContainerChest

                if (randomize.get()) {
                    var found = false
                    for (i in chest.inventorySlots.indices) {
                        val stack = chest.lowerChestInventory.getStackInSlot(i)

                        if (stack != null && (itemWhitelisted(stack) && !stealTrashItems.get())) {
                            found = true
                        }
                    }

                    var i = 0
                    for (loop in 1..chest.inventorySlots.size) {
                        if(chest.lowerChestInventory.getStackInSlot(i) == null) {
                            i = RandomUtils.nextInt(1, chest.inventorySlots.size)
                        } else break
                    }

                    val stack = chest.lowerChestInventory.getStackInSlot(i)

                    if (stack != null && (itemWhitelisted(stack) && !stealTrashItems.get())) {
                        mc.playerController.windowClick(chest.windowId, i, 0, 1, mc.thePlayer)
                        timer.reset()
                        val delayFirst = floor(minDelay.value.coerceAtMost(maxDelay.value)).toInt()
                        val delaySecond = ceil(minDelay.value.coerceAtLeast(maxDelay.value)).toInt()
                        decidedTimer = RandomUtils.nextInt(delayFirst, delaySecond)
                        gotItems = true
                        return
                    }

                    if (gotItems && !found && autoClose.get() && ticksInChest > 3) {
                        mc.thePlayer.closeScreen()
                        return
                    }
                } else {
                    for (i in chest.inventorySlots.indices) {
                        val stack = chest.lowerChestInventory.getStackInSlot(i)

                        if (stack != null && (itemWhitelisted(stack) && !stealTrashItems.get())) {
                            mc.playerController.windowClick(chest.windowId, i, 0, 1, mc.thePlayer)
                            timer.reset()
                            val delayFirst =
                                floor(minDelay.value.coerceAtMost(maxDelay.value)).toInt()
                            val delaySecond =
                                ceil(minDelay.value.coerceAtLeast(maxDelay.value)).toInt()
                            decidedTimer = RandomUtils.nextInt(delayFirst, delaySecond)
                            gotItems = true
                            return
                        }
                    }

                    if (gotItems && autoClose.get() && ticksInChest > 3) {
                        mc.thePlayer.closeScreen()
                    }
                }
            }
        }
    }

    @EventTarget
    fun onWorld(e: WorldEvent) {
        if (disableOnWorldChange.get()) {
            this.toggle()
        }
    }


    private fun itemWhitelisted(itemStack: ItemStack): Boolean {
        val whitelistedItems: ArrayList<Item?> = object : ArrayList<Item?>() {
            init {
                add(Items.ender_pearl)
                add(Items.iron_ingot)
                add(Items.snowball)
                add(Items.gold_ingot)
                add(Items.redstone)
                add(Items.diamond)
                add(Items.emerald)
                add(Items.quartz)
                add(Items.bow)
                add(Items.arrow)
                add(Items.fishing_rod)
            }
        }
        val item: Item = itemStack.item
        val itemName = itemStack.displayName

        if (itemName.contains("Right Click") || itemName.contains("Click to Use") || itemName.contains("Players Finder")) {
            return true
        }

        val whitelistedPotions: ArrayList<Int?> = object : ArrayList<Int?>() {
            init {
                add(6)
                add(1)
                add(5)
                add(8)
                add(14)
                add(12)
                add(10)
                add(16)
            }
        }

        if (item is ItemPotion) {
            val potionID = getPotionId(itemStack)
            return whitelistedPotions.contains(potionID)
        }

        return ((item is ItemBlock
                && item.getBlock() !is BlockTNT
                && item.getBlock() !is BlockSlime
                && item.getBlock() !is BlockFalling)
                || item is ItemAnvilBlock
                || item is ItemSword
                || item is ItemArmor
                || item is ItemTool
                || item is ItemFood
                || item is ItemSkull
                || itemName.contains("\u00a7")
                || (whitelistedItems.contains(item)
                && item != Items.spider_eye))
    }

    private fun getPotionId(potion: ItemStack): Int {
        val item: Item = potion.item

        try {
            if (item is ItemPotion) {
                return item.getEffects(potion.metadata)[0].potionID
            }
        } catch (ignored: NullPointerException) {
        }

        return 0
    }

}