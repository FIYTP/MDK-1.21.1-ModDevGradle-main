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
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import org.joml.Matrix4f;

/**
 * 3A 级突发重伤视觉冲击渲染层 (PainShockOverlay —— 修复黑视内径过小与遮挡倒计时问题)
 */
@EventBusSubscriber(modid = "realmedicalmod", value = Dist.CLIENT)
public class PainShockOverlay {

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onRenderGuiPost(RenderGuiEvent.Post event) {
        if (!MedicalConfig.PAIN_SHOCK_ENABLED.get()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (mc.screen != null || mc.options.hideGui || mc.player.isSpectator()) return;

        float shockAlpha = PainShockClientState.getShockAlpha();
        float progress = PainShockClientState.getShockProgress();
        float blackout = PainShockClientState.getBlackoutAlpha();
        float splatterAlpha = PainShockClientState.getBloodSplatterAlpha();
        float flashAlpha = PainShockClientState.getConcussionFlashAlpha();

        if (shockAlpha <= 0.002f && blackout <= 0.002f && splatterAlpha <= 0.002f) return;

        GuiGraphics graphics = event.getGuiGraphics();
        int w = graphics.guiWidth();
        int h = graphics.guiHeight();
        Matrix4f pose = graphics.pose().last().pose();

        float hitX = PainShockClientState.getHitDirX();
        float hitY = PainShockClientState.getHitDirY();
        float leftBoost = Math.max(0.0f, -hitX) * 0.55f;
        float rightBoost = Math.max(0.0f, hitX) * 0.55f;
        float topBoost = Math.max(0.0f, -hitY) * 0.55f;
        float bottomBoost = Math.max(0.0f, hitY) * 0.55f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        // ---------- 1. 突发重创“眼前一黑”深黑边角隧道猛烈收缩（外环纯黑 #000000 锁死四角，中心留出准星求生窗） ----------
        if (blackout > 0.002f) {
            float minInnerCfg = MedicalConfig.TRAUMA_BLACK_CORNER_MIN_INNER_RADIUS.get().floatValue();
            float maxAlphaCfg = MedicalConfig.TRAUMA_BLACK_CORNER_MAX_ALPHA.get().floatValue();
            float tunnelInner = Mth.clamp(0.64f - 0.38f * blackout, minInnerCfg, 0.64f);
            float tunnelMid = tunnelInner + 0.18f;
            float tunnelOuterStart = tunnelInner + 0.32f;

            // 内过渡环：深暗血红向黑渊过渡
            PainVisualOverlay.drawEllipticalRing(
                    pose, w, h,
                    tunnelInner, 0.0f,
                    tunnelMid, Mth.clamp(blackout * 0.68f, 0.0f, 0.75f),
                    48, 2, 6,
                    leftBoost, rightBoost, topBoost, bottomBoost
            );
            // 中层黑视环：浓重墨黑压迫
            PainVisualOverlay.drawEllipticalRing(
                    pose, w, h,
                    tunnelMid, Mth.clamp(blackout * 0.68f, 0.0f, 0.75f),
                    tunnelOuterStart, Mth.clamp(blackout * 0.90f, 0.0f, 0.92f),
                    6, 0, 2,
                    leftBoost, rightBoost, topBoost, bottomBoost
            );
            // 外圈四角纯黑封边层（RGB 0,0,0，无缝锁死屏幕四角）
            PainVisualOverlay.drawEllipticalRing(
                    pose, w, h,
                    tunnelOuterStart, Mth.clamp(blackout * 0.88f, 0.0f, 0.92f),
                    1.48f, Mth.clamp(blackout * maxAlphaCfg, 0.0f, maxAlphaCfg),
                    0, 0, 0,
                    leftBoost, rightBoost, topBoost, bottomBoost
            );
        }

        // ---------- 2. 动脉血雾迸裂与定向血斑飞溅 ----------
        if (splatterAlpha > 0.005f) {
            drawArterialBloodSplatter(pose, w, h, splatterAlpha, PainShockClientState.getShockSeed(), hitX, hitY);
        }

        if (shockAlpha > 0.002f) {
            // ---------- 3. 强力 RGB 色差震荡环 ----------
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);

            float waveRadius = 0.34f + 0.44f * (1.0f - (float) Math.pow(1.0f - progress, 3));
            float caAlpha = Mth.clamp(shockAlpha * 0.72f, 0.0f, 0.78f);

            PainVisualOverlay.drawEllipticalRing(
                    pose, w, h,
                    waveRadius, 0.0f,
                    waveRadius + 0.24f, caAlpha,
                    255, 20, 42,
                    leftBoost, rightBoost, topBoost, bottomBoost
            );
            PainVisualOverlay.drawEllipticalRing(
                    pose, w, h,
                    Math.max(0.24f, waveRadius - 0.05f), 0.0f,
                    waveRadius + 0.16f, caAlpha * 0.85f,
                    25, 230, 255,
                    rightBoost, leftBoost, bottomBoost, topBoost
            );

            // ---------- 4. 受创瞬间白红强光过曝与骨裂闪电裂纹 ----------
            if (flashAlpha > 0.01f) {
                PainVisualOverlay.drawEllipticalRing(
                        pose, w, h,
                        0.32f, 0.0f,
                        1.38f, Mth.clamp(flashAlpha * 0.78f, 0.0f, 0.82f),
                        255, 225, 230,
                        leftBoost, rightBoost, topBoost, bottomBoost
                );
                drawImpactFractureCracks(pose, w, h, flashAlpha, PainShockClientState.getShockSeed(), hitX, hitY);
            }

            // ---------- 5. 48 道高对比痛觉神经电脉冲芒刺 ----------
            drawNeuralShockSpikes(pose, w, h, shockAlpha, progress, PainShockClientState.getShockSeed(), hitX, hitY);

            RenderSystem.defaultBlendFunc();
        }

        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private static void drawArterialBloodSplatter(Matrix4f pose, int w, int h, float alpha, long seed, float hitX, float hitY) {
        float cx = w * 0.5f;
        float cy = h * 0.5f;
        float rx = w * 0.5f;
        float ry = h * 0.5f;

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean hasVerts = false;

        int blobCount = 34;
        for (int i = 0; i < blobCount; i++) {
            long h1 = seed ^ (i * 0x9E3779B97F4A7C15L);
            long h2 = (h1 ^ (h1 >>> 29)) * 0xBF58476D1CE4E5B9L;
            float u1 = ((h2 >>> 8) & 0xFFFF) / 65535.0f;
            float u2 = ((h2 >>> 24) & 0xFFFF) / 65535.0f;
            float u3 = ((h2 >>> 40) & 0xFFFF) / 65535.0f;

            double angle = (i / (double) blobCount) * Math.PI * 2.0 + (u1 - 0.5f) * 0.28;
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);

            float dirDot = Math.max(0.0f, cos * hitX + sin * hitY);
            float radialDist = Mth.lerp(u2, 0.58f - dirDot * 0.14f, 0.98f);

            float bx = cx + cos * rx * radialDist;
            float by = cy + sin * ry * radialDist;
            float radius = (4.5f + u3 * 13.5f) * (0.85f + 0.45f * dirDot);

            int aCore = Mth.clamp((int) (alpha * (0.68f + 0.32f * dirDot) * 235.0f), 0, 240);
            int aRim = aCore / 3;
            if (aCore < 6) continue;

            for (int k = 0; k < 4; k++) {
                float a = k * ((float) Math.PI * 0.25f) + u1;
                float dx = Mth.cos(a) * radius;
                float dy = Mth.sin(a) * radius * (0.8f + 0.4f * u2);
                PainVisualOverlay.addTaperedQuadSegment(buf, pose,
                        bx - dx, by - dy, bx + dx, by + dy,
                        radius * 0.72f, radius * 0.68f,
                        165, 8, 16, aRim, aRim);
                PainVisualOverlay.addTaperedQuadSegment(buf, pose,
                        bx - dx * 0.65f, by - dy * 0.65f, bx + dx * 0.65f, by + dy * 0.65f,
                        radius * 0.45f, radius * 0.42f,
                        110, 4, 10, aCore, aCore);
            }

            float streakLen = radius * (1.8f + u1 * 2.4f);
            float sx = bx - cos * streakLen * 0.55f;
            float sy = by - sin * streakLen * 0.35f + streakLen * 0.65f * (1.0f - alpha * 0.4f);
            PainVisualOverlay.addTaperedQuadSegment(buf, pose,
                    bx, by, sx, sy,
                    radius * 0.32f, 0.4f,
                    185, 12, 20, aCore, 0);
            hasVerts = true;
        }

        MeshData mesh = buf.build();
        if (mesh != null) {
            if (hasVerts) {
                BufferUploader.drawWithShader(mesh);
            } else {
                mesh.close();
            }
        }
    }

    private static void drawImpactFractureCracks(Matrix4f pose, int w, int h, float flashAlpha, long seed, float hitX, float hitY) {
        float cx = w * 0.5f;
        float cy = h * 0.5f;
        float rx = w * 0.5f;
        float ry = h * 0.5f;

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean hasVerts = false;

        int cracks = 14;
        for (int i = 0; i < cracks; i++) {
            long hash = seed ^ (i * 0x517CC1B727220A95L);
            float r1 = ((hash >>> 12) & 0xFFFF) / 65535.0f;
            float r2 = ((hash >>> 28) & 0xFFFF) / 65535.0f;

            double baseAngle = (i / (double) cracks) * Math.PI * 2.0 + (r1 - 0.5f) * 0.35;
            float cos = (float) Math.cos(baseAngle);
            float sin = (float) Math.sin(baseAngle);
            float dirWeight = 1.0f + Math.max(0.0f, cos * hitX + sin * hitY) * 0.75f;

            float x = cx + cos * rx * 1.05f;
            float y = cy + sin * ry * 1.05f;
            float inX = -cos;
            float inY = -sin;
            float perpX = -inY;
            float perpY = inX;

            float totalLen = Math.min(w, h) * (0.24f + 0.18f * r2) * dirWeight;
            int steps = 6;
            for (int s = 1; s <= steps; s++) {
                float t = s / (float) steps;
                float jag = (((hash >>> (s * 5)) & 0x1F) / 15.5f - 1.0f) * 14.0f * (1.0f - t * 0.5f);
                float nx = (cx + cos * rx * 1.05f) + inX * (t * totalLen) + perpX * jag;
                float ny = (cy + sin * ry * 1.05f) + inY * (t * totalLen) + perpY * jag;

                float w1 = (2.6f - 1.8f * ((s - 1) / (float) steps)) * dirWeight;
                float w2 = (2.6f - 1.8f * t) * dirWeight;
                int a1 = Mth.clamp((int) (flashAlpha * (1.0f - (s - 1) / (float) steps) * 245.0f), 0, 255);
                int a2 = Mth.clamp((int) (flashAlpha * (1.0f - t) * 245.0f), 0, 255);

                PainVisualOverlay.addTaperedQuadSegment(buf, pose, x, y, nx, ny,
                        w1 * 2.0f, w2 * 2.0f, 255, 25, 50, a1 / 2, a2 / 2);
                PainVisualOverlay.addTaperedQuadSegment(buf, pose, x, y, nx, ny,
                        w1 * 0.8f, w2 * 0.8f, 255, 245, 250, a1, a2);
                x = nx;
                y = ny;
                hasVerts = true;
            }
        }

        MeshData mesh = buf.build();
        if (mesh != null) {
            if (hasVerts) {
                BufferUploader.drawWithShader(mesh);
            } else {
                mesh.close();
            }
        }
    }

    private static void drawNeuralShockSpikes(Matrix4f pose, int w, int h, float shockAlpha, float progress, long seed, float hitX, float hitY) {
        float cx = w * 0.5f;
        float cy = h * 0.5f;
        float rx = w * 0.5f;
        float ry = h * 0.5f;

        int numSpikes = 48;
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        for (int i = 0; i < numSpikes; i++) {
            long hash = seed ^ (i * 0x9E3779B97F4A7C15L);
            float rand01 = ((hash >>> 16) & 0xFFFF) / 65535.0f;
            float rand02 = ((hash >>> 32) & 0xFFFF) / 65535.0f;

            double angle = (i / (double) numSpikes) * Math.PI * 2.0 + (rand01 - 0.5f) * 0.12;
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);

            float dirBoost = 1.0f + Math.max(0.0f, cos * hitX + sin * hitY) * 0.65f;
            float spikeReach = (0.28f + 0.36f * rand02) * shockAlpha * dirBoost;
            float outerR = 1.15f;
            float innerR = Math.max(0.32f, outerR - spikeReach * (1.15f - 0.45f * progress));

            float x1 = cx + cos * rx * outerR;
            float y1 = cy + sin * ry * outerR;
            float x2 = cx + cos * rx * innerR;
            float y2 = cy + sin * ry * innerR;

            float width = (1.2f + rand01 * 2.2f) * dirBoost;
            int aOuter = (int) (Mth.clamp(shockAlpha * (0.65f + 0.35f * rand02) * dirBoost, 0.0f, 0.96f) * 255.0f);

            boolean isCrimson = (i % 3 != 0);
            int r = isCrimson ? 255 : 245;
            int g = isCrimson ? 22 : 245;
            int b = isCrimson ? 42 : 255;

            PainVisualOverlay.addQuadSegment(buffer, pose, x1, y1, x2, y2, width, r, g, b, aOuter, 0);
        }

        MeshData mesh = buffer.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
    }
}