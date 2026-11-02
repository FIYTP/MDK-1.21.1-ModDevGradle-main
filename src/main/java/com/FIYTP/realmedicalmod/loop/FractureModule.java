package com.FIYTP.realmedicalmod.loop;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.config.MedicalConfig;

import java.util.Random;

public class FractureModule implements UpdateModule {

    private final Random random = new Random();

    @Override
    public void process(MedicalData data) {
        for (MedicalData.Limb limb : MedicalData.Limb.values()) {
            MedicalData.LimbData ld = data.getLimb(limb);

            if (!ld.hasFracture()) continue;

            // 夹板愈合
            if (ld.hasSplint()) {
                int timer = ld.getFractureTimer() + 1;
                ld.setFractureTimer(timer);

                if (timer >= MedicalConfig.SPLINT_HEAL_TIME.get()) {
                    ld.setFracture(false);
                    ld.setSplint(false);
                    ld.setFractureTimer(0);
                    ld.setOpenFracture(false);
                    if (ld.getBleeding() <= 0) {
                        ld.setOpenWound(false);
                        ld.setInfectionAgeTimer(0);
                    }
                }
            }

            // 开放性骨折额外感染（仅未愈合时）
            if (ld.isOpenFracture()) {
                float rate = MedicalConfig.OPEN_FRACTURE_INFECTION_RATE.get().floatValue();
                int intPart = (int) rate;
                float fracPart = rate - intPart;
                int increase = intPart + (random.nextFloat() < fracPart ? 1 : 0);
                if (increase > 0) {
                    ld.setInfection(Math.min(100, ld.getInfection() + increase));
                }
            }
        }
    }
}
