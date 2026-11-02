package com.FIYTP.realmedicalmod.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 真实医疗系统 - 集中数值配置类
 * 允许整合包作者与玩家通过 config/realmedicalmod-common.toml 平衡各项生理数值与表现
 */
public class MedicalConfig {
    public static final ModConfigSpec SPEC;

    // ================= 出血 (bleeding) =================
    public static final ModConfigSpec.DoubleValue BLEED_TO_BLOOD_RATIO;
    public static final ModConfigSpec.IntValue NATURAL_CLOTTING_THRESHOLD;
    public static final ModConfigSpec.IntValue NATURAL_CLOTTING_MIN_TIME;
    public static final ModConfigSpec.IntValue NATURAL_CLOTTING_MAX_TIME;

    // ================= 血容量回升 (blood_regen) =================
    public static final ModConfigSpec.BooleanValue BLOOD_VOLUME_REGEN_ENABLED;
    public static final ModConfigSpec.DoubleValue BLOOD_REGEN_RATE;
    public static final ModConfigSpec.IntValue BLOOD_REGEN_FOOD_THRESHOLD;

    // ================= 血氧 (blood_oxygen) =================
    public static final ModConfigSpec.IntValue CARDIAC_ARREST_OXYGEN_LOSS;
    public static final ModConfigSpec.IntValue VFIB_OXYGEN_LOSS;
    public static final ModConfigSpec.DoubleValue OXYGEN_CHANGE_RATIO_70;
    public static final ModConfigSpec.DoubleValue OXYGEN_CHANGE_RATIO_50;
    public static final ModConfigSpec.DoubleValue OXYGEN_CHANGE_RATIO_30;
    public static final ModConfigSpec.DoubleValue OXYGEN_CHANGE_RATIO_10;
    public static final ModConfigSpec.DoubleValue OXYGEN_CHANGE_RATIO_LOW;
    public static final ModConfigSpec.DoubleValue OXYGEN_LOSS_RESP_STOP_HIGH;
    public static final ModConfigSpec.DoubleValue OXYGEN_LOSS_RESP_STOP_MID;
    public static final ModConfigSpec.DoubleValue OXYGEN_LOSS_RESP_STOP_LOW;
    public static final ModConfigSpec.DoubleValue OXYGEN_LOSS_RESP_STOP_CRITICAL;
    public static final ModConfigSpec.DoubleValue OXYGEN_RECOVERY_RATE_HIGH;
    public static final ModConfigSpec.DoubleValue OXYGEN_RECOVERY_RATE_MID;
    public static final ModConfigSpec.DoubleValue OXYGEN_RECOVERY_RATE_LOW;
    public static final ModConfigSpec.DoubleValue OXYGEN_RECOVERY_RATE_CRIT;
    public static final ModConfigSpec.DoubleValue RESPIRATION_EFFECT_PER_RATE;
    public static final ModConfigSpec.DoubleValue RESPIRATION_BASELINE;
    public static final ModConfigSpec.DoubleValue RESPIRATION_STOP_EFFECT;
    public static final ModConfigSpec.IntValue UNDERWATER_BREATH_HOLD_TIME;
    public static final ModConfigSpec.DoubleValue BREATH_HOLD_OXYGEN_LOSS;

    // ================= 脑健康 (brain_health) =================
    public static final ModConfigSpec.IntValue CARDIAC_ARREST_BRAIN_LOSS;
    public static final ModConfigSpec.IntValue VFIB_BRAIN_LOSS;
    public static final ModConfigSpec.IntValue BRAIN_HEALTH_REGEN_INTERVAL;
    public static final ModConfigSpec.IntValue BRAIN_HEALTH_REGEN_AMOUNT;
    public static final ModConfigSpec.DoubleValue BRAIN_LOSS_OXYGEN_90;
    public static final ModConfigSpec.DoubleValue BRAIN_LOSS_OXYGEN_50;
    public static final ModConfigSpec.DoubleValue BRAIN_LOSS_OXYGEN_30;
    public static final ModConfigSpec.DoubleValue BRAIN_LOSS_OXYGEN_LOW;

    // ================= 意识 (consciousness) =================
    public static final ModConfigSpec.DoubleValue CONSCIOUSNESS_BASE_RECOVERY;
    public static final ModConfigSpec.DoubleValue PAIN_PENALTY_DIVISOR;
    public static final ModConfigSpec.DoubleValue OXYGEN_PENALTY_90;
    public static final ModConfigSpec.DoubleValue OXYGEN_PENALTY_80;
    public static final ModConfigSpec.DoubleValue OXYGEN_PENALTY_70;
    public static final ModConfigSpec.DoubleValue OXYGEN_PENALTY_50;
    public static final ModConfigSpec.DoubleValue OXYGEN_PENALTY_30;
    public static final ModConfigSpec.DoubleValue OXYGEN_PENALTY_CRITICAL;

    // ================= 心率 (heart_rate) =================
    public static final ModConfigSpec.IntValue RESTING_HEART_RATE;
    public static final ModConfigSpec.IntValue MAX_HEART_RATE;
    public static final ModConfigSpec.DoubleValue PAIN_HEART_RATE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue RESP_MOD_STOP;
    public static final ModConfigSpec.DoubleValue RESP_MOD_SLOW;

    // ================= 呼吸 (respiration) =================
    public static final ModConfigSpec.IntValue BASE_RESPIRATION;
    public static final ModConfigSpec.IntValue MAX_RESPIRATION;
    public static final ModConfigSpec.DoubleValue MAX_RUNNING_RESPIRATION_BONUS;
    public static final ModConfigSpec.IntValue RUNNING_BONUS_MAX_TIME;
    public static final ModConfigSpec.IntValue EXTREME_RESP_LOW_THRESHOLD;
    public static final ModConfigSpec.IntValue EXTREME_RESP_HIGH_THRESHOLD;

    // ================= 心脏骤停与濒危状态 (cardiac_arrest / critical_state) =================
    public static final ModConfigSpec.IntValue CRITICAL_BLOOD_VOLUME;
    public static final ModConfigSpec.IntValue CRITICAL_TIMER_MAX;
    public static final ModConfigSpec.IntValue VFIB_THRESHOLD;
    public static final ModConfigSpec.IntValue VFIB_DURATION;
    public static final ModConfigSpec.IntValue SEVERE_HYPOXIA_THRESHOLD;
    public static final ModConfigSpec.IntValue SEVERE_HYPOXIA_DURATION;
    public static final ModConfigSpec.IntValue BRADY_ARREST_THRESHOLD;
    public static final ModConfigSpec.IntValue BRADY_DURATION;

    // ================= 运动 (exercise) =================
    public static final ModConfigSpec.DoubleValue SPRINT_EXERCISE_INTENSITY;
    public static final ModConfigSpec.DoubleValue JUMP_EXERCISE_INTENSITY;
    public static final ModConfigSpec.IntValue ATTACK_TIMER_DURATION;

    // ================= 疼痛 (pain) =================
    public static final ModConfigSpec.IntValue PAIN_DECAY_INTERVAL;
    public static final ModConfigSpec.IntValue MAX_PAIN_LEVEL;

    // ================= 伤害 (damage) =================
    public static final ModConfigSpec.DoubleValue SAFE_FALL_DISTANCE;
    public static final ModConfigSpec.DoubleValue HEAD_BRAIN_DAMAGE_THRESHOLD;

    // ================= 视觉与特效 (effects) =================
    public static final ModConfigSpec.IntValue BLEED_PARTICLE_THRESHOLD;
    public static final ModConfigSpec.IntValue BRAIN_DAMAGE_FONT_THRESHOLD;
    public static final ModConfigSpec.IntValue RESP_BORDER_THICKNESS;
    public static final ModConfigSpec.IntValue BRAIN_DAMAGE_TRIGGER_THRESHOLD;
    public static final ModConfigSpec.IntValue BRAIN_DAMAGE_TRIGGER_INTERVAL;
    public static final ModConfigSpec.DoubleValue BRAIN_DAMAGE_TRIGGER_CHANCE;
    public static final ModConfigSpec.IntValue BRAIN_DAMAGE_TRIGGER_COOLDOWN;
    public static final ModConfigSpec.DoubleValue BRAIN_DAMAGE_SHOCK_CHANCE;
    public static final ModConfigSpec.DoubleValue BRAIN_DAMAGE_INPUT_INVERT_CHANCE;
    public static final ModConfigSpec.DoubleValue BRAIN_DAMAGE_VIEW_INVERT_CHANCE;
    public static final ModConfigSpec.IntValue BRAIN_DAMAGE_INVERT_DURATION_MIN;
    public static final ModConfigSpec.IntValue BRAIN_DAMAGE_INVERT_DURATION_MAX;

    // ================= 音效 (sound) =================
    public static final ModConfigSpec.BooleanValue ENABLE_CRISIS_SOUND;
    public static final ModConfigSpec.DoubleValue CRISIS_HEARTBEAT_VOLUME;
    public static final ModConfigSpec.IntValue HEARTBEAT_TRIGGER_BLOOD_VOLUME;
    public static final ModConfigSpec.IntValue HEARTBEAT_TRIGGER_MAX_HEART_RATE;
    public static final ModConfigSpec.IntValue HEARTBEAT_TRIGGER_MIN_HEART_RATE;

    // ================= 内出血 (internal_bleeding) =================
    public static final ModConfigSpec.IntValue TRIGGER_DAMAGE_THRESHOLD;
    public static final ModConfigSpec.DoubleValue RATE_PER_DAMAGE;
    public static final ModConfigSpec.IntValue MAX_INTERNAL_RATE;
    public static final ModConfigSpec.IntValue SELF_HEAL_THRESHOLD;
    public static final ModConfigSpec.IntValue SELF_HEAL_INTERVAL;
    public static final ModConfigSpec.DoubleValue CHEST_TRIGGER_CHANCE;
    public static final ModConfigSpec.DoubleValue LIMB_TRIGGER_CHANCE;
    public static final ModConfigSpec.DoubleValue FALL_TRIGGER_CHANCE;
    public static final ModConfigSpec.DoubleValue LIMB_RATE_MULTIPLIER;

    // ================= 血胸 (hemothorax) =================
    public static final ModConfigSpec.DoubleValue ACCUMULATION_RATIO;
    public static final ModConfigSpec.IntValue MAX_CHEST_ACCUMULATION;
    public static final ModConfigSpec.IntValue LEVEL1_THRESHOLD;
    public static final ModConfigSpec.IntValue LEVEL2_THRESHOLD;
    public static final ModConfigSpec.IntValue LEVEL3_THRESHOLD;
    public static final ModConfigSpec.IntValue LEVEL4_THRESHOLD;
    public static final ModConfigSpec.IntValue AUTO_VOMIT_INTERVAL;
    public static final ModConfigSpec.IntValue VOMIT_AMOUNT;
    public static final ModConfigSpec.DoubleValue LEVEL1_RESPIRATION_MULT;
    public static final ModConfigSpec.DoubleValue LEVEL2_RESPIRATION_MULT;
    public static final ModConfigSpec.DoubleValue LEVEL3_RESPIRATION_MULT;
    public static final ModConfigSpec.DoubleValue LEVEL4_RESPIRATION_MULT;
    public static final ModConfigSpec.DoubleValue LUNG_CONTUSION_RATIO;
    public static final ModConfigSpec.IntValue MIN_VOMIT_LEVEL;
    public static final ModConfigSpec.IntValue LEVEL1_VOMIT_AMOUNT;
    public static final ModConfigSpec.IntValue LEVEL2_VOMIT_AMOUNT;
    public static final ModConfigSpec.IntValue LEVEL3_VOMIT_AMOUNT;
    public static final ModConfigSpec.IntValue LEVEL4_VOMIT_AMOUNT;
    public static final ModConfigSpec.DoubleValue LEVEL1_BLOOD_PRESSURE_MODIFIER;
    public static final ModConfigSpec.DoubleValue LEVEL2_BLOOD_PRESSURE_MODIFIER;
    public static final ModConfigSpec.DoubleValue LEVEL3_BLOOD_PRESSURE_MODIFIER;
    public static final ModConfigSpec.DoubleValue LEVEL4_BLOOD_PRESSURE_MODIFIER;
    public static final ModConfigSpec.DoubleValue LEVEL1_HEART_RATE_BONUS;
    public static final ModConfigSpec.DoubleValue LEVEL2_HEART_RATE_BONUS;
    public static final ModConfigSpec.DoubleValue LEVEL3_HEART_RATE_BONUS;
    public static final ModConfigSpec.DoubleValue LEVEL4_HEART_RATE_BONUS;
    public static final ModConfigSpec.DoubleValue LEVEL1_CONSCIOUSNESS_PENALTY;
    public static final ModConfigSpec.DoubleValue LEVEL2_CONSCIOUSNESS_PENALTY;
    public static final ModConfigSpec.DoubleValue LEVEL3_CONSCIOUSNESS_PENALTY;
    public static final ModConfigSpec.DoubleValue LEVEL4_CONSCIOUSNESS_PENALTY;

    // ================= 肺挫伤 (lung_contusion) =================
    public static final ModConfigSpec.DoubleValue CONTUSION_20_RESPIRATION_MULT;
    public static final ModConfigSpec.DoubleValue CONTUSION_40_RESPIRATION_MULT;
    public static final ModConfigSpec.DoubleValue CONTUSION_60_RESPIRATION_MULT;
    public static final ModConfigSpec.DoubleValue CONTUSION_80_RESPIRATION_MULT;
    public static final ModConfigSpec.DoubleValue CONTUSION_100_RESPIRATION_MULT;
    public static final ModConfigSpec.DoubleValue CONTUSION_OXYGEN_PENALTY;

    // ================= 颅内出血 (intracranial_hemorrhage) =================
    public static final ModConfigSpec.DoubleValue TRIGGER_CHANCE_AT_10;
    public static final ModConfigSpec.DoubleValue TRIGGER_CHANCE_AT_13;
    public static final ModConfigSpec.DoubleValue TRIGGER_CHANCE_AT_16;
    public static final ModConfigSpec.DoubleValue TRIGGER_CHANCE_AT_19;
    public static final ModConfigSpec.DoubleValue TRIGGER_CHANCE_AT_20_PLUS;
    public static final ModConfigSpec.DoubleValue PRIOR_DAMAGE_BONUS;
    public static final ModConfigSpec.DoubleValue IC_RATE_PER_DAMAGE;
    public static final ModConfigSpec.IntValue IC_MAX_RATE;
    public static final ModConfigSpec.IntValue ONSET_DELAY;
    public static final ModConfigSpec.IntValue IC_LEVEL1_THRESHOLD;
    public static final ModConfigSpec.IntValue IC_LEVEL2_THRESHOLD;
    public static final ModConfigSpec.IntValue IC_LEVEL3_THRESHOLD;
    public static final ModConfigSpec.IntValue IC_LEVEL4_THRESHOLD;
    public static final ModConfigSpec.IntValue IC_MAX_ACCUMULATION;
    public static final ModConfigSpec.DoubleValue IC_LEVEL4_BRAIN_DAMAGE;

    // ================= 血压系统 (blood_pressure) =================
    // 血压基础值
    public static final ModConfigSpec.IntValue NORMAL_SBP;
    public static final ModConfigSpec.IntValue NORMAL_DBP;
    public static final ModConfigSpec.DoubleValue NORMAL_MAP;
    public static final ModConfigSpec.DoubleValue NORMAL_CO;
    public static final ModConfigSpec.DoubleValue MAP_APPROACH_SPEED;

    // 外周阻力
    public static final ModConfigSpec.DoubleValue TPR_NORMAL;
    public static final ModConfigSpec.DoubleValue TPR_COMPENSATED;
    public static final ModConfigSpec.DoubleValue TPR_DECOMPENSATED;

    // 阈值
    public static final ModConfigSpec.DoubleValue MAP_HYPOTENSION;
    public static final ModConfigSpec.DoubleValue MAP_SEVERE_HYPOTENSION;
    public static final ModConfigSpec.IntValue SBP_HYPERTENSIVE_CRISIS;

    // 血压对出血的修正系数
    public static final ModConfigSpec.DoubleValue BLEED_MULT_MAP_BELOW_40;
    public static final ModConfigSpec.DoubleValue BLEED_MULT_MAP_40_55;
    public static final ModConfigSpec.DoubleValue BLEED_MULT_MAP_55_70;
    public static final ModConfigSpec.DoubleValue BLEED_MULT_MAP_70_85;
    public static final ModConfigSpec.DoubleValue BLEED_MULT_MAP_85_105;
    public static final ModConfigSpec.DoubleValue BLEED_MULT_MAP_105_125;
    public static final ModConfigSpec.DoubleValue BLEED_MULT_MAP_125_145;
    public static final ModConfigSpec.DoubleValue BLEED_MULT_MAP_145_165;
    public static final ModConfigSpec.DoubleValue BLEED_MULT_MAP_ABOVE_165;

    // 部位类型系数
    public static final ModConfigSpec.DoubleValue EXTERNAL_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue CHEST_EXTERNAL_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue INTERNAL_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue HEMOTHORAX_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue INTRACRANIAL_MULTIPLIER;

    // 意识影响系数
    public static final ModConfigSpec.DoubleValue CONS_PENALTY_MAP_60;
    public static final ModConfigSpec.DoubleValue CONS_PENALTY_MAP_50;
    public static final ModConfigSpec.DoubleValue CONS_PENALTY_MAP_40;
    public static final ModConfigSpec.DoubleValue CONS_PENALTY_MAP_BELOW_40;
    public static final ModConfigSpec.DoubleValue CONS_PENALTY_SBP_180;
    public static final ModConfigSpec.DoubleValue CONS_PENALTY_SBP_200;
    public static final ModConfigSpec.DoubleValue CONS_PENALTY_SBP_220;

    // 脑健康影响系数
    public static final ModConfigSpec.DoubleValue BRAIN_PENALTY_MAP_BELOW_50;
    public static final ModConfigSpec.DoubleValue BRAIN_PENALTY_SBP_ABOVE_200;

    // ================= 感染系统 (infection) =================
    public static final ModConfigSpec.DoubleValue INFECTION_BASE_RATE;
    public static final ModConfigSpec.DoubleValue INFECTION_BANDAGE_REDUCTION;
    public static final ModConfigSpec.DoubleValue INFECTION_WET_BONUS;
    public static final ModConfigSpec.DoubleValue INFECTION_FRACTURE_BONUS;
    public static final ModConfigSpec.IntValue INFECTION_WINDOW_PERIOD;
    public static final ModConfigSpec.IntValue INFECTION_SYSTEMIC_THRESHOLD;

    // ================= 免疫治疗系统 (immunity) =================
    public static final ModConfigSpec.DoubleValue IMMUNE_BASE_RATE;
    public static final ModConfigSpec.DoubleValue IMMUNE_HUNGER_HIGH_MULT;
    public static final ModConfigSpec.DoubleValue IMMUNE_HUNGER_MID_MULT;
    public static final ModConfigSpec.DoubleValue IMMUNE_HUNGER_LOW_MULT;
    public static final ModConfigSpec.DoubleValue IMMUNE_HUNGER_CRIT_MULT;
    public static final ModConfigSpec.DoubleValue IMMUNE_HUNGER_STARVE_MULT;
    public static final ModConfigSpec.IntValue DISINFECTANT_DURATION;
    public static final ModConfigSpec.DoubleValue DISINFECTANT_IMMUNE_MULT;
    public static final ModConfigSpec.BooleanValue DISINFECTANT_BLOCKS_GROWTH;

    // ================= SIRS 全身炎症反应 (sirs) =================
    // SIRS 触发与晋级
    public static final ModConfigSpec.IntValue SIRS_LIMB_THRESHOLD;
    public static final ModConfigSpec.IntValue SIRS_TOTAL_THRESHOLD;
    public static final ModConfigSpec.IntValue SIRS_PROGRESSION_INTERVAL;
    public static final ModConfigSpec.IntValue SIRS_RECOVERY_LIMB_THRESHOLD;
    public static final ModConfigSpec.IntValue SIRS_RECOVERY_TOTAL_THRESHOLD;
    public static final ModConfigSpec.IntValue SIRS_RECOVERY_INTERVAL_L4_TO_L3;
    public static final ModConfigSpec.IntValue SIRS_RECOVERY_INTERVAL_L3_TO_L2;
    public static final ModConfigSpec.IntValue SIRS_RECOVERY_INTERVAL_L2_TO_L1;
    public static final ModConfigSpec.IntValue SIRS_RECOVERY_INTERVAL_L1_TO_L0;

    // 环境倍率
    public static final ModConfigSpec.DoubleValue SIRS_ENV_WATER;
    public static final ModConfigSpec.DoubleValue SIRS_ENV_CAVE;
    public static final ModConfigSpec.DoubleValue SIRS_ENV_NETHER;
    public static final ModConfigSpec.DoubleValue SIRS_ENV_RAIN;
    public static final ModConfigSpec.DoubleValue SIRS_ENV_INDOOR;
    public static final ModConfigSpec.DoubleValue SIRS_ENV_NORMAL;

    // SIRS 各等级对系统的影响
    public static final ModConfigSpec.IntValue SIRS_HEART_BONUS_PER_LEVEL;
    public static final ModConfigSpec.IntValue SIRS_RESP_BONUS_PER_LEVEL;
    public static final ModConfigSpec.DoubleValue SIRS_OXYGEN_PENALTY_PER_LEVEL;
    public static final ModConfigSpec.DoubleValue SIRS_BLOOD_LOSS_L2;
    public static final ModConfigSpec.DoubleValue SIRS_BLOOD_LOSS_L3;
    public static final ModConfigSpec.DoubleValue SIRS_BLOOD_LOSS_L4;

    // SIRS 对 TPR（血压）的影响
    public static final ModConfigSpec.DoubleValue SIRS_TPR_MODIFIER_L1;
    public static final ModConfigSpec.DoubleValue SIRS_TPR_MODIFIER_L2;
    public static final ModConfigSpec.DoubleValue SIRS_TPR_MODIFIER_L3;
    public static final ModConfigSpec.DoubleValue SIRS_TPR_MODIFIER_L4;

    // ================= 伤害类型细化 (damage_type) =================
    public static final ModConfigSpec.DoubleValue PIERCING_EXTERNAL_RATIO;
    public static final ModConfigSpec.DoubleValue PIERCING_INTERNAL_RATIO;
    public static final ModConfigSpec.DoubleValue PIERCING_FRACTURE_OPEN_CHANCE;
    public static final ModConfigSpec.DoubleValue SLASHING_EXTERNAL_RATIO;
    public static final ModConfigSpec.DoubleValue SLASHING_INTERNAL_RATIO;
    public static final ModConfigSpec.DoubleValue SLASHING_FRACTURE_OPEN_CHANCE;
    public static final ModConfigSpec.DoubleValue BLUNT_EXTERNAL_RATIO;
    public static final ModConfigSpec.DoubleValue BLUNT_INTERNAL_RATIO;
    public static final ModConfigSpec.DoubleValue BLUNT_FRACTURE_OPEN_CHANCE;
    public static final ModConfigSpec.DoubleValue MIXED_EXTERNAL_RATIO;
    public static final ModConfigSpec.DoubleValue MIXED_INTERNAL_RATIO;
    public static final ModConfigSpec.DoubleValue MIXED_FRACTURE_OPEN_CHANCE;
    public static final ModConfigSpec.DoubleValue EXPLOSION_EXTERNAL_RATIO;
    public static final ModConfigSpec.DoubleValue EXPLOSION_INTERNAL_RATIO;
    public static final ModConfigSpec.DoubleValue EXPLOSION_HEAD_RATIO;
    public static final ModConfigSpec.DoubleValue EXPLOSION_CHEST_RATIO;
    public static final ModConfigSpec.DoubleValue EXPLOSION_ARM_RATIO;
    public static final ModConfigSpec.DoubleValue EXPLOSION_LEG_RATIO;

    // ================= 骨折系统 (fracture) =================
    public static final ModConfigSpec.IntValue MIN_DAMAGE_FOR_FRACTURE;
    public static final ModConfigSpec.DoubleValue BASE_FRACTURE_CHANCE;
    public static final ModConfigSpec.IntValue HIGH_DAMAGE_THRESHOLD;
    public static final ModConfigSpec.DoubleValue HIGH_DAMAGE_FRACTURE_CHANCE;
    public static final ModConfigSpec.IntValue FALL_MIN_HEIGHT_FOR_FRACTURE;
    public static final ModConfigSpec.DoubleValue FALL_FRACTURE_CHANCE_PER_BLOCK;
    public static final ModConfigSpec.DoubleValue EXPLOSION_FRACTURE_CHANCE;
    public static final ModConfigSpec.IntValue SPLINT_HEAL_TIME;
    public static final ModConfigSpec.IntValue FRACTURE_PAIN_LEVEL;
    public static final ModConfigSpec.IntValue SPLINT_PAIN_DECAY_INTERVAL;
    public static final ModConfigSpec.IntValue NO_SPLINT_PAIN_DECAY_INTERVAL;
    public static final ModConfigSpec.DoubleValue FRACTURE_BLEED_PER_DAMAGE;
    public static final ModConfigSpec.DoubleValue FRACTURE_BLEED_MAX;
    public static final ModConfigSpec.DoubleValue FRACTURE_LIMB_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue FRACTURE_CHEST_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue OPEN_FRACTURE_INFECTION_RATE;
    public static final ModConfigSpec.IntValue SECONDARY_TRAUMA_INTERVAL;
    public static final ModConfigSpec.IntValue SECONDARY_TRAUMA_INTERNAL_BLEED;
    public static final ModConfigSpec.IntValue SECONDARY_TRAUMA_MAX_INTERNAL_BLEED;
    public static final ModConfigSpec.IntValue SECONDARY_TRAUMA_EXTERNAL_BLEED;
    public static final ModConfigSpec.IntValue SECONDARY_TRAUMA_MAX_EXTERNAL_BLEED;
    public static final ModConfigSpec.IntValue SECONDARY_TRAUMA_PAIN_INCREASE;
    public static final ModConfigSpec.IntValue SECONDARY_TRAUMA_HEAL_TIME_PENALTY;
    public static final ModConfigSpec.DoubleValue LEG_FRACTURE_NO_SPLINT_SPEED_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue LEG_FRACTURE_SPLINT_SPEED_MULTIPLIER;
    public static final ModConfigSpec.BooleanValue LEG_FRACTURE_JUMP_ALLOWED;
    public static final ModConfigSpec.DoubleValue ARM_FRACTURE_NO_SPLINT_MINING_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue ARM_FRACTURE_NO_SPLINT_ATTACK_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue ARM_FRACTURE_SPLINT_MINING_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue ARM_FRACTURE_SPLINT_ATTACK_MULTIPLIER;

    // ================= 断肢系统 (amputation) =================
    public static final ModConfigSpec.BooleanValue AMPUTATION_ENABLED;
    public static final ModConfigSpec.IntValue AMPUTATION_DAMAGE_THRESHOLD;
    public static final ModConfigSpec.DoubleValue AMPUTATION_CHANCE;
    public static final ModConfigSpec.BooleanValue AMPUTATION_SCALES_WITH_DAMAGE;
    public static final ModConfigSpec.DoubleValue AMPUTATION_CHANCE_PER_DAMAGE;
    public static final ModConfigSpec.DoubleValue AMPUTATION_CHANCE_CAP;
    public static final ModConfigSpec.IntValue AMPUTATION_BLEEDING;
    public static final ModConfigSpec.IntValue AMPUTATION_ADJACENT_PAIN;
    public static final ModConfigSpec.IntValue AMPUTATION_ADJACENT_BLEEDING;
    public static final ModConfigSpec.IntValue AMPUTATION_ADJACENT_INTERNAL_BLEEDING;
    public static final ModConfigSpec.DoubleValue AMPUTATION_LEG_SPEED_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue AMPUTATION_DOUBLE_LEG_SPEED_MULTIPLIER;
    public static final ModConfigSpec.BooleanValue AMPUTATION_LEG_JUMP_ALLOWED;
    public static final ModConfigSpec.BooleanValue AMPUTATION_DOUBLE_LEG_PRONE;
    public static final ModConfigSpec.DoubleValue AMPUTATION_ARM_MINING_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue AMPUTATION_ARM_ATTACK_MULTIPLIER;
    public static final ModConfigSpec.BooleanValue AMPUTATION_ARM_INTERACTION_ALLOWED;

    // ================= 连续仲裁 buff 系统 (arbitrated_buffs) =================
    // 通用
    public static final ModConfigSpec.DoubleValue ARBITER_MIN_MULTIPLIER;
    public static final ModConfigSpec.IntValue POTION_HYSTERESIS_TICKS;
    // 疼痛连续系数
    public static final ModConfigSpec.DoubleValue PAIN_LEG_SPEED_SLOPE;
    public static final ModConfigSpec.DoubleValue PAIN_ARM_ATTACK_SLOPE;
    public static final ModConfigSpec.DoubleValue PAIN_ARM_SPEED_SLOPE;
    public static final ModConfigSpec.DoubleValue PAIN_HEAD_BLINDNESS_SLOPE;
    public static final ModConfigSpec.DoubleValue PAIN_CHEST_NAUSEA_SLOPE;
    // 失血与血氧
    public static final ModConfigSpec.DoubleValue BLOOD_LOSS_SPEED_SLOPE;
    public static final ModConfigSpec.DoubleValue BLOOD_LOSS_ATTACK_SLOPE;
    public static final ModConfigSpec.DoubleValue BLOOD_LOSS_DIG_SLOPE;
    public static final ModConfigSpec.IntValue HYPOXIA_ONSET_SPO2;
    public static final ModConfigSpec.DoubleValue HYPOXIA_MAX_PENALTY;
    // SIRS 感染
    public static final ModConfigSpec.DoubleValue SIRS_FATIGUE_PER_LEVEL;
    public static final ModConfigSpec.DoubleValue SEPSIS_MIN_MULTIPLIER;
    // 意识压制
    public static final ModConfigSpec.DoubleValue CONSC_IMPACT_FACTOR;
    public static final ModConfigSpec.DoubleValue CONSC_IMPACT_UNCONSCIOUS;
    // 器官伤
    public static final ModConfigSpec.DoubleValue HEMOTHORAX_L3_SPEED_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue HEMOTHORAX_L4_SPEED_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue CONTUSION_FATIGUE_SLOPE;
    public static final ModConfigSpec.IntValue IC_BLINDNESS_L3;
    public static final ModConfigSpec.IntValue IC_BLINDNESS_L4;
    // 感知药水插值
    public static final ModConfigSpec.IntValue PERCEIVED_PAIN_BLINDNESS_THRESHOLD;
    public static final ModConfigSpec.IntValue PERCEIVED_PAIN_NAUSEA_THRESHOLD;
    // 挖掘药水插值（无挖掘属性，DIG_SLOWDOWN 插值）
    public static final ModConfigSpec.IntValue DIG_SLOWDOWN_FRACTURE_MAX_LEVEL;

    // ================= 中毒系统 (toxin) =================
    // 累加与代谢
    public static final ModConfigSpec.DoubleValue TOXIN_ACCUMULATION_RATE;
    public static final ModConfigSpec.DoubleValue TOXIN_DECAY_RATE;
    public static final ModConfigSpec.DoubleValue TOXIN_IMMUNE_DECAY_MULT;
    // 阈值分级
    public static final ModConfigSpec.DoubleValue TOXIN_MILD_THRESHOLD;
    public static final ModConfigSpec.DoubleValue TOXIN_MID_THRESHOLD;
    public static final ModConfigSpec.DoubleValue TOXIN_SEVERE_THRESHOLD;
    public static final ModConfigSpec.DoubleValue TOXIN_CRITICAL_THRESHOLD;
    // 症状斜率
    public static final ModConfigSpec.DoubleValue TOXIN_NAUSEA_SLOPE;
    public static final ModConfigSpec.DoubleValue TOXIN_RESP_RATE_SLOPE;
    public static final ModConfigSpec.DoubleValue TOXIN_RESP_SUPPRESSION_SLOPE;
    public static final ModConfigSpec.DoubleValue TOXIN_CONSCIOUSNESS_SLOPE;
    public static final ModConfigSpec.DoubleValue TOXIN_TPR_SLOPE;
    public static final ModConfigSpec.DoubleValue TOXIN_STROKE_VOLUME_SLOPE;
    // 重度并发症
    public static final ModConfigSpec.DoubleValue TOXIN_INFECTION_RATE;
    public static final ModConfigSpec.DoubleValue TOXIN_VFIB_CHANCE;
    public static final ModConfigSpec.DoubleValue TOXIN_CLOTTING_TIME_MULT;

    // ================= 烧伤系统 (burn) =================
    // 入口与负荷
    public static final ModConfigSpec.DoubleValue BURN_LOAD_PER_DAMAGE;
    public static final ModConfigSpec.DoubleValue BURN_CONVERSION_RATE;
    public static final ModConfigSpec.DoubleValue BURN_AREA_PER_DAMAGE;
    public static final ModConfigSpec.IntValue BURN_HEAT_WINDOW;
    public static final ModConfigSpec.DoubleValue BURN_IRRIGATION_RATE;
    // 深度阈值
    public static final ModConfigSpec.DoubleValue BURN_DEGREE_I;
    public static final ModConfigSpec.DoubleValue BURN_DEGREE_SHALLOW_II;
    public static final ModConfigSpec.DoubleValue BURN_DEGREE_DEEP_II;
    public static final ModConfigSpec.DoubleValue BURN_DEGREE_III;
    // TBSA 权重与部位分配
    public static final ModConfigSpec.DoubleValue BURN_TBSA_WEIGHT_HEAD;
    public static final ModConfigSpec.DoubleValue BURN_TBSA_WEIGHT_CHEST;
    public static final ModConfigSpec.DoubleValue BURN_TBSA_WEIGHT_ARM;
    public static final ModConfigSpec.DoubleValue BURN_TBSA_WEIGHT_LEG;
    public static final ModConfigSpec.DoubleValue BURN_PLACEMENT_HEAD;
    public static final ModConfigSpec.DoubleValue BURN_PLACEMENT_CHEST;
    public static final ModConfigSpec.DoubleValue BURN_PLACEMENT_ARM;
    public static final ModConfigSpec.DoubleValue BURN_PLACEMENT_LEG;
    public static final ModConfigSpec.DoubleValue BURN_STICKINESS;
    // TBSA 锚点
    public static final ModConfigSpec.DoubleValue BURN_TBSA_PAIN_THRESHOLD;
    public static final ModConfigSpec.DoubleValue BURN_TBSA_SHOCK_THRESHOLD;
    public static final ModConfigSpec.DoubleValue BURN_TBSA_COAGULOPATHY_THRESHOLD;
    // 渗漏休克与回吸收
    public static final ModConfigSpec.DoubleValue BURN_LEAK_BASE;
    public static final ModConfigSpec.IntValue BURN_LEAK_PEAK_TIME;
    public static final ModConfigSpec.IntValue BURN_LEAK_DURATION;
    public static final ModConfigSpec.IntValue BURN_EDEMA_RETURN_START;
    public static final ModConfigSpec.IntValue BURN_EDEMA_RETURN_END;
    public static final ModConfigSpec.DoubleValue BURN_TOXIN_RELEASE;
    // 疼痛/感染/愈合
    public static final ModConfigSpec.DoubleValue BURN_PAIN_I;
    public static final ModConfigSpec.DoubleValue BURN_PAIN_SHALLOW_II;
    public static final ModConfigSpec.DoubleValue BURN_PAIN_DEEP_II;
    public static final ModConfigSpec.DoubleValue BURN_PAIN_III;
    public static final ModConfigSpec.DoubleValue BURN_INFECTION_RATE;
    public static final ModConfigSpec.IntValue BURN_ESCHAR_PHASE_START;
    public static final ModConfigSpec.IntValue BURN_ESCHAR_PHASE_END;
    public static final ModConfigSpec.IntValue BURN_HEAL_TIME_I;
    public static final ModConfigSpec.IntValue BURN_HEAL_TIME_SHALLOW_II;
    public static final ModConfigSpec.IntValue BURN_HEAL_TIME_DEEP_II;
    public static final ModConfigSpec.DoubleValue BURN_HEAL_MULT_BANDAGED;
    public static final ModConfigSpec.DoubleValue BURN_PAIN_MULT_BANDAGED;
    public static final ModConfigSpec.DoubleValue BURN_INFECTION_MULT_BANDAGED;
    // 吸入性损伤与代谢
    public static final ModConfigSpec.DoubleValue BURN_INHALATION_RATE;
    public static final ModConfigSpec.DoubleValue BURN_INHALATION_DECAY;
    public static final ModConfigSpec.DoubleValue BURN_INHALATION_VENT_MULT;
    public static final ModConfigSpec.DoubleValue BURN_METABOLISM_SLOPE;
    public static final ModConfigSpec.DoubleValue BURN_CLOTTING_SLOPE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        // 1. 出血
        builder.comment("出血与自然凝血系统配置").push("bleeding");
        BLEED_TO_BLOOD_RATIO = builder
                .comment("每秒失血换算系数（将 mL/min 出血速率转换为每秒实际扣除的失血量，默认 1/60 即约 0.01667）",
                         "影响范围: 所有外出血扣减血容量",
                         "推荐范围: [0.005, 0.1]")
                .defineInRange("bleedToBloodRatio", 1.0 / 60.0, 0.0, 1.0);
        NATURAL_CLOTTING_THRESHOLD = builder
                .comment("自然止血最大外出血阈值 (mL/min)。低于或等于此数值的创口在未包扎状态下可随时间自然凝血止血",
                         "推荐范围: [10, 100]")
                .defineInRange("naturalClottingThreshold", 50, 0, 500);
        NATURAL_CLOTTING_MIN_TIME = builder
                .comment("轻微出血 (1 mL/min) 自然止血所需最短时间 (秒)",
                         "推荐范围: [30, 300]")
                .defineInRange("naturalClottingMinTime", 60, 1, 3600);
        NATURAL_CLOTTING_MAX_TIME = builder
                .comment("中度出血 (50 mL/min) 自然止血所需最长时间 (秒)",
                         "推荐范围: [60, 600]")
                .defineInRange("naturalClottingMaxTime", 300, 1, 3600);
        builder.pop();

        // 1.5 血容量回升
        builder.comment("血容量自然回升系统配置（无活动性失血且饱食时缓慢恢复血容量）").push("blood_regen");
        BLOOD_VOLUME_REGEN_ENABLED = builder
                .comment("是否启用血容量自然回升（关闭后完全禁用）")
                .define("enableBloodVolumeRegen", true);
        BLOOD_REGEN_RATE = builder
                .comment("血容量每秒回升速率 (mL/s)",
                         "影响范围: 满足条件时的血容量恢复速度",
                         "推荐范围: [1, 20]")
                .defineInRange("bloodRegenRate", 5.0, 0.0, 100.0);
        BLOOD_REGEN_FOOD_THRESHOLD = builder
                .comment("触发回升所需的最低饱食度 (0~20)",
                         "推荐范围: [6, 20]")
                .defineInRange("bloodRegenFoodThreshold", 14, 0, 20);
        builder.pop();

        // 2. 血氧
        builder.comment("血氧动态衰减与代偿系统配置").push("blood_oxygen");
        CARDIAC_ARREST_OXYGEN_LOSS = builder
                .comment("心脏骤停发生时每秒硬性扣除的血氧百分比 (%)",
                         "推荐范围: [10, 50]")
                .defineInRange("cardiacArrestOxygenLoss", 20, 0, 100);
        VFIB_OXYGEN_LOSS = builder
                .comment("心室纤颤发生时每秒硬性扣除的血氧百分比 (%)",
                         "推荐范围: [5, 30]")
                .defineInRange("vfibOxygenLoss", 10, 0, 100);
        OXYGEN_CHANGE_RATIO_70 = builder
                .comment("血容量 > 70% 时的每秒基础血氧恢复率 (%/s)",
                         "推荐范围: [0.1, 2.0]")
                .defineInRange("oxygenChangeRatio70", 0.5, -10.0, 10.0);
        OXYGEN_CHANGE_RATIO_50 = builder
                .comment("血容量在 50% ~ 70% 时的每秒基础血氧变化率 (%/s)",
                         "推荐范围: [-1.0, 0.0]")
                .defineInRange("oxygenChangeRatio50", -0.2, -10.0, 10.0);
        OXYGEN_CHANGE_RATIO_30 = builder
                .comment("血容量在 30% ~ 50% 时的每秒基础血氧变化率 (%/s)",
                         "推荐范围: [-2.0, 0.0]")
                .defineInRange("oxygenChangeRatio30", -0.5, -10.0, 10.0);
        OXYGEN_CHANGE_RATIO_10 = builder
                .comment("血容量在 10% ~ 30% 时的每秒基础血氧变化率 (%/s)",
                         "推荐范围: [-5.0, 0.0]")
                .defineInRange("oxygenChangeRatio10", -1.0, -10.0, 10.0);
        OXYGEN_CHANGE_RATIO_LOW = builder
                .comment("血容量 <= 10% 时的每秒基础血氧变化率 (%/s)",
                         "推荐范围: [-10.0, 0.0]")
                .defineInRange("oxygenChangeRatioLow", -2.0, -10.0, 10.0);
        OXYGEN_LOSS_RESP_STOP_HIGH = builder
                .comment("呼吸停止时血氧 > 70% 的每秒下降速率")
                .defineInRange("oxygenLossRespStopHigh", 0.3, 0.0, 10.0);
        OXYGEN_LOSS_RESP_STOP_MID = builder
                .comment("呼吸停止时血氧 50~70% 的每秒下降速率")
                .defineInRange("oxygenLossRespStopMid", 0.6, 0.0, 10.0);
        OXYGEN_LOSS_RESP_STOP_LOW = builder
                .comment("呼吸停止时血氧 30~50% 的每秒下降速率")
                .defineInRange("oxygenLossRespStopLow", 1.0, 0.0, 10.0);
        OXYGEN_LOSS_RESP_STOP_CRITICAL = builder
                .comment("呼吸停止时血氧 < 30% 的每秒下降速率")
                .defineInRange("oxygenLossRespStopCritical", 1.5, 0.0, 10.0);

        OXYGEN_RECOVERY_RATE_HIGH = builder
                .comment("血容量 > 70% 时的每秒血氧恢复速率")
                .defineInRange("oxygenRecoveryRateHigh", 2.0, 0.0, 10.0);
        OXYGEN_RECOVERY_RATE_MID = builder
                .comment("血容量 50~70% 时的每秒血氧恢复速率")
                .defineInRange("oxygenRecoveryRateMid", 1.0, 0.0, 10.0);
        OXYGEN_RECOVERY_RATE_LOW = builder
                .comment("血容量 30~50% 时的每秒血氧恢复速率")
                .defineInRange("oxygenRecoveryRateLow", 0.5, 0.0, 10.0);
        OXYGEN_RECOVERY_RATE_CRIT = builder
                .comment("血容量 < 30% 时的每秒血氧恢复速率")
                .defineInRange("oxygenRecoveryRateCrit", 0.2, 0.0, 10.0);

        RESPIRATION_EFFECT_PER_RATE = builder
            .comment("呼吸频率每偏离基准 1 次/分的血氧变化率")
            .defineInRange("respirationEffectPerRate", 0.3, 0.0, 5.0);

        RESPIRATION_BASELINE = builder
            .comment("呼吸频率的血氧平衡基准（呼吸 = 此值时血氧不变）")
            .defineInRange("respirationBaseline", 10.0, 5.0, 20.0);

        RESPIRATION_STOP_EFFECT = builder
            .comment("呼吸完全停止时每秒的血氧下降速率")
            .defineInRange("respirationStopEffect", -5.0, -20.0, 0.0);

        UNDERWATER_BREATH_HOLD_TIME = builder
            .comment("水下屏息缓冲时间 (秒，在此时间内肺部存氧可维持血氧，不发生剧烈缺氧)")
            .defineInRange("underwaterBreathHoldTime", 15, 0, 60);

        BREATH_HOLD_OXYGEN_LOSS = builder
            .comment("水下屏息期间每秒的血氧基础消耗率")
            .defineInRange("breathHoldOxygenLoss", 0.2, 0.0, 2.0);

        builder.pop();

        // 3. 脑健康
        builder.comment("脑健康度消耗与缓慢自愈机制").push("brain_health");
        CARDIAC_ARREST_BRAIN_LOSS = builder
                .comment("心脏骤停发生时每秒扣除的脑健康点数 (%)",
                         "推荐范围: [2, 20]")
                .defineInRange("cardiacArrestBrainLoss", 5, 0, 100);
        VFIB_BRAIN_LOSS = builder
                .comment("心室纤颤发生时每秒扣除的脑健康点数 (%)",
                         "推荐范围: [1, 10]")
                .defineInRange("vfibBrainLoss", 2, 0, 100);
        BRAIN_HEALTH_REGEN_INTERVAL = builder
                .comment("脑健康无条件自然恢复的触发间隔周期 (秒，默认 60s 恢复 1 点)",
                         "推荐范围: [10, 300]")
                .defineInRange("brainHealthRegenInterval", 60, 1, 3600);
        BRAIN_HEALTH_REGEN_AMOUNT = builder
                .comment("每次自然恢复触发时回复的脑健康数值 (%)",
                         "推荐范围: [1, 5]")
                .defineInRange("brainHealthRegenAmount", 1, 1, 100);
        BRAIN_LOSS_OXYGEN_90 = builder
                .comment("血氧 >= 90% 且供血充盈时的脑健康微量恢复速率 (%/s)",
                         "推荐范围: [0.0, 1.0]")
                .defineInRange("brainLossOxygen90", 0.1, -10.0, 10.0);
        BRAIN_LOSS_OXYGEN_50 = builder
                .comment("血氧在 50% ~ 70% 时的脑健康每秒消耗速率 (%/s)",
                         "推荐范围: [-1.0, 0.0]")
                .defineInRange("brainLossOxygen50", -0.2, -10.0, 10.0);
        BRAIN_LOSS_OXYGEN_30 = builder
                .comment("血氧在 30% ~ 50% 时的脑健康每秒消耗速率 (%/s)",
                         "推荐范围: [-2.0, 0.0]")
                .defineInRange("brainLossOxygen30", -0.5, -10.0, 10.0);
        BRAIN_LOSS_OXYGEN_LOW = builder
                .comment("血氧 < 30% 严重缺氧时的脑健康每秒消耗速率 (%/s)",
                         "推荐范围: [-5.0, 0.0]")
                .defineInRange("brainLossOxygenLow", -1.0, -10.0, 10.0);
        builder.pop();

        // 4. 意识
        builder.comment("意识清醒度调控与呼吸/疼痛惩罚").push("consciousness");
        CONSCIOUSNESS_BASE_RECOVERY = builder
                .comment("意识每秒自然恢复的基准量 (%)",
                         "推荐范围: [0.5, 5.0]")
                .defineInRange("consciousnessBaseRecovery", 1.0, 0.0, 10.0);
        PAIN_PENALTY_DIVISOR = builder
                .comment("体感疼痛(0~55)换算为意识每秒惩罚的除数 (penalty = perceivedPain / divisor)",
                         "推荐范围: [15.0, 50.0]")
                .defineInRange("painPenaltyDivisor", 25.0, 1.0, 200.0);
        OXYGEN_PENALTY_90 = builder
                .comment("血氧在 80% ~ 90% 区间时每秒扣除的意识点数 (%)",
                         "推荐范围: [0.0, 2.0]")
                .defineInRange("oxygenPenalty90", 0.0, 0.0, 10.0);
        OXYGEN_PENALTY_80 = builder
                .comment("血氧在 70% ~ 80% 区间时每秒扣除的意识点数 (%)",
                         "推荐范围: [0.0, 3.0]")
                .defineInRange("oxygenPenalty80", 0.0, 0.0, 10.0);
        OXYGEN_PENALTY_70 = builder
                .comment("血氧在 50% ~ 70% 区间时每秒扣除的意识点数 (%)",
                         "推荐范围: [0.1, 2.0]")
                .defineInRange("oxygenPenalty70", 0.5, 0.0, 10.0);
        OXYGEN_PENALTY_50 = builder
                .comment("血氧在 30% ~ 50% 区间时每秒扣除的意识点数 (%)",
                         "推荐范围: [0.5, 3.0]")
                .defineInRange("oxygenPenalty50", 1.5, 0.0, 10.0);
        OXYGEN_PENALTY_30 = builder
                .comment("血氧在 20% ~ 30% 区间时每秒扣除的意识点数 (%)",
                         "推荐范围: [1.0, 5.0]")
                .defineInRange("oxygenPenalty30", 3.0, 0.0, 20.0);
        OXYGEN_PENALTY_CRITICAL = builder
                .comment("血氧 < 20% 严重缺氧区间时每秒扣除的意识点数 (%)",
                         "推荐范围: [3.0, 10.0]")
                .defineInRange("oxygenPenaltyCritical", 5.0, 0.0, 50.0);
        builder.pop();

        // 5. 心率
        builder.comment("心电动力学与窦性调节").push("heart_rate");
        RESTING_HEART_RATE = builder
                .comment("健康人体基础静息心率 (bpm)",
                         "推荐范围: [60, 90]")
                .defineInRange("restingHeartRate", 75, 30, 120);
        MAX_HEART_RATE = builder
                .comment("代偿性窦性心动过速的最大允许心率上限 (bpm)",
                         "推荐范围: [180, 240]")
                .defineInRange("maxHeartRate", 200, 150, 300);
        PAIN_HEART_RATE_MULTIPLIER = builder
                .comment("全身疼痛等级对心率加速的加成系数 (bpm/级)",
                         "推荐范围: [2.0, 8.0]")
                .defineInRange("painHeartRateMultiplier", 4.0, 0.0, 20.0);
        RESP_MOD_STOP = builder
                .comment("呼吸停止 (<=0) 时对心率的减慢修正值 (bpm)",
                         "推荐范围: [-40.0, 0.0]")
                .defineInRange("respModStop", -20.0, -100.0, 100.0);
        RESP_MOD_SLOW = builder
                .comment("呼吸过缓 (<8 次/分) 时对心率的减慢修正值 (bpm)",
                         "推荐范围: [-20.0, 0.0]")
                .defineInRange("respModSlow", -10.0, -100.0, 100.0);
        builder.pop();

        // 6. 呼吸
        builder.comment("呼吸中枢与通气调节").push("respiration");
        BASE_RESPIRATION = builder
                .comment("静息基础呼吸频率 (次/分)",
                         "推荐范围: [12, 18]")
                .defineInRange("baseRespiration", 14, 6, 30);
        MAX_RESPIRATION = builder
                .comment("呼吸频率绝对上限 (次/分)",
                         "推荐范围: [40, 80]")
                .defineInRange("maxRespiration", 50, 20, 120);
        MAX_RUNNING_RESPIRATION_BONUS = builder
                .comment("连续奔跑运动带来的最大呼吸频率增益 (次/分)",
                         "推荐范围: [15.0, 40.0]")
                .defineInRange("maxRunningRespirationBonus", 28.0, 0.0, 60.0);
        RUNNING_BONUS_MAX_TIME = builder
                .comment("奔跑增益达到最大所需持续奔跑时间 (秒)",
                         "推荐范围: [120, 600]")
                .defineInRange("runningBonusMaxTime", 300, 10, 1200);
        EXTREME_RESP_LOW_THRESHOLD = builder
                .comment("触发屏幕边缘全屏红框警告的呼吸过缓下限门槛 (次/分)",
                         "推荐范围: [4, 8]")
                .defineInRange("extremeRespLowThreshold", 6, 1, 15);
        EXTREME_RESP_HIGH_THRESHOLD = builder
                .comment("触发屏幕边缘全屏红框警告的过度换气上限门槛 (次/分)",
                         "推荐范围: [35, 55]")
                .defineInRange("extremeRespHighThreshold", 40, 25, 80);
        builder.pop();

        // 7. 心脏骤停与室颤
        builder.comment("心脏急症与濒危状态阈值").push("cardiac_arrest");
        CRITICAL_BLOOD_VOLUME = builder
                .comment("触发 5 秒濒危倒计时的失血性休克血容量门槛 (mL，人体总量 5000 mL)",
                         "推荐范围: [2000, 3500]")
                .defineInRange("criticalBloodVolume", 3000, 500, 4500);
        CRITICAL_TIMER_MAX = builder
                .comment("濒危状态到心脏骤停的倒计时时长 (秒)",
                         "推荐范围: [3, 10]")
                .defineInRange("criticalTimerMax", 5, 1, 120);
        VFIB_THRESHOLD = builder
                .comment("触发心室纤颤倒计时的恶性心动过速心率阈值 (bpm)",
                         "推荐范围: [180, 230]")
                .defineInRange("vfibThreshold", 190, 150, 240);
        VFIB_DURATION = builder
                .comment("维持恶性心动过速触发室颤所需的持续秒数 (秒)",
                         "推荐范围: [15, 60]")
                .defineInRange("vfibDuration", 30, 5, 120);
        SEVERE_HYPOXIA_THRESHOLD = builder
                .comment("触发缺氧性室颤倒计时的血氧饱和度极限阈值 (%)",
                         "推荐范围: [10, 30]")
                .defineInRange("severeHypoxiaThreshold", 20, 1, 50);
        SEVERE_HYPOXIA_DURATION = builder
                .comment("血氧低于极限阈值持续该秒数后触发缺氧性室颤 (秒)",
                         "推荐范围: [15, 60]")
                .defineInRange("severeHypoxiaDuration", 30, 5, 120);
        BRADY_ARREST_THRESHOLD = builder
                .comment("触发停搏倒计时的严重心动过缓心率阈值 (bpm)",
                         "推荐范围: [20, 45]")
                .defineInRange("bradyArrestThreshold", 40, 10, 60);
        BRADY_DURATION = builder
                .comment("维持严重心动过缓触发心脏骤停所需的持续秒数 (秒)",
                         "推荐范围: [15, 60]")
                .defineInRange("bradyDuration", 30, 5, 120);
        builder.pop();

        // 8. 运动
        builder.comment("运动强度计算与体力活动").push("exercise");
        SPRINT_EXERCISE_INTENSITY = builder
                .comment("疾跑冲刺时的目标运动强度 (0.0 ~ 1.0)",
                         "推荐范围: [0.4, 0.8]")
                .defineInRange("sprintExerciseIntensity", 0.6, 0.0, 1.0);
        JUMP_EXERCISE_INTENSITY = builder
                .comment("跳跃与滞空时的最低目标运动强度 (0.0 ~ 1.0)",
                         "推荐范围: [0.1, 0.4]")
                .defineInRange("jumpExerciseIntensity", 0.2, 0.0, 1.0);
        ATTACK_TIMER_DURATION = builder
                .comment("挥击攻击/破坏方块后激发交感神经兴奋维持的时间 (秒)",
                         "推荐范围: [5, 20]")
                .defineInRange("attackTimerDuration", 10, 1, 60);
        builder.pop();

        // 9. 疼痛
        builder.comment("疼痛感应与自然衰退").push("pain");
        PAIN_DECAY_INTERVAL = builder
                .comment("各肢体疼痛等级自然衰退 1 级所需时间 (秒)",
                         "推荐范围: [30, 180]")
                .defineInRange("painDecayInterval", 60, 1, 600);
        MAX_PAIN_LEVEL = builder
                .comment("单肢体最高允许记录的疼痛等级上限",
                         "推荐范围: [10, 20]")
                .defineInRange("maxPainLevel", 10, 1, 20);
        builder.pop();

        // 10. 伤害
        builder.comment("创伤与跌落机制").push("damage");
        SAFE_FALL_DISTANCE = builder
                .comment("安全坠落距离阈值 (格)。低于此值的坠落不产生骨折、出血或疼痛",
                         "推荐范围: [3.0, 8.0]")
                .defineInRange("safeFallDistance", 5.0, 0.0, 20.0);
        HEAD_BRAIN_DAMAGE_THRESHOLD = builder
                .comment("头部遭受单次伤害触发脑损伤的最小伤害值",
                         "推荐范围: [3.0, 10.0]")
                .defineInRange("headBrainDamageThreshold", 5.0, 0.0, 20.0);
        builder.pop();

        // 11. 视觉与特效
        builder.comment("客户端视觉与粒子呈现").push("effects");
        BLEED_PARTICLE_THRESHOLD = builder
                .comment("开始在玩家身周生成出血滴落粒子的全身出血速率门槛 (mL/min)",
                         "推荐范围: [20, 100]")
                .defineInRange("bleedParticleThreshold", 50, 0, 500);
        BRAIN_DAMAGE_FONT_THRESHOLD = builder
                .comment("客户端文本开始出现字形乱码与抖动错位的脑健康阈值 (%)",
                         "推荐范围: [70, 95]")
                .defineInRange("brainDamageFontThreshold", 90, 10, 100);
        RESP_BORDER_THICKNESS = builder
                .comment("极端呼吸异常触发的全屏边缘警告框像素宽度",
                         "推荐范围: [8, 32]")
                .defineInRange("respBorderThickness", 16, 1, 64);

        BRAIN_DAMAGE_TRIGGER_THRESHOLD = builder
                .comment("脑损伤突发效果触发的脑健康阈值 (%)")
                .defineInRange("brainDamageTriggerThreshold", 90, 10, 100);
        BRAIN_DAMAGE_TRIGGER_INTERVAL = builder
                .comment("脑损伤突发效果检查间隔 (秒)")
                .defineInRange("brainDamageTriggerInterval", 30, 1, 300);
        BRAIN_DAMAGE_TRIGGER_CHANCE = builder
                .comment("脑损伤突发效果每次检查的触发概率 (0.0~1.0)")
                .defineInRange("brainDamageTriggerChance", 0.20, 0.0, 1.0);
        BRAIN_DAMAGE_TRIGGER_COOLDOWN = builder
                .comment("脑损伤突发效果触发后的冷却时间 (秒)")
                .defineInRange("brainDamageTriggerCooldown", 30, 1, 300);
        BRAIN_DAMAGE_SHOCK_CHANCE = builder
                .comment("突发效果中冲击定格的相对权重/概率")
                .defineInRange("brainDamageShockChance", 0.50, 0.0, 1.0);
        BRAIN_DAMAGE_INPUT_INVERT_CHANCE = builder
                .comment("突发效果中按键颠倒的相对权重/概率")
                .defineInRange("brainDamageInputInvertChance", 0.25, 0.0, 1.0);
        BRAIN_DAMAGE_VIEW_INVERT_CHANCE = builder
                .comment("突发效果中视角颠倒的相对权重/概率")
                .defineInRange("brainDamageViewInvertChance", 0.25, 0.0, 1.0);
        BRAIN_DAMAGE_INVERT_DURATION_MIN = builder
                .comment("按键/视角颠倒持续时间下限 (秒)")
                .defineInRange("brainDamageInvertDurationMin", 3, 1, 30);
        BRAIN_DAMAGE_INVERT_DURATION_MAX = builder
                .comment("按键/视角颠倒持续时间上限 (秒)")
                .defineInRange("brainDamageInvertDurationMax", 5, 1, 30);
        builder.pop();

        // 12. 音效
        builder.comment("客户端危机音效系统").push("sound");
        ENABLE_CRISIS_SOUND = builder
                .comment("是否启用危机分层动态音效（包含心跳、监护仪不规则警报、喘息、骤停重音等）")
                .define("enableCrisisSound", true);
        CRISIS_HEARTBEAT_VOLUME = builder
                .comment("心跳音效全局音量加成倍数",
                         "推荐范围: [0.5, 1.5]")
                .defineInRange("crisisHeartbeatVolume", 1.0, 0.0, 2.0);
        HEARTBEAT_TRIGGER_BLOOD_VOLUME = builder
                .comment("心跳音效触发的血容量上限阈值（低于此值触发）")
                .defineInRange("heartbeatTriggerBloodVolume", 3500, 0, 5000);
        HEARTBEAT_TRIGGER_MAX_HEART_RATE = builder
                .comment("心跳音效触发的高心率上限阈值（高于此值且非运动诱导时触发）")
                .defineInRange("heartbeatTriggerMaxHeartRate", 150, 60, 300);
        HEARTBEAT_TRIGGER_MIN_HEART_RATE = builder
                .comment("心跳音效触发的低心率下限阈值（低于此值触发严重过缓心跳）")
                .defineInRange("heartbeatTriggerMinHeartRate", 45, 10, 100);
        builder.pop();

        // 13. 内出血
        builder.comment("内出血系统").push("internal_bleeding");
        TRIGGER_DAMAGE_THRESHOLD = builder
                .comment("触发内出血的最低伤害量")
                .defineInRange("triggerDamageThreshold", 8, 1, 100);
        RATE_PER_DAMAGE = builder
                .comment("每点伤害对应的内出血速率 (mL/min)")
                .defineInRange("ratePerDamage", 5.0, 0.1, 50.0);
        MAX_INTERNAL_RATE = builder
                .comment("内出血速率上限 (mL/min)")
                .defineInRange("maxInternalRate", 80, 1, 500);
        SELF_HEAL_THRESHOLD = builder
                .comment("四肢内出血的自愈阈值 (mL/min)，低于此值可自愈")
                .defineInRange("selfHealThreshold", 5, 0, 100);
        SELF_HEAL_INTERVAL = builder
                .comment("自愈间隔 (秒)，每N秒衰减1点速率")
                .defineInRange("selfHealInterval", 60, 1, 600);
        CHEST_TRIGGER_CHANCE = builder
                .comment("胸部受击触发内出血的概率")
                .defineInRange("chestTriggerChance", 0.5, 0.0, 1.0);
        LIMB_TRIGGER_CHANCE = builder
                .comment("四肢受击触发内出血的概率")
                .defineInRange("limbTriggerChance", 0.15, 0.0, 1.0);
        FALL_TRIGGER_CHANCE = builder
                .comment("坠落冲击触发内出血的概率")
                .defineInRange("fallTriggerChance", 0.4, 0.0, 1.0);
        LIMB_RATE_MULTIPLIER = builder
                .comment("四肢内出血速率的倍率（相对于胸部）")
                .defineInRange("limbRateMultiplier", 0.5, 0.0, 1.0);
        builder.comment("头部和胸部的内出血不会自愈").pop();

        // 14. 血胸
        builder.comment("血胸系统").push("hemothorax");
        ACCUMULATION_RATIO = builder
                .comment("胸部内出血累积到胸腔的比例 (0~1)")
                .defineInRange("accumulationRatio", 0.7, 0.0, 1.0);
        MAX_CHEST_ACCUMULATION = builder
                .comment("胸腔积血上限 (mL)")
                .defineInRange("maxChestAccumulation", 2000, 100, 5000);
        LEVEL1_THRESHOLD = builder
                .comment("血胸等级1阈值 (mL)")
                .defineInRange("level1Threshold", 500, 0, 2000);
        LEVEL2_THRESHOLD = builder
                .comment("血胸等级2阈值 (mL)")
                .defineInRange("level2Threshold", 1000, 0, 2000);
        LEVEL3_THRESHOLD = builder
                .comment("血胸等级3阈值 (mL)")
                .defineInRange("level3Threshold", 1500, 0, 2000);
        LEVEL4_THRESHOLD = builder
                .comment("血胸等级4阈值 (mL)")
                .defineInRange("level4Threshold", 1800, 0, 2000);
        AUTO_VOMIT_INTERVAL = builder
                .comment("自动吐血间隔 (秒)")
                .defineInRange("autoVomitInterval", 15, 1, 600);
        VOMIT_AMOUNT = builder
                .comment("每次吐血减少的胸腔积血量 (mL)")
                .defineInRange("vomitAmount", 300, 10, 1000);
        LEVEL1_RESPIRATION_MULT = builder
                .comment("血胸等级1的呼吸频率倍率")
                .defineInRange("level1RespirationMultiplier", 0.9, 0.0, 1.0);
        LEVEL2_RESPIRATION_MULT = builder
                .comment("血胸等级2的呼吸频率倍率")
                .defineInRange("level2RespirationMultiplier", 0.75, 0.0, 1.0);
        LEVEL3_RESPIRATION_MULT = builder
                .comment("血胸等级3的呼吸频率倍率")
                .defineInRange("level3RespirationMultiplier", 0.5, 0.0, 1.0);
        LEVEL4_RESPIRATION_MULT = builder
                .comment("血胸等级4的呼吸频率倍率")
                .defineInRange("level4RespirationMultiplier", 0.3, 0.0, 1.0);
        LUNG_CONTUSION_RATIO = builder
                .comment("胸部内出血转化为肺挫伤的比例")
                .defineInRange("lungContusionRatio", 0.30, 0.0, 1.0);
        MIN_VOMIT_LEVEL = builder
                .comment("触发咯血的最低血胸等级")
                .defineInRange("minVomitLevel", 1, 0, 4);
        LEVEL1_VOMIT_AMOUNT = builder
                .comment("血胸1级每次咯血量 (mL)")
                .defineInRange("level1VomitAmount", 100, 10, 1000);
        LEVEL2_VOMIT_AMOUNT = builder
                .comment("血胸2级每次咯血量 (mL)")
                .defineInRange("level2VomitAmount", 200, 10, 1000);
        LEVEL3_VOMIT_AMOUNT = builder
                .comment("血胸3级每次咯血量 (mL)")
                .defineInRange("level3VomitAmount", 300, 10, 1000);
        LEVEL4_VOMIT_AMOUNT = builder
                .comment("血胸4级每次咯血量 (mL)")
                .defineInRange("level4VomitAmount", 400, 10, 1000);
        LEVEL1_BLOOD_PRESSURE_MODIFIER = builder
                .comment("血胸1级对外周阻力TPR的修正系数")
                .defineInRange("level1BloodPressureModifier", 0.95, 0.0, 2.0);
        LEVEL2_BLOOD_PRESSURE_MODIFIER = builder
                .comment("血胸2级对外周阻力TPR的修正系数")
                .defineInRange("level2BloodPressureModifier", 0.85, 0.0, 2.0);
        LEVEL3_BLOOD_PRESSURE_MODIFIER = builder
                .comment("血胸3级对外周阻力TPR的修正系数")
                .defineInRange("level3BloodPressureModifier", 0.70, 0.0, 2.0);
        LEVEL4_BLOOD_PRESSURE_MODIFIER = builder
                .comment("血胸4级对外周阻力TPR的修正系数")
                .defineInRange("level4BloodPressureModifier", 0.50, 0.0, 2.0);
        LEVEL1_HEART_RATE_BONUS = builder
                .comment("血胸1级对心率的加成 (bpm)")
                .defineInRange("level1HeartRateBonus", 5.0, 0.0, 50.0);
        LEVEL2_HEART_RATE_BONUS = builder
                .comment("血胸2级对心率的加成 (bpm)")
                .defineInRange("level2HeartRateBonus", 10.0, 0.0, 50.0);
        LEVEL3_HEART_RATE_BONUS = builder
                .comment("血胸3级对心率的加成 (bpm)")
                .defineInRange("level3HeartRateBonus", 15.0, 0.0, 50.0);
        LEVEL4_HEART_RATE_BONUS = builder
                .comment("血胸4级对心率的加成 (bpm)")
                .defineInRange("level4HeartRateBonus", 20.0, 0.0, 50.0);
        LEVEL1_CONSCIOUSNESS_PENALTY = builder
                .comment("血胸1级对意识的每秒惩罚")
                .defineInRange("level1ConsciousnessPenalty", 0.0, 0.0, 10.0);
        LEVEL2_CONSCIOUSNESS_PENALTY = builder
                .comment("血胸2级对意识的每秒惩罚")
                .defineInRange("level2ConsciousnessPenalty", 0.2, 0.0, 10.0);
        LEVEL3_CONSCIOUSNESS_PENALTY = builder
                .comment("血胸3级对意识的每秒惩罚")
                .defineInRange("level3ConsciousnessPenalty", 0.5, 0.0, 10.0);
        LEVEL4_CONSCIOUSNESS_PENALTY = builder
                .comment("血胸4级对意识的每秒惩罚")
                .defineInRange("level4ConsciousnessPenalty", 1.5, 0.0, 10.0);
        builder.pop();

        // 肺挫伤
        builder.comment("肺挫伤系统").push("lung_contusion");
        CONTUSION_20_RESPIRATION_MULT = builder
                .comment("肺挫伤 (0~20%] 时的呼吸倍率")
                .defineInRange("contusion20RespirationMult", 0.95, 0.0, 1.0);
        CONTUSION_40_RESPIRATION_MULT = builder
                .comment("肺挫伤 (20~40%] 时的呼吸倍率")
                .defineInRange("contusion40RespirationMult", 0.85, 0.0, 1.0);
        CONTUSION_60_RESPIRATION_MULT = builder
                .comment("肺挫伤 (40~60%] 时的呼吸倍率")
                .defineInRange("contusion60RespirationMult", 0.70, 0.0, 1.0);
        CONTUSION_80_RESPIRATION_MULT = builder
                .comment("肺挫伤 (60~80%] 时的呼吸倍率")
                .defineInRange("contusion80RespirationMult", 0.50, 0.0, 1.0);
        CONTUSION_100_RESPIRATION_MULT = builder
                .comment("肺挫伤 (80~100%] 时的呼吸倍率")
                .defineInRange("contusion100RespirationMult", 0.30, 0.0, 1.0);
        CONTUSION_OXYGEN_PENALTY = builder
                .comment("肺挫伤血氧每秒基础惩罚系数")
                .defineInRange("contusionOxygenPenalty", 0.05, 0.0, 2.0);
        builder.pop();

        // 15. 颅内出血
        builder.comment("颅内出血系统").push("intracranial_hemorrhage");
        TRIGGER_CHANCE_AT_10 = builder
                .comment("伤害10~12时的触发概率")
                .defineInRange("triggerChanceAt10", 0.10, 0.0, 1.0);
        TRIGGER_CHANCE_AT_13 = builder
                .comment("伤害13~15时的触发概率")
                .defineInRange("triggerChanceAt13", 0.25, 0.0, 1.0);
        TRIGGER_CHANCE_AT_16 = builder
                .comment("伤害16~18时的触发概率")
                .defineInRange("triggerChanceAt16", 0.50, 0.0, 1.0);
        TRIGGER_CHANCE_AT_19 = builder
                .comment("伤害19~20时的触发概率")
                .defineInRange("triggerChanceAt19", 0.80, 0.0, 1.0);
        TRIGGER_CHANCE_AT_20_PLUS = builder
                .comment("伤害>20时的触发概率")
                .defineInRange("triggerChanceAt20Plus", 1.0, 0.0, 1.0);
        PRIOR_DAMAGE_BONUS = builder
                .comment("已有脑损伤(<70)时的额外触发概率加成")
                .defineInRange("priorDamageBonus", 0.20, 0.0, 1.0);
        IC_RATE_PER_DAMAGE = builder
                .comment("每点伤害对应的颅内出血速率 (mL/min)")
                .defineInRange("ratePerDamage", 1.5, 0.1, 10.0);
        IC_MAX_RATE = builder
                .comment("颅内出血速率上限 (mL/min)")
                .defineInRange("maxRate", 30, 1, 100);
        ONSET_DELAY = builder
                .comment("清醒期时长 (秒)，期间症状隐蔽")
                .defineInRange("onsetDelay", 30, 0, 300);
        IC_LEVEL1_THRESHOLD = builder
                .comment("颅内出血等级1阈值 (mL)")
                .defineInRange("level1Threshold", 20, 0, 150);
        IC_LEVEL2_THRESHOLD = builder
                .comment("颅内出血等级2阈值 (mL)")
                .defineInRange("level2Threshold", 40, 0, 150);
        IC_LEVEL3_THRESHOLD = builder
                .comment("颅内出血等级3阈值 (mL)")
                .defineInRange("level3Threshold", 60, 0, 150);
        IC_LEVEL4_THRESHOLD = builder
                .comment("颅内出血等级4阈值 (mL)")
                .defineInRange("level4Threshold", 80, 0, 150);
        IC_MAX_ACCUMULATION = builder
                .comment("颅内积血上限 (mL)")
                .defineInRange("maxAccumulation", 150, 50, 500);
        IC_LEVEL4_BRAIN_DAMAGE = builder
                .comment("颅内出血等级4时每秒脑健康损失")
                .defineInRange("level4BrainDamage", 5.0, 0.1, 20.0);
        builder.pop();

        // 16. 血压系统
        builder.comment("血压系统").push("blood_pressure");

        NORMAL_SBP = builder
                .comment("正常收缩压 (mmHg)")
                .defineInRange("normalSBP", 120, 60, 200);

        NORMAL_DBP = builder
                .comment("正常舒张压 (mmHg)")
                .defineInRange("normalDBP", 80, 30, 130);

        NORMAL_MAP = builder
                .comment("正常平均动脉压 (mmHg)")
                .defineInRange("normalMAP", 93.0, 40.0, 150.0);

        NORMAL_CO = builder
                .comment("正常心输出量 (L/min)")
                .defineInRange("normalCO", 5.25, 1.0, 15.0);

        MAP_APPROACH_SPEED = builder
                .comment("血压每秒逼近目标值的速度 (0~1)")
                .defineInRange("mapApproachSpeed", 0.05, 0.001, 1.0);

        TPR_NORMAL = builder
                .comment("正常外周阻力系数")
                .defineInRange("tprNormal", 1.0, 0.1, 3.0);

        TPR_COMPENSATED = builder
                .comment("失血代偿期的外周阻力峰值（血液重新分配，血管收缩）")
                .defineInRange("tprCompensated", 1.1, 0.5, 2.0);

        TPR_DECOMPENSATED = builder
                .comment("失血失代偿期的外周阻力最低值（血管麻痹）")
                .defineInRange("tprDecompensated", 0.4, 0.1, 2.0);

        MAP_HYPOTENSION = builder
                .comment("低血压阈值 MAP (mmHg)")
                .defineInRange("mapHypotension", 65.0, 30.0, 100.0);

        MAP_SEVERE_HYPOTENSION = builder
                .comment("严重低血压阈值 MAP (mmHg)")
                .defineInRange("mapSevereHypotension", 50.0, 20.0, 80.0);

        SBP_HYPERTENSIVE_CRISIS = builder
                .comment("高血压危象阈值 SBP (mmHg)")
                .defineInRange("sbpHypertensiveCrisis", 180, 140, 250);

        // 血压对出血的修正系数
        BLEED_MULT_MAP_BELOW_40 = builder
                .comment("MAP <40 时的出血速率修正系数")
                .defineInRange("bleedMultiplierMapBelow40", 0.3, 0.0, 3.0);

        BLEED_MULT_MAP_40_55 = builder
                .comment("MAP 40~55 时的出血速率修正系数")
                .defineInRange("bleedMultiplierMap40to55", 0.5, 0.0, 3.0);

        BLEED_MULT_MAP_55_70 = builder
                .comment("MAP 55~70 时的出血速率修正系数")
                .defineInRange("bleedMultiplierMap55to70", 0.7, 0.0, 3.0);

        BLEED_MULT_MAP_70_85 = builder
                .comment("MAP 70~85 时的出血速率修正系数")
                .defineInRange("bleedMultiplierMap70to85", 0.9, 0.0, 3.0);

        BLEED_MULT_MAP_85_105 = builder
                .comment("MAP 85~105 时的出血速率修正系数（正常）")
                .defineInRange("bleedMultiplierMap85to105", 1.0, 0.0, 3.0);

        BLEED_MULT_MAP_105_125 = builder
                .comment("MAP 105~125 时的出血速率修正系数")
                .defineInRange("bleedMultiplierMap105to125", 1.2, 0.0, 3.0);

        BLEED_MULT_MAP_125_145 = builder
                .comment("MAP 125~145 时的出血速率修正系数")
                .defineInRange("bleedMultiplierMap125to145", 1.4, 0.0, 3.0);

        BLEED_MULT_MAP_145_165 = builder
                .comment("MAP 145~165 时的出血速率修正系数")
                .defineInRange("bleedMultiplierMap145to165", 1.6, 0.0, 3.0);

        BLEED_MULT_MAP_ABOVE_165 = builder
                .comment("MAP ≥165 时的出血速率修正系数")
                .defineInRange("bleedMultiplierMapAbove165", 1.8, 0.0, 3.0);

        // 部位类型系数
        EXTERNAL_MULTIPLIER = builder
                .comment("外出血（四肢/头部）的部位系数")
                .defineInRange("externalMultiplier", 1.0, 0.0, 2.0);

        CHEST_EXTERNAL_MULTIPLIER = builder
                .comment("外出血（胸部）的部位系数")
                .defineInRange("chestExternalMultiplier", 0.8, 0.0, 2.0);

        INTERNAL_MULTIPLIER = builder
                .comment("内出血（非胸腔）的部位系数")
                .defineInRange("internalMultiplier", 0.7, 0.0, 2.0);

        HEMOTHORAX_MULTIPLIER = builder
                .comment("内出血（胸腔/血胸）的部位系数")
                .defineInRange("hemothoraxMultiplier", 0.5, 0.0, 2.0);

        INTRACRANIAL_MULTIPLIER = builder
                .comment("颅内出血的部位系数")
                .defineInRange("intracranialMultiplier", 0.3, 0.0, 2.0);

        // 意识影响系数
        CONS_PENALTY_MAP_60 = builder
                .comment("MAP 60~70 时每秒意识惩罚")
                .defineInRange("consPenaltyMap60", 0.1, 0.0, 10.0);

        CONS_PENALTY_MAP_50 = builder
                .comment("MAP 50~60 时每秒意识惩罚")
                .defineInRange("consPenaltyMap50", 0.3, 0.0, 10.0);

        CONS_PENALTY_MAP_40 = builder
                .comment("MAP 40~50 时每秒意识惩罚")
                .defineInRange("consPenaltyMap40", 0.8, 0.0, 10.0);

        CONS_PENALTY_MAP_BELOW_40 = builder
                .comment("MAP <40 时每秒意识惩罚")
                .defineInRange("consPenaltyMapBelow40", 2.0, 0.0, 20.0);

        CONS_PENALTY_SBP_180 = builder
                .comment("SBP 180~200 时每秒意识惩罚")
                .defineInRange("consPenaltySbp180", 0.5, 0.0, 10.0);

        CONS_PENALTY_SBP_200 = builder
                .comment("SBP 200~220 时每秒意识惩罚")
                .defineInRange("consPenaltySbp200", 1.5, 0.0, 10.0);

        CONS_PENALTY_SBP_220 = builder
                .comment("SBP >220 时每秒意识惩罚")
                .defineInRange("consPenaltySbp220", 3.0, 0.0, 20.0);

        // 脑健康影响系数
        BRAIN_PENALTY_MAP_BELOW_50 = builder
                .comment("MAP <50 时每秒脑健康损失系数")
                .defineInRange("brainPenaltyMapBelow50", 0.5, 0.0, 5.0);

        BRAIN_PENALTY_SBP_ABOVE_200 = builder
                .comment("SBP >200 时每秒脑健康损失系数")
                .defineInRange("brainPenaltySbpAbove200", 0.3, 0.0, 5.0);

        builder.pop(); // blood_pressure

        // 13. 感染系统
        builder.comment("感染系统").push("infection");

        INFECTION_BASE_RATE = builder
                .comment("未包扎伤口的每秒感染增长")
                .defineInRange("baseInfectionRate", 0.12, 0.0, 2.0);

        INFECTION_BANDAGE_REDUCTION = builder
                .comment("包扎后保留的感染增长率比例（0.3 = 降低70%）")
                .defineInRange("bandageReduction", 0.3, 0.0, 1.0);

        INFECTION_WET_BONUS = builder
                .comment("潮湿环境的感染加成")
                .defineInRange("wetBonus", 0.3, 0.0, 2.0);

        INFECTION_FRACTURE_BONUS = builder
                .comment("骨折伤口感染加成")
                .defineInRange("fractureBonus", 0.5, 0.0, 2.0);

        INFECTION_WINDOW_PERIOD = builder
                .comment("受伤后前 N 秒不产生感染（窗口期）")
                .defineInRange("windowPeriod", 30, 0, 300);

        INFECTION_SYSTEMIC_THRESHOLD = builder
                .comment("触发全身扩散的局部感染值阈值")
                .defineInRange("systemicThreshold", 80, 20, 100);

        builder.pop();

        builder.comment("免疫治疗系统").push("immunity");

        IMMUNE_BASE_RATE = builder
            .comment("基础免疫治疗速率（每秒降低的感染值）",
                     "100 点感染值从满到 0 需要约 1/0.10 = 1000 秒 ≈ 17 分钟")
            .defineInRange("immuneBaseRate", 0.10, 0.0, 5.0);

        IMMUNE_HUNGER_HIGH_MULT = builder
            .comment("饱食度 ≥ 18（饱腹）时的免疫治疗倍率")
            .defineInRange("immuneHungerHighMult", 2.5, 0.0, 10.0);

        IMMUNE_HUNGER_MID_MULT = builder
            .comment("饱食度 14~17 时的免疫治疗倍率")
            .defineInRange("immuneHungerMidMult", 1.5, 0.0, 10.0);

        IMMUNE_HUNGER_LOW_MULT = builder
            .comment("饱食度 8~13（正常）时的免疫治疗倍率")
            .defineInRange("immuneHungerLowMult", 1.0, 0.0, 10.0);

        IMMUNE_HUNGER_CRIT_MULT = builder
            .comment("饱食度 3~7（饥饿）时的免疫治疗倍率")
            .defineInRange("immuneHungerCritMult", 0.5, 0.0, 10.0);

        IMMUNE_HUNGER_STARVE_MULT = builder
            .comment("饱食度 < 3（濒饿）时的免疫治疗倍率")
            .defineInRange("immuneHungerStarveMult", 0.2, 0.0, 10.0);

        DISINFECTANT_DURATION = builder
            .comment("消毒剂效果持续秒数")
            .defineInRange("disinfectantDuration", 60, 1, 600);

        DISINFECTANT_IMMUNE_MULT = builder
            .comment("消毒剂生效期间的免疫治疗加速倍率")
            .defineInRange("disinfectantImmuneMult", 2.0, 1.0, 10.0);

        DISINFECTANT_BLOCKS_GROWTH = builder
            .comment("消毒剂生效期间是否完全阻止感染值增长")
            .define("disinfectantBlocksGrowth", true);

        builder.pop();

        // 14. SIRS 全身炎症反应
        builder.comment("SIRS 全身炎症反应").push("sirs");

        SIRS_LIMB_THRESHOLD = builder
                .comment("SIRS 触发的最高肢体感染值阈值")
                .defineInRange("limbThreshold", 40, 10, 100);

        SIRS_TOTAL_THRESHOLD = builder
                .comment("SIRS 触发的全身感染总值阈值")
                .defineInRange("totalThreshold", 60, 10, 300);

        SIRS_PROGRESSION_INTERVAL = builder
                .comment("SIRS 晋级间隔（秒），按环境倍率调整")
                .defineInRange("progressionInterval", 180, 30, 600);

        SIRS_RECOVERY_LIMB_THRESHOLD = builder
                .comment("SIRS 开始降级的最高肢体感染值门槛（必须低于此值才开始降级）")
                .defineInRange("recoveryLimbThreshold", 20, 0, 100);

        SIRS_RECOVERY_TOTAL_THRESHOLD = builder
                .comment("SIRS 开始降级的全身感染总值门槛")
                .defineInRange("recoveryTotalThreshold", 30, 0, 300);

        SIRS_RECOVERY_INTERVAL_L4_TO_L3 = builder
                .comment("SIRS 4 级降为 3 级所需时间（秒）")
                .defineInRange("recoveryIntervalL4toL3", 300, 30, 3600);

        SIRS_RECOVERY_INTERVAL_L3_TO_L2 = builder
                .comment("SIRS 3 级降为 2 级所需时间（秒）")
                .defineInRange("recoveryIntervalL3toL2", 240, 30, 3600);

        SIRS_RECOVERY_INTERVAL_L2_TO_L1 = builder
                .comment("SIRS 2 级降为 1 级所需时间（秒）")
                .defineInRange("recoveryIntervalL2toL1", 180, 30, 3600);

        SIRS_RECOVERY_INTERVAL_L1_TO_L0 = builder
                .comment("SIRS 1 级降为 0 级所需时间（秒）")
                .defineInRange("recoveryIntervalL1toL0", 120, 30, 3600);

        SIRS_ENV_WATER = builder
                .comment("水下环境对 SIRS 晋级的倍率（倍率越小，SIRS 晋级越快）")
                .defineInRange("envWater", 0.7, 0.1, 2.0);

        SIRS_ENV_CAVE = builder
                .comment("洞穴深处环境对 SIRS 晋级的倍率（倍率越小，SIRS 晋级越快）")
                .defineInRange("envCave", 0.85, 0.1, 2.0);

        SIRS_ENV_NETHER = builder
                .comment("下界/末地环境对 SIRS 晋级的倍率（倍率越小，SIRS 晋级越快）")
                .defineInRange("envNether", 0.9, 0.1, 2.0);

        SIRS_ENV_RAIN = builder
                .comment("淋雨环境对 SIRS 晋级的倍率（倍率越小，SIRS 晋级越快）")
                .defineInRange("envRain", 0.8, 0.1, 2.0);

        SIRS_ENV_INDOOR = builder
                .comment("室内环境对 SIRS 晋级的倍率（倍率越小，SIRS 晋级越快）")
                .defineInRange("envIndoor", 1.2, 0.5, 3.0);

        SIRS_ENV_NORMAL = builder
                .comment("正常户外环境对 SIRS 晋级的倍率（倍率越小，SIRS 晋级越快）")
                .defineInRange("envNormal", 1.0, 0.5, 3.0);

        SIRS_HEART_BONUS_PER_LEVEL = builder
                .comment("SIRS 每级对心率的加成 (bpm)")
                .defineInRange("heartBonusPerLevel", 15, 0, 50);

        SIRS_RESP_BONUS_PER_LEVEL = builder
                .comment("SIRS 每级对呼吸的加成 (次/分)")
                .defineInRange("respBonusPerLevel", 4, 0, 20);

        SIRS_OXYGEN_PENALTY_PER_LEVEL = builder
                .comment("SIRS 每级对血氧的每秒惩罚")
                .defineInRange("oxygenPenaltyPerLevel", 0.1, 0.0, 5.0);

        SIRS_BLOOD_LOSS_L2 = builder
                .comment("SIRS 2 级每秒血容量损失 (mL)")
                .defineInRange("bloodLossL2", 0.1, 0.0, 10.0);

        SIRS_BLOOD_LOSS_L3 = builder
                .comment("SIRS 3 级每秒血容量损失 (mL)")
                .defineInRange("bloodLossL3", 0.2, 0.0, 10.0);

        SIRS_BLOOD_LOSS_L4 = builder
                .comment("SIRS 4 级每秒血容量损失 (mL)")
                .defineInRange("bloodLossL4", 0.3, 0.0, 10.0);

        SIRS_TPR_MODIFIER_L1 = builder
                .comment("SIRS 1 级的外周阻力倍率")
                .defineInRange("sirsTprModifierL1", 0.95, 0.1, 1.5);

        SIRS_TPR_MODIFIER_L2 = builder
                .comment("SIRS 2 级的外周阻力倍率")
                .defineInRange("sirsTprModifierL2", 0.85, 0.1, 1.5);

        SIRS_TPR_MODIFIER_L3 = builder
                .comment("SIRS 3 级的外周阻力倍率")
                .defineInRange("sirsTprModifierL3", 0.70, 0.1, 1.5);

        SIRS_TPR_MODIFIER_L4 = builder
                .comment("SIRS 4 级的外周阻力倍率")
                .defineInRange("sirsTprModifierL4", 0.50, 0.1, 1.5);

        builder.pop();

        // 17. 伤害类型细化
        builder.comment("伤害类型细化配置").push("damage_type");
        PIERCING_EXTERNAL_RATIO = builder
                .comment("穿刺伤外出血比例")
                .defineInRange("piercingExternalRatio", 0.80, 0.0, 1.0);
        PIERCING_INTERNAL_RATIO = builder
                .comment("穿刺伤内出血比例")
                .defineInRange("piercingInternalRatio", 0.20, 0.0, 1.0);
        PIERCING_FRACTURE_OPEN_CHANCE = builder
                .comment("穿刺伤引发开放性骨折概率")
                .defineInRange("piercingFractureOpenChance", 1.00, 0.0, 1.0);

        SLASHING_EXTERNAL_RATIO = builder
                .comment("斩击伤外出血比例")
                .defineInRange("slashingExternalRatio", 0.95, 0.0, 1.0);
        SLASHING_INTERNAL_RATIO = builder
                .comment("斩击伤内出血比例")
                .defineInRange("slashingInternalRatio", 0.05, 0.0, 1.0);
        SLASHING_FRACTURE_OPEN_CHANCE = builder
                .comment("斩击伤引发开放性骨折概率")
                .defineInRange("slashingFractureOpenChance", 0.80, 0.0, 1.0);

        BLUNT_EXTERNAL_RATIO = builder
                .comment("钝击伤外出血比例")
                .defineInRange("bluntExternalRatio", 0.80, 0.0, 1.0);
        BLUNT_INTERNAL_RATIO = builder
                .comment("钝击伤内出血比例")
                .defineInRange("bluntInternalRatio", 0.20, 0.0, 1.0);
        BLUNT_FRACTURE_OPEN_CHANCE = builder
                .comment("钝击伤引发开放性骨折概率")
                .defineInRange("bluntFractureOpenChance", 0.00, 0.0, 1.0);

        MIXED_EXTERNAL_RATIO = builder
                .comment("混合伤外出血比例")
                .defineInRange("mixedExternalRatio", 0.70, 0.0, 1.0);
        MIXED_INTERNAL_RATIO = builder
                .comment("混合伤内出血比例")
                .defineInRange("mixedInternalRatio", 0.30, 0.0, 1.0);
        MIXED_FRACTURE_OPEN_CHANCE = builder
                .comment("混合伤引发开放性骨折概率")
                .defineInRange("mixedFractureOpenChance", 0.50, 0.0, 1.0);

        EXPLOSION_EXTERNAL_RATIO = builder
                .comment("爆炸伤外出血比例")
                .defineInRange("explosionExternalRatio", 0.30, 0.0, 1.0);
        EXPLOSION_INTERNAL_RATIO = builder
                .comment("爆炸伤内出血比例")
                .defineInRange("explosionInternalRatio", 0.70, 0.0, 1.0);
        EXPLOSION_HEAD_RATIO = builder
                .comment("爆炸伤分配到头部的比例")
                .defineInRange("explosionHeadRatio", 0.15, 0.0, 1.0);
        EXPLOSION_CHEST_RATIO = builder
                .comment("爆炸伤分配到胸部的比例")
                .defineInRange("explosionChestRatio", 0.35, 0.0, 1.0);
        EXPLOSION_ARM_RATIO = builder
                .comment("爆炸伤分配到单侧手臂的比例（每只手分别分配）")
                .defineInRange("explosionArmRatio", 0.13, 0.0, 1.0);
        EXPLOSION_LEG_RATIO = builder
                .comment("爆炸伤分配到单侧腿部的比例（每条腿分别分配）")
                .defineInRange("explosionLegRatio", 0.12, 0.0, 1.0);
        builder.pop();

        // 18. 骨折系统
        builder.comment("骨折系统配置").push("fracture");
        MIN_DAMAGE_FOR_FRACTURE = builder
                .comment("骨折判定最低伤害")
                .defineInRange("minDamageForFracture", 8, 1, 100);
        BASE_FRACTURE_CHANCE = builder
                .comment("基础骨折概率")
                .defineInRange("baseFractureChance", 0.20, 0.0, 1.0);
        HIGH_DAMAGE_THRESHOLD = builder
                .comment("高伤害骨折阈值")
                .defineInRange("highDamageThreshold", 12, 1, 100);
        HIGH_DAMAGE_FRACTURE_CHANCE = builder
                .comment("高伤害骨折概率")
                .defineInRange("highDamageFractureChance", 0.40, 0.0, 1.0);
        FALL_MIN_HEIGHT_FOR_FRACTURE = builder
                .comment("坠落骨折最低格数")
                .defineInRange("fallMinHeightForFracture", 5, 1, 100);
        FALL_FRACTURE_CHANCE_PER_BLOCK = builder
                .comment("坠落每格增加骨折概率")
                .defineInRange("fallFractureChancePerBlock", 0.05, 0.0, 1.0);
        EXPLOSION_FRACTURE_CHANCE = builder
                .comment("爆炸骨折概率")
                .defineInRange("explosionFractureChance", 0.50, 0.0, 1.0);
        SPLINT_HEAL_TIME = builder
                .comment("夹板愈合时间（秒）")
                .defineInRange("splintHealTime", 600, 1, 7200);
        FRACTURE_PAIN_LEVEL = builder
                .comment("骨折瞬间疼痛等级")
                .defineInRange("fracturePainLevel", 7, 0, 10);
        SPLINT_PAIN_DECAY_INTERVAL = builder
                .comment("夹板后疼痛衰减间隔（秒/级）")
                .defineInRange("splintPainDecayInterval", 30, 1, 3600);
        NO_SPLINT_PAIN_DECAY_INTERVAL = builder
                .comment("未夹板疼痛衰减间隔（秒/级）")
                .defineInRange("noSplintPainDecayInterval", 60, 1, 3600);
        FRACTURE_BLEED_PER_DAMAGE = builder
                .comment("骨折时每点伤害对应的内出血速率 (mL/min)")
                .defineInRange("fractureBleedPerDamage", 5.0, 0.5, 50.0);
        FRACTURE_BLEED_MAX = builder
                .comment("骨折内出血速率上限 (mL/min)")
                .defineInRange("fractureBleedMax", 200.0, 10.0, 500.0);
        FRACTURE_LIMB_MULTIPLIER = builder
                .comment("四肢骨折的内出血倍率")
                .defineInRange("fractureLimbMultiplier", 1.0, 0.0, 3.0);
        FRACTURE_CHEST_MULTIPLIER = builder
                .comment("胸部骨折的内出血倍率")
                .defineInRange("fractureChestMultiplier", 0.5, 0.0, 3.0);
        OPEN_FRACTURE_INFECTION_RATE = builder
                .comment("开放性骨折每秒额外感染值")
                .defineInRange("openFractureInfectionRate", 0.05, 0.0, 10.0);
        SECONDARY_TRAUMA_INTERVAL = builder
                .comment("二次创伤触发间隔（秒）")
                .defineInRange("secondaryTraumaInterval", 5, 1, 300);
        SECONDARY_TRAUMA_INTERNAL_BLEED = builder
                .comment("二次创伤内出血增量 (mL/min)")
                .defineInRange("secondaryTraumaInternalBleed", 10, 0, 500);
        SECONDARY_TRAUMA_MAX_INTERNAL_BLEED = builder
                .comment("二次创伤导致的肢体内出血上限 (mL/min)，模拟肌筋膜间隙内骨折血肿压迫自限止血机制")
                .defineInRange("secondaryTraumaMaxInternalBleed", 80, 0, 1000);
        SECONDARY_TRAUMA_EXTERNAL_BLEED = builder
                .comment("二次创伤外出血增量 (mL/min)")
                .defineInRange("secondaryTraumaExternalBleed", 5, 0, 500);
        SECONDARY_TRAUMA_MAX_EXTERNAL_BLEED = builder
                .comment("二次创伤导致的开放性骨折外出血上限 (mL/min)")
                .defineInRange("secondaryTraumaMaxExternalBleed", 60, 0, 1000);
        SECONDARY_TRAUMA_PAIN_INCREASE = builder
                .comment("二次创伤疼痛增量")
                .defineInRange("secondaryTraumaPainIncrease", 1, 0, 10);
        SECONDARY_TRAUMA_HEAL_TIME_PENALTY = builder
                .comment("二次创伤愈合倒退时间（秒）")
                .defineInRange("secondaryTraumaHealTimePenalty", 30, 0, 600);
        LEG_FRACTURE_NO_SPLINT_SPEED_MULTIPLIER = builder
                .comment("腿部未夹板移动速度倍率")
                .defineInRange("legFractureNoSplintSpeedMultiplier", 0.2, 0.0, 1.0);
        LEG_FRACTURE_SPLINT_SPEED_MULTIPLIER = builder
                .comment("腿部已夹板移动速度倍率")
                .defineInRange("legFractureSplintSpeedMultiplier", 0.5, 0.0, 1.0);
        LEG_FRACTURE_JUMP_ALLOWED = builder
                .comment("腿部骨折是否允许跳跃")
                .define("legFractureJumpAllowed", false);
        ARM_FRACTURE_NO_SPLINT_MINING_MULTIPLIER = builder
                .comment("手臂未夹板挖掘速度倍率")
                .defineInRange("armFractureNoSplintMiningMultiplier", 0.3, 0.0, 1.0);
        ARM_FRACTURE_NO_SPLINT_ATTACK_MULTIPLIER = builder
                .comment("手臂未夹板攻击力倍率")
                .defineInRange("armFractureNoSplintAttackMultiplier", 0.3, 0.0, 1.0);
        ARM_FRACTURE_SPLINT_MINING_MULTIPLIER = builder
                .comment("手臂已夹板挖掘速度倍率")
                .defineInRange("armFractureSplintMiningMultiplier", 0.7, 0.0, 1.0);
        ARM_FRACTURE_SPLINT_ATTACK_MULTIPLIER = builder
                .comment("手臂已夹板攻击力倍率")
                .defineInRange("armFractureSplintAttackMultiplier", 0.7, 0.0, 1.0);
        builder.pop();

        // 19. 断肢系统
        builder.comment("断肢系统配置：四肢受极大伤害按概率截断，头/躯干永不截肢").push("amputation");
        AMPUTATION_ENABLED = builder
                .comment("断肢系统总开关")
                .define("amputationEnabled", true);
        AMPUTATION_DAMAGE_THRESHOLD = builder
                .comment("断肢判定最低单次伤害（该部位受到的伤害值）")
                .defineInRange("amputationDamageThreshold", 15, 1, 100);
        AMPUTATION_CHANCE = builder
                .comment("达标后基础断肢概率")
                .defineInRange("amputationChance", 0.10, 0.0, 1.0);
        AMPUTATION_SCALES_WITH_DAMAGE = builder
                .comment("断肢概率是否随超出阈值的伤害线性提升")
                .define("amputationScalesWithDamage", true);
        AMPUTATION_CHANCE_PER_DAMAGE = builder
                .comment("阈值之上每点额外伤害增加的断肢概率")
                .defineInRange("amputationChancePerDamage", 0.01, 0.0, 1.0);
        AMPUTATION_CHANCE_CAP = builder
                .comment("断肢概率上限")
                .defineInRange("amputationChanceCap", 0.60, 0.0, 1.0);
        AMPUTATION_BLEEDING = builder
                .comment("断口瞬间外出血量 (mL，直接加入断肢部位出血池)")
                .defineInRange("amputationBleeding", 800, 0, 5000);
        AMPUTATION_ADJACENT_PAIN = builder
                .comment("相邻部位承受的剧痛增量")
                .defineInRange("amputationAdjacentPain", 8, 0, 10);
        AMPUTATION_ADJACENT_BLEEDING = builder
                .comment("相邻部位瞬间外出血增量 (mL)")
                .defineInRange("amputationAdjacentBleeding", 200, 0, 5000);
        AMPUTATION_ADJACENT_INTERNAL_BLEEDING = builder
                .comment("相邻部位内出血速率增量 (mL/min)")
                .defineInRange("amputationAdjacentInternalBleeding", 100, 0, 1000);
        AMPUTATION_LEG_SPEED_MULTIPLIER = builder
                .comment("单腿截肢移动速度倍率")
                .defineInRange("amputationLegSpeedMultiplier", 0.15, 0.0, 1.0);
        AMPUTATION_DOUBLE_LEG_SPEED_MULTIPLIER = builder
                .comment("双腿截肢移动速度倍率（保留微小挪动）")
                .defineInRange("amputationDoubleLegSpeedMultiplier", 0.03, 0.0, 1.0);
        AMPUTATION_LEG_JUMP_ALLOWED = builder
                .comment("腿部截肢后是否允许跳跃")
                .define("amputationLegJumpAllowed", false);
        AMPUTATION_DOUBLE_LEG_PRONE = builder
                .comment("双腿截肢后是否强制卧倒姿态（防止躯干悬浮）")
                .define("amputationDoubleLegProne", true);
        AMPUTATION_ARM_MINING_MULTIPLIER = builder
                .comment("手臂截肢后挖掘速度倍率")
                .defineInRange("amputationArmMiningMultiplier", 0.0, 0.0, 1.0);
        AMPUTATION_ARM_ATTACK_MULTIPLIER = builder
                .comment("手臂截肢后攻击力倍率")
                .defineInRange("amputationArmAttackMultiplier", 0.0, 0.0, 1.0);
        AMPUTATION_ARM_INTERACTION_ALLOWED = builder
                .comment("手臂截肢后是否允许另一条手交互/使用物品（false=双手都断时禁止交互）")
                .define("amputationArmInteractionAllowed", true);
        builder.pop();

        // 19. 连续仲裁 buff 系统
        builder.comment("连续仲裁 buff 系统：伤情→能力倍率连续插值，属性仲裁与感知药水统一配置").push("arbitrated_buffs");
        ARBITER_MIN_MULTIPLIER = builder
                .comment("属性仲裁器每维度倍率下限（防归零保护）")
                .defineInRange("arbiterMinMultiplier", 0.05, 0.0, 1.0);
        POTION_HYSTERESIS_TICKS = builder
                .comment("感知药水等级切换的滞回缓冲（tick），防边界抖动")
                .defineInRange("potionHysteresisTicks", 40, 0, 200);
        PAIN_LEG_SPEED_SLOPE = builder
                .comment("腿部疼痛每级对移速的线性斜率（1 - slope*p，p 为体感疼痛 0~55）")
                .defineInRange("painLegSpeedSlope", 0.006, 0.0, 1.0);
        PAIN_ARM_ATTACK_SLOPE = builder
                .comment("手臂疼痛每级对攻击力的线性斜率")
                .defineInRange("painArmAttackSlope", 0.008, 0.0, 1.0);
        PAIN_ARM_SPEED_SLOPE = builder
                .comment("手臂疼痛每级对攻速的线性斜率")
                .defineInRange("painArmSpeedSlope", 0.006, 0.0, 1.0);
        PAIN_HEAD_BLINDNESS_SLOPE = builder
                .comment("头部疼痛每级对失明药水等级的插值斜率（按 0~10 疼痛线性）")
                .defineInRange("painHeadBlindnessSlope", 0.15, 0.0, 1.0);
        PAIN_CHEST_NAUSEA_SLOPE = builder
                .comment("胸部疼痛每级对反胃药水等级的插值斜率")
                .defineInRange("painChestNauseaSlope", 0.15, 0.0, 1.0);
        BLOOD_LOSS_SPEED_SLOPE = builder
                .comment("失血每 1% 对移速的线性斜率（b 为失血百分比 0~100）")
                .defineInRange("bloodLossSpeedSlope", 0.5, 0.0, 1.0);
        BLOOD_LOSS_ATTACK_SLOPE = builder
                .comment("失血每 1% 对攻击力的线性斜率")
                .defineInRange("bloodLossAttackSlope", 0.5, 0.0, 1.0);
        BLOOD_LOSS_DIG_SLOPE = builder
                .comment("失血每 1% 对挖掘（DIG_SLOWDOWN 插值）的线性斜率")
                .defineInRange("bloodLossDigSlope", 0.4, 0.0, 1.0);
        HYPOXIA_ONSET_SPO2 = builder
                .comment("低血氧乏力开始阈值（SpO2 低于此值开始惩罚）")
                .defineInRange("hypoxiaOnsetSpo2", 90, 50, 100);
        HYPOXIA_MAX_PENALTY = builder
                .comment("SpO2 到 0 时的最大能力衰减比例（0.7 = 倍率压到 0.3）")
                .defineInRange("hypoxiaMaxPenalty", 0.7, 0.0, 1.0);
        SIRS_FATIGUE_PER_LEVEL = builder
                .comment("SIRS 每级的疲劳衰减比例（倍率 = 1 - 系数*等级/4，脓毒症休克另有下限）")
                .defineInRange("sirsFatiguePerLevel", 0.08, 0.0, 0.25);
        SEPSIS_MIN_MULTIPLIER = builder
                .comment("脓毒症（SIRS L4）全身倍率下限")
                .defineInRange("sepsisMinMultiplier", 0.3, 0.0, 1.0);
        CONSC_IMPACT_FACTOR = builder
                .comment("意识对攻击/挖掘能力的影响系数（倍率 = 1 - factor*(1 - c/100)）")
                .defineInRange("conscImpactFactor", 0.8, 0.0, 1.0);
        CONSC_IMPACT_UNCONSCIOUS = builder
                .comment("意识为 0（昏迷）时的能力倍率，替代旧 weakness 255 硬压制")
                .defineInRange("conscImpactUnconscious", 0.05, 0.0, 1.0);
        HEMOTHORAX_L3_SPEED_MULTIPLIER = builder
                .comment("血胸 L3 移速倍率")
                .defineInRange("hemothoraxL3SpeedMultiplier", 0.5, 0.0, 1.0);
        HEMOTHORAX_L4_SPEED_MULTIPLIER = builder
                .comment("血胸 L4 移速倍率")
                .defineInRange("hemothoraxL4SpeedMultiplier", 0.05, 0.0, 1.0);
        CONTUSION_FATIGUE_SLOPE = builder
                .comment("肺挫伤每 1% 对移速/攻速的疲劳斜率")
                .defineInRange("contusionFatigueSlope", 0.4, 0.0, 1.0);
        IC_BLINDNESS_L3 = builder
                .comment("颅内出血 L3 失明药水等级")
                .defineInRange("icBlindnessL3", 1, 0, 5);
        IC_BLINDNESS_L4 = builder
                .comment("颅内出血 L4 失明药水等级")
                .defineInRange("icBlindnessL4", 2, 0, 5);
        PERCEIVED_PAIN_BLINDNESS_THRESHOLD = builder
                .comment("体感疼痛触发失明插值的阈值（体感疼痛 0~55）")
                .defineInRange("perceivedPainBlindnessThreshold", 40, 0, 55);
        PERCEIVED_PAIN_NAUSEA_THRESHOLD = builder
                .comment("体感疼痛触发反胃插值的阈值")
                .defineInRange("perceivedPainNauseaThreshold", 35, 0, 55);
        DIG_SLOWDOWN_FRACTURE_MAX_LEVEL = builder
                .comment("臂骨折（未夹板）对应的挖掘迟缓药水最大等级（插值：夹板状态按倍率比例折算）")
                .defineInRange("digSlowdownFractureMaxLevel", 3, 0, 10);
        builder.pop();

        // ================= 中毒系统 (toxin) =================
        builder.comment("中毒系统：poison 伤害转毒素条（0~100），全身性生理影响（呼吸/血氧/意识/血压/凝血/感染/心律）").push("toxin");
        TOXIN_ACCUMULATION_RATE = builder
                .comment("每次 poison 伤害事件转毒素条的累加系数（毒素 += 伤害量 × 系数）",
                         "原版 Poison I 每 1.25s 扣 1 点伤害，Poison II 0.6s 扣 1 点",
                         "系数 6 时 Poison I 约 8 分钟充满，Poison II 约 4 分钟充满")
                .defineInRange("accumulationRate", 6.0, 0.0, 100.0);
        TOXIN_DECAY_RATE = builder
                .comment("毒素自然代谢速率（每秒循环扣减，默认 0.25 = 15点/分钟）")
                .defineInRange("decayRate", 0.25, 0.0, 10.0);
        TOXIN_IMMUNE_DECAY_MULT = builder
                .comment("饱食度≥18 时的代谢加成倍率（免疫健康态代谢更快）")
                .defineInRange("immuneDecayMult", 2.0, 0.1, 10.0);
        TOXIN_MILD_THRESHOLD = builder
                .comment("轻度中毒阈值：达到后反胃/腹痛（nausea 插值）")
                .defineInRange("mildThreshold", 20.0, 0.0, 100.0);
        TOXIN_MID_THRESHOLD = builder
                .comment("中度中毒阈值：达到后呼吸抑制、血氧下降、意识下降、血压下降（分布性休克）")
                .defineInRange("midThreshold", 40.0, 0.0, 100.0);
        TOXIN_SEVERE_THRESHOLD = builder
                .comment("重度中毒阈值：达到后感染值抬升（→SIRS 链）、凝血障碍")
                .defineInRange("severeThreshold", 70.0, 0.0, 100.0);
        TOXIN_CRITICAL_THRESHOLD = builder
                .comment("濒死中毒阈值：达到后室颤概率加倍")
                .defineInRange("criticalThreshold", 90.0, 0.0, 100.0);
        TOXIN_NAUSEA_SLOPE = builder
                .comment("轻度反胃药水插值斜率（每点毒素升的等级数，毒素100时约4级）")
                .defineInRange("nauseaSlope", 0.05, 0.0, 1.0);
        TOXIN_RESP_RATE_SLOPE = builder
                .comment("毒素呼吸频率上抬斜率（浅快呼吸，每点毒素 +N 次/分，毒素100时 +6）")
                .defineInRange("respRateSlope", 0.1, 0.0, 5.0);
        TOXIN_RESP_SUPPRESSION_SLOPE = builder
                .comment("毒素换气效率折减斜率（呼吸肌抑制，每点毒素乘 1-slope×超阈量）")
                .defineInRange("respSuppressionSlope", 0.003, 0.0, 0.1);
        TOXIN_CONSCIOUSNESS_SLOPE = builder
                .comment("毒素直接意识惩罚斜率（每点毒素每秒扣意识，毒素100时 3.0/s；血氧传导惩罚另计）")
                .defineInRange("consciousnessSlope", 0.05, 0.0, 1.0);
        TOXIN_TPR_SLOPE = builder
                .comment("毒素血管扩张 TPR 斜率（分布性休克，每点毒素乘 1-slope×超阈量，毒素100时 ×0.76）")
                .defineInRange("tprSlope", 0.004, 0.0, 0.1);
        TOXIN_STROKE_VOLUME_SLOPE = builder
                .comment("毒素心肌抑制每搏输出量斜率（每点毒素乘 1-slope×超阈量）")
                .defineInRange("strokeVolumeSlope", 0.004, 0.0, 0.1);
        TOXIN_INFECTION_RATE = builder
                .comment("重度中毒时每秒抬升的感染累加值（分摊到全部肢体，走现有感染→SIRS 自动晋级链）")
                .defineInRange("infectionRate", 0.5, 0.0, 10.0);
        TOXIN_VFIB_CHANCE = builder
                .comment("重度中毒每秒触发毒素性室颤的基础概率（濒死阈值以上加倍）")
                .defineInRange("vfibChance", 0.005, 0.0, 1.0);
        TOXIN_CLOTTING_TIME_MULT = builder
                .comment("重度中毒时自然凝血时间倍率（毒素诱发消耗性凝血病）")
                .defineInRange("clottingTimeMult", 1.5, 0.1, 10.0);
        builder.pop();

        // ================= 烧伤系统 (burn) =================
        builder.comment("烧伤系统：火伤负荷累加→深度定型（I/浅II/深II/III度），TBSA 驱动渗漏休克/感染/凝血/代谢").push("burn");
        BURN_LOAD_PER_DAMAGE = builder
                .comment("每次火伤事件的烧伤负荷累加系数（负荷 += 伤害量 × 系数；盔甲部位减半）",
                         "原版着火 2伤/s、岩浆 8伤/s：系数1时烧10s≈III度、岩浆2.5s≈III度")
                .defineInRange("loadPerDamage", 1.0, 0.0, 10.0);
        BURN_CONVERSION_RATE = builder
                .comment("热力期负荷缓冲转正速率（每秒转正的负荷点数，模拟余热持续损伤）")
                .defineInRange("conversionRate", 1.0, 0.1, 20.0);
        BURN_AREA_PER_DAMAGE = builder
                .comment("每次火伤事件的面积累加率（面积 += 伤害量 × 系数，封顶1.0）")
                .defineInRange("areaPerDamage", 0.03, 0.0, 1.0);
        BURN_HEAT_WINDOW = builder
                .comment("热力期时长（秒）：窗口内浸水/雨可冲刷未转正负荷，阻止烧伤加深")
                .defineInRange("heatWindow", 15, 1, 600);
        BURN_IRRIGATION_RATE = builder
                .comment("浸水/雨冲刷未转正负荷的速率（每秒冲刷掉的负荷点数）")
                .defineInRange("irrigationRate", 3.0, 0.1, 50.0);
        BURN_DEGREE_I = builder
                .comment("I度烧伤定型阈值（转正负荷达到即升级，不可逆）")
                .defineInRange("degreeI", 2.0, 0.1, 100.0);
        BURN_DEGREE_SHALLOW_II = builder
                .comment("浅II度定型阈值")
                .defineInRange("degreeShallowII", 6.0, 0.1, 100.0);
        BURN_DEGREE_DEEP_II = builder
                .comment("深II度定型阈值")
                .defineInRange("degreeDeepII", 12.0, 0.1, 100.0);
        BURN_DEGREE_III = builder
                .comment("III度定型阈值")
                .defineInRange("degreeIII", 20.0, 0.1, 100.0);
        BURN_TBSA_WEIGHT_HEAD = builder
                .comment("头颈部 TBSA 权重（九分法：头9%）")
                .defineInRange("tbsaWeightHead", 0.09, 0.0, 1.0);
        BURN_TBSA_WEIGHT_CHEST = builder
                .comment("躯干 TBSA 权重（九分法：36%，躯干含盆会阴并入胸段）")
                .defineInRange("tbsaWeightChest", 0.37, 0.0, 1.0);
        BURN_TBSA_WEIGHT_ARM = builder
                .comment("单臂 TBSA 权重（九分法：双上肢18%，每臂9%）")
                .defineInRange("tbsaWeightArm", 0.09, 0.0, 1.0);
        BURN_TBSA_WEIGHT_LEG = builder
                .comment("单腿 TBSA 权重（九分法：双下肢36%，每腿18%）")
                .defineInRange("tbsaWeightLeg", 0.18, 0.0, 1.0);
        BURN_PLACEMENT_HEAD = builder
                .comment("火伤部位随机分配权重：头")
                .defineInRange("placementHead", 0.05, 0.0, 1.0);
        BURN_PLACEMENT_CHEST = builder
                .comment("火伤部位随机分配权重：躯干")
                .defineInRange("placementChest", 0.30, 0.0, 1.0);
        BURN_PLACEMENT_ARM = builder
                .comment("火伤部位随机分配权重：单臂")
                .defineInRange("placementArm", 0.15, 0.0, 1.0);
        BURN_PLACEMENT_LEG = builder
                .comment("火伤部位随机分配权重：单腿")
                .defineInRange("placementLeg", 0.20, 0.0, 1.0);
        BURN_STICKINESS = builder
                .comment("粘滞率：火伤命中已烧伤肢体的概率（同次着火集中烧同一区域）")
                .defineInRange("stickiness", 0.75, 0.0, 1.0);
        BURN_TBSA_PAIN_THRESHOLD = builder
                .comment("TBSA 疼痛危象阈值：超过后全身体感疼痛显著上抬")
                .defineInRange("tbsaPainThreshold", 0.10, 0.0, 1.0);
        BURN_TBSA_SHOCK_THRESHOLD = builder
                .comment("TBSA 休克阈值：超过后渗漏休克启动（>15% 真实医学值）")
                .defineInRange("tbsaShockThreshold", 0.15, 0.0, 1.0);
        BURN_TBSA_COAGULOPATHY_THRESHOLD = builder
                .comment("TBSA 凝血紊乱阈值：超过后凝血时间延长+SIRS自动晋级+高代谢（>30% 真实医学值）")
                .defineInRange("tbsaCoagulopathyThreshold", 0.30, 0.0, 1.0);
        BURN_LEAK_BASE = builder
                .comment("渗漏基础速率（mL/s = TBSA × 基率 × 深度系数；浅II=1.0/深II=1.2/III=0.8焦痂护壳）")
                .defineInRange("leakBase", 1.2, 0.0, 50.0);
        BURN_LEAK_PEAK_TIME = builder
                .comment("渗漏峰值时刻（秒，伤后；渗漏曲线 shape：0→峰→0，全程20min）")
                .defineInRange("leakPeakTime", 480, 10, 86400);
        BURN_LEAK_DURATION = builder
                .comment("渗漏总时长（秒；默认1200=20分钟休克期）")
                .defineInRange("leakDuration", 1200, 60, 86400);
        BURN_EDEMA_RETURN_START = builder
                .comment("回吸收期起点（秒，伤后；水肿开始回流入血并释放烧伤毒素）")
                .defineInRange("edemaReturnStart", 1500, 60, 86400);
        BURN_EDEMA_RETURN_END = builder
                .comment("回吸收期终点（秒，伤后）")
                .defineInRange("edemaReturnEnd", 2100, 60, 86400);
        BURN_TOXIN_RELEASE = builder
                .comment("回吸收期释放的烧伤毒素总量（TBSA × 该值，分摊在回吸收窗口进入中毒条）")
                .defineInRange("toxinRelease", 80.0, 0.0, 500.0);
        BURN_PAIN_I = builder
                .comment("I度烧伤基础疼痛输出（每秒，乘 0.3+0.7×面积；深II迟钝、III度神经毁损近无痛）")
                .defineInRange("painI", 1.0, 0.0, 20.0);
        BURN_PAIN_SHALLOW_II = builder
                .comment("浅II度基础疼痛输出（每秒）——最痛的烧伤")
                .defineInRange("painShallowII", 1.3, 0.0, 20.0);
        BURN_PAIN_DEEP_II = builder
                .comment("深II度基础疼痛输出（每秒，痛觉迟钝）")
                .defineInRange("painDeepII", 0.5, 0.0, 20.0);
        BURN_PAIN_III = builder
                .comment("III度基础疼痛输出（每秒，神经已毁损）")
                .defineInRange("painIII", 0.1, 0.0, 20.0);
        BURN_INFECTION_RATE = builder
                .comment("创面感染压力基础速率（每肢体每秒累加 = (深度-1)×面积×该值×焦痂期3×包扎0.3）")
                .defineInRange("infectionRate", 0.05, 0.0, 5.0);
        BURN_ESCHAR_PHASE_START = builder
                .comment("焦痂溶解期起点（秒，伤后；感染压力×3——稳定期与爆发期的戏剧性）")
                .defineInRange("escharPhaseStart", 2400, 60, 86400);
        BURN_ESCHAR_PHASE_END = builder
                .comment("焦痂溶解期终点（秒，伤后；默认第2-3MC日）")
                .defineInRange("escharPhaseEnd", 3600, 60, 172800);
        BURN_HEAL_TIME_I = builder
                .comment("I度自愈时长（秒）")
                .defineInRange("healTimeI", 90, 1, 86400);
        BURN_HEAL_TIME_SHALLOW_II = builder
                .comment("浅II度自愈时长（秒）")
                .defineInRange("healTimeShallowII", 300, 1, 86400);
        BURN_HEAL_TIME_DEEP_II = builder
                .comment("深II度自愈时长（秒，需扛过感染检查）")
                .defineInRange("healTimeDeepII", 600, 1, 86400);
        BURN_HEAL_MULT_BANDAGED = builder
                .comment("包扎对愈合速度的倍率（敷料防护）")
                .defineInRange("healMultBandaged", 2.0, 0.1, 10.0);
        BURN_PAIN_MULT_BANDAGED = builder
                .comment("包扎对疼痛输出的倍率")
                .defineInRange("painMultBandaged", 0.5, 0.0, 1.0);
        BURN_INFECTION_MULT_BANDAGED = builder
                .comment("包扎对感染压力的倍率")
                .defineInRange("infectionMultBandaged", 0.3, 0.0, 1.0);
        BURN_INHALATION_RATE = builder
                .comment("吸入性损伤增长速率：头II度+着火暴露时每秒增长量（0-1封顶）")
                .defineInRange("inhalationRate", 0.02, 0.0, 1.0);
        BURN_INHALATION_DECAY = builder
                .comment("吸入性损伤消退速率（每秒，脱离火源且伤龄超过热力期后）")
                .defineInRange("inhalationDecay", 0.005, 0.0, 1.0);
        BURN_INHALATION_VENT_MULT = builder
                .comment("吸入性损伤换气效率折减系数（效率 ×(1-该值×严重度)，0.3=重度时效率×0.7）")
                .defineInRange("inhalationVentMult", 0.3, 0.0, 1.0);
        BURN_METABOLISM_SLOPE = builder
                .comment("高代谢饥饿额外消耗斜率（倍率 = 1 + TBSA×该值；30%时×1.6 真实高代谢）")
                .defineInRange("metabolismSlope", 2.0, 0.0, 10.0);
        BURN_CLOTTING_SLOPE = builder
                .comment("凝血紊乱斜率（TBSA>30%时凝血时间倍率 = 1+(TBSA-0.3)×该值）")
                .defineInRange("clottingSlope", 2.0, 0.0, 10.0);
        builder.pop();

        SPEC = builder.build();
    }
}
