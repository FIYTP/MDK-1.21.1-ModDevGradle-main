package com.FIYTP.realmedicalmod.loop;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.config.MedicalConfig;

/**
 * 基础内出血模块 (InternalBleedingModule)
 * 职责：处理四肢/非致命部位低速内出血的自愈逻辑。
 * 头部（HEAD）和胸部（CHEST）完全不自愈；
 * 极低速率（<= selfHealThreshold mL/min）时，每 selfHealInterval 秒自愈 1 mL/min。
 */
public class InternalBleedingModule implements UpdateModule {

    @Override
    public void process(MedicalData data) {
        if (data == null) return;

        for (MedicalData.Limb limb : MedicalData.Limb.values()) {
            MedicalData.LimbData ld = data.getLimb(limb);
            if (ld == null) continue;

            int rate = ld.getInternalBleedingRate();
            if (rate <= 0) {
                if (ld.getInternalHealingTimer() > 0) {
                    ld.setInternalHealingTimer(0);
                }
                continue;
            }

            // 头部与胸部内出血完全不自愈
            if (limb == MedicalData.Limb.HEAD || limb == MedicalData.Limb.CHEST) {
                if (ld.getInternalHealingTimer() > 0) {
                    ld.setInternalHealingTimer(0);
                }
                continue;
            }

            // 四肢与非致命部位：极低速率下缓慢自愈
            int threshold = MedicalConfig.SELF_HEAL_THRESHOLD.get();
            int interval = MedicalConfig.SELF_HEAL_INTERVAL.get();
            if (rate <= threshold) {
                int timer = ld.getInternalHealingTimer() + 1;
                if (timer >= interval) {
                    ld.setInternalBleedingRate(rate - 1);
                    timer = 0;
                }
                ld.setInternalHealingTimer(timer);
            } else {
                if (ld.getInternalHealingTimer() > 0) {
                    ld.setInternalHealingTimer(0);
                }
            }
        }
    }
}
