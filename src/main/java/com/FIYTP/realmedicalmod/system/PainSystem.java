package com.FIYTP.realmedicalmod.system;

import com.FIYTP.realmedicalmod.config.MedicalConfig;

public class PainSystem {

    // 根据伤害量计算疼痛等级（0~maxPainLevel）
    public static int calculatePainLevel(float damage) {
        if (damage <= 0) return 0;
        int maxPain = MedicalConfig.MAX_PAIN_LEVEL.get();
        if (damage <= 1.5f) return Math.min(1, maxPain);
        if (damage <= 2.5f) return Math.min(2, maxPain);
        if (damage <= 3.5f) return Math.min(3, maxPain);
        if (damage <= 4.5f) return Math.min(4, maxPain);
        if (damage <= 5.5f) return Math.min(5, maxPain);
        if (damage <= 7.0f) return Math.min(6, maxPain);
        if (damage <= 8.5f) return Math.min(7, maxPain);
        if (damage <= 10.0f) return Math.min(8, maxPain);
        if (damage <= 12.0f) return Math.min(9, maxPain);
        return maxPain;
    }

    // ★ 累加疼痛逻辑 ★
    public static int applyPain(int currentPain, int newPain) {
        int maxPain = MedicalConfig.MAX_PAIN_LEVEL.get();
        if (newPain > currentPain) {
            return Math.min(maxPain, newPain); // 覆盖并受上限限制
        } else {
            // 累加
            int result = (int) Math.ceil(currentPain + newPain * 0.5f); // 四舍五入
            return Math.min(maxPain, result); // 上限
        }
    }
}