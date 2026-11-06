package com.FIYTP.realmedicalmod.loop;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.config.MedicalConfig;

/**
 * 意识清醒度演进模块（符合现实生理学的痛觉意识保底升级版）
 *
 * 核心生理修正：
 * 单纯肢体骨折或创伤疼痛（无严重低血氧、无失血休克低血压、无颅内出血或严重血胸时），
 * 只会让意识下降至「痛觉意识下限 (MedicalConfig.PAIN_CONSCIOUSNESS_FLOOR，默认 25%)」并限制恢复，
 * 而不会每秒无限把意识扣到 0 引发永久昏迷；
 * 唯有真正危及中枢灌注与供氧的病理状态（低血氧、低血压 MAP<70、血胸、颅内出血、重度神经毒素）才能将意识压至 0（昏迷）。
 */
public class ConsciousnessModule implements UpdateModule {

    @Override
    public void process(MedicalData data) {
        // 心脏骤停时，意识直接归零且不再恢复
        if (data.isCardiacArrest()) {
            data.setConsciousnessFloat(0.0f);
            return;
        }

        float currentConsciousness = data.getConsciousnessFloat();
        int perceivedPain = data.getPerceivedPain();
        float oxygen = data.getBloodOxygenFloat();

        float oxygenPenalty;
        if (oxygen >= 90.0f) {
            oxygenPenalty = MedicalConfig.OXYGEN_PENALTY_90.get().floatValue();
        } else if (oxygen >= 80.0f) {
            oxygenPenalty = MedicalConfig.OXYGEN_PENALTY_80.get().floatValue();
        } else if (oxygen >= 70.0f) {
            oxygenPenalty = MedicalConfig.OXYGEN_PENALTY_70.get().floatValue();
        } else if (oxygen >= 50.0f) {
            oxygenPenalty = MedicalConfig.OXYGEN_PENALTY_50.get().floatValue();
        } else if (oxygen >= 30.0f) {
            oxygenPenalty = MedicalConfig.OXYGEN_PENALTY_30.get().floatValue();
        } else {
            oxygenPenalty = MedicalConfig.OXYGEN_PENALTY_CRITICAL.get().floatValue();
        }

        // ★ 体感疼痛惩罚（受 PAIN_CONSCIOUSNESS_FLOOR 下限约束） ★
        // 当意识已降至痛觉保底线（默认 25）以下时，单纯疼痛不再继续往下扣减意识，仅阻止意识自然回升；
        // 只有缺氧、低血压休克、颅内出血、血胸或毒素才能将意识继续扣至 0。
        float rawPainPenalty = (float) (perceivedPain / MedicalConfig.PAIN_PENALTY_DIVISOR.get());
        float painFloor = MedicalConfig.PAIN_CONSCIOUSNESS_FLOOR.get().floatValue();
        float baseRecovery = MedicalConfig.CONSCIOUSNESS_BASE_RECOVERY.get().floatValue();
        float painPenalty = (currentConsciousness <= painFloor)
                ? Math.min(rawPainPenalty, baseRecovery)
                : rawPainPenalty;

        // ★ 毒素直接意识惩罚 (神经毒性) ★
        float toxinPenalty = 0.0f;
        float toxin = data.getToxinValue();
        float toxinMidThreshold = MedicalConfig.TOXIN_MID_THRESHOLD.get().floatValue();
        if (toxin >= toxinMidThreshold) {
            float slope = MedicalConfig.TOXIN_CONSCIOUSNESS_SLOPE.get().floatValue();
            toxinPenalty = slope * (toxin - toxinMidThreshold);
        }

        // 血胸对意识的抑制
        int hemoLevel = data.getHemothoraxLevel();
        float hemothoraxPenalty = switch (hemoLevel) {
            case 1 -> MedicalConfig.LEVEL1_CONSCIOUSNESS_PENALTY.get().floatValue();
            case 2 -> MedicalConfig.LEVEL2_CONSCIOUSNESS_PENALTY.get().floatValue();
            case 3 -> MedicalConfig.LEVEL3_CONSCIOUSNESS_PENALTY.get().floatValue();
            case 4 -> MedicalConfig.LEVEL4_CONSCIOUSNESS_PENALTY.get().floatValue();
            default -> 0.0f;
        };

        // 颅内出血对意识的抑制
        float intracranialPenalty = 0.0f;
        if (data.getIntracranialBleedRate() > 0 || data.getIntracranialAccumulation() > 0) {
            int onsetTimer = data.getIntracranialOnsetTimer();
            if (onsetTimer < MedicalConfig.ONSET_DELAY.get()) {
                intracranialPenalty = 0.5f;
            } else {
                int icLevel = data.getIntracranialLevel();
                if (icLevel == 1) {
                    intracranialPenalty = 1.0f;
                } else if (icLevel == 2) {
                    intracranialPenalty = 2.0f;
                } else if (icLevel == 3) {
                    intracranialPenalty = 3.5f;
                } else if (icLevel >= 4) {
                    intracranialPenalty = 5.0f;
                }
            }
        }

        // 血压对意识的影响
        float bpPenalty = 0.0f;
        float map = data.getMeanArterialPressure();
        int sbp = data.getSystolicBP();

        if (map < 70) {
            if (map >= 60) bpPenalty += MedicalConfig.CONS_PENALTY_MAP_60.get().floatValue();
            else if (map >= 50) bpPenalty += MedicalConfig.CONS_PENALTY_MAP_50.get().floatValue();
            else if (map >= 40) bpPenalty += MedicalConfig.CONS_PENALTY_MAP_40.get().floatValue();
            else bpPenalty += MedicalConfig.CONS_PENALTY_MAP_BELOW_40.get().floatValue();
        }

        if (sbp >= 180) {
            if (sbp < 200) bpPenalty += MedicalConfig.CONS_PENALTY_SBP_180.get().floatValue();
            else if (sbp < 220) bpPenalty += MedicalConfig.CONS_PENALTY_SBP_200.get().floatValue();
            else bpPenalty += MedicalConfig.CONS_PENALTY_SBP_220.get().floatValue();
        }

        float change = baseRecovery - painPenalty - oxygenPenalty - hemothoraxPenalty - intracranialPenalty - bpPenalty - toxinPenalty;

        // 若当前高于痛觉保底线，而扣减完全是由疼痛引起（无缺氧/低血压/颅内出血/血胸/中毒），则最低扣至 painFloor
        float nonPainPenalties = oxygenPenalty + hemothoraxPenalty + intracranialPenalty + bpPenalty + toxinPenalty;
        float nextConsciousness = currentConsciousness + change;
        if (nonPainPenalties <= 0.001f && currentConsciousness >= painFloor && nextConsciousness < painFloor) {
            nextConsciousness = painFloor;
        }

        data.setConsciousnessFloat(Math.max(0.0f, Math.min(100.0f, nextConsciousness)));
    }
}