package com.FIYTP.realmedicalmod.client.font;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.capability.MedicalDataCapability;
import com.FIYTP.realmedicalmod.config.MedicalConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

import java.util.Random;

/**
 * 脑损伤字体渲染混淆与扭曲效果辅助类
 */
public class BrainDamageFontHelper {
    // 字符替换池
    private static final char[] CHINESE_GLYPHS = new char[]{'█', '▓', '▒', '░', '◊', '◈', '◆', '▚', '▞', '▟', '▜'};
    private static final char[] ASCII_GLYPHS = new char[]{'@', '#', '$', '%', '&', '*'};

    // 性能优化：每 5 帧刷新一次随机种子
    private static long lastFrameCount = -1;
    private static long cachedSeed = 1234567L;
    private static int cachedBrainHealth = 100;
    private static float cachedEffectStrength = 0.0f;

    // 防止在 Mixin 内部递归触发
    public static final ThreadLocal<Boolean> IS_PROCESSING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public static float getEffectStrength(int brainHealth) {
        int threshold = MedicalConfig.BRAIN_DAMAGE_FONT_THRESHOLD.get();
        if (brainHealth >= threshold) return 0.0f;
        if (brainHealth >= 80) return (threshold - brainHealth) * (0.2f / Math.max(1, threshold - 80)); // 0.0 ~ 0.2
        if (brainHealth >= 60) return 0.2f + (80 - brainHealth) * 0.01f;   // 0.2 ~ 0.4
        if (brainHealth >= 40) return 0.4f + (60 - brainHealth) * 0.01f;   // 0.4 ~ 0.6
        if (brainHealth >= 20) return 0.6f + (40 - brainHealth) * 0.01f;   // 0.6 ~ 0.8
        return 0.8f + (20 - brainHealth) * 0.01f;                          // 0.8 ~ 1.0
    }

    /**
     * 更新并返回当前脑损伤效果强度。若脑健康 >= 90 或不在游戏内，返回 0.0f
     */
    public static float updateAndGetEffectStrength() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return 0.0f;
        }

        // 使用 guiTicks 或 tickCount 或 帧数作为时间基准
        long currentFrame = mc.gui != null ? mc.gui.getGuiTicks() : 0;
        if (currentFrame / 5 != lastFrameCount) {
            lastFrameCount = currentFrame / 5;
            cachedSeed = new Random().nextLong();

            try {
                MedicalData data = player.getData(MedicalDataCapability.MEDICAL_DATA);
                if (data != null) {
                    cachedBrainHealth = data.getBrainHealth();
                } else {
                    cachedBrainHealth = 100;
                }
            } catch (Exception e) {
                cachedBrainHealth = 100;
            }
            cachedEffectStrength = getEffectStrength(cachedBrainHealth);
        }

        return cachedEffectStrength;
    }

    public static int getCachedBrainHealth() {
        return cachedBrainHealth;
    }

    /**
     * 混淆单字符
     */
    public static int obfuscateCodePoint(int codePoint, int charIndex, int brainHealth, long seed) {
        if (Character.isWhitespace(codePoint) || Character.isISOControl(codePoint)) {
            return codePoint;
        }

        // 阈值分级替换规则：
        // 80~90: 每 15 字符替换 1 个
        // 60~80: 每 5 字符替换 1 个
        // 40~60: 每 2 字符替换 1 个
        // 20~40: 全部替换
        // 0~20: 全部替换
        boolean shouldReplace = false;
        long hash = (seed ^ ((long) charIndex * 31234567L) ^ ((long) codePoint * 1000003L));
        int randVal = (int) Math.abs(hash % 1000);

        if (brainHealth < 40) {
            shouldReplace = true;
        } else if (brainHealth < 60) {
            // 每 2 字符替换 1 个
            shouldReplace = (charIndex % 2 == 0) || (randVal < 500);
        } else if (brainHealth < 80) {
            // 每 5 字符替换 1 个
            shouldReplace = (charIndex % 5 == 0) || (randVal < 200);
        } else if (brainHealth < 90) {
            // 每 15 字符替换 1 个
            shouldReplace = (charIndex % 15 == 0) || (randVal < 66);
        }

        if (!shouldReplace) {
            return codePoint;
        }

        // 中文（CJK等高位字符）用方块符号替换，英文/ASCII 用 ASCII 符号替换
        if (codePoint > 127) {
            int idx = Math.abs((int) (hash % CHINESE_GLYPHS.length));
            return CHINESE_GLYPHS[idx];
        } else {
            int idx = Math.abs((int) (hash % ASCII_GLYPHS.length));
            return ASCII_GLYPHS[idx];
        }
    }

    /**
     * 对 String 文本进行脑损伤混淆
     */
    public static String obfuscateString(String text) {
        float strength = updateAndGetEffectStrength();
        if (strength <= 0.0f || text == null || text.isEmpty()) {
            return text;
        }

        int brainHealth = cachedBrainHealth;
        long seed = cachedSeed;

        StringBuilder sb = new StringBuilder(text.length());
        int charIndex = 0;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            int newCodePoint = obfuscateCodePoint(codePoint, charIndex++, brainHealth, seed);
            sb.appendCodePoint(newCodePoint);
            i += Character.charCount(codePoint);
        }

        return sb.toString();
    }

    /**
     * 包装 FormattedCharSequence，进行字符混淆
     */
    public static FormattedCharSequence obfuscateCharSequence(FormattedCharSequence text) {
        float strength = updateAndGetEffectStrength();
        if (strength <= 0.0f || text == null) {
            return text;
        }

        int brainHealth = cachedBrainHealth;
        long seed = cachedSeed;

        return output -> {
            int[] charCounter = new int[]{0};
            return text.accept((index, style, codePoint) -> {
                int newCp = obfuscateCodePoint(codePoint, charCounter[0]++, brainHealth, seed);
                return output.accept(index, style, newCp);
            });
        };
    }

    /**
     * 计算并应用 Y 轴位移（已移除晃动，直接返回原 Y）
     */
    public static float computeYOffset(float originalY, int brainHealth, float strength) {
        return originalY;
    }

    /**
     * 对矩阵进行微倾斜/旋转与散开（已移除旋转晃动，避免视觉恶心）
     */
    public static void applyMatrixDistortion(Matrix4f matrix, int brainHealth, float strength) {
        // 已移除旋转与扭曲
    }

    /**
     * 针对颜色与透明度进行扰动（中度透明度波动、重度颜色漂移）
     */
    public static int computeColorPerturbation(int color, int brainHealth, float strength) {
        if (strength <= 0.0f || brainHealth >= 80) {
            return color;
        }

        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        if (a == 0) {
            a = 0xFF;
        }

        long time = System.currentTimeMillis();

        if (brainHealth >= 60) {
            // 60~80: 透明度波动 (保持在 0.55 ~ 1.0 之间)
            float alphaWave = 0.75f + 0.25f * (float) Math.sin(time * 0.008);
            a = (int) (a * alphaWave);
        } else if (brainHealth >= 40) {
            // 40~60: 颜色漂移 + 透明度轻微波动
            float drift = (float) Math.sin(time * 0.006);
            r = Mth.clamp((int) (r * (1.0f + 0.25f * drift)), 0, 255);
            g = Mth.clamp((int) (g * (1.0f - 0.2f * drift)), 0, 255);
            b = Mth.clamp((int) (b * (1.0f + 0.15f * drift)), 0, 255);
        } else {
            // < 40: 剧烈杂色与暗淡漂移
            float drift = (float) Math.sin(time * 0.01);
            r = Mth.clamp((int) (r * (0.8f + 0.35f * drift)), 0, 255);
            g = Mth.clamp((int) (g * (0.6f + 0.4f * Math.cos(time * 0.009))), 0, 255);
            b = Mth.clamp((int) (b * (0.7f + 0.35f * Math.sin(time * 0.011))), 0, 255);
            float alphaWave = 0.7f + 0.3f * (float) Math.sin(time * 0.012);
            a = (int) (a * alphaWave);
        }

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * 是否需要绘制重影（重度 40~60）
     */
    public static boolean shouldRenderGhostShadow(int brainHealth) {
        return brainHealth < 60 && brainHealth >= 20;
    }
}
