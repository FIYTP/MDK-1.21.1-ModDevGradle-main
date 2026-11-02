package com.FIYTP.realmedicalmod.loop;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.config.MedicalConfig;
import net.minecraft.world.entity.player.Player;

import java.util.Random;

/**
 * 局部感染模块 (LocalInfectionModule)
 * 职责：管理每个肢体的局部感染增长、伤口窗口期以及局部炎症评估。
 */
public class LocalInfectionModule implements UpdateModule {

    private final Random random = new Random();

    @Override
    public void process(MedicalData data) {
        process(null, data);
    }

    public void process(Player player, MedicalData data) {
        if (data == null) return;

        boolean isWet = isInWetEnvironment(player);
        int windowPeriod = MedicalConfig.INFECTION_WINDOW_PERIOD.get();
        float baseRate = MedicalConfig.INFECTION_BASE_RATE.get().floatValue();
        float bandageReduction = MedicalConfig.INFECTION_BANDAGE_REDUCTION.get().floatValue();
        float wetBonus = MedicalConfig.INFECTION_WET_BONUS.get().floatValue();
        float fractureBonus = MedicalConfig.INFECTION_FRACTURE_BONUS.get().floatValue();

        // 免疫治疗参数
        float immuneBase = MedicalConfig.IMMUNE_BASE_RATE.get().floatValue();
        float hungerMult = getHungerMultiplier(player);
        float disinfectantMult = MedicalConfig.DISINFECTANT_IMMUNE_MULT.get().floatValue();
        boolean disinfectantBlocksGrowth = MedicalConfig.DISINFECTANT_BLOCKS_GROWTH.get();

        for (MedicalData.Limb limb : MedicalData.Limb.values()) {
            MedicalData.LimbData ld = data.getLimb(limb);
            if (ld == null) continue;

            // 1. 递减消毒剂计时器
            int disinfectantTimer = ld.getDisinfectantTimer();
            boolean disinfectantActive = false;
            if (disinfectantTimer > 0) {
                ld.setDisinfectantTimer(disinfectantTimer - 1);
                disinfectantActive = true;
            }

            // 2. 感染增长逻辑
            boolean canGrow = ld.hasOpenWound();
            if (canGrow && disinfectantActive && disinfectantBlocksGrowth) {
                canGrow = false;
            }

            if (canGrow) {
                int age = ld.getInfectionAgeTimer();
                if (age < windowPeriod) {
                    ld.setInfectionAgeTimer(age + 1);
                } else {
                    if (ld.getInfection() <= 0) {
                        // 首次感染
                        int initial = 5 + random.nextInt(11);
                        ld.setInfection(initial);
                    } else if (ld.getInfection() < 100) {
                        // 持续增长
                        float rate = baseRate;
                        if (ld.isBandaged()) rate *= bandageReduction;
                        if (isWet) rate *= (1.0f + wetBonus);
                        if (ld.hasFracture()) rate *= (1.0f + fractureBonus);

                        // 累积小数
                        float accum = ld.getInfectionAccumulator() + rate;
                        int increase = (int) accum;
                        if (increase > 0) {
                            ld.setInfection(Math.min(100, ld.getInfection() + increase));
                            ld.setInfectionAccumulator(accum - increase);
                        } else {
                            ld.setInfectionAccumulator(accum);
                        }
                    }
                }
            }

            // 3. 免疫治疗（独立于增长）
            if (ld.getInfection() > 0) {
                float immuneRate = immuneBase * hungerMult;
                if (disinfectantActive) {
                    immuneRate *= disinfectantMult;
                }

                // 累积负值治疗
                float accum = ld.getInfectionAccumulator() - immuneRate;
                if (accum < 0) {
                    int decrease = (int) Math.ceil(-accum);
                    ld.setInfection(Math.max(0, ld.getInfection() - decrease));
                    ld.setInfectionAccumulator(accum + decrease);
                } else {
                    ld.setInfectionAccumulator(accum);
                }
            }
        }
    }

    private float getHungerMultiplier(Player player) {
        if (player == null) return 1.0f;

        int food = player.getFoodData().getFoodLevel();
        if (food >= 18) return MedicalConfig.IMMUNE_HUNGER_HIGH_MULT.get().floatValue();
        if (food >= 14) return MedicalConfig.IMMUNE_HUNGER_MID_MULT.get().floatValue();
        if (food >= 8) return MedicalConfig.IMMUNE_HUNGER_LOW_MULT.get().floatValue();
        if (food >= 3) return MedicalConfig.IMMUNE_HUNGER_CRIT_MULT.get().floatValue();
        return MedicalConfig.IMMUNE_HUNGER_STARVE_MULT.get().floatValue();
    }

    private boolean isInWetEnvironment(Player player) {
        if (player == null) return false;
        if (player.isInWaterOrBubble()) {
            return true;
        }
        return player.level().isRainingAt(player.blockPosition());
    }
}
