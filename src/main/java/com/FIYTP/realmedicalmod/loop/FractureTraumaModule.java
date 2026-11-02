package com.FIYTP.realmedicalmod.loop;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.config.MedicalConfig;
import com.FIYTP.realmedicalmod.system.PainSystem;
import net.minecraft.server.level.ServerPlayer;

public class FractureTraumaModule implements UpdateModule {

    @Override
    public void process(MedicalData data) {
        // 无 player 时的兜底
    }

    public void process(ServerPlayer player, MedicalData data) {
        if (player == null || data == null) return;

        boolean anyTrauma = false;

        for (MedicalData.Limb limb : MedicalData.Limb.values()) {
            MedicalData.LimbData ld = data.getLimb(limb);
            if (ld == null || !ld.hasFracture()) continue;
            if (ld.hasSplint()) continue; // 夹板后无二次创伤

            // 检测玩家是否在使用该肢体
            boolean isUsing = false;
            if (limb == MedicalData.Limb.LEFT_LEG || limb == MedicalData.Limb.RIGHT_LEG) {
                isUsing = player.isSprinting()
                        || Math.abs(player.getDeltaMovement().horizontalDistance()) > 0.05
                        || player.getDeltaMovement().y > 0.1;
            } else if (limb == MedicalData.Limb.LEFT_ARM || limb == MedicalData.Limb.RIGHT_ARM) {
                isUsing = player.swinging || player.isUsingItem();
            } else if (limb == MedicalData.Limb.CHEST) {
                isUsing = player.isSprinting();
            }

            if (isUsing) {
                anyTrauma = true;
            }
        }

        if (!anyTrauma) {
            int timer = data.getFractureTraumaTimer();
            if (timer > 0) {
                data.setFractureTraumaTimer(Math.max(0, timer - 1));
            }
            return;
        }

        int timer = data.getFractureTraumaTimer() + 1;
        if (timer < MedicalConfig.SECONDARY_TRAUMA_INTERVAL.get()) {
            data.setFractureTraumaTimer(timer);
            return;
        }
        data.setFractureTraumaTimer(0);

        // 对所有未夹板的骨折肢体施加二次创伤
        for (MedicalData.Limb limb : MedicalData.Limb.values()) {
            MedicalData.LimbData ld = data.getLimb(limb);
            if (ld == null || !ld.hasFracture() || ld.hasSplint()) continue;

            // 加剧内出血（设置上限，模拟筋膜间隙内骨折血肿压迫自限止血机制）
            int maxInternal = MedicalConfig.SECONDARY_TRAUMA_MAX_INTERNAL_BLEED.get();
            int currentInternal = ld.getInternalBleedingRate();
            if (currentInternal < maxInternal) {
                ld.setInternalBleedingRate(Math.min(maxInternal, currentInternal + MedicalConfig.SECONDARY_TRAUMA_INTERNAL_BLEED.get()));
            }

            // 加剧外出血（仅开放性骨折，设置上限）
            if (ld.isOpenFracture()) {
                int maxExternal = MedicalConfig.SECONDARY_TRAUMA_MAX_EXTERNAL_BLEED.get();
                int currentBleed = ld.getBleeding();
                if (currentBleed < maxExternal) {
                    ld.setBleeding(Math.min(maxExternal, currentBleed + MedicalConfig.SECONDARY_TRAUMA_EXTERNAL_BLEED.get()));
                }
                ld.setOpenWound(true);
            }

            // 加剧疼痛
            int newPain = PainSystem.applyPain(ld.getPainLevel(), MedicalConfig.SECONDARY_TRAUMA_PAIN_INCREASE.get());
            ld.setPainLevel(Math.min(10, newPain));
        }
    }
}
