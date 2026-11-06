package com.FIYTP.realmedicalmod.loop;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.config.MedicalConfig;
import net.minecraft.util.Mth;

/**
 * 呼吸频率演进模块（集成延髓自主呼吸保底 + 阿片类药物过量呼吸抑制/呼吸骤停 Apnea）
 *
 * 生理与毒理逻辑（所有参数均由 {@link MedicalConfig} 驱动，零硬编码）：
 * 1. 普通创伤昏迷 (consciousness < 20 且未发生阿片严重过量)：
 *    脑干延髓保留 {@code UNCONSCIOUS_MIN_RESPIRATION_FACTOR}（默认 65%）的基础自主呼吸，防止单纯摔伤骨折扣减脑健康。
 * 2. 阿片类药物（吗啡 / 芬太尼）延髓呼吸中枢抑制：
 *    - 阿片类药物直接作用于脑干延髓前包钦格复合体（pre-Bötzinger complex）μ-受体，强行压制呼吸驱动力。
 *    - 当 {@code opioidConcentration >= OPIOID_APNEA_THRESHOLD}（默认 80%）时，触发**中枢性呼吸暂停 (Central Apnea)**，
 *      呼吸频率直接强制归零 ({@code respirationRate = 0})，血氧随之断崖下跌并迅速引发缺氧性脑损伤与室颤！
 *    - 当 {@code opioidConcentration >= OPIOID_RESP_DEPRESSION_START}（默认 25%）时，按非线性曲线将呼吸频率压制至
 *      {@code OPIOID_MIN_DEPRESSED_RESP_FACTOR}，且会穿透普通昏迷的自主呼吸保底。
 */
public class RespirationModule implements UpdateModule {

    @Override
    public void process(MedicalData data) {
        // ====== 特殊情况：心搏停止或溺水时呼吸归零 ======
        if (data.isInVFib() || data.isCardiacArrest()) {
            data.setRespirationRate(0);
            return;
        }

        if (data.isUnderwater()) {
            data.setRespirationRate(0);
            return;
        }

        // ====== 阿片类药物致死性过量：延髓呼吸中枢完全麻痹 (Central Apnea) ======
        int opioid = data.getOpioidConcentration();
        int apneaThreshold = MedicalConfig.OPIOID_APNEA_THRESHOLD.get();
        if (opioid >= apneaThreshold) {
            data.setRespirationRate(0);
            return;
        }

        int oxygen = data.getBloodOxygen();
        int runningTimer = data.getRunningTimer();

        // ====== 1. 血氧 -> 呼吸频率 (化学感受器代偿机制) ======
        float respiration;

        if (oxygen >= 90) {
            respiration = 12 + (oxygen - 90) / 10.0f * 4.0f;
        } else if (oxygen >= 80) {
            respiration = 20 + (90 - oxygen) / 10.0f * 5.0f;
        } else if (oxygen >= 70) {
            respiration = 25 + (80 - oxygen) / 10.0f * 7.0f;
        } else if (oxygen >= 50) {
            respiration = 32 + (70 - oxygen) / 20.0f * 8.0f;
        } else if (oxygen >= 30) {
            respiration = 8 + (50 - oxygen) / 20.0f * 32.0f;
        } else {
            respiration = oxygen / 30.0f * 6.0f;
        }

        // ====== 2. 奔跑时间 -> 呼吸频率 (运动积累效应) ======
        if (runningTimer > 0) {
            int maxBonusTime = MedicalConfig.RUNNING_BONUS_MAX_TIME.get();
            float maxBonus = MedicalConfig.MAX_RUNNING_RESPIRATION_BONUS.get().floatValue();
            float runMod = maxBonus * (float) Math.sqrt((float) Math.min(runningTimer, maxBonusTime) / Math.max(1, maxBonusTime));
            respiration += runMod;
        }

        // ====== 3. 意识影响（保留脑干延髓自主呼吸保底倍率） ======
        int consciousness = data.getConsciousness();
        if (consciousness < 20) {
            float minAutoFactor = MedicalConfig.UNCONSCIOUS_MIN_RESPIRATION_FACTOR.get().floatValue();
            float consFactor = minAutoFactor + (consciousness / 20.0f) * (1.0f - minAutoFactor);
            respiration = respiration * consFactor;
        }

        // 疼痛刺激交感神经引起呼吸浅快（阿片镇痛生效后按体感疼痛自动消退）
        int perceivedPain = data.getPerceivedPain();
        if (perceivedPain > 10 && consciousness > 0) {
            respiration += (perceivedPain - 10) * 0.25f;
        }

        // ====== 4. 阿片类药物中枢呼吸抑制（直接抑制延髓呼吸中枢并削弱低氧化学感受器代偿） ======
        int respDepStart = MedicalConfig.OPIOID_RESP_DEPRESSION_START.get();
        if (opioid >= respDepStart) {
            float minDepFactor = MedicalConfig.OPIOID_MIN_DEPRESSED_RESP_FACTOR.get().floatValue();
            float span = Math.max(1.0f, (float) (apneaThreshold - respDepStart));
            float t = Mth.clamp((opioid - respDepStart) / span, 0.0f, 1.0f);
            // 过量区间呈陡峭加速抑制曲线
            float opioidFactor = Mth.lerp(t * (0.65f + 0.35f * t), 1.0f, minDepFactor);
            respiration = respiration * opioidFactor;
        }

        // 毒素呼吸兴奋->浅快呼吸
        float toxin = data.getToxinValue();
        float toxinMidThreshold = MedicalConfig.TOXIN_MID_THRESHOLD.get().floatValue();
        if (toxin >= toxinMidThreshold) {
            float respRateSlope = MedicalConfig.TOXIN_RESP_RATE_SLOPE.get().floatValue();
            respiration += respRateSlope * (toxin - toxinMidThreshold);
        }

        // 胸部严重内出血直接扣减
        MedicalData.LimbData chest = data.getLimb(MedicalData.Limb.CHEST);
        if (chest != null && chest.getInternalBleedingRate() > 30 && data.getHemothoraxLevel() < 4) {
            respiration -= 2.0f;
        }

        // ====== 5. 颅内出血等级 3+ 脑干受压不规则呼吸 ======
        if (data.getIntracranialOnsetTimer() >= MedicalConfig.ONSET_DELAY.get()) {
            int icLevel = data.getIntracranialLevel();
            if (icLevel >= 3) {
                if (icLevel == 4) {
                    respiration *= 0.6f;
                }
                float fluctuation = (float) (Math.random() * 8.0 - 4.0);
                respiration += fluctuation;
            }
        }

        // ====== 6. SIRS 呼吸加成 ======
        int sirsLevel = data.getSirsLevel();
        if (sirsLevel > 0) {
            respiration += sirsLevel * MedicalConfig.SIRS_RESP_BONUS_PER_LEVEL.get().floatValue();
        }

        // ====== 7. 血胸倍率乘算 ======
        int hemoLevel = data.getHemothoraxLevel();
        if (hemoLevel == 1) {
            respiration *= MedicalConfig.LEVEL1_RESPIRATION_MULT.get().floatValue();
        } else if (hemoLevel == 2) {
            respiration *= MedicalConfig.LEVEL2_RESPIRATION_MULT.get().floatValue();
        } else if (hemoLevel == 3) {
            respiration *= MedicalConfig.LEVEL3_RESPIRATION_MULT.get().floatValue();
        } else if (hemoLevel == 4) {
            respiration *= MedicalConfig.LEVEL4_RESPIRATION_MULT.get().floatValue();
        }

        // ====== 8. 肺挫伤呼吸抑制 ======
        float lungContusion = data.getLungContusion();
        float lungMult = 1.0f;
        if (lungContusion >= 80) lungMult = MedicalConfig.CONTUSION_100_RESPIRATION_MULT.get().floatValue();
        else if (lungContusion >= 60) lungMult = MedicalConfig.CONTUSION_80_RESPIRATION_MULT.get().floatValue();
        else if (lungContusion >= 40) lungMult = MedicalConfig.CONTUSION_60_RESPIRATION_MULT.get().floatValue();
        else if (lungContusion >= 20) lungMult = MedicalConfig.CONTUSION_40_RESPIRATION_MULT.get().floatValue();
        else if (lungContusion > 0) lungMult = MedicalConfig.CONTUSION_20_RESPIRATION_MULT.get().floatValue();
        respiration *= lungMult;

        int maxResp = MedicalConfig.MAX_RESPIRATION.get();
        respiration = Math.max(0.0f, Math.min(maxResp, respiration));
        data.setRespirationRate(Math.round(respiration));
    }
}