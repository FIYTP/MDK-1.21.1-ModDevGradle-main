package com.FIYTP.realmedicalmod.loop;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.config.MedicalConfig;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public class BleedParticleModule {

    public static void spawn(ServerPlayer player, MedicalData data) {
        int totalBleeding = 0;
        for (MedicalData.Limb limb : MedicalData.Limb.values()) {
            MedicalData.LimbData ld = data.getLimb(limb);
            if (!ld.isBandaged() && !ld.isTourniqueted()) {
                totalBleeding += ld.getBleeding();
            }
        }

        int threshold = MedicalConfig.BLEED_PARTICLE_THRESHOLD.get();
        if (totalBleeding < threshold) {
            return;
        }

        int particleCount = Math.min(totalBleeding / 100, 15);
        if (particleCount <= 0) return;

        ServerLevel level = player.serverLevel();
        if (level == null) return;

        // 使用原版伤害指示粒子 (红色小颗粒)
        level.sendParticles(
                ParticleTypes.DAMAGE_INDICATOR,
                player.getX(), player.getY() + 0.5, player.getZ(),
                particleCount,
                0.5, 0.5, 0.5,
                0.05
        );
    }
}