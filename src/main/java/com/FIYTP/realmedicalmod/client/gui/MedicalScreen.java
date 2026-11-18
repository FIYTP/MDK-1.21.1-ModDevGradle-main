package com.FIYTP.realmedicalmod.client.gui;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.capability.MedicalDataCapability;
import com.FIYTP.realmedicalmod.config.MedicalConfig;
import com.FIYTP.realmedicalmod.client.sound.MedicalSoundHelper;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 医疗面板 UI —— 3A 科技风全息生物遥测终端 (全量升级版 v2.0)
 *
 * 本次重大升级：
 * 1. 总体疼痛 (Perceived Pain 0~55) 全局动态主题染色系统：
 *    面板主色调、背景网格、扫描激光线、卡片边框与呼吸角标随总体疼痛值 (0 -> 55) 从冷静霓虹青平滑过渡至
 *    预警琥珀黄 (12~24)、重度灼热橙红 (25~39) 与极度剧痛深渊猩红 (40~55)，并在高痛感下伴随心搏同频边框呼吸与微颤。
 * 2. 3A 全息动态痛觉表情遥测仪 (Holographic Pain Expression Avatar, 0~55 从笑脸到痛苦脸)：
 *    基于 OpenGL 矢量连续形变算法绘制高精全息人脸，随总体疼痛值 (0~55) 实时连续改变眉毛倾角、眼睑开合/眨眼/紧闭(`> <`)、
 *    嘴部微笑弧线 -> 拧眉咬牙 -> 剧痛哀嚎及太阳穴青筋电弧，搭配 6 级 Wong-Baker 痛觉色阶尺。
 * 3. 高精度解剖级 4 层 DICOM 微观断层 CT 扫描仪 (High-Precision Anatomical CT Tomography)：
 *    彻底摒弃原有的粗糙菱形占位图，采用 OpenGL 多环极坐标与解剖组织建模绘制：
 *    - LAYER 01 (表皮与真皮层)：角质层/表皮/真皮乳头波浪环/皮下脂肪网 + 开放创口撕裂缺口与喷射血滴 + 烧伤热力弧 + 绿色无菌敷料编织环
 *    - LAYER 02 (肌肉血管灌注层)：四大肌群室与筋膜隔 + 搏动主干动脉与伴行静脉 + 放射状毛细血管床 + 组织间隙内出血紫红血肿池 + 止血带环压
 *    - LAYER 03 (骨骼与骨髓腔层)：部位专属骨骼截面（颅骨穹窿/胸廓双肺与肋骨/四肢双骨皮质环）+ 骨小梁蜂窝网 + 粉碎性骨折裂隙与钛合金外固定支架
 *    - LAYER 04 (细胞炎症与痛觉神经层)：高倍神经束髓鞘轴突截面 + 痛觉神经放电脉冲环 + 细菌感染菌落群与生物靶向锁定框
 */
public class MedicalScreen extends Screen {

    private static int getHealthColor(float pct) {
        pct = Mth.clamp(pct, 0.0f, 1.0f);
        if (pct > 0.5f) {
            return lerpColor(C_WARN_YELLOW, C_SAFE_GREEN, (pct - 0.5f) * 2.0f);
        } else {
            return lerpColor(C_ALERT_RED, C_WARN_YELLOW, pct * 2.0f);
        }
    }

    private static int lerpColor(int c1, int c2, float t) {
        t = Mth.clamp(t, 0.0f, 1.0f);
        int r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        int r = (int) (r1 + (r2 - r1) * t);
        int g = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int withAlpha(int rgb, float alpha) {
        int a = Mth.clamp((int) (alpha * 255.0f), 0, 255);
        return (a << 24) | (rgb & 0x00FFFFFF);
    }

    // 基础语义常量
    private static final int C_NEON_CYAN = 0xFF00F0FF;
    private static final int C_ALERT_RED = 0xFFFF2040;
    private static final int C_WARN_YELLOW = 0xFFFFD000;
    private static final int C_SAFE_GREEN = 0xFF00FF80;
    private static final int C_VITAL_BP = 0xFFFF9900;
    private static final int C_VITAL_PAIN = 0xFFFF0055;
    private static final int C_VITAL_OXYGEN = 0xFF00D2FF;
    private static final int C_VITAL_HEART = 0xFFFF2A55;
    private static final int C_TEXT_LABEL = 0xFF00D2FF;

    private static final int LIMB_HEALTHY = 0xFF00FF80;
    private static final int LIMB_MINOR = 0xFFFFD000;
    private static final int LIMB_MODERATE = 0xFFFF8000;
    private static final int LIMB_CRITICAL = 0xFFFF1030;
    private static final int LIMB_AMPUTATED = 0xFF303030;

    private static final Map<String, String> I18N_CACHE = new HashMap<>();

    public enum ScreenState {
        OPENING, OPEN, CLOSING, CLOSED
    }

    private static final long OPEN_ANIM_DURATION_MS = 420L;
    private static final long CLOSE_ANIM_DURATION_MS = 260L;
    private static final int HUD_BAR_H = 38;
    private static final int LEFT_PANEL_X = 16;
    private static final int LEFT_PANEL_W = 236;

    private static final String[] DICOM_LAYER_NAMES = new String[]{
            "L1 表皮真皮层",
            "L2 肌肉血管层",
            "L3 骨骼皮质层",
            "L4 神经细胞层"
    };

    private final BrainScan3DRenderer brainScanRenderer = new BrainScan3DRenderer();
    private final Cardiopulmonary3DRenderer cardiopulmonaryRenderer = new Cardiopulmonary3DRenderer();
    private final EmbeddedDetailPanelRenderer detailPanelRenderer = new EmbeddedDetailPanelRenderer();
    private final ClickRippleManager rippleManager = new ClickRippleManager();

    private ScreenState state = ScreenState.OPENING;
    private long stateStartTimeMs = 0L;
    private long lastRenderTimeMs = 0L;
    private boolean openPulseTriggered = false;

    private MedicalData.Limb selectedLimb = null;
    private MedicalData.Limb hoveredLimb = null;
    private MedicalData.Limb lastHoveredLimb = null;
    private final Map<MedicalData.Limb, Float> limbHoverProgress = new EnumMap<>(MedicalData.Limb.class);
    private final Map<MedicalData.Limb, int[]> limbScreenBounds = new EnumMap<>(MedicalData.Limb.class);

    // DICOM 微观 CT 断层探查状态
    private MedicalData.Limb microTomographyLimb = null;
    private float microTomographyAnim = 0.0f;
    private int dcmLayer = 0;
    private long dcmWipeStartMs = 0L;
    private int ctBtnX, ctBtnY, ctBtnW, ctBtnH;
    private int layerBtnPrevX, layerBtnNextX, layerBtnY, layerBtnW, layerBtnH;

    // 双通道临床示波器环形缓冲区
    private static final int OSC_SAMPLES = 160;
    private final float[] ecgBuffer = new float[OSC_SAMPLES];
    private final float[] plethBuffer = new float[OSC_SAMPLES];
    private int oscWriteHead = 0;
    private float oscSweepAccumulator = 0.0f;
    private float ecgPhase = 0.0f;
    private float rPeakFlashAlpha = 0.0f;

    // 数码管与总体疼痛 (0~55) 平滑插值状态
    private float smoothHrDisplay = 75.0f;
    private float smoothBloodDisplay = 5000.0f;
    private float smoothSpo2Display = 98.0f;
    private float smoothPerceivedPain = 0.0f;

    // 当前帧由总体疼痛 (0~55) 动态计算出的全局主题色
    private int themePrimaryColor = C_NEON_CYAN;
    private int themeSecondaryColor = 0xFF00A0FF;
    private int themeCardBgColor = 0xDF060E18;

    public MedicalScreen() {
        super(Component.translatable("medical.panel.title"));
        for (MedicalData.Limb limb : MedicalData.Limb.values()) {
            limbHoverProgress.put(limb, 0.0f);
            limbScreenBounds.put(limb, new int[]{0, 0, 0, 0});
        }
    }

    @Override
    protected void init() {
        super.init();
        long now = System.currentTimeMillis();
        if (state != ScreenState.OPEN) {
            state = ScreenState.OPENING;
            stateStartTimeMs = now;
            openPulseTriggered = false;
            MedicalSoundHelper.playOpenSound();
        }
        lastRenderTimeMs = now;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void beginClosing() {
        if (state != ScreenState.CLOSING && state != ScreenState.CLOSED) {
            state = ScreenState.CLOSING;
            stateStartTimeMs = System.currentTimeMillis();
            MedicalSoundHelper.playCloseSound();
        }
    }

    @Override
    public void onClose() {
        if (state != ScreenState.CLOSING && state != ScreenState.CLOSED) {
            beginClosing();
            return;
        }
        super.onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_H) {
            if (microTomographyLimb != null) {
                microTomographyLimb = null;
                MedicalSoundHelper.playCloseSound();
                return true;
            }
            beginClosing();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * 根据总体疼痛 (0 ~ 55) 计算全局面板动态主题色
     * 0~10: 冷静科技青 -> 11~24: 预警琥珀黄 -> 25~39: 灼热创伤橙 -> 40~55: 剧痛深渊猩红 + 心搏脉冲
     */
    private void updatePainDrivenThemeColors(long now) {
        float maxPainCap = Math.max(10.0f, MedicalConfig.MAX_PERCEIVED_PAIN.get().floatValue());
        float p = Mth.clamp(smoothPerceivedPain, 0.0f, maxPainCap);
        int baseAccent;
        int baseBg;

        float pr = Mth.clamp(p / maxPainCap, 0.0f, 1.0f);
        if (pr <= 0.18f) {
            float t = pr / 0.18f;
            baseAccent = lerpColor(0xFF00F0FF, 0xFF00E5B8, t);
            baseBg = lerpColor(0x060E18, 0x081218, t);
        } else if (pr <= 0.42f) {
            float t = (pr - 0.18f) / 0.24f;
            baseAccent = lerpColor(0xFF00E5B8, 0xFFFFB300, t);
            baseBg = lerpColor(0x081218, 0x161006, t);
        } else if (pr <= 0.68f) {
            float t = (pr - 0.42f) / 0.26f;
            baseAccent = lerpColor(0xFFFFB300, 0xFFFF3D00, t);
            baseBg = lerpColor(0x161006, 0x1C0806, t);
        } else {
            float t = (pr - 0.68f) / 0.32f;
            baseAccent = lerpColor(0xFFFF3D00, 0xFFFF0044, t);
            baseBg = lerpColor(0x1C0806, 0x24040A, t);
        }

        // 高疼痛下叠加心搏共振亮度呼吸
        if (pr > 0.32f) {
            float painPulse = 0.5f + 0.5f * Mth.sin(now * 0.008f + ecgPhase * ((float) Math.PI * 2.0f));
            float boost = Mth.clamp((pr - 0.32f) / 0.68f, 0.0f, 1.0f) * 0.28f * painPulse;
            baseAccent = lerpColor(baseAccent, 0xFFFF8095, boost);
        }

        this.themePrimaryColor = baseAccent;
        this.themeSecondaryColor = lerpColor(baseAccent, 0xFF00A0FF, 0.25f);
        this.themeCardBgColor = (0xDF << 24) | (baseBg & 0x00FFFFFF);
    }

    /**
     * 绘制受总体疼痛值染色的全屏背景科技网格与激光扫描束
     */
    private void drawPainTintedBackground(GuiGraphics g, long now) {
        float maxPainCap = Math.max(10.0f, MedicalConfig.MAX_PERCEIVED_PAIN.get().floatValue());
        float painRatio = Mth.clamp(smoothPerceivedPain / maxPainCap, 0.0f, 1.0f);
        int topBg = lerpColor(0x060B10, 0x1E0408, painRatio * 0.85f) | 0xD4000000;
        int botBg = lerpColor(0x0B131C, 0x2A060C, painRatio * 0.85f) | 0xEC000000;
        g.fillGradient(0, 0, this.width, this.height, topBg, botBg);

        int gridColor = withAlpha(themePrimaryColor, 0.05f + 0.04f * painRatio);
        for (int gx = 0; gx < this.width; gx += 32) {
            g.fill(gx, 0, gx + 1, this.height, gridColor);
        }
        for (int gy = 0; gy < this.height; gy += 32) {
            g.fill(0, gy, this.width, gy + 1, gridColor);
        }

        float scanPeriod = 3500.0f - painRatio * 1400.0f; // 疼痛越高，全息扫描激光扫速越急促
        float scanProg = (now % (long) scanPeriod) / scanPeriod;
        int scanY = (int) (scanProg * (this.height + 40)) - 20;
        if (scanY >= -20 && scanY <= this.height) {
            g.fill(0, scanY, this.width, scanY + 1, withAlpha(themePrimaryColor, 0.32f));
            g.fillGradient(0, scanY - 12, this.width, scanY, 0x00000000, withAlpha(themePrimaryColor, 0.15f));
        }

        HudRenderHelper.drawAmbientPhotonDust(g, this.width, this.height, now);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        MedicalData data = player.getData(MedicalDataCapability.MEDICAL_DATA);
        if (data == null) return;

        long now = System.currentTimeMillis();
        if (lastRenderTimeMs == 0L) lastRenderTimeMs = now;
        float dt = Mth.clamp((now - lastRenderTimeMs) / 1000.0f, 0.001f, 0.1f);
        lastRenderTimeMs = now;

        float rawAnim = updateScreenStateProgress(mc, now);
        if (state == ScreenState.CLOSED) return;

        updateOscilloscopeBuffers(data, dt);

        // 平滑插值生命体征与总体疼痛 (0~55)
        smoothHrDisplay = Mth.lerp(Math.min(1.0f, dt * 8.0f), smoothHrDisplay, data.getHeartRate());
        smoothBloodDisplay = Mth.lerp(Math.min(1.0f, dt * 8.0f), smoothBloodDisplay, data.getBloodVolume());
        smoothSpo2Display = Mth.lerp(Math.min(1.0f, dt * 8.0f), smoothSpo2Display, data.getBloodOxygen());
        smoothPerceivedPain = Mth.lerp(Math.min(1.0f, dt * 6.0f), smoothPerceivedPain, data.getPerceivedPain());

        // 更新由总体疼痛驱动的全局面板颜色
        updatePainDrivenThemeColors(now);

        // 1. 绘制受疼痛染色的全息背景网格与激光扫描线
        drawPainTintedBackground(graphics, now);

        if (state == ScreenState.OPENING && rawAnim < 0.45f) {
            float beamT = rawAnim / 0.45f;
            int halfSpan = (int) (this.height * 0.5f * EasingFunctions.easeOutCubic(beamT));
            int cy = this.height / 2;
            graphics.fill(0, cy - halfSpan, this.width, cy - halfSpan + 2, withAlpha(themePrimaryColor, 0.7f));
            graphics.fill(0, cy + halfSpan - 2, this.width, cy + halfSpan, withAlpha(themePrimaryColor, 0.7f));
        }

        float topBarProg = (state == ScreenState.CLOSING)
                ? EasingFunctions.easeInBack(rawAnim)
                : EasingFunctions.easeOutBack(MedicalAnimUtils.getStaggeredProgress(rawAnim, 0.0f, 0.60f));
        float leftPanelProg = (state == ScreenState.CLOSING)
                ? EasingFunctions.easeInCubic(rawAnim)
                : EasingFunctions.easeOutCubic(MedicalAnimUtils.getStaggeredProgress(rawAnim, 0.12f, 0.68f));
        float rightPanelProg = (state == ScreenState.CLOSING)
                ? EasingFunctions.easeInCubic(rawAnim)
                : EasingFunctions.easeOutBack(MedicalAnimUtils.getStaggeredProgress(rawAnim, 0.22f, 0.78f));

        // 极高疼痛 (>= 38) 时面板产生轻微的痛觉神经震颤微偏移
        float painJitterX = 0.0f;
        float painJitterY = 0.0f;
        if (smoothPerceivedPain >= 38.0f) {
            float jStrength = (smoothPerceivedPain - 38.0f) / 17.0f;
            painJitterX = Mth.sin(now * 0.045f) * 1.2f * jStrength;
            painJitterY = Mth.cos(now * 0.053f) * 0.9f * jStrength;
        }

        // 2. 顶部多维生命体征数码遥测顶栏
        graphics.pose().pushPose();
        float topOffsetY = (1.0f - topBarProg) * -(HUD_BAR_H + 16) + painJitterY * 0.5f;
        graphics.pose().translate(painJitterX * 0.5f, topOffsetY, 0.0f);
        drawTopBiometricBar(graphics, data, now);
        graphics.pose().popPose();

        // 3. 左侧 3A 全息解剖人偶与痛觉表情遥测舱
        graphics.pose().pushPose();
        float leftOffsetX = (1.0f - leftPanelProg) * -(LEFT_PANEL_W + 30) + painJitterX;
        graphics.pose().translate(leftOffsetX, painJitterY, 0.0f);
        drawLeftBodyScannerPanel(graphics, data, mouseX, mouseY, dt, now);
        graphics.pose().popPose();

        if (!openPulseTriggered && rawAnim >= 0.65f) {
            openPulseTriggered = true;
            int[] chestBox = limbScreenBounds.get(MedicalData.Limb.CHEST);
            if (chestBox != null && chestBox[2] > 0) {
                float cx = chestBox[0] + chestBox[2] * 0.5f;
                float cy = chestBox[1] + chestBox[3] * 0.5f;
                rippleManager.addUltrasoundPulse(cx, cy, 155.0f, themePrimaryColor, packAllLimbBoxes());
            }
        }

        // 4. 右侧全集成多模态详情与示波器舱
        int rightPanelX = LEFT_PANEL_X + LEFT_PANEL_W + 10;
        int rightPanelW = this.width - rightPanelX - 24;
        graphics.pose().pushPose();
        float rightOffsetX = (1.0f - rightPanelProg) * (rightPanelW + 40) + painJitterX;
        graphics.pose().translate(rightOffsetX, painJitterY, 0.0f);
        drawDetailPanel(graphics, data, rightPanelX, rightPanelW, mouseX, mouseY, dt, now);
        graphics.pose().popPose();

        if (rawAnim > 0.5f) {
            SciFiDataStreamRenderer.renderHorizontalDataTicker(
                    graphics, this.font, 16, this.height - 20, this.width - 32, now);
            SciFiDataStreamRenderer.renderVerticalHexStream(
                    graphics, this.font, this.width - 18, HUD_BAR_H + 14, this.height - HUD_BAR_H - 44, now);
        }

        rippleManager.updateAndRender(graphics, dt);
        updateAndRenderMicroTomography(graphics, data, mouseX, mouseY, dt, now);

        if (microTomographyLimb == null && hoveredLimb != null && rawAnim > 0.85f) {
            drawHoveredLimbTacticalCallout(graphics, data, hoveredLimb, mouseX, mouseY);
        }
    }

    private float updateScreenStateProgress(Minecraft mc, long now) {
        long elapsed = now - stateStartTimeMs;
        if (state == ScreenState.OPENING) {
            if (elapsed >= OPEN_ANIM_DURATION_MS) {
                state = ScreenState.OPEN;
                return 1.0f;
            }
            return Mth.clamp(elapsed / (float) OPEN_ANIM_DURATION_MS, 0.0f, 1.0f);
        } else if (state == ScreenState.CLOSING) {
            if (elapsed >= CLOSE_ANIM_DURATION_MS) {
                state = ScreenState.CLOSED;
                mc.setScreen(null);
                return 0.0f;
            }
            return 1.0f - Mth.clamp(elapsed / (float) CLOSE_ANIM_DURATION_MS, 0.0f, 1.0f);
        }
        return 1.0f;
    }

    // ============================================================
    // 二、顶部 3A 多维生命体征数码遥测舱 (含微型痛觉读数)
    // ============================================================
    private void drawTopBiometricBar(GuiGraphics g, MedicalData data, long now) {
        int barX = 16;
        int barY = 6;
        int barW = this.width - 32;

        HudRenderHelper.drawSciFiCard(g, barX, barY, barW, HUD_BAR_H, 5, themeCardBgColor, withAlpha(themePrimaryColor, 0.75f));
        HudRenderHelper.drawCardCorners(g, barX, barY, barW, HUD_BAR_H, 8, themePrimaryColor);

        HudRenderHelper.drawGlowingString(g, this.font, "生命体征监护",
                barX + 10, barY + 6, themePrimaryColor, withAlpha(themePrimaryColor, 0.4f));
        String subStatus = data.isCardiacArrest() ? "状态: 心脏骤停"
                : (data.isInVFib() ? "状态: 心室纤颤"
                : (data.isInCriticalState() ? "状态: 濒危 (" + data.getCriticalTimer() + "s)"
                : "疼痛: " + data.getPerceivedPain() + "/" + MedicalConfig.MAX_PERCEIVED_PAIN.get() + " (" + getPainStageText(data.getPerceivedPain()) + ") | 右键部位: CT"));
        int subColor = (data.isCardiacArrest() || data.isInVFib() || data.isInCriticalState())
                ? C_ALERT_RED : themePrimaryColor;
        g.drawString(this.font, subStatus, barX + 10, barY + 19, subColor, false);

        int metricsStartX = Math.max(barX + 235, barX + barW - 355);
        int availW = (barX + barW - 10) - metricsStartX;
        if (availW < 140) return;

        int slotW = availW / 4;

        int hr = Math.round(smoothHrDisplay);
        int hrColor = (data.isCardiacArrest() || data.isInVFib() || hr >= 150 || hr <= 45)
                ? C_ALERT_RED : (hr >= 115 ? C_WARN_YELLOW : C_SAFE_GREEN);
        drawDigitalVitalSlot(g, metricsStartX, barY + 4, slotW - 4, "HR (BPM)",
                String.format("%03d", Mth.clamp(hr, 0, 299)), hrColor);
        int pipAlpha = (int) (Mth.clamp(0.25f + rPeakFlashAlpha * 0.75f, 0.0f, 1.0f) * 255.0f);
        g.fill(metricsStartX + slotW - 12, barY + 7, metricsStartX + slotW - 7, barY + 12,
                (pipAlpha << 24) | (hrColor & 0x00FFFFFF));

        int sbp = data.getSystolicBP();
        int dbp = data.getDiastolicBP();
        float map = data.getMeanArterialPressure();
        int bpColor = (map < 55 || sbp >= 180) ? C_ALERT_RED
                : (map < 70 || sbp >= 145 ? C_WARN_YELLOW : C_VITAL_BP);
        drawDigitalVitalSlot(g, metricsStartX + slotW, barY + 4, slotW - 4, "BP (mmHg)",
                sbp + "/" + dbp, bpColor);

        int spo2 = Math.round(smoothSpo2Display);
        int spo2Color = spo2 < 80 ? C_ALERT_RED : (spo2 < 92 ? C_WARN_YELLOW : C_VITAL_OXYGEN);
        drawDigitalVitalSlot(g, metricsStartX + slotW * 2, barY + 4, slotW - 4, "SpO2 (%)",
                String.format("%02d", Mth.clamp(spo2, 0, 100)), spo2Color);

        int blood = Math.round(smoothBloodDisplay);
        int bloodColor = blood <= 3000 ? C_ALERT_RED : (blood <= 4000 ? C_WARN_YELLOW : C_SAFE_GREEN);
        drawDigitalVitalSlot(g, metricsStartX + slotW * 3, barY + 4, slotW - 4, "BLOOD (mL)",
                String.format("%04d", Mth.clamp(blood, 0, 9999)), bloodColor);
    }

    private void drawDigitalVitalSlot(GuiGraphics g, int x, int y, int w, String label, String digits, int color) {
        g.fill(x, y, x + w, y + 30, 0x88040B12);
        g.fill(x, y, x + 2, y + 30, color);
        g.drawString(this.font, label, x + 5, y + 2, 0xFF90A4AE, false);

        int dimColor = withAlpha(color, 0.14f);
        SegmentDisplayRenderer.renderString(g, digits, x + 6, y + 13, 6, 13, 2, 1, color, dimColor);
    }

    // ============================================================
    // 三、左侧 3A 全息解剖人偶 + 总体疼痛表情遥测舱
    // ============================================================
    private void drawLeftBodyScannerPanel(GuiGraphics g, MedicalData data, int mouseX, int mouseY, float dt, long now) {
        int panelX = LEFT_PANEL_X;
        int panelY = HUD_BAR_H + 14;
        int panelW = LEFT_PANEL_W;
        int panelH = this.height - HUD_BAR_H - 38;
        if (panelH < 100) return;

        drawCyberCard(g, panelX, panelY, panelW, panelH, "人体创伤定位", themePrimaryColor);

        String ctTxt = "CT 断层";
        this.ctBtnW = this.font.width(ctTxt) + 10;
        this.ctBtnH = 13;
        this.ctBtnX = panelX + panelW - ctBtnW - 8;
        this.ctBtnY = panelY + 5;
        boolean ctHov = mouseX >= ctBtnX && mouseX <= ctBtnX + ctBtnW && mouseY >= ctBtnY && mouseY <= ctBtnY + ctBtnH;
        HudRenderHelper.drawSciFiCard(g, ctBtnX, ctBtnY, ctBtnW, ctBtnH, 2,
                microTomographyLimb != null ? 0xCC006080 : (ctHov ? 0x990A2538 : 0x66050E17),
                ctHov ? themePrimaryColor : withAlpha(themePrimaryColor, 0.5f));
        g.drawString(this.font, ctTxt, ctBtnX + 5, ctBtnY + 3, themePrimaryColor, false);

        // 预留底部 46px 给 3A 总体疼痛动态表情遥测仪 (0~55 从笑脸到痛苦脸)
        int painWidgetH = 44;
        int bodyAreaTop = panelY + 23;
        int bodyAreaH = Math.max(70, panelH - 28 - painWidgetH);
        int cx = panelX + panelW / 2;
        int cy = bodyAreaTop + bodyAreaH / 2;

        float scale = Math.min(panelW / 150.0f, bodyAreaH / 185.0f);
        scale = Mth.clamp(scale, 0.48f, 1.25f);

        int headW = Math.round(32 * scale);
        int headH = Math.round(32 * scale);
        int chestW = Math.round(42 * scale);
        int chestH = Math.round(54 * scale);
        int armW = Math.round(20 * scale);
        int armH = Math.round(52 * scale);
        int legW = Math.round(19 * scale);
        int legH = Math.round(56 * scale);
        int gap = Math.max(2, Math.round(4 * scale));

        int chestX = cx - chestW / 2;
        int chestY = cy - chestH / 2 - Math.round(8 * scale);
        int headX = cx - headW / 2;
        int headY = chestY - headH - gap;

        int leftArmX = chestX - armW - gap;
        int leftArmY = chestY + 2;
        int rightArmX = chestX + chestW + gap;
        int rightArmY = chestY + 2;

        int legsY = chestY + chestH + gap;
        int leftLegX = cx - legW - Math.max(2, gap / 2);
        int rightLegX = cx + Math.max(2, gap / 2);

        limbScreenBounds.put(MedicalData.Limb.HEAD, new int[]{headX, headY, headW, headH});
        limbScreenBounds.put(MedicalData.Limb.CHEST, new int[]{chestX, chestY, chestW, chestH});
        limbScreenBounds.put(MedicalData.Limb.LEFT_ARM, new int[]{leftArmX, leftArmY, armW, armH});
        limbScreenBounds.put(MedicalData.Limb.RIGHT_ARM, new int[]{rightArmX, rightArmY, armW, armH});
        limbScreenBounds.put(MedicalData.Limb.LEFT_LEG, new int[]{leftLegX, legsY, legW, legH});
        limbScreenBounds.put(MedicalData.Limb.RIGHT_LEG, new int[]{rightLegX, legsY, legW, legH});

        drawAnatomicalVascularAndSkeleton(g, data, cx, chestY + chestH / 3, now);

        hoveredLimb = null;
        if (microTomographyLimb == null) {
            for (MedicalData.Limb limb : MedicalData.Limb.values()) {
                int[] b = limbScreenBounds.get(limb);
                if (mouseX >= b[0] && mouseX <= b[0] + b[2] && mouseY >= b[1] && mouseY <= b[1] + b[3]) {
                    hoveredLimb = limb;
                    break;
                }
            }
        }
        if (hoveredLimb != null && hoveredLimb != lastHoveredLimb) {
            MedicalSoundHelper.playHoverSound();
        }
        lastHoveredLimb = hoveredLimb;

        for (MedicalData.Limb limb : MedicalData.Limb.values()) {
            float targetHov = (limb == hoveredLimb || limb == selectedLimb) ? 1.0f : 0.0f;
            float curHov = MedicalAnimUtils.smoothDamp(limbHoverProgress.getOrDefault(limb, 0.0f), targetHov, 14.0f, dt);
            limbHoverProgress.put(limb, curHov);

            drawSingleLimbHologramBox(g, data, limb, curHov, now);
        }

        // 在左侧面板底部绘制 3A 全息总体疼痛动态表情遥测卡 (0 ~ 55 从笑脸到极度痛苦脸)
        int widgetY = panelY + panelH - painWidgetH - 5;
        drawPainExpressionWidget(g, panelX + 8, widgetY, panelW - 16, painWidgetH, data, now);
    }

    /**
     * 3A 全息总体疼痛动态表情遥测仪 (0 ~ 55 从笑脸到痛苦脸)
     */
    private void drawPainExpressionWidget(GuiGraphics g, int x, int y, int w, int h, MedicalData data, long now) {
        int rawPain = data.getPerceivedPain();
        float maxPainCap = Math.max(10.0f, MedicalConfig.MAX_PERCEIVED_PAIN.get().floatValue());
        float pNorm = Mth.clamp(smoothPerceivedPain / maxPainCap, 0.0f, 1.0f);
        int faceColor = getPainScaleColor(pNorm);

        HudRenderHelper.drawSciFiCard(g, x, y, w, h, 3, 0xCC040A12, withAlpha(faceColor, 0.7f));
        g.fill(x, y + 2, x + 2, y + h - 2, faceColor);

        // 1. 左侧：OpenGL 高精度连续形变全息人脸 (半径 r=16)
        float faceCX = x + 23.0f;
        float faceCY = y + h * 0.5f;
        float faceR = 15.5f;
        drawHolographicPainFace(g, faceCX, faceCY, faceR, smoothPerceivedPain, faceColor, now);

        // 2. 右侧：总体疼痛数值 (XX / 55)、表情阶段状态词与 6 段式 Wong-Baker 色阶进度条
        int infoX = x + 44;
        int infoW = w - 50;

        String title = "总体疼痛";
        g.drawString(this.font, title, infoX, y + 4, 0xFFB0BEC5, false);

        String valStr = rawPain + " / " + MedicalConfig.MAX_PERCEIVED_PAIN.get();
        g.drawString(this.font, valStr, infoX + infoW - this.font.width(valStr), y + 4, faceColor, false);

        String stageDesc = getPainStageText(rawPain);
        g.drawString(this.font, stageDesc, infoX, y + 15, faceColor, false);

        // 绘制 6 级临床痛觉色阶条 (0~55)
        int barY = y + 28;
        int segGap = 2;
        int numStages = 6;
        int segW = (infoW - (numStages - 1) * segGap) / numStages;
        int[] stageColors = new int[]{
                0xFF00FF80, // Stage 0: 无痛笑脸
                0xFF00E5FF, // Stage 1: 轻度平静
                0xFFFFEA00, // Stage 2: 中度不适
                0xFFFF9100, // Stage 3: 重度拧眉
                0xFFFF3D00, // Stage 4: 剧烈咬牙
                0xFFFF0055  // Stage 5: 痛觉崩溃
        };

        for (int i = 0; i < numStages; i++) {
            int sx = infoX + i * (segW + segGap);
            float segStart = (i / (float) numStages) * maxPainCap;
            float segEnd = ((i + 1) / (float) numStages) * maxPainCap;
            float fill = Mth.clamp((smoothPerceivedPain - segStart) / (segEnd - segStart), 0.0f, 1.0f);

            g.fill(sx, barY, sx + segW, barY + 6, 0xFF08121C);
            if (fill > 0.01f) {
                int fw = Math.max(1, (int) (segW * fill));
                g.fill(sx, barY, sx + fw, barY + 6, stageColors[i]);
            }
        }
    }

    /**
     * 使用 OpenGL 连续矢量几何绘制从「镇定微笑 (0)」到「极度痛苦抽搐 (55)」的全息表情人脸
     */
    private void drawHolographicPainFace(GuiGraphics g, float cx, float cy, float r,
                                         float painVal, int color, long now) {
        float maxPainCap = Math.max(10.0f, MedicalConfig.MAX_PERCEIVED_PAIN.get().floatValue());
        float pNorm = Mth.clamp(painVal / maxPainCap, 0.0f, 1.0f);

        // 高痛感下人脸产生剧痛抽搐颤抖
        if (pNorm > 0.45f) {
            float shake = (pNorm - 0.45f) * 2.2f;
            cx += Mth.sin(now * 0.055f) * shake;
            cy += Mth.cos(now * 0.068f) * shake * 0.8f;
        }

        Matrix4f pose = g.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        int cr = (color >>> 16) & 0xFF;
        int cg = (color >>> 8) & 0xFF;
        int cb = color & 0xFF;

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        // 1. 绘制全息人脸外轮廓环 (32 段平滑圆环 + 太阳穴生物电极刻度)
        int ringSegs = 32;
        for (int i = 0; i < ringSegs; i++) {
            float a1 = (i / (float) ringSegs) * ((float) Math.PI * 2.0f);
            float a2 = ((i + 1) / (float) ringSegs) * ((float) Math.PI * 2.0f);
            float x1 = cx + Mth.cos(a1) * r;
            float y1 = cy + Mth.sin(a1) * r;
            float x2 = cx + Mth.cos(a2) * r;
            float y2 = cy + Mth.sin(a2) * r;
            PainVisualOverlay.addQuadSegment(buf, pose, x1, y1, x2, y2, 0.95f, cr, cg, cb, 215, 215);
            PainVisualOverlay.addQuadSegment(buf, pose, x1, y1, x2, y2, 2.2f, cr, cg, cb, 45, 45);
        }

        // 2. 绘制双眉：随疼痛值从舒展平缓 (pain=0) -> 紧蹙八字/倒八字怒蹙 (pain=55)
        // browTilt: 0 时微微柔和弯拱，越高时眉头越往下压、眉尾挑起并剧烈紧缩
        float browTilt = Mth.clamp((painVal - 6.0f) / 35.0f, -0.15f, 1.0f);
        for (int side = -1; side <= 1; side += 2) {
            float innerX = cx + side * (r * 0.16f);
            float outerX = cx + side * (r * 0.56f);
            float innerY = cy - r * 0.28f + browTilt * (r * 0.18f);
            float outerY = cy - r * 0.32f - browTilt * (r * 0.10f);
            float midX = (innerX + outerX) * 0.5f;
            float midY = (innerY + outerY) * 0.5f - (1.0f - Math.abs(browTilt)) * (r * 0.06f);

            PainVisualOverlay.addQuadSegment(buf, pose, innerX, innerY, midX, midY, 0.9f, cr, cg, cb, 240, 240);
            PainVisualOverlay.addQuadSegment(buf, pose, midX, midY, outerX, outerY, 0.85f, cr, cg, cb, 240, 200);
        }

        // 3. 绘制双眼：
        // - pain < 28: 圆润全息眼眸（带自然周期眨眼与半眯紧绷收缩）
        // - pain >= 28: 剧痛紧闭的 `>   <` 折角痛苦眼睑！
        float eyeCenterY = cy - r * 0.10f;
        float eyeOffsetX = r * 0.34f;
        boolean blinking = (painVal < 15.0f) && ((now % 3800L) < 130L);

        if (painVal < 28.0f) {
            float squint = blinking ? 0.1f : Mth.clamp(1.0f - (painVal / 36.0f), 0.25f, 1.0f);
            float eyeRx = r * 0.15f;
            float eyeRy = r * 0.15f * squint;

            for (int side = -1; side <= 1; side += 2) {
                float ex = cx + side * eyeOffsetX;
                int eSegs = 12;
                for (int i = 0; i < eSegs; i++) {
                    float a1 = (i / (float) eSegs) * ((float) Math.PI * 2.0f);
                    float a2 = ((i + 1) / (float) eSegs) * ((float) Math.PI * 2.0f);
                    PainVisualOverlay.addQuadSegment(buf, pose,
                            ex + Mth.cos(a1) * eyeRx, eyeCenterY + Mth.sin(a1) * eyeRy,
                            ex + Mth.cos(a2) * eyeRx, eyeCenterY + Mth.sin(a2) * eyeRy,
                            0.85f, cr, cg, cb, 245, 245);
                }
                // 瞳孔高光点
                if (!blinking) {
                    float pr = r * 0.05f;
                    buf.addVertex(pose, ex - pr, eyeCenterY - pr, 0.0f).setColor(255, 255, 255, 230);
                    buf.addVertex(pose, ex + pr, eyeCenterY - pr, 0.0f).setColor(255, 255, 255, 230);
                    buf.addVertex(pose, ex + pr, eyeCenterY + pr, 0.0f).setColor(255, 255, 255, 230);
                    buf.addVertex(pose, ex - pr, eyeCenterY + pr, 0.0f).setColor(255, 255, 255, 230);
                }
            }
        } else {
            // 重度/极度疼痛 (>= 28)：紧闭痛苦折角眼 `>  <`
            float winceSpan = r * 0.18f;
            float winceH = r * 0.13f;
            // 左眼 `>`
            float lx = cx - eyeOffsetX;
            PainVisualOverlay.addQuadSegment(buf, pose, lx - winceSpan, eyeCenterY - winceH, lx + winceSpan * 0.6f, eyeCenterY, 1.05f, cr, cg, cb, 255, 255);
            PainVisualOverlay.addQuadSegment(buf, pose, lx + winceSpan * 0.6f, eyeCenterY, lx - winceSpan, eyeCenterY + winceH, 1.05f, cr, cg, cb, 255, 255);
            // 右眼 `<`
            float rx = cx + eyeOffsetX;
            PainVisualOverlay.addQuadSegment(buf, pose, rx + winceSpan, eyeCenterY - winceH, rx - winceSpan * 0.6f, eyeCenterY, 1.05f, cr, cg, cb, 255, 255);
            PainVisualOverlay.addQuadSegment(buf, pose, rx - winceSpan * 0.6f, eyeCenterY, rx + winceSpan, eyeCenterY + winceH, 1.05f, cr, cg, cb, 255, 255);
        }

        // 4. 绘制嘴部：随总体疼痛 (0~55) 从「上扬笑脸」 -> 「平直」 -> 「下撇撇嘴」 -> 「咬牙切齿/哀嚎张口」
        float mouthY = cy + r * 0.34f;
        float mouthHalfW = r * (0.38f + 0.12f * pNorm);
        // smileFactor: pain=0 时为 +1.0 (标准微笑上扬)，pain=12 时为 0 (平线)，pain>=24 时为 -1.0 (痛苦下弯)
        float smileFactor = Mth.clamp(1.0f - (painVal / 12.0f), -1.15f, 1.0f);
        int mSegs = 12;

        if (painVal < 26.0f) {
            // 连续弧线嘴（微笑 -> 平静 -> 痛苦下弯）
            for (int i = 0; i < mSegs; i++) {
                float t1 = (i / (float) mSegs) * 2.0f - 1.0f;       // [-1, 1]
                float t2 = ((i + 1) / (float) mSegs) * 2.0f - 1.0f;
                float mx1 = cx + t1 * mouthHalfW;
                float mx2 = cx + t2 * mouthHalfW;
                // 抛物线曲率：(1 - t^2) 决定中间凹凸
                float my1 = mouthY + (1.0f - t1 * t1) * (r * 0.22f) * smileFactor;
                float my2 = mouthY + (1.0f - t2 * t2) * (r * 0.22f) * smileFactor;
                PainVisualOverlay.addQuadSegment(buf, pose, mx1, my1, mx2, my2, 1.0f, cr, cg, cb, 245, 245);
            }
        } else {
            // 重度/极度疼痛 (pain >= 26)：张口咬牙切齿 / 波浪抽搐哀嚎嘴框
            float openH = r * (0.10f + 0.14f * ((painVal - 26.0f) / 29.0f));
            for (int i = 0; i < mSegs; i++) {
                float t1 = (i / (float) mSegs) * 2.0f - 1.0f;
                float t2 = ((i + 1) / (float) mSegs) * 2.0f - 1.0f;
                float mx1 = cx + t1 * mouthHalfW;
                float mx2 = cx + t2 * mouthHalfW;

                float arch1 = (1.0f - t1 * t1);
                float arch2 = (1.0f - t2 * t2);
                // 上唇微拱，下唇深凹张开
                float topY1 = mouthY - arch1 * openH * 0.7f;
                float topY2 = mouthY - arch2 * openH * 0.7f;
                float botY1 = mouthY + openH * 0.6f - arch1 * openH * 0.2f;
                float botY2 = mouthY + openH * 0.6f - arch2 * openH * 0.2f;

                PainVisualOverlay.addQuadSegment(buf, pose, mx1, topY1, mx2, topY2, 0.9f, cr, cg, cb, 250, 250);
                PainVisualOverlay.addQuadSegment(buf, pose, mx1, botY1, mx2, botY2, 0.9f, cr, cg, cb, 250, 250);
            }
            // 左右嘴角闭合与内部紧咬牙列竖线
            PainVisualOverlay.addQuadSegment(buf, pose, cx - mouthHalfW, mouthY, cx - mouthHalfW, mouthY + openH * 0.6f, 0.85f, cr, cg, cb, 240, 240);
            PainVisualOverlay.addQuadSegment(buf, pose, cx + mouthHalfW, mouthY, cx + mouthHalfW, mouthY + openH * 0.6f, 0.85f, cr, cg, cb, 240, 240);
            for (int tooth = -2; tooth <= 2; tooth++) {
                float tx = cx + (tooth / 3.0f) * mouthHalfW;
                PainVisualOverlay.addQuadSegment(buf, pose, tx, mouthY - openH * 0.35f, tx, mouthY + openH * 0.45f, 0.55f, cr, cg, cb, 160, 160);
            }
        }

        // 5. 当疼痛 >= 32 时，在额角绘制暴起的痛觉青筋十字与电脉冲芒刺
        if (painVal >= 32.0f) {
            float vx = cx + r * 0.58f;
            float vy = cy - r * 0.55f;
            float vs = r * 0.20f * (0.8f + 0.3f * Mth.sin(now * 0.015f));
            PainVisualOverlay.addQuadSegment(buf, pose, vx - vs, vy, vx + vs, vy, 0.85f, 255, 30, 70, 240, 240);
            PainVisualOverlay.addQuadSegment(buf, pose, vx, vy - vs, vx, vy + vs, 0.85f, 255, 30, 70, 240, 240);
        }

        MeshData mesh = buf.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private int getPainScaleColor(float pNorm) {
        if (pNorm < 0.18f) return lerpColor(0xFF00FF80, 0xFF00E5FF, pNorm / 0.18f);
        if (pNorm < 0.42f) return lerpColor(0xFF00E5FF, 0xFFFFD000, (pNorm - 0.18f) / 0.24f);
        if (pNorm < 0.70f) return lerpColor(0xFFFFD000, 0xFFFF3D00, (pNorm - 0.42f) / 0.28f);
        return lerpColor(0xFFFF3D00, 0xFFFF0055, (pNorm - 0.70f) / 0.30f);
    }

    private String getPainStageText(int pain) {
        float maxCap = Math.max(10.0f, MedicalConfig.MAX_PERCEIVED_PAIN.get().floatValue());
        float r = pain / maxCap;
        if (r <= 0.05f) return "无痛";
        if (r <= 0.22f) return "轻度";
        if (r <= 0.42f) return "中度";
        if (r <= 0.65f) return "重度";
        if (r <= 0.85f) return "剧痛";
        return "极度剧痛";
    }

    private void drawAnatomicalVascularAndSkeleton(GuiGraphics g, MedicalData data, int heartX, int heartY, long now) {
        Matrix4f pose = g.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        float bpm = Mth.clamp(data.getHeartRate(), 35.0f, 220.0f);
        float pulsePhase = ((now % (long) (60000.0f / bpm)) / (60000.0f / bpm));

        int[] head = limbScreenBounds.get(MedicalData.Limb.HEAD);
        int[] chest = limbScreenBounds.get(MedicalData.Limb.CHEST);
        int[] lArm = limbScreenBounds.get(MedicalData.Limb.LEFT_ARM);
        int[] rArm = limbScreenBounds.get(MedicalData.Limb.RIGHT_ARM);
        int[] lLeg = limbScreenBounds.get(MedicalData.Limb.LEFT_LEG);
        int[] rLeg = limbScreenBounds.get(MedicalData.Limb.RIGHT_LEG);

        int hx = chest[0] + chest[2] / 2;
        int hy = chest[1] + chest[3] / 3;
        int pelvisY = chest[1] + chest[3] - 6;

        int[][] vessels = new int[][]{
                {hx, hy, head[0] + head[2] / 2, head[1] + head[3] / 2},
                {hx, hy, lArm[0] + lArm[2] / 2, lArm[1] + lArm[3] * 2 / 3},
                {hx, hy, rArm[0] + rArm[2] / 2, rArm[1] + rArm[3] * 2 / 3},
                {hx, hy, hx, pelvisY},
                {hx, pelvisY, lLeg[0] + lLeg[2] / 2, lLeg[1] + lLeg[3] * 3 / 4},
                {hx, pelvisY, rLeg[0] + rLeg[2] / 2, rLeg[1] + rLeg[3] * 3 / 4}
        };

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        for (int[] v : vessels) {
            PainVisualOverlay.addQuadSegment(buf, pose, v[0], v[1], v[2], v[3], 1.1f, 0, 180, 220, 55, 35);

            if (!data.isCardiacArrest()) {
                float pHead = pulsePhase;
                float pTail = Math.max(0.0f, pHead - 0.28f);
                float px1 = Mth.lerp(pTail, v[0], v[2]);
                float py1 = Mth.lerp(pTail, v[1], v[3]);
                float px2 = Mth.lerp(pHead, v[0], v[2]);
                float py2 = Mth.lerp(pHead, v[1], v[3]);
                PainVisualOverlay.addQuadSegment(buf, pose, px1, py1, px2, py2, 1.8f, 255, 45, 75, 0, 210);
            }
        }

        MeshData mesh = buf.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
    }

    private void drawSingleLimbHologramBox(GuiGraphics g, MedicalData data, MedicalData.Limb limb, float hoverProg, long now) {
        MedicalData.LimbData ld = data.getLimb(limb);
        int[] baseBox = limbScreenBounds.get(limb);
        if (ld == null || baseBox == null) return;

        int expand = Math.round(hoverProg * 2.5f);
        int x = baseBox[0] - expand;
        int y = baseBox[1] - expand;
        int w = baseBox[2] + expand * 2;
        int h = baseBox[3] + expand * 2;

        boolean isSelected = (limb == selectedLimb);
        int limbColor = getLimbColor(ld);

        float alertPulse = 1.0f;
        if (ld.getBleeding() > 100 || ld.getInternalBleedingRate() > 100 || ld.hasFracture()) {
            alertPulse = 0.65f + 0.35f * Mth.sin(now * 0.008f);
        }

        int bgAlpha = isSelected ? 0x88 : (hoverProg > 0.1f ? 0x66 : 0x44);
        int bgColor = ((int) (bgAlpha * alertPulse) << 24) | (limbColor & 0x00FFFFFF);
        int borderColor = isSelected ? 0xFFFFFFFF : (hoverProg > 0.2f ? themePrimaryColor : limbColor);

        if (isSelected || hoverProg > 0.05f) {
            int glowAlpha = (int) ((isSelected ? 0.45f : hoverProg * 0.30f) * 255.0f);
            int glowCol = (glowAlpha << 24) | (borderColor & 0x00FFFFFF);
            g.fill(x - 2, y - 2, x + w + 2, y + h + 2, glowCol);
        }

        HudRenderHelper.drawSciFiCard(g, x, y, w, h, 3, bgColor, borderColor);

        int midX = x + w / 2;
        int boneTop = y + 6;
        int boneBot = y + h - 6;
        if (ld.isAmputated()) {
            HudRenderHelper.drawLine(g, x + 4, y + 4, x + w - 4, y + h - 4, C_ALERT_RED, 1);
            HudRenderHelper.drawLine(g, x + w - 4, y + 4, x + 4, y + h - 4, C_ALERT_RED, 1);
        } else if (ld.hasFracture()) {
            int breakY = (boneTop + boneBot) / 2;
            int fracCol = ld.hasSplint() ? C_SAFE_GREEN : C_ALERT_RED;
            HudRenderHelper.drawLine(g, midX, boneTop, midX - 3, breakY - 2, fracCol, 1);
            HudRenderHelper.drawLine(g, midX - 3, breakY - 2, midX + 3, breakY + 2, 0xFFFFFFFF, 1);
            HudRenderHelper.drawLine(g, midX + 3, breakY + 2, midX, boneBot, fracCol, 1);
        } else {
            g.fill(midX, boneTop, midX + 1, boneBot, 0x44E0F7FF);
        }

        if (ld.getBleeding() > 0) {
            if (ld.hasPerforation()) {
                g.fill(x + w - 6, y + 2, x + w - 2, y + 6, 0xFFFF2A85);
                g.fill(x + w - 4, y + 2, x + w - 2, y + 4, 0xFFFF9100);
            } else {
                g.fill(x + w - 6, y + 2, x + w - 2, y + 6, 0xFFFF1744);
            }
        }
        if (ld.getInternalBleedingRate() > 0) {
            int px = (ld.getBleeding() > 0) ? (x + w - 12) : (x + w - 6);
            g.fill(px, y + 2, px + 4, y + 6, 0xFFAB47BC);
        }
        if (com.FIYTP.realmedicalmod.treatment.ModItems.getEmbeddedBulletCount(ld) > 0) {
            g.fill(x + w - 12, y + h - 6, x + w - 8, y + h - 2, 0xFFFFC400);
        }
        if (ld.isTourniqueted()) {
            g.fill(x + 2, y + 2, x + 6, y + 6, 0xFFFF9100);
        }
        if (ld.isBandaged()) {
            g.fill(x + 2, y + h - 6, x + 6, y + h - 2, 0xFF00E676);
        }
        if (ld.hasFracture() || ld.getInfection() >= 25) {
            g.drawString(this.font, "!", x + w - 6, y + h - 9, 0xFFFFD600, false);
        }

        int hp = BrainScan3DRenderer.computeLimbHealthPercent(ld);
        String hpTxt = hp + "%";
        int tw = this.font.width(hpTxt);
        if (w >= tw + 4 && h >= 18) {
            g.drawString(this.font, hpTxt, x + (w - tw) / 2, y + (h - 7) / 2, 0xEEFFFFFF, false);
        }
    }

    private void drawHoveredLimbTacticalCallout(GuiGraphics g, MedicalData data, MedicalData.Limb limb, int mouseX, int mouseY) {
        MedicalData.LimbData ld = data.getLimb(limb);
        int[] box = limbScreenBounds.get(limb);
        if (ld == null || box == null) return;

        int anchorX = box[0] + box[2];
        int anchorY = box[1] + box[3] / 2;
        int cardX = Math.min(this.width - 135, anchorX + 18);
        int cardY = Mth.clamp(anchorY - 16, HUD_BAR_H + 16, this.height - 48);

        int color = getLimbColor(ld);
        HudRenderHelper.drawSciFiLeaderLine(g, anchorX, anchorY, cardX, cardY + 12, color, withAlpha(color, 0.4f));

        int embCnt = com.FIYTP.realmedicalmod.treatment.ModItems.getEmbeddedBulletCount(ld);
        String perfTag = ld.hasPerforation() ? " [贯穿]" : (embCnt > 0 ? " [嵌弹×" + embCnt + "]" : "");
        String title = getLimbFullName(limb) + " [" + BrainScan3DRenderer.computeLimbHealthPercent(ld) + "%]" + perfTag;
        String sub = "外:" + ld.getBleeding() + " 内:" + ld.getInternalBleedingRate() + " 痛:" + ld.getPainLevel();
        HudRenderHelper.drawTacticalBadge(g, this.font, cardX, cardY, 122, 24, title, sub, color, 0xEE050D17);
    }

    // ============================================================
    // 四、3A 级 4 层高精度解剖级 DICOM 微观断层 CT 扫描仪
    // ============================================================
    private void updateAndRenderMicroTomography(GuiGraphics g, MedicalData data, int mouseX, int mouseY, float dt, long now) {
        float target = (microTomographyLimb != null) ? 1.0f : 0.0f;
        microTomographyAnim = MedicalAnimUtils.smoothDamp(microTomographyAnim, target, 14.0f, dt);
        if (microTomographyAnim < 0.02f || microTomographyLimb == null) return;

        MedicalData.LimbData ld = data.getLimb(microTomographyLimb);
        if (ld == null) return;

        int boxW = Math.min(386, this.width - 40);
        int boxH = Math.min(232, this.height - 50);
        int boxX = (this.width - boxW) / 2;
        int boxY = (this.height - boxH) / 2;

        int dimAlpha = (int) (microTomographyAnim * 195.0f);
        g.fill(0, 0, this.width, this.height, (dimAlpha << 24) | 0x02060A);

        g.pose().pushPose();
        float scale = 0.85f + 0.15f * EasingFunctions.easeOutBack(microTomographyAnim);
        g.pose().translate(boxX + boxW * 0.5f, boxY + boxH * 0.5f, 0.0f);
        g.pose().scale(scale, scale, 1.0f);
        g.pose().translate(-(boxX + boxW * 0.5f), -(boxY + boxH * 0.5f), 0.0f);

        String title = "CT 断层 · " + getLimbFullName(microTomographyLimb);
        drawCyberCard(g, boxX, boxY, boxW, boxH, title, themePrimaryColor);

        // 顶部层态导航栏 + [< PREV] [NEXT >] 切片按钮
        int curLayerIdx = Math.floorMod(dcmLayer, DICOM_LAYER_NAMES.length);
        String layerName = DICOM_LAYER_NAMES[curLayerIdx];
        g.drawString(this.font, layerName, boxX + 12, boxY + 25, C_WARN_YELLOW, false);

        this.layerBtnW = 22;
        this.layerBtnH = 12;
        this.layerBtnY = boxY + 23;
        this.layerBtnNextX = boxX + boxW - 34;
        this.layerBtnPrevX = this.layerBtnNextX - 26;
        HudRenderHelper.drawSciFiCard(g, layerBtnPrevX, layerBtnY, layerBtnW, layerBtnH, 2, 0x88071624, themePrimaryColor);
        HudRenderHelper.drawSciFiCard(g, layerBtnNextX, layerBtnY, layerBtnW, layerBtnH, 2, 0x88071624, themePrimaryColor);
        g.drawString(this.font, "<", layerBtnPrevX + 8, layerBtnY + 2, 0xFFFFFFFF, false);
        g.drawString(this.font, ">", layerBtnNextX + 8, layerBtnY + 2, 0xFFFFFFFF, false);

        // 左侧：高分辨率圆形 CT 断层扫描视窗 (含 HU 标尺、极坐标刻度与 4 层解剖几何绘制)
        int scopeX = boxX + 12;
        int scopeY = boxY + 40;
        int scopeSize = Math.min(162, boxH - 60);
        g.fill(scopeX, scopeY, scopeX + scopeSize, scopeY + scopeSize, 0xE602070D);
        HudRenderHelper.drawSciFiBorder(g, scopeX, scopeY, scopeSize, scopeSize, withAlpha(themePrimaryColor, 0.75f));

        // 左侧边缘 HU (亨氏单位) 密度标尺条
        for (int s = 0; s < 10; s++) {
            int sy = scopeY + 12 + s * ((scopeSize - 24) / 10);
            int gray = 35 + s * 22;
            int huCol = (0xCC << 24) | (gray << 16) | (Math.min(255, gray + 20) << 8) | 255;
            g.fill(scopeX + 4, sy, scopeX + 8, sy + 6, huCol);
        }
        g.drawString(this.font, "HU", scopeX + 3, scopeY + 3, 0xFF546E7A, false);

        float radarCX = scopeX + scopeSize * 0.52f;
        float radarCY = scopeY + scopeSize * 0.50f;
        float radarR = scopeSize * 0.39f;

        // 调用高精度 OpenGL 解剖断层渲染器
        drawHighPrecisionTomographySlice(g, radarCX, radarCY, radarR, curLayerIdx, microTomographyLimb, data, ld, now);

        // 切片切换激光纵向擦除扫描光束
        float wipeProg = Mth.clamp((now - dcmWipeStartMs) / 480.0f, 0.0f, 1.0f);
        if (wipeProg < 1.0f) {
            int wipeY = scopeY + (int) (scopeSize * wipeProg);
            g.fill(scopeX + 2, wipeY, scopeX + scopeSize - 2, wipeY + 2, 0xFF00F0FF);
            g.fillGradient(scopeX + 2, Math.max(scopeY, wipeY - 14), scopeX + scopeSize - 2, wipeY, 0x0000F0FF, 0x5500F0FF);
        }

        // 右侧：分层定量诊断报告与 4 层指示器
        int rightX = scopeX + scopeSize + 10;
        int rightY = scopeY;
        int rightW = boxX + boxW - rightX - 12;

        // 4 层切片深度进度指示器
        int pillW = (rightW - 9) / 4;
        for (int l = 0; l < 4; l++) {
            int px = rightX + l * (pillW + 3);
            boolean active = (l == curLayerIdx);
            g.fill(px, rightY, px + pillW, rightY + 10, active ? themePrimaryColor : 0x550A1926);
            String lNum = "L0" + (l + 1);
            g.drawString(this.font, lNum, px + (pillW - this.font.width(lNum)) / 2, rightY + 1,
                    active ? 0xFF000000 : 0xFF90A4AE, false);
        }

        int metricY = rightY + 16;
        switch (curLayerIdx) {
            case 0 -> {
                drawTomographyMetric(g, rightX, metricY, rightW, "开放创口",
                        ld.hasOpenWound() || ld.getBleeding() > 0 ? "存在" : "完整",
                        ld.getBleeding() > 0 ? C_ALERT_RED : C_SAFE_GREEN);
                drawTomographyMetric(g, rightX, metricY + 28, rightW, "敷料包扎",
                        ld.isBandaged() ? tr("medical.detail.bandaged") : tr("medical.detail.unbandaged"),
                        ld.isBandaged() ? C_SAFE_GREEN : 0xFF90A4AE);
                drawTomographyMetric(g, rightX, metricY + 56, rightW, "烧伤",
                        ld.hasBurn() ? String.format("%d° (%.1f%%)", ld.getBurnDegree(), ld.getBurnArea()) : "无",
                        ld.hasBurn() ? 0xFFFF6D00 : C_SAFE_GREEN);
                drawTomographyMetric(g, rightX, metricY + 84, rightW, "凝血计时",
                        ld.getNaturalClottingTimer() > 0 ? ld.getNaturalClottingTimer() + " s" : "待命",
                        C_NEON_CYAN);
            }
            case 1 -> {
                drawTomographyMetric(g, rightX, metricY, rightW, tr("medical.detail.bleeding_rate"),
                        ld.getBleeding() + " mL/min",
                        ld.getBleeding() > 0 ? C_ALERT_RED : C_SAFE_GREEN);
                drawTomographyMetric(g, rightX, metricY + 28, rightW, tr("medical.detail.internal_bleeding_rate"),
                        ld.getInternalBleedingRate() + " mL/min",
                        ld.getInternalBleedingRate() > 0 ? 0xFFE040FB : C_SAFE_GREEN);
                drawTomographyMetric(g, rightX, metricY + 56, rightW, tr("medical.detail.tourniquet"),
                        ld.isTourniqueted() ? tr("medical.detail.bound") : tr("medical.detail.unbound"),
                        ld.isTourniqueted() ? C_WARN_YELLOW : 0xFF90A4AE);
                drawTomographyMetric(g, rightX, metricY + 84, rightW, "局部灌注 MAP",
                        String.format("%.0f mmHg (CO %.1f)", data.getMeanArterialPressure(), data.getCardiacOutput()),
                        C_VITAL_BP);
            }
            case 2 -> {
                boolean orthoFixed = com.FIYTP.realmedicalmod.treatment.ModItems.isLimbOrthopedicFixed(ld);
                boolean chestBraced = com.FIYTP.realmedicalmod.treatment.ModItems.isChestBraced(ld);
                String fracState = !ld.hasFracture() ? tr("medical.status.healthy")
                        : (orthoFixed ? "骨科内/外固定已锁定"
                        : (ld.isOpenFracture() ? (selectedLimb == MedicalData.Limb.CHEST ? "连枷胸 (浮动胸壁)" : tr("medical.status.open_fracture"))
                        : (ld.hasSplint() ? tr("medical.status.fracture_splinted")
                        : (chestBraced ? "肋骨骨折 (胸带支撑)" : tr("medical.status.fracture")))));
                drawTomographyMetric(g, rightX, metricY, rightW, tr("medical.detail.fracture"),
                        fracState, ld.hasFracture() ? (orthoFixed ? C_NEON_CYAN : C_ALERT_RED) : C_SAFE_GREEN);
                drawTomographyMetric(g, rightX, metricY + 28, rightW, "关节对位",
                        ld.hasDislocation() ? tr("medical.status.dislocation") : "正常",
                        ld.hasDislocation() ? C_WARN_YELLOW : C_SAFE_GREEN);
                drawTomographyMetric(g, rightX, metricY + 56, rightW, tr("medical.detail.fracture_healing"),
                        ld.hasFracture() ? (ld.getFractureTimer() > 0 ? ld.getFractureTimer() + " s" : "需手术/固定") : "完整", C_NEON_CYAN);
                String fixText = orthoFixed ? "钛合金固定架/环抱器" : (ld.hasSplint() ? "铝合金夹板" : (chestBraced ? "加压弹性胸带" : "无"));
                drawTomographyMetric(g, rightX, metricY + 84, rightW, "骨科固定",
                        fixText,
                        (orthoFixed || ld.hasSplint() || chestBraced) ? C_SAFE_GREEN : 0xFF90A4AE);
            }
            default -> {
                drawTomographyMetric(g, rightX, metricY, rightW, tr("medical.detail.infection"),
                        ld.getInfection() + " %",
                        ld.getInfection() > 0 ? C_WARN_YELLOW : C_SAFE_GREEN);
                drawTomographyMetric(g, rightX, metricY + 28, rightW, tr("medical.detail.pain_level"),
                        ld.getPainLevel() + " / 10",
                        ld.getPainLevel() > 0 ? C_VITAL_PAIN : C_SAFE_GREEN);
                int tomoEmb = com.FIYTP.realmedicalmod.treatment.ModItems.getEmbeddedBulletCount(ld);
                boolean tomoPacked = com.FIYTP.realmedicalmod.treatment.ModItems.isPerforationPacked(ld);
                String ballisticTxt = ld.hasPerforation()
                        ? (tomoPacked ? "前后贯穿(已塞纱)" : "前后贯穿(双创口)")
                        : (tomoEmb > 0 ? ("盲管嵌弹×" + tomoEmb) : "无弹道存留");
                int ballisticCol = ld.hasPerforation() ? 0xFFFF2A85 : (tomoEmb > 0 ? 0xFFFFC400 : C_SAFE_GREEN);
                drawTomographyMetric(g, rightX, metricY + 56, rightW, "弹道创道",
                        ballisticTxt, ballisticCol);
                drawTomographyMetric(g, rightX, metricY + 84, rightW, "SIRS 等级",
                        data.getSirsLevel() + " / 4",
                        data.getSirsLevel() > 0 ? C_ALERT_RED : C_SAFE_GREEN);
            }
        }

        g.drawString(this.font, "滚轮切换切片 | ESC 关闭",
                boxX + 12, boxY + boxH - 14, 0xFF80CBC4, false);

        g.pose().popPose();
    }

    /**
     * 高精度 OpenGL 4 层解剖横截面 CT 渲染器（彻底告别粗糙菱形）
     */
    private void drawHighPrecisionTomographySlice(GuiGraphics g, float cx, float cy, float r,
                                                  int layer, MedicalData.Limb limb,
                                                  MedicalData data, MedicalData.LimbData ld, long now) {
        Matrix4f pose = g.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        float timeSec = (now % 60000L) / 1000.0f;
        int segs = 56;

        // 0. 绘制外围 CT 旋转扫描机架刻度环与旋转激光游标
        for (int i = 0; i < segs; i++) {
            float a1 = (i / (float) segs) * ((float) Math.PI * 2.0f);
            float a2 = ((i + 1) / (float) segs) * ((float) Math.PI * 2.0f);
            float gR = r * 1.08f;
            PainVisualOverlay.addQuadSegment(buf, pose,
                    cx + Mth.cos(a1) * gR, cy + Mth.sin(a1) * gR,
                    cx + Mth.cos(a2) * gR, cy + Mth.sin(a2) * gR,
                    0.5f, 0, 190, 235, 65, 65);
            if (i % 7 == 0) {
                PainVisualOverlay.addQuadSegment(buf, pose,
                        cx + Mth.cos(a1) * (gR - 3.0f), cy + Mth.sin(a1) * (gR - 3.0f),
                        cx + Mth.cos(a1) * (gR + 2.0f), cy + Mth.sin(a1) * (gR + 2.0f),
                        0.75f, 0, 240, 255, 150, 150);
            }
        }
        // 旋转 CT 机架投影径向扫描线
        float gantryAng = timeSec * 1.6f;
        PainVisualOverlay.addQuadSegment(buf, pose,
                cx, cy, cx + Mth.cos(gantryAng) * r * 1.06f, cy + Mth.sin(gantryAng) * r * 1.06f,
                0.8f, 0, 240, 255, 110, 15);

        switch (layer) {
            case 0 -> {
                // ========== LAYER 01: 表皮、真皮乳头层、皮下脂肪蜂窝网与创口/敷料 ==========
                boolean hasWound = ld.getBleeding() > 0 || ld.hasOpenWound();
                float burnRatio = ld.hasBurn() ? Mth.clamp(ld.getBurnArea() / 30.0f, 0.15f, 1.0f) : 0.0f;

                for (int i = 0; i < segs; i++) {
                    float u1 = i / (float) segs;
                    float u2 = (i + 1) / (float) segs;
                    float a1 = u1 * ((float) Math.PI * 2.0f);
                    float a2 = u2 * ((float) Math.PI * 2.0f);

                    // 解剖轮廓轻微椭圆塑造
                    float shape1 = 0.92f + 0.08f * Mth.cos(a1 * 2.0f);
                    float shape2 = 0.92f + 0.08f * Mth.cos(a2 * 2.0f);

                    // 若有开放性出血创口，在右上象限 (a ∈ [0.5, 0.95]) 形成深 V 型表皮撕裂缺口
                    float cleft1 = (hasWound && a1 > 0.55f && a1 < 0.95f)
                            ? (1.0f - 0.32f * Mth.sin(((a1 - 0.55f) / 0.40f) * (float) Math.PI)) : 1.0f;
                    float cleft2 = (hasWound && a2 > 0.55f && a2 < 0.95f)
                            ? (1.0f - 0.32f * Mth.sin(((a2 - 0.55f) / 0.40f) * (float) Math.PI)) : 1.0f;

                    float rEpi1 = r * 0.88f * shape1 * cleft1;
                    float rEpi2 = r * 0.88f * shape2 * cleft2;

                    // 烧伤区域染色为焦橙红，正常表皮为翡翠青
                    boolean inBurnArc = (u1 < burnRatio);
                    int er = inBurnArc ? 255 : (cleft1 < 0.98f ? 255 : 0);
                    int eg = inBurnArc ? 105 : (cleft1 < 0.98f ? 25 : 230);
                    int eb = inBurnArc ? 20 : (cleft1 < 0.98f ? 50 : 180);

                    // 1) 外表皮角质层亮环
                    PainVisualOverlay.addQuadSegment(buf, pose,
                            cx + Mth.cos(a1) * rEpi1, cy + Mth.sin(a1) * rEpi1,
                            cx + Mth.cos(a2) * rEpi2, cy + Mth.sin(a2) * rEpi2,
                            1.5f, er, eg, eb, 230, 230);

                    // 2) 真皮乳头波浪层 (Dermal Papillae Wave Ring)
                    float wave1 = 0.76f + 0.035f * Mth.sin(a1 * 18.0f);
                    float wave2 = 0.76f + 0.035f * Mth.sin(a2 * 18.0f);
                    float rDer1 = r * wave1 * shape1 * cleft1;
                    float rDer2 = r * wave2 * shape2 * cleft2;
                    PainVisualOverlay.addQuadSegment(buf, pose,
                            cx + Mth.cos(a1) * rDer1, cy + Mth.sin(a1) * rDer1,
                            cx + Mth.cos(a2) * rDer2, cy + Mth.sin(a2) * rDer2,
                            0.9f, 0, 175, 220, 150, 150);

                    // 3) 若已包扎，在表皮外侧绘制双层无菌加压纱布编织环與卡扣
                    if (ld.isBandaged()) {
                        float rBand1 = r * 0.96f * shape1;
                        float rBand2 = r * 0.96f * shape2;
                        int ba = (i % 2 == 0) ? 220 : 120;
                        PainVisualOverlay.addQuadSegment(buf, pose,
                                cx + Mth.cos(a1) * rBand1, cy + Mth.sin(a1) * rBand1,
                                cx + Mth.cos(a2) * rBand2, cy + Mth.sin(a2) * rBand2,
                                1.6f, 0, 255, 128, ba, ba);
                    }
                }

                // 4) 皮下脂肪小叶蜂窝微晶格 (Subcutaneous Adipose Lattice)
                for (int ring = 1; ring <= 2; ring++) {
                    float subR = r * (0.42f + ring * 0.14f);
                    int cells = 12 + ring * 6;
                    for (int c = 0; c < cells; c++) {
                        float ang = (c / (float) cells) * ((float) Math.PI * 2.0f) + ring * 0.3f;
                        drawCircleOutline(buf, pose, cx + Mth.cos(ang) * subR, cy + Mth.sin(ang) * subR,
                                r * 0.06f, 10, 0, 150, 190, 75);
                    }
                }

                // 5) 若有外出血，在表皮撕裂缺口处绘制向外喷溅的动态血液粒子
                if (ld.getBleeding() > 0) {
                    for (int drop = 0; drop < 6; drop++) {
                        float dProg = ((timeSec * 1.8f + drop * 0.17f) % 1.0f);
                        float dAng = 0.75f + (drop - 2.5f) * 0.06f;
                        float dRad = r * (0.72f + dProg * 0.34f);
                        drawFilledDisc(buf, pose, cx + Mth.cos(dAng) * dRad, cy + Mth.sin(dAng) * dRad,
                                2.2f * (1.0f - dProg * 0.5f), 255, 25, 45, (int) ((1.0f - dProg) * 240));
                    }
                }
            }
            case 1 -> {
                // ========== LAYER 02: 肌肉筋膜室、搏动主干动静脉、毛细血管网与内出血血肿 ==========
                // 1) 外周深筋膜鞘环 (Deep Fascia Epimysium)
                drawCircleOutline(buf, pose, cx, cy, r * 0.88f, 48, 0, 180, 230, 180);

                // 2) 四大肌群室横截面 (Muscle Compartments) 与结缔组织肌间隔 (Intermuscular Septa)
                for (int q = 0; q < 4; q++) {
                    float qAng = q * ((float) Math.PI * 0.5f) + 0.4f;
                    PainVisualOverlay.addQuadSegment(buf, pose,
                            cx + Mth.cos(qAng) * r * 0.22f, cy + Mth.sin(qAng) * r * 0.22f,
                            cx + Mth.cos(qAng) * r * 0.88f, cy + Mth.sin(qAng) * r * 0.88f,
                            0.9f, 0, 160, 210, 110, 110);

                    // 肌室中心肌束环
                    float mCX = cx + Mth.cos(qAng + 0.4f) * r * 0.52f;
                    float mCY = cy + Mth.sin(qAng + 0.4f) * r * 0.52f;
                    drawCircleOutline(buf, pose, mCX, mCY, r * 0.22f, 18, 0, 140, 180, 85);
                }

                // 3) 若有内出血，在组织间隙渲染紫红色浸润性血肿池 (Interstitial Hematoma Pool)
                int inBleed = ld.getInternalBleedingRate();
                if (inBleed > 0) {
                    float hemaScale = Mth.clamp(0.18f + (inBleed / 300.0f) * 0.28f, 0.18f, 0.46f);
                    float pulse = 0.9f + 0.1f * Mth.sin(timeSec * 5.0f);
                    drawFilledDisc(buf, pose, cx - r * 0.28f, cy + r * 0.22f, r * hemaScale * pulse, 215, 25, 245, 165);
                    drawCircleOutline(buf, pose, cx - r * 0.28f, cy + r * 0.22f, r * hemaScale * pulse, 24, 255, 80, 255, 230);
                }

                // 4) 主干动脉 (Main Artery - 随心率收缩搏动) 与双伴行静脉 (Paired Veins)
                float artPulse = 1.0f + 0.22f * PainVisualOverlay.evaluateDicroticPulse(ecgPhase);
                float artX = cx + r * 0.18f;
                float artY = cy - r * 0.16f;
                float artR = r * 0.13f * (ld.isTourniqueted() ? 0.45f : artPulse);
                drawFilledDisc(buf, pose, artX, artY, artR, 255, 25, 50, 210);
                drawCircleOutline(buf, pose, artX, artY, artR * 1.25f, 20, 255, 90, 110, 240);

                // 伴行静脉 (深青蓝)
                drawFilledDisc(buf, pose, cx - r * 0.16f, cy - r * 0.22f, r * 0.11f, 0, 150, 255, 180);
                drawFilledDisc(buf, pose, cx + r * 0.08f, cy + r * 0.24f, r * 0.09f, 0, 150, 255, 165);

                // 5) 放射状微血管灌注分支
                for (int b = 0; b < 14; b++) {
                    float bAng = (b / 14.0f) * ((float) Math.PI * 2.0f);
                    float bx1 = artX + Mth.cos(bAng) * artR;
                    float by1 = artY + Mth.sin(bAng) * artR;
                    float bx2 = cx + Mth.cos(bAng) * r * 0.82f;
                    float by2 = cy + Mth.sin(bAng) * r * 0.82f;
                    PainVisualOverlay.addQuadSegment(buf, pose, bx1, by1, bx2, by2, 0.65f, 255, 45, 70, 150, 25);
                }

                // 6) 若绑定了止血带，在外圈绘制橙色高压机械锁紧环
                if (ld.isTourniqueted()) {
                    drawCircleOutline(buf, pose, cx, cy, r * 0.92f, 48, 255, 145, 0, 245);
                }
            }
            case 2 -> {
                // ========== LAYER 03: 骨骼皮质、骨小梁蜂窝网、粉碎骨折裂隙与外固定钛板 ==========
                drawCircleOutline(buf, pose, cx, cy, r * 0.88f, 48, 0, 140, 180, 75);

                if (limb == MedicalData.Limb.CHEST) {
                    // 胸部横截面：后方脊柱椎体 + 前方胸骨 + 左右对称肋骨弓围绕双肺野
                    for (int side = -1; side <= 1; side += 2) {
                        float lungCX = cx + side * r * 0.36f;
                        drawCircleOutline(buf, pose, lungCX, cy, r * 0.34f, 28, 200, 245, 255, 210);
                        // 若有血胸，在胸膜腔底部渲染暗红积液液面
                        if (data.getHemothoraxLevel() > 0) {
                            drawFilledDisc(buf, pose, lungCX, cy + r * 0.14f, r * 0.22f, 255, 20, 45, 175);
                        }
                    }
                    // 脊柱骨密质核心
                    drawFilledDisc(buf, pose, cx, cy + r * 0.56f, r * 0.16f, 225, 250, 255, 230);
                } else if (limb == MedicalData.Limb.HEAD) {
                    // 头颅横截面：厚实的颅骨外板/板障/内板双环 + 大脑镰中线
                    drawCircleOutline(buf, pose, cx, cy, r * 0.78f, 48, 230, 250, 255, 240);
                    drawCircleOutline(buf, pose, cx, cy, r * 0.68f, 48, 160, 225, 255, 190);
                    PainVisualOverlay.addQuadSegment(buf, pose, cx, cy - r * 0.68f, cx, cy + r * 0.68f, 0.8f, 0, 210, 255, 140, 140);
                } else {
                    // 四肢双骨截面 (如尺骨+桡骨 / 胫骨+腓骨)：主承重骨 + 副骨
                    float mainBX = cx - r * 0.15f;
                    float mainBY = cy - r * 0.08f;
                    float mainBR = r * 0.34f;

                    float subBX = cx + r * 0.34f;
                    float subBY = cy + r * 0.20f;
                    float subBR = r * 0.19f;

                    // 主骨高密度骨皮质环 (Cortical Bone) + 内部骨小梁网格 (Trabecular Lattice)
                    drawCircleOutline(buf, pose, mainBX, mainBY, mainBR, 36, 235, 252, 255, 245);
                    drawCircleOutline(buf, pose, mainBX, mainBY, mainBR * 0.62f, 28, 120, 210, 240, 170);
                    for (int sp = 0; sp < 8; sp++) {
                        float sa = (sp / 8.0f) * ((float) Math.PI * 2.0f);
                        PainVisualOverlay.addQuadSegment(buf, pose,
                                mainBX + Mth.cos(sa) * mainBR * 0.2f, mainBY + Mth.sin(sa) * mainBR * 0.2f,
                                mainBX + Mth.cos(sa + 0.5f) * mainBR * 0.62f, mainBY + Mth.sin(sa + 0.5f) * mainBR * 0.62f,
                                0.55f, 150, 225, 255, 130, 130);
                    }

                    // 副骨皮质环与髓腔
                    drawCircleOutline(buf, pose, subBX, subBY, subBR, 24, 220, 245, 255, 225);
                    drawCircleOutline(buf, pose, subBX, subBY, subBR * 0.55f, 18, 120, 200, 235, 150);
                }

                // 若存在骨折：绘制贯穿骨皮质的红色锯齿状粉碎性骨折裂隙与游离骨片
                if (ld.hasFracture()) {
                    PainVisualOverlay.addQuadSegment(buf, pose, cx - r * 0.52f, cy - r * 0.35f, cx - r * 0.12f, cy - r * 0.05f, 1.5f, 255, 25, 45, 255, 255);
                    PainVisualOverlay.addQuadSegment(buf, pose, cx - r * 0.12f, cy - r * 0.05f, cx + r * 0.05f, cy - r * 0.22f, 1.5f, 255, 255, 255, 255, 255);
                    PainVisualOverlay.addQuadSegment(buf, pose, cx + r * 0.05f, cy - r * 0.22f, cx + r * 0.38f, cy + r * 0.18f, 1.5f, 255, 25, 45, 255, 255);
                }

                // 若已打夹板：在两侧绘制翠绿色钛合金外固定支架与锁定螺钉
                if (ld.hasSplint()) {
                    PainVisualOverlay.addQuadSegment(buf, pose, cx - r * 0.82f, cy - r * 0.45f, cx - r * 0.82f, cy + r * 0.45f, 2.2f, 0, 255, 128, 240, 240);
                    PainVisualOverlay.addQuadSegment(buf, pose, cx + r * 0.82f, cy - r * 0.45f, cx + r * 0.82f, cy + r * 0.45f, 2.2f, 0, 255, 128, 240, 240);
                    PainVisualOverlay.addQuadSegment(buf, pose, cx - r * 0.82f, cy, cx + r * 0.82f, cy, 0.9f, 0, 255, 128, 160, 160);
                }
            }
            default -> {
                // ========== LAYER 04: 细胞基质、外周神经束髓鞘、痛觉放电环与病原体感染菌落 ==========
                drawCircleOutline(buf, pose, cx, cy, r * 0.88f, 48, 0, 180, 220, 110);

                // 1) 中央外周神经干横截面 (Peripheral Nerve Trunk & Fascicles)
                float nerveR = r * 0.32f;
                drawCircleOutline(buf, pose, cx, cy, nerveR, 32, 0, 240, 255, 210);
                for (int f = 0; f < 6; f++) {
                    float fa = (f / 6.0f) * ((float) Math.PI * 2.0f) + timeSec * 0.2f;
                    float fx = cx + Mth.cos(fa) * nerveR * 0.55f;
                    float fy = cy + Mth.sin(fa) * nerveR * 0.55f;
                    drawCircleOutline(buf, pose, fx, fy, nerveR * 0.26f, 16, 80, 220, 255, 175);
                }

                // 2) 痛觉神经放电脉冲环与放射状电刺 (随 ld.getPainLevel() 0~10 增强)
                int pain = ld.getPainLevel();
                if (pain > 0) {
                    float painWave = (timeSec * (1.2f + pain * 0.25f)) % 1.0f;
                    float waveR = nerveR + painWave * (r * 0.56f);
                    int waveA = (int) ((1.0f - painWave) * (120 + pain * 13));
                    drawCircleOutline(buf, pose, cx, cy, waveR, 36, 255, 0, 95, waveA);

                    int spikes = 4 + pain * 2;
                    for (int sp = 0; sp < spikes; sp++) {
                        float sa = (sp / (float) spikes) * ((float) Math.PI * 2.0f) + timeSec * 2.5f;
                        float sLen = r * (0.35f + 0.045f * pain);
                        PainVisualOverlay.addQuadSegment(buf, pose,
                                cx + Mth.cos(sa) * nerveR, cy + Mth.sin(sa) * nerveR,
                                cx + Mth.cos(sa + 0.12f) * sLen, cy + Mth.sin(sa + 0.12f) * sLen,
                                0.9f, 255, 20, 100, 220, 0);
                    }
                }

                // 3) 细菌感染群落与生物靶向锁定框 (随 ld.getInfection() 0~100% 增殖)
                int inf = ld.getInfection();
                if (inf > 0) {
                    int colonies = Math.max(1, inf / 8);
                    for (int c = 0; c < colonies; c++) {
                        float ca = c * 2.39996f + 0.5f;
                        float crDist = r * (0.28f + ((c * 19) % 50) * 0.011f);
                        float colX = cx + Mth.cos(ca) * crDist;
                        float colY = cy + Mth.sin(ca) * crDist;
                        float colR = 2.5f + (inf / 100.0f) * 4.5f;
                        drawFilledDisc(buf, pose, colX, colY, colR, 255, 190, 0, 195);
                        drawCircleOutline(buf, pose, colX, colY, colR * 1.6f, 12, 255, 80, 20, 150);
                    }
                }
            }
        }

        MeshData mesh = buf.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private void drawCircleOutline(BufferBuilder buf, Matrix4f pose, float cx, float cy, float radius,
                                   int segments, int r, int g, int b, int a) {
        for (int i = 0; i < segments; i++) {
            float a1 = (i / (float) segments) * ((float) Math.PI * 2.0f);
            float a2 = ((i + 1) / (float) segments) * ((float) Math.PI * 2.0f);
            PainVisualOverlay.addQuadSegment(buf, pose,
                    cx + Mth.cos(a1) * radius, cy + Mth.sin(a1) * radius,
                    cx + Mth.cos(a2) * radius, cy + Mth.sin(a2) * radius,
                    0.85f, r, g, b, a, a);
        }
    }

    private void drawFilledDisc(BufferBuilder buf, Matrix4f pose, float cx, float cy, float radius,
                                int r, int g, int b, int a) {
        float c = radius * 0.7f;
        buf.addVertex(pose, cx - c, cy - c, 0.0f).setColor(r, g, b, a);
        buf.addVertex(pose, cx + c, cy - c, 0.0f).setColor(r, g, b, a);
        buf.addVertex(pose, cx + c, cy + c, 0.0f).setColor(r, g, b, a);
        buf.addVertex(pose, cx - c, cy + c, 0.0f).setColor(r, g, b, a);
    }

    private void drawTomographyMetric(GuiGraphics g, int x, int y, int w, String label, String val, int color) {
        g.fill(x, y, x + w, y + 23, 0x8806121E);
        g.fill(x, y, x + 2, y + 23, color);
        g.drawString(this.font, label, x + 6, y + 3, 0xFF90A4AE, false);
        g.drawString(this.font, val, x + 6, y + 13, color, false);
    }

    // ============================================================
    // 五、右侧详情面板
    // ============================================================
    private void drawDetailPanel(GuiGraphics graphics, MedicalData data, int panelX, int panelWidth,
                                 int mouseX, int mouseY, float deltaSeconds, long now) {
        int panelY = HUD_BAR_H + 14;
        int panelHeight = this.height - HUD_BAR_H - 38;

        if (panelWidth < 100 || panelHeight < 60) return;

        if (selectedLimb == MedicalData.Limb.HEAD) {
            String cardTitle = "头部与脑扫描";
            drawCyberCard(graphics, panelX, panelY, panelWidth, panelHeight, cardTitle, themePrimaryColor);
            brainScanRenderer.render(graphics, this.font, panelX, panelY, panelWidth, panelHeight,
                    data, deltaSeconds, mouseX, mouseY, now);
            return;
        }

        if (selectedLimb == MedicalData.Limb.CHEST) {
            String cardTitle = "胸腔与心肺全息";
            drawCyberCard(graphics, panelX, panelY, panelWidth, panelHeight, cardTitle, themePrimaryColor);
            cardiopulmonaryRenderer.render(graphics, this.font, panelX, panelY, panelWidth, panelHeight,
                    data, deltaSeconds, mouseX, mouseY, now, themePrimaryColor,
                    (g, ecgX, ecgY, ecgW, ecgH, d) -> drawDualOscilloscope(g, ecgX, ecgY, ecgW, ecgH, d));
            return;
        }

        String cardTitle = selectedLimb != null
                ? getLimbFullName(selectedLimb) + " · " + tr("medical.detail.title")
                : "全身概览";

        drawCyberCard(graphics, panelX, panelY, panelWidth, panelHeight, cardTitle, themePrimaryColor);

        detailPanelRenderer.renderLimbOrOverviewPanel(
                graphics, this.font,
                panelX, panelY, panelWidth, panelHeight,
                data, selectedLimb,
                mouseX, mouseY, now, themePrimaryColor,
                (g, ecgX, ecgY, ecgW, ecgH, d) -> drawDualOscilloscope(g, ecgX, ecgY, ecgW, ecgH, d)
        );
    }

    // ============================================================
    // 六、临床级双通道监护示波器 (Lead II ECG & SpO2 Plethysmograph)
    // ============================================================
    private void updateOscilloscopeBuffers(MedicalData data, float dt) {
        rPeakFlashAlpha = Math.max(0.0f, rPeakFlashAlpha - dt * 3.5f);

        oscSweepAccumulator += dt * 45.0f;
        int steps = (int) oscSweepAccumulator;
        if (steps <= 0) return;
        oscSweepAccumulator -= steps;
        steps = Math.min(steps, 6);

        float bpm = Mth.clamp(data.getHeartRate(), 30.0f, 240.0f);
        float stepSec = 1.0f / 45.0f;

        for (int s = 0; s < steps; s++) {
            float prevPhase = ecgPhase;
            ecgPhase = (ecgPhase + stepSec * (bpm / 60.0f)) % 1.0f;

            float ecgVal;
            float plethVal;

            if (data.isCardiacArrest()) {
                ecgVal = ((System.nanoTime() & 0xFF) / 255.0f - 0.5f) * 0.04f;
                plethVal = 0.0f;
            } else if (data.isInVFib()) {
                float t = System.currentTimeMillis() * 0.015f + s * 0.4f;
                ecgVal = (Mth.sin(t * 1.7f) * 0.45f + Mth.cos(t * 3.1f) * 0.35f + Mth.sin(t * 5.3f) * 0.20f);
                plethVal = 0.08f * Mth.sin(t * 0.9f);
            } else {
                ecgVal = evaluateClinicalLeadII(ecgPhase);
                if (prevPhase < 0.22f && ecgPhase >= 0.22f) {
                    rPeakFlashAlpha = 1.0f;
                }
                float plethPhase = (ecgPhase + 0.82f) % 1.0f;
                float mapFactor = Mth.clamp(data.getMeanArterialPressure() / 93.0f, 0.2f, 1.3f);
                plethVal = (PainVisualOverlay.evaluateDicroticPulse(plethPhase) - 0.35f) * 1.2f * mapFactor;
            }

            oscWriteHead = (oscWriteHead + 1) % OSC_SAMPLES;
            ecgBuffer[oscWriteHead] = Mth.clamp(ecgVal, -1.0f, 1.2f);
            plethBuffer[oscWriteHead] = Mth.clamp(plethVal, -0.8f, 1.0f);
        }
    }

    private float evaluateClinicalLeadII(float p) {
        if (p < 0.08f) return 0.0f;
        if (p < 0.16f) return 0.18f * Mth.sin(((p - 0.08f) / 0.08f) * (float) Math.PI);
        if (p < 0.19f) return 0.0f;
        if (p < 0.21f) return -0.22f * Mth.sin(((p - 0.19f) / 0.02f) * (float) Math.PI);
        if (p < 0.25f) return 1.15f * Mth.sin(((p - 0.21f) / 0.04f) * (float) Math.PI);
        if (p < 0.28f) return -0.35f * Mth.sin(((p - 0.25f) / 0.03f) * (float) Math.PI);
        if (p < 0.36f) return 0.0f;
        if (p < 0.50f) return 0.32f * Mth.sin(((p - 0.36f) / 0.14f) * (float) Math.PI);
        return 0.0f;
    }

    private void drawDualOscilloscope(GuiGraphics g, int x, int y, int width, int height, MedicalData data) {
        HudRenderHelper.drawSciFiCard(g, x, y, width, height, 4, 0xE603080E, withAlpha(themePrimaryColor, 0.55f));

        for (int gx = x + 8; gx < x + width - 4; gx += 12) {
            g.fill(gx, y + 4, gx + 1, y + height - 4, withAlpha(themePrimaryColor, 0.08f));
        }
        for (int gy = y + 8; gy < y + height - 4; gy += 12) {
            g.fill(x + 4, gy, x + width - 4, gy + 1, withAlpha(themePrimaryColor, 0.08f));
        }

        int hr = data.getHeartRate();
        int ecgColor = (data.isCardiacArrest() || data.isInVFib() || hr >= 150 || hr <= 45)
                ? C_ALERT_RED : (hr >= 115 ? C_WARN_YELLOW : C_SAFE_GREEN);

        g.drawString(this.font, "LEAD II  " + hr + " BPM", x + 6, y + 4, ecgColor, false);
        String bpInfo = String.format("MAP %.0f mmHg", data.getMeanArterialPressure());
        g.drawString(this.font, bpInfo, x + width - this.font.width(bpInfo) - 6, y + 4, C_VITAL_BP, false);

        float ecgCenterY = y + height * 0.42f;
        float ecgAmp = height * 0.24f;
        float plethCenterY = y + height * 0.78f;
        float plethAmp = height * 0.14f;

        g.drawString(this.font, "PLETH  SpO2 " + data.getBloodOxygen() + "%", x + 6, (int) (plethCenterY - 10), C_VITAL_OXYGEN, false);

        Matrix4f pose = g.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        int plotX = x + 6;
        int plotW = width - 12;
        int er = (ecgColor >>> 16) & 0xFF;
        int eg = (ecgColor >>> 8) & 0xFF;
        int eb = ecgColor & 0xFF;

        for (int i = 0; i < OSC_SAMPLES - 1; i++) {
            int age = (oscWriteHead - i + OSC_SAMPLES) % OSC_SAMPLES;
            if (age > OSC_SAMPLES - 10) continue;

            float ageNorm = 1.0f - (age / (float) (OSC_SAMPLES - 10));
            int alpha = (int) (Math.pow(ageNorm, 1.8) * 240.0f);
            if (alpha < 8) continue;

            float x1 = plotX + (i / (float) (OSC_SAMPLES - 1)) * plotW;
            float x2 = plotX + ((i + 1) / (float) (OSC_SAMPLES - 1)) * plotW;

            float ey1 = ecgCenterY - ecgBuffer[i] * ecgAmp;
            float ey2 = ecgCenterY - ecgBuffer[i + 1] * ecgAmp;

            PainVisualOverlay.addQuadSegment(buf, pose, x1, ey1, x2, ey2, 1.8f, er, eg, eb, alpha / 3, alpha / 3);
            PainVisualOverlay.addQuadSegment(buf, pose, x1, ey1, x2, ey2, 0.85f, er, eg, eb, alpha, alpha);

            float py1 = plethCenterY - plethBuffer[i] * plethAmp;
            float py2 = plethCenterY - plethBuffer[i + 1] * plethAmp;
            int pAlpha = (alpha * 3) / 4;
            PainVisualOverlay.addQuadSegment(buf, pose, x1, py1, x2, py2, 0.75f, 0, 210, 255, pAlpha, pAlpha);
        }

        MeshData mesh = buf.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
        RenderSystem.defaultBlendFunc();

        int headX = plotX + (int) ((oscWriteHead / (float) (OSC_SAMPLES - 1)) * plotW);
        int headY = (int) (ecgCenterY - ecgBuffer[oscWriteHead] * ecgAmp);
        g.fill(headX - 1, headY - 1, headX + 2, headY + 2, 0xFFFFFFFF);
    }

    // ============================================================
    // 七、鼠标交互事件处理
    // ============================================================
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (microTomographyLimb != null && microTomographyAnim > 0.4f) {
            // 点击 < 或 > 切换 CT 层
            if (mouseY >= layerBtnY && mouseY <= layerBtnY + layerBtnH) {
                if (mouseX >= layerBtnPrevX && mouseX <= layerBtnPrevX + layerBtnW) {
                    dcmLayer = Math.floorMod(dcmLayer - 1, DICOM_LAYER_NAMES.length);
                    dcmWipeStartMs = System.currentTimeMillis();
                    MedicalSoundHelper.playLimbSelectSound();
                    return true;
                }
                if (mouseX >= layerBtnNextX && mouseX <= layerBtnNextX + layerBtnW) {
                    dcmLayer = Math.floorMod(dcmLayer + 1, DICOM_LAYER_NAMES.length);
                    dcmWipeStartMs = System.currentTimeMillis();
                    MedicalSoundHelper.playLimbSelectSound();
                    return true;
                }
            }
            microTomographyLimb = null;
            MedicalSoundHelper.playCloseSound();
            return true;
        }

        if (mouseX >= ctBtnX && mouseX <= ctBtnX + ctBtnW && mouseY >= ctBtnY && mouseY <= ctBtnY + ctBtnH) {
            microTomographyLimb = (selectedLimb != null) ? selectedLimb : MedicalData.Limb.CHEST;
            dcmLayer = 0;
            dcmWipeStartMs = System.currentTimeMillis();
            MedicalSoundHelper.playLimbSelectSound();
            return true;
        }

        if (selectedLimb == MedicalData.Limb.HEAD && brainScanRenderer.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (selectedLimb == MedicalData.Limb.CHEST && cardiopulmonaryRenderer.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (selectedLimb != MedicalData.Limb.HEAD && selectedLimb != MedicalData.Limb.CHEST
                && detailPanelRenderer.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        for (MedicalData.Limb limb : MedicalData.Limb.values()) {
            int[] b = limbScreenBounds.get(limb);
            if (b != null && mouseX >= b[0] && mouseX <= b[0] + b[2] && mouseY >= b[1] && mouseY <= b[1] + b[3]) {
                rippleManager.addRipple((float) mouseX, (float) mouseY, 48.0f, themePrimaryColor);
                MedicalSoundHelper.playLimbSelectSound();

                if (button == 1) {
                    selectedLimb = limb;
                    microTomographyLimb = limb;
                    dcmLayer = 0;
                    dcmWipeStartMs = System.currentTimeMillis();
                } else {
                    selectedLimb = (selectedLimb == limb) ? null : limb;
                }
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (microTomographyLimb == null && selectedLimb == MedicalData.Limb.HEAD) {
            if (brainScanRenderer.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
                return true;
            }
        }
        if (microTomographyLimb == null && selectedLimb == MedicalData.Limb.CHEST) {
            if (cardiopulmonaryRenderer.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (microTomographyLimb != null && microTomographyAnim > 0.4f) {
            if (deltaY != 0) {
                dcmLayer = Math.floorMod(dcmLayer + (deltaY > 0 ? 1 : -1), DICOM_LAYER_NAMES.length);
                dcmWipeStartMs = System.currentTimeMillis();
                MedicalSoundHelper.playLimbSelectSound();
            }
            return true;
        }

        if (selectedLimb == MedicalData.Limb.HEAD && brainScanRenderer.mouseScrolled(mouseX, mouseY, deltaY)) {
            return true;
        }
        if (selectedLimb == MedicalData.Limb.CHEST && cardiopulmonaryRenderer.mouseScrolled(mouseX, mouseY, deltaY)) {
            return true;
        }
        if (selectedLimb != MedicalData.Limb.HEAD && selectedLimb != MedicalData.Limb.CHEST
                && detailPanelRenderer.mouseScrolled(mouseX, mouseY, deltaY)) {
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    // ============================================================
    // 八、通用视觉与本地化辅助方法
    // ============================================================
    private void drawCyberCard(GuiGraphics g, int x, int y, int w, int h, String title, int accentColor) {
        HudRenderHelper.drawSciFiCard(g, x, y, w, h, 6, themeCardBgColor, withAlpha(accentColor, 0.72f));
        HudRenderHelper.drawCardCorners(g, x, y, w, h, 10, accentColor);

        g.fillGradient(x + 2, y + 2, x + w - 2, y + 20, withAlpha(accentColor, 0.25f), withAlpha(accentColor, 0.02f));
        g.fill(x + 6, y + 20, x + w - 6, y + 21, withAlpha(accentColor, 0.38f));
        HudRenderHelper.drawGlowingString(g, this.font, title, x + 10, y + 6, accentColor, withAlpha(accentColor, 0.35f));
    }

    private int getLimbColor(MedicalData.LimbData ld) {
        if (ld == null) return LIMB_HEALTHY;
        if (ld.isAmputated()) return LIMB_AMPUTATED;
        if (com.FIYTP.realmedicalmod.treatment.ModItems.isLimbNecrotic(ld)) return 0xFF6A1B9A;
        int hp = BrainScan3DRenderer.computeLimbHealthPercent(ld);
        if (ld.getBleeding() > 200 || ld.getInternalBleedingRate() > 200 || hp <= 25) {
            return LIMB_CRITICAL;
        }
        if (ld.getBleeding() > 50 || ld.getInternalBleedingRate() > 60 || ld.hasFracture() || hp <= 60) {
            return LIMB_MODERATE;
        }
        if (ld.getBleeding() > 0 || ld.getInternalBleedingRate() > 0 || ld.getPainLevel() > 0 || ld.getInfection() > 0 || hp < 95) {
            return LIMB_MINOR;
        }
        return LIMB_HEALTHY;
    }

    private int[] packAllLimbBoxes() {
        MedicalData.Limb[] limbs = MedicalData.Limb.values();
        int[] packed = new int[limbs.length * 4];
        for (int i = 0; i < limbs.length; i++) {
            int[] b = limbScreenBounds.get(limbs[i]);
            if (b != null) {
                packed[i * 4] = b[0];
                packed[i * 4 + 1] = b[1];
                packed[i * 4 + 2] = b[2];
                packed[i * 4 + 3] = b[3];
            }
        }
        return packed;
    }

    private String getLimbFullName(MedicalData.Limb limb) {
        if (limb == null) return "";
        return switch (limb) {
            case HEAD -> tr("medical.limb.head");
            case CHEST -> tr("medical.limb.chest");
            case LEFT_ARM -> tr("medical.limb.left_arm");
            case RIGHT_ARM -> tr("medical.limb.right_arm");
            case LEFT_LEG -> tr("medical.limb.left_leg");
            case RIGHT_LEG -> tr("medical.limb.right_leg");
        };
    }

    private static String tr(String key) {
        return I18N_CACHE.computeIfAbsent(key, k -> Component.translatable(k).getString());
    }

    // =========================================================================
    // 内嵌式 3A 右侧诊断面板自适应与滚动兼容引擎
    // =========================================================================
    private static class EmbeddedDetailPanelRenderer {

        @FunctionalInterface
        public interface OscilloscopeDrawer {
            void draw(GuiGraphics graphics, int x, int y, int width, int height, MedicalData data);
        }

        private float scrollOffset = 0.0f;
        private float scrollTarget = 0.0f;
        private float maxScroll = 0.0f;
        private long lastFrameMs = 0L;

        private boolean compactTwoColumn = true;
        private boolean ecgCollapsed = false;
        private MedicalData.Limb lastLimb = null;

        private int viewportX, viewportY, viewportW, viewportH;
        private int btnCompactX, btnCompactY, btnCompactW, btnCompactH;
        private int btnEcgX, btnEcgY, btnEcgW, btnEcgH;
        private int btnUnbindX, btnUnbindY, btnUnbindW, btnUnbindH;
        private MedicalData.Limb unbindTargetLimb = null;
        private int btnUnbindSplintX, btnUnbindSplintY, btnUnbindSplintW, btnUnbindSplintH;
        private MedicalData.Limb unbindSplintTargetLimb = null;

        public void renderLimbOrOverviewPanel(GuiGraphics g, Font font,
                                              int panelX, int panelY, int panelWidth, int panelHeight,
                                              MedicalData data, MedicalData.Limb selectedLimb,
                                              int mouseX, int mouseY, long now, int themeAccent,
                                              OscilloscopeDrawer ecgDrawer) {
            if (lastLimb != selectedLimb) {
                lastLimb = selectedLimb;
                scrollOffset = 0.0f;
                scrollTarget = 0.0f;
            }

            if (lastFrameMs == 0L) lastFrameMs = now;
            float dt = Mth.clamp((now - lastFrameMs) / 1000.0f, 0.001f, 0.1f);
            lastFrameMs = now;

            scrollTarget = Mth.clamp(scrollTarget, 0.0f, maxScroll);
            scrollOffset = Mth.lerp(Math.min(1.0f, dt * 14.0f), scrollOffset, scrollTarget);

            drawHeaderControls(g, font, panelX, panelY, panelWidth, mouseX, mouseY, themeAccent);

            int bottomPad = 10;
            int desiredEcgH = ecgCollapsed ? 18 : Math.min(92, Math.max(58, (int) (panelHeight * 0.34f)));
            if (panelHeight < 155) {
                desiredEcgH = 18;
            }

            int ecgY = panelY + panelHeight - desiredEcgH - bottomPad;
            int contentX = panelX + 14;
            int contentW = panelWidth - 28;

            this.viewportX = contentX;
            this.viewportY = panelY + 26;
            this.viewportW = contentW;
            this.viewportH = Math.max(36, ecgY - this.viewportY - 6);

            g.enableScissor(viewportX, viewportY, viewportX + viewportW, viewportY + viewportH);

            int innerW = viewportW - 7;
            int startY = (int) (viewportY - scrollOffset);
            int endY;

            if (selectedLimb != null) {
                endY = drawLimbTelemetryContent(g, font, data, selectedLimb, viewportX, startY, innerW, themeAccent);
            } else {
                endY = drawSystemOverviewContent(g, font, data, viewportX, startY, innerW, themeAccent);
            }

            int totalContentH = endY - startY;
            this.maxScroll = Math.max(0.0f, totalContentH - viewportH + 4);

            g.disableScissor();

            if (this.maxScroll > 1.0f) {
                BrainScan3DRenderer.drawHolographicScrollbar(
                        g, viewportX + viewportW - 3, viewportY, 3, viewportH,
                        scrollOffset, maxScroll, totalContentH
                );
                if (scrollOffset > 2.0f) {
                    g.fillGradient(viewportX, viewportY, viewportX + innerW, viewportY + 8, 0xCC050B12, 0x00050B12);
                }
                if (scrollOffset < maxScroll - 2.0f) {
                    g.fillGradient(viewportX, viewportY + viewportH - 8, viewportX + innerW, viewportY + viewportH, 0x00050B12, 0xCC050B12);
                }
            }

            if (desiredEcgH >= 48 && ecgDrawer != null) {
                ecgDrawer.draw(g, contentX, ecgY, contentW, desiredEcgH, data);
            } else {
                drawCollapsedEcgStrip(g, font, contentX, ecgY, contentW, desiredEcgH, data, themeAccent);
            }
        }

        private void drawHeaderControls(GuiGraphics g, Font font, int panelX, int panelY, int panelW,
                                        int mouseX, int mouseY, int themeAccent) {
            int btnY = panelY + 5;
            int btnH = 13;

            String ecgLabel = ecgCollapsed ? "心电 ▲" : "心电 ▼";
            this.btnEcgW = font.width(ecgLabel) + 10;
            this.btnEcgH = btnH;
            this.btnEcgX = panelX + panelW - btnEcgW - 10;
            this.btnEcgY = btnY;

            boolean hovEcg = mouseX >= btnEcgX && mouseX <= btnEcgX + btnEcgW && mouseY >= btnEcgY && mouseY <= btnEcgY + btnEcgH;
            HudRenderHelper.drawSciFiCard(g, btnEcgX, btnEcgY, btnEcgW, btnEcgH, 2,
                    !ecgCollapsed ? withAlpha(themeAccent, 0.35f) : 0x66050E17,
                    hovEcg ? themeAccent : withAlpha(themeAccent, 0.5f));
            g.drawString(font, ecgLabel, btnEcgX + 5, btnEcgY + 3, !ecgCollapsed ? themeAccent : 0xFF90A4AE, false);

            String gridLabel = compactTwoColumn ? "双列" : "单列";
            this.btnCompactW = font.width(gridLabel) + 10;
            this.btnCompactH = btnH;
            this.btnCompactX = btnEcgX - btnCompactW - 4;
            this.btnCompactY = btnY;

            if (this.btnCompactX > panelX + 150) {
                boolean hovGrid = mouseX >= btnCompactX && mouseX <= btnCompactX + btnCompactW && mouseY >= btnCompactY && mouseY <= btnCompactY + btnCompactH;
                HudRenderHelper.drawSciFiCard(g, btnCompactX, btnCompactY, btnCompactW, btnCompactH, 2,
                        compactTwoColumn ? withAlpha(themeAccent, 0.35f) : 0x66050E17,
                        hovGrid ? themeAccent : withAlpha(themeAccent, 0.5f));
                g.drawString(font, gridLabel, btnCompactX + 5, btnCompactY + 3, compactTwoColumn ? 0xFFFFFFFF : 0xFF90A4AE, false);
            }
        }

        private int drawLimbTelemetryContent(GuiGraphics g, Font font, MedicalData data, MedicalData.Limb limb,
                                             int x, int y, int w, int themeAccent) {
            MedicalData.LimbData ld = data.getLimb(limb);
            if (ld == null) return y;

            int health = BrainScan3DRenderer.computeLimbHealthPercent(ld);
            int hc = getHealthColor(health / 100.0f);
            g.drawString(font, "部位完整度", x, y, themeAccent, false);
            String hpStr = health + "%";
            g.drawString(font, hpStr, x + w - font.width(hpStr), y, hc, false);
            y += 11;
            HudRenderHelper.drawSciFiProgressBar(g, x, y, w, 6, health / 100.0f, hc, 0xFF0A1824);
            y += 12;

            int bleed = ld.getBleeding();
            int clampedBleed = ld.isTourniqueted() ? com.FIYTP.realmedicalmod.treatment.ModItems.getTourniquetClampedBleed(ld) : 0;
            int inBleed = ld.getInternalBleedingRate();
            int pain = ld.getPainLevel();
            int inf = ld.getInfection();
            boolean isPerf = ld.hasPerforation();
            boolean isPerfPacked = com.FIYTP.realmedicalmod.treatment.ModItems.isPerforationPacked(ld);
            int embBullets = com.FIYTP.realmedicalmod.treatment.ModItems.getEmbeddedBulletCount(ld);
            int exBleedCol = isPerf ? 0xFFFF2A85 : C_ALERT_RED;
            String exBleedLabel = isPerf
                    ? (isPerfPacked ? tr("medical.detail.bleeding_rate") + "[塞纱]" : tr("medical.detail.bleeding_rate") + "[贯穿]")
                    : tr("medical.detail.bleeding_rate");

            if (compactTwoColumn && w >= 180) {
                int colW = (w - 6) / 2;
                if (clampedBleed > 0 && bleed <= 0) {
                    drawDashedBleedCard(g, font, x, y, colW, exBleedLabel,
                            clampedBleed + " mL/m", Math.min(1f, clampedBleed / 500f), exBleedCol);
                } else {
                    drawMetricCard(g, font, x, y, colW, exBleedLabel, bleed + " mL/m",
                            Math.min(1f, bleed / 500f), bleed > 0 ? exBleedCol : (isPerf ? 0xFF881B4C : 0xFF37474F));
                    if (isPerf && bleed > 0) {
                        drawPerforationHazardTicks(g, x + 5, y + 13, colW - 8, 5, Math.min(1f, bleed / 500f));
                    }
                }
                int tampInBleed = com.FIYTP.realmedicalmod.treatment.ModItems.getCompartmentTamponadedBleed(ld);
                if (tampInBleed > 0 && inBleed <= 0) {
                    drawDashedBleedCard(g, font, x + colW + 6, y, colW, tr("medical.detail.internal_bleeding_rate"),
                            tampInBleed + " mL/m", Math.min(1f, tampInBleed / 300f), 0xFFE040FB);
                } else {
                    drawMetricCard(g, font, x + colW + 6, y, colW, tr("medical.detail.internal_bleeding_rate"), inBleed + " mL/m",
                            Math.min(1f, inBleed / 300f), inBleed > 0 ? 0xFFE040FB : 0xFF37474F);
                }
                y += 24;

                drawMetricCard(g, font, x, y, colW, tr("medical.detail.pain_level"), pain + " / 10",
                        pain / 10f, pain > 0 ? C_VITAL_PAIN : 0xFF37474F);
                drawMetricCard(g, font, x + colW + 6, y, colW, tr("medical.detail.infection"), inf + " %",
                        inf / 100f, inf > 0 ? C_WARN_YELLOW : 0xFF37474F);
                y += 24;
            } else {
                if (clampedBleed > 0 && bleed <= 0) {
                    y = drawDashedBleedFullRow(g, font, x, y, w, exBleedLabel,
                            clampedBleed + " mL/min", Math.min(1f, clampedBleed / 500f), exBleedCol);
                } else if (bleed > 0) {
                    int barY = y + 10;
                    y = drawFullRow(g, font, x, y, w, exBleedLabel, bleed + " mL/min",
                            Math.min(1f, bleed / 500f), exBleedCol);
                    if (isPerf) {
                        drawPerforationHazardTicks(g, x, barY, w, 5, Math.min(1f, bleed / 500f));
                    }
                }
                int tampInBleed = com.FIYTP.realmedicalmod.treatment.ModItems.getCompartmentTamponadedBleed(ld);
                if (tampInBleed > 0 && inBleed <= 0) {
                    y = drawDashedBleedFullRow(g, font, x, y, w, tr("medical.detail.internal_bleeding_rate"),
                            tampInBleed + " mL/min", Math.min(1f, tampInBleed / 300f), 0xFFE040FB);
                } else if (inBleed > 0) {
                    y = drawFullRow(g, font, x, y, w, tr("medical.detail.internal_bleeding_rate"), inBleed + " mL/min",
                            Math.min(1f, inBleed / 300f), 0xFFE040FB);
                }
                y = drawFullRow(g, font, x, y, w, tr("medical.detail.pain_level"), pain + " / 10",
                        pain / 10f, pain > 0 ? C_VITAL_PAIN : 0xFF455A64);
                y = drawFullRow(g, font, x, y, w, tr("medical.detail.infection"), inf + " %",
                        inf / 100f, inf > 0 ? C_WARN_YELLOW : 0xFF455A64);
            }

            boolean isOrthoFixed = com.FIYTP.realmedicalmod.treatment.ModItems.isLimbOrthopedicFixed(ld);
            boolean isChestBraced = com.FIYTP.realmedicalmod.treatment.ModItems.isChestBraced(ld);
            if (ld.hasFracture() && (ld.hasSplint() || isOrthoFixed || (limb == MedicalData.Limb.CHEST && isChestBraced)) && ld.getFractureTimer() > 0) {
                float maxHealT = isOrthoFixed
                        ? MedicalConfig.ORTHO_SURGERY_HEAL_TIME_SEC.get().floatValue()
                        : MedicalConfig.SPLINT_FRACTURE_HEAL_TIME_SEC.get().floatValue();
                y = drawFullRow(g, font, x, y, w, tr("medical.detail.fracture_healing"), ld.getFractureTimer() + "s",
                        Mth.clamp(1.0f - ld.getFractureTimer() / Math.max(1.0f, maxHealT), 0.0f, 1.0f), isOrthoFixed ? C_NEON_CYAN : C_SAFE_GREEN);
            }
            if (ld.hasBurn()) {
                y = drawFullRow(g, font, x, y, w, "烧伤 (" + ld.getBurnDegree() + "°)",
                        String.format("%.1f%%", ld.getBurnArea()), Mth.clamp(ld.getBurnArea() / 25.0f, 0.0f, 1.0f), 0xFFFF6D00);
            }
            if (limb != MedicalData.Limb.HEAD && limb != MedicalData.Limb.CHEST && !ld.isAmputated()) {
                float icp = com.FIYTP.realmedicalmod.treatment.ModItems.getCompartmentPressure(ld);
                float cBlood = com.FIYTP.realmedicalmod.treatment.ModItems.getCompartmentBlood(ld);
                if (icp > 8.0f || cBlood > 5.0f || com.FIYTP.realmedicalmod.treatment.ModItems.isCompartmentSyndrome(ld)) {
                    int icpCol = com.FIYTP.realmedicalmod.treatment.ModItems.isCompartmentSyndrome(ld) ? C_ALERT_RED : C_WARN_YELLOW;
                    y = drawFullRow(g, font, x, y, w, "筋膜室压",
                            String.format("%.0f mmHg (%.0f mL)", icp, cBlood),
                            Mth.clamp(icp / 65.0f, 0.0f, 1.0f), icpCol);
                }
            }

            if (embBullets > 0) {
                y = drawFullRow(g, font, x, y, w, tr("medical.detail.embedded_bullet"),
                        embBullets + " 枚 (需手术取弹)", Math.min(1.0f, embBullets / 4.0f), 0xFFFFC400);
            }
            y = drawLimbStatusPills(g, font, x, y + 2, w, ld);
            if (ld.isTourniqueted()) {
                this.unbindTargetLimb = limb;
                this.btnUnbindX = x;
                this.btnUnbindY = y + 3;
                this.btnUnbindW = Math.min(86, w);
                this.btnUnbindH = 15;
                int tqSec = com.FIYTP.realmedicalmod.treatment.ModItems.getTourniquetTimer(ld);
                int bCol = (!ld.isAmputated() && tqSec >= MedicalConfig.TOURNIQUET_ISCHEMIA_STAGE2_SEC.get()) ? C_ALERT_RED : C_WARN_YELLOW;
                HudRenderHelper.drawSciFiCard(g, btnUnbindX, btnUnbindY, btnUnbindW, btnUnbindH, 2, withAlpha(bCol, 0.28f), bCol);
                g.drawCenteredString(font, "解开止血带", btnUnbindX + btnUnbindW / 2, btnUnbindY + 4, 0xFFFFFFFF);
                y += 20;
            } else {
                this.unbindTargetLimb = null;
                this.btnUnbindW = 0;
            }
            if (ld.hasSplint()) {
                this.unbindSplintTargetLimb = limb;
                this.btnUnbindSplintX = x;
                this.btnUnbindSplintY = y + 3;
                this.btnUnbindSplintW = Math.min(86, w);
                this.btnUnbindSplintH = 15;
                int sCol = C_SAFE_GREEN;
                HudRenderHelper.drawSciFiCard(g, btnUnbindSplintX, btnUnbindSplintY, btnUnbindSplintW, btnUnbindSplintH, 2, withAlpha(sCol, 0.26f), sCol);
                g.drawCenteredString(font, "拆下夹板", btnUnbindSplintX + btnUnbindSplintW / 2, btnUnbindSplintY + 4, 0xFFFFFFFF);
                y += 20;
            } else {
                this.unbindSplintTargetLimb = null;
                this.btnUnbindSplintW = 0;
            }

            if (limb == MedicalData.Limb.CHEST) {
                y += 4;
                g.fill(x, y + 8, x + w, y + 9, withAlpha(themeAccent, 0.35f));
                g.drawString(font, "胸腔与血流", x, y, themeAccent, false);
                y += 12;

                float chestBlood = data.getChestBloodAccumulation();
                int hemoLv = data.getHemothoraxLevel();
                float lungContusion = data.getLungContusion();
                int sirsLv = data.getSirsLevel();

                if (compactTwoColumn && w >= 180) {
                    int colW = (w - 6) / 2;
                    drawMetricCard(g, font, x, y, colW, tr("medical.detail.chest_blood_accumulation"),
                            String.format("%.0f mL (LV.%d)", chestBlood, hemoLv),
                            Math.min(1f, chestBlood / 2000f), hemoLv > 0 ? C_ALERT_RED : 0xFF37474F);
                    drawMetricCard(g, font, x + colW + 6, y, colW, tr("medical.detail.lung_contusion"),
                            String.format("%.0f%%", lungContusion),
                            Mth.clamp(lungContusion / 100f, 0f, 1f), lungContusion > 0 ? C_WARN_YELLOW : 0xFF37474F);
                    y += 24;

                    drawMetricCard(g, font, x, y, colW, tr("medical.detail.blood_pressure"),
                            data.getSystolicBP() + "/" + data.getDiastolicBP() + " (MAP " + (int) data.getMeanArterialPressure() + ")",
                            Mth.clamp(data.getMeanArterialPressure() / 130f, 0f, 1f), C_VITAL_BP);
                    drawMetricCard(g, font, x + colW + 6, y, colW, "CO / TPR / SIRS",
                            String.format("%.1fL | %.2f | S%d", data.getCardiacOutput(), data.getPeripheralResistance(), sirsLv),
                            Mth.clamp(data.getCardiacOutput() / 8.0f, 0f, 1f), sirsLv > 0 ? C_ALERT_RED : themeAccent);
                    y += 24;
                } else {
                    y = drawFullRow(g, font, x, y, w, tr("medical.detail.chest_blood_accumulation"),
                            String.format("%.0f mL (LV.%d)", chestBlood, hemoLv), Math.min(1f, chestBlood / 2000f),
                            hemoLv > 0 ? C_ALERT_RED : 0xFF455A64);
                    y = drawFullRow(g, font, x, y, w, tr("medical.detail.blood_pressure"),
                            data.getSystolicBP() + "/" + data.getDiastolicBP() + " mmHg (MAP " + (int) data.getMeanArterialPressure() + ")",
                            Mth.clamp(data.getMeanArterialPressure() / 130f, 0f, 1f), C_VITAL_BP);
                    y = drawFullRow(g, font, x, y, w, tr("medical.detail.cardiac_output") + " / TPR",
                            String.format("CO %.2f L/m | TPR %.2f", data.getCardiacOutput(), data.getPeripheralResistance()),
                            Mth.clamp(data.getCardiacOutput() / 8.0f, 0f, 1f), themeAccent);
                    if (sirsLv > 0) {
                        y = drawFullRow(g, font, x, y, w, tr("medical.detail.sirs_level"),
                                sirsLv + " / 4", sirsLv / 4.0f, C_ALERT_RED);
                    }
                }
            }

            return y;
        }

        private int drawSystemOverviewContent(GuiGraphics g, Font font, MedicalData data, int x, int y, int w, int themeAccent) {
            g.drawString(font, "全身生命体征", x, y, themeAccent, false);
            y += 13;

            int totalBleed = 0;
            int totalClampedBleed = 0;
            for (MedicalData.Limb limb : MedicalData.Limb.values()) {
                MedicalData.LimbData ld = data.getLimb(limb);
                if (ld != null) {
                    if (!ld.isBandaged() && !ld.isTourniqueted()) totalBleed += ld.getBleeding();
                    if (ld.isTourniqueted()) totalClampedBleed += com.FIYTP.realmedicalmod.treatment.ModItems.getTourniquetClampedBleed(ld);
                    totalBleed += ld.getInternalBleedingRate();
                }
            }

            if (compactTwoColumn && w >= 180) {
                int colW = (w - 6) / 2;
                drawMetricCard(g, font, x, y, colW, tr("medical.detail.blood_volume"),
                        data.getBloodVolume() + " / " + data.getMaxBloodVolume() + " mL",
                        data.getBloodVolume() / (float) Math.max(1, data.getMaxBloodVolume()), C_VITAL_HEART);
                if (totalClampedBleed > 0 && totalBleed <= 0) {
                    drawDashedBleedCard(g, font, x + colW + 6, y, colW, tr("medical.detail.total_bleeding_rate"),
                            totalClampedBleed + " mL/min", Math.min(1f, totalClampedBleed / 600f), C_ALERT_RED);
                } else {
                    drawMetricCard(g, font, x + colW + 6, y, colW, tr("medical.detail.total_bleeding_rate"),
                            totalBleed + " mL/min",
                            Math.min(1f, totalBleed / 600f), totalBleed > 0 ? C_ALERT_RED : C_SAFE_GREEN);
                }
                y += 24;

                drawMetricCard(g, font, x, y, colW, tr("medical.detail.brain_health"),
                        data.getBrainHealth() + "%", data.getBrainHealth() / 100f,
                        getHealthColor(data.getBrainHealth() / 100f));
                drawMetricCard(g, font, x + colW + 6, y, colW, tr("medical.detail.consciousness"),
                        data.getConsciousness() + "%", data.getConsciousness() / 100f,
                        getHealthColor(data.getConsciousness() / 100f));
                y += 24;

                drawMetricCard(g, font, x, y, colW, tr("medical.detail.blood_pressure"),
                        data.getSystolicBP() + "/" + data.getDiastolicBP() + " (MAP " + (int) data.getMeanArterialPressure() + ")",
                        Mth.clamp(data.getMeanArterialPressure() / 130f, 0f, 1f), C_VITAL_BP);
                drawMetricCard(g, font, x + colW + 6, y, colW, tr("medical.detail.perceived_pain"),
                        data.getPerceivedPain() + " / " + MedicalConfig.MAX_PERCEIVED_PAIN.get(),
                        Mth.clamp(data.getPerceivedPain() / Math.max(10f, MedicalConfig.MAX_PERCEIVED_PAIN.get().floatValue()), 0f, 1f), C_VITAL_PAIN);
                y += 24;

                drawMetricCard(g, font, x, y, colW, tr("medical.detail.respiration") + " / SpO2",
                        data.getRespirationRate() + " rpm | " + data.getBloodOxygen() + "%",
                        data.getBloodOxygen() / 100f, C_VITAL_OXYGEN);
                int adrPct = Math.round(com.FIYTP.realmedicalmod.treatment.ModItems.getAdrenalineLevel(data));
                if (adrPct > 0 && data.getOpioidConcentration() == 0 && data.getAddictionLevel() == 0) {
                    int adrTier = com.FIYTP.realmedicalmod.treatment.ModItems.getAdrenalineTier(adrPct);
                    String aRoman = adrTier == 1 ? "I" : (adrTier == 2 ? "II" : "III");
                    drawMetricCard(g, font, x + colW + 6, y, colW, "肾上腺素",
                            adrPct + "% | 激增 " + aRoman, adrPct / 100f, 0xFFFFD600);
                } else if (data.getOpioidConcentration() > 0 || data.getAddictionLevel() > 0) {
                    int op = data.getOpioidConcentration();
                    int addLv = data.getAddictionLevel();
                    int addTier = com.FIYTP.realmedicalmod.treatment.ModItems.getAddictionTier(addLv);
                    String tierRoman = addTier == 1 ? "I" : (addTier == 2 ? "II" : (addTier >= 3 ? "III" : "-"));
                    int opCol = op >= MedicalConfig.OPIOID_OVERDOSE_THRESHOLD.get() ? C_ALERT_RED
                            : (addTier >= 2 ? 0xFFE040FB : (op >= MedicalConfig.OPIOID_RESP_DEPRESSION_START.get() ? C_WARN_YELLOW : 0xFFCE93D8));
                    drawMetricCard(g, font, x + colW + 6, y, colW, "阿片浓度 / 成瘾",
                            op + "% | 成瘾 " + tierRoman + " (" + addLv + "%)",
                            Math.max(op / 100f, addLv / 100f), opCol);
                } else {
                    drawMetricCard(g, font, x + colW + 6, y, colW, "SIRS / 烧伤面积",
                            "SIRS " + data.getSirsLevel() + " | " + String.format("%.1f%%", data.getBurnTBSA()),
                            Math.max(data.getSirsLevel() / 4f, data.getBurnTBSA() / 100f),
                            (data.getSirsLevel() > 0 || data.getBurnTBSA() > 0) ? C_WARN_YELLOW : 0xFF37474F);
                }
                y += 24;

            } else {
                y = drawFullRow(g, font, x, y, w, tr("medical.detail.blood_volume"),
                        data.getBloodVolume() + " mL", data.getBloodVolume() / 5000f, C_VITAL_HEART);
                if (totalClampedBleed > 0 && totalBleed <= 0) {
                    y = drawDashedBleedFullRow(g, font, x, y, w, tr("medical.detail.total_bleeding_rate"),
                            totalClampedBleed + " mL/min", Math.min(1f, totalClampedBleed / 600f), C_ALERT_RED);
                } else {
                    y = drawFullRow(g, font, x, y, w, tr("medical.detail.total_bleeding_rate"),
                            totalBleed + " mL/min", Math.min(1f, totalBleed / 600f), totalBleed > 0 ? C_ALERT_RED : 0xFF455A64);
                }
                y = drawFullRow(g, font, x, y, w, tr("medical.detail.brain_health"),
                        data.getBrainHealth() + "%", data.getBrainHealth() / 100f, getHealthColor(data.getBrainHealth() / 100f));
                y = drawFullRow(g, font, x, y, w, tr("medical.detail.consciousness"),
                        data.getConsciousness() + "%", data.getConsciousness() / 100f, getHealthColor(data.getConsciousness() / 100f));
                y = drawFullRow(g, font, x, y, w, tr("medical.detail.perceived_pain"),
                        data.getPerceivedPain() + " / " + MedicalConfig.MAX_PERCEIVED_PAIN.get(), data.getPerceivedPain() / Math.max(10f, MedicalConfig.MAX_PERCEIVED_PAIN.get().floatValue()), C_VITAL_PAIN);
                y = drawFullRow(g, font, x, y, w, tr("medical.detail.blood_pressure"),
                        data.getSystolicBP() + "/" + data.getDiastolicBP() + " mmHg", data.getMeanArterialPressure() / 130f, C_VITAL_BP);
            }

            return y;
        }

        private void drawMetricCard(GuiGraphics g, Font font, int x, int y, int w,
                                    String label, String val, float pct, int color) {
            pct = Mth.clamp(pct, 0.0f, 1.0f);
            g.fill(x, y, x + w, y + 21, 0x88071320);
            g.fill(x, y, x + 2, y + 21, color);
            g.drawString(font, label, x + 5, y + 2, 0xFFB0BEC5, false);
            int vw = font.width(val);
            g.drawString(font, val, x + w - vw - 3, y + 2, color, false);
            HudRenderHelper.drawSciFiProgressBar(g, x + 5, y + 13, w - 8, 5, pct, color, 0xFF050C14);
        }

        private int drawFullRow(GuiGraphics g, Font font, int x, int y, int w,
                                String label, String val, float pct, int color) {
            pct = Mth.clamp(pct, 0.0f, 1.0f);
            g.drawString(font, label, x, y, 0xFFB0BEC5, false);
            int vw = font.width(val);
            g.drawString(font, val, x + w - vw, y, color, false);
            HudRenderHelper.drawSciFiProgressBar(g, x, y + 10, w, 5, pct, color, 0xFF07121E);
            return y + 18;
        }

        private void drawPerforationHazardTicks(GuiGraphics g, int x, int y, int w, int h, float pct) {
            int fillW = Math.round(w * Mth.clamp(pct, 0.0f, 1.0f));
            for (int dx = 3; dx < fillW - 2; dx += 6) {
                g.fill(x + dx, y + 1, x + dx + 2, y + h - 1, 0xFFFF9100);
            }
        }

        /**
         * 止血带绑扎期间：以虚线进度条 + 虚线数值下划线表示拿掉止血带后的实际出血量（无任何多余文字描述）
         */
        private void drawDashedBleedCard(GuiGraphics g, Font font, int x, int y, int w,
                                         String label, String val, float pct, int color) {
            pct = Mth.clamp(pct, 0.0f, 1.0f);
            g.fill(x, y, x + w, y + 21, 0x88071320);
            for (int dy = 0; dy < 21; dy += 4) {
                g.fill(x, y + dy, x + 2, Math.min(y + 21, y + dy + 2), color);
            }
            g.drawString(font, label, x + 5, y + 2, 0xFFB0BEC5, false);
            int vw = font.width(val);
            int vx = x + w - vw - 3;
            g.drawString(font, val, vx, y + 2, withAlpha(color, 0.85f), false);
            for (int dx = 0; dx < vw; dx += 4) {
                g.fill(vx + dx, y + 10, Math.min(vx + vw, vx + dx + 2), y + 11, color);
            }
            drawDashedProgressBar(g, x + 5, y + 13, w - 8, 5, pct, color, 0xFF050C14);
        }

        private int drawDashedBleedFullRow(GuiGraphics g, Font font, int x, int y, int w,
                                           String label, String val, float pct, int color) {
            pct = Mth.clamp(pct, 0.0f, 1.0f);
            g.drawString(font, label, x, y, 0xFFB0BEC5, false);
            int vw = font.width(val);
            int vx = x + w - vw;
            g.drawString(font, val, vx, y, withAlpha(color, 0.85f), false);
            for (int dx = 0; dx < vw; dx += 4) {
                g.fill(vx + dx, y + 8, Math.min(vx + vw, vx + dx + 2), y + 9, color);
            }
            drawDashedProgressBar(g, x, y + 10, w, 5, pct, color, 0xFF07121E);
            return y + 18;
        }

        private void drawDashedProgressBar(GuiGraphics g, int x, int y, int w, int h, float pct, int color, int bgColor) {
            g.fill(x, y, x + w, y + h, bgColor);
            // 虚线外框
            for (int dx = 0; dx < w; dx += 5) {
                int segEnd = Math.min(x + w, x + dx + 3);
                g.fill(x + dx, y, segEnd, y + 1, withAlpha(color, 0.55f));
                g.fill(x + dx, y + h - 1, segEnd, y + h, withAlpha(color, 0.55f));
            }
            int fillW = Math.round(w * Mth.clamp(pct, 0.0f, 1.0f));
            for (int dx = 0; dx < fillW; dx += 5) {
                int segEnd = Math.min(x + fillW, x + dx + 3);
                g.fill(x + dx, y + 1, segEnd, y + h - 1, withAlpha(color, 0.88f));
            }
        }

        private int drawLimbStatusPills(GuiGraphics g, Font font, int x, int y, int w, MedicalData.LimbData ld) {
            int px = x;
            int py = y;
            int rightEdge = x + w;
            boolean abnormal = false;

            if (ld.hasPerforation()) {
                abnormal = true;
                boolean packed = com.FIYTP.realmedicalmod.treatment.ModItems.isPerforationPacked(ld);
                String pTxt = packed ? tr("medical.status.perforation_packed") : tr("medical.status.perforation");
                int pCol = packed ? 0xFFFF9100 : 0xFFFF2A85;
                int nx = drawWrappedPill(g, font, x, px, py, rightEdge, pTxt, pCol);
                if (nx < 0) { py += 14; px = drawWrappedPill(g, font, x, x, py, rightEdge, pTxt, pCol); } else px = nx;
            }
            int pillEmb = com.FIYTP.realmedicalmod.treatment.ModItems.getEmbeddedBulletCount(ld);
            if (pillEmb > 0) {
                abnormal = true;
                String eTxt = Component.translatable("medical.status.embedded_bullet", pillEmb).getString();
                int nx = drawWrappedPill(g, font, x, px, py, rightEdge, eTxt, 0xFFFFC400);
                if (nx < 0) { py += 14; px = drawWrappedPill(g, font, x, x, py, rightEdge, eTxt, 0xFFFFC400); } else px = nx;
            }
            if (ld.isAmputated()) {
                abnormal = true;
                int nx = drawWrappedPill(g, font, x, px, py, rightEdge, tr("medical.status.amputated"), 0xFFFF1744);
                if (nx < 0) { py += 14; px = drawWrappedPill(g, font, x, x, py, rightEdge, tr("medical.status.amputated"), 0xFFFF1744); } else px = nx;
            }
            if (com.FIYTP.realmedicalmod.treatment.ModItems.isLimbNecrotic(ld)) {
                abnormal = true;
                int nx = drawWrappedPill(g, font, x, px, py, rightEdge, "肢体坏死", 0xFFCE93D8);
                if (nx < 0) { py += 14; px = drawWrappedPill(g, font, x, x, py, rightEdge, "肢体坏死", 0xFFCE93D8); } else px = nx;
            }
            if (com.FIYTP.realmedicalmod.treatment.ModItems.isCompartmentSyndrome(ld)) {
                abnormal = true;
                int nx = drawWrappedPill(g, font, x, px, py, rightEdge, "骨筋膜室综合征", 0xFFFF6D00);
                if (nx < 0) { py += 14; px = drawWrappedPill(g, font, x, x, py, rightEdge, "骨筋膜室综合征", 0xFFFF6D00); } else px = nx;
            }
            if (ld.isBandaged()) {
                px = drawWrappedPill(g, font, x, px, py, rightEdge, tr("medical.status.bandaged"), 0xFF00E676);
                if (px < 0) { py += 14; px = drawWrappedPill(g, font, x, x, py, rightEdge, tr("medical.status.bandaged"), 0xFF00E676); }
            }
            if (ld.isTourniqueted()) {
                int nx = drawWrappedPill(g, font, x, px, py, rightEdge, tr("medical.status.tourniquet"), 0xFFFF9100);
                if (nx < 0) { py += 14; px = drawWrappedPill(g, font, x, x, py, rightEdge, tr("medical.status.tourniquet"), 0xFFFF9100); } else px = nx;
            }
            if (ld.getInternalBleedingRate() > 0) {
                abnormal = true;
                int nx = drawWrappedPill(g, font, x, px, py, rightEdge, tr("medical.status.internal_bleeding"), 0xFFE040FB);
                if (nx < 0) { py += 14; px = drawWrappedPill(g, font, x, x, py, rightEdge, tr("medical.status.internal_bleeding"), 0xFFE040FB); } else px = nx;
            }
            if (ld.hasFracture()) {
                abnormal = true;
                boolean orthoFixed = com.FIYTP.realmedicalmod.treatment.ModItems.isLimbOrthopedicFixed(ld);
                boolean chestBraced = com.FIYTP.realmedicalmod.treatment.ModItems.isChestBraced(ld);
                String fTxt;
                int fCol;
                if (orthoFixed) {
                    fTxt = "骨科外固定锁定";
                    fCol = 0xFF00E5FF;
                } else if (ld.isOpenFracture()) {
                    fTxt = ld.hasSplint() ? "开放骨折(夹板禁忌!)" : "开放/连枷骨折(需手术)";
                    fCol = 0xFFFF1744;
                } else if (ld.hasSplint()) {
                    fTxt = tr("medical.status.fracture_splinted");
                    fCol = 0xFF00E676;
                } else if (chestBraced) {
                    fTxt = "肋骨骨折(胸带支撑)";
                    fCol = 0xFF00E676;
                } else {
                    fTxt = tr("medical.status.fracture");
                    fCol = 0xFFFF1744;
                }
                int nx = drawWrappedPill(g, font, x, px, py, rightEdge, fTxt, fCol);
                if (nx < 0) { py += 14; px = drawWrappedPill(g, font, x, x, py, rightEdge, fTxt, fCol); } else px = nx;
            }
            if (ld.hasDislocation()) {
                abnormal = true;
                int nx = drawWrappedPill(g, font, x, px, py, rightEdge, tr("medical.status.dislocation"), 0xFFFFD600);
                if (nx < 0) { py += 14; px = drawWrappedPill(g, font, x, x, py, rightEdge, tr("medical.status.dislocation"), 0xFFFFD600); } else px = nx;
            }
            if (ld.getInfection() > 0) {
                abnormal = true;
                String iTxt = ld.getInfection() <= 25 ? tr("medical.status.mild_infection")
                        : ld.getInfection() <= 50 ? tr("medical.status.moderate_infection")
                        : ld.getInfection() <= 75 ? tr("medical.status.severe_infection")
                        : tr("medical.status.critical_infection");
                int nx = drawWrappedPill(g, font, x, px, py, rightEdge, iTxt, 0xFFFFB300);
                if (nx < 0) { py += 14; px = drawWrappedPill(g, font, x, x, py, rightEdge, iTxt, 0xFFFFB300); } else px = nx;
            }
            if (!abnormal && !ld.isBandaged() && !ld.isTourniqueted() && ld.getBleeding() <= 0) {
                drawPill(g, font, px, py, tr("medical.status.healthy"), 0xFF00E676);
            }
            return py + 14;
        }

        private int drawWrappedPill(GuiGraphics g, Font font, int startX, int curX, int curY, int rightEdge, String text, int color) {
            int pw = font.width(text) + 8;
            if (curX > startX && curX + pw > rightEdge) return -1;
            drawPill(g, font, curX, curY, text, color);
            return curX + pw + 4;
        }

        private void drawPill(GuiGraphics g, Font font, int x, int y, String text, int color) {
            int pw = font.width(text) + 8;
            g.fill(x, y, x + pw, y + 11, withAlpha(color, 0.22f));
            g.fill(x, y, x + 2, y + 11, color);
            g.drawString(font, text, x + 4, y + 2, color, false);
        }

        private void drawCollapsedEcgStrip(GuiGraphics g, Font font, int x, int y, int w, int h, MedicalData data, int themeAccent) {
            HudRenderHelper.drawSciFiCard(g, x, y, w, h, 2, 0xAA040C14, withAlpha(themeAccent, 0.45f));
            String info = "心电监护已折叠 (点击右上角「心电 ▲」展开)";
            g.drawString(font, info, x + 6, y + 5, themeAccent, false);
        }

        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (unbindTargetLimb != null && btnUnbindW > 0
                    && mouseX >= btnUnbindX && mouseX <= btnUnbindX + btnUnbindW
                    && mouseY >= btnUnbindY && mouseY <= btnUnbindY + btnUnbindH) {
                com.FIYTP.realmedicalmod.network.NetworkHandler.sendTreatmentToServer(
                        new com.FIYTP.realmedicalmod.network.TreatmentApplyPacket(0, unbindTargetLimb.ordinal(), -1));
                MedicalSoundHelper.playLimbSelectSound();
                return true;
            }
            if (unbindSplintTargetLimb != null && btnUnbindSplintW > 0
                    && mouseX >= btnUnbindSplintX && mouseX <= btnUnbindSplintX + btnUnbindSplintW
                    && mouseY >= btnUnbindSplintY && mouseY <= btnUnbindSplintY + btnUnbindSplintH) {
                com.FIYTP.realmedicalmod.network.NetworkHandler.sendTreatmentToServer(
                        new com.FIYTP.realmedicalmod.network.TreatmentApplyPacket(0, unbindSplintTargetLimb.ordinal(), -2));
                MedicalSoundHelper.playLimbSelectSound();
                return true;
            }
            if (mouseX >= btnEcgX && mouseX <= btnEcgX + btnEcgW && mouseY >= btnEcgY && mouseY <= btnEcgY + btnEcgH) {
                this.ecgCollapsed = !this.ecgCollapsed;
                MedicalSoundHelper.playLimbSelectSound();
                return true;
            }
            if (mouseX >= btnCompactX && mouseX <= btnCompactX + btnCompactW && mouseY >= btnCompactY && mouseY <= btnCompactY + btnCompactH) {
                this.compactTwoColumn = !this.compactTwoColumn;
                this.scrollTarget = 0.0f;
                MedicalSoundHelper.playLimbSelectSound();
                return true;
            }
            return false;
        }

        public boolean mouseScrolled(double mouseX, double mouseY, double deltaY) {
            if (viewportW > 0 && mouseX >= viewportX && mouseX <= viewportX + viewportW
                    && mouseY >= viewportY && mouseY <= viewportY + viewportH && maxScroll > 0.5f) {
                scrollTarget = Mth.clamp(scrollTarget - (float) deltaY * 18.0f, 0.0f, maxScroll);
                return true;
            }
            return false;
        }
    }

    // =========================================================================
    // 九、3A 级全息 3D 心肺与胸腔动态扫描仪 (Cardiopulmonary3DRenderer - 高精解剖点云与分形支气管重制版)
    //
    // 解剖学与视觉架构（对标 BrainScan3DRenderer 2800+ 顶点高密度全息点云 + 微米级线网）：
    // 1. 高密度双肺解剖曲面点云 (1,920 顶点 + 菲涅尔边缘发光 + 斜裂/水平裂 + 左肺心切迹与舌叶)：
    //    - 右肺：上叶、中叶、下叶，清晰雕刻斜裂 (Oblique Fissure) 与水平裂 (Horizontal Fissure) 凹槽；
    //    - 左肺：上叶、下叶，内下侧精准内凹形成容纳心脏的「心切迹 (Cardiac Notch)」与「舌叶 (Lingula)」；
    //    - 随 getRespirationRate() 驱动真实生理呼吸：胸廓径向扩张 + 膈穹窿下沉，支持 CT 激光扫描平面高亮。
    // 2. 5 级递归 3D 分形气管-支气管树 (Trachea + Carina + 220 段叶/段/亚段支气管 + 18 环气管软骨环)：
    //    - 含 C 型气管软骨环细节，自气管隆突 (Carina) 向双肺各肺叶递归分叉 5 代至终末细支气管肺泡囊，
    //    - 内置 48 颗随吸气/呼气双向流动的氧合气流光粒。
    // 3. 四腔室高精 3D 心脏 + 冠状动脉树 (LAD/LCX/RCA) + 主动脉弓三分支 + 上腔静脉/肺动脉干 (860+ 顶点)：
    //    - 精确还原左前下方 40° 心轴与心尖 (Apex Cordis)、前室间沟与房室冠状沟；
    //    - 主动脉弓顶部伸出头臂干、左颈总动脉、左锁骨下动脉三大分支，右侧接入蓝色上/下腔静脉；
    //    - 严格按 Wiggers 心动周期随 getHeartRate() 呈现「心房先缩 (P波) -> 心室螺旋向心强力收缩 (QRS波) ->
    //      主动脉弓扩张与冠状动脉脉冲波」，室颤 (VFib) 时心肌高频杂乱电弧乱颤，骤停时失血暗化静止。
    // 4. 肋膈隐窝贴合式 3D 胸腔积血池 (Hemothorax) 与肺实质内 3D 肺泡挫伤云团 (Lung Contusion)。
    // =========================================================================
    private static class Cardiopulmonary3DRenderer {

        public enum ThoraxViewMode {
            SPLIT("分屏"),
            HOLO_FULL("全息"),
            CLINICAL_GRID("列表");

            public final String label;
            ThoraxViewMode(String label) { this.label = label; }
        }

        /**
         * 肺部与心脏高密度全息点云顶点
         * region: 0=右肺, 1=左肺, 2=左/右心室心肌, 3=心房, 4=主动脉与动脉干, 5=上下腔静脉与肺动脉
         */
        private static class OrganVertex {
            final float x, y, z;
            final float nx, ny, nz;
            final int region;
            final int lobeId; // 0=上叶, 1=中叶, 2=下叶
            final float surfaceTex; // 肺小叶或心肌纤维微纹理 (0~1)
            final int connectA;
            final int connectB;

            OrganVertex(float x, float y, float z, float nx, float ny, float nz,
                        int region, int lobeId, float surfaceTex, int connectA, int connectB) {
                this.x = x; this.y = y; this.z = z;
                this.nx = nx; this.ny = ny; this.nz = nz;
                this.region = region;
                this.lobeId = lobeId;
                this.surfaceTex = surfaceTex;
                this.connectA = connectA;
                this.connectB = connectB;
            }
        }

        private record AirwayBranch(
                float x1, float y1, float z1,
                float x2, float y2, float z2,
                int generation, boolean isLeft, float phaseSeed
        ) {}

        private record CoronarySegment(
                float x1, float y1, float z1,
                float x2, float y2, float z2,
                float distFromRoot, float width
        ) {}

        private final List<OrganVertex> lungVertices = new ArrayList<>();
        private final List<OrganVertex> heartVertices = new ArrayList<>();
        private final List<AirwayBranch> airwayTree = new ArrayList<>();
        private final List<CoronarySegment> coronaryTree = new ArrayList<>();

        private ThoraxViewMode currentMode = ThoraxViewMode.SPLIT;
        private boolean ecgCollapsed = false;

        private float yaw = -0.18f;
        private float pitch = 0.12f;
        private boolean dragging = false;
        private long lastInteractionMs = 0L;

        private float heartPhase = 0.0f;
        private float respPhase = 0.0f;

        private float scrollOffset = 0.0f;
        private float scrollTarget = 0.0f;
        private float maxScroll = 0.0f;

        private int lastPanelX, lastPanelY, lastPanelW;
        private int holoX, holoY, holoW, holoH;
        private int scrollX, scrollY, scrollW, scrollH;
        private int btnEcgX, btnEcgY, btnEcgW, btnEcgH;

        public Cardiopulmonary3DRenderer() {
            generateHighDensityLungMesh();
            generateFractalBronchialTree();
            generateHighDensityHeartAndVessels();
        }

        /**
         * 1. 构建高密度解剖级双肺 3D 点云网格（28 层纬环 × 34 经度 = 1,904 个解剖顶点）
         *    精确还原：圆润肺尖、前锐后钝的肋面、内侧纵隔面、底部内凹的膈肌穹窿、
         *              右肺三叶（斜裂+水平裂）、左肺两叶（斜裂+深度心切迹 Cardiac Notch + 舌叶 Lingula）。
         */
        private void generateHighDensityLungMesh() {
            lungVertices.clear();
            int rings = 28;
            int sectors = 34;

            for (int side = -1; side <= 1; side += 2) {
                // 解剖正面观：屏幕左侧 (side = -1) 为患者右肺 (region 0)，屏幕右侧 (side = +1) 为患者左肺 (region 1)
                boolean isLeftLung = (side > 0);
                int region = isLeftLung ? 1 : 0;
                int startIdx = lungVertices.size();

                for (int i = 0; i < rings; i++) {
                    float v = i / (float) (rings - 1); // 0.0 = 肺尖 Apex, 1.0 = 肺底 Base
                    float theta = v * (float) Math.PI;
                    float sinT = Mth.sin(theta);

                    // 纵向高度：肺尖 -0.74 -> 肺底 +0.62
                    float baseY = -0.74f + v * 1.36f;

                    // 真实肺横截面包络：上部圆锥渐宽，中下部宽厚饱满，最底部贴合膈肌急收
                    float coneEnv = (float) Math.pow(sinT, 0.72) * (0.62f + 0.52f * v);
                    if (v > 0.88f) {
                        coneEnv *= Mth.lerp((v - 0.88f) / 0.12f, 1.0f, 0.35f);
                    }

                    float radX = (isLeftLung ? 0.34f : 0.37f) * coneEnv;
                    float radZ = 0.42f * coneEnv;
                    // 肺门与纵隔偏移：自肺尖向肺底略微向外展开
                    float centerOffsetX = side * (0.19f + 0.22f * Mth.sin(v * 1.35f));

                    for (int j = 0; j < sectors; j++) {
                        float u = j / (float) sectors;
                        float phi = u * Mth.TWO_PI;
                        float cosP = Mth.cos(phi);
                        float sinP = Mth.sin(phi);

                        float lx = cosP * radX;
                        float lz = sinP * radZ;
                        float ly = baseY;

                        // 内侧纵隔面 (Medial Mediastinal Surface) 压平并微凹
                        boolean isMedial = (side * cosP < 0.0f);
                        if (isMedial) {
                            lx *= 0.46f;
                            // 左肺前下内侧雕刻出深邃自然的「心切迹 (Cardiac Notch)」以包裹 3D 心脏
                            if (isLeftLung && v > 0.34f && v < 0.88f && sinP > -0.35f) {
                                float notchV = Mth.sin(((v - 0.34f) / 0.54f) * (float) Math.PI);
                                float notchZ = Mth.clamp((sinP + 0.35f) / 1.35f, 0.0f, 1.0f);
                                float notchDepth = 0.21f * notchV * notchZ;
                                lx += notchDepth; // 向外侧退让出心包腔空间
                            }
                        } else {
                            // 外侧肋面 (Costal Surface) 后外侧更饱满
                            if (sinP < 0.0f) {
                                lx *= 1.08f;
                            }
                        }

                        // 膈面 (Diaphragmatic Base)：肺底中央向内上方凹陷（贴合左/右膈肌穹窿），外后缘向下延伸为肋膈角
                        if (v > 0.78f) {
                            float baseBlend = (v - 0.78f) / 0.22f;
                            float radialNorm = Mth.clamp(cosP * cosP + sinP * sinP, 0.0f, 1.0f);
                            ly -= 0.11f * baseBlend * (1.0f - 0.65f * radialNorm);
                            // 后外侧肋膈角向下深探
                            if (sinP < 0.0f) {
                                ly += 0.06f * baseBlend * (-sinP);
                            }
                        }

                        float wx = centerOffsetX + lx;
                        float wy = ly;
                        float wz = lz;

                        // 判定肺叶归属并雕刻解剖学「斜裂 (Oblique Fissure)」与右肺「水平裂 (Horizontal Fissure)」
                        // 斜裂平面：由后上 (y=-0.28, z=-0.3) 斜向前下 (y=+0.45, z=+0.3)
                        float obliquePlaneY = 0.08f + wz * 0.85f;
                        float distOblique = Math.abs(wy - obliquePlaneY);
                        // 右肺水平裂平面：前中部水平向 (y = -0.04, wz > -0.05)
                        float distHoriz = (!isLeftLung && wz > -0.08f && wy < obliquePlaneY + 0.05f)
                                ? Math.abs(wy - (-0.04f))
                                : 999.0f;

                        int lobeId;
                        if (wy > obliquePlaneY) {
                            lobeId = 2; // 下叶 (Inferior Lobe)
                        } else if (!isLeftLung && wy > -0.04f && wz > -0.08f) {
                            lobeId = 1; // 右肺中叶 (Middle Lobe)
                        } else {
                            lobeId = 0; // 上叶 (Superior Lobe)
                        }

                        // 在叶间裂处向内收束形成清晰的 3D 叶间裂沟槽
                        float fissureIndent = 1.0f;
                        if (distOblique < 0.055f) {
                            fissureIndent -= 0.14f * (1.0f - distOblique / 0.055f);
                        }
                        if (distHoriz < 0.045f) {
                            fissureIndent -= 0.12f * (1.0f - distHoriz / 0.045f);
                        }
                        wx = centerOffsetX + (wx - centerOffsetX) * fissureIndent;
                        wz *= fissureIndent;

                        // 肺小叶多边形微起伏纹理
                        float lobule1 = Mth.sin(wx * 14.0f + wz * 11.0f) * Mth.cos(wy * 12.0f - wz * 9.0f);
                        float lobule2 = Mth.sin(wy * 19.0f + wx * 15.0f) * 0.5f;
                        float tex = Mth.clamp(0.5f + 0.35f * lobule1 + 0.25f * lobule2, 0.0f, 1.0f);
                        // 叶间裂边缘高亮标记
                        if (distOblique < 0.035f || distHoriz < 0.030f) {
                            tex = 1.0f;
                        }

                        float nx = wx - centerOffsetX;
                        float ny = (wy - baseY) * 0.5f;
                        float nz = wz;
                        float nLen = Mth.sqrt(nx * nx + ny * ny + nz * nz);
                        if (nLen > 0.0001f) {
                            nx /= nLen; ny /= nLen; nz /= nLen;
                        }

                        int curIdx = lungVertices.size();
                        int prevInRing = (j > 0) ? (curIdx - 1) : (curIdx + sectors - 1);
                        int prevRing = (i > 0 && (j % 2 == 0)) ? (curIdx - sectors) : -1;

                        lungVertices.add(new OrganVertex(wx, wy, wz, nx, ny, nz, region, lobeId, tex, prevInRing, prevRing));
                    }
                }
            }
        }

        /**
         * 2. 构建 5 代递归 3D 分形气管-支气管树 (Trachea + Carina + Lobar/Segmental/Subsegmental Bronchi)
         */
        private void generateFractalBronchialTree() {
            airwayTree.clear();
            Random rand = new Random(19980715L);

            // 主气管 (Trachea)：由喉部 नीचे延伸至气管隆突 (Carina: 0.0, -0.28, -0.05)
            int tracheaSteps = 8;
            for (int i = 0; i < tracheaSteps; i++) {
                float y1 = Mth.lerp(i / (float) tracheaSteps, -0.86f, -0.28f);
                float y2 = Mth.lerp((i + 1) / (float) tracheaSteps, -0.86f, -0.28f);
                airwayTree.add(new AirwayBranch(0.0f, y1, -0.05f, 0.0f, y2, -0.05f, 0, false, i * 0.12f));
            }

            // 右主支气管 (较粗短陡直，进入右肺门 x = -0.24, y = -0.14)
            airwayTree.add(new AirwayBranch(0.0f, -0.28f, -0.05f, -0.24f, -0.14f, -0.04f, 1, false, 0.1f));
            // 右上叶支气管 (Eparterial bronchus)
            buildSubBronchi(-0.24f, -0.14f, -0.04f, -0.16f, -0.22f, 0.02f, 2, 5, false, rand);
            buildSubBronchi(-0.24f, -0.14f, -0.04f, -0.22f, -0.08f, -0.08f, 2, 5, false, rand);
            // 右中间支气管 -> 右中叶 & 右下叶
            airwayTree.add(new AirwayBranch(-0.24f, -0.14f, -0.04f, -0.29f, 0.05f, -0.03f, 1, false, 0.3f));
            buildSubBronchi(-0.29f, 0.05f, -0.03f, -0.18f, 0.10f, 0.12f, 2, 5, false, rand);  // 右中叶
            buildSubBronchi(-0.29f, 0.05f, -0.03f, -0.15f, 0.26f, -0.06f, 2, 5, false, rand); // 右下叶基底段
            buildSubBronchi(-0.29f, 0.05f, -0.03f, -0.08f, 0.28f, 0.04f, 2, 5, false, rand);  // 右下叶内基底段

            // 左主支气管 (较细长倾斜，从主动脉弓下方跨过进入左肺门 x = +0.26, y = -0.11)
            airwayTree.add(new AirwayBranch(0.0f, -0.28f, -0.05f, 0.26f, -0.11f, -0.04f, 1, true, 0.15f));
            // 左上叶固有段与舌叶支气管
            buildSubBronchi(0.26f, -0.11f, -0.04f, 0.15f, -0.24f, 0.02f, 2, 5, true, rand);  // 左上叶尖后段
            buildSubBronchi(0.26f, -0.11f, -0.04f, 0.21f, -0.06f, -0.09f, 2, 5, true, rand); // 左上叶前段
            buildSubBronchi(0.26f, -0.11f, -0.04f, 0.18f, 0.14f, 0.09f, 2, 5, true, rand);   // 左肺舌叶段
            // 左下叶支气管
            airwayTree.add(new AirwayBranch(0.26f, -0.11f, -0.04f, 0.32f, 0.08f, -0.04f, 1, true, 0.35f));
            buildSubBronchi(0.32f, 0.08f, -0.04f, 0.16f, 0.26f, -0.06f, 2, 5, true, rand);  // 左下叶外基底段
            buildSubBronchi(0.32f, 0.08f, -0.04f, 0.08f, 0.28f, 0.04f, 2, 5, true, rand);   // 左下叶前内基底段
        }

        private void buildSubBronchi(float x, float y, float z, float dx, float dy, float dz,
                                     int gen, int maxGen, boolean isLeft, Random rand) {
            float x2 = x + dx;
            float y2 = y + dy;
            float z2 = z + dz;
            // 约束在肺实质边界内
            x2 = isLeft ? Mth.clamp(x2, 0.16f, 0.56f) : Mth.clamp(x2, -0.58f, -0.16f);
            y2 = Mth.clamp(y2, -0.66f, 0.52f);
            z2 = Mth.clamp(z2, -0.32f, 0.32f);

            airwayTree.add(new AirwayBranch(x, y, z, x2, y2, z2, gen, isLeft, rand.nextFloat()));
            if (gen >= maxGen) return;

            float scale = 0.64f;
            float spread = 0.09f / gen;
            // 分叉出两支子代支气管
            buildSubBronchi(x2, y2, z2,
                    dx * scale + (rand.nextFloat() - 0.35f) * spread * (isLeft ? 1 : -1),
                    dy * scale + (rand.nextFloat() - 0.45f) * spread,
                    dz * scale + (rand.nextFloat() - 0.2f) * spread,
                    gen + 1, maxGen, isLeft, rand);
            buildSubBronchi(x2, y2, z2,
                    dx * scale - (rand.nextFloat() - 0.35f) * spread * (isLeft ? 1 : -1),
                    dy * scale + (rand.nextFloat() - 0.35f) * spread,
                    dz * scale - (rand.nextFloat() - 0.2f) * spread,
                    gen + 1, maxGen, isLeft, rand);
        }

        /**
         * 3. 构建高密度四腔室 3D 心脏 + 主动脉弓三分支 + 肺动脉干 + 上下腔静脉 + 冠状动脉树
         */
        private void generateHighDensityHeartAndVessels() {
            heartVertices.clear();
            coronaryTree.clear();

            // A. 左右心室与心房主体 (22 纬环 × 28 经度 = 616 顶点，精准雕刻前室间沟、冠状沟与左前下心尖)
            int hRings = 22;
            int hSectors = 28;
            float baseHeartX = 0.08f;
            float baseHeartY = 0.14f;
            float baseHeartZ = 0.11f;

            for (int i = 0; i < hRings; i++) {
                float v = i / (float) (hRings - 1); // 0.0 = 心底房室部, 1.0 = 左下心尖 (Apex Cordis)
                // 心脏纵轴向患者左前下方倾斜 (x 向 +0.20 偏移, y 从 -0.06 降至 +0.40, z 向 +0.12 倾斜)
                float axisX = baseHeartX + (float) Math.pow(v, 1.15) * 0.19f;
                float axisY = baseHeartY - 0.18f + v * 0.46f;
                float axisZ = baseHeartZ + v * 0.11f;

                // 心脏长轴包络：心底宽厚 -> 心室中部最饱满 -> 心尖圆锥收拢
                float env = Mth.sin(v * (float) Math.PI * 0.92f) * (1.12f - 0.32f * v);
                if (v < 0.26f) {
                    // 冠状沟 (Coronary Sulcus, v ≈ 0.26) 略微内束区分心房与心室
                    float sulcus = Math.abs(v - 0.25f);
                    if (sulcus < 0.06f) env *= (0.90f + 0.10f * (sulcus / 0.06f));
                }

                float rx = 0.21f * env;
                float rz = 0.18f * env;
                int region = (v < 0.25f) ? 3 : 2; // 3 = 心房部, 2 = 心室心肌部

                for (int j = 0; j < hSectors; j++) {
                    float u = j / (float) hSectors;
                    float phi = u * Mth.TWO_PI;
                    float cosP = Mth.cos(phi);
                    float sinP = Mth.sin(phi);

                    // 前室间沟 (Anterior Interventricular Sulcus, phi ≈ 1.15 rad) 凹陷分隔右心室与左心室
                    float distSulcus = Math.abs(phi - 1.15f);
                    float groove = (distSulcus < 0.28f && v > 0.22f)
                            ? (1.0f - 0.14f * (1.0f - distSulcus / 0.28f))
                            : 1.0f;

                    float wx = axisX + cosP * rx * groove;
                    float wy = axisY + sinP * 0.03f * env;
                    float wz = axisZ + sinP * rz * groove;

                    // 螺旋心肌纤维纹理 (Helical Myocardial Fiber Orientation)
                    float fiberTex = 0.5f + 0.5f * Mth.sin(phi * 6.0f + v * 14.0f);
                    if (distSulcus < 0.18f) fiberTex = 1.0f;

                    int curIdx = heartVertices.size();
                    int prevInRing = (j > 0) ? (curIdx - 1) : (curIdx + hSectors - 1);
                    int prevRing = (i > 0 && (j % 2 == 0)) ? (curIdx - hSectors) : -1;

                    heartVertices.add(new OrganVertex(wx, wy, wz, cosP, 0.2f, sinP, region, 0, fiberTex, prevInRing, prevRing));
                }
            }

            // B. 升主动脉 + 主动脉弓 (Aortic Arch) + 三大弓上动脉分支 (头臂干、左颈总、左锁骨下)
            int archSteps = 16;
            int tubeSectors = 10;
            for (int i = 0; i < archSteps; i++) {
                float t = i / (float) (archSteps - 1);
                // 从左心室流出道 (0.09, -0.02, 0.10) 向上向右前升起，再向左后方弯成主动脉弓，最后沿脊柱旁下行
                float ang = t * (float) Math.PI * 1.18f;
                float cx = 0.07f - Mth.sin(ang * 0.85f) * 0.06f + (t > 0.5f ? (t - 0.5f) * 0.12f : -t * 0.04f);
                float cy = -0.02f - Mth.sin(ang) * 0.34f + (t > 0.75f ? (t - 0.75f) * 0.45f : 0.0f);
                float cz = 0.10f - t * 0.16f;
                float tubeR = 0.052f * (1.0f - 0.15f * t);

                for (int s = 0; s < tubeSectors; s++) {
                    float phi = (s / (float) tubeSectors) * Mth.TWO_PI;
                    float vx = cx + Mth.cos(phi) * tubeR;
                    float vy = cy + Mth.sin(phi) * tubeR * 0.6f;
                    float vz = cz + Mth.sin(phi) * tubeR;

                    int curIdx = heartVertices.size();
                    int prevInRing = (s > 0) ? (curIdx - 1) : (curIdx + tubeSectors - 1);
                    int prevRing = (i > 0 && (s % 2 == 0)) ? (curIdx - tubeSectors) : -1;
                    heartVertices.add(new OrganVertex(vx, vy, vz, Mth.cos(phi), -0.3f, Mth.sin(phi), 4, i, t, prevInRing, prevRing));
                }
            }

            // 主动脉弓顶部三大动脉干：1.头臂干 (Brachiocephalic)  2.左颈总动脉 (LCC)  3.左锁骨下动脉 (LSA)
            float[][] archBranches = new float[][]{
                    {0.02f, -0.33f, 0.04f, -0.05f, -0.56f, 0.02f},
                    {0.06f, -0.35f, 0.02f,  0.05f, -0.58f, 0.00f},
                    {0.10f, -0.33f, -0.01f, 0.13f, -0.55f, -0.02f}
            };
            for (float[] br : archBranches) {
                addTubularVessel(br[0], br[1], br[2], br[3], br[4], br[5], 0.022f, 5, 6, 4);
            }

            // C. 上腔静脉 (Superior Vena Cava - 右侧蓝色大静脉) 与 肺动脉干 (Pulmonary Trunk)
            addTubularVessel(-0.07f, -0.46f, 0.05f, -0.05f, 0.04f, 0.07f, 0.042f, 7, 8, 5); // 上腔静脉 SVC
            addTubularVessel(-0.05f, 0.26f, 0.04f, -0.05f, 0.50f, 0.03f, 0.040f, 5, 8, 5);  // 下腔静脉 IVC
            addTubularVessel(0.11f, 0.02f, 0.16f, 0.05f, -0.18f, 0.08f, 0.044f, 6, 8, 5);   // 肺动脉干主干

            // D. 3D 冠状动脉树 (左前降支 LAD + 对角支 D1/D2 + 左回旋支 LCX + 右冠状动脉 RCA)
            // 左前降支 (LAD) 沿前室间沟下行至心尖
            int ladSteps = 10;
            for (int i = 0; i < ladSteps; i++) {
                float t1 = i / (float) ladSteps;
                float t2 = (i + 1) / (float) ladSteps;
                coronaryTree.add(new CoronarySegment(
                        evalLadX(t1), evalLadY(t1), evalLadZ(t1),
                        evalLadX(t2), evalLadY(t2), evalLadZ(t2),
                        t1, 1.55f - 0.65f * t1
                ));
                // 对角支分叉 (Diagonal branches)
                if (i == 3 || i == 6) {
                    float bx = evalLadX(t1);
                    float by = evalLadY(t1);
                    float bz = evalLadZ(t1);
                    coronaryTree.add(new CoronarySegment(bx, by, bz, bx + 0.09f, by + 0.07f, bz - 0.03f, t1 + 0.1f, 1.05f));
                }
            }
            // 右冠状动脉 (RCA) 沿右侧冠状沟走行
            coronaryTree.add(new CoronarySegment(0.05f, 0.02f, 0.16f, -0.04f, 0.10f, 0.18f, 0.1f, 1.35f));
            coronaryTree.add(new CoronarySegment(-0.04f, 0.10f, 0.18f, -0.03f, 0.22f, 0.15f, 0.35f, 1.15f));
            coronaryTree.add(new CoronarySegment(-0.03f, 0.22f, 0.15f, 0.06f, 0.30f, 0.14f, 0.60f, 0.95f));
            // 左回旋支 (LCX) 沿左侧房室沟走行
            coronaryTree.add(new CoronarySegment(0.12f, 0.01f, 0.18f, 0.24f, 0.08f, 0.14f, 0.15f, 1.30f));
            coronaryTree.add(new CoronarySegment(0.24f, 0.08f, 0.14f, 0.27f, 0.20f, 0.09f, 0.45f, 1.05f));
        }

        private float evalLadX(float t) { return 0.11f + t * 0.17f + Mth.sin(t * 9.0f) * 0.008f; }
        private float evalLadY(float t) { return 0.00f + t * 0.37f; }
        private float evalLadZ(float t) { return 0.19f + Mth.sin(t * (float) Math.PI) * 0.06f + t * 0.03f; }

        private void addTubularVessel(float x1, float y1, float z1, float x2, float y2, float z2,
                                      float radius, int rings, int sectors, int region) {
            for (int r = 0; r < rings; r++) {
                float t = r / (float) Math.max(1, rings - 1);
                float cx = Mth.lerp(t, x1, x2);
                float cy = Mth.lerp(t, y1, y2);
                float cz = Mth.lerp(t, z1, z2);
                for (int s = 0; s < sectors; s++) {
                    float phi = (s / (float) sectors) * Mth.TWO_PI;
                    float vx = cx + Mth.cos(phi) * radius;
                    float vy = cy;
                    float vz = cz + Mth.sin(phi) * radius;
                    int curIdx = heartVertices.size();
                    int prevInRing = (s > 0) ? (curIdx - 1) : (curIdx + sectors - 1);
                    int prevRing = (r > 0 && (s % 2 == 0)) ? (curIdx - sectors) : -1;
                    heartVertices.add(new OrganVertex(vx, vy, vz, Mth.cos(phi), 0.0f, Mth.sin(phi), region, r, t, prevInRing, prevRing));
                }
            }
        }

        public void render(GuiGraphics g, Font font, int panelX, int panelY, int panelW, int panelH,
                           MedicalData data, float dt, int mouseX, int mouseY, long now,
                           int themeAccent, EmbeddedDetailPanelRenderer.OscilloscopeDrawer ecgDrawer) {
            this.lastPanelX = panelX;
            this.lastPanelY = panelY;
            this.lastPanelW = panelW;

            int hr = data.getHeartRate();
            if (!data.isCardiacArrest() && !data.isInVFib() && hr > 0) {
                heartPhase = (heartPhase + dt * (Mth.clamp(hr, 20, 260) / 60.0f)) % 1.0f;
            } else if (data.isInVFib()) {
                heartPhase = (heartPhase + dt * 6.5f) % 1.0f;
            }

            int respRate = data.getRespirationRate();
            if (!data.isCardiacArrest() && !data.isInVFib() && respRate > 0) {
                respPhase = (respPhase + dt * (Mth.clamp(respRate, 1, 60) / 60.0f)) % 1.0f;
            }

            if (!dragging && (now - lastInteractionMs) > 2500L) {
                yaw += dt * 0.26f;
            }

            scrollTarget = Mth.clamp(scrollTarget, 0.0f, maxScroll);
            scrollOffset = Mth.lerp(Math.min(1.0f, dt * 14.0f), scrollOffset, scrollTarget);

            drawModeTabsAndEcgToggle(g, font, panelX, panelY, panelW, mouseX, mouseY, themeAccent);

            int bottomPad = 10;
            int desiredEcgH = ecgCollapsed ? 18 : Math.min(86, Math.max(54, (int) (panelH * 0.31f)));
            if (panelH < 160 || currentMode == ThoraxViewMode.HOLO_FULL) {
                desiredEcgH = (currentMode == ThoraxViewMode.HOLO_FULL) ? 0 : 18;
            }

            int contentX = panelX + 12;
            int contentTop = panelY + 25;
            int contentW = panelW - 24;
            int ecgY = panelY + panelH - desiredEcgH - bottomPad;
            int contentH = (desiredEcgH > 0) ? Math.max(50, ecgY - contentTop - 6) : Math.max(50, panelH - 35);

            if (currentMode == ThoraxViewMode.HOLO_FULL) {
                this.holoX = contentX;
                this.holoY = contentTop;
                this.holoW = contentW;
                this.holoH = contentH;
                this.scrollW = 0;

                HudRenderHelper.drawSciFiCard(g, holoX, holoY, holoW, holoH, 4, 0x99030912, withAlpha(themeAccent, 0.5f));
                render3DCardiopulmonaryViewport(g, font, holoX, holoY, holoW, holoH, data, now, true);
            } else if (currentMode == ThoraxViewMode.CLINICAL_GRID || contentW < 210) {
                this.holoW = 0;
                this.scrollX = contentX;
                this.scrollY = contentTop;
                this.scrollW = contentW;
                this.scrollH = contentH;

                renderScrollableThoraxTelemetry(g, font, scrollX, scrollY, scrollW, scrollH, data, themeAccent, true);
            } else {
                int leftHoloW = (int) (contentW * 0.48f);
                int rightDiagX = contentX + leftHoloW + 8;
                int rightDiagW = contentW - leftHoloW - 8;

                this.holoX = contentX;
                this.holoY = contentTop;
                this.holoW = leftHoloW;
                this.holoH = contentH;

                this.scrollX = rightDiagX;
                this.scrollY = contentTop;
                this.scrollW = rightDiagW;
                this.scrollH = contentH;

                HudRenderHelper.drawSciFiCard(g, holoX, holoY, holoW, holoH, 4, 0x99030912, withAlpha(themeAccent, 0.5f));
                render3DCardiopulmonaryViewport(g, font, holoX, holoY, holoW, holoH, data, now, false);

                HudRenderHelper.drawSciFiCard(g, scrollX, scrollY, scrollW, scrollH, 4, 0x99040A12, withAlpha(themeAccent, 0.35f));
                renderScrollableThoraxTelemetry(g, font, scrollX + 6, scrollY + 4, scrollW - 12, scrollH - 8, data, themeAccent, false);
            }

            if (desiredEcgH >= 48 && ecgDrawer != null) {
                ecgDrawer.draw(g, contentX, ecgY, contentW, desiredEcgH, data);
            } else if (desiredEcgH > 0) {
                HudRenderHelper.drawSciFiCard(g, contentX, ecgY, contentW, desiredEcgH, 2, 0xAA040C14, withAlpha(themeAccent, 0.45f));
                g.drawString(font, "心电监护已折叠 (点击右上角「心电 ▲」展开)", contentX + 6, ecgY + 5, themeAccent, false);
            }
        }

        private void drawModeTabsAndEcgToggle(GuiGraphics g, Font font, int panelX, int panelY, int panelW,
                                              int mouseX, int mouseY, int themeAccent) {
            int tabH = 13;
            int tabY = panelY + 5;
            int curRight = panelX + panelW - 10;

            String ecgLabel = ecgCollapsed ? "心电 ▲" : "心电 ▼";
            this.btnEcgW = font.width(ecgLabel) + 10;
            this.btnEcgH = tabH;
            this.btnEcgX = curRight - btnEcgW;
            this.btnEcgY = tabY;
            boolean hovEcg = mouseX >= btnEcgX && mouseX <= btnEcgX + btnEcgW && mouseY >= btnEcgY && mouseY <= btnEcgY + btnEcgH;
            HudRenderHelper.drawSciFiCard(g, btnEcgX, btnEcgY, btnEcgW, btnEcgH, 2,
                    !ecgCollapsed ? withAlpha(themeAccent, 0.35f) : 0x66050E17,
                    hovEcg ? themeAccent : withAlpha(themeAccent, 0.5f));
            g.drawString(font, ecgLabel, btnEcgX + 5, btnEcgY + 3, !ecgCollapsed ? themeAccent : 0xFF90A4AE, false);

            curRight = btnEcgX - 4;
            ThoraxViewMode[] modes = ThoraxViewMode.values();
            for (int i = modes.length - 1; i >= 0; i--) {
                ThoraxViewMode m = modes[i];
                int tw = font.width(m.label) + 10;
                int tx = curRight - tw;
                if (tx < panelX + 120) break;

                boolean active = (this.currentMode == m);
                boolean hovered = mouseX >= tx && mouseX <= tx + tw && mouseY >= tabY && mouseY <= tabY + tabH;
                int bg = active ? 0xCC004D66 : (hovered ? 0x990A2233 : 0x66050E17);
                int border = active ? C_NEON_CYAN : (hovered ? 0xAA00E5FF : 0x4400A0FF);
                int txtCol = active ? 0xFFFFFFFF : (hovered ? C_NEON_CYAN : 0xFF90A4AE);

                HudRenderHelper.drawSciFiCard(g, tx, tabY, tw, tabH, 2, bg, border);
                g.drawString(font, m.label, tx + 5, tabY + 3, txtCol, false);
                curRight = tx - 4;
            }
        }

        private void render3DCardiopulmonaryViewport(GuiGraphics g, Font font, int vx, int vy, int vw, int vh,
                                                     MedicalData data, long now, boolean fullMode) {
            g.enableScissor(vx + 1, vy + 1, vx + vw - 1, vy + vh - 1);

            int hr = data.getHeartRate();
            int respRate = data.getRespirationRate();
            float lungContusion = data.getLungContusion();
            float chestBlood = data.getChestBloodAccumulation();
            int hemoLevel = data.getHemothoraxLevel();

            float cx = vx + vw * 0.5f;
            float cy = vy + vh * (fullMode ? 0.46f : 0.44f);
            float scale = Math.min(vw, vh) * (fullMode ? 0.37f : 0.39f);

            float cosYaw = Mth.cos(yaw);
            float sinYaw = Mth.sin(yaw);
            float cosPitch = Mth.cos(pitch);
            float sinPitch = Mth.sin(pitch);

            // 纵向全息 CT 扫描平面周期
            float scanCycle = (now % 3400L) / 3400.0f;
            float scanPlaneY = -0.88f + scanCycle * 1.65f;

            // 生理呼吸扩张波形 (0.0 呼气末 ~ 1.0 吸气峰)
            float breathWave = (respRate <= 0 || data.isCardiacArrest() || data.isInVFib())
                    ? 0.0f
                    : (0.5f - 0.5f * Mth.cos(respPhase * Mth.TWO_PI));

            // Wiggers 心动周期波形：P 波心房收缩 (0.02~0.13) + QRS 心室强力收缩射血 (0.15~0.36)
            float atrialKick = 0.0f;
            float ventricularKick = 0.0f;
            if (!data.isCardiacArrest() && !data.isInVFib() && hr > 0) {
                if (heartPhase >= 0.02f && heartPhase <= 0.13f) {
                    atrialKick = Mth.sin(((heartPhase - 0.02f) / 0.11f) * (float) Math.PI);
                } else if (heartPhase >= 0.15f && heartPhase <= 0.36f) {
                    ventricularKick = Mth.sin(((heartPhase - 0.15f) / 0.21f) * (float) Math.PI);
                }
            }

            // 张力性血胸 (LV.3~4) 引发纵隔与心脏受压移位
            float mediastinalShiftX = (hemoLevel >= 3) ? (0.045f * (hemoLevel - 2)) : 0.0f;

            // 1. 绘制全息校准陀螺环底座
            drawThoraxGimbalRings(g, cx, cy, scale, hemoLevel, lungContusion);

            Matrix4f pose = g.pose().last().pose();
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            RenderSystem.disableDepthTest();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);

            // 2. 绘制 5 级分形气管-支气管树 + 气管软骨环 + 呼吸氧合气流粒子
            drawFractalAirwayAndParticles(pose, cx, cy, scale, cosYaw, sinYaw, cosPitch, sinPitch,
                    respRate, breathWave, mediastinalShiftX, now);

            // 3. 绘制高精四腔室 3D 心脏点云 + 冠状动脉树 (LAD/LCX/RCA) + 主动脉弓三分支
            drawHighDensityHeartAndCoronaries(pose, cx, cy, scale, cosYaw, sinYaw, cosPitch, sinPitch,
                    data, atrialKick, ventricularKick, mediastinalShiftX, scanPlaneY, now);

            // 4. 绘制高密度 1,904 顶点双肺全息曲面点云 + 叶间裂 + 肺泡挫伤云团 + 肋膈角胸腔积血池
            drawHighDensityLungsAndPathology(pose, cx, cy, scale, cosYaw, sinYaw, cosPitch, sinPitch,
                    breathWave, lungContusion, chestBlood, hemoLevel, scanPlaneY, now);

            RenderSystem.defaultBlendFunc();

            // 5. 绘制水平激光扫描光带
            int scanScreenY = (int) (cy + scanPlaneY * scale * 0.85f);
            if (scanScreenY > vy + 10 && scanScreenY < vy + vh - 28) {
                int scanW = (int) (scale * 2.1f);
                g.fill((int) cx - scanW / 2, scanScreenY, (int) cx + scanW / 2, scanScreenY + 1, 0x8800F0FF);
                g.fillGradient((int) cx - scanW / 2, scanScreenY - 8, (int) cx + scanW / 2, scanScreenY, 0x0000F0FF, 0x3300F0FF);
            }

            // 6. 绘制全息引线锚点与底部状态栏
            draw3DThoraxLabels(g, font, vx, vy, vw, vh, cx, cy, scale, cosYaw, sinYaw, cosPitch, sinPitch,
                    data, mediastinalShiftX, fullMode);
            drawThoraxFooterBar(g, font, vx, vy + vh - 24, vw, 22, data);

            g.disableScissor();
        }

        private float[] project3D(float x, float y, float z, float cx, float cy, float scale,
                                  float cosY, float sinY, float cosP, float sinP) {
            float rx = x * cosY - z * sinY;
            float rz = x * sinY + z * cosY;
            float ry = y * cosP - rz * sinP;
            float fz = y * sinP + rz * cosP;
            float fov = 2.95f / (2.95f - fz * 0.45f);
            return new float[]{cx + rx * scale * fov, cy + ry * scale * fov, fz};
        }

        private void drawThoraxGimbalRings(GuiGraphics g, float cx, float cy, float scale, int hemoLevel, float contusion) {
            Matrix4f pose = g.pose().last().pose();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);

            Tesselator tess = Tesselator.getInstance();
            BufferBuilder buf = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

            int ringR = (hemoLevel >= 2 || contusion >= 50.0f) ? 255 : 0;
            int ringG = (hemoLevel >= 2 || contusion >= 50.0f) ? 90 : 195;
            int ringB = (hemoLevel >= 2 || contusion >= 50.0f) ? 60 : 245;

            int segs = 40;
            float rOuter = scale * 0.98f;
            for (int i = 0; i < segs; i += 2) {
                float a1 = (i / (float) segs) * Mth.TWO_PI;
                float a2 = ((i + 1) / (float) segs) * Mth.TWO_PI;
                PainVisualOverlay.addQuadSegment(buf, pose,
                        cx + Mth.cos(a1) * rOuter, cy + Mth.sin(a1) * rOuter * 0.24f + scale * 0.62f,
                        cx + Mth.cos(a2) * rOuter, cy + Mth.sin(a2) * rOuter * 0.24f + scale * 0.62f,
                        0.75f, ringR, ringG, ringB, 65, 65);
            }

            MeshData mesh = buf.build();
            if (mesh != null) BufferUploader.drawWithShader(mesh);
        }

        private void drawFractalAirwayAndParticles(Matrix4f pose, float cx, float cy, float scale,
                                                   float cosY, float sinY, float cosP, float sinP,
                                                   int respRate, float breathWave, float shiftX, long now) {
            Tesselator tess = Tesselator.getInstance();
            BufferBuilder buf = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

            float expand = 1.0f + 0.11f * breathWave;

            // 1. 绘制主气管 C 型软骨环 (16 环精致全息刻度环)
            for (int r = 0; r < 14; r++) {
                float ry = Mth.lerp(r / 13.0f, -0.84f, -0.30f);
                float rad = 0.044f;
                int segs = 8;
                for (int s = 0; s < segs; s++) {
                    float a1 = -2.2f + (s / (float) segs) * 4.4f;
                    float a2 = -2.2f + ((s + 1) / (float) segs) * 4.4f;
                    float[] p1 = project3D(shiftX + Mth.cos(a1) * rad, ry, -0.05f + Mth.sin(a1) * rad,
                            cx, cy, scale, cosY, sinY, cosP, sinP);
                    float[] p2 = project3D(shiftX + Mth.cos(a2) * rad, ry, -0.05f + Mth.sin(a2) * rad,
                            cx, cy, scale, cosY, sinY, cosP, sinP);
                    PainVisualOverlay.addQuadSegment(buf, pose, p1[0], p1[1], p2[0], p2[1],
                            0.65f, 80, 245, 255, 155, 155);
                }
            }

            // 2. 绘制 5 代递归支气管树与终末肺泡囊微光节点
            for (AirwayBranch br : airwayTree) {
                int gen = br.generation();
                float sx1 = (gen == 0 ? br.x1() : br.x1() * expand) + shiftX * (gen <= 1 ? 1.0f : 0.4f);
                float sy1 = (gen == 0 ? br.y1() : br.y1() + 0.05f * breathWave * Math.max(0.0f, br.y1() + 0.3f));
                float sz1 = (gen == 0 ? br.z1() : br.z1() * expand);

                float sx2 = (gen == 0 ? br.x2() : br.x2() * expand) + shiftX * (gen <= 1 ? 1.0f : 0.4f);
                float sy2 = (gen == 0 ? br.y2() : br.y2() + 0.05f * breathWave * Math.max(0.0f, br.y2() + 0.3f));
                float sz2 = (gen == 0 ? br.z2() : br.z2() * expand);

                float[] p1 = project3D(sx1, sy1, sz1, cx, cy, scale, cosY, sinY, cosP, sinP);
                float[] p2 = project3D(sx2, sy2, sz2, cx, cy, scale, cosY, sinY, cosP, sinP);

                float depth = Mth.clamp(((p1[2] + p2[2]) * 0.5f + 1.2f) / 2.2f, 0.25f, 1.0f);
                float width = switch (gen) {
                    case 0 -> 1.35f;
                    case 1 -> 1.05f;
                    case 2 -> 0.78f;
                    case 3 -> 0.55f;
                    case 4 -> 0.42f;
                    default -> 0.32f;
                };
                int alpha = (int) Mth.clamp((165 - gen * 18 + 45 * breathWave) * depth, 25, 225);
                PainVisualOverlay.addQuadSegment(buf, pose, p1[0], p1[1], p2[0], p2[1],
                        width, 20, 235, 255, alpha, alpha);

                // 终末第 5 代细支气管末端绘制肺泡囊发光微球
                if (gen == 5) {
                    float rAlv = (0.9f + 0.5f * breathWave);
                    buf.addVertex(pose, p2[0] - rAlv, p2[1] - rAlv, 0.0f).setColor(120, 255, 240, alpha);
                    buf.addVertex(pose, p2[0] + rAlv, p2[1] - rAlv, 0.0f).setColor(120, 255, 240, alpha);
                    buf.addVertex(pose, p2[0] + rAlv, p2[1] + rAlv, 0.0f).setColor(120, 255, 240, alpha);
                    buf.addVertex(pose, p2[0] - rAlv, p2[1] + rAlv, 0.0f).setColor(120, 255, 240, alpha);
                }

                // 呼吸气流氧合光子（吸气向肺泡深入，呼气向主气管汇流）
                if (respRate > 0 && gen <= 3) {
                    float tFlow = (respPhase * 2.0f + br.phaseSeed()) % 1.0f;
                    if (respPhase >= 0.5f) tFlow = 1.0f - tFlow;
                    float fx = Mth.lerp(tFlow, p1[0], p2[0]);
                    float fy = Mth.lerp(tFlow, p1[1], p2[1]);
                    float pr = 1.15f;
                    buf.addVertex(pose, fx - pr, fy - pr, 0.0f).setColor(200, 255, 255, 210);
                    buf.addVertex(pose, fx + pr, fy - pr, 0.0f).setColor(200, 255, 255, 210);
                    buf.addVertex(pose, fx + pr, fy + pr, 0.0f).setColor(200, 255, 255, 210);
                    buf.addVertex(pose, fx - pr, fy + pr, 0.0f).setColor(200, 255, 255, 210);
                }
            }

            MeshData mesh = buf.build();
            if (mesh != null) BufferUploader.drawWithShader(mesh);
        }

        private void drawHighDensityHeartAndCoronaries(Matrix4f pose, float cx, float cy, float scale,
                                                       float cosY, float sinY, float cosP, float sinP,
                                                       MedicalData data, float atrialKick, float ventricularKick,
                                                       float shiftX, float scanPlaneY, long now) {
            int n = heartVertices.size();
            if (n == 0) return;

            boolean vfib = data.isInVFib();
            boolean arrest = data.isCardiacArrest();

            float[] px = new float[n];
            float[] py = new float[n];
            float[] pz = new float[n];
            int[] vColors = new int[n];

            float heartCenterX = 0.14f + shiftX;
            float heartCenterY = 0.14f;
            float heartCenterZ = 0.12f;

            for (int i = 0; i < n; i++) {
                OrganVertex v = heartVertices.get(i);
                float vx = v.x + shiftX;
                float vy = v.y;
                float vz = v.z;

                // 根据解剖分区施加真实的 Wiggers 心动周期形变
                if (v.region == 2) {
                    // 心室部：QRS 射血期向心收缩 + 心尖顺时针微扭转 (Ventricular Torsion)
                    float contract = 1.0f - 0.17f * ventricularKick;
                    if (vfib) {
                        contract += 0.10f * Mth.sin(now * 0.042f + i * 0.37f);
                    }
                    vx = heartCenterX + (vx - heartCenterX) * contract;
                    vy = heartCenterY + (vy - heartCenterY) * (1.0f - 0.09f * ventricularKick);
                    vz = heartCenterZ + (vz - heartCenterZ) * contract;
                } else if (v.region == 3) {
                    // 心房部：P 波期收缩，心室收缩期舒张充盈
                    float contract = 1.0f - 0.14f * atrialKick + 0.05f * ventricularKick;
                    vx = heartCenterX + (vx - heartCenterX) * contract;
                    vz = heartCenterZ + (vz - heartCenterZ) * contract;
                } else if (v.region == 4) {
                    // 主动脉弓：心室射血后顺应性弹性扩张 (Windkessel Effect)
                    float pulseWave = (!arrest && !vfib && Math.abs(v.surfaceTex - ((heartPhase * 1.8f) % 1.0f)) < 0.22f) ? 1.0f : 0.0f;
                    float expand = 1.0f + 0.12f * ventricularKick + 0.08f * pulseWave;
                    vx += v.nx * 0.008f * expand;
                    vz += v.nz * 0.008f * expand;
                }

                float[] proj = project3D(vx, vy, vz, cx, cy, scale, cosY, sinY, cosP, sinP);
                px[i] = proj[0];
                py[i] = proj[1];
                pz[i] = proj[2];

                float depth = Mth.clamp((proj[2] + 1.25f) / 2.3f, 0.22f, 1.0f);
                float rim = 1.0f - Math.min(1.0f, Math.abs(proj[2]) * 0.65f);
                float bright = 0.55f + 0.45f * v.surfaceTex + 0.30f * rim;

                int r, g, b;
                if (arrest) {
                    r = 135; g = 45; b = 60;
                } else if (vfib) {
                    float flicker = 0.5f + 0.5f * Mth.sin(now * 0.05f + i * 0.5f);
                    r = 255;
                    g = (int) (20 + 60 * flicker);
                    b = (int) (90 + 140 * (1.0f - flicker));
                } else if (v.region == 5) {
                    // 腔静脉与肺动脉：静脉含氧低呈深青蓝
                    r = 35; g = 165; b = 255;
                } else if (v.region == 4) {
                    // 主动脉弓：鲜亮动脉红 + 射血脉搏波高亮
                    float wave = Math.max(0.0f, 1.0f - Math.abs(v.surfaceTex - ((heartPhase * 1.7f) % 1.0f)) * 4.5f);
                    r = 255;
                    g = (int) (55 + 145 * wave);
                    b = (int) (80 + 130 * wave);
                } else {
                    // 左/右心室与心房心肌：随收缩迸发鲜红能量光泽
                    r = 255;
                    g = (int) (28 + 105 * ventricularKick + 35 * v.surfaceTex);
                    b = (int) (65 + 75 * ventricularKick);
                }

                float distToScan = Math.abs(vy - scanPlaneY);
                float scanBoost = (distToScan < 0.08f) ? (1.0f - distToScan / 0.08f) : 0.0f;
                if (scanBoost > 0.0f) {
                    g = (int) Mth.lerp(scanBoost, g, 230);
                    b = (int) Mth.lerp(scanBoost, b, 255);
                }

                int alpha = (int) Mth.clamp((depth * bright * 185.0f) + ventricularKick * 45.0f + scanBoost * 60.0f, 28.0f, 250.0f);
                vColors[i] = (alpha << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
            }

            Tesselator tess = Tesselator.getInstance();

            // A. 绘制心脏内部半透明体积辉光核 (Volumetric Myocardial Core)
            float[] coreProj = project3D(heartCenterX + 0.04f, heartCenterY + 0.05f, heartCenterZ,
                    cx, cy, scale, cosY, sinY, cosP, sinP);
            BufferBuilder glowBuf = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            float coreRad = scale * (0.22f - 0.03f * ventricularKick);
            int coreAlpha = arrest ? 35 : (int) (65 + 45 * ventricularKick);
            drawSoftGlowDisc(glowBuf, pose, coreProj[0], coreProj[1], coreRad, 255, 25, 65, coreAlpha);

            // B. 绘制 3D 冠状动脉树 (LAD / LCX / RCA) 及其射血脉冲亮流
            float contract = 1.0f - 0.16f * ventricularKick;
            for (CoronarySegment seg : coronaryTree) {
                float x1 = heartCenterX + (seg.x1() + shiftX - heartCenterX) * contract;
                float y1 = heartCenterY + (seg.y1() - heartCenterY) * (1.0f - 0.08f * ventricularKick);
                float z1 = heartCenterZ + (seg.z1() - heartCenterZ) * contract;

                float x2 = heartCenterX + (seg.x2() + shiftX - heartCenterX) * contract;
                float y2 = heartCenterY + (seg.y2() - heartCenterY) * (1.0f - 0.08f * ventricularKick);
                float z2 = heartCenterZ + (seg.z2() - heartCenterZ) * contract;

                float[] p1 = project3D(x1, y1, z1, cx, cy, scale, cosY, sinY, cosP, sinP);
                float[] p2 = project3D(x2, y2, z2, cx, cy, scale, cosY, sinY, cosP, sinP);

                float pulse = (!arrest && !vfib)
                        ? Math.max(0.0f, 1.0f - Math.abs(seg.distFromRoot() - ((heartPhase * 1.9f) % 1.0f)) * 4.0f)
                        : 0.0f;
                int cr = 255;
                int cg = arrest ? 50 : (int) (95 + 145 * pulse);
                int cb = arrest ? 60 : (int) (40 + 160 * pulse);
                PainVisualOverlay.addQuadSegment(glowBuf, pose, p1[0], p1[1], p2[0], p2[1],
                        seg.width() * (1.0f + 0.35f * pulse), cr, cg, cb, 235, 235);
            }
            MeshData glowMesh = glowBuf.build();
            if (glowMesh != null) BufferUploader.drawWithShader(glowMesh);

            // C. 绘制 0.36px 精密心肌纤维网格线
            BufferBuilder lineBuf = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            for (int i = 0; i < n; i++) {
                OrganVertex v = heartVertices.get(i);
                int c = vColors[i];
                int la = (((c >>> 24) & 0xFF) * 2) / 5;
                int lr = (c >>> 16) & 0xFF;
                int lg = (c >>> 8) & 0xFF;
                int lb = c & 0xFF;

                if (v.connectA >= 0 && v.connectA < n) {
                    float dx = px[i] - px[v.connectA];
                    float dy = py[i] - py[v.connectA];
                    if (dx * dx + dy * dy < scale * scale * 0.14f) {
                        PainVisualOverlay.addQuadSegment(lineBuf, pose, px[i], py[i], px[v.connectA], py[v.connectA],
                                0.36f, lr, lg, lb, la, la);
                    }
                }
                if (v.connectB >= 0 && v.connectB < n) {
                    float dx = px[i] - px[v.connectB];
                    float dy = py[i] - py[v.connectB];
                    if (dx * dx + dy * dy < scale * scale * 0.14f) {
                        PainVisualOverlay.addQuadSegment(lineBuf, pose, px[i], py[i], px[v.connectB], py[v.connectB],
                                0.34f, lr, lg, lb, la, la);
                    }
                }
            }
            MeshData lineMesh = lineBuf.build();
            if (lineMesh != null) BufferUploader.drawWithShader(lineMesh);

            // D. 绘制高密度发光心肌点云节点 (Holographic Point Cloud)
            BufferBuilder ptBuf = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            for (int i = 0; i < n; i++) {
                int c = vColors[i];
                int a = (c >>> 24) & 0xFF;
                int r = (c >>> 16) & 0xFF;
                int g = (c >>> 8) & 0xFF;
                int b = c & 0xFF;

                float hs = (pz[i] > 0.0f) ? 1.05f : 0.72f;
                float x = px[i];
                float y = py[i];
                ptBuf.addVertex(pose, x - hs, y - hs, 0.0f).setColor(r, g, b, a);
                ptBuf.addVertex(pose, x + hs, y - hs, 0.0f).setColor(r, g, b, a);
                ptBuf.addVertex(pose, x + hs, y + hs, 0.0f).setColor(r, g, b, a);
                ptBuf.addVertex(pose, x - hs, y + hs, 0.0f).setColor(r, g, b, a);
            }
            MeshData ptMesh = ptBuf.build();
            if (ptMesh != null) BufferUploader.drawWithShader(ptMesh);
        }

        private void drawHighDensityLungsAndPathology(Matrix4f pose, float cx, float cy, float scale,
                                                      float cosY, float sinY, float cosP, float sinP,
                                                      float breathWave, float lungContusion,
                                                      float chestBlood, int hemoLevel,
                                                      float scanPlaneY, long now) {
            int n = lungVertices.size();
            if (n == 0) return;

            float contusionNorm = Mth.clamp(lungContusion / 100.0f, 0.0f, 1.0f);
            float bloodRatio = Mth.clamp(chestBlood / 2000.0f, Math.min(1.0f, hemoLevel * 0.16f), 1.0f);
            float fluidSurfaceY = 0.60f - bloodRatio * 0.82f;

            float[] px = new float[n];
            float[] py = new float[n];
            float[] pz = new float[n];
            int[] vColors = new int[n];

            for (int i = 0; i < n; i++) {
                OrganVertex v = lungVertices.get(i);

                // 肺顺应性：受肺挫伤与胸腔积血浸没限制
                boolean submerged = (bloodRatio > 0.01f && v.y > fluidSurfaceY);
                float compliance = (1.0f - 0.42f * contusionNorm) * (submerged ? (1.0f - 0.65f * bloodRatio) : 1.0f);

                float radialExpand = 1.0f + 0.13f * breathWave * compliance;
                float vertDrop = 0.07f * breathWave * Math.max(0.0f, (v.y + 0.74f) / 1.36f) * compliance;

                float vx = v.x * radialExpand;
                float vy = v.y + vertDrop;
                float vz = v.z * radialExpand;

                // 胸腔积血淹没下肺叶时引起压迫性肺不张 (向内上方回缩)
                if (submerged) {
                    float subDepth = Mth.clamp((vy - fluidSurfaceY) / 0.55f, 0.0f, 1.0f);
                    vx *= (1.0f - 0.20f * subDepth * bloodRatio);
                    vy -= 0.09f * subDepth * bloodRatio;
                }

                float[] proj = project3D(vx, vy, vz, cx, cy, scale, cosY, sinY, cosP, sinP);
                px[i] = proj[0];
                py[i] = proj[1];
                pz[i] = proj[2];

                // 3D 表面法线旋转后的 Z 分量 -> 计算菲涅尔边缘发光 (Fresnel Rim Lighting)
                float rnx = v.nx * cosY - v.nz * sinY;
                float rnz = v.nx * sinY + v.nz * cosY;
                float fzNorm = v.ny * sinP + rnz * cosP;
                float fresnelRim = (float) Math.pow(1.0f - Math.min(1.0f, Math.abs(fzNorm)), 1.35);

                float depth = Mth.clamp((proj[2] + 1.35f) / 2.5f, 0.18f, 1.0f);
                float texBright = 0.38f + 0.62f * fresnelRim + 0.25f * v.surfaceTex;

                // 判定该点是否位于肺挫伤病灶云团内
                float contusionField = 0.5f + 0.5f * Mth.sin(v.x * 6.8f + v.y * 5.4f - v.z * 6.2f);
                boolean inContusion = contusionNorm > 0.01f && (contusionField < contusionNorm * 1.12f);

                int r, g, b;
                if (submerged) {
                    // 胸腔积血浸没区：深紫红液面染色
                    float meniscusGlow = Math.abs(v.y - fluidSurfaceY) < 0.045f ? 1.0f : 0.0f;
                    r = 255;
                    g = (int) (20 + 95 * meniscusGlow);
                    b = (int) (55 + 80 * meniscusGlow);
                    texBright += 0.45f * meniscusGlow;
                } else if (inContusion) {
                    // 肺挫伤区：从琥珀橙过渡至肺泡出血猩红
                    float severity = Mth.clamp((contusionNorm * 1.12f - contusionField) / Math.max(0.05f, contusionNorm), 0.0f, 1.0f);
                    r = 255;
                    g = (int) Mth.lerp(severity, 165, 25);
                    b = (int) Mth.lerp(severity, 15, 55);
                    texBright += 0.35f * severity;
                } else if (v.lobeId == 1) {
                    // 右肺中叶：微带青碧色阶以凸显三叶分界
                    r = 0;
                    g = (int) (225 + 30 * breathWave);
                    b = 235;
                } else {
                    // 健康肺实质：全息医疗科技青蓝
                    r = (int) (10 + 30 * fresnelRim);
                    g = (int) (195 + 55 * breathWave);
                    b = 255;
                }

                float distToScan = Math.abs(vy - scanPlaneY);
                float scanBoost = (distToScan < 0.08f) ? (1.0f - distToScan / 0.08f) : 0.0f;
                if (scanBoost > 0.0f) {
                    r = (int) Mth.lerp(scanBoost, r, 210);
                    g = (int) Mth.lerp(scanBoost, g, 255);
                    b = 255;
                }

                int alpha = (int) Mth.clamp((depth * texBright * 150.0f) + scanBoost * 75.0f, 16.0f, 240.0f);
                vColors[i] = (alpha << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
            }

            Tesselator tess = Tesselator.getInstance();

            // A. 若存在肺挫伤或胸腔积血，先渲染 3D 体积病灶柔光团 (Volumetric Pathology Halos)
            if (contusionNorm > 0.01f || bloodRatio > 0.01f) {
                BufferBuilder haloBuf = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
                if (contusionNorm > 0.01f) {
                    float pulse = 0.75f + 0.25f * Mth.sin(now * 0.007f);
                    float[][] foci = new float[][]{
                            {-0.38f, -0.10f, 0.06f}, {0.38f, -0.12f, 0.05f},
                            {-0.34f, 0.22f, -0.04f}, {0.36f, 0.24f, -0.04f}
                    };
                    int count = Math.max(1, Math.round(contusionNorm * foci.length));
                    for (int k = 0; k < count; k++) {
                        float[] fp = project3D(foci[k][0], foci[k][1], foci[k][2], cx, cy, scale, cosY, sinY, cosP, sinP);
                        drawSoftGlowDisc(haloBuf, pose, fp[0], fp[1], scale * (0.12f + 0.10f * contusionNorm) * pulse,
                                255, 75, 20, (int) (95 * contusionNorm));
                    }
                }
                if (bloodRatio > 0.01f) {
                    // 左右肋膈隐窝积血弯月面 (Costodiaphragmatic Meniscus Rings)
                    float wave = now * 0.005f;
                    for (int side = -1; side <= 1; side += 2) {
                        float poolCX = side * 0.36f;
                        int segs = 22;
                        for (int s = 0; s < segs; s++) {
                            float a1 = (s / (float) segs) * Mth.TWO_PI;
                            float a2 = ((s + 1) / (float) segs) * Mth.TWO_PI;
                            float rx = 0.29f;
                            float rz = 0.34f;
                            float wy1 = fluidSurfaceY + Mth.sin(a1 * 3.0f + wave) * 0.018f;
                            float wy2 = fluidSurfaceY + Mth.sin(a2 * 3.0f + wave) * 0.018f;
                            float[] p1 = project3D(poolCX + Mth.cos(a1) * rx, wy1, Mth.sin(a1) * rz,
                                    cx, cy, scale, cosY, sinY, cosP, sinP);
                            float[] p2 = project3D(poolCX + Mth.cos(a2) * rx, wy2, Mth.sin(a2) * rz,
                                    cx, cy, scale, cosY, sinY, cosP, sinP);
                            PainVisualOverlay.addQuadSegment(haloBuf, pose, p1[0], p1[1], p2[0], p2[1],
                                    1.25f, 255, 30, 70, 215, 215);
                        }
                    }
                }
                MeshData haloMesh = haloBuf.build();
                if (haloMesh != null) BufferUploader.drawWithShader(haloMesh);
            }

            // B. 绘制 0.34px 微米级双肺丝网连接线
            BufferBuilder lineBuf = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            for (int i = 0; i < n; i++) {
                OrganVertex v = lungVertices.get(i);
                int c = vColors[i];
                int la = (((c >>> 24) & 0xFF) * 3) / 8;
                int lr = (c >>> 16) & 0xFF;
                int lg = (c >>> 8) & 0xFF;
                int lb = c & 0xFF;

                if (v.connectA >= 0 && v.connectA < n) {
                    float dx = px[i] - px[v.connectA];
                    float dy = py[i] - py[v.connectA];
                    if (dx * dx + dy * dy < scale * scale * 0.15f) {
                        PainVisualOverlay.addQuadSegment(lineBuf, pose, px[i], py[i], px[v.connectA], py[v.connectA],
                                0.34f, lr, lg, lb, la, la);
                    }
                }
                if (v.connectB >= 0 && v.connectB < n) {
                    float dx = px[i] - px[v.connectB];
                    float dy = py[i] - py[v.connectB];
                    if (dx * dx + dy * dy < scale * scale * 0.15f) {
                        PainVisualOverlay.addQuadSegment(lineBuf, pose, px[i], py[i], px[v.connectB], py[v.connectB],
                                0.32f, lr, lg, lb, la, la);
                    }
                }
            }
            MeshData lineMesh = lineBuf.build();
            if (lineMesh != null) BufferUploader.drawWithShader(lineMesh);

            // C. 绘制 1,904 个双肺高密度全息点云发光节点 (Holographic Point Cloud)
            BufferBuilder ptBuf = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            for (int i = 0; i < n; i++) {
                int c = vColors[i];
                int a = (c >>> 24) & 0xFF;
                int r = (c >>> 16) & 0xFF;
                int g = (c >>> 8) & 0xFF;
                int b = c & 0xFF;

                float hs = (pz[i] > 0.0f) ? 1.05f : 0.70f;
                float x = px[i];
                float y = py[i];

                ptBuf.addVertex(pose, x - hs, y - hs, 0.0f).setColor(r, g, b, a);
                ptBuf.addVertex(pose, x + hs, y - hs, 0.0f).setColor(r, g, b, a);
                ptBuf.addVertex(pose, x + hs, y + hs, 0.0f).setColor(r, g, b, a);
                ptBuf.addVertex(pose, x - hs, y + hs, 0.0f).setColor(r, g, b, a);
            }
            MeshData ptMesh = ptBuf.build();
            if (ptMesh != null) BufferUploader.drawWithShader(ptMesh);
        }

        private void drawSoftGlowDisc(BufferBuilder buf, Matrix4f pose, float x, float y, float radius,
                                      int r, int g, int b, int centerAlpha) {
            float core = radius * 0.38f;
            int haloAlpha = centerAlpha / 3;
            buf.addVertex(pose, x - core, y - core, 0.0f).setColor(255, 220, 230, centerAlpha);
            buf.addVertex(pose, x + core, y - core, 0.0f).setColor(255, 220, 230, centerAlpha);
            buf.addVertex(pose, x + core, y + core, 0.0f).setColor(255, 220, 230, centerAlpha);
            buf.addVertex(pose, x - core, y + core, 0.0f).setColor(255, 220, 230, centerAlpha);

            buf.addVertex(pose, x - radius, y - radius, 0.0f).setColor(r, g, b, haloAlpha);
            buf.addVertex(pose, x + radius, y - radius, 0.0f).setColor(r, g, b, haloAlpha);
            buf.addVertex(pose, x + radius, y + radius, 0.0f).setColor(r, g, b, haloAlpha);
            buf.addVertex(pose, x - radius, y + radius, 0.0f).setColor(r, g, b, haloAlpha);
        }

        private void draw3DThoraxLabels(GuiGraphics g, Font font, int vx, int vy, int vw, int vh,
                                        float cx, float cy, float scale,
                                        float cosY, float sinY, float cosP, float sinP,
                                        MedicalData data, float shiftX, boolean fullMode) {
            int hr = data.getHeartRate();
            int resp = data.getRespirationRate();
            float contusion = data.getLungContusion();
            float chestBlood = data.getChestBloodAccumulation();
            int hemoLv = data.getHemothoraxLevel();

            // 1. 左心尖/心率锚点
            float[] pHeart = project3D(0.24f + shiftX, 0.26f, 0.20f, cx, cy, scale, cosY, sinY, cosP, sinP);
            String hrTxt = data.isCardiacArrest() ? "心脏停搏" : (data.isInVFib() ? "心室纤颤" : ("心率 " + hr + " BPM"));
            int hrCol = (data.isCardiacArrest() || data.isInVFib() || hr >= 140 || hr <= 45) ? C_ALERT_RED : C_SAFE_GREEN;
            drawLeaderTag(g, font, (int) pHeart[0], (int) pHeart[1], vx + vw - 6, (int) pHeart[1] - 8, true, hrTxt, hrCol);

            // 2. 右肺/呼吸锚点
            float[] pResp = project3D(-0.36f, -0.26f, 0.06f, cx, cy, scale, cosY, sinY, cosP, sinP);
            String respTxt = "呼吸 " + resp + " rpm";
            int respCol = (resp <= 6 || resp >= 30) ? C_WARN_YELLOW : C_NEON_CYAN;
            drawLeaderTag(g, font, (int) pResp[0], (int) pResp[1], vx + 6, (int) pResp[1] - 8, false, respTxt, respCol);

            // 3. 肺挫伤锚点
            if (contusion > 0.5f || fullMode) {
                float[] pCont = project3D(-0.42f, 0.12f, 0.08f, cx, cy, scale, cosY, sinY, cosP, sinP);
                String cTxt = String.format("肺挫伤 %.0f%%", contusion);
                int cCol = contusion > 0.5f ? 0xFFFF9100 : 0xFF78909C;
                drawLeaderTag(g, font, (int) pCont[0], (int) pCont[1], vx + 6, (int) pCont[1] + 12, false, cTxt, cCol);
            }

            // 4. 胸腔积血锚点
            if (chestBlood > 1.0f || hemoLv > 0 || fullMode) {
                float[] pHemo = project3D(0.36f, 0.50f, 0.08f, cx, cy, scale, cosY, sinY, cosP, sinP);
                String hTxt = String.format("胸腔积血 %.0fmL", chestBlood);
                int hCol = (chestBlood > 1.0f || hemoLv > 0) ? C_ALERT_RED : 0xFF78909C;
                drawLeaderTag(g, font, (int) pHemo[0], (int) pHemo[1], vx + vw - 6, (int) pHemo[1] + 10, true, hTxt, hCol);
            }
        }

        private void drawLeaderTag(GuiGraphics g, Font font, int ax, int ay, int edgeX, int ty,
                                   boolean alignRight, String text, int color) {
            int tw = font.width(text);
            int labelX = alignRight ? (edgeX - tw - 3) : (edgeX + 3);
            int elbowX = alignRight ? (labelX - 4) : (labelX + tw + 4);
            HudRenderHelper.drawLine(g, ax, ay, elbowX, ty + 4, withAlpha(color, 0.7f), 1);
            HudRenderHelper.drawDiamond(g, ax, ay, 2, color);
            g.fill(labelX - 2, ty - 1, labelX + tw + 2, ty + 9, 0xCC040B14);
            g.drawString(font, text, labelX, ty, color, false);
        }

        private void drawThoraxFooterBar(GuiGraphics g, Font font, int x, int y, int w, int h, MedicalData data) {
            g.fill(x + 4, y, x + w - 4, y + h, 0xAA050D17);
            int halfW = (w - 20) / 2;

            int spo2 = data.getBloodOxygen();
            int oxCol = spo2 < 80 ? C_ALERT_RED : (spo2 < 92 ? C_WARN_YELLOW : C_VITAL_OXYGEN);
            g.drawString(font, "血氧 " + spo2 + "%", x + 8, y + 2, oxCol, false);
            HudRenderHelper.drawSciFiProgressBar(g, x + 8, y + 13, halfW, 5, spo2 / 100.0f, oxCol, 0xFF0A1926);

            int rx = x + 12 + halfW;
            float co = data.getCardiacOutput();
            int coCol = (co < 3.0f) ? C_ALERT_RED : (co < 4.2f ? C_WARN_YELLOW : C_SAFE_GREEN);
            g.drawString(font, String.format("心输出 %.1fL", co), rx, y + 2, coCol, false);
            HudRenderHelper.drawSciFiProgressBar(g, rx, y + 13, halfW, 5, Mth.clamp(co / 7.5f, 0.0f, 1.0f), coCol, 0xFF0A1926);
        }

        private void renderScrollableThoraxTelemetry(GuiGraphics g, Font font, int x, int y, int w, int h,
                                                     MedicalData data, int themeAccent, boolean forceTwoColumn) {
            MedicalData.LimbData chestLd = data.getLimb(MedicalData.Limb.CHEST);
            if (chestLd == null) return;

            int contentW = w - 8;
            g.enableScissor(x, y, x + w, y + h);

            int drawY = (int) (y - scrollOffset);
            int startY = drawY;

            int health = BrainScan3DRenderer.computeLimbHealthPercent(chestLd);
            int hc = getHealthColor(health / 100.0f);
            g.drawString(font, "胸腔完整度", x, drawY, themeAccent, false);
            String hpStr = health + "%";
            g.drawString(font, hpStr, x + contentW - font.width(hpStr), drawY, hc, false);
            drawY += 11;
            HudRenderHelper.drawSciFiProgressBar(g, x, drawY, contentW, 6, health / 100.0f, hc, 0xFF0A1824);
            drawY += 12;

            float chestBlood = data.getChestBloodAccumulation();
            int hemoLv = data.getHemothoraxLevel();
            float lungContusion = data.getLungContusion();
            int bleed = chestLd.getBleeding();
            int inBleed = chestLd.getInternalBleedingRate();
            int pain = chestLd.getPainLevel();
            int inf = chestLd.getInfection();

            if (forceTwoColumn || contentW >= 185) {
                int colW = (contentW - 6) / 2;
                drawThoraxCard(g, font, x, drawY, colW, tr("medical.detail.chest_blood_accumulation"),
                        String.format("%.0f mL (LV.%d)", chestBlood, hemoLv),
                        Math.min(1f, chestBlood / 2000f), (chestBlood > 1f || hemoLv > 0) ? C_ALERT_RED : 0xFF37474F);
                drawThoraxCard(g, font, x + colW + 6, drawY, colW, tr("medical.detail.lung_contusion"),
                        String.format("%.0f%%", lungContusion),
                        Mth.clamp(lungContusion / 100f, 0f, 1f), lungContusion > 0 ? C_WARN_YELLOW : 0xFF37474F);
                drawY += 24;

                drawThoraxCard(g, font, x, drawY, colW, tr("medical.detail.bleeding_rate"),
                        bleed + " mL/m", Math.min(1f, bleed / 500f), bleed > 0 ? C_ALERT_RED : 0xFF37474F);
                drawThoraxCard(g, font, x + colW + 6, drawY, colW, tr("medical.detail.internal_bleeding_rate"),
                        inBleed + " mL/m", Math.min(1f, inBleed / 300f), inBleed > 0 ? 0xFFE040FB : 0xFF37474F);
                drawY += 24;

                drawThoraxCard(g, font, x, drawY, colW, tr("medical.detail.pain_level"),
                        pain + " / 10", pain / 10f, pain > 0 ? C_VITAL_PAIN : 0xFF37474F);
                drawThoraxCard(g, font, x + colW + 6, drawY, colW, tr("medical.detail.infection"),
                        inf + " %", inf / 100f, inf > 0 ? C_WARN_YELLOW : 0xFF37474F);
                drawY += 24;
            } else {
                drawY = drawThoraxRow(g, font, x, drawY, contentW, tr("medical.detail.chest_blood_accumulation"),
                        String.format("%.0f mL (LV.%d)", chestBlood, hemoLv),
                        Math.min(1f, chestBlood / 2000f), (chestBlood > 1f || hemoLv > 0) ? C_ALERT_RED : 0xFF455A64);
                drawY = drawThoraxRow(g, font, x, drawY, contentW, tr("medical.detail.lung_contusion"),
                        String.format("%.0f%%", lungContusion),
                        Mth.clamp(lungContusion / 100f, 0f, 1f), lungContusion > 0 ? C_WARN_YELLOW : 0xFF455A64);
                drawY = drawThoraxRow(g, font, x, drawY, contentW, tr("medical.detail.bleeding_rate"),
                        bleed + " mL/min", Math.min(1f, bleed / 500f), bleed > 0 ? C_ALERT_RED : 0xFF455A64);
                drawY = drawThoraxRow(g, font, x, drawY, contentW, tr("medical.detail.internal_bleeding_rate"),
                        inBleed + " mL/min", Math.min(1f, inBleed / 300f), inBleed > 0 ? 0xFFE040FB : 0xFF455A64);
                drawY = drawThoraxRow(g, font, x, drawY, contentW, tr("medical.detail.pain_level"),
                        pain + " / 10", pain / 10f, pain > 0 ? C_VITAL_PAIN : 0xFF455A64);
                drawY = drawThoraxRow(g, font, x, drawY, contentW, tr("medical.detail.infection"),
                        inf + " %", inf / 100f, inf > 0 ? C_WARN_YELLOW : 0xFF455A64);
            }

            drawY = drawThoraxRow(g, font, x, drawY, contentW, tr("medical.detail.blood_pressure"),
                    data.getSystolicBP() + "/" + data.getDiastolicBP() + " (MAP " + (int) data.getMeanArterialPressure() + ")",
                    Mth.clamp(data.getMeanArterialPressure() / 130f, 0f, 1f), C_VITAL_BP);

            drawY = drawThoraxRow(g, font, x, drawY, contentW, "心输出 / 阻力 / SIRS",
                    String.format("%.1fL | TPR %.2f | S%d", data.getCardiacOutput(), data.getPeripheralResistance(), data.getSirsLevel()),
                    Mth.clamp(data.getCardiacOutput() / 8.0f, 0f, 1f), themeAccent);

            int totalContentH = drawY - startY;
            this.maxScroll = Math.max(0.0f, totalContentH - h + 6);
            g.disableScissor();

            if (this.maxScroll > 1.0f) {
                BrainScan3DRenderer.drawHolographicScrollbar(g, x + w - 4, y, 3, h, scrollOffset, maxScroll, totalContentH);
            }
        }

        private void drawThoraxCard(GuiGraphics g, Font font, int x, int y, int w, String label, String val, float pct, int color) {
            pct = Mth.clamp(pct, 0.0f, 1.0f);
            g.fill(x, y, x + w, y + 21, 0x88071320);
            g.fill(x, y, x + 2, y + 21, color);
            g.drawString(font, label, x + 5, y + 2, 0xFFB0BEC5, false);
            g.drawString(font, val, x + w - font.width(val) - 3, y + 2, color, false);
            HudRenderHelper.drawSciFiProgressBar(g, x + 5, y + 14, w - 8, 4, pct, color, 0xFF0A1926);
        }

        private int drawThoraxRow(GuiGraphics g, Font font, int x, int y, int w, String label, String val, float pct, int color) {
            g.drawString(font, label, x, y, 0xFFB0BEC5, false);
            g.drawString(font, val, x + w - font.width(val), y, color, false);
            HudRenderHelper.drawSciFiProgressBar(g, x, y + 10, w, 4, Mth.clamp(pct, 0.0f, 1.0f), color, 0xFF0A1926);
            return y + 18;
        }

        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (mouseX >= btnEcgX && mouseX <= btnEcgX + btnEcgW && mouseY >= btnEcgY && mouseY <= btnEcgY + btnEcgH) {
                this.ecgCollapsed = !this.ecgCollapsed;
                MedicalSoundHelper.playLimbSelectSound();
                return true;
            }

            Font font = Minecraft.getInstance().font;
            int tabH = 13;
            int tabY = lastPanelY + 5;
            int curRight = btnEcgX - 4;
            ThoraxViewMode[] modes = ThoraxViewMode.values();
            for (int i = modes.length - 1; i >= 0; i--) {
                ThoraxViewMode m = modes[i];
                int tw = font.width(m.label) + 10;
                int tx = curRight - tw;
                if (tx < lastPanelX + 120) break;
                if (mouseX >= tx && mouseX <= tx + tw && mouseY >= tabY && mouseY <= tabY + tabH) {
                    this.currentMode = m;
                    this.scrollTarget = 0.0f;
                    MedicalSoundHelper.playLimbSelectSound();
                    return true;
                }
                curRight = tx - 4;
            }

            if (holoW > 0 && mouseX >= holoX && mouseX <= holoX + holoW && mouseY >= holoY && mouseY <= holoY + holoH) {
                this.dragging = true;
                this.lastInteractionMs = System.currentTimeMillis();
                return true;
            }
            return false;
        }

        public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            if (holoW > 0 && mouseX >= holoX && mouseX <= holoX + holoW && mouseY >= holoY && mouseY <= holoY + holoH) {
                this.dragging = true;
                this.lastInteractionMs = System.currentTimeMillis();
                this.yaw += (float) dragX * 0.015f;
                this.pitch = Mth.clamp(this.pitch + (float) dragY * 0.015f, -0.85f, 0.85f);
                return true;
            }
            return false;
        }

        public boolean mouseScrolled(double mouseX, double mouseY, double deltaY) {
            if (scrollW > 0 && mouseX >= scrollX && mouseX <= scrollX + scrollW
                    && mouseY >= scrollY && mouseY <= scrollY + scrollH && maxScroll > 0.5f) {
                scrollTarget = Mth.clamp(scrollTarget - (float) deltaY * 18.0f, 0.0f, maxScroll);
                return true;
            }
            return false;
        }
    }
}