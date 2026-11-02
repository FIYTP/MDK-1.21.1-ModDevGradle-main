package com.FIYTP.realmedicalmod.client.gui;

import net.minecraft.util.Mth;

/**
 * 医疗面板 UI 科幻动画与缓动函数系统
 * 提供赛博朋克 / 3A 游戏风格的数学缓动、弹性回弹、震荡衰减及周期脉冲
 */
public final class MedicalAnimUtils {

    private MedicalAnimUtils() {}

    /**
     * 三次方平滑出场：快速进入，柔和减速停靠
     */
    public static float easeOutCubic(float x) {
        float f = 1.0f - Mth.clamp(x, 0.0f, 1.0f);
        return 1.0f - f * f * f;
    }

    /**
     * 五次方超平滑减速
     */
    public static float easeOutQuint(float x) {
        float f = 1.0f - Mth.clamp(x, 0.0f, 1.0f);
        return 1.0f - f * f * f * f * f;
    }

    /**
     * 三次方平滑入场（关闭面板时）
     */
    public static float easeInCubic(float x) {
        float c = Mth.clamp(x, 0.0f, 1.0f);
        return c * c * c;
    }

    /**
     * 二次方进出缓动
     */
    public static float easeInOutQuad(float x) {
        float t = Mth.clamp(x, 0.0f, 1.0f);
        return t < 0.5f ? 2.0f * t * t : 1.0f - (float) Math.pow(-2.0f * t + 2.0f, 2.0) / 2.0f;
    }

    /**
     * 弹性回弹缓冲（HUD 卡片展开微越界回弹）
     */
    public static float easeOutBack(float x) {
        float c1 = 1.70158f;
        float c3 = c1 + 1.0f;
        float f = Mth.clamp(x, 0.0f, 1.0f) - 1.0f;
        return 1.0f + c3 * f * f * f + c1 * f * f;
    }

    /**
     * 极度轻微的机械卡扣回弹（c1 = 1.1）
     */
    public static float easeOutSoftBack(float x) {
        float c1 = 1.10f;
        float c3 = c1 + 1.0f;
        float f = Mth.clamp(x, 0.0f, 1.0f) - 1.0f;
        return 1.0f + c3 * f * f * f + c1 * f * f;
    }

    /**
     * 阶梯错开时间片计算 (Staggered Delay)
     * @param totalProgress 全局进度 0~1
     * @param startDelay 开始时间点 0~1
     * @param duration 持续跨度 0~1
     * @return 映射到局部 [0, 1] 的插值进度
     */
    public static float getStaggeredProgress(float totalProgress, float startDelay, float duration) {
        if (totalProgress <= startDelay) return 0.0f;
        if (totalProgress >= startDelay + duration) return 1.0f;
        return (totalProgress - startDelay) / duration;
    }

    /**
     * 平滑插值 (防止帧率波动突变)
     */
    public static float smoothDamp(float current, float target, float speed, float deltaSeconds) {
        if (deltaSeconds <= 0.0f) return current;
        float factor = 1.0f - (float) Math.exp(-speed * deltaSeconds);
        return current + (target - current) * factor;
    }

    /**
     * 呼吸光晕脉冲波 0.0 ~ 1.0
     * @param frequency 频率 Hz (每秒周期数)
     */
    public static float getBreathingPulse(float frequency) {
        double timeSec = System.currentTimeMillis() * 0.001;
        return (float) (Math.sin(timeSec * frequency * Math.PI * 2.0) * 0.5 + 0.5);
    }
}
