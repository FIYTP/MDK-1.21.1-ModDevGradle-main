package com.FIYTP.realmedicalmod.loop;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import net.minecraft.server.level.ServerPlayer;

public class PainSuggestionModule {
    public static void suggest(ServerPlayer player, MedicalData data, BuffManager buffManager) {
        for (MedicalData.Limb limb : MedicalData.Limb.values()) {
            MedicalData.LimbData ld = data.getLimb(limb);
            int pain = ld.getPainLevel();
            if (pain == 0) continue;

            if (limb == MedicalData.Limb.LEFT_LEG || limb == MedicalData.Limb.RIGHT_LEG) {
                if (pain >= 7) buffManager.suggest(player, "slowness", 3);
                else if (pain >= 4) buffManager.suggest(player, "slowness", 2);
                else buffManager.suggest(player, "slowness", 1);
            }

            if (limb == MedicalData.Limb.LEFT_ARM || limb == MedicalData.Limb.RIGHT_ARM) {
                if (pain >= 7) {
                    buffManager.suggest(player, "weakness", 3);
                    buffManager.suggest(player, "dig_slowdown", 2);
                } else if (pain >= 4) {
                    buffManager.suggest(player, "weakness", 2);
                    buffManager.suggest(player, "dig_slowdown", 1);
                } else {
                    buffManager.suggest(player, "weakness", 1);
                }
            }

            if (limb == MedicalData.Limb.HEAD) {
                if (pain >= 7) {
                    buffManager.suggest(player, "blindness", 2);
                    buffManager.suggest(player, "nausea", 3);
                } else if (pain >= 4) {
                    buffManager.suggest(player, "blindness", 1);
                    buffManager.suggest(player, "nausea", 2);
                } else {
                    buffManager.suggest(player, "nausea", 1);
                }
            }

            if (limb == MedicalData.Limb.CHEST) {
                if (pain >= 7) buffManager.suggest(player, "nausea", 2);
                else if (pain >= 4) buffManager.suggest(player, "nausea", 1);
            }
        }
    }
}