package com.FIYTP.realmedicalmod.treatment;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.config.MedicalConfig;

public class InfectionTreatment {

    public static void applyDisinfectant(MedicalData.LimbData ld) {
        if (ld == null) return;
        int duration = MedicalConfig.DISINFECTANT_DURATION.get();
        ld.setDisinfectantTimer(duration);
    }
}
