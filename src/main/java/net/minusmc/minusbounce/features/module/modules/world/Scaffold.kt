package net.minusmc.minusbounce.features.module.modules.world

import net.minecraft.block.*
import net.minecraft.block.material.Material
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.item.ItemBlock
import net.minecraft.network.play.client.C03PacketPlayer.C05PacketPlayerLook
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement
import net.minecraft.network.play.client.C0BPacketEntityAction
import net.minecraft.network.play.server.S2FPacketSetSlot
import net.minecraft.util.*
import net.minusmc.minusbounce.MinusBounce
import net.minusmc.minusbounce.event.*
import net.minusmc.minusbounce.features.module.Module
import net.minusmc.minusbounce.features.module.ModuleCategory
import net.minusmc.minusbounce.features.module.ModuleInfo
import net.minusmc.minusbounce.features.module.modules.combat.KillAura
import net.minusmc.minusbounce.features.module.modules.world.scaffold.ScaffoldMovementPlanner
import net.minusmc.minusbounce.features.module.modules.world.scaffold.ScaffoldMovementPrediction
import net.minusmc.minusbounce.injection.access.StaticStorage
import net.minusmc.minusbounce.ui.font.Fonts
import net.minusmc.minusbounce.utils.*
import net.minusmc.minusbounce.utils.InventoryUtils.BLOCK_BLACKLIST
import net.minusmc.minusbounce.utils.InventoryUtils.invalidBlocks
import net.minusmc.minusbounce.utils.MovementUtils.isMoving
import net.minusmc.minusbounce.utils.RotationUtils.getRotationDifference
import net.minusmc.minusbounce.utils.block.BlockUtils.rayTrace
import net.minusmc.minusbounce.utils.block.PlaceInfo
import net.minusmc.minusbounce.utils.block.ScaffoldBlockPlacer
import net.minusmc.minusbounce.utils.extensions.*
import net.minusmc.minusbounce.utils.misc.RandomUtils
import net.minusmc.minusbounce.utils.movement.MovementFixType
import net.minusmc.minusbounce.utils.render.RenderUtils
import net.minusmc.minusbounce.utils.timer.MSTimer
import net.minusmc.minusbounce.value.*
import org.lwjgl.opengl.GL11
import java.awt.Color
import kotlin.math.*


@Suppress("UNUSED_PARAMETER")
@ModuleInfo("Scaffold", "Scaffold", "Use huge balls to rolling on mid-air", ModuleCategory.WORLD)
class Scaffold: Module(){
    private val modes = ListValue("Mode", arrayOf("Normal", "Snap", "Telly", "Legit", "GodBridge"), "Normal")
    private val rotationMode = object : ListValue("Rotations", arrayOf("Normal", "None"), "Normal") {
        override fun onChanged(oldValue: String, newValue: String) {
            if (newValue.equals("None", true)) {
                RotationUtils.resetRotationOwner("Scaffold")
            }
        }
    }
    private val searchModeValue = ListValue("AimMode", arrayOf("Area", "Center", "TryRotation"), "Center")
    private val yawOffset = ListValue("OffsetYaw", arrayOf("Dynamic", "Side"), "Dynamic") { searchModeValue.get() == "TryRotation" }
    private val reset = BoolValue("RotationActivateReset", false) { modes.get() == "Telly" || modes.get() == "Snap" }
    private val rotationTiming = ListValue("RotationTiming", arrayOf("Normal", "OnTick", "OnTickSnap"), "Normal")
    private val rayTrace = ListValue("RayTraceMethod", arrayOf("Calculate", "Client", "None"), "Calculate")
    private val ticks = IntegerValue("Ticks", 3, 0, 10) { modes.get() == "Telly" }
    private val delayValue = IntRangeValue("Delay", 0, 0, 0, 10)
    private val sprint = ListValue("Sprint", arrayOf("Normal", "Legit", "VulcanToggle", "Omni", "Off"), "Normal")
    private val noExpand = BoolValue("NoExpand", true)
    private val supportPlacement = BoolValue("SupportPlacement", true)
    private val supportDepth = IntegerValue("SupportDepth", 4, 1, 8) { supportPlacement.get() }
    private val constructFailResult = BoolValue("ConstructFailResult", true)

    private val godBridgeLedge = ListValue("GodBridgeLedge", arrayOf("Random", "Jump", "Sneak", "StopInput", "Backwards", "Off"), "Random") { modes.get().equals("GodBridge", true) }
    private val godBridgeForceSneakBelowCount = IntegerValue("GodBridgeForceSneakBelowCount", 5, 0, 10) { modes.get().equals("GodBridge", true) }
    private val godBridgeSneakTicks = IntegerValue("GodBridgeSneakTicks", 1, 1, 10) { modes.get().equals("GodBridge", true) }

    private val startSneak = BoolValue("StartSneak", true)
    private val speed = FloatRangeValue("Speed", 90f, 90f, 0f, 180f)

    private val eagleValue = ListValue("Eagle", arrayOf("Off", "Normal"), "Off")
    private val eagleEdgeDistanceValue = FloatRangeValue("EagleEdgeDistance", 0f, 0f, 0f, 0.2f) { !eagleValue.get().equals("off", true) }
    private val eagleBlocksValue = IntegerValue("EagleBlocks", 0, 0, 10) { eagleValue.get().equals("normal", true) }
    private val eagleSilent = BoolValue("Silent", false) { !eagleValue.get().equals("Off", true) }

    private val towerModeValue = ListValue("Tower", arrayOf("Off", "Motion", "Pulldown", "Karhu", "Vulcan", "Hypixel", "Air", "Legit", "MMC", "Matrix", "NCP", "Normal", "Vanilla", "WatchDog"), "Off")
    private val sameYValue = ListValue("SameY", arrayOf("Off", "Same", "AutoJump"), "Off")

    private val timer = FloatValue("Timer", 1f, 0f, 5f)
    private val safeWalk = BoolValue("SafeWalk", false)
    private val predict = object : BoolValue("Predict", true) {
        override fun onChanged(oldValue: Boolean, newValue: Boolean) {
            if (!newValue) ScaffoldMovementPrediction.reset()
        }
    }
    private val predictionBootstrapBackoff = FloatValue("PredictionBootstrapBackoff", 0.2f, 0f, 0.4f) { predict.get() }
    private val predictionCutoffDistance = FloatValue("PredictionCutoffDistance", 0.05f, 0f, 0.3f) { predict.get() }
    private val predictionWarmupPlacements = IntegerValue("PredictionWarmupPlacements", 2, 0, 4) { predict.get() }
    private val adStrafe = ListValue("ADStrafe", arrayOf("Always", "Edge", "None"))
    private val jumpAutomatically = BoolValue("JumpAutomatically", false)
    private val movementCorrection = BoolValue("MovementCorrection", true)

    private val counter = BoolValue("Counter", false)
    private val scaffoldRender = BoolValue("Render", true)

    /* Values */
    private var targetBlock: BlockPos? = null
    private var blockPlace: BlockPos? = null
    private var side: EnumFacing? = null
    private var enumFacing: EnumFacing? = null
    private var currentPlacementTarget: ScaffoldBlockPlacer.PlacementTarget? = null
    private var currentOptimalLine: ScaffoldMovementPlanner.MovementLine? = null

    private var adStrafeDirection: Boolean = false
    private val startTime = MSTimer()
    private val adStrafeTimer = MSTimer()
    private var xPos: Double = 0.0
    private var zPos: Double = 0.0
    private var startY = 0.0
    private var targetYaw = 0f
    private var targetPitch = 0f
    private var ticksOnAir = 0
    private var godBridgeOnRightSide = false
    private var godBridgeSneakTicksLeft = 0
    private var wasTowering = false
    private var towerJumpOffY = Double.NaN

    private var lastX: Double = 0.0
    private var lastZ: Double = 0.0

    // Render thingy
    private var progress = 0f
    private var lastMS = 0L
    private val renderedPlacements = mutableListOf<RenderedPlacement>()

    private var placedBlocksWithoutEagle = 0
    private var eagleSneaking = false

    private data class RenderedPlacement(val pos: BlockPos, val time: Long)

    override fun onDisable() {
        mc.gameSettings.keyBindUseItem.pressed = false
        mc.gameSettings.keyBindSneak.pressed = false
        mc.gameSettings.keyBindSprint.pressed = false
        mc.timer.timerSpeed = 1f
        RotationUtils.resetRotationOwner("Scaffold")
        godBridgeSneakTicksLeft = 0
        godBridgeOnRightSide = false
        currentPlacementTarget = null
        currentOptimalLine = null
        ScaffoldMovementPlanner.reset()
        ScaffoldMovementPrediction.reset()
        renderedPlacements.clear()
    }

    override fun onEnable() {
        targetYaw = MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw) - 180F
        targetPitch = 82F
        startY = floor(mc.thePlayer.posY)
        lastMS = System.currentTimeMillis()
        startTime.reset()
        godBridgeSneakTicksLeft = 0
        godBridgeOnRightSide = false
        currentPlacementTarget = null
        currentOptimalLine = null
        ScaffoldMovementPlanner.reset()
        ScaffoldMovementPrediction.reset()
    }

    @EventTarget
    fun onWorld(event: WorldEvent) {
        currentPlacementTarget = null
        currentOptimalLine = null
        ScaffoldMovementPlanner.reset()
        ScaffoldMovementPrediction.reset()
    }

    /* Init */
    private fun calculateSneaking() {
        if (eagleValue.get() == "Normal" && mc.thePlayer.onGround) {
            var dif = 0.5
            val edge = RandomUtils.nextFloat(eagleEdgeDistanceValue.getMinValue(), eagleEdgeDistanceValue.getMaxValue())
            val blockPos = BlockPos(mc.thePlayer).offset(EnumFacing.DOWN)

            if (edge > 0.0F) {
                for (facingType in StaticStorage.facings()) {
                    if (facingType == EnumFacing.UP || facingType == EnumFacing.DOWN) continue

                    val placeInfo = blockPos.offset(facingType)
                    if (blockPos.getBlock() is BlockAir) {
                        var calcDif = if (facingType == EnumFacing.NORTH || facingType == EnumFacing.SOUTH) {
                            abs(placeInfo.z + 0.5 - mc.thePlayer.posZ)
                        } else {
                            abs(placeInfo.x + 0.5 - mc.thePlayer.posX)
                        }

                        calcDif -= 0.5

                        if (calcDif < dif) {
                            dif = calcDif
                        }
                    }
                }
            }

            if (placedBlocksWithoutEagle >= eagleBlocksValue.get()) {
                val shouldEagle = blockPos.getBlock() is BlockAir || (edge > 0 && dif < edge)

                if (eagleSilent.get()) {
                    if (eagleSneaking != shouldEagle) {
                        mc.netHandler.addToSendQueue(C0BPacketEntityAction(mc.thePlayer, if (shouldEagle) C0BPacketEntityAction.Action.START_SNEAKING else C0BPacketEntityAction.Action.STOP_SNEAKING))
                    }

                    eagleSneaking = shouldEagle
                } else {
                    mc.gameSettings.keyBindSneak.pressed = shouldEagle
                }

                placedBlocksWithoutEagle = 0
            } else {
                placedBlocksWithoutEagle++
            }
        }
    }

    @EventTarget(priority = -5)
    fun onTick(event: EventTick){
        updateTowerState()

        if (targetBlock == null || blockPlace == null)
            return

        mc.timer.timerSpeed = timer.get()

        when (sprint.get().lowercase()) {
            "normal" -> {
                mc.gameSettings.keyBindSprint.pressed = true
            }
            "legit" -> {
                val player = MathHelper.wrapAngleTo180_float(MovementUtils.getPlayerDirection())
                val target = MathHelper.wrapAngleTo180_float(RotationUtils.targetRotation?.yaw ?: return)

                mc.gameSettings.keyBindSprint.pressed = if(RotationUtils.active) abs(player - target) > 90 - 22.5 else true
            }
            "omni" -> mc.thePlayer.isSprinting = true
            "vulcantoggle" -> {
                mc.netHandler.addToSendQueue(C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.START_SPRINTING))
                mc.netHandler.addToSendQueue(C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.STOP_SPRINTING))
            }
            else -> {
                mc.gameSettings.keyBindSprint.pressed = false
                mc.thePlayer.isSprinting = false
            }
        }

        handleTowerMotion(updateTowerState())
    }

    private fun handleTowerMotion(towering: Boolean) {
        if (!towering || !isBlockBelowTower()) {
            towerJumpOffY = Double.NaN
            return
        }

        when(towerModeValue.get().lowercase()){
            "motion" -> handleLiquidBounceMotionTower()
            "pulldown" -> handleLiquidBouncePulldownTower()
            "karhu" -> handleLiquidBounceKarhuTower()
            "hypixel" -> handleLiquidBounceHypixelTower()

            "air" -> {
                if (mc.thePlayer.ticksExisted % 2 == 0 && blockNear(2)) {
                    mc.thePlayer.motionY = 0.42
                    mc.thePlayer.onGround = true
                }
            }

            "legit" ->{
                if (mc.thePlayer.onGround) {
                    mc.thePlayer.jump()
                }
            }

            "matrix" -> {
                if (isBlockUnder(2.0, false) && mc.thePlayer.motionY < 0.2) {
                    mc.thePlayer.motionY = 0.42
                    mc.thePlayer.onGround = true
                }
            }

            "ncp" -> {
                if (blockNear(2)) {
                    PacketUtils.sendPacketNoEvent(C08PacketPlayerBlockPlacement(null))

                    if (mc.thePlayer.posY % 1 <= 0.00153598) {
                        mc.thePlayer.setPosition(mc.thePlayer.posX, floor(mc.thePlayer.posY), mc.thePlayer.posZ)
                        mc.thePlayer.motionY = 0.42
                    } else if (mc.thePlayer.posY % 1 < 0.1 && RotationUtils.offGroundTicks != 0) {
                        mc.thePlayer.motionY = 0.0
                        mc.thePlayer.setPosition(mc.thePlayer.posX, floor(mc.thePlayer.posY), mc.thePlayer.posZ)
                    }
                }
            }

            "normal" -> {
                if (mc.thePlayer.onGround) {
                    mc.thePlayer.motionY = 0.42
                }
            }

            "vanilla" -> {
                if (blockNear(2)) {
                    mc.thePlayer.motionY = 0.42
                }
            }

            "vulcan" -> handleLiquidBounceVulcanTower()

            "watchdog" -> {
                if (!isMoving) {
                    return
                }

                if (mc.thePlayer.onGround) {
                    mc.thePlayer.motionY = MovementUtils.getJumpBoostModifier(0.42F)
                    mc.thePlayer.motionX *= .65
                    mc.thePlayer.motionZ *= .65
                }
            }
        }
    }

    private fun handleLiquidBounceMotionTower() {
        if (mc.thePlayer.onGround || towerJumpOffY.isNaN()) {
            towerJumpOffY = mc.thePlayer.posY
            if (mc.thePlayer.onGround) {
                mc.thePlayer.motionY = 0.42
            }
            return
        }

        if (mc.thePlayer.posY > towerJumpOffY + 0.78) {
            mc.thePlayer.setPosition(mc.thePlayer.posX, truncate(mc.thePlayer.posY), mc.thePlayer.posZ)
            mc.thePlayer.motionY = 0.42
            towerJumpOffY = mc.thePlayer.posY
        }
    }

    private fun handleLiquidBouncePulldownTower() {
        if (mc.thePlayer.onGround) {
            mc.thePlayer.motionY = 0.42
        } else if (mc.thePlayer.motionY < 0.1) {
            mc.thePlayer.motionY = -1.0
        }
    }

    private fun handleLiquidBounceKarhuTower() {
        mc.timer.timerSpeed = 5f

        if (mc.thePlayer.onGround) {
            mc.thePlayer.motionY = 0.42
        } else if (mc.thePlayer.motionY < 0.06) {
            mc.thePlayer.motionY -= 1.0
        }
    }

    private fun handleLiquidBounceVulcanTower() {
        if (mc.thePlayer.ticksExisted % 2 == 0) {
            mc.thePlayer.motionY = 0.7
        } else {
            mc.thePlayer.motionY = if (isMoving) 0.42 else 0.6
        }
    }

    private fun handleLiquidBounceHypixelTower() {
        if (mc.thePlayer.posX % 1.0 != 0.0 && !isMoving) {
            mc.thePlayer.motionX = min(round(mc.thePlayer.posX) - mc.thePlayer.posX, 0.281)
        }

        if (RotationUtils.offGroundTicks > 14) {
            mc.thePlayer.motionY -= 0.09
            mc.thePlayer.motionX *= 0.6
            mc.thePlayer.motionZ *= 0.6
            return
        }

        when (RotationUtils.offGroundTicks % 3) {
            0 -> {
                mc.thePlayer.motionY = 0.42
                MovementUtils.strafe(0.247f - RandomUtils.nextFloat(0f, 0.01f))
            }
            2 -> mc.thePlayer.motionY = 1.0 - (mc.thePlayer.posY % 1.0)
        }
    }

    @EventTarget
    fun onPacket(event: PacketEvent){
        val packet = event.packet

        when(towerModeValue.get().lowercase()){
            "mmc" -> {
                if (mc.gameSettings.keyBindJump.isKeyDown && packet is C08PacketPlayerBlockPlacement) {
                    if (packet.getPosition() == BlockPos(
                            mc.thePlayer.posX,
                            mc.thePlayer.posY - 1.4,
                            mc.thePlayer.posZ
                        )
                    ) {
                        mc.gameSettings.keyBindSprint.pressed = false
                        mc.thePlayer.isSprinting = false
                        mc.thePlayer.motionY = 0.42
                    }
                }
            }

            "normal", "watchdog" -> {
                if (mc.thePlayer.motionY > -0.0784000015258789 && packet is C08PacketPlayerBlockPlacement) {
                    if (packet.getPosition() == BlockPos(
                            mc.thePlayer.posX,
                            mc.thePlayer.posY - 1.4,
                            mc.thePlayer.posZ
                        )
                    ) {
                        mc.thePlayer.motionY = -0.0784000015258789
                    }
                }
            }
        }

        if (packet is S2FPacketSetSlot) {
            if (packet.func_149174_e() == null) {
                event.cancelEvent()
                return
            }
            try {
                val slot = packet.func_149173_d() - 36
                if (slot < 0) return
                val itemStack = mc.thePlayer.inventory.getStackInSlot(slot)
                val item = packet.func_149174_e().item

                if ((itemStack == null && packet.func_149174_e().stackSize <= 6 && item is ItemBlock && item.block !in BLOCK_BLACKLIST) ||
                    (itemStack != null && abs(itemStack.stackSize - packet.func_149174_e().stackSize) <= 6) ||
                    (packet.func_149174_e() == null)
                ) {
                    event.cancelEvent()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @EventTarget
    fun onClick(e: PreUpdateEvent){
        if (BlockPos(mc.thePlayer).offset(EnumFacing.DOWN).getBlock() is BlockAir) {
            ticksOnAir++
        } else {
            ticksOnAir = 0
        }

        val blockSlot = InventoryUtils.findBlockInHotbar() ?: return
        if (blockSlot != -1) {
            mc.thePlayer.inventory.currentItem = blockSlot - 36
        }

        val towering = updateTowerState()
        val placementTarget = resolvePlacementTarget() ?: return
        applyPlacementTarget(placementTarget)

        if((mc.thePlayer.motionX != 0.0 || mc.thePlayer.motionZ != 0.0) && startTime.hasTimePassed(200)){
            if(mc.thePlayer.motionX == 0.0 && mc.thePlayer.motionY == 0.0){
                startTime.reset()
            }
        }

        calculateRotations()

        if (modes.get().equals("GodBridge", true) && !towering && !wasTowering) {
            updateGodBridgePlacementFromRotation()
        }

        val rotationTimingMode = rotationTiming.get().lowercase()
        if (rotationTimingMode == "normal") {
            setRotation()
        }

        calculateSneaking()

        if (sameYValue.get().equals("AutoJump", true)) {
            if(mc.thePlayer.onGround && (isMoving || mc.gameSettings.keyBindJump.isPressed)){
                mc.thePlayer.jump()
            }
        }

        if (startY - 1 != floor(targetBlock?.y?.toDouble() ?: return) && sameY) {
            return
        }

        if (usesRotations() && RotationUtils.active && !RotationUtils.isOwner("Scaffold") && RotationUtils.activeOwner != "Legacy") {
            return
        }

        val onTickRotation = Rotation(targetYaw, targetPitch).apply { fixedSensitivity() }
        val activePlacementRotation = if (!usesRotations() || rotationTimingMode == "ontick" || rotationTimingMode == "onticksnap") {
            onTickRotation
        } else {
            RotationUtils.getActiveRotation()
        }
        val (yaw, pitch) = activePlacementRotation

        val eyes = mc.thePlayer.eyes
        val rotX = yaw * Math.PI / 180f
        val rotY = pitch * Math.PI / 180f
        val look = Vec3(-cos(rotY) * sin(rotX), -sin(rotY), cos(rotY) * cos(rotX))
        val vec = eyes + (look * maxReach)

        val placeOn = blockPlace ?: return
        val theWorld = mc.theWorld ?: return
        val placeOnState = theWorld.getBlockState(placeOn)
        val collisionBox = placeOnState.block.getCollisionBoundingBox(theWorld, placeOn, placeOnState) ?: return

        val rayTraceMode = this.rayTrace.get().lowercase()
        val rayTrace = getPlacementRayTrace(rayTraceMode, eyes, vec, collisionBox, placeOn, enumFacing) ?: return
        val verifiedTarget = ScaffoldBlockPlacer.resolveForRotation(
            currentPlacementTarget ?: return,
            Rotation(yaw, pitch),
            maxReach,
            rayTraceMode,
            constructFailResult.get()
        ) ?: return

        if (!BadPacketUtils.bad(slot = false, attack = true, swing = false, block = false, inventory = true) &&
            ticksOnAir >= RandomUtils.nextInt(delayValue.getMinValue(), delayValue.getMaxValue()) &&
            ticksOnAir > if(noExpand.get()) 0 else { -1 } &&
            isPlacementRayTraceValid(rayTraceMode, rayTrace, placeOn, enumFacing) &&
            mc.theWorld.getBlockState(verifiedTarget.placeOn).block.material != Material.air
        ){
            if (usesRotations() && (rotationTimingMode == "ontick" || rotationTimingMode == "onticksnap")) {
                sendPlacementRotation(activePlacementRotation)
                if (rotationTimingMode == "onticksnap") {
                    setRotation(activePlacementRotation)
                }
            }

            val predictionEnabled = predict.get() || modes.get().equals("GodBridge", true)
            val fallOffPosition = if (predictionEnabled) currentOptimalLine?.let { ScaffoldMovementPrediction.getFallOffPositionOnLine(it) } else null
            if (ScaffoldBlockPlacer.place(verifiedTarget)) {
                ScaffoldMovementPlanner.trackPlacedBlock(verifiedTarget.placeAt)
                if (predictionEnabled) ScaffoldMovementPrediction.onPlace(currentOptimalLine, fallOffPosition)
                renderedPlacements.add(RenderedPlacement(verifiedTarget.placeAt, System.currentTimeMillis()))
            }

            mc.sendClickBlockToController(mc.currentScreen == null && mc.gameSettings.keyBindAttack.isKeyDown && mc.inGameHasFocus)
            ticksOnAir = 0
        }

        if (mc.thePlayer.onGround || mc.gameSettings.keyBindJump.isKeyDown && !isMoving) {
            startY = floor(mc.thePlayer.posY)
        }

        if(mc.thePlayer.posY < startY){
            startY = mc.thePlayer.posY
        }
    }

    private fun resolvePlacementTarget(): ScaffoldBlockPlacer.PlacementTarget? {
        for (placeAt in getTargetedPlacementCandidates()) {
            ScaffoldBlockPlacer.findBestWithSupport(
                placeAt = placeAt,
                currentRotation = currentRotation,
                range = maxReach,
                aimMode = searchModeValue.get(),
                allowSupport = supportPlacement.get(),
                supportDepth = supportDepth.get(),
                constructFailResult = constructFailResult.get()
            )?.let {
                return it
            }
        }

        val placeOn = getPlacePossibility() ?: return currentPlacementTarget
        val facing = getPlaceSide(placeOn) ?: return currentPlacementTarget
        val placeAt = placeOn.offset(facing)

        return ScaffoldBlockPlacer.findBestWithSupport(
            placeAt = placeAt,
            currentRotation = currentRotation,
            range = maxReach,
            aimMode = searchModeValue.get(),
            allowSupport = supportPlacement.get(),
            supportDepth = supportDepth.get(),
            constructFailResult = constructFailResult.get()
        ) ?: ScaffoldBlockPlacer.findBestTarget(
            placeAt = placeAt,
            currentRotation = currentRotation,
            range = maxReach,
            aimMode = "None",
            constructFailResult = true,
            source = "Legacy"
        )
    }

    private fun getTargetedPlacementCandidates(): List<BlockPos> {
        val player = mc.thePlayer ?: return emptyList()
        val towerTarget = isToweringOrWasTowering()
        val targetY = if (towerTarget) player.posY - 1.0 else if (sameY) startY - 1.0 else player.posY - 1.0
        val candidates = linkedSetOf<BlockPos>()

        fun add(x: Double, z: Double) {
            candidates.add(BlockPos(x, targetY, z))
        }

        if (towerTarget) {
            add(player.posX, player.posZ)
            for (xOffset in doubleArrayOf(-0.301, 0.301)) {
                for (zOffset in doubleArrayOf(-0.301, 0.301)) {
                    add(player.posX + xOffset, player.posZ + zOffset)
                }
            }

            return candidates.sortedBy {
                mc.thePlayer.getDistanceSq(it.x + 0.5, it.y + 0.5, it.z + 0.5)
            }
        }

        val predictionEnabled = predict.get() || modes.get().equals("GodBridge", true)
        if (predictionEnabled) {
            ScaffoldMovementPrediction.getPredictedPlacementPos(
                currentOptimalLine,
                predictionBootstrapBackoff.get().toDouble(),
                predictionCutoffDistance.get().toDouble(),
                predictionWarmupPlacements.get()
            )?.let { add(it.xCoord, it.zCoord) }
        }

        add(player.posX, player.posZ)

        if (isMoving) {
            val facing = getHorizontalFacingFromYaw(MovementUtils.getPlayerDirection())
            add(player.posX + facing.frontOffsetX * 0.42, player.posZ + facing.frontOffsetZ * 0.42)
            add(player.posX + facing.frontOffsetX * 0.72, player.posZ + facing.frontOffsetZ * 0.72)
        }

        for (xOffset in doubleArrayOf(-0.301, 0.301)) {
            for (zOffset in doubleArrayOf(-0.301, 0.301)) {
                add(player.posX + xOffset, player.posZ + zOffset)
            }
        }

        return candidates.toList()
    }

    private fun applyPlacementTarget(target: ScaffoldBlockPlacer.PlacementTarget) {
        currentPlacementTarget = target
        blockPlace = target.placeOn
        enumFacing = target.facing
        side = target.facing.opposite
        targetBlock = target.placeAt
    }

    private fun sendPlacementRotation(rotation: Rotation) {
        val serverRotation = RotationUtils.getServerRotationValue()
        if (getRotationDifference(rotation, serverRotation) <= 0.01) {
            return
        }

        val packet = C05PacketPlayerLook(rotation.yaw, rotation.pitch, mc.thePlayer.onGround)
        PacketUtils.sendPacketNoEvent(packet)
        RotationUtils.trackOutgoingPacket(packet, true)
    }

    private fun modifyVec(original: Vec3, direction: EnumFacing, pos: Vec3): Vec3 {
        val x = original.xCoord
        val y = original.yCoord
        val z = original.zCoord

        val side = direction.opposite

        return when (side.axis ?: return original) {
            EnumFacing.Axis.Y -> Vec3(x, pos.yCoord + side.directionVec.y.coerceAtLeast(0), z)
            EnumFacing.Axis.X -> Vec3(pos.xCoord + side.directionVec.x.coerceAtLeast(0), y, z)
            EnumFacing.Axis.Z -> Vec3(x, y, pos.zCoord + side.directionVec.z.coerceAtLeast(0))
        }
    }
    private fun getPlacementRayTrace(mode: String, eyes: Vec3, vec: Vec3, collisionBox: AxisAlignedBB, placeOn: BlockPos, facing: EnumFacing?): MovingObjectPosition? {
        val calculated = collisionBox.calculateIntercept(eyes, vec)?.let {
            MovingObjectPosition(it.hitVec, it.sideHit, placeOn)
        }

        return when (mode) {
            "client" -> {
                val objectMouseOver = mc.objectMouseOver
                if (objectMouseOver != null && objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK && objectMouseOver.blockPos == placeOn && objectMouseOver.sideHit == facing) {
                    objectMouseOver
                } else {
                    calculated
                }
            }
            "none" -> MovingObjectPosition(Vec3(placeOn.x + 0.5, placeOn.y + 0.5, placeOn.z + 0.5), facing, placeOn)
            else -> calculated
        }
    }

    private fun isPlacementRayTraceValid(mode: String, rayTrace: MovingObjectPosition, placeOn: BlockPos, facing: EnumFacing?): Boolean {
        if (rayTrace.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK || rayTrace.sideHit != facing) {
            return false
        }

        return mode == "calculate" || rayTrace.blockPos == placeOn
    }


    private fun getPlacePossibility(): BlockPos? {
        val playerPos = BlockPos(mc.thePlayer.posX, mc.thePlayer.posY - 1, mc.thePlayer.posZ)
        val positions = ArrayList<Vec3>()
        val hashMap = HashMap<Vec3, BlockPos>()

        for (x in playerPos.x - 5..playerPos.x + 5) {
            for (y in playerPos.y - 1..playerPos.y) {
                for (z in playerPos.z - 5..playerPos.z + 5) {
                    if (isValidBock(BlockPos(x, y, z))) {
                        val blockPos = BlockPos(x, y, z)
                        val block = mc.theWorld.getBlockState(blockPos).block
                        val ex = MathHelper.clamp_double(
                            mc.thePlayer.posX, blockPos.x.toDouble(),
                            blockPos.x + block.blockBoundsMaxX
                        )
                        val ey = MathHelper.clamp_double(
                            mc.thePlayer.posY, blockPos.y.toDouble(),
                            blockPos.y + block.blockBoundsMaxY
                        )
                        val ez = MathHelper.clamp_double(
                            mc.thePlayer.posZ, blockPos.z.toDouble(),
                            blockPos.z + block.blockBoundsMaxZ
                        )
                        val vec3 = Vec3(ex, ey, ez)
                        positions.add(vec3)
                        hashMap[vec3] = blockPos
                    }
                }
            }
        }
        return hashMap[positions.minByOrNull { mc.thePlayer.getDistanceSq(it.xCoord, it.yCoord, it.zCoord) }]
    }

    private fun isValidBock(blockPos: BlockPos?): Boolean {
        val block = Minecraft.getMinecraft().theWorld.getBlockState(blockPos).block
        return (block !is BlockLiquid && block !is BlockAir && block !is BlockChest
                && block !is BlockFurnace)
    }

    private fun isPosSolid(pos: BlockPos?): Boolean {
        val block = Minecraft.getMinecraft().theWorld.getBlockState(pos).block
        return ((block.material.isSolid || !block.isTranslucent || block is BlockLadder
                || block is BlockCarpet || block is BlockSnow || block is BlockSkull)
                && !block.material.isLiquid && block !is BlockContainer)
    }

    /**
     * Checks if the player is near a block
     *
     * @return block near
     */
    private fun blockNear(range: Int): Boolean {
        for (x in -range..range) {
            for (y in -range..range) {
                for (z in -range..range) {
                    if (blockRelativeToPlayer(x, y, z) !is BlockAir) return true
                }
            }
        }

        return false
    }

    /**
     * Gets the block relative to the player from the offset
     *
     * @return block relative to the player
     */
    private fun blockRelativeToPlayer(offsetX: Int, offsetY: Int, offsetZ: Int): Block {
        return mc.theWorld.getBlockState(BlockPos(mc.thePlayer).add(offsetX, offsetY, offsetZ)).block
    }

    /**
     * Checks if there is a block under the player
     *
     * @return block under
     */
    private fun isBlockUnder(height: Double, boundingBox: Boolean): Boolean {
        if (boundingBox) {
            var offset = 0
            while (offset < height) {
                val bb = mc.thePlayer.entityBoundingBox.offset(0.0, -offset.toDouble(), 0.0)

                if (mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, bb).isNotEmpty()) {
                    return true
                }
                offset += 2
            }
        } else {
            var offset = 0
            while (offset < height) {
                if (blockRelativeToPlayer(0, -offset, 0).isFullBlock) {
                    return true
                }
                offset++
            }
        }
        return false
    }

    private fun updateTowerState(): Boolean {
        val towering = !towerModeValue.get().equals("Off", true) &&
            mc.gameSettings.keyBindJump.isKeyDown &&
            blocksAmount > 0

        if (towering) {
            wasTowering = true
        } else if (mc.thePlayer.onGround) {
            wasTowering = false
        }

        return towering
    }

    private fun isToweringOrWasTowering(): Boolean {
        return updateTowerState() || wasTowering
    }

    private fun isBlockBelowTower(): Boolean {
        return isBlockUnder(3.0, true) || blockNear(2)
    }

    @EventTarget
    fun onStrafe(event: StrafeEvent) {
        // Boing Boing
        val bb = mc.thePlayer.entityBoundingBox.offset(0.0, -0.5, 0.0).expand(-0.001, 0.0, -0.001)
        if (
            jumpAutomatically.get()
            && isMoving
            && mc.thePlayer.onGround
            && !mc.thePlayer.isSneaking
            && !mc.gameSettings.keyBindSneak.isKeyDown
            && !mc.gameSettings.keyBindJump.isKeyDown &&
            mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, bb).isEmpty()
        ){
            mc.thePlayer.jump()
        }

        if(!movementCorrection.get()){
            MovementUtils.useDiagonalSpeed()
        }
    }

    @EventTarget
    fun onInput(event: MoveInputEvent){
        currentOptimalLine = ScaffoldMovementPlanner.getOptimalMovementLine(
            event.originalForward,
            event.originalStrafe,
            mc.thePlayer.rotationYaw
        )
        val blockSlot = InventoryUtils.findBlockInHotbar() ?: return
        if (blockSlot != -1) {
            if (safeWalk.get() && mc.thePlayer.onGround && mc.theWorld.getCollidingBoundingBoxes(
                    mc.thePlayer, mc.thePlayer.entityBoundingBox.addCoord(
                        mc.thePlayer.motionX, mc.thePlayer.motionY, mc.thePlayer.motionZ
                    ).expand(-0.175, 0.0, -0.175)
                ).isEmpty()
            ) {
                event.sneak = true
            }

            if(startSneak.get() && !startTime.hasTimePassed(200L) && mc.thePlayer.posX != 0.0 && mc.thePlayer.posZ != 0.0){
                event.sneak = true
            }

            if (
                modes.get().equals("telly", true) &&
                mc.thePlayer.onGround &&
                isMoving &&
                if(reset.get() && usesRotations()) !RotationUtils.active else true
            ) {
                event.jump = true
            }

            if (modes.get().equals("GodBridge", true)) {
                handleGodBridgeLedgeInput(event)
            }

            when (adStrafe.get().lowercase()) {
                "always" -> {
                    if (adStrafeTimer.hasTimeElapsed(125.0, true)) {
                        adStrafeDirection = !adStrafeDirection
                    }

                    if (isMoving && !mc.gameSettings.keyBindLeft.isKeyDown && !mc.gameSettings.keyBindRight.isKeyDown) {
                        event.strafe = if(adStrafeDirection) 1.0F else -1.0F
                    }
                }
                "edge" -> {
                    val b = BlockPos(mc.thePlayer.posX, mc.thePlayer.posY - 0.5, mc.thePlayer.posZ)
                    if(b.getBlock() is BlockAir &&
                        mc.currentScreen == null &&
                        !mc.gameSettings.keyBindJump.isKeyDown &&
                        event.forward != 0.0F
                    ){
                        when (
                            EnumFacing.getHorizontal(
                                MathHelper.floor_double(
                                    (targetYaw * 4.0f / 360.0f).toDouble() + 0.5) and 3
                            )
                        ){
                            EnumFacing.EAST -> event.strafe = if(b.z + 0.5 > mc.thePlayer.posZ) 1.0f else -1.0f
                            EnumFacing.WEST -> event.strafe = if(b.z + 0.5 < mc.thePlayer.posZ) 1.0f else -1.0f
                            EnumFacing.SOUTH -> event.strafe = if(b.x + 0.5 < mc.thePlayer.posX) 1.0f else -1.0f
                            else -> event.strafe = if(b.x + 0.5 > mc.thePlayer.posX) 1.0f else -1.0f
                        }
                    }
                }
            }
        }
    }

    private fun handleGodBridgeLedgeInput(event: MoveInputEvent) {
        if (!isMoving || !isGodBridgeNearLedge()) {
            if (godBridgeSneakTicksLeft > 0) {
                event.sneak = true
                godBridgeSneakTicksLeft--
            }
            return
        }

        if (canGodBridgePlaceWithCurrentRotation()) {
            return
        }

        val mode = if (blocksAmount < godBridgeForceSneakBelowCount.get()) {
            "Sneak"
        } else {
            when (godBridgeLedge.get().lowercase()) {
                "random" -> arrayOf("Jump", "Sneak", "StopInput", "Backwards")[RandomUtils.nextInt(0, 4)]
                else -> godBridgeLedge.get()
            }
        }

        when (mode.lowercase()) {
            "jump" -> {
                if (mc.thePlayer.onGround) {
                    event.jump = true
                }
            }
            "sneak" -> {
                event.sneak = true
                godBridgeSneakTicksLeft = godBridgeSneakTicks.get() - 1
            }
            "stopinput" -> {
                event.forward = 0f
                event.strafe = 0f
            }
            "backwards" -> {
                event.forward = -1f
                event.strafe = 0f
            }
        }
    }

    private fun isGodBridgeNearLedge(): Boolean {
        val player = mc.thePlayer ?: return false
        val world = mc.theWorld ?: return false
        val bb = player.entityBoundingBox.offset(player.motionX, -0.51, player.motionZ).expand(-0.04, 0.0, -0.04)
        if (world.getCollidingBoundingBoxes(player, bb).isEmpty()) {
            return true
        }

        return BlockPos(player.posX, player.posY - 0.5, player.posZ).getBlock() is BlockAir
    }

    private fun canGodBridgePlaceWithCurrentRotation(): Boolean {
        val placeOn = blockPlace ?: return false
        val facing = enumFacing ?: return false
        val rotation = Rotation(targetYaw, targetPitch)
        val eyes = mc.thePlayer.eyes
        val look = RotationUtils.getVectorForRotation(rotation)
        val end = eyes + (look * maxReach)
        val hit = mc.theWorld.rayTraceBlocks(eyes, end, false, false, true) ?: return false

        return hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK &&
            hit.blockPos == placeOn &&
            hit.sideHit == facing &&
            targetBlock?.getBlock() is BlockAir
    }

    private fun getGodBridgeRotations() {
        val rawForward = mc.thePlayer.movementInput.moveForward
        val rawStrafe = mc.thePlayer.movementInput.moveStrafe

        val rotation = if (rawForward == 0f && rawStrafe == 0f) {
            getGodBridgeRotationForNoInput()
        } else {
            val direction = MovementUtils.getPlayerDirection() + 180f
            val movingYaw = (round(direction / 45f) * 45f)
            val isMovingStraight = abs(movingYaw % 90f) < 0.001f

            if (isMovingStraight) {
                getGodBridgeRotationForStraightInput(movingYaw)
            } else {
                Rotation(movingYaw, 75.6f)
            }
        }

        rotation.fixedSensitivity()
        targetYaw = rotation.yaw
        targetPitch = rotation.pitch
    }

    private fun getGodBridgeRotationForStraightInput(movingYaw: Float): Rotation {
        if (mc.thePlayer.onGround) {
            val yawRad = Math.toRadians(movingYaw.toDouble())

            godBridgeOnRightSide = floor(mc.thePlayer.posX + cos(yawRad) * 0.5) != floor(mc.thePlayer.posX) ||
                floor(mc.thePlayer.posZ + sin(yawRad) * 0.5) != floor(mc.thePlayer.posZ)

            val facing = getHorizontalFacingFromYaw(movingYaw)
            val posInDirection = BlockPos(
                mc.thePlayer.posX + facing.frontOffsetX * 0.6,
                mc.thePlayer.posY,
                mc.thePlayer.posZ + facing.frontOffsetZ * 0.6
            )

            val isLeaningOffBlock = BlockPos(mc.thePlayer.posX, mc.thePlayer.posY - 0.5, mc.thePlayer.posZ).getBlock() is BlockAir
            val nextBlockIsAir = posInDirection.down().getBlock() is BlockAir

            if (isLeaningOffBlock && nextBlockIsAir) {
                godBridgeOnRightSide = !godBridgeOnRightSide
            }
        }

        return Rotation(movingYaw + if (godBridgeOnRightSide) 45f else -45f, 75.7f)
    }

    private fun getGodBridgeRotationForNoInput(): Rotation {
        val baseYaw = currentPlacementTarget?.rotation?.yaw
            ?: targetYaw.takeIf { !it.isNaN() }
            ?: mc.thePlayer.rotationYaw
        val axisMovement = floor(baseYaw / 90f) * 90f
        return Rotation(axisMovement + 45f, 75f)
    }

    private fun getHorizontalFacingFromYaw(yaw: Float): EnumFacing {
        return EnumFacing.getHorizontal(MathHelper.floor_double((yaw * 4.0f / 360.0f + 0.5f).toDouble()) and 3)
    }

    private fun updateGodBridgePlacementFromRotation() {
        val eyes = mc.thePlayer.eyes
        val look = RotationUtils.getVectorForRotation(Rotation(targetYaw, targetPitch))
        val end = eyes + (look * maxReach)
        val hit = mc.theWorld.rayTraceBlocks(eyes, end, false, false, true) ?: return

        val hitFacing = hit.sideHit ?: return
        if (hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK || hitFacing.axis == EnumFacing.Axis.Y) {
            return
        }

        val placeOn = hit.blockPos ?: return
        val hitState = mc.theWorld.getBlockState(placeOn) ?: return
        if (hitState.block.material == Material.air || !isValidBock(placeOn)) {
            return
        }

        val placeTarget = placeOn.offset(hitFacing)
        val targetState = mc.theWorld.getBlockState(placeTarget) ?: return
        if (targetState.block.material != Material.air && !targetState.block.isReplaceable(mc.theWorld, placeTarget)) {
            return
        }

        blockPlace = placeOn
        enumFacing = hitFacing
        side = hitFacing.opposite
        targetBlock = placeTarget
        ScaffoldBlockPlacer.fromRayTrace(
            hit,
            placeTarget,
            placeOn,
            hitFacing,
            Rotation(targetYaw, targetPitch),
            "GodBridge"
        )?.let {
            currentPlacementTarget = it
        }
    }

    /**
     *  @return block relative to the player
     */
    private fun calculateRotations() {
        val killAura = MinusBounce.moduleManager[KillAura::class.java]

        if(killAura != null && killAura.state && killAura.target != null){
            return
        }

        val blockPlace = blockPlace ?: return
        val enumFacing = enumFacing ?: return

        if (isToweringOrWasTowering()) {
            getRotations()
            return
        }

        when (modes.get().lowercase()) {
            "godbridge" -> {
                getGodBridgeRotations()
            }

            "snap" -> {
                getRotations()

                if (ticksOnAir <= 0 || isObjectMouseOverBlock(enumFacing, blockPlace)) {
                    if (reset.get()) {
                        RotationUtils.active = false
                        return
                    } else {
                        targetYaw = mc.thePlayer.rotationYaw
                    }
                }
            }

            "legit" -> {
                /* Player Position Update (Edge Exception + Legit Mode) */
                xPos = mc.thePlayer.posX
                zPos = mc.thePlayer.posZ

                if (!this.buildForward()) {
                    xPos += mc.thePlayer.posX - lastX
                    zPos += mc.thePlayer.posZ - lastZ
                }

                lastX = mc.thePlayer.posX
                lastZ = mc.thePlayer.posZ

                val miX = blockPlace.x - 0.28
                val maX = blockPlace.x + 1.28

                val miZ = blockPlace.z - 0.28
                val maZ = blockPlace.z + 1.28

                if (xPos !in miX..maX || zPos !in miZ..maZ) getRotations()
            }

            "telly" -> {
                if (RotationUtils.offGroundTicks >= ticks.get()) {
                    if (!isObjectMouseOverBlock(enumFacing, blockPlace)) {
                        getRotations()
                    }
                } else if (isMoving) {
                    if (reset.get() && usesRotations()) {
                        RotationUtils.active = false
                        return
                    } else {
                        getRotations()
                        targetYaw = mc.thePlayer.rotationYaw
                    }
                }
            }

            else -> if (ticksOnAir > 0 && !isObjectMouseOverBlock(enumFacing, blockPlace)) {
                getRotations()
            }
        }
    }

    /* Setting rotations */
    private fun setRotation(rotation: Rotation = Rotation(targetYaw, targetPitch)){
        if (!usesRotations()) {
            RotationUtils.resetRotationOwner("Scaffold")
            return
        }

        RotationUtils.requestRotations(
            owner = "Scaffold",
            rotation = rotation,
            keepLength = 3,
            speed = RandomUtils.nextFloat(speed.getMinValue(), speed.getMaxValue()),
            fixType = if (movementCorrection.get()) MovementFixType.FULL else MovementFixType.NONE,
            silent = true,
            priority = 70,
            resetMode = RotationUtils.RotationResetMode.SERVER,
            resetThreshold = 1f,
            ticksUntilReset = 20
        )
    }

    private fun usesRotations() = !rotationMode.get().equals("None", true)

    private fun getPlaceSide(blockPos: BlockPos): EnumFacing? {
        val positions = ArrayList<Vec3>()
        val hashMap = HashMap<Vec3, EnumFacing>()
        val playerPos = BlockPos(mc.thePlayer)
        if (!isPosSolid(blockPos.add(0, 1, 0)) && !blockPos.add(0, 1, 0).equalsBlockPos(playerPos)
        ) {
            val vec4 = this.getBestHitFeet(blockPos.add(0, 1, 0))
            positions.add(vec4)
            hashMap[vec4] = EnumFacing.UP
        }
        if (!isPosSolid(blockPos.add(0, -1, 0)) && !blockPos.add(0, -1, 0).equalsBlockPos(playerPos)
        ) {
            val vec4 = this.getBestHitFeet(blockPos.add(0, -1, 0))
            positions.add(vec4)
            hashMap[vec4] = EnumFacing.DOWN
        }
        if (!isPosSolid(blockPos.add(1, 0, 0)) && !blockPos.add(1, 0, 0).equalsBlockPos(playerPos)) {
            val vec4 = this.getBestHitFeet(blockPos.add(1, 0, 0))
            positions.add(vec4)
            hashMap[vec4] = EnumFacing.EAST
        }
        if (!isPosSolid(blockPos.add(-1, 0, 0)) && !blockPos.add(-1, 0, 0).equalsBlockPos(playerPos)) {
            val vec4 = this.getBestHitFeet(blockPos.add(-1, 0, 0))
            positions.add(vec4)
            hashMap[vec4] = EnumFacing.WEST
        }
        if (!isPosSolid(blockPos.add(0, 0, 1)) && !blockPos.add(0, 0, 1).equalsBlockPos(playerPos)) {
            val vec4 = this.getBestHitFeet(blockPos.add(0, 0, 1))
            positions.add(vec4)
            hashMap[vec4] = EnumFacing.SOUTH
        }
        if (!isPosSolid(blockPos.add(0, 0, -1)) && !blockPos.add(0, 0, -1).equalsBlockPos(playerPos)) {
            val vec4 = this.getBestHitFeet(blockPos.add(0, 0, -1))
            positions.add(vec4)
            hashMap[vec4] = EnumFacing.NORTH
        }

        if (positions.isNotEmpty()) {
            positions.sortBy { mc.thePlayer.getDistance(it.xCoord, it.yCoord, it.zCoord) }

            val vec5 = this.getBestHitFeet(blockPos)
            if (mc.thePlayer.getDistance(vec5.xCoord, vec5.yCoord, vec5.zCoord) >= mc.thePlayer.getDistance(positions[0].xCoord, positions[0].yCoord, positions[0].zCoord)
            ) {
                return hashMap[positions[0]]
            }
        }

        return null
    }

    private fun getBestHitFeet(blockPos: BlockPos): Vec3 {
        val block = mc.theWorld.getBlockState(blockPos).block
        val ex = MathHelper.clamp_double(
            mc.thePlayer.posX, blockPos.x.toDouble(),
            blockPos.x + block.blockBoundsMaxX
        )
        val ey = MathHelper.clamp_double(
            mc.thePlayer.posY, blockPos.y.toDouble(),
            blockPos.y + block.blockBoundsMaxY
        )
        val ez = MathHelper.clamp_double(
            mc.thePlayer.posZ, blockPos.z.toDouble(),
            blockPos.z + block.blockBoundsMaxZ
        )
        return Vec3(ex, ey, ez)
    }

    private fun BlockPos.equalsBlockPos(blockPos: BlockPos): Boolean {
        return this.x == blockPos.x && (this.y == blockPos.y) && (this.z == blockPos.z)
    }

    /**
     * @author fmcpe
     *
     * 6/21/2024
     * Raytrace
     * From MCP
     */
    @JvmOverloads
    fun isObjectMouseOverBlock(
        facing: EnumFacing,
        block: BlockPos,
        rotation: Rotation? = currentRotation,
        obj: MovingObjectPosition? = rayTrace(rotation),
    ): Boolean{
        if (obj != null) {
            return obj.blockPos == block && obj.sideHit == facing
        }

        return false
    }

    private fun buildForward(): Boolean {
        val realYaw = MathHelper.wrapAngleTo180_float(currentRotation.yaw)
        return (realYaw > 77.5 && realYaw < 102.5) || (realYaw > 167.5 || realYaw < -167.0) || (realYaw < -77.5 && realYaw > -102.5 || realYaw > -12.5 && realYaw < 12.5)
    }

    private fun getRotations(){
        val eyes = mc.thePlayer.eyes
        var placeRotation: PlaceRotation? = null
        val blockPos = targetBlock ?: return
        val neighborBlock = blockPlace ?: return

        if (!searchModeValue.get().equals("TryRotation", true)) {
            currentPlacementTarget
                ?.takeIf { it.placeAt == blockPos && it.placeOn == neighborBlock && it.facing == enumFacing }
                ?.let {
                    targetYaw = it.rotation.yaw
                    targetPitch = it.rotation.pitch
                    return
                }
        }

        when (searchModeValue.get().lowercase()) {
            "area" -> for (x in 0.1..0.9) {
                for (y in 0.1..0.9) {
                    for (z in 0.1..0.9) {
                        val currentPlaceRotation = findTargetPlace(blockPos, neighborBlock, Vec3(x, y, z), side ?: return, eyes, maxReach.toFloat()) ?: continue

                        if (placeRotation == null || getRotationDifference(currentPlaceRotation.rotation, currentRotation) < getRotationDifference(placeRotation.rotation, currentRotation))
                            placeRotation = currentPlaceRotation
                    }
                }
            }

            "center" -> {
                placeRotation = findTargetPlace(blockPos, neighborBlock, Vec3(0.5, 0.5, 0.5), side ?: return, eyes, maxReach.toFloat()) ?: return
            }

            "tryrotation" -> {
                val possibleYaw = when (yawOffset.get()){
                    "Dynamic" -> arrayOf(0.0, 45.0, 135.0, 180.0)
                    else -> arrayOf(45.0, 135.0)
                }

                val playerPosition = BlockPos(mc.thePlayer.posX, mc.thePlayer.posY - 0.5, mc.thePlayer.posZ)

                val rotationList = mutableListOf<PlaceRotation>()

                for (yaw in possibleYaw.flatMap {yaw -> (-1..1 step 2).map { mc.thePlayer.rotationYaw + yaw * it } }) {
                    for (pitch in max(currentRotation.pitch - 50.0, -90.0)..min(currentRotation.pitch + 50.0, 90.0) step 0.05) {
                        val rotation = Rotation(yaw.toFloat(), pitch.toFloat())
                        rotation.fixedSensitivity()

                        val eye = Vec3(mc.thePlayer.posX, mc.thePlayer.entityBoundingBox.minY + mc.thePlayer.eyeHeight, mc.thePlayer.posZ)
                        val look = RotationUtils.getVectorForRotation(rotation)
                        val vec = eye + (look * mc.playerController.blockReachDistance.toDouble())
                        val hitBlock = mc.theWorld.rayTraceBlocks(eye, vec, false, false, true)
                        val currentPlaceRotation = PlaceRotation(PlaceInfo(hitBlock.blockPos, hitBlock.sideHit, hitBlock.hitVec), rotation)

                        if (hitBlock.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                            && eye.distanceTo(currentPlaceRotation.placeInfo.vec3) < maxReach
                            && mc.theWorld.rayTraceBlocks(eye, currentPlaceRotation.placeInfo.vec3, false, true, false) == null
                            && isValidBock(hitBlock.blockPos)
                            && !rotationList.contains(currentPlaceRotation)
                            && hitBlock.blockPos.equalsBlockPos(blockPlace ?: continue)
                            && hitBlock.sideHit.axis != EnumFacing.Axis.Y
                            && hitBlock.blockPos.y <= playerPosition.y
                        ) {
                            rotationList.add(currentPlaceRotation)
                        }
                    }
                }

                placeRotation = rotationList.minByOrNull {
                    val d = getRotationDifference(it.rotation, currentRotation)
                    val x = it.placeInfo.vec3.xCoord - it.placeInfo.blockPos.x + 0.5
                    val y = it.placeInfo.vec3.yCoord - it.placeInfo.blockPos.y + 0.5
                    val z = it.placeInfo.vec3.zCoord - it.placeInfo.blockPos.z + 0.5
                    sqrt(d + x * x + y * y + z * z)
                }
            }
        }

        targetYaw = placeRotation?.rotation?.yaw ?: return
        targetPitch = placeRotation.rotation.pitch
    }

    private fun findTargetPlace(blockPos: BlockPos, neighborBlock: BlockPos, vec3: Vec3, side: EnumFacing, eyes: Vec3, maxReach: Float): PlaceRotation? {
        mc.theWorld ?: return null

        val vec = (Vec3(blockPos) + vec3).addVector(
            side.directionVec.x * vec3.xCoord, side.directionVec.y  * vec3.yCoord, side.directionVec.z * vec3.zCoord
        )

        val distance = eyes.distanceTo(vec)

        if (distance > maxReach || mc.theWorld.rayTraceBlocks(eyes, vec, false, true, false) != null)
            return null

        val rotation = RotationUtils.toRotation(vec, false)
        val raytrace = rayTrace(rotation) ?: return null

        return if (raytrace.blockPos == neighborBlock && raytrace.sideHit == side.opposite) {
            val placeInfo = PlaceInfo(neighborBlock, side.opposite, modifyVec(raytrace.hitVec, side.opposite, Vec3(neighborBlock)))
            PlaceRotation(placeInfo, rotation)
        } else null
    }

    @EventTarget
    fun onRender3D(event: Render3DEvent) {
        if (!scaffoldRender.get()) {
            return
        }

        val now = System.currentTimeMillis()
        val iterator = renderedPlacements.iterator()
        while (iterator.hasNext()) {
            val placement = iterator.next()
            val age = now - placement.time
            if (age > 500L) {
                iterator.remove()
                continue
            }

            val alpha = ((1.0 - age / 500.0) * 90.0).roundToInt().coerceIn(0, 90)
            RenderUtils.drawBlockBox(placement.pos, Color(0, 255, 0, alpha), true)
        }

        currentPlacementTarget?.let {
            RenderUtils.drawBlockBox(it.placeAt, Color(0, 255, 0, 90), true)
        }
    }

    /**
     * Render counter
     */
    @EventTarget
    fun onRender2D(event: Render2DEvent) {
        progress = (System.currentTimeMillis() - lastMS).toFloat() / 100F
        if (progress >= 1) progress = 1f

        val scaledResolution = ScaledResolution(mc)
        val info = "$blocksAmount Blocks"
        val infoWidth = Fonts.fontSFUI40.getStringWidth(info)

        if(counter.get()){
            GlStateManager.translate(0.0, (-14F - (progress * 4F)).toDouble(), 0.0)
            GL11.glEnable(GL11.GL_BLEND)
            GL11.glDisable(GL11.GL_TEXTURE_2D)
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
            GL11.glEnable(GL11.GL_LINE_SMOOTH)
            GL11.glColor4f(0.15F, 0.15F, 0.15F, progress)
            GL11.glBegin(GL11.GL_TRIANGLE_FAN)
            GL11.glVertex2d((scaledResolution.scaledWidth / 2 - 3).toDouble(), (scaledResolution.scaledHeight - 60).toDouble())
            GL11.glVertex2d((scaledResolution.scaledWidth / 2).toDouble(), (scaledResolution.scaledHeight - 57).toDouble())
            GL11.glVertex2d((scaledResolution.scaledWidth / 2 + 3).toDouble(), (scaledResolution.scaledHeight - 60).toDouble())
            GL11.glEnd()
            GL11.glEnable(GL11.GL_TEXTURE_2D)
            GL11.glDisable(GL11.GL_BLEND)
            GL11.glDisable(GL11.GL_LINE_SMOOTH)
            RenderUtils.drawRoundedRect((scaledResolution.scaledWidth / 2 - (infoWidth / 2) - 4).toFloat(), (scaledResolution.scaledHeight - 60).toFloat(), (scaledResolution.scaledWidth / 2 + (infoWidth / 2) + 4).toFloat(), (scaledResolution.scaledHeight - 74).toFloat(), 2F, Color(0.15F, 0.15F, 0.15F, progress).rgb)
            GlStateManager.resetColor()
            Fonts.fontSFUI35.drawCenteredString(info, (scaledResolution.scaledWidth / 2).toFloat() + 0.1F, (scaledResolution.scaledHeight - 70).toFloat(), Color(1F, 1F, 1F, 0.8F * progress).rgb, false)
            GlStateManager.translate(0.0, (14F + (progress * 4F)).toDouble(), 0.0)
        }
    }

    /**
     * @return hotbar blocks amount
     */
    private val blocksAmount: Int
        get() {
            var amount = 0
            for (i in 36..44) {
                val itemStack = mc.thePlayer.inventoryContainer.getSlot(i).stack
                if (itemStack != null && itemStack.item is ItemBlock) {
                    val block = (itemStack.item as ItemBlock).getBlock()
                    amount += if (!invalidBlocks.contains(block)) itemStack.stackSize else 0
                }
            }
            return amount
        }

    private val currentRotation: Rotation
        get() = RotationUtils.targetRotation ?: Rotation(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch)

    private val maxReach: Double
        get() = mc.playerController.blockReachDistance.toDouble()

    /**
     * @return sameY
     */
    private val sameY: Boolean
        get() = !sameYValue.get().equals("Off", true) && !mc.gameSettings.keyBindJump.isKeyDown && isMoving
}
