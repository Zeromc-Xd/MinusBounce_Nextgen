package net.minusmc.minusbounce.features.module.modules.combat

import net.minecraft.client.entity.EntityOtherPlayerMP
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.item.EntityArmorStand
import net.minecraft.entity.player.EntityPlayer
import net.minusmc.minusbounce.MinusBounce
import net.minusmc.minusbounce.event.EventTarget
import net.minusmc.minusbounce.event.PreMotionEvent
import net.minusmc.minusbounce.features.module.Module
import net.minusmc.minusbounce.features.module.ModuleCategory
import net.minusmc.minusbounce.features.module.ModuleInfo
import net.minusmc.minusbounce.utils.ClientUtils
import net.minusmc.minusbounce.utils.EntityUtils
import net.minusmc.minusbounce.utils.RaycastUtils
import net.minusmc.minusbounce.utils.RotationUtils
import net.minusmc.minusbounce.utils.extensions.expands
import net.minusmc.minusbounce.utils.extensions.getNearestPointBB
import net.minusmc.minusbounce.utils.extensions.rotation
import net.minusmc.minusbounce.value.BoolValue
import net.minusmc.minusbounce.value.FloatValue
import net.minusmc.minusbounce.value.IntegerValue
import net.minusmc.minusbounce.value.ListValue
import kotlin.math.min


/* Modified class:
 * Minecraft.java
 */
@ModuleInfo("TimerRange", "Timer Range", "Make you walk to target faster", category = ModuleCategory.COMBAT)
object TimerRange: Module() {
    private val mode = ListValue("Mode", arrayOf("RayCast", "Radius"), "RayCast")
    private val rangeMode = ListValue("RangeMode", arrayOf("Setting", "Smart"), "Smart")
    private val minDistance: FloatValue = object : FloatValue("MinDistance", 3F, 0F, 4F) {
        override fun onChanged(oldValue: Float, newValue: Float) {
            if (newValue > maxDistance.get()) set(maxDistance.get())
        }
    }

    private val maxDistance: FloatValue = object : FloatValue("MaxDistance", 4F, 3F, 7F, { rangeMode.equals("Setting") }) {
        override fun onChanged(oldValue: Float, newValue: Float) {
            if (newValue < minDistance.get()) set(minDistance.get())
        }
    }

    private val maxTimeValue = IntegerValue("MaxTime", 3, 0, 20)
    private val delayValue = IntegerValue("Delay", 5, 0, 20)
    private val maxHurtTimeValue = IntegerValue("TargetMaxHurtTime", 2, 0, 10)
    private val onlyKillAura = BoolValue("OnlyKillAura", true)
    private val auraClick = BoolValue("AuraClick", true)
    private val onlyPlayer = BoolValue("OnlyPlayer", true)
    private val debug = BoolValue("Debug", false)
    private val betterAnimation = BoolValue("BetterAnimation", true)
    private val reverseValue = BoolValue("Reverse", false)
    private val maxReverseRange : FloatValue = object : FloatValue("MaxReverseRange", 2.8f, 1f, 4f) {
        override fun onChanged(oldValue: Float, newValue: Float) {
            if (newValue > minDistance.get() && minDistance.get() > minReverseRange.get()) set(minDistance.get())
            else if (newValue < minReverseRange.get()) set(minReverseRange.get())
        }
    }

    private val minReverseRange : FloatValue = object : FloatValue("MinReverseRange", 2.5f, 1f, 4f) {
        override fun onChanged(oldValue: Float, newValue: Float) {
            if (newValue > maxReverseRange.get()) set(maxReverseRange.get())
        }
    }
    private val reverseTime : IntegerValue = object : IntegerValue("ReverseStopTime", 3, 1, 10) {
        override fun onChanged(oldValue: Int, newValue: Int) {
            if (newValue < reverseTickTime.get()) set(reverseTickTime.get())
        }
    }
    private val reverseTickTime : IntegerValue = object : IntegerValue("ReverseTickTime", 3, 0, 10) {
        override fun onChanged(oldValue: Int, newValue: Int) {
            if (newValue > reverseTime.get()) set(reverseTime.get())
        }
    }
    private val reverseDelay = IntegerValue("ReverseDelay", 5, 0, 20)
    private val reverseTargetMaxHurtTime = IntegerValue("ReverseTargetMaxHurtTime", 3, 0, 10)
    private val reverseAuraClick = ListValue("ReverseAuraClick", arrayOf("None", "BeforeTimer", "AfterTimer"), "None")

    @JvmStatic
    private var working = false
    private var stopWorking = false
    private var lastNearest = 10.0
    private var cooldown = 0
    private var freezeTicks = 0
    private var reverseFreeze = true
    private var firstAnimation = true

    @EventTarget 
    fun onMotion(event: PreMotionEvent) {
        val killAura = MinusBounce.moduleManager[KillAura::class.java] ?: return
        val thePlayer = mc.thePlayer ?: return
        val rotation = RotationUtils.targetRotation ?: mc.thePlayer.rotation
        if (onlyKillAura.get() && !killAura.state) return
        if (mode.equals("RayCast")) {
            val entity = RaycastUtils.raycastEntity(maxDistance.get() + 1.0, rotation.yaw, rotation.pitch, object: RaycastUtils.IEntityFilter {
                override fun canRaycast(entity: Entity?): Boolean {
                    return entity != null && entity is EntityLivingBase && entity !is EntityArmorStand && (!onlyPlayer.get() || entity is EntityPlayer) && EntityUtils.isSelected(
                        entity,
                        true
                    )
                }
            })

            if (entity == null || entity !is EntityLivingBase) {
                lastNearest = 10.0
                return
            }
            if (!EntityUtils.isSelected(entity, true)) return
            val vecEyes = thePlayer.getPositionEyes(1f)
            val predictEyes = if (rangeMode.equals("Smart")) {
                thePlayer.getPositionEyes(maxTimeValue.get() + 1f)
            } else thePlayer.getPositionEyes(3f)
            val entityBox = entity.entityBoundingBox.expands(entity.collisionBorderSize.toDouble())
            val box = getNearestPointBB(
                vecEyes,
                entityBox
            )
            val box2 = getNearestPointBB(
                predictEyes,
                if (entity is EntityOtherPlayerMP) {
                    entityBox.offset(entity.otherPlayerMPX - entity.posX, entity.otherPlayerMPY - entity.posY, entity.otherPlayerMPZ - entity.posZ)
                } else entityBox
            )
            val range = box.distanceTo(vecEyes)
            val afterRange = box2.distanceTo(predictEyes)
            if (!working && reverseValue.get()) {
                if (range <= maxReverseRange.get() && range >= minReverseRange.get() && cooldown <= 0 && entity.hurtTime <= reverseTargetMaxHurtTime.get()) {
                    freezeTicks = reverseTime.get()
                    firstAnimation = false
                    reverseFreeze = true
                    return
                }
            }
            if (range < minDistance.get()) {
                stopWorking = true
            } else if (((rangeMode.equals("Smart") && range > minDistance.get() && afterRange < minDistance.get() && afterRange < range) || (rangeMode.equals("Setting") && range <= maxDistance.get() && range < lastNearest && afterRange < range)) && entity.hurtTime <= maxHurtTimeValue.get()) {
                stopWorking = false
                foundTarget()
            }
            lastNearest = range
        } else {
            val entityList = mc.theWorld.getEntitiesWithinAABBExcludingEntity(
                thePlayer,
                thePlayer.entityBoundingBox.expands(maxDistance.get() + 1.0)
            )
            if (entityList.isNotEmpty()) {
                val vecEyes = thePlayer.getPositionEyes(1f)
                val afterEyes = if (rangeMode.equals("Smart")) {
                    thePlayer.getPositionEyes(maxTimeValue.get() + 1f)
                } else thePlayer.getPositionEyes(3f)
                var targetFound = false
                var targetInRange = false
                var nearest = 10.0
                for (entity in entityList) {
                    if (entity !is EntityLivingBase) continue
                    if (onlyPlayer.get() && entity !is EntityPlayer) continue
                    if (!EntityUtils.isSelected(entity, true)) continue
                    val entityBox = entity.entityBoundingBox.expands(entity.collisionBorderSize.toDouble())
                    val box = getNearestPointBB(
                        vecEyes,
                        entityBox
                    )
                    val box2 = getNearestPointBB(
                        afterEyes,
                        if (entity is EntityOtherPlayerMP) {
                            entityBox.offset(entity.otherPlayerMPX - entity.posX, entity.otherPlayerMPY - entity.posY, entity.otherPlayerMPZ - entity.posZ)
                        } else entityBox
                    )
                    val range = box.distanceTo(vecEyes)
                    val afterRange = box2.distanceTo(afterEyes)
                    if (!working && reverseValue.get()) {
                        if (range <= maxReverseRange.get() && range >= minReverseRange.get() && cooldown <= 0 && entity.hurtTime <= reverseTargetMaxHurtTime.get()) {
                            freezeTicks = reverseTime.get()
                            firstAnimation = false
                            reverseFreeze = true
                            return
                        }
                    }
                    if (range < minDistance.get()) {
                        targetInRange = true
                        break
                    } else if (range <= maxDistance.get() && afterRange < range && entity.hurtTime <= maxHurtTimeValue.get()) {
                        targetFound = true
                    }
                    nearest = min(nearest, range)
                }
                if (targetInRange) {
                    stopWorking = true
                } else if (targetFound && nearest < lastNearest) {
                    stopWorking = false
                    foundTarget()
                }
                lastNearest = nearest
            } else {
                lastNearest = 10.0
            }
        }
    }

    private fun foundTarget() {
        val killAura = MinusBounce.moduleManager[KillAura::class.java] ?: return 
        if (cooldown > 0 || freezeTicks != 0 || maxTimeValue.get() == 0) return
        cooldown = delayValue.get()
        working = true
        freezeTicks = 0
        if (betterAnimation.get()) firstAnimation = false
        while (freezeTicks <= maxTimeValue.get() - (if (auraClick.get()) 1 else 0) && !stopWorking) {
            ++freezeTicks
            mc.runTick()
        }
        if (debug.get()) ClientUtils.displayChatMessage("Timer-ed")
        if (auraClick.get()) {
            killAura.clicks++
            ++freezeTicks
            mc.runTick()
            if (debug.get()) ClientUtils.displayChatMessage("Clicked")
        }
        stopWorking = false
        working = false
    }

    @JvmStatic
    fun handleTick(): Boolean {
        val killAura = MinusBounce.moduleManager[KillAura::class.java] ?: return false

        if (working || freezeTicks < 0) return true
        if (state && freezeTicks > 0) {
            --freezeTicks
            return true
        }
        if (reverseFreeze) {
            reverseFreeze = false
            var time = reverseTickTime.get()
            working = true
            if (reverseAuraClick.equals("BeforeTimer")) killAura.clicks++
            while (time > 0) {
                --time
                mc.runTick()
            }
            working = false
            cooldown = reverseDelay.get()
            if (reverseAuraClick.equals("AfterTimer")) killAura.clicks++
        }
        if (cooldown > 0) --cooldown
        return false
    }

    @JvmStatic
    fun freezeAnimation(): Boolean {
        if (freezeTicks != 0) {
            if (!firstAnimation) {
                firstAnimation = true
                return false
            }
            return true
        }
        return false
    }
}