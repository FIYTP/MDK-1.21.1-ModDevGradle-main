package com.FIYTP.realmedicalmod.client.gui;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.capability.MedicalDataCapability;
import com.FIYTP.realmedicalmod.config.MedicalConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

@EventBusSubscriber(modid = "realmedicalmod", value = Dist.CLIENT)
public class CriticalOverlay {

    // 室颤闪烁动画计时器（每渲染帧 +1）
    private static int flashTick = 0;

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        MedicalData data = player.getData(MedicalDataCapability.MEDICAL_DATA);
        if (data == null) return;

        flashTick++;

        GuiGraphics graphics = event.getGuiGraphics();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        // ===== 1. 心脏骤停 → 全屏幕泛红 =====
        if (data.isCardiacArrest()) {
            // 半透明红色覆盖全屏（约50%透明度）
            int redColor = 0x88FF0000;  // 透明度0x88（约53%）
            graphics.fill(0, 0, screenWidth, screenHeight, redColor);

            // 可选：在屏幕中央显示“心脏骤停”文字
            String text = Component.translatable("medical.warning.cardiac_arrest").getString();
            int textWidth = mc.font.width(text);
            int textX = (screenWidth - textWidth) / 2;
            int textY = screenHeight / 2 - 10;
            // 黑色背景
            graphics.fill(textX - 20, textY - 10, textX + textWidth + 20, textY + mc.font.lineHeight + 10, 0xCC000000);
            graphics.drawString(mc.font, text, textX, textY, 0xFFFF0000, false);
            return; // 心脏骤停时不再显示倒计时
        }

        // ===== 2. 室颤 → 闪烁红光 + 室颤警报 =====
        if (data.isInVFib()) {
            // 闪烁红光：透明度在 0~70% 之间正弦波动
            float alpha = 0.35f + 0.35f * (float) Math.sin(flashTick * 0.5f);
            int redColor = (((int) (alpha * 255f)) & 0xFF) << 24 | 0xFF0000;
            graphics.fill(0, 0, screenWidth, screenHeight, redColor);

            String vfibText = Component.translatable("medical.warning.vfib").getString();
            int vfibWidth = mc.font.width(vfibText);
            int vfibX = (screenWidth - vfibWidth) / 2;
            int vfibY = screenHeight / 2 - 10;
            graphics.fill(vfibX - 20, vfibY - 10, vfibX + vfibWidth + 20, vfibY + mc.font.lineHeight + 10, 0xCC000000);
            graphics.drawString(mc.font, vfibText, vfibX, vfibY, 0xFFFF3333, false);
            return; // 室颤时不显示濒危倒计时
        }

        // ===== 3. 呼吸异常警告（优先级低于心脏骤停与室颤） =====
        int respRate = data.getRespirationRate();
        int lowThreshold = MedicalConfig.EXTREME_RESP_LOW_THRESHOLD.get();
        int highThreshold = MedicalConfig.EXTREME_RESP_HIGH_THRESHOLD.get();
        if (respRate > 0 && (respRate < lowThreshold || respRate > highThreshold)) {
            // 呼吸严重异常 → 屏幕四周边缘泛红（透明度较低 0x44）
            int warningAlpha = 0x44FF0000;
            int borderThickness = MedicalConfig.RESP_BORDER_THICKNESS.get();
            // 顶边
            graphics.fill(0, 0, screenWidth, borderThickness, warningAlpha);
            // 底边
            graphics.fill(0, screenHeight - borderThickness, screenWidth, screenHeight, warningAlpha);
            // 左边
            graphics.fill(0, borderThickness, borderThickness, screenHeight - borderThickness, warningAlpha);
            // 右边
            graphics.fill(screenWidth - borderThickness, borderThickness, screenWidth, screenHeight - borderThickness, warningAlpha);
        }

        // ===== 4. 血胸等级 2+ → 屏幕边缘暗红色 =====
        int hemoLevel = data.getHemothoraxLevel();
        if (hemoLevel >= 2) {
            int hemoAlpha = (hemoLevel == 2) ? 0x44 : (hemoLevel == 3 ? 0x66 : 0x88);
            int darkRed = (hemoAlpha << 24) | 0x660000;
            int hemoThickness = 24 + (hemoLevel - 2) * 8; // 24px, 32px, 40px
            graphics.fill(0, 0, screenWidth, hemoThickness, darkRed);
            graphics.fill(0, screenHeight - hemoThickness, screenWidth, screenHeight, darkRed);
            graphics.fill(0, hemoThickness, hemoThickness, screenHeight - hemoThickness, darkRed);
            graphics.fill(screenWidth - hemoThickness, hemoThickness, screenWidth, screenHeight - hemoThickness, darkRed);
        }

        // ===== 5. 颅内出血清醒期后 → 屏幕中央暗角（多层阶梯遮罩模拟暗角效果） =====
        if (data.getIntracranialOnsetTimer() >= 30) {
            int icLevel = data.getIntracranialLevel();
            if (icLevel >= 1) {
                // 等级越高，遮罩层数越多、范围越大、边缘越深
                int layers = icLevel * 3; // 3, 6, 9, 12 层渐变
                int stepAlpha = 0x11;
                for (int i = 0; i < layers; i++) {
                    int insetX = (screenWidth / 2) * i / (layers + 4);
                    int insetY = (screenHeight / 2) * i / (layers + 4);
                    int color = (stepAlpha << 24); // 黑色半透明阶梯
                    // 顶
                    graphics.fill(insetX, insetY, screenWidth - insetX, insetY + 6, color);
                    // 底
                    graphics.fill(insetX, screenHeight - insetY - 6, screenWidth - insetX, screenHeight - insetY, color);
                    // 左
                    graphics.fill(insetX, insetY, insetX + 6, screenHeight - insetY, color);
                    // 右
                    graphics.fill(screenWidth - insetX - 6, insetY, screenWidth - insetX, screenHeight - insetY, color);
                }
            }
        }

        // ===== 6. 濒危状态 → 倒计时 + 警告文字 =====
        if (!data.isInCriticalState() || data.getCriticalTimer() <= 0) {
            return;
        }

        int timer = data.getCriticalTimer();

        // 屏幕中央倒计时数字
        String timerText = String.valueOf(timer);
        int textWidth = mc.font.width(timerText);
        int textX = (screenWidth - textWidth) / 2;
        int textY = (screenHeight - mc.font.lineHeight) / 2 - 20;

        int bgWidth = textWidth + 40;
        int bgHeight = mc.font.lineHeight + 40;
        graphics.fill(textX - 20, textY - 20, textX - 20 + bgWidth, textY - 20 + bgHeight, 0xAA000000);

        graphics.drawString(mc.font, timerText, textX, textY, 0xFFFF0000, false);

        // 屏幕上方警告文字
        String warning = Component.translatable("medical.warning.critical").getString();
        int warnWidth = mc.font.width(warning);
        graphics.drawString(mc.font, warning, (screenWidth - warnWidth) / 2, 20, 0xFFFF0000, false);
    }
}