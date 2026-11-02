package com.FIYTP.realmedicalmod.loop;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.config.MedicalConfig;
import com.FIYTP.realmedicalmod.loop.AttributeArbiter.Dimension;
import net.minecraft.server.level.ServerPlayer;

/**
 * 疼痛影响评估模块（影响评估层）
 *
 * 替换旧 PainSuggestionModule 的离散查表（疼痛1~10 → 药水1~3级），
 * 改为连续线性插值：部位疼痛 p 直接换算成能力倍率 1 - slope*p。
 *
 * 体感疼痛（getPerceivedPain，0~55）是全部部位疼痛之和，
 * 作为全身性感知阈值（重度疼到眼冒金星/反胃）的来源；
 * 部位疼痛（getPain，0~10）决定各部位专属的能力衰减。
 */
public class PainEffectModule implements UpdateModule {

    @Override
    public void process(MedicalData data) {
        // 无 player 时的兜底
    }

    public void process(ServerPlayer player, MedicalData data, BuffManager buffManager) {
        if (player == null || data == null || buffManager == null) return;

        float legSlope = MedicalConfig.PAIN_LEG_SPEED_SLOPE.get().floatValue();
        float armAttackSlope = MedicalConfig.PAIN_ARM_ATTACK_SLOPE.get().floatValue();
        float armSpeedSlope = MedicalConfig.PAIN_ARM_SPEED_SLOPE.get().floatValue();
        float headBlindSlope = MedicalConfig.PAIN_HEAD_BLINDNESS_SLOPE.get().floatValue();
        float chestNauseaSlope = MedicalConfig.PAIN_CHEST_NAUSEA_SLOPE.get().floatValue();

        // 1. 各部位疼痛 → 连续能力倍率（多肢体独立提交，天然区分单/双侧）
        for (MedicalData.Limb limb : MedicalData.Limb.values()) {
            MedicalData.LimbData limbData = data.getLimb(limb);
            if (limbData == null) continue;
            float p = limbData.getPainLevel();
            if (p <= 0.01f) continue;

            switch (limb) {
                case LEFT_LEG, RIGHT_LEG -> {
                    float mul = 1.0f - legSlope * p;
                    AttributeArbiter.suggest(player, Dimension.MOVEMENT_SPEED,
                            "pain_" + limb.name().toLowerCase(), mul);
                }
                case LEFT_ARM, RIGHT_ARM -> {
                    String src = "pain_" + limb.name().toLowerCase();
                    AttributeArbiter.suggest(player, Dimension.ATTACK_DAMAGE, src,
                            1.0f - armAttackSlope * p);
                    AttributeArbiter.suggest(player, Dimension.ATTACK_SPEED, src,
                            1.0f - armSpeedSlope * p);
                }
                case HEAD -> {
                    // 头痛 → 失明药水连续插值（0~10 疼痛 → 0~1.5 级）
                    float levelF = headBlindSlope * p;
                    if (levelF >= 1.0f) {
                        buffManager.suggest(player, "blindness", (int) levelF);
                    }
                }
                case CHEST -> {
                    // 胸痛 → 反胃药水连续插值（重度胸痛引发干呕感）
                    float levelF = chestNauseaSlope * p;
                    if (levelF >= 1.0f) {
                        buffManager.suggest(player, "nausea", (int) levelF);
                    }
                }
                default -> {
                }
            }
        }

        // 2. 全身体感疼痛 → 感知阈值插值（眼冒金星/反胃）
        int perceived = data.getPerceivedPain();
        int blindnessThreshold = MedicalConfig.PERCEIVED_PAIN_BLINDNESS_THRESHOLD.get();
        int nauseaThreshold = MedicalConfig.PERCEIVED_PAIN_NAUSEA_THRESHOLD.get();

        if (perceived >= blindnessThreshold) {
            // 阈值 → 等级1，每超阈值 10 点升一级，上限 4
            int level = 1 + (perceived - blindnessThreshold) / 10;
            buffManager.suggest(player, "blindness", Math.min(level, 4));
        }
        if (perceived >= nauseaThreshold) {
            int level = 1 + (perceived - nauseaThreshold) / 10;
            buffManager.suggest(player, "nausea", Math.min(level, 4));
        }
    }
}
