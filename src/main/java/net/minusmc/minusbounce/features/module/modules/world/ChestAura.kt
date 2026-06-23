package net.minusmc.minusbounce.features.module.modules.world

import net.minecraft.block.BlockChest
import net.minecraft.block.BlockEnderChest
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.network.play.server.S0EPacketSpawnObject
import net.minecraft.network.play.server.S24PacketBlockAction
import net.minecraft.network.play.server.S29PacketSoundEffect
import net.minecraft.network.play.server.S45PacketTitle
import net.minecraft.tileentity.TileEntity
import net.minecraft.tileentity.TileEntityChest
import net.minecraft.tileentity.TileEntityEnderChest
import net.minecraft.util.BlockPos
import net.minecraft.util.Vec3
import net.minusmc.minusbounce.event.*
import net.minusmc.minusbounce.features.module.Module
import net.minusmc.minusbounce.features.module.ModuleCategory
import net.minusmc.minusbounce.features.module.ModuleInfo
import net.minusmc.minusbounce.utils.ClientUtils.displayChatMessage
import net.minusmc.minusbounce.utils.EntityUtils.isSelected
import net.minusmc.minusbounce.utils.InventoryUtils.serverOpenContainer
import net.minusmc.minusbounce.utils.RotationUtils
import net.minusmc.minusbounce.utils.RotationUtils.getVectorForRotation
import net.minusmc.minusbounce.utils.RotationUtils.performRayTrace
import net.minusmc.minusbounce.utils.RotationUtils.performRaytrace
import net.minusmc.minusbounce.utils.RotationUtils.toRotation
import net.minusmc.minusbounce.utils.block.BlockUtils.getBlock
import net.minusmc.minusbounce.utils.extensions.*
import net.minusmc.minusbounce.utils.timer.MSTimer
import net.minusmc.minusbounce.value.BoolValue
import net.minusmc.minusbounce.value.FloatValue
import net.minusmc.minusbounce.value.IntegerValue
import net.minusmc.minusbounce.value.ListValue
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.*
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

@ModuleInfo("ChestAura", "Chest Aura", "Opening chest", ModuleCategory.WORLD)
class ChestAura : Module() {
    private val range = FloatValue("Range", 5F, 1F, 5F)
    private val delay = IntegerValue("Delay", 200, 50, 500)
    private val throughWalls = BoolValue("ThroughWalls", true)
    private val ignoreLooted = BoolValue("IgnoreLootedChests", true)
    private val detectRefill = BoolValue("DetectChestRefill", true)
    private val openInfo = ListValue("OpenInfo", arrayOf("Off", "Self", "Other", "Everyone"), "Off")

    var tileTarget: Triple<Vec3, TileEntity, Double>? = null
    private val timer = MSTimer()

    private val clickedTileEntities = mutableSetOf<TileEntity>()
    private val chestOpenMap = mutableMapOf<BlockPos, Pair<Int, Long>>()

    // Substrings that indicate that chests have been refilled, broadcasted via title packet
    private val refillSubstrings = arrayOf("refill", "reabastecidos")
    private val decimalFormat = DecimalFormat("##0.00", DecimalFormatSymbols(Locale.ENGLISH))

    @EventTarget
    fun onMotion(event: PreUpdateEvent) {
        if (!timer.hasTimePassed(delay.get()))
            return

        val thePlayer = mc.thePlayer ?: return

        // Check if there is an opponent in range
        if (mc.theWorld.loadedEntityList.any {
                isSelected(it, true) && thePlayer.getDistanceSqToEntity(it) < 10.0F
            }) return

        if (serverOpenContainer && tileTarget != null) {
            timer.reset()
            return
        }

        val eyes = thePlayer.eyes

        val pointsInRange = mc.theWorld.tickableTileEntities
            // Check if tile entity is correct type, not already clicked, not blocked by a block and in range
            .filter {
                shouldClickTileEntity(it) && it.getDistanceSq(thePlayer.posX, thePlayer.posY, thePlayer.posZ) <= (range.get() + 1.0F).pow(2)
            }.flatMap { entity ->
                val box = entity.blockType.getSelectedBoundingBox(mc.theWorld, entity.pos)

                val points = mutableListOf(getNearestPointBB(eyes, box))

                for (x in 0.0..1.0) {
                    for (y in 0.0..1.0) {
                        for (z in 0.0..1.0) {
                            points += Vec3(box.minX + (box.maxX - box.minX) * x, box.minY + (box.maxY - box.minY) * y, box.minZ + (box.maxZ - box.minZ) * z)
                        }
                    }
                }

                points
                    .map { Triple(it, entity, it.squareDistanceTo(eyes)) }
                    .filter { it.third <= range.get().pow(2) }
            }.sortedBy { it.third }

        // Vecs are already sorted by distance
        val closestClickable = pointsInRange
            .firstOrNull { (vec, entity) ->
                // If through walls is enabled and its range is same as normal, just return the first one
                if (throughWalls.get())
                    return@firstOrNull true

                val result = mc.theWorld.rayTraceBlocks(eyes, vec) ?: return@firstOrNull false
                val distanceSq = result.hitVec.squareDistanceTo(eyes)

                // If chest is behind a wall, check if through walls is enabled and its range
                if (result.blockPos != entity.pos) throughWalls.get() && distanceSq <= range.get().pow(2)
                else distanceSq <= range.get().pow(2)
            } ?: return

        tileTarget = closestClickable

        RotationUtils.setRotations(toRotation(closestClickable.first), keepLength = delay.get() / 20)
    }

    @EventTarget
    fun onWorld(event: WorldEvent) = onDisable()

    override fun onDisable() {
        clickedTileEntities.clear()
        chestOpenMap.clear()
    }

    @EventTarget
    fun onPacket(event: PacketEvent) {
        when (val packet = event.packet) {
            // Detect chest opening from sound effect
            is S29PacketSoundEffect -> {
                if (packet.soundName != "random.chestopen")
                    return

                val entity = mc.theWorld.getTileEntity(BlockPos(packet.x, packet.y, packet.z)) ?: return

                clickedTileEntities += entity
            }

            // Detect already looted chests by having their lid open or closed
            is S24PacketBlockAction -> {
                if (!ignoreLooted.get() || (packet.blockType !is BlockChest && packet.blockType !is BlockEnderChest))
                    return

                clickedTileEntities += mc.theWorld.getTileEntity(packet.blockPosition)

                if (openInfo.get() != "Off") {
                    val (prevState, prevTime) = chestOpenMap[packet.blockPosition] ?: (null to null)

                    // Prevent repetitive packet spamming
                    if (prevState == packet.data2)
                        return

                    // If there is no info about the chest ever being opened, don't print anything
                    if (packet.data2 == 0 && prevState != 1)
                        return

                    val player: EntityPlayer
                    val distance: String

                    // If chest is not last clicked chest, find a player that might have opened it
                    if (packet.blockPosition != tileTarget?.second?.pos) {
                        val nearPlayers = mc.theWorld.playerEntities
                            .mapNotNull {
                                val distanceSq = it.getDistanceSqToCenter(packet.blockPosition)

                                if (distanceSq <= 36) it to distanceSq
                                else null
                            }.sortedBy { it.second }

                        if (nearPlayers.isEmpty())
                            return

                        // Find the closest player that is looking at the chest or else just the closest
                        player = (nearPlayers.firstOrNull { (player) ->
                            player.rayTrace(5.0, 1f)?.blockPos == packet.blockPosition
                        } ?: nearPlayers.first()).first

                        val entity = mc.theWorld.getTileEntity(packet.blockPosition)
                        val box = entity.blockType.getSelectedBoundingBox(mc.theWorld, packet.blockPosition)
                        distance = decimalFormat.format(player.getDistanceToBox(box))
                    } else {
                        player = mc.thePlayer
                        distance = decimalFormat.format(sqrt(tileTarget!!.third))
                    }

                    when (player) {
                        mc.thePlayer -> if (openInfo.get() == "Other") return
                        else -> if (openInfo.get() == "Self") return
                    }

                    val actionMsg = if (packet.data2 == 1) "§a§lOpened§3" else "§c§lClosed§3"
                    val timeTakenMsg = if (packet.data2 == 0 && prevTime != null)
                        ", took §b${decimalFormat.format((System.currentTimeMillis() - prevTime) / 1000.0)} s§3"
                    else ""
                    val playerMsg = if (player == mc.thePlayer) actionMsg else "§b${player.name} §3${actionMsg.lowercase()}"

                    displayChatMessage("§8[§9§lChestAura§8] $playerMsg chest from §b$distance m§3$timeTakenMsg.")

                    chestOpenMap[packet.blockPosition] = packet.data2 to System.currentTimeMillis()
                }
            }

            // Detect chests getting refilled
            is S45PacketTitle -> {
                if (!detectRefill.get())
                    return

                if (refillSubstrings.any { it in (packet.message?.unformattedText ?: "") })
                    clickedTileEntities.clear()
            }

            // Armor stands might be showing time until opened chests get refilled
            // Whenever an armor stand spawns, blacklist chest that it might be inside
            is S0EPacketSpawnObject -> {
                if (ignoreLooted.get() && packet.type == 78) {
                    val entity = mc.theWorld.getTileEntity(
                        BlockPos(packet.realX, packet.realY + 2.0, packet.realZ)
                    )

                    if (entity !is TileEntityChest && entity !is TileEntityEnderChest)
                        return

                    clickedTileEntities += entity
                }
            }
        }
    }

    @EventTarget
    fun onTick(event: TickEvent) {
        val player = mc.thePlayer ?: return
        val target = tileTarget ?: return

        val rotationToUse = RotationUtils.targetRotation ?: return

        val distance = sqrt(target.third)

        if (distance <= range.get()) {
            val pos = target.second.pos

            val rotationVec = getVectorForRotation(rotationToUse) * mc.playerController.blockReachDistance.toDouble()

            val visibleResult = performRayTrace(pos, rotationVec)
            val invisibleResult = performRaytrace(pos, rotationToUse)

            val resultToUse = if (visibleResult?.blockPos == pos) {
                visibleResult
            } else {
                if (invisibleResult?.blockPos == pos) {
                    invisibleResult
                } else null
            }

            resultToUse?.run {
                if (player.onPlayerRightClick(blockPos, sideHit, hitVec)) {
                    player.swingItem()

                    timer.reset()
                }
            }
        }
    }

    private fun shouldClickTileEntity(entity: TileEntity): Boolean {
        // Check if entity hasn't been clicked already
        if (entity in clickedTileEntities) return false

        // Check if entity is of correct type
        return when (entity) {
            is TileEntityChest -> {
                val block = getBlock(entity.pos)

                if (block !is BlockChest) return false

                // Check if there isn't a block above the chest (works even for double chests)
                block.getLockableContainer(mc.theWorld, entity.pos) != null
            }

            is TileEntityEnderChest -> getBlock(entity.pos.up())?.isNormalCube != true

            else -> false
        }
    }

    private var S0EPacketSpawnObject.realX
        get() = x / 32.0
        set(value) {
            x = (value * 32.0).roundToInt()
        }
    private var S0EPacketSpawnObject.realY
        get() = y / 32.0
        set(value) {
            y = (value * 32.0).roundToInt()
        }
    private var S0EPacketSpawnObject.realZ
        get() = z / 32.0
        set(value) {
            z = (value * 32.0).roundToInt()
        }
}
