package net.minusmc.minusbounce.injection.forge.mixins.accessors;

import net.minecraft.item.ItemFood;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemFood.class)
public interface ItemFoodAccessor {
    @Accessor("potionId")
    int getPotionId();

    @Accessor("potionDuration")
    int getPotionDuration();

    @Accessor("potionAmplifier")
    int getPotionAmplifier();

    @Accessor("potionEffectProbability")
    float getPotionEffectProbability();
}
