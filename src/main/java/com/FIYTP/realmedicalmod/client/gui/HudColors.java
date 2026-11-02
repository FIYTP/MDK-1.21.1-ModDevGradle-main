package com.FIYTP.realmedicalmod.client.gui;

/**
 * 3A 科技风 HUD 配色面板与颜色工具类
 * 统一色板：
 * - 主色：青色 #00E5FF / 蓝绿 #00ADB5
 * - 辅色：深色背景 #0B131C / #060B10
 * - 危险色：红 #FF1744 / 橙 #FF9100
 * - 正常色：青绿 #00E676
 * - 警告色：黄色 #FFD600
 * - 文字主色：0xFFE0F7FA (极浅青白)
 * - 次要文字：0xFF90A4AE (灰蓝)
 * - 装饰线暗色：0x3300ADB5
 */
public final class HudColors {
    private HudColors() {}

    // 主题色
    public static final int CYAN = 0xFF00E5FF;
    public static final int TEAL = 0xFF00ADB5;
    public static final int DARK_BG = 0xFF0B131C;
    public static final int DARK_BG_DEEP = 0xFF060B10;
    public static final int DARK_BG_CARD = 0xE60D1824; // 90% 不透明卡片底色

    // 状态警示色
    public static final int NORMAL = 0xFF00E676;
    public static final int WARNING = 0xFFFFD600;
    public static final int CAUTION_ORANGE = 0xFFFF9100;
    public static final int DANGER = 0xFFFF1744;
    public static final int CRITICAL_PURPLE = 0xFFD500F9;

    // 文字颜色
    public static final int TEXT_PRIMARY = 0xFFE0F7FA;
    public static final int TEXT_SECONDARY = 0xFF90A4AE;
    public static final int TEXT_MUTED = 0xFF546E7A;
    public static final int TEXT_CYAN = 0xFF80DEEA;

    // 边框与装饰
    public static final int BORDER_CYAN = 0xAA00E5FF;
    public static final int BORDER_DIM = 0x3300ADB5;
    public static final int SCAN_LINE = 0x2200E5FF;

    /**
     * 线性插值颜色（支持 Alpha）
     */
    public static int lerpColor(float t, int colorA, int colorB) {
        if (t <= 0.0f) return colorA;
        if (t >= 1.0f) return colorB;

        int aA = (colorA >> 24) & 0xFF;
        int rA = (colorA >> 16) & 0xFF;
        int gA = (colorA >> 8) & 0xFF;
        int bA = colorA & 0xFF;

        int aB = (colorB >> 24) & 0xFF;
        int rB = (colorB >> 16) & 0xFF;
        int gB = (colorB >> 8) & 0xFF;
        int bB = colorB & 0xFF;

        int a = (int) (aA + (aB - aA) * t);
        int r = (int) (rA + (rB - rA) * t);
        int g = (int) (gA + (gB - gA) * t);
        int b = (int) (bA + (bB - bA) * t);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * 设置或乘算 Alpha (0.0f ~ 1.0f)
     */
    public static int withAlpha(int color, float alphaFactor) {
        int a = (int) (((color >> 24) & 0xFF) * alphaFactor);
        a = Math.max(0, Math.min(255, a));
        return (a << 24) | (color & 0x00FFFFFF);
    }

    /**
     * 直接指定 Alpha 分量 (0 ~ 255)
     */
    public static int withAlphaInt(int color, int alpha) {
        alpha = Math.max(0, Math.min(255, alpha));
        return (alpha << 24) | (color & 0x00FFFFFF);
    }
}
