package com.FIYTP.realmedicalmod.event;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;

@EventBusSubscriber(modid = "realmedicalmod", value = Dist.CLIENT)
public class HealthBarRemover {
    private static final ResourceLocation PLAYER_HEALTH =
            ResourceLocation.fromNamespaceAndPath("minecraft", "player_health");

    @SubscribeEvent
    public static void onRenderGuiLayer(RenderGuiLayerEvent.Pre event) {
        if (PLAYER_HEALTH.equals(event.getName())) {
            event.setCanceled(true);
        }
    }
}