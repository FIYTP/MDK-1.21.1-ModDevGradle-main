package com.FIYTP.realmedicalmod.client.gui;

import com.FIYTP.realmedicalmod.client.BrainDamageClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

@EventBusSubscriber(modid = "realmedicalmod", value = Dist.CLIENT)
public class BrainDamageShockOverlay {

    private static final long TOTAL_DURATION_MS = 2500L; // 2.5 秒
    private static final long FREEZE_DURATION_MS = 300L;  // 0.3 秒定格

    @SubscribeEvent
    public static void onRenderGuiPost(RenderGuiEvent.Post event) {
        long startTime = BrainDamageClientState.getShockStartTime();
        if (startTime <= 0) return;

        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed >= TOTAL_DURATION_MS) {
            BrainDamageClientState.setShockProgress(0.0f);
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        GuiGraphics graphics = event.getGuiGraphics();

        int baseColor = BrainDamageClientState.getShockColor() & 0x00FFFFFF;

        if (elapsed < FREEZE_DURATION_MS) {
            // 定格阶段（0 ~ 0.3 秒）：强烈闪烁高亮滤镜
            float t = (float) elapsed / FREEZE_DURATION_MS;
            int alpha = (int) (180 + (1.0f - t) * 75); // 180~255
            graphics.fill(0, 0, w, h, (alpha << 24) | baseColor);
        } else {
            // 残影淡出阶段（0.3 ~ 2.5 秒）：双层暗角 + 全屏色层逐渐淡出
            float progress = (float) (elapsed - FREEZE_DURATION_MS) / (TOTAL_DURATION_MS - FREEZE_DURATION_MS);
            float alphaMul = 1.0f - progress;
            BrainDamageClientState.setShockProgress(alphaMul);

            // 全屏薄滤镜
            int screenAlpha = (int) (alphaMul * 100);
            if (screenAlpha > 0) {
                graphics.fill(0, 0, w, h, (screenAlpha << 24) | baseColor);
            }

            // 四周晕影暗角与边缘冲击条
            int vignetteAlpha = (int) (alphaMul * 180);
            int vignetteColor = (vignetteAlpha << 24) | baseColor;
            int edgeSize = (int) (Math.min(w, h) * 0.35f * alphaMul);
            if (edgeSize > 0) {
                graphics.fillGradient(0, 0, w, edgeSize, vignetteColor, 0x00000000);
                graphics.fillGradient(0, h - edgeSize, w, h, 0x00000000, vignetteColor);
                graphics.fillGradient(0, 0, edgeSize, h, vignetteColor, 0x00000000);
                graphics.fillGradient(w - edgeSize, 0, w, h, 0x00000000, vignetteColor);
            }
        }
    }
}
