/*
 * MinusBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/MinusMC/MinusBounce
 */
package net.minusmc.minusbounce.injection.forge.mixins.network;

import io.netty.buffer.Unpooled;
import net.minecraft.client.ClientBrandRetriever;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiDownloadTerrain;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.DataWatcher;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.PacketThreadUtil;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C17PacketCustomPayload;
import net.minecraft.network.play.client.C19PacketResourcePackStatus;
import net.minecraft.network.play.server.*;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.WorldSettings;
import net.minusmc.minusbounce.MinusBounce;
import net.minusmc.minusbounce.event.EntityDamageEvent;
import net.minusmc.minusbounce.event.EntityMovementEvent;
import net.minusmc.minusbounce.event.PostVelocityEvent;
import net.minusmc.minusbounce.features.module.modules.misc.NoRotateSet;
import net.minusmc.minusbounce.features.module.modules.misc.Patcher;
import net.minusmc.minusbounce.features.special.AntiForge;
import net.minusmc.minusbounce.ui.client.clickgui.dropdown.DropDownClickGui;
import net.minusmc.minusbounce.ui.client.hud.designer.GuiHudDesigner;
import net.minusmc.minusbounce.utils.ClientUtils;
import net.minusmc.minusbounce.utils.MinecraftInstance;
import net.minusmc.minusbounce.utils.Rotation;
import net.minusmc.minusbounce.utils.RotationUtils;
import net.minusmc.minusbounce.utils.movement.MovementFixType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.UUID;

@Mixin(NetHandlerPlayClient.class)
public abstract class MixinNetHandlerPlayClient {

    @Shadow
    @Final
    public NetworkManager netManager;

    @Shadow
    private boolean doneLoadingTerrain;

    @Shadow
    private Minecraft gameController;

    @Shadow
    public WorldClient clientWorldController;

    @Shadow
    public int currentServerMaxPlayers;

    @Shadow
    public abstract NetworkPlayerInfo getPlayerInfo(UUID p_175102_1_);

    @Inject(method = "handleSpawnPlayer", at = @At("HEAD"), cancellable = true)
    private void handleSpawnPlayer(S0CPacketSpawnPlayer packetIn, CallbackInfo callbackInfo) {
        if (Patcher.INSTANCE.getSilentNPESP().get()) {
            try {
                PacketThreadUtil.checkThreadAndEnqueue(packetIn, (NetHandlerPlayClient) (Object) this, gameController);
                double d0 = (double)packetIn.getX() / 32.0D;
                double d1 = (double)packetIn.getY() / 32.0D;
                double d2 = (double)packetIn.getZ() / 32.0D;
                float f = (float)(packetIn.getYaw() * 360) / 256.0F;
                float f1 = (float)(packetIn.getPitch() * 360) / 256.0F;
                EntityOtherPlayerMP entityotherplayermp = new EntityOtherPlayerMP(gameController.theWorld, getPlayerInfo(packetIn.getPlayer()).getGameProfile());
                entityotherplayermp.prevPosX = entityotherplayermp.lastTickPosX = entityotherplayermp.serverPosX = packetIn.getX();
                entityotherplayermp.prevPosY = entityotherplayermp.lastTickPosY = entityotherplayermp.serverPosY = packetIn.getY();
                entityotherplayermp.prevPosZ = entityotherplayermp.lastTickPosZ = entityotherplayermp.serverPosZ = packetIn.getZ();
                int i = packetIn.getCurrentItemID();

                if (i == 0)
                {
                    entityotherplayermp.inventory.mainInventory[entityotherplayermp.inventory.currentItem] = null;
                }
                else
                {
                    entityotherplayermp.inventory.mainInventory[entityotherplayermp.inventory.currentItem] = new ItemStack(Item.getItemById(i), 1, 0);
                }

                entityotherplayermp.setPositionAndRotation(d0, d1, d2, f, f1);
                clientWorldController.addEntityToWorld(packetIn.getEntityID(), entityotherplayermp);
                List<DataWatcher.WatchableObject> list = packetIn.func_148944_c();

                if (list != null)
                {
                    entityotherplayermp.getDataWatcher().updateWatchedObjectsFromList(list);
                }
            } catch (Exception e) {
                // ignore
            }
            callbackInfo.cancel();
        }
    }


    @Inject(method = "handleCloseWindow", at = @At("HEAD"), cancellable = true)
    private void handleCloseWindow(final S2EPacketCloseWindow packetIn, final CallbackInfo callbackInfo) {
        if (this.gameController.currentScreen instanceof DropDownClickGui
            || this.gameController.currentScreen instanceof GuiHudDesigner 
            || this.gameController.currentScreen instanceof GuiChat)
            callbackInfo.cancel();
    }

    @Inject(method = "handleResourcePack", at = @At("HEAD"), cancellable = true)
    private void handleResourcePack(final S48PacketResourcePackSend p_handleResourcePack_1_, final CallbackInfo callbackInfo) {
        final String url = p_handleResourcePack_1_.getURL();
        final String hash = p_handleResourcePack_1_.getHash();

        try {
            final String scheme = new URI(url).getScheme();
            final boolean isLevelProtocol = "level".equals(scheme);

            if(!"http".equals(scheme) && !"https".equals(scheme) && !isLevelProtocol)
                throw new URISyntaxException(url, "Wrong protocol");

            if(isLevelProtocol && (url.contains("..") || !url.endsWith(".zip"))) {
                String s2 = url.substring("level://".length());
                File file1 = new File(this.gameController.mcDataDir, "saves");
                File file2 = new File(file1, s2);

                if (file2.isFile() && !url.toLowerCase().contains("minusbounce")) {
                    netManager.sendPacket(new C19PacketResourcePackStatus(hash, C19PacketResourcePackStatus.Action.ACCEPTED)); // perform like vanilla
                    netManager.sendPacket(new C19PacketResourcePackStatus(hash, C19PacketResourcePackStatus.Action.SUCCESSFULLY_LOADED));
                } else {
                    netManager.sendPacket(new C19PacketResourcePackStatus(hash, C19PacketResourcePackStatus.Action.FAILED_DOWNLOAD));
                }
                
                callbackInfo.cancel(); // despite not having it enabled we still prevents anything from illegally checking files in your computer.
                
            }
        } catch (final URISyntaxException e) {
            ClientUtils.INSTANCE.getLogger().error("Failed to handle resource pack", e);
            netManager.sendPacket(new C19PacketResourcePackStatus(hash, C19PacketResourcePackStatus.Action.FAILED_DOWNLOAD));
            callbackInfo.cancel();
        } catch (final IllegalStateException e) {
            ClientUtils.INSTANCE.getLogger().error("Failed to handle resource pack", e);
            callbackInfo.cancel();
        }
    }

    @Inject(method = "handleJoinGame", at = @At("HEAD"), cancellable = true)
    private void handleJoinGameWithAntiForge(S01PacketJoinGame packetIn, final CallbackInfo callbackInfo) {
        if(!AntiForge.enabled || !AntiForge.blockFML || Minecraft.getMinecraft().isIntegratedServerRunning())
            return;

        PacketThreadUtil.checkThreadAndEnqueue(packetIn, (NetHandlerPlayClient) (Object) this, gameController);
        this.gameController.playerController = new PlayerControllerMP(gameController, (NetHandlerPlayClient) (Object) this);
        this.clientWorldController = new WorldClient((NetHandlerPlayClient) (Object) this, new WorldSettings(0L, packetIn.getGameType(), false, packetIn.isHardcoreMode(), packetIn.getWorldType()), packetIn.getDimension(), packetIn.getDifficulty(), this.gameController.mcProfiler);
        this.gameController.gameSettings.difficulty = packetIn.getDifficulty();
        this.gameController.loadWorld(this.clientWorldController);
        this.gameController.thePlayer.dimension = packetIn.getDimension();
        this.gameController.displayGuiScreen(new GuiDownloadTerrain((NetHandlerPlayClient) (Object) this));
        this.gameController.thePlayer.setEntityId(packetIn.getEntityId());
        this.currentServerMaxPlayers = packetIn.getMaxPlayers();
        this.gameController.thePlayer.setReducedDebug(packetIn.isReducedDebugInfo());
        this.gameController.playerController.setGameType(packetIn.getGameType());
        this.gameController.gameSettings.sendSettingsToServer();
        this.netManager.sendPacket(new C17PacketCustomPayload("MC|Brand", (new PacketBuffer(Unpooled.buffer())).writeString(ClientBrandRetriever.getClientModName())));
        callbackInfo.cancel();
    }

    @Inject(method = "handleEntityMovement", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;onGround:Z"))
    private void handleEntityMovementEvent(S14PacketEntity packetIn, final CallbackInfo callbackInfo) {
        final Entity entity = packetIn.getEntity(this.clientWorldController);

        if(entity != null)
            MinusBounce.eventManager.callEvent(new EntityMovementEvent(entity));
    }

    /**
     * Handles changes in player positioning and rotation such as when travelling to a new dimension, (re)spawning,
     * mounting horses etc. Seems to immediately reply to the server with the clients post-processing perspective on the
     * player positioning
     *
     * @author fmcpe
     * @reason misc
     */
    @Overwrite
    public void handlePlayerPosLook(S08PacketPlayerPosLook packetIn) {
        PacketThreadUtil.checkThreadAndEnqueue(packetIn, (NetHandlerPlayClient) (Object) this, this.gameController);
        EntityPlayer entityplayer = this.gameController.thePlayer;
        double d0 = packetIn.getX();
        double d1 = packetIn.getY();
        double d2 = packetIn.getZ();
        float f = packetIn.getYaw();
        float f1 = packetIn.getPitch();

        if (packetIn.func_179834_f().contains(S08PacketPlayerPosLook.EnumFlags.X)) {
            d0 += entityplayer.posX;
        } else {
            entityplayer.motionX = 0.0D;
        }

        if (packetIn.func_179834_f().contains(S08PacketPlayerPosLook.EnumFlags.Y)) {
            d1 += entityplayer.posY;
        } else {
            entityplayer.motionY = 0.0D;
        }

        if (packetIn.func_179834_f().contains(S08PacketPlayerPosLook.EnumFlags.Z)) {
            d2 += entityplayer.posZ;
        } else {
            entityplayer.motionZ = 0.0D;
        }

        if (packetIn.func_179834_f().contains(S08PacketPlayerPosLook.EnumFlags.X_ROT)) {
            f1 += entityplayer.rotationPitch;
        }

        if (packetIn.func_179834_f().contains(S08PacketPlayerPosLook.EnumFlags.Y_ROT)) {
            f += entityplayer.rotationYaw;
        }

        final float prevYaw = entityplayer.rotationYaw;
        final float prevPitch = entityplayer.rotationPitch;

        entityplayer.setPositionAndRotation(d0, d1, d2, f, f1);
        this.netManager.sendPacket(new C03PacketPlayer.C06PacketPlayerPosLook(entityplayer.posX, entityplayer.getEntityBoundingBox().minY, entityplayer.posZ, entityplayer.rotationYaw, entityplayer.rotationPitch, false));

        if (!this.doneLoadingTerrain) {
            this.gameController.thePlayer.prevPosX = this.gameController.thePlayer.posX;
            this.gameController.thePlayer.prevPosY = this.gameController.thePlayer.posY;
            this.gameController.thePlayer.prevPosZ = this.gameController.thePlayer.posZ;
            this.doneLoadingTerrain = true;
            this.gameController.displayGuiScreen(null);
        } else {
            NoRotateSet noRotateSetModule = MinusBounce.moduleManager.getModule(NoRotateSet.class);
            if (noRotateSetModule != null && noRotateSetModule.getState()) {
                entityplayer.setPositionAndRotation(d0, d1, d2, prevYaw, prevPitch);
                RotationUtils.INSTANCE.setRotations(new Rotation(f, f1), 2, 360f, MovementFixType.FULL);
            }
        }
    }

    @Inject(method = "handleEntityStatus", at = @At("HEAD"))
    public void handleDamagePacket(S19PacketEntityStatus packetIn, CallbackInfo callbackInfo) {
        if (packetIn.getOpCode() == 2) {
            Entity entity = packetIn.getEntity(this.clientWorldController);
            if (entity != null) {
                MinusBounce.eventManager.callEvent(new EntityDamageEvent(entity));
                if (entity instanceof EntityPlayer)
                    MinusBounce.hud.handleDamage((EntityPlayer) entity);
            }
        }
    }

    @Inject(method = "handleEntityVelocity", at = @At("RETURN"))
    public void onPostHandleEntityVelocity(S12PacketEntityVelocity packet, CallbackInfo ci) {
        if(MinecraftInstance.mc.thePlayer == null ||
                MinecraftInstance.mc.theWorld == null) {
            return;
        }

        if (packet.getEntityID() == MinecraftInstance.mc.thePlayer.getEntityId()) {
            MinusBounce.eventManager.callEvent(new PostVelocityEvent());
        }
    }

    @Redirect(
        method = "handleUpdateSign",
        slice = @Slice(from = @At(value = "CONSTANT", args = "stringValue=Unable to locate sign at ", ordinal = 0)),
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/EntityPlayerSP;addChatMessage(Lnet/minecraft/util/IChatComponent;)V", ordinal = 0)
    )
    private void removeDebugMessage(EntityPlayerSP instance, IChatComponent component) {
        
    }
}