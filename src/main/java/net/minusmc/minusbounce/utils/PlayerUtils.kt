package net.minusmc.minusbounce.utils

import net.minecraft.block.*
import net.minecraft.client.Minecraft
import net.minecraft.client.entity.EntityPlayerSP
import net.minecraft.client.multiplayer.WorldClient
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.EnumCreatureAttribute
import net.minecraft.entity.SharedMonsterAttributes
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemBlock
import net.minecraft.item.ItemEnderPearl
import net.minecraft.item.ItemPotion
import net.minecraft.item.ItemStack
import net.minecraft.potion.Potion
import net.minecraft.util.*
import net.minusmc.minusbounce.MinusBounce
import net.minusmc.minusbounce.event.KnockBackEvent
import net.minusmc.minusbounce.features.module.modules.movement.Sprint
import net.minusmc.minusbounce.utils.MinecraftInstance.Companion.mc


object PlayerUtils {
	fun getSlimeSlot(): Int {
        for(i in 36..44) {
            val stack = mc.thePlayer.inventoryContainer.getSlot(i).stack
            if (stack != null && stack.item != null) {
            	if (stack.item is ItemBlock) {
            		val item = stack.item as ItemBlock
	            	if (item.getBlock() is BlockSlime) return i - 36
            	}
            }
        }
        return -1
    }

    /**
     * Gets the block at a position
     *
     * @return block
     */
    fun block(x: Double, y: Double, z: Double): Block {
        return mc.theWorld.getBlockState(BlockPos(x, y, z)).block
    }

    fun getPearlSlot(): Int {
        for(i in 36..44) {
            val stack = mc.thePlayer.inventoryContainer.getSlot(i).stack
            if (stack != null && stack.item is ItemEnderPearl) return i - 36
        }
        return -1
    }

    fun isHealPotion(stack: ItemStack): Boolean {
        val itempotion = ItemPotion()
        val effects = itempotion.getEffects(stack)
        for (effect in effects) {
            if (effect.effectName == "potion.heal") return true
        }
        return false
    }

    fun getHealPotion(): Int {
        for (i in 36..44) {
            val stack = mc.thePlayer.inventoryContainer.getSlot(i).stack
            if(stack != null && stack.item is ItemPotion && isHealPotion(stack)) return i - 36
        }
        return -1
    }

    val isOnEdge: Boolean
        get() = mc.thePlayer.onGround && !mc.thePlayer.isSneaking && !mc.gameSettings.keyBindSneak.isKeyDown && !mc.gameSettings.keyBindJump.isKeyDown && mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, mc.thePlayer.entityBoundingBox.offset(0.0, -0.5, 0.0).expand(-0.001, 0.0, -0.001)).isEmpty()

    val isOnIce: Boolean
        get() {
            val player = mc.thePlayer
            val blockUnder = mc.theWorld.getBlockState(BlockPos(player.posX, player.posY - 1.0, player.posZ)).block
            return blockUnder is BlockIce || blockUnder is BlockPackedIce
        }

    val isBlockUnder: Boolean
        get() {
            if (mc.thePlayer == null) return false
            if (mc.thePlayer.posY < 0.0) {
                return false
            }
            var off = 0
            while (off < mc.thePlayer.posY.toInt() + 2) {
                val bb = mc.thePlayer.entityBoundingBox.offset(0.0, -off.toDouble(), 0.0)
                if (mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, bb).isNotEmpty()) {
                    return true
                }
                off += 2
            }
            return false
        }

    fun predictPosition(entity: EntityPlayer, predictTicks: Int): DoubleArray {
        val diffX = entity.prevPosX - entity.posX
        val diffZ = entity.prevPosZ - entity.posZ
        var posX = entity.posX
        var posZ = entity.posZ
        for (i in 0..predictTicks) {
            posX -= diffX * i
            posZ -= diffZ * i
        }
        return doubleArrayOf(posX, posZ)
    }


    fun getPredictedPos(isHitting: Boolean, targetEntity: Entity, forward: Float, strafe: Float, yaw: Float): Vec3 {
        var strafe = strafe * 0.98f
        var forward = forward * 0.98f
        var f4 = 0.91f
        var motionX = mc.thePlayer.motionX
        var motionZ = mc.thePlayer.motionZ
        var motionY = mc.thePlayer.motionY
        var isSprinting = mc.thePlayer.isSprinting

        if (isHitting) {
            val attackDamage = mc.thePlayer.getEntityAttribute(SharedMonsterAttributes.attackDamage).attributeValue.toFloat()
            val enchantmentDamage = if (targetEntity is EntityLivingBase) {
                EnchantmentHelper.getModifierForCreature(mc.thePlayer.heldItem, targetEntity.creatureAttribute)
            } else {
                EnchantmentHelper.getModifierForCreature(mc.thePlayer.heldItem, EnumCreatureAttribute.UNDEFINED)
            }

            if (attackDamage > 0.0f || enchantmentDamage > 0.0f) {
                var knockbackModifier = EnchantmentHelper.getKnockbackModifier(mc.thePlayer)
                if (mc.thePlayer.isSprinting) knockbackModifier++

                val attacked = targetEntity.attackEntityFrom(DamageSource.causePlayerDamage(mc.thePlayer), attackDamage)
                if (attacked) {
                    if (knockbackModifier > 0) {
                        val event = KnockBackEvent(0.6, false, 1, 0, false, false, false, false)
                        MinusBounce.eventManager.callEvent(event)

                        if(!event.isCancelled){
                            motionX *= event.motion
                            motionZ *= event.motion
                            if (!event.sprint) isSprinting = false
                        }
                    }
                }
            }
        }

        if (mc.thePlayer.isJumping && mc.thePlayer.onGround && mc.thePlayer.jumpTicks == 0) {
            motionY = 0.42
            if (mc.thePlayer.isPotionActive(Potion.jump)) {
                motionY += (mc.thePlayer.getActivePotionEffect(Potion.jump).amplifier + 1) * 0.1f
            }
            if (isSprinting) {
                val yawRadians = yaw * 0.017453292f
                motionX -= MathHelper.sin(yawRadians) * 0.2f
                motionZ += MathHelper.cos(yawRadians) * 0.2f
            }
        }

        if (mc.thePlayer.onGround) {
            f4 = mc.thePlayer.worldObj.getBlockState(BlockPos(MathHelper.floor_double(mc.thePlayer.posX), MathHelper.floor_double(mc.thePlayer.entityBoundingBox.minY) - 1, MathHelper.floor_double(mc.thePlayer.posZ)))
                .block.slipperiness * 0.91f
        }

        val friction = if (mc.thePlayer.onGround) {
            var moveSpeed = mc.thePlayer.aiMoveSpeed * (0.16277136f / (f4 * f4 * f4))
            if (mc.thePlayer == Minecraft.getMinecraft().thePlayer &&
                MinusBounce.moduleManager.getModule(Sprint::class.java)?.state == true
            ) {
                moveSpeed = 0.12999998f
            }
            moveSpeed
        } else {
            mc.thePlayer.jumpMovementFactor
        }

        val magnitude = strafe * strafe + forward * forward
        if (magnitude >= 1.0E-4f) {
            val normalized = MathHelper.sqrt_float(magnitude).coerceAtLeast(1.0f)
            val scale = friction / normalized
            strafe *= scale
            forward *= scale
            val yawRadians = yaw * Math.PI.toFloat() / 180.0f
            motionX += strafe * MathHelper.cos(yawRadians) - forward * MathHelper.sin(yawRadians)
            motionZ += forward * MathHelper.cos(yawRadians) + strafe * MathHelper.sin(yawRadians)
        }

        motionY *= 0.9800000190734863
        motionX *= f4
        motionZ *= f4

        return Vec3(motionX, motionY, motionZ)
    }

    fun getPredictedPos(
        forward: Float,
        strafe: Float,
        motionX: Double,
        motionY: Double,
        motionZ: Double,
        posX: Double,
        posY: Double,
        posZ: Double,
        isJumping: Boolean,
        yaw: Float,
    ): Position {
        var strafe = strafe * 0.98f
        var forward = forward * 0.98f
        var motionX = motionX
        var motionY = motionY
        var motionZ = motionZ
        var f4 = 0.91f
        val isSprinting = mc.thePlayer.isSprinting

        if (isJumping && mc.thePlayer.onGround && mc.thePlayer.jumpTicks == 0) {
            motionY = 0.42
            if (mc.thePlayer.isPotionActive(Potion.jump)) {
                motionY += (mc.thePlayer.getActivePotionEffect(Potion.jump).amplifier + 1) * 0.1f
            }
            if (isSprinting) {
                val yawRadians = yaw * 0.017453292f
                motionX -= MathHelper.sin(yawRadians) * 0.2f
                motionZ += MathHelper.cos(yawRadians) * 0.2f
            }
        }

        if (mc.thePlayer.onGround) {
            f4 = mc.thePlayer.worldObj.getBlockState(BlockPos(
                MathHelper.floor_double(posX),
                MathHelper.floor_double(posY) - 1,
                MathHelper.floor_double(posZ)
            )).block.slipperiness * 0.91f
        }

        val f6 = 0.16277136f / (f4 * f4 * f4)
        val friction = if (mc.thePlayer.onGround) {
            var moveSpeed = mc.thePlayer.aiMoveSpeed * f6
            if (mc.thePlayer == Minecraft.getMinecraft().thePlayer &&
                MinusBounce.moduleManager.getModule(Sprint::class.java)?.state == true
            ) {
                moveSpeed = 0.12999998f
            }
            moveSpeed
        } else {
            mc.thePlayer.jumpMovementFactor
        }

        var f7 = strafe * strafe + forward * forward
        if (f7 >= 1.0E-4f) {
            f7 = MathHelper.sqrt_float(f7)
            if (f7 < 1.0f) f7 = 1.0f
            f7 = friction / f7
            strafe *= f7
            forward *= f7
            val yawRadians = yaw * Math.PI.toFloat() / 180.0f
            val f8 = MathHelper.sin(yawRadians)
            val f9 = MathHelper.cos(yawRadians)
            motionX += strafe * f9 - forward * f8
            motionZ += forward * f9 + strafe * f8
        }

        var newPosX = posX + motionX
        var newPosY = posY + motionY
        var newPosZ = posZ + motionZ

        f4 = 0.91f
        if (mc.thePlayer.onGround) {
            f4 = mc.thePlayer.worldObj.getBlockState(BlockPos(
                MathHelper.floor_double(newPosX),
                MathHelper.floor_double(mc.thePlayer.entityBoundingBox.minY) - 1,
                MathHelper.floor_double(newPosZ)
            )).block.slipperiness * 0.91f
        }

        if (mc.thePlayer.worldObj.isRemote && (!mc.thePlayer.worldObj.isBlockLoaded(BlockPos(newPosX.toInt(), 0, newPosZ.toInt())) ||
                    !mc.thePlayer.worldObj.getChunkFromBlockCoords(BlockPos(newPosX.toInt(), 0, newPosZ.toInt())).isLoaded())) {
            motionY = if (newPosY > 0.0) -0.1 else 0.0
        } else {
            motionY -= 0.08
        }

        motionY *= 0.9800000190734863
        motionX *= f4
        motionZ *= f4

        return Position(forward, strafe, motionX, motionY, motionZ, newPosX, newPosY, newPosZ, isJumping, yaw)
    }

    fun Position.toVec3XYZ() = Vec3(this.posX, this.posY, this.posZ)


    /**
     * Checks if the player is inside a block
     *
     * @return inside block
     */
    fun insideBlock(): Boolean {
        if (mc.thePlayer.ticksExisted < 5) {
            return false
        }

        val player: EntityPlayerSP = mc.thePlayer
        val world: WorldClient = mc.theWorld
        val bb = player.entityBoundingBox
        for (x in MathHelper.floor_double(bb.minX) until MathHelper.floor_double(bb.maxX) + 1) {
            for (y in MathHelper.floor_double(bb.minY) until MathHelper.floor_double(bb.maxY) + 1) {
                for (z in MathHelper.floor_double(bb.minZ) until MathHelper.floor_double(bb.maxZ) + 1) {
                    val block = world.getBlockState(BlockPos(x, y, z)).block
                    var boundingBox: AxisAlignedBB? = null
                    if (block != null && block !is BlockAir && (block.getCollisionBoundingBox(
                            world,
                            BlockPos(x, y, z),
                            world.getBlockState(BlockPos(x, y, z))
                        ).also {
                            boundingBox = it
                        }) != null && player.entityBoundingBox.intersectsWith(boundingBox)
                    ) {
                        return true
                    }
                }
            }
        }
        return false
    }
}

data class Position(
    var forward: Float,
    var strafe: Float,
    var motionX: Double,
    var motionY: Double,
    var motionZ: Double,
    var posX: Double,
    var posY: Double,
    var posZ: Double,
    var isJumping: Boolean,
    var yaw: Float,
)
