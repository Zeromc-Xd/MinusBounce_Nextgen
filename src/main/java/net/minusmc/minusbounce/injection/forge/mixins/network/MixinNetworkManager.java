package net.minusmc.minusbounce.injection.forge.mixins.network;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.util.Vec3;
import net.minusmc.minusbounce.MinusBounce;
import net.minusmc.minusbounce.event.EventState;
import net.minusmc.minusbounce.event.PacketEvent;
import net.minusmc.minusbounce.features.module.modules.client.HUD;
import net.minusmc.minusbounce.utils.RotationUtils;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static net.minusmc.minusbounce.utils.MinecraftInstance.*;

@SideOnly(Side.CLIENT)
@Mixin(value = NetworkManager.class, priority = 9999)
public class MixinNetworkManager {

    @Inject(method = "channelRead0*", at = @At("HEAD"), cancellable = true)
    private void read(ChannelHandlerContext context, Packet<?> packet, CallbackInfo callback) {
        final PacketEvent event = new PacketEvent(packet, EventState.RECEIVE);
        MinusBounce.eventManager.callEvent(event);

        RotationUtils.trackIncomingPacket(packet, !event.isCancelled());

        if(event.isCancelled()) {
            callback.cancel();
        }
    }

    @Inject(method = "sendPacket(Lnet/minecraft/network/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void send(Packet<?> packet, CallbackInfo callback) {
        final PacketEvent event = new PacketEvent(packet, EventState.SEND);
        MinusBounce.eventManager.callEvent(event);

        if(event.isCancelled()) {
            RotationUtils.trackOutgoingPacket(packet, false);
            callback.cancel();
            return;
        }

        RotationUtils.trackOutgoingPacket(packet, true);
        updateServerPosition(packet);
    }

    @Inject(method = "sendPacket(Lnet/minecraft/network/Packet;Lio/netty/util/concurrent/GenericFutureListener;[Lio/netty/util/concurrent/GenericFutureListener;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void send2(Packet<?> packet,
                       GenericFutureListener<? extends Future<? super Void>> listener,
                       GenericFutureListener<? extends Future<? super Void>>[] listeners,
                       CallbackInfo callback) {
        RotationUtils.trackOutgoingPacket(packet, true);
        updateServerPosition(packet);
    }

    private void updateServerPosition(Packet<?> packet) {
        if (packet instanceof C03PacketPlayer.C04PacketPlayerPosition || packet instanceof C03PacketPlayer.C05PacketPlayerLook || packet instanceof C03PacketPlayer.C06PacketPlayerPosLook) {
            final C03PacketPlayer p = (C03PacketPlayer)packet;

            rotIncrement = 3;
            if (!(packet instanceof C03PacketPlayer.C05PacketPlayerLook)) {
                lastServerPosition = serverPosition;
                serverPosition = new Vec3(p.x, p.y, p.z);
            }
        }
    }

    @Inject(method = "getIsencrypted", at = @At("HEAD"), cancellable = true)
    private void injectEncryption(CallbackInfoReturnable<Boolean> cir) {
        final HUD hud = MinusBounce.moduleManager.getModule(HUD.class);
        if(hud != null && hud.getTabHead().get()) {
            cir.setReturnValue(true);
        }
    }

}
