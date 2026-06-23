/*
 * MinusBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/MinusMC/MinusBounce
 */
package net.minusmc.minusbounce.features.module.modules.combat

import net.minecraft.entity.EntityLivingBase
import net.minecraft.network.play.client.C0BPacketEntityAction
import net.minecraft.util.MathHelper
import net.minecraft.util.MovingObjectPosition
import net.minusmc.minusbounce.MinusBounce
import net.minusmc.minusbounce.event.EventTarget
import net.minusmc.minusbounce.event.EventTick
import net.minusmc.minusbounce.features.module.Module
import net.minusmc.minusbounce.features.module.ModuleCategory
import net.minusmc.minusbounce.features.module.ModuleInfo
import net.minusmc.minusbounce.value.BoolValue
import net.minusmc.minusbounce.value.ListValue
import kotlin.math.abs


@ModuleInfo(name = "SuperKnockback", spacedName = "Super Knockback", description = "Increases knockback dealt to other entities.", category = ModuleCategory.COMBAT)
class SuperKnockback : Module() {
    private val mode = ListValue("Mode", arrayOf("Legit", "LegitFast", "LessPacket", "Packet", "DoublePacket"), "Legit")
    private val intelligent = BoolValue("Intelligent", false)

    @EventTarget
    fun onEventTick(e: EventTick) {
        val mm = MinusBounce.moduleManager
        var entity: EntityLivingBase? = null
        if (mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY && mc.objectMouseOver.entityHit is EntityLivingBase) {
            entity = mc.objectMouseOver.entityHit as EntityLivingBase
        }

        if (mm[KillAura::class.java]?.state == true && mm[KillAura::class.java]?.target != null) {
            entity = mm[KillAura::class.java]?.target
        }

        val isBackTrackEnabled = mm[BackTrack::class.java]?.state == true && mm[BackTrack::class.java]?.packets?.isNotEmpty() == true

        if(entity == null){
            return
        }

        if (isBackTrackEnabled) {
            return
        }

        val x: Double = mc.thePlayer.posX - entity.posX
        val z: Double = mc.thePlayer.posZ - entity.posZ
        val calcYaw = MathHelper.atan2(z, x) * 180.0 / 3.141592653589793 - 90.0
        if (intelligent.get() && abs(MathHelper.wrapAngleTo180_double(calcYaw - entity.rotationYawHead)) > 120.0) {
            return
        }
        when (mode.get().lowercase()) {
            "packet" -> {
                if (entity.hurtTime == 10) {
                    mc.thePlayer.sendQueue.addToSendQueue(
                        C0BPacketEntityAction(
                            mc.thePlayer,
                            C0BPacketEntityAction.Action.STOP_SPRINTING
                        )
                    )
                    mc.thePlayer.sendQueue.addToSendQueue(
                        C0BPacketEntityAction(
                            mc.thePlayer,
                            C0BPacketEntityAction.Action.START_SPRINTING
                        )
                    )
                    mc.thePlayer.serverSprintState = true
                }
            }

            "doublepacket" -> {
                if (entity.hurtTime == 10) {
                    mc.thePlayer.sendQueue.addToSendQueue(
                        C0BPacketEntityAction(
                            mc.thePlayer,
                            C0BPacketEntityAction.Action.STOP_SPRINTING
                        )
                    )
                    mc.thePlayer.sendQueue.addToSendQueue(
                        C0BPacketEntityAction(
                            mc.thePlayer,
                            C0BPacketEntityAction.Action.START_SPRINTING
                        )
                    )
                    mc.thePlayer.sendQueue.addToSendQueue(
                        C0BPacketEntityAction(
                            mc.thePlayer,
                            C0BPacketEntityAction.Action.STOP_SPRINTING
                        )
                    )
                    mc.thePlayer.sendQueue.addToSendQueue(
                        C0BPacketEntityAction(
                            mc.thePlayer,
                            C0BPacketEntityAction.Action.START_SPRINTING
                        )
                    )
                    mc.thePlayer.serverSprintState = true
                }
            }
            "legit" -> mc.thePlayer.reSprint = if (entity.hurtTime == 10) 2 else mc.thePlayer.reSprint
            "legitfast" -> mc.thePlayer.sprintingTicksLeft = 0
            "lesspacket" -> {
                if (entity.hurtTime == 10) {
                    if (mc.thePlayer.isSprinting) {
                        mc.thePlayer.isSprinting = false
                    }
                    mc.netHandler.addToSendQueue(
                        C0BPacketEntityAction(
                            mc.thePlayer,
                            C0BPacketEntityAction.Action.START_SPRINTING
                        )
                    )
                    mc.thePlayer.serverSprintState = true
                }
            }
        }
    }
}
