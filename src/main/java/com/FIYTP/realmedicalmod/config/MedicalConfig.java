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
    public static final ModConfigSpec.DoubleValue PAIN_CONSCIOUSNESS_FLOOR;
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
    public static final ModConfigSpec.DoubleValue UNCONSCIOUS_MIN_RESPIRATION_FACTOR;

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
    public static final ModConfigSpec.IntValue MAX_PERCEIVED_PAIN;
    public static final ModConfigSpec.DoubleValue PAIN_LIMB_EXPONENT;
    public static final ModConfigSpec.DoubleValue PAIN_DOMINANT_LIMB_WEIGHT;
    public static final ModConfigSpec.DoubleValue PAIN_SECONDARY_LIMB_WEIGHT;
    // 疼痛视觉：红晕血管搏动 vignette
    public static final ModConfigSpec.BooleanValue PAIN_VIGNETTE_ENABLED;
    public static final ModConfigSpec.IntValue PAIN_VIGNETTE_START_THRESHOLD;
    public static final ModConfigSpec.IntValue PAIN_VIGNETTE_FULL_THRESHOLD;
    public static final ModConfigSpec.DoubleValue PAIN_VIGNETTE_MAX_ALPHA;
    // 疼痛视觉：重度灰蓝色罩
    public static final ModConfigSpec.IntValue PAIN_GRAY_WASH_THRESHOLD;
    public static final ModConfigSpec.DoubleValue PAIN_GRAY_WASH_MAX_ALPHA;
    // 疼痛视觉：剧痛瞬发闪抖
    public static final ModConfigSpec.BooleanValue PAIN_SHOCK_ENABLED;
    public static final ModConfigSpec.IntValue PAIN_SHOCK_JUMP_THRESHOLD;
    public static final ModConfigSpec.IntValue PAIN_SHOCK_SEVERE_THRESHOLD;
    public static final ModConfigSpec.IntValue PAIN_SHOCK_COOLDOWN_TICKS;
    public static final ModConfigSpec.DoubleValue PAIN_SHOCK_WHITE_FLASH_ALPHA;
    public static final ModConfigSpec.DoubleValue PAIN_SHOCK_MAX_OFFSET;
    // 疼痛音效：心跳触发与音量加成
    public static final ModConfigSpec.IntValue HEARTBEAT_TRIGGER_PAIN;
    public static final ModConfigSpec.DoubleValue HEARTBEAT_PAIN_VOLUME_BONUS;

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
    public static final ModConfigSpec.IntValue NORMAL_SBP;
    public static final ModConfigSpec.IntValue NORMAL_DBP;
    public static final ModConfigSpec.DoubleValue NORMAL_MAP;
    public static final ModConfigSpec.DoubleValue NORMAL_CO;
    public static final ModConfigSpec.DoubleValue MAP_APPROACH_SPEED;

    public static final ModConfigSpec.DoubleValue TPR_NORMAL;
    public static final ModConfigSpec.DoubleValue TPR_COMPENSATED;
    public static final ModConfigSpec.DoubleValue TPR_DECOMPENSATED;

    public static final ModConfigSpec.DoubleValue MAP_HYPOTENSION;
    public static final ModConfigSpec.DoubleValue MAP_SEVERE_HYPOTENSION;
    public static final ModConfigSpec.IntValue SBP_HYPERTENSIVE_CRISIS;

    public static final ModConfigSpec.DoubleValue BLEED_MULT_MAP_BELOW_40;
    public static final ModConfigSpec.DoubleValue BLEED_MULT_MAP_40_55;
    public static final ModConfigSpec.DoubleValue BLEED_MULT_MAP_55_70;
    public static final ModConfigSpec.DoubleValue BLEED_MULT_MAP_70_85;
    public static final ModConfigSpec.DoubleValue BLEED_MULT_MAP_85_105;
    public static final ModConfigSpec.DoubleValue BLEED_MULT_MAP_105_125;
    public static final ModConfigSpec.DoubleValue BLEED_MULT_MAP_125_145;
    public static final ModConfigSpec.DoubleValue BLEED_MULT_MAP_145_165;
    public static final ModConfigSpec.DoubleValue BLEED_MULT_MAP_ABOVE_165;

    public static final ModConfigSpec.DoubleValue EXTERNAL_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue CHEST_EXTERNAL_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue INTERNAL_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue HEMOTHORAX_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue INTRACRANIAL_MULTIPLIER;

    public static final ModConfigSpec.DoubleValue CONS_PENALTY_MAP_60;
    public static final ModConfigSpec.DoubleValue CONS_PENALTY_MAP_50;
    public static final ModConfigSpec.DoubleValue CONS_PENALTY_MAP_40;
    public static final ModConfigSpec.DoubleValue CONS_PENALTY_MAP_BELOW_40;
    public static final ModConfigSpec.DoubleValue CONS_PENALTY_SBP_180;
    public static final ModConfigSpec.DoubleValue CONS_PENALTY_SBP_200;
    public static final ModConfigSpec.DoubleValue CONS_PENALTY_SBP_220;

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
    public static final ModConfigSpec.IntValue SIRS_LIMB_THRESHOLD;
    public static final ModConfigSpec.IntValue SIRS_TOTAL_THRESHOLD;
    public static final ModConfigSpec.IntValue SIRS_PROGRESSION_INTERVAL;
    public static final ModConfigSpec.IntValue SIRS_RECOVERY_LIMB_THRESHOLD;
    public static final ModConfigSpec.IntValue SIRS_RECOVERY_TOTAL_THRESHOLD;
    public static final ModConfigSpec.IntValue SIRS_RECOVERY_INTERVAL_L4_TO_L3;
    public static final ModConfigSpec.IntValue SIRS_RECOVERY_INTERVAL_L3_TO_L2;
    public static final ModConfigSpec.IntValue SIRS_RECOVERY_INTERVAL_L2_TO_L1;
    public static final ModConfigSpec.IntValue SIRS_RECOVERY_INTERVAL_L1_TO_L0;

    public static final ModConfigSpec.DoubleValue SIRS_ENV_WATER;
    public static final ModConfigSpec.DoubleValue SIRS_ENV_CAVE;
    public static final ModConfigSpec.DoubleValue SIRS_ENV_NETHER;
    public static final ModConfigSpec.DoubleValue SIRS_ENV_RAIN;
    public static final ModConfigSpec.DoubleValue SIRS_ENV_INDOOR;
    public static final ModConfigSpec.DoubleValue SIRS_ENV_NORMAL;

    public static final ModConfigSpec.IntValue SIRS_HEART_BONUS_PER_LEVEL;
    public static final ModConfigSpec.IntValue SIRS_RESP_BONUS_PER_LEVEL;
    public static final ModConfigSpec.DoubleValue SIRS_OXYGEN_PENALTY_PER_LEVEL;
    public static final ModConfigSpec.DoubleValue SIRS_BLOOD_LOSS_L2;
    public static final ModConfigSpec.DoubleValue SIRS_BLOOD_LOSS_L3;
    public static final ModConfigSpec.DoubleValue SIRS_BLOOD_LOSS_L4;

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
    public static final ModConfigSpec.DoubleValue ARBITER_MIN_MULTIPLIER;
    public static final ModConfigSpec.IntValue POTION_HYSTERESIS_TICKS;
    public static final ModConfigSpec.DoubleValue PAIN_LEG_SPEED_SLOPE;
    public static final ModConfigSpec.DoubleValue PAIN_ARM_ATTACK_SLOPE;
    public static final ModConfigSpec.DoubleValue PAIN_ARM_SPEED_SLOPE;
    public static final ModConfigSpec.DoubleValue PAIN_HEAD_BLINDNESS_SLOPE;
    public static final ModConfigSpec.DoubleValue PAIN_CHEST_NAUSEA_SLOPE;
    public static final ModConfigSpec.DoubleValue PAIN_ACTION_EXPONENT;
    public static final ModConfigSpec.DoubleValue PAIN_LEG_MAX_SPEED_PENALTY;
    public static final ModConfigSpec.DoubleValue PAIN_ARM_MAX_ATTACK_PENALTY;
    public static final ModConfigSpec.DoubleValue PAIN_ARM_MAX_SPEED_PENALTY;
    public static final ModConfigSpec.DoubleValue PAIN_SYSTEMIC_MAX_SPEED_PENALTY;
    public static final ModConfigSpec.DoubleValue PAIN_SYSTEMIC_MAX_ACTION_PENALTY;
    public static final ModConfigSpec.IntValue PAIN_SPRINT_LOCK_THRESHOLD;
    public static final ModConfigSpec.DoubleValue BLOOD_LOSS_SPEED_SLOPE;
    public static final ModConfigSpec.DoubleValue BLOOD_LOSS_ATTACK_SLOPE;
    public static final ModConfigSpec.DoubleValue BLOOD_LOSS_DIG_SLOPE;
    public static final ModConfigSpec.IntValue HYPOXIA_ONSET_SPO2;
    public static final ModConfigSpec.DoubleValue HYPOXIA_MAX_PENALTY;
    public static final ModConfigSpec.DoubleValue SIRS_FATIGUE_PER_LEVEL;
    public static final ModConfigSpec.DoubleValue SEPSIS_MIN_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue CONSC_IMPACT_FACTOR;
    public static final ModConfigSpec.DoubleValue CONSC_IMPACT_UNCONSCIOUS;
    public static final ModConfigSpec.DoubleValue HEMOTHORAX_L3_SPEED_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue HEMOTHORAX_L4_SPEED_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue CONTUSION_FATIGUE_SLOPE;
    public static final ModConfigSpec.IntValue IC_BLINDNESS_L3;
    public static final ModConfigSpec.IntValue IC_BLINDNESS_L4;
    public static final ModConfigSpec.IntValue PERCEIVED_PAIN_BLINDNESS_THRESHOLD;
    public static final ModConfigSpec.IntValue PERCEIVED_PAIN_NAUSEA_THRESHOLD;
    public static final ModConfigSpec.IntValue DIG_SLOWDOWN_FRACTURE_MAX_LEVEL;

    // ================= 中毒系统 (toxin) =================
    public static final ModConfigSpec.DoubleValue TOXIN_ACCUMULATION_RATE;
    public static final ModConfigSpec.DoubleValue TOXIN_DECAY_RATE;
    public static final ModConfigSpec.DoubleValue TOXIN_IMMUNE_DECAY_MULT;
    public static final ModConfigSpec.DoubleValue TOXIN_MILD_THRESHOLD;
    public static final ModConfigSpec.DoubleValue TOXIN_MID_THRESHOLD;
    public static final ModConfigSpec.DoubleValue TOXIN_SEVERE_THRESHOLD;
    public static final ModConfigSpec.DoubleValue TOXIN_CRITICAL_THRESHOLD;
    public static final ModConfigSpec.DoubleValue TOXIN_NAUSEA_SLOPE;
    public static final ModConfigSpec.DoubleValue TOXIN_RESP_RATE_SLOPE;
    public static final ModConfigSpec.DoubleValue TOXIN_RESP_SUPPRESSION_SLOPE;
    public static final ModConfigSpec.DoubleValue TOXIN_CONSCIOUSNESS_SLOPE;
    public static final ModConfigSpec.DoubleValue TOXIN_TPR_SLOPE;
    public static final ModConfigSpec.DoubleValue TOXIN_STROKE_VOLUME_SLOPE;
    public static final ModConfigSpec.DoubleValue TOXIN_INFECTION_RATE;
    public static final ModConfigSpec.DoubleValue TOXIN_VFIB_CHANCE;
    public static final ModConfigSpec.DoubleValue TOXIN_CLOTTING_TIME_MULT;

    // ================= 烧伤系统 (burn) =================
    public static final ModConfigSpec.DoubleValue BURN_LOAD_PER_DAMAGE;
    public static final ModConfigSpec.DoubleValue BURN_CONVERSION_RATE;
    public static final ModConfigSpec.DoubleValue BURN_AREA_PER_DAMAGE;
    public static final ModConfigSpec.IntValue BURN_HEAT_WINDOW;
    public static final ModConfigSpec.DoubleValue BURN_IRRIGATION_RATE;
    public static final ModConfigSpec.DoubleValue BURN_DEGREE_I;
    public static final ModConfigSpec.DoubleValue BURN_DEGREE_SHALLOW_II;
    public static final ModConfigSpec.DoubleValue BURN_DEGREE_DEEP_II;
    public static final ModConfigSpec.DoubleValue BURN_DEGREE_III;
    public static final ModConfigSpec.DoubleValue BURN_TBSA_WEIGHT_HEAD;
    public static final ModConfigSpec.DoubleValue BURN_TBSA_WEIGHT_CHEST;
    public static final ModConfigSpec.DoubleValue BURN_TBSA_WEIGHT_ARM;
    public static final ModConfigSpec.DoubleValue BURN_TBSA_WEIGHT_LEG;
    public static final ModConfigSpec.DoubleValue BURN_PLACEMENT_HEAD;
    public static final ModConfigSpec.DoubleValue BURN_PLACEMENT_CHEST;
    public static final ModConfigSpec.DoubleValue BURN_PLACEMENT_ARM;
    public static final ModConfigSpec.DoubleValue BURN_PLACEMENT_LEG;
    public static final ModConfigSpec.DoubleValue BURN_STICKINESS;
    public static final ModConfigSpec.DoubleValue BURN_TBSA_PAIN_THRESHOLD;
    public static final ModConfigSpec.DoubleValue BURN_TBSA_SHOCK_THRESHOLD;
    public static final ModConfigSpec.DoubleValue BURN_TBSA_COAGULOPATHY_THRESHOLD;
    public static final ModConfigSpec.DoubleValue BURN_LEAK_BASE;
    public static final ModConfigSpec.IntValue BURN_LEAK_PEAK_TIME;
    public static final ModConfigSpec.IntValue BURN_LEAK_DURATION;
    public static final ModConfigSpec.IntValue BURN_EDEMA_RETURN_START;
    public static final ModConfigSpec.IntValue BURN_EDEMA_RETURN_END;
    public static final ModConfigSpec.DoubleValue BURN_TOXIN_RELEASE;
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
    public static final ModConfigSpec.DoubleValue BURN_INHALATION_RATE;
    public static final ModConfigSpec.DoubleValue BURN_INHALATION_DECAY;
    public static final ModConfigSpec.DoubleValue BURN_INHALATION_VENT_MULT;
    public static final ModConfigSpec.DoubleValue BURN_METABOLISM_SLOPE;
    public static final ModConfigSpec.DoubleValue BURN_CLOTTING_SLOPE;

    // ==================== 阿片类药物系统 (opioid) ====================
    public static final ModConfigSpec.IntValue MORPHINE_MAX_DOSE_UNITS;
    public static final ModConfigSpec.DoubleValue MORPHINE_MAX_MG;
    public static final ModConfigSpec.DoubleValue MORPHINE_TARGET_PAIN_RELIEF;
    public static final ModConfigSpec.DoubleValue MORPHINE_SYSTEMIC_PAIN_RELIEF;
    public static final ModConfigSpec.DoubleValue MORPHINE_CONCENTRATION_PER_FULL_DOSE;
    public static final ModConfigSpec.DoubleValue MORPHINE_ADDICTION_GAIN;

    public static final ModConfigSpec.IntValue FENTANYL_MAX_DOSE_UNITS;
    public static final ModConfigSpec.DoubleValue FENTANYL_MAX_MCG;
    public static final ModConfigSpec.DoubleValue FENTANYL_TARGET_PAIN_RELIEF;
    public static final ModConfigSpec.DoubleValue FENTANYL_SYSTEMIC_PAIN_RELIEF;
    public static final ModConfigSpec.DoubleValue FENTANYL_CONCENTRATION_PER_FULL_DOSE;
    public static final ModConfigSpec.DoubleValue FENTANYL_ADDICTION_GAIN;

    public static final ModConfigSpec.DoubleValue OPIOID_ANALGESIA_FULL_CONCENTRATION;
    public static final ModConfigSpec.DoubleValue OPIOID_CENTRAL_ANALGESIA_MAX_REDUCTION;
    public static final ModConfigSpec.DoubleValue OPIOID_ANALGESIA_CURVE_EXPONENT;
    public static final ModConfigSpec.DoubleValue OPIOID_PAIN_TRANSMISSION_BLOCK;
    public static final ModConfigSpec.DoubleValue OPIOID_METABOLISM_RATE_PER_SEC;

    public static final ModConfigSpec.IntValue OPIOID_RESP_DEPRESSION_START;
    public static final ModConfigSpec.IntValue OPIOID_SEDATION_START;
    public static final ModConfigSpec.IntValue OPIOID_OVERDOSE_THRESHOLD;
    public static final ModConfigSpec.IntValue OPIOID_APNEA_THRESHOLD;
    public static final ModConfigSpec.DoubleValue OPIOID_MIN_DEPRESSED_RESP_FACTOR;
    public static final ModConfigSpec.DoubleValue OPIOID_SEDATION_CONSCIOUSNESS_SLOPE;
    public static final ModConfigSpec.DoubleValue OPIOID_OVERDOSE_CONSCIOUSNESS_PENALTY;
    public static final ModConfigSpec.DoubleValue OPIOID_ACUTE_OVERDOSE_CONSCIOUSNESS_DROP;
    public static final ModConfigSpec.DoubleValue OPIOID_HEART_RATE_SLOPE;
    public static final ModConfigSpec.DoubleValue OPIOID_OVERDOSE_BRADYCARDIA_PENALTY;

    // 阿片三级成瘾惩罚、心理诱导、滑块持续运动与 II/III 级戒断行动受限
    public static final ModConfigSpec.IntValue ADDICTION_TIER1_THRESHOLD;
    public static final ModConfigSpec.IntValue ADDICTION_TIER2_THRESHOLD;
    public static final ModConfigSpec.IntValue ADDICTION_TIER3_THRESHOLD;
    public static final ModConfigSpec.IntValue ADDICTION_DECAY_INTERVAL_SEC;
    public static final ModConfigSpec.DoubleValue ADDICTION_NAME_HALLUCINATION_CHANCE_T1;
    public static final ModConfigSpec.DoubleValue ADDICTION_NAME_HALLUCINATION_CHANCE_T2;
    public static final ModConfigSpec.DoubleValue ADDICTION_NAME_HALLUCINATION_CHANCE_T3;
    public static final ModConfigSpec.DoubleValue ADDICTION_BLIND_ZONE_CHANCE_T1;
    public static final ModConfigSpec.DoubleValue ADDICTION_BLIND_ZONE_CHANCE_T2;
    public static final ModConfigSpec.DoubleValue ADDICTION_BLIND_ZONE_CHANCE_T3;
    public static final ModConfigSpec.DoubleValue ADDICTION_SLIDER_DRIFT_SPEED_T1;
    public static final ModConfigSpec.DoubleValue ADDICTION_SLIDER_DRIFT_SPEED_T2;
    public static final ModConfigSpec.DoubleValue ADDICTION_SLIDER_DRIFT_SPEED_T3;
    public static final ModConfigSpec.IntValue ADDICTION_WITHDRAWAL_OPIOID_THRESHOLD;
    public static final ModConfigSpec.DoubleValue ADDICTION_WITHDRAWAL_SPEED_MULT_T2;
    public static final ModConfigSpec.DoubleValue ADDICTION_WITHDRAWAL_SPEED_MULT_T3;

    // 注射阿片药物愉悦性视觉效果
    public static final ModConfigSpec.BooleanValue OPIOID_EUPHORIA_ENABLED;
    public static final ModConfigSpec.DoubleValue OPIOID_EUPHORIA_MAX_ALPHA;
    public static final ModConfigSpec.DoubleValue OPIOID_EUPHORIA_RUSH_DURATION_SEC;

    // 3A 脑损伤升级视觉效果（历史画面闪回、定格撕裂震荡、四态神经变色）配置
    public static ModConfigSpec.BooleanValue BRAIN_VFX_UPGRADE_ENABLED;
    public static ModConfigSpec.DoubleValue BRAIN_VFX_FLASHBACK_INTENSITY;
    public static ModConfigSpec.IntValue BRAIN_VFX_FREEZE_DURATION_MS;
    public static ModConfigSpec.DoubleValue BRAIN_VFX_SHAKE_AMPLITUDE;
    public static ModConfigSpec.DoubleValue BRAIN_VFX_COLOR_SHIFT_INTENSITY;

    // ==================== 肾上腺素系统（受创内源性应激 Buff + 外源性肾上腺素自动注射器）配置 ====================
    public static ModConfigSpec.BooleanValue ADRENALINE_TRAUMA_ENABLED;
    public static ModConfigSpec.DoubleValue ADRENALINE_TRAUMA_MIN_DAMAGE;
    public static ModConfigSpec.DoubleValue ADRENALINE_TRAUMA_GAIN;
    public static ModConfigSpec.IntValue ADRENALINE_TRAUMA_DURATION_SEC;
    public static ModConfigSpec.IntValue ADRENALINE_TRAUMA_COOLDOWN_SEC;
    public static ModConfigSpec.DoubleValue ADRENALINE_INJECTOR_GAIN;
    public static ModConfigSpec.DoubleValue ADRENALINE_DECAY_RATE_PER_SEC;
    public static ModConfigSpec.IntValue ADRENALINE_TIER1_THRESHOLD;
    public static ModConfigSpec.IntValue ADRENALINE_TIER2_THRESHOLD;
    public static ModConfigSpec.IntValue ADRENALINE_TIER3_THRESHOLD;
    public static ModConfigSpec.DoubleValue ADRENALINE_MAX_HR_BOOST;
    public static ModConfigSpec.DoubleValue ADRENALINE_MAX_RESP_BOOST;
    public static ModConfigSpec.DoubleValue ADRENALINE_MAX_PAIN_SLOW_RELIEF;
    public static ModConfigSpec.DoubleValue ADRENALINE_SPEED_BOOST_T1;
    public static ModConfigSpec.DoubleValue ADRENALINE_SPEED_BOOST_T2;
    public static ModConfigSpec.DoubleValue ADRENALINE_UNBANDAGED_BLEED_MULT;
    public static ModConfigSpec.IntValue ADRENALINE_CRASH_DURATION_SEC;
    public static ModConfigSpec.DoubleValue ADRENALINE_CRASH_SPEED_MULT;


    // ==================== 盐酸纳洛酮 (Naloxone) 与止血带/肢体坏死 (Tourniquet & Necrosis) 配置 ====================
    public static ModConfigSpec.DoubleValue NALOXONE_OPIOID_CLEAR_AMOUNT;
    public static ModConfigSpec.IntValue NALOXONE_BLOCKADE_DURATION_SEC;
    public static ModConfigSpec.DoubleValue NALOXONE_RECEPTOR_BLOCK_FACTOR;
    public static ModConfigSpec.DoubleValue NALOXONE_HYPERALGESIA_MULTIPLIER;
    public static ModConfigSpec.IntValue NALOXONE_WOUND_PAIN_REBOUND;
    public static ModConfigSpec.DoubleValue NALOXONE_CONSCIOUSNESS_RESTORE;
    public static ModConfigSpec.IntValue NALOXONE_MIN_RESTORED_RESP_RATE;
    public static ModConfigSpec.IntValue NALOXONE_REBOUND_HR_BOOST;
    public static ModConfigSpec.IntValue NALOXONE_REBOUND_BP_BOOST;
    public static ModConfigSpec.IntValue NALOXONE_PRECIPITATED_WITHDRAWAL_TICKS;

    public static ModConfigSpec.IntValue BANDAGE_MAX_BLEEDING_THRESHOLD;
    public static ModConfigSpec.IntValue TOURNIQUET_ISCHEMIA_STAGE1_SEC;
    public static ModConfigSpec.IntValue TOURNIQUET_ISCHEMIA_STAGE2_SEC;
    public static ModConfigSpec.IntValue TOURNIQUET_NECROSIS_TIME_SEC;
    public static ModConfigSpec.IntValue NECROSIS_SELF_INFECTION_RATE_PER_SEC;
    public static ModConfigSpec.IntValue NECROSIS_OTHER_LIMBS_INFECTION_RATE_PER_SEC;
    public static ModConfigSpec.DoubleValue NECROSIS_ONE_LEG_SPEED_MULT;
    public static ModConfigSpec.DoubleValue NECROSIS_TWO_LEGS_SPEED_MULT;
    public static ModConfigSpec.DoubleValue NECROSIS_ONE_ARM_ACTION_MULT;
    public static ModConfigSpec.DoubleValue NECROSIS_TWO_ARMS_ACTION_MULT;

    public static ModConfigSpec.IntValue HEAVY_TRAUMA_PAIN_DELTA_THRESHOLD;
    public static ModConfigSpec.IntValue HEAVY_TRAUMA_BLEED_DELTA_THRESHOLD;
    public static ModConfigSpec.IntValue HEAVY_TRAUMA_SPLATTER_DURATION_MS;
    public static ModConfigSpec.DoubleValue HEAVY_TRAUMA_FOV_PUNCH_MAX;
    public static ModConfigSpec.DoubleValue HEAVY_TRAUMA_CAMERA_RECOIL_MULT;

    public static ModConfigSpec.IntValue SUTURE_MIN_CONSCIOUSNESS;
    public static ModConfigSpec.IntValue SUTURE_ARTERIAL_BLEED_THRESHOLD;
    public static ModConfigSpec.IntValue SUTURE_LIGATION_DURABILITY_COST;
    public static ModConfigSpec.IntValue SUTURE_ANASTOMOSIS_DURABILITY_COST;
    public static ModConfigSpec.IntValue SUTURE_MISTAKE_PAIN_GAIN;
    public static ModConfigSpec.IntValue SUTURE_MISTAKE_BLOOD_LOSS;
    public static ModConfigSpec.IntValue SUTURE_ABORT_TEAR_BLEED_INCREASE;
    public static ModConfigSpec.DoubleValue SUTURE_ARTERIAL_LIGATION_LEG_SPEED_MULT;
    public static ModConfigSpec.DoubleValue SUTURE_ARTERIAL_LIGATION_ARM_ACTION_MULT;

    public static ModConfigSpec.DoubleValue SUTURE_RETRACTOR_OPEN_THRESHOLD;
    public static ModConfigSpec.DoubleValue SUTURE_STUMP_DRIFT_AMPLITUDE;
    public static ModConfigSpec.DoubleValue SUTURE_STUMP_CLAMP_RADIUS;
    public static ModConfigSpec.DoubleValue SUTURE_WINDING_REQUIRED_DEGREES;
    public static ModConfigSpec.DoubleValue SUTURE_KNOT_TENSION_MIN;
    public static ModConfigSpec.DoubleValue SUTURE_KNOT_TENSION_MAX;
    public static ModConfigSpec.DoubleValue SUTURE_KNOT_TENSION_SNAP;
    public static ModConfigSpec.DoubleValue SUTURE_KNOT_HOLD_SECONDS;
    public static ModConfigSpec.DoubleValue SUTURE_BULLDOG_CALM_BEAT_MAX;
    public static ModConfigSpec.DoubleValue SUTURE_GRAFT_ALIGN_TOLERANCE_DEG;
    public static ModConfigSpec.DoubleValue SUTURE_GRAFT_SNAP_DISTANCE;
    public static ModConfigSpec.IntValue SUTURE_ANASTOMOSIS_STITCH_COUNT;
    public static ModConfigSpec.DoubleValue SUTURE_CURSOR_PAIN_SHAKE_MAX;
    public static ModConfigSpec.DoubleValue SUTURE_CURSOR_WITHDRAWAL_SHAKE;

    public static ModConfigSpec.DoubleValue SUTURE_CURSOR_CONSCIOUSNESS_SWAY_MAX;
    public static ModConfigSpec.DoubleValue BURN_RATE_MULTIPLIER;
    public static ModConfigSpec.DoubleValue BURN_FIRE_AREA_GAIN_PER_SEC;
    public static ModConfigSpec.DoubleValue BURN_LAVA_AREA_GAIN_PER_SEC;
    public static ModConfigSpec.BooleanValue BURN_VFX_ENABLED;
    public static ModConfigSpec.DoubleValue BURN_VFX_MAX_INTENSITY;

    public static ModConfigSpec.IntValue COMPARTMENT_MAX_INTERNAL_BLEED_RATE;
    public static ModConfigSpec.DoubleValue COMPARTMENT_MAX_BLOOD_CAP_ML;
    public static ModConfigSpec.DoubleValue COMPARTMENT_BLOOD_ACCUM_MULT;
    public static ModConfigSpec.DoubleValue COMPARTMENT_VENOUS_COLLAPSE_MMHG;
    public static ModConfigSpec.DoubleValue COMPARTMENT_TAMPONADE_FULL_MMHG;
    public static ModConfigSpec.DoubleValue COMPARTMENT_SYNDROME_THRESHOLD_MMHG;
    public static ModConfigSpec.DoubleValue COMPARTMENT_CRITICAL_DELTA_P_MMHG;
    public static ModConfigSpec.DoubleValue COMPARTMENT_ISCHEMIA_START_MMHG;
    public static ModConfigSpec.IntValue COMPARTMENT_NECROSIS_TIME_SEC;
    public static ModConfigSpec.IntValue COMPARTMENT_STAGE1_MIN_PAIN;
    public static ModConfigSpec.IntValue COMPARTMENT_STAGE2_MIN_PAIN;
    public static ModConfigSpec.DoubleValue COMPARTMENT_LEG_SPEED_MULT;
    public static ModConfigSpec.DoubleValue COMPARTMENT_ARM_ACTION_MULT;
    public static ModConfigSpec.DoubleValue COMPARTMENT_RHABDO_TOXIN_PER_SEC;
    public static ModConfigSpec.DoubleValue COMPARTMENT_NATURAL_ABSORB_ML_PER_SEC;

    public static ModConfigSpec.IntValue FASCIOTOMY_MIN_CONSCIOUSNESS;
    public static ModConfigSpec.IntValue FASCIOTOMY_DURABILITY_COST;
    public static ModConfigSpec.IntValue FASCIOTOMY_MISTAKE_PAIN_GAIN;
    public static ModConfigSpec.IntValue FASCIOTOMY_MISTAKE_BLOOD_LOSS;
    public static ModConfigSpec.IntValue FASCIOTOMY_ABORT_BLEED_PENALTY;
    public static ModConfigSpec.DoubleValue FASCIOTOMY_INCISION_CORRIDOR_WIDTH;
    public static ModConfigSpec.IntValue FASCIOTOMY_CLOT_COUNT;
    public static ModConfigSpec.IntValue FASCIOTOMY_BLEEDER_COUNT;
    public static ModConfigSpec.DoubleValue FASCIOTOMY_CAUTERY_HOLD_SECONDS;

    // 1. 骨折夹板固定、愈合倒计时与行动力恢复配置
    public static ModConfigSpec.IntValue SPLINT_FRACTURE_HEAL_TIME_SEC;
    public static ModConfigSpec.IntValue SPLINT_PAIN_RELIEF;
    public static ModConfigSpec.DoubleValue FRACTURE_UNSPLINTED_LEG_SPEED_MULT;
    public static ModConfigSpec.DoubleValue FRACTURE_SPLINTED_LEG_SPEED_MULT;
    public static ModConfigSpec.DoubleValue FRACTURE_UNSPLINTED_ARM_ACTION_MULT;
    public static ModConfigSpec.DoubleValue FRACTURE_SPLINTED_ARM_ACTION_MULT;
    public static ModConfigSpec.DoubleValue SPLINT_MOBILITY_PAIN_RELIEF_FACTOR;
    public static ModConfigSpec.BooleanValue SPLINT_ALLOW_WEAK_JUMP;
    public static ModConfigSpec.DoubleValue SPLINT_JUMP_VELOCITY_MULT;

    // 2. 非阿片类止痛药 —— 布洛芬缓释止痛片（生效慢、效果差、天花板效应）配置
    public static ModConfigSpec.IntValue NSAID_DOSE_DURABILITY_COST;
    public static ModConfigSpec.IntValue NSAID_ONSET_DELAY_SEC;
    public static ModConfigSpec.IntValue NSAID_RAMP_UP_SEC;
    public static ModConfigSpec.IntValue NSAID_ACTIVE_DURATION_SEC;
    public static ModConfigSpec.DoubleValue NSAID_MAX_PERCEIVED_PAIN_REDUCTION;
    public static ModConfigSpec.IntValue NSAID_LIMB_PAIN_RELIEF_INTERVAL_SEC;
    public static ModConfigSpec.IntValue NSAID_MAX_LIMB_PAIN_REDUCTION;
    public static ModConfigSpec.DoubleValue NSAID_OVERDOSE_GASTRIC_TOXIN_GAIN;

    // 3. 内出血注射药物 —— 氨甲环酸止血注射器 (TXA，抗纤溶促凝 + 急性低血压与高凝血栓副作用) 配置
    public static ModConfigSpec.IntValue TXA_IMMEDIATE_INTERNAL_BLEED_REDUCTION;
    public static ModConfigSpec.IntValue TXA_SYSTEMIC_INTERNAL_BLEED_REDUCTION;
    public static ModConfigSpec.IntValue TXA_CONTINUOUS_CLOT_RATE_PER_SEC;
    public static ModConfigSpec.DoubleValue TXA_CONCENTRATION_PER_DOSE;
    public static ModConfigSpec.DoubleValue TXA_DECAY_RATE_PER_SEC;
    public static ModConfigSpec.IntValue TXA_HYPOTENSION_SYS_DROP;
    public static ModConfigSpec.IntValue TXA_HYPOTENSION_DIA_DROP;
    public static ModConfigSpec.IntValue TXA_REFLEX_HR_BOOST;
    public static ModConfigSpec.IntValue TXA_THROMBOSIS_THRESHOLD;
    public static ModConfigSpec.DoubleValue TXA_THROMBOSIS_O2_PENALTY_PER_SEC;
    public static ModConfigSpec.IntValue TXA_THROMBOSIS_ISCHEMIA_MULT;
    public static ModConfigSpec.IntValue TXA_DIZZINESS_TICKS;

    public static ModConfigSpec.IntValue ORTHO_MIN_CONSCIOUSNESS;
    public static ModConfigSpec.IntValue ORTHO_DURABILITY_COST;
    public static ModConfigSpec.IntValue ORTHO_SURGERY_HEAL_TIME_SEC;
    public static ModConfigSpec.IntValue ORTHO_MISTAKE_PAIN_GAIN;
    public static ModConfigSpec.IntValue ORTHO_MISTAKE_BLOOD_LOSS;
    public static ModConfigSpec.IntValue ORTHO_ABORT_INTERNAL_BLEED_PENALTY;
    public static ModConfigSpec.IntValue ORTHO_SPLINTER_COUNT;
    public static ModConfigSpec.DoubleValue ORTHO_REDUCTION_TOLERANCE_PX;
    public static ModConfigSpec.DoubleValue ORTHO_REDUCTION_HOLD_SECONDS;
    public static ModConfigSpec.IntValue ORTHO_SCREW_COUNT;
    public static ModConfigSpec.DoubleValue ORTHO_DRILL_HOLD_SECONDS;
    public static ModConfigSpec.IntValue OPEN_FRACTURE_SPLINT_INFECTION_PENALTY_PER_SEC;
    public static ModConfigSpec.IntValue UNSTABLE_FRACTURE_MOVE_INTERNAL_BLEED_GAIN;
    public static ModConfigSpec.IntValue RIB_FRACTURE_DELAYED_HEMOTHORAX_STRAIN_THRESHOLD;
    public static ModConfigSpec.DoubleValue RIB_FRACTURE_DELAYED_HEMOTHORAX_BLOOD_ML;
    public static ModConfigSpec.DoubleValue FLAIL_CHEST_O2_PENALTY_PER_SEC;

    public static ModConfigSpec.IntValue CHEST_DRAIN_MIN_CONSCIOUSNESS;
    public static ModConfigSpec.IntValue CHEST_DRAIN_DURABILITY_COST;
    public static ModConfigSpec.IntValue CHEST_DRAIN_MISTAKE_PAIN_GAIN;
    public static ModConfigSpec.IntValue CHEST_DRAIN_MISTAKE_BLOOD_LOSS;
    public static ModConfigSpec.IntValue CHEST_DRAIN_ABORT_BLEED_PENALTY;
    public static ModConfigSpec.DoubleValue HEMOTHORAX_SELF_ABSORB_MAX_ML;
    public static ModConfigSpec.DoubleValue HEMOTHORAX_SELF_ABSORB_RATE_ML_PER_SEC;
    public static ModConfigSpec.DoubleValue LUNG_CONTUSION_BASE_DECAY_PER_SEC;
    public static ModConfigSpec.DoubleValue LUNG_CONTUSION_TXA_SPEED_MULT;
    public static ModConfigSpec.DoubleValue LUNG_CONTUSION_NSAID_SPEED_MULT;
    public static ModConfigSpec.DoubleValue LUNG_CONTUSION_SPRINT_WORSEN_PER_SEC;

    // --- 创伤性气胸（闭合性/开放性吸吮伤/张力性气胸）与 14G 胸腔穿刺减压针配置 (11项) ---
    public static ModConfigSpec.DoubleValue PNEUMOTHORAX_CLOSED_SELF_ABSORB_MAX_ML;
    public static ModConfigSpec.DoubleValue PNEUMOTHORAX_SELF_ABSORB_RATE_ML_PER_SEC;
    public static ModConfigSpec.DoubleValue PNEUMOTHORAX_OPEN_INFLOW_ML_PER_SEC;
    public static ModConfigSpec.DoubleValue PNEUMOTHORAX_VALVE_SEAL_VENT_ML_PER_SEC;
    public static ModConfigSpec.DoubleValue PNEUMOTHORAX_DEAD_SEAL_TENSION_GAIN_ML_PER_SEC;
    public static ModConfigSpec.DoubleValue PNEUMOTHORAX_TENSION_THRESHOLD_ML;
    public static ModConfigSpec.IntValue PNEUMOTHORAX_TENSION_BP_DROP_PER_SEC;
    public static ModConfigSpec.DoubleValue PNEUMOTHORAX_TENSION_O2_LOSS_PER_SEC;
    public static ModConfigSpec.DoubleValue DECOMPRESSION_NEEDLE_DRAIN_ML;
    public static ModConfigSpec.IntValue DECOMPRESSION_NEEDLE_SAFE_WINDOW_SEC;
    public static ModConfigSpec.IntValue DECOMPRESSION_NEEDLE_DURABILITY_COST;

    // --- 中毒、局部/全身感染与 SIRS（全身炎症反应综合征）临床药物与针剂配置 (33项) ---
    public static ModConfigSpec.IntValue CHARCOAL_DOSE_DURABILITY_COST;
    public static ModConfigSpec.DoubleValue CHARCOAL_IMMEDIATE_TOXIN_CLEAR;
    public static ModConfigSpec.IntValue CHARCOAL_DURATION_SEC;
    public static ModConfigSpec.DoubleValue CHARCOAL_DECAY_MULT;
    public static ModConfigSpec.DoubleValue CHARCOAL_SEVERE_TOXIN_THRESHOLD;
    public static ModConfigSpec.IntValue CHARCOAL_MIN_CONSCIOUSNESS;

    public static ModConfigSpec.IntValue NAC_MAX_DOSE_UNITS;
    public static ModConfigSpec.DoubleValue NAC_MAX_MG;
    public static ModConfigSpec.DoubleValue NAC_IMMEDIATE_TOXIN_CLEAR;
    public static ModConfigSpec.DoubleValue NAC_CONCENTRATION_PER_FULL_DOSE;
    public static ModConfigSpec.DoubleValue NAC_DECAY_RATE_PER_SEC;
    public static ModConfigSpec.DoubleValue NAC_CONTINUOUS_TOXIN_CLEAR_PER_SEC;
    public static ModConfigSpec.IntValue NAC_HISTAMINE_OVERDOSE_THRESHOLD;
    public static ModConfigSpec.IntValue NAC_HISTAMINE_SYS_BP_DROP;
    public static ModConfigSpec.DoubleValue NAC_HISTAMINE_BRONCHOSPASM_O2_LOSS;

    public static ModConfigSpec.IntValue POVIDONE_DURABILITY_COST;
    public static ModConfigSpec.IntValue POVIDONE_IMMEDIATE_INFECTION_CLEAN;
    public static ModConfigSpec.IntValue POVIDONE_DEEP_INFECTION_THRESHOLD;
    public static ModConfigSpec.IntValue POVIDONE_PROTECTION_DURATION_SEC;
    public static ModConfigSpec.DoubleValue POVIDONE_IMMUNE_SPEED_MULT;
    public static ModConfigSpec.IntValue POVIDONE_OPEN_WOUND_STING_PAIN;

    public static ModConfigSpec.IntValue AMOXICILLIN_DOSE_DURABILITY_COST;
    public static ModConfigSpec.IntValue AMOXICILLIN_ONSET_DELAY_SEC;
    public static ModConfigSpec.IntValue AMOXICILLIN_ACTIVE_DURATION_SEC;
    public static ModConfigSpec.DoubleValue AMOXICILLIN_CONTINUOUS_KILL_PER_SEC;
    public static ModConfigSpec.DoubleValue AMOXICILLIN_IMMUNE_MULT;
    public static ModConfigSpec.DoubleValue AMOXICILLIN_SHOCK_MALABSORPTION_FACTOR;
    public static ModConfigSpec.DoubleValue AMOXICILLIN_OVERDOSE_TOXIN_GAIN;

    public static ModConfigSpec.IntValue MEROPENEM_MAX_DOSE_UNITS;
    public static ModConfigSpec.DoubleValue MEROPENEM_MAX_MG;
    public static ModConfigSpec.IntValue MEROPENEM_TARGET_INFECTION_CLEAR;
    public static ModConfigSpec.IntValue MEROPENEM_SYSTEMIC_INFECTION_CLEAR;
    public static ModConfigSpec.DoubleValue MEROPENEM_CONCENTRATION_PER_FULL_DOSE;
    public static ModConfigSpec.DoubleValue MEROPENEM_DECAY_RATE_PER_SEC;
    public static ModConfigSpec.DoubleValue MEROPENEM_CONTINUOUS_KILL_PER_SEC;
    public static ModConfigSpec.DoubleValue MEROPENEM_SIRS_RECOVERY_SPEED_MULT;
    public static ModConfigSpec.DoubleValue MEROPENEM_SIRS_TPR_RESTORE;
    public static ModConfigSpec.IntValue MEROPENEM_HERXHEIMER_INFECTION_THRESHOLD;
    public static ModConfigSpec.DoubleValue MEROPENEM_HERXHEIMER_DOSE_RATIO_THRESHOLD;
    public static ModConfigSpec.DoubleValue MEROPENEM_HERXHEIMER_TOXIN_SPIKE;
    public static ModConfigSpec.IntValue MEROPENEM_NEUROTOXIC_THRESHOLD;
    public static ModConfigSpec.BooleanValue LOCK_HOTBAR_DURING_MEDICAL_ANIM;
    public static ModConfigSpec.IntValue SURGICAL_KIT_READY_ANIM_DURATION_MS;

    // --- 枪伤弹道分型（盲管伤嵌留子弹 vs 前后对穿贯穿伤）临床病理与救治配置 (12项) ---
    public static ModConfigSpec.DoubleValue EMBEDDED_BULLET_INFECTION_ACCUM_PER_SEC;
    public static ModConfigSpec.IntValue EMBEDDED_BULLET_MIN_PAIN_FLOOR;
    public static ModConfigSpec.DoubleValue EMBEDDED_BULLET_MOVE_REBLEED_CHANCE;
    public static ModConfigSpec.IntValue EMBEDDED_BULLET_MOVE_REBLEED_AMOUNT;
    public static ModConfigSpec.IntValue EMBEDDED_BULLET_BANDAGE_PRESS_PAIN_SPIKE;
    public static ModConfigSpec.DoubleValue EMBEDDED_BULLET_FRACTURE_HEAL_PENALTY_MULT;
    public static ModConfigSpec.DoubleValue PERFORATION_REGULAR_BANDAGE_EFFICIENCY;
    public static ModConfigSpec.DoubleValue PERFORATION_PRESSURE_BANDAGE_EFFICIENCY;
    public static ModConfigSpec.DoubleValue PERFORATION_BANDAGE_RESIDUAL_BLEED_RATIO;
    public static ModConfigSpec.IntValue PERFORATION_MIN_RESIDUAL_BLEED;
    public static ModConfigSpec.DoubleValue PERFORATION_PACKING_SPRINT_TEAR_CHANCE;
    public static ModConfigSpec.IntValue SUTURE_BULLET_EXTRACT_DURABILITY_COST;
    public static ModConfigSpec.DoubleValue TACZ_GUNSHOT_FRACTURE_MIN_DAMAGE;
    public static ModConfigSpec.DoubleValue TACZ_GUNSHOT_FRACTURE_CHANCE_MULTIPLIER;
    public static ModConfigSpec.DoubleValue TACZ_PNEUMOTHORAX_MIN_PENETRATING_DAMAGE;
    public static ModConfigSpec.DoubleValue TACZ_PNEUMOTHORAX_BASE_CHANCE;
    public static ModConfigSpec.IntValue TACZ_SHOTGUN_EMBEDDED_PELLETS_MIN;
    public static ModConfigSpec.IntValue TACZ_SHOTGUN_EMBEDDED_PELLETS_MAX;
    public static ModConfigSpec.IntValue TACZ_MAX_EMBEDDED_BULLETS_PER_LIMB;
    public static ModConfigSpec.DoubleValue TACZ_INJURY_SWAY_AMPLITUDE_MULTIPLIER;
    public static ModConfigSpec.DoubleValue TACZ_INJURY_ADS_SLOWDOWN_STRENGTH;

    // --- Sable: Ragdolls + Ragdoll Reactions 物理布娃娃与受创脱手兼容配置 (11项) ---
    public static ModConfigSpec.BooleanValue RAGDOLL_COMPAT_ENABLED;
    public static ModConfigSpec.DoubleValue RAGDOLL_LEG_HEAVY_DAMAGE_THRESHOLD;
    public static ModConfigSpec.IntValue RAGDOLL_LEG_KNOCKDOWN_DURATION_TICKS;
    public static ModConfigSpec.DoubleValue RAGDOLL_ARM_DISARM_DAMAGE_THRESHOLD;
    public static ModConfigSpec.DoubleValue RAGDOLL_ARM_HEAVY_HIT_DISARM_CHANCE;
    public static ModConfigSpec.DoubleValue RAGDOLL_ARM_DISARM_THROW_SPEED;
    public static ModConfigSpec.IntValue RAGDOLL_ARM_DISARM_PICKUP_DELAY_TICKS;
    public static ModConfigSpec.DoubleValue RAGDOLL_HEAVY_CALIBER_KNOCKDOWN_DAMAGE;
    public static ModConfigSpec.IntValue RAGDOLL_HEAVY_CALIBER_DURATION_TICKS;
    public static ModConfigSpec.DoubleValue RAGDOLL_EXPLOSION_MIN_DAMAGE;
    public static ModConfigSpec.IntValue RAGDOLL_EXPLOSION_DURATION_TICKS;

    // --- 3A 深黑边角回归、病理视觉噪点与程序化窒息/紧张杂音引擎配置 (12项) ---
    public static ModConfigSpec.BooleanValue TRAUMA_BLACK_CORNER_VIGNETTE_ENABLED;
    public static ModConfigSpec.DoubleValue TRAUMA_BLACK_CORNER_MAX_ALPHA;
    public static ModConfigSpec.DoubleValue TRAUMA_BLACK_CORNER_MIN_INNER_RADIUS;
    public static ModConfigSpec.DoubleValue TRAUMA_EYELID_DROOP_MAX_STRENGTH;
    public static ModConfigSpec.DoubleValue TRAUMA_HYPOXIA_VISUAL_SNOW_INTENSITY;
    public static ModConfigSpec.DoubleValue TRAUMA_COLD_SWEAT_FROST_INTENSITY;
    public static ModConfigSpec.BooleanValue TRAUMA_SOUNDSCAPE_ENGINE_ENABLED;
    public static ModConfigSpec.DoubleValue TRAUMA_SOUND_MASTER_VOLUME;
    public static ModConfigSpec.DoubleValue TRAUMA_SOUND_TINNITUS_VOLUME;
    public static ModConfigSpec.DoubleValue TRAUMA_SOUND_ASPHYXIA_WHEEZE_VOLUME;
    public static ModConfigSpec.DoubleValue TRAUMA_SOUND_BLOOD_WHOOSH_VOLUME;
    public static ModConfigSpec.DoubleValue TRAUMA_SOUND_CRITICAL_DRONE_VOLUME;










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
                .comment("体感疼痛(0~100)换算为意识每秒惩罚的除数 (penalty = perceivedPain / divisor)",
                        "推荐范围: [20.0, 120.0]")
                .defineInRange("painPenaltyDivisor", 65.0, 1.0, 200.0);
        PAIN_CONSCIOUSNESS_FLOOR = builder
                .comment("单纯疼痛可扣减意识的保底线 (默认 25.0：无缺氧/低血压/颅内出血/血胸时，仅靠疼痛最低将意识压至 25%)")
                .defineInRange("painConsciousnessFloor", 25.0, 0.0, 100.0);
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
        UNCONSCIOUS_MIN_RESPIRATION_FACTOR = builder
                .comment("意识归零(昏迷)时脑干延髓维持的最低自主呼吸倍率 (默认 0.65，防止昏迷直接导致呼吸归零与脑健康扣减)")
                .defineInRange("unconsciousMinRespirationFactor", 0.65, 0.0, 1.0);
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
                .comment("各肢体疼痛等级自然衰退 1 级所需时间 (秒)", "推荐范围: [30, 180]")
                .defineInRange("painDecayInterval", 60, 1, 600);
        MAX_PAIN_LEVEL = builder
                .comment("单肢体最高允许记录的疼痛等级上限", "推荐范围: [10, 20]")
                .defineInRange("maxPainLevel", 10, 1, 20);
        MAX_PERCEIVED_PAIN = builder
                .comment("总体体感疼痛上限 (默认 100 百分制)")
                .defineInRange("maxPerceivedPain", 100, 50, 500);
        PAIN_LIMB_EXPONENT = builder
                .comment("单肢体疼痛转总体疼痛的非线性幂指数 (p/maxPain)^exponent，越大则高痛部位占比越高")
                .defineInRange("painLimbExponent", 2.2, 1.0, 4.0);
        PAIN_DOMINANT_LIMB_WEIGHT = builder
                .comment("最痛单个肢体满级时直接贡献的总体疼痛分值 (默认 70.0，即单肢10级直接达70分)")
                .defineInRange("painDominantLimbWeight", 70.0, 10.0, 200.0);
        PAIN_SECONDARY_LIMB_WEIGHT = builder
                .comment("其余受伤肢体按非线性占比叠加的权重系数")
                .defineInRange("painSecondaryLimbWeight", 30.0, 0.0, 200.0);
        PAIN_VIGNETTE_ENABLED = builder
                .comment("体感疼痛红晕血管搏动视觉层（频率绑定心率，痛越重心跳越快红晕搏动越急促）")
                .define("painVignetteEnabled", true);
        PAIN_VIGNETTE_START_THRESHOLD = builder
                .comment("红晕开始出现的体感疼痛阈值 (0~100，低于此值不渲染)")
                .defineInRange("painVignetteStartThreshold", 12, 0, 500);
        PAIN_VIGNETTE_FULL_THRESHOLD = builder
                .comment("红晕达到最大强度的体感疼痛阈值 (0~100，与起始阈值之间线性插值)")
                .defineInRange("painVignetteFullThreshold", 68, 1, 500);
        PAIN_VIGNETTE_MAX_ALPHA = builder
                .comment("红晕最大不透明度 (0~1，建议 0.2~0.45 保持可玩性)")
                .defineInRange("painVignetteMaxAlpha", 0.45, 0.0, 1.0);
        PAIN_GRAY_WASH_THRESHOLD = builder
                .comment("灰蓝色罩（痛到发灰）开始的体感疼痛阈值 (0~100)")
                .defineInRange("painGrayWashThreshold", 42, 0, 500);
        PAIN_GRAY_WASH_MAX_ALPHA = builder
                .comment("灰蓝色罩最大不透明度 (0~1，建议不超过 0.2 避免遮挡战场信息)")
                .defineInRange("painGrayWashMaxAlpha", 0.18, 0.0, 1.0);
        PAIN_SHOCK_ENABLED = builder
                .comment("剧痛瞬发效果：体感疼痛骤升（如骨折/烧伤瞬间）触发白闪定格+画面抖动+淡出")
                .define("painShockEnabled", true);
        PAIN_SHOCK_JUMP_THRESHOLD = builder
                .comment("单次判定窗口内体感疼痛跳变量达到此值触发闪抖 (0~100)")
                .defineInRange("painShockJumpThreshold", 14, 1, 500);
        PAIN_SHOCK_SEVERE_THRESHOLD = builder
                .comment("体感疼痛越过此绝对阈值时同样触发闪抖 (0~100)")
                .defineInRange("painShockSevereThreshold", 68, 1, 500);
        PAIN_SHOCK_COOLDOWN_TICKS = builder
                .comment("闪抖触发冷却 (tick，防止连续受伤刷屏；20 tick = 1 秒)")
                .defineInRange("painShockCooldownTicks", 100, 0, 1200);
        PAIN_SHOCK_WHITE_FLASH_ALPHA = builder
                .comment("白闪最大不透明度 (0~1)")
                .defineInRange("painShockWhiteFlashAlpha", 0.65, 0.0, 1.0);
        PAIN_SHOCK_MAX_OFFSET = builder
                .comment("画面抖动最大位移 (像素，0 = 关闭抖动只保留白闪)")
                .defineInRange("painShockMaxOffset", 6.0, 0.0, 30.0);
        HEARTBEAT_TRIGGER_PAIN = builder
                .comment("体感疼痛达到此值时触发心跳音效 (0~100，与血容量/心率触发条件并联)")
                .defineInRange("heartbeatTriggerPain", 42, 0, 500);
        HEARTBEAT_PAIN_VOLUME_BONUS = builder
                .comment("疼痛时心跳音量加成 (随疼痛强度线性叠加 0~此值)")
                .defineInRange("heartbeatPainVolumeBonus", 0.35, 0.0, 1.0);
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

        BRAIN_PENALTY_MAP_BELOW_50 = builder
                .comment("MAP <50 时每秒脑健康损失系数")
                .defineInRange("brainPenaltyMapBelow50", 0.5, 0.0, 5.0);
        BRAIN_PENALTY_SBP_ABOVE_200 = builder
                .comment("SBP >200 时每秒脑健康损失系数")
                .defineInRange("brainPenaltySbpAbove200", 0.3, 0.0, 5.0);

        builder.pop(); // blood_pressure

        // 感染系统
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

        // SIRS
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

        // 伤害类型细化
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

        // 骨折系统
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

        // 断肢系统
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

        // 连续仲裁 buff 系统
        builder.comment("连续仲裁 buff 系统：伤情→能力倍率连续插值，属性仲裁与感知药水统一配置").push("arbitrated_buffs");
        ARBITER_MIN_MULTIPLIER = builder
                .comment("属性仲裁器每维度倍率下限（防归零保护）")
                .defineInRange("arbiterMinMultiplier", 0.05, 0.0, 1.0);
        POTION_HYSTERESIS_TICKS = builder
                .comment("感知药水等级切换的滞回缓冲（tick），防边界抖动")
                .defineInRange("potionHysteresisTicks", 40, 0, 200);
        PAIN_LEG_SPEED_SLOPE = builder
                .comment("腿部疼痛每级对移速的线性斜率（1 - slope*p，p 为体感疼痛 0~100）")
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
        PAIN_ACTION_EXPONENT = builder
                .comment("肢体疼痛转化为行动力衰减的非线性幂指数 (p/maxPain)^exponent")
                .defineInRange("painActionExponent", 2.2, 1.0, 4.0);
        PAIN_LEG_MAX_SPEED_PENALTY = builder
                .comment("单腿满级疼痛时的最大移速衰减比例 (0.82 = 满痛时移速降低82%)")
                .defineInRange("painLegMaxSpeedPenalty", 0.82, 0.0, 0.95);
        PAIN_ARM_MAX_ATTACK_PENALTY = builder
                .comment("单臂满级疼痛时的最大攻击力衰减比例")
                .defineInRange("painArmMaxAttackPenalty", 0.75, 0.0, 0.95);
        PAIN_ARM_MAX_SPEED_PENALTY = builder
                .comment("单臂满级疼痛时的最大攻速衰减比例")
                .defineInRange("painArmMaxSpeedPenalty", 0.75, 0.0, 0.95);
        PAIN_SYSTEMIC_MAX_SPEED_PENALTY = builder
                .comment("总体体感疼痛满值时对全身移速的最大牵制比例 (即使双腿未受伤也会因全身剧痛减速)")
                .defineInRange("painSystemicMaxSpeedPenalty", 0.65, 0.0, 0.95);
        PAIN_SYSTEMIC_MAX_ACTION_PENALTY = builder
                .comment("总体体感疼痛满值时对全身攻速与攻击力的最大牵制比例")
                .defineInRange("painSystemicMaxActionPenalty", 0.55, 0.0, 0.95);
        PAIN_SPRINT_LOCK_THRESHOLD = builder
                .comment("强制禁止疾跑的总体体感疼痛阈值 (默认42，单肢8级疼痛即打断疾跑)")
                .defineInRange("painSprintLockThreshold", 42, 0, 500);
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

        // 中毒系统
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

        // 烧伤系统
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

        // ==================== 阿片类药物系统 (opioid) ====================
        builder.comment("阿片类强效镇痛、过量呼吸抑制、三级成瘾诱导/戒断行动受限与多巴胺欣快感视觉配置").push("opioid");

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
                .comment("吗啡全支推注增加的成瘾度数值 (默认 25.0，单支全推即可达到 I 级成瘾)")
                .defineInRange("morphineAddictionGain", 25.0, 0.0, 100.0);

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
                .comment("芬太尼全支推注增加的成瘾度数值 (默认 45.0)")
                .defineInRange("fentanylAddictionGain", 45.0, 0.0, 100.0);

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

        // 5. 阿片成瘾三级 Buff 阈值、高频心理诱导、滑块持续运动与 II/III 级戒断行动受限参数
        ADDICTION_TIER1_THRESHOLD = builder
                .comment("触发「阿片成瘾 I 级」Buff 的成瘾度阈值 (默认 20)")
                .defineInRange("addictionTier1Threshold", 20, 1, 100);
        ADDICTION_TIER2_THRESHOLD = builder
                .comment("触发「阿片成瘾 II 级」Buff 的成瘾度阈值 (默认 50)")
                .defineInRange("addictionTier2Threshold", 50, 1, 100);
        ADDICTION_TIER3_THRESHOLD = builder
                .comment("触发「阿片成瘾 III 级」Buff 的成瘾度阈值 (默认 80)")
                .defineInRange("addictionTier3Threshold", 80, 1, 100);
        ADDICTION_DECAY_INTERVAL_SEC = builder
                .comment("体内无阿片残留时，成瘾度自然消退 1 点所需的秒数 (默认 240 秒，使成瘾症状持久生效)")
                .defineInRange("addictionDecayIntervalSec", 240, 5, 3600);

        ADDICTION_NAME_HALLUCINATION_CHANCE_T1 = builder
                .comment("I 级成瘾时阿片药物名称与医疗面板出现诱导话语的概率占空比 (默认 0.70)")
                .defineInRange("addictionNameHallucinationChanceT1", 0.70, 0.0, 1.0);
        ADDICTION_NAME_HALLUCINATION_CHANCE_T2 = builder
                .comment("II 级成瘾时阿片药物名称与医疗面板出现诱导话语的概率占空比 (默认 0.90)")
                .defineInRange("addictionNameHallucinationChanceT2", 0.90, 0.0, 1.0);
        ADDICTION_NAME_HALLUCINATION_CHANCE_T3 = builder
                .comment("III 级成瘾时阿片药物名称与医疗面板出现诱导话语的概率占空比 (默认 1.00)")
                .defineInRange("addictionNameHallucinationChanceT3", 1.00, 0.0, 1.0);

        ADDICTION_BLIND_ZONE_CHANCE_T1 = builder
                .comment("I 级成瘾在注射界面无法观察到安全剂量区间的概率占空比 (默认 0.85，一级成瘾即很难观察到安全剂量)")
                .defineInRange("addictionBlindZoneChanceT1", 0.85, 0.0, 1.0);
        ADDICTION_BLIND_ZONE_CHANCE_T2 = builder
                .comment("II 级成瘾在注射界面无法观察到安全剂量区间的概率占空比 (默认 0.95)")
                .defineInRange("addictionBlindZoneChanceT2", 0.95, 0.0, 1.0);
        ADDICTION_BLIND_ZONE_CHANCE_T3 = builder
                .comment("III 级成瘾在注射界面无法观察到安全剂量区间的概率占空比 (默认 1.00，完全遮蔽安全剂量)")
                .defineInRange("addictionBlindZoneChanceT3", 1.00, 0.0, 1.0);

        ADDICTION_SLIDER_DRIFT_SPEED_T1 = builder
                .comment("I 级成瘾在注射界面剂量滑块持续自主晃动/右移的速度 (刻度/秒，默认 24.0)")
                .defineInRange("addictionSliderDriftSpeedT1", 24.0, 0.0, 200.0);
        ADDICTION_SLIDER_DRIFT_SPEED_T2 = builder
                .comment("II 级成瘾在注射界面剂量滑块持续自主晃动/右移的速度 (刻度/秒，默认 42.0)")
                .defineInRange("addictionSliderDriftSpeedT2", 42.0, 0.0, 200.0);
        ADDICTION_SLIDER_DRIFT_SPEED_T3 = builder
                .comment("III 级成瘾在注射界面剂量滑块持续强行冲向满剂量的速度 (刻度/秒，默认 68.0)")
                .defineInRange("addictionSliderDriftSpeedT3", 68.0, 0.0, 200.0);

        ADDICTION_WITHDRAWAL_OPIOID_THRESHOLD = builder
                .comment("II/III 级成瘾者压制戒断行动受限所需的最低体内阿片血药浓度 (% ，默认 15；低于此浓度即触发戒断行动受限)")
                .defineInRange("addictionWithdrawalOpioidThreshold", 15, 1, 80);
        ADDICTION_WITHDRAWAL_SPEED_MULT_T2 = builder
                .comment("II 级成瘾未注射阿片（处于戒断状态）时的移动速度与动作倍率上限 (默认 0.42，禁止疾跑且移速削减 58%)")
                .defineInRange("addictionWithdrawalSpeedMultT2", 0.42, 0.05, 1.0);
        ADDICTION_WITHDRAWAL_SPEED_MULT_T3 = builder
                .comment("III 级成瘾未注射阿片（处于重度戒断状态）时的移动速度与动作倍率上限 (默认 0.16，禁止疾跑与跳跃，移速削减 84%)")
                .defineInRange("addictionWithdrawalSpeedMultT3", 0.16, 0.05, 1.0);

        // 6. 注射阿片药物愉悦性 (Euphoria) 视觉效果参数
        OPIOID_EUPHORIA_ENABLED = builder
                .comment("是否启用注射阿片类药物后的多巴胺欣快感/温暖柔光视觉特效 (默认 true)")
                .define("opioidEuphoriaEnabled", true);
        OPIOID_EUPHORIA_MAX_ALPHA = builder
                .comment("阿片欣快感视觉光晕与漂浮粒子的最大不透明度强度 (默认 0.58)")
                .defineInRange("opioidEuphoriaMaxAlpha", 0.58, 0.0, 1.0);
        OPIOID_EUPHORIA_RUSH_DURATION_SEC = builder
                .comment("推注阿片药物瞬间「暖流冲顶波 (Euphoria Rush)」持续秒数 (默认 8.0 秒)")
                .defineInRange("opioidEuphoriaRushDurationSec", 8.0, 1.0, 60.0);

        // 7. 3A 脑损伤视觉升级（真实帧缓冲历史闪回、画面定格撕裂震荡、四态皮层变色）
        BRAIN_VFX_UPGRADE_ENABLED = builder
                .comment("是否启用 3A 脑损伤升级视觉特效（历史帧闪回残影、画面硬定格撕裂震荡、RGB 棱镜色散与皮层变色，默认 true）")
                .define("brainVfxUpgradeEnabled", true);
        BRAIN_VFX_FLASHBACK_INTENSITY = builder
                .comment("脑损伤「闪回 (Temporal Flashback)」历史帧重影与记忆回闪的最大强度 (默认 0.85)")
                .defineInRange("brainVfxFlashbackIntensity", 0.85, 0.0, 1.0);
        BRAIN_VFX_FREEZE_DURATION_MS = builder
                .comment("脑损伤「画面定格震荡 (Frame-Freeze Concussion)」单次硬定格持续毫秒数 (默认 320 ms)")
                .defineInRange("brainVfxFreezeDurationMs", 320, 50, 2000);
        BRAIN_VFX_SHAKE_AMPLITUDE = builder
                .comment("脑损伤「定格撕裂震荡」画面位移与水平切片错位强度倍率 (默认 1.0)")
                .defineInRange("brainVfxShakeAmplitude", 1.0, 0.0, 3.0);
        BRAIN_VFX_COLOR_SHIFT_INTENSITY = builder
                .comment("脑损伤「皮层神经变色 (Cortical Color Shift)」与 RGB 通道分离色散的最大强度 (默认 0.82)")
                .defineInRange("brainVfxColorShiftIntensity", 0.82, 0.0, 1.0);


        // 8. 肾上腺素系统（受创短暂内源性应激 Buff + 外源性肾上腺素自动注射器 EpiPen）
        ADRENALINE_TRAUMA_ENABLED = builder
                .comment("是否启用受伤时短暂赋予内源性「肾上腺素激增 I」应激 Buff (默认 true)")
                .define("adrenalineTraumaEnabled", true);
        ADRENALINE_TRAUMA_MIN_DAMAGE = builder
                .comment("触发受创内源性肾上腺素应激所需的最低原始伤害阈值 (默认 1.0)")
                .defineInRange("adrenalineTraumaMinDamage", 2.5, 0.1, 20.0);
        ADRENALINE_TRAUMA_GAIN = builder
                .comment("单次受伤应激赋予的体内肾上腺素浓度百分比 (默认 32.0%)")
                .defineInRange("adrenalineTraumaGain", 24.0, 5.0, 100.0);
        ADRENALINE_TRAUMA_DURATION_SEC = builder
                .comment("受创内源性肾上腺素应激 Buff 持续秒数 (默认 8 秒)")
                .defineInRange("adrenalineTraumaDurationSec", 3, 2, 60);
        ADRENALINE_TRAUMA_COOLDOWN_SEC = builder
                .comment("受创内源性肾上腺素应激的生理不应期冷却秒数 (默认 18 秒，防止高频小伤害无限刷)")
                .defineInRange("adrenalineTraumaCooldownSec", 25, 1, 300);
        ADRENALINE_INJECTOR_GAIN = builder
                .comment("单支肾上腺素自动注射器 (0.3mg) 推注增加的肾上腺素浓度百分比 (默认 65.0%)")
                .defineInRange("adrenalineInjectorGain", 55.0, 10.0, 100.0);
        ADRENALINE_DECAY_RATE_PER_SEC = builder
                .comment("外源性肾上腺素每秒自然代谢清除速率 (%/s，默认 1.45，单支约维持 45 秒)")
                .defineInRange("adrenalineDecayRatePerSec", 4.5, 0.1, 20.0);
        ADRENALINE_TIER1_THRESHOLD = builder
                .comment("「肾上腺素激增 I 级」浓度阈值 (% ，默认 15)")
                .defineInRange("adrenalineTier1Threshold", 15, 1, 100);
        ADRENALINE_TIER2_THRESHOLD = builder
                .comment("「肾上腺素激增 II 级」浓度阈值 (% ，默认 45)")
                .defineInRange("adrenalineTier2Threshold", 45, 1, 100);
        ADRENALINE_TIER3_THRESHOLD = builder
                .comment("「肾上腺素激增 III 级 (交感过载临界)」浓度阈值 (% ，默认 75)")
                .defineInRange("adrenalineTier3Threshold", 75, 1, 100);
        ADRENALINE_MAX_HR_BOOST = builder
                .comment("100% 肾上腺素浓度时对心率的最大交感兴奋拉升值 (BPM，默认 58.0)")
                .defineInRange("adrenalineMaxHrBoost", 36.0, 0.0, 150.0);
        ADRENALINE_MAX_RESP_BOOST = builder
                .comment("100% 肾上腺素浓度时对呼吸频率的最大支气管扩张拉升值 (rpm，默认 10.0)")
                .defineInRange("adrenalineMaxRespBoost", 10.0, 0.0, 40.0);
        ADRENALINE_MAX_PAIN_SLOW_RELIEF = builder
                .comment("肾上腺素激增期间最高可临时豁免的疼痛移速/攻速惩罚比例 (默认 0.85 = 豁免 85% 痛觉减速)")
                .defineInRange("adrenalineMaxPainSlowRelief", 0.85, 0.0, 1.0);
        ADRENALINE_SPEED_BOOST_T1 = builder
                .comment("I 级肾上腺素激增提供的应急移动速度倍率上限 (默认 1.08 = +8% 移速)")
                .defineInRange("adrenalineSpeedBoostT1", 1.08, 1.0, 1.5);
        ADRENALINE_SPEED_BOOST_T2 = builder
                .comment("II/III 级肾上腺素激增提供的强效移动速度倍率上限 (默认 1.15 = +15% 移速)")
                .defineInRange("adrenalineSpeedBoostT2", 1.15, 1.0, 1.8);
        ADRENALINE_UNBANDAGED_BLEED_MULT = builder
                .comment("肾上腺素升压期间，未包扎/未绑止血带的开放性伤口额外加剧的外出血倍率 (默认 0.30 = +30% 出血)")
                .defineInRange("adrenalineUnbandagedBleedMult", 0.30, 0.0, 2.0);
        ADRENALINE_CRASH_DURATION_SEC = builder
                .comment("高浓度外源性肾上腺素代谢归零后的交感透支疲惫期秒数 (默认 12 秒)")
                .defineInRange("adrenalineCrashDurationSec", 4, 0, 120);
        ADRENALINE_CRASH_SPEED_MULT = builder
                .comment("肾上腺素退潮透支期内的移动速度与攻速倍率 (默认 0.88 = -12% 移速)")
                .defineInRange("adrenalineCrashSpeedMult", 0.88, 0.3, 1.0);

        builder.pop();

        // ==================== 盐酸纳洛酮与止血带/肢体坏死配置 ====================
        builder.comment("盐酸纳洛酮拮抗剂、止血带大出血限制、肢体缺血坏死与突发重伤视觉配置")
                .push("naloxone_and_tourniquet");

        NALOXONE_OPIOID_CLEAR_AMOUNT = builder
                .comment("单支纳洛酮注射立即清除的阿片血药浓度百分比 (默认 75.0%)")
                .defineInRange("naloxoneOpioidClearAmount", 75.0, 10.0, 100.0);
        NALOXONE_BLOCKADE_DURATION_SEC = builder
                .comment("纳洛酮注射后的 μ-阿片受体竞争性阻断与痛觉过敏持续时间 (秒，默认 25)")
                .defineInRange("naloxoneBlockadeDurationSec", 25, 1, 300);
        NALOXONE_RECEPTOR_BLOCK_FACTOR = builder
                .comment("纳洛酮受体阻断期内阿片镇痛保留比例 (默认 0.25 = 剥夺 75% 阿片镇痛效力)")
                .defineInRange("naloxoneReceptorBlockFactor", 0.25, 0.0, 1.0);
        NALOXONE_HYPERALGESIA_MULTIPLIER = builder
                .comment("纳洛酮受体阻断期内体感疼痛反跳放大倍率 (默认 1.22 = +22% 痛感)")
                .defineInRange("naloxoneHyperalgesiaMultiplier", 1.22, 1.0, 3.0);
        NALOXONE_WOUND_PAIN_REBOUND = builder
                .comment("纳洛酮剥夺镇痛瞬间每个受伤部位反跳增加的疼痛等级 (默认 2)")
                .defineInRange("naloxoneWoundPainRebound", 2, 0, 10);
        NALOXONE_CONSCIOUSNESS_RESTORE = builder
                .comment("纳洛酮逆转阿片过量昏迷时立即恢复的意识值 (默认 38.0)")
                .defineInRange("naloxoneConsciousnessRestore", 38.0, 0.0, 100.0);
        NALOXONE_MIN_RESTORED_RESP_RATE = builder
                .comment("纳洛酮逆转阿片呼吸暂停/抑制时恢复的最低呼吸频率 (rpm，默认 18)")
                .defineInRange("naloxoneMinRestoredRespRate", 18, 8, 40);
        NALOXONE_REBOUND_HR_BOOST = builder
                .comment("纳洛酮交感儿茶酚胺反跳对心率的拉升值 (BPM，默认 24)")
                .defineInRange("naloxoneReboundHrBoost", 24, 0, 100);
        NALOXONE_REBOUND_BP_BOOST = builder
                .comment("纳洛酮交感反跳对收缩压的拉升值 (mmHg，默认 22)")
                .defineInRange("naloxoneReboundBpBoost", 22, 0, 100);
        NALOXONE_PRECIPITATED_WITHDRAWAL_TICKS = builder
                .comment("纳洛酮促发急性戒断的反胃眩晕持续时间 (Ticks，默认 140 = 7秒)")
                .defineInRange("naloxonePrecipitatedWithdrawalTicks", 140, 0, 1200);

        BANDAGE_MAX_BLEEDING_THRESHOLD = builder
                .comment("绷带/加压绷带可直接处理的出血率上限 (mL/min，默认 300；达到此大出血阈值时必须先绑止血带)")
                .defineInRange("bandageMaxBleedingThreshold", 300, 50, 2000);
        TOURNIQUET_ISCHEMIA_STAGE1_SEC = builder
                .comment("止血带连续绑扎触发 I 阶段缺血剧痛的秒数 (默认 180 秒 = 3分钟)")
                .defineInRange("tourniquetIschemiaStage1Sec", 180, 15, 3600);
        TOURNIQUET_ISCHEMIA_STAGE2_SEC = builder
                .comment("止血带连续绑扎触发 II 阶段重度缺血剧痛的秒数 (默认 240 秒 = 4分钟)")
                .defineInRange("tourniquetIschemiaStage2Sec", 240, 30, 3600);
        TOURNIQUET_NECROSIS_TIME_SEC = builder
                .comment("止血带连续绑扎导致肢体不可逆缺血坏死的时长 (秒，默认 300 秒 = 5分钟)")
                .defineInRange("tourniquetNecrosisTimeSec", 300, 30, 3600);
        NECROSIS_SELF_INFECTION_RATE_PER_SEC = builder
                .comment("坏死肢体自身每秒上升的感染值 (%/秒，默认 5)")
                .defineInRange("necrosisSelfInfectionRatePerSec", 5, 1, 50);
        NECROSIS_OTHER_LIMBS_INFECTION_RATE_PER_SEC = builder
                .comment("每个坏死肢体导致全身其他部位每秒急剧上升的感染值 (%/秒，默认 3)")
                .defineInRange("necrosisOtherLimbsInfectionRatePerSec", 3, 1, 50);
        NECROSIS_ONE_LEG_SPEED_MULT = builder
                .comment("单腿缺血坏死时的移动速度倍率 (默认 0.38)")
                .defineInRange("necrosisOneLegSpeedMult", 0.38, 0.05, 1.0);
        NECROSIS_TWO_LEGS_SPEED_MULT = builder
                .comment("双腿缺血坏死时的移动速度倍率 (默认 0.18)")
                .defineInRange("necrosisTwoLegsSpeedMult", 0.18, 0.01, 1.0);
        NECROSIS_ONE_ARM_ACTION_MULT = builder
                .comment("单臂缺血坏死时的攻击伤害与攻速倍率 (默认 0.40)")
                .defineInRange("necrosisOneArmActionMult", 0.40, 0.05, 1.0);
        NECROSIS_TWO_ARMS_ACTION_MULT = builder
                .comment("双臂缺血坏死时的攻击伤害与攻速倍率 (默认 0.18)")
                .defineInRange("necrosisTwoArmsActionMult", 0.18, 0.01, 1.0);

        HEAVY_TRAUMA_PAIN_DELTA_THRESHOLD = builder
                .comment("触发突发重伤血花飞溅与震荡裂纹的部位总痛感单次跳变阈值 (默认 4)")
                .defineInRange("heavyTraumaPainDeltaThreshold", 4, 1, 30);
        HEAVY_TRAUMA_BLEED_DELTA_THRESHOLD = builder
                .comment("触发突发重伤血花飞溅的出血率单次暴增阈值 (mL/min，默认 150)")
                .defineInRange("heavyTraumaBleedDeltaThreshold", 150, 20, 1000);
        HEAVY_TRAUMA_SPLATTER_DURATION_MS = builder
                .comment("突发重伤动脉血斑飞溅残留持续时间 (毫秒，默认 2800)")
                .defineInRange("heavyTraumaSplatterDurationMs", 2800, 300, 15000);
        HEAVY_TRAUMA_FOV_PUNCH_MAX = builder
                .comment("突发重伤瞬间广角 FOV 冲击波最大幅度 (度，默认 13.5)")
                .defineInRange("heavyTraumaFovPunchMax", 13.5, 0.0, 45.0);
        HEAVY_TRAUMA_CAMERA_RECOIL_MULT = builder
                .comment("突发重伤 3D 镜头受击甩动增强倍率 (默认 2.2)")
                .defineInRange("heavyTraumaCameraRecoilMult", 2.2, 0.5, 10.0);


        builder.pop();

            builder.comment("野战血管缝合手术包 (field_vascular_suture_kit) 与双路径微创手术小游戏参数")
                    .push("vascular_suture_kit");

            SUTURE_MIN_CONSCIOUSNESS = builder
                    .comment("使用野战血管缝合手术包所需的最低清醒意识阈值 (% ，默认 50)")
                    .defineInRange("sutureMinConsciousness", 50, 10, 100);
            SUTURE_ARTERIAL_BLEED_THRESHOLD = builder
                    .comment("判定为主干动脉断裂（推荐路径B血管桥接，若强行选路径A结扎将引发缺血）的出血率阈值 (mL/min，默认 1000)")
                    .defineInRange("sutureArterialBleedThreshold", 1000, 200, 5000);
            SUTURE_LIGATION_DURABILITY_COST = builder
                    .comment("完成路径 A（血管结扎闭塞术）消耗的手术包耐久度 (默认 20，满耐久 100)")
                    .defineInRange("sutureLigationDurabilityCost", 20, 1, 100);
            SUTURE_ANASTOMOSIS_DURABILITY_COST = builder
                    .comment("完成路径 B（血管吻合桥接术）消耗的手术包耐久度 (默认 25，满耐久 100)")
                    .defineInRange("sutureAnastomosisDurabilityCost", 25, 1, 100);
            SUTURE_MISTAKE_PAIN_GAIN = builder
                    .comment("手术小游戏操作失误（夹偏、崩断缝线、收缩期强夹、缝歪）单次增加的局部疼痛等级 (默认 1)")
                    .defineInRange("sutureMistakePainGain", 1, 0, 10);
            SUTURE_MISTAKE_BLOOD_LOSS = builder
                    .comment("手术小游戏操作失误单次喷溅流失的血量 (mL，默认 15)")
                    .defineInRange("sutureMistakeBloodLoss", 15, 0, 500);
            SUTURE_ABORT_TEAR_BLEED_INCREASE = builder
                    .comment("撑开切口后未完成手术强行中断或昏迷造成的血管二次撕裂出血增量 (mL/min，默认 120)")
                    .defineInRange("sutureAbortTearBleedIncrease", 120, 0, 1000);
            SUTURE_ARTERIAL_LIGATION_LEG_SPEED_MULT = builder
                    .comment("对 >1000 mL/min 腿部主干动脉强行采取路径 A 结扎后的缺血移速倍率 (默认 0.62)")
                    .defineInRange("sutureArterialLigationLegSpeedMult", 0.62, 0.05, 1.0);
            SUTURE_ARTERIAL_LIGATION_ARM_ACTION_MULT = builder
                    .comment("对 >1000 mL/min 手臂主干动脉强行采取路径 A 结扎后的缺血攻速与伤害倍率 (默认 0.65)")
                    .defineInRange("sutureArterialLigationArmActionMult", 0.65, 0.05, 1.0);

            SUTURE_RETRACTOR_OPEN_THRESHOLD = builder
                    .comment("第 1 步左右创口牵开器撑开并锁定切口所需的最小张开比例 (默认 0.88)")
                    .defineInRange("sutureRetractorOpenThreshold", 0.88, 0.50, 0.98);
            SUTURE_STUMP_DRIFT_AMPLITUDE = builder
                    .comment("路径 A-2 游离血管断端受组织痉挛摆动的幅度 (像素，默认 8.5)")
                    .defineInRange("sutureStumpDriftAmplitude", 8.5, 0.0, 30.0);
            SUTURE_STUMP_CLAMP_RADIUS = builder
                    .comment("止血钳/微血管夹精准夹持血管断端的判定半径 (像素，默认 13.0)")
                    .defineInRange("sutureStumpClampRadius", 13.0, 4.0, 30.0);
            SUTURE_WINDING_REQUIRED_DEGREES = builder
                    .comment("路径 A-3 外科结绕线所需的累计旋转角度 (度，默认 720.0 = 绕满 2 圈)")
                    .defineInRange("sutureWindingRequiredDegrees", 720.0, 180.0, 1800.0);
            SUTURE_KNOT_TENSION_MIN = builder
                    .comment("路径 A-3 死结收紧锁死绿色区间的张力下限 (默认 0.68)")
                    .defineInRange("sutureKnotTensionMin", 0.68, 0.30, 0.90);
            SUTURE_KNOT_TENSION_MAX = builder
                    .comment("路径 A-3 死结收紧锁死绿色区间的张力上限 (默认 0.92)")
                    .defineInRange("sutureKnotTensionMax", 0.92, 0.50, 0.98);
            SUTURE_KNOT_TENSION_SNAP = builder
                    .comment("路径 A-3 用力过猛导致缝线崩断的极限张力阈值 (默认 0.98)")
                    .defineInRange("sutureKnotTensionSnap", 0.98, 0.80, 1.00);
            SUTURE_KNOT_HOLD_SECONDS = builder
                    .comment("路径 A-3 在绿色锁死张力区间内需保持的时长 (秒，默认 0.75)")
                    .defineInRange("sutureKnotHoldSeconds", 0.75, 0.20, 5.00);
            SUTURE_BULLDOG_CALM_BEAT_MAX = builder
                    .comment("路径 B-2 动脉舒张期允许扣上哈巴狗阻断夹的最大搏动波阈值 (默认 0.35)")
                    .defineInRange("sutureBulldogCalmBeatMax", 0.35, 0.05, 0.90);
            SUTURE_GRAFT_ALIGN_TOLERANCE_DEG = builder
                    .comment("路径 B-3 人工血管桥接管嵌入时允许的最大角度偏差 (度，默认 9.5)")
                    .defineInRange("sutureGraftAlignToleranceDeg", 9.5, 2.0, 30.0);
            SUTURE_GRAFT_SNAP_DISTANCE = builder
                    .comment("路径 B-3 人工血管桥接管嵌入断端中心的判定半径 (像素，默认 13.0)")
                    .defineInRange("sutureGraftSnapDistance", 13.0, 4.0, 35.0);
            SUTURE_ANASTOMOSIS_STITCH_COUNT = builder
                    .comment("路径 B-4 端端血管吻合所需的总缝合针数 (默认 6)")
                    .defineInRange("sutureAnastomosisStitchCount", 6, 2, 12);
            SUTURE_CURSOR_PAIN_SHAKE_MAX = builder
                    .comment("剧痛状态下手术器械准星的最大生理震颤幅度 (像素，默认 4.2)")
                    .defineInRange("sutureCursorPainShakeMax", 4.2, 0.0, 15.0);
            SUTURE_CURSOR_WITHDRAWAL_SHAKE = builder
                    .comment("阿片戒断状态下手术器械准星叠加的额外手抖幅度 (像素，默认 4.5)")
                    .defineInRange("sutureCursorWithdrawalShake", 4.5, 0.0, 15.0);

            SUTURE_CURSOR_CONSCIOUSNESS_SWAY_MAX = builder
                .comment("意识下降（低于100%逼近50%下限）时手术器械指针眩晕漂移晃动的最大幅度 (像素，默认 22.0)")
                .defineInRange("sutureCursorConsciousnessSwayMax", 22.0, 0.0, 60.0);
            BURN_RATE_MULTIPLIER = builder
                .comment("烧伤热负荷累积、深度晋升与面积上涨速率总倍率 (默认 4.5)")
                .defineInRange("burnRateMultiplier", 4.5, 0.5, 30.0);
            BURN_FIRE_AREA_GAIN_PER_SEC = builder
                .comment("身体着火或处于火焰中时每秒增加的受累部位烧伤面积百分比 (%/秒，默认 4.2)")
                .defineInRange("burnFireAreaGainPerSec", 4.2, 0.1, 50.0);
            BURN_LAVA_AREA_GAIN_PER_SEC = builder
                .comment("接触熔岩时每秒增加的受累部位烧伤面积百分比 (%/秒，默认 12.5)")
                .defineInRange("burnLavaAreaGainPerSec", 12.5, 0.5, 100.0);
            BURN_VFX_ENABLED = builder
                .comment("是否启用 3A 烧伤独特视觉效果（焦化热浪暗角、热空气折射波纹、表皮火纹与火星粒子，默认 true）")
                .define("burnVfxEnabled", true);
            BURN_VFX_MAX_INTENSITY = builder
                .comment("3A 烧伤独特视觉效果的最大强度与不透明度上限 (默认 0.88)")
                .defineInRange("burnVfxMaxIntensity", 0.88, 0.0, 1.0);

        builder.pop();

        builder.comment("四肢内出血封顶机制（骨筋膜室综合征）与野战筋膜切开减压包配置")
                .push("compartment_syndrome_and_fasciotomy");

        COMPARTMENT_MAX_INTERNAL_BLEED_RATE = builder
                .comment("单侧四肢内出血速率的解剖学初始封顶上限 (mL/min，默认 240)")
                .defineInRange("compartmentMaxInternalBleedRate", 240, 30, 1000);
        COMPARTMENT_MAX_BLOOD_CAP_ML = builder
                .comment("单个四肢骨筋膜室的最大积血容量封顶上限 (mL，默认 450.0；达到此容量时高压完全压迫闭塞该肢体内出血)")
                .defineInRange("compartmentMaxBloodCapMl", 450.0, 100.0, 2000.0);
        COMPARTMENT_BLOOD_ACCUM_MULT = builder
                .comment("四肢内出血流入密闭骨筋膜室转化为腔内血肿的速率倍率 (默认 1.25)")
                .defineInRange("compartmentBloodAccumMult", 1.25, 0.1, 10.0);
        COMPARTMENT_VENOUS_COLLAPSE_MMHG = builder
                .comment("开始产生微血管/静脉压迫自限止血效应的骨筋膜室室内压阈值 (mmHg，默认 30.0)")
                .defineInRange("compartmentVenousCollapseMmhg", 30.0, 15.0, 80.0);
        COMPARTMENT_TAMPONADE_FULL_MMHG = builder
                .comment("完全压迫闭塞腔内出血点（使该肢体有效内出血率降至 0）的封顶室内压阈值 (mmHg，默认 65.0)")
                .defineInRange("compartmentTamponadeFullMmhg", 65.0, 35.0, 140.0);
        COMPARTMENT_SYNDROME_THRESHOLD_MMHG = builder
                .comment("确诊触发「骨筋膜室综合征」的室内压阈值 (mmHg，默认 30.0)")
                .defineInRange("compartmentSyndromeThresholdMmhg", 30.0, 15.0, 80.0);
        COMPARTMENT_CRITICAL_DELTA_P_MMHG = builder
                .comment("触发骨筋膜室微循环缺血的临界灌注压差阈值 (ΔP = 舒张压 - 室内压 <= 30.0 mmHg)")
                .defineInRange("compartmentCriticalDeltaPMmhg", 30.0, 10.0, 60.0);
        COMPARTMENT_ISCHEMIA_START_MMHG = builder
                .comment("骨筋膜室高压开始累计缺血坏死倒计时的室内压阈值 (mmHg，默认 42.0)")
                .defineInRange("compartmentIschemiaStartMmhg", 42.0, 20.0, 100.0);
        COMPARTMENT_NECROSIS_TIME_SEC = builder
                .comment("重度骨筋膜室综合征未切开减压导致肢体不可逆缺血坏死的持续秒数 (默认 240 秒 = 4分钟)")
                .defineInRange("compartmentNecrosisTimeSec", 240, 30, 3600);
        COMPARTMENT_STAGE1_MIN_PAIN = builder
                .comment("骨筋膜室综合征早期（>=30 mmHg）强制维持的最低深部剧烈胀痛等级 (默认 7)")
                .defineInRange("compartmentStage1MinPain", 7, 1, 10);
        COMPARTMENT_STAGE2_MIN_PAIN = builder
                .comment("骨筋膜室综合征重度缺血期（>=42 mmHg）强制维持的最低剧痛等级 (默认 9)")
                .defineInRange("compartmentStage2MinPain", 9, 1, 10);
        COMPARTMENT_LEG_SPEED_MULT = builder
                .comment("下肢骨筋膜室综合征导致肌群麻痹时的移动速度倍率 (默认 0.55)")
                .defineInRange("compartmentLegSpeedMult", 0.55, 0.05, 1.0);
        COMPARTMENT_ARM_ACTION_MULT = builder
                .comment("上肢骨筋膜室综合征导致前臂肌群麻痹时的攻速与伤害倍率 (默认 0.58)")
                .defineInRange("compartmentArmActionMult", 0.58, 0.05, 1.0);
        COMPARTMENT_RHABDO_TOXIN_PER_SEC = builder
                .comment("重度骨筋膜室综合征引发横纹肌溶解每秒向血液释放的肌红蛋白毒素值 (默认 0.35)")
                .defineInRange("compartmentRhabdoToxinPerSec", 0.35, 0.0, 10.0);
        COMPARTMENT_NATURAL_ABSORB_ML_PER_SEC = builder
                .comment("内出血停止且未进展为骨筋膜室综合征时，腔内小血肿每秒自然吸收消退速率 (mL/秒，默认 1.5)")
                .defineInRange("compartmentNaturalAbsorbMlPerSec", 1.5, 0.0, 50.0);

        FASCIOTOMY_MIN_CONSCIOUSNESS = builder
                .comment("使用野战筋膜切开减压包所需的最低清醒意识阈值 (% ，默认 50)")
                .defineInRange("fasciotomyMinConsciousness", 50, 10, 100);
        FASCIOTOMY_DURABILITY_COST = builder
                .comment("完成一次筋膜切开减压手术消耗的减压包耐久度 (默认 25，满耐久 100)")
                .defineInRange("fasciotomyDurabilityCost", 25, 1, 100);
        FASCIOTOMY_MISTAKE_PAIN_GAIN = builder
                .comment("筋膜切开减压手术操作失误单次增加的局部疼痛等级 (默认 1)")
                .defineInRange("fasciotomyMistakePainGain", 1, 0, 10);
        FASCIOTOMY_MISTAKE_BLOOD_LOSS = builder
                .comment("筋膜切开减压手术操作失误单次流失的血量 (mL，默认 15)")
                .defineInRange("fasciotomyMistakeBloodLoss", 15, 0, 500);
        FASCIOTOMY_ABORT_BLEED_PENALTY = builder
                .comment("切开深筋膜解除压迫后未完成电凝止血即强行中断手术造成的开放性出血增量 (mL/min，默认 100)")
                .defineInRange("fasciotomyAbortBleedPenalty", 100, 0, 1000);
        FASCIOTOMY_INCISION_CORRIDOR_WIDTH = builder
                .comment("第 1 步手术刀纵行切开深筋膜允许的安全轨迹半宽容差 (像素，默认 14.0)")
                .defineInRange("fasciotomyIncisionCorridorWidth", 14.0, 5.0, 40.0);
        FASCIOTOMY_CLOT_COUNT = builder
                .comment("第 2 步深部血肿清除需吸引清理的凝块数量 (默认 5)")
                .defineInRange("fasciotomyClotCount", 5, 2, 10);
        FASCIOTOMY_BLEEDER_COUNT = builder
                .comment("第 3 步减压后需双极电凝止血的深部复发出血点数量 (默认 3)")
                .defineInRange("fasciotomyBleederCount", 3, 1, 8);
        FASCIOTOMY_CAUTERY_HOLD_SECONDS = builder
                .comment("第 3 步双极电凝镊在单个深部出血点上需保持电凝的时长 (秒，默认 0.55)")
                .defineInRange("fasciotomyCauteryHoldSeconds", 0.55, 0.15, 3.0);

        builder.pop();

        builder.comment("骨折夹板固定愈合、布洛芬缓释止痛片（非阿片类）与氨甲环酸内出血止血注射器配置")
                .push("splint_nsaid_and_txa");

        SPLINT_FRACTURE_HEAL_TIME_SEC = builder
                .comment("绑上骨折夹板后骨折自然愈合所需的倒计时秒数 (默认 300 秒 = 5分钟)")
                .defineInRange("splintFractureHealTimeSec", 300, 30, 3600);
        SPLINT_PAIN_RELIEF = builder
                .comment("使用夹板固定骨断端瞬间立即缓解的局部疼痛等级 (默认 2)")
                .defineInRange("splintPainRelief", 2, 0, 10);
        FRACTURE_UNSPLINTED_LEG_SPEED_MULT = builder
                .comment("腿部骨折未绑夹板固定时的基础移动速度倍率上限 (默认 0.45)")
                .defineInRange("fractureUnsplintedLegSpeedMult", 0.45, 0.05, 1.0);
        FRACTURE_SPLINTED_LEG_SPEED_MULT = builder
                .comment("腿部骨折绑上夹板固定后恢复的移动速度倍率上限 (默认 0.78)")
                .defineInRange("fractureSplintedLegSpeedMult", 0.78, 0.10, 1.0);
        FRACTURE_UNSPLINTED_ARM_ACTION_MULT = builder
                .comment("手臂骨折未绑夹板固定时的攻击速度与伤害倍率上限 (默认 0.50)")
                .defineInRange("fractureUnsplintedArmActionMult", 0.50, 0.05, 1.0);
        FRACTURE_SPLINTED_ARM_ACTION_MULT = builder
                .comment("手臂骨折绑上夹板固定后恢复的攻击速度与伤害倍率上限 (默认 0.82)")
                .defineInRange("fractureSplintedArmActionMult", 0.82, 0.10, 1.0);
        SPLINT_MOBILITY_PAIN_RELIEF_FACTOR = builder
                .comment("骨折部位绑上夹板后，对该部位疼痛引起的移速/攻速惩罚的减免比例 (默认 0.50 = 减免 50% 痛觉减速)")
                .defineInRange("splintMobilityPainReliefFactor", 0.50, 0.0, 1.0);
        SPLINT_ALLOW_WEAK_JUMP = builder
                .comment("腿部骨折绑上夹板固定后是否恢复受限跳跃能力 (默认 true)")
                .define("splintAllowWeakJump", true);
        SPLINT_JUMP_VELOCITY_MULT = builder
                .comment("腿部骨折绑上夹板固定后的跳跃初速度倍率 (默认 0.62)")
                .defineInRange("splintJumpVelocityMult", 0.62, 0.10, 1.0);

        NSAID_DOSE_DURABILITY_COST = builder
                .comment("单次服用布洛芬缓释止痛片消耗的药瓶耐久度 (默认 25，满耐久 100 = 4次)")
                .defineInRange("nsaidDoseDurabilityCost", 25, 1, 100);
        NSAID_ONSET_DELAY_SEC = builder
                .comment("布洛芬口服后胃肠吸收潜伏延迟秒数 (默认 18 秒；潜伏期内镇痛效果为 0，体现生效慢)")
                .defineInRange("nsaidOnsetDelaySec", 18, 1, 300);
        NSAID_RAMP_UP_SEC = builder
                .comment("布洛芬度过潜伏期后药效从 0% 缓慢爬升至峰值所需的秒数 (默认 25 秒)")
                .defineInRange("nsaidRampUpSec", 25, 1, 300);
        NSAID_ACTIVE_DURATION_SEC = builder
                .comment("布洛芬起效后的持续温和镇痛秒数 (默认 120 秒)")
                .defineInRange("nsaidActiveDurationSec", 120, 10, 1200);
        NSAID_MAX_PERCEIVED_PAIN_REDUCTION = builder
                .comment("布洛芬满效时对总体体感疼痛的最大压制点数 (天花板效应，默认 18.0，体现效果差)")
                .defineInRange("nsaidMaxPerceivedPainReduction", 18.0, 1.0, 100.0);
        NSAID_LIMB_PAIN_RELIEF_INTERVAL_SEC = builder
                .comment("布洛芬起效期内每间隔多少秒才缓慢消除 1 点目标部位疼痛 (默认 20 秒)")
                .defineInRange("nsaidLimbPainReliefIntervalSec", 20, 5, 300);
        NSAID_MAX_LIMB_PAIN_REDUCTION = builder
                .comment("单次布洛芬药效周期最多可累计消除的部位疼痛等级上限 (默认 2 级)")
                .defineInRange("nsaidMaxLimbPainReduction", 2, 0, 10);
        NSAID_OVERDOSE_GASTRIC_TOXIN_GAIN = builder
                .comment("在布洛芬药效期内重复过量服药引发胃黏膜损伤与抗血小板副作用增加的毒素值 (默认 12.0)")
                .defineInRange("nsaidOverdoseGastricToxinGain", 12.0, 0.0, 100.0);

        TXA_IMMEDIATE_INTERNAL_BLEED_REDUCTION = builder
                .comment("单支氨甲环酸 (TXA) 注射瞬间对目标部位直接削减的内出血速率 (mL/min，默认 65)")
                .defineInRange("txaImmediateInternalBleedReduction", 65, 5, 1000);
        TXA_SYSTEMIC_INTERNAL_BLEED_REDUCTION = builder
                .comment("单支氨甲环酸 (TXA) 注射瞬间对全身其余部位协同削减的内出血速率 (mL/min，默认 35)")
                .defineInRange("txaSystemicInternalBleedReduction", 35, 0, 1000);
        TXA_CONTINUOUS_CLOT_RATE_PER_SEC = builder
                .comment("氨甲环酸在体内有效期内每秒持续抗纤溶促凝消除的内出血速率 (mL/min/秒，默认 4)")
                .defineInRange("txaContinuousClotRatePerSec", 4, 0, 100);
        TXA_CONCENTRATION_PER_DOSE = builder
                .comment("单支氨甲环酸推注增加的血药浓度百分比 (默认 55.0% ；连打两支即跨入 >=75% 血栓风险区)")
                .defineInRange("txaConcentrationPerDose", 55.0, 10.0, 100.0);
        TXA_DECAY_RATE_PER_SEC = builder
                .comment("体内氨甲环酸血药浓度每秒自然代谢衰减速率 (%/秒，默认 0.65，单支约维持 85 秒)")
                .defineInRange("txaDecayRatePerSec", 0.65, 0.05, 20.0);
        TXA_HYPOTENSION_SYS_DROP = builder
                .comment("氨甲环酸快速推注副作用：外周血管扩张导致收缩压瞬间下降值 (mmHg，默认 16)")
                .defineInRange("txaHypotensionSysDrop", 16, 0, 80);
        TXA_HYPOTENSION_DIA_DROP = builder
                .comment("氨甲环酸快速推注副作用：舒张压瞬间下降值 (mmHg，默认 10)")
                .defineInRange("txaHypotensionDiaDrop", 10, 0, 60);
        TXA_REFLEX_HR_BOOST = builder
                .comment("氨甲环酸推注低血压引起的反射性心率加快值 (BPM，默认 10)")
                .defineInRange("txaReflexHrBoost", 10, 0, 60);
        TXA_THROMBOSIS_THRESHOLD = builder
                .comment("触发氨甲环酸过量「高凝微血栓形成」副作用的血药浓度阈值 (% ，默认 75)")
                .defineInRange("txaThrombosisThreshold", 75, 20, 100);
        TXA_THROMBOSIS_O2_PENALTY_PER_SEC = builder
                .comment("高凝微血栓状态（>=75% 浓度）堵塞微循环每秒额外扣减的血氧值 (%/秒，默认 0.85)")
                .defineInRange("txaThrombosisO2PenaltyPerSec", 0.85, 0.0, 10.0);
        TXA_THROMBOSIS_ISCHEMIA_MULT = builder
                .comment("体内存在高浓度氨甲环酸时，绑有止血带或骨筋膜室综合征的淤血肢体缺血恶化加速倍率 (默认 2 倍)")
                .defineInRange("txaThrombosisIschemiaMult", 2, 1, 10);
        TXA_DIZZINESS_TICKS = builder
                .comment("低压或过量推注氨甲环酸引发脑供血不足与GABA拮抗眩晕的时间 (Ticks，默认 100 = 5秒)")
                .defineInRange("txaDizzinessTicks", 100, 0, 1200);

        builder.pop();

        builder.comment("野战骨科复位固定包、四肢开放性/不稳定骨折与胸部肋骨骨折/连枷胸/迟发性血气胸配置")
                .push("orthopedic_and_rib_fracture");

        ORTHO_MIN_CONSCIOUSNESS = builder
                .comment("执行骨科复位固定手术所需的最低意识阈值 (% ，默认 50)")
                .defineInRange("orthoMinConsciousness", 50, 10, 100);
        ORTHO_DURABILITY_COST = builder
                .comment("单次骨科复位固定手术消耗的手术包耐久度 (默认 25，满耐久 100 = 4次)")
                .defineInRange("orthoDurabilityCost", 25, 1, 100);
        ORTHO_SURGERY_HEAL_TIME_SEC = builder
                .comment("经骨科手术（外固定架/肋骨环抱器）精确复位锁定后的骨折愈合倒计时秒数 (默认 210 秒)")
                .defineInRange("orthoSurgeryHealTimeSec", 210, 30, 3600);
        ORTHO_MISTAKE_PAIN_GAIN = builder
                .comment("骨科手术步骤失误（夹伤血管或钻偏骨皮质）增加的局部疼痛等级 (默认 1)")
                .defineInRange("orthoMistakePainGain", 1, 0, 10);
        ORTHO_MISTAKE_BLOOD_LOSS = builder
                .comment("骨科手术步骤失误造成的瞬时失血量 (mL，默认 15)")
                .defineInRange("orthoMistakeBloodLoss", 15, 0, 500);
        ORTHO_ABORT_INTERNAL_BLEED_PENALTY = builder
                .comment("骨科手术中途强行退出或昏迷中断导致骨断端二次移位刺伤血管增加的内出血速率 (mL/min，默认 85)")
                .defineInRange("orthoAbortInternalBleedPenalty", 85, 0, 1000);
        ORTHO_SPLINTER_COUNT = builder
                .comment("骨科手术阶段1：需用咬骨钳清理夹除的游离碎骨片数量 (默认 4)")
                .defineInRange("orthoSplinterCount", 4, 2, 8);
        ORTHO_REDUCTION_TOLERANCE_PX = builder
                .comment("骨科手术阶段2：X光透视下骨断端牵引对位允许的最大像素误差 (默认 8.5)")
                .defineInRange("orthoReductionTolerancePx", 8.5, 3.0, 24.0);
        ORTHO_REDUCTION_HOLD_SECONDS = builder
                .comment("骨科手术阶段2：骨断端对位在绿区内保持稳定以锁紧复位钳所需的秒数 (默认 0.65)")
                .defineInRange("orthoReductionHoldSeconds", 0.65, 0.20, 3.0);
        ORTHO_SCREW_COUNT = builder
                .comment("骨科手术阶段3：需钻入的钛合金固定螺钉 / 镍钛肋骨环抱器卡爪数量 (默认 4)")
                .defineInRange("orthoScrewCount", 4, 2, 6);
        ORTHO_DRILL_HOLD_SECONDS = builder
                .comment("骨科手术阶段3：每枚骨螺钉钻入或肋骨环抱器卡合所需的按住秒数 (默认 0.48)")
                .defineInRange("orthoDrillHoldSeconds", 0.48, 0.15, 2.5);
        OPEN_FRACTURE_SPLINT_INFECTION_PENALTY_PER_SEC = builder
                .comment("临床禁忌惩罚：对未清创的开放性骨折直接绑夹板导致封闭创腔每秒额外增加的感染值 (默认 1)")
                .defineInRange("openFractureSplintInfectionPenaltyPerSec", 1, 0, 20);
        UNSTABLE_FRACTURE_MOVE_INTERNAL_BLEED_GAIN = builder
                .comment("未固定的开放性/移位骨折在疾跑或跳跃时断骨切割血管每次增加的内出血速率 (mL/min，默认 18)")
                .defineInRange("unstableFractureMoveInternalBleedGain", 18, 0, 200);
        RIB_FRACTURE_DELAYED_HEMOTHORAX_STRAIN_THRESHOLD = builder
                .comment("未绑加压胸带的单纯肋骨骨折在跑跳/战斗下触发「非静默迟发性血气胸」所需的累积胸壁应力阈值 (默认 12)")
                .defineInRange("ribFractureDelayedHemothoraxStrainThreshold", 12, 3, 120);
        RIB_FRACTURE_DELAYED_HEMOTHORAX_BLOOD_ML = builder
                .comment("迟发性血气胸触发瞬间肋骨断端刺破肺脏/肋间血管涌入胸腔的积血量 (mL，默认 180.0)")
                .defineInRange("ribFractureDelayedHemothoraxBloodMl", 180.0, 20.0, 1000.0);
        FLAIL_CHEST_O2_PENALTY_PER_SEC = builder
                .comment("连枷胸（浮动胸壁反常呼吸）未做肋骨环抱器内固定手术前每秒额外损失的血氧饱和度 (%/秒，默认 0.55)")
                .defineInRange("flailChestO2PenaltyPerSec", 0.55, 0.05, 5.0);

        CHEST_DRAIN_MIN_CONSCIOUSNESS = builder
                .comment("执行胸腔闭式引流手术所需的最低意识阈值 (% ，默认 50)")
                .defineInRange("chestDrainMinConsciousness", 50, 10, 100);
        CHEST_DRAIN_DURABILITY_COST = builder
                .comment("单次胸腔闭式引流手术消耗的引流包耐久度 (默认 25，满耐久 100 = 4次)")
                .defineInRange("chestDrainDurabilityCost", 25, 1, 100);
        CHEST_DRAIN_MISTAKE_PAIN_GAIN = builder
                .comment("胸腔闭式引流手术操作失误（切伤肋间动脉或负压过高）增加的胸痛等级 (默认 1)")
                .defineInRange("chestDrainMistakePainGain", 1, 0, 10);
        CHEST_DRAIN_MISTAKE_BLOOD_LOSS = builder
                .comment("胸腔闭式引流手术操作失误造成的瞬时失血量 (mL，默认 15)")
                .defineInRange("chestDrainMistakeBloodLoss", 15, 0, 500);
        CHEST_DRAIN_ABORT_BLEED_PENALTY = builder
                .comment("胸腔闭式引流切开肋间后强行中断手术导致开放性气胸与出血增加的内出血速率 (mL/min，默认 90)")
                .defineInRange("chestDrainAbortBleedPenalty", 90, 0, 1000);
        HEMOTHORAX_SELF_ABSORB_MAX_ML = builder
                .comment("胸膜淋巴系统可自行缓慢吸收的最大少量胸腔积血上限 (mL，默认 250.0；超过此值必须用胸腔闭式引流包排空)")
                .defineInRange("hemothoraxSelfAbsorbMaxMl", 250.0, 0.0, 1000.0);
        HEMOTHORAX_SELF_ABSORB_RATE_ML_PER_SEC = builder
                .comment("少量胸腔积血 (<250mL) 在无活动性胸腔内出血时的自然吸收速率 (mL/秒，默认 2.5)")
                .defineInRange("hemothoraxSelfAbsorbRateMlPerSec", 2.5, 0.1, 50.0);
        LUNG_CONTUSION_BASE_DECAY_PER_SEC = builder
                .comment("解除血胸压迫与连枷胸后，肺挫伤在非疾跑状态下的基础自然消退速率 (%/秒，默认 0.25)")
                .defineInRange("lungContusionBaseDecayPerSec", 0.25, 0.01, 10.0);
        LUNG_CONTUSION_TXA_SPEED_MULT = builder
                .comment("体内存在氨甲环酸 (TXA) 有效浓度时，封堵肺泡毛细血管渗血带来的肺挫伤消退加速倍率 (默认 2.0 倍)")
                .defineInRange("lungContusionTxaSpeedMult", 2.0, 1.0, 10.0);
        LUNG_CONTUSION_NSAID_SPEED_MULT = builder
                .comment("口服布洛芬抗炎起效期间，减轻肺间质炎性水肿带来的肺挫伤消退加速倍率 (默认 1.8 倍)")
                .defineInRange("lungContusionNsaidSpeedMult", 1.8, 1.0, 10.0);
        LUNG_CONTUSION_SPRINT_WORSEN_PER_SEC = builder
                .comment("顶着肺挫伤强行剧烈疾跑导致肺毛细血管压飙升、肺泡水肿加重的速率 (%/秒，默认 0.35)")
                .defineInRange("lungContusionSprintWorsenPerSec", 0.35, 0.0, 10.0);
        PNEUMOTHORAX_CLOSED_SELF_ABSORB_MAX_ML = builder
                .comment("胸膜毛细血管可自行缓慢吸收的闭合性轻度气胸最大积气量上限 (mL，默认 250.0)")
                .defineInRange("pneumothoraxClosedSelfAbsorbMaxMl", 250.0, 0.0, 1000.0);
        PNEUMOTHORAX_SELF_ABSORB_RATE_ML_PER_SEC = builder
                .comment("闭合性轻度气胸 (<=250mL) 在非疾跑状态下的自然吸收速率 (mL/秒，默认 2.0)")
                .defineInRange("pneumothoraxSelfAbsorbRateMlPerSec", 2.0, 0.1, 50.0);
        PNEUMOTHORAX_OPEN_INFLOW_ML_PER_SEC = builder
                .comment("未封堵的开放性气胸（吸吮性胸壁伤口）每秒从外界吸入胸膜腔的空气量 (mL/秒，默认 18.0)")
                .defineInRange("pneumothoraxOpenInflowMlPerSec", 18.0, 1.0, 200.0);
        PNEUMOTHORAX_VALVE_SEAL_VENT_ML_PER_SEC = builder
                .comment("使用加压绷带执行「三边单向阀胸封」后，呼气相每秒单向排出的胸膜腔残气量 (mL/秒，默认 4.5)")
                .defineInRange("pneumothoraxValveSealVentMlPerSec", 4.5, 0.5, 50.0);
        PNEUMOTHORAX_DEAD_SEAL_TENSION_GAIN_ML_PER_SEC = builder
                .comment("临床禁忌惩罚：用普通绷带死封未排气的气胸伤口，导致肺漏气只进不出憋成张力性气胸的积气速率 (mL/秒，默认 22.0)")
                .defineInRange("pneumothoraxDeadSealTensionGainMlPerSec", 22.0, 2.0, 200.0);
        PNEUMOTHORAX_TENSION_THRESHOLD_ML = builder
                .comment("触发致命「张力性气胸（胸内正压 >+15cmH2O，压迫腔静脉引发梗阻性休克）」的胸膜腔积气阈值 (mL，默认 800.0)")
                .defineInRange("pneumothoraxTensionThresholdMl", 800.0, 300.0, 2000.0);
        PNEUMOTHORAX_TENSION_BP_DROP_PER_SEC = builder
                .comment("张力性气胸压瘪上下腔静脉导致回心血量断崖下跌（梗阻性休克）每秒扣减的收缩压/舒张压 (mmHg/秒，默认 2)")
                .defineInRange("pneumothoraxTensionBpDropPerSec", 2, 0, 20);
        PNEUMOTHORAX_TENSION_O2_LOSS_PER_SEC = builder
                .comment("张力性气胸导致患侧肺完全塌陷与纵隔移位每秒额外扣除的血氧饱和度 (%/秒，默认 0.85)")
                .defineInRange("pneumothoraxTensionO2LossPerSec", 0.85, 0.1, 10.0);
        DECOMPRESSION_NEEDLE_DRAIN_ML = builder
                .comment("单次使用 14G 胸腔穿刺减压针在第 2 肋间穿刺排出的高压胸膜腔积气量 (mL，默认 550.0)")
                .defineInRange("decompressionNeedleDrainMl", 550.0, 100.0, 2000.0);
        DECOMPRESSION_NEEDLE_SAFE_WINDOW_SEC = builder
                .comment("胸腔穿刺减压针留置套管单向阀阻止张力性气胸复发的安全窗口持续秒数 (默认 180 秒)")
                .defineInRange("decompressionNeedleSafeWindowSec", 180, 30, 1800);
        DECOMPRESSION_NEEDLE_DURABILITY_COST = builder
                .comment("单次胸腔穿刺减压消耗的减压针耐久度 (默认 50，满耐久 100 = 2次)")
                .defineInRange("decompressionNeedleDurabilityCost", 50, 1, 100);


            builder.pop();

        builder.comment("中毒、局部/全身感染与 SIRS（全身炎症反应综合征）临床阶梯药物与针剂配置")
                .push("toxin_infection_and_sirs_drugs");

        CHARCOAL_DOSE_DURABILITY_COST = builder
                .comment("单次口服医用活性炭混悬液消耗的耐久度 (默认 25，满耐久 100 = 4次)")
                .defineInRange("charcoalDoseDurabilityCost", 25, 1, 100);
        CHARCOAL_IMMEDIATE_TOXIN_CLEAR = builder
                .comment("医用活性炭混悬液口服瞬间在胃肠道直接吸附清除的轻中度毒素值 (默认 22.0)")
                .defineInRange("charcoalImmediateToxinClear", 22.0, 1.0, 100.0);
        CHARCOAL_DURATION_SEC = builder
                .comment("医用活性炭在肠道内维持肠肝循环阻断与加速排毒的持续时间 (秒，默认 90)")
                .defineInRange("charcoalDurationSec", 90, 10, 1200);
        CHARCOAL_DECAY_MULT = builder
                .comment("医用活性炭生效期间对体内毒素每秒自然代谢清除速率的加速倍率 (默认 2.4)")
                .defineInRange("charcoalDecayMult", 2.4, 1.0, 10.0);
        CHARCOAL_SEVERE_TOXIN_THRESHOLD = builder
                .comment("判定为重度深部血液/细胞内中毒（单纯胃肠活性炭直接吸附效率折减 50%，需配合乙酰半胱氨酸静脉解毒）的毒素阈值 (默认 60.0)")
                .defineInRange("charcoalSevereToxinThreshold", 60.0, 20.0, 100.0);
        CHARCOAL_MIN_CONSCIOUSNESS = builder
                .comment("安全口服活性炭混悬液所需的最低清醒意识阈值（防黑炭误吸性肺炎，默认 35）")
                .defineInRange("charcoalMinConsciousness", 35, 1, 100);

        NAC_MAX_DOSE_UNITS = builder
                .comment("单支乙酰半胱氨酸解毒注射液的最大刻度单位 (默认 100)")
                .defineInRange("nacMaxDoseUnits", 100, 10, 1000);
        NAC_MAX_MG = builder
                .comment("单支乙酰半胱氨酸解毒注射液对应的标准毫克数 (默认 2000.0 mg)")
                .defineInRange("nacMaxMg", 2000.0, 100.0, 10000.0);
        NAC_IMMEDIATE_TOXIN_CLEAR = builder
                .comment("乙酰半胱氨酸全支 (100% 剂量) 推注瞬间中和清除的血液/横纹肌溶解毒素值 (默认 48.0)")
                .defineInRange("nacImmediateToxinClear", 48.0, 5.0, 100.0);
        NAC_CONCENTRATION_PER_FULL_DOSE = builder
                .comment("乙酰半胱氨酸全支推注增加的体内血药浓度百分比 (默认 65.0%)")
                .defineInRange("nacConcentrationPerFullDose", 65.0, 10.0, 150.0);
        NAC_DECAY_RATE_PER_SEC = builder
                .comment("体内乙酰半胱氨酸血药浓度每秒自然代谢衰减速率 (%/秒，默认 0.70)")
                .defineInRange("nacDecayRatePerSec", 0.70, 0.05, 10.0);
        NAC_CONTINUOUS_TOXIN_CLEAR_PER_SEC = builder
                .comment("乙酰半胱氨酸有效血药浓度期间每秒持续中和清除的全身毒素值 (点/秒，默认 0.85)")
                .defineInRange("nacContinuousToxinClearPerSec", 0.85, 0.05, 10.0);
        NAC_HISTAMINE_OVERDOSE_THRESHOLD = builder
                .comment("触发乙酰半胱氨酸过快/过量推注「类过敏组胺释放反应 (Anaphylactoid Reaction)」的血药浓度阈值 (% ，默认 70)")
                .defineInRange("nacHistamineOverdoseThreshold", 70, 25, 100);
        NAC_HISTAMINE_SYS_BP_DROP = builder
                .comment("乙酰半胱氨酸过量诱发组胺释放导致外周血管扩张引发的收缩压下降值 (mmHg，默认 18)")
                .defineInRange("nacHistamineSysBpDrop", 18, 0, 80);
        NAC_HISTAMINE_BRONCHOSPASM_O2_LOSS = builder
                .comment("乙酰半胱氨酸过量诱发组胺支气管痉挛每秒额外扣除的血氧值 (%/秒，默认 0.55)")
                .defineInRange("nacHistamineBronchospasmO2Loss", 0.55, 0.0, 5.0);

        POVIDONE_DURABILITY_COST = builder
                .comment("单次对部位喷涂聚维酮碘消毒喷雾消耗的耐久度 (默认 20，满耐久 100 = 5次)")
                .defineInRange("povidoneDurabilityCost", 20, 1, 100);
        POVIDONE_IMMEDIATE_INFECTION_CLEAN = builder
                .comment("聚维酮碘对轻度表浅感染部位直接杀灭消除的感染值 (默认 18)")
                .defineInRange("povidoneImmediateInfectionClean", 18, 1, 100);
        POVIDONE_DEEP_INFECTION_THRESHOLD = builder
                .comment("局部感染深入肌层（单纯外用碘伏直接清除效果折减 65%，需配合全身抗生素）的感染阈值 (默认 45)")
                .defineInRange("povidoneDeepInfectionThreshold", 45, 10, 100);
        POVIDONE_PROTECTION_DURATION_SEC = builder
                .comment("聚维酮碘在创面形成棕褐色络合碘杀菌保护膜的持续秒数 (默认 120 秒)")
                .defineInRange("povidoneProtectionDurationSec", 120, 10, 1200);
        POVIDONE_IMMUNE_SPEED_MULT = builder
                .comment("聚维酮碘保护膜生效期内局部伤口感染清除速率倍率 (默认 2.5)")
                .defineInRange("povidoneImmuneSpeedMult", 2.5, 1.0, 10.0);
        POVIDONE_OPEN_WOUND_STING_PAIN = builder
                .comment("对未闭合开放性伤口或浅表烧伤直接喷涂高浓度碘伏引发的短暂化学刺激痛增量 (默认 1)")
                .defineInRange("povidoneOpenWoundStingPain", 1, 0, 5);

        AMOXICILLIN_DOSE_DURABILITY_COST = builder
                .comment("单次口服阿莫西林克拉维酸钾片消耗的药瓶耐久度 (默认 25，满耐久 100 = 4次)")
                .defineInRange("amoxicillinDoseDurabilityCost", 25, 1, 100);
        AMOXICILLIN_ONSET_DELAY_SEC = builder
                .comment("阿莫西林克拉维酸钾口服后胃肠吸收进入血液循环所需的潜伏延迟秒数 (默认 15 秒)")
                .defineInRange("amoxicillinOnsetDelaySec", 15, 1, 300);
        AMOXICILLIN_ACTIVE_DURATION_SEC = builder
                .comment("阿莫西林克拉维酸钾在体内维持有效抑菌血药浓度的持续秒数 (默认 180 秒)")
                .defineInRange("amoxicillinActiveDurationSec", 180, 20, 1800);
        AMOXICILLIN_CONTINUOUS_KILL_PER_SEC = builder
                .comment("阿莫西林起效期间每秒主动杀灭全身各部位细菌感染的速率 (点/秒，默认 0.48)")
                .defineInRange("amoxicillinContinuousKillPerSec", 0.48, 0.05, 10.0);
        AMOXICILLIN_IMMUNE_MULT = builder
                .comment("阿莫西林起效期间对全身免疫抗感染速率的加成倍率 (默认 3.5)")
                .defineInRange("amoxicillinImmuneMult", 3.5, 1.0, 10.0);
        AMOXICILLIN_SHOCK_MALABSORPTION_FACTOR = builder
                .comment("当处于 SIRS >= 2 级或休克低血压 (MAP < 65) 胃肠缺血水肿时，口服抗生素吸收率保留比例 (默认 0.30 = 衰减 70%)")
                .defineInRange("amoxicillinShockMalabsorptionFactor", 0.30, 0.05, 1.0);
        AMOXICILLIN_OVERDOSE_TOXIN_GAIN = builder
                .comment("在阿莫西林药效期内重复过量吞服引发肠道菌群失调与肝肾代谢毒性增加的毒素值 (默认 10.0)")
                .defineInRange("amoxicillinOverdoseToxinGain", 10.0, 0.0, 100.0);

        MEROPENEM_MAX_DOSE_UNITS = builder
                .comment("单支注射用美罗培南复合针剂的最大刻度单位 (默认 100)")
                .defineInRange("meropenemMaxDoseUnits", 100, 10, 1000);
        MEROPENEM_MAX_MG = builder
                .comment("单支注射用美罗培南复合针剂对应的标准毫克数 (默认 1000.0 mg)")
                .defineInRange("meropenemMaxMg", 1000.0, 100.0, 5000.0);
        MEROPENEM_TARGET_INFECTION_CLEAR = builder
                .comment("美罗培南复合针剂全支 (100% 剂量) 推注瞬间对目标部位直接清除的深部感染值 (默认 38)")
                .defineInRange("meropenemTargetInfectionClear", 38, 1, 100);
        MEROPENEM_SYSTEMIC_INFECTION_CLEAR = builder
                .comment("美罗培南复合针剂全支 (100% 剂量) 推注瞬间随血液循环对全身其余部位直接清除的感染值 (默认 22)")
                .defineInRange("meropenemSystemicInfectionClear", 22, 0, 100);
        MEROPENEM_CONCENTRATION_PER_FULL_DOSE = builder
                .comment("美罗培南复合针剂全支推注增加的体内血药浓度百分比 (默认 70.0%)")
                .defineInRange("meropenemConcentrationPerFullDose", 70.0, 10.0, 150.0);
        MEROPENEM_DECAY_RATE_PER_SEC = builder
                .comment("体内美罗培南血药浓度每秒自然代谢衰减速率 (%/秒，默认 0.55)")
                .defineInRange("meropenemDecayRatePerSec", 0.55, 0.05, 10.0);
        MEROPENEM_CONTINUOUS_KILL_PER_SEC = builder
                .comment("美罗培南有效血药浓度期间每秒持续清除全身各部位感染的速率 (点/秒，默认 1.15)")
                .defineInRange("meropenemContinuousKillPerSec", 1.15, 0.05, 10.0);
        MEROPENEM_SIRS_RECOVERY_SPEED_MULT = builder
                .comment("美罗培南+氢化可的松压制全身细胞因子风暴、加速 SIRS 降级的速率倍率 (默认 4.5 倍)")
                .defineInRange("meropenemSirsRecoverySpeedMult", 4.5, 1.0, 20.0);
        MEROPENEM_SIRS_TPR_RESTORE = builder
                .comment("复合针剂中的氢化可的松对抗脓毒症分布性休克血管麻痹、恢复外周血管阻力 (TPR) 的提升值 (默认 0.22)")
                .defineInRange("meropenemSirsTprRestore", 0.22, 0.0, 1.5);
        MEROPENEM_HERXHEIMER_INFECTION_THRESHOLD = builder
                .comment("触发「赫氏反应 (Jarisch-Herxheimer 内毒素休克风暴)」的全身总感染阈值 (默认 110)")
                .defineInRange("meropenemHerxheimerInfectionThreshold", 110, 30, 500);
        MEROPENEM_HERXHEIMER_DOSE_RATIO_THRESHOLD = builder
                .comment("在重度感染或高阶 SIRS 下单次推注比例超过该阈值时触发细菌大面积裂解内毒素释放反应 (默认 0.65)")
                .defineInRange("meropenemHerxheimerDoseRatioThreshold", 0.65, 0.20, 1.0);
        MEROPENEM_HERXHEIMER_TOXIN_SPIKE = builder
                .comment("赫氏反应触发瞬间裂解细菌向血液释放的内毒素激增数值 (默认 18.0)")
                .defineInRange("meropenemHerxheimerToxinSpike", 18.0, 0.0, 100.0);
        MEROPENEM_NEUROTOXIC_THRESHOLD = builder
                .comment("连续过量推注美罗培南触发中枢 GABA 受体拮抗惊厥/眩晕的血药浓度阈值 (% ，默认 85)")
                .defineInRange("meropenemNeurotoxicThreshold", 85, 30, 100);
        LOCK_HOTBAR_DURING_MEDICAL_ANIM = builder
                .comment("播放第一人称医疗动画（器械就绪检视、打针、缠绷带、绑止血带、服药、喷碘伏）期间是否锁定热键栏槽位，严禁切到其他物品打断动画 (默认 true)")
                .define("lockHotbarDuringMedicalAnim", true);
        SURGICAL_KIT_READY_ANIM_DURATION_MS = builder
                .comment("切换手持四大重型医疗器械包时的第一人称真实模型就绪动画持续毫秒数 (默认 1050 ms)")
                .defineInRange("surgicalKitReadyAnimDurationMs", 1050, 400, 3000);

        builder.pop();

        builder.comment("枪伤弹道分型（盲管伤体内嵌留子弹 vs 前后对穿贯穿伤）临床病理、绷带受限与取弹缝合配置")
                .push("gunshot_embedded_and_perforation");

        EMBEDDED_BULLET_INFECTION_ACCUM_PER_SEC = builder
                .comment("体内每颗未取出的嵌留子弹/弹片每秒额外增加的深部厌氧感染累积速率 (默认 0.16)")
                .defineInRange("embeddedBulletInfectionAccumPerSec", 0.16, 0.0, 5.0);
        EMBEDDED_BULLET_MIN_PAIN_FLOOR = builder
                .comment("部位存在未取出嵌留子弹时的异物保底刺痛等级（未取弹前疼痛无法自然消退至该值以下，默认 2）")
                .defineInRange("embeddedBulletMinPainFloor", 2, 0, 10);
        EMBEDDED_BULLET_MOVE_REBLEED_CHANCE = builder
                .comment("部位存在未取出嵌留子弹时，疾跑/跳跃/发力每秒因弹头摩擦切割周围血管导致复发渗血的概率 (默认 0.15)")
                .defineInRange("embeddedBulletMoveRebleedChance", 0.15, 0.0, 1.0);
        EMBEDDED_BULLET_MOVE_REBLEED_AMOUNT = builder
                .comment("嵌留子弹摩擦切割血管引发单次复发渗血的外出血增量 (mL/min，默认 35)")
                .defineInRange("embeddedBulletMoveRebleedAmount", 35, 1, 500);
        EMBEDDED_BULLET_BANDAGE_PRESS_PAIN_SPIKE = builder
                .comment("未取出嵌留子弹就直接缠绕绷带压迫伤口时，将弹头压向深部神经引发的瞬时剧痛跳变增量 (默认 2)")
                .defineInRange("embeddedBulletBandagePressPainSpike", 2, 0, 10);
        EMBEDDED_BULLET_FRACTURE_HEAL_PENALTY_MULT = builder
                .comment("部位存在未取出嵌留子弹卡阻骨断端时，骨折愈合推进速率保留比例 (默认 0.40 = 减慢 60%)")
                .defineInRange("embeddedBulletFractureHealPenaltyMult", 0.40, 0.05, 1.0);
        PERFORATION_REGULAR_BANDAGE_EFFICIENCY = builder
                .comment("贯穿伤（前后双创口+空腔通道）下普通卷式绷带的单次止血吸收效率系数 (默认 0.35 = 35% 效率)")
                .defineInRange("perforationRegularBandageEfficiency", 0.35, 0.05, 1.0);
        PERFORATION_PRESSURE_BANDAGE_EFFICIENCY = builder
                .comment("贯穿伤下加压绷带的单次止血吸收效率系数 (默认 0.60 = 60% 效率)")
                .defineInRange("perforationPressureBandageEfficiency", 0.60, 0.10, 1.0);
        PERFORATION_BANDAGE_RESIDUAL_BLEED_RATIO = builder
                .comment("贯穿伤未完成深部塞纱填塞或外科缝合前，单次缠绕绷带后至少残留的空腔渗血比例 (默认 0.28)")
                .defineInRange("perforationBandageResidualBleedRatio", 0.28, 0.05, 0.90);
        PERFORATION_MIN_RESIDUAL_BLEED = builder
                .comment("贯穿伤未塞纱填塞/未缝合前，单次缠绕绷带后保留的最低空腔残余渗血速率 (mL/min，默认 25)")
                .defineInRange("perforationMinResidualBleed", 25, 5, 300);
        PERFORATION_PACKING_SPRINT_TEAR_CHANCE = builder
                .comment("对贯穿伤仅做加压绷带二次塞纱填塞（未做外科缝合）期间，疾跑/跳跃每秒导致塞纱崩裂复发出血的概率 (默认 0.12)")
                .defineInRange("perforationPackingSprintTearChance", 0.12, 0.0, 1.0);
        SUTURE_BULLET_EXTRACT_DURABILITY_COST = builder
                .comment("使用野战血管缝合手术包完成探查取弹与贯穿创道清创缝合消耗的耐久度 (默认 15)")
                .defineInRange("sutureBulletExtractDurabilityCost", 15, 1, 100);
        TACZ_GUNSHOT_FRACTURE_MIN_DAMAGE = builder
                .comment("TaCZ 枪击触发穿透性骨折所需的最低穿透伤害阈值（调高以避免普通交火频繁骨折，默认 9.5）")
                .defineInRange("taczGunshotFractureMinDamage", 9.5, 1.0, 50.0);
        TACZ_GUNSHOT_FRACTURE_CHANCE_MULTIPLIER = builder
                .comment("TaCZ 枪击骨折全局概率缩放倍率（默认 0.26，大幅降低枪击导致的骨折频率）")
                .defineInRange("taczGunshotFractureChanceMultiplier", 0.26, 0.0, 2.0);
        TACZ_PNEUMOTHORAX_MIN_PENETRATING_DAMAGE = builder
                .comment("TaCZ 胸腔枪击触发开放性气胸所需的最低护甲后穿透伤害阈值（默认 9.5）")
                .defineInRange("taczPneumothoraxMinPenetratingDamage", 9.5, 1.0, 50.0);
        TACZ_PNEUMOTHORAX_BASE_CHANCE = builder
                .comment("TaCZ 胸腔枪击达到穿透阈值时的气胸基础触发概率（默认 0.15，避免频繁触发气胸）")
                .defineInRange("taczPneumothoraxBaseChance", 0.15, 0.0, 1.0);
        TACZ_SHOTGUN_EMBEDDED_PELLETS_MIN = builder
                .comment("霰弹枪单次命中未完全贯穿部位时最少嵌留的鹿弹铅丸数量（默认 3）")
                .defineInRange("taczShotgunEmbeddedPelletsMin", 3, 1, 12);
        TACZ_SHOTGUN_EMBEDDED_PELLETS_MAX = builder
                .comment("霰弹枪单次命中未完全贯穿部位时最多嵌留的鹿弹铅丸数量（默认 6）")
                .defineInRange("taczShotgunEmbeddedPelletsMax", 6, 1, 12);
        TACZ_MAX_EMBEDDED_BULLETS_PER_LIMB = builder
                .comment("单个部位最多可嵌留的子弹/霰弹铅丸总数上限（默认 12）")
                .defineInRange("taczMaxEmbeddedBulletsPerLimb", 12, 1, 16);
        TACZ_INJURY_SWAY_AMPLITUDE_MULTIPLIER = builder
                .comment("疼痛与手臂受伤时的准星/枪口晃动幅度全局倍率（默认 4.2，提供显著的大幅晃动反馈）")
                .defineInRange("taczInjurySwayAmplitudeMultiplier", 4.2, 0.5, 15.0);
        TACZ_INJURY_ADS_SLOWDOWN_STRENGTH = builder
                .comment("疼痛与手臂受伤时的开镜举枪减速强度倍率（默认 2.4，让受伤举枪过程明显缓慢沉重）")
                .defineInRange("taczInjuryAdsSlowdownStrength", 2.4, 0.5, 10.0);
        RAGDOLL_COMPAT_ENABLED = builder
                .comment("是否启用 Sable: Ragdolls 与受创物理扑倒/物品脱手兼容系统 (默认 true)")
                .define("ragdollCompatEnabled", true);
        RAGDOLL_LEG_HEAVY_DAMAGE_THRESHOLD = builder
                .comment("腿部承受单次较重伤害触发暂时摔倒（布娃娃扑倒）的伤害阈值 (默认 6.5)")
                .defineInRange("ragdollLegHeavyDamageThreshold", 6.5, 1.0, 50.0);
        RAGDOLL_LEG_KNOCKDOWN_DURATION_TICKS = builder
                .comment("腿部受重伤摔倒后在地面扑倒/翻滚的持续时间 (Tick，20 Tick = 1秒，默认 65 即 3.25 秒后自动站起)")
                .defineInRange("ragdollLegKnockdownDurationTicks", 65, 10, 400);
        RAGDOLL_ARM_DISARM_DAMAGE_THRESHOLD = builder
                .comment("手部（左臂/右臂）承受较重伤害导致手中物品脱手飞出的伤害阈值 (默认 6.0)")
                .defineInRange("ragdollArmDisarmDamageThreshold", 6.0, 1.0, 50.0);
        RAGDOLL_ARM_HEAVY_HIT_DISARM_CHANCE = builder
                .comment("手部承受超过阈值的重伤时，手中物品脱手抛出的概率 (默认 0.78；若触发骨折/脱臼/截肢则 100% 脱手)")
                .defineInRange("ragdollArmHeavyHitDisarmChance", 0.88, 0.0, 1.0);
        RAGDOLL_ARM_DISARM_THROW_SPEED = builder
                .comment("手部重伤导致手中物品脱手飞出时的物理抛射初速度 (默认 0.42)")
                .defineInRange("ragdollArmDisarmThrowSpeed", 0.42, 0.05, 2.0);
        RAGDOLL_ARM_DISARM_PICKUP_DELAY_TICKS = builder
                .comment("手中物品因手部重伤脱手飞出后的禁止拾取延迟 (Tick，默认 40 即 2 秒，防止原地瞬间吸回)")
                .defineInRange("ragdollArmDisarmPickupDelayTicks", 40, 10, 200);
        RAGDOLL_HEAVY_CALIBER_KNOCKDOWN_DAMAGE = builder
                .comment("大口径枪械或重击直接触发全身真实物理布娃娃击飞瘫软的单发伤害阈值 (默认 13.5)")
                .defineInRange("ragdollHeavyCaliberKnockdownDamage", 13.5, 4.0, 100.0);
        RAGDOLL_HEAVY_CALIBER_DURATION_TICKS = builder
                .comment("被大口径武器命中触发全身布娃娃瘫软的持续时间 (Tick，默认 85 即 4.25 秒)")
                .defineInRange("ragdollHeavyCaliberDurationTicks", 85, 20, 400);
        RAGDOLL_EXPLOSION_MIN_DAMAGE = builder
                .comment("爆炸冲击波导致玩家暂时摔倒/布娃娃翻滚的最低爆炸伤害阈值 (默认 3.5)")
                .defineInRange("ragdollExplosionMinDamage", 3.5, 0.5, 50.0);
        RAGDOLL_EXPLOSION_DURATION_TICKS = builder
                .comment("爆炸导致暂时摔倒的基准持续时间 (Tick，默认 90 即 4.5 秒)")
                .defineInRange("ragdollExplosionDurationTicks", 90, 20, 400);
        TRAUMA_BLACK_CORNER_VIGNETTE_ENABLED = builder
                .comment("是否启用重伤/失血/缺氧时的纯正深黑边角压迫暗角 (Black Corner Vignette，默认 true)")
                .define("traumaBlackCornerVignetteEnabled", true);
        TRAUMA_BLACK_CORNER_MAX_ALPHA = builder
                .comment("深黑边角压迫层在屏幕四角与边缘的最大不透明度 (默认 0.96)")
                .defineInRange("traumaBlackCornerMaxAlpha", 0.96, 0.10, 1.0);
        TRAUMA_BLACK_CORNER_MIN_INNER_RADIUS = builder
                .comment("极重伤/濒死时深黑边角向屏幕中心收拢的最小通透内径 (默认 0.25，确保中心视野可见)")
                .defineInRange("traumaBlackCornerMinInnerRadius", 0.25, 0.15, 0.75);
        TRAUMA_EYELID_DROOP_MAX_STRENGTH = builder
                .comment("低意识与濒死状态下上下眼皮沉重闭合黑幕的最大强度 (默认 0.85)")
                .defineInRange("traumaEyelidDroopMaxStrength", 0.85, 0.0, 1.0);
        TRAUMA_HYPOXIA_VISUAL_SNOW_INTENSITY = builder
                .comment("气胸/血胸/低血氧窒息时的紫绀视网膜静电雪花噪点强度 (默认 0.85)")
                .defineInRange("traumaHypoxiaVisualSnowIntensity", 0.85, 0.0, 2.0);
        TRAUMA_COLD_SWEAT_FROST_INTENSITY = builder
                .comment("大失血休克时的屏幕边缘冷汗水雾霜化折射强度 (默认 0.80)")
                .defineInRange("traumaColdSweatFrostIntensity", 0.80, 0.0, 2.0);
        TRAUMA_SOUNDSCAPE_ENGINE_ENABLED = builder
                .comment("是否启用程序化生理杂音与窒息/紧张合成音效引擎 (默认 true)")
                .define("traumaSoundscapeEngineEnabled", true);
        TRAUMA_SOUND_MASTER_VOLUME = builder
                .comment("受伤生理杂音与窒息声学引擎主音量倍率 (默认 0.75)")
                .defineInRange("traumaSoundMasterVolume", 0.75, 0.0, 2.0);
        TRAUMA_SOUND_TINNITUS_VOLUME = builder
                .comment("头部受创/爆震/剧痛时的 3800Hz 尖锐耳鸣与颅内低频嗡鸣音量 (默认 0.70)")
                .defineInRange("traumaSoundTinnitusVolume", 0.70, 0.0, 2.0);
        TRAUMA_SOUND_ASPHYXIA_WHEEZE_VOLUME = builder
                .comment("气胸/血胸/低血氧时的破风箱窒息哮鸣、胸壁漏气嘶鸣与喘息杂音音量 (默认 0.85)")
                .defineInRange("traumaSoundAsphyxiaWheezeVolume", 0.85, 0.0, 2.0);
        TRAUMA_SOUND_BLOOD_WHOOSH_VOLUME = builder
                .comment("失血与心动过速时的双音内耳心搏及颈动脉血液冲刷杂音音量 (默认 0.80)")
                .defineInRange("traumaSoundBloodWhooshVolume", 0.80, 0.0, 2.0);
        TRAUMA_SOUND_CRITICAL_DRONE_VOLUME = builder
                .comment("濒死倒计时/室颤/大出血时的暗黑低频紧张压迫氛围音音量 (默认 0.85)")
                .defineInRange("traumaSoundCriticalDroneVolume", 0.85, 0.0, 2.0);

        builder.pop();

        SPEC = builder.build();
    }
}