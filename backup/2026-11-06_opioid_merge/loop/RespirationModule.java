package com.FIYTP.realmedicalmod.loop;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.config.MedicalConfig;

/**
 * 呼吸频率演进模块（符合现实生理学的延髓自主呼吸修正版）
 *
 * 根因修复说明：
 * 原版代码在 consciousness < 20 时直接乘以 (consciousness / 20.0f)，导致当玩家因剧痛或失血陷入昏迷 (consciousness == 0) 时，
 * 呼吸频率被强制乘 0 归零，进而触发 BloodOxygenModule 的 -5%/s 窒息断崖下跌并迅速扣光脑健康 (brainHealth)。
 *
 * 现实生理逻辑：
 * 人体自主呼吸由脑干（延髓呼吸中枢）调控，而非大脑皮层意识控制。
 * 单纯昏迷/失去意识 (consciousness == 0) 时，只要未发生心脏骤停、室颤、溺水或 IV 级脑疝，
 * 延髓仍会维持基础自主呼吸（由 MedicalConfig.UNCONSCIOUS_MIN_RESPIRATION_FACTOR 配置，默认保留 65% 基础呼吸频率），
 * 绝不会因为昏迷而直接停止呼吸导致脑死亡。
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

        // ====== 3. 意识影响（保留脑干延髓自主呼吸保底倍率，防止昏迷直接停呼吸扣脑健康） ======
        int consciousness = data.getConsciousness();
        if (consciousness < 20) {
            float minAutoFactor = MedicalConfig.UNCONSCIOUS_MIN_RESPIRATION_FACTOR.get().floatValue();
            float consFactor = minAutoFactor + (consciousness / 20.0f) * (1.0f - minAutoFactor);
            respiration = respiration * consFactor;
        }

        // 疼痛刺激交感神经引起呼吸浅快（仅在清醒或浅昏迷时有效）
        int totalPain = data.getTotalPainLevel();
        if (totalPain > 6 && consciousness > 0) {
            respiration += (totalPain - 6) * 0.5f;
        }

        // 阿片药物中枢呼吸抑制
        int opioid = data.getOpioidConcentration();
        if (opioid > 20) {
            float opioidFactor = 1.0f - (opioid / 100.0f) * 0.7f;
            respiration = respiration * Math.max(0.3f, opioidFactor);
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

        // ====== 4. 颅内出血等级 3+ 脑干受压不规则呼吸 ======
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

        // ====== 5. SIRS 呼吸加成 ======
        int sirsLevel = data.getSirsLevel();
        if (sirsLevel > 0) {
            respiration += sirsLevel * MedicalConfig.SIRS_RESP_BONUS_PER_LEVEL.get().floatValue();
        }

        // ====== 6. 血胸倍率乘算 ======
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

        // ====== 7. 肺挫伤呼吸抑制 ======
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