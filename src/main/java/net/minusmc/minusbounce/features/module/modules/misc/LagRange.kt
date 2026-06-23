package net.minusmc.minusbounce.features.module.modules.misc

import net.minecraft.client.multiplayer.WorldClient
import net.minecraft.entity.EntityLivingBase
import net.minecraft.util.AxisAlignedBB
import net.minusmc.minusbounce.MinusBounce
import net.minusmc.minusbounce.event.EventState
import net.minusmc.minusbounce.event.EventTarget
import net.minusmc.minusbounce.event.GameLoop
import net.minusmc.minusbounce.event.PacketEvent
import net.minusmc.minusbounce.event.PreUpdateEvent
import net.minusmc.minusbounce.event.Render3DEvent
import net.minusmc.minusbounce.event.WorldEvent
import net.minusmc.minusbounce.features.module.Module
import net.minusmc.minusbounce.features.module.ModuleCategory
import net.minusmc.minusbounce.features.module.ModuleInfo
import net.minusmc.minusbounce.features.module.modules.combat.KillAura
import net.minusmc.minusbounce.features.module.modules.world.Scaffold
import net.minusmc.minusbounce.features.special.LagRangePacketManager
import net.minusmc.minusbounce.utils.EntityUtils
import net.minusmc.minusbounce.utils.extensions.getDistanceToEntityBox
import net.minusmc.minusbounce.utils.misc.RandomUtils
import net.minusmc.minusbounce.utils.render.RenderUtils
import net.minusmc.minusbounce.value.BoolValue
import net.minusmc.minusbounce.value.FloatValue
import net.minusmc.minusbounce.value.IntRangeValue
import java.awt.Color

@Suppress("UNUSED_PARAMETER")
@ModuleInfo(
    name = "LagRange",
    spacedName = "Lag Range",
    description = "Uses blink to gain reach while reducing counterattack windows.",
    category = ModuleCategory.COMBAT
)
class LagRange : Module() {
    private val delay = IntRangeValue("Delay", 150, 200, 0, 1000, "ms")
    private val esp = BoolValue("ESP", true)
    private val discoveredRange = FloatValue("DiscoveredRange", 4f, 3f, 16f, "m")

    private val color = Color(72, 125, 227, 70)
    private var lastWorld: WorldClient? = null
    private var dynamicDelay = 0
    private var lastDelayAdjustment = 0L
    private var entity: EntityLivingBase? = null
    private var multiplayer = false
    private var ignoreWholeTick = false
    private var flushScheduled = false

    override val tag: String
        get() = "${delay.getMinValue()}-${delay.getMaxValue()}ms"

    override fun onEnable() {
        entity = null
        lastWorld = mc.theWorld
        dynamicDelay = 0
        lastDelayAdjustment = 0L
        multiplayer = !mc.isIntegratedServerRunning
        ignoreWholeTick = true
        flushScheduled = false
        LagRangePacketManager.flushAll()
    }

    override fun onDisable() {
        LagRangePacketManager.flushAll()
        resetLocalState()
    }

    @EventTarget
    fun onPreUpdate(event: PreUpdateEvent) {
        multiplayer = !mc.isIntegratedServerRunning
    }

    @EventTarget(priority = -5)
    fun onPacket(event: PacketEvent) {
        if (event.eventType != EventState.SEND || event.isCancelled || !multiplayer) return

        val player = mc.thePlayer
        val world = mc.theWorld
        val networkManager = mc.netHandler?.networkManager

        if (player == null || world == null || networkManager?.netHandler == null || player.isDead || ignoreWholeTick) {
            requestFlush()
            return
        }

        if (MinusBounce.moduleManager[Scaffold::class.java]?.state == true) {
            requestFlush()
            return
        }

        entity = findTarget()

        if (lastWorld !== world) {
            requestFlush()
            lastWorld = world
            return
        }

        val target = entity
        if (target != null && player.getDistanceToEntity(target) in 3f..discoveredRange.get()) {
            LagRangePacketManager.enqueue(event.packet)
            event.cancelEvent()
            event.stopRunEvent = true
        } else {
            requestFlush()
        }
    }

    @EventTarget
    fun onGameLoop(event: GameLoop) {
        if (mc.thePlayer == null || mc.theWorld == null || mc.netHandler == null) {
            LagRangePacketManager.clear()
            ignoreWholeTick = false
            return
        }

        updateDynamicDelay()
        LagRangePacketManager.releaseExpired(dynamicDelay.toLong())
        ignoreWholeTick = false
    }

    @EventTarget
    fun onWorld(event: WorldEvent) {
        LagRangePacketManager.clear()
        lastWorld = event.worldClient
        entity = null
        ignoreWholeTick = true
        flushScheduled = false
    }

    @EventTarget
    fun onRender3D(event: Render3DEvent) {
        val player = mc.thePlayer ?: return
        if (!esp.get() || mc.gameSettings.thirdPersonView == 0) return

        val playerBox = player.entityBoundingBox.expand(0.1, 0.1, 0.1)
        for (position in LagRangePacketManager.queuedPositions()) {
            val box = AxisAlignedBB(
                playerBox.minX - player.posX + position.xCoord - mc.renderManager.renderPosX,
                playerBox.minY - player.posY + position.yCoord - mc.renderManager.renderPosY,
                playerBox.minZ - player.posZ + position.zCoord - mc.renderManager.renderPosZ,
                playerBox.maxX - player.posX + position.xCoord - mc.renderManager.renderPosX,
                playerBox.maxY - player.posY + position.yCoord - mc.renderManager.renderPosY,
                playerBox.maxZ - player.posZ + position.zCoord - mc.renderManager.renderPosZ
            )
            RenderUtils.drawAxisAlignedBB(box, color)
        }
    }

    private fun findTarget(): EntityLivingBase? {
        val player = mc.thePlayer ?: return null
        val world = mc.theWorld ?: return null
        val candidates = world.loadedEntityList
            .asSequence()
            .filterIsInstance<EntityLivingBase>()
            .filter { EntityUtils.isSelected(it, true) }
            .filter { player.getDistanceToEntityBox(it) <= discoveredRange.get() }
            .toMutableList()

        when (MinusBounce.moduleManager[KillAura::class.java]?.priority?.lowercase()) {
            "health" -> candidates.sortBy { it.health + it.absorptionAmount }
            "distance" -> candidates.sortBy { player.getDistanceToEntityBox(it) }
        }

        return candidates.firstOrNull()
    }

    private fun requestFlush() {
        if (flushScheduled) return
        flushScheduled = true

        mc.addScheduledTask {
            LagRangePacketManager.flushAll()
            ignoreWholeTick = true
            flushScheduled = false
        }
    }

    private fun updateDynamicDelay() {
        val now = System.currentTimeMillis()
        if (dynamicDelay == 0 || now - lastDelayAdjustment > 1000L) {
            dynamicDelay = RandomUtils.nextInt(delay.getMinValue(), delay.getMaxValue() + 1)
            lastDelayAdjustment = now
        }
    }

    private fun resetLocalState() {
        entity = null
        lastWorld = null
        dynamicDelay = 0
        lastDelayAdjustment = 0L
        multiplayer = false
        ignoreWholeTick = false
        flushScheduled = false
    }
}
