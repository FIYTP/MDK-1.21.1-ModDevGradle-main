package com.FIYTP.realmedicalmod.loop;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.capability.MedicalDataImpl;
import com.FIYTP.realmedicalmod.config.MedicalConfig;
import net.minecraft.util.Mth;

/**
 * 意识清醒度演进与阿片类药物中枢代谢模块 (ConsciousnessModule)
 *
 * 核心生理与药理机制（全部参数由 {@link MedicalConfig} 驱动，零硬编码）：
 * 1. 阿片类血药浓度自然代谢：
 *    每秒按 {@code OPIOID_METABOLISM_RATE_PER_SEC} 线性/一级动力学衰减体内阿片浓度。
 * 2. 阿片类药物中枢镇静与过量麻醉昏迷：
 *    - 当 {@code opioidConcentration >= OPIOID_SEDATION_START}（默认 30%）时，产生中枢镇静嗜睡作用；
 *    - 当 {@code opioidConcentration >= OPIOID_OVERDOSE_THRESHOLD}（默认 60%）时，触发重度阿片过量中枢麻痹，
 *      每秒强力扣减意识（{@code OPIOID_OVERDOSE_CONSCIOUSNESS_PENALTY}），迅速将意识压制至 0（深度昏迷，丧失行动与自救能力）！
 * 3. 单纯创伤疼痛意识保底：
 *    无低血氧、无阿片过量、无低血压休克、无颅内出血或血胸时，单纯肢体疼痛最低只将意识扣至 {@code PAIN_CONSCIOUSNESS_FLOOR}。
 */
public class ConsciousnessModule implements UpdateModule {

    private int addictionDecayTick = 0;

    @Override
    public void process(MedicalData data) {
        // ====== 0. 每秒代谢体内阿片血药浓度（肾上腺素衰减已由 ModItems 每秒统一驱动） ======

        float opioidConc = (data instanceof MedicalDataImpl impl)
                ? impl.getOpioidConcentrationFloat()
                : (float) data.getOpioidConcentration();
        if (opioidConc > 0.0f) {
            float decayRate = MedicalConfig.OPIOID_METABOLISM_RATE_PER_SEC.get().floatValue();
            float nextOpioid = Math.max(0.0f, opioidConc - decayRate);
            if (data instanceof MedicalDataImpl impl) {
                impl.setOpioidConcentrationFloat(nextOpioid);
            } else {
                data.setOpioidConcentration(Math.round(nextOpioid));
            }
            opioidConc = nextOpioid;
            addictionDecayTick = 0;
        } else if (data.getAddictionLevel() > 0) {
            int decayInterval = Math.max(5, MedicalConfig.ADDICTION_DECAY_INTERVAL_SEC.get());
            if (++addictionDecayTick >= decayInterval) {
                addictionDecayTick = 0;
                data.setAddictionLevel(Math.max(0, data.getAddictionLevel() - 1));
            }
        }

        // 心脏骤停时，意识直接归零且不再恢复
        if (data.isCardiacArrest()) {
            data.setConsciousnessFloat(0.0f);
            return;
        }

        float currentConsciousness = data.getConsciousnessFloat();
        int perceivedPain = data.getPerceivedPain();
        float oxygen = data.getBloodOxygenFloat();

        float oxygenPenalty;
        if (oxygen >= 90.0f) {
            oxygenPenalty = MedicalConfig.OXYGEN_PENALTY_90.get().floatValue();
        } else if (oxygen >= 80.0f) {
            oxygenPenalty = MedicalConfig.OXYGEN_PENALTY_80.get().floatValue();
        } else if (oxygen >= 70.0f) {
            oxygenPenalty = MedicalConfig.OXYGEN_PENALTY_70.get().floatValue();
        } else if (oxygen >= 50.0f) {
            oxygenPenalty = MedicalConfig.OXYGEN_PENALTY_50.get().floatValue();
        } else if (oxygen >= 30.0f) {
            oxygenPenalty = MedicalConfig.OXYGEN_PENALTY_30.get().floatValue();
        } else {
            oxygenPenalty = MedicalConfig.OXYGEN_PENALTY_CRITICAL.get().floatValue();
        }

        // ★ 体感疼痛惩罚（受 PAIN_CONSCIOUSNESS_FLOOR 下限约束，且阿片镇痛降低 perceivedPain 后自动解除） ★
        float rawPainPenalty = (float) (perceivedPain / MedicalConfig.PAIN_PENALTY_DIVISOR.get());
        float painFloor = MedicalConfig.PAIN_CONSCIOUSNESS_FLOOR.get().floatValue();
        float baseRecovery = MedicalConfig.CONSCIOUSNESS_BASE_RECOVERY.get().floatValue();
        float painPenalty = (currentConsciousness <= painFloor)
                ? Math.min(rawPainPenalty, baseRecovery)
                : rawPainPenalty;

        // ★ 阿片类药物中枢镇静与过量昏迷惩罚 (Opioid Central Narcosis) ★
        float opioidPenalty = 0.0f;
        int sedationStart = MedicalConfig.OPIOID_SEDATION_START.get();
        int overdoseThreshold = MedicalConfig.OPIOID_OVERDOSE_THRESHOLD.get();
        if (opioidConc >= overdoseThreshold) {
            float baseOverdosePen = MedicalConfig.OPIOID_OVERDOSE_CONSCIOUSNESS_PENALTY.get().floatValue();
            float extraRatio = Mth.clamp((opioidConc - overdoseThreshold) / Math.max(1.0f, 100.0f - overdoseThreshold), 0.0f, 1.0f);
            opioidPenalty = baseOverdosePen * (1.0f + extraRatio);
        } else if (opioidConc >= sedationStart) {
            float sedSlope = MedicalConfig.OPIOID_SEDATION_CONSCIOUSNESS_SLOPE.get().floatValue();
            opioidPenalty = (opioidConc - sedationStart) * sedSlope;
        }

        // ★ 毒素直接意识惩罚 (神经毒性) ★
        float toxinPenalty = 0.0f;
        float toxin = data.getToxinValue();
        float toxinMidThreshold = MedicalConfig.TOXIN_MID_THRESHOLD.get().floatValue();
        if (toxin >= toxinMidThreshold) {
            float slope = MedicalConfig.TOXIN_CONSCIOUSNESS_SLOPE.get().floatValue();
            toxinPenalty = slope * (toxin - toxinMidThreshold);
        }

        // 血胸对意识的抑制
        int hemoLevel = data.getHemothoraxLevel();
        float hemothoraxPenalty = switch (hemoLevel) {
            case 1 -> MedicalConfig.LEVEL1_CONSCIOUSNESS_PENALTY.get().floatValue();
            case 2 -> MedicalConfig.LEVEL2_CONSCIOUSNESS_PENALTY.get().floatValue();
            case 3 -> MedicalConfig.LEVEL3_CONSCIOUSNESS_PENALTY.get().floatValue();
            case 4 -> MedicalConfig.LEVEL4_CONSCIOUSNESS_PENALTY.get().floatValue();
            default -> 0.0f;
        };

        // 颅内出血对意识的抑制
        float intracranialPenalty = 0.0f;
        if (data.getIntracranialBleedRate() > 0 || data.getIntracranialAccumulation() > 0) {
            int onsetTimer = data.getIntracranialOnsetTimer();
            if (onsetTimer < MedicalConfig.ONSET_DELAY.get()) {
                intracranialPenalty = 0.5f;
            } else {
                int icLevel = data.getIntracranialLevel();
                if (icLevel == 1) {
                    intracranialPenalty = 1.0f;
                } else if (icLevel == 2) {
                    intracranialPenalty = 2.0f;
                } else if (icLevel == 3) {
                    intracranialPenalty = 3.5f;
                } else if (icLevel >= 4) {
                    intracranialPenalty = 5.0f;
                }
            }
        }

        // 血压对意识的影响
        float bpPenalty = 0.0f;
        float map = data.getMeanArterialPressure();
        int sbp = data.getSystolicBP();

        if (map < 70) {
            if (map >= 60) bpPenalty += MedicalConfig.CONS_PENALTY_MAP_60.get().floatValue();
            else if (map >= 50) bpPenalty += MedicalConfig.CONS_PENALTY_MAP_50.get().floatValue();
            else if (map >= 40) bpPenalty += MedicalConfig.CONS_PENALTY_MAP_40.get().floatValue();
            else bpPenalty += MedicalConfig.CONS_PENALTY_MAP_BELOW_40.get().floatValue();
        }

        if (sbp >= 180) {
            if (sbp < 200) bpPenalty += MedicalConfig.CONS_PENALTY_SBP_180.get().floatValue();
            else if (sbp < 220) bpPenalty += MedicalConfig.CONS_PENALTY_SBP_200.get().floatValue();
            else bpPenalty += MedicalConfig.CONS_PENALTY_SBP_220.get().floatValue();
        }

        float adrBonus = (data instanceof MedicalDataImpl aImpl && aImpl.getAdrenalineLevelFloat() > 0.0f)
                ? (aImpl.getAdrenalineLevelFloat() / 100.0f) * 1.8f
                : 0.0f;
        float change = baseRecovery + adrBonus - painPenalty - oxygenPenalty - hemothoraxPenalty - intracranialPenalty - bpPenalty - toxinPenalty - opioidPenalty;

        // 若当前高于痛觉保底线，而扣减完全是由疼痛引起（无缺氧/阿片过量/低血压/颅内出血/血胸/中毒），则最低扣至 painFloor
        float nonPainPenalties = oxygenPenalty + hemothoraxPenalty + intracranialPenalty + bpPenalty + toxinPenalty + opioidPenalty;
        float nextConsciousness = currentConsciousness + change;
        if (nonPainPenalties <= 0.001f && currentConsciousness >= painFloor && nextConsciousness < painFloor) {
            nextConsciousness = painFloor;
        }

        data.setConsciousnessFloat(Math.max(0.0f, Math.min(100.0f, nextConsciousness)));
    }
}