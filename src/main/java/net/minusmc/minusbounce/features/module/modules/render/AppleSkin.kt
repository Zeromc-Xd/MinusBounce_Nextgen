package net.minusmc.minusbounce.features.module.modules.render

import net.minecraft.client.gui.Gui
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.init.Items
import net.minecraft.item.ItemFood
import net.minecraft.item.ItemStack
import net.minecraft.potion.Potion
import net.minecraft.util.EnumChatFormatting
import net.minecraft.util.FoodStats
import net.minecraftforge.common.MinecraftForge
import net.minusmc.minusbounce.event.EventTarget
import net.minusmc.minusbounce.event.EventTick
import net.minusmc.minusbounce.features.module.Module
import net.minusmc.minusbounce.features.module.ModuleCategory
import net.minusmc.minusbounce.features.module.ModuleInfo
import net.minusmc.minusbounce.injection.forge.mixins.accessors.FoodStatsAccessor
import net.minusmc.minusbounce.injection.forge.mixins.accessors.ItemFoodAccessor
import net.minusmc.minusbounce.value.BoolValue
import net.minusmc.minusbounce.value.FloatValue
import org.lwjgl.opengl.GL11
import java.util.Locale
import java.util.Random
import java.util.Vector
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

@ModuleInfo(name = "AppleSkin", description = "Shows food, saturation and exhaustion information.", category = ModuleCategory.RENDER)
object AppleSkin : Module() {
    val saturationValue = BoolValue("Saturation", true)
    val exhaustionValue = BoolValue("Exhaustion", true)
    val heldFoodValue = BoolValue("HeldFood", true)
    val tooltipValue = BoolValue("Tooltip", true)
    val debugValue = BoolValue("Debug", true)
    val rottenFoodValue = BoolValue("FoodEffect", true)
    val maxFlashAlphaValue = FloatValue("MaxFlashAlpha", 0.65F, 0F, 1F)
    private val foodBarOffsets = Vector<IntPoint>()
    private val random = Random()
    private var unclampedFlashAlpha = 0F
    private var flashAlpha = 0F
    private var alphaDir = 1
    private var forgeRegistered = false

    override fun onInitialize() {
        if (!forgeRegistered) {
            MinecraftForge.EVENT_BUS.register(AppleSkinForgeEvents.INSTANCE)
            forgeRegistered = true
        }
    }

    @EventTarget
    fun onTick(event: EventTick) {
        unclampedFlashAlpha += alphaDir * 0.125F
        if (unclampedFlashAlpha >= 1.5F) alphaDir = -1 else if (unclampedFlashAlpha <= -0.5F) alphaDir = 1
        flashAlpha = max(0F, min(1F, unclampedFlashAlpha)) * min(1F, maxFlashAlphaValue.get())
    }

    fun renderForgeFoodOverlay(width: Int, height: Int) {
        if (!state || mc.thePlayer == null || mc.theWorld == null || mc.playerController == null) return
        val stats = mc.thePlayer.foodStats ?: return
        val right = width / 2 + 91
        val top = height - 39
        mc.textureManager.bindTexture(Gui.icons)
        generateHungerBarOffsets(right, top, mc.ingameGUI.updateCounter)
        if (exhaustionValue.get()) drawExhaustionOverlay(getExhaustion(stats), right, top)
        if (saturationValue.get()) drawSaturationOverlay(0F, stats.saturationLevel, 0, stats.foodLevel, right, top, 1F)
        val heldItem = mc.thePlayer.heldItem
        val holdingFood = heldItem != null && heldItem.item is ItemFood
        if (!heldFoodValue.get() || !holdingFood) {
            resetFlash()
            return
        }
        val foodValues = getFoodValues(heldItem)
        val newFoodValue = stats.foodLevel + foodValues.hunger
        val newSaturationValue = stats.saturationLevel + foodValues.saturationIncrement()
        val saturationGained = if (newSaturationValue > newFoodValue) newFoodValue - stats.saturationLevel else foodValues.saturationIncrement()
        drawHungerOverlay(foodValues.hunger, stats.foodLevel, right, top, flashAlpha, isRottenFood(heldItem))
        if (saturationValue.get()) drawSaturationOverlay(saturationGained, stats.saturationLevel, foodValues.hunger, stats.foodLevel, right, top, flashAlpha)
    }

    fun appendTooltip(stack: ItemStack?, tooltip: MutableList<String>) {
        if (!state || !tooltipValue.get() || stack == null || stack.item !is ItemFood) return
        val foodValues = getFoodValues(stack)
        val saturation = foodValues.saturationIncrement()
        tooltip.add(EnumChatFormatting.GRAY.toString() + "Food: " + EnumChatFormatting.GREEN + "+" + foodValues.hunger + EnumChatFormatting.GRAY + " | Saturation: " + EnumChatFormatting.GOLD + "+" + format(saturation))
        if (rottenFoodValue.get() && isRottenFood(stack)) tooltip.add(EnumChatFormatting.RED.toString() + "Negative food effect")
    }

    fun appendDebugLines(list: MutableList<String>) {
        if (!state || !debugValue.get() || mc.thePlayer == null) return
        val stats = mc.thePlayer.foodStats ?: return
        list.add("")
        list.add("AppleSkin")
        list.add("Food: ${stats.foodLevel}/20")
        list.add("Saturation: ${format(stats.saturationLevel)}/20")
        list.add("Exhaustion: ${format(getExhaustion(stats))}/4")
        val heldItem = mc.thePlayer.heldItem
        if (heldItem != null && heldItem.item is ItemFood) {
            val values = getFoodValues(heldItem)
            list.add("Held food: +${values.hunger} / +${format(values.saturationIncrement())}")
        }
    }

    private fun generateHungerBarOffsets(right: Int, top: Int, ticks: Int) {
        random.setSeed(ticks * 312871L)
        val preferFoodBars = 10
        val stats: FoodStats = mc.thePlayer.foodStats
        val foodLevel = max(1, stats.foodLevel)
        val shouldAnimatedFood = stats.saturationLevel <= 0F && mc.ingameGUI.updateCounter % (foodLevel * 3 + 1) == 0
        if (foodBarOffsets.size != preferFoodBars) foodBarOffsets.setSize(preferFoodBars)
        for (i in 0 until preferFoodBars) {
            val x = right - i * 8 - 9
            var y = top
            if (shouldAnimatedFood) y += random.nextInt(3) - 1
            var point = foodBarOffsets[i]
            if (point == null) {
                point = IntPoint()
                foodBarOffsets[i] = point
            }
            point.x = x - right
            point.y = y - top
        }
    }

    private fun drawExhaustionOverlay(exhaustion: Float, right: Int, top: Int) {
        if (exhaustion <= 0F) return
        val amount = (exhaustion / 4F).coerceIn(0F, 1F)
        val left = right - 10 * 8 - 1
        val y = top + 10
        GlStateManager.disableTexture2D()
        GlStateManager.enableBlend()
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
        Gui.drawRect(left, y, right, y + 2, 0x55000000)
        Gui.drawRect(left, y, left + ((right - left) * amount).toInt(), y + 2, 0xAAFFB000.toInt())
        GlStateManager.disableBlend()
        GlStateManager.enableTexture2D()
        GlStateManager.color(1F, 1F, 1F, 1F)
    }

    private fun drawSaturationOverlay(saturationGained: Float, saturationLevel: Float, hungerRestored: Int, foodLevel: Int, right: Int, top: Int, alpha: Float) {
        if (saturationLevel + saturationGained < 0F) return
        GlStateManager.enableBlend()
        GlStateManager.color(1F, 1F, 1F, alpha)
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
        val modifiedSaturation = max(0F, min(20F, saturationLevel + saturationGained))
        val modifiedFood = max(0, min(20, foodLevel + hungerRestored))
        var startSaturationBar = 0
        val endSaturationBar = ceil((modifiedSaturation / 2F).toDouble()).toInt()
        if (saturationGained != 0F) startSaturationBar = max(saturationLevel / 2F, 0F).toInt()
        val iconStartOffset = 16
        val iconSize = 9
        for (i in startSaturationBar until endSaturationBar) {
            val offset = foodBarOffsets.getOrNull(i) ?: continue
            val x = right + offset.x
            val y = top + offset.y
            val v = 3 * iconSize
            var u = iconStartOffset + 4 * iconSize
            var ub = iconStartOffset + iconSize
            for (effect in mc.thePlayer.activePotionEffects) {
                if (effect.potionID == Potion.hunger.id) {
                    u += 4 * iconSize
                    break
                }
            }
            var ubX = x
            var ubIconSize = iconSize
            if (i * 2 + 1 == modifiedSaturation.toInt()) {
                val halfIconSize = iconSize / 2
                ubX += halfIconSize
                ub += halfIconSize
                ubIconSize -= halfIconSize
            }
            if (i * 2 + 1 == modifiedFood) u += iconSize
            GlStateManager.color(0.75F, 0.65F, 0F, alpha)
            mc.ingameGUI.drawTexturedModalRect(ubX, y, ub, v, ubIconSize, iconSize)
            if (modifiedSaturation > modifiedFood) continue
            GlStateManager.color(1F, 1F, 1F, alpha)
            mc.ingameGUI.drawTexturedModalRect(x, y, u, v, iconSize, iconSize)
        }
        GlStateManager.disableBlend()
        GlStateManager.color(1F, 1F, 1F, 1F)
    }

    private fun drawHungerOverlay(hungerRestored: Int, foodLevel: Int, right: Int, top: Int, alpha: Float, useRottenTextures: Boolean) {
        if (hungerRestored <= 0) return
        GlStateManager.enableBlend()
        GlStateManager.color(1F, 1F, 1F, alpha)
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
        val modifiedFood = max(0, min(20, foodLevel + hungerRestored))
        val startFoodBars = max(0, foodLevel / 2)
        val endFoodBars = ceil((modifiedFood / 2F).toDouble()).toInt()
        val iconStartOffset = 16
        val iconSize = 9
        for (i in startFoodBars until endFoodBars) {
            val offset = foodBarOffsets.getOrNull(i) ?: continue
            val x = right + offset.x
            val y = top + offset.y
            val v = 3 * iconSize
            var u = iconStartOffset + 4 * iconSize
            var ub = iconStartOffset + iconSize
            if (useRottenTextures) {
                u += 4 * iconSize
                ub += 12 * iconSize
            }
            if (i * 2 + 1 == modifiedFood) u += iconSize
            GlStateManager.color(1F, 1F, 1F, alpha * 0.25F)
            mc.ingameGUI.drawTexturedModalRect(x, y, ub, v, iconSize, iconSize)
            GlStateManager.color(1F, 1F, 1F, alpha)
            mc.ingameGUI.drawTexturedModalRect(x, y, u, v, iconSize, iconSize)
        }
        GlStateManager.disableBlend()
        GlStateManager.color(1F, 1F, 1F, 1F)
    }

    private fun getFoodValues(stack: ItemStack): FoodValues {
        val food = stack.item as? ItemFood
        return FoodValues(food?.getHealAmount(stack) ?: 0, food?.getSaturationModifier(stack) ?: 0F)
    }

    private fun isRottenFood(stack: ItemStack): Boolean {
        val food = stack.item as? ItemFood ?: return false
        return if ((food as ItemFoodAccessor).potionId > 0) {
            val potion = Potion.potionTypes[(food as ItemFoodAccessor).potionId]
            potion != null && potion.isBadEffect
        } else {
            val item = stack.item
            item === Items.rotten_flesh || item === Items.poisonous_potato || item === Items.spider_eye || item === Items.fish && stack.metadata == 3
        }
    }

    private fun getExhaustion(stats: FoodStats): Float {
        return try {
            (stats as FoodStatsAccessor).foodExhaustionLevel
        } catch (_: Throwable) {
            0F
        }
    }

    private fun format(value: Float): String = String.format(Locale.US, "%.1f", value)

    private fun resetFlash() {
        unclampedFlashAlpha = 0F
        flashAlpha = 0F
        alphaDir = 1
    }

    private class IntPoint(var x: Int = 0, var y: Int = 0)
    private class FoodValues(val hunger: Int, val saturationModifier: Float) {
        fun saturationIncrement() = hunger * saturationModifier * 2F
    }
}
