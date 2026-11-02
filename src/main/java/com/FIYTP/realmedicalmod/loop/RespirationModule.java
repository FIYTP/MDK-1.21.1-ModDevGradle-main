package com.FIYTP.realmedicalmod.loop;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.config.MedicalConfig;

public class RespirationModule implements UpdateModule {

    @Override
    public void process(MedicalData data) {
        // ====== 特殊情况 ======
        if (data.isInVFib() || data.isCardiacArrest()) {
            data.setRespirationRate(0);
            return;
        }

        // 溺水检测：玩家在水下时呼吸频率为 0
        if (data.isUnderwater()) {
            data.setRespirationRate(0);
            return;
        }

        int oxygen = data.getBloodOxygen();
        int runningTimer = data.getRunningTimer();

        // ====== 1. 血氧 → 呼吸频率（代偿机制） ======
        float respiration;

        if (oxygen >= 90) {
            respiration = 12 + (oxygen - 90) / 10 * 4;
        } else if (oxygen >= 80) {
            respiration = 20 + (90 - oxygen) / 10 * 5;
        } else if (oxygen >= 70) {
            respiration = 25 + (80 - oxygen) / 10 * 7;
        } else if (oxygen >= 50) {
            respiration = 32 + (70 - oxygen) / 20 * 8;
        } else if (oxygen >= 30) {
            respiration = 8 + (50 - oxygen) / 20 * 32;
        } else {
            respiration = oxygen / 30 * 6;
        }

        // ====== 2. 奔跑时间 → 呼吸频率（运动积累效应） ======
        if (runningTimer > 0) {
            int maxBonusTime = MedicalConfig.RUNNING_BONUS_MAX_TIME.get();
            float maxBonus = MedicalConfig.MAX_RUNNING_RESPIRATION_BONUS.get().floatValue();
            float runMod = maxBonus * (float) Math.sqrt((float) Math.min(runningTimer, maxBonusTime) / maxBonusTime);
            respiration += runMod;
        }

        // 意识影响
        int consciousness = data.getConsciousness();
        if (consciousness < 20) {
            float consFactor = consciousness / 20.0f;
            respiration = respiration * consFactor;
        }

        // 疼痛影响
        int totalPain = data.getTotalPainLevel();
        if (totalPain > 6) {
            respiration += (totalPain - 6) * 0.5f;
        }

        // 阿片药物抑制
        int opioid = data.getOpioidConcentration();
        if (opioid > 20) {
            float opioidFactor = 1.0f - (opioid / 100.0f) * 0.7f;
            respiration = respiration * Math.max(0.3f, opioidFactor);
        }

        // 毒素呼吸兴奋→浅快呼吸：中度中毒起频率上抬（代偿性，但换气效率已由
        // BloodOxygenModule 的 respEffect 折减覆盖——频率升而效率降，净效应为缺氧）
        float toxin = data.getToxinValue();
        float toxinMidThreshold = MedicalConfig.TOXIN_MID_THRESHOLD.get().floatValue(); // 40
        if (toxin >= toxinMidThreshold) {
            float respRateSlope = MedicalConfig.TOXIN_RESP_RATE_SLOPE.get().floatValue(); // 0.1次/点
            respiration += respRateSlope * (toxin - toxinMidThreshold); // 毒素100 → +6次/分
        }

        // 胸部严重内出血直接扣减
        MedicalData.LimbData chest = data.getLimb(MedicalData.Limb.CHEST);
        if (chest != null && chest.getInternalBleedingRate() > 30 && data.getHemothoraxLevel() < 4) {
            respiration -= 2.0f;
        }

        // ====== 4. 颅内出血等级 3+ 不规则呼吸 ======
        if (data.getIntracranialOnsetTimer() >= MedicalConfig.ONSET_DELAY.get()) {
            int icLevel = data.getIntracranialLevel();
            if (icLevel >= 3) {
                // 等级 4 时中枢受压基线下降
                if (icLevel == 4) {
                    respiration *= 0.6f;
                }
                // 附加 [-4, +4] 次/分随机不规则波动
                float fluctuation = (float) (Math.random() * 8.0 - 4.0);
                respiration += fluctuation;
            }
        }

        // ====== 5. SIRS 呼吸加成（先加 SIRS 代谢需求） ======
        int sirsLevel = data.getSirsLevel();
        if (sirsLevel > 0) {
            respiration += sirsLevel * MedicalConfig.SIRS_RESP_BONUS_PER_LEVEL.get().floatValue();
        }

        // ====== 6. 血胸倍率乘算（在 SIRS 加成之后） ======
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
        respiration = Math.max(0, Math.min(maxResp, respiration));
        data.setRespirationRate(Math.round(respiration));
    }
}