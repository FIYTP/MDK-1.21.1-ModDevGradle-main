package com.FIYTP.realmedicalmod.loop;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import net.minecraft.server.level.ServerPlayer;

public class ConsciousnessEffectModule implements UpdateModule {
    @Override
    public void process(MedicalData data) {
        // 无 player 时的兜底
    }

    public void process(ServerPlayer player, MedicalData data) {
        if (player == null || data == null) return;

        int consciousness = data.getConsciousness();
        if (consciousness >= 80) return; // 正常，不注册

        float intensity = (80 - consciousness) / 80.0f;
        float speedMul = 1.0f - intensity * 0.7f; // 1.0 → 0.3

        com.FIYTP.realmedicalmod.loop.AttributeArbiter.suggest(player,
                com.FIYTP.realmedicalmod.loop.AttributeArbiter.Dimension.MOVEMENT_SPEED,
                "consciousness", speedMul);
    }
}
