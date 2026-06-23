package net.minusmc.minusbounce.injection.forge.mixins.accessors;

import net.minecraft.util.FoodStats;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(FoodStats.class)
public interface FoodStatsAccessor {
    @Accessor("foodExhaustionLevel")
    float getFoodExhaustionLevel();
}
