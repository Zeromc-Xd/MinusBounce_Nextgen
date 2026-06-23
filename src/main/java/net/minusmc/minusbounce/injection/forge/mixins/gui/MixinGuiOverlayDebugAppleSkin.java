package net.minusmc.minusbounce.injection.forge.mixins.gui;

import net.minusmc.minusbounce.features.module.modules.render.AppleSkin;
import net.minecraft.client.gui.GuiOverlayDebug;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(GuiOverlayDebug.class)
public class MixinGuiOverlayDebugAppleSkin {
    @Inject(method = "call", at = @At("RETURN"), require = 0)
    private void appendAppleSkinDebug(CallbackInfoReturnable<List<String>> callbackInfoReturnable) {
        AppleSkin.INSTANCE.appendDebugLines(callbackInfoReturnable.getReturnValue());
    }
}
