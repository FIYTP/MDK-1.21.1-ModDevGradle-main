package com.FIYTP.realmedicalmod.loop;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.config.MedicalConfig;

/**
 * 自然凝血止血模块 (NaturalClottingModule)
 * 模拟现实中中小静脉及微小创口在无外力干预下血液自然凝固止血的生理过程。
 */
public class NaturalClottingModule implements UpdateModule {

    @Override
    public void process(MedicalData data) {
        int threshold = MedicalConfig.NATURAL_CLOTTING_THRESHOLD.get();
        for (MedicalData.Limb limb : MedicalData.Limb.values()) {
            MedicalData.LimbData ld = data.getLimb(limb);
            int rate = ld.getBleeding();

            // 若外出血已停止且无开放性骨折，开放性伤口闭合并重置计时器
            if (rate <= 0 && !ld.isOpenFracture()) {
                if (ld.hasOpenWound()) {
                    ld.setOpenWound(false);
                    ld.setInfectionAgeTimer(0);
                }
            }

            // 计时器重置条件：
            // 1. 出血速率 > threshold (创口过大无法自愈)
            // 2. 出血速率 <= 0 (无出血)
            // 3. 已包扎 (isBandaged) 或已使用止血带 (isTourniqueted)
            if (rate <= 0 || rate > threshold || ld.isBandaged() || ld.isTourniqueted()) {
                if (ld.getNaturalClottingTimer() != 0) {
                    ld.setNaturalClottingTimer(0);
                }
                if (ld.getClottingStartRate() != 0) {
                    ld.setClottingStartRate(0);
                }
                continue;
            }

            int currentTimer = ld.getNaturalClottingTimer() + 1;

            // requiredTime 以凝血起始速率计算并冻结：
            // 若每次用当前速率重算，速率被本模块递减后 requiredTime 随之缩短，
            // 形成反馈回路（曲线变凸、总时长远小于配置上限）
            int requiredTime = (ld.getClottingStartRate() > 0 && currentTimer > 1)
                    ? calculateRequiredClottingTime(ld.getClottingStartRate())
                    : calculateRequiredClottingTime(rate);

            // 毒素诱发消耗性凝血病：重度中毒凝血时间显著延长（默认 ×1.5）
            // 注意：只延长"何时算完成"的时限，不重置已推进的计时，避免反馈回路
            float toxin = data.getToxinValue();
            float toxinSevereThreshold = MedicalConfig.TOXIN_SEVERE_THRESHOLD.get().floatValue(); // 70
            if (toxin >= toxinSevereThreshold) {
                requiredTime = Math.round(requiredTime * MedicalConfig.TOXIN_CLOTTING_TIME_MULT.get().floatValue());
            }

            // 烧伤凝血紊乱：TBSA>30%（浅II及以上计入）时凝血时间按斜率延长
            // （大面积烧伤早期高凝、后期消耗性低凝；同"只延长不重置"原则）
            float burnTBSA = data.getBurnTBSA();
            float coagulopathyThreshold = MedicalConfig.BURN_TBSA_COAGULOPATHY_THRESHOLD.get().floatValue();
            if (burnTBSA > coagulopathyThreshold) {
                float mult = 1.0f + (burnTBSA - coagulopathyThreshold) * MedicalConfig.BURN_CLOTTING_SLOPE.get().floatValue();
                requiredTime = Math.round(requiredTime * mult);
            }

            if (currentTimer >= requiredTime) {
                // 凝血完成，外出血停止，计时器归零
                ld.setBleeding(0);
                ld.setNaturalClottingTimer(0);
                ld.setClottingStartRate(0);
                if (!ld.isOpenFracture()) {
                    ld.setOpenWound(false);
                    ld.setInfectionAgeTimer(0);
                }
            } else {
                if (currentTimer == 1) {
                    // 凝血开始：记录起始速率，供后续线性递减
                    ld.setClottingStartRate(rate);
                } else if (ld.getClottingStartRate() > 0 && rate > ld.getClottingStartRate()) {
                    // 凝血期间速率升高（新伤/继发出血）：按新速率重新凝血
                    ld.setNaturalClottingTimer(0);
                    ld.setClottingStartRate(rate);
                    continue;
                }
                // 凝血推进：出血速率从起始值线性递减，恰好计时结束时归零
                // 只降不升：外部模块若临时降低了速率，不允许被插值曲线顶回
                if (currentTimer > 1 && ld.getClottingStartRate() > 0) {
                    int remaining = Math.max(0, Math.round(
                            ld.getClottingStartRate() * (float) (requiredTime - currentTimer) / requiredTime));
                    if (remaining < rate) {
                        ld.setBleeding(remaining);
                    }
                }
                ld.setNaturalClottingTimer(currentTimer);
            }
        }
    }

    /**
     * 计算自然止血所需时间（秒）
     * 依据配置的阈值与最小/最大时间进行线性插值
     */
    public static int calculateRequiredClottingTime(int rate) {
        int threshold = MedicalConfig.NATURAL_CLOTTING_THRESHOLD.get();
        int minTime = MedicalConfig.NATURAL_CLOTTING_MIN_TIME.get();
        int maxTime = MedicalConfig.NATURAL_CLOTTING_MAX_TIME.get();
        if (rate <= 1) return minTime;
        if (rate >= threshold) return maxTime;
        float progress = (float) (rate - 1) / Math.max(1, threshold - 1);
        return minTime + Math.round(progress * (maxTime - minTime));
    }
}
