package com.FIYTP.realmedicalmod.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;

/**
 * 3A 科技风 HUD 渲染工具套件
 * 提供：科幻切角边框、动态角标、发光网格、周期扫描线、发光文本、发光线条、环形与条形进度条、
 * 机械滚轮数字仪表、微观断层探查视窗、战伤临界全息毛刺故障、CT激光切面扫描波、全息锁定十字光标
 */
public final class HudRenderHelper {
    private HudRenderHelper() {}

    /**
     * 绘制科幻切角边框卡片底板
     * @param cornerCut 切角大小（像素）
     * @param bgColor 底色（包含 Alpha）
     * @param borderColor 边框颜色
     */
    public static void drawSciFiCard(GuiGraphics graphics, int x, int y, int width, int height, int cornerCut, int bgColor, int borderColor) {
        // 主背景
        graphics.fill(x + cornerCut, y, x + width - cornerCut, y + height, bgColor);
        graphics.fill(x, y + cornerCut, x + cornerCut, y + height - cornerCut, bgColor);
        graphics.fill(x + width - cornerCut, y + cornerCut, x + width, y + height - cornerCut, bgColor);

        // 边框 - 顶底
        graphics.fill(x + cornerCut, y, x + width - cornerCut, y + 1, borderColor);
        graphics.fill(x + cornerCut, y + height - 1, x + width - cornerCut, y + height, borderColor);
        // 边框 - 左右
        graphics.fill(x, y + cornerCut, x + 1, y + height - cornerCut, borderColor);
        graphics.fill(x + width - 1, y + cornerCut, x + width, y + height - cornerCut, borderColor);

        // 4个切角微对角线（步进填充）
        for (int i = 0; i < cornerCut; i++) {
            graphics.fill(x + cornerCut - i - 1, y + i, x + cornerCut - i, y + i + 1, borderColor);
            graphics.fill(x + width - cornerCut + i, y + i, x + width - cornerCut + i + 1, y + i + 1, borderColor);
            graphics.fill(x + cornerCut - i - 1, y + height - 1 - i, x + cornerCut - i, y + height - i, borderColor);
            graphics.fill(x + width - cornerCut + i, y + height - 1 - i, x + width - cornerCut + i + 1, y + height - i, borderColor);
        }
    }

    /**
     * 绘制卡片四角科幻动态角标（带呼吸光效）
     */
    public static void drawCardCorners(GuiGraphics graphics, int x, int y, int width, int height, int cornerLen, int color) {
        // 左上
        graphics.fill(x, y, x + cornerLen, y + 2, color);
        graphics.fill(x, y, x + 2, y + cornerLen, color);
        // 右上
        graphics.fill(x + width - cornerLen, y, x + width, y + 2, color);
        graphics.fill(x + width - 2, y, x + width, y + cornerLen, color);
        // 左下
        graphics.fill(x, y + height - 2, x + cornerLen, y + height, color);
        graphics.fill(x, y + height - cornerLen, x + 2, y + height, color);
        // 右下
        graphics.fill(x + width - cornerLen, y + height - 2, x + width, y + height, color);
        graphics.fill(x + width - 2, y + height - cornerLen, x + width, y + height, color);
    }

    /**
     * 绘制双层发光文本（底层弥散光晕 + 顶层亮字）
     */
    public static void drawGlowingString(GuiGraphics graphics, Font font, String text, int x, int y, int mainColor, int glowColor) {
        int glowAlpha = ((glowColor >> 24) & 0xFF) / 3;
        int dimGlow = (glowAlpha << 24) | (glowColor & 0x00FFFFFF);

        // 4 向微弱光晕
        graphics.drawString(font, text, x - 1, y, dimGlow, false);
        graphics.drawString(font, text, x + 1, y, dimGlow, false);
        graphics.drawString(font, text, x, y - 1, dimGlow, false);
        graphics.drawString(font, text, x, y + 1, dimGlow, false);

        // 主体字
        graphics.drawString(font, text, x, y, mainColor, false);
    }

    /**
     * 居中双层发光文本
     */
    public static void drawCenteredGlowingString(GuiGraphics graphics, Font font, String text, int centerX, int y, int mainColor, int glowColor) {
        int w = font.width(text);
        drawGlowingString(graphics, font, text, centerX - w / 2, y, mainColor, glowColor);
    }

    /**
     * 绘制科幻能量进度条（带底槽、渐变填充、流光前缘与切角）
     */
    public static void drawSciFiProgressBar(GuiGraphics graphics, int x, int y, int width, int height, float progress, int barColor, int trackBgColor) {
        progress = Math.max(0.0f, Math.min(1.0f, progress));

        // 底槽
        graphics.fill(x, y, x + width, y + height, trackBgColor);
        graphics.fill(x, y, x + width, y + 1, 0x3300ADB5);
        graphics.fill(x, y + height - 1, x + width, y + height, 0x3300ADB5);

        int fillW = (int) (width * progress);
        if (fillW > 0) {
            // 主填充
            graphics.fill(x, y, x + fillW, y + height, barColor);

            // 前缘高亮脉冲光头
            int headW = Math.min(4, fillW);
            int headColor = HudColors.withAlpha(0xFFFFFFFF, 0.8f);
            graphics.fill(x + fillW - headW, y, x + fillW, y + height, headColor);
        }
    }

    /**
     * 绘制全屏背景科技网格与周期扫描激光
     */
    public static void drawBackgroundGridAndScanline(GuiGraphics graphics, int screenWidth, int screenHeight, long timeMs) {
        // 1. 深色微透明渐变底色
        graphics.fillGradient(0, 0, screenWidth, screenHeight, 0xD0060B10, 0xEA0B131C);

        // 2. 细密科技网格 (每 32 像素一条淡青线)
        int gridSpacing = 32;
        int gridColor = 0x0D00E5FF; // ~5% 透明度，非常优雅细腻不刺眼

        for (int gx = 0; gx < screenWidth; gx += gridSpacing) {
            graphics.fill(gx, 0, gx + 1, screenHeight, gridColor);
        }
        for (int gy = 0; gy < screenHeight; gy += gridSpacing) {
            graphics.fill(0, gy, screenWidth, gy + 1, gridColor);
        }

        // 3. 周期性全局自上而下全息扫描激光束（周期约 3.5 秒）
        float scanPeriod = 3500.0f;
        float scanProgress = (timeMs % (long) scanPeriod) / scanPeriod;
        int scanY = (int) (scanProgress * (screenHeight + 40)) - 20;

        if (scanY >= -20 && scanY <= screenHeight) {
            // 扫描激光束核心亮线
            graphics.fill(0, scanY, screenWidth, scanY + 1, 0x4400E5FF);
            // 扫描光束向下拖尾羽化渐变
            graphics.fillGradient(0, scanY - 12, screenWidth, scanY, 0x0000E5FF, 0x2200E5FF);
        }
    }

    /**
     * 绘制科幻切角边框（仅描边，不填充底色）
     * 用于肢体方块外框、ECG 区域外框等
     */
    public static void drawSciFiBorder(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        int cut = 3; // 切角大小

        // 上/下边
        graphics.fill(x + cut, y, x + width - cut, y + 1, color);
        graphics.fill(x + cut, y + height - 1, x + width - cut, y + height, color);
        // 左/右边
        graphics.fill(x, y + cut, x + 1, y + height - cut, color);
        graphics.fill(x + width - 1, y + cut, x + width, y + height - cut, color);

        // 四个切角斜线
        for (int i = 0; i < cut; i++) {
            graphics.fill(x + cut - i - 1, y + i, x + cut - i, y + i + 1, color);
            graphics.fill(x + width - cut + i, y + i, x + width - cut + i + 1, y + i + 1, color);
            graphics.fill(x + cut - i - 1, y + height - 1 - i, x + cut - i, y + height - i, color);
            graphics.fill(x + width - cut + i, y + height - 1 - i, x + width - cut + i + 1, y + height - i, color);
        }
    }

    /**
     * 绘制发光对角切角引导折线 (从起点 (x1, y1) 经切角折弯到 (x2, y2))
     * 用于战术检伤分类 / 肢体引线
     */
    public static void drawSciFiLeaderLine(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color, int glowColor) {
        int midX = x1 + (x2 - x1) / 2;
        // 先水平后垂直或先垂直后水平折角
        // 绘制主线与弥散光
        drawLine(graphics, x1, y1, midX, y1, glowColor, 2);
        drawLine(graphics, midX, y1, x2, y2, glowColor, 2);
        drawLine(graphics, x1, y1, midX, y1, color, 1);
        drawLine(graphics, midX, y1, x2, y2, color, 1);

        // 起点微型菱形端点
        drawDiamond(graphics, x1, y1, 2, color);
        // 终点光点
        drawDiamond(graphics, x2, y2, 3, color);
    }

    /**
     * 绘制简易端点菱形
     */
    public static void drawDiamond(GuiGraphics graphics, int cx, int cy, int size, int color) {
        for (int i = -size; i <= size; i++) {
            int span = size - Math.abs(i);
            graphics.fill(cx - span, cy + i, cx + span + 1, cy + i + 1, color);
        }
    }

    /**
     * 绘制带粗细的直线 (基于 Bresenham 或轴对齐优化)
     */
    public static void drawLine(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color, int thickness) {
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1;
        int sy = y1 < y2 ? 1 : -1;
        int err = dx - dy;

        int currX = x1;
        int currY = y1;
        int half = thickness / 2;

        while (true) {
            if (thickness <= 1) {
                graphics.fill(currX, currY, currX + 1, currY + 1, color);
            } else {
                graphics.fill(currX - half, currY - half, currX + half + 1, currY + half + 1, color);
            }

            if (currX == x2 && currY == y2) break;
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                currX += sx;
            }
            if (e2 < dx) {
                err += dx;
                currY += sy;
            }
        }
    }

    /**
     * 绘制 3A 战术检伤警戒浮标卡片 (带左侧危险警示条与动态流动光边)
     */
    public static void drawTacticalBadge(GuiGraphics graphics, Font font, int x, int y, int width, int height, String title, String subtitle, int accentColor, int bgColor) {
        // 卡片底板
        drawSciFiCard(graphics, x, y, width, height, 3, bgColor, HudColors.withAlpha(accentColor, 0.6f));
        // 左侧重度危险着重色条
        graphics.fill(x, y + 2, x + 3, y + height - 2, accentColor);
        // 右上动态切角标
        graphics.fill(x + width - 6, y, x + width, y + 1, accentColor);
        graphics.fill(x + width - 1, y, x + width, y + 6, accentColor);

        // 文字
        graphics.drawString(font, title, x + 7, y + 3, accentColor, false);
        if (subtitle != null && !subtitle.isEmpty()) {
            graphics.drawString(font, subtitle, x + 7, y + 13, 0xAAFFFFFF, false);
        }
    }

    /**
     * 绘制全息能量光子微尘 (根据时间漂移)
     */
    public static void drawAmbientPhotonDust(GuiGraphics graphics, int screenWidth, int screenHeight, long timeMs) {
        float timeSec = timeMs * 0.001f;
        int numParticles = 24;
        for (int i = 0; i < numParticles; i++) {
            // 伪随机确定性轨道
            float speed = 15.0f + (i * 7) % 20;
            float px = ((i * 79 + timeSec * speed) % (screenWidth + 20)) - 10;
            float py = ((i * 137 + (float) Math.sin(timeSec * 0.8f + i) * 30.0f + screenHeight) % screenHeight);

            float alphaPulse = 0.2f + 0.3f * (float) Math.sin(timeSec * 2.0f + i * 1.5f);
            int pColor = HudColors.withAlpha(0x00E5FF, Math.max(0.05f, alphaPulse));

            graphics.fill((int) px, (int) py, (int) px + 2, (int) py + 2, pColor);
            graphics.fill((int) px - 1, (int) py, (int) px + 3, (int) py + 1, HudColors.withAlpha(0x00E5FF, alphaPulse * 0.4f));
        }
    }

    /**
     * 机械翻滚数字仪 (Odometer Rolling Digit)
     * 模拟航空仪表与机械滚轮表，平滑上下滚动插值过渡
     */
    public static void drawRollingNumber(GuiGraphics g, Font font, float currentVal, int x, int y, int color) {
        int intPart = (int) currentVal;
        float frac = currentVal - intPart;
        String valStr = String.valueOf(intPart);
        int len = valStr.length();

        int digitW = 7;
        int charH = 10;
        int totalW = len * digitW;

        g.enableScissor(x, y - 1, x + totalW + 2, y + charH + 1);

        for (int i = 0; i < len; i++) {
            char c = valStr.charAt(i);
            int d = c - '0';
            int dx = x + i * digitW;

            // 个位数或根据位权赋予滚动偏移
            float digitOffset = 0.0f;
            if (i == len - 1) {
                digitOffset = frac;
            } else if (i == len - 2 && (valStr.charAt(len - 1) == '9')) {
                digitOffset = frac;
            }

            int curY = y - (int) (digitOffset * charH);
            int nextY = curY + charH;
            int nextD = (d + 1) % 10;

            g.drawString(font, String.valueOf(d), dx, curY, color, false);
            if (digitOffset > 0.01f) {
                g.drawString(font, String.valueOf(nextD), dx, nextY, color, false);
            }
        }

        g.disableScissor();
    }

    /**
     * 绘制处置引导全息锁定十字光标 (Tactical Surgical Reticle)
     * 旋转准星圆环、0°~360° 发光进度弧、四角收紧呼吸准星与菱形微爆
     */
    public static void drawTacticalSurgicalReticle(GuiGraphics g, int cx, int cy, float progress, long now, int accentColor) {
        float spin = (now % 2500) / 2500f * 360f;
        int baseR = 18;

        // 旋转外导轨 (三段断续圆弧)
        drawSmoothArcStatic(g, cx, cy, baseR + 3, spin, spin + 50, 0x4400E5FF, 1);
        drawSmoothArcStatic(g, cx, cy, baseR + 3, spin + 120, spin + 170, 0x4400E5FF, 1);
        drawSmoothArcStatic(g, cx, cy, baseR + 3, spin + 240, spin + 290, 0x4400E5FF, 1);

        // 0°~360° 发光治疗进度弧 (从 -90° 顶部顺时针闭合)
        if (progress > 0.01f) {
            float sweep = Math.min(360f, progress * 360f);
            drawSmoothArcStatic(g, cx, cy, baseR, -90f, -90f + sweep, accentColor, 2);
            drawSmoothArcStatic(g, cx, cy, baseR - 1, -90f, -90f + sweep, HudColors.withAlpha(accentColor, 0.4f), 1);
        }

        // 四角收紧向内准星 (Breath / Locking)
        float breath = 0.5f + 0.5f * (float) Math.sin(now / 150.0);
        int bracketDist = (int) (baseR + 6 - breath * 2);
        // 上
        g.fill(cx - 3, cy - bracketDist, cx + 4, cy - bracketDist + 1, accentColor);
        g.fill(cx, cy - bracketDist, cx + 1, cy - bracketDist + 4, accentColor);
        // 下
        g.fill(cx - 3, cy + bracketDist, cx + 4, cy + bracketDist + 1, accentColor);
        g.fill(cx, cy + bracketDist - 3, cx + 1, cy + bracketDist + 1, accentColor);
        // 左
        g.fill(cx - bracketDist, cy - 3, cx - bracketDist + 1, cy + 4, accentColor);
        g.fill(cx - bracketDist, cy, cx - bracketDist + 4, cy + 1, accentColor);
        // 右
        g.fill(cx + bracketDist, cy - 3, cx + bracketDist + 1, cy + 4, accentColor);
        g.fill(cx + bracketDist - 3, cy, cx + bracketDist + 1, cy + 1, accentColor);

        // 中心聚焦点
        drawDiamond(g, cx, cy, 2, accentColor);
    }

    /**
     * 静态平滑圆弧绘制 (供 Reticle 使用)
     */
    public static void drawSmoothArcStatic(GuiGraphics g, int cx, int cy, int r, float a0, float a1, int color, int thickness) {
        if (a1 <= a0) return;
        float step = 2.5f;
        int half = thickness / 2;
        int d = thickness % 2 == 0 ? half : half + 1;
        for (float a = a0; a <= a1; a += step) {
            double rad = Math.toRadians(a);
            int px = cx + (int) Math.round(Math.cos(rad) * r);
            int py = cy + (int) Math.round(Math.sin(rad) * r);
            g.fill(px - half, py - half, px + d, py + d, color);
        }
    }
}
