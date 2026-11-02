package com.FIYTP.realmedicalmod.event;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.capability.MedicalDataCapability;
import com.FIYTP.realmedicalmod.config.MedicalConfig;
import com.FIYTP.realmedicalmod.system.BleedingSystem;
import com.FIYTP.realmedicalmod.system.PainSystem;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;

@EventBusSubscriber(modid = "realmedicalmod")
public class FallDamageHandler {

    @SubscribeEvent
    public static void onLivingFall(LivingFallEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getEntity() instanceof Player player)) return;

        float distance = event.getDistance();

        // ★ 低于安全坠落距离完全无伤害，消除抖动和音效 ★
        float safeDist = MedicalConfig.SAFE_FALL_DISTANCE.get().floatValue();
        if (distance < safeDist) {
            event.setDamageMultiplier(0.0f);
            event.getEntity().fallDistance = 0;
            return;
        }

        // 5格及以上才执行医疗逻辑
        float amount = Math.min(distance, 40.0f);

        MedicalData data = player.getData(MedicalDataCapability.MEDICAL_DATA);
        if (data == null) return;

        MedicalData.Limb hitLimb = determineFallLimb(distance);
        MedicalData.LimbData limbData = data.getLimb(hitLimb);

        // 坠落固定走 BLUNT 分类
        float externalRatio = MedicalConfig.BLUNT_EXTERNAL_RATIO.get().floatValue();
        float internalRatio = MedicalConfig.BLUNT_INTERNAL_RATIO.get().floatValue();

        float totalBleeding = BleedingSystem.calculateBleeding(amount, false);
        int bleedingAdded = (int)(totalBleeding * externalRatio);
        if (hitLimb == MedicalData.Limb.HEAD) {
            // 头部只扣外出血，不分配普通内出血
            limbData.setBleeding(limbData.getBleeding() + bleedingAdded);
        } else {
            limbData.setBleeding(limbData.getBleeding() + bleedingAdded);
            limbData.setInternalBleedingRate(limbData.getInternalBleedingRate() + (int)(totalBleeding * internalRatio));
        }
        if (externalRatio > 0 && bleedingAdded > 0) {
            limbData.setOpenWound(true);
        }

        int newPain = PainSystem.calculatePainLevel(amount);
        int currentPain = limbData.getPainLevel();
        int resultPain = PainSystem.applyPain(currentPain, newPain);
        limbData.setPainLevel(resultPain);

        if (hitLimb == MedicalData.Limb.HEAD && amount >= 5.0f) {
            int brainDamage = (int)((amount - 5.0f) * 2 + 10);
            data.setBrainHealth(Math.max(0, data.getBrainHealth() - brainDamage));
            int headPain = limbData.getPainLevel() + 2;
            limbData.setPainLevel(Math.min(10, headPain));

            // 颅内出血触发判定
            double baseProb = 0.0;
            if (amount < 13.0f) {
                baseProb = MedicalConfig.TRIGGER_CHANCE_AT_10.get();
            } else if (amount < 16.0f) {
                baseProb = MedicalConfig.TRIGGER_CHANCE_AT_13.get();
            } else if (amount < 19.0f) {
                baseProb = MedicalConfig.TRIGGER_CHANCE_AT_16.get();
            } else if (amount <= 20.0f) {
                baseProb = MedicalConfig.TRIGGER_CHANCE_AT_19.get();
            } else {
                baseProb = MedicalConfig.TRIGGER_CHANCE_AT_20_PLUS.get();
            }

            double priorBonus = 0.0;
            if (data.getBrainHealth() < 70) {
                priorBonus = MedicalConfig.PRIOR_DAMAGE_BONUS.get();
            }

            double finalProb = baseProb + priorBonus;
            if (Math.random() < finalProb) {
                double ratePerDmg = MedicalConfig.IC_RATE_PER_DAMAGE.get();
                int bleedRate = (int) Math.round(amount * ratePerDmg);
                int maxRate = MedicalConfig.IC_MAX_RATE.get();
                bleedRate = Math.min(bleedRate, maxRate);

                if (bleedRate > data.getIntracranialBleedRate()) {
                    data.setIntracranialBleedRate(bleedRate);
                    data.setIntracranialOnsetTimer(0);
                }
            }
        }

        // ====== 坠落骨折判定（基于坠落高度，头部除外） ======
        if (distance >= MedicalConfig.FALL_MIN_HEIGHT_FOR_FRACTURE.get() && hitLimb != MedicalData.Limb.HEAD && !limbData.hasFracture()) {
            float chance = distance * MedicalConfig.FALL_FRACTURE_CHANCE_PER_BLOCK.get().floatValue();
            if (Math.random() < chance) {
                limbData.setFracture(true);
                limbData.setOpenFracture(false); // 坠落固定闭合性骨折

                // 骨折瞬间疼痛
                int fracturePain = MedicalConfig.FRACTURE_PAIN_LEVEL.get();
                limbData.setPainLevel(PainSystem.applyPain(limbData.getPainLevel(), fracturePain));

                // 骨折内出血（动态计算）
                float fractureBleed = amount * MedicalConfig.FRACTURE_BLEED_PER_DAMAGE.get().floatValue();
                fractureBleed = Math.min(fractureBleed, MedicalConfig.FRACTURE_BLEED_MAX.get().floatValue());

                float multiplier = (hitLimb == MedicalData.Limb.CHEST)
                        ? MedicalConfig.FRACTURE_CHEST_MULTIPLIER.get().floatValue()
                        : MedicalConfig.FRACTURE_LIMB_MULTIPLIER.get().floatValue();
                fractureBleed *= multiplier;

                limbData.setInternalBleedingRate(limbData.getInternalBleedingRate() + (int)fractureBleed);
            }
        }

        player.syncData(MedicalDataCapability.MEDICAL_DATA);
    }

    private static MedicalData.Limb determineFallLimb(float distance) {
        double roll = Math.random();
        if (distance < 8.0f) {
            if (roll < 0.45) return MedicalData.Limb.LEFT_LEG;
            if (roll < 0.90) return MedicalData.Limb.RIGHT_LEG;
            return roll < 0.95 ? MedicalData.Limb.LEFT_ARM : MedicalData.Limb.RIGHT_ARM;
        } else if (distance < 15.0f) {
            if (roll < 0.35) return MedicalData.Limb.LEFT_LEG;
            if (roll < 0.70) return MedicalData.Limb.RIGHT_LEG;
            if (roll < 0.85) return MedicalData.Limb.LEFT_ARM;
            if (roll < 0.95) return MedicalData.Limb.RIGHT_ARM;
            return roll < 0.98 ? MedicalData.Limb.CHEST : MedicalData.Limb.HEAD;
        } else {
            if (roll < 0.30) return MedicalData.Limb.LEFT_LEG;
            if (roll < 0.60) return MedicalData.Limb.RIGHT_LEG;
            if (roll < 0.75) return MedicalData.Limb.LEFT_ARM;
            if (roll < 0.85) return MedicalData.Limb.RIGHT_ARM;
            if (roll < 0.95) return MedicalData.Limb.CHEST;
            return MedicalData.Limb.HEAD;
        }
    }
}
