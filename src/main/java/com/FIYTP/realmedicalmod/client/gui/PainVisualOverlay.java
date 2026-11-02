package com.FIYTP.realmedicalmod.client.gui;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.capability.MedicalDataCapability;
import com.FIYTP.realmedicalmod.config.MedicalConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * 疼痛视觉层：红晕血管搏动 vignette（方向1）+ 重度灰蓝色罩（方向3近似版）。
 *
 * 设计要点：
 * - 搏动频率绑定真实心率（data.getHeartRate()），痛越重 → 心率越快 → 红晕搏动越急促，
 *   与 HUD "同频搏动血管网络" 和 CrisisSoundManager 心跳音效三通道同频共振。
 * - 搏动曲线用双峰脉冲（主搏 + 次搏）模拟真实脉搏波形，比正弦波更有"血管跳动"质感。
 * - 多层渐变：外圈深红 + 内圈亮红暖光 + 屏幕上缘轻微渗透，营造血液涌向视野边缘的压迫感。
 * - alpha 预算：与意识黑色暗角同帧叠加时主动衰减，避免双重 vignette 叠加过黑。
 */
@EventBusSubscriber(modid = "realmedicalmod", value = Dist.CLIENT)
public class PainVisualOverlay {

    /** 上一次渲染的搏动相位（毫秒基准，客户端 tick 驱动会更准但渲染帧足够） */
    private static long lastFrameTime = 0;
    /** 平滑后的心率（避免瞬时心率抖动导致搏动节奏跳变） */
    private static float smoothHeartRate = 72.0f;
    /** 平滑后的体感疼痛（减轻配置读取与数据跳变的视觉突兀） */
    private static float smoothPain = 0.0f;

    @SubscribeEvent
    public static void onRenderGuiPost(RenderGuiEvent.Post event) {
        if (!MedicalConfig.PAIN_VIGNETTE_ENABLED.get()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        // 视觉遮罩只覆盖游戏内画面，不阻挡打开的容器界面
        if (mc.screen != null) return;

        MedicalData data = mc.player.getData(MedicalDataCapability.MEDICAL_DATA);
        if (data == null) return;

        int perceivedPain = data.getPerceivedPain();
        int startThreshold = MedicalConfig.PAIN_VIGNETTE_START_THRESHOLD.get();
        int fullThreshold = MedicalConfig.PAIN_VIGNETTE_FULL_THRESHOLD.get();
        int grayThreshold = MedicalConfig.PAIN_GRAY_WASH_THRESHOLD.get();

        // 帧间隔与时间平滑
        long now = System.currentTimeMillis();
        long frameDelta = lastFrameTime == 0 ? 16 : Math.min(100, now - lastFrameTime);
        lastFrameTime = now;
        float dt = frameDelta / 1000.0f;

        // 平滑体感疼痛（指数趋近，τ≈0.4s：既保留冲击感又不闪烁）
        smoothPain += (perceivedPain - smoothPain) * Math.min(1.0f, dt / 0.4f);

        // 平滑心率（τ≈1.5s：搏动节奏过渡要更慢才自然）
        int hr = data.getHeartRate();
        if (hr > 0) {
            smoothHeartRate += (hr - smoothHeartRate) * Math.min(1.0f, dt / 1.5f);
        }

        if (smoothPain < startThreshold) return;

        // 归一化强度：起始→满强度线性插值
        float span = Math.max(1, fullThreshold - startThreshold);
        float intensity = Math.max(0.0f, Math.min(1.0f, (smoothPain - startThreshold) / span));

        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        GuiGraphics graphics = event.getGuiGraphics();

        // ===== 搏动波形：双峰脉搏（主搏锐起缓落 + 次搏弱回声），周期 = 60000/HR 毫秒 =====
        float periodMs = 60000.0f / Math.max(40.0f, smoothHeartRate);
        float phase = (now % (long) periodMs) / periodMs; // 0~1
        float pulse = pulseWave(phase);

        // 意识暗角在场时给红晕打折（黑色暗角已经占用了边缘 alpha 预算）
        int consciousness = data.getConsciousness();
        float consciousnessTax = 1.0f;
        if (consciousness > 0 && consciousness < 80) {
            consciousnessTax = 0.55f + 0.45f * (consciousness / 80.0f);
        }

        // ===== 第 1 层：外圈深红 vignette（四周梯度，随搏动呼吸） =====
        float maxAlpha = MedicalConfig.PAIN_VIGNETTE_MAX_ALPHA.get().floatValue();
        // 基线 60% + 搏动起伏 40%：静息时也有持续红晕，搏动时明显涌上来
        float breathe = 0.6f + 0.4f * pulse;
        float vignetteAlphaF = maxAlpha * intensity * breathe * consciousnessTax;
        int vAlpha = (int) (vignetteAlphaF * 255.0f);
        if (vAlpha > 0) {
            // 深红偏暗（血液淤积感）：RGB(120, 8, 12)
            int outerColor = (vAlpha << 24) | 0xFF78080C;
            int edgeSize = (int) (Math.min(w, h) * (0.28f + 0.10f * intensity) );
            if (edgeSize > 0) {
                // 四边梯度，中心透明
                graphics.fillGradient(0, 0, w, edgeSize, outerColor, 0x00000000);
                graphics.fillGradient(0, h - edgeSize, w, h, 0x00000000, outerColor);
                graphics.fillGradient(0, 0, edgeSize, h, outerColor, 0x00000000);
                graphics.fillGradient(w - edgeSize, 0, w, h, 0x00000000, outerColor);
            }
        }

        // ===== 第 2 层：内圈亮红暖光（更窄，只在搏动峰时明显，制造"涌上心头"的冲击） =====
        // 搏动峰附近才出现：pulse > 0.55 才渲染，宽度与亮度都吃 pulse
        if (pulse > 0.55f) {
            float surge = (pulse - 0.55f) / 0.45f; // 0~1
            int sAlpha = (int) (maxAlpha * intensity * surge * 0.55f * consciousnessTax * 255.0f);
            if (sAlpha > 0) {
                // 亮红暖调（充血感）：RGB(255, 40, 30)
                int innerColor = (sAlpha << 24) | 0xFFFF281E;
                int innerEdge = (int) (Math.min(w, h) * (0.12f + 0.06f * intensity));
                if (innerEdge > 0) {
                    graphics.fillGradient(0, 0, w, innerEdge, innerColor, 0x00000000);
                    graphics.fillGradient(0, h - innerEdge, w, h, 0x00000000, innerColor);
                    graphics.fillGradient(0, 0, innerEdge, h, innerColor, 0x00000000);
                    graphics.fillGradient(w - innerEdge, 0, w, h, 0x00000000, innerColor);
                }
            }
        }

        // ===== 第 3 层：重度灰蓝色罩（方向3近似版：痛到发灰，≥grayThreshold 渐入） =====
        if (smoothPain >= grayThreshold) {
            float graySpan = Math.max(1.0f, 55.0f - grayThreshold);
            float grayIntensity = Math.min(1.0f, (smoothPain - grayThreshold) / graySpan);
            // 灰蓝罩不吃搏动（持续性的感知钝化），但随强度加深
            float grayAlphaF = MedicalConfig.PAIN_GRAY_WASH_MAX_ALPHA.get().floatValue()
                    * grayIntensity;
            int gAlpha = (int) (grayAlphaF * 255.0f);
            if (gAlpha > 0) {
                // 冷灰蓝（痛觉过载的失色感）：RGB(150, 165, 185)
                int grayColor = (gAlpha << 24) | 0xFF96A5B9;
                graphics.fill(0, 0, w, h, grayColor);
            }
        }
    }

    /**
     * 双峰脉搏波形。phase ∈ [0,1)：
     * - 主搏 [0, 0.18)：锐起缓落（心跳收缩相）
     * - 次搏 [0.28, 0.42)：低幅回声（舒张充盈，幅度约主搏 40%）
     * - 其余：静息基线
     */
    private static float pulseWave(float phase) {
        if (phase < 0.18f) {
            // 主搏：快速上升到 1，随后指数回落
            float t = phase / 0.18f;
            if (t < 0.25f) {
                return t / 0.25f; // 0→1 锐起
            }
            float fall = (t - 0.25f) / 0.75f;
            return (1.0f - fall) * (1.0f - fall); // 缓落
        }
        if (phase >= 0.28f && phase < 0.42f) {
            // 次搏：钟形弱回声，峰值约 0.4
            float t = (phase - 0.28f) / 0.14f; // 0~1
            float bell = 1.0f - (2.0f * t - 1.0f) * (2.0f * t - 1.0f);
            return 0.4f * bell;
        }
        return 0.0f;
    }
}
