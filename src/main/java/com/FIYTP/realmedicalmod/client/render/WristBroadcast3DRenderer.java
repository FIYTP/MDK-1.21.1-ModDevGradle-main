package com.FIYTP.realmedicalmod.client.render;

import com.FIYTP.realmedicalmod.client.WristBroadcastManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

import java.util.List;

/**
 * 阶段 3（美学重构版）：精密 45° 折线透明全息战术播报条 (WristBroadcast3DRenderer)
 *
 * 彻底废除廉价黑大框，与阶段 2 军工表盘美学 100% 统一：
 * - 256x128 实时高精透明光学光栅纹理（仅 28% 幽暗玻璃通透度 + 隔行扫描线 + 45° 切角极细边框 + 四角锚点）；
 * - 左侧独立六边形定制像素医学矢量徽章（血滴/断骨/穿孔靶心/双肺/三叶生物危害/心电/医疗绿十字）；
 * - 顶沿切角缺口处嵌 3x5 军工点阵眉标；右侧以带暗槽底影的发光七段军工数码管打出醒目核心数值；
 * - 从表壳 12 点钟双光学透镜拉出 45° 激光引导折线（Laser Draw-in 展开动画）托起悬浮全息条；
 * - 100% 绑定左手手表 3D 局部坐标系，零 2D 贴屏。
 */
public final class WristBroadcast3DRenderer {

    private static final int TEX_W = 256;
    private static final int TEX_H = 128; // 上半 0..63 槽位0，下半 64..127 槽位1

    private static NativeImage ribbonImage = null;
    private static DynamicTexture ribbonTexture = null;
    private static ResourceLocation ribbonTextureId = null;
    private static long lastUploadMs = 0L;

    private static final int[] SEG_DIGITS = {
            0b0111111, 0b0000110, 0b1011011, 0b1001111, 0b1100110,
            0b1101101, 0b1111101, 0b0000111, 0b1111111, 0b1101111
    };

    private WristBroadcast3DRenderer() {}

    public static void renderAttachedBroadcasts(PoseStack poseStack,
                                                MultiBufferSource bufferSource,
                                                ResourceLocation watchAtlasTex,
                                                float caseBaseZ,
                                                float caseRadius,
                                                float caseDepth) {
        List<WristBroadcastManager.BroadcastEntry> entries = WristBroadcastManager.getActiveEntries();
        if (entries.isEmpty()) return;

        // 只要手表进入渲染管线，标记未读播报已被玩家视野触达（驱动激光折线展开）
        WristBroadcastManager.markEntriesViewedNow();

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        if (font == null) return;

        long nowMs = System.currentTimeMillis();
        ResourceLocation liveRibbonTex = getOrUpdateRibbonTexture(entries, nowMs);
        float zTop = caseBaseZ + caseDepth;

        poseStack.pushPose();
        // 锚定在表壳 12 点钟双光学发射透镜上方空气中（+X 为正上方，+Z 朝向玩家双眼）
        poseStack.translate(caseRadius + 0.036f, 0.0f, zTop + 0.044f);
        // 阶段 2 实机校准正交基：+x' 向右，+y' 向上，+z' 正对玩家
        poseStack.mulPose(Axis.ZP.rotationDegrees(-90.0f));
        poseStack.mulPose(Axis.XP.rotationDegrees(-7.0f));

        float cardW = 0.290f;
        float cardH = 0.072f;
        float gap = 0.012f;

        // 1. 绘制从表壳双透镜拉出的 45° 细锐发光激光引导折线 (Laser Leader Line)
        WristBroadcastManager.BroadcastEntry first = entries.get(0);
        float firstOpen = computeRibbonAnim(first, nowMs);
        if (firstOpen > 0.02f) {
            int[] rgb = getSeverityRgb(first.severity);
            float lineProg = Mth.clamp(firstOpen * 2.2f, 0.0f, 1.0f);
            VertexConsumer lineVc = bufferSource.getBuffer(RenderType.entityTranslucent(watchAtlasTex));
            PoseStack.Pose p = poseStack.last();
            Matrix4f m = p.pose();
            // 起点在表壳双透镜 (0, -0.044, -0.036) -> 斜上折点 (-0.035, -0.008, -0.006) -> 横延至标签左下角
            float elbowX = Mth.lerp(lineProg, 0.0f, -0.055f);
            float elbowY = Mth.lerp(lineProg, -0.042f, -0.004f);
            float elbowZ = Mth.lerp(lineProg, -0.036f, -0.002f);
            emitThinBeam3D(lineVc, p, m, 0.0f, -0.042f, -0.036f, elbowX, elbowY, elbowZ, 0.0020f,
                    rgb[0], rgb[1], rgb[2], Math.round(210 * firstOpen));
            if (lineProg > 0.65f) {
                float hProg = (lineProg - 0.65f) / 0.35f;
                emitThinBeam3D(lineVc, p, m, elbowX, elbowY, elbowZ,
                        Mth.lerp(hProg, elbowX, -cardW * 0.48f), elbowY, elbowZ, 0.0018f,
                        rgb[0], rgb[1], rgb[2], Math.round(180 * firstOpen));
            }
        }

        // 2. 先批量绘制所有全息玻璃卡片面片（严格避免在 font.drawInBatch 切换 RenderType 后复用已关闭的 BufferBuilder！）
        int count = Math.min(entries.size(), 2);
        VertexConsumer cardVc = bufferSource.getBuffer(RenderType.entityTranslucent(liveRibbonTex));
        for (int i = 0; i < count; i++) {
            WristBroadcastManager.BroadcastEntry e = entries.get(i);
            float open = computeRibbonAnim(e, nowMs);
            if (open <= 0.02f) continue;

            float slideProg = Mth.clamp((open - 0.15f) / 0.85f, 0.0f, 1.0f);
            float curW = cardW * (0.18f + 0.82f * slideProg);
            float x0 = -cardW * 0.5f;
            float x1 = x0 + curW;
            float y0 = i * (cardH + gap);
            float y1 = y0 + cardH;

            float v0 = (i == 0) ? 0.0f : 0.5f;
            float v1 = (i == 0) ? 0.5f : 1.0f;
            float u1 = slideProg;
            int a = Mth.clamp(Math.round(open * 255.0f), 0, 255);

            PoseStack.Pose p = poseStack.last();
            Matrix4f m = p.pose();
            emitTexturedQuad(cardVc, p, m, x0 + 0.002f, y0 - 0.002f, -0.003f,
                    x1 + 0.002f, y1 - 0.002f, -0.003f, 0.0f, v1, u1, v0, 120, 210, 255, Math.round(a * 0.22f));
            emitTexturedQuad(cardVc, p, m, x0, y0, 0.0f, x1, y1, 0.0f,
                    0.0f, v1, u1, v0, 255, 255, 255, a);
        }

        // 3. 所有玻璃卡片写完后再统一绘制 3D 空间中文标题（绝不再触发 Not building!）
        for (int i = 0; i < count; i++) {
            WristBroadcastManager.BroadcastEntry e = entries.get(i);
            float open = computeRibbonAnim(e, nowMs);
            if (open <= 0.02f) continue;
            float slideProg = Mth.clamp((open - 0.15f) / 0.85f, 0.0f, 1.0f);
            if (slideProg <= 0.55f) continue;

            float x0 = -cardW * 0.5f;
            float y0 = i * (cardH + gap);
            int textA = Mth.clamp(Math.round(((slideProg - 0.55f) / 0.45f) * open * 255.0f), 30, 255);
            int titleCol = (textA << 24) | 0xF2FBFF;
            poseStack.pushPose();
            poseStack.translate(x0 + 0.056f, y0 + cardH * 0.62f, 0.0018f);
            float ts = 0.00215f;
            poseStack.scale(ts, -ts, ts);
            font.drawInBatch(e.title, 0.0f, 0.0f, titleCol, false,
                    poseStack.last().pose(), bufferSource, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
            poseStack.popPose();
        }

        poseStack.popPose();
    }

    // ==================== 256x128 透明全息光栅纹理动态烘焙 ====================

    private static ResourceLocation getOrUpdateRibbonTexture(List<WristBroadcastManager.BroadcastEntry> entries, long nowMs) {
        if (ribbonTextureId == null) {
            ribbonImage = new NativeImage(TEX_W, TEX_H, true);
            ribbonTexture = new DynamicTexture(ribbonImage);
            ribbonTextureId = Minecraft.getInstance().getTextureManager().register("realmedicalmod_wrist_broadcast_ribbon", ribbonTexture);
        }
        if (nowMs - lastUploadMs >= 45L) {
            lastUploadMs = nowMs;
            clearImage();
            for (int i = 0; i < Math.min(entries.size(), 2); i++) {
                paintRibbonSlot(entries.get(i), i * 64, nowMs);
            }
            ribbonTexture.upload();
        }
        return ribbonTextureId;
    }

    private static void paintRibbonSlot(WristBroadcastManager.BroadcastEntry e, int yOff, long nowMs) {
        int[] rgb = getSeverityRgb(e.severity);
        float pulse = (e.severity == WristBroadcastManager.Severity.CRITICAL)
                ? (0.68f + 0.32f * Mth.sin(nowMs * 0.018f))
                : 0.94f;
        int cr = Mth.clamp(Math.round(rgb[0] * pulse), 0, 255);
        int cg = Mth.clamp(Math.round(rgb[1] * pulse), 0, 255);
        int cb = Mth.clamp(Math.round(rgb[2] * pulse), 0, 255);

        int x0 = 2, y0 = yOff + 4, w = 250, h = 56;
        int cut = 7;

        // 1) 极通透 28% 玻璃底 + 隔行全息扫描微光栅（彻底告别厚重黑方框！）
        for (int py = y0 + 1; py < y0 + h; py++) {
            boolean scanLine = ((py - y0) % 3) == 0;
            int bgA = scanLine ? 88 : 56;
            for (int px = x0 + 1; px < x0 + w; px++) {
                // 45° 切角裁剪（左上与右下切角）
                int dxL = px - x0, dyT = py - y0;
                int dxR = (x0 + w) - px, dyB = (y0 + h) - py;
                if (dxL + dyT < cut || dxR + dyB < cut) continue;
                int br = 4 + cr / 28;
                int bg = 10 + cg / 24;
                int bb = 16 + cb / 22;
                setPx(px, py, br, bg, bb, bgA);
            }
        }

        // 2) 极细 45° 切角双层外框 + 四角高光微锚点
        drawLinePx(x0 + cut, y0, x0 + w, y0, cr, cg, cb, 195);
        drawLinePx(x0, y0 + h, x0 + w - cut, y0 + h, cr, cg, cb, 165);
        drawLinePx(x0, y0 + cut, x0, y0 + h, cr, cg, cb, 210);
        drawLinePx(x0 + w, y0, x0 + w, y0 + h - cut, cr, cg, cb, 180);
        drawLinePx(x0, y0 + cut, x0 + cut, y0, cr, cg, cb, 235);
        drawLinePx(x0 + w - cut, y0 + h, x0 + w, y0 + h - cut, cr, cg, cb, 235);
        // 顶部切角军工细刻度尺
        for (int tx = x0 + 52; tx < x0 + w - 12; tx += 8) {
            setPx(tx, y0 + 2, cr, cg, cb, 110);
        }

        // 3) 左侧独立六边形医学矢量图标徽章 (中心 x=26, y=y0+28, 半径 17)
        drawHexBadge(26, y0 + 28, 17, cr, cg, cb);
        drawMedicalVectorIcon(26, y0 + 28, e.icon, cr, cg, cb);

        // 4) 顶沿缺口 3x5 军工点阵丝印眉标 (如 // HEMORRHAGE)
        drawPixelText(50, y0 + 6, "// " + e.tag, cr, cg, cb, 225);

        // 5) 右侧带暗槽底影的发光七段军工数码管核心数值 + 极小单位
        if (e.numericVal >= 0) {
            int digits = e.numericVal >= 100 ? 3 : (e.numericVal >= 10 ? 2 : 1);
            int numX = 242 - digits * 13 - 26;
            drawSevenSeg(numX, y0 + 18, e.numericVal, Math.max(2, digits), 10, 18, 2, cr, cg, cb);
            if (e.unit != null && !e.unit.isEmpty()) {
                drawPixelText(242 - e.unit.length() * 4, y0 + 38, e.unit, cr, cg, cb, 190);
            }
        }
    }

    private static void drawHexBadge(int cx, int cy, int r, int cr, int cg, int cb) {
        int[] xs = new int[6], ys = new int[6];
        for (int i = 0; i < 6; i++) {
            float a = (float) (i * Math.PI / 3.0 + Math.PI / 6.0);
            xs[i] = Math.round(cx + Mth.cos(a) * r);
            ys[i] = Math.round(cy + Mth.sin(a) * r);
        }
        for (int i = 0; i < 6; i++) {
            int n = (i + 1) % 6;
            drawLinePx(xs[i], ys[i], xs[n], ys[n], cr, cg, cb, 220);
        }
    }

    private static void drawMedicalVectorIcon(int cx, int cy, WristBroadcastManager.IconKind kind, int r, int g, int b) {
        switch (kind) {
            case BLEED -> {
                // 滴落血滴矢量轮廓
                for (int dy = -8; dy <= 7; dy++) {
                    float halfW = dy < 0 ? (dy + 8) * 0.72f : Mth.sqrt(Math.max(0.0f, 49.0f - dy * dy)) * 0.85f;
                    drawLinePx(Math.round(cx - halfW), cy + dy, Math.round(cx + halfW), cy + dy, r, g, b, 240);
                }
                setPx(cx - 2, cy + 1, 255, 255, 255, 210);
                setPx(cx - 2, cy + 2, 255, 255, 255, 210);
            }
            case FRACTURE -> {
                // 断裂骨骼 + 闪电裂隙
                drawThickLinePx(cx - 8, cy - 6, cx - 2, cy - 1, r, g, b, 240);
                drawThickLinePx(cx + 2, cy + 1, cx + 8, cy + 6, r, g, b, 240);
                drawLinePx(cx - 2, cy + 5, cx + 2, cy - 5, 255, 255, 255, 230);
            }
            case BALLISTIC -> {
                // 准星穿孔靶心
                drawCirclePx(cx, cy, 7, r, g, b, 235);
                drawLinePx(cx - 10, cy, cx - 4, cy, r, g, b, 245);
                drawLinePx(cx + 4, cy, cx + 10, cy, r, g, b, 245);
                drawLinePx(cx, cy - 10, cx, cy - 4, r, g, b, 245);
                drawLinePx(cx, cy + 4, cx, cy + 10, r, g, b, 245);
                fillRectPx(cx - 1, cy - 1, 3, 3, 255, 255, 255, 255);
            }
            case LUNG -> {
                // 简约对称双肺叶 + 气管
                drawLinePx(cx, cy - 8, cx, cy - 2, r, g, b, 245);
                fillRectPx(cx - 7, cy - 3, 5, 10, r, g, b, 220);
                fillRectPx(cx + 3, cy - 3, 5, 10, r, g, b, 220);
            }
            case BIOHAZARD -> {
                // 三叶生物危害标
                drawCirclePx(cx, cy - 4, 4, r, g, b, 235);
                drawCirclePx(cx - 4, cy + 3, 4, r, g, b, 235);
                drawCirclePx(cx + 4, cy + 3, 4, r, g, b, 235);
                fillRectPx(cx - 1, cy - 1, 3, 3, 255, 255, 255, 230);
            }
            case HEART -> {
                // 心跳折线脉冲
                drawLinePx(cx - 9, cy, cx - 4, cy, r, g, b, 240);
                drawLinePx(cx - 4, cy, cx - 1, cy - 7, r, g, b, 255);
                drawLinePx(cx - 1, cy - 7, cx + 2, cy + 6, r, g, b, 255);
                drawLinePx(cx + 2, cy + 6, cx + 5, cy, r, g, b, 240);
                drawLinePx(cx + 5, cy, cx + 9, cy, r, g, b, 240);
            }
            case RECOVERY -> {
                // 医疗正十字
                fillRectPx(cx - 2, cy - 7, 5, 15, r, g, b, 245);
                fillRectPx(cx - 7, cy - 2, 15, 5, r, g, b, 245);
            }
        }
    }

    private static float computeRibbonAnim(WristBroadcastManager.BroadcastEntry e, long nowMs) {
        if (e.dismissStartMs > 0L) {
            return Mth.clamp(1.0f - (nowMs - e.dismissStartMs) / 260.0f, 0.0f, 1.0f);
        }
        long startRef = e.firstSeenMs > 0L ? e.firstSeenMs : e.createdMs;
        float t = Mth.clamp((nowMs - startRef) / 260.0f, 0.0f, 1.0f);
        return 1.0f - (1.0f - t) * (1.0f - t) * (1.0f - t);
    }

    private static int[] getSeverityRgb(WristBroadcastManager.Severity sev) {
        return switch (sev) {
            case CRITICAL -> new int[]{255, 36, 68};
            case WARNING -> new int[]{255, 184, 0};
            case RECOVERY -> new int[]{0, 240, 175};
        };
    }

    // ==================== 七段数码管、3x5 点阵与 3D 顶点原语 ====================

    private static void drawSevenSeg(int x, int y, int val, int minDigits, int w, int h, int t, int r, int g, int b) {
        String s = String.valueOf(Math.max(0, val));
        int total = Math.max(minDigits, s.length());
        int step = w + t + 1;
        for (int i = 0; i < total; i++) {
            int dx = x + i * step;
            drawSegMask(dx, y, w, h, t, 0b1111111, r, g, b, 34);
            int idx = i - (total - s.length());
            if (idx >= 0 && idx < s.length()) {
                int d = s.charAt(idx) - '0';
                if (d >= 0 && d <= 9) drawSegMask(dx, y, w, h, t, SEG_DIGITS[d], r, g, b, 250);
            }
        }
    }

    private static void drawSegMask(int x, int y, int w, int h, int t, int mask, int r, int g, int b, int a) {
        int hh = h / 2;
        if ((mask & 1) != 0) fillRectPx(x + t, y, w - 2 * t, t, r, g, b, a);
        if ((mask & 2) != 0) fillRectPx(x + w - t, y + t, t, hh - t, r, g, b, a);
        if ((mask & 4) != 0) fillRectPx(x + w - t, y + hh + 1, t, h - hh - t - 1, r, g, b, a);
        if ((mask & 8) != 0) fillRectPx(x + t, y + h - t, w - 2 * t, t, r, g, b, a);
        if ((mask & 16) != 0) fillRectPx(x, y + hh + 1, t, h - hh - t - 1, r, g, b, a);
        if ((mask & 32) != 0) fillRectPx(x, y + t, t, hh - t, r, g, b, a);
        if ((mask & 64) != 0) fillRectPx(x + t, y + hh - t / 2, w - 2 * t, t, r, g, b, a);
    }

    private static void drawPixelText(int x, int y, String text, int r, int g, int b, int a) {
        int cx = x;
        for (int i = 0; i < text.length(); i++) {
            int[] glyph = glyph3x5(Character.toUpperCase(text.charAt(i)));
            for (int row = 0; row < 5; row++) {
                for (int col = 0; col < 3; col++) {
                    if (((glyph[row] >> (2 - col)) & 1) != 0) {
                        setPx(cx + col, y + row, r, g, b, a);
                    }
                }
            }
            cx += 4;
        }
    }

    private static int[] glyph3x5(char ch) {
        return switch (ch) {
            case 'A' -> new int[]{0b010, 0b101, 0b111, 0b101, 0b101};
            case 'B' -> new int[]{0b110, 0b101, 0b110, 0b101, 0b110};
            case 'C' -> new int[]{0b011, 0b100, 0b100, 0b100, 0b011};
            case 'D' -> new int[]{0b110, 0b101, 0b101, 0b101, 0b110};
            case 'E' -> new int[]{0b111, 0b100, 0b110, 0b100, 0b111};
            case 'F' -> new int[]{0b111, 0b100, 0b110, 0b100, 0b100};
            case 'G' -> new int[]{0b011, 0b100, 0b101, 0b101, 0b011};
            case 'H' -> new int[]{0b101, 0b101, 0b111, 0b101, 0b101};
            case 'I' -> new int[]{0b111, 0b010, 0b010, 0b010, 0b111};
            case 'K' -> new int[]{0b101, 0b101, 0b110, 0b101, 0b101};
            case 'L' -> new int[]{0b100, 0b100, 0b100, 0b100, 0b111};
            case 'M' -> new int[]{0b101, 0b111, 0b111, 0b101, 0b101};
            case 'N' -> new int[]{0b110, 0b101, 0b101, 0b101, 0b101};
            case 'O', '0' -> new int[]{0b111, 0b101, 0b101, 0b101, 0b111};
            case 'P' -> new int[]{0b111, 0b101, 0b111, 0b100, 0b100};
            case 'R' -> new int[]{0b110, 0b101, 0b110, 0b101, 0b101};
            case 'S' -> new int[]{0b011, 0b100, 0b010, 0b001, 0b110};
            case 'T' -> new int[]{0b111, 0b010, 0b010, 0b010, 0b010};
            case 'U' -> new int[]{0b101, 0b101, 0b101, 0b101, 0b111};
            case 'V' -> new int[]{0b101, 0b101, 0b101, 0b101, 0b010};
            case 'W' -> new int[]{0b101, 0b101, 0b111, 0b111, 0b101};
            case 'X' -> new int[]{0b101, 0b101, 0b010, 0b101, 0b101};
            case 'Y' -> new int[]{0b101, 0b101, 0b010, 0b010, 0b010};
            case 'Z' -> new int[]{0b111, 0b001, 0b010, 0b100, 0b111};
            case '/' -> new int[]{0b001, 0b001, 0b010, 0b100, 0b100};
            case '-' -> new int[]{0b000, 0b000, 0b111, 0b000, 0b000};
            case '+' -> new int[]{0b000, 0b010, 0b111, 0b010, 0b000};
            case '!' -> new int[]{0b010, 0b010, 0b010, 0b000, 0b010};
            default -> new int[]{0, 0, 0, 0, 0};
        };
    }

    private static void clearImage() {
        for (int y = 0; y < TEX_H; y++) {
            for (int x = 0; x < TEX_W; x++) {
                ribbonImage.setPixelRGBA(x, y, 0);
            }
        }
    }

    private static void drawCirclePx(int cx, int cy, int r, int cr, int cg, int cb, int a) {
        for (int i = 0; i < 28; i++) {
            float ang = (float) (i * Math.PI * 2.0 / 28.0);
            setPx(Math.round(cx + Mth.cos(ang) * r), Math.round(cy + Mth.sin(ang) * r), cr, cg, cb, a);
        }
    }

    private static void drawThickLinePx(int x0, int y0, int x1, int y1, int r, int g, int b, int a) {
        drawLinePx(x0, y0, x1, y1, r, g, b, a);
        drawLinePx(x0, y0 + 1, x1, y1 + 1, r, g, b, a);
    }

    private static void drawLinePx(int x0, int y0, int x1, int y1, int r, int g, int b, int a) {
        int steps = Math.max(1, Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)));
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            setPx(Math.round(x0 + (x1 - x0) * t), Math.round(y0 + (y1 - y0) * t), r, g, b, a);
        }
    }

    private static void fillRectPx(int x, int y, int w, int h, int r, int g, int b, int a) {
        for (int py = y; py < y + h; py++) {
            for (int px = x; px < x + w; px++) {
                setPx(px, py, r, g, b, a);
            }
        }
    }

    private static void setPx(int x, int y, int r, int g, int b, int a) {
        if (x < 0 || y < 0 || x >= TEX_W || y >= TEX_H) return;
        int abgr = ((Mth.clamp(a, 0, 255) & 0xFF) << 24)
                | ((Mth.clamp(b, 0, 255) & 0xFF) << 16)
                | ((Mth.clamp(g, 0, 255) & 0xFF) << 8)
                | (Mth.clamp(r, 0, 255) & 0xFF);
        ribbonImage.setPixelRGBA(x, y, abgr);
    }

    private static void emitTexturedQuad(VertexConsumer vc, PoseStack.Pose pose, Matrix4f mat,
                                         float x0, float y0, float z0, float x1, float y1, float z1,
                                         float u0, float v0, float u1, float v1,
                                         int r, int g, int b, int a) {
        int full = LightTexture.FULL_BRIGHT;
        vc.addVertex(mat, x0, y0, z0).setColor(r, g, b, a).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(full).setNormal(pose, 0, 0, 1);
        vc.addVertex(mat, x1, y0, z0).setColor(r, g, b, a).setUv(u1, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(full).setNormal(pose, 0, 0, 1);
        vc.addVertex(mat, x1, y1, z1).setColor(r, g, b, a).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(full).setNormal(pose, 0, 0, 1);
        vc.addVertex(mat, x0, y1, z1).setColor(r, g, b, a).setUv(u0, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(full).setNormal(pose, 0, 0, 1);
    }

    private static void emitThinBeam3D(VertexConsumer vc, PoseStack.Pose pose, Matrix4f mat,
                                       float x0, float y0, float z0, float x1, float y1, float z1,
                                       float halfW, int r, int g, int b, int a) {
        float u = 0.875f, v = 0.875f;
        int full = LightTexture.FULL_BRIGHT;
        vc.addVertex(mat, x0 - halfW, y0, z0).setColor(r, g, b, a).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(full).setNormal(pose, 0, 0, 1);
        vc.addVertex(mat, x0 + halfW, y0, z0).setColor(r, g, b, a).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(full).setNormal(pose, 0, 0, 1);
        vc.addVertex(mat, x1 + halfW, y1, z1).setColor(r, g, b, a).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(full).setNormal(pose, 0, 0, 1);
        vc.addVertex(mat, x1 - halfW, y1, z1).setColor(r, g, b, a).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(full).setNormal(pose, 0, 0, 1);
    }
}