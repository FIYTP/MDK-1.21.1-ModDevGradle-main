package com.FIYTP.realmedicalmod.loop;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.config.MedicalConfig;
import com.FIYTP.realmedicalmod.treatment.ModItems;
import com.FIYTP.realmedicalmod.loop.AttributeArbiter.Dimension;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

/**
 * 疼痛影响评估模块（非线性高痛权重 + 全身疼痛牵制升级版）
 *
 * 核心设计（所有系数均由 {@link MedicalConfig} 驱动，零硬编码）：
 * 1. 单肢体非线性惩罚曲线：
 *    单肢疼痛 (0 ~ MAX_PAIN_LEVEL) 按幂函数 {@code (p / maxPain) ^ PAIN_ACTION_EXPONENT} 叠加线性项转化为能力衰减。
 *    轻痛 (1~3级) 影响轻微，高痛 (8~10级) 惩罚呈指数级陡升，单腿 10 级疼痛可削减高达 {@code PAIN_LEG_MAX_SPEED_PENALTY} 的移速。
 * 2. 双腿复合惩罚：
 *    当双腿同时存在疼痛时，向 {@link AttributeArbiter} 提交双腿复合乘算倍率，反映双下肢同时受损时的行动失能。
 * 3. 总体体感疼痛 ({@code getPerceivedPain()}, 0 ~ MAX_PERCEIVED_PAIN) 全身性行动力压制：
 *    即便双腿完好，当头部/胸腔/手臂达到剧痛推高总体疼痛时，也会通过 {@code "pain_systemic"} 源按
 *    {@code PAIN_SYSTEMIC_MAX_SPEED_PENALTY} 与 {@code PAIN_SYSTEMIC_MAX_ACTION_PENALTY} 压制全身移速与攻速；
 *    并在达到 {@code PAIN_SPRINT_LOCK_THRESHOLD} 时自动打断疾跑。
 */
public class PainEffectModule implements UpdateModule {

    @Override
    public void process(MedicalData data) {
        // 无 player 时的兜底
    }

    public void process(ServerPlayer player, MedicalData data, BuffManager buffManager) {
        if (player == null || data == null || buffManager == null) return;

        float maxLimbPain = Math.max(1.0f, MedicalConfig.MAX_PAIN_LEVEL.get().floatValue());
        float maxPerceivedPain = Math.max(10.0f, MedicalConfig.MAX_PERCEIVED_PAIN.get().floatValue());
        float actionExponent = Math.max(1.0f, MedicalConfig.PAIN_ACTION_EXPONENT.get().floatValue());

        // 线性斜率与满痛最大惩罚上限（均从 MedicalConfig 读取）
        float legSlope = MedicalConfig.PAIN_LEG_SPEED_SLOPE.get().floatValue();
        float legMaxPenalty = MedicalConfig.PAIN_LEG_MAX_SPEED_PENALTY.get().floatValue();

        float armAttackSlope = MedicalConfig.PAIN_ARM_ATTACK_SLOPE.get().floatValue();
        float armMaxAttackPenalty = MedicalConfig.PAIN_ARM_MAX_ATTACK_PENALTY.get().floatValue();

        float armSpeedSlope = MedicalConfig.PAIN_ARM_SPEED_SLOPE.get().floatValue();
        float armMaxSpeedPenalty = MedicalConfig.PAIN_ARM_MAX_SPEED_PENALTY.get().floatValue();

        float systemicMaxSpeedPenalty = MedicalConfig.PAIN_SYSTEMIC_MAX_SPEED_PENALTY.get().floatValue();
        float systemicMaxActionPenalty = MedicalConfig.PAIN_SYSTEMIC_MAX_ACTION_PENALTY.get().floatValue();
        int sprintLockThreshold = MedicalConfig.PAIN_SPRINT_LOCK_THRESHOLD.get();

        // ★ 肾上腺素激增期间：交感神经应激暂时豁免高达 50%~85% 的痛觉移速/攻速惩罚 ★
        float adrLevel = ModItems.getAdrenalineLevel(data);
        float maxAdrRelief = MedicalConfig.ADRENALINE_MAX_PAIN_SLOW_RELIEF.get().floatValue();
        float adrPainReliefFactor = (adrLevel > 0.0f)
                ? (1.0f - Mth.clamp((adrLevel / 65.0f) * maxAdrRelief, 0.0f, maxAdrRelief))
                : 1.0f;

        float combinedLegMultiplier = 1.0f;
        int painfulLegCount = 0;

        // 1. 各部位疼痛 -> 非线性物理能力倍率（提交至原有 AttributeArbiter）
        for (MedicalData.Limb limb : MedicalData.Limb.values()) {
            MedicalData.LimbData limbData = data.getLimb(limb);
            if (limbData == null) continue;
            float p = limbData.getPainLevel();
            if (p <= 0.01f) continue;

            float normP = Mth.clamp(p / maxLimbPain, 0.0f, 1.0f);
            float nonLinearCurve = (float) Math.pow(normP, actionExponent);
            float splintMobilityRelief = (limbData.hasFracture() && (limbData.hasSplint() || ModItems.isLimbOrthopedicFixed(limbData)))
                    ? (1.0f - Mth.clamp(MedicalConfig.SPLINT_MOBILITY_PAIN_RELIEF_FACTOR.get().floatValue(), 0.0f, 0.9f))
                    : 1.0f;

            switch (limb) {
                case LEFT_LEG, RIGHT_LEG -> {
                    float linearLoss = legSlope * p;
                    float expLoss = legMaxPenalty * nonLinearCurve;
                    float totalLoss = Mth.clamp(Math.max(linearLoss, expLoss) * adrPainReliefFactor * splintMobilityRelief, 0.0f, legMaxPenalty);
                    float mul = Mth.clamp(1.0f - totalLoss, 0.05f, 1.0f);

                    AttributeArbiter.suggest(player, Dimension.MOVEMENT_SPEED,
                            "pain_" + limb.name().toLowerCase(), mul);

                    combinedLegMultiplier *= mul;
                    painfulLegCount++;
                }
                case LEFT_ARM, RIGHT_ARM -> {
                    String src = "pain_" + limb.name().toLowerCase();

                    float atkLoss = Mth.clamp(Math.max(armAttackSlope * p, armMaxAttackPenalty * nonLinearCurve) * adrPainReliefFactor * splintMobilityRelief, 0.0f, armMaxAttackPenalty);
                    float spdLoss = Mth.clamp(Math.max(armSpeedSlope * p, armMaxSpeedPenalty * nonLinearCurve) * adrPainReliefFactor * splintMobilityRelief, 0.0f, armMaxSpeedPenalty);

                    AttributeArbiter.suggest(player, Dimension.ATTACK_DAMAGE, src, Mth.clamp(1.0f - atkLoss, 0.05f, 1.0f));
                    AttributeArbiter.suggest(player, Dimension.ATTACK_SPEED, src, Mth.clamp(1.0f - spdLoss, 0.05f, 1.0f));
                }
                default -> {
                    // HEAD 与 CHEST 的剧痛除了视觉定向充血外，也会推高 getPerceivedPain() 触发下方全身行动力牵制
                }
            }
        }

        // 若双腿同时疼痛，提交双腿复合乘算倍率，避免 AttributeArbiter 仅取单腿最小值而忽略另一条伤腿
        if (painfulLegCount >= 2) {
            AttributeArbiter.suggest(player, Dimension.MOVEMENT_SPEED,
                    "pain_legs_combined", Mth.clamp(combinedLegMultiplier, 0.05f, 1.0f));
        }

        // 2. 总体体感疼痛 (getPerceivedPain) -> 全身性移动与动作压制 + 重度剧痛禁止疾跑
        int perceivedPain = data.getPerceivedPain();
        if (perceivedPain > 0) {
            float sysNorm = Mth.clamp(perceivedPain / maxPerceivedPain, 0.0f, 1.0f);
            float sysCurve = (float) Math.pow(sysNorm, actionExponent);

            boolean anySplinted = false;
            for (MedicalData.Limb l : MedicalData.Limb.values()) {
                MedicalData.LimbData ld = data.getLimb(l);
                if (ld != null && ld.hasFracture() && (ld.hasSplint() || ModItems.isLimbOrthopedicFixed(ld))) {
                    anySplinted = true;
                    break;
                }
            }
            float sysSplintRelief = anySplinted
                    ? (1.0f - Mth.clamp(MedicalConfig.SPLINT_MOBILITY_PAIN_RELIEF_FACTOR.get().floatValue() * 0.5f, 0.0f, 0.6f))
                    : 1.0f;
            float sysSpeedMul = Mth.clamp(1.0f - systemicMaxSpeedPenalty * sysCurve * adrPainReliefFactor * sysSplintRelief, 0.05f, 1.0f);
            float sysActionMul = Mth.clamp(1.0f - systemicMaxActionPenalty * sysCurve * adrPainReliefFactor * sysSplintRelief, 0.05f, 1.0f);

            AttributeArbiter.suggest(player, Dimension.MOVEMENT_SPEED, "pain_systemic", sysSpeedMul);
            AttributeArbiter.suggest(player, Dimension.ATTACK_DAMAGE, "pain_systemic", sysActionMul);
            AttributeArbiter.suggest(player, Dimension.ATTACK_SPEED, "pain_systemic", sysActionMul);

            if (perceivedPain >= sprintLockThreshold && adrLevel < MedicalConfig.ADRENALINE_TIER1_THRESHOLD.get() && player.isSprinting()) {
                player.setSprinting(false);
            }
        }
    }
}