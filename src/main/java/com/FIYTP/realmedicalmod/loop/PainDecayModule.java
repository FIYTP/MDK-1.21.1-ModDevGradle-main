package com.FIYTP.realmedicalmod.loop;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.config.MedicalConfig;

public class PainDecayModule implements UpdateModule {
    @Override
    public void process(MedicalData data) {
        int timer = data.getPainDecayTimer() + 1;
        if (timer >= MedicalConfig.PAIN_DECAY_INTERVAL.get()) {
            timer = 0;
            for (MedicalData.Limb limb : MedicalData.Limb.values()) {
                MedicalData.LimbData ld = data.getLimb(limb);
                if (ld.getPainLevel() > 0) {
                    ld.setPainLevel(ld.getPainLevel() - 1);
                }
            }
        }
        data.setPainDecayTimer(timer);
    }
}