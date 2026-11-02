package com.FIYTP.realmedicalmod.loop;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.config.MedicalConfig;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * 血胸模块 (HemothoraxModule)
 * 职责：
 * 1. 胸部内出血按 ACCUMULATION_RATIO 效率转化为胸腔积血 (0~MAX_CHEST_ACCUMULATION mL)，剩余比例转化为肺挫伤。
 * 2. 评定血胸等级 (0~4)。
 * 3. 达到 MIN_VOMIT_LEVEL 时，每 AUTO_VOMIT_INTERVAL 秒触发咯血缓解：排出分级积血，即时刷新等级，产生聊天/状态/粒子/音效反馈。
 */
public class HemothoraxModule {

    private static final DustParticleOptions BLOOD_PARTICLE = new DustParticleOptions(
            new Vector3f(0.8f, 0.0f, 0.0f), 1.2f
    );

    public void process(ServerPlayer player, MedicalData data) {
        if (player == null || player.isRemoved() || data == null) return;

        MedicalData.LimbData chest = data.getLimb(MedicalData.Limb.CHEST);
        int chestBleedRate = (chest != null) ? chest.getInternalBleedingRate() : 0;

        // 1. 积血与肺挫伤累加
        float currentAcc = data.getChestBloodAccumulation();
        if (chestBleedRate > 0) {
            // 用和 BleedModule 一致的公式计算有效速率
            float map = data.getMeanArterialPressure();
            float mapMultiplier = BleedModule.getMapMultiplier(map);
            float hemoMultiplier = MedicalConfig.HEMOTHORAX_MULTIPLIER.get().floatValue();

            float effectiveRate = chestBleedRate * mapMultiplier * hemoMultiplier;

            // 70% 进入血胸
            float ratio = MedicalConfig.ACCUMULATION_RATIO.get().floatValue();
            float added = (effectiveRate * ratio) / 60.0f;
            float maxAcc = MedicalConfig.MAX_CHEST_ACCUMULATION.get().floatValue();
            currentAcc = Math.min(maxAcc, currentAcc + added);
            data.setChestBloodAccumulation(currentAcc);

            // 30% 进入肺挫伤
            float lungRatio = 1.0f - ratio;
            float lungAdded = (effectiveRate * lungRatio) / 60.0f;
            float newLung = data.getLungContusion() + lungAdded;
            data.setLungContusion(Math.min(100.0f, newLung));
        }

        // 2. 评定血胸等级
        int level;
        if (currentAcc >= MedicalConfig.LEVEL4_THRESHOLD.get().floatValue()) {
            level = 4;
        } else if (currentAcc >= MedicalConfig.LEVEL3_THRESHOLD.get().floatValue()) {
            level = 3;
        } else if (currentAcc >= MedicalConfig.LEVEL2_THRESHOLD.get().floatValue()) {
            level = 2;
        } else if (currentAcc >= MedicalConfig.LEVEL1_THRESHOLD.get().floatValue()) {
            level = 1;
        } else {
            level = 0;
        }
        data.setHemothoraxLevel(level);

        // 3. 咯血缓解逻辑
        int minVomitLevel = MedicalConfig.MIN_VOMIT_LEVEL.get();
        if (level >= minVomitLevel) {
            int vomitTimer = data.getHemothoraxVomitTimer() + 1;
            int vomitInterval = MedicalConfig.AUTO_VOMIT_INTERVAL.get();
            if (vomitTimer >= vomitInterval) {
                float vomitAmount = switch (level) {
                    case 1 -> MedicalConfig.LEVEL1_VOMIT_AMOUNT.get();
                    case 2 -> MedicalConfig.LEVEL2_VOMIT_AMOUNT.get();
                    case 3 -> MedicalConfig.LEVEL3_VOMIT_AMOUNT.get();
                    case 4 -> MedicalConfig.LEVEL4_VOMIT_AMOUNT.get();
                    default -> 100;
                };

                float newAcc = Math.max(0.0f, currentAcc - vomitAmount);
                data.setChestBloodAccumulation(newAcc);
                vomitTimer = 0;

                // 立即重新评估等级
                int newLevel;
                if (newAcc >= MedicalConfig.LEVEL4_THRESHOLD.get()) newLevel = 4;
                else if (newAcc >= MedicalConfig.LEVEL3_THRESHOLD.get()) newLevel = 3;
                else if (newAcc >= MedicalConfig.LEVEL2_THRESHOLD.get()) newLevel = 2;
                else if (newAcc >= MedicalConfig.LEVEL1_THRESHOLD.get()) newLevel = 1;
                else newLevel = 0;
                data.setHemothoraxLevel(newLevel);

                // 玩家可见反馈
                player.sendSystemMessage(Component.literal("§c⚠ 你剧烈咳嗽，咳出一口血！胸腔积血 -" + (int)vomitAmount + " mL"));
                player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 20, 0, false, false));
                player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 10, 0, false, false));

                // 粒子 + 音效
                ServerLevel levelServer = player.serverLevel();
                if (levelServer != null) {
                    Vec3 eyePos = player.getEyePosition();
                    levelServer.playSound(null, eyePos.x, eyePos.y, eyePos.z,
                            SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.0f, 0.8f);
                    levelServer.sendParticles(BLOOD_PARTICLE,
                            eyePos.x, eyePos.y - 0.2, eyePos.z,
                            60, 0.3, 0.1, 0.3, 0.15);
                }
            }
            data.setHemothoraxVomitTimer(vomitTimer);
        } else {
            int timer = data.getHemothoraxVomitTimer();
            if (timer > 0) {
                data.setHemothoraxVomitTimer(Math.max(0, timer - 1));
            }
        }
    }
}
