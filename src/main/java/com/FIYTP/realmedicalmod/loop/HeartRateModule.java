package com.FIYTP.realmedicalmod.loop;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.config.MedicalConfig;

public class HeartRateModule implements UpdateModule {

    @Override
    public void process(MedicalData data) {
        if (data.getBloodVolume() <= 0) {
            data.setVFib(false);
            data.setCardiacArrest(true);
            data.setHeartRate(0);
            return;
        }
        if (data.isCardiacArrest()) {
            data.setHeartRate(0);
            return;
        }
        if (data.isInVFib()) {
            data.setHeartRate(-1);
            return;
        }

        int bloodVol = data.getBloodVolume();
        int maxBlood = data.getMaxBloodVolume();
        float ratio = (float) bloodVol / maxBlood;

        float bloodMod;
        if (ratio > 0.15f) {
            // 代偿期：血容量下降，心率上升（但幅度调低）
            // 从75%血容量开始上升，最高在15%时达到约70 bpm加成
            float normalRange = (0.75f - Math.min(ratio, 0.75f)) / 0.60f;
            bloodMod = 10 + normalRange * 60; // 10~70
            if (ratio > 0.75f) {
                bloodMod = 10;
            }
        } else {
            // 失代偿期：血容量极低，心率骤降
            float crash = (0.15f - ratio) / 0.15f;
            bloodMod = 70 - crash * 70; // 70~0
            if (ratio < 0.02f) {
                data.setCardiacArrest(true);
                data.setHeartRate(0);
                return;
            }
        }

        // 疼痛修正
        int totalPain = data.getTotalPainLevel();
        float painMod = (float) (totalPain * MedicalConfig.PAIN_HEART_RATE_MULTIPLIER.get());

        // 意识修正
        int consciousness = data.getConsciousness();
        float consciousnessMod;
        if (consciousness >= 80) {
            consciousnessMod = 0;
        } else if (consciousness >= 50) {
            consciousnessMod = -(80 - consciousness) * 0.5f;
        } else if (consciousness > 0) {
            consciousnessMod = -(80 - consciousness) * 0.8f;
        } else {
            consciousnessMod = -70;
        }

        // 运动贡献（由主循环更新）
        float exerciseMod = data.getExerciseContribution();

        // 阿片类药物影响
        int opioid = data.getOpioidConcentration();
        float opioidMod = -opioid * 0.5f;

        // ★ 呼吸频率对心率的影响 ★
        int respRate = data.getRespirationRate();
        float respMod = 0;
        if (respRate <= 0) {
            respMod = MedicalConfig.RESP_MOD_STOP.get().floatValue();            // 呼吸停止 → 心率下降
        } else if (respRate < 8) {
            respMod = MedicalConfig.RESP_MOD_SLOW.get().floatValue();            // 呼吸过缓
        } else if (respRate <= 20) {
            respMod = 0;              // 正常范围
        } else if (respRate <= 30) {
            respMod = (respRate - 20) * 1.0f;   // 轻微加快
        } else if (respRate <= 45) {
            respMod = 10 + (respRate - 30) * 1.5f;  // 明显加快
        } else {
            respMod = 32.5f + (respRate - 45) * 2.0f;  // 极度加快
        }

        // ★ 颅内出血库欣反应（等级 2/3 心率代偿性升高，等级 4 脑疝骤降） ★
        float cushingMod = 0;
        if (data.getIntracranialOnsetTimer() >= MedicalConfig.ONSET_DELAY.get()) {
            int icLevel = data.getIntracranialLevel();
            if (icLevel == 2) {
                cushingMod = 15.0f;
            } else if (icLevel == 3) {
                cushingMod = 25.0f;
            } else if (icLevel == 4) {
                cushingMod = -40.0f;  // 脑疝形成，心率骤降
            }
        }

        // ★ 血压代偿反射（低血压心动过速代偿，高血压迷走神经反射减慢） ★
        float bpReflexMod = 0;
        float map = data.getMeanArterialPressure();
        float hypotensionThreshold = MedicalConfig.MAP_HYPOTENSION.get().floatValue(); // 65.0
        if (map < hypotensionThreshold) {
            bpReflexMod += (hypotensionThreshold - map) * 0.5f;
        }
        if (map > 120.0f) {
            bpReflexMod -= (map - 120.0f) * 0.3f;
        }

        // ★ SIRS 炎症因子交感神经刺激加成 ★
        float sirsMod = 0;
        int sirsLevel = data.getSirsLevel();
        if (sirsLevel > 0) {
            sirsMod = sirsLevel * MedicalConfig.SIRS_HEART_BONUS_PER_LEVEL.get().floatValue();
        }

        // ★ 血胸交感代偿加成 ★
        int hemoLevel = data.getHemothoraxLevel();
        float hemoHeartMod = switch (hemoLevel) {
            case 1 -> MedicalConfig.LEVEL1_HEART_RATE_BONUS.get().floatValue();
            case 2 -> MedicalConfig.LEVEL2_HEART_RATE_BONUS.get().floatValue();
            case 3 -> MedicalConfig.LEVEL3_HEART_RATE_BONUS.get().floatValue();
            case 4 -> MedicalConfig.LEVEL4_HEART_RATE_BONUS.get().floatValue();
            default -> 0.0f;
        };

        float heartRate = MedicalConfig.RESTING_HEART_RATE.get() + bloodMod + painMod + consciousnessMod + exerciseMod + opioidMod + respMod + cushingMod + bpReflexMod + sirsMod + hemoHeartMod;

        if (heartRate <= 0) {
            data.setCardiacArrest(true);
            data.setHeartRate(0);
            return;
        }
        int maxHr = MedicalConfig.MAX_HEART_RATE.get();
        if (heartRate > maxHr) heartRate = maxHr;

        data.setHeartRate(Math.round(heartRate));
    }
}