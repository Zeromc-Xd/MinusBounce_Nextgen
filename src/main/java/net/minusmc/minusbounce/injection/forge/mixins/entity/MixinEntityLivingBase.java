/*
 * MinusBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/MinusMC/MinusBounce
 */
package net.minusmc.minusbounce.injection.forge.mixins.entity;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minusmc.minusbounce.MinusBounce;
import net.minusmc.minusbounce.event.JumpEvent;
import net.minusmc.minusbounce.utils.movement.MovementCorrection;
import net.minusmc.minusbounce.event.LookEvent;
import net.minusmc.minusbounce.features.module.modules.client.Animations;
import net.minusmc.minusbounce.features.module.modules.movement.NoJumpDelay;
import net.minusmc.minusbounce.features.module.modules.render.AntiBlind;
import net.minusmc.minusbounce.injection.implementations.IEntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.Iterator;
import java.util.Objects;

@Mixin(EntityLivingBase.class)
public abstract class MixinEntityLivingBase extends MixinEntity implements IEntityLivingBase {


    @Shadow
    protected abstract float getJumpUpwardsMotion();

    @Shadow
    public abstract IAttributeInstance getEntityAttribute(IAttribute attribute);

    @Shadow
    public abstract PotionEffect getActivePotionEffect(Potion potionIn);

    @Shadow
    public abstract boolean isPotionActive(Potion potionIn);

    @Shadow
    public int jumpTicks;

    @Shadow
    public abstract boolean isOnLadder();

    @Shadow
    public float prevRotationYawHead;

    @Shadow
    public abstract void setLastAttacker(Entity entityIn);

    @Shadow
    public void onLivingUpdate() {
    }

    @Shadow
    public void onUpdate() {
    }

    @Shadow
    protected abstract void updateFallState(double y, boolean onGroundIn, Block blockIn, BlockPos pos);

    @Shadow
    public abstract float getHealth();

    @Shadow
    public abstract ItemStack getHeldItem();

    @Shadow public float moveStrafing;

    @Shadow public float moveForward;

    private double realPosX;
    
    public double getRealPosX() {
        return realPosX;
    }

    public void setRealPosX(double x) {
        realPosX = x;
    }

    private double realPosY;

    public double getRealPosY() {
        return realPosY;
    }

    public void setRealPosY(double y) {
        realPosY = y;
    }

    private double realPosZ;

    public double getRealPosZ() {
        return realPosZ;
    }

    public void setRealPosZ(double z) {
        realPosZ = z;
    }

    @Inject(method = "updatePotionEffects", at = @At(value = "INVOKE", target = "Lnet/minecraft/potion/PotionEffect;onUpdate(Lnet/minecraft/entity/EntityLivingBase;)Z"),
        locals = LocalCapture.CAPTURE_FAILSOFT, cancellable = true)
    private void checkPotionEffect(CallbackInfo ci, Iterator<Integer> iterator, Integer integer, PotionEffect potioneffect) {
        if (potioneffect == null)
            ci.cancel();
    }

    /**
     * @author fmcpe
     * @reason JumpEvent
     */
    @Overwrite
    protected void jump() {
        float jumpMotion = this.getJumpUpwardsMotion();
        float jumpYaw = this.rotationYaw;

        if ((Object) this == Minecraft.getMinecraft().thePlayer) {
            final JumpEvent event = new JumpEvent(jumpMotion, jumpYaw);
            MinusBounce.eventManager.callEvent(event);
            MovementCorrection.correctJump(event);
            if (event.isCancelled()) {
                return;
            }
            jumpMotion = event.getMotion();
            jumpYaw = event.getYaw();
        }

        this.motionY = jumpMotion;

        if (this.isPotionActive(Potion.jump)) {
            this.motionY += (float) (this.getActivePotionEffect(Potion.jump).getAmplifier() + 1) * 0.1F;
        }

        if (this.isSprinting()) {
            final float f = jumpYaw * 0.017453292F;
            this.motionX -= MathHelper.sin(f) * 0.2F;
            this.motionZ += MathHelper.cos(f) * 0.2F;
        }

        this.isAirBorne = true;
    }

    /**
     * @author .
     * @reason .
     */
    @Overwrite
    public Vec3 getLook(float partialTicks){
        final LookEvent event = new LookEvent(this.rotationYaw, this.rotationPitch, this.prevRotationYaw, this.prevRotationPitch);
        MinusBounce.eventManager.callEvent(event);
        if(partialTicks == 1.0F){
            return this.getVectorForRotation(event.getPitch(), event.getYaw());
        } else {
            final float f = event.getLastPitch() + (event.getPitch() - event.getLastPitch()) * partialTicks;
            final float f1 = event.getLastYaw() + (event.getYaw() - event.getLastYaw()) * partialTicks;
            return this.getVectorForRotation(f, f1);
        }
    }

    @Inject(method = "onLivingUpdate", at = @At("HEAD"))
    private void headLiving(CallbackInfo callbackInfo) {
        if (
            Objects.requireNonNull(MinusBounce.moduleManager.getModule(NoJumpDelay.class)).getState()
        ) {
            jumpTicks = 0;
        }
    }

    @Inject(method = "isPotionActive(Lnet/minecraft/potion/Potion;)Z", at = @At("HEAD"), cancellable = true)
    private void isPotionActive(Potion p_isPotionActive_1_, final CallbackInfoReturnable<Boolean> callbackInfoReturnable) {
        final AntiBlind antiBlind = MinusBounce.moduleManager.getModule(AntiBlind.class);

        if ((p_isPotionActive_1_ == Potion.confusion || p_isPotionActive_1_ == Potion.blindness)) {
            assert antiBlind != null;
            if (antiBlind.getState() && antiBlind.getConfusionEffect().get()) callbackInfoReturnable.setReturnValue(false);
        }
    }

    /**
     * @reason visionfx sucks
     * @author i don't know
     */
    @Overwrite
    private int getArmSwingAnimationEnd() {
        int speed = Objects.requireNonNull(MinusBounce.moduleManager.getModule(Animations.class)).getState() ? 2 + (20 - Animations.INSTANCE.getSpeedSwing().get()) : 6;
        return this.isPotionActive(Potion.digSpeed) ? speed - (1 + this.getActivePotionEffect(Potion.digSpeed).getAmplifier()) : (this.isPotionActive(Potion.digSlowdown) ? speed + (1 + this.getActivePotionEffect(Potion.digSlowdown).getAmplifier()) * 2 : speed);
    }

}
