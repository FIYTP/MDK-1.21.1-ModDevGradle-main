package com.FIYTP.realmedicalmod.client.gui;

import com.FIYTP.realmedicalmod.client.PainShockClientState;
import com.FIYTP.realmedicalmod.config.MedicalConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * 剧痛瞬发视觉层（方向2）：白闪定格 + 画面抖动 + 残影淡出。
 *
 * 时间轴（默认）：
 * - [0, 0.15s) 定格：高亮白闪（瞳孔骤缩感）+ 最强抖动
 * - [0.15s, 0.35s) 冲击衰减：抖动快速减弱，白色 vignette 退去
 * - [0.35s, 1.15s) 残影：屏幕边缘红白余晖缓慢淡出（痛感回荡）
 *
 * 抖动规则：
 * - 越痛抖得越狠（强度吃 shockIntensity 与配置 PAIN_SHOCK_MAX_OFFSET）
 * - 定格阶段全幅抖，随后按距离平方衰减（突刺感强、收敛快，不晕）
 * - GUI 打开时跳过抖动（同 ConsciousnessVisualOverlay 的 mc.screen 判断），
 *   但白闪照常渲染（保留事件感知）
 */
@EventBusSubscriber(modid = "realmedicalmod", value = Dist.CLIENT)
public class PainShockOverlay {

    // 抖动衰减曲线的时间常数（ms）：前 200ms 抖动最猛
    private static final long SHAKE_DECAY_MS = 200L;

    @SubscribeEvent
    public static void onRenderGuiPre(RenderGuiEvent.Pre event) {
        // 抖动走 Pre（和意识 wobble 一样挂在 pose translate 上）
        long startTime = PainShockClientState.getShockStartTime();
        if (startTime <= 0 || PainShockClientState.isExpired()) return;

        Minecraft mc = Minecraft.getInstance();
        // GUI 打开时不抖（避免操作困难），白闪由 Post 阶层负责
        if (mc.screen != null) return;

        long elapsed = System.currentTimeMillis() - startTime;
        float maxOffset = MedicalConfig.PAIN_SHOCK_MAX_OFFSET.get().floatValue();
        if (maxOffset <= 0.0f) return;

        // 抖动包络：定格阶段全幅，之后 200ms 内按平方衰减到 0
        float shakeEnv;
        long afterFreeze = elapsed - PainShockClientState.getFreezeDurationMs();
        if (afterFreeze < 0) {
            shakeEnv = 1.0f;
        } else {
            float t = Math.min(1.0f, (float) afterFreeze / SHAKE_DECAY_MS);
            shakeEnv = (1.0f - t) * (1.0f - t);
        }
        if (shakeEnv <= 0.0f) return;

        float amplitude = maxOffset * shakeEnv * PainShockClientState.getShockIntensity();

        // 高频交替抖动（帧间交替方向，制造骨头震到的顿挫感）
        long frame = System.currentTimeMillis() / 16; // 约 60fps 帧号
        float dirX = (frame % 2 == 0) ? 1.0f : -0.8f;
        float dirY = (frame % 3 == 0) ? -0.7f : (frame % 3 == 1 ? 0.5f : 1.0f);
        // 轻微随机成分避免机械感
        float jitterX = (float) (Math.sin(frame * 12.9898) * 0.37f);
        float jitterY = (float) (Math.sin(frame * 78.233) * 0.37f);

        event.getGuiGraphics().pose().translate(
                (dirX + jitterX) * amplitude,
                (dirY + jitterY) * amplitude,
                0.0f);
    }

    @SubscribeEvent
    public static void onRenderGuiPost(RenderGuiEvent.Post event) {
        long startTime = PainShockClientState.getShockStartTime();
        if (startTime <= 0 || PainShockClientState.isExpired()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed < 0) return;

        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        GuiGraphics graphics = event.getGuiGraphics();

        float intensity = PainShockClientState.getShockIntensity();
        float flashMax = MedicalConfig.PAIN_SHOCK_WHITE_FLASH_ALPHA.get().floatValue();

        if (PainShockClientState.isFrozenPhase()) {
            // ===== 定格阶段：白闪（0~0.15s，255→快速回落但不消失） =====
            float t = (float) elapsed / PainShockClientState.getFreezeDurationMs();
            // 前段急升急降的"闪一下"，末段维持中亮
            float flashCurve = t < 0.4f
                    ? t / 0.4f
                    : 1.0f - (t - 0.4f) * 0.55f;
            int alpha = (int) (flashMax * intensity * flashCurve * 255.0f);
            if (alpha > 0) {
                // 冷白偏粉（剧痛的眼前发白感）：RGB(255, 248, 246)
                graphics.fill(0, 0, w, h, (alpha << 24) | 0xFFFFF8F6);
            }
        } else {
            // ===== 淡出阶段：红白余晖 vignette（0.15s~1.15s） =====
            float fade = PainShockClientState.getFadeProgress();
            float alphaMul = 1.0f - fade;

            // 全屏薄纱快速退场（前 30% 淡出期内保留）
            if (fade < 0.3f) {
                float thin = (1.0f - fade / 0.3f) * 0.4f;
                int thinAlpha = (int) (flashMax * intensity * thin * 255.0f);
                if (thinAlpha > 0) {
                    graphics.fill(0, 0, w, h, (thinAlpha << 24) | 0xFFFFF8F6);
                }
            }

            // 边缘余晖：白转红消退（痛感回荡的血管搏动残像）
            int emberAlpha = (int) (alphaMul * alphaMul * intensity * 140.0f);
            if (emberAlpha > 0) {
                // 由白向暖红过渡：前半白后半红
                int emberColor = fade < 0.5f
                        ? (emberAlpha << 24) | 0xFFFFF0EA
                        : (emberAlpha << 24) | 0xE0FF3B30;
                int edgeSize = (int) (Math.min(w, h) * (0.22f + 0.12f * alphaMul));
                if (edgeSize > 0) {
                    graphics.fillGradient(0, 0, w, edgeSize, emberColor, 0x00000000);
                    graphics.fillGradient(0, h - edgeSize, w, h, 0x00000000, emberColor);
                    graphics.fillGradient(0, 0, edgeSize, h, emberColor, 0x00000000);
                    graphics.fillGradient(w - edgeSize, 0, w, h, 0x00000000, emberColor);
                }
            }
        }
    }
}
