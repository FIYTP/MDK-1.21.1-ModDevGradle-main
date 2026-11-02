package com.FIYTP.realmedicalmod.loop;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.config.MedicalConfig;
import com.FIYTP.realmedicalmod.loop.AttributeArbiter.Dimension;
import net.minecraft.server.level.ServerPlayer;

/**
 * 器官损伤影响评估模块（影响评估层）
 *
 * 统一收编所有"器官类"伤势的表现层效果（此前散落三处，用户要求归拢）：
 * 1. 血胸（HemothoraxModule 管生理层等级 0~4；此处管表现层移速压制）
 * 2. 肺挫伤（getLungContusion 0~100% → 呼吸困难全身乏力渐进）
 * 3. 颅内出血（getIntracranialLevel 0~4 → 感知层失明插值，
 *    从 ConsciousnessSuggestionModule 迁入）
 */
public class OrganEffectModule implements UpdateModule {

    @Override
    public void process(MedicalData data) {
        // 无 player 时的兜底
    }

    public void process(ServerPlayer player, MedicalData data, BuffManager buffManager) {
        if (player == null || data == null || buffManager == null) return;

        applyHemothorax(player, data);
        applyLungContusion(player, data);
        applyIntracranial(player, data, buffManager);
    }

    /** 血胸等级：L3 呼吸急促 ×0.5，L4 濒死 ×0.05（沿用既有数值，迁自 LimbEffectModule） */
    private void applyHemothorax(ServerPlayer player, MedicalData data) {
        int level = data.getHemothoraxLevel();
        if (level >= 4) {
            AttributeArbiter.suggest(player, Dimension.MOVEMENT_SPEED, "hemothorax",
                    MedicalConfig.HEMOTHORAX_L4_SPEED_MULTIPLIER.get().floatValue());
        } else if (level == 3) {
            AttributeArbiter.suggest(player, Dimension.MOVEMENT_SPEED, "hemothorax",
                    MedicalConfig.HEMOTHORAX_L3_SPEED_MULTIPLIER.get().floatValue());
        }
    }

    /** 肺挫伤：0~100% → 移速/攻速连续疲劳（呼吸做功耗竭感） */
    private void applyLungContusion(ServerPlayer player, MedicalData data) {
        float contusion = data.getLungContusion();
        if (contusion <= 0.01f) return;

        float slope = MedicalConfig.CONTUSION_FATIGUE_SLOPE.get().floatValue();
        float mul = 1.0f - slope * contusion / 100.0f;

        AttributeArbiter.suggest(player, Dimension.MOVEMENT_SPEED, "lung_contusion", mul);
        AttributeArbiter.suggest(player, Dimension.ATTACK_SPEED, "lung_contusion", mul);
    }

    /** 颅内出血：L3/L4 → 失明药水（压迫视神经/枕叶），迁自 ConsciousnessSuggestionModule */
    private void applyIntracranial(ServerPlayer player, MedicalData data, BuffManager buffManager) {
        int icLevel = data.getIntracranialLevel();
        if (icLevel >= 4) {
            buffManager.suggest(player, "blindness",
                    MedicalConfig.IC_BLINDNESS_L4.get().intValue());
        } else if (icLevel == 3) {
            buffManager.suggest(player, "blindness",
                    MedicalConfig.IC_BLINDNESS_L3.get().intValue());
        }
    }
}
