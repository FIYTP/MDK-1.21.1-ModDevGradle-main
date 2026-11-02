package com.FIYTP.realmedicalmod.loop;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.config.MedicalConfig;

public class ConsciousnessModule implements UpdateModule {

    @Override
    public void process(MedicalData data) {
        // 心脏骤停时，意识不再恢复（保持归零）
        if (data.isCardiacArrest()) {
            return;
        }

        // ★ 使用体感疼痛 ★
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

        // ★ 体感疼痛惩罚 ★
        float painPenalty = (float) (perceivedPain / MedicalConfig.PAIN_PENALTY_DIVISOR.get());

        // ★ 毒素直接意识惩罚（神经毒性）★
        // 中毒≥40 起线性扣意识；血氧下降的传导惩罚由低血氧链条另行施加，此处只算直接项
        float toxinPenalty = 0.0f;
        float toxin = data.getToxinValue();
        float toxinMidThreshold = MedicalConfig.TOXIN_MID_THRESHOLD.get().floatValue(); // 40
        if (toxin >= toxinMidThreshold) {
            float slope = MedicalConfig.TOXIN_CONSCIOUSNESS_SLOPE.get().floatValue(); // 0.05/s每点
            toxinPenalty = slope * (toxin - toxinMidThreshold); // 毒素100 → 3.0/s
        }

        // 呼吸异常对意识的影响完全通过 BloodOxygenModule → 低血氧惩罚链条传导，此处不再施加双重呼吸惩罚

        // 血胸对意识的抑制
        int hemoLevel = data.getHemothoraxLevel();
        float hemothoraxPenalty = switch (hemoLevel) {
            case 1 -> MedicalConfig.LEVEL1_CONSCIOUSNESS_PENALTY.get().floatValue();
            case 2 -> MedicalConfig.LEVEL2_CONSCIOUSNESS_PENALTY.get().floatValue();
            case 3 -> MedicalConfig.LEVEL3_CONSCIOUSNESS_PENALTY.get().floatValue();
            case 4 -> MedicalConfig.LEVEL4_CONSCIOUSNESS_PENALTY.get().floatValue();
            default -> 0.0f;
        };

        // 颅内出血对意识的抑制（清醒期内轻微 -0.5/s；急性发作期按等级剧烈扣减）
        float intracranialPenalty = 0.0f;
        if (data.getIntracranialBleedRate() > 0 || data.getIntracranialAccumulation() > 0) {
            int onsetTimer = data.getIntracranialOnsetTimer();
            if (onsetTimer < MedicalConfig.ONSET_DELAY.get()) {
                intracranialPenalty = 0.5f; // 清醒期内轻微扣减
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

        // 低血压路径
        if (map < 70) {
            if (map >= 60) bpPenalty += MedicalConfig.CONS_PENALTY_MAP_60.get().floatValue();
            else if (map >= 50) bpPenalty += MedicalConfig.CONS_PENALTY_MAP_50.get().floatValue();
            else if (map >= 40) bpPenalty += MedicalConfig.CONS_PENALTY_MAP_40.get().floatValue();
            else bpPenalty += MedicalConfig.CONS_PENALTY_MAP_BELOW_40.get().floatValue();
        }

        // 高血压危象
        if (sbp >= 180) {
            if (sbp < 200) bpPenalty += MedicalConfig.CONS_PENALTY_SBP_180.get().floatValue();
            else if (sbp < 220) bpPenalty += MedicalConfig.CONS_PENALTY_SBP_200.get().floatValue();
            else bpPenalty += MedicalConfig.CONS_PENALTY_SBP_220.get().floatValue();
        }

        float change = MedicalConfig.CONSCIOUSNESS_BASE_RECOVERY.get().floatValue() - painPenalty - oxygenPenalty - hemothoraxPenalty - intracranialPenalty - bpPenalty - toxinPenalty;

        float currentConsciousness = data.getConsciousnessFloat();
        data.setConsciousnessFloat(Math.max(0.0f, Math.min(100.0f, currentConsciousness + change)));
    }
}