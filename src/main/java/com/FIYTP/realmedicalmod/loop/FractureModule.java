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

            // 骨折愈合倒计时已统一交由 ModItems 骨科/夹板权威倒计时系统推进，
            // 此处不再重复反向递增 fractureTimer，避免计时器正负抵消死锁与开放性骨折误自愈。

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
