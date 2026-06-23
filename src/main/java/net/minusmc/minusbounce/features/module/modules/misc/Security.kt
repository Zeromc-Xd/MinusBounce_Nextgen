package net.minusmc.minusbounce.features.module.modules.misc

import net.minecraft.network.play.client.C19PacketResourcePackStatus
import net.minecraft.network.play.server.S27PacketExplosion
import net.minecraft.network.play.server.S2APacketParticles
import net.minecraft.network.play.server.S2DPacketOpenWindow
import net.minecraft.network.play.server.S48PacketResourcePackSend
import net.minusmc.minusbounce.event.EventState
import net.minusmc.minusbounce.event.EventTarget
import net.minusmc.minusbounce.event.PacketEvent
import net.minusmc.minusbounce.features.module.Module
import net.minusmc.minusbounce.features.module.ModuleCategory
import net.minusmc.minusbounce.features.module.ModuleInfo
import net.minusmc.minusbounce.utils.ClientUtils
import net.minusmc.minusbounce.value.BoolValue
import java.io.UnsupportedEncodingException
import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlin.math.abs

@ModuleInfo(
    name = "SecurityFeatures",
    spacedName = "Security Features",
    description = "Patches common exploits",
    category = ModuleCategory.MISC,
)
class Security : Module() {
    private val antiResourcePackExploit = BoolValue("Anti-Resource-Pack-Exploit", true)
    private val antiCrashExploits = BoolValue("Anti-Crash-Exploits", true)

    @EventTarget
    fun onUpdate(event: PacketEvent) {
        if (mc.thePlayer == null || mc.theWorld == null) return
        if (antiResourcePackExploit.get()) {
            if (event.eventType === EventState.RECEIVE) {
                if (event.packet is S48PacketResourcePackSend) {
                    val s48 = event.packet
                    var url = s48.url
                    val hash = s48.hash
                    try {
                        val uri = URI(url)
                        val scheme = uri.scheme
                        val isLevelProtocol = "level" == scheme
                        if ("http" != scheme && "https" != scheme && !isLevelProtocol) {
                            mc.thePlayer.sendQueue.addToSendQueue(
                                C19PacketResourcePackStatus(
                                    hash, C19PacketResourcePackStatus.Action.FAILED_DOWNLOAD
                                )
                            )
                            event.isCancelled = true
                        }
                        url = URLDecoder.decode(url.substring("level://".length), StandardCharsets.UTF_8.toString())
                        if (isLevelProtocol && (url.contains("..") || !url.endsWith("/resources.zip"))) {
                            ClientUtils.displayChatMessage("SERVER TRIED TO ACCESS YOUR FILE USING EXPLOITS: $uri")
                            event.isCancelled = true
                        }
                    } catch (ex: URISyntaxException) {
                        event.isCancelled = true
                    } catch (ex: UnsupportedEncodingException) {
                        event.isCancelled = true
                    }
                }
            }
        }
        if (antiCrashExploits.get()) {
            if (event.packet is S2APacketParticles) {
                val packetParticles = event.packet
                if (packetParticles.particleCount > 400 || abs(packetParticles.particleSpeed.toDouble()) > 400) {
                    event.isCancelled = true
                    ClientUtils.displayChatMessage("Server tried to crash the client with particles packet")
                }
            }
            if (event.packet is S27PacketExplosion) {
                val ePacket = event.packet
                if (abs(ePacket.strength.toDouble()) > 99 || abs(ePacket.x) > 99 || abs(
                        ePacket.z
                    ) > 99 || abs(ePacket.y) > 99
                ) {
                    event.isCancelled = true
                    ClientUtils.displayChatMessage("Server tried to crash the client with explosion packet")
                }
            }
            if (event.packet is S2DPacketOpenWindow) {
                if (abs(event.packet.slotCount.toDouble()) > 70) {
                    ClientUtils.displayChatMessage("Server tried to crash the client with window packet")
                    event.isCancelled = true
                }
            }
        }
    }

}