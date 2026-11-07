package com.FIYTP.realmedicalmod.loop;

import com.FIYTP.realmedicalmod.RealMedicalMod;
import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.capability.MedicalDataCapability;
import com.FIYTP.realmedicalmod.config.MedicalConfig;
import com.FIYTP.realmedicalmod.treatment.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 连续属性仲裁器（表现层统一入口 —— 含意识归零完全失能锁死 + II/III 级阿片成瘾未注射戒断行动受限）
 */
@EventBusSubscriber(modid = RealMedicalMod.MODID)
public class AttributeArbiter {

    /** 能力维度 */
    public enum Dimension {
        MOVEMENT_SPEED(Attributes.MOVEMENT_SPEED, "arbitrated_movement_speed"),
        ATTACK_DAMAGE(Attributes.ATTACK_DAMAGE, "arbitrated_attack_damage"),
        ATTACK_SPEED(Attributes.ATTACK_SPEED, "arbitrated_attack_speed");

        final net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute;
        final String modifierPath;

        Dimension(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, String modifierPath) {
            this.attribute = attribute;
            this.modifierPath = modifierPath;
        }

        ResourceLocation modifierId() {
            return ResourceLocation.fromNamespaceAndPath(RealMedicalMod.MODID, modifierPath);
        }
    }

    // 每玩家每维度：来源ID -> 倍率
    private static final Map<UUID, Map<Dimension, Map<String, Float>>> SUGGESTIONS = new HashMap<>();

    /** 清空某玩家本轮全部建议（每秒循环开头调用） */
    public static void clear(ServerPlayer player) {
        if (player == null) return;
        SUGGESTIONS.computeIfAbsent(player.getUUID(), k -> new EnumMap<>(Dimension.class)).clear();
    }

    /** 提交一个连续倍率建议（0~1，1=正常） */
    public static void suggest(ServerPlayer player, Dimension dimension, String sourceId, float multiplier) {
        if (player == null || dimension == null || sourceId == null) return;
        SUGGESTIONS.computeIfAbsent(player.getUUID(), k -> new EnumMap<>(Dimension.class))
                .computeIfAbsent(dimension, k -> new HashMap<>())
                .put(sourceId, multiplier);
    }

    /** 每秒循环结尾调用：把每维度 min 倍率写入属性 modifier */
    public static void apply(ServerPlayer player) {
        if (player == null) return;

        MedicalData data = player.getData(MedicalDataCapability.MEDICAL_DATA);
        boolean unconscious = (data != null) && (data.getConsciousness() <= 0 || data.isCardiacArrest());

        // ★ II 级与 III 级阿片成瘾：若未注射阿片（体内阿片浓度低于压制阈值），触发戒断行动受限！ ★
        boolean withdrawalRestricted = (data != null) && ModItems.isInWithdrawalMovementRestriction(data);
        if (withdrawalRestricted) {
            int tier = ModItems.getAddictionTier(data.getAddictionLevel());
            float withdrawalMult = (tier >= 3)
                    ? MedicalConfig.ADDICTION_WITHDRAWAL_SPEED_MULT_T3.get().floatValue()
                    : MedicalConfig.ADDICTION_WITHDRAWAL_SPEED_MULT_T2.get().floatValue();
            suggest(player, Dimension.MOVEMENT_SPEED, "opioid_withdrawal", withdrawalMult);
            suggest(player, Dimension.ATTACK_DAMAGE, "opioid_withdrawal", withdrawalMult);
            suggest(player, Dimension.ATTACK_SPEED, "opioid_withdrawal", withdrawalMult);
        }

        if ((unconscious || withdrawalRestricted) && player.isSprinting()) {
            player.setSprinting(false);
        }

        Map<Dimension, Map<String, Float>> byDim = SUGGESTIONS.get(player.getUUID());

        for (Dimension dim : Dimension.values()) {
            AttributeInstance attr = player.getAttribute(dim.attribute);
            if (attr == null) continue;

            // 意识归零或心脏骤停时：直接将移动速度、攻击力、攻速倍率锁死为 0.0f（即 modifier = -1.0f）
            if (unconscious) {
                attr.removeModifier(dim.modifierId());
                attr.addTransientModifier(new AttributeModifier(
                        dim.modifierId(), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                continue;
            }

            Map<String, Float> map = (byDim == null) ? null : byDim.get(dim);
            if (map == null || map.isEmpty()) {
                attr.removeModifier(dim.modifierId());
                continue;
            }

            float min = 1.0f;
            for (float m : map.values()) {
                if (m < min) min = m;
            }
            min = Math.max(MedicalConfig.ARBITER_MIN_MULTIPLIER.get().floatValue(), min);

            attr.removeModifier(dim.modifierId());
            if (min < 1.0f) {
                attr.addTransientModifier(new AttributeModifier(
                        dim.modifierId(), min - 1.0f, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        }
    }

    /** 玩家登出/重生时清除全部状态，防止 modifier 残留 */
    private static void removeAll(ServerPlayer player) {
        if (player == null) return;
        SUGGESTIONS.remove(player.getUUID());
        for (Dimension dim : Dimension.values()) {
            AttributeInstance attr = player.getAttribute(dim.attribute);
            if (attr != null) {
                attr.removeModifier(dim.modifierId());
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            removeAll(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            removeAll(player);
        }
    }
}