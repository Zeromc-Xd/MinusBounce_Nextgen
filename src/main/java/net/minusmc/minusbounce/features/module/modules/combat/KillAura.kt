/*
 * MinusBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/MinusMC/MinusBounce
 */
package net.minusmc.minusbounce.features.module.modules.combat

import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.item.ItemSword
import net.minecraft.network.play.client.C03PacketPlayer
import net.minecraft.network.play.client.C07PacketPlayerDigging
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement
import net.minecraft.network.play.client.C0APacketAnimation
import net.minecraft.util.BlockPos
import net.minecraft.util.EnumFacing
import net.minecraft.util.MovingObjectPosition
import net.minecraft.util.Vec3
import net.minusmc.minusbounce.MinusBounce
import net.minusmc.minusbounce.event.*
import net.minusmc.minusbounce.features.module.Module
import net.minusmc.minusbounce.features.module.ModuleCategory
import net.minusmc.minusbounce.features.module.ModuleInfo
import net.minusmc.minusbounce.features.module.modules.combat.killaura.KillAuraBlocking
import net.minusmc.minusbounce.features.module.modules.world.Scaffold
import net.minusmc.minusbounce.utils.BadPacketUtils
import net.minusmc.minusbounce.utils.ClassUtils
import net.minusmc.minusbounce.utils.EntityUtils.isSelected
import net.minusmc.minusbounce.utils.Rotation
import net.minusmc.minusbounce.utils.RotationUtils
import net.minusmc.minusbounce.utils.RaycastUtils
import net.minusmc.minusbounce.utils.extensions.getDistanceToEntityBox
import net.minusmc.minusbounce.utils.extensions.getNearestPointBB
import net.minusmc.minusbounce.utils.misc.RandomUtils
import net.minusmc.minusbounce.utils.movement.MovementFixType
import net.minusmc.minusbounce.utils.timer.MSTimer
import net.minusmc.minusbounce.utils.timer.TimeUtils
import net.minusmc.minusbounce.value.*
import org.lwjgl.input.Keyboard
import org.lwjgl.opengl.GL11
import java.util.*
import kotlin.math.*

@Suppress("UNUSED_PARAMETER")
@ModuleInfo(name = "KillAura", spacedName = "Kill Aura", description = "Automatically attacks targets around you.", category = ModuleCategory.COMBAT, keyBind = Keyboard.KEY_R)
class KillAura : Module() {

    //AutoBlock modes
    private val blockingModes = ClassUtils.resolvePackage("${javaClass.`package`.name}.killaura.blocking", KillAuraBlocking::class.java)
        .map { it.newInstance() as KillAuraBlocking }
        .sortedBy { it.modeName }

    private val blockingMode: KillAuraBlocking
        get() = blockingModes.find { autoBlockModeValue.get().equals(it.modeName, true) } ?: throw NullPointerException()

    //CPS & HurtTime
    private val cps = IntRangeValue("CPS", 5, 8, 1, 20)
    private val hurtTimeValue = IntegerValue("HurtTime", 10, 0, 10)
    private val clickMode = ListValue("Click-Pattern", arrayOf("Legit", "Normal", "NormalNoise", "Blatant"), "Blatant")

    // Range & throughWalls
    private val rotationRangeValue = FloatValue("Rotation-Range", 3.7f, 1f, 8f, "m")
    private val range = FloatValue("Range", 3.7f, 1f, 8f, "m")
    private val fixServersSideMisplace = BoolValue("Fix-MisPlace", true)
    private val predict = BoolValue("Predict", false)
    private val doRandom = BoolValue("Random", false)
    private val throughWall = BoolValue("ThroughWall", true)
    private val outborder = BoolValue("OutBorder", true)

    // Rotations & TurnSpeed
    private val rotations = ListValue("Rotation-Mode", arrayOf("Vanilla", "BackTrack", "Grim"), "BackTrack")
    private val turnSpeed = FloatRangeValue("Speed", 180f, 180f, 0f, 180f, "°") {
        !rotations.get().equals("None", true)
    }

    //Target / Modes
    private val priorityValue = ListValue("Priority", arrayOf("Health", "Distance", "HurtResistance", "HurtTime", "Armor"), "Distance")
    val priority: String
        get() = priorityValue.get()

    val targetModeValue = ListValue("TargetMode", arrayOf("Single", "Switch"), "Switch")
    private val switchDelayValue = IntegerValue("SwitchDelay", 1000, 1, 2000, "ms") {
        targetModeValue.get().equals("switch", true)
    }

    // Bypass
    private val interact = BoolValue("Interact", true)
    private val noslow = BoolValue("NoSlow", true)
    private val noBadPackets = BoolValue("NoBadPackets", false)

    // AutoBlock & Interact
    val autoBlockModeValue: ListValue = object : ListValue("AutoBlock", blockingModes.map { it.modeName }.toTypedArray(), "None") {
        override fun onChange(oldValue: String, newValue: String) {
            if (state) onDisable()
        }

        override fun onChanged(oldValue: String, newValue: String) {
            if (state) onEnable()
        }
    }
    val autoBlockRangeValue = FloatValue("AutoBlock-Range", 5f, 0f, 12f, "m") {
        !autoBlockModeValue.get().equals("None", true)
    }

    // Rotation
    private val silentRotationValue = BoolValue("SilentRotation", true) { !rotations.get().equals("none", true) }
    private val movementCorrection = ListValue("MovementFix", arrayOf("None", "Normal", "Full"), "Full")

    // Visuals
    private val espModes = ListValue("ESP", arrayOf("Jello", "Off"), "Jello")
    private val circleValue = BoolValue("Circle", true)
    private val accuracyValue = IntegerValue("Accuracy", 59, 0, 59) { circleValue.get() }
    private val red = IntegerValue("Red", 255, 0, 255) { circleValue.get() }
    private val green = IntegerValue("Green", 255, 0, 255) { circleValue.get() }
    private val blue = IntegerValue("Blue", 255, 0, 255) { circleValue.get() }
    private val alpha = IntegerValue("Alpha", 255, 0, 255) { circleValue.get() }

    // Target
    private val prevTargetEntities = mutableListOf<Int>()
    private val discoveredEntities = mutableListOf<EntityLivingBase>()
    var target: EntityLivingBase? = null

    // autoclicker vars
    private var nextClickTime: Long = 0
    private val random = Random(System.currentTimeMillis())
    private var k: Long = 0
    private var l: Long = 0
    private var m = 0.0
    private var n = false

    // Attack delay
    private val attackTimer = MSTimer()
    private var attackDelay = 0L
    private val switchTimer = MSTimer()
    var clicks = 0
    var swing = false
    private var attackTickTimes = mutableListOf<Pair<MovingObjectPosition, Int>>()

    // Fake block status
    var blockingStatus = false

    private var correctedRange: Float = 0f

    private var packetSend: Boolean = false

    override fun onDisable() {
        target = null
        switchTimer.reset()
        attackTickTimes.clear()
        clicks = 0
        prevTargetEntities.clear()
        stopBlocking(false)
        blockingStatus = false
        blockingMode.onDisable()
    }

    override fun onEnable(){
        attackTickTimes.clear()
    }

    @EventTarget
    fun onWorldChange(event: WorldEvent) {
        attackTickTimes.clear()
    }

    @EventTarget
    fun onUpdate(event: PreUpdateEvent){
        val target = target ?: return

        RotationUtils.setRotations(
            rotation = getTargetRotation(target) ?: return,
            speed = RandomUtils.nextFloat(turnSpeed.getMinValue(), turnSpeed.getMaxValue()),
            fixType = when (movementCorrection.get().lowercase()) {
                "normal" -> MovementFixType.NORMAL
                "full" -> MovementFixType.FULL
                else -> MovementFixType.NONE
            },
            silent = silentRotationValue.get()
        )

        when(clickMode.get().lowercase()) {
            "normal", "normalnoise" -> {
                if (nextClickTime > 0L) {
                    if (System.currentTimeMillis() > nextClickTime) {
                        runAttack()
                        delay()
                    }
                } else {
                    delay()
                }
            }
            else -> repeat(clicks) { runAttack(); clicks-- }
        }
    }

    @EventTarget
    fun onRender3D(event: Render3DEvent) {
        if (circleValue.get()) {
            GL11.glPushMatrix()
            GL11.glTranslated(
                mc.thePlayer.lastTickPosX + (mc.thePlayer.posX - mc.thePlayer.lastTickPosX) * mc.timer.renderPartialTicks - mc.renderManager.renderPosX,
                mc.thePlayer.lastTickPosY + (mc.thePlayer.posY - mc.thePlayer.lastTickPosY) * mc.timer.renderPartialTicks - mc.renderManager.renderPosY,
                mc.thePlayer.lastTickPosZ + (mc.thePlayer.posZ - mc.thePlayer.lastTickPosZ) * mc.timer.renderPartialTicks - mc.renderManager.renderPosZ
            )
            GL11.glEnable(GL11.GL_BLEND)
            GL11.glEnable(GL11.GL_LINE_SMOOTH)
            GL11.glDisable(GL11.GL_TEXTURE_2D)
            GL11.glDisable(GL11.GL_DEPTH_TEST)
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)

            GL11.glLineWidth(1F)
            GL11.glColor4f(red.get().toFloat() / 255.0F, green.get().toFloat() / 255.0F, blue.get().toFloat() / 255.0F, alpha.get().toFloat() / 255.0F)
            GL11.glRotatef(90F, 1F, 0F, 0F)
            GL11.glBegin(GL11.GL_LINE_STRIP)

            val range = max(rotationRangeValue.get(), range.get())

            for (i in 0..360 step 60 - accuracyValue.get()) { // You can change circle accuracy  (60 - accuracy)
                GL11.glVertex2f(cos(i * Math.PI / 180.0).toFloat() * range, (sin(i * Math.PI / 180.0).toFloat() * range))
            }

            GL11.glEnd()

            GL11.glDisable(GL11.GL_BLEND)
            GL11.glEnable(GL11.GL_TEXTURE_2D)
            GL11.glEnable(GL11.GL_DEPTH_TEST)
            GL11.glDisable(GL11.GL_LINE_SMOOTH)

            GL11.glPopMatrix()
        }

        target ?: return

        if((target?.hurtTime ?: return) <= hurtTimeValue.get()){
            when(clickMode.get().lowercase()){
                "legit" -> {
                    if (attackTimer.hasTimePassed(attackDelay)) {
                        attackTimer.reset()

                        val e = ClickingEvent()
                        MinusBounce.eventManager.callEvent(e)
                        if(e.isCancelled) {
                            attackDelay += 50L
                        } else {
                            clicks++
                            attackDelay = TimeUtils.randomClickDelay(cps.getMinValue(), cps.getMaxValue())
                        }
                    }
                }
                "blatant" -> {
                    if (attackTimer.hasTimePassed(attackDelay)) {
                        clicks++
                        attackTimer.reset()
                        attackDelay = TimeUtils.randomClickDelay(cps.getMinValue(), cps.getMaxValue())
                    }
                }
            }
        }

        /* Draw ESP */
        when(espModes.get().lowercase()){
            "jello" -> drawCircle(target!!)
        }
    }

    private fun delay() {
        val minCps = cps.getMinValue()
        val maxCps = cps.getMaxValue()

        var c = 1000 / TimeUtils.randomClickDelay(minCps, maxCps) + 0.4 * random.nextDouble()
        if(clickMode.get().equals("normalnoise", true)){
            val proximityFactor = random.nextDouble() + 0.01
            val baseC = maxCps - (random.nextDouble() * (maxCps - minCps) * proximityFactor)
            val noise = (random.nextGaussian() * 0.1) + (0.2 * sin(System.nanoTime() / 1_000_000_000.0))
            c = (baseC + noise).coerceIn(minCps.toDouble(), maxCps.toDouble())
        }

        var d = (1000.0 / c).roundToLong()

        if (System.currentTimeMillis() > k) {
            if (!n && random.nextInt(100) >= 85) {
                n = true
                m = 1.1 + random.nextDouble() * 0.15
            } else {
                n = false
            }

            k = System.currentTimeMillis() + 500L + random.nextInt(1500)
        }

        if (n) d *= m.toLong()

        if (System.currentTimeMillis() > l) {
            if (random.nextInt(100) >= 80) {
                d += 50L + random.nextInt(100)
            }

            l = System.currentTimeMillis() + 500L + random.nextInt(1500)
        }

        nextClickTime = System.currentTimeMillis() + d
    }

    /**
     * Sigma Jello Mark
     *
     */
    private fun drawCircle(it: EntityLivingBase) {
        val drawTime = (System.currentTimeMillis() % 2000).toInt()
        val drawMode=drawTime>1000
        var drawPercent=drawTime/1000.0

        if(!drawMode){
            drawPercent = 1 -drawPercent
        }else{
            drawPercent-=1
        }

        drawPercent = if (drawPercent < 0.5) { 2 * drawPercent * drawPercent } else { 1 - (-2 * drawPercent + 2).pow(2) / 2 }
        val points = mutableListOf<Vec3>()

        val bb = it.entityBoundingBox
        val radius = bb.maxX-bb.minX
        val height = bb.maxY-bb.minY

        val posX = it.lastTickPosX + (it.posX - it.lastTickPosX) * mc.timer.renderPartialTicks
        var posY = it.lastTickPosY + (it.posY - it.lastTickPosY) * mc.timer.renderPartialTicks
        if(drawMode){
            posY -= 0.5
        }else{
            posY += 0.5
        }
        val posZ = it.lastTickPosZ + (it.posZ - it.lastTickPosZ) * mc.timer.renderPartialTicks

        for(i in 0..360 step 7){
            points.add(Vec3(posX - sin(i * Math.PI / 180F) * radius,posY+height*drawPercent,posZ + cos(i * Math.PI / 180F) * radius))
        }
        points.add(points[0])

        mc.entityRenderer.disableLightmap()
        GL11.glPushMatrix()
        GL11.glDisable(GL11.GL_TEXTURE_2D)
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
        GL11.glEnable(GL11.GL_LINE_SMOOTH)
        GL11.glEnable(GL11.GL_BLEND)
        GL11.glDisable(GL11.GL_DEPTH_TEST)
        GL11.glBegin(GL11.GL_LINE_STRIP)

        val baseMove= (if(drawPercent>0.5) 1-drawPercent else drawPercent)*2
        val min=(height/60)*20*(1-baseMove)*(if(drawMode) -1 else 1)

        for(i in 0..20) {
            var moveFace=(height/60F)*i*baseMove
            if(drawMode){
                moveFace=-moveFace
            }
            val firstPoint=points[0]
            GL11.glVertex3d(
                firstPoint.xCoord - mc.renderManager.viewerPosX, firstPoint.yCoord - moveFace - min - mc.renderManager.viewerPosY,
                firstPoint.zCoord - mc.renderManager.viewerPosZ
            )
            GL11.glColor4f(1F, 1F, 1F, 0.7F*(i/20F))
            for (vec3 in points) {
                GL11.glVertex3d(
                    vec3.xCoord - mc.renderManager.viewerPosX, vec3.yCoord - moveFace - min - mc.renderManager.viewerPosY,
                    vec3.zCoord - mc.renderManager.viewerPosZ
                )
            }
            GL11.glColor4f(0F,0F,0F,0F)
        }
        GL11.glEnd()
        GL11.glEnable(GL11.GL_DEPTH_TEST)
        GL11.glDisable(GL11.GL_LINE_SMOOTH)
        GL11.glDisable(GL11.GL_BLEND)
        GL11.glEnable(GL11.GL_TEXTURE_2D)
        GL11.glPopMatrix()
    }

    private fun runAttack() {
        if(BadPacketUtils.bad(slot = false, attack = true, swing = true, block = true, inventory = true) &&
            (autoBlockModeValue.get().equals("none", true)
            || autoBlockModeValue.get().equals("reblock", true))
        ){
            return
        }

        val target = this.target ?: return
        val objectMouseOver = mc.objectMouseOver

        if (objectMouseOver != null && objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY && objectMouseOver.entityHit == target) {
            runAttackWithMouseOver(target)
            return
        }

        val rotation = RotationUtils.targetRotation ?: getTargetRotation(target) ?: return
        val wallRange = if (throughWall.get()) range.get().toDouble() else 0.0

        RaycastUtils.runWithModifiedRaycastResult(rotation, range.get().toDouble(), wallRange) { result ->
            if (result.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY && result.entityHit == target) {
                runAttackWithMouseOver(target)
            }
        }
    }

    private fun runAttackWithMouseOver(target: Entity) {
        if (canBlock && blockingStatus && !autoBlockModeValue.get().equals("none", true)) {
            if(packetSend && noBadPackets.get()){
                return
            }

            blockingMode.onPreAttack()
            packetSend = true
        }

        mc.leftClickCounter = 10

        mc.clickMouse()

        if (canBlock && !blockingStatus && mc.thePlayer.getDistanceToEntityBox(target) <= autoBlockRangeValue.get() && !autoBlockModeValue.get().equals("none", true)) {
            if(packetSend && noBadPackets.get()){
                return
            }

            blockingMode.onPostAttack()
            packetSend = true
        }

        if (targetModeValue.get().equals("Switch", true)) {
            if (switchTimer.hasTimePassed(switchDelayValue.get().toLong())) {
                prevTargetEntities.add(target.entityId)
                switchTimer.reset()
            }
        }
    }


    @EventTarget(priority = -5)
    fun onRayTrace(e: RayTraceRangeEvent){
        if (target != null) {
            correctedRange = range.get() + if (this.fixServersSideMisplace.get()) 0.00256f else 0f
            if (this.fixServersSideMisplace.get()) {
                val n = 0.010625f
                if (mc.thePlayer.horizontalFacing === EnumFacing.NORTH || mc.thePlayer.horizontalFacing === EnumFacing.WEST) {
                    correctedRange += n * 2.0f
                }
            }
            e.range = correctedRange
            e.blockReachDistance = max(mc.playerController.blockReachDistance, correctedRange)
        }
    }
    private fun updateTarget() {
        discoveredEntities.clear()

        for (entity in mc.theWorld.loadedEntityList) {
            if (entity !is EntityLivingBase || !isSelected(entity, true) || (targetModeValue.get().equals("switch", true) && prevTargetEntities.contains(entity.entityId))) {
                continue
            }

            if (mc.thePlayer.getDistanceToEntityBox(entity) <= rotationRangeValue.get()) {
                discoveredEntities.add(entity)
            }
        }

        when (priorityValue.get().lowercase()) {
            "health" -> discoveredEntities.minByOrNull { it.health + it.absorptionAmount }
            "hurtresistance" -> discoveredEntities.minByOrNull { it.hurtResistantTime }
            "hurttime" -> discoveredEntities.minByOrNull { it.hurtTime }
            "armor" -> discoveredEntities.minByOrNull { it.totalArmorValue }
            else -> discoveredEntities.minByOrNull { mc.thePlayer.getDistanceToEntityBox(it) }
        }?.let {
            target = it
            return
        }

        target = null

        if (prevTargetEntities.isNotEmpty()) {
            prevTargetEntities.clear()
            updateTarget()
        }
    }

    private fun getTargetRotation(entity: Entity): Rotation? {
        val boundingBox = entity.entityBoundingBox

        /* We don't override. So Vec3(0.0, 0.0, 0.0) is a good solution */
        val rotation = RotationUtils.searchCenter(
            entity.entityBoundingBox,
            outborder.get(),
            doRandom.get(),
            predict.get(),
            throughWall.get()
        )?.rotation

        return when (rotations.get().lowercase()) {
            /* Old BackTrack Rotation Function is getting vecRotation and DO NOTHING with it. then calculate from vec (input) */
            "backtrack" -> RotationUtils.toRotation(RotationUtils.getCenter(entity.entityBoundingBox))
            "grim" -> RotationUtils.toRotation(getNearestPointBB(mc.thePlayer.getPositionEyes(1F), boundingBox))
            else -> rotation
        }
    }

    fun startBlocking(check: Boolean, interact: Boolean) {
        if(!blockingStatus || !check){
            if(interact && this.interact.get()){
                val mouseOver = mc.objectMouseOver
                val target = mouseOver?.entityHit
                if (mouseOver != null && target != null && mouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY) {
                    mc.playerController.isPlayerRightClickingOnEntity(mc.thePlayer, target, mouseOver)
                    mc.playerController.interactWithEntitySendPacket(mc.thePlayer, target)
                }
            }

            mc.netHandler.addToSendQueue(C08PacketPlayerBlockPlacement(mc.thePlayer.inventory.getCurrentItem() ?: return))
            blockingStatus = true
        }
    }

    fun stopBlocking(swingCheck: Boolean) {
        if(blockingStatus && (!swingCheck || !swing)){
            if(!mc.gameSettings.keyBindUseItem.isKeyDown){
                mc.netHandler.addToSendQueue(C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN))
            } else {
                mc.gameSettings.keyBindUseItem.pressed = false
            }
            blockingStatus = false
        }
    }

    private val canBlock: Boolean
        get() = try { mc.thePlayer.heldItem != null && mc.thePlayer.heldItem.item is ItemSword } catch (_: Exception) { false }

    override val tag: String
        get() = targetModeValue.get()

    @EventTarget
    fun onPreMotion(event: PreMotionEvent) {
        if(target == null || mc.thePlayer.isDead || MinusBounce.moduleManager.getModule(Scaffold::class.java)?.state ?: return){
            stopBlocking(false)
            blockingStatus = false
            return
        }

        blockingMode.onPreMotion()
    }

    @EventTarget
    fun onPostMotion(event: PostMotionEvent) {
        packetSend = false
        updateTarget()

        blockingMode.onPostMotion()
    }

    @EventTarget(priority = -10)
    fun onSlowDown(event: SlowDownEvent) {
        mc.thePlayer ?: return
        mc.theWorld ?: return

        if (canBlock && blockingStatus && noslow.get()) {
            event.forward = 1.0F
            event.strafe = 1.0F
        }

        blockingMode.onSlowDown(event)
    }

    @EventTarget
    fun onPacket(event: PacketEvent) {
        val packet = event.packet

        if(event.eventType == EventState.SEND){
            when(packet){
                is C0APacketAnimation -> swing = true
                is C03PacketPlayer -> swing = false
            }
        }

        blockingMode.onPacket(event)
    }
}
