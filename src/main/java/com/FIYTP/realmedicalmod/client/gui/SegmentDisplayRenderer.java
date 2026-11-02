package com.FIYTP.realmedicalmod.client.gui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 七段数码管 / 科技 LCD 风格大号数字渲染器
 * 用于心率、血压、体温、血氧等关键生命体征的 3A 级工业视觉呈现
 */
public final class SegmentDisplayRenderer {
    private SegmentDisplayRenderer() {}

    // 标准 7 段显示掩码：a(顶), b(右上), c(右下), d(底), e(左下), f(左上), g(中间)
    // 对应位：bit 0=a, 1=b, 2=c, 3=d, 4=e, 5=f, 6=g
    private static final int[] DIGIT_MASKS = {
            0b0111111, // 0: a,b,c,d,e,f
            0b0000110, // 1: b,c
            0b1011011, // 2: a,b,d,e,g
            0b1001111, // 3: a,b,c,d,g
            0b1100110, // 4: b,c,f,g
            0b1101101, // 5: a,c,d,f,g
            0b1111101, // 6: a,c,d,e,f,g
            0b0000111, // 7: a,b,c
            0b1111111, // 8: a,b,c,d,e,f,g
            0b1101111  // 9: a,b,c,d,f,g
    };

    /**
     * 渲染单字符数码管
     * @param digit 字符 ('0'-'9', '-', '/', ' ')
     * @param segWidth 单笔画粗细（通常 2~3 像素）
     */
    public static void renderSegmentChar(GuiGraphics graphics, char digit, int x, int y, int width, int height, int segWidth, int activeColor, int dimColor) {
        int halfH = height / 2;

        if (digit >= '0' && digit <= '9') {
            int mask = DIGIT_MASKS[digit - '0'];

            // a: 顶横
            drawSegH(graphics, x + segWidth, y, width - segWidth * 2, segWidth, (mask & (1 << 0)) != 0 ? activeColor : dimColor);
            // b: 右上竖
            drawSegV(graphics, x + width - segWidth, y + segWidth, segWidth, halfH - segWidth, (mask & (1 << 1)) != 0 ? activeColor : dimColor);
            // c: 右下竖
            drawSegV(graphics, x + width - segWidth, y + halfH, segWidth, height - halfH - segWidth, (mask & (1 << 2)) != 0 ? activeColor : dimColor);
            // d: 底横
            drawSegH(graphics, x + segWidth, y + height - segWidth, width - segWidth * 2, segWidth, (mask & (1 << 3)) != 0 ? activeColor : dimColor);
            // e: 左下竖
            drawSegV(graphics, x, y + halfH, segWidth, height - halfH - segWidth, (mask & (1 << 4)) != 0 ? activeColor : dimColor);
            // f: 左上竖
            drawSegV(graphics, x, y + segWidth, segWidth, halfH - segWidth, (mask & (1 << 5)) != 0 ? activeColor : dimColor);
            // g: 中横
            drawSegH(graphics, x + segWidth, y + halfH - segWidth / 2, width - segWidth * 2, segWidth, (mask & (1 << 6)) != 0 ? activeColor : dimColor);
        } else if (digit == '-') {
            // 只亮中横
            drawSegH(graphics, x + segWidth, y + halfH - segWidth / 2, width - segWidth * 2, segWidth, activeColor);
        } else if (digit == '/') {
            // 斜线：步进绘制
            for (int i = 0; i < height; i++) {
                int px = x + width - (int) ((float) i / height * width) - segWidth;
                graphics.fill(px, y + i, px + segWidth, y + i + 1, activeColor);
            }
        } else if (digit == '.') {
            // 小数点
            graphics.fill(x + width - segWidth * 2, y + height - segWidth * 2, x + width, y + height, activeColor);
        }
    }

    private static void drawSegH(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        if ((color >>> 24) > 0) {
            graphics.fill(x, y, x + w, y + h, color);
        }
    }

    private static void drawSegV(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        if ((color >>> 24) > 0) {
            graphics.fill(x, y, x + w, y + h, color);
        }
    }

    /**
     * 渲染整串 LCD/七段数码管字符串
     * @return 实际渲染占用的总宽度
     */
    public static int renderString(GuiGraphics graphics, String text, int x, int y, int digitWidth, int digitHeight, int spacing, int segWidth, int activeColor, int dimColor) {
        int curX = x;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int w = (c == '.' || c == ':') ? segWidth * 3 : (c == '/' ? (int)(digitWidth * 0.7f) : digitWidth);
            renderSegmentChar(graphics, c, curX, y, w, digitHeight, segWidth, activeColor, dimColor);
            curX += w + spacing;
        }
        return curX - x;
    }
}
