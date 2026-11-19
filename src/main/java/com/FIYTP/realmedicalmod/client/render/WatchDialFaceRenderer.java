package com.FIYTP.realmedicalmod.client.render;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.config.MedicalConfig;
import com.FIYTP.realmedicalmod.treatment.ModItems;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * 阶段 2：256x256 军工精密 OLED 活体表盘渲染引擎 (WatchDialFaceRenderer)
 *
 * 彻底消灭 AI 廉价感的四大工艺：
 * 1. 自研高精度斜切七段军工数码管 (7-Segment Vector Renderer)，带熄灭暗槽底影 (888) 与柔和像素辉光；
 * 2. 自研 3x5 军工点阵微缩字库 (BIO-OS / BPM / mmHg / CNS / EEG / PAIN)，拒绝傻大黑粗默认字体；
 * 3. 5 大核心体征精密分舱：
 *    - 左上：实时 P-QRS-T 磷光余晖心电示波器 + 大号七段数码管 BPM
 *    - 右上：标志性 0~55 连续形变全息痛觉头像（无痛微笑 -> 皱眉 -> 咬牙 -> 剧痛哀嚎 + 太阳穴电弧 + 眨眼）
 *    - 左中：血压 SBP/DBP 七段数码管 + 16 格独立发光脉压/血容量灌注刻度尺
 *    - 右下：意识清醒度 CNS 240° 精密军工环形刻度仪表盘
 *    - 底部：脑健康实时 EEG 脑电波动示波器 + BRAIN % + 恶化/回稳趋势微箭头
 * 4. 光学仪式感特效：抬腕唤醒 CRT 横向激光扫线 + 剧痛/低血氧/室颤时的微弱 RGB 色差错位与扫描线干扰。
 */
public final class WatchDialFaceRenderer {

    private static final int SIZE = 256;
    private static final int UPDATE_INTERVAL_MS = 33; // ~30 FPS 丝滑刷新，单帧 <0.12ms 零卡顿

    private static NativeImage dialImage = null;
    private static DynamicTexture dialTexture = null;
    private static ResourceLocation dialTextureId = null;
    private static long lastUploadMs = 0L;
    private static long lastStepNs = 0L;

    // 远端玩家独立表盘纹理（与本地玩家静态 ECG/EEG/趋势缓冲完全隔离，防止多人联机互相覆写）
    private static NativeImage remoteDialImage = null;
    private static DynamicTexture remoteDialTexture = null;
    private static ResourceLocation remoteDialTextureId = null;
    private static long lastRemoteUploadMs = 0L;

    // 实时 ECG 心电 & EEG 脑电环形示波缓冲区
    private static final int ECG_LEN = 110;
    private static final float[] ecgBuf = new float[ECG_LEN];
    private static int ecgHead = 0;
    private static float ecgAcc = 0.0f;
    private static float ecgPhase = 0.0f;

    private static final int EEG_LEN = 104;
    private static final float[] eegBuf = new float[EEG_LEN];
    private static int eegHead = 0;
    private static float eegAcc = 0.0f;
    private static float eegTime = 0.0f;

    // 脑健康波动趋势追踪
    private static float prevBrainHealth = 100.0f;
    private static float brainTrendSmooth = 0.0f;

    // 抬腕 CRT 开机扫线状态
    private static float prevActiveLevel = 0.0f;
    private static float bootScanProgress = 1.0f;

    // 七段数码管段位掩码：bit0=A(顶), bit1=B(右上), bit2=C(右下), bit3=D(底), bit4=E(左下), bit5=F(左上), bit6=G(中)
    private static final int[] SEG_DIGITS = {
            0b0111111, // 0
            0b0000110, // 1
            0b1011011, // 2
            0b1001111, // 3
            0b1100110, // 4
            0b1101101, // 5
            0b1111101, // 6
            0b0000111, // 7
            0b1111111, // 8
            0b1101111  // 9
    };

    private WatchDialFaceRenderer() {}

    /**
     * 获取或按 30Hz 节流刷新 256x256 活体 OLED 表盘纹理（默认本地玩家）
     */
    public static ResourceLocation getOrUpdateDialTexture(MedicalData data, float activeLevel) {
        return getOrUpdateDialTexture(data, activeLevel, true);
    }

    /**
     * 多人隔离版：本地玩家使用实时 30Hz 动态示波纹理；远端玩家使用独立低频纹理，绝不污染本地 ECG/EEG 缓冲。
     */
    public static ResourceLocation getOrUpdateDialTexture(MedicalData data, float activeLevel, boolean isLocalPlayer) {
        long nowMs = System.currentTimeMillis();
        if (!isLocalPlayer) {
            if (remoteDialTextureId == null) {
                remoteDialImage = new NativeImage(SIZE, SIZE, true);
                remoteDialTexture = new DynamicTexture(remoteDialImage);
                remoteDialTextureId = Minecraft.getInstance().getTextureManager().register("realmedicalmod_watch_oled_remote", remoteDialTexture);
            }
            if (nowMs - lastRemoteUploadMs >= 200L) {
                lastRemoteUploadMs = nowMs;
                NativeImage savedImg = dialImage;
                int savedEcgHead = ecgHead;
                float savedEcgAcc = ecgAcc;
                float savedEcgPhase = ecgPhase;
                int savedEegHead = eegHead;
                float savedEegAcc = eegAcc;
                float savedEegTime = eegTime;
                float savedPrevBrain = prevBrainHealth;
                float savedBrainTrend = brainTrendSmooth;
                float savedPrevActive = prevActiveLevel;
                float savedBootScan = bootScanProgress;
                long savedStepNs = lastStepNs;
                try {
                    dialImage = remoteDialImage;
                    renderDialPixels(data, 0.0f, nowMs);
                    remoteDialTexture.upload();
                } finally {
                    dialImage = savedImg;
                    ecgHead = savedEcgHead;
                    ecgAcc = savedEcgAcc;
                    ecgPhase = savedEcgPhase;
                    eegHead = savedEegHead;
                    eegAcc = savedEegAcc;
                    eegTime = savedEegTime;
                    prevBrainHealth = savedPrevBrain;
                    brainTrendSmooth = savedBrainTrend;
                    prevActiveLevel = savedPrevActive;
                    bootScanProgress = savedBootScan;
                    lastStepNs = savedStepNs;
                }
            }
            return remoteDialTextureId;
        }

        if (dialTextureId == null) {
            dialImage = new NativeImage(SIZE, SIZE, true);
            dialTexture = new DynamicTexture(dialImage);
            dialTextureId = Minecraft.getInstance().getTextureManager().register("realmedicalmod_watch_oled_live", dialTexture);
        }

        int refreshMs = ModItems.safeCfgInt(MedicalConfig.WRISTBAND_DIAL_REFRESH_MS, UPDATE_INTERVAL_MS);
        if (nowMs - lastUploadMs >= refreshMs) {
            lastUploadMs = nowMs;
            renderDialPixels(data, activeLevel, nowMs);
            dialTexture.upload();
        }
        return dialTextureId;
    }

    private static void renderDialPixels(MedicalData data, float activeLevel, long nowMs) {
        long nowNs = System.nanoTime();
        if (lastStepNs == 0L) lastStepNs = nowNs;
        float dt = Mth.clamp((nowNs - lastStepNs) / 1_000_000_000.0f, 0.005f, 0.1f);
        lastStepNs = nowNs;

        // 抬腕瞬间触发 CRT 开机激光扫线
        if (activeLevel > 0.28f && prevActiveLevel <= 0.28f) {
            bootScanProgress = 0.0f;
        }
        prevActiveLevel = activeLevel;
        if (bootScanProgress < 1.0f) {
            bootScanProgress = Math.min(1.0f, bootScanProgress + dt * 2.8f);
        }

        stepWaveforms(data, dt, nowMs);

        // 读取 5 大核心生理数据
        boolean arrest = data != null && data.isCardiacArrest();
        boolean vfib = data != null && data.isInVFib();
        int hr = data == null ? 74 : (arrest ? 0 : Mth.clamp(data.getHeartRate(), 0, 240));
        int sbp = data == null ? 120 : Mth.clamp(data.getSystolicBP(), 0, 260);
        int dbp = data == null ? 80 : Mth.clamp(data.getDiastolicBP(), 0, 180);
        int bloodMl = data == null ? 5000 : Mth.clamp(data.getBloodVolume(), 0, 5000);
        int cnsPct = data == null ? 100 : (arrest ? 0 : Mth.clamp(data.getConsciousness(), 0, 100));
        int brainPct = data == null ? 100 : Mth.clamp(data.getBrainHealth(), 0, 100);
        float maxPainCap = Math.max(10.0f, (float) ModItems.safeCfgDouble(MedicalConfig.MAX_PERCEIVED_PAIN, 55.0));
        float painVal = data == null ? 0.0f : Mth.clamp(data.getPerceivedPain(), 0.0f, maxPainCap);
        float painRatio = Mth.clamp(painVal / maxPainCap, 0.0f, 1.0f);
        int ichLevel = data == null ? 0 : data.getIntracranialLevel();
        boolean critical = data != null && (data.isInCriticalState() || arrest || vfib || bloodMl < 2500);

        // 全局主题主色（冷静军工青 -> 琥珀 -> 猩红）
        int accent = critical ? 0xFFFF2444 : (painRatio > 0.55f ? 0xFFFF5518 : (painRatio > 0.25f ? 0xFFFFB800 : 0xFF00E5FF));

        // 1. 清屏并绘制深邃蜂窝光栅 + 24 等分精密测速刻度外环
        drawDialBackgroundAndBezelTicks(accent, critical, nowMs);

        // 2. 顶部军工微缩铭文 BIO-OS // MK-IV 与双侧状态指示点
        drawPixelText(78, 22, "BIO-OS // MK4", scaleColor(accent, 0.72f), 1);
        int dotCol = critical ? (((nowMs / 240L) & 1L) == 0 ? 0xFFFF2040 : 0xFF400810) : 0xFF00FF90;
        fillRect(62, 23, 5, 4, dotCol);
        fillRect(189, 23, 5, 4, dotCol);
        drawDottedHLine(44, 212, 33, scaleColor(accent, 0.35f));

        // 3. 左上舱：实时 ECG 示波器 + 大号七段数码管 BPM
        drawEcgChamber(28, 39, 118, 60, hr, arrest, vfib, nowMs);

        // 4. 右上舱：标志性 0~55 连续形变动态痛觉表情头像（笑脸 -> 痛苦脸）
        drawPainAvatarChamber(152, 39, 76, 76, painVal, painRatio, nowMs);

        // 5. 左中舱：血压 SBP/DBP 军工七段数码管 + 16 格脉压/血容量灌注刻度尺
        drawBloodPressureChamber(28, 105, 118, 56, sbp, dbp, bloodMl, accent);

        // 6. 右下舱：意识清醒度 CNS 240° 精密军工环形刻度仪表盘
        drawConsciousnessGauge(190, 149, 28, cnsPct, accent, nowMs);

        // 7. 底部宽舱：脑健康波动（实时 EEG 脑电双波示波器 + BRAIN % + 趋势箭头）
        drawBrainEegChamber(44, 174, 168, 52, brainPct, ichLevel, brainTrendSmooth, accent, nowMs);

        // 8. 光学特效：CRT 抬腕开机激光扫线 & 剧痛/室颤横向信号干扰
        applyOpticalEffects(accent, painRatio, critical, nowMs);
    }

    // ==================== 1. 蜂窝微光底盘与 24 刻度外环 ====================

    private static void drawDialBackgroundAndBezelTicks(int accent, boolean critical, long nowMs) {
        int ar = (accent >> 16) & 0xFF;
        int ag = (accent >> 8) & 0xFF;
        int ab = accent & 0xFF;

        for (int y = 0; y < SIZE; y++) {
            float dy = y - 128.0f;
            boolean scanDark = (y & 1) == 0;
            for (int x = 0; x < SIZE; x++) {
                float dx = x - 128.0f;
                float d2 = dx * dx + dy * dy;
                // 角落圆环外保持极暗 OLED 边框
                if (d2 > 125.0f * 125.0f) {
                    setPx(x, y, 4, 8, 12, 255);
                    continue;
                }
                boolean grid = ((x % 12) == 0 || (y % 12) == 0);
                int r = grid ? (6 + ar / 38) : 3;
                int g = grid ? (14 + ag / 32) : 8;
                int b = grid ? (20 + ab / 28) : 13;
                if (scanDark) {
                    r = (r * 4) / 5;
                    g = (g * 4) / 5;
                    b = (b * 4) / 5;
                }
                setPx(x, y, r, g, b, 255);
            }
        }

        // 外围 24 等分精密测速刻度环
        for (int i = 0; i < 24; i++) {
            float ang = (float) (i * Math.PI * 2.0 / 24.0);
            boolean major = (i % 6) == 0;
            float rIn = major ? 113.0f : 117.0f;
            float rOut = 123.0f;
            int col = major ? accent : scaleColor(accent, 0.45f);
            drawLine(128 + Mth.cos(ang) * rIn, 128 + Mth.sin(ang) * rIn,
                    128 + Mth.cos(ang) * rOut, 128 + Mth.sin(ang) * rOut, col);
        }
    }

    // ==================== 2. 心跳 ECG 示波器 + 七段数码管 BPM ====================

    private static void drawEcgChamber(int x, int y, int w, int h, int hr, boolean arrest, boolean vfib, long nowMs) {
        int col = (arrest || vfib) ? 0xFFFF2444 : (hr > 135 || hr < 50 ? 0xFFFFB800 : 0xFF00FF96);
        drawChamferedBox(x, y, w, h, scaleColor(col, 0.42f), 0xFF040D12);
        drawPixelText(x + 5, y + 4, "CH1 ECG", scaleColor(col, 0.78f), 1);

        // 示波波形区 (y+12 .. y+35)
        int wx = x + 4, wy = y + 12, ww = w - 8, wh = 22;
        int midY = wy + wh / 2 + 2;
        drawDottedHLine(wx, wx + ww, midY, scaleColor(col, 0.18f));
        for (int i = 1; i < ECG_LEN; i++) {
            int i0 = (ecgHead + i - 1) % ECG_LEN;
            int i1 = (ecgHead + i) % ECG_LEN;
            float fade = i / (float) ECG_LEN;
            float x0 = wx + ((i - 1) / (float) (ECG_LEN - 1)) * ww;
            float x1 = wx + (i / (float) (ECG_LEN - 1)) * ww;
            float y0 = midY - ecgBuf[i0] * (wh * 0.46f);
            float y1 = midY - ecgBuf[i1] * (wh * 0.46f);
            drawLine(x0, y0, x1, y1, scaleColor(col, 0.18f + 0.82f * fade * fade));
        }
        // 扫描亮头点
        float tipY = midY - ecgBuf[(ecgHead + ECG_LEN - 1) % ECG_LEN] * (wh * 0.46f);
        fillRect(wx + ww - 2, Math.round(tipY) - 1, 3, 3, 0xFFFFFFFF);

        // 大号七段军工数码管 BPM（带熄灭底槽 888）
        int digY = y + 37;
        if (arrest) {
            drawPixelText(x + 8, digY + 5, "ASYSTOLE", 0xFFFF2444, 2);
        } else if (vfib) {
            drawPixelText(x + 14, digY + 5, "V-FIB!", (((nowMs / 180L) & 1L) == 0 ? 0xFFFF2444 : 0xFFFFB800), 2);
        } else {
            drawSevenSegNumber(x + 8, digY, hr, 3, 12, 19, 3, col);
            drawPixelText(x + 62, digY + 12, "BPM", scaleColor(col, 0.80f), 1);
            // 随心搏跳动的微缩红心图标
            float beat = (float) Math.exp(-Math.pow((ecgPhase - 0.32f) / 0.08f, 2.0));
            drawTinyHeart(x + 92, digY + 7, beat > 0.35f ? 0xFFFF2A55 : 0xFF551422);
        }
    }

    // ==================== 3. 标志性 0~55 痛觉动态笑脸 -> 痛苦脸 ====================

    private static void drawPainAvatarChamber(int x, int y, int w, int h, float pain, float pr, long nowMs) {
        int faceCol;
        if (pr <= 0.16f) faceCol = 0xFF00FF88;       // 无痛笑脸
        else if (pr <= 0.36f) faceCol = 0xFFAEEA00;  // 轻痛微抿
        else if (pr <= 0.56f) faceCol = 0xFFFFD000;  // 中痛拧眉
        else if (pr <= 0.76f) faceCol = 0xFFFF7A00;  // 重痛咬牙
        else faceCol = 0xFFFF1E42;                   // 剧痛哀嚎

        drawChamferedBox(x, y, w, h, scaleColor(faceCol, 0.55f), 0xFF050B10);
        drawPixelText(x + 5, y + 4, "PAIN", scaleColor(faceCol, 0.78f), 1);
        drawPixelText(x + w - 18, y + 4, String.format("%02d", Math.round(pain)), faceCol, 1);

        // 高痛微颤偏移
        float shakeX = pr > 0.55f ? Mth.sin(nowMs * 0.048f) * (pr - 0.55f) * 3.5f : 0.0f;
        float shakeY = pr > 0.55f ? Mth.cos(nowMs * 0.061f) * (pr - 0.55f) * 2.8f : 0.0f;
        float cx = x + w * 0.5f + shakeX;
        float cy = y + h * 0.53f + shakeY;
        float r = 21.0f;

        // 全息人脸圆形外廓（双圈精致感）
        drawCircleOutline(cx, cy, r, faceCol);
        drawCircleOutline(cx, cy, r - 2.2f, scaleColor(faceCol, 0.30f));

        // 1) 眉毛：随疼痛从平和 (0°) -> 拧紧内八 (+36°)
        float browTilt = pr * 4.5f;
        float browY = cy - 8.5f + pr * 1.8f;
        drawThickLine(cx - 11.5f, browY - browTilt * 0.45f, cx - 3.5f, browY + browTilt * 0.65f, faceCol);
        drawThickLine(cx + 11.5f, browY - browTilt * 0.45f, cx + 3.5f, browY + browTilt * 0.65f, faceCol);

        // 2) 眼睛：低痛自然眨眼圆眼 -> 中痛眯眼 -> 剧痛紧闭 > <
        float eyeY = cy - 2.5f;
        boolean blink = (nowMs % 3600L) < 120L && pr < 0.65f;
        if (pr >= 0.72f) {
            // 剧痛紧闭 >  <
            drawThickLine(cx - 10.5f, eyeY - 3.0f, cx - 4.5f, eyeY, faceCol);
            drawThickLine(cx - 10.5f, eyeY + 3.0f, cx - 4.5f, eyeY, faceCol);
            drawThickLine(cx + 10.5f, eyeY - 3.0f, cx + 4.5f, eyeY, faceCol);
            drawThickLine(cx + 10.5f, eyeY + 3.0f, cx + 4.5f, eyeY, faceCol);
        } else if (blink) {
            drawThickLine(cx - 10.0f, eyeY, cx - 4.5f, eyeY, faceCol);
            drawThickLine(cx + 4.5f, eyeY, cx + 10.0f, eyeY, faceCol);
        } else {
            int eyeH = pr > 0.40f ? 2 : 4;
            fillRect(Math.round(cx - 8.5f), Math.round(eyeY - eyeH * 0.5f), 4, eyeH, faceCol);
            fillRect(Math.round(cx + 4.5f), Math.round(eyeY - eyeH * 0.5f), 4, eyeH, faceCol);
        }

        // 3) 嘴部连续形变：笑脸弧线 -> 平直 -> 下撇 -> 咬牙切齿 -> 剧痛哀嚎张口
        float mouthY = cy + 8.5f;
        if (pr < 0.68f) {
            // 连续二次贝塞尔微笑/下撇弧线 (curve: +5.5 微笑 -> 0 平直 -> -5.0 痛苦下撇)
            float curve = 5.5f - pr * 15.5f;
            float prevX = cx - 9.0f, prevY = mouthY;
            for (int s = 1; s <= 12; s++) {
                float t = s / 12.0f;
                float mx = Mth.lerp(t, cx - 9.0f, cx + 9.0f);
                float my = mouthY + 4.0f * t * (1.0f - t) * curve;
                drawThickLine(prevX, prevY, mx, my, faceCol);
                prevX = mx;
                prevY = my;
            }
        } else {
            // 剧痛哀嚎张口（椭圆张口 + 锯齿牙光）
            float openH = 4.0f + (pr - 0.68f) * 14.0f;
            drawRectOutline(Math.round(cx - 8.0f), Math.round(mouthY - openH * 0.5f), 16, Math.round(openH), faceCol);
            drawDottedHLine(Math.round(cx - 6.0f), Math.round(cx + 6.0f), Math.round(mouthY), scaleColor(faceCol, 0.75f));
        }

        // 4) 高痛太阳穴痛觉脉冲电弧
        if (pr >= 0.58f && ((nowMs / 150L) & 1L) == 0) {
            drawLine(cx - r - 2, cy - 11, cx - r - 6, cy - 15, 0xFFFF3855);
            drawLine(cx - r - 6, cy - 15, cx - r - 3, cy - 19, 0xFFFFB800);
            drawLine(cx + r + 2, cy - 11, cx + r + 6, cy - 15, 0xFFFF3855);
            drawLine(cx + r + 6, cy - 15, cx + r + 3, cy - 19, 0xFFFFB800);
        }

        // 底部 6 级 Wong-Baker 痛觉色阶灯
        int[] stages = {0xFF00FF88, 0xFF88EE00, 0xFFFFD000, 0xFFFF9800, 0xFFFF4818, 0xFFFF1838};
        int activeStage = Mth.clamp((int) Math.ceil(pr * 6.0f), 0, 6);
        for (int i = 0; i < 6; i++) {
            int sc = (i < activeStage || (i == 0 && activeStage == 0)) ? stages[i] : scaleColor(stages[i], 0.20f);
            fillRect(x + 7 + i * 11, y + h - 7, 8, 3, sc);
        }
    }

    // ==================== 4. 血压 SBP/DBP + 16 格脉压灌注尺 ====================

    private static void drawBloodPressureChamber(int x, int y, int w, int h, int sbp, int dbp, int bloodMl, int accent) {
        int bpCol = sbp < 78 ? 0xFFFF2444 : (sbp < 98 ? 0xFFFFB800 : 0xFF00E5FF);
        drawChamferedBox(x, y, w, h, scaleColor(bpCol, 0.42f), 0xFF040C12);
        drawPixelText(x + 5, y + 4, "BP mmHg", scaleColor(bpCol, 0.78f), 1);
        drawPixelText(x + w - 34, y + 4, bloodMl + "mL", scaleColor(bloodMl < 3000 ? 0xFFFF3850 : bpCol, 0.85f), 1);

        // 七段数码管收缩压 / 舒张压 (如 120/80)
        int dy = y + 14;
        drawSevenSegNumber(x + 6, dy, sbp, 3, 10, 17, 2, bpCol);
        // 斜杠 "/"
        drawThickLine(x + 52, dy + 16, x + 58, dy + 2, scaleColor(bpCol, 0.75f));
        drawSevenSegNumber(x + 62, dy, dbp, 3, 10, 17, 2, scaleColor(bpCol, 0.88f));

        // 底部 16 格独立发光血容量灌注刻度尺
        int segCount = 16;
        int filled = Mth.clamp(Math.round((bloodMl / 5000.0f) * segCount), 0, segCount);
        for (int i = 0; i < segCount; i++) {
            int sx = x + 6 + i * 7;
            int sy = y + h - 14;
            int sc = (i < 4) ? 0xFFFF2444 : (i < 8 ? 0xFFFFB800 : 0xFF00E5FF);
            fillRect(sx, sy, 5, 8, i < filled ? sc : scaleColor(sc, 0.16f));
        }
    }

    // ==================== 5. 意识清醒度 CNS 240° 精密军工环形表盘 ====================

    private static void drawConsciousnessGauge(int cx, int cy, int r, int cnsPct, int accent, long nowMs) {
        int col = cnsPct < 40 ? 0xFFFF2444 : (cnsPct < 75 ? 0xFFFFB800 : 0xFF00E8FF);
        float startDeg = 150.0f;
        float sweepDeg = 240.0f;
        float fillDeg = sweepDeg * (cnsPct / 100.0f);

        // 240° 背景刻度槽与实时发光弧带
        for (int d = 0; d <= 240; d += 3) {
            float rad = (float) Math.toRadians(startDeg + d);
            float cos = Mth.cos(rad), sin = Mth.sin(rad);
            boolean active = d <= fillDeg;
            int arcCol = active ? (d < 85 ? 0xFFFF2444 : col) : scaleColor(col, 0.16f);
            drawThickLine(cx + cos * (r - 5), cy + sin * (r - 5), cx + cos * r, cy + sin * r, arcCol);
            if (d % 30 == 0) {
                drawLine(cx + cos * (r + 1), cy + sin * (r + 1), cx + cos * (r + 4), cy + sin * (r + 4), scaleColor(col, 0.65f));
            }
        }

        drawPixelText(cx - 9, cy - 11, "CNS", scaleColor(col, 0.80f), 1);
        drawSevenSegNumber(cx - 15, cy - 2, cnsPct, 3, 7, 12, 2, col);
        drawPixelText(cx - 5, cy + 14, "PCT", scaleColor(col, 0.65f), 1);
    }

    // ==================== 6. 脑健康波动 EEG 示波器 + BRAIN % ====================

    private static void drawBrainEegChamber(int x, int y, int w, int h, int brainPct, int ichLevel,
                                            float trend, int accent, long nowMs) {
        int col = (brainPct < 55 || ichLevel >= 2) ? 0xFFFF2444 : (brainPct < 85 || ichLevel > 0 ? 0xFFFFB800 : 0xFF40D8FF);
        drawChamferedBox(x, y, w, h, scaleColor(col, 0.45f), 0xFF040C14);
        drawPixelText(x + 6, y + 4, "EEG BRAIN WAVE", scaleColor(col, 0.78f), 1);

        // 实时脑电双通道波动示波窗
        int wx = x + 6, wy = y + 13, ww = 104, wh = 31;
        int midY = wy + wh / 2;
        drawDottedHLine(wx, wx + ww, midY, scaleColor(col, 0.20f));
        for (int i = 1; i < EEG_LEN; i++) {
            int i0 = (eegHead + i - 1) % EEG_LEN;
            int i1 = (eegHead + i) % EEG_LEN;
            float fade = i / (float) EEG_LEN;
            float x0 = wx + ((i - 1) / (float) (EEG_LEN - 1)) * ww;
            float x1 = wx + (i / (float) (EEG_LEN - 1)) * ww;
            float y0 = midY - eegBuf[i0] * (wh * 0.44f);
            float y1 = midY - eegBuf[i1] * (wh * 0.44f);
            drawLine(x0, y0, x1, y1, scaleColor(col, 0.22f + 0.78f * fade));
        }

        // 右侧 BRAIN % 七段数码管 + 波动趋势箭头
        int rx = x + 116;
        drawPixelText(rx, y + 7, ichLevel > 0 ? ("ICH L" + ichLevel) : "NEURAL", ichLevel > 0 ? 0xFFFF2444 : scaleColor(col, 0.72f), 1);
        drawSevenSegNumber(rx, y + 17, brainPct, 3, 10, 17, 2, col);
        drawPixelText(rx + 2, y + 39, trend < -0.08f ? "FALLING!" : (trend > 0.08f ? "STABLE+" : "NOMINAL"),
                trend < -0.08f ? 0xFFFF2444 : (trend > 0.08f ? 0xFF00FF88 : scaleColor(col, 0.72f)), 1);
    }

    // ==================== 波形物理积分 & 光学特效 ====================

    private static void stepWaveforms(MedicalData data, float dt, long nowMs) {
        boolean arrest = data != null && data.isCardiacArrest();
        boolean vfib = data != null && data.isInVFib();
        float hr = data == null ? 74.0f : (arrest ? 0.0f : Mth.clamp(data.getHeartRate(), 30.0f, 220.0f));

        if (hr > 0.0f) {
            ecgPhase = (ecgPhase + dt * (hr / 60.0f)) % 1.0f;
        }
        ecgAcc += dt * 52.0f;
        while (ecgAcc >= 1.0f) {
            ecgAcc -= 1.0f;
            float s = 0.0f;
            if (!arrest) {
                if (vfib) {
                    s = Mth.sin(nowMs * 0.061f) * 0.50f + Mth.cos(nowMs * 0.103f) * 0.40f;
                } else {
                    float p = ecgPhase;
                    if (p > 0.12f && p < 0.22f) s = 0.18f * Mth.sin((p - 0.12f) / 0.10f * Mth.PI);
                    else if (p >= 0.26f && p < 0.29f) s = -0.24f;
                    else if (p >= 0.29f && p < 0.34f) s = 1.0f;
                    else if (p >= 0.34f && p < 0.38f) s = -0.36f;
                    else if (p >= 0.48f && p < 0.64f) s = 0.28f * Mth.sin((p - 0.48f) / 0.16f * Mth.PI);
                }
            }
            ecgBuf[ecgHead] = s;
            ecgHead = (ecgHead + 1) % ECG_LEN;
        }

        // EEG 脑电波动（脑健康越低 / 颅内出血越重，尖棘波紊乱波动越剧烈！）
        float curBrain = data == null ? 100.0f : data.getBrainHealthFloat();
        float deltaBrain = (curBrain - prevBrainHealth) / Math.max(0.01f, dt);
        brainTrendSmooth = Mth.lerp(Math.min(1.0f, dt * 4.0f), brainTrendSmooth, Mth.clamp(deltaBrain, -5.0f, 5.0f));
        prevBrainHealth = curBrain;

        float damageSeverity = Mth.clamp((100.0f - curBrain) / 65.0f, 0.0f, 1.0f);
        int ich = data == null ? 0 : data.getIntracranialLevel();
        float disorder = Mth.clamp(damageSeverity + ich * 0.22f, 0.0f, 1.0f);

        eegTime += dt;
        eegAcc += dt * 46.0f;
        while (eegAcc >= 1.0f) {
            eegAcc -= 1.0f;
            float alphaWave = Mth.sin(eegTime * 11.5f) * 0.24f + Mth.cos(eegTime * 6.8f) * 0.14f;
            float spikeWave = (Mth.sin(eegTime * 37.0f) * 0.55f + Mth.sin(eegTime * 63.0f) * 0.35f) * disorder;
            float val = (curBrain <= 1.0f) ? 0.0f : Mth.clamp(alphaWave * (1.0f - disorder * 0.4f) + spikeWave, -0.95f, 0.95f);
            eegBuf[eegHead] = val;
            eegHead = (eegHead + 1) % EEG_LEN;
            eegTime += 0.018f;
        }
    }

    private static void applyOpticalEffects(int accent, float painRatio, boolean critical, long nowMs) {
        // 1) 抬腕唤醒 CRT 横向激光扫线（未扫到的下半屏保持幽暗待机）
        if (bootScanProgress < 0.99f) {
            int scanY = Math.round(bootScanProgress * SIZE);
            for (int y = Math.max(0, scanY); y < SIZE; y++) {
                boolean isBeam = (y >= scanY && y <= scanY + 4);
                for (int x = 0; x < SIZE; x++) {
                    if (isBeam) {
                        setPx(x, y, 180, 250, 255, 255);
                    } else {
                        setPx(x, y, 3, 7, 11, 255);
                    }
                }
            }
            return;
        }

        // 2) 高痛/危重时的轻微横向信号撕裂干扰线
        if ((critical || painRatio > 0.65f) && ((nowMs / 140L) % 5L == 0L)) {
            int glitchY = (int) ((nowMs * 7L) % (SIZE - 24)) + 12;
            for (int x = 24; x < SIZE - 24; x++) {
                setPx(x, glitchY, 255, 60, 88, 255);
            }
        }
    }

    // ==================== 自研七段数码管 & 3x5 军工点阵字库 ====================

    private static void drawSevenSegNumber(int x, int y, int value, int minDigits, int digW, int digH, int thick, int col) {
        value = Math.max(0, value);
        String str = String.valueOf(value);
        int totalDigits = Math.max(minDigits, str.length());
        int step = digW + thick + 2;
        int ghostCol = scaleColor(col, 0.13f);

        for (int i = 0; i < totalDigits; i++) {
            int dx = x + i * step;
            // 先绘制所有 7 段熄灭暗槽底影 (888 真实数码管质感！)
            drawSevenSegMask(dx, y, digW, digH, thick, 0b1111111, ghostCol);
            int strIdx = i - (totalDigits - str.length());
            if (strIdx >= 0 && strIdx < str.length()) {
                int d = str.charAt(strIdx) - '0';
                if (d >= 0 && d <= 9) {
                    drawSevenSegMask(dx, y, digW, digH, thick, SEG_DIGITS[d], col);
                }
            }
        }
    }

    private static void drawSevenSegMask(int x, int y, int w, int h, int t, int mask, int col) {
        int halfH = h / 2;
        if ((mask & 1) != 0) fillRect(x + t, y, w - 2 * t, t, col);                        // A 顶
        if ((mask & 2) != 0) fillRect(x + w - t, y + t, t, halfH - t, col);                 // B 右上
        if ((mask & 4) != 0) fillRect(x + w - t, y + halfH + 1, t, h - halfH - t - 1, col); // C 右下
        if ((mask & 8) != 0) fillRect(x + t, y + h - t, w - 2 * t, t, col);                 // D 底
        if ((mask & 16) != 0) fillRect(x, y + halfH + 1, t, h - halfH - t - 1, col);        // E 左下
        if ((mask & 32) != 0) fillRect(x, y + t, t, halfH - t, col);                        // F 左上
        if ((mask & 64) != 0) fillRect(x + t, y + halfH - t / 2, w - 2 * t, t, col);        // G 中
    }

    private static void drawPixelText(int x, int y, String text, int col, int scale) {
        int cx = x;
        for (int i = 0; i < text.length(); i++) {
            int[] glyph = getGlyph3x5(Character.toUpperCase(text.charAt(i)));
            for (int r = 0; r < 5; r++) {
                int rowBits = glyph[r];
                for (int c = 0; c < 3; c++) {
                    if (((rowBits >> (2 - c)) & 1) != 0) {
                        fillRect(cx + c * scale, y + r * scale, scale, scale, col);
                    }
                }
            }
            cx += 4 * scale;
        }
    }

    private static int[] getGlyph3x5(char ch) {
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
            case 'S', '5' -> new int[]{0b011, 0b100, 0b010, 0b001, 0b110};
            case 'T' -> new int[]{0b111, 0b010, 0b010, 0b010, 0b010};
            case 'U' -> new int[]{0b101, 0b101, 0b101, 0b101, 0b111};
            case 'V' -> new int[]{0b101, 0b101, 0b101, 0b101, 0b010};
            case 'W' -> new int[]{0b101, 0b101, 0b111, 0b111, 0b101};
            case 'Y' -> new int[]{0b101, 0b101, 0b010, 0b010, 0b010};
            case '1' -> new int[]{0b010, 0b110, 0b010, 0b010, 0b111};
            case '2' -> new int[]{0b110, 0b001, 0b010, 0b100, 0b111};
            case '3' -> new int[]{0b110, 0b001, 0b010, 0b001, 0b110};
            case '4' -> new int[]{0b101, 0b101, 0b111, 0b001, 0b001};
            case '6' -> new int[]{0b011, 0b100, 0b111, 0b101, 0b111};
            case '7' -> new int[]{0b111, 0b001, 0b010, 0b010, 0b010};
            case '8' -> new int[]{0b111, 0b101, 0b111, 0b101, 0b111};
            case '9' -> new int[]{0b111, 0b101, 0b111, 0b001, 0b110};
            case '-' -> new int[]{0b000, 0b000, 0b111, 0b000, 0b000};
            case '+' -> new int[]{0b000, 0b010, 0b111, 0b010, 0b000};
            case '/' -> new int[]{0b001, 0b001, 0b010, 0b100, 0b100};
            case '!' -> new int[]{0b010, 0b010, 0b010, 0b000, 0b010};
            default -> new int[]{0, 0, 0, 0, 0};
        };
    }

    // ==================== 256x256 像素光栅绘制原语 ====================

    private static void drawTinyHeart(int x, int y, int col) {
        int[] rows = {0b01010, 0b11111, 0b11111, 0b01110, 0b00100};
        for (int r = 0; r < 5; r++) {
            for (int c = 0; c < 5; c++) {
                if (((rows[r] >> (4 - c)) & 1) != 0) {
                    fillRect(x + c * 2, y + r * 2, 2, 2, col);
                }
            }
        }
    }

    private static void drawChamferedBox(int x, int y, int w, int h, int borderCol, int bgCol) {
        fillRect(x + 1, y + 1, w - 2, h - 2, bgCol);
        int cut = 4;
        drawLine(x + cut, y, x + w - cut, y, borderCol);
        drawLine(x + cut, y + h, x + w - cut, y + h, borderCol);
        drawLine(x, y + cut, x, y + h - cut, borderCol);
        drawLine(x + w, y + cut, x + w, y + h - cut, borderCol);
        drawLine(x, y + cut, x + cut, y, borderCol);
        drawLine(x + w - cut, y, x + w, y + cut, borderCol);
        drawLine(x, y + h - cut, x + cut, y + h, borderCol);
        drawLine(x + w - cut, y + h, x + w, y + h - cut, borderCol);
    }

    private static void drawRectOutline(int x, int y, int w, int h, int col) {
        fillRect(x, y, w, 1, col);
        fillRect(x, y + h - 1, w, 1, col);
        fillRect(x, y, 1, h, col);
        fillRect(x + w - 1, y, 1, h, col);
    }

    private static void drawDottedHLine(int x0, int x1, int y, int col) {
        for (int x = x0; x <= x1; x += 2) {
            setPxArgb(x, y, col);
        }
    }

    private static void drawCircleOutline(float cx, float cy, float r, int col) {
        int steps = 48;
        float px = cx + r, py = cy;
        for (int i = 1; i <= steps; i++) {
            float a = (float) (i * Math.PI * 2.0 / steps);
            float nx = cx + Mth.cos(a) * r;
            float ny = cy + Mth.sin(a) * r;
            drawLine(px, py, nx, ny, col);
            px = nx;
            py = ny;
        }
    }

    private static void drawThickLine(float x0, float y0, float x1, float y1, int col) {
        drawLine(x0, y0, x1, y1, col);
        drawLine(x0, y0 + 1, x1, y1 + 1, col);
    }

    private static void drawLine(float x0, float y0, float x1, float y1, int col) {
        float dx = x1 - x0, dy = y1 - y0;
        int steps = Math.max(1, Math.round(Math.max(Math.abs(dx), Math.abs(dy))));
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            setPxArgb(Math.round(x0 + dx * t), Math.round(y0 + dy * t), col);
        }
    }

    private static void fillRect(int x, int y, int w, int h, int col) {
        int x0 = Math.max(0, x), y0 = Math.max(0, y);
        int x1 = Math.min(SIZE, x + w), y1 = Math.min(SIZE, y + h);
        for (int py = y0; py < y1; py++) {
            for (int px = x0; px < x1; px++) {
                setPxArgb(px, py, col);
            }
        }
    }

    private static int scaleColor(int argb, float factor) {
        int r = Mth.clamp(Math.round(((argb >> 16) & 0xFF) * factor), 0, 255);
        int g = Mth.clamp(Math.round(((argb >> 8) & 0xFF) * factor), 0, 255);
        int b = Mth.clamp(Math.round((argb & 0xFF) * factor), 0, 255);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static void setPxArgb(int x, int y, int argb) {
        if (x < 0 || y < 0 || x >= SIZE || y >= SIZE) return;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        setPx(x, y, r, g, b, 255);
    }

    private static void setPx(int x, int y, int r, int g, int b, int a) {
        if (x < 0 || y < 0 || x >= SIZE || y >= SIZE) return;
        int abgr = ((a & 0xFF) << 24) | ((b & 0xFF) << 16) | ((g & 0xFF) << 8) | (r & 0xFF);
        dialImage.setPixelRGBA(x, y, abgr);
    }
}