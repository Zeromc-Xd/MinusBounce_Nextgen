package net.minusmc.minusbounce.features.special;

import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.util.Vec3;
import net.minusmc.minusbounce.utils.MinecraftInstance;
import net.minusmc.minusbounce.utils.PacketUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class LagRangePacketManager {
    private static final Object LOCK = new Object();
    private static final Deque<TimedPacket> OUTGOING_PACKETS = new ArrayDeque<>();

    private LagRangePacketManager() {
    }

    public static void enqueue(final Packet<?> packet) {
        if (packet == null) {
            return;
        }

        synchronized (LOCK) {
            OUTGOING_PACKETS.addLast(new TimedPacket(packet, System.currentTimeMillis()));
        }
    }

    public static void releaseExpired(final long delay) {
        final long now = System.currentTimeMillis();
        final List<Packet<?>> packets = new ArrayList<>();

        synchronized (LOCK) {
            while (!OUTGOING_PACKETS.isEmpty()) {
                final TimedPacket timedPacket = OUTGOING_PACKETS.peekFirst();
                if (timedPacket == null || now - timedPacket.time <= Math.max(delay, 0L)) {
                    break;
                }

                packets.add(OUTGOING_PACKETS.removeFirst().packet);
            }
        }

        sendPackets(packets);
    }

    public static void flushAll() {
        final List<Packet<?>> packets = new ArrayList<>();

        synchronized (LOCK) {
            while (!OUTGOING_PACKETS.isEmpty()) {
                packets.add(OUTGOING_PACKETS.removeFirst().packet);
            }
        }

        sendPackets(packets);
    }

    public static void clear() {
        synchronized (LOCK) {
            OUTGOING_PACKETS.clear();
        }
    }

    public static int queuedPackets() {
        synchronized (LOCK) {
            return OUTGOING_PACKETS.size();
        }
    }

    public static List<Vec3> queuedPositions() {
        final List<Vec3> positions = new ArrayList<>();

        synchronized (LOCK) {
            for (final TimedPacket timedPacket : OUTGOING_PACKETS) {
                if (timedPacket.packet instanceof C03PacketPlayer) {
                    final C03PacketPlayer packet = (C03PacketPlayer) timedPacket.packet;
                    if (packet.isMoving()) {
                        positions.add(new Vec3(packet.x, packet.y, packet.z));
                    }
                }
            }
        }

        return positions;
    }

    private static void sendPackets(final List<Packet<?>> packets) {
        for (final Packet<?> packet : packets) {
            PacketUtils.sendPacketNoEvent(packet);
            updateServerPosition(packet);
        }
    }

    private static void updateServerPosition(final Packet<?> packet) {
        if (packet instanceof C03PacketPlayer.C04PacketPlayerPosition
                || packet instanceof C03PacketPlayer.C06PacketPlayerPosLook) {
            final C03PacketPlayer movementPacket = (C03PacketPlayer) packet;
            MinecraftInstance.lastServerPosition = MinecraftInstance.serverPosition;
            MinecraftInstance.serverPosition = new Vec3(movementPacket.x, movementPacket.y, movementPacket.z);
            MinecraftInstance.rotIncrement = 3;
        } else if (packet instanceof C03PacketPlayer.C05PacketPlayerLook) {
            MinecraftInstance.rotIncrement = 3;
        }
    }

    private static final class TimedPacket {
        private final Packet<?> packet;
        private final long time;

        private TimedPacket(final Packet<?> packet, final long time) {
            this.packet = packet;
            this.time = time;
        }
    }
}
