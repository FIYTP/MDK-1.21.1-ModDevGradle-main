package com.FIYTP.realmedicalmod.loop;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.config.MedicalConfig;
import com.FIYTP.realmedicalmod.network.BrainDamageEffectPacket;
import com.FIYTP.realmedicalmod.network.NetworkHandler;
import net.minecraft.server.level.ServerPlayer;

import java.util.Random;

public class BrainDamageTriggerModule implements UpdateModule {

    private static final Random RANDOM = new Random();

    @Override
    public void process(MedicalData data) {
        // 无 player 时的兜底
    }

    public void process(ServerPlayer player, MedicalData data) {
        if (player == null || data == null) return;

        // 1. 处理冷却递减（每秒递减 1）
        int cd = data.getBrainDamageTriggerCooldown();
        if (cd > 0) {
            data.setBrainDamageTriggerCooldown(cd - 1);
        }

        int brainHealth = data.getBrainHealth();
        int threshold = MedicalConfig.BRAIN_DAMAGE_TRIGGER_THRESHOLD.get();

        // 2. 检查是否需要强制触发（首次跌破 90 / 60 / 30）
        boolean shouldForceTrigger = false;
        if (brainHealth < 90 && !data.hasForcedTrigger90()) {
            data.setForcedTrigger90(true);
            shouldForceTrigger = true;
        }
        if (brainHealth < 60 && !data.hasForcedTrigger60()) {
            data.setForcedTrigger60(true);
            shouldForceTrigger = true;
        }
        if (brainHealth < 30 && !data.hasForcedTrigger30()) {
            data.setForcedTrigger30(true);
            shouldForceTrigger = true;
        }

        if (shouldForceTrigger) {
            triggerEffect(player, data);
            return;
        }

        // 3. 常规触发检查
        if (brainHealth >= threshold) {
            data.setBrainDamageTriggerTimer(0);
            return;
        }

        // 脑健康 < 阈值，累加计时器
        int timer = data.getBrainDamageTriggerTimer() + 1;
        int interval = MedicalConfig.BRAIN_DAMAGE_TRIGGER_INTERVAL.get();

        if (timer >= interval) {
            data.setBrainDamageTriggerTimer(0);

            // 冷却中不触发
            if (data.getBrainDamageTriggerCooldown() > 0) {
                return;
            }

            // 按 triggerChance 判定概率
            double chance = MedicalConfig.BRAIN_DAMAGE_TRIGGER_CHANCE.get();
            if (RANDOM.nextDouble() < chance) {
                triggerEffect(player, data);
            }
        } else {
            data.setBrainDamageTriggerTimer(timer);
        }
    }

    private void triggerEffect(ServerPlayer player, MedicalData data) {
        // 设定触发后冷却
        int cooldown = MedicalConfig.BRAIN_DAMAGE_TRIGGER_COOLDOWN.get();
        data.setBrainDamageTriggerCooldown(cooldown);

        // 随机选择效果（按 50% 冲击 / 25% 按键颠倒 / 25% 视角颠倒）
        double shockWeight = MedicalConfig.BRAIN_DAMAGE_SHOCK_CHANCE.get();
        double inputWeight = MedicalConfig.BRAIN_DAMAGE_INPUT_INVERT_CHANCE.get();
        double viewWeight = MedicalConfig.BRAIN_DAMAGE_VIEW_INVERT_CHANCE.get();
        double totalWeight = shockWeight + inputWeight + viewWeight;
        if (totalWeight <= 0) totalWeight = 1.0;

        double roll = RANDOM.nextDouble() * totalWeight;
        BrainDamageEffectPacket.EffectType type;
        if (roll < shockWeight) {
            type = BrainDamageEffectPacket.EffectType.SHOCK;
        } else if (roll < shockWeight + inputWeight) {
            type = BrainDamageEffectPacket.EffectType.INPUT_INVERT;
        } else {
            type = BrainDamageEffectPacket.EffectType.VIEW_INVERT;
        }

        // 计算颠倒持续时间（秒）
        int minDur = MedicalConfig.BRAIN_DAMAGE_INVERT_DURATION_MIN.get();
        int maxDur = MedicalConfig.BRAIN_DAMAGE_INVERT_DURATION_MAX.get();
        if (maxDur < minDur) maxDur = minDur;
        int duration = minDur + (maxDur > minDur ? RANDOM.nextInt(maxDur - minDur + 1) : 0);

        NetworkHandler.sendToPlayer(player, new BrainDamageEffectPacket(type, duration));
    }
}
