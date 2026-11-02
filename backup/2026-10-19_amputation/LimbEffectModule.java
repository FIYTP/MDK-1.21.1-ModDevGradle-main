package com.FIYTP.realmedicalmod.loop;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.config.MedicalConfig;
import com.FIYTP.realmedicalmod.loop.AttributeArbiter.Dimension;
import net.minecraft.server.level.ServerPlayer;

/**
 * 肢体伤情影响评估模块（影响评估层）
 *
 * 职责：把腿/臂骨折换算成各能力维度的连续倍率，提交给 AttributeArbiter。
 * 不再直接打属性 modifier（消灭 FRACTURE_ATTACK_MODIFIER_ID 旁路）；
 * 血胸/肺挫伤等器官伤效果统一迁至 OrganEffectModule。
 */
public class LimbEffectModule implements UpdateModule {

    @Override
    public void process(MedicalData data) {
        // 无 player 时的兜底
    }

    public void process(ServerPlayer player, MedicalData data) {
        if (player == null || data == null) return;

        // 1. 腿部骨折（取双腿中最严重的倍率；双腿骨折时不再被 max 合并抹平）
        MedicalData.LimbData leftLeg = data.getLimb(MedicalData.Limb.LEFT_LEG);
        MedicalData.LimbData rightLeg = data.getLimb(MedicalData.Limb.RIGHT_LEG);

        if (leftLeg != null && leftLeg.hasFracture()) {
            float m = leftLeg.hasSplint()
                    ? MedicalConfig.LEG_FRACTURE_SPLINT_SPEED_MULTIPLIER.get().floatValue()
                    : MedicalConfig.LEG_FRACTURE_NO_SPLINT_SPEED_MULTIPLIER.get().floatValue();
            AttributeArbiter.suggest(player, Dimension.MOVEMENT_SPEED, "leg_fracture_left", m);
        }
        if (rightLeg != null && rightLeg.hasFracture()) {
            float m = rightLeg.hasSplint()
                    ? MedicalConfig.LEG_FRACTURE_SPLINT_SPEED_MULTIPLIER.get().floatValue()
                    : MedicalConfig.LEG_FRACTURE_NO_SPLINT_SPEED_MULTIPLIER.get().floatValue();
            AttributeArbiter.suggest(player, Dimension.MOVEMENT_SPEED, "leg_fracture_right", m);
        }

        // 2. 手臂骨折（每臂独立提交攻击力/攻速/挖掘维度；挖掘维度经 BuffManager 的 DIG_SLOWDOWN 插值呈现）
        applyArmFracture(player, data.getLimb(MedicalData.Limb.LEFT_ARM), "arm_fracture_left");
        applyArmFracture(player, data.getLimb(MedicalData.Limb.RIGHT_ARM), "arm_fracture_right");
    }

    /** 单臂骨折 → 攻击力/攻速/挖掘连续倍率 */
    private void applyArmFracture(ServerPlayer player, MedicalData.LimbData arm, String sourceId) {
        if (arm == null || !arm.hasFracture()) return;

        boolean splinted = arm.hasSplint();
        float attackMul = splinted
                ? MedicalConfig.ARM_FRACTURE_SPLINT_ATTACK_MULTIPLIER.get().floatValue()
                : MedicalConfig.ARM_FRACTURE_NO_SPLINT_ATTACK_MULTIPLIER.get().floatValue();
        float miningMul = splinted
                ? MedicalConfig.ARM_FRACTURE_SPLINT_MINING_MULTIPLIER.get().floatValue()
                : MedicalConfig.ARM_FRACTURE_NO_SPLINT_MINING_MULTIPLIER.get().floatValue();

        // 攻速按攻力与挖掘的中间值插值（无独立配置，保持现实保守估计）
        float speedMul = (attackMul + miningMul) * 0.5f;

        AttributeArbiter.suggest(player, Dimension.ATTACK_DAMAGE, sourceId, attackMul);
        AttributeArbiter.suggest(player, Dimension.ATTACK_SPEED, sourceId, speedMul);

        // 挖掘维度无属性可用，折算成 DIG_SLOWDOWN 药水等级经感知层呈现：
        // 挖掘倍率 1.0 → 等级 0；倍率越低等级越高，按配置最大等级插值
        double maxLevel = MedicalConfig.DIG_SLOWDOWN_FRACTURE_MAX_LEVEL.get();
        int digLevel = (int) Math.round((1.0 - miningMul) * maxLevel);
        if (digLevel > 0) {
            com.FIYTP.realmedicalmod.loop.BuffManager.INSTANCE.suggest(player, "dig_slowdown", digLevel);
        }
    }
}
