package com.FIYTP.realmedicalmod.loop;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.config.MedicalConfig;

public class BleedModule implements UpdateModule {

    @Override
    public void process(MedicalData data) {
        float map = data.getMeanArterialPressure();
        float mapMultiplier = getMapMultiplier(map);

        float totalRate = 0.0f;
        for (MedicalData.Limb limb : MedicalData.Limb.values()) {
            MedicalData.LimbData ld = data.getLimb(limb);

            // 外出血
            if (!ld.isBandaged() && !ld.isTourniqueted()) {
                float typeMul = (limb == MedicalData.Limb.CHEST)
                        ? MedicalConfig.CHEST_EXTERNAL_MULTIPLIER.get().floatValue()
                        : MedicalConfig.EXTERNAL_MULTIPLIER.get().floatValue();
                totalRate += ld.getBleeding() * mapMultiplier * typeMul;
            }

            // 内出血
            float internalTypeMul;
            if (limb == MedicalData.Limb.HEAD) {
                internalTypeMul = MedicalConfig.INTRACRANIAL_MULTIPLIER.get().floatValue();
            } else if (limb == MedicalData.Limb.CHEST) {
                internalTypeMul = MedicalConfig.HEMOTHORAX_MULTIPLIER.get().floatValue();
            } else {
                internalTypeMul = MedicalConfig.INTERNAL_MULTIPLIER.get().floatValue();
            }
            totalRate += ld.getInternalBleedingRate() * mapMultiplier * internalTypeMul;
        }

        if (totalRate > 0.0f) {
            float loss = totalRate * MedicalConfig.BLEED_TO_BLOOD_RATIO.get().floatValue();

            // 累加小数部分
            float accumulated = data.getBloodLossAccumulator() + loss;

            // 取整数部分扣血
            int lossInt = (int) accumulated;
            float remainder = accumulated - lossInt;

            // 保存小数部分
            data.setBloodLossAccumulator(remainder);

            // 扣血容量
            if (lossInt > 0) {
                int newVolume = Math.max(0, data.getBloodVolume() - lossInt);
                data.setBloodVolume(newVolume);
            }
        }
    }

    /**
     * 根据平均动脉压 (MAP) 从 MedicalConfig 读取出血速率修正系数
     */
    public static float getMapMultiplier(float map) {
        if (map < 40.0f) {
            return MedicalConfig.BLEED_MULT_MAP_BELOW_40.get().floatValue();
        } else if (map < 55.0f) {
            return MedicalConfig.BLEED_MULT_MAP_40_55.get().floatValue();
        } else if (map < 70.0f) {
            return MedicalConfig.BLEED_MULT_MAP_55_70.get().floatValue();
        } else if (map < 85.0f) {
            return MedicalConfig.BLEED_MULT_MAP_70_85.get().floatValue();
        } else if (map < 105.0f) {
            return MedicalConfig.BLEED_MULT_MAP_85_105.get().floatValue();
        } else if (map < 125.0f) {
            return MedicalConfig.BLEED_MULT_MAP_105_125.get().floatValue();
        } else if (map < 145.0f) {
            return MedicalConfig.BLEED_MULT_MAP_125_145.get().floatValue();
        } else if (map < 165.0f) {
            return MedicalConfig.BLEED_MULT_MAP_145_165.get().floatValue();
        } else {
            return MedicalConfig.BLEED_MULT_MAP_ABOVE_165.get().floatValue();
        }
    }
}
