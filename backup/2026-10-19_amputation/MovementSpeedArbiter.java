package com.FIYTP.realmedicalmod.loop;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = "realmedicalmod")
public class MovementSpeedArbiter {
    // 每玩家每 tick 的建议倍率
    private static final Map<UUID, Map<String, Float>> suggestions = new HashMap<>();

    private static final ResourceLocation MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath("realmedicalmod", "arbitrated_speed");

    public static void clear(ServerPlayer player) {
        if (player == null) return;
        suggestions.computeIfAbsent(player.getUUID(), k -> new HashMap<>()).clear();
    }

    public static void suggest(ServerPlayer player, String sourceId, float multiplier) {
        if (player == null || sourceId == null) return;
        suggestions.computeIfAbsent(player.getUUID(), k -> new HashMap<>())
                .put(sourceId, multiplier);
    }

    public static void apply(ServerPlayer player) {
        if (player == null) return;
        Map<String, Float> map = suggestions.get(player.getUUID());
        AttributeInstance attr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr == null) return;

        if (map == null || map.isEmpty()) {
            attr.removeModifier(MODIFIER_ID);
            return;
        }

        // 取最小值
        float minMultiplier = 1.0f;
        for (float m : map.values()) {
            if (m < minMultiplier) {
                minMultiplier = m;
            }
        }

        // 下限保护：不低于 0.05
        minMultiplier = Math.max(0.05f, minMultiplier);

        // 应用
        attr.removeModifier(MODIFIER_ID);
        double amount = (double) minMultiplier - 1.0;
        AttributeModifier modifier = new AttributeModifier(
                MODIFIER_ID, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr.addTransientModifier(modifier);
    }

    public static void clearPlayer(UUID uuid) {
        if (uuid != null) {
            suggestions.remove(uuid);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() != null) {
            clearPlayer(event.getEntity().getUUID());
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            clearPlayer(player.getUUID());
            AttributeInstance attr = player.getAttribute(Attributes.MOVEMENT_SPEED);
            if (attr != null) {
                attr.removeModifier(MODIFIER_ID);
            }
        }
    }
}
