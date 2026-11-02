package com.FIYTP.realmedicalmod.client.gui;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.capability.MedicalDataCapability;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.util.Random;

@EventBusSubscriber(modid = "realmedicalmod", value = Dist.CLIENT)
public class ConsciousnessVisualOverlay {

    private static final Random RANDOM = new Random();

    @SubscribeEvent
    public static void onRenderGuiPre(RenderGuiEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        // 如果当前打开了 GUI 界面（如菜单、聊天栏、背包等），避免扭曲 UI 造成操作困难
        if (mc.screen != null) return;

        MedicalData data = mc.player.getData(MedicalDataCapability.MEDICAL_DATA);
        if (data == null) return;

        int consciousness = data.getConsciousness();
        if (consciousness >= 80 || consciousness <= 0) return;

        float intensity = (80 - consciousness) / 80.0f;
        if (intensity <= 0.4f) return; // 只在重度时扭曲

        float time = (System.currentTimeMillis() % 2000) / 2000.0f;
        float wobbleX = (float) Math.sin(time * Math.PI * 2) * intensity * 3.0f;
        float wobbleY = (float) Math.cos(time * Math.PI * 2 * 0.7) * intensity * 2.0f;

        event.getGuiGraphics().pose().translate(wobbleX, wobbleY, 0);
    }

    @SubscribeEvent
    public static void onRenderGuiPost(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        // 视觉遮罩只覆盖游戏内画面，不阻挡打开的容器界面
        if (mc.screen != null) return;

        MedicalData data = mc.player.getData(MedicalDataCapability.MEDICAL_DATA);
        if (data == null) return;

        int consciousness = data.getConsciousness();
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        GuiGraphics graphics = event.getGuiGraphics();

        // 意识归零 → 全屏黑
        if (consciousness <= 0) {
            graphics.fill(0, 0, w, h, 0xFF000000);
            return;
        }

        // 意识正常 → 无效果
        if (consciousness >= 80) return;

        float intensity = (80 - consciousness) / 80.0f; // 0~1

        // 1. 暗角
        int vignetteAlpha = (int) (intensity * 220);
        int vignetteColor = (vignetteAlpha << 24);
        int edgeSize = (int) (Math.min(w, h) * 0.35f * intensity);
        if (edgeSize > 0) {
            graphics.fillGradient(0, 0, w, edgeSize, vignetteColor, 0x00000000);
            graphics.fillGradient(0, h - edgeSize, w, h, 0x00000000, vignetteColor);
            graphics.fillGradient(0, 0, edgeSize, h, vignetteColor, 0x00000000);
            graphics.fillGradient(w - edgeSize, 0, w, h, 0x00000000, vignetteColor);
        }

        // 2. 噪点（intensity > 0.3 时）
        if (intensity > 0.3f) {
            int noiseCount = (int) ((intensity - 0.3f) * 300);
            for (int i = 0; i < noiseCount; i++) {
                int x = RANDOM.nextInt(w);
                int y = RANDOM.nextInt(h);
                int alpha = 30 + RANDOM.nextInt(60);
                int color = (alpha << 24) | 0xFFFFFF;
                graphics.fill(x, y, x + 2, y + 2, color);
            }
        }
    }
}
