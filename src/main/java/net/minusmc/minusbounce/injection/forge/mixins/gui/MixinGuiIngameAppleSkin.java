package net.minusmc.minusbounce.injection.forge.mixins.gui;

import net.minecraftforge.client.GuiIngameForge;
import net.minusmc.minusbounce.features.module.modules.render.AppleSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiIngameForge.class)
public class MixinGuiIngameAppleSkin {
    @Inject(method = "renderFood", at = @At("RETURN"), remap = false, require = 0)
    private void renderAppleSkin(int width, int height, CallbackInfo callbackInfo) {
        AppleSkin.INSTANCE.renderForgeFoodOverlay(width, height);
    }
}
