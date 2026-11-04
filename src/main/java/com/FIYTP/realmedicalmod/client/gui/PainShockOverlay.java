package com.FIYTP.realmedicalmod.client.gui;

import com.FIYTP.realmedicalmod.client.PainShockClientState;
import com.FIYTP.realmedicalmod.config.MedicalConfig;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import org.joml.Matrix4f;

/**
 * 3A 级急性痛觉休克冲击渲染层 (PainShockOverlay)
 *
 * 替代原有的全屏死白/死黑闪烁与粗糙横条，采用《逃离塔科夫》/《赛博朋克 2077》受创神经震荡特效：
 * 1. 瞬发径向肾上腺素过曝冲击环 (Radial Overexposure Shockwave)：
 *    受创瞬间外围产生冷白/猩红高光过曝环并向边缘极速坍缩，绝不遮挡准星中央视野。
 * 2. RGB 棱镜色差震荡环 (Chromatic Aberration Shock Ring)：
 *    利用 Additive 混合在视野边缘产生红/青双色错位冲击光晕，营造强烈的神经眩晕质感。
 * 3. 径向痛觉神经电脉冲芒刺 (Radial Neural Spike Rays)：
 *    沿屏幕四周向内迸发 36 道不规则锐利神经芒刺，随指数曲线快速消退。
 * 4. 痛觉黑视边缘聚拢 (Tunnel Blackout Contraction)：
 *    当剧痛超过重度阈值时，四周呈现丝滑的椭圆黑视收缩，随后渐进恢复。
 */
@EventBusSubscriber(modid = "realmedicalmod", value = Dist.CLIENT)
public class PainShockOverlay {

    @SubscribeEvent
    public static void onRenderGuiPost(RenderGuiEvent.Post event) {
        if (!MedicalConfig.PAIN_SHOCK_ENABLED.get()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (mc.screen != null || mc.options.hideGui || mc.player.isSpectator()) return;

        float shockAlpha = PainShockClientState.getShockAlpha();
        float progress = PainShockClientState.getShockProgress();
        float blackout = PainShockClientState.getBlackoutAlpha();

        if (shockAlpha <= 0.002f && blackout <= 0.002f) return;

        GuiGraphics graphics = event.getGuiGraphics();
        int w = graphics.guiWidth();
        int h = graphics.guiHeight();
        Matrix4f pose = graphics.pose().last().pose();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        // ---------- 1. 痛觉黑视椭圆隧道收缩 (Severe Pain Tunnel Blackout) ----------
        if (blackout > 0.002f) {
            float tunnelInner = Mth.clamp(0.62f - 0.28f * blackout, 0.25f, 0.65f);
            PainVisualOverlay.drawEllipticalRing(
                    pose, w, h,
                    tunnelInner, 0.0f,
                    1.38f, Mth.clamp(blackout * 0.92f, 0.0f, 0.92f),
                    8, 4, 8,
                    0.0f, 0.0f, 0.0f, 0.0f
            );
        }

        if (shockAlpha > 0.002f) {
            // ---------- 2. RGB 棱镜色差分离冲击波 (Chromatic Aberration Ring) ----------
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);

            // 冲击波半径随 progress 从中心向外围扩散
            float waveRadius = 0.42f + 0.35f * (1.0f - (float) Math.pow(1.0f - progress, 3));
            float caAlpha = Mth.clamp(shockAlpha * 0.55f, 0.0f, 0.65f);

            // 红色外偏环
            PainVisualOverlay.drawEllipticalRing(
                    pose, w, h,
                    waveRadius, 0.0f,
                    waveRadius + 0.24f, caAlpha,
                    255, 25, 45,
                    0.0f, 0.0f, 0.0f, 0.0f
            );
            // 青色内偏环
            PainVisualOverlay.drawEllipticalRing(
                    pose, w, h,
                    Math.max(0.22f, waveRadius - 0.04f), 0.0f,
                    waveRadius + 0.16f, caAlpha * 0.85f,
                    30, 225, 255,
                    0.0f, 0.0f, 0.0f, 0.0f
            );

            // ---------- 3. 瞬发冷白神经闪光环 (仅在受击前 35% 时间内闪烁，中心留空不瞎眼) ----------
            if (progress < 0.35f) {
                float flashFactor = 1.0f - (progress / 0.35f);
                float whiteAlpha = Mth.clamp(shockAlpha * flashFactor * 0.75f, 0.0f, 0.75f);
                PainVisualOverlay.drawEllipticalRing(
                        pose, w, h,
                        0.35f, whiteAlpha * 0.12f,
                        1.35f, whiteAlpha,
                        255, 240, 245,
                        0.0f, 0.0f, 0.0f, 0.0f
                );
            }

            // ---------- 4. 径向痛觉神经电脉冲芒刺 (Radial Neural Spike Rays) ----------
            drawNeuralShockSpikes(pose, w, h, shockAlpha, progress, PainShockClientState.getShockSeed());

            RenderSystem.defaultBlendFunc();
        }

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    /**
     * 绘制沿屏幕四周向中心迸发的 3A 痛觉神经芒刺针线
     */
    private static void drawNeuralShockSpikes(Matrix4f pose, int w, int h, float shockAlpha, float progress, long seed) {
        float cx = w * 0.5f;
        float cy = h * 0.5f;
        float rx = w * 0.5f;
        float ry = h * 0.5f;

        int numSpikes = 40;
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        for (int i = 0; i < numSpikes; i++) {
            // 基于冲击种子生成稳定但每次受击不同的芒刺排布
            long hash = seed ^ (i * 0x9E3779B97F4A7C15L);
            float rand01 = ((hash >>> 16) & 0xFFFF) / 65535.0f;
            float rand02 = ((hash >>> 32) & 0xFFFF) / 65535.0f;

            double angle = (i / (double) numSpikes) * Math.PI * 2.0 + (rand01 - 0.5f) * 0.12;
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);

            // 芒刺从屏幕外框 (1.15) 快速刺向内侧 (0.48 ~ 0.78)，随后向边缘回缩
            float spikeReach = (0.22f + 0.28f * rand02) * shockAlpha;
            float outerR = 1.15f;
            float innerR = Math.max(0.42f, outerR - spikeReach * (1.1f - 0.4f * progress));

            float x1 = cx + cos * rx * outerR;
            float y1 = cy + sin * ry * outerR;
            float x2 = cx + cos * rx * innerR;
            float y2 = cy + sin * ry * innerR;

            float width = 0.8f + rand01 * 1.6f;
            int aOuter = (int) (Mth.clamp(shockAlpha * (0.5f + 0.5f * rand02), 0.0f, 0.9f) * 255.0f);

            // 交替冷白与鲜红神经电脉冲
            boolean isCrimson = (i % 3 != 0);
            int r = isCrimson ? 255 : 235;
            int g = isCrimson ? 35 : 245;
            int b = isCrimson ? 50 : 255;

            PainVisualOverlay.addQuadSegment(buffer, pose, x1, y1, x2, y2, width, r, g, b, aOuter, 0);
        }

        MeshData mesh = buffer.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
    }
}