/*
 * MinusBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/MinusMC/MinusBounce
 */
package net.minusmc.minusbounce.injection.forge.mixins.render;

import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.util.ResourceLocation;
import net.minusmc.minusbounce.MinusBounce;
import net.minusmc.minusbounce.features.module.modules.render.Glint;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(RenderItem.class)

public abstract class MixinRenderItem {
    @Final
    @Shadow
    private TextureManager textureManager;

    @Final
    @Shadow
    private static ResourceLocation RES_ITEM_GLINT;

    @Shadow
    public abstract void renderModel(IBakedModel model, int color);

    @Redirect(method = "renderEffect", at = @At(value="INVOKE", target="Lnet/minecraft/client/renderer/entity/RenderItem;renderModel(Lnet/minecraft/client/resources/model/IBakedModel;I)V"), require = 0)
    private void renderModel(RenderItem renderItem, IBakedModel model, int color) {
        final Glint glint = MinusBounce.moduleManager.getModule(Glint.class);

        assert glint != null;
        this.renderModel(model, glint.getState() ? glint.getColor().getRGB() : -8372020);
    }
}
