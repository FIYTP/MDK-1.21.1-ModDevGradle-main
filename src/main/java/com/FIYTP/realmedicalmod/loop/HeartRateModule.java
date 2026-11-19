package com.FIYTP.realmedicalmod.loop;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.treatment.ModItems;
import com.FIYTP.realmedicalmod.config.MedicalConfig;
import net.minecraft.util.Mth;

/**
 * 心率动态演进模块（集成阿片镇痛平抑交感心动过速 + 过量迷走神经心动过缓）
 */
public class HeartRateModule implements UpdateModule {

    @Override
    public void process(MedicalData data) {
        if (data.getBloodVolume() <= 0) {
            data.setVFib(false);
            data.setCardiacArrest(true);
            data.setHeartRate(0);
            return;
        }
        if (data.isCardiacArrest()) {
            data.setHeartRate(0);
            return;
        }
        if (data.isInVFib()) {
            data.setHeartRate(-1);
            return;
        }

        int bloodVol = data.getBloodVolume();
        int maxBlood = data.getMaxBloodVolume();
        float ratio = (float) bloodVol / maxBlood;

        float bloodMod;
        if (ratio > 0.15f) {
            float normalRange = (0.75f - Math.min(ratio, 0.75f)) / 0.60f;
            bloodMod = 10 + normalRange * 60;
            if (ratio > 0.75f) {
                bloodMod = 10;
            }
        } else {
            float crash = (0.15f - ratio) / 0.15f;
            bloodMod = 70 - crash * 70;
            if (ratio < 0.02f) {
                data.setCardiacArrest(true);
                data.setHeartRate(0);
                return;
            }
        }

        // ★ 疼痛交感神经修正（严格基于各部位实际总痛感 totalPain，避免单腿摔伤被放大导致室颤） ★
        int totalPain = data.getTotalPainLevel();
        int perceivedPain = data.getPerceivedPain();
        int opioid = data.getOpioidConcentration();
        float maxPerceivedPain = Math.max(10.0f, MedicalConfig.MAX_PERCEIVED_PAIN.get().floatValue());
        float painRatio = Mth.clamp(perceivedPain / maxPerceivedPain, 0.0f, 1.0f);

        float effectivePain = (float) totalPain;
        if (opioid > 0) {
            float fullConc = Math.max(1.0f, MedicalConfig.OPIOID_ANALGESIA_FULL_CONCENTRATION.get().floatValue());
            float blockMax = MedicalConfig.OPIOID_PAIN_TRANSMISSION_BLOCK.get().floatValue();
            float block = Mth.clamp(opioid / fullConc, 0.0f, 1.0f) * blockMax;
            effectivePain *= Math.max(0.0f, 1.0f - block);
        }
        float painMult = MedicalConfig.PAIN_HEART_RATE_MULTIPLIER.get().floatValue();
        float maxPainHrBonus = MedicalConfig.MAX_PAIN_LEVEL.get().floatValue() * 2.6f * painMult;
        float painMod = Math.min(maxPainHrBonus, effectivePain * painMult);

        // ★ 意识修正（当存在剧痛交感风暴时，自主交感兴奋会抵消无痛昏迷的迷走降心率效应） ★
        int consciousness = data.getConsciousness();
        float consciousnessMod;
        if (consciousness >= 80) {
            consciousnessMod = 0;
        } else if (consciousness >= 50) {
            consciousnessMod = -(80 - consciousness) * 0.5f;
        } else if (consciousness > 0) {
            consciousnessMod = -(80 - consciousness) * 0.8f;
        } else {
            consciousnessMod = -70;
        }
        consciousnessMod *= (1.0f - painRatio);

        // 运动贡献
        float exerciseMod = data.getExerciseContribution();

        // ★ 阿片类药物迷走神经兴奋与过量心动过缓（全部系数读取自 MedicalConfig） ★
        float opioidSlope = MedicalConfig.OPIOID_HEART_RATE_SLOPE.get().floatValue();
        float opioidMod = -opioid * opioidSlope;
        int overdoseThreshold = MedicalConfig.OPIOID_OVERDOSE_THRESHOLD.get();
        if (opioid >= overdoseThreshold) {
            float overdoseBrady = MedicalConfig.OPIOID_OVERDOSE_BRADYCARDIA_PENALTY.get().floatValue();
            float odRatio = Mth.clamp((opioid - overdoseThreshold) / Math.max(1.0f, 100.0f - overdoseThreshold), 0.0f, 1.0f);
            opioidMod -= overdoseBrady * (0.5f + 0.5f * odRatio);
        }

        // ★ 呼吸频率对心率的影响 ★
        int respRate = data.getRespirationRate();
        float respMod = 0;
        if (respRate <= 0) {
            respMod = MedicalConfig.RESP_MOD_STOP.get().floatValue();
        } else if (respRate < 8) {
            respMod = MedicalConfig.RESP_MOD_SLOW.get().floatValue();
        } else if (respRate <= 20) {
            respMod = 0;
        } else if (respRate <= 30) {
            respMod = (respRate - 20) * 1.0f;
        } else if (respRate <= 45) {
            respMod = 10 + (respRate - 30) * 1.5f;
        } else {
            respMod = 32.5f + (respRate - 45) * 2.0f;
        }

        // ★ 颅内出血库欣反应 ★
        float cushingMod = 0;
        if (data.getIntracranialOnsetTimer() >= MedicalConfig.ONSET_DELAY.get()) {
            int icLevel = data.getIntracranialLevel();
            if (icLevel == 2) {
                cushingMod = 15.0f;
            } else if (icLevel == 3) {
                cushingMod = 25.0f;
            } else if (icLevel == 4) {
                cushingMod = -40.0f;
            }
        }

        // ★ 血压反射代偿 ★
        float bpReflexMod = 0;
        float map = data.getMeanArterialPressure();
        float hypotensionThreshold = MedicalConfig.MAP_HYPOTENSION.get().floatValue();
        if (map < hypotensionThreshold) {
            bpReflexMod += (hypotensionThreshold - map) * 0.5f;
        }
        if (map > 120.0f) {
            bpReflexMod -= (map - 120.0f) * 0.3f;
        }

        // ★ SIRS 炎症因子交感神经刺激加成 ★
        float sirsMod = 0;
        int sirsLevel = data.getSirsLevel();
        if (sirsLevel > 0) {
            sirsMod = sirsLevel * MedicalConfig.SIRS_HEART_BONUS_PER_LEVEL.get().floatValue();
        }

        // ★ 血胸交感代偿加成 ★
        int hemoLevel = data.getHemothoraxLevel();
        float hemoHeartMod = switch (hemoLevel) {
            case 1 -> MedicalConfig.LEVEL1_HEART_RATE_BONUS.get().floatValue();
            case 2 -> MedicalConfig.LEVEL2_HEART_RATE_BONUS.get().floatValue();
            case 3 -> MedicalConfig.LEVEL3_HEART_RATE_BONUS.get().floatValue();
            case 4 -> MedicalConfig.LEVEL4_HEART_RATE_BONUS.get().floatValue();
            default -> 0.0f;
        };

        // 张力性气胸（梗阻性休克）交感代偿心动过速：按胸膜腔正压与配置强度联动
        float pneumoHeartMod = 0.0f;
        if (ModItems.getPneumothoraxType(data) == 3) {
            float pleuralP = Math.max(0.0f, ModItems.getPleuralPressureCmH2O(data));
            float tensionBpDrop = MedicalConfig.PNEUMOTHORAX_TENSION_BP_DROP_PER_SEC.get();
            pneumoHeartMod = Math.min(55.0f, (12.0f + pleuralP * 1.4f) * Math.max(0.5f, tensionBpDrop / 2.0f));
        }

        float baseHr = MedicalConfig.RESTING_HEART_RATE.get() + bloodMod + painMod + consciousnessMod + exerciseMod + opioidMod + respMod + cushingMod + bpReflexMod + sirsMod + hemoHeartMod + pneumoHeartMod;

        // ★ 肾上腺素心率修正：受伤内源性应激绝不推高心率致室颤；单支注射器安全封顶，仅多支过量(III级)可冲入室颤区 ★
        float adrLevel = ModItems.getAdrenalineLevel(data);
        boolean fromInj = (data instanceof com.FIYTP.realmedicalmod.capability.MedicalDataImpl impl) && impl.isAdrenalineFromInjector();
        float adrenalineMod = 0.0f;
        if (adrLevel > 0.0f) {
            float maxHrBoostCfg = MedicalConfig.ADRENALINE_MAX_HR_BOOST.get().floatValue();
            float tier1Pct = MedicalConfig.ADRENALINE_TIER1_THRESHOLD.get().floatValue() / 100.0f;
            float tier2Pct = MedicalConfig.ADRENALINE_TIER2_THRESHOLD.get().floatValue() / 100.0f;
            if (!fromInj) {
                float traumaHrCeiling = MedicalConfig.RESTING_HEART_RATE.get().floatValue() + maxHrBoostCfg * 1.25f;
                float traumaMaxBoost = maxHrBoostCfg * tier1Pct;
                if (baseHr < traumaHrCeiling) {
                    adrenalineMod = Math.min(traumaMaxBoost, traumaHrCeiling - baseHr);
                }
            } else {
                int adrTier = ModItems.getAdrenalineTier(adrLevel);
                int vfibMargin = MedicalConfig.ADRENALINE_TIER1_THRESHOLD.get();
                int vfibSafeCap = Math.max(140, MedicalConfig.VFIB_THRESHOLD.get() - vfibMargin);
                if (adrTier >= 3) {
                    adrenalineMod = (adrLevel / 100.0f) * maxHrBoostCfg;
                } else {
                    float singleBoost = maxHrBoostCfg * tier2Pct;
                    float rawBoost = (adrLevel / Math.max(1.0f, MedicalConfig.ADRENALINE_INJECTOR_GAIN.get().floatValue())) * singleBoost;
                    if (baseHr < vfibSafeCap) {
                        adrenalineMod = Math.min(rawBoost, vfibSafeCap - baseHr);
                    }
                }
            }
        }

        float heartRate = baseHr + adrenalineMod;

        if (heartRate <= 0) {
            data.setCardiacArrest(true);
            data.setHeartRate(0);
            return;
        }
        int maxHr = MedicalConfig.MAX_HEART_RATE.get();
        if (heartRate > maxHr) heartRate = maxHr;

        data.setHeartRate(Math.round(heartRate));
    }
}