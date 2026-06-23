package net.minusmc.minusbounce.features.module.modules.render;

import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public enum AppleSkinForgeEvents {
    INSTANCE;

    @SubscribeEvent
    public void onItemTooltip(ItemTooltipEvent event) {
        AppleSkin.INSTANCE.appendTooltip(event.itemStack, event.toolTip);
    }
}
