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

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;


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
            g.fill(x + w - 6, y + 2, x + w - 2, y + 6, 0xFFFF1744);
        }
        if (ld.getInternalBleedingRate() > 0) {
            int px = (ld.getBleeding() > 0) ? (x + w - 12) : (x + w - 6);
            g.fill(px, y + 2, px + 4, y + 6, 0xFFAB47BC);
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

        String title = getLimbFullName(limb) + " [" + BrainScan3DRenderer.computeLimbHealthPercent(ld) + "%]";
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
                String fracState = ld.isOpenFracture() ? tr("medical.status.open_fracture")
                        : (ld.hasFracture() ? (ld.hasSplint() ? tr("medical.status.fracture_splinted") : tr("medical.status.fracture"))
                        : tr("medical.status.healthy"));
                drawTomographyMetric(g, rightX, metricY, rightW, tr("medical.detail.fracture"),
                        fracState, ld.hasFracture() ? C_ALERT_RED : C_SAFE_GREEN);
                drawTomographyMetric(g, rightX, metricY + 28, rightW, "关节对位",
                        ld.hasDislocation() ? tr("medical.status.dislocation") : "正常",
                        ld.hasDislocation() ? C_WARN_YELLOW : C_SAFE_GREEN);
                drawTomographyMetric(g, rightX, metricY + 56, rightW, tr("medical.detail.fracture_healing"),
                        ld.hasFracture() ? ld.getFractureTimer() + " s" : "完整", C_NEON_CYAN);
                drawTomographyMetric(g, rightX, metricY + 84, rightW, "夹板固定",
                        ld.hasSplint() ? "已固定" : "无",
                        ld.hasSplint() ? C_SAFE_GREEN : 0xFF90A4AE);
            }
            default -> {
                drawTomographyMetric(g, rightX, metricY, rightW, tr("medical.detail.infection"),
                        ld.getInfection() + " %",
                        ld.getInfection() > 0 ? C_WARN_YELLOW : C_SAFE_GREEN);
                drawTomographyMetric(g, rightX, metricY + 28, rightW, tr("medical.detail.pain_level"),
                        ld.getPainLevel() + " / 10",
                        ld.getPainLevel() > 0 ? C_VITAL_PAIN : C_SAFE_GREEN);
                drawTomographyMetric(g, rightX, metricY + 56, rightW, "贯通伤",
                        ld.hasPerforation() ? "存在" : "无",
                        ld.hasPerforation() ? C_ALERT_RED : C_SAFE_GREEN);
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
        if (selectedLimb != MedicalData.Limb.HEAD && detailPanelRenderer.mouseClicked(mouseX, mouseY, button)) {
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
        if (selectedLimb != MedicalData.Limb.HEAD && detailPanelRenderer.mouseScrolled(mouseX, mouseY, deltaY)) {
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
            int inBleed = ld.getInternalBleedingRate();
            int pain = ld.getPainLevel();
            int inf = ld.getInfection();

            if (compactTwoColumn && w >= 180) {
                int colW = (w - 6) / 2;
                drawMetricCard(g, font, x, y, colW, tr("medical.detail.bleeding_rate"), bleed + " mL/m",
                        Math.min(1f, bleed / 500f), bleed > 0 ? C_ALERT_RED : 0xFF37474F);
                drawMetricCard(g, font, x + colW + 6, y, colW, tr("medical.detail.internal_bleeding_rate"), inBleed + " mL/m",
                        Math.min(1f, inBleed / 300f), inBleed > 0 ? 0xFFE040FB : 0xFF37474F);
                y += 24;

                drawMetricCard(g, font, x, y, colW, tr("medical.detail.pain_level"), pain + " / 10",
                        pain / 10f, pain > 0 ? C_VITAL_PAIN : 0xFF37474F);
                drawMetricCard(g, font, x + colW + 6, y, colW, tr("medical.detail.infection"), inf + " %",
                        inf / 100f, inf > 0 ? C_WARN_YELLOW : 0xFF37474F);
                y += 24;
            } else {
                if (bleed > 0) {
                    y = drawFullRow(g, font, x, y, w, tr("medical.detail.bleeding_rate"), bleed + " mL/min",
                            Math.min(1f, bleed / 500f), C_ALERT_RED);
                }
                if (inBleed > 0) {
                    y = drawFullRow(g, font, x, y, w, tr("medical.detail.internal_bleeding_rate"), inBleed + " mL/min",
                            Math.min(1f, inBleed / 300f), 0xFFE040FB);
                }
                y = drawFullRow(g, font, x, y, w, tr("medical.detail.pain_level"), pain + " / 10",
                        pain / 10f, pain > 0 ? C_VITAL_PAIN : 0xFF455A64);
                y = drawFullRow(g, font, x, y, w, tr("medical.detail.infection"), inf + " %",
                        inf / 100f, inf > 0 ? C_WARN_YELLOW : 0xFF455A64);
            }

            if (ld.hasFracture() && ld.hasSplint() && ld.getFractureTimer() > 0) {
                y = drawFullRow(g, font, x, y, w, tr("medical.detail.fracture_healing"), ld.getFractureTimer() + "s",
                        Mth.clamp(1.0f - ld.getFractureTimer() / 300.0f, 0.0f, 1.0f), C_SAFE_GREEN);
            }
            if (ld.hasBurn()) {
                y = drawFullRow(g, font, x, y, w, "烧伤 (" + ld.getBurnDegree() + "°)",
                        String.format("%.1f%%", ld.getBurnArea()), Mth.clamp(ld.getBurnArea() / 25.0f, 0.0f, 1.0f), 0xFFFF6D00);
            }

            y = drawLimbStatusPills(g, font, x, y + 2, w, ld);

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
            for (MedicalData.Limb limb : MedicalData.Limb.values()) {
                MedicalData.LimbData ld = data.getLimb(limb);
                if (ld != null) {
                    if (!ld.isBandaged() && !ld.isTourniqueted()) totalBleed += ld.getBleeding();
                    totalBleed += ld.getInternalBleedingRate();
                }
            }

            if (compactTwoColumn && w >= 180) {
                int colW = (w - 6) / 2;
                drawMetricCard(g, font, x, y, colW, tr("medical.detail.blood_volume"),
                        data.getBloodVolume() + " / " + data.getMaxBloodVolume() + " mL",
                        data.getBloodVolume() / (float) Math.max(1, data.getMaxBloodVolume()), C_VITAL_HEART);
                drawMetricCard(g, font, x + colW + 6, y, colW, tr("medical.detail.total_bleeding_rate"),
                        totalBleed + " mL/min",
                        Math.min(1f, totalBleed / 600f), totalBleed > 0 ? C_ALERT_RED : C_SAFE_GREEN);
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
                drawMetricCard(g, font, x + colW + 6, y, colW, "SIRS / 烧伤面积",
                        "SIRS " + data.getSirsLevel() + " | " + String.format("%.1f%%", data.getBurnTBSA()),
                        Math.max(data.getSirsLevel() / 4f, data.getBurnTBSA() / 100f),
                        (data.getSirsLevel() > 0 || data.getBurnTBSA() > 0) ? C_WARN_YELLOW : 0xFF37474F);
                y += 24;
            } else {
                y = drawFullRow(g, font, x, y, w, tr("medical.detail.blood_volume"),
                        data.getBloodVolume() + " mL", data.getBloodVolume() / 5000f, C_VITAL_HEART);
                y = drawFullRow(g, font, x, y, w, tr("medical.detail.total_bleeding_rate"),
                        totalBleed + " mL/min", Math.min(1f, totalBleed / 600f), totalBleed > 0 ? C_ALERT_RED : 0xFF455A64);
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

        private int drawLimbStatusPills(GuiGraphics g, Font font, int x, int y, int w, MedicalData.LimbData ld) {
            int px = x;
            int py = y;
            int rightEdge = x + w;
            boolean abnormal = false;

            if (ld.isAmputated()) {
                drawPill(g, font, px, py, tr("medical.status.amputated"), 0xFFFF1744);
                return py + 14;
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
                String fTxt = ld.isOpenFracture() ? tr("medical.status.open_fracture")
                        : (ld.hasSplint() ? tr("medical.status.fracture_splinted") : tr("medical.status.fracture"));
                int nx = drawWrappedPill(g, font, x, px, py, rightEdge, fTxt, 0xFFFF1744);
                if (nx < 0) { py += 14; px = drawWrappedPill(g, font, x, x, py, rightEdge, fTxt, 0xFFFF1744); } else px = nx;
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
}