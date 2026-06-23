/*
 * MinusBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/MinusMC/MinusBounce
 */
package net.minusmc.minusbounce.injection.forge.mixins.util;

import net.minecraft.util.Timer;
import net.minusmc.minusbounce.features.module.modules.combat.TimerRange;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Timer.class)
public abstract class MixinTimer {
    @Shadow
    public float renderPartialTicks;

    @Shadow
    public float elapsedPartialTicks;

    @Inject(method = "updateTimer", at = @At("TAIL"))
    public void updateTimer(CallbackInfo ci){
        if (!TimerRange.freezeAnimation()) this.renderPartialTicks = this.elapsedPartialTicks;
    }
}