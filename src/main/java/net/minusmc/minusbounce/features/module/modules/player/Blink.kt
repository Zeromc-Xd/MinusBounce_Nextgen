/*
 * LiquidBounce+ Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/WYSI-Foundation/LiquidBouncePlus/
 */
package net.minusmc.minusbounce.features.module.modules.player

import net.minecraft.client.renderer.GlStateManager
import net.minecraft.network.Packet
import net.minecraft.network.handshake.client.C00Handshake
import net.minecraft.network.login.client.C00PacketLoginStart
import net.minecraft.network.login.client.C01PacketEncryptionResponse
import net.minecraft.network.play.client.C01PacketChatMessage
import net.minecraft.network.status.client.C00PacketServerQuery
import net.minecraft.util.AxisAlignedBB
import net.minecraft.util.Vec3
import net.minusmc.minusbounce.event.*
import net.minusmc.minusbounce.features.module.Module
import net.minusmc.minusbounce.features.module.ModuleCategory
import net.minusmc.minusbounce.features.module.ModuleInfo
import net.minusmc.minusbounce.utils.PacketUtils.sendPacketNoEvent
import net.minusmc.minusbounce.utils.render.RenderUtils
import net.minusmc.minusbounce.value.BoolValue
import net.minusmc.minusbounce.value.IntegerValue
import org.lwjgl.opengl.GL11
import java.awt.Color


@ModuleInfo(name = "Blink", description = "Suspends all movement packets.", category = ModuleCategory.PLAYER)
object Blink : Module() {

    private val pulse = BoolValue("Pulse", false)
    private val pulseDelay = IntegerValue("Delay", 1000, 0, 10000, "ms") { pulse.get() }
    private val initialPosition = BoolValue("ShowInitialPosition", true)
    private val overlay = BoolValue("Overlay", false)
    
    val blinkedPackets = ArrayList<Packet<*>>()
    private var startTime: Long = -1
    private var pos: Vec3? = null
    val color = Color(72, 125, 227).rgb

    override fun onEnable() {
        start()
    }

    private fun start() {
        blinkedPackets.clear()
        pos = Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ)
        startTime = System.currentTimeMillis()
    }

    override fun onDisable() {
        reset()
    }

    private fun reset() {
        synchronized(blinkedPackets) {
            for (packet in blinkedPackets) {
                sendPacketNoEvent(packet)
            }
        }
        blinkedPackets.clear()
        pos = null
    }

    override val tag: String
        get() = blinkedPackets.size.toString()

    @EventTarget
    fun onRender(event: StartRenderTickEvent) {
        if (overlay.get() && nullCheck()) {
            RenderUtils.drawText("blinking: " + blinkedPackets.size, color)
        }
    }

    @EventTarget
    fun onRenderWorld(e: Render3DEvent) {
        if (!nullCheck() || pos == null || !initialPosition.get()) {
            return
        }
        drawBox(pos!!)
    }

    @EventTarget
    fun onSendPacket(e: PacketEvent) {
        if(e.eventType == EventState.SEND){
            if (!nullCheck()) {
                state = false
                return
            }
            val packet = e.packet
            if (packet.javaClass.simpleName.startsWith("S")) {
                return
            }
            if (packet is C00Handshake
                || packet is C00PacketLoginStart
                || packet is C00PacketServerQuery
                || packet is C01PacketEncryptionResponse
                || packet is C01PacketChatMessage
            ) {
                return
            }
            blinkedPackets.add(packet)
            e.cancelEvent()

            if (pulse.get()) {
                if (System.currentTimeMillis() - startTime >= pulseDelay.get()) {
                    reset()
                    start()
                }
            }
        }
    }

    fun drawBox(pos: Vec3) {
        GlStateManager.pushMatrix()
        val x: Double = pos.xCoord - mc.renderManager.viewerPosX
        val y: Double = pos.yCoord - mc.renderManager.viewerPosY
        val z: Double = pos.zCoord - mc.renderManager.viewerPosZ
        val bbox = mc.thePlayer.entityBoundingBox.expand(0.1, 0.1, 0.1)
        val axis = AxisAlignedBB(
            bbox.minX - mc.thePlayer.posX + x,
            bbox.minY - mc.thePlayer.posY + y,
            bbox.minZ - mc.thePlayer.posZ + z,
            bbox.maxX - mc.thePlayer.posX + x,
            bbox.maxY - mc.thePlayer.posY + y,
            bbox.maxZ - mc.thePlayer.posZ + z
        )
        val a = (color shr 24 and 255) / 255.0f
        val r = (color shr 16 and 255) / 255.0f
        val g = (color shr 8 and 255) / 255.0f
        val b = (color and 255) / 255.0f
        GL11.glBlendFunc(770, 771)
        GL11.glEnable(3042)
        GL11.glDisable(3553)
        GL11.glDisable(2929)
        GL11.glDepthMask(false)
        GL11.glLineWidth(2.0f)
        GL11.glColor4f(r, g, b, a)
        RenderUtils.drawBoundingBox(axis, r, g, b)
        GL11.glEnable(3553)
        GL11.glEnable(2929)
        GL11.glDepthMask(true)
        GL11.glDisable(3042)
        GlStateManager.popMatrix()
    }

    fun nullCheck(): Boolean {
        return mc.thePlayer != null && mc.theWorld != null
    }
}