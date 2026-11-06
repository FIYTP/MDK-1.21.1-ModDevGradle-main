package com.FIYTP.realmedicalmod.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 【MedicalConfig.java 阿片类药物系统（吗啡 & 芬太尼）配置项补丁说明】
 *
 * 请将下方【第一部分：静态字段声明】粘贴到 MedicalConfig.java 类的顶部字段区，
 * 并将【第二部分：static 块 builder 初始化代码】粘贴到 MedicalConfig.java 的 static { ... } 块内（SPEC = builder.build(); 之前）。
 */
public class MedicalConfig_OpioidPatch {

    // =========================================================================
    // 【第一部分：粘贴到 MedicalConfig.java 的静态字段声明区】
    // =========================================================================
    // ==================== 阿片类药物系统 (opioid) ====================
    public static ModConfigSpec.IntValue MORPHINE_MAX_DOSE_UNITS;
    public static ModConfigSpec.DoubleValue MORPHINE_MAX_MG;
    public static ModConfigSpec.DoubleValue MORPHINE_TARGET_PAIN_RELIEF;
    public static ModConfigSpec.DoubleValue MORPHINE_SYSTEMIC_PAIN_RELIEF;
    public static ModConfigSpec.DoubleValue MORPHINE_CONCENTRATION_PER_FULL_DOSE;
    public static ModConfigSpec.DoubleValue MORPHINE_ADDICTION_GAIN;

    public static ModConfigSpec.IntValue FENTANYL_MAX_DOSE_UNITS;
    public static ModConfigSpec.DoubleValue FENTANYL_MAX_MCG;
    public static ModConfigSpec.DoubleValue FENTANYL_TARGET_PAIN_RELIEF;
    public static ModConfigSpec.DoubleValue FENTANYL_SYSTEMIC_PAIN_RELIEF;
    public static ModConfigSpec.DoubleValue FENTANYL_CONCENTRATION_PER_FULL_DOSE;
    public static ModConfigSpec.DoubleValue FENTANYL_ADDICTION_GAIN;

    public static ModConfigSpec.DoubleValue OPIOID_ANALGESIA_FULL_CONCENTRATION;
    public static ModConfigSpec.DoubleValue OPIOID_CENTRAL_ANALGESIA_MAX_REDUCTION;
    public static ModConfigSpec.DoubleValue OPIOID_ANALGESIA_CURVE_EXPONENT;
    public static ModConfigSpec.DoubleValue OPIOID_PAIN_TRANSMISSION_BLOCK;
    public static ModConfigSpec.DoubleValue OPIOID_METABOLISM_RATE_PER_SEC;

    public static ModConfigSpec.IntValue OPIOID_RESP_DEPRESSION_START;
    public static ModConfigSpec.IntValue OPIOID_SEDATION_START;
    public static ModConfigSpec.IntValue OPIOID_OVERDOSE_THRESHOLD;
    public static ModConfigSpec.IntValue OPIOID_APNEA_THRESHOLD;
    public static ModConfigSpec.DoubleValue OPIOID_MIN_DEPRESSED_RESP_FACTOR;
    public static ModConfigSpec.DoubleValue OPIOID_SEDATION_CONSCIOUSNESS_SLOPE;
    public static ModConfigSpec.DoubleValue OPIOID_OVERDOSE_CONSCIOUSNESS_PENALTY;
    public static ModConfigSpec.DoubleValue OPIOID_ACUTE_OVERDOSE_CONSCIOUSNESS_DROP;
    public static ModConfigSpec.DoubleValue OPIOID_HEART_RATE_SLOPE;
    public static ModConfigSpec.DoubleValue OPIOID_OVERDOSE_BRADYCARDIA_PENALTY;

    // =========================================================================
    // 【第二部分：粘贴到 MedicalConfig.java 的 static { ... } 块内，在 SPEC = builder.build(); 之前】
    // =========================================================================
    public static void initOpioidConfig(ModConfigSpec.Builder builder) {
        builder.comment("阿片类强效镇痛与过量呼吸抑制系统配置 (盐酸吗啡 & 枸橼酸芬太尼)").push("opioid");

        // 1. 盐酸吗啡 (Morphine Sulfate)
        MORPHINE_MAX_DOSE_UNITS = builder
                .comment("单支盐酸吗啡自动注射器的最大刻度耐久单位 (默认 100 单位 = 100%)")
                .defineInRange("morphineMaxDoseUnits", 100, 10, 1000);
        MORPHINE_MAX_MG = builder
                .comment("单支盐酸吗啡自动注射器对应的标准毫克数 (mg，默认 10.0 mg)")
                .defineInRange("morphineMaxMg", 10.0, 1.0, 100.0);
        MORPHINE_TARGET_PAIN_RELIEF = builder
                .comment("吗啡全支 (100% 剂量) 推注时对目标注射肢体直接消除的疼痛等级 (默认 8.0)")
                .defineInRange("morphineTargetPainRelief", 8.0, 0.0, 20.0);
        MORPHINE_SYSTEMIC_PAIN_RELIEF = builder
                .comment("吗啡全支 (100% 剂量) 推注时经血液循环对全身其余肢体协同消除的疼痛等级 (默认 4.0)")
                .defineInRange("morphineSystemicPainRelief", 4.0, 0.0, 20.0);
        MORPHINE_CONCENTRATION_PER_FULL_DOSE = builder
                .comment("吗啡全支 (100% 剂量) 推注增加的体内阿片血药浓度百分比 (默认 50.0%，单支半量 25% 处于安全窗，连打两支过量)")
                .defineInRange("morphineConcentrationPerFullDose", 50.0, 5.0, 200.0);
        MORPHINE_ADDICTION_GAIN = builder
                .comment("吗啡全支推注增加的成瘾值 (默认 12.0)")
                .defineInRange("morphineAddictionGain", 12.0, 0.0, 100.0);

        // 2. 枸橼酸芬太尼 (Fentanyl Citrate)
        FENTANYL_MAX_DOSE_UNITS = builder
                .comment("单支枸橼酸芬太尼微量注射器的最大刻度耐久单位 (默认 100 单位 = 100%)")
                .defineInRange("fentanylMaxDoseUnits", 100, 10, 1000);
        FENTANYL_MAX_MCG = builder
                .comment("单支枸橼酸芬太尼微量注射器对应的标准微克数 (mcg，默认 100.0 mcg)")
                .defineInRange("fentanylMaxMcg", 100.0, 10.0, 1000.0);
        FENTANYL_TARGET_PAIN_RELIEF = builder
                .comment("芬太尼全支 (100% 剂量) 推注时对目标部位直接消除的疼痛等级 (默认 10.0)")
                .defineInRange("fentanylTargetPainRelief", 10.0, 0.0, 20.0);
        FENTANYL_SYSTEMIC_PAIN_RELIEF = builder
                .comment("芬太尼全支 (100% 剂量) 推注时对全身各部位协同消除的疼痛等级 (默认 8.0)")
                .defineInRange("fentanylSystemicPainRelief", 8.0, 0.0, 20.0);
        FENTANYL_CONCENTRATION_PER_FULL_DOSE = builder
                .comment("芬太尼全支 (100% 剂量) 推注增加的体内阿片血药浓度百分比 (默认 115.0%！治疗窗极窄：推注 25%~40% 即可强效镇痛；单支推注 >= 70% 即刻触发 >=80% 浓度的延髓呼吸骤停!)")
                .defineInRange("fentanylConcentrationPerFullDose", 115.0, 10.0, 300.0);
        FENTANYL_ADDICTION_GAIN = builder
                .comment("芬太尼全支推注增加的成瘾值 (默认 28.0)")
                .defineInRange("fentanylAddictionGain", 28.0, 0.0, 100.0);

        // 3. 中枢 μ-阿片受体镇痛与代谢动力学
        OPIOID_ANALGESIA_FULL_CONCENTRATION = builder
                .comment("达到中枢镇痛饱和所需的阿片血药浓度百分比 (默认 50.0%)")
                .defineInRange("opioidAnalgesiaFullConcentration", 50.0, 5.0, 100.0);
        OPIOID_CENTRAL_ANALGESIA_MAX_REDUCTION = builder
                .comment("中枢阿片镇痛饱和时最大可压制的体感疼痛点数 (默认 100.0)")
                .defineInRange("opioidCentralAnalgesiaMaxReduction", 100.0, 0.0, 200.0);
        OPIOID_ANALGESIA_CURVE_EXPONENT = builder
                .comment("中枢阿片镇痛响应曲线指数 (<1.0 表示低中剂量即可快速起效镇痛，默认 0.75)")
                .defineInRange("opioidAnalgesiaCurveExponent", 0.75, 0.2, 3.0);
        OPIOID_PAIN_TRANSMISSION_BLOCK = builder
                .comment("饱和阿片浓度下阻断脊髓痛觉上行传导的最大比例 (默认 0.92 即阻断 92% 痛感)")
                .defineInRange("opioidPainTransmissionBlock", 0.92, 0.0, 1.0);
        OPIOID_METABOLISM_RATE_PER_SEC = builder
                .comment("体内阿片血药浓度每秒自然代谢清除速率 (%/s，默认 0.45)")
                .defineInRange("opioidMetabolismRatePerSec", 0.45, 0.01, 10.0);

        // 4. 延髓呼吸抑制、中枢昏迷与过量毒理阈值
        OPIOID_RESP_DEPRESSION_START = builder
                .comment("开始出现延髓呼吸抑制的阿片血药浓度阈值 (% ，默认 25)")
                .defineInRange("opioidRespDepressionStart", 25, 1, 100);
        OPIOID_SEDATION_START = builder
                .comment("开始出现中枢镇静嗜睡与针尖样瞳孔视野缩窄的阿片血药浓度阈值 (% ，默认 30)")
                .defineInRange("opioidSedationStart", 30, 1, 100);
        OPIOID_OVERDOSE_THRESHOLD = builder
                .comment("触发急性阿片中毒过量 (重度呼吸抑制 + 深度昏迷 + 迷走心动过缓) 的血药浓度阈值 (% ，默认 60)")
                .defineInRange("opioidOverdoseThreshold", 60, 10, 100);
        OPIOID_APNEA_THRESHOLD = builder
                .comment("触发致死性延髓呼吸麻痹 (中枢性呼吸暂停 Central Apnea，呼吸频率直接归零) 的血药浓度阈值 (% ，默认 80)")
                .defineInRange("opioidApneaThreshold", 80, 20, 100);
        OPIOID_MIN_DEPRESSED_RESP_FACTOR = builder
                .comment("阿片呼吸抑制在达到呼吸暂停阈值前可将呼吸频率压制到的最低倍率 (默认 0.12)")
                .defineInRange("opioidMinDepressedRespFactor", 0.12, 0.0, 1.0);
        OPIOID_SEDATION_CONSCIOUSNESS_SLOPE = builder
                .comment("镇静区间 (30%~60%) 每超 1% 浓度每秒扣减意识的斜率 (默认 0.06)")
                .defineInRange("opioidSedationConsciousnessSlope", 0.06, 0.0, 2.0);
        OPIOID_OVERDOSE_CONSCIOUSNESS_PENALTY = builder
                .comment("过量区间 (>=60%) 每秒基础扣减意识点数 (默认 4.5，随浓度升高翻倍，迅速导致深度昏迷)")
                .defineInRange("opioidOverdoseConsciousnessPenalty", 4.5, 0.5, 50.0);
        OPIOID_ACUTE_OVERDOSE_CONSCIOUSNESS_DROP = builder
                .comment("单次推注直接跨入 >=80% 致命过量区时瞬间扣除的急性意识冲击值 (默认 35.0)")
                .defineInRange("opioidAcuteOverdoseConsciousnessDrop", 35.0, 0.0, 100.0);
        OPIOID_HEART_RATE_SLOPE = builder
                .comment("阿片血药浓度每 1% 对心率的迷走神经抑制斜率 (默认 0.45 BPM/%)")
                .defineInRange("opioidHeartRateSlope", 0.45, 0.0, 5.0);
        OPIOID_OVERDOSE_BRADYCARDIA_PENALTY = builder
                .comment("阿片过量 (>=60%) 时额外叠加的迷走神经心动过缓心率扣减值 (默认 28.0 BPM)")
                .defineInRange("opioidOverdoseBradycardiaPenalty", 28.0, 0.0, 100.0);

        builder.pop();
    }
}