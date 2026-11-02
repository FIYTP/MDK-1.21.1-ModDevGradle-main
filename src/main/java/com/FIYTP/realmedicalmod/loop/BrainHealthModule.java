package com.FIYTP.realmedicalmod.loop;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.config.MedicalConfig;

public class BrainHealthModule implements UpdateModule {

    @Override
    public void process(MedicalData data) {
        // ===== 1. 心脏骤停 → 脑健康极速下降 =====
        if (data.isCardiacArrest()) {
            float newBrain = data.getBrainHealthFloat() - MedicalConfig.CARDIAC_ARREST_BRAIN_LOSS.get();
            data.setBrainHealthFloat(Math.max(0.0f, newBrain));
            return;
        }

        // ===== 2. 室颤 → 脑健康快速下降 =====
        if (data.isInVFib()) {
            float newBrain = data.getBrainHealthFloat() - MedicalConfig.VFIB_BRAIN_LOSS.get();
            data.setBrainHealthFloat(Math.max(0.0f, newBrain));
            return;
        }

        // ===== 3. 正常情况：血氧影响 =====
        float oxygen = data.getBloodOxygenFloat();
        float change;
        if (oxygen >= 90.0f) {
            change = MedicalConfig.BRAIN_LOSS_OXYGEN_90.get().floatValue();
        } else if (oxygen >= 70.0f) {
            change = 0.0f;
        } else if (oxygen >= 50.0f) {
            change = MedicalConfig.BRAIN_LOSS_OXYGEN_50.get().floatValue();
        } else if (oxygen >= 30.0f) {
            change = MedicalConfig.BRAIN_LOSS_OXYGEN_30.get().floatValue();
        } else {
            change = MedicalConfig.BRAIN_LOSS_OXYGEN_LOW.get().floatValue();
        }

        // 颅内出血急性期直接摧毁脑健康（清醒期结束后）
        if (data.getIntracranialOnsetTimer() >= MedicalConfig.ONSET_DELAY.get()) {
            int icLevel = data.getIntracranialLevel();
            if (icLevel == 4) {
                change -= MedicalConfig.IC_LEVEL4_BRAIN_DAMAGE.get().floatValue();
            } else if (icLevel == 3) {
                change -= 2.0f;
            } else if (icLevel == 2) {
                change -= 1.0f;
            }
        }

        // 血压对脑健康的影响
        float map = data.getMeanArterialPressure();
        int sbp = data.getSystolicBP();

        if (map < 50) {
            float coeff = MedicalConfig.BRAIN_PENALTY_MAP_BELOW_50.get().floatValue();
            change -= ((50.0f - map) / 50.0f) * coeff;
        }

        if (sbp >= 200) {
            float coeff = MedicalConfig.BRAIN_PENALTY_SBP_ABOVE_200.get().floatValue();
            change -= ((sbp - 200) / 20.0f) * coeff;
        }

        float newBrain = data.getBrainHealthFloat() + change;
        data.setBrainHealthFloat(Math.max(0.0f, Math.min(100.0f, newBrain)));
    }
}
