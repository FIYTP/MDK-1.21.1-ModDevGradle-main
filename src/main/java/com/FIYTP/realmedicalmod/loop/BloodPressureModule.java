package com.FIYTP.realmedicalmod.loop;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.config.MedicalConfig;

public class BloodPressureModule implements UpdateModule {

    @Override
    public void process(MedicalData data) {
        int bloodVolume = data.getBloodVolume();
        int maxBloodVolume = data.getMaxBloodVolume();
        int heartRate = data.getHeartRate();

        // 1. 每搏输出量 (mL)
        float bloodRatio = (float) bloodVolume / (float) maxBloodVolume;
        float strokeVolume = 70.0f * bloodRatio;

        // 毒素心肌抑制：中毒≥40 时每搏输出量线性下降（毒素100 → ×0.76）
        float toxin = data.getToxinValue();
        float toxinMidThreshold = MedicalConfig.TOXIN_MID_THRESHOLD.get().floatValue(); // 40
        if (toxin >= toxinMidThreshold) {
            float slope = MedicalConfig.TOXIN_STROKE_VOLUME_SLOPE.get().floatValue(); // 0.004
            strokeVolume *= Math.max(0.05f, 1.0f - slope * (toxin - toxinMidThreshold));
        }

        // 2. 心输出量 (L/min)
        float cardiacOutput = (heartRate * strokeVolume) / 1000.0f;
        data.setCardiacOutput(cardiacOutput);

        // 外周阻力 (TPR)
        float tprNormal = MedicalConfig.TPR_NORMAL.get().floatValue();       // 1.0
        float tprComp = MedicalConfig.TPR_COMPENSATED.get().floatValue();    // 1.1
        float tprDecomp = MedicalConfig.TPR_DECOMPENSATED.get().floatValue();// 0.4

        float tpr;
        if (bloodRatio > 0.7f) {
            // 正常期：TPR 保持正常
            tpr = tprNormal;
        } else if (bloodRatio > 0.5f) {
            // 代偿期：TPR 从正常缓慢升到峰值（血管收缩，维持血压）
            float progress = (0.7f - bloodRatio) / 0.2f;  // 0~1
            tpr = tprNormal + progress * (tprComp - tprNormal);  // 1.0 → 1.1
        } else if (bloodRatio > 0.3f) {
            // 失代偿前期：TPR 从峰值回落到正常
            float progress = (0.5f - bloodRatio) / 0.2f;  // 0~1
            tpr = tprComp + progress * (tprNormal - tprComp);  // 1.1 → 1.0
        } else {
            // 失代偿期：TPR 快速下降（血管麻痹）
            float progress = Math.min(1.0f, (0.3f - bloodRatio) / 0.3f);  // 0~1
            tpr = tprNormal + progress * (tprDecomp - tprNormal);  // 1.0 → 0.4
        }

        // 联动修饰
        tpr *= getSirsTprModifier(data.getSirsLevel());
        tpr *= getPainTprModifier(data.getTotalPainLevel());
        tpr *= getOpioidTprModifier(data.getOpioidConcentration());
        tpr *= getToxinTprModifier(data.getToxinValue());

        // 血胸修正
        int hemoLevel = data.getHemothoraxLevel();
        float hemoMod = switch (hemoLevel) {
            case 1 -> MedicalConfig.LEVEL1_BLOOD_PRESSURE_MODIFIER.get().floatValue();
            case 2 -> MedicalConfig.LEVEL2_BLOOD_PRESSURE_MODIFIER.get().floatValue();
            case 3 -> MedicalConfig.LEVEL3_BLOOD_PRESSURE_MODIFIER.get().floatValue();
            case 4 -> MedicalConfig.LEVEL4_BLOOD_PRESSURE_MODIFIER.get().floatValue();
            default -> 1.0f;
        };
        tpr *= hemoMod;

        data.setPeripheralResistance(tpr);

        // 3. 目标 MAP
        float normalMAP = MedicalConfig.NORMAL_MAP.get().floatValue();
        float normalCO = MedicalConfig.NORMAL_CO.get().floatValue();
        float mapCoefficient = (normalCO > 0) ? (normalMAP / normalCO) : 17.71f;
        float targetMAP = cardiacOutput * tpr * mapCoefficient;

        // 4. 平滑逼近
        float currentMAP = data.getMeanArterialPressure();
        currentMAP += (targetMAP - currentMAP) * MedicalConfig.MAP_APPROACH_SPEED.get().floatValue();

        // 5. 脉压
        float pulsePressure = 40.0f * (cardiacOutput / (normalCO <= 0 ? 5.25f : normalCO));

        // 6. 反推收缩压和舒张压
        int systolicBP = Math.round(currentMAP + (2.0f / 3.0f) * pulsePressure);
        int diastolicBP = Math.round(currentMAP - (1.0f / 3.0f) * pulsePressure);

        // 7. 限制范围
        currentMAP = Math.max(20.0f, Math.min(180.0f, currentMAP));
        systolicBP = Math.max(40, Math.min(250, systolicBP));
        diastolicBP = Math.max(20, Math.min(180, diastolicBP));

        data.setMeanArterialPressure(currentMAP);
        data.setSystolicBP(systolicBP);
        data.setDiastolicBP(diastolicBP);
    }

    /**
     * SIRS / 脓毒性休克对 TPR 的影响
     * 血管扩张导致外周阻力下降
     */
    private float getSirsTprModifier(int sirsLevel) {
        return switch (sirsLevel) {
            case 1 -> MedicalConfig.SIRS_TPR_MODIFIER_L1.get().floatValue();
            case 2 -> MedicalConfig.SIRS_TPR_MODIFIER_L2.get().floatValue();
            case 3 -> MedicalConfig.SIRS_TPR_MODIFIER_L3.get().floatValue();
            case 4 -> MedicalConfig.SIRS_TPR_MODIFIER_L4.get().floatValue();
            default -> 1.0f;
        };
    }

    /** 毒素血管扩张：中毒≥40 时 TPR 线性下降（分布性休克，毒素100 → ×0.76） */
    private float getToxinTprModifier(float toxin) {
        float threshold = MedicalConfig.TOXIN_MID_THRESHOLD.get().floatValue(); // 40
        if (toxin < threshold) return 1.0f;
        float slope = MedicalConfig.TOXIN_TPR_SLOPE.get().floatValue(); // 0.004
        return Math.max(0.05f, 1.0f - slope * (toxin - threshold));
    }

    private float getPainTprModifier(int totalPain) {
        return 1.0f;
    }

    private float getOpioidTprModifier(int opioidConcentration) {
        return 1.0f;
    }
}
