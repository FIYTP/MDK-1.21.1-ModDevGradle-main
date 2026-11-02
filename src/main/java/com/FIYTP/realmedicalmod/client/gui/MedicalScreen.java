package com.FIYTP.realmedicalmod.client.gui;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.capability.MedicalDataCapability;
import com.FIYTP.realmedicalmod.client.sound.MedicalSoundHelper;
import com.FIYTP.realmedicalmod.config.MedicalConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;

/**
 * 医疗面板 UI —— 3A 科技风全息 HUD 终端 (终极完整满血版)
 * 注：六边形处置矩阵 (Hex Intervention Matrix) 已移除。
 */
public class MedicalScreen extends Screen {

    private static final Map<String, String> I18N_CACHE = new HashMap<>();

    // ============================================================
    // 一、界面状态与动画系统
    // ============================================================
    public enum ScreenState {
        OPENING, OPEN, CLOSING, CLOSED
    }

    private ScreenState state = ScreenState.OPENING;
    private long openStartTime;
    private long closeStartTime;
    private static final long OPEN_DURATION_MS = 480;
    private static final long CLOSE_DURATION_MS = 300;

    private final BrainScan3DRenderer brainScanRenderer = new BrainScan3DRenderer();
    private final ClickRippleManager rippleManager = new ClickRippleManager();

    private MedicalData.Limb selectedLimb = null;
    private MedicalData.Limb hoveredLimb = null;
    private MedicalData.Limb lastHoveredLimb = null;

    // 3A 核心动效与微观断层探查系统
    private MedicalData.Limb microTomographyLimb = null;
    private float microTomographyAnim = 0.0f;
    private long lastLimbClickTime = 0L;
    private MedicalData.Limb lastClickedLimb = null;
    // ===== 按需全扫协议 (On-Demand Full-Body Sweep) =====
    private float sweepScanAnim = 0.0f;
    private boolean sweepScanRevealed = false;
    private long sweepScanStampTime = 0L;
    // ===== DICOM 滚轮分层刮擦 (Dicom Layer Scrub) =====
    private int dcmLayer = 3;
    private long dcmWipeStart = 0L;
    private static final long DICOM_WIPE_MS = 320L;
    private static final String[] DICOM_LAYER_NAMES = {"SKIN", "VASCULAR", "SOFT TISSUE", "BONE"};

    // 2.5D 视差平滑追踪
    private float smoothTiltX = 0.0f;
    private float smoothTiltY = 0.0f;

    // ============================================================
    // 二、3A 弹簧物理数值插值器 (Damped Spring System)
    // ============================================================
    private static class SpringVal {
        float val;
        float vel;

        SpringVal(float init) {
            this.val = init;
            this.vel = 0.0f;
        }

        void update(float target, float dt, float stiffness, float damping) {
            float force = -stiffness * (val - target) - damping * vel;
            vel += force * dt;
            val += vel * dt;
        }
    }

    private final SpringVal sprHeartRate = new SpringVal(75.0f);
    private final SpringVal sprSystolic = new SpringVal(120.0f);
    private final SpringVal sprDiastolic = new SpringVal(80.0f);
    private final SpringVal sprOxygen = new SpringVal(98.0f);
    private final SpringVal sprRespiration = new SpringVal(16.0f);
    private final SpringVal sprBloodLoss = new SpringVal(0.0f);
    private final SpringVal sprConsciousness = new SpringVal(100.0f);
    private final SpringVal sprBrainHealth = new SpringVal(100.0f);
    private final SpringVal sprBloodVolume = new SpringVal(5000.0f);
    private final SpringVal sprCardiacOutput = new SpringVal(5.0f);

    private final float[] limbHoverProgress = new float[MedicalData.Limb.values().length];
    private float selectedPulsePhase = 0.0f;
    private float scanBeamY = 0.0f;
    private float globalPulse = 0.0f;
    private float cardiacPulse = 0.0f;

    // 心电图与 PPG 容积波缓冲区
    private static final int BUFFER_SIZE = 300;
    private final float[] historyY = new float[BUFFER_SIZE];
    private final float[] historyPPG = new float[BUFFER_SIZE];
    private final boolean[] historyValid = new boolean[BUFFER_SIZE];
    private int sweepIndex = 0;
    private float sweepAccumulator = 0.0f;
    private float cardiacCycleTime = 0.0f;
    private float vfibPhase = 0.0f;
    private float rPeakFlash = 0.0f;

    // 环境粒子系统
    private static final int MAX_PARTICLES = 60;
    private final float[] particleX = new float[MAX_PARTICLES];
    private final float[] particleY = new float[MAX_PARTICLES];
    private final float[] particleVX = new float[MAX_PARTICLES];
    private final float[] particleVY = new float[MAX_PARTICLES];
    private final float[] particleLife = new float[MAX_PARTICLES];
    private final float[] particleMaxLife = new float[MAX_PARTICLES];
    private final int[] particleColor = new int[MAX_PARTICLES];
    private boolean particlesInitialized = false;

    // 连接线动画
    private float connectionLineProgress = 0.0f;
    private MedicalData.Limb connectionLineLimb = null;

    // ============================================================
    // 三、人体模型尺寸常量
    // ============================================================
    private static final int HEAD_SIZE = 36;
    private static final int BODY_W = 46;
    private static final int BODY_H = 68;
    private static final int ARM_W = 18;
    private static final int ARM_H = 64;
    private static final int LEG_W = 20;
    private static final int LEG_H = 78;

    private static final int TOTAL_W = ARM_W * 2 + BODY_W + 18;
    private static final int TOTAL_H = HEAD_SIZE + BODY_H + LEG_H + 12;

    private static final int HEAD_OFFSET_X = ARM_W + 9 + (BODY_W - HEAD_SIZE) / 2;
    private static final int HEAD_OFFSET_Y = 0;
    private static final int BODY_OFFSET_X = ARM_W + 9;
    private static final int BODY_OFFSET_Y = HEAD_SIZE + 4;
    private static final int LEFT_ARM_OFFSET_X = 0;
    private static final int LEFT_ARM_OFFSET_Y = HEAD_SIZE + 4;
    private static final int RIGHT_ARM_OFFSET_X = ARM_W + BODY_W + 18;
    private static final int RIGHT_ARM_OFFSET_Y = HEAD_SIZE + 4;
    private static final int LEFT_LEG_OFFSET_X = ARM_W + 9 + 1;
    private static final int LEFT_LEG_OFFSET_Y = HEAD_SIZE + BODY_H + 8;
    private static final int RIGHT_LEG_OFFSET_X = ARM_W + 9 + BODY_W - LEG_W - 1;
    private static final int RIGHT_LEG_OFFSET_Y = HEAD_SIZE + BODY_H + 8;

    private static final int VIZ_PROGRESS = 0;
    private static final int VIZ_SEGMENT = 1;
    private static final int VIZ_FLOW = 2;

    private static final int HUD_BAR_H = 48;

    public MedicalScreen() {
        super(Component.translatable("medical.gui.title"));
        for (int i = 0; i < BUFFER_SIZE; i++) {
            historyY[i] = 0.5f;
            historyPPG[i] = 0.5f;
            historyValid[i] = false;
        }
    }

    @Override
    protected void init() {
        super.init();
        openStartTime = System.currentTimeMillis();
        state = ScreenState.OPENING;
        particlesInitialized = false;
        MedicalSoundHelper.playOpenSound();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    private MedicalData getMedicalData() {
        if (this.minecraft == null || this.minecraft.player == null) return null;
        return this.minecraft.player.getData(MedicalDataCapability.MEDICAL_DATA);
    }

    // ============================================================
    // 四、红血球漂流场 (Erythrocyte Drift Field)
    // ============================================================
    private void initParticles() {
        for (int i = 0; i < MAX_PARTICLES; i++) {
            respawnParticle(i);
        }
        particlesInitialized = true;
    }

    private void respawnParticle(int i) {
        particleX[i] = (float) (Math.random() * (this.width + 40) - 20);
        particleY[i] = (float) (Math.random() * (this.height + 40) - 20);
        particleVX[i] = ((float) Math.random() - 0.5f) * 12.0f;
        particleVY[i] = -((float) Math.random() * 8.0f + 3.0f);
        particleMaxLife[i] = (float) Math.random() * 4.0f + 2.0f;
        particleLife[i] = (float) (Math.random() * particleMaxLife[i]);
        int[] tints = {0xFF6A3C, 0xE53935, 0xC62828, 0xD84315, 0xB71C1C};
        particleColor[i] = tints[(int) (Math.random() * tints.length)];
    }

    private void updateAndRenderParticles(GuiGraphics g, float dt) {
        if (!particlesInitialized) initParticles();

        MedicalData data = getMedicalData();
        float volRatio = 1.0f;
        float spo2N = 1.0f;
        float hrScale = 1.0f;
        if (data != null) {
            volRatio = Math.max(0.15f, Math.min(1.0f,
                    (float) data.getBloodVolume() / Math.max(1, data.getMaxBloodVolume())));
            float spo2 = data.getBloodOxygenFloat();
            spo2N = Math.max(0.0f, Math.min(1.0f, (spo2 - 20.0f) / 55.0f));
            hrScale = Math.max(0.5f, Math.min(2.2f, data.getHeartRate() / 72.0f));
        }

        int aliveCount = (int) (MAX_PARTICLES * (0.35f + 0.65f * volRatio));

        for (int i = 0; i < MAX_PARTICLES; i++) {
            if (i >= aliveCount) {
                particleLife[i] += dt;
                if (particleLife[i] >= particleMaxLife[i]) {
                    respawnParticle(i);
                    particleLife[i] = 0;
                }
                continue;
            }
            particleLife[i] += dt;
            if (particleLife[i] >= particleMaxLife[i]) {
                respawnParticle(i);
                particleLife[i] = 0;
            }
            particleX[i] += particleVX[i] * dt * hrScale;
            particleY[i] += particleVY[i] * dt * hrScale;

            float lifeFrac = particleLife[i] / particleMaxLife[i];
            float alpha = (float) Math.sin(lifeFrac * Math.PI) * 0.35f;

            int baseCol = particleColor[i];
            int r = (baseCol >> 16) & 0xFF;
            int gg = (baseCol >> 8) & 0xFF;
            int b = baseCol & 0xFF;
            int rr = (int) (r * (0.45f + 0.55f * spo2N));
            int g2 = (int) (gg * (0.55f + 0.45f * spo2N));
            int bb = (int) Math.min(255, b + (1.0f - spo2N) * 120);
            int col = (rr << 16) | (g2 << 8) | bb;

            int a = (int) (alpha * 255) & 0xFF;
            int finalCol = (a << 24) | (col & 0x00FFFFFF);

            float size = 1.6f + (float) Math.sin(lifeFrac * Math.PI) * 1.6f;
            int px = (int) particleX[i];
            int py = (int) particleY[i];
            int s = (int) size;

            g.fill(px - s - 1, py - s - 1, px + s + 2, py + s + 2,
                    (Math.max(0, a / 4) << 24) | (col & 0x00FFFFFF));
            g.fill(px - s, py - s, px + s + 1, py + s + 1, finalCol);
            if (s >= 2) {
                g.fill(px - s / 3, py - s / 3, px + s / 3 + 1, py + s / 3 + 1,
                        ((int) (a * 0.45f) << 24) | (col & 0x00FFFFFF));
            }
        }
    }

    // ============================================================
    // 五、全局主渲染循环 (2.5D 视差 + 物理驱动)
    // ============================================================
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        long now = System.currentTimeMillis();
        float deltaSeconds = 0.016f;

        globalPulse += deltaSeconds;
        selectedPulsePhase += deltaSeconds * 3.5f;
        scanBeamY += deltaSeconds * 45.0f;

        float targetTiltX = ((mouseX - (this.width / 2.0f)) / (this.width / 2.0f)) * 4.5f;
        float targetTiltY = ((mouseY - (this.height / 2.0f)) / (this.height / 2.0f)) * 3.0f;
        smoothTiltX += (targetTiltX - smoothTiltX) * 0.12f;
        smoothTiltY += (targetTiltY - smoothTiltY) * 0.12f;

        float globalScale = 1.0f;
        float globalAlpha = 1.0f;

        if (state == ScreenState.OPENING) {
            long elapsed = now - openStartTime;
            float progress = Mth.clamp((float) elapsed / OPEN_DURATION_MS, 0.0f, 1.0f);
            globalScale = EasingFunctions.easeOutBack(progress);
            globalAlpha = EasingFunctions.easeOutCubic(progress);
            if (progress >= 1.0f) {
                state = ScreenState.OPEN;
                globalScale = 1.0f;
                globalAlpha = 1.0f;
            }
        } else if (state == ScreenState.CLOSING) {
            long elapsed = now - closeStartTime;
            float progress = Mth.clamp((float) elapsed / CLOSE_DURATION_MS, 0.0f, 1.0f);
            globalScale = 1.0f - EasingFunctions.easeInBack(progress) * 0.15f;
            globalAlpha = 1.0f - EasingFunctions.easeInCubic(progress);
            if (progress >= 1.0f) {
                state = ScreenState.CLOSED;
                super.onClose();
                return;
            }
        }

        HudRenderHelper.drawBackgroundGridAndScanline(graphics, this.width, this.height, now);
        HudRenderHelper.drawAmbientPhotonDust(graphics, this.width, this.height, now);
        drawCinematicVignette(graphics);
        updateAndRenderParticles(graphics, deltaSeconds);

        MedicalData data = getMedicalData();
        if (data == null) {
            drawCyberCard(graphics, 20, 20, this.width - 40, this.height - 40, "INITIALIZING NEURAL TELEMETRY...", 0xFF00E5FF);
            drawLoadingAnimation(graphics, this.width / 2, this.height / 2 + 30, now);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        boolean isCardiacDeath = data.isCardiacArrest();
        boolean isNeuralCrisis = !isCardiacDeath && (sprBrainHealth.val < 30.0f || sprConsciousness.val < 25.0f);
        if (isCardiacDeath) {
            drawAsystoleTakeover(graphics, now);
        } else if (isNeuralCrisis) {
            drawNeuralInterference(graphics, now);
        }

        updateSpringVitals(data, deltaSeconds);
        generatePhysiologyWaves(data, deltaSeconds);
        updateHoverState(mouseX, mouseY, deltaSeconds);
        updateConnectionLine(deltaSeconds);

        float hrNormalized = Mth.clamp(sprHeartRate.val / 60.0f, 0.6f, 3.2f);
        cardiacPulse = (float) Math.sin(globalPulse * hrNormalized * Math.PI);

        graphics.pose().pushPose();
        float centerX = this.width / 2.0f;
        float centerY = this.height / 2.0f;
        graphics.pose().translate(centerX + smoothTiltX * 1.5f, centerY + smoothTiltY * 1.5f, 0.0f);
        graphics.pose().scale(globalScale, globalScale, 1.0f);
        graphics.pose().translate(-centerX, -centerY, 0.0f);

        float topBarP = 1f, bodyP = 1f, detailP = 1f, footerP = 1f;
        if (state == ScreenState.OPENING) {
            long elapsed = now - openStartTime;
            topBarP = EasingFunctions.easeOutCubic(Mth.clamp((elapsed - 40) / 200f, 0f, 1f));
            bodyP   = EasingFunctions.easeOutCubic(Mth.clamp((elapsed - 100) / 220f, 0f, 1f));
            detailP = EasingFunctions.easeOutCubic(Mth.clamp((elapsed - 160) / 240f, 0f, 1f));
            footerP = EasingFunctions.easeOutCubic(Mth.clamp((elapsed - 240) / 180f, 0f, 1f));
        }

        float asmTop = topBarP, asmBody = bodyP, asmDetail = detailP, asmFooter = footerP;
        drawAssemblyTrail(graphics, this.width / 2.0f, 26f, asmTop, now, 0);
        drawAssemblyTrail(graphics, 140f, this.height / 2.0f, asmBody, now, 1);
        drawAssemblyTrail(graphics, this.width - 150f, this.height / 2.0f, asmDetail, now, 2);
        drawAssemblyTrail(graphics, this.width / 2.0f, this.height - 18f, asmFooter, now, 3);
        drawAssemblyReticle(graphics, Math.min(Math.min(asmTop, asmBody), Math.min(asmDetail, asmFooter)), now);

        graphics.pose().pushPose();
        float zTop = 1.0f - 0.55f * (1.0f - topBarP);
        graphics.pose().translate(this.width / 2.0f, 26f, 0f);
        graphics.pose().scale(zTop, zTop, 1.0f);
        graphics.pose().translate(-this.width / 2.0f, -26f, 0f);
        graphics.pose().translate(0f, -42f * (1f - topBarP), 0f);
        drawHudHeader(graphics, data, now);
        graphics.pose().popPose();

        graphics.pose().pushPose();
        float zBody = 1.0f - 0.40f * (1.0f - bodyP);
        graphics.pose().translate(140f, this.height / 2.0f, 0f);
        graphics.pose().scale(zBody, zBody, 1.0f);
        graphics.pose().translate(-140f, -this.height / 2.0f, 0f);
        graphics.pose().translate(-90f * (1f - bodyP), -16f * (1f - bodyP), 0f);
        drawBodyModelCard(graphics, data, mouseX, mouseY, deltaSeconds, now);
        graphics.pose().popPose();

        if (selectedLimb != null && connectionLineProgress > 0.01f) {
            drawConnectionLine(graphics, data, now);
        }

        graphics.pose().pushPose();
        float zDetail = 1.0f - 0.40f * (1.0f - detailP);
        graphics.pose().translate(this.width - 150f, this.height / 2.0f, 0f);
        graphics.pose().scale(zDetail, zDetail, 1.0f);
        graphics.pose().translate(-(this.width - 150f), -this.height / 2.0f, 0f);
        graphics.pose().translate(90f * (1f - detailP), 16f * (1f - detailP), 0f);
        drawDetailPanel(graphics, data, mouseX, mouseY, deltaSeconds, now);
        graphics.pose().popPose();

        rippleManager.updateAndRender(graphics, deltaSeconds);

        graphics.pose().pushPose();
        float zFooter = 1.0f - 0.30f * (1.0f - footerP);
        graphics.pose().translate(this.width / 2.0f, this.height - 18f, 0f);
        graphics.pose().scale(zFooter, zFooter, 1.0f);
        graphics.pose().translate(-this.width / 2.0f, -(this.height - 18f), 0f);
        graphics.pose().translate(0f, 26f * (1f - footerP), 0f);
        drawHelpText(graphics, now);
        graphics.pose().popPose();

        graphics.pose().popPose();

        drawFloatingTriageCard(graphics, mouseX, mouseY, data);
        drawMicroTomographyHUD(graphics, data, now);
        drawCyberCursor(graphics, mouseX, mouseY, now);

        lastMouseX = mouseX;
        lastMouseY = mouseY;
        int chipCx = this.width - 26;
        int chipCy = this.height - 18;
        boolean hoveringChip = mouseX > chipCx - 14 && mouseX < chipCx + 14
                && mouseY > chipCy - 14 && mouseY < chipCy + 14;
        float legendTarget = hoveringChip ? 1f : 0f;
        legendExpand += (legendTarget - legendExpand) * Math.min(1f, deltaSeconds * 10f);
        if (legendExpand < 0.005f) legendExpand = 0f;

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    // ============================================================
    // 六、数值与生理信号驱动
    // ============================================================
    private void updateSpringVitals(MedicalData data, float dt) {
        sprHeartRate.update(data.getHeartRate(), dt, 150.0f, 16.0f);
        sprSystolic.update(data.getSystolicBP(), dt, 130.0f, 15.0f);
        sprDiastolic.update(data.getDiastolicBP(), dt, 130.0f, 15.0f);
        sprOxygen.update(data.getBloodOxygen(), dt, 90.0f, 14.0f);
        sprRespiration.update(data.getRespirationRate(), dt, 90.0f, 14.0f);
        sprBloodLoss.update(data.getBloodLossAccumulator(), dt, 100.0f, 15.0f);
        sprConsciousness.update(data.getConsciousness(), dt, 100.0f, 15.0f);
        sprBrainHealth.update(data.getBrainHealth(), dt, 100.0f, 15.0f);
        sprBloodVolume.update(data.getBloodVolume(), dt, 80.0f, 14.0f);
        sprCardiacOutput.update(data.getCardiacOutput(), dt, 110.0f, 15.0f);

        if (sweepScanAnim > 0.0f) {
            sweepScanAnim += dt / 3.0f;
            if (sweepScanAnim >= 2.0f) {
                sweepScanAnim = 0.0f;
                sweepScanRevealed = false;
            }
            if (!sweepScanRevealed && sweepScanAnim >= 1.0f) {
                sweepScanRevealed = true;
                sweepScanStampTime = System.currentTimeMillis();
            }
        }

        if (microTomographyLimb != null) {
            microTomographyAnim = Math.min(1.0f, microTomographyAnim + dt * 5.0f);
        } else {
            microTomographyAnim = Math.max(0.0f, microTomographyAnim - dt * 5.0f);
        }
    }

    private void generatePhysiologyWaves(MedicalData data, float dt) {
        if (dt <= 0.0f) return;
        final float sweepSpeed = 75.0f;
        sweepAccumulator += dt * sweepSpeed;

        int steps = (int) sweepAccumulator;
        if (steps <= 0) return;
        sweepAccumulator -= steps;
        if (steps > 30) steps = 30;

        int heartRate = data.getHeartRate();
        boolean cardiacArrest = data.isCardiacArrest();
        boolean inVFib = data.isInVFib();
        float effectiveDt = dt / Math.max(1, steps);

        for (int s = 0; s < steps; s++) {
            float ecgVal = 0.5f;
            float ppgVal = 0.5f;

            if (cardiacArrest) {
                cardiacCycleTime = 0.0f;
                float drift = (float) Math.sin(System.currentTimeMillis() * 0.0005) * 0.008f;
                float noise = ((float) Math.random() - 0.5f) * 0.012f;
                ecgVal = 0.5f + drift + noise;
                ppgVal = 0.5f;
            } else if (inVFib) {
                cardiacCycleTime = 0.0f;
                vfibPhase += effectiveDt * 35.0f;
                float f1 = (float) Math.sin(vfibPhase * 1.0f) * 0.22f;
                float f2 = (float) Math.sin(vfibPhase * 2.37f + 1.2f) * 0.15f;
                float f3 = (float) Math.sin(vfibPhase * 0.43f + 2.7f) * 0.09f;
                float f4 = (float) Math.sin(vfibPhase * 5.11f) * 0.05f;
                float noise = ((float) Math.random() - 0.5f) * 0.04f;
                ecgVal = 0.5f + f1 + f2 + f3 + f4 + noise;
                ppgVal = 0.5f + (float) Math.sin(vfibPhase * 0.4f) * 0.06f;
            } else if (heartRate > 0) {
                float rrInterval = 60.0f / (float) heartRate;
                cardiacCycleTime += effectiveDt;
                if (cardiacCycleTime >= rrInterval) {
                    cardiacCycleTime -= rrInterval;
                    rPeakFlash = 1.0f;
                }
                float t = cardiacCycleTime;
                ecgVal = evaluateLeadIIECG(t, rrInterval) + ((float) Math.random() - 0.5f) * 0.006f;

                float tNorm = t / rrInterval;
                if (tNorm < 0.28f) {
                    ppgVal = 0.5f - (float) Math.sin((tNorm / 0.28f) * Math.PI) * 0.36f;
                } else if (tNorm < 0.42f) {
                    ppgVal = 0.5f - 0.12f * (float) Math.sin(((tNorm - 0.28f) / 0.14f) * Math.PI);
                } else {
                    ppgVal = 0.5f + (0.5f - ppgVal) * 0.08f;
                }
            }

            historyY[sweepIndex] = Mth.clamp(ecgVal, 0.02f, 0.98f);
            historyPPG[sweepIndex] = Mth.clamp(ppgVal, 0.04f, 0.96f);
            historyValid[sweepIndex] = true;
            sweepIndex = (sweepIndex + 1) % BUFFER_SIZE;

            for (int k = 0; k < 5; k++) {
                int eraseIdx = (sweepIndex + k) % BUFFER_SIZE;
                historyValid[eraseIdx] = false;
            }
        }

        if (rPeakFlash > 0.0f) {
            rPeakFlash = Math.max(0.0f, rPeakFlash - dt * 3.0f);
        }
    }

    private float evaluateLeadIIECG(float t, float rrInterval) {
        float baseline = 0.5f;
        float pStart = 0.00f;
        float pDur = 0.09f;
        if (t >= pStart && t < pStart + pDur) {
            float phase = (t - pStart) / pDur;
            return baseline - (float) Math.sin(phase * Math.PI) * 0.055f;
        }

        float prSegEnd = 0.16f;
        if (t < prSegEnd) return baseline;

        float qrsStart = 0.16f;
        float qDur = 0.025f;
        if (t >= qrsStart && t < qrsStart + qDur) {
            float phase = (t - qrsStart) / qDur;
            return baseline + (float) Math.sin(phase * Math.PI) * 0.04f;
        }

        float rStart = qrsStart + qDur;
        float rDur = 0.040f;
        if (t >= rStart && t < rStart + rDur) {
            float phase = (t - rStart) / rDur;
            return baseline - (float) Math.sin(phase * Math.PI) * 0.42f;
        }

        float sStart = rStart + rDur;
        float sDur = 0.030f;
        if (t >= sStart && t < sStart + sDur) {
            float phase = (t - sStart) / sDur;
            return baseline + (float) Math.sin(phase * Math.PI) * 0.09f;
        }

        float stSegEnd = 0.32f;
        if (t < stSegEnd) return baseline;

        float tStart = 0.32f;
        float tDur = 0.18f;
        if (t >= tStart && t < tStart + tDur) {
            float phase = (t - tStart) / tDur;
            return baseline - (float) Math.sin(phase * Math.PI) * 0.10f;
        }

        return baseline;
    }

    // ============================================================
    // 七、顶部科技 HUD 标题栏
    // ============================================================
    private void drawHudHeader(GuiGraphics graphics, MedicalData data, long now) {
        int barX = 12;
        int barY = 6;
        int barW = this.width - 24;
        int barH = HUD_BAR_H;

        boolean critical = data.isCardiacArrest() || data.isInVFib();
        int themeCol = critical ? HudColors.DANGER : HudColors.CYAN;

        drawCyberCard(graphics, barX, barY, barW, barH, "", themeCol);

        float pulse = 0.5f + 0.5f * (float) Math.sin(now / 180.0);
        int dx = barX + 14;
        int dy = barY + barH / 2 - 3;

        graphics.fill(dx - 4, dy - 4, dx + 9, dy + 9, HudColors.withAlpha(themeCol, 0.12f + 0.18f * pulse));
        graphics.fill(dx, dy, dx + 5, dy + 5, themeCol);
        graphics.fill(dx + 1, dy + 1, dx + 4, dy + 4, 0xFFFFFFFF);
        graphics.renderOutline(dx - 4, dy - 4, 13, 13, 0x4400E5FF);

        HudRenderHelper.drawGlowingString(graphics, this.font, "TRAUMA MONITOR", dx + 18, barY + 10, 0xFFE6F7FF, themeCol);
        boolean liveVisible = (now / 500) % 2 == 0;
        if (liveVisible) {
            int liveX = dx + 18 + this.font.width("TRAUMA MONITOR") + 8;
            graphics.fill(liveX, barY + 11, liveX + 4, barY + 15, 0xFFFF1744);
            graphics.drawString(this.font, "LIVE", liveX + 6, barY + 10, 0xFFFF1744, false);
        }
        graphics.drawString(this.font, "// 生物遥测神经终端 v2.5", dx + 18, barY + 24, 0xFF546E7A, false);

        int seconds = (int) ((now / 1000) % 60);
        int minutes = (int) ((now / 60000) % 60);
        String clock = String.format("%02d:%02d", minutes, seconds);
        graphics.drawString(this.font, clock, dx + 18 + this.font.width("// 生物遥测神经终端 v2.5") + 14,
                barY + 24, 0xFF37474F, false);

        if (this.width < 820) {
            int rx = barX + barW - 14;
            String txt = (int) sprHeartRate.val + " BPM  " +
                    (int) sprSystolic.val + "/" + (int) sprDiastolic.val + "  " +
                    (int) sprOxygen.val + "%";
            int tw = this.font.width(txt);
            graphics.drawString(this.font, txt, rx - tw, barY + barH / 2 - 4, HudColors.TEXT_PRIMARY, false);
            return;
        }

        int rightEdge = barX + barW - 16;
        int gcy = barY + barH / 2;

        int spo2Cx = rightEdge - 20;
        int spo2Val = (int) sprOxygen.val;
        int spo2Col = getOxygenColor(spo2Val);
        drawOrganRingGauge(graphics, spo2Cx, gcy - 2, 14, sprOxygen.val / 100f, spo2Col,
                spo2Val + "%", spo2Val < 90, OrganType.LUNG, now);

        int hrCx = spo2Cx - 80;
        int hrVal = (int) sprHeartRate.val;
        int hrCol = getHeartRateColor(hrVal);
        boolean hrAlert = hrVal <= 0 || hrVal > 140;
        drawOrganRingGauge(graphics, hrCx, gcy - 2, 14, sprHeartRate.val / 200f, hrCol,
                String.valueOf(hrVal), hrAlert, OrganType.HEART, now);

        if (rPeakFlash > 0.1f) {
            int flashA = (int) (rPeakFlash * 180);
            graphics.fill(hrCx - 2, gcy - 20, hrCx + 3, gcy - 17,
                    (flashA << 24) | (hrCol & 0x00FFFFFF));
        }

        int bpW = 120;
        int bpH = barH - 16;
        int bpX = hrCx - 80 - bpW;
        int bpY = barY + 8;
        drawBPCapsule(graphics, bpX, bpY, bpW, bpH, data);
        graphics.fill(bpX - 8, barY + 8, bpX - 7, barY + barH - 8, 0x3300E5FF);
    }

    /** 器官剪影类型 (微像素代码绘制, 无贴图) */
    private enum OrganType { HEART, LUNG }

    /**
     * 器官剪影仪表 (Organ-Silhouette Gauge) — 取代抽象环形仪表
     */
    private void drawOrganRingGauge(GuiGraphics g, int cx, int cy, int radius,
                                    float value01, int color, String valueText,
                                    boolean alert, OrganType organ, long now) {
        final float startDeg = 135f;
        final float sweepDeg = 270f;
        final int segments = 18;
        final float segArc = sweepDeg / segments;
        float clamped = Mth.clamp(value01, 0f, 1f);

        drawSmoothArc(g, cx, cy, radius + 3, startDeg, startDeg + sweepDeg, 0x1800E5FF, 1);
        drawSmoothArc(g, cx, cy, radius, startDeg, startDeg + sweepDeg, 0xFF0A1820, 3);
        for (int i = 0; i < segments; i++) {
            float segMidT = (i + 0.5f) / segments;
            if (segMidT > clamped) break;
            float a0 = startDeg + i * segArc + 1.0f;
            float a1 = startDeg + (i + 1) * segArc - 1.0f;
            int col = color;
            if (alert) {
                float p = 0.45f + 0.55f * (float) Math.sin(now / 140.0);
                col = HudColors.withAlpha(col, p);
            }
            drawSmoothArc(g, cx, cy, radius, a0, a1, col, 3);
        }
        if (clamped > 0.01f) {
            float endDeg = startDeg + sweepDeg * clamped;
            double rad = Math.toRadians(endDeg);
            int ex = cx + (int) Math.round(Math.cos(rad) * radius);
            int ey = cy + (int) Math.round(Math.sin(rad) * radius);
            g.fill(ex - 1, ey - 1, ex + 2, ey + 2, 0xCCFFFFFF);
        }

        g.pose().pushPose();
        g.pose().translate(cx, cy - 2, 0);
        drawOrganSilhouette(g, organ, now, color, value01);
        g.pose().popPose();

        g.pose().pushPose();
        g.pose().translate(cx, cy + radius + 3, 0);
        g.pose().scale(0.8f, 0.8f, 1f);
        int tw = this.font.width(valueText);
        g.drawString(this.font, valueText, -tw / 2, 0, 0xFFFFFFFF, false);
        g.pose().popPose();
    }

    private void drawOrganSilhouette(GuiGraphics g, OrganType organ, long now, int color, float value01) {
        int bodyCol = (0xFF << 24) | (color & 0x00FFFFFF);
        int dimCol = 0x6600B8D4;

        if (organ == OrganType.HEART) {
            float beat = cardiacPulse;
            float scale = 1.0f + beat * 0.35f;
            boolean dead = value01 <= 0.005f;
            boolean fibrillating = dead || (value01 > 1.0f - 0.001f && beat < 0.01f && (now / 80) % 2 == 0);

            g.pose().pushPose();
            if (fibrillating) {
                int jx = (int) ((now / 30) % 3) - 1;
                int jy = (int) ((now / 47) % 3) - 1;
                g.pose().translate(jx, jy, 0);
            }
            g.pose().scale(scale, scale, 1f);

            g.fill(-3, -4, -1, -2, bodyCol);
            g.fill(1, -4, 3, -2, bodyCol);
            g.fill(-4, -2, 4, 0, bodyCol);
            g.fill(-3, 0, 3, 2, bodyCol);
            g.fill(-1, 2, 1, 3, bodyCol);
            g.fill(-3, -3, -2, -2, 0xCCFFFFFF);
            if (fibrillating) {
                int fl = (int) ((now / 90) % 4);
                g.fill(-2 + fl, 1, -1 + fl, 2, 0xFFFF1744);
            }
            if (dead) {
                g.fill(-3, -1, 3, 1, 0xFF37474F);
            }
            g.pose().popPose();
        } else if (organ == OrganType.LUNG) {
            float respRate = Math.max(4.0f, sprRespiration != null ? sprRespiration.val : 16.0f);
            float respCycle = (float) ((now / 1000.0) * (respRate / 12.0) * Math.PI * 2.0);
            float breathe = 0.5f + 0.5f * (float) Math.sin(respCycle);
            int inhale = (int) (breathe * 2);

            for (int lobe = 0; lobe < 2; lobe++) {
                int ox = lobe == 0 ? -5 : 1;
                g.fill(ox, -4, ox + 4, -2, bodyCol);
                g.fill(ox, -2, ox + 4, 2 + inhale, bodyCol);
                g.fill(ox + 1, -3, ox + 2, 0, dimCol);
                g.fill(ox + 2, 1, ox + 3, 1 + inhale, dimCol);
            }
            g.fill(-1, -5, 1, -1, 0xFFB0BEC5);
            g.fill(-4, -1, -3, 0, 0xFFB0BEC5);
            g.fill(3, -1, 4, 0, 0xFFB0BEC5);
            if (value01 < 0.6f) {
                int purpleA = (int) ((0.6f - value01) * 200);
                g.fill(-5, -5, 5, 4, (purpleA << 24) | 0x006A1B9A);
            }
        }
    }

    private void drawRingGauge(GuiGraphics g, int cx, int cy, int radius,
                               float value01, int color, String valueText, boolean alert) {
        final float startDeg = 135f;
        final float sweepDeg = 270f;
        final int segments = 18;
        final float segArc = sweepDeg / segments;

        float clamped = Mth.clamp(value01, 0f, 1f);
        long now = System.currentTimeMillis();

        drawSmoothArc(g, cx, cy, radius + 3, startDeg, startDeg + sweepDeg, 0x1800E5FF, 1);
        drawSmoothArc(g, cx, cy, radius, startDeg, startDeg + sweepDeg, 0xFF0A1820, 3);

        for (int i = 0; i < segments; i++) {
            float segMidT = (i + 0.5f) / segments;
            if (segMidT > clamped) break;
            float a0 = startDeg + i * segArc + 1.0f;
            float a1 = startDeg + (i + 1) * segArc - 1.0f;

            int col = color;
            if (alert) {
                float p = 0.45f + 0.55f * (float) Math.sin(now / 140.0);
                col = HudColors.withAlpha(col, p);
            }
            drawSmoothArc(g, cx, cy, radius, a0, a1, col, 3);
        }

        if (clamped > 0.01f) {
            float endDeg = startDeg + sweepDeg * clamped;
            double rad = Math.toRadians(endDeg);
            int ex = cx + (int) Math.round(Math.cos(rad) * radius);
            int ey = cy + (int) Math.round(Math.sin(rad) * radius);
            g.fill(ex - 1, ey - 1, ex + 2, ey + 2, 0xCCFFFFFF);
        }

        g.pose().pushPose();
        g.pose().translate(cx, cy - 1, 0);
        g.pose().scale(0.8f, 0.8f, 1f);
        try {
            float num = Float.parseFloat(valueText);
            int len = valueText.length();
            int totalW = len * 7;
            HudRenderHelper.drawRollingNumber(g, this.font, num, -totalW / 2, -4, 0xFFFFFFFF);
        } catch (Exception ignored) {
            int tw = this.font.width(valueText);
            g.drawString(this.font, valueText, -tw / 2, -4, 0xFFFFFFFF, false);
        }
        g.pose().popPose();
    }

    private void drawBigRingGauge(GuiGraphics g, int cx, int cy, int radius,
                                  float value01, int color, String value,
                                  String unit, String label) {
        final float startDeg = 135f;
        final float sweepDeg = 270f;
        float clamped = Mth.clamp(value01, 0f, 1f);

        drawSmoothArc(g, cx, cy, radius + 6, startDeg, startDeg + sweepDeg, 0x0C00E5FF, 1);
        drawSmoothArc(g, cx, cy, radius + 3, startDeg, startDeg + sweepDeg, 0x2200E5FF, 1);
        drawSmoothArc(g, cx, cy, radius, startDeg, startDeg + sweepDeg, 0xFF0A1820, 5);

        if (clamped > 0f) {
            float endDeg = startDeg + sweepDeg * clamped;
            drawSmoothArc(g, cx, cy, radius, startDeg, endDeg, color, 5);
            drawSmoothArc(g, cx, cy, radius + 2, startDeg, endDeg, HudColors.withAlpha(color, 0.25f), 1);

            double rad = Math.toRadians(endDeg);
            int ex = cx + (int) Math.round(Math.cos(rad) * radius);
            int ey = cy + (int) Math.round(Math.sin(rad) * radius);
            g.fill(ex - 3, ey - 3, ex + 4, ey + 4, HudColors.withAlpha(color, 0.4f));
            g.fill(ex - 1, ey - 1, ex + 2, ey + 2, 0xFFFFFFFF);
        }

        float[] tickPositions = {0f, 0.25f, 0.5f, 0.75f, 1.0f};
        for (float tp : tickPositions) {
            float tickDeg = startDeg + sweepDeg * tp;
            double rad = Math.toRadians(tickDeg);
            int tx1 = cx + (int) Math.round(Math.cos(rad) * (radius - 4));
            int ty1 = cy + (int) Math.round(Math.sin(rad) * (radius - 4));
            int tx2 = cx + (int) Math.round(Math.cos(rad) * (radius + 5));
            int ty2 = cy + (int) Math.round(Math.sin(rad) * (radius + 5));
            g.fill(Math.min(tx1, tx2), Math.min(ty1, ty2),
                    Math.max(tx1, tx2) + 1, Math.max(ty1, ty2) + 1, 0x4400E5FF);
        }

        g.pose().pushPose();
        g.pose().translate(cx, cy - 7, 0);
        g.pose().scale(1.7f, 1.7f, 1f);
        int vw = this.font.width(value);
        g.drawString(this.font, value, -vw / 2, 0, 0xFFFFFFFF, false);
        g.pose().popPose();

        int uw = this.font.width(unit);
        g.drawString(this.font, unit, cx - uw / 2, cy + 10, HudColors.TEXT_SECONDARY, false);

        int lw = this.font.width(label);
        g.drawString(this.font, label, cx - lw / 2, cy + radius + 8, 0xFFB0BEC5, false);
    }

    private void drawBPCapsule(GuiGraphics g, int x, int y, int w, int h, MedicalData data) {
        g.fill(x, y, x + w, y + h, 0x66081218);
        g.renderOutline(x, y, w, h, 0xFF1A3040);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x1800E5FF);

        g.drawString(this.font, "BP mmHg", x + 5, y + 3, 0xFF546E7A, false);

        int bpCol = getBPColor(data.getMeanArterialPressure(), (int) sprSystolic.val);
        int curX = x + w - 50;
        HudRenderHelper.drawRollingNumber(g, this.font, sprSystolic.val, curX, y + 3, bpCol);
        g.drawString(this.font, "/", curX + 18, y + 3, 0xFF888888, false);
        HudRenderHelper.drawRollingNumber(g, this.font, sprDiastolic.val, curX + 24, y + 3, bpCol);

        int tx = x + 6;
        int tw = w - 12;
        int ty1 = y + h - 18;
        int ty2 = y + h - 9;

        float sbp = Mth.clamp(sprSystolic.val / 240f, 0f, 1f);
        float dbp = Mth.clamp(sprDiastolic.val / 160f, 0f, 1f);

        int normX0 = tx + (int) (tw * (90f / 240f));
        int normX1 = tx + (int) (tw * (140f / 240f));
        g.fill(normX0, ty1, normX1, ty1 + 5, 0x1A00FF88);

        drawGlowBar(g, tx, ty1, tw, 5, sbp, bpCol, "SYS");
        drawGlowBar(g, tx, ty2, tw, 5, dbp, HudColors.withAlpha(bpCol, 0.8f), "DIA");

        float mapT = Mth.clamp(data.getMeanArterialPressure() / 160f, 0f, 1f);
        int mapX = tx + (int) (tw * mapT);
        g.fill(mapX, ty1 - 4, mapX + 1, ty2 + 8, 0xBB00E5FF);
        g.fill(mapX - 1, ty1 - 5, mapX + 2, ty1 - 3, 0xFF00E5FF);
    }

    private void drawGlowBar(GuiGraphics g, int x, int y, int w, int h, float t, int color, String label) {
        g.fill(x, y, x + w, y + h, 0x331A2A33);
        int fw = (int) (w * t);
        if (fw > 0) {
            g.fill(x, y, x + fw, y + h, color);
            g.fill(x, y, x + fw, y + 1, HudColors.withAlpha(color, 0.9f));
            g.fill(x + fw - 1, y - 1, x + fw + 1, y + h + 1, 0xCCFFFFFF);
        }
    }

    // ============================================================
    // 八、人体拓扑视口卡片
    // ============================================================
    private void drawBodyModelCard(GuiGraphics graphics, MedicalData data, int mouseX, int mouseY,
                                   float deltaSeconds, long now) {
        int cardX = 24;
        int cardY = HUD_BAR_H + 14;
        int cardW = 220;
        int cardH = this.height - HUD_BAR_H - 50;

        drawCyberCard(graphics, cardX, cardY, cardW, cardH, "ANATOMY SCAN // 解剖拓扑", 0xFF00E5FF);
        SciFiDataStreamRenderer.renderVerticalHexStream(graphics, this.font,
                cardX + cardW - 34, cardY + 30, cardH - 50, now);

        int startX = cardX + (cardW - TOTAL_W) / 2 + 10;
        int startY = cardY + (cardH - TOTAL_H) / 2 + 5;

        float beamPhase = (scanBeamY % (TOTAL_H + 40)) - 20;
        int beamScreenY = startY + (int) beamPhase;
        if (beamScreenY > startY - 5 && beamScreenY < startY + TOTAL_H + 5) {
            graphics.fill(startX - 12, beamScreenY, startX + TOTAL_W + 12, beamScreenY + 1, 0x5500E5FF);
        }

        int gridColor = 0x0C00E5FF;
        for (int gx = startX; gx < startX + TOTAL_W; gx += 8) {
            graphics.fill(gx, startY, gx + 1, startY + TOTAL_H, gridColor);
        }
        for (int gy = startY; gy < startY + TOTAL_H; gy += 8) {
            graphics.fill(startX, gy, startX + TOTAL_W, gy + 1, gridColor);
        }

        drawLimb(graphics, data, MedicalData.Limb.HEAD, startX + HEAD_OFFSET_X, startY + HEAD_OFFSET_Y,
                HEAD_SIZE, HEAD_SIZE, mouseX, mouseY, now);
        drawLimb(graphics, data, MedicalData.Limb.CHEST, startX + BODY_OFFSET_X, startY + BODY_OFFSET_Y,
                BODY_W, BODY_H, mouseX, mouseY, now);
        drawLimb(graphics, data, MedicalData.Limb.LEFT_ARM, startX + LEFT_ARM_OFFSET_X, startY + LEFT_ARM_OFFSET_Y,
                ARM_W, ARM_H, mouseX, mouseY, now);
        drawLimb(graphics, data, MedicalData.Limb.RIGHT_ARM, startX + RIGHT_ARM_OFFSET_X, startY + RIGHT_ARM_OFFSET_Y,
                ARM_W, ARM_H, mouseX, mouseY, now);
        drawLimb(graphics, data, MedicalData.Limb.LEFT_LEG, startX + LEFT_LEG_OFFSET_X, startY + LEFT_LEG_OFFSET_Y,
                LEG_W, LEG_H, mouseX, mouseY, now);
        drawLimb(graphics, data, MedicalData.Limb.RIGHT_LEG, startX + RIGHT_LEG_OFFSET_X, startY + RIGHT_LEG_OFFSET_Y,
                LEG_W, LEG_H, mouseX, mouseY, now);

        drawVascularNetwork(graphics, startX, startY, data, now);
        drawBodyDashboard(graphics, data, startX, startY, now);
        drawOnDemandSweepScan(graphics, data, startX, startY, TOTAL_W, TOTAL_H, now);
        drawFloatingVitals(graphics, data, startX, startY, now);

        if (data.isCardiacArrest() || data.isInVFib()) {
            float alertPulse = 0.5f + 0.5f * (float) Math.sin(now / 120.0);
            int alertBgAlpha = (int) ((0.30f + 0.50f * alertPulse) * 255);
            graphics.fill(cardX + 8, cardY + cardH - 28, cardX + cardW - 8, cardY + cardH - 8,
                    (alertBgAlpha << 24) | 0x00FF1744);
            HudRenderHelper.drawGlowingString(graphics, this.font, "⚠ CARDIAC EMERGENCY",
                    cardX + 16, cardY + cardH - 24, 0xFFFFFFFF, HudColors.DANGER);
        }

        int statusY = cardY + cardH - 40;
        if (!data.isCardiacArrest() && !data.isInVFib()) {
            int limbIssues = countLimbIssues(data);
            String statusMsg = limbIssues == 0 ? "ALL SYSTEMS NOMINAL" : limbIssues + " ANOMALIES DETECTED";
            int statusCol = limbIssues == 0 ? 0xFF00E676 : 0xFFFF9100;
            drawCenteredSmall(graphics, statusMsg, cardX + cardW / 2, statusY, statusCol);
        }
    }

    private void drawVascularNetwork(GuiGraphics g, int bx, int by, MedicalData data, long now) {
        int heartX = bx + BODY_OFFSET_X + BODY_W / 2;
        int heartY = by + BODY_OFFSET_Y + 18;

        boolean arrest = data.isCardiacArrest() || data.isInVFib();
        float pulse = Math.max(0.0f, cardiacPulse);
        float beatScale = 1.0f + pulse * 0.5f;
        int hRad = (int) (5 * beatScale);

        if (!arrest) {
            int glowAlpha = (int) (pulse * 90);
            if (glowAlpha > 0) {
                g.fill(heartX - hRad - 2, heartY - hRad - 2, heartX + hRad + 2, heartY + hRad + 2, (glowAlpha << 24) | 0x00FF1744);
            }
        }
        g.fill(heartX - hRad, heartY - hRad, heartX + hRad, heartY + hRad, arrest ? 0x88550000 : 0xCCFF1744);
        g.fill(heartX - 2, heartY - 2, heartX + 2, heartY + 2, arrest ? 0xFF888888 : 0xFFFFFFFF);

        int hr = data.getHeartRate();
        int speedMs = arrest ? 100000 : Math.max(15, 60000 / Math.max(40, hr * 12));
        int offset = arrest ? 0 : (int) ((now / (long) speedMs) % 12);
        int col = arrest ? 0x33550000 : (pulse > 0.3f ? 0x9900E5FF : 0x5500ADB5);

        drawDottedLine(g, heartX, heartY, bx + HEAD_OFFSET_X + HEAD_SIZE / 2, by + HEAD_OFFSET_Y + HEAD_SIZE, col, offset);
        drawDottedLine(g, heartX, heartY, bx + LEFT_ARM_OFFSET_X + ARM_W / 2, by + LEFT_ARM_OFFSET_Y + 10, col, offset);
        drawDottedLine(g, heartX, heartY, bx + RIGHT_ARM_OFFSET_X + ARM_W / 2, by + RIGHT_ARM_OFFSET_Y + 10, col, offset);
        drawDottedLine(g, heartX, heartY + 16, bx + LEFT_LEG_OFFSET_X + LEG_W / 2, by + LEFT_LEG_OFFSET_Y + 10, col, offset);
        drawDottedLine(g, heartX, heartY + 16, bx + RIGHT_LEG_OFFSET_X + LEG_W / 2, by + RIGHT_LEG_OFFSET_Y + 10, col, offset);
    }

    private void drawBodyDashboard(GuiGraphics g, MedicalData data, int bx, int by, long now) {
        boolean arrest = data.isCardiacArrest() || data.isInVFib();
        float heartOut = Math.max(0.0f, Math.min(1.0f, data.getCardiacOutput()));
        float spo2 = data.getBloodOxygenFloat();
        float consciousness = Math.max(0.0f, Math.min(1.0f, data.getConsciousnessFloat() / 100.0f));
        float brainHp = Math.max(0.0f, Math.min(1.0f, data.getBrainHealthFloat() / 100.0f));
        float volRatio = Math.max(0.0f, Math.min(1.0f,
                (float) data.getBloodVolume() / Math.max(1, data.getMaxBloodVolume())));
        float pulse = Math.max(0.0f, cardiacPulse);

        int heartX = bx + BODY_OFFSET_X + BODY_W / 2;
        int heartY = by + BODY_OFFSET_Y + 18;
        if (!arrest) {
            float beatScale = 1.0f + pulse * 0.9f;
            int coreR = (int) (3 * beatScale);
            int coreAlpha = (int) (120 + pulse * 130);
            int haloAlpha = (int) ((26 + pulse * 60) * heartOut + 8);
            g.fill(heartX - coreR - 1, heartY - coreR - 1, heartX + coreR + 2, heartY + coreR + 2,
                    (Math.min(255, haloAlpha * 2) << 24) | 0x00FF1744);
            g.fill(heartX - coreR, heartY - coreR, heartX + coreR + 1, heartY + coreR + 1,
                    (coreAlpha << 24) | 0x00FF3D5C);
            g.fill(heartX - 1, heartY - 1, heartX + 2, heartY + 2, 0xFFFFFFFF);
        } else {
            int ember = (int) (40 + 20 * Math.sin(now / 600.0));
            g.fill(heartX - 4, heartY - 4, heartX + 5, heartY + 5, ((ember) << 24) | 0x00550000);
            g.fill(heartX - 1, heartY - 1, heartX + 2, heartY + 2, 0xFF662222);
        }

        int respRate = data.getRespirationRate();
        float respCycle = (float) ((now / 1000.0) * Math.max(0.5, respRate / 12.0) * Math.PI * 2.0);
        float breathe = 0.5f + 0.5f * (float) Math.sin(respCycle);
        if (arrest) breathe = 0.05f;
        float hypox = Math.max(0.0f, Math.min(1.0f, (75.0f - spo2) / 55.0f));
        int lungAlpha = (int) (breathe * 70 + 10);
        int lungCol = HudColors.lerpColor(hypox, 0x00E1F5FE, 0x007B1FA2);
        int lungW = BODY_W / 2 - 6;
        int lungH = BODY_H - 22;
        g.fill(bx + BODY_OFFSET_X + 3, by + BODY_OFFSET_Y + 6,
                bx + BODY_OFFSET_X + 3 + lungW, by + BODY_OFFSET_Y + 6 + (int) (lungH * (0.8f + breathe * 0.2f)),
                (lungAlpha << 24) | lungCol);
        g.fill(bx + BODY_OFFSET_X + BODY_W - 3 - lungW, by + BODY_OFFSET_Y + 6,
                bx + BODY_OFFSET_X + BODY_W - 3, by + BODY_OFFSET_Y + 6 + (int) (lungH * (0.8f + breathe * 0.2f)),
                (lungAlpha << 24) | lungCol);

        int brainCx = bx + HEAD_OFFSET_X + HEAD_SIZE / 2;
        int brainCy = by + HEAD_OFFSET_Y + HEAD_SIZE / 2;
        float auraBase = 2.0f + consciousness * 5.0f;
        int auraAlpha = (int) (30 + consciousness * 150);
        if (brainHp > 0.3f) {
            float fl = 1.0f + 0.06f * (float) Math.sin(now / 700.0);
            int r = (int) (auraBase * fl);
            g.fill(brainCx - r, brainCy - r, brainCx + r + 1, brainCy + r + 1,
                    (auraAlpha << 24) | 0x0080DEEA);
        } else {
            long seg = now / 90;
            java.util.Random rng = new java.util.Random(seg * 31 + (int) (brainHp * 100));
            for (int i = 0; i < 10; i++) {
                if (rng.nextFloat() > 0.4f + brainHp) continue;
                int ang = rng.nextInt(360);
                int rr = (int) (auraBase + rng.nextInt(3));
                int px = brainCx + (int) (rr * Math.cos(Math.toRadians(ang)));
                int py = brainCy + (int) (rr * Math.sin(Math.toRadians(ang)));
                g.fill(px, py, px + 2, py + 2, ((int) (60 + 120 * rng.nextFloat()) << 24) | 0x00FF6E40);
            }
        }

        int dimAlpha = (int) ((1.0f - volRatio) * 90);
        if (dimAlpha > 4) {
            g.fill(bx + BODY_OFFSET_X, by + BODY_OFFSET_Y,
                    bx + BODY_OFFSET_X + BODY_W, by + BODY_OFFSET_Y + BODY_H, (dimAlpha << 24) | 0x00000810);
            g.fill(bx + LEFT_LEG_OFFSET_X, by + LEFT_LEG_OFFSET_Y,
                    bx + LEFT_LEG_OFFSET_X + LEG_W, by + LEFT_LEG_OFFSET_Y + LEG_H, (dimAlpha << 24) | 0x00000810);
            g.fill(bx + RIGHT_LEG_OFFSET_X, by + RIGHT_LEG_OFFSET_Y,
                    bx + RIGHT_LEG_OFFSET_X + LEG_W, by + RIGHT_LEG_OFFSET_Y + LEG_H, (dimAlpha << 24) | 0x00000810);
            g.fill(bx + LEFT_ARM_OFFSET_X, by + LEFT_ARM_OFFSET_Y,
                    bx + LEFT_ARM_OFFSET_X + ARM_W, by + LEFT_ARM_OFFSET_Y + ARM_H, (dimAlpha << 24) | 0x00000810);
            g.fill(bx + RIGHT_ARM_OFFSET_X, by + RIGHT_ARM_OFFSET_Y,
                    bx + RIGHT_ARM_OFFSET_X + ARM_W, by + RIGHT_ARM_OFFSET_Y + ARM_H, (dimAlpha << 24) | 0x00000810);
        }

        if (data.isInCriticalState()) {
            int criticalRemaining = data.getCriticalTimer();
            float urgency = criticalRemaining <= 3 ? 1.0f : 0.55f;
            float redPulse = 0.5f + 0.5f * (float) Math.sin(now / (criticalRemaining <= 3 ? 90.0 : 160.0));
            int redAlpha = (int) (urgency * redPulse * 60);
            g.fill(bx + BODY_OFFSET_X - 2, by + BODY_OFFSET_Y - 2,
                    bx + BODY_OFFSET_X + BODY_W + 2, by + BODY_OFFSET_Y + BODY_H + 2,
                    (redAlpha << 24) | 0x00FF1744);
        }

        drawHemorrhageVisualization(g, data, bx, by, now);
    }

    private final java.util.List<int[]> hemorrhageDripsps = new java.util.ArrayList<>();
    private float bloodPoolDepth = 0.0f;

    private void drawHemorrhageVisualization(GuiGraphics g, MedicalData data, int bx, int by, long now) {
        java.util.List<float[]> bleeders = new java.util.ArrayList<>();
        MedicalData.Limb[] limbs = MedicalData.Limb.values();
        for (MedicalData.Limb limb : limbs) {
            MedicalData.LimbData ld = data.getLimb(limb);
            float rate = ld.getBleeding();
            if (rate <= 0) rate = ld.getInternalBleedingRate() > 0 ? 2.0f : 0.0f;
            if (rate <= 0) continue;
            int[] box = getLimbCenterBox(limb, bx, by);
            if (box == null) continue;
            bleeders.add(new float[]{box[0], box[1], box[0] + box[2], box[1] + box[3], rate});
        }
        if (bleeders.isEmpty()) {
            bloodPoolDepth = Math.max(0.0f, bloodPoolDepth - 0.003f);
        } else {
            float maxRate = 0.0f;
            for (float[] b : bleeders) maxRate = Math.max(maxRate, b[4]);
            bloodPoolDepth = Math.min(1.0f, bloodPoolDepth + maxRate * 0.0012f);
        }

        if (!bleeders.isEmpty() && hemorrhageDripsps.size() < 24) {
            for (float[] b : bleeders) {
                float dripChance = Math.min(0.4f, b[4] * 0.08f);
                if ((float) Math.random() < dripChance) {
                    float wx = b[0] + (float) Math.random() * (b[2] - b[0]);
                    float wy = b[1] + (b[3] - b[1]) * (0.4f + (float) Math.random() * 0.3f);
                    hemorrhageDripsps.add(new int[]{(int) wx, (int) wy, (int) b[3], 0});
                }
            }
        }

        java.util.Iterator<int[]> dit = hemorrhageDripsps.iterator();
        while (dit.hasNext()) {
            int[] d = dit.next();
            d[3] += 3;
            int dropY = d[1] + d[3];
            if (dropY >= d[2]) {
                dit.remove();
                continue;
            }
            g.fill(d[0] - 1, dropY - 2, d[0] + 2, dropY + 2, 0xCCB71C1C);
            g.fill(d[0], dropY - 5, d[0] + 1, dropY - 2, 0x558E0E0E);
        }

        if (bloodPoolDepth > 0.02f) {
            for (float[] b : bleeders) {
                int poolW = (int) ((b[2] - b[0]) * (0.4f + bloodPoolDepth * 0.55f));
                int px1 = (int) (b[0] + (b[2] - b[0] - poolW) / 2);
                int poolH = Math.max(1, (int) (bloodPoolDepth * 6));
                int floorY = (int) b[3];
                for (int layer = 0; layer < poolH; layer++) {
                    int shrink = (int) ((poolH - layer) * 1.2f);
                    int alpha = 0xC0 - layer * 12;
                    g.fill(px1 + shrink, floorY - layer - 1, px1 + poolW - shrink, floorY - layer,
                            (alpha << 24) | 0x00610B0B);
                }
            }
        }

        for (float[] b : bleeders) {
            float rateN = Math.min(1.0f, b[4] / 5.0f);
            int glowA = (int) (30 + rateN * 50);
            int x1 = (int) b[0], y1 = (int) b[1], x2 = (int) b[2], y2 = (int) b[3];
            g.fill(x1 - 1, y1 - 1, x2 + 1, y1, (glowA << 24) | 0x00FF1744);
            g.fill(x1 - 1, y2, x2 + 1, y2 + 1, (glowA << 24) | 0x00FF1744);
            g.fill(x1 - 1, y1, x1, y2, (glowA << 24) | 0x00FF1744);
            g.fill(x2, y1, x2 + 1, y2, (glowA << 24) | 0x00FF1744);
        }
    }

    private int countLimbIssues(MedicalData data) {
        int count = 0;
        for (MedicalData.Limb limb : MedicalData.Limb.values()) {
            MedicalData.LimbData ld = data.getLimb(limb);
            if (ld.getBleeding() > 0 || ld.getInternalBleedingRate() > 0 || ld.hasFracture()
                    || ld.hasDislocation() || ld.getInfection() > 0) {
                count++;
            }
        }
        return count;
    }

    private void drawFloatingVitals(GuiGraphics g, MedicalData data, int bodyX, int bodyY, long now) {
        int leftX = bodyX - 48;
        int rightX = bodyX + TOTAL_W + 8;

        float bob1 = (float) Math.sin(now / 800.0) * 2.0f;
        float bob2 = (float) Math.sin(now / 900.0 + 1.5) * 2.0f;
        float bob3 = (float) Math.sin(now / 700.0 + 3.0) * 2.0f;

        drawMicroReadout(g, leftX, bodyY + 20 + (int) bob1, "HR",
                String.valueOf((int) sprHeartRate.val), getHeartRateColor(data.getHeartRate()));
        drawMicroReadout(g, leftX, bodyY + 55 + (int) bob2, "O₂",
                (int) sprOxygen.val + "%", getOxygenColor(data.getBloodOxygen()));

        drawMicroReadout(g, rightX, bodyY + 20 + (int) bob3, "BP",
                (int) sprSystolic.val + "/" + (int) sprDiastolic.val,
                getBPColor(data.getMeanArterialPressure(), data.getSystolicBP()));
    }

    private void drawMicroReadout(GuiGraphics g, int x, int y, String label, String value, int color) {
        g.fill(x, y, x + 40, y + 22, 0x88060D14);
        g.fill(x, y, x + 2, y + 22, HudColors.withAlpha(color, 0.7f));
        g.drawString(this.font, label, x + 4, y + 2, 0xFF546E7A, false);

        g.pose().pushPose();
        g.pose().translate(x + 4, y + 11, 0);
        g.pose().scale(0.85f, 0.85f, 1f);
        g.drawString(this.font, value, 0, 0, color, false);
        g.pose().popPose();
    }

    private void drawConnectionLine(GuiGraphics g, MedicalData data, long now) {
        if (connectionLineLimb == null || connectionLineProgress < 0.01f) return;

        int bodyCardX = 24;
        int bodyCardY = HUD_BAR_H + 14;
        int bodyCardW = 220;
        int bodyCardH = this.height - HUD_BAR_H - 50;

        int startX = bodyCardX + (bodyCardW - TOTAL_W) / 2 + 10;
        int startY = bodyCardY + (bodyCardH - TOTAL_H) / 2 + 5;

        int limbCx, limbCy;
        switch (connectionLineLimb) {
            case HEAD:      limbCx = startX + HEAD_OFFSET_X + HEAD_SIZE / 2; limbCy = startY + HEAD_OFFSET_Y + HEAD_SIZE / 2; break;
            case CHEST:     limbCx = startX + BODY_OFFSET_X + BODY_W / 2;   limbCy = startY + BODY_OFFSET_Y + BODY_H / 2; break;
            case LEFT_ARM:  limbCx = startX + LEFT_ARM_OFFSET_X + ARM_W / 2; limbCy = startY + LEFT_ARM_OFFSET_Y + ARM_H / 2; break;
            case RIGHT_ARM: limbCx = startX + RIGHT_ARM_OFFSET_X + ARM_W / 2; limbCy = startY + RIGHT_ARM_OFFSET_Y + ARM_H / 2; break;
            case LEFT_LEG:  limbCx = startX + LEFT_LEG_OFFSET_X + LEG_W / 2; limbCy = startY + LEFT_LEG_OFFSET_Y + LEG_H / 2; break;
            case RIGHT_LEG: limbCx = startX + RIGHT_LEG_OFFSET_X + LEG_W / 2; limbCy = startY + RIGHT_LEG_OFFSET_Y + LEG_H / 2; break;
            default: return;
        }

        int panelX = 260;
        int targetX = panelX;
        int targetY = bodyCardY + 40;
        int midX = limbCx + (targetX - limbCx) / 2;

        float p = connectionLineProgress;
        int lineColor = HudColors.withAlpha(HudColors.CYAN, 0.5f * p);
        int dotColor = HudColors.withAlpha(HudColors.CYAN, 0.8f * p);

        int currentEndX = limbCx + (int) ((midX - limbCx) * Math.min(1f, p * 2f));
        drawDottedHLine(g, limbCx, limbCy, currentEndX, lineColor, now);

        if (p > 0.5f) {
            float p2 = (p - 0.5f) * 2f;
            int curX = midX + (int) ((targetX - midX) * p2);
            int curY = limbCy + (int) ((targetY - limbCy) * p2);
            drawDottedLine(g, midX, limbCy, curX, curY, lineColor, (int) ((now / 40) % 4));
        }

        g.fill(limbCx - 2, limbCy - 2, limbCx + 3, limbCy + 3, dotColor);
        g.fill(limbCx - 1, limbCy - 1, limbCx + 2, limbCy + 2, 0xCCFFFFFF);
    }

    private void drawDottedHLine(GuiGraphics g, int x0, int y, int x1, int color, long now) {
        int offset = (int) ((now / 50) % 6);
        int sx = Math.min(x0, x1);
        int ex = Math.max(x0, x1);
        for (int x = sx + offset; x < ex; x += 6) {
            g.fill(x, y, Math.min(x + 3, ex), y + 1, color);
        }
    }

    private void drawDottedLine(GuiGraphics g, int x0, int y0, int x1, int y1, int color, int offset) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        int steps = (int) (dist / 4.0f);
        if (steps <= 0) return;
        for (int i = offset % 4; i < steps; i += 4) {
            float t = (float) i / steps;
            int px = x0 + (int) (dx * t);
            int py = y0 + (int) (dy * t);
            g.fill(px, py, px + 2, py + 2, color);
        }
    }

    // ============================================================
    // 九、肢体渲染
    // ============================================================
    private void drawLimb(GuiGraphics graphics, MedicalData data, MedicalData.Limb limb,
                          int x, int y, int w, int h, int mouseX, int mouseY, long now) {
        MedicalData.LimbData ld = data.getLimb(limb);
        int baseColor = getLimbColor(ld);
        int health = getLimbHealthPercent(ld);

        boolean isHovered = (limb == hoveredLimb);
        boolean isSelected = (limb == selectedLimb);
        float anim = limbHoverProgress[limb.ordinal()];

        float dimFactor = (selectedLimb != null && !isSelected && !isHovered) ? 0.45f : 1.0f;

        int expand = (int) (anim * 3.0f);
        int drawX = x - expand;
        int drawY = y - expand;
        int drawW = w + expand * 2;
        int drawH = h + expand * 2;

        if (anim > 0.05f) {
            int glowAlpha = (int) (anim * 25 * dimFactor);
            graphics.fill(drawX - 3, drawY - 3, drawX + drawW + 3, drawY + drawH + 3,
                    (glowAlpha << 24) | (baseColor & 0x00FFFFFF));
            graphics.fill(drawX - 1, drawY - 1, drawX + drawW + 1, drawY + drawH + 1,
                    (glowAlpha * 2 << 24) | (baseColor & 0x00FFFFFF));
        }

        float baseFill = (0.35f + 0.40f * anim) * dimFactor;
        int r = Math.min(4, drawH / 2);
        for (int row = 0; row < r; row++) {
            float intensity = baseFill * (0.8f + 0.2f * (float) Math.sin((float) row / drawH * Math.PI));
            int inset = r - (int) Math.round(Math.sqrt(Math.max(0, r * r - (r - row - 0.5) * (r - row - 0.5))));
            graphics.fill(drawX + inset, drawY + row, drawX + drawW - inset, drawY + row + 1, HudColors.withAlpha(baseColor, intensity));
        }

        int midH = drawH - 2 * r;
        if (midH > 0) {
            int halfMid = midH / 2;
            graphics.fill(drawX, drawY + r, drawX + drawW, drawY + r + halfMid, HudColors.withAlpha(baseColor, baseFill * 0.95f));
            graphics.fill(drawX, drawY + r + halfMid, drawX + drawW, drawY + drawH - r, HudColors.withAlpha(baseColor, baseFill * 1.0f));
        }

        for (int row = drawH - r; row < drawH; row++) {
            float intensity = baseFill * (0.8f + 0.2f * (float) Math.sin((float) row / drawH * Math.PI));
            int d = row - (drawH - r);
            int inset = r - (int) Math.round(Math.sqrt(Math.max(0, r * r - (d + 0.5) * (d + 0.5))));
            graphics.fill(drawX + inset, drawY + row, drawX + drawW - inset, drawY + row + 1, HudColors.withAlpha(baseColor, intensity));
        }

        int dotAlpha = (int) (0.08f * dimFactor * 255);
        int dotColor = (dotAlpha << 24) | 0x00FFFFFF;
        for (int gy = drawY + 3; gy < drawY + drawH - 2; gy += 4) {
            for (int gx = drawX + 3; gx < drawX + drawW - 2; gx += 4) {
                graphics.fill(gx, gy, gx + 1, gy + 1, dotColor);
            }
        }

        int hBarH = 2;
        int hBarY = drawY + drawH - hBarH - 2;
        int hBarX = drawX + 3;
        int hBarW = drawW - 6;
        graphics.fill(hBarX, hBarY, hBarX + hBarW, hBarY + hBarH, 0x441A2A33);
        int filledW = (int) (hBarW * (health / 100f));
        int hpColor = getHealthColor(health);
        graphics.fill(hBarX, hBarY, hBarX + filledW, hBarY + hBarH, HudColors.withAlpha(hpColor, 0.8f * dimFactor));

        int borderColor;
        if (isSelected) {
            float breath = 0.6f + 0.4f * (float) Math.sin(selectedPulsePhase);
            borderColor = HudColors.withAlpha(HudColors.CYAN, breath);
        } else if (isHovered) {
            borderColor = 0xFFFFFFFF;
        } else {
            borderColor = HudColors.withAlpha(baseColor, 0.7f * dimFactor);
        }
        HudRenderHelper.drawSciFiBorder(graphics, drawX, drawY, drawW, drawH, borderColor);

        if (isSelected) {
            float breath = 0.5f + 0.5f * (float) Math.sin(selectedPulsePhase);
            HudRenderHelper.drawCardCorners(graphics, drawX - 3, drawY - 3, drawW + 6, drawH + 6, 4,
                    HudColors.withAlpha(HudColors.CYAN, 0.4f + 0.6f * breath));
        }

        if (ld.getBleeding() > 0) {
            float bleedIntensity = Math.min(1f, ld.getBleeding() / 300f);
            for (int s = 0; s < 2; s++) {
                float phase = ((now + s * 500) % 1400) / 1400f;
                int flowY = drawY + (int) (phase * drawH);
                int flowEnd = Math.min(flowY + (int) (6 + bleedIntensity * 8), drawY + drawH);
                graphics.fill(drawX + drawW - 2, flowY, drawX + drawW, flowEnd,
                        HudColors.withAlpha(0xFFFF1744, 0.5f + bleedIntensity * 0.5f));
            }
        }

        if (ld.getInternalBleedingRate() > 0) {
            float intensity = Math.min(1f, ld.getInternalBleedingRate() / 200f);
            float ip = 0.5f + 0.5f * (float) Math.sin(now / 200.0);
            int internalA = (int) ((0.06f + 0.12f * ip) * intensity * 255);
            graphics.fill(drawX + 3, drawY + 3, drawX + drawW - 3, drawY + drawH - 3,
                    (internalA << 24) | 0x00FF1744);
        }

        if (ld.hasFracture()) {
            int fx = drawX + drawW / 2;
            float flash = 0.6f + 0.4f * (float) Math.sin(now / 250.0);
            int crackColor = HudColors.withAlpha(0xFFFF1744, flash);
            for (int i = 0; i < drawH - 6; i += 2) {
                int px = fx + (int) (Math.sin(i * 0.7) * 3);
                graphics.fill(px, drawY + 3 + i, px + 1, drawY + 5 + i, crackColor);
                graphics.fill(px - 1, drawY + 3 + i, px, drawY + 5 + i, HudColors.withAlpha(0xFFFF1744, 0.15f * flash));
                graphics.fill(px + 1, drawY + 3 + i, px + 2, drawY + 5 + i, HudColors.withAlpha(0xFFFF1744, 0.15f * flash));
            }
        }

        if (isHovered || isSelected) {
            String label = getLimbFullName(limb) + "  " + health + "%";
            int textW = this.font.width(label);
            int tagX = drawX + drawW / 2 - textW / 2 - 4;
            int tagY = drawY - 16;
            int tagW = textW + 8;
            int tagH = 14;

            drawRoundedRect(graphics, tagX, tagY, tagW, tagH, 3, 0xDD060B10);
            graphics.renderOutline(tagX, tagY, tagW, tagH, borderColor);
            int triX = drawX + drawW / 2;
            graphics.fill(triX - 1, tagY + tagH, triX + 2, tagY + tagH + 2, 0xDD060B10);
            graphics.drawString(this.font, label, tagX + 4, tagY + 3, borderColor, false);

            if (isHovered && !isSelected) {
                String hint = "CLICK ▸";
                int hintW = this.font.width(hint);
                graphics.drawString(this.font, hint, drawX + drawW / 2 - hintW / 2,
                        drawY + drawH / 2 - 4, 0x88FFFFFF, false);
            }
        }

        if (ld.isTourniqueted()) {
            int bandX = drawX + drawW / 2 - 3;
            int bandY = drawY + 3;
            int bandH = drawH - 6;
            graphics.fill(bandX, bandY, bandX + 5, bandY + bandH, 0xAAFF9100);
            graphics.fill(bandX - 1, bandY, bandX + 6, bandY + 2, 0xFFFFFFFF);
            graphics.fill(bandX - 1, bandY + bandH - 2, bandX + 6, bandY + bandH, 0xFFFFFFFF);
        }

        if (ld.hasSplint()) {
            graphics.fill(drawX + drawW - 6, drawY + 4, drawX + drawW - 3, drawY + drawH - 4, 0xFF00E676);
            graphics.fill(drawX + drawW - 7, drawY + 4, drawX + drawW - 2, drawY + 6, 0xFF00E676);
            graphics.fill(drawX + drawW - 7, drawY + drawH - 6, drawX + drawW - 2, drawY + drawH - 4, 0xFF00E676);
        }
    }

    // ============================================================
    // 十、右侧详情面板
    // ============================================================
    private void drawDetailPanel(GuiGraphics graphics, MedicalData data, int mouseX, int mouseY,
                                 float deltaSeconds, long now) {
        int panelX = 260;
        int panelY = HUD_BAR_H + 14;
        int panelWidth = this.width - panelX - 24;
        int panelHeight = this.height - HUD_BAR_H - 50;

        if (panelWidth < 100) return;

        if (selectedLimb == MedicalData.Limb.HEAD) {
            String cardTitle = "CEREBRAL SCAN // 3D 大脑全息扫描";
            drawCyberCard(graphics, panelX, panelY, panelWidth, panelHeight, cardTitle, 0xFF00E5FF);
            brainScanRenderer.render(graphics, this.font, panelX, panelY, panelWidth, panelHeight, data, deltaSeconds);
            return;
        }

        String cardTitle = selectedLimb != null
                ? "DIAGNOSIS // " + getLimbFullName(selectedLimb) + " " + tr("medical.detail.title")
                : "SYSTEM OVERVIEW // 全身生理概览";

        drawCyberCard(graphics, panelX, panelY, panelWidth, panelHeight, cardTitle, 0xFF00E5FF);

        if (selectedLimb != null) {
            int health = getLimbHealthPercent(data.getLimb(selectedLimb));
            int hc = getHealthColor(health);
            float hFrac = health / 100f;
            int lineW = (int) ((panelWidth - 40) * hFrac);
            graphics.fill(panelX + 12, panelY + 22, panelX + 12 + lineW, panelY + 23, hc);
            if (lineW > 2) {
                graphics.fill(panelX + 12 + lineW - 1, panelY + 21, panelX + 12 + lineW + 1, panelY + 24, 0xCCFFFFFF);
            }
        }

        int contentX = panelX + 16;
        int contentW = panelWidth - 32;
        int lineY = panelY + 30;

        if (selectedLimb != null) {
            lineY = drawLimbDetail(graphics, data, selectedLimb, contentX, lineY, contentW, now);
        } else {
            lineY = drawSystemOverview(graphics, data, contentX, lineY, contentW, now);
        }

        int ecgY = Math.max(lineY + 8, panelY + panelHeight - 110);
        int ecgHeight = Math.min(95, (panelY + panelHeight - 12) - ecgY);
        if (ecgHeight > 45) {
            drawDualOscilloscope(graphics, contentX, ecgY, contentW, ecgHeight, data);
        }
    }

    // ============================================================
    // 十一、选中肢体详情
    // ============================================================
    private int drawLimbDetail(GuiGraphics g, MedicalData data, MedicalData.Limb limb, int x, int y, int w, long now) {
        MedicalData.LimbData ld = data.getLimb(limb);
        int health = getLimbHealthPercent(ld);
        int hc = getHealthColor(health);

        g.drawString(this.font, "INTEGRITY STATUS", x, y, 0xFF546E7A, false);

        String hStr = health + "%";
        g.pose().pushPose();
        g.pose().translate(x + w - 60, y - 4, 0);
        g.pose().scale(1.5f, 1.5f, 1f);
        int hw = this.font.width(hStr);
        g.drawString(this.font, hStr, -hw, 0, hc, false);
        g.pose().popPose();

        y += 14;
        drawFancyProgressBar(g, x, y, w, 10, health / 100f, hc, now);
        y += 20;

        if (ld.getBleeding() > 0) {
            y = drawVisualRow(g, x, y, w, "出血 BLEED", ld.getBleeding() + " mL/min",
                    Math.min(1f, ld.getBleeding() / 500f), getBleedingColor(ld.getBleeding()), VIZ_FLOW);
        }
        if (ld.getInternalBleedingRate() > 0) {
            y = drawVisualRow(g, x, y, w, "内出血 INTERNAL", ld.getInternalBleedingRate() + " mL/min",
                    Math.min(1f, ld.getInternalBleedingRate() / 300f), 0xFFFF1744, VIZ_FLOW);
        }
        y = drawVisualRow(g, x, y, w, "疼痛 PAIN", ld.getPainLevel() + " / 10",
                ld.getPainLevel() / 10f, getPainColor(ld.getPainLevel()), VIZ_SEGMENT);
        y = drawVisualRow(g, x, y, w, "感染 INFECTION", ld.getInfection() + "%",
                ld.getInfection() / 100f, getInfectionColor(ld.getInfection()), VIZ_SEGMENT);

        y += 6;
        g.drawString(this.font, "STATUS FLAGS", x, y, 0xFF546E7A, false);
        y += 12;

        int pillX = x;
        int pillY = y;
        int rowEnd = x + w;
        if (ld.isAmputated()) {
            // 已截肢：最高优先级红色药丸，覆盖其余状态
            drawStatusPill(g, pillX, pillY, tr("medical.status.amputated"), true, 0xFFFF1744);
            return pillY + 22;
        }
        pillX += drawStatusPill(g, pillX, pillY, tr("medical.status.bandaged"), ld.isBandaged(), 0xFF00E676);
        if (ld.isTourniqueted()) {
            if (pillX + 60 > rowEnd) { pillX = x; pillY += 18; }
            pillX += drawStatusPill(g, pillX, pillY, tr("medical.status.tourniquet"), true, 0xFFFF9100);
        }
        if (ld.hasFracture()) {
            String fracTxt = ld.isOpenFracture() ? tr("medical.status.open_fracture")
                    : (ld.hasSplint() ? tr("medical.status.fracture_splinted") : tr("medical.status.fracture"));
            if (pillX + 80 > rowEnd) { pillX = x; pillY += 18; }
            pillX += drawStatusPill(g, pillX, pillY, fracTxt, true, 0xFFFF1744);
        }
        if (ld.hasDislocation()) {
            if (pillX + 70 > rowEnd) { pillX = x; pillY += 18; }
            pillX += drawStatusPill(g, pillX, pillY, tr("medical.status.dislocation"), true, 0xFFFFD600);
        }
        if (ld.getInfection() > 0) {
            String infTxt = ld.getInfection() <= 25 ? tr("medical.status.mild_infection")
                    : ld.getInfection() <= 50 ? tr("medical.status.moderate_infection")
                    : ld.getInfection() <= 75 ? tr("medical.status.severe_infection")
                    : tr("medical.status.critical_infection");
            if (pillX + 80 > rowEnd) { pillX = x; pillY += 18; }
            pillX += drawStatusPill(g, pillX, pillY, infTxt, true, 0xFFFF1744);
        }
        if (!ld.isBandaged() && !ld.isTourniqueted() && !ld.hasFracture()
                && !ld.hasDislocation() && ld.getInfection() <= 0) {
            drawStatusPill(g, pillX, pillY, tr("medical.status.healthy"), true, 0xFF00E676);
        }
        y = pillY + 22;

        if (limb == MedicalData.Limb.CHEST) {
            y = drawChestExtra(g, data, x, y, w, now);
        }

        return y;
    }

    private int drawChestExtra(GuiGraphics g, MedicalData data, int x, int y, int w, long now) {
        drawSectionDivider(g, x, y, w);
        y += 8;
        g.drawString(this.font, "CARDIOPULMONARY METRICS", x, y, 0xFF546E7A, false);
        y += 13;

        int spo2 = data.getBloodOxygen();
        y = drawVisualRow(g, x, y, w, "血氧 SpO₂", spo2 + "%",
                spo2 / 100f, getOxygenColor(spo2), VIZ_PROGRESS);

        int rr = data.getRespirationRate();
        y = drawVisualRow(g, x, y, w, "呼吸 RESP", rr + " /min",
                Mth.clamp(rr / 40f, 0f, 1f), 0xFFE0E0E0, VIZ_PROGRESS);

        int bv = data.getBloodVolume();
        int mbv = data.getMaxBloodVolume();
        y = drawVisualRow(g, x, y, w, "血容量 VOL", bv + " / " + mbv + " mL",
                Mth.clamp((float) bv / Math.max(1, mbv), 0f, 1f), getBloodVolumeColor(bv, mbv), VIZ_PROGRESS);

        float chestBlood = data.getChestBloodAccumulation();
        if (chestBlood > 0) {
            y = drawVisualRow(g, x, y, w, "胸腔积血 HEMO", (int) chestBlood + " / 2000 mL",
                    Mth.clamp(chestBlood / 2000f, 0f, 1f), HudColors.DANGER, VIZ_FLOW);
        }

        int hemoLevel = data.getHemothoraxLevel();
        if (hemoLevel > 0) {
            y = drawVisualRow(g, x, y, w, "血胸 HEMOTHORAX", tr("medical.hemothorax.level." + hemoLevel),
                    hemoLevel / 4f, getHemothoraxLevelColor(hemoLevel), VIZ_SEGMENT);
        }

        float lungContusion = data.getLungContusion();
        if (lungContusion > 0) {
            int color = lungContusion > 60 ? 0xFFFF1744 : (lungContusion > 30 ? 0xFFFF9100 : 0xFFFFD600);
            y = drawVisualRow(g, x, y, w, "肺挫伤 CONTUSION", String.format("%.1f%%", lungContusion),
                    Mth.clamp(lungContusion / 100f, 0f, 1f), color, VIZ_PROGRESS);
        }

        y = drawVisualRow(g, x, y, w, "心输出量 CO",
                String.format("%.2f L/min", sprCardiacOutput.val),
                Mth.clamp(sprCardiacOutput.val / 10f, 0f, 1f), 0xFFFFFFFF, VIZ_PROGRESS);

        y = drawVisualRow(g, x, y, w, "外周阻力 SVR",
                String.format("×%.2f", data.getPeripheralResistance()),
                Mth.clamp(data.getPeripheralResistance() / 2f, 0f, 1f), 0xFFFFFFFF, VIZ_PROGRESS);

        if (data.getSirsLevel() > 0) {
            y = drawVisualRow(g, x, y, w, "SIRS", data.getSirsLevel() + " / 4",
                    data.getSirsLevel() / 4f, 0xFFFF1744, VIZ_SEGMENT);
        }

        // 中毒条：全身毒素水平（0~100），轻/中/重度阈值取自配置
        float toxin = data.getToxinValue();
        if (toxin > 0.1f) {
            float midTh = MedicalConfig.TOXIN_MID_THRESHOLD.get().floatValue();
            float sevTh = MedicalConfig.TOXIN_SEVERE_THRESHOLD.get().floatValue();
            int toxinColor = toxin >= sevTh ? 0xFFFF1744
                    : (toxin >= midTh ? 0xFFFF9100 : 0xFFAEEA00);
            String toxinTxt = String.format("%.0f/100 ", toxin)
                    + (toxin >= sevTh ? "重度" : (toxin >= midTh ? "中度" : "轻度"));
            y = drawVisualRow(g, x, y, w, "毒素 TOXIN", toxinTxt,
                    Mth.clamp(toxin / 100f, 0f, 1f), toxinColor, VIZ_SEGMENT);
        }

        if (data.isInVFib()) {
            y += 4;
            drawAlertBanner(g, x, y, w, tr("medical.warning.vfib"), now);
            y += 18;
        }
        if (data.isCardiacArrest()) {
            drawAlertBanner(g, x, y, w, tr("medical.warning.cardiac_arrest"), now);
            y += 18;
        }
        return y;
    }

    private void drawAlertBanner(GuiGraphics g, int x, int y, int w, String text, long now) {
        float flash = 0.5f + 0.5f * (float) Math.sin(now / 130.0);
        int bgA = (int) ((0.35f + 0.45f * flash) * 255);
        g.fill(x, y, x + w, y + 14, (bgA << 24) | 0x00FF1744);
        if ((now / 400) % 2 == 0) {
            g.renderOutline(x, y, w, 14, 0xFFFF1744);
        }
        HudRenderHelper.drawGlowingString(g, this.font, "⚠ " + text, x + 6, y + 3, 0xFFFFFFFF, HudColors.DANGER);
    }

    // ============================================================
    // 十二、全身概览
    // ============================================================
    private int drawSystemOverview(GuiGraphics g, MedicalData data, int x, int y, int w, long now) {
        int gaugeR = 28;
        int cols = Math.min(4, (w + 10) / (gaugeR * 2 + 20));
        int spacing = w / Math.max(1, cols);
        int gcy = y + gaugeR + 8;

        int hr = (int) sprHeartRate.val;
        drawBigRingGauge(g, x + spacing / 2, gcy, gaugeR,
                Mth.clamp(sprHeartRate.val / 200f, 0f, 1f), getHeartRateColor(hr),
                String.valueOf(hr), "BPM", "心率 HR");

        if (cols >= 2) {
            int spo2 = (int) sprOxygen.val;
            drawBigRingGauge(g, x + spacing + spacing / 2, gcy, gaugeR,
                    Mth.clamp(sprOxygen.val / 100f, 0f, 1f), getOxygenColor(spo2),
                    String.valueOf(spo2), "%", "血氧 SpO₂");
        }

        if (cols >= 3) {
            int con = (int) sprConsciousness.val;
            drawBigRingGauge(g, x + spacing * 2 + spacing / 2, gcy, gaugeR,
                    Mth.clamp(sprConsciousness.val / 100f, 0f, 1f), getConsciousnessColor(con),
                    String.valueOf(con), "%", "意识 CONS");
        }

        if (cols >= 4) {
            int brain = (int) sprBrainHealth.val;
            drawBigRingGauge(g, x + spacing * 3 + spacing / 2, gcy, gaugeR,
                    Mth.clamp(sprBrainHealth.val / 100f, 0f, 1f), getHealthColor(brain),
                    String.valueOf(brain), "%", "脑健康 BRAIN");
        }

        int nextY = gcy + gaugeR + 24;
        drawSectionDivider(g, x, nextY, w);
        nextY += 8;

        g.drawString(this.font, "BLOOD VOLUME", x, nextY, 0xFF546E7A, false);
        nextY += 12;

        int bv = (int) sprBloodVolume.val;
        int mbv = data.getMaxBloodVolume();
        int bvc = getBloodVolumeColor(bv, mbv);

        int tankX = x + 4;
        int tankY = nextY + 2;
        int tankW = 44;
        int tankH = 72;
        drawFluidTank(g, tankX, tankY, tankW, tankH, bv, mbv, bvc);

        int infoX = tankX + tankW + 22;

        g.pose().pushPose();
        g.pose().translate(infoX, tankY + 6, 0);
        g.pose().scale(2.0f, 2.0f, 1f);
        g.drawString(this.font, String.valueOf(bv), 0, 0, bvc, false);
        g.pose().popPose();

        int bvTxtW = (int) (this.font.width(String.valueOf(bv)) * 2.0f);
        g.drawString(this.font, "/ " + mbv + " mL", infoX + bvTxtW + 6, tankY + 18, 0xFF546E7A, false);

        int barW = Math.min(280, w - (infoX - x) - 10);
        drawFancyProgressBar(g, infoX, tankY + 34, barW, 8,
                Mth.clamp((float) bv / Math.max(1, mbv), 0f, 1f), bvc, now);

        int volPct = bv * 100 / Math.max(1, mbv);
        String volStatus = volPct > 70 ? "NOMINAL" : volPct > 50 ? "LOW" : "CRITICAL";
        int volStatusCol = volPct > 70 ? 0xFF00E676 : volPct > 50 ? 0xFFFF9100 : 0xFFFF1744;
        drawStatusPill(g, infoX, tankY + 48, volStatus, true, volStatusCol);

        String conText = getConsciousnessText(data.getConsciousness());
        g.drawString(this.font, "意识: " + conText, infoX, tankY + 66,
                getConsciousnessColor(data.getConsciousness()), false);

        nextY = tankY + tankH + 14;
        drawSectionDivider(g, x, nextY, w);
        nextY += 8;

        float p = 0.5f + 0.5f * (float) Math.sin(now / 500.0);
        int hintColor = HudColors.withAlpha(0xFF00E5FF, 0.4f + 0.6f * p);
        g.drawString(this.font, "◆ 点击左侧身体部位查看创伤详情", x, nextY, hintColor, false);
        nextY += 16;

        return nextY;
    }

    private void drawSectionDivider(GuiGraphics g, int x, int y, int w) {
        g.fill(x, y, x + w, y + 1, 0x2200ADB5);
        int midX = x + w / 2;
        int hlW = w / 4;
        g.fill(midX - hlW, y, midX + hlW, y + 1, 0x4400E5FF);
    }

    private void drawFancyProgressBar(GuiGraphics g, int x, int y, int w, int h, float t, int color, long now) {
        t = Mth.clamp(t, 0f, 1f);
        g.fill(x, y, x + w, y + h, 0xFF0A1820);
        g.renderOutline(x, y, w, h, 0xFF1A3040);

        int fillW = (int) (w * t);
        if (fillW <= 0) return;

        g.fill(x + 1, y + 1, x + fillW - 1, y + h - 1, HudColors.withAlpha(color, 0.7f));
        g.fill(x + 1, y + 1, x + fillW - 1, y + 2, HudColors.withAlpha(color, 0.95f));

        float phase = (now % 1500) / 1500f;
        int hlX = x + 1 + (int) (phase * (fillW - 12));
        int hlW = 10;
        if (hlX + hlW < x + fillW) {
            g.fill(hlX, y + 1, hlX + hlW, y + h - 1, HudColors.withAlpha(0xFFFFFFFF, 0.12f));
        }

        g.fill(x + fillW - 2, y, x + fillW, y + h, 0xBBFFFFFF);
        g.fill(x + 1, y - 1, x + fillW - 1, y, HudColors.withAlpha(color, 0.15f));
        g.fill(x + 1, y + h, x + fillW - 1, y + h + 1, HudColors.withAlpha(color, 0.15f));
    }

    private void drawFluidTank(GuiGraphics g, int x, int y, int w, int h, int value, int max, int color) {
        float t = Mth.clamp((float) value / Math.max(1, max), 0f, 1f);
        int fillH = (int) (h * t);
        int fillY = y + h - fillH;

        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF0A1820);
        g.renderOutline(x - 1, y - 1, w + 2, h + 2, 0xFF1A3040);
        g.fill(x, y, x + w, y + h, 0xFF060E14);

        if (fillH > 0) {
            for (int row = 0; row < fillH; row += 3) {
                int hChunk = Math.min(3, fillH - row);
                float rowT = (float) row / fillH;
                float alpha = 0.40f + 0.25f * rowT;
                g.fill(x + 1, fillY + row, x + w - 1, fillY + row + hChunk,
                        HudColors.withAlpha(color, alpha));
            }

            long now = System.currentTimeMillis();
            float phase = (now % 2500) / 2500f * (float) Math.PI * 2;
            for (int i = 0; i < w - 2; i++) {
                float wave = (float) Math.sin(i * 0.35f + phase) * 1.8f
                        + (float) Math.sin(i * 0.7f + phase * 1.3f) * 0.9f;
                int wy = fillY + (int) Math.round(wave);
                wy = Mth.clamp(wy, fillY, fillY + 7);
                g.fill(x + 1 + i, wy, x + 2 + i, wy + 3, HudColors.withAlpha(color, 0.95f));
            }
            g.fill(x + 2, fillY + 1, x + w / 3, fillY + 2, HudColors.withAlpha(0xFFFFFFFF, 0.15f));
        }

        for (int i = 1; i < 5; i++) {
            int ty = y + (h * i / 5);
            g.fill(x + w - 7, ty, x + w - 1, ty + 1, 0x44FFFFFF);
            String pct = (100 - i * 20) + "";
            g.pose().pushPose();
            g.pose().scale(0.6f, 0.6f, 1f);
            g.drawString(this.font, pct,
                    (int) ((x + w + 3) / 0.6f), (int) ((ty - 2) / 0.6f), 0x44FFFFFF, false);
            g.pose().popPose();
        }

        int[] marks = {70, 50, 30};
        int[] markCols = {0xFF00FF88, 0xFFFFD600, 0xFFFF1744};
        for (int i = 0; i < marks.length; i++) {
            int ty = y + h - (h * marks[i] / 100);
            g.fill(x - 3, ty, x + 4, ty + 1, markCols[i]);
        }
        g.renderOutline(x - 2, y - 2, w + 4, h + 4, 0x1800E5FF);
    }

    private int drawVisualRow(GuiGraphics g, int x, int y, int w, String label, String value,
                              float t, int color, int type) {
        final int rowH = 24;
        final int labelW = Math.min(140, w / 3);
        final int valueW = Math.min(100, w / 4);
        int barX = x + labelW;
        int barW = Math.max(30, w - labelW - valueW - 8);
        t = Mth.clamp(t, 0f, 1f);

        g.fill(x, y, x + w, y + rowH - 2, 0x1A121F2C);
        g.fill(x, y, x + 3, y + rowH - 2, color);
        g.drawString(this.font, label, x + 8, y + 7, 0xFFB0BEC5, false);

        int barY0 = y + 8;
        int barY1 = y + 14;
        long now = System.currentTimeMillis();

        if (type == VIZ_SEGMENT) {
            int segs = 10;
            int gap = 2;
            int segW = Math.max(2, (barW - (segs - 1) * gap) / segs);
            int lit = (int) Math.ceil(t * segs);
            for (int i = 0; i < segs; i++) {
                int sx = barX + i * (segW + gap);
                if (i < lit) {
                    g.fill(sx, barY0, sx + segW, barY0 + 3, color);
                    g.fill(sx, barY0 + 3, sx + segW, barY1, HudColors.withAlpha(color, 0.7f));
                    g.fill(sx, barY0, sx + segW, barY0 + 1, HudColors.withAlpha(color, 0.95f));
                } else {
                    g.fill(sx, barY0, sx + segW, barY1, 0xFF0A1820);
                    g.fill(sx, barY0, sx + segW, barY0 + 1, 0xFF142028);
                }
            }
        } else if (type == VIZ_FLOW) {
            g.fill(barX, barY0, barX + barW, barY1, 0xFF0A1820);
            int fillW = (int) (barW * t);
            if (fillW > 0) {
                g.fill(barX, barY0, barX + fillW, barY1, HudColors.withAlpha(color, 0.65f));
                for (int s = 0; s < 2; s++) {
                    float phase = ((now + s * 450) % 1000) / 1000f;
                    int hx = barX + (int) (phase * fillW);
                    if (hx > barX && hx + 6 < barX + fillW) {
                        g.fill(hx, barY0, hx + 4, barY1, HudColors.withAlpha(color, 0.4f));
                        g.fill(hx + 4, barY0, hx + 6, barY1, 0xAAFFFFFF);
                    }
                }
                g.fill(barX, barY0, barX + fillW, barY0 + 1, HudColors.withAlpha(color, 0.9f));
            }
        } else {
            g.fill(barX, barY0, barX + barW, barY1, 0xFF0A1820);
            int fillW = (int) (barW * t);
            if (fillW > 0) {
                g.fill(barX, barY0, barX + fillW, barY1, color);
                g.fill(barX, barY0, barX + fillW, barY0 + 1, HudColors.withAlpha(color, 0.95f));
                g.fill(barX + fillW - 1, barY0 - 1, barX + fillW + 1, barY1 + 1, 0xCCFFFFFF);
                g.fill(barX + fillW - 1, barY0 - 2, barX + fillW + 1, barY0, HudColors.withAlpha(color, 0.3f));
                g.fill(barX + fillW - 1, barY1, barX + fillW + 1, barY1 + 2, HudColors.withAlpha(color, 0.3f));
            }
        }

        int vw = this.font.width(value);
        g.drawString(this.font, value, x + w - vw - 6, y + 7, color, false);

        return y + rowH;
    }

    private int drawStatusPill(GuiGraphics g, int x, int y, String label, boolean active, int activeColor) {
        int tw = this.font.width(label);
        int w = tw + 14;
        int h = 14;

        if (active) {
            drawRoundedRect(g, x, y, w, h, 3, HudColors.withAlpha(activeColor, 0.22f));
            g.renderOutline(x, y, w, h, activeColor);
            g.fill(x + 1, y + 3, x + 4, y + h - 3, activeColor);
            g.drawString(this.font, label, x + 7, y + 3, 0xFFFFFFFF, false);
        } else {
            drawRoundedRect(g, x, y, w, h, 3, 0x180A1820);
            g.renderOutline(x, y, w, h, 0x44546E7A);
            g.drawString(this.font, label, x + 7, y + 3, 0xFF546E7A, false);
        }
        return w + 6;
    }

    private void drawRoundedRect(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) return;
        r = Math.max(0, Math.min(r, Math.min(w / 2, h / 2)));
        for (int i = 0; i < r; i++) {
            double d = r - i - 0.5;
            int inset = r - (int) Math.round(Math.sqrt(Math.max(0, r * r - d * d)));
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
            g.fill(x + inset, y + h - i - 1, x + w - inset, y + h - i, color);
        }
        g.fill(x, y + r, x + w, y + h - r, color);
    }

    public int drawDataLine(GuiGraphics graphics, String label, String value, int color, int x, int y, int width) {
        graphics.fill(x, y - 2, x + width, y + 12, 0x33121F2C);
        graphics.fill(x, y - 2, x + 2, y + 12, 0x8800ADB5);
        graphics.drawString(this.font, label, x + 6, y, 0xFFB0BEC5, false);

        int valW = this.font.width(value);
        int glowCol = (0x33 << 24) | (color & 0x00FFFFFF);
        graphics.drawString(this.font, value, x + width - valW - 7, y, glowCol, false);
        graphics.drawString(this.font, value, x + width - valW - 8, y, color, false);
        return y + 16;
    }

    // ============================================================
    // 十三、双通道示波器渲染
    // ============================================================
    private void drawDualOscilloscope(GuiGraphics graphics, int x, int y, int width, int height, MedicalData data) {
        graphics.fill(x, y, x + width, y + height, 0xEE021008);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 2, 0x1800FF88);

        final int bigGrid = 16;
        final int smallGrid = 4;
        int gridColorMinor = 0x1200FF66;
        for (int gx = x; gx < x + width; gx += smallGrid) graphics.fill(gx, y, gx + 1, y + height, gridColorMinor);
        for (int gy = y; gy < y + height; gy += smallGrid) graphics.fill(x, gy, x + width, gy + 1, gridColorMinor);

        int gridColorMajor = 0x3300FF88;
        for (int gx = x; gx < x + width; gx += bigGrid) graphics.fill(gx, y, gx + 1, y + height, gridColorMajor);
        for (int gy = y; gy < y + height; gy += bigGrid) graphics.fill(x, gy, x + width, gy + 1, gridColorMajor);

        int hr = data.getHeartRate();
        int baseLineColor = getECGColor(hr);
        int sampleCount = Math.min(BUFFER_SIZE, width - 4);
        float xStep = (float) (width - 4) / (float) sampleCount;

        int ecgH = (int) (height * 0.65f);
        drawECGPass(graphics, x, y, width, ecgH, sampleCount, xStep, baseLineColor, 0.08f, 3);
        drawECGPass(graphics, x, y, width, ecgH, sampleCount, xStep, baseLineColor, 0.25f, 1);
        drawECGPass(graphics, x, y, width, ecgH, sampleCount, xStep, 0xFFFFFFFF, 0.6f, 0);

        int ppgY = y + ecgH;
        int ppgH = height - ecgH - 4;
        drawWaveformTrace(graphics, x + 2, ppgY, sampleCount, xStep, ppgH, historyPPG, 0xFF00E5FF);

        int cursorBufIdx = (sweepIndex - 1 + BUFFER_SIZE) % BUFFER_SIZE;
        if (historyValid[cursorBufIdx]) {
            int cursorX = x + 2 + (int) ((sampleCount - 1) * xStep);
            int cursorY = y + 2 + (int) (historyY[cursorBufIdx] * (ecgH - 4));
            graphics.fill(cursorX - 4, cursorY - 4, cursorX + 5, cursorY + 5, 0x2200FFCC);
            graphics.fill(cursorX - 2, cursorY - 2, cursorX + 3, cursorY + 3, 0x5500FFCC);
            graphics.fill(cursorX - 1, cursorY - 1, cursorX + 2, cursorY + 2, 0xFFFFFFFF);
            graphics.fill(cursorX, y, cursorX + 1, cursorY - 4, 0x1A00FFCC);
            graphics.fill(cursorX, cursorY + 5, cursorX + 1, y + height, 0x1A00FFCC);
        }

        graphics.drawString(this.font, "LEAD II", x + 5, y + 4, 0xFF70A890, false);
        graphics.drawString(this.font, "PLETH PPG", x + 5, ppgY + 2, 0xFF3D9CA8, false);

        graphics.pose().pushPose();
        graphics.pose().translate(x + 55, y + 2, 0);
        graphics.pose().scale(1.2f, 1.2f, 1f);
        graphics.drawString(this.font, String.valueOf(hr), 0, 0, baseLineColor, false);
        graphics.pose().popPose();
        graphics.drawString(this.font, "BPM", x + 55 + (int) (this.font.width(String.valueOf(hr)) * 1.2f) + 4,
                y + 5, 0xFF70A890, false);

        int flashAlpha = (int) (rPeakFlash * 255);
        if (flashAlpha > 10) {
            int iconX = x + width - 18;
            int iconY = y + 5;
            float scale = 1.0f + rPeakFlash * 0.4f;
            graphics.pose().pushPose();
            graphics.pose().translate(iconX + 4, iconY + 4, 0);
            graphics.pose().scale(scale, scale, 1f);
            graphics.fill(-3, -3, 4, 4, (flashAlpha << 24) | 0x0033FF77);
            graphics.fill(-2, -2, 3, 3, (Math.min(255, flashAlpha + 50) << 24) | 0x0066FFAA);
            graphics.pose().popPose();
        }
        graphics.renderOutline(x + width - 15, y + 4, 10, 10, 0xFF0D5C3A);

        String bpText = data.getSystolicBP() + "/" + data.getDiastolicBP();
        graphics.drawString(this.font, "BP " + bpText, x + width - 88, y + 4, 0xFF88CCA8, false);

        String mapText = "MAP " + (int) data.getMeanArterialPressure();
        graphics.drawString(this.font, mapText, x + width - 68, y + height - 12, 0xFF558870, false);

        String rhythmStatus = getHeartRateStatus(hr);
        graphics.drawString(this.font, rhythmStatus, x + 5, y + height - 12, 0xFF66B28A, false);

        if (data.isInVFib()) {
            drawVfibStormOverlay(graphics, x, y, width, height, System.currentTimeMillis());
        }

        graphics.renderOutline(x, y, width, height, 0xFF0D5C3A);
        HudRenderHelper.drawCardCorners(graphics, x, y, width, height, 4, 0x6600FF88);
    }

    private void drawVfibStormOverlay(GuiGraphics graphics, int x, int y, int width, int height, long now) {
        int scroll = (int) ((now / 60) % 16);
        int gridCol = 0x30FF1744;
        for (int gx = x - scroll; gx < x + width; gx += 16) {
            if (gx >= x) graphics.fill(gx, y, gx + 1, y + height, gridCol);
        }
        for (int gy = y; gy < y + height; gy += 16) {
            graphics.fill(x, gy, x + width, gy + 1, 0x18D50000);
        }

        float bandPos = (now % 1400L) / 1400.0f;
        int bandX = x + (int) (bandPos * width);
        int bandW = 26;
        for (int i = 0; i < bandW; i++) {
            float fall = 1.0f - Math.abs(i - bandW / 2.0f) / (bandW / 2.0f);
            int a = (int) (fall * 36);
            if (a > 0) {
                graphics.fill(bandX - bandW / 2 + i, y + 1, bandX - bandW / 2 + i + 1, y + height - 1, (a << 24) | 0x00FF1744);
            }
        }

        boolean blinkOn = (now / 260L) % 2 == 0;
        int alertBg = blinkOn ? 0xCCE50027 : 0x66330011;
        graphics.fill(x, y, x + width, y + 11, alertBg);
        String vfText = "▶ VF — VENTRICULAR FIBRILLATION — 除颤准备";
        int vfTextCol = blinkOn ? 0xFFFFFFFF : 0xFFFF9E80;
        int vtw = this.font.width(vfText);
        int textRange = Math.max(1, width - vtw - 8);
        int textOff = (int) ((now / 30L) % (textRange * 2));
        if (textOff > textRange) textOff = textRange * 2 - textOff;
        graphics.drawString(this.font, vfText, x + 4 + textOff, y + 2, vfTextCol, false);
        graphics.fill(x, y + 11, x + width, y + 12, blinkOn ? 0xFFFF1744 : 0x44FF1744);

        float cornerPulse = 0.5f + 0.5f * (float) Math.sin(now / 160.0);
        int ca = (int) (cornerPulse * 110);
        int ccol = (ca << 24) | 0x00FF1744;
        int cs = 14;
        graphics.fill(x, y + 12, x + cs, y + 12 + cs, ccol);
        graphics.fill(x + width - cs, y + 12, x + width, y + 12 + cs, ccol);
        graphics.fill(x, y + height - cs, x + cs, y + height, ccol);
        graphics.fill(x + width - cs, y + height - cs, x + width, y + height, ccol);
    }

    private void drawECGPass(GuiGraphics graphics, int x, int y, int width, int height,
                             int sampleCount, float xStep, int color, float alphaMultiplier,
                             int extraThickness) {
        int prevScreenX = -1;
        int prevScreenY = -1;

        for (int i = 0; i < sampleCount; i++) {
            int bufIdx = (sweepIndex - sampleCount + i + BUFFER_SIZE * 2) % BUFFER_SIZE;

            if (!historyValid[bufIdx]) {
                prevScreenX = -1;
                prevScreenY = -1;
                continue;
            }

            int screenX = x + 2 + (int) (i * xStep);
            int screenY = y + 2 + (int) (historyY[bufIdx] * (height - 4));

            if (prevScreenX >= 0) {
                float age = (float) i / (float) sampleCount;
                int alpha = (int) ((35 + age * 220) * alphaMultiplier);
                alpha = Mth.clamp(alpha, 5, 255);
                int segColor = (alpha << 24) | (color & 0x00FFFFFF);

                drawSmoothLine(graphics, prevScreenX, prevScreenY, screenX, screenY, segColor, extraThickness);
            }

            prevScreenX = screenX;
            prevScreenY = screenY;
        }
    }

    private void drawWaveformTrace(GuiGraphics g, int ox, int oy, int count, float step, int height, float[] buffer, int color) {
        int prevX = -1, prevY = -1;
        for (int i = 0; i < count; i++) {
            int idx = (sweepIndex - count + i + BUFFER_SIZE * 2) % BUFFER_SIZE;
            if (!historyValid[idx]) {
                prevX = -1;
                continue;
            }
            int px = ox + (int) (i * step);
            int py = oy + (int) (buffer[idx] * height);

            if (prevX >= 0) {
                float age = (float) i / count;
                int a = (int) ((30 + age * 225));
                int c = (a << 24) | (color & 0x00FFFFFF);
                g.fill(px, py, px + 1, py + 1, c);
                if (Math.abs(py - prevY) > 1) {
                    int sy = Math.min(py, prevY);
                    int ey = Math.max(py, prevY);
                    g.fill(px, sy, px + 1, ey, c);
                }
            }
            prevX = px;
            prevY = py;
        }
    }

    private void drawSmoothLine(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color, int extra) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;

        int halfExtra = extra / 2;

        while (true) {
            if (halfExtra <= 0) {
                graphics.fill(x0, y0, x0 + 1, y0 + 1, color);
            } else {
                graphics.fill(x0, y0 - halfExtra, x0 + 1, y0 + halfExtra + 1, color);
            }

            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; x0 += sx; }
            if (e2 < dx) { err += dx; y0 += sy; }
        }
    }

    // ============================================================
    // 十四、悬浮快速检伤卡片
    // ============================================================
    private void drawFloatingTriageCard(GuiGraphics g, int mx, int my, MedicalData data) {
        if (hoveredLimb == null) return;
        MedicalData.LimbData ld = data.getLimb(hoveredLimb);
        int health = getLimbHealthPercent(ld);

        int tipW = 168;
        int tipH = 54;
        int tipX = mx + 20;
        int tipY = my - 16;
        if (tipX + tipW > this.width - 8) tipX = mx - tipW - 16;
        if (tipY + tipH > this.height - 10) tipY = this.height - tipH - 10;
        if (tipY < HUD_BAR_H + 8) tipY = HUD_BAR_H + 8;

        int statusColor = getHealthColor(health);

        String triageMsg;
        if (ld.getBleeding() > 0) {
            triageMsg = "严重出血: 需止血带/加压";
            statusColor = 0xFFFF1744;
        } else if (ld.hasFracture()) {
            triageMsg = "骨折: 需固定夹板处置";
            statusColor = 0xFFFF9100;
        } else if (ld.getInfection() > 0) {
            triageMsg = "组织感染: 需抗生素注射";
            statusColor = 0xFFFFD600;
        } else {
            triageMsg = "生理组织完整良好";
        }

        String titleStr = getLimbFullName(hoveredLimb).toUpperCase() + " [" + health + "%]";
        HudRenderHelper.drawTacticalBadge(g, this.font, tipX, tipY, tipW, tipH,
                titleStr, triageMsg, statusColor, 0xEA050E17);

        int barW = tipW - 14;
        HudRenderHelper.drawSciFiProgressBar(g, tipX + 7, tipY + 44, barW, 4,
                health / 100.0f, statusColor, 0x6608141E);

        int targetAnchorX = tipX < mx ? tipX + tipW : tipX;
        int targetAnchorY = tipY + tipH / 2;
        HudRenderHelper.drawSciFiLeaderLine(g, mx, my, targetAnchorX, targetAnchorY,
                HudColors.withAlpha(statusColor, 0.75f), HudColors.withAlpha(statusColor, 0.25f));
    }

    // ============================================================
    // 十五、几何与辅助视觉
    // ============================================================
    private void drawCyberCard(GuiGraphics graphics, int x, int y, int width, int height, String title, int highlight) {
        graphics.fill(x, y, x + width, y + height, 0xE0050D15);
        graphics.renderOutline(x, y, width, height, 0xFF142B3C);
        HudRenderHelper.drawCardCorners(graphics, x, y, width, height, 6, HudColors.withAlpha(highlight, 0.7f));
        graphics.fill(x + 6, y, x + width - 6, y + 1, HudColors.withAlpha(highlight, 0.45f));

        if (title != null && !title.isEmpty()) {
            HudRenderHelper.drawGlowingString(graphics, this.font, title, x + 10, y + 8, 0xFFECEFF1, highlight);
            int lineY = y + 21;
            int midW = width - 20;
            for (int i = 0; i < midW; i += 4) {
                int segW = Math.min(4, midW - i);
                graphics.fill(x + 10 + i, lineY, x + 10 + i + segW, lineY + 1, 0x3300ADB5);
            }
        }
    }

    private void drawCinematicVignette(GuiGraphics g) {
        int w = this.width;
        int h = this.height;
        int depth = 42;
        for (int i = 0; i < depth; i += 3) {
            int a = (int) ((1.0f - (float) i / depth) * 75);
            int color = a << 24;
            g.fill(0, i, w, i + 3, color);
            g.fill(0, h - i - 3, w, h - i, color);
            g.fill(i, 0, i + 3, h, color);
            g.fill(w - i - 3, 0, w - i, h, color);
        }
    }

    private void drawSmoothArc(GuiGraphics g, int cx, int cy, int r, float a0, float a1, int color, int thickness) {
        if (a1 <= a0) return;
        float step = 2.2f;
        int half = thickness / 2;
        int d = thickness % 2 == 0 ? half : half + 1;
        for (float a = a0; a <= a1; a += step) {
            double rad = Math.toRadians(a);
            int px = cx + (int) Math.round(Math.cos(rad) * r);
            int py = cy + (int) Math.round(Math.sin(rad) * r);
            g.fill(px - half, py - half, px + d, py + d, color);
        }
    }

    private void drawCenteredSmall(GuiGraphics g, String text, int cx, int y, int color) {
        int w = this.font.width(text);
        g.drawString(this.font, text, cx - w / 2, y, color, false);
    }

    private void drawLoadingAnimation(GuiGraphics g, int cx, int cy, long now) {
        int dots = (int) ((now / 300) % 4);
        StringBuilder sb = new StringBuilder("CONNECTING");
        for (int i = 0; i < dots; i++) sb.append(".");
        int tw = this.font.width(sb.toString());
        g.drawString(this.font, sb.toString(), cx - tw / 2, cy, 0xFF00E5FF, false);
        float angle = (now % 2000) / 2000f * 360f;
        drawSmoothArc(g, cx, cy + 18, 8, angle, angle + 90, 0xFF00E5FF, 2);
        drawSmoothArc(g, cx, cy + 18, 8, angle + 180, angle + 270, 0x8800E5FF, 2);
    }

    private void drawCyberCursor(GuiGraphics g, int mx, int my, long now) {
        float p = 0.4f + 0.2f * (float) Math.sin(now / 300.0);
        int a = (int) (p * 40);
        g.fill(mx - 3, my - 3, mx + 4, my + 4, (a << 24) | 0x0000E5FF);
        g.fill(mx - 8, my, mx - 4, my + 1, 0x6600E5FF);
        g.fill(mx + 5, my, mx + 9, my + 1, 0x6600E5FF);
        g.fill(mx, my - 8, mx + 1, my - 4, 0x6600E5FF);
        g.fill(mx, my + 5, mx + 1, my + 9, 0x6600E5FF);
    }

    /**
     * pointy-top 六边形填充 + 描边 (逐行切片)
     * 注: 原先由处置矩阵使用, 现仅供底栏图例芯片使用。
     */
    private void drawHexFill(GuiGraphics g, int cx, int cy, int r, float halfH, int fillCol, boolean hovered, int accent, long now) {
        if (r <= 0) return;
        for (int dy = -r; dy <= r; dy++) {
            int halfW = (int) (r - Math.abs(dy) * 0.58f);
            if (halfW < 0) continue;
            g.fill(cx - halfW, cy + dy, cx + halfW + 1, cy + dy + 1, fillCol);
        }
        int[][] pts = {
                {cx, cy - r}, {cx + r * 3 / 4, cy - r / 2}, {cx + r * 3 / 4, cy + r / 2},
                {cx, cy + r}, {cx - r * 3 / 4, cy + r / 2}, {cx - r * 3 / 4, cy - r / 2}
        };
        int edgeCol = hovered ? accent : (accent & 0x66FFFFFF);
        for (int i = 0; i < 6; i++) {
            int[] p1 = pts[i];
            int[] p2 = pts[(i + 1) % 6];
            HudRenderHelper.drawLine(g, p1[0], p1[1], p2[0], p2[1], hovered ? accent : edgeCol, 1);
        }
        HudRenderHelper.drawLine(g, pts[0][0], pts[0][1], pts[1][0], pts[1][1], edgeCol, 1);
    }

    // ============================================================
    // 十六、底栏按键帮助
    // ============================================================
    private void drawHelpText(GuiGraphics graphics, long now) {
        int y = this.height - 26;
        graphics.fill(12, y - 4, this.width - 12, this.height - 4, 0x88060D14);
        graphics.fill(12, y - 4, this.width - 12, y - 3, 0x3300E5FF);
        SciFiDataStreamRenderer.renderHorizontalDataTicker(graphics, this.font, 22,
                this.height - 12, this.width - 66, now);

        int cx = this.width - 26;
        int cy = this.height - 18;
        int mx = lastMouseX, my = lastMouseY;
        boolean hoverChip = mx > cx - 12 && mx < cx + 12 && my > cy - 12 && my < cy + 12;

        if (legendExpand > 0.01f) {
            drawLegendPanel(graphics);
        }
        float breathe = 0.5f + 0.5f * (float) Math.sin(now / 450.0);
        int chipCol = hoverChip || legendExpand > 0.5f ? 0xFF00E5FF : (int) (0x88 + 0x40 * breathe) << 24 | 0x00E5FF;
        drawHexFill(graphics, cx, cy, 9, 0, 0x22060D14, hoverChip, chipCol, now);
        graphics.drawString(this.font, "?", cx - 2, cy - 4, hoverChip || legendExpand > 0.5f ? 0xFF00E5FF : 0xFF546E7A, false);
    }

    private float legendExpand = 0f;
    private int lastMouseX = 0, lastMouseY = 0;

    private void drawLegendPanel(GuiGraphics g) {
        int cx = this.width - 26;
        int panelW = 170;
        int panelH = (int) (96 * legendExpand);
        int px = cx - panelW / 2;
        int py = this.height - 36 - panelH;
        if (panelH < 10) return;

        HudRenderHelper.drawSciFiCard(g, px, py, panelW, panelH + 8, 4, 0xF0060D14, 0xFF00E5FF);
        g.drawString(this.font, "PROTOCOL LEGEND", px + 10, py + 6, 0xFF00E5FF, false);
        g.fill(px + 10, py + 16, px + panelW - 10, py + 17, 0x4400E5FF);

        String[][] entries = {
                {"H", "关闭面板"}, {"LMB", "选中肢体"}, {"TAB", "循环肢体"},
                {"R", "取消选中"}, {"S", "全身诊断扫描"},
                {"SCROLL", "断层逐层刮擦"}, {"HEAD", "3D脑部扫描"}
        };
        int itemStep = (int) (96f / entries.length);
        int visCount = (int) (entries.length * legendExpand);
        for (int i = 0; i < visCount; i++) {
            int iy = py + 22 + i * itemStep;
            drawKeyHint(g, px + 10, iy, entries[i][0], entries[i][1]);
        }
    }

    private int drawKeyHint(GuiGraphics g, int x, int y, String key, String desc) {
        int keyW = this.font.width(key) + 6;
        g.fill(x, y, x + keyW, y + 11, 0x440A1820);
        g.renderOutline(x, y, keyW, 11, 0xFF1A3040);
        g.drawString(this.font, key, x + 3, y + 2, 0xFF00E5FF, false);
        g.drawString(this.font, desc, x + keyW + 3, y + 2, 0xFF546E7A, false);
        return x + keyW + 3 + this.font.width(desc) + 4;
    }

    // ============================================================
    // 十七、交互逻辑与按键响应
    // ============================================================
    private void updateHoverState(int mouseX, int mouseY, float dt) {
        int bodyCardX = 24;
        int bodyCardY = HUD_BAR_H + 14;
        int bodyCardW = 220;
        int bodyCardH = this.height - HUD_BAR_H - 50;

        int startX = bodyCardX + (bodyCardW - TOTAL_W) / 2 + 10;
        int startY = bodyCardY + (bodyCardH - TOTAL_H) / 2 + 5;

        hoveredLimb = null;
        if (isMouseOverBlock(mouseX, mouseY, startX + HEAD_OFFSET_X, startY + HEAD_OFFSET_Y, HEAD_SIZE, HEAD_SIZE)) {
            hoveredLimb = MedicalData.Limb.HEAD;
        } else if (isMouseOverBlock(mouseX, mouseY, startX + BODY_OFFSET_X, startY + BODY_OFFSET_Y, BODY_W, BODY_H)) {
            hoveredLimb = MedicalData.Limb.CHEST;
        } else if (isMouseOverBlock(mouseX, mouseY, startX + LEFT_ARM_OFFSET_X, startY + LEFT_ARM_OFFSET_Y, ARM_W, ARM_H)) {
            hoveredLimb = MedicalData.Limb.LEFT_ARM;
        } else if (isMouseOverBlock(mouseX, mouseY, startX + RIGHT_ARM_OFFSET_X, startY + RIGHT_ARM_OFFSET_Y, ARM_W, ARM_H)) {
            hoveredLimb = MedicalData.Limb.RIGHT_ARM;
        } else if (isMouseOverBlock(mouseX, mouseY, startX + LEFT_LEG_OFFSET_X, startY + LEFT_LEG_OFFSET_Y, LEG_W, LEG_H)) {
            hoveredLimb = MedicalData.Limb.LEFT_LEG;
        } else if (isMouseOverBlock(mouseX, mouseY, startX + RIGHT_LEG_OFFSET_X, startY + RIGHT_LEG_OFFSET_Y, LEG_W, LEG_H)) {
            hoveredLimb = MedicalData.Limb.RIGHT_LEG;
        }

        if (hoveredLimb != lastHoveredLimb) {
            if (hoveredLimb != null) MedicalSoundHelper.playHoverSound();
            lastHoveredLimb = hoveredLimb;
        }

        for (MedicalData.Limb limb : MedicalData.Limb.values()) {
            int idx = limb.ordinal();
            float target = (limb == hoveredLimb || limb == selectedLimb) ? 1.0f : 0.0f;
            limbHoverProgress[idx] += (target - limbHoverProgress[idx]) * (dt * 12.0f);
        }
    }

    private void updateConnectionLine(float dt) {
        if (selectedLimb != null) {
            if (connectionLineLimb != selectedLimb) {
                connectionLineLimb = selectedLimb;
                connectionLineProgress = 0.0f;
            }
            connectionLineProgress = Math.min(1.0f, connectionLineProgress + dt * 4.0f);
        } else {
            connectionLineProgress = Math.max(0.0f, connectionLineProgress - dt * 6.0f);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (state == ScreenState.CLOSING) return false;

        int bodyCardX = 24;
        int bodyCardY = HUD_BAR_H + 14;
        int bodyCardW = 220;
        int bodyCardH = this.height - HUD_BAR_H - 50;

        int startX = bodyCardX + (bodyCardW - TOTAL_W) / 2 + 10;
        int startY = bodyCardY + (bodyCardH - TOTAL_H) / 2 + 5;

        MedicalData.Limb[] limbsForPulse = MedicalData.Limb.values();
        int[] limbBoxes = new int[limbsForPulse.length * 4];
        for (int li = 0; li < limbsForPulse.length; li++) {
            int[] box = getLimbCenterBox(limbsForPulse[li], startX, startY);
            limbBoxes[li * 4] = box[0];
            limbBoxes[li * 4 + 1] = box[1];
            limbBoxes[li * 4 + 2] = box[2];
            limbBoxes[li * 4 + 3] = box[3];
        }
        rippleManager.addUltrasoundPulse((float) mouseX, (float) mouseY, 60.0f, HudColors.CYAN, limbBoxes);
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        long nowClick = System.currentTimeMillis();
        MedicalData.Limb hitLimb = null;
        if (isMouseOverBlock(mouseX, mouseY, startX + HEAD_OFFSET_X, startY + HEAD_OFFSET_Y, HEAD_SIZE, HEAD_SIZE)) hitLimb = MedicalData.Limb.HEAD;
        else if (isMouseOverBlock(mouseX, mouseY, startX + BODY_OFFSET_X, startY + BODY_OFFSET_Y, BODY_W, BODY_H)) hitLimb = MedicalData.Limb.CHEST;
        else if (isMouseOverBlock(mouseX, mouseY, startX + LEFT_ARM_OFFSET_X, startY + LEFT_ARM_OFFSET_Y, ARM_W, ARM_H)) hitLimb = MedicalData.Limb.LEFT_ARM;
        else if (isMouseOverBlock(mouseX, mouseY, startX + RIGHT_ARM_OFFSET_X, startY + RIGHT_ARM_OFFSET_Y, ARM_W, ARM_H)) hitLimb = MedicalData.Limb.RIGHT_ARM;
        else if (isMouseOverBlock(mouseX, mouseY, startX + LEFT_LEG_OFFSET_X, startY + LEFT_LEG_OFFSET_Y, LEG_W, LEG_H)) hitLimb = MedicalData.Limb.LEFT_LEG;
        else if (isMouseOverBlock(mouseX, mouseY, startX + RIGHT_LEG_OFFSET_X, startY + RIGHT_LEG_OFFSET_Y, LEG_W, LEG_H)) hitLimb = MedicalData.Limb.RIGHT_LEG;

        if (hitLimb != null) {
            if (hitLimb == lastClickedLimb && (nowClick - lastLimbClickTime) < 350L) {
                microTomographyLimb = (microTomographyLimb == hitLimb) ? null : hitLimb;
                MedicalSoundHelper.playLimbSelectSound();
                lastLimbClickTime = 0L;
                lastClickedLimb = null;
                return true;
            } else {
                lastLimbClickTime = nowClick;
                lastClickedLimb = hitLimb;
                toggleSelection(hitLimb);
                return true;
            }
        }

        if (microTomographyLimb != null && microTomographyAnim > 0.5f) {
            microTomographyLimb = null;
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void toggleSelection(MedicalData.Limb limb) {
        MedicalSoundHelper.playLimbSelectSound();
        if (selectedLimb == limb) {
            selectedLimb = null;
        } else {
            selectedLimb = limb;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 72 || keyCode == 256) {
            startCloseAnimation();
            return true;
        }
        if (keyCode == 258) {
            MedicalData.Limb[] limbs = MedicalData.Limb.values();
            int cur = selectedLimb == null ? -1 : selectedLimb.ordinal();
            int next = cur + 1;
            selectedLimb = (next >= limbs.length) ? null : limbs[next];
            MedicalSoundHelper.playLimbSelectSound();
            return true;
        }
        if (keyCode == 82) {
            if (selectedLimb != null) {
                selectedLimb = null;
                MedicalSoundHelper.playLimbSelectSound();
            }
            return true;
        }
        if (keyCode == 83) {
            if (sweepScanAnim <= 0.01f) {
                sweepScanAnim = 0.0001f;
                sweepScanRevealed = false;
                MedicalSoundHelper.playOpenSound();
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (microTomographyLimb != null && microTomographyAnim > 0.5f) {
            if (deltaY != 0) {
                int nl = (dcmLayer + (deltaY > 0 ? 1 : DICOM_LAYER_NAMES.length - 1)) % DICOM_LAYER_NAMES.length;
                dcmLayer = nl;
                dcmWipeStart = System.currentTimeMillis();
                MedicalSoundHelper.playLimbSelectSound();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    @Override
    public void onClose() {
        if (state == ScreenState.OPEN || state == ScreenState.OPENING) {
            startCloseAnimation();
        } else if (state == ScreenState.CLOSED) {
            super.onClose();
        }
    }

    private void startCloseAnimation() {
        if (state == ScreenState.CLOSING || state == ScreenState.CLOSED) return;
        state = ScreenState.CLOSING;
        closeStartTime = System.currentTimeMillis();
        MedicalSoundHelper.playCloseSound();
    }

    private boolean isMouseOverBlock(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    // ============================================================
    // 十八、状态计算与辅助方法
    // ============================================================
    private int getLimbHealthPercent(MedicalData.LimbData ld) {
        int bleeding = ld.getBleeding();
        int internal = ld.getInternalBleedingRate();
        int pain = ld.getPainLevel();
        int h = 100;
        h -= Math.min(40, bleeding / 15);
        h -= Math.min(30, internal / 10);
        h -= Math.min(30, pain * 3);
        return Math.max(0, h);
    }

    private int getLimbColor(MedicalData.LimbData ld) {
        int health = getLimbHealthPercent(ld);
        int base = (health > 80) ? HudColors.NORMAL
                : (health > 50) ? HudColors.WARNING
                : (health > 20) ? HudColors.CAUTION_ORANGE : HudColors.DANGER;
        if (ld.getBleeding() > 0) base = HudColors.DANGER;
        if (ld.hasFracture()) base = HudColors.CRITICAL_PURPLE;
        return base;
    }

    private String getLimbFullName(MedicalData.Limb limb) {
        switch (limb) {
            case HEAD: return tr("medical.limb.head");
            case CHEST: return tr("medical.limb.chest");
            case LEFT_ARM: return tr("medical.limb.left_arm");
            case RIGHT_ARM: return tr("medical.limb.right_arm");
            case LEFT_LEG: return tr("medical.limb.left_leg");
            case RIGHT_LEG: return tr("medical.limb.right_leg");
            default: return "";
        }
    }

    public String getLimbStatus(MedicalData.LimbData ld) {
        StringBuilder sb = new StringBuilder();
        if (ld.getBleeding() > 0) {
            int b = ld.getBleeding();
            if (b <= 50) sb.append(tr("medical.status.minor_bleeding")).append(" ");
            else if (b <= 200) sb.append(tr("medical.status.moderate_bleeding")).append(" ");
            else if (b <= 500) sb.append(tr("medical.status.severe_bleeding")).append(" ");
            else if (b <= 1000) sb.append(tr("medical.status.catastrophic_bleeding")).append(" ");
            else sb.append(tr("medical.status.destructive_bleeding")).append(" ");
        }
        if (ld.getInternalBleedingRate() > 0) sb.append(tr("medical.status.internal_bleeding")).append(" ");
        if (ld.hasFracture()) {
            if (ld.isOpenFracture()) sb.append(tr("medical.status.open_fracture")).append(" ");
            else if (ld.hasSplint()) sb.append(tr("medical.status.fracture_splinted")).append(" ");
            else sb.append(tr("medical.status.fracture")).append(" ");
        }
        if (ld.hasDislocation()) sb.append(tr("medical.status.dislocation")).append(" ");
        if (ld.isTourniqueted()) sb.append(tr("medical.status.tourniquet")).append(" ");
        if (ld.isBandaged()) sb.append(tr("medical.status.bandaged")).append(" ");
        if (ld.getInfection() > 0) {
            int inf = ld.getInfection();
            if (inf <= 25) sb.append(tr("medical.status.mild_infection")).append(" ");
            else if (inf <= 50) sb.append(tr("medical.status.moderate_infection")).append(" ");
            else if (inf <= 75) sb.append(tr("medical.status.severe_infection")).append(" ");
            else sb.append(tr("medical.status.critical_infection")).append(" ");
        }
        if (sb.length() == 0) return tr("medical.status.healthy");
        return sb.toString().trim();
    }

    private String tr(String key) {
        return I18N_CACHE.computeIfAbsent(key, k -> Component.translatable(k).getString());
    }

    private int getBleedingColor(int value) {
        if (value == 0) return HudColors.NORMAL;
        if (value <= 50) return 0xFF81C784;
        if (value <= 200) return HudColors.WARNING;
        if (value <= 500) return HudColors.CAUTION_ORANGE;
        return HudColors.DANGER;
    }

    private int getPainColor(int value) {
        if (value == 0) return HudColors.NORMAL;
        if (value <= 3) return 0xFF81C784;
        if (value <= 6) return HudColors.WARNING;
        return HudColors.DANGER;
    }

    private int getInfectionColor(int value) {
        if (value == 0) return HudColors.NORMAL;
        if (value <= 25) return 0xFF81C784;
        if (value <= 50) return HudColors.WARNING;
        if (value <= 75) return HudColors.CAUTION_ORANGE;
        return HudColors.DANGER;
    }

    private int getHealthColor(int value) {
        if (value > 80) return HudColors.NORMAL;
        if (value > 50) return HudColors.WARNING;
        return HudColors.DANGER;
    }

    private String getConsciousnessText(int value) {
        if (value <= 0) return value + "% (" + tr("medical.consciousness.visual.coma") + ")";
        else if (value < 40) return value + "% (" + tr("medical.consciousness.visual.severe") + ")";
        else if (value < 80) return value + "% (" + tr("medical.consciousness.visual.blur") + ")";
        return value + "%";
    }

    private int getConsciousnessColor(int value) {
        if (value > 80) return HudColors.NORMAL;
        if (value > 50) return HudColors.WARNING;
        return HudColors.DANGER;
    }

    private int getOxygenColor(int value) {
        if (value > 90) return HudColors.NORMAL;
        if (value > 70) return HudColors.WARNING;
        return HudColors.DANGER;
    }

    private int getHeartRateColor(int value) {
        if (value >= 60 && value <= 100) return HudColors.NORMAL;
        if (value > 100 && value <= 140) return HudColors.WARNING;
        return HudColors.DANGER;
    }

    private int getHemothoraxLevelColor(int level) {
        if (level <= 1) return HudColors.WARNING;
        if (level == 2) return HudColors.CAUTION_ORANGE;
        return HudColors.DANGER;
    }

    private int getBloodVolumeColor(int value, int maxValue) {
        int ratio = value * 100 / Math.max(1, maxValue);
        if (ratio > 70) return HudColors.NORMAL;
        if (ratio > 50) return HudColors.WARNING;
        if (ratio > 30) return HudColors.CAUTION_ORANGE;
        return HudColors.DANGER;
    }

    private int getBPColor(float map, int sbp) {
        if (sbp >= 180 || map < 50) return HudColors.DANGER;
        if (map < 65) return HudColors.CAUTION_ORANGE;
        if (map > 120 || sbp > 140) return HudColors.WARNING;
        return HudColors.NORMAL;
    }

    private int getECGColor(int heartRate) {
        if (heartRate <= 0) return 0xFFFF3344;
        if (heartRate < 50) return 0xFF00E5FF;
        if (heartRate <= 100) return 0xFF00FF88;
        if (heartRate <= 140) return 0xFFFFD600;
        return 0xFFFF3344;
    }

    private String getHeartRateStatus(int hr) {
        if (hr <= 0) return tr("medical.ecg.cardiac_arrest");
        if (hr < 45) return tr("medical.ecg.severe_bradycardia");
        if (hr < 60) return tr("medical.ecg.bradycardia");
        if (hr <= 100) return tr("medical.ecg.normal");
        if (hr <= 140) return tr("medical.ecg.tachycardia");
        if (hr <= 180) return tr("medical.ecg.severe_tachycardia");
        return tr("medical.ecg.extreme_tachycardia");
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ============================================================
    // 十九、3A 核心动效增强
    // ============================================================
    private void drawAsystoleTakeover(GuiGraphics g, long now) {
        g.fill(0, 0, this.width, this.height, 0x30000508);

        int lineY = this.height / 2;
        g.fill(0, lineY, this.width, lineY + 2, 0x997A0C18);
        g.fill(0, lineY, this.width, lineY + 1, 0xCCB71C1C);
        long msSince = now % 400000L;
        float decay = Math.max(0.0f, 1.0f - msSince / 60000.0f);
        if (decay > 0.01f) {
            long seed = (now / 60) * 60;
            int pr = (int) ((seed ^ (seed >>> 16)) & 0xFF);
            int noiseX = (int) ((now / 16) % this.width);
            int noiseH = (int) (decay * (pr % 3));
            g.fill(noiseX, lineY - noiseH, noiseX + 14, lineY + 2, 0x3DB71C1C);
        }

        String asys = "ASYS";
        int ax = this.width - this.font.width(asys) - 14;
        int ay = 12;
        float blink = 0.6f + 0.4f * (float) Math.sin(now / 260.0);
        int aCol = ((int) (255 * blink) << 24) | 0x00FF1744;
        HudRenderHelper.drawGlowingString(g, this.font, asys, ax, ay, aCol, 0xFFB71C1C);
        HudRenderHelper.drawGlowingString(g, this.font, "► CARDIAC ARREST — CPR IN PROGRESS", 14, ay,
                0xFFB71C1C, 0xFF5D0711);

        g.fill(0, 0, this.width, 26, 0x44000000);
        g.fill(0, this.height - 26, this.width, this.height, 0x44000000);
    }

    private void drawNeuralInterference(GuiGraphics g, long now) {
        float severity = Math.max(0.0f, Math.min(1.0f,
                (30.0f - Math.min(sprBrainHealth.val, sprConsciousness.val)) / 30.0f));

        long seed = (now / 130) * 130;
        int pr = (int) ((seed ^ (seed >>> 16)) & 0x7F);
        if (pr < (int) (30 + severity * 50)) {
            int tearY = 30 + (pr * 11) % (this.height - 80);
            for (int i = 0; i < 3; i++) {
                int off = ((pr >> i) & 3) * (int) (4 + severity * 10);
                g.fill(Math.max(0, off), tearY + i * 3, this.width, tearY + i * 3 + 1, 0x2A00E5FF);
            }
        }

        java.util.Random rng = new java.util.Random(now / 90);
        int edgePts = (int) (8 + severity * 26);
        for (int i = 0; i < edgePts; i++) {
            int side = rng.nextInt(4);
            int ex, ey;
            int m = 8;
            switch (side) {
                case 0 -> { ex = rng.nextInt(this.width); ey = rng.nextInt(m); }
                case 1 -> { ex = rng.nextInt(this.width); ey = this.height - rng.nextInt(m); }
                case 2 -> { ex = rng.nextInt(m); ey = rng.nextInt(this.height); }
                default -> { ex = this.width - rng.nextInt(m); ey = rng.nextInt(this.height); }
            }
            int s = 1 + rng.nextInt(2 + (int) (severity * 3));
            int col = rng.nextFloat() > 0.6f ? 0x5500E5FF : 0x55B0BEC5;
            g.fill(ex, ey, ex + s, ey + s, col);
        }

        String ghost = "NEURAL INTERFERENCE DETECTED";
        int gx = 14;
        int gy = this.height - 20;
        int ghostShift = (int) (severity * (1 + (now / 180) % 3));
        HudRenderHelper.drawGlowingString(g, this.font, ghost, gx + ghostShift, gy, 0x40B0BEC5, 0x20004552);
        HudRenderHelper.drawGlowingString(g, this.font, ghost, gx - ghostShift, gy, 0x60FFAB91, 0x40774C1F);

        int scanY = (int) ((now / 20) % this.height);
        g.fill(0, scanY, this.width, scanY + 2, 0x12000000);
    }

    private void drawOnDemandSweepScan(GuiGraphics g, MedicalData data, int startX, int startY, int totalW, int totalH, long now) {
        if (sweepScanAnim <= 0.0f) return;
        float phase = Math.min(1.0f, sweepScanAnim);

        float sweepY = startY + phase * (totalH + 30) - 15;
        float slope = 0.268f;
        g.enableScissor(startX - 10, startY - 10, startX + totalW + 10, startY + totalH + 10);
        for (int lx = 0; lx < totalW + 20; lx += 2) {
            int curX = startX - 10 + lx;
            int curY = (int) (sweepY + (lx - (totalW / 2)) * slope);
            g.fill(curX, curY, curX + 2, curY + 2, 0x88FFFFFF);
            g.fill(curX, curY - 3, curX + 2, curY, 0x2200E5FF);
            g.fill(curX, curY + 2, curX + 2, curY + 5, 0x2200E5FF);
        }
        g.disableScissor();

        MedicalData.Limb[] limbs = MedicalData.Limb.values();
        for (MedicalData.Limb limb : limbs) {
            int[] box = getLimbCenterBox(limb, startX, startY);
            if (box == null) continue;
            float limbTopFrac = (float) (box[1] - startY) / totalH;
            float since = phase - limbTopFrac;
            if (since > 0.0f && since < 0.25f) {
                float glow = (1.0f - since / 0.25f);
                MedicalData.LimbData ld = data.getLimb(limb);
                int col = limbIssueColor(ld);
                int alpha = (int) (glow * 90);
                g.fill(box[0] - 2, box[1] - 2, box[0] + box[2] + 2, box[1] + box[3] + 2, (alpha << 24) | (col & 0x00FFFFFF));
            }
        }

        if (sweepScanRevealed) {
            long t = now - sweepScanStampTime;
            if (t < 6000L) {
                int stampX = startX + totalW - 70;
                int stampY = startY + 6;
                int idx = 0;
                for (MedicalData.Limb limb : limbs) {
                    MedicalData.LimbData ld = data.getLimb(limb);
                    int issueCount = countLimbIssuesFrom(ld);
                    if (issueCount <= 0) continue;
                    int col = limbIssueColor(ld);
                    long lt = t - idx * 120L;
                    if (lt < 0) { idx++; continue; }
                    float pop = Math.min(1.0f, lt / 200.0f);
                    float scale = 1.0f + 0.5f * (float) Math.sin(pop * Math.PI);
                    String label = limbShortName(limb) + " ×" + issueCount;
                    int w = this.font.width(label) + 10;
                    int h = (int) (12 * scale);
                    int yy = stampY + idx * 15;
                    g.fill(stampX - w, yy, stampX, yy + h, (0xE0 << 24) | 0x00061018);
                    HudRenderHelper.drawGlowingString(g, this.font, label, stampX - w + 5, yy + 2,
                            (0xFF000000 | (col & 0x00FFFFFF)), col);
                    idx++;
                }
                String banner = "SWEEP COMPLETE — FINDINGS: " + stampCountCache(data);
                HudRenderHelper.drawGlowingString(g, this.font, banner,
                        startX + totalW - this.font.width(banner) - 6, startY - 12, 0xFF00E5FF, HudColors.CYAN);
            }
        }
    }

    private String limbShortName(MedicalData.Limb limb) {
        return switch (limb) {
            case HEAD -> "颅";
            case CHEST -> "胸";
            case LEFT_ARM -> "左臂";
            case RIGHT_ARM -> "右臂";
            case LEFT_LEG -> "左腿";
            case RIGHT_LEG -> "右腿";
        };
    }

    private int limbIssueColor(MedicalData.LimbData ld) {
        if (ld.hasFracture()) return 0xAA00FF;
        if (ld.getBleeding() > 0 || ld.hasOpenWound()) return 0xFF1744;
        if (ld.getInfection() > 0) return 0x00E676;
        return 0x00E5FF;
    }

    private int countLimbIssuesFrom(MedicalData.LimbData ld) {
        int n = 0;
        if (ld.hasFracture()) n++;
        if (ld.getBleeding() > 0 || ld.hasOpenWound()) n++;
        if (ld.getInfection() > 0) n++;
        return n;
    }

    private int stampCountCache(MedicalData data) {
        int total = 0;
        for (MedicalData.Limb limb : MedicalData.Limb.values()) {
            total += countLimbIssuesFrom(data.getLimb(limb));
        }
        return total;
    }

    private int[] getLimbCenterBox(MedicalData.Limb limb, int startX, int startY) {
        return switch (limb) {
            case HEAD -> new int[]{startX + HEAD_OFFSET_X, startY + HEAD_OFFSET_Y, HEAD_SIZE, HEAD_SIZE};
            case CHEST -> new int[]{startX + BODY_OFFSET_X, startY + BODY_OFFSET_Y, BODY_W, BODY_H};
            case LEFT_ARM -> new int[]{startX + LEFT_ARM_OFFSET_X, startY + LEFT_ARM_OFFSET_Y, ARM_W, ARM_H};
            case RIGHT_ARM -> new int[]{startX + RIGHT_ARM_OFFSET_X, startY + RIGHT_ARM_OFFSET_Y, ARM_W, ARM_H};
            case LEFT_LEG -> new int[]{startX + LEFT_LEG_OFFSET_X, startY + LEFT_LEG_OFFSET_Y, LEG_W, LEG_H};
            case RIGHT_LEG -> new int[]{startX + RIGHT_LEG_OFFSET_X, startY + RIGHT_LEG_OFFSET_Y, LEG_W, LEG_H};
        };
    }

    private void drawMicroTomographyHUD(GuiGraphics g, MedicalData data, long now) {
        if (microTomographyAnim <= 0.01f || microTomographyLimb == null) return;

        float anim = microTomographyAnim;
        int winW = (int) (240 * anim);
        int winH = (int) (180 * anim);
        int winX = this.width / 2 - winW / 2;
        int winY = this.height / 2 - winH / 2 + 10;

        if (winW < 50 || winH < 40) return;

        HudRenderHelper.drawSciFiCard(g, winX, winY, winW, winH, 4, 0xF0060D14, 0xFF00E5FF);
        HudRenderHelper.drawCardCorners(g, winX, winY, winW, winH, 8, 0xFF00E5FF);

        String title = "TOMOGRAPHY // " + microTomographyLimb.name()
                + " // L" + (dcmLayer + 1) + " " + DICOM_LAYER_NAMES[dcmLayer];
        g.drawString(this.font, title, winX + 10, winY + 8, 0xFF00E5FF, false);
        g.fill(winX + 10, winY + 19, winX + winW - 10, winY + 20, 0x4400E5FF);

        int gridX = winX + 12;
        int gridY = winY + 25;
        int gridW = winW - 24;
        int gridH = winH - 55;
        g.fill(gridX, gridY, gridX + gridW, gridY + gridH, 0x4404090E);

        for (int gx = gridX; gx < gridX + gridW; gx += 16) {
            g.fill(gx, gridY, gx + 1, gridY + gridH, 0x1100E5FF);
        }
        for (int gy = gridY; gy < gridY + gridH; gy += 16) {
            g.fill(gridX, gy, gridX + gridW, gy + 1, 0x1100E5FF);
        }

        MedicalData.LimbData ld = data.getLimb(microTomographyLimb);
        boolean hasFracture = ld != null && ld.hasFracture();
        boolean hasBleeding = ld != null && (ld.getBleeding() > 0 || ld.getInternalBleedingRate() > 0);
        boolean hasLaceration = hasBleeding;

        float wipeP = 1.0f;
        if (dcmWipeStart > 0) {
            wipeP = Mth.clamp((float) (now - dcmWipeStart) / (float) DICOM_WIPE_MS, 0.0f, 1.0f);
            if (wipeP >= 1.0f) dcmWipeStart = 0L;
        }
        int revealW = (int) (gridW * wipeP);
        if (wipeP < 1.0f) {
            int wipeX = gridX + revealW;
            g.fill(wipeX, gridY, wipeX + 2, gridY + gridH, 0xAA00E5FF);
            g.fill(wipeX - 1, gridY, wipeX + 3, gridY + 1, 0xCC00E5FF);
            g.fill(wipeX - 1, gridY + gridH - 1, wipeX + 3, gridY + gridH, 0xCC00E5FF);
        }

        renderDcmLayerContent(g, gridX, gridY, revealW, gridH, ld, hasFracture, hasBleeding, hasLaceration, now);

        int indY = gridY + gridH + 6;
        int segW = (gridW - 3 * 4) / 4;
        for (int i = 0; i < 4; i++) {
            int sx = gridX + i * (segW + 4);
            int fillCol = (i == dcmLayer) ? 0xFF00E5FF : 0x3300E5FF;
            g.fill(sx, indY, sx + segW, indY + 3, fillCol);
            if (i == dcmLayer) {
                g.drawString(this.font, "L" + (i + 1), sx, indY + 5, 0xFF00E5FF, false);
            }
        }
        g.drawString(this.font, "SCROLL TO SCRUB", gridX + gridW - 78, indY + 5, 0x6600E5FF, false);
    }

    private void renderDcmLayerContent(GuiGraphics g, int gridX, int gridY, int revealW, int gridH,
                                       MedicalData.LimbData ld, boolean hasFracture, boolean hasBleeding,
                                       boolean hasLaceration, long now) {
        if (revealW <= 2) return;
        int midX = gridX + revealW / 2;
        int topY = gridY + 12;
        int botY = gridY + gridH - 14;
        int midY = (topY + botY) / 2;

        g.enableScissor(gridX, gridY, gridX + revealW, gridY + gridH);

        switch (dcmLayer) {
            case 0 -> {
                g.fill(gridX + 8, topY, gridX + revealW - 8, botY, 0x335A4A44);
                HudRenderHelper.drawLine(g, gridX + 8, topY, gridX + revealW - 8, topY, 0xFF8A7A72, 1);
                HudRenderHelper.drawLine(g, gridX + 8, botY, gridX + revealW - 8, botY, 0xFF8A7A72, 1);
                int burnDeg = ld.getBurnDegree();
                boolean heatActive = ld.getBurnHeatTimer() > 0;
                if (burnDeg > 0 || heatActive) {
                    // 烧伤病灶：横向宽度 ∝ 面积，按深度着色
                    int zoneW = Math.max(10, (int) ((revealW - 16) * Math.max(0.15f, ld.getBurnArea())));
                    int zx0 = midX - zoneW / 2;
                    int zx1 = midX + zoneW / 2;
                    int zoneColor = switch (Math.max(1, burnDeg)) {
                        case 1 -> 0x66E85D3A;                                   // I度:红斑
                        case 2 -> 0x88D4442A;                                   // 浅II:鲜红+水疱
                        case 3 -> 0x88905A38;                                   // 深II:斑驳红褐
                        default -> 0xCC1E1610;                                  // III:焦黑
                    };
                    g.fill(zx0, topY + 3, zx1, botY - 3, zoneColor);
                    if (burnDeg == 2) {
                        // 浅II度:水疱亮斑
                        for (int i = 0; i < 5; i++) {
                            int bx = zx0 + 4 + i * Math.max(3, (zoneW - 8) / 5);
                            int by = topY + 8 + ((i * 7) % Math.max(6, (botY - topY) / 2));
                            g.fill(bx, by, bx + 3, by + 2, 0xAAF0E6C8);
                        }
                    } else if (burnDeg == 3) {
                        // 深II度:斑驳纹理
                        for (int i = 0; i < 6; i++) {
                            int bx = zx0 + 3 + (i * Math.max(4, (zoneW - 6) / 6));
                            int by = topY + 6 + ((i * 11) % Math.max(8, botY - topY - 12));
                            g.fill(bx, by, bx + 4, by + 3, 0x556B4226);
                        }
                    } else if (burnDeg >= 4) {
                        g.drawString(this.font, "ESCHAR FORMED", gridX + 6, gridY + 6, 0xFFC8B8A0, false);
                    }
                    String burnLabel = burnDeg == 0 ? "THERMAL LOADING"
                            : burnDeg == 1 ? "EPIDERMAL ERYTHEMA"
                            : burnDeg == 2 ? "PARTIAL THICKNESS" + (ld.isBandaged() ? " [DRESSED]" : "")
                            : burnDeg == 3 ? "DEEP DERMAL" + (ld.isBandaged() ? " [DRESSED]" : "")
                            : "FULL THICKNESS" + (ld.isBandaged() ? " [DRESSED]" : "");
                    int labelColor = burnDeg >= 4 ? 0xFFC8B8A0 : (burnDeg >= 2 ? 0xFFFF8A5C : 0xFFE8B08A);
                    g.drawString(this.font, burnLabel, gridX + 6, gridY + 6, labelColor, false);
                    if (heatActive) {
                        // 热力期闪烁:IRRIGATE 提示（冷水冲洗可阻止加深）
                        boolean blink = (now % 800) < 400;
                        if (blink) {
                            g.drawString(this.font, "IRRIGATE NOW", gridX + 6, gridY + gridH - 12, 0xFF00E5FF, false);
                        }
                    }
                } else if (hasLaceration) {
                    for (int i = 0; i < 6; i++) {
                        int lx = midX - 15 + i * 6;
                        HudRenderHelper.drawLine(g, lx, midY - 4 + i, lx + 4, midY + 4 - i, 0xFFFF1744, 1);
                    }
                    g.drawString(this.font, "LACERATION OPEN", gridX + 6, gridY + 6, 0xFFFF5252, false);
                } else {
                    g.drawString(this.font, "INTEGUMENT INTACT", gridX + 6, gridY + 6, 0xFF00FF88, false);
                }
                if (hasBleeding) {
                    g.fill(midX - 20, midY - 10, midX + 20, midY + 10, 0x3D3A1E4A);
                    g.drawString(this.font, "HEMATOMA", midX + 22, midY - 3, 0xFF9A6CD0, false);
                }
            }
            case 1 -> {
                HudRenderHelper.drawLine(g, midX - 3, topY, midX - 3, botY, 0xFFC23B4E, 1);
                HudRenderHelper.drawLine(g, midX + 3, topY, midX + 3, botY, 0xFFC23B4E, 1);
                for (int i = 0; i < 7; i++) {
                    int vy = topY + 6 + i * ((botY - topY) / 8);
                    HudRenderHelper.drawLine(g, midX - 3, vy, midX - 14, vy + 6, 0x88C23B4E, 1);
                    HudRenderHelper.drawLine(g, midX + 3, vy, midX + 14, vy - 6, 0x88C23B4E, 1);
                }
                if (hasBleeding) {
                    float t = (now % 1000) / 1000.0f;
                    for (int i = 0; i < 6; i++) {
                        float angle = -1.2f + (i * 0.4f);
                        float dist = (t * 22.0f + i * 4.0f) % 24.0f;
                        int px = midX + (int) (Math.cos(angle) * dist);
                        int py = midY + (int) (Math.sin(angle) * dist);
                        int alpha = (int) ((1.0f - dist / 24.0f) * 220);
                        g.fill(px - 1, py - 1, px + 2, py + 2, (alpha << 24) | 0x00FF1744);
                    }
                    g.drawString(this.font, "ACTIVE ARTERIAL JET", gridX + 6, gridY + gridH - 12, 0xFFFF1744, false);
                } else {
                    g.drawString(this.font, "PERFUSION NOMINAL", gridX + 6, gridY + 6, 0xFF00FF88, false);
                }
            }
            case 2 -> {
                for (int i = 0; i < 9; i++) {
                    int fy = topY + 4 + i * ((botY - topY) / 10);
                    int off = (i % 2 == 0) ? 3 : -3;
                    HudRenderHelper.drawLine(g, gridX + 10, fy + off, gridX + revealW - 10, fy - off, 0x55B0563A, 1);
                }
                int shrapnelX = midX - 18;
                int shrapnelY = topY + 16;
                HudRenderHelper.drawDiamond(g, shrapnelX, shrapnelY, 3, 0xFFFFD600);
                g.fill(shrapnelX + 4, shrapnelY, shrapnelX + 16, shrapnelY + 1, 0x88FFD600);
                g.drawString(this.font, "FOREIGN BODY", shrapnelX + 18, shrapnelY - 3, 0xFFFFD600, false);
                if (hasBleeding) {
                    g.fill(midX - 24, midY - 6, midX + 8, midY + 6, 0x2E5A2E3A);
                }
            }
            default -> {
                if (hasFracture) {
                    HudRenderHelper.drawLine(g, midX - 6, topY, midX - 4, midY - 3, 0xFFEEEEEE, 4);
                    HudRenderHelper.drawLine(g, midX + 5, midY + 4, midX + 3, botY, 0xFFEEEEEE, 4);
                    HudRenderHelper.drawLine(g, midX - 7, midY - 3, midX + 7, midY + 4, 0xFFFF1744, 2);
                    g.drawString(this.font, "DISPLACEMENT: 14.2°", gridX + 6, gridY + 6, 0xFFFF5252, false);
                } else {
                    HudRenderHelper.drawLine(g, midX, topY, midX, botY, 0xFFCCCCCC, 5);
                    g.drawString(this.font, "CORTICAL BONE: INTACT", gridX + 6, gridY + 6, 0xFF00FF88, false);
                }
            }
        }
        g.disableScissor();
    }

    private void drawAssemblyTrail(GuiGraphics g, float panelX, float panelY, float progress, long now, int seed) {
        if (progress >= 0.999f) return;
        float ang = (seed * 2.399963f) + 1.1f;
        float depth = 120f + (seed % 2) * 60f;
        float sx = panelX + (float) Math.cos(ang) * depth;
        float sy = panelY + (float) Math.sin(ang) * depth * 0.6f;

        for (int i = 0; i < 8; i++) {
            float tp = (i / 8.0f);
            float px = sx + (panelX - sx) * tp * progress;
            float py = sy + (panelY - sy) * tp * progress;
            int a = (int) ((1.0f - tp) * 150 * progress);
            if (a <= 0) continue;
            int jitter = (int) (Math.sin(now * 0.02f + i * 1.7f + seed) * 1.5f);
            g.fill((int) px + jitter, (int) py + jitter, (int) px + 2 + jitter, (int) py + 2 + jitter,
                    (a << 24) | 0x0000E5FF);
        }
        int ax = (int) (sx + (panelX - sx) * 0.5f * progress);
        int ay = (int) (sy + (panelY - sy) * 0.5f * progress);
        int a2 = (int) (90 * progress);
        if (a2 > 0) HudRenderHelper.drawLine(g, ax, ay, (int) panelX, (int) panelY, (a2 << 24) | 0x0000E5FF, 1);
    }

    private void drawAssemblyReticle(GuiGraphics g, float overallProgress, long now) {
        if (overallProgress >= 1.0f) return;
        int cx = this.width / 2;
        int cy = this.height / 2;
        int a = (int) (70 * (1.0f - overallProgress) + 20);
        int col = (a << 24) | 0x0000E5FF;
        int armLen = (int) (60 - 40 * overallProgress);
        HudRenderHelper.drawLine(g, cx - armLen, cy, cx - 6, cy, col, 1);
        HudRenderHelper.drawLine(g, cx + 6, cy, cx + armLen, cy, col, 1);
        HudRenderHelper.drawLine(g, cx, cy - armLen, cx, cy - 6, col, 1);
        HudRenderHelper.drawLine(g, cx, cy + 6, cx, cy + armLen, col, 1);
        int m = (int) (36 - 26 * overallProgress);
        int[][] corners = {{cx - m, cy - m, 1, 1}, {cx + m, cy - m, -1, 1}, {cx - m, cy + m, 1, -1}, {cx + m, cy + m, -1, -1}};
        for (int[] c : corners) {
            g.fill(c[0], c[1], c[0] + 7 * c[2], c[1] + 1 * c[3], col);
            g.fill(c[0], c[1], c[0] + 1 * c[2], c[1] + 7 * c[3], col);
        }
        if (overallProgress > 0.95f) {
            float flash = (overallProgress - 0.95f) / 0.05f;
            int fa = (int) (50 * (1.0f - flash));
            if (fa > 0) {
                g.fill(0, 0, this.width, 2, (fa << 24) | 0x0000E5FF);
                g.fill(0, this.height - 2, this.width, this.height, (fa << 24) | 0x0000E5FF);
            }
        }
    }
}