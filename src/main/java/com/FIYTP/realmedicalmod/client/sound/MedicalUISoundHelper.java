package com.FIYTP.realmedicalmod.client.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

/**
 * 医疗面板 UI 科幻音效管理器
 * 遵循 Mod 规则：严禁外部音频资产，纯复用原版 SoundEvents 并通过精细调校 Pitch / Volume 实现 3A 级科幻 HUD 听觉质感
 */
public final class MedicalUISoundHelper {

    private MedicalUISoundHelper() {}

    /**
     * 打开面板启动音：电子仪器开机自检 / 启动蜂鸣 (复用铁砧使用/经验球/信标激活高音组合)
     */
    public static void playOpenSound() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSoundManager() == null) return;
        // 快速双音节电子初始化
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.85f, 0.35f));
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.6f, 0.25f));
    }

    /**
     * 关闭面板注销音：气压卸荷 / 息屏轻响
     */
    public static void playCloseSound() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSoundManager() == null) return;
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 0.75f, 0.3f));
    }

    /**
     * 肢体切换 / 按钮悬停轻触音：微弱金属触点嘀嗒
     */
    public static void playHoverTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSoundManager() == null) return;
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 2.0f, 0.08f));
    }

    /**
     * 部位选中 / 点击确认音：清脆机械电磁吸合音
     */
    public static void playSelectSound() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSoundManager() == null) return;
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.CHEST_OPEN, 2.0f, 0.2f));
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.4f, 0.35f));
    }

    /**
     * 危险 / 警报脉冲提醒音：电子短鸣
     */
    public static void playWarningBeep() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSoundManager() == null) return;
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BIT.value(), 1.8f, 0.25f));
    }
}
