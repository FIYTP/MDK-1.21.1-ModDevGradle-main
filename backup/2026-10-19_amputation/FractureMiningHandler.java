package com.FIYTP.realmedicalmod.event;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.capability.MedicalDataCapability;
import com.FIYTP.realmedicalmod.config.MedicalConfig;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = "realmedicalmod")
public class FractureMiningHandler {

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        if (player == null) return;

        MedicalData data = player.getData(MedicalDataCapability.MEDICAL_DATA);
        if (data == null) return;

        MedicalData.LimbData leftArm = data.getLimb(MedicalData.Limb.LEFT_ARM);
        MedicalData.LimbData rightArm = data.getLimb(MedicalData.Limb.RIGHT_ARM);

        float miningMultiplier = 1.0f;
        boolean hasFracture = false;

        if (leftArm != null && leftArm.hasFracture()) {
            hasFracture = true;
            float m = leftArm.hasSplint()
                    ? MedicalConfig.ARM_FRACTURE_SPLINT_MINING_MULTIPLIER.get().floatValue()
                    : MedicalConfig.ARM_FRACTURE_NO_SPLINT_MINING_MULTIPLIER.get().floatValue();
            miningMultiplier = Math.min(miningMultiplier, m);
        }

        if (rightArm != null && rightArm.hasFracture()) {
            hasFracture = true;
            float m = rightArm.hasSplint()
                    ? MedicalConfig.ARM_FRACTURE_SPLINT_MINING_MULTIPLIER.get().floatValue()
                    : MedicalConfig.ARM_FRACTURE_NO_SPLINT_MINING_MULTIPLIER.get().floatValue();
            miningMultiplier = Math.min(miningMultiplier, m);
        }

        if (hasFracture) {
            event.setNewSpeed(event.getOriginalSpeed() * miningMultiplier);
        }
    }
}
