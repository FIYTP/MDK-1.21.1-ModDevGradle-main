package com.FIYTP.realmedicalmod.loop;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.config.MedicalConfig;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

/**
 * 中毒系统核心模块 (ToxinModule)
 * 毒素条 0~100，由 poison 伤害事件累加（HitPartHandler → TOXIN 分支）。
 * 每秒循环职责：
 * 1. 代谢：自然衰减（饱食度≥18 时 ×2，挂免疫系统同款加成）
 * 2. 轻度 (≥20)：反胃药水插值建议
 * 3. 重度 (≥70)：感染值抬升（走现有感染→SIRS 链）、毒素性室颤小概率触发
 * 其余症状（呼吸/血氧/意识/血压/凝血）由各系统模块读取 toxinValue 自行施加，
 * 与本模块解耦（详见 RespirationModule/BloodOxygenModule/ConsciousnessModule/
 * BloodPressureModule/NaturalClottingModule 的 toxin 惩罚项）。
 */
public class ToxinModule implements UpdateModule {

    private final RandomSource random = RandomSource.create();

    @Override
    public void process(MedicalData data) {
        process(null, data);
    }

    public void process(Player player, MedicalData data) {
        if (data == null) return;
        float toxin = data.getToxinValue();
        if (toxin <= 0.0f) return;

        // ===== 1. 代谢（自然衰减）=====
        float decay = MedicalConfig.TOXIN_DECAY_RATE.get().floatValue(); // 默认 0.25/秒 = 15/分钟
        if (player != null && player.getFoodData().getFoodLevel() >= 18) {
            decay *= MedicalConfig.TOXIN_IMMUNE_DECAY_MULT.get().floatValue(); // 免疫加成 ×2
        }
        float newToxin = toxin - decay;
        data.setToxinValue(newToxin); // setter 自带 0~100 钳制
        toxin = data.getToxinValue(); // 读取钳制后的值供后续症状使用

        // ===== 2. 轻度：反胃药水插值（挂到 BuffManager，由影响评估层统一应用）=====
        // 交由 ToxinEffectModule 在评估阶段建议，此处只维护数值
    }

    /** 重度症状（供 ToxinEffectModule 调用）：感染抬升 + 室颤概率 */
    public void processSevere(Player player, MedicalData data) {
        float toxin = data.getToxinValue();

        // 感染值抬升：中毒组织免疫崩溃 → 走现有感染→SIRS 自动晋级链
        if (toxin >= MedicalConfig.TOXIN_SEVERE_THRESHOLD.get().floatValue()) {
            float infectionRate = MedicalConfig.TOXIN_INFECTION_RATE.get().floatValue();
            // 抬升全身肢体感染均值：分摊到所有肢体（毒素是全身性的）
            for (MedicalData.Limb limb : MedicalData.Limb.values()) {
                MedicalData.LimbData ld = data.getLimb(limb);
                if (ld == null) continue;
                float accum = ld.getInfectionAccumulator() + infectionRate;
                int increase = (int) accum;
                if (increase > 0) {
                    ld.setInfection(Math.min(100, ld.getInfection() + increase));
                    ld.setInfectionAccumulator(accum - increase);
                } else {
                    ld.setInfectionAccumulator(accum);
                }
            }

            // 毒素性室颤：心肌应激小概率触发（走现有 setVFib 路径）
            if (!data.isCardiacArrest() && !data.isInVFib()) {
                float chance = MedicalConfig.TOXIN_VFIB_CHANCE.get().floatValue(); // 默认 0.005/秒
                if (toxin >= MedicalConfig.TOXIN_CRITICAL_THRESHOLD.get().floatValue()) {
                    chance *= 2.0f; // 濒死浓度加倍
                }
                if (random.nextFloat() < chance) {
                    data.setVFib(true);
                }
            }
        }
    }
}
