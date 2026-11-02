package com.FIYTP.realmedicalmod.client.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * 医疗面板 3A 科技风 HUD 音效系统
 * 完全复用原版 SoundEvents，通过音调、音量与音效层叠实现细腻的未来主义科技感交互声
 */
public final class MedicalSoundHelper {
    private MedicalSoundHelper() {}

    private static void playSound(SoundEvent event, float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.getSoundManager() != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, volume));
        }
    }

    /**
     * 打开面板启动音效：短促的科技能量充能启动感
     */
    public static void playOpenSound() {
        // 双层叠加：高频晶体蜂鸣 + 柔和能量信标微启动
        playSound(SoundEvents.BEACON_ACTIVATE, 0.45f, 1.85f);
        playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.6f, 1.9f);
    }

    /**
     * 关闭面板音效：能量平滑淡出微降频音
     */
    public static void playCloseSound() {
        playSound(SoundEvents.BEACON_DEACTIVATE, 0.35f, 1.7f);
        playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.3f, 0.75f);
    }

    /**
     * 肢体/部位切换音效：清脆精准的科技 HUD 确认音
     */
    public static void playLimbSelectSound() {
        playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.45f, 1.6f);
        playSound(SoundEvents.AMETHYST_BLOCK_HIT, 0.35f, 2.0f);
    }

    /**
     * 鼠标悬停微触觉音效（极低音量柔和高频）
     */
    public static void playHoverSound() {
        playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.12f, 1.9f);
    }

    /**
     * 危险数值脉冲警告低频短促警报
     */
    public static void playDangerAlertSound() {
        playSound(SoundEvents.NOTE_BLOCK_IRON_XYLOPHONE.value(), 0.5f, 0.65f);
    }
}
