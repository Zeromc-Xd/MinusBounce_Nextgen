/*
 * MinusBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/MinusMC/MinusBounce
 */
package net.minusmc.minusbounce.utils

import net.minecraft.client.Minecraft
import net.minecraft.client.entity.EntityPlayerSP
import net.minecraft.client.gui.GuiScreen
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.item.ItemStack
import net.minecraft.util.AxisAlignedBB
import net.minecraft.util.Vec3
import net.minusmc.minusbounce.injection.implementations.IEntity
import net.minusmc.minusbounce.injection.implementations.IEntityLivingBase
import net.minusmc.minusbounce.injection.implementations.IEntityPlayerSP
import net.minusmc.minusbounce.injection.implementations.IStack

open class MinecraftInstance {
    companion object {
        @JvmField val mc: Minecraft = Minecraft.getMinecraft()
        @JvmField var serverPosition = Vec3(0.0, 0.0, 0.0)
        @JvmField var lastServerPosition = serverPosition
        @JvmField var rotIncrement: Int = 0

        var runTimeTicks = 0
        var AABBOffGroundticks = 0
        val serverRotation: Rotation
            get() = (mc.thePlayer as IEntityPlayerSP).serverRotation


        val motionX: Double
            get() = mc.thePlayer.motionX

        val motionZ: Double
            get() = mc.thePlayer.motionZ

        val bb: AxisAlignedBB
            get() = mc.thePlayer.entityBoundingBox

        val eyesPos: Vec3
            get() = mc.thePlayer.getPositionEyes(mc.timer.renderPartialTicks)

        var EntityLivingBase.realPosX: Double
            get() = (this as IEntityLivingBase).realPosX
            set(value) {
                (this as IEntityLivingBase).realPosX = value
            }

        var EntityLivingBase.realPosY: Double
            get() = (this as IEntityLivingBase).realPosY
            set(value) {
                (this as IEntityLivingBase).realPosY = value
            }

        var EntityLivingBase.realPosZ: Double
            get() = (this as IEntityLivingBase).realPosZ
            set(value) {
                (this as IEntityLivingBase).realPosZ = value
            }

        var EntityPlayerSP.reSprint: Int
            get() = (this as IEntityPlayerSP).reSprint
            set(value) {
                (this as IEntityPlayerSP).reSprint = value
            }

        var Entity.ticksSprint: Int
            get() = (this as IEntity).ticksSprint
            set(value) {
                (this as IEntity).ticksSprint = value
            }

        var ItemStack.id: Int
            get() = (this as IStack).id
            set(v){
                (this as IStack).id = v
            }

        var serverInv: GuiScreen? = mc.currentScreen
    }
}
