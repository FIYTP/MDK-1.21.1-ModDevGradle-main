package com.FIYTP.realmedicalmod.loop;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import net.minecraft.server.level.ServerPlayer;

public class DeathModule {
    public void process(ServerPlayer player, MedicalData data) {
        if (data.getBrainHealth() <= 0) {
            player.kill();
        }
    }
}