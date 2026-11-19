package com.FIYTP.realmedicalmod.client.gui;

/**
 * 3A 科技风 HUD 配色面板与颜色工具类
 */
public final class HudColors {
    private HudColors() {}

    // ============================================================
    // 主题色
    // ============================================================
    public static final int CYAN = 0xFF00E5FF;
    public static final int TEAL = 0xFF00ADB5;
    public static final int DARK_BG = 0xFF0B131C;
    public static final int DARK_BG_DEEP = 0xFF060B10;
    public static final int DARK_BG_CARD = 0xE60D1824;

    // ============================================================
    // 状态警示色
    // ============================================================
    public static final int NORMAL = 0xFF00E676;
    public static final int WARNING = 0xFFFFD600;
    public static final int CAUTION_ORANGE = 0xFFFF9100;
    public static final int DANGER = 0xFFFF1744;
    public static final int CRITICAL_PURPLE = 0xFFD500F9;

    // ============================================================
    // 文字颜色
    // ============================================================
    public static final int TEXT_PRIMARY = 0xFFE0F7FA;
    public static final int TEXT_SECONDARY = 0xFF90A4AE;
    public static final int TEXT_MUTED = 0xFF546E7A;
    public static final int TEXT_CYAN = 0xFF80DEEA;

    // ============================================================
    // 边框与装饰
    // ============================================================
    public static final int BORDER_CYAN = 0xAA00E5FF;
    public static final int BORDER_DIM = 0x3300ADB5;
    public static final int SCAN_LINE = 0x2200E5FF;

    // ============================================================
    // 新增别名常量（供 BrainScan3DRenderer / MedicalScreen 使用）
    // 与上面已有常量语义对应，避免修改其他文件
    // ============================================================
    /** 霓虹青（= CYAN） */
    public static final int COLOR_NEON_CYAN = CYAN;
    /** 警报红（= DANGER） */
    public static final int COLOR_ALERT_RED = DANGER;
    /** 警告黄（= WARNING） */
    public static final int COLOR_WARNING_YELLOW = WARNING;
    /** 安全绿（= NORMAL） */
    public static final int COLOR_SAFE_GREEN = NORMAL;
    /** 全息蓝 */
    public static final int COLOR_HOLOGRAM_BLUE = 0xFF40C4FF;
    /** 标签文字（= TEXT_SECONDARY） */
    public static final int TEXT_LABEL = TEXT_SECONDARY;

    // ============================================================
    // 生命体征专用色（右侧 Renderer 使用）
    // ============================================================
    /** 血压 — 橙红 */
    public static final int VITAL_BLOOD_PRESSURE = 0xFFFF6E40;
    /** 疼痛 — 红 */
    public static final int VITAL_PAIN = 0xFFFF5252;
    /** 血氧 — 蓝 */
    public static final int VITAL_OXYGEN = 0xFF40C4FF;
    /** 心跳 — 深红 */
    public static final int VITAL_HEART_BEAT = 0xFFFF1744;

    // ============================================================
    // 肢体状态色（供 MedicalScreen 的肢体着色使用）
    // ============================================================
    /** 健康肢体 — 青绿 */
    public static final int LIMB_HEALTHY         = 0xFF00E676;
    /** 轻微伤 — 黄 */
    public static final int LIMB_MINOR_INJURY    = 0xFFFFD600;
    /** 中度伤 — 橙 */
    public static final int LIMB_MODERATE_INJURY = 0xFFFF9100;
    /** 重伤 — 红 */
    public static final int LIMB_CRITICAL        = 0xFFFF1744;
    /** 截肢 — 灰 */
    public static final int LIMB_AMPUTATED       = 0xFF546E7A;

    // ============================================================
    // 颜色工具方法
    // ============================================================

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

    /**
     * 按健康比例取色（供 BrainScan3DRenderer / MedicalScreen 使用）
     * @param ratio 0.0 ~ 1.0
     * @return ratio > 0.7 绿，> 0.4 黄，否则红
     */
    public static int getHealthColor(float ratio) {
        if (ratio > 0.7f) return NORMAL;
        if (ratio > 0.4f) return WARNING;
        return DANGER;
    }
}
