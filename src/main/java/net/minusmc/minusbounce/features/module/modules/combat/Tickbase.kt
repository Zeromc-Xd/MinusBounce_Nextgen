/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.minusmc.minusbounce.features.module.modules.combat

import net.minecraft.entity.EntityLivingBase
import net.minecraft.network.play.server.S08PacketPlayerPosLook
import net.minecraft.util.Vec3
import net.minusmc.minusbounce.event.*
import net.minusmc.minusbounce.features.module.Module
import net.minusmc.minusbounce.features.module.ModuleCategory
import net.minusmc.minusbounce.features.module.ModuleInfo
import net.minusmc.minusbounce.features.module.modules.player.Blink
import net.minusmc.minusbounce.utils.EntityUtils
import net.minusmc.minusbounce.utils.SimulatedPlayer
import net.minusmc.minusbounce.utils.render.RenderUtils.glColor
import net.minusmc.minusbounce.utils.timing.WaitTickUtils
import net.minusmc.minusbounce.value.BoolValue
import net.minusmc.minusbounce.value.FloatValue
import net.minusmc.minusbounce.value.IntegerValue
import org.lwjgl.opengl.GL11.*
import java.awt.Color

@ModuleInfo(name = "TickBase", spacedName = "Tick Base", description = "Tickbased.", category = ModuleCategory.COMBAT)
class TickBase : Module() {

    private val balanceMaxValue = IntegerValue("BalanceMaxValue", 100, 1,1000)
    private val balanceRecoveryIncrement = FloatValue("BalanceRecoveryIncrement", 0.1f, 0.01f,10f)
    private val maxTicksAtATime = IntegerValue("MaxTicksAtATime", 20, 1,100)
    private val rangeToAttack = FloatValue("RangeToAttack", 3.0f, 0.1f,10f)
    private val forceGround = BoolValue("ForceGround", false)
    private val pauseAfterTick = IntegerValue("PauseAfterTick", 0, 0,100)
    private val pauseOnFlag = BoolValue("PauseOnFlag", true)

    private var ticksToSkip = 0
    private var tickBalance = 0f
    private var reachedTheLimit = false
    private val tickBuffer = mutableListOf<TickData>()
    private var duringTickModification = false

    override fun onToggle(state: Boolean) {
        duringTickModification = false
    }

    @EventTarget(priority = -10)
    fun onTickPre(event: PreUpdateEvent) {
        val player = mc.thePlayer ?: return

        if (player.ridingEntity != null || Blink.handleEvents()) {
            return
        }

        if (ticksToSkip-- > 0) {
            event.cancelEvent()
            event.stopRunEvent = true
        }
    }

    @EventTarget
    fun onTickPost(event: PostPlayerTickEvent) {
        val player = mc.thePlayer ?: return

        if (player.ridingEntity != null || Blink.handleEvents()) {
            return
        }

        if (!duringTickModification && tickBuffer.isNotEmpty()) {
            val nearbyEnemy = getNearestEntityInRange() ?: return
            val currentDistance = player.positionVector.squareDistanceTo(nearbyEnemy.positionVector)

            val possibleTicks = tickBuffer
                .mapIndexed { index, tick -> index to tick }
                .filter { (_, tick) ->
                    tick.position.squareDistanceTo(nearbyEnemy.positionVector) < currentDistance &&
                            tick.position.squareDistanceTo(nearbyEnemy.positionVector) in 0f..rangeToAttack.get()
                }
                .filter { (_, tick) -> !forceGround.get() || tick.onGround }

            val criticalTick = possibleTicks
                .filter { (_, tick) -> tick.fallDistance > 0.0f }
                .minByOrNull { (index, _) -> index }

            val (bestTick, _) = criticalTick ?: possibleTicks.minByOrNull { (index, _) -> index } ?: return

            if (bestTick == 0) return

            duringTickModification = true

            ticksToSkip = bestTick + pauseAfterTick.get()

            WaitTickUtils.scheduleTicks(ticksToSkip) {
                repeat(bestTick) {
                    player.onUpdate()
                    tickBalance -= 1
                }

                duringTickModification = false
            }
        }
    }

    @EventTarget
    fun onMove(event: MoveEvent) {
        if (mc.thePlayer?.ridingEntity != null || Blink.handleEvents()) {
            return
        }

        tickBuffer.clear()

        val simulatedPlayer = SimulatedPlayer.fromClientPlayer(mc.thePlayer.movementInput)

        if (tickBalance <= 0) {
            reachedTheLimit = true
        }
        if (tickBalance > balanceMaxValue.get() / 2) {
            reachedTheLimit = false
        }
        if (tickBalance <= balanceMaxValue.get()) {
            tickBalance += balanceRecoveryIncrement.get()
        }

        if (reachedTheLimit) return

        repeat(minOf(tickBalance.toInt(), maxTicksAtATime.get())) {
            simulatedPlayer.tick()
            tickBuffer += TickData(
                simulatedPlayer.pos,
                simulatedPlayer.fallDistance,
                simulatedPlayer.motionX,
                simulatedPlayer.motionY,
                simulatedPlayer.motionZ,
                simulatedPlayer.onGround
            )
        }
    }

    @EventTarget
    fun onRender3D(event: Render3DEvent) {
        val color = Color(254, 238, 237)

        synchronized(tickBuffer) {
            glPushMatrix()
            glDisable(GL_TEXTURE_2D)
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
            glEnable(GL_LINE_SMOOTH)
            glEnable(GL_BLEND)
            glDisable(GL_DEPTH_TEST)
            mc.entityRenderer.disableLightmap()
            glBegin(GL_LINE_STRIP)
            glColor(color)

            val renderPosX = mc.renderManager.viewerPosX
            val renderPosY = mc.renderManager.viewerPosY
            val renderPosZ = mc.renderManager.viewerPosZ

            for (tick in tickBuffer) {
                glVertex3d(tick.position.xCoord - renderPosX,
                    tick.position.yCoord - renderPosY,
                    tick.position.zCoord - renderPosZ
                )
            }

            glColor4f(1.0f, 1.0f, 1.0f, 1.0f)
            glEnd()
            glEnable(GL_DEPTH_TEST)
            glDisable(GL_LINE_SMOOTH)
            glDisable(GL_BLEND)
            glEnable(GL_TEXTURE_2D)
            glPopMatrix()
        }
    }

    @EventTarget
    fun onPacket(event: PacketEvent) {
        if (event.packet is S08PacketPlayerPosLook && pauseOnFlag.get()) {
            tickBalance = 0f
        }
    }

    private data class TickData(
        val position: Vec3,
        val fallDistance: Float,
        val motionX: Double,
        val motionY: Double,
        val motionZ: Double,
        val onGround: Boolean,
    )

    private fun getNearestEntityInRange(): EntityLivingBase? {
        val player = mc.thePlayer ?: return null

        return mc.theWorld?.loadedEntityList
            ?.filterIsInstance<EntityLivingBase>()
            ?.filter { EntityUtils.isSelected(it, true) }
            ?.minByOrNull { player.getDistanceToEntity(it) }
    }
}