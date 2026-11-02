package com.FIYTP.realmedicalmod.loop;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.config.MedicalConfig;
import net.minecraft.world.entity.player.Player;

/**
 * 血容量自然回升模块 (BloodVolumeRegenModule)
 * 职责：在无活动性失血、未处于危重状态且饱食度充足时，缓慢回升血容量。
 * 回升量通过浮点累加器结算，避免整数截断偏差。
 */
public class BloodVolumeRegenModule implements UpdateModule {

    @Override
    public void process(MedicalData data) {
        process(null, data);
    }

    public void process(Player player, MedicalData data) {
        if (data == null) return;
        if (!MedicalConfig.BLOOD_VOLUME_REGEN_ENABLED.get()) return;

        int bloodVolume = data.getBloodVolume();
        if (bloodVolume >= 5000) return;

        // 1. 无活动性失血：与 BleedModule 相同口径计算总失血速率
        if (hasActiveBleeding(data)) return;

        // 2. 危重状态不回升
        if (data.isCardiacArrest() || data.isInVFib()) return;
        if (data.getSirsLevel() > 1) return;

        // 3. 饱食度门槛
        if (player != null) {
            int food = player.getFoodData().getFoodLevel();
            if (food < MedicalConfig.BLOOD_REGEN_FOOD_THRESHOLD.get()) return;
        }

        // 4. 累加回升量（浮点累加器，杜绝截断偏差）
        float rate = MedicalConfig.BLOOD_REGEN_RATE.get().floatValue();
        float accumulated = data.getBloodRegenAccumulator() + rate;
        int regenInt = (int) accumulated;
        data.setBloodRegenAccumulator(accumulated - regenInt);

        if (regenInt > 0) {
            data.setBloodVolume(Math.min(5000, bloodVolume + regenInt));
        }
    }

    private boolean hasActiveBleeding(MedicalData data) {
        float map = data.getMeanArterialPressure();
        float mapMultiplier = BleedModule.getMapMultiplier(map);

        for (MedicalData.Limb limb : MedicalData.Limb.values()) {
            MedicalData.LimbData ld = data.getLimb(limb);
            if (ld == null) continue;

            // 外出血
            if (!ld.isBandaged() && !ld.isTourniqueted()) {
                float typeMul = (limb == MedicalData.Limb.CHEST)
                        ? MedicalConfig.CHEST_EXTERNAL_MULTIPLIER.get().floatValue()
                        : MedicalConfig.EXTERNAL_MULTIPLIER.get().floatValue();
                if (ld.getBleeding() * mapMultiplier * typeMul > 0.0f) return true;
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
            if (ld.getInternalBleedingRate() * mapMultiplier * internalTypeMul > 0.0f) return true;
        }
        return false;
    }
}
