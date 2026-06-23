package net.minusmc.minusbounce.features.module.modules.combat.velocitys.intave

import net.minecraft.network.play.server.S12PacketEntityVelocity
import net.minecraft.util.Vec3
import net.minusmc.minusbounce.event.AttackEvent
import net.minusmc.minusbounce.event.MoveInputEvent
import net.minusmc.minusbounce.event.PacketEvent
import net.minusmc.minusbounce.features.module.modules.combat.velocitys.VelocityMode
import net.minusmc.minusbounce.utils.PlayerUtils
import net.minusmc.minusbounce.utils.PlayerUtils.toVec3XYZ
import net.minusmc.minusbounce.utils.RotationUtils.targetRotation
import net.minusmc.minusbounce.utils.extensions.rotation
import kotlin.math.sqrt

/**
 * Serenity
 *
 * 8/6/24
 * Legit Velocity
 */
class Legit : VelocityMode("Legit") {
    private var attacked = false
    private var lastVelocity = false
    private var lastVelocityLocation = Vec3(0.0, 0.0, 0.0)
    private val possibleInput = mutableListOf(
        Input(-1f, 1f),
        Input(0f, 1f),
        Input(1f, 1f),
    )

    override fun onPacket(event: PacketEvent) {
        val packet = event.packet

        if(packet is S12PacketEntityVelocity && !lastVelocity){
            lastVelocityLocation = Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ)
            lastVelocity = true
        }
    }

    override fun onAttack(event: AttackEvent) {
        attacked = true
    }

    override fun onInput(event: MoveInputEvent) {
        attacked = false
        mc.objectMouseOver ?: return
        mc.thePlayer ?: return
        mc.theWorld ?: return

        if(lastVelocity && mc.thePlayer.hurtTime > 0){
            val playerPos = Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ)
            val yaw = targetRotation ?: mc.thePlayer.rotation
            val pStrafe = mutableListOf<Pair<Vec3, Input>>()

            possibleInput.forEach {
                val forward = it.forward
                val strafe = it.strafe

                pStrafe.add (
                    PlayerUtils.getPredictedPos(
                        forward,
                        strafe,
                        mc.thePlayer.motionX,
                        mc.thePlayer.motionY,
                        mc.thePlayer.motionZ,
                        playerPos.xCoord,
                        playerPos.yCoord,
                        playerPos.zCoord,
                        false,
                        yaw.yaw
                    ).toVec3XYZ() to Input(strafe, forward),
                )
            }

            val input = pStrafe.minByOrNull { (pos, _) ->
                val d0 = pos.xCoord - lastVelocityLocation.xCoord
                val d2 = pos.zCoord - lastVelocityLocation.zCoord
                sqrt(d0 * d0 + d2 * d2)
            } ?: return

            event.strafe = input.second.strafe
            event.sneak = false
        } else {
            lastVelocity = false
        }
    }
}
data class Input(val strafe: Float, val forward: Float)