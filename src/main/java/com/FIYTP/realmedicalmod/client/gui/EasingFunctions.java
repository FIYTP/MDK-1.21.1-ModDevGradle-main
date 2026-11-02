package com.FIYTP.realmedicalmod.client.gui;

/**
 * 常用科幻 UI 缓动曲线函数
 * 输入 t ∈ [0, 1]，输出插值比例
 */
public final class EasingFunctions {
    private EasingFunctions() {}

    public static float linear(float t) {
        return Math.max(0.0f, Math.min(1.0f, t));
    }

    public static float easeOutCubic(float t) {
        t = Math.max(0.0f, Math.min(1.0f, t));
        float f = 1.0f - t;
        return 1.0f - f * f * f;
    }

    public static float easeInCubic(float t) {
        t = Math.max(0.0f, Math.min(1.0f, t));
        return t * t * t;
    }

    public static float easeInOutCubic(float t) {
        t = Math.max(0.0f, Math.min(1.0f, t));
        return t < 0.5f ? 4.0f * t * t * t : 1.0f - (float) Math.pow(-2.0f * t + 2.0f, 3) / 2.0f;
    }

    public static float easeOutQuad(float t) {
        t = Math.max(0.0f, Math.min(1.0f, t));
        return 1.0f - (1.0f - t) * (1.0f - t);
    }

    public static float easeOutQuint(float t) {
        t = Math.max(0.0f, Math.min(1.0f, t));
        float f = 1.0f - t;
        return 1.0f - f * f * f * f * f;
    }

    /**
     * 3A 经典回弹缓动（面板打开时轻微回弹）
     */
    public static float easeOutBack(float t) {
        t = Math.max(0.0f, Math.min(1.0f, t));
        float c1 = 1.70158f;
        float c3 = c1 + 1.0f;
        float f = t - 1.0f;
        return 1.0f + c3 * f * f * f + c1 * f * f;
    }

    public static float easeInBack(float t) {
        t = Math.max(0.0f, Math.min(1.0f, t));
        float c1 = 1.70158f;
        float c3 = c1 + 1.0f;
        return c3 * t * t * t - c1 * t * t;
    }
}
