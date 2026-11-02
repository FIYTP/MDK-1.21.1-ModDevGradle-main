package com.FIYTP.realmedicalmod.client;

import com.FIYTP.realmedicalmod.network.BrainDamageEffectPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

public class BrainDamageClientState {
    private static float shockProgress = 0.0f;
    private static long shockStartTime = 0;
    private static int shockColor = 0xFFFFDD44;

    private static boolean inputInverted = false;
    private static boolean viewInverted = false;
    private static long inputInvertEndTime = 0;
    private static long viewInvertEndTime = 0;

    public static void handleEffectPacket(BrainDamageEffectPacket packet) {
        long now = System.currentTimeMillis();
        switch (packet.effectType()) {
            case SHOCK -> {
                shockStartTime = now;
                shockProgress = 1.0f;
                shockColor = (Math.random() < 0.5) ? 0xFFFFDD44 : 0xFFFF4444;

                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.getSoundManager().play(
                            SimpleSoundInstance.forUI(SoundEvents.WARDEN_SONIC_BOOM, 0.5f, 1.5f)
                    );
                }
            }
            case INPUT_INVERT -> {
                inputInverted = true;
                inputInvertEndTime = now + (long) packet.duration() * 1000L;
            }
            case VIEW_INVERT -> {
                viewInverted = true;
                viewInvertEndTime = now + (long) packet.duration() * 1000L;
            }
        }
    }

    public static boolean isInputInverted() {
        if (!inputInverted) return false;
        if (System.currentTimeMillis() >= inputInvertEndTime) {
            inputInverted = false;
            return false;
        }
        return true;
    }

    public static boolean isViewInverted() {
        if (!viewInverted) return false;
        if (System.currentTimeMillis() >= viewInvertEndTime) {
            viewInverted = false;
            return false;
        }
        return true;
    }

    public static float getShockProgress() {
        return shockProgress;
    }

    public static void setShockProgress(float progress) {
        shockProgress = progress;
    }

    public static long getShockStartTime() {
        return shockStartTime;
    }

    public static int getShockColor() {
        return shockColor;
    }
}
