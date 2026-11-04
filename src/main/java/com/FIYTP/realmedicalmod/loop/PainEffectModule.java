package com.FIYTP.realmedicalmod.loop;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.config.MedicalConfig;
import com.FIYTP.realmedicalmod.loop.AttributeArbiter.Dimension;
import net.minecraft.server.level.ServerPlayer;

/**
 * 疼痛影响评估模块（3A 视觉去粗糙化升级版）
 *
 * 核心改动说明：
 * 原版 Minecraft 的 BLINDNESS（5格纯黑浓雾墙）与 CONFUSION（反胃地狱门式大幅度绿幕旋转扭曲）
 * 观感极其粗糙且破坏射击/操作体验。
 * 现已将疼痛的视觉压迫（动脉重搏红晕、部位感知充血、RGB 色差分离、毛细血管神经丝、3D 镜头微颤与受击偏转）
 * 全部交由客户端 3A 级渲染层 (PainVisualOverlay + PainShockOverlay) 处理；
 * 本服务端模块专注负责部位疼痛对移动速度、攻击伤害、攻击速度与重度手臂剧痛挖掘效率的连续物理属性衰减。
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

        // 各部位疼痛 -> 连续物理能力倍率（不再乱塞原版失明/反胃药水破坏画面）
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
                default -> {
                    // HEAD 与 CHEST 的疼痛视觉表现由客户端 PainVisualOverlay 定向充血与色散接管
                }
            }
        }
    }
}