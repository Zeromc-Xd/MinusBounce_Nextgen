package net.minusmc.minusbounce.ui.client.clickgui.rise

import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.client.renderer.GlStateManager
import net.minusmc.minusbounce.MinusBounce
import net.minusmc.minusbounce.features.module.Module
import net.minusmc.minusbounce.features.module.ModuleCategory
import net.minusmc.minusbounce.features.module.modules.client.ClickGUI
import net.minusmc.minusbounce.ui.client.clickgui.rise.render.RiseAlphaShader
import net.minusmc.minusbounce.ui.client.clickgui.rise.render.RiseAnimation
import net.minusmc.minusbounce.ui.client.clickgui.rise.render.RiseColors
import net.minusmc.minusbounce.ui.client.clickgui.rise.render.RiseEasing
import net.minusmc.minusbounce.ui.client.clickgui.rise.render.RiseFonts
import net.minusmc.minusbounce.ui.client.clickgui.rise.render.RiseLayer
import net.minusmc.minusbounce.ui.client.clickgui.rise.render.RiseRenderUtil
import net.minusmc.minusbounce.ui.font.GameFontRenderer
import net.minusmc.minusbounce.utils.ClientUtils
import net.minusmc.minusbounce.value.ListValue
import net.minusmc.minusbounce.value.TextValue
import net.minusmc.minusbounce.value.Value
import net.minusmc.minusbounce.value.FontValue
import net.minusmc.minusbounce.value.BoolValue
import net.minusmc.minusbounce.value.FloatRangeValue
import net.minusmc.minusbounce.value.FloatValue
import net.minusmc.minusbounce.value.BlockValue
import net.minusmc.minusbounce.value.IntRangeValue
import net.minusmc.minusbounce.value.IntegerValue
import org.lwjgl.input.Keyboard
import org.lwjgl.input.Mouse
import org.lwjgl.opengl.GL11
import java.awt.Color
import java.io.IOException
import java.text.Collator
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class RiseClickGui : GuiScreen() {
    private var x = -1f
    private var y = -1f
    private val widthBase = 400f
    private val heightBase = 300f
    private val sidebarWidth = 100f
    private val moduleWidth = 283f
    private val moduleDefaultHeight = 38f
    private val round = 12f

    private var selectedCategory: ModuleCategory? = Persistent.selectedCategory
    private var lastCategory: ModuleCategory? = Persistent.lastCategory
    private var dragging = false
    private var dragX = 0f
    private var dragY = 0f
    private var search = Persistent.search
    private var searchFocused = Persistent.searchFocused
    private var scroll = Persistent.scroll
    private var scrollTarget = Persistent.scrollTarget
    private var sidebarOffset = 0.0
    private var sidebarOpacity = 255.0
    private var transitionOpacity = 0.0
    private var transitionStartedAt = 0L
    private var transitionScroll = 0.0
    private var lastTime = System.currentTimeMillis()

    private val scaleAnimation = RiseAnimation(RiseEasing.EASE_OUT_EXPO, 300)
    private val opacityAnimation = RiseAnimation(RiseEasing.EASE_OUT_EXPO, 300)
    private val bloomLayer = RiseLayer(12f)
    private val collator = Collator.getInstance()
    private val modules = linkedMapOf<Module, ModuleState>()
    private val categories = hashMapOf<String, CategoryState>()
    private val valueSlides = hashMapOf<Any, Double>()

    private var activeSlider: Value<*>? = null
    private var activeRangeSide = 0
    private var mouseX = 0
    private var mouseY = 0

    private val guiScale get() = ClickGUI.scaleValue.get().coerceIn(0.5f, 2.0f)
    private val fastEffects get() = ClickGUI.fastRenderValue.get()
    private val scaledWidth get() = widthBase * guiScale
    private val scaledHeight get() = heightBase * guiScale
    private var cachedModuleKey = ""
    private var cachedModuleList = emptyList<Module>()

    override fun initGui() {
        ClientUtils.disableFastRender()
        val sr = ScaledResolution(mc)
        if (x < 0f && Persistent.x >= 0f) x = Persistent.x
        if (y < 0f && Persistent.y >= 0f) y = Persistent.y
        if (x < 0f || y < 0f || x + scaledWidth > sr.scaledWidth || y + scaledHeight > sr.scaledHeight) {
            x = sr.scaledWidth / 2f - scaledWidth / 2f
            y = sr.scaledHeight / 2f - scaledHeight / 2f
        }
        scaleAnimation.reset(0.0)
        opacityAnimation.reset(0.0)
        transitionOpacity = 0.0
        transitionStartedAt = 0L
        transitionScroll = scroll
        sidebarOffset = 0.0
        sidebarOpacity = 255.0
        clampPosition()
        lastTime = System.currentTimeMillis()
        Keyboard.enableRepeatEvents(true)
        rebuildModuleCache()
    }

    override fun onGuiClosed() {
        persistState()
        MinusBounce.fileManager.saveConfig(MinusBounce.fileManager.valuesConfig)
        MinusBounce.fileManager.saveConfig(MinusBounce.fileManager.clickGuiConfig)
        Keyboard.enableRepeatEvents(false)
        dragging = false
        activeSlider = null
        activeRangeSide = 0
        searchFocused = false
        GL11.glDisable(GL11.GL_SCISSOR_TEST)
        RiseRenderUtil.clearScaleTransform()
        GlStateManager.resetColor()
    }

    override fun doesGuiPauseGame() = false

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        this.mouseX = logicalMouseX(mouseX)
        this.mouseY = logicalMouseY(mouseY)
        render(this.mouseX, this.mouseY, partialTicks)
        super.drawScreen(mouseX, mouseY, partialTicks)
    }

    private fun render(mouseX: Int, mouseY: Int, partialTicks: Float) {
        updateAnimations(mouseX, mouseY)
        val animationTime = scaleAnimation.getValue().coerceAtLeast(0.01)
        val opacity = opacityAnimation.getValue()
        val tx = (x + widthBase / 2f) * (1.0 - animationTime)
        val ty = (y + heightBase / 2f) * (1.0 - animationTime)

        GlStateManager.pushMatrix()
        try {
        val interfaceScale = guiScale
        RiseRenderUtil.setScaleTransform(x.toDouble(), y.toDouble(), interfaceScale.toDouble(), animationTime, tx, ty)
        if (interfaceScale != 1f) {
            GlStateManager.translate(x, y, 0f)
            GlStateManager.scale(interfaceScale, interfaceScale, 1f)
            GlStateManager.translate(-x, -y, 0f)
        }
        if (animationTime < 0.999) {
            GlStateManager.translate(tx.toFloat(), ty.toFloat(), 0f)
            GlStateManager.scale(animationTime.toFloat(), animationTime.toFloat(), 1f)
        }

        if (animationTime > 0.993) {
            if (fastEffects) {
                RiseRenderUtil.fastShadow(x.toDouble(), y.toDouble(), widthBase.toDouble(), heightBase.toDouble(), round.toDouble())
            } else {
                RiseRenderUtil.dropShadow(18f, x.toDouble(), y.toDouble(), widthBase.toDouble(), heightBase.toDouble(), 30, (round * 1.3f).toDouble())
            }
        }

        if (!fastEffects) {
            bloomLayer.clear()
            bloomLayer.add { renderBloom() }
            if (opacity > 0.2) {
                applyFrameScissor()
                bloomLayer.run()
                GL11.glDisable(GL11.GL_SCISSOR_TEST)
            }
        } else if (opacity > 0.2) {
            applyFrameScissor()
            renderFastBloom()
            GL11.glDisable(GL11.GL_SCISSOR_TEST)
        }

        RiseAlphaShader.alpha = opacity.toFloat()
        RiseAlphaShader.run { RiseRenderUtil.roundedRectangle(x, y, widthBase, heightBase, round, RiseColors.withAlpha(RiseColors.background, (254 * opacity).roundToInt())) }

        applyFrameScissor()

        renderSelectedScreen(mouseX, mouseY, partialTicks)
        renderTransitionOverlay(opacity)
        renderSidebar(mouseX, mouseY)

        GL11.glDisable(GL11.GL_SCISSOR_TEST)
        RiseRenderUtil.clearScaleTransform()
        } finally {
            GL11.glDisable(GL11.GL_SCISSOR_TEST)
            RiseRenderUtil.clearScaleTransform()
            GlStateManager.resetColor()
            GlStateManager.popMatrix()
        }
    }

    private fun updateAnimations(mouseX: Int, mouseY: Int) {
        val now = System.currentTimeMillis()
        val delta = (now - lastTime).coerceAtLeast(1L).coerceAtMost(60L)
        lastTime = now

        scaleAnimation.run(1.0)
        opacityAnimation.run(1.0)
        scroll += (scrollTarget - scroll) * min(1.0, delta / 70.0)

        if (dragging) {
            x = mouseX + dragX
            y = mouseY + dragY
        }

        sidebarOpacity = 255.0
        sidebarOffset = 0.0
        val transitionElapsed = transitionElapsed()
        transitionOpacity = when {
            transitionElapsed < 0L || transitionElapsed > 400L -> 0.0
            transitionElapsed <= 200L -> transitionElapsed * (255.0 / 200.0)
            else -> (400L - transitionElapsed) * (255.0 / 200.0)
        }.coerceIn(0.0, 255.0)
        if (transitionElapsed > 400L) transitionStartedAt = 0L
        clampPosition()
        persistPosition()
    }

    private fun renderBloom() {
        val accent = RiseColors.accent(0.0, y / 5.0)
        RiseRenderUtil.roundedRectangle(x.toDouble(), y.toDouble(), widthBase.toDouble(), heightBase.toDouble(), round.toDouble(), RiseColors.withAlpha(accent, 70))
        RiseRenderUtil.leftRoundedRectangle(x.toDouble(), y.toDouble(), (sidebarWidth.toDouble() + sidebarOffset).coerceAtLeast(0.0), heightBase.toDouble(), round.toDouble(), RiseColors.withAlpha(RiseColors.secondary, sidebarOpacity.roundToInt()))
        categoryEntries().forEachIndexed { index, item ->
            val state = categories.getOrPut(item.key) { CategoryState() }
            if (state.selector > 1.0) {
                val cy = y + 10f + (index + 1) * 19.5f + 16f
                val textW = categoryWidth(item.name)
                RiseRenderUtil.roundedRectangle((x.toDouble() + 9.0), (cy - 5.5f).toDouble(), textW + 14.0, 15.0, 7.5, RiseColors.withAlpha(RiseColors.accent(0.0, cy / 5.0), min(state.selector.roundToInt(), sidebarOpacity.roundToInt())).darker())
            }
        }
    }

    private fun renderFastBloom() {
        val accent = RiseColors.accent(0.0, y / 5.0)
        RiseRenderUtil.roundedRectangle(x.toDouble(), y.toDouble(), widthBase.toDouble(), heightBase.toDouble(), round.toDouble(), RiseColors.withAlpha(accent, 18))
        RiseRenderUtil.leftRoundedRectangle(x.toDouble(), y.toDouble(), sidebarWidth.toDouble(), heightBase.toDouble(), round.toDouble(), RiseColors.withAlpha(RiseColors.secondary, 210))
        categoryEntries().forEachIndexed { index, item ->
            val state = categories.getOrPut(item.key) { CategoryState() }
            if (state.selector > 8.0) {
                val cy = y + 10f + (index + 1) * 19.5f + 16f
                val textW = categoryWidth(item.name)
                RiseRenderUtil.roundedRectangle((x.toDouble() + 9.0), (cy - 5.5f).toDouble(), textW + 14.0, 15.0, 7.5, RiseColors.withAlpha(RiseColors.accent(0.0, cy / 5.0), min(state.selector.roundToInt(), 100)).darker())
            }
        }
    }

    private fun renderSidebar(mouseX: Int, mouseY: Int) {
        val sidebarRealWidth = sidebarWidth.toDouble() + sidebarOffset
        RiseRenderUtil.leftRoundedRectangle(x.toDouble(), y.toDouble(), sidebarRealWidth.coerceAtLeast(0.0), heightBase.toDouble(), round.toDouble(), RiseColors.withAlpha(RiseColors.secondary, sidebarOpacity.roundToInt()))
        RiseRenderUtil.horizontalGradient(x + sidebarRealWidth, y.toDouble(), 30.0, heightBase.toDouble(), Color(0, 0, 0, min(sidebarOpacity.roundToInt() / 7, 36)), Color(0, 0, 0, 0))

        if (!fastEffects) {
            for (i in 0..8) {
                val radius = i * 50.0
                RiseRenderUtil.circle(x.toDouble() + sidebarWidth.toDouble() - radius / 2.0, y.toDouble() + heightBase.toDouble() / 2.0 - radius / 2.0, radius, RiseColors.withAlpha(RiseColors.first, 1))
            }
        }

        var offset = 10.0
        categoryEntries().forEach { item ->
            offset += 19.5
            renderCategory(item, offset, sidebarRealWidth, mouseX, mouseY)
        }

        renderTitleBlock()
    }

    private fun renderTitleBlock() {
        val name = MinusBounce.CLIENT_NAME
        val version = MinusBounce.CLIENT_VERSION.toString()
        val titleFont = fontTitle()
        val versionFont = fontSmall()
        val available = sidebarWidth - 18f
        val gap = 2f
        val inlineWidth = titleFont.width(name) + gap + versionFont.width(version)
        val baseY = y + ((19.5f + 30f) / 2f - titleFont.fontHeight() / 2f) + 2f
        val alpha = sidebarOpacity.roundToInt()
        val nameColor = RiseColors.withAlpha(Color.WHITE, alpha)
        val versionColor = RiseColors.withAlpha(RiseColors.first, min(200, alpha))
        val nameGlow = RiseColors.withAlpha(RiseColors.first, min(120, alpha))
        val versionGlow = RiseColors.withAlpha(RiseColors.first, min(80, alpha))

        if (inlineWidth <= available) {
            val start = x + (sidebarWidth - inlineWidth) / 2f
            drawGlowText(titleFont, name, start, baseY, nameColor, nameGlow)
            drawGlowText(versionFont, version, start + titleFont.width(name) + gap, baseY - 2f, versionColor, versionGlow)
        } else {
            val titleX = x + (sidebarWidth - titleFont.width(name)) / 2f
            val versionX = x + (sidebarWidth - versionFont.width(version)) / 2f
            drawGlowText(titleFont, name, titleX, baseY - 2f, nameColor, nameGlow)
            drawGlowText(versionFont, version, versionX, baseY + titleFont.fontHeight() - 3f, versionColor, versionGlow)
        }
    }

    private fun renderCategory(item: CategoryItem, offset: Double, sidebarRealWidth: Double, mouseX: Int, mouseY: Int) {
        val state = categories.getOrPut(item.key) { CategoryState() }
        val selected = selectedCategory == item.category
        state.selector += ((if (selected) 255.0 else 0.0) - state.selector) * 0.2
        val px = (x.toDouble() + 9.0).toFloat()
        val py = (y + offset).toFloat() + 16f
        val accent = RiseColors.accent(0.0, py / 5.0)
        val textX = px + 7f
        val textColor = RiseColors.withAlpha(Color.WHITE, min(if (selected) 255 else 200, sidebarOpacity.roundToInt()))

        if (state.selector > 0.5) {
            RiseRenderUtil.roundedRectangle(px.toDouble(), (py - 5.5f).toDouble(), categoryWidth(item.name) + 14.0, 15.0, 7.5, RiseColors.withAlpha(accent, min(state.selector.roundToInt(), sidebarOpacity.roundToInt())).darker())
        }

        drawGlowText(fontCategory(), item.name, textX, py, textColor, RiseColors.withAlpha(accent, if (selected) 95 else 14))
    }

    private fun renderSelectedScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        val showingPrevious = transitionStartedAt != 0L && transitionElapsed() < 200L
        val renderedCategory = if (showingPrevious) lastCategory else selectedCategory
        val renderedScroll = if (showingPrevious) transitionScroll else scroll
        val searchScreen = renderedCategory == null
        val modules = modulesForScreen(renderedCategory)
        val topClip = contentTop(searchScreen)
        val bottomClip = contentBottom()
        val startY = if (searchScreen) y.toDouble() + 35.0 + renderedScroll else y.toDouble() + 7.0 + renderedScroll

        if (searchScreen) renderSearchBar(renderedScroll)

        applyContentScissor(searchScreen)

        var yy = startY
        var height = 0.0
        val visible = arrayListOf<ModuleState>()
        modules.forEach { module ->
            val state = moduleState(module)
            val target = moduleHeight(module)
            state.opening.duration = (fullModuleHeight(module).toLong() * 3L).coerceIn(1L, 450L)
            state.height = state.opening.run(target)
            state.x = x + sidebarWidth + 8f
            state.y = yy.toFloat()
            if (yy + state.height > topClip && yy < bottomClip) {
                drawModule(state, mouseX, mouseY, partialTicks, searchScreen)
                visible += state
            }
            yy += state.height + 7.0
            height += state.height + 7.0
        }
        lastVisible = visible

        if (!showingPrevious) {
            scrollLimit(height, searchScreen)
            drawScrollbar(height, searchScreen)
        }
        applyFrameScissor()
    }

    private fun renderTransitionOverlay(opacity: Double) {
        if (transitionOpacity <= 0.5) return
        val alpha = (transitionOpacity * opacity).roundToInt().coerceIn(0, 255)
        RiseRenderUtil.roundedRectangle(x, y, widthBase, heightBase, round, RiseColors.withAlpha(RiseColors.background, alpha))
    }

    private fun transitionElapsed() = if (transitionStartedAt == 0L) -1L else System.currentTimeMillis() - transitionStartedAt

    private var lastVisible = listOf<ModuleState>()
    private fun visibleModules() = lastVisible

    private fun renderSearchBar(renderedScroll: Double) {
        val alpha = if (renderedScroll < 0) (255 + renderedScroll * 6).roundToInt().coerceIn(0, 255) else 255
        val label = if (search.isEmpty()) "Search" else search
        val cx = x + sidebarWidth + (widthBase - sidebarWidth) / 2f
        val sy = y + 17f + renderedScroll.toFloat()
        val color = if (search.isEmpty()) RiseColors.withAlpha(RiseColors.text, (alpha * 0.75).roundToInt()) else RiseColors.withAlpha(RiseColors.text, alpha)
        drawCenteredGlowText(fontSearch(), label, cx, sy, color, RiseColors.withAlpha(RiseColors.first, 80))
    }

    private fun drawModule(state: ModuleState, mouseX: Int, mouseY: Int, partialTicks: Float, searchScreen: Boolean) {
        val module = state.module
        val overModule = inside(mouseX, mouseY, state.x, state.y, moduleWidth, moduleDefaultHeight - 3f)
        state.hover += ((if (overModule) if (state.mouseDown) 35.0 else 20.0 else 0.0) - state.hover) * 0.5

        RiseRenderUtil.roundedRectangle(state.x.toDouble(), state.y.toDouble(), moduleWidth.toDouble(), state.height, 6.0, RiseColors.overlay)
        if (module.state) RiseRenderUtil.roundedRectangle(state.x.toDouble(), state.y.toDouble(), moduleWidth.toDouble(), state.height, 6.0, RiseColors.withAlpha(RiseColors.accent(0.0, state.y / 5.0), 16))
        if (state.hover > 0.5) RiseRenderUtil.roundedRectangle(state.x.toDouble(), state.y.toDouble(), moduleWidth.toDouble(), state.height, 6.0, Color(0, 0, 0, state.hover.roundToInt()))

        val fontColor = RiseColors.withAlpha(RiseColors.text, if (module.state) 255 else 200)
        val nameColor = if (module.state) RiseColors.accent(0.0, state.y / 5.0) else fontColor
        if (searchScreen) {
            fontSmall().drawString("(${module.category.displayName})", state.x + fontName().width(module.name) + 10f, state.y + 10f, RiseColors.withAlpha(fontColor, 64).rgb)
        }
        drawGlowText(fontName(), module.name, state.x + 6f, state.y + 8f, nameColor, RiseColors.withAlpha(RiseColors.accent(0.0, state.y / 5.0), if (module.state) 100 else 18))
        fontDescription().drawString(module.description.take(62), state.x + 6f, state.y + 25f, RiseColors.withAlpha(fontColor, 70).rgb)

        if (state.height > moduleDefaultHeight + 1.0) {
            val clipTop = max(state.y.toDouble(), contentTop(searchScreen))
            val clipBottom = min(state.y.toDouble() + state.height, contentBottom())
            if (clipBottom > clipTop) {
                GL11.glEnable(GL11.GL_SCISSOR_TEST)
                RiseRenderUtil.scissor(state.x.toDouble(), clipTop, moduleWidth.toDouble(), clipBottom - clipTop)
                var valueY = state.y + moduleDefaultHeight + 1f
                module.values.filter { it.canDisplay() }.forEach { value ->
                    val vh = valueHeight(value)
                    if (valueY + vh > clipTop && valueY < clipBottom) {
                        drawValue(value, state.x + 6f, valueY, moduleWidth - 12f, mouseX, mouseY, state.settingOpacity.roundToInt().coerceIn(0, 255))
                    }
                    valueY += vh
                }
                applyContentScissor(searchScreen)
            }
        }

        val targetOpacity = if (state.expanded) 255.0 else 0.0
        state.settings.duration = (state.opening.duration / if (state.expanded) 2L else 3L).coerceAtLeast(1L)
        state.settingOpacity = state.settings.run(targetOpacity)
    }

    private fun drawValue(value: Value<*>, x: Float, y: Float, width: Float, mouseX: Int, mouseY: Int, opacity: Int) {
        if (opacity <= 0) return
        when (value) {
            is BoolValue -> drawBoolValue(value, x, y, opacity)
            is ListValue -> drawListValue(value, x, y, opacity)
            is IntegerValue -> drawNumberValue(value, x, y, value.get().toDouble(), value.minimum.toDouble(), value.maximum.toDouble(), mouseX, opacity)
            is FloatValue -> drawNumberValue(value, x, y, value.get().toDouble(), value.minimum.toDouble(), value.maximum.toDouble(), mouseX, opacity)
            is IntRangeValue -> drawBoundsValue(value, x, y, value.getMinValue().toDouble(), value.getMaxValue().toDouble(), value.minimum.toDouble(), value.maximum.toDouble(), mouseX, opacity)
            is FloatRangeValue -> drawBoundsValue(value, x, y, value.getMinValue().toDouble(), value.getMaxValue().toDouble(), value.minimum.toDouble(), value.maximum.toDouble(), mouseX, opacity)
            is TextValue -> drawTextValue(value, x, y, opacity)
            is FontValue -> drawFontValue(value, x, y, opacity)
            else -> fontSmall().drawString(value.name, x, y, RiseColors.withAlpha(RiseColors.secondaryText, opacity).rgb)
        }
    }

    private fun drawBoolValue(value: BoolValue, x: Float, y: Float, opacity: Int) {
        val prefix = value.name + ": "
        val boolText = value.get().toString()
        fontSmall().drawString(prefix, x, y, RiseColors.withAlpha(RiseColors.secondaryText, opacity).rgb)
        val valueColor = if (value.get()) RiseColors.withAlpha(RiseColors.first, opacity) else RiseColors.withAlpha(RiseColors.secondaryText, opacity)
        fontSmall().drawString(boolText, x + fontSmall().width(prefix), y, valueColor.rgb)
    }

    private fun drawListValue(value: ListValue, x: Float, y: Float, opacity: Int) {
        val prefix = value.name + ": "
        fontSmall().drawString(prefix, x, y, RiseColors.withAlpha(RiseColors.secondaryText, opacity).rgb)
        fontSmall().drawString(value.get(), x + fontSmall().width(prefix), y, RiseColors.withAlpha(RiseColors.first, opacity).rgb)
    }

    private fun drawTextValue(value: TextValue, x: Float, y: Float, opacity: Int) {
        val prefix = value.name + ": "
        fontSmall().drawString(prefix, x, y, RiseColors.withAlpha(RiseColors.secondaryText, opacity).rgb)
        fontSmall().drawString(value.get(), x + fontSmall().width(prefix), y, RiseColors.withAlpha(RiseColors.secondaryText, opacity).rgb)
    }

    private fun drawFontValue(value: FontValue, x: Float, y: Float, opacity: Int) {
        val prefix = value.name + ": "
        val current = value.values.firstOrNull { it == value.get() }
        val name = current?.let { value.values.indexOf(it).takeIf { index -> index >= 0 }?.let { idx -> net.minusmc.minusbounce.utils.FontUtils.getAllFontDetails()[idx].first } } ?: net.minusmc.minusbounce.ui.font.Fonts.getFontDetails(value.get())?.let { "${it[0]} ${it[1]}" } ?: "Default"
        fontSmall().drawString(prefix, x, y, RiseColors.withAlpha(RiseColors.secondaryText, opacity).rgb)
        fontSmall().drawString(name, x + fontSmall().width(prefix), y, RiseColors.withAlpha(RiseColors.first, opacity).rgb)
    }

    private fun drawNumberValue(value: Value<*>, x: Float, y: Float, current: Double, minValue: Double, maxValue: Double, mouseX: Int, opacity: Int) {
        val sliderWidth = 100.0
        val valueWidth = fontSmall().width(value.name) + 7.0
        val sx = x.toDouble() + valueWidth
        val percentage = if (maxValue == minValue) 0.0 else ((current - minValue) / (maxValue - minValue)).coerceIn(0.0, 1.0)
        val rendered = animateValue(value, percentage, 30.0)

        if (activeSlider === value && activeRangeSide == 0) setSliderValue(value, mouseX, sx, sliderWidth)

        fontSmall().drawString(value.name, x, y, RiseColors.withAlpha(RiseColors.secondaryText, opacity).rgb)
        val display = formatNumber(current)
        fontSmall().drawString(display, (sx + 105.0).toFloat(), y, RiseColors.withAlpha(RiseColors.secondaryText, opacity).rgb)

        RiseRenderUtil.roundedRectangle(sx, y.toDouble() + 1.5, sliderWidth, 2.0, 1.0, RiseColors.withAlpha(RiseColors.background, min(opacity, RiseColors.background.alpha)))
        RiseRenderUtil.roundedRectangle(sx, y.toDouble() + 1.5, sliderWidth * rendered, 2.0, 1.0, RiseColors.withAlpha(RiseColors.first, min(70, opacity)))
        RiseRenderUtil.roundedRectangle(sx + rendered * sliderWidth - 2.5, y.toDouble(), 5.0, 5.0, 2.5, RiseColors.withAlpha(RiseColors.first, opacity))
    }

    private fun drawBoundsValue(value: Value<*>, x: Float, y: Float, first: Double, second: Double, minValue: Double, maxValue: Double, mouseX: Int, opacity: Int) {
        val sliderWidth = 100.0
        val grabberWidth = 5.0
        val valueWidth = fontSmall().width(value.name) + 7.0
        val sx = x.toDouble() + valueWidth
        val p1 = if (maxValue == minValue) 0.0 else ((first - minValue) / (maxValue - minValue)).coerceIn(0.0, 1.0)
        val p2 = if (maxValue == minValue) 0.0 else ((second - minValue) / (maxValue - minValue)).coerceIn(0.0, 1.0)
        var r1 = animateValue(value, p1, 30.0)
        var r2 = animateValue(RangeKey(value), p2, 30.0)

        if (activeSlider === value && activeRangeSide != 0) setRangeValue(value, mouseX, sx, sliderWidth)

        val same = abs(r1 - r2) < 0.01
        if (same) {
            r1 = (r1 - 0.025).coerceIn(0.0, 1.0)
            r2 = (r2 + 0.025).coerceIn(0.0, 1.0)
        }

        fontSmall().drawString(value.name, x, y, RiseColors.withAlpha(RiseColors.secondaryText, opacity).rgb)
        val display = if (value is IntRangeValue) "${first.roundToInt()} ${second.roundToInt()}" else "${formatNumber(first)} ${formatNumber(second)}"
        fontSmall().drawString(display, (sx + 105.0).toFloat(), y, RiseColors.withAlpha(RiseColors.secondaryText, opacity).rgb)

        RiseRenderUtil.roundedRectangle(sx, y.toDouble() + 1.5, sliderWidth, 2.0, 1.0, RiseColors.withAlpha(RiseColors.background, min(opacity, RiseColors.background.alpha)))
        val start = sx + min(r1, r2) * sliderWidth
        val end = sx + max(r1, r2) * sliderWidth
        RiseRenderUtil.roundedRectangle(start, y.toDouble() + 1.5, (end - start).coerceAtLeast(if (same) 5.0 else 0.0), 2.0, 1.0, RiseColors.withAlpha(RiseColors.first, min(70, opacity)))
        RiseRenderUtil.roundedRectangle(start - grabberWidth / 2.0, y.toDouble(), grabberWidth, grabberWidth, grabberWidth / 2.0, RiseColors.withAlpha(RiseColors.first, opacity))
        RiseRenderUtil.roundedRectangle(end - grabberWidth / 2.0, y.toDouble(), grabberWidth, grabberWidth, grabberWidth / 2.0, RiseColors.withAlpha(RiseColors.first, opacity))
    }

    override fun handleMouseInput() {
        super.handleMouseInput()
        val wheel = Mouse.getEventDWheel()
        if (wheel != 0 && inside(mouseX, mouseY, x, y, widthBase, heightBase)) {
            scrollTarget += if (wheel > 0) 28.0 else -28.0
            persistState()
        }
    }

    @Throws(IOException::class)
    override fun mouseClicked(mouseX: Int, mouseY: Int, mouseButton: Int) {
        val mx = logicalMouseX(mouseX)
        val my = logicalMouseY(mouseY)
        if (inside(mx, my, x, y, widthBase, 15f)) {
            dragging = true
            dragX = x - mx
            dragY = y - my
            return
        }
        if (!inside(mx, my, x, y, widthBase, heightBase)) return

        var offset = 10.0
        categoryEntries().forEach { item ->
            offset += 19.5
            val cy = y + offset.toFloat() + 16f
            val cx = (x.toDouble() + 9.0).toFloat()
            if (mouseButton == 0 && inside(mx, my, cx, cy - 5f, (categoryWidth(item.name) + 14.0).toFloat(), 15f)) {
                switchCategory(item.category)
                return
            }
        }

        val searchScreen = selectedCategory == null
        if (searchScreen && inside(mx, my, x + sidebarWidth + 25f, y + 5f, widthBase - sidebarWidth - 50f, 28f)) {
            searchFocused = true
            return
        }

        modulesForScreen().forEach { module ->
            val state = moduleState(module)
            if (inside(mx, my, state.x, state.y, moduleWidth, state.height.toFloat())) {
                if (inside(mx, my, state.x, state.y, moduleWidth, moduleDefaultHeight - 3f)) {
                    state.mouseDown = true
                    if (mouseButton == 0) module.toggle()
                    if (mouseButton == 1 && module.values.any { it.canDisplay() }) {
                        state.expanded = !state.expanded
                        updateExpandedState(module, state.expanded)
                    }
                    return
                }
                if (state.expanded) clickValues(module, state.x + 6f, state.y + moduleDefaultHeight + 1f, mx, my, mouseButton)
                return
            }
        }
    }

    private fun clickValues(module: Module, x: Float, y: Float, mouseX: Int, mouseY: Int, mouseButton: Int) {
        var yy = y
        module.values.filter { it.canDisplay() }.forEach { value ->
            if (inside(mouseX, mouseY, x, yy, moduleWidth - 12f, valueHeight(value))) {
                when (value) {
                    is BoolValue -> if (mouseButton == 0) { value.set(!value.get()); saveValueConfigSoon() }
                    is ListValue -> if (mouseButton == 0 || mouseButton == 1) { cycleList(value, mouseButton == 1); saveValueConfigSoon() }
                    is FontValue -> if (mouseButton == 0 || mouseButton == 1) { cycleFont(value, mouseButton == 1); saveValueConfigSoon() }
                    is IntegerValue, is FloatValue -> if (mouseButton == 0 && overNumberSlider(value, x, yy, mouseX, mouseY)) { activeSlider = value; activeRangeSide = 0 }
                    is IntRangeValue, is FloatRangeValue -> if (mouseButton == 0 && overNumberSlider(value, x, yy, mouseX, mouseY)) { activeSlider = value; activeRangeSide = pickRangeSide(value, x, yy, mouseX) }
                }
                return
            }
            yy += valueHeight(value)
        }
    }

    override fun mouseReleased(mouseX: Int, mouseY: Int, state: Int) {
        val hadSlider = activeSlider != null
        dragging = false
        activeSlider = null
        activeRangeSide = 0
        modules.values.forEach { it.mouseDown = false }
        if (hadSlider) saveValueConfigSoon()
        super.mouseReleased(mouseX, mouseY, state)
    }

    @Throws(IOException::class)
    override fun keyTyped(typedChar: Char, keyCode: Int) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(null)
            return
        }
        if (isSearchChar(typedChar) && selectedCategory != null) {
            switchCategory(null)
            search = ""
            searchFocused = true
        }
        if (selectedCategory == null && (searchFocused || isSearchChar(typedChar))) {
            searchFocused = true
            when {
                keyCode == Keyboard.KEY_BACK && search.isNotEmpty() -> search = search.dropLast(1)
                keyCode == Keyboard.KEY_DELETE -> search = ""
                isSearchChar(typedChar) -> search += typedChar
            }
            scrollTarget = 0.0
            persistState()
            return
        }
        super.keyTyped(typedChar, keyCode)
    }

    private fun switchCategory(category: ModuleCategory?) {
        if (selectedCategory == category) return
        persistExpandedStates()
        lastCategory = selectedCategory
        transitionScroll = scroll
        transitionStartedAt = System.currentTimeMillis()
        selectedCategory = category
        searchFocused = category == null
        transitionOpacity = 0.0
        scroll = Persistent.scrollByCategory[category?.name ?: "SEARCH"] ?: 0.0
        scrollTarget = scroll
        persistState()
    }

    private fun modulesForScreen(category: ModuleCategory? = selectedCategory): List<Module> {
        val categoryKey = category?.name ?: "SEARCH"
        val query = search.lowercase(Locale.getDefault()).replace(" ", "")
        val key = categoryKey + "|" + query + "|" + MinusBounce.moduleManager.modules.size
        if (key == cachedModuleKey) return cachedModuleList
        val base = if (category == null) MinusBounce.moduleManager.modules else MinusBounce.moduleManager.getModuleOnCategory(category)
        val filtered = if (category == null && query.isNotEmpty()) base.filter {
            it.name.lowercase(Locale.getDefault()).replace(" ", "").contains(query) || it.description.lowercase(Locale.getDefault()).replace(" ", "").contains(query)
        } else base
        cachedModuleList = filtered.sortedWith { a, b -> collator.compare(a.name, b.name) }
        cachedModuleKey = key
        return cachedModuleList
    }

    private fun rebuildModuleCache() {
        modules.clear()
        MinusBounce.moduleManager.modules.sortedWith { a, b -> collator.compare(a.name, b.name) }.forEach {
            modules[it] = ModuleState(it, expanded = Persistent.expandedModules.contains(it.name))
        }
    }

    private fun moduleState(module: Module) = modules.getOrPut(module) { ModuleState(module) }

    private fun moduleHeight(module: Module): Double {
        val state = moduleState(module)
        return if (state.expanded) fullModuleHeight(module) else moduleDefaultHeight.toDouble()
    }

    private fun fullModuleHeight(module: Module): Double = moduleDefaultHeight +
        (module.values.filter { it.canDisplay() }.sumOf { valueHeight(it).toDouble() } - 1.0).coerceAtLeast(0.0)

    private fun valueHeight(value: Value<*>) = 14f

    private fun scrollLimit(contentHeight: Double, searchScreen: Boolean) {
        val visible = contentBottom() - contentTop(searchScreen)
        val minScroll = (-contentHeight + visible).coerceAtMost(0.0)
        scrollTarget = scrollTarget.coerceIn(minScroll, 0.0)
    }

    private fun drawScrollbar(contentHeight: Double, searchScreen: Boolean) {
        val visible = contentBottom() - contentTop(searchScreen)
        if (contentHeight <= visible) return
        val padding = 7.0
        val sx = x.toDouble() + widthBase.toDouble() - 4.0
        val sy = contentTop(searchScreen) + padding
        val sh = (contentBottom() - contentTop(searchScreen) - padding * 2.0).coerceAtLeast(1.0)
        val barH = (sh * (visible / contentHeight)).coerceAtLeast(20.0)
        val progress = (-scrollTarget / (contentHeight - visible).coerceAtLeast(1.0)).coerceIn(0.0, 1.0)
        RiseRenderUtil.roundedRectangle(sx, sy, 2.0, sh, 1.0, Color(255, 255, 255, 20))
        RiseRenderUtil.roundedRectangle(sx, sy + (sh - barH) * progress, 2.0, barH, 1.0, Color(255, 255, 255, 70))
    }

    private fun setSliderValue(value: Value<*>, mouseX: Int, sx: Double, sliderWidth: Double) {
        val pct = ((mouseX - sx) / sliderWidth).coerceIn(0.0, 1.0)
        when (value) {
            is IntegerValue -> value.set(value.minimum + ((value.maximum - value.minimum).toDouble() * pct).roundToInt())
            is FloatValue -> value.set((value.minimum.toDouble() + (value.maximum - value.minimum).toDouble() * pct).toFloat())
        }
    }

    private fun setRangeValue(value: Value<*>, mouseX: Int, sx: Double, sliderWidth: Double) {
        val pct = ((mouseX - sx) / sliderWidth).coerceIn(0.0, 1.0)
        when (value) {
            is IntRangeValue -> {
                val next = value.minimum + ((value.maximum - value.minimum).toDouble() * pct).roundToInt()
                if (activeRangeSide == 1) value.setMinValue(next.coerceAtMost(value.getMaxValue())) else value.setMaxValue(next.coerceAtLeast(value.getMinValue()))
            }
            is FloatRangeValue -> {
                val next = (value.minimum.toDouble() + (value.maximum - value.minimum).toDouble() * pct).toFloat()
                if (activeRangeSide == 1) value.setMinValue(next.coerceAtMost(value.getMaxValue())) else value.setMaxValue(next.coerceAtLeast(value.getMinValue()))
            }
        }
    }

    private fun pickRangeSide(value: Value<*>, x: Float, y: Float, mouseX: Int): Int {
        val sx = x.toDouble() + fontSmall().width(value.name).toDouble() + 7.0
        val minValue: Double
        val maxValue: Double
        val first: Double
        val second: Double
        when (value) {
            is IntRangeValue -> { minValue = value.minimum.toDouble(); maxValue = value.maximum.toDouble(); first = value.getMinValue().toDouble(); second = value.getMaxValue().toDouble() }
            is FloatRangeValue -> { minValue = value.minimum.toDouble(); maxValue = value.maximum.toDouble(); first = value.getMinValue().toDouble(); second = value.getMaxValue().toDouble() }
            else -> return 1
        }
        val p1 = if (maxValue == minValue) 0.0 else ((first - minValue) / (maxValue - minValue)).coerceIn(0.0, 1.0)
        val p2 = if (maxValue == minValue) 0.0 else ((second - minValue) / (maxValue - minValue)).coerceIn(0.0, 1.0)
        val d1 = abs(mouseX - (sx + p1 * 100.0))
        val d2 = abs(mouseX - (sx + p2 * 100.0))
        return if (d1 <= d2) 1 else 2
    }

    private fun overNumberSlider(value: Value<*>, x: Float, y: Float, mouseX: Int, mouseY: Int): Boolean {
        val sx = x.toDouble() + fontSmall().width(value.name).toDouble() + 7.0 - 5.0
        return inside(mouseX, mouseY, sx.toFloat(), y - 3.5f, 110f, 14f)
    }

    private fun cycleList(value: ListValue, backwards: Boolean) {
        val current = value.values.indexOfFirst { it.equals(value.get(), true) }.coerceAtLeast(0)
        val next = if (backwards) (current - 1 + value.values.size) % value.values.size else (current + 1) % value.values.size
        value.set(value.values[next])
    }

    private fun cycleFont(value: FontValue, backwards: Boolean) {
        val fonts = value.values
        if (fonts.isEmpty()) return
        val current = fonts.indexOfFirst { it == value.get() }.coerceAtLeast(0)
        val next = if (backwards) (current - 1 + fonts.size) % fonts.size else (current + 1) % fonts.size
        value.set(fonts[next])
    }

    private fun animateValue(key: Any, target: Double, speed: Double): Double {
        val old = valueSlides.getOrDefault(key, target)
        val next = (old * (speed - 1.0) + target) / speed
        valueSlides[key] = next
        return next
    }

    private fun formatNumber(value: Double): String {
        val text = String.format(Locale.US, "%.2f", value)
        return text.trimEnd('0').trimEnd('.')
    }

    private fun saveValueConfigSoon() {
        MinusBounce.fileManager.saveConfig(MinusBounce.fileManager.valuesConfig)
    }

    private fun persistPosition() {
        Persistent.x = x
        Persistent.y = y
    }

    private fun persistExpandedStates() {
        modules.forEach { (module, state) -> updateExpandedState(module, state.expanded) }
    }

    private fun updateExpandedState(module: Module, expanded: Boolean) {
        if (expanded) Persistent.expandedModules.add(module.name) else Persistent.expandedModules.remove(module.name)
    }

    private fun persistState() {
        persistPosition()
        persistExpandedStates()
        Persistent.selectedCategory = selectedCategory
        Persistent.lastCategory = lastCategory
        Persistent.search = search
        Persistent.searchFocused = searchFocused
        Persistent.scroll = scroll
        Persistent.scrollTarget = scrollTarget
        Persistent.scrollByCategory[selectedCategory?.name ?: "SEARCH"] = scrollTarget
    }

    private fun categoryEntries(): List<CategoryItem> = listOf(CategoryItem("Search", null)) + ModuleCategory.values().map { CategoryItem(it.displayName, it) }

    private fun categoryWidth(name: String): Double = fontCategory().width(name).toDouble()

    private fun drawGlowText(font: GameFontRenderer, text: String, x: Float, y: Float, color: Color, glow: Color) {
        if (fastEffects) {
            if (glow.alpha > 70) {
                val soft = RiseColors.withAlpha(glow, (glow.alpha * 0.28).roundToInt())
                font.drawString(text, x + 0.45f, y + 0.45f, soft.rgb)
            }
            font.drawString(text, x, y, color.rgb)
            return
        }
        if (glow.alpha > 0) {
            val soft = RiseColors.withAlpha(glow, (glow.alpha * 0.45).roundToInt())
            font.drawString(text, x - 0.7f, y, soft.rgb)
            font.drawString(text, x + 0.7f, y, soft.rgb)
            font.drawString(text, x, y - 0.7f, soft.rgb)
            font.drawString(text, x, y + 0.7f, soft.rgb)
        }
        font.drawString(text, x, y, color.rgb)
    }

    private fun drawCenteredGlowText(font: GameFontRenderer, text: String, x: Float, y: Float, color: Color, glow: Color) {
        drawGlowText(font, text, x - font.width(text) / 2f, y, color, glow)
    }

    private fun isSearchChar(char: Char): Boolean {
        if (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL)) return false
        return char.lowercaseChar() in "abcdefghijklmnopqrstuvwxyz1234567890 "
    }

    private fun applyFrameScissor() {
        GL11.glEnable(GL11.GL_SCISSOR_TEST)
        RiseRenderUtil.scissor((x + 1).toDouble(), (y + 1).toDouble(), (widthBase - 2).toDouble(), (heightBase - 2).toDouble())
    }

    private fun clampPosition() {
        val sr = ScaledResolution(mc)
        val maxX = sr.scaledWidth - scaledWidth - 2f
        val maxY = sr.scaledHeight - scaledHeight - 2f
        x = x.coerceIn(2f, max(2f, maxX))
        y = y.coerceIn(2f, max(2f, maxY))
    }

    private fun applyContentScissor(searchScreen: Boolean) {
        GL11.glEnable(GL11.GL_SCISSOR_TEST)
        val top = contentTop(searchScreen)
        val bottom = contentBottom()
        RiseRenderUtil.scissor((x + sidebarWidth + 2).toDouble(), top, (widthBase - sidebarWidth - 9).toDouble(), bottom - top)
    }

    private fun contentTop(searchScreen: Boolean) = y.toDouble() + if (searchScreen) 36.0 else 8.0

    private fun contentBottom() = y.toDouble() + heightBase.toDouble() - 10.0

    private fun logicalMouseX(mouseX: Int) = (x + (mouseX - x) / guiScale).roundToInt()

    private fun logicalMouseY(mouseY: Int) = (y + (mouseY - y) / guiScale).roundToInt()

    private fun inside(mouseX: Int, mouseY: Int, x: Float, y: Float, width: Float, height: Float) = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height

    private fun fontTitle() = RiseFonts.title
    private fun fontSearch() = RiseFonts.search
    private fun fontName() = RiseFonts.module
    private fun fontDescription() = RiseFonts.description
    private fun fontSmall() = RiseFonts.value
    private fun fontCategory() = RiseFonts.category

    private fun GameFontRenderer.width(text: String) = getStringWidth(text).toFloat()
    private fun GameFontRenderer.fontHeight() = FONT_HEIGHT.toFloat()

    companion object Persistent {
        var x = -1f
        var y = -1f
        var selectedCategory: ModuleCategory? = null
        var lastCategory: ModuleCategory? = null
        var search = ""
        var searchFocused = false
        var scroll = 0.0
        var scrollTarget = 0.0
        val scrollByCategory = hashMapOf<String, Double>()
        val expandedModules = hashSetOf<String>()

        fun resetSessionState() {
            x = -1f
            y = -1f
            selectedCategory = null
            lastCategory = null
            search = ""
            searchFocused = false
            scroll = 0.0
            scrollTarget = 0.0
            scrollByCategory.clear()
            expandedModules.clear()
            RiseRenderUtil.clearScaleTransform()
            GL11.glDisable(GL11.GL_SCISSOR_TEST)
            GlStateManager.resetColor()
        }
    }

    private data class CategoryItem(val name: String, val category: ModuleCategory?) { val key = category?.name ?: "SEARCH" }
    private data class CategoryState(var selector: Double = 0.0)
    private data class RangeKey(val value: Value<*>)
    private class ModuleState(
        val module: Module,
        var x: Float = 0f,
        var y: Float = 0f,
        var height: Double = 38.0,
        var hover: Double = 0.0,
        var settingOpacity: Double = 0.0,
        var expanded: Boolean = false,
        var mouseDown: Boolean = false
    ) {
        val opening = RiseAnimation(RiseEasing.EASE_OUT_EXPO, 200L).also { it.reset(height) }
        val settings = RiseAnimation(RiseEasing.LINEAR, 100L).also { it.reset(settingOpacity) }
    }
}
