package com.FIYTP.realmedicalmod.loop;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.config.MedicalConfig;

public class BloodOxygenModule implements UpdateModule {

    @Override
    public void process(MedicalData data) {
        // 1. 心脏骤停/室颤优先级最高
        if (data.isCardiacArrest()) {
            int loss = MedicalConfig.CARDIAC_ARREST_OXYGEN_LOSS.get();
            data.setBloodOxygenFloat(Math.max(0.0f, data.getBloodOxygenFloat() - loss));
            return;
        }
        if (data.isInVFib()) {
            int loss = MedicalConfig.VFIB_OXYGEN_LOSS.get();
            data.setBloodOxygenFloat(Math.max(0.0f, data.getBloodOxygenFloat() - loss));
            return;
        }

        int respRate = data.getRespirationRate();
        float currentOxygen = data.getBloodOxygenFloat();
        int bloodVol = data.getBloodVolume();
        int maxBlood = data.getMaxBloodVolume();
        float bloodRatio = (float) bloodVol / (float) maxBlood;

        // 2. 呼吸效率贡献（核心修复与水下屏息缓冲机制）
        float respEffect;
        if (data.isUnderwater()) {
            int currentTimer = data.getUnderwaterTimer() + 1;
            data.setUnderwaterTimer(currentTimer);
            int bufferTime = MedicalConfig.UNDERWATER_BREATH_HOLD_TIME.get();
            if (currentTimer <= bufferTime) {
                // 水下屏息缓冲期：肺部有氧储备，血氧仅微量消耗
                respEffect = -MedicalConfig.BREATH_HOLD_OXYGEN_LOSS.get().floatValue();
            } else {
                // 屏息耗尽/溺水窒息期：血氧剧烈下降
                respEffect = MedicalConfig.RESPIRATION_STOP_EFFECT.get().floatValue();
            }
        } else {
            // 不在水下，重置屏息计时器
            data.setUnderwaterTimer(0);
            if (respRate <= 0) {
                // 非水下呼吸停止（如气道堵塞、严重神经呼吸中枢抑制等）
                respEffect = MedicalConfig.RESPIRATION_STOP_EFFECT.get().floatValue();
            } else {
                // 呼吸频率偏离基准的线性贡献
                float baseline = MedicalConfig.RESPIRATION_BASELINE.get().floatValue();
                float rate = MedicalConfig.RESPIRATION_EFFECT_PER_RATE.get().floatValue();
                respEffect = (respRate - baseline) * rate;

                // 毒素呼吸抑制：呼吸肌乏力 → 有效换气按毒素浓度折减
                float toxin = data.getToxinValue();
                float toxinMidThreshold = MedicalConfig.TOXIN_MID_THRESHOLD.get().floatValue(); // 40
                if (toxin >= toxinMidThreshold) {
                    float respSlope = MedicalConfig.TOXIN_RESP_SUPPRESSION_SLOPE.get().floatValue(); // 0.003
                    respEffect *= Math.max(0.05f, 1.0f - respSlope * (toxin - toxinMidThreshold));
                }

                // 吸入性损伤：气道水肿 → 有效换气按严重度折减（烧伤系统）
                float inhalation = data.getInhalationInjury();
                if (inhalation > 0.0f) {
                    float ventMult = MedicalConfig.BURN_INHALATION_VENT_MULT.get().floatValue(); // 0.3
                    respEffect *= Math.max(0.1f, 1.0f - ventMult * inhalation);
                }
            }
        }

        // 3. 血容量效率贡献
        float volumeEffect;
        if (bloodRatio > 0.7f) {
            volumeEffect = 0.5f;
        } else if (bloodRatio > 0.5f) {
            volumeEffect = 0.0f;
        } else if (bloodRatio > 0.3f) {
            volumeEffect = -0.3f;
        } else if (bloodRatio > 0.1f) {
            volumeEffect = -0.8f;
        } else {
            volumeEffect = -1.5f;
        }

        // 4. 肺挫伤惩罚
        float lungContusion = data.getLungContusion();
        float contusionPenalty = (lungContusion / 10.0f) * MedicalConfig.CONTUSION_OXYGEN_PENALTY.get().floatValue();

        // 5. SIRS 惩罚
        int sirsLevel = data.getSirsLevel();
        float sirsPenalty = sirsLevel * MedicalConfig.SIRS_OXYGEN_PENALTY_PER_LEVEL.get().floatValue();

        // 6. 综合计算
        float change = respEffect + volumeEffect - contusionPenalty - sirsPenalty;

        // 7. 恢复上限：血氧无法超过 98%
        float nextOxygen = currentOxygen + change;
        if (nextOxygen > 98.0f) nextOxygen = 98.0f;
        if (nextOxygen < 0.0f) nextOxygen = 0.0f;

        data.setBloodOxygenFloat(nextOxygen);
    }
}