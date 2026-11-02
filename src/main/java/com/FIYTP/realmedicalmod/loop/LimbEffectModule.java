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

        // 3. 断肢（仅四肢）：截断的肢体比骨折更极端——直接采用断肢专用倍率
        if (MedicalConfig.AMPUTATION_ENABLED.get()) {
            // 3.1 腿部截肢 → 移速
            boolean leftAmp = leftLeg != null && leftLeg.isAmputated();
            boolean rightAmp = rightLeg != null && rightLeg.isAmputated();

            // 3.0 双腿截肢 → 强制卧倒姿态（否则只剩躯干悬浮在站立高度）
            // Pose.SLEEPING 使命中箱降为 0.2 高，本体贴地平躺；
            // 每 tick 重申以覆盖 Player#updatePlayerPose 的姿态重算。
            if (leftAmp && rightAmp) {
                if (MedicalConfig.AMPUTATION_DOUBLE_LEG_PRONE.get()
                        && player.getPose() != net.minecraft.world.entity.Pose.SLEEPING
                        && !player.isPassenger()) {
                    player.setPose(net.minecraft.world.entity.Pose.SLEEPING);
                }
            } else if ((player.getPose() == net.minecraft.world.entity.Pose.SLEEPING)
                    && (leftAmp || rightAmp)) {
                // 单腿截肢不卧倒（可单脚跳/爬行），确保不残留卧倒姿态
                player.setPose(net.minecraft.world.entity.Pose.STANDING);
            }

            if (leftAmp && rightAmp) {
                AttributeArbiter.suggest(player, Dimension.MOVEMENT_SPEED, "leg_amputated_both",
                        MedicalConfig.AMPUTATION_DOUBLE_LEG_SPEED_MULTIPLIER.get().floatValue());
            } else if (leftAmp) {
                AttributeArbiter.suggest(player, Dimension.MOVEMENT_SPEED, "leg_amputated_left",
                        MedicalConfig.AMPUTATION_LEG_SPEED_MULTIPLIER.get().floatValue());
            } else if (rightAmp) {
                AttributeArbiter.suggest(player, Dimension.MOVEMENT_SPEED, "leg_amputated_right",
                        MedicalConfig.AMPUTATION_LEG_SPEED_MULTIPLIER.get().floatValue());
            }

            // 3.2 手臂截肢 → 攻击/攻速/挖掘（另一条臂正常时本就为半力，截断侧直接归零倍率）
            applyArmAmputation(player, data.getLimb(MedicalData.Limb.LEFT_ARM), "arm_amputated_left");
            applyArmAmputation(player, data.getLimb(MedicalData.Limb.RIGHT_ARM), "arm_amputated_right");
        }
    }

    /** 单臂截肢 → 攻击力/攻速/挖掘归断肢倍率 */
    private void applyArmAmputation(ServerPlayer player, MedicalData.LimbData arm, String sourceId) {
        if (arm == null || !arm.isAmputated()) return;

        float attackMul = MedicalConfig.AMPUTATION_ARM_ATTACK_MULTIPLIER.get().floatValue();
        float miningMul = MedicalConfig.AMPUTATION_ARM_MINING_MULTIPLIER.get().floatValue();
        float speedMul = Math.max(attackMul, miningMul);

        AttributeArbiter.suggest(player, Dimension.ATTACK_DAMAGE, sourceId, attackMul);
        AttributeArbiter.suggest(player, Dimension.ATTACK_SPEED, sourceId, speedMul);

        double maxLevel = MedicalConfig.DIG_SLOWDOWN_FRACTURE_MAX_LEVEL.get();
        int digLevel = (int) Math.round((1.0 - miningMul) * maxLevel);
        if (digLevel > 0) {
            com.FIYTP.realmedicalmod.loop.BuffManager.INSTANCE.suggest(player, "dig_slowdown", digLevel);
        }
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
