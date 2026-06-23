/*
 * MinusBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/MinusMC/MinusBounce
 */
package net.minusmc.minusbounce.utils

import net.minecraft.block.Block
import net.minecraft.block.BlockBush
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.inventory.GuiInventory
import net.minecraft.client.settings.KeyBinding
import net.minecraft.enchantment.Enchantment
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.init.Blocks
import net.minecraft.init.Items
import net.minecraft.item.*
import net.minecraft.network.play.client.*
import net.minecraft.network.play.client.C16PacketClientStatus.EnumState.OPEN_INVENTORY_ACHIEVEMENT
import net.minecraft.network.play.server.S09PacketHeldItemChange
import net.minecraft.network.play.server.S2DPacketOpenWindow
import net.minecraft.network.play.server.S2EPacketCloseWindow
import net.minusmc.minusbounce.MinusBounce
import net.minusmc.minusbounce.event.*
import net.minusmc.minusbounce.features.module.modules.world.ChestAura
import net.minusmc.minusbounce.utils.PacketUtils.sendPacket
import net.minusmc.minusbounce.utils.timer.MSTimer
import org.lwjgl.input.Keyboard


object InventoryUtils : MinecraftInstance(), Listenable {

    // What slot is selected on server-side?
    // TODO: Is this equal to mc.playerController.currentPlayerItem?
    var serverSlot
        get() = _serverSlot
        set(value) {
            if (value != _serverSlot) {
                sendPacket(C09PacketHeldItemChange(value))

                _serverSlot = value
            }
        }

    // Is inventory open on server-side?
    var serverOpenInventory
        get() = _serverOpenInventory
        set(value) {
            if (value != _serverOpenInventory) {
                sendPacket(
                    if (value) C16PacketClientStatus(OPEN_INVENTORY_ACHIEVEMENT)
                    else C0DPacketCloseWindow(mc.thePlayer?.openContainer?.windowId ?: 0)
                )

                _serverOpenInventory = value
            }
        }

    var serverOpenContainer = false
        private set

    // Backing fields
    private var _serverSlot = 0
    private var _serverOpenInventory = false

    var isFirstInventoryClick = true

    val CLICK_TIMER = MSTimer()

    val BLOCK_BLACKLIST = listOf(
        Blocks.chest,
        Blocks.ender_chest,
        Blocks.trapped_chest,
        Blocks.anvil,
        Blocks.sand,
        Blocks.web,
        Blocks.torch,
        Blocks.crafting_table,
        Blocks.furnace,
        Blocks.waterlily,
        Blocks.dispenser,
        Blocks.stone_pressure_plate,
        Blocks.wooden_pressure_plate,
        Blocks.noteblock,
        Blocks.dropper,
        Blocks.tnt,
        Blocks.standing_banner,
        Blocks.wall_banner,
        Blocks.redstone_torch,
        Blocks.ladder
    )

    fun findItem(startInclusive: Int, endInclusive: Int, item: Item): Int? {
        for (i in startInclusive..endInclusive)
            if (mc.thePlayer.openContainer.getSlot(i).stack?.item == item)
                return i

        return null
    }

    fun hasSpaceInHotbar(): Boolean {
        for (i in 36..44)
            mc.thePlayer.openContainer.getSlot(i).stack ?: return true

        return false
    }

    fun findBlockInHotbar(): Int? {
        val player = mc.thePlayer ?: return null
        val inventory = player.openContainer

        return (36..44).filter {
            val stack = inventory.getSlot(it).stack ?: return@filter false
            val block = if (stack.item is ItemBlock) (stack.item as ItemBlock).block else return@filter false

            stack.item is ItemBlock && stack.stackSize > 0 && block !in BLOCK_BLACKLIST && block !is BlockBush
        }.minByOrNull { (inventory.getSlot(it).stack.item as ItemBlock).block.isFullCube }
    }

    fun findLargestBlockStackInHotbar(): Int? {
        val player = mc.thePlayer ?: return null
        val inventory = player.openContainer

        return (36..44).filter {
            val stack = inventory.getSlot(it).stack ?: return@filter false
            val block = if (stack.item is ItemBlock) (stack.item as ItemBlock).block else return@filter false

            stack.item is ItemBlock && stack.stackSize > 0 && block.isFullCube && block !in BLOCK_BLACKLIST && block !is BlockBush
        }.maxByOrNull { inventory.getSlot(it).stack.stackSize }
    }

    fun hasSpaceHotbar(): Boolean {
        for (i in 36..44) {
            mc.thePlayer.inventoryContainer.getSlot(i).stack ?: return true
        }
        return false
    }

    // Converts container slot to hotbar slot id, else returns null
    fun Int.toHotbarIndex(stacksSize: Int): Int? {
        val parsed = this - stacksSize + 9

        return if (parsed in 0..8) parsed else null
    }

    @EventTarget
    fun onPacket(event: PacketEvent) {

        if (event.isCancelled) return

        when (val packet = event.packet) {
            is C08PacketPlayerBlockPlacement, is C0EPacketClickWindow -> {
                CLICK_TIMER.reset()

                if (packet is C0EPacketClickWindow)
                    isFirstInventoryClick = false
            }

            is C16PacketClientStatus ->
                if (packet.status == OPEN_INVENTORY_ACHIEVEMENT) {
                    if (_serverOpenInventory) event.cancelEvent()
                    else {
                        isFirstInventoryClick = true
                        _serverOpenInventory = true
                    }
                }

            is C0DPacketCloseWindow, is S2EPacketCloseWindow, is S2DPacketOpenWindow -> {
                isFirstInventoryClick = false
                _serverOpenInventory = false
                serverOpenContainer = false

                if (packet is S2DPacketOpenWindow) {
                    if (packet.guiId == "minecraft:chest" || packet.guiId == "minecraft:container")
                        serverOpenContainer = true
                } else
                    MinusBounce.moduleManager.getModule(ChestAura::class.java)?.tileTarget = null
            }

            is C09PacketHeldItemChange -> {
                // Support for Singleplayer
                // (client packets get sent and received, duplicates would get cancelled, making slot changing impossible)
                if (event.eventType == EventState.RECEIVE) return

                if (packet.slotId == _serverSlot) event.cancelEvent()
                else _serverSlot = packet.slotId
            }

            is S09PacketHeldItemChange -> {
                if (_serverSlot == packet.heldItemHotbarIndex)
                    return

                _serverSlot = packet.heldItemHotbarIndex
            }
        }
    }

    @EventTarget
    fun onWorld(event: WorldEvent) {
        // Prevents desync
        _serverOpenInventory = false
        _serverSlot = 0
        serverOpenContainer = false
    }

    override fun handleEvents() = true
    
    
    
    /////////////////////////////////////////////////////////////////
    //////////////////KEEP DISTANCE//////////////////////////////////

    var mc: Minecraft = Minecraft.getMinecraft()

    var isInventoryOpen: Boolean = false

    var timer = MSTimer()

    var invalidBlocks: List<Block> = listOf(
        Blocks.enchanting_table,
        Blocks.carpet,
        Blocks.glass_pane,
        Blocks.ladder,
        Blocks.web,
        Blocks.stained_glass_pane,
        Blocks.iron_bars,
        Blocks.air,
        Blocks.water,
        Blocks.flowing_water,
        Blocks.lava,
        Blocks.ladder,
        Blocks.soul_sand,
        Blocks.ice,
        Blocks.packed_ice,
        Blocks.sand,
        Blocks.flowing_lava,
        Blocks.snow_layer,
        Blocks.chest,
        Blocks.ender_chest,
        Blocks.torch,
        Blocks.anvil,
        Blocks.trapped_chest,
        Blocks.noteblock,
        Blocks.jukebox,
        Blocks.wooden_pressure_plate,
        Blocks.stone_pressure_plate,
        Blocks.light_weighted_pressure_plate,
        Blocks.heavy_weighted_pressure_plate,
        Blocks.stone_button,
        Blocks.tnt,
        Blocks.wooden_button,
        Blocks.lever,
        Blocks.crafting_table,
        Blocks.furnace,
        Blocks.stone_slab,
        Blocks.wooden_slab,
        Blocks.stone_slab2,
        Blocks.brown_mushroom,
        Blocks.red_mushroom,
        Blocks.gold_block,
        Blocks.red_flower,
        Blocks.yellow_flower,
        Blocks.flower_pot
    )

    fun getBlockSlot(): Int {
        var item = -1
        var stacksize = 0

        if (mc.thePlayer.heldItem != null && mc.thePlayer.heldItem.item != null && mc.thePlayer.heldItem.item is ItemBlock && !invalidBlocks.contains(
                (mc.thePlayer.heldItem.item as ItemBlock).getBlock()
            )
        ) return mc.thePlayer.inventory.currentItem
        for (i in 36..44) {
            if (mc.thePlayer.inventoryContainer.getSlot(i).stack != null && mc.thePlayer.inventoryContainer.getSlot(i).stack.item is ItemBlock && !invalidBlocks.contains(
                    (mc.thePlayer.inventoryContainer.getSlot(i).stack.item as ItemBlock).getBlock()
                ) && mc.thePlayer.inventoryContainer.getSlot(i).stack.stackSize >= stacksize
            ) {
                item = i - 36
                stacksize = mc.thePlayer.inventoryContainer.getSlot(i).stack.stackSize
            }
        }

        return item
    }

    fun getBlockSlotInventory(): ItemStack? {
        var item: ItemStack? = null
        var stacksize = 0

        if (mc.thePlayer.heldItem != null && mc.thePlayer.heldItem.item != null && mc.thePlayer.heldItem.item is ItemBlock && !invalidBlocks.contains(
                (mc.thePlayer.heldItem.item as ItemBlock).getBlock()
            )
        ) return mc.thePlayer.heldItem
        for (i in 9..44) {
            if (mc.thePlayer.inventoryContainer.getSlot(i).stack != null && mc.thePlayer.inventoryContainer.getSlot(i).stack.item is ItemBlock && !invalidBlocks.contains(
                    (mc.thePlayer.inventoryContainer.getSlot(i).stack.item as ItemBlock).getBlock()
                ) && mc.thePlayer.inventoryContainer.getSlot(i).stack.stackSize >= stacksize
            ) {
                item = mc.thePlayer.inventoryContainer.getSlot(i).stack
                stacksize = mc.thePlayer.inventoryContainer.getSlot(i).stack.stackSize
            }
        }

        return item
    }

    fun getCobwebSlot(): Int {
        var item = -1
        var stacksize = 0

        for (i in 36..44) {
            if (mc.thePlayer.inventoryContainer.getSlot(i).stack != null && (mc.thePlayer.inventoryContainer.getSlot(i).stack.item is ItemBlock)) {
                val block = mc.thePlayer.inventoryContainer.getSlot(i).stack.item as ItemBlock
                if (block.getBlock() === Blocks.web) {
                    item = i - 36
                    stacksize = mc.thePlayer.inventoryContainer.getSlot(i).stack.stackSize
                }
            }
        }

        return item
    }

    fun getBucketSlot(): Int {
        var item = -1
        var stacksize = 0

        for (i in 36..44) {
            if (mc.thePlayer.inventoryContainer.getSlot(i).stack != null && (mc.thePlayer.inventoryContainer.getSlot(i).stack.item === Items.water_bucket)) {
                item = i - 36
                stacksize = mc.thePlayer.inventoryContainer.getSlot(i).stack.stackSize
            }
        }

        return item
    }

    fun getBucketSlotInventory(): ItemStack? {
        var item: ItemStack? = null
        var stacksize = 0

        for (i in 9..44) {
            if (mc.thePlayer.inventoryContainer.getSlot(i).stack != null && (mc.thePlayer.inventoryContainer.getSlot(i).stack.item === Items.water_bucket)) {
                item = mc.thePlayer.inventoryContainer.getSlot(i).stack
                stacksize = mc.thePlayer.inventoryContainer.getSlot(i).stack.stackSize
            }
        }

        return item
    }

    fun getProjectileSlot(): Int {
        var item = -1
        var stacksize = 0

        for (i in 36..44) {
            if (mc.thePlayer.inventoryContainer.getSlot(i).stack != null && (mc.thePlayer.inventoryContainer.getSlot(
                    i
                ).stack.item is ItemSnowball || mc.thePlayer.inventoryContainer.getSlot(i).stack.item is ItemEgg || mc.thePlayer.inventoryContainer.getSlot(
                    i
                ).stack.item is ItemFishingRod) && mc.thePlayer.inventoryContainer.getSlot(i).stack.stackSize >= stacksize
            ) {
                item = i - 36
                stacksize = mc.thePlayer.inventoryContainer.getSlot(i).stack.stackSize
            }
        }

        return item
    }

    fun getProjectileSlotInventory(): ItemStack? {
        var item: ItemStack? = null
        var stacksize = 0

        for (i in 9..44) {
            if (mc.thePlayer.inventoryContainer.getSlot(i).stack != null && (mc.thePlayer.inventoryContainer.getSlot(
                    i
                ).stack.item is ItemSnowball || mc.thePlayer.inventoryContainer.getSlot(i).stack.item is ItemEgg || mc.thePlayer.inventoryContainer.getSlot(
                    i
                ).stack.item is ItemFishingRod) && mc.thePlayer.inventoryContainer.getSlot(i).stack.stackSize >= stacksize
            ) {
                item = mc.thePlayer.inventoryContainer.getSlot(i).stack
                stacksize = mc.thePlayer.inventoryContainer.getSlot(i).stack.stackSize
            }
        }

        return item
    }

    fun getProtection(stack: ItemStack): Float {
        var prot = 0.0f

        if (stack.item is ItemArmor) {
            val armor = stack.item as ItemArmor
            prot =
                (prot + armor.damageReduceAmount + ((100 - armor.damageReduceAmount) * EnchantmentHelper.getEnchantmentLevel(
                    Enchantment.protection.effectId,
                    stack
                )) * 0.0075).toFloat()
            prot = (prot + EnchantmentHelper.getEnchantmentLevel(
                Enchantment.blastProtection.effectId,
                stack
            ) / 100.0).toFloat()
            prot = (prot + EnchantmentHelper.getEnchantmentLevel(
                Enchantment.fireProtection.effectId,
                stack
            ) / 100.0).toFloat()
            prot = (prot + EnchantmentHelper.getEnchantmentLevel(Enchantment.thorns.effectId, stack) / 100.0).toFloat()
            prot =
                (prot + EnchantmentHelper.getEnchantmentLevel(Enchantment.unbreaking.effectId, stack) / 50.0).toFloat()
            prot =
                (prot + EnchantmentHelper.getEnchantmentLevel(Enchantment.protection.effectId, stack) / 100.0).toFloat()
        }

        return prot
    }

    fun isBestArmor(stack: ItemStack, type: Int): Boolean {
        val prot = getProtection(stack)
        var strType = ""

        when (type) {
            1 -> {
                strType = "helmet"
            }
            2 -> {
                strType = "chestplate"
            }
            3 -> {
                strType = "leggings"
            }
            4 -> {
                strType = "boots"
            }
        }

        if (!stack.unlocalizedName.contains(strType)) {
            return false
        }

        for (i in 5..44) {
            if (Minecraft.getMinecraft().thePlayer.inventoryContainer.getSlot(i).hasStack) {
                val `is` = Minecraft.getMinecraft().thePlayer.inventoryContainer.getSlot(i).stack

                if (getProtection(`is`) > prot && `is`.unlocalizedName.contains(strType)) {
                    return false
                }
            }
        }
        return true
    }

    fun drop(slot: Int) {
        Minecraft.getMinecraft().playerController.windowClick(
            Minecraft.getMinecraft().thePlayer.inventoryContainer.windowId, slot, 1, 4,
            Minecraft.getMinecraft().thePlayer as EntityPlayer
        )
    }

    fun shiftClick(slot: Int) {
        Minecraft.getMinecraft().playerController.windowClick(
            Minecraft.getMinecraft().thePlayer.inventoryContainer.windowId, slot, 0, 1,
            Minecraft.getMinecraft().thePlayer as EntityPlayer
        )
    }

    fun isBadStack(`is`: ItemStack, preferSword: Boolean, keepTools: Boolean): Boolean {
        for (type in 1..4) {
            var strType = ""

            when (type) {
                1 -> {
                    strType = "helmet"
                }
                2 -> {
                    strType = "chestplate"
                }
                3 -> {
                    strType = "leggings"
                }
                4 -> {
                    strType = "boots"
                }
            }
            if (`is`.item is ItemArmor && !isBestArmor(`is`, type) && `is`.unlocalizedName.contains(strType)) {
                return true
            }
            if (mc.thePlayer.inventoryContainer.getSlot(4 + type).hasStack && isBestArmor(
                    mc.thePlayer.inventoryContainer.getSlot(
                        4 + type
                    ).stack, type
                ) && mc.thePlayer.inventoryContainer.getSlot(4 + type).stack.unlocalizedName.contains(
                    strType
                ) && `is`.unlocalizedName.contains(strType)
            ) {
                return true
            }
        }
        if ((`is`.item is ItemSword) && (`is` != bestWeapon()) && !preferSword) {
            return true
        }

        if (`is`.item is ItemSword && `is` != bestSword() && preferSword) {
            return true
        }

        if (`is`.item is ItemBow && `is` != bestBow()) {
            return true
        }

        if (keepTools) {
            if (`is`.item is ItemAxe && `is` != bestAxe() && (preferSword || `is` != bestWeapon())) {
                return true
            }

            if (`is`.item is ItemPickaxe && `is` != bestPick() && (preferSword || `is` != bestWeapon())) {
                return true
            }

            if (`is`.item is ItemSpade && `is` != bestShovel()) {
                return true
            }
        } else {
            if (`is`.item is ItemAxe && (preferSword || `is` != bestWeapon())) {
                return true
            }

            if (`is`.item is ItemPickaxe && (preferSword || `is` != bestWeapon())) {
                return true
            }

            if (`is`.item is ItemSpade) {
                return true
            }
        }

        return false
    }

    fun bestWeapon(): ItemStack? {
        var bestWeapon: ItemStack? = null
        var itemDamage = -1f

        for (i in 9..44) {
            if (mc.thePlayer.inventoryContainer.getSlot(i).hasStack) {
                val `is` = mc.thePlayer.inventoryContainer.getSlot(i).stack

                if (`is`.item is ItemSword || `is`.item is ItemAxe || `is`.item is ItemPickaxe) {
                    val toolDamage = getItemDamage(`is`)

                    if (toolDamage >= itemDamage) {
                        itemDamage = getItemDamage(`is`)
                        bestWeapon = `is`
                    }
                }
            }
        }

        return bestWeapon
    }

    fun bestSword(): ItemStack? {
        var bestSword: ItemStack? = null
        var itemDamage = -1f

        for (i in 9..44) {
            if (mc.thePlayer.inventoryContainer.getSlot(i).hasStack) {
                val `is` = mc.thePlayer.inventoryContainer.getSlot(i).stack

                if (`is`.item is ItemSword) {
                    val swordDamage = getItemDamage(`is`)

                    if (swordDamage >= itemDamage) {
                        itemDamage = getItemDamage(`is`)
                        bestSword = `is`
                    }
                }
            }
        }

        return bestSword
    }

    fun bestBow(): ItemStack? {
        var bestBow: ItemStack? = null
        var itemDamage = -1f

        for (i in 9..44) {
            if (mc.thePlayer.inventoryContainer.getSlot(i).hasStack) {
                val `is` = mc.thePlayer.inventoryContainer.getSlot(i).stack

                if (`is`.item is ItemBow) {
                    val bowDamage = getBowDamage(`is`)

                    if (bowDamage >= itemDamage) {
                        itemDamage = getBowDamage(`is`)
                        bestBow = `is`
                    }
                }
            }
        }

        return bestBow
    }

    fun bestAxe(): ItemStack? {
        var bestTool: ItemStack? = null
        var itemSkill = -1f

        for (i in 9..44) {
            if (mc.thePlayer.inventoryContainer.getSlot(i).hasStack) {
                val `is` = mc.thePlayer.inventoryContainer.getSlot(i).stack

                if (`is`.item is ItemAxe) {
                    val toolSkill = getToolRating(`is`)

                    if (toolSkill >= itemSkill) {
                        itemSkill = getToolRating(`is`)
                        bestTool = `is`
                    }
                }
            }
        }

        return bestTool
    }

    fun bestPick(): ItemStack? {
        var bestTool: ItemStack? = null
        var itemSkill = -1f

        for (i in 9..44) {
            if (mc.thePlayer.inventoryContainer.getSlot(i).hasStack) {
                val `is` = mc.thePlayer.inventoryContainer.getSlot(i).stack

                if (`is`.item is ItemPickaxe) {
                    val toolSkill = getToolRating(`is`)

                    if (toolSkill >= itemSkill) {
                        itemSkill = getToolRating(`is`)
                        bestTool = `is`
                    }
                }
            }
        }

        return bestTool
    }

    fun bestShovel(): ItemStack? {
        var bestTool: ItemStack? = null
        var itemSkill = -1f

        for (i in 9..44) {
            if (mc.thePlayer.inventoryContainer.getSlot(i).hasStack) {
                val `is` = mc.thePlayer.inventoryContainer.getSlot(i).stack

                if (`is`.item is ItemSpade) {
                    val toolSkill = getToolRating(`is`)

                    if (toolSkill >= itemSkill) {
                        itemSkill = getToolRating(`is`)
                        bestTool = `is`
                    }
                }
            }
        }

        return bestTool
    }

    fun getToolRating(itemStack: ItemStack): Float {
        var damage = getToolMaterialRating(itemStack, false)
        damage += EnchantmentHelper.getEnchantmentLevel(Enchantment.efficiency.effectId, itemStack) * 2.00f
        damage += EnchantmentHelper.getEnchantmentLevel(Enchantment.silkTouch.effectId, itemStack) * 0.50f
        damage += EnchantmentHelper.getEnchantmentLevel(Enchantment.fortune.effectId, itemStack) * 0.50f
        damage += EnchantmentHelper.getEnchantmentLevel(Enchantment.unbreaking.effectId, itemStack) * 0.10f
        damage += (itemStack.maxDamage - itemStack.itemDamage) * 0.000000000001f
        return damage
    }

    fun getItemDamage(itemStack: ItemStack): Float {
        var damage = getToolMaterialRating(itemStack, true)
        damage += EnchantmentHelper.getEnchantmentLevel(Enchantment.sharpness.effectId, itemStack) * 1.25f
        damage += EnchantmentHelper.getEnchantmentLevel(Enchantment.fireAspect.effectId, itemStack) * 0.50f
        damage += EnchantmentHelper.getEnchantmentLevel(Enchantment.unbreaking.effectId, itemStack) * 0.01f
        damage += (itemStack.maxDamage - itemStack.itemDamage) * 0.000000000001f

        if (itemStack.item is ItemSword) {
            damage += 0.2.toFloat()
        }

        return damage
    }

    fun getBowDamage(itemStack: ItemStack): Float {
        var damage = 5f
        damage += EnchantmentHelper.getEnchantmentLevel(Enchantment.power.effectId, itemStack) * 1.25f
        damage += EnchantmentHelper.getEnchantmentLevel(Enchantment.punch.effectId, itemStack) * 0.75f
        damage += EnchantmentHelper.getEnchantmentLevel(Enchantment.flame.effectId, itemStack) * 0.50f
        damage += EnchantmentHelper.getEnchantmentLevel(Enchantment.unbreaking.effectId, itemStack) * 0.10f
        damage += itemStack.maxDamage - itemStack.itemDamage * 0.001f
        return damage
    }

    fun getToolMaterialRating(itemStack: ItemStack, checkForDamage: Boolean): Float {
        val `is` = itemStack.item
        var rating = 0f

        when (`is`) {
            is ItemSword -> {
                when (`is`.toolMaterialName) {
                    "WOOD" -> rating = 4f
                    "GOLD" -> rating = 4f
                    "STONE" -> rating = 5f
                    "IRON" -> rating = 6f
                    "EMERALD" -> rating = 7f
                }
            }

            is ItemPickaxe -> {
                when (`is`.toolMaterialName) {
                    "WOOD" -> rating = 2f
                    "GOLD" -> rating = 2f
                    "STONE" -> rating = 3f
                    "IRON" -> rating = (if (checkForDamage) 4 else 40).toFloat()
                    "EMERALD" -> rating = (if (checkForDamage) 5 else 50).toFloat()
                }
            }

            is ItemAxe -> {
                when (`is`.toolMaterialName) {
                    "WOOD" -> rating = 3f
                    "GOLD" -> rating = 3f
                    "STONE" -> rating = 4f
                    "IRON" -> rating = 5f
                    "EMERALD" -> rating = 6f
                }
            }

            is ItemSpade -> {
                when (`is`.toolMaterialName) {
                    "WOOD" -> rating = 1f
                    "GOLD" -> rating = 1f
                    "STONE" -> rating = 2f
                    "IRON" -> rating = 3f
                    "EMERALD" -> rating = 4f
                }
            }
        }

        return rating
    }

    fun openInv(mode: String) {
        if (mode.equals("Spoof", ignoreCase = true)) {
            if (!isInventoryOpen && mc.currentScreen !is GuiInventory) {
                PacketUtils.sendPacketNoEvent(C16PacketClientStatus(OPEN_INVENTORY_ACHIEVEMENT))
                isInventoryOpen = true
            }
        }
    }

    fun closeInv(mode: String) {
        if (mode.equals("Spoof", ignoreCase = true)) {
            if (isInventoryOpen && mc.currentScreen !is GuiInventory) {
                PacketUtils.sendPacketNoEvent(C0DPacketCloseWindow(mc.thePlayer.inventoryContainer.windowId))
                for (bind in moveKeys) {
                    KeyBinding.setKeyBindState(bind.keyCode, Keyboard.isKeyDown(bind.keyCode))
                }
                isInventoryOpen = false
            }
        }
    }

    var moveKeys: Array<KeyBinding> = arrayOf(
        mc.gameSettings.keyBindForward,
        mc.gameSettings.keyBindBack,
        mc.gameSettings.keyBindLeft,
        mc.gameSettings.keyBindRight,
        mc.gameSettings.keyBindJump,
        mc.gameSettings.keyBindSneak
    )
}