package com.FIYTP.realmedicalmod.loop;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.config.MedicalConfig;

/**
 * 颅内出血模块 (IntracranialHemorrhageModule)
 * 职责：
 * 1. 颅内出血速率以 intracranialBleedRate / 60.0f 累加至 intracranialAccumulation (0~IC_MAX_ACCUMULATION mL)。
 * 2. 潜伏期/清醒期计时（ONSET_DELAY 秒），潜伏期内仅微弱扣减意识（每秒 -0.5）。
 * 3. 清醒期后急性发作，重新计算颅内压/颅内出血等级（0~4级）。
 * 4. 驱动急性症状（脑健康直接破坏、重度意识丧失等在 BrainHealthModule 与 ConsciousnessModule 联动生效）。
 */
public class IntracranialHemorrhageModule implements UpdateModule {

    @Override
    public void process(MedicalData data) {
        if (data == null) return;

        int bleedRate = data.getIntracranialBleedRate();
        if (bleedRate <= 0) {
            if (data.getIntracranialOnsetTimer() > 0) {
                data.setIntracranialOnsetTimer(0);
            }
            return;
        }

        // 1. 累加积血 (最大 IC_MAX_ACCUMULATION mL)
        float currentAcc = data.getIntracranialAccumulation();
        float added = bleedRate / 60.0f;
        float maxAcc = MedicalConfig.IC_MAX_ACCUMULATION.get().floatValue();
        currentAcc = Math.min(maxAcc, currentAcc + added);
        data.setIntracranialAccumulation(currentAcc);

        // 2. 评定等级
        int level;
        if (currentAcc >= MedicalConfig.IC_LEVEL4_THRESHOLD.get().floatValue()) {
            level = 4;
        } else if (currentAcc >= MedicalConfig.IC_LEVEL3_THRESHOLD.get().floatValue()) {
            level = 3;
        } else if (currentAcc >= MedicalConfig.IC_LEVEL2_THRESHOLD.get().floatValue()) {
            level = 2;
        } else if (currentAcc >= MedicalConfig.IC_LEVEL1_THRESHOLD.get().floatValue()) {
            level = 1;
        } else {
            level = 0;
        }
        data.setIntracranialLevel(level);

        // 3. 潜伏期/清醒期计时推进（ONSET_DELAY 秒）
        int onsetTimer = data.getIntracranialOnsetTimer();
        int onsetDelay = MedicalConfig.ONSET_DELAY.get();
        if (onsetTimer < onsetDelay) {
            data.setIntracranialOnsetTimer(onsetTimer + 1);
        }
    }
}
