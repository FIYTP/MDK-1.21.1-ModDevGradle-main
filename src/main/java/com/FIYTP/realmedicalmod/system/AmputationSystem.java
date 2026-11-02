package com.FIYTP.realmedicalmod.system;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.capability.MedicalDataCapability;
import com.FIYTP.realmedicalmod.config.MedicalConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/**
 * 断肢系统：四肢受极大伤害时按概率截断。
 * 头部和躯干永不截肢。
 * 截断时：相邻部位承受剧痛与灾难性出血，断口成为开放创口（感染源）。
 */
public class AmputationSystem {

    /** 是否为可截肢部位（仅四肢） */
    public static boolean isAmputatable(MedicalData.Limb limb) {
        return limb == MedicalData.Limb.LEFT_ARM || limb == MedicalData.Limb.RIGHT_ARM
                || limb == MedicalData.Limb.LEFT_LEG || limb == MedicalData.Limb.RIGHT_LEG;
    }

    /** 相邻部位：断肢时承接剧痛与灾难性出血 */
    public static MedicalData.Limb getAdjacentLimb(MedicalData.Limb limb) {
        return switch (limb) {
            case LEFT_ARM, RIGHT_ARM -> MedicalData.Limb.CHEST;
            case LEFT_LEG, RIGHT_LEG -> MedicalData.Limb.CHEST;
            default -> MedicalData.Limb.CHEST;
        };
    }

    /**
     * 截肢判定入口：在肢体受到伤害后调用。
     * 仅四肢；伤害达到 AMPUTATION_DAMAGE_THRESHOLD 且掷中概率 → 截断。
     * 返回 true 表示发生了截肢。
     */
    public static boolean tryAmputate(Player player, MedicalData data, MedicalData.Limb limb, float amount) {
        if (!MedicalConfig.AMPUTATION_ENABLED.get()) return false;
        if (!isAmputatable(limb)) return false;
        MedicalData.LimbData limbData = data.getLimb(limb);
        if (limbData.isAmputated()) return false;

        float threshold = MedicalConfig.AMPUTATION_DAMAGE_THRESHOLD.get().floatValue();
        if (amount < threshold) return false;

        float chance = MedicalConfig.AMPUTATION_CHANCE.get().floatValue();
        if (MedicalConfig.AMPUTATION_SCALES_WITH_DAMAGE.get()) {
            // 超过阈值后，每多1点伤害提高 AMPUTATION_CHANCE_PER_DAMAGE 概率
            float bonus = (amount - threshold) * MedicalConfig.AMPUTATION_CHANCE_PER_DAMAGE.get().floatValue();
            chance += bonus;
        }
        chance = Math.min(chance, MedicalConfig.AMPUTATION_CHANCE_CAP.get().floatValue());

        if (player.getRandom().nextFloat() >= chance) return false;

        performAmputation(player, data, limb);
        return true;
    }

    /** 执行截肢：标记状态 + 相邻部位剧痛/灾难性出血 + 开放创口 + 提示与音效 */
    public static void performAmputation(Player player, MedicalData data, MedicalData.Limb limb) {
        MedicalData.LimbData limbData = data.getLimb(limb);
        limbData.setAmputated(true);

        // 断口：灾难性出血（截断肢体本身）+ 开放创口（感染源）
        int bleed = MedicalConfig.AMPUTATION_BLEEDING.get();
        limbData.setBleeding(limbData.getBleeding() + bleed);
        limbData.setOpenWound(true);
        limbData.setTourniqueted(false);
        limbData.setBandaged(false);

        // 相邻部位：剧痛 + 灾难性出血
        MedicalData.Limb adjacent = getAdjacentLimb(limb);
        MedicalData.LimbData adjData = data.getLimb(adjacent);
        int maxPain = MedicalConfig.MAX_PAIN_LEVEL.get();
        adjData.setPainLevel(Math.min(maxPain, adjData.getPainLevel() + MedicalConfig.AMPUTATION_ADJACENT_PAIN.get()));
        int adjBleed = MedicalConfig.AMPUTATION_ADJACENT_BLEEDING.get();
        adjData.setBleeding(adjData.getBleeding() + adjBleed);
        adjData.setInternalBleedingRate(adjData.getInternalBleedingRate() + MedicalConfig.AMPUTATION_ADJACENT_INTERNAL_BLEEDING.get());
        adjData.setOpenWound(true);

        // 剧痛本体
        limbData.setPainLevel(maxPain);

        // 提示与音效
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(Component.translatable("medical.amputation.happened",
                    Component.translatable("medical.limb." + limb.name().toLowerCase())));
            serverPlayer.level().playSound(null, serverPlayer.blockPosition(),
                    SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0f, 0.6f);
            serverPlayer.level().playSound(null, serverPlayer.blockPosition(),
                    SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 0.8f, 1.4f);
        }
    }

    /** 快捷判断（供移速/挖掘等功能限制与客户端渲染使用） */
    public static boolean isLimbAmputated(Player player, MedicalData.Limb limb) {
        return player.getData(MedicalDataCapability.MEDICAL_DATA).getLimb(limb).isAmputated();
    }
}
