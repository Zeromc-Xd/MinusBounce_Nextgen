package net.minusmc.minusbounce.features.module.modules.combat

import net.minecraft.entity.EntityLivingBase
import net.minecraft.network.Packet
import net.minecraft.network.play.server.*
import net.minecraft.util.AxisAlignedBB
import net.minecraft.util.MathHelper
import net.minecraft.util.Vec3
import net.minusmc.minusbounce.MinusBounce
import net.minusmc.minusbounce.event.EventTarget
import net.minusmc.minusbounce.event.PacketEvent
import net.minusmc.minusbounce.event.Render3DEvent
import net.minusmc.minusbounce.event.TickEvent
import net.minusmc.minusbounce.features.module.Module
import net.minusmc.minusbounce.features.module.ModuleCategory
import net.minusmc.minusbounce.features.module.ModuleInfo
import net.minusmc.minusbounce.features.module.modules.player.Blink
import net.minusmc.minusbounce.features.module.modules.world.Scaffold
import net.minusmc.minusbounce.utils.Constants
import net.minusmc.minusbounce.utils.PacketUtils
import net.minusmc.minusbounce.utils.timer.MSTimer
import net.minusmc.minusbounce.value.BoolValue
import net.minusmc.minusbounce.value.FloatRangeValue
import net.minusmc.minusbounce.value.IntegerValue


@Suppress("UNUSED_PARAMETER")
@ModuleInfo("BackTrack", "Back Track", "Let you attack in their previous position", ModuleCategory.COMBAT)
class BackTrack : Module() {
    private val pulse = BoolValue("Pulse", true)
    private val delay = IntegerValue("Delay", 400, 0, 10000) { pulse.get() }
    private val range = FloatRangeValue("Range", 3F, 6F, 0F, 10F) { pulse.get() }
    private val velocity = BoolValue("Velocity", true)
    private val explosion = BoolValue("Explosion", true)
    private val esp = BoolValue("ESP", true)

    private var switch = false
    val packets = mutableListOf<Packet<*>>()
    val timer = MSTimer()

    override fun onEnable() {
        mc.thePlayer ?: return
        mc.theWorld ?: return

        for (e in mc.theWorld.loadedEntityList){
            if(e is EntityLivingBase){
                e.realPosX = e.serverPosX.toDouble()
                e.realPosY = e.serverPosY.toDouble()
                e.realPosZ = e.serverPosZ.toDouble()
            }
        }

        packets.clear()
    }

    override fun onDisable() {
        if(packets.size > 0){
            flushPackets()
        }

        packets.clear()
    }

    @EventTarget(priority = Int.MAX_VALUE)
    fun onPacket(event: PacketEvent) {
        mc.thePlayer ?: return
        mc.theWorld ?: return
        mc.netHandler ?: return

        if (MinusBounce.moduleManager[Scaffold::class.java]!!.state) {
            packets.clear()
            return
        }

        val packet = event.packet

        if (packet::class.java !in Constants.serverPacketClasses)
            return

        when (packet) {
            is S14PacketEntity -> {
                val entity = mc.theWorld.getEntityByID(packet.entityId)

                if (entity is EntityLivingBase) {
                    entity.realPosX += packet.func_149062_c()
                    entity.realPosY += packet.func_149061_d()
                    entity.realPosZ += packet.func_149064_e()
                }    
            }

            is S18PacketEntityTeleport -> {
                val entity = mc.theWorld.getEntityByID(packet.entityId)

                if (entity is EntityLivingBase) {
                    entity.realPosX = packet.x.toDouble()
                    entity.realPosY = packet.y.toDouble()
                    entity.realPosZ = packet.z.toDouble()
                }
            }

            is S08PacketPlayerPosLook, is S40PacketDisconnect -> {
                flushPackets()
                return
            }

            is S13PacketDestroyEntities -> {
                for (id in packet.entityIDs){
                    if(id == (target?.entityId ?: id)){
                        flushPackets()
                        return
                    }
                }
            }
        }

        if (target == null) {
            flushPackets()
            return
        }

        addPacket(event)
    }

    val target: EntityLivingBase?
        get() = MinusBounce.moduleManager[KillAura::class.java]?.target

    @EventTarget
    fun onRender3D(event: Render3DEvent) {
        mc.thePlayer ?: return
        mc.theWorld ?: return
        val target = this.target ?: return
        val realX = target.realPosX / 32.0
        val realY = target.realPosY / 32.0
        val realZ = target.realPosZ / 32.0

        if (target != mc.thePlayer && !target.isInvisible && esp.get() && packets.isNotEmpty()) {
            Blink.drawBox(Vec3(realX, realY, realZ))
        }
    }

    @EventTarget
    fun onUpdate(e: TickEvent){
        mc.thePlayer ?: return
        mc.theWorld ?: return
        val target = this.target ?: return

        if (target.realPosX == 0.0 || target.realPosY == 0.0 || target.realPosZ == 0.0 || target.width == 0f || target.height == 0f){
            switch = false
            return
        }

        val d0 = target.realPosX / 32.0
        val d2 = target.realPosY / 32.0
        val d3 = target.realPosZ / 32.0
        val d4 = target.serverPosX / 32.0
        val d5 = target.serverPosY / 32.0
        val d6 = target.serverPosZ / 32.0
        val f = target.width / 2.0f
        val entityServerPos = AxisAlignedBB(d4 - f, d5, d6 - f, d4 + f, d5 + target.height, d6 + f)
        val positionEyes = mc.thePlayer.getPositionEyes(mc.timer.renderPartialTicks)
        val currentX = MathHelper.clamp_double(positionEyes.xCoord, entityServerPos.minX, entityServerPos.maxX)
        val currentY = MathHelper.clamp_double(positionEyes.yCoord, entityServerPos.minY, entityServerPos.maxY)
        val currentZ = MathHelper.clamp_double(positionEyes.zCoord, entityServerPos.minZ, entityServerPos.maxZ)
        val entityPosMe = AxisAlignedBB(d0 - f, d2, d3 - f, d0 + f, d2 + target.height, d3 + f)
        val realX = MathHelper.clamp_double(positionEyes.xCoord, entityPosMe.minX, entityPosMe.maxX)
        val realY = MathHelper.clamp_double(positionEyes.yCoord, entityPosMe.minY, entityPosMe.maxY)
        val realZ = MathHelper.clamp_double(positionEyes.zCoord, entityPosMe.minZ, entityPosMe.maxZ)

        var distance = range.getMaxValue().toDouble()
        if (!mc.thePlayer.canEntityBeSeen(target)) {
            distance = if (distance > 3.0) 3.0 else distance
        }

        val collision = target.collisionBorderSize.toDouble()
        val width = (mc.thePlayer.width / 2.0f).toDouble()
        val mePosXForPlayer = lastServerPosition.xCoord + (serverPosition.xCoord - lastServerPosition.xCoord) / MathHelper.clamp_int(rotIncrement, 1, 3)
        val mePosYForPlayer = lastServerPosition.yCoord + (serverPosition.yCoord - lastServerPosition.yCoord) / MathHelper.clamp_int(rotIncrement, 1, 3)
        val mePosZForPlayer = lastServerPosition.zCoord + (serverPosition.zCoord - lastServerPosition.zCoord) / MathHelper.clamp_int(rotIncrement, 1, 3)
        val mePosForPlayerBox = AxisAlignedBB(
            mePosXForPlayer - width,
            mePosYForPlayer,
            mePosZForPlayer - width,
            mePosXForPlayer + width,
            mePosYForPlayer + mc.thePlayer.height,
            mePosZForPlayer + width
        ).expand(collision, collision, collision)

        val entityPosEyes = Vec3(d4, d5 + target.eyeHeight, d6)
        val bestX = MathHelper.clamp_double(entityPosEyes.xCoord, mePosForPlayerBox.minX, mePosForPlayerBox.maxX)
        val bestY = MathHelper.clamp_double(entityPosEyes.yCoord, mePosForPlayerBox.minY, mePosForPlayerBox.maxY)
        val bestZ = MathHelper.clamp_double(entityPosEyes.zCoord, mePosForPlayerBox.minZ, mePosForPlayerBox.maxZ)

        if (entityPosEyes.distanceTo(Vec3(bestX, bestY, bestZ)) > range.getMinValue()
        ) {
            switch = true
        }

        val eyesToRealPosition = positionEyes.distanceTo(Vec3(realX, realY, realZ))
        val eyesToCurrentPosition = positionEyes.distanceTo(Vec3(currentX, currentY, currentZ))

        if (!switch || eyesToRealPosition <= eyesToCurrentPosition || serverPosition.distanceTo(Vec3(d0, d2, d3)) >= distance || (timer.hasTimePassed(delay.get()) && pulse.get())) {
            timer.reset()
            flushPackets()
        }
    }

    private fun flushPackets() {
        if (packets.isNotEmpty()) {
            packets.forEach(PacketUtils::processPacket)
            packets.clear()
            switch = false
        }
    }

    private fun addPacket(event: PacketEvent) {
        val packet = event.packet
        var freeze = true

        when (packet) {
            is S19PacketEntityStatus -> freeze = packet.opCode != 2.toByte() || mc.theWorld.getEntityByID(packet.entityId) !is EntityLivingBase
            is S12PacketEntityVelocity -> freeze = !velocity.get()
            is S27PacketExplosion -> freeze = !explosion.get()
        }

        if (packet::class.java !in Constants.serverOtherPacketClasses && freeze && switch) {
            packets.add(packet)
            event.isCancelled = true
            event.stopRunEvent = true
        }
    }
}
