package com.FIYTP.realmedicalmod.loop;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.config.MedicalConfig;
import com.FIYTP.realmedicalmod.loop.AttributeArbiter.Dimension;
import net.minecraft.server.level.ServerPlayer;

/**
 * 全身状态影响评估模块（影响评估层）
 *
 * 统一收编所有"全身性"生理状态 → 能力维度的连续通路：
 * 1. 失血（血容量不足 → 全身乏力）
 * 2. 低血氧（SpO2 → 全身乏力渐进）
 * 3. SIRS 感染分级（0~4 级 → 脓毒症渐进虚弱；用户点名纳入）
 * 4. 意识压制（c<100 → 攻击/攻速衰减；c=0 昏迷压到 config 倍率，
 *    替代旧 ConsciousnessSuggestionModule 的 weakness/dig 255 硬压制）
 */
public class SystemicEffectModule implements UpdateModule {

    @Override
    public void process(MedicalData data) {
        // 无 player 时的兜底
    }

    public void process(ServerPlayer player, MedicalData data) {
        if (player == null || data == null) return;

        applyBloodLoss(player, data);
        applyHypoxia(player, data);
        applySepsis(player, data);
        applyConsciousness(player, data);
    }

    /** 失血百分比 b → 移速/攻击 1-sb*b、攻速 1-sd*b（线性，b=100 时压到底） */
    private void applyBloodLoss(ServerPlayer player, MedicalData data) {
        int maxVolume = data.getMaxBloodVolume();
        if (maxVolume <= 0) return;
        float bloodLoss = (maxVolume - data.getBloodVolume()) * 100.0f / maxVolume;
        if (bloodLoss <= 0.01f) return;

        float speedSlope = MedicalConfig.BLOOD_LOSS_SPEED_SLOPE.get().floatValue();
        float attackSlope = MedicalConfig.BLOOD_LOSS_ATTACK_SLOPE.get().floatValue();
        float digSlope = MedicalConfig.BLOOD_LOSS_DIG_SLOPE.get().floatValue();

        float speedMul = 1.0f - speedSlope * bloodLoss / 100.0f;
        float attackMul = 1.0f - attackSlope * bloodLoss / 100.0f;
        float digMul = 1.0f - digSlope * bloodLoss / 100.0f;

        AttributeArbiter.suggest(player, Dimension.MOVEMENT_SPEED, "blood_loss", speedMul);
        AttributeArbiter.suggest(player, Dimension.ATTACK_DAMAGE, "blood_loss", attackMul);
        AttributeArbiter.suggest(player, Dimension.ATTACK_SPEED, "blood_loss", attackMul);

        // 挖掘维度折算 DIG_SLOWDOWN（无挖掘属性）
        double maxLevel = MedicalConfig.DIG_SLOWDOWN_FRACTURE_MAX_LEVEL.get();
        int digLevel = (int) Math.round((1.0 - digMul) * maxLevel);
        if (digLevel > 0) {
            BuffManager.INSTANCE.suggest(player, "dig_slowdown", digLevel);
        }
    }

    /** SpO2 低于阈值 → 乏力渐进（SpO2 阈值处 0 惩罚 → SpO2=0 处 maxPenalty） */
    private void applyHypoxia(ServerPlayer player, MedicalData data) {
        float spo2 = data.getBloodOxygen();
        int onset = MedicalConfig.HYPOXIA_ONSET_SPO2.get();
        if (spo2 >= onset) return;

        float maxPenalty = MedicalConfig.HYPOXIA_MAX_PENALTY.get().floatValue();
        // (onset - spo2) / onset：阈值处 0，SpO2=0 处 1
        float penalty = maxPenalty * (onset - spo2) / (float) onset;
        float mul = 1.0f - penalty;

        AttributeArbiter.suggest(player, Dimension.MOVEMENT_SPEED, "hypoxia", mul);
        AttributeArbiter.suggest(player, Dimension.ATTACK_DAMAGE, "hypoxia", mul);
        AttributeArbiter.suggest(player, Dimension.ATTACK_SPEED, "hypoxia", mul);

        double maxLevel = MedicalConfig.DIG_SLOWDOWN_FRACTURE_MAX_LEVEL.get();
        int digLevel = (int) Math.round((1.0 - mul) * maxLevel);
        if (digLevel > 0) {
            BuffManager.INSTANCE.suggest(player, "dig_slowdown", digLevel);
        }
    }

    /** SIRS 等级 0~4 → 全身疲劳渐进（L4 脓毒症另受下限保护） */
    private void applySepsis(ServerPlayer player, MedicalData data) {
        int sirs = data.getSirsLevel();
        if (sirs <= 0) return;

        float perLevel = MedicalConfig.SIRS_FATIGUE_PER_LEVEL.get().floatValue();
        float mul = 1.0f - perLevel * sirs;

        if (sirs >= 4) {
            // 脓毒症：全身机能下限保护
            float floor = MedicalConfig.SEPSIS_MIN_MULTIPLIER.get().floatValue();
            mul = Math.max(mul, floor);
        }

        AttributeArbiter.suggest(player, Dimension.MOVEMENT_SPEED, "sirs", mul);
        AttributeArbiter.suggest(player, Dimension.ATTACK_DAMAGE, "sirs", mul);
        AttributeArbiter.suggest(player, Dimension.ATTACK_SPEED, "sirs", mul);
    }

    /** 意识 c → 攻击/攻速/挖掘压制；c=0 昏迷压制（替代旧 weakness 255） */
    private void applyConsciousness(ServerPlayer player, MedicalData data) {
        float c = data.getConsciousness();
        float factor = MedicalConfig.CONSC_IMPACT_FACTOR.get().floatValue();
        float unconsciousMul = MedicalConfig.CONSC_IMPACT_UNCONSCIOUS.get().floatValue();

        float mul;
        if (c <= 0.01f) {
            mul = unconsciousMul;
        } else {
            mul = 1.0f - factor * (1.0f - c / 100.0f);
            if (c < 5.0f) {
                // 接近昏迷时平滑过渡到昏迷压制倍率，避免 5→0 跳变
                float t = (5.0f - c) / 5.0f;
                mul = mul * (1.0f - t) + unconsciousMul * t;
            }
        }

        if (mul >= 0.999f) return;

        AttributeArbiter.suggest(player, Dimension.ATTACK_DAMAGE, "consciousness", mul);
        AttributeArbiter.suggest(player, Dimension.ATTACK_SPEED, "consciousness", mul);

        double maxLevel = MedicalConfig.DIG_SLOWDOWN_FRACTURE_MAX_LEVEL.get();
        int digLevel = (int) Math.round((1.0 - mul) * maxLevel);
        if (digLevel > 0) {
            BuffManager.INSTANCE.suggest(player, "dig_slowdown", digLevel);
        }
    }
}
