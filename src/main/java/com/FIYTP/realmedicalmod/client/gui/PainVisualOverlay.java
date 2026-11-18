package com.FIYTP.realmedicalmod.client.gui;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.capability.MedicalDataCapability;
import com.FIYTP.realmedicalmod.client.PainShockClientState;
import com.FIYTP.realmedicalmod.config.MedicalConfig;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Matrix4f;

/**
 * 3A 级疼痛视觉与镜头反馈系统 (PainVisualOverlay - 边缘血管精确锚定、四角无缝覆盖与层级排序修复版)
 *
 * 核心升级：
 * 1. 精确屏幕边界射线锚定 (Exact Screen-Border Ray Intersection)：
 *    采用 rBorder = 1.0 / max(|cos|, |sin|) 精确计算外圈暗角与每条血管在矩形屏幕四边上的物理交点，
 *    外圈（rOuter >= 0.98）自适应延伸至屏幕四角，内圈（rOuter < 0.98）严格保持自身半径不误铺全屏。
 * 2. 显式关闭背面剔除 (disableCull) 并采用 EventPriority.HIGH 底层渲染，
 *    确保疼痛暗角永远处于 CriticalOverlay 濒死倒计时全息舱的下层，绝不遮挡倒计时。
 */
@EventBusSubscriber(modid = "realmedicalmod", value = Dist.CLIENT)
public class PainVisualOverlay {

    private static final int RADIAL_SEGMENTS = 48;

    private static long lastFrameTimeNs = 0L;
    private static float smoothPain = 0.0f;
    private static float smoothHeartRate = 75.0f;
    private static float heartPhase = 0.0f;

    private static float smoothHeadPain = 0.0f;
    private static float smoothChestPain = 0.0f;
    private static float smoothLeftPain = 0.0f;
    private static float smoothRightPain = 0.0f;

    // 3A 烧伤独特视觉状态（焦化热浪暗角、热空气折射波纹、表皮火纹与上升火星粒子）
    private static float smoothBurnIntensity = 0.0f;
    private static float smoothBurnTop = 0.0f;
    private static float smoothBurnBottom = 0.0f;
    private static float smoothBurnLeft = 0.0f;
    private static float smoothBurnRight = 0.0f;
    private static int currentMaxBurnDegree = 0;
    private static float emberSpawnAccum = 0.0f;

    private static final class BurnEmberParticle {
        boolean active;
        float x, y;
        float vx, vy;
        float size;
        float age, maxAge;
        float phase;
        boolean isAsh;
    }

    private static final BurnEmberParticle[] BURN_EMBERS = new BurnEmberParticle[56];
    static {
        for (int i = 0; i < BURN_EMBERS.length; i++) {
            BURN_EMBERS[i] = new BurnEmberParticle();
        }
    }

    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!MedicalConfig.PAIN_VIGNETTE_ENABLED.get()) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.isPaused() || mc.options.hideGui || player.isSpectator()) return;

        float startThreshold = MedicalConfig.PAIN_VIGNETTE_START_THRESHOLD.get().floatValue();
        float fullThreshold = MedicalConfig.PAIN_VIGNETTE_FULL_THRESHOLD.get().floatValue();
        float painFactor = Mth.clamp((smoothPain - startThreshold) / Math.max(1.0f, fullThreshold - startThreshold), 0.0f, 1.0f);

        float shockAlpha = MedicalConfig.PAIN_SHOCK_ENABLED.get() ? PainShockClientState.getShockAlpha() : 0.0f;
        float burnCam = MedicalConfig.BURN_VFX_ENABLED.get() ? smoothBurnIntensity : 0.0f;
        if (painFactor <= 0.001f && shockAlpha <= 0.001f && burnCam <= 0.005f) return;

        double timeSec = System.currentTimeMillis() / 1000.0;
        float pulse = evaluateDicroticPulse(heartPhase);

        float tremorRoll = 0.0f;
        float tremorPitch = 0.0f;
        float tremorYaw = 0.0f;

        if (painFactor > 0.05f) {
            // 采用连续平滑的心搏余弦包络与超低频大范围呼吸/失稳漂移（彻底消除高频抽搐感）
            float smoothPulseWave = (float) (0.5 - 0.5 * Math.cos(Mth.clamp(pulse, 0.0f, 1.0f) * Math.PI));
            tremorPitch += smoothPulseWave * painFactor * 0.42f;

            float slowSway = painFactor * (0.45f + 0.55f * painFactor);
            tremorRoll += (float) (Math.sin(timeSec * 1.35) * 0.75 + Math.cos(timeSec * 0.78) * 0.45) * slowSway;
            tremorYaw += (float) (Math.sin(timeSec * 1.12) * 0.85 + Math.cos(timeSec * 0.64 + 1.1) * 0.55) * slowSway;
            tremorPitch += (float) (Math.cos(timeSec * 1.24) * 0.72 + Math.sin(timeSec * 0.72 + 0.6) * 0.42) * slowSway;

            float sideBias = (smoothRightPain - smoothLeftPain) * 0.65f * painFactor;
            tremorRoll += sideBias;
        }

        if (shockAlpha > 0.001f) {
            float maxOffset = MedicalConfig.PAIN_SHOCK_MAX_OFFSET.get().floatValue() * MedicalConfig.HEAVY_TRAUMA_CAMERA_RECOIL_MULT.get().floatValue();
            float shockWave = PainShockClientState.getCameraRecoilWave();
            int dir = PainShockClientState.getShockDirectionSign();
            float hitX = PainShockClientState.getHitDirX();
            float hitY = PainShockClientState.getHitDirY();

            tremorRoll += (dir * 0.7f + hitX * 0.6f) * shockAlpha * maxOffset * 1.15f * shockWave;
            tremorPitch += (0.65f + Math.abs(hitY) * 0.5f) * shockAlpha * maxOffset * 0.85f * shockWave;
            tremorYaw += -dir * shockAlpha * maxOffset * 0.60f * shockWave;
        }

        if (burnCam > 0.01f) {
            tremorPitch += (float) (Math.sin(timeSec * 1.55) * 0.45 + Math.cos(timeSec * 0.92) * 0.28) * burnCam;
            tremorYaw += (float) (Math.cos(timeSec * 1.18) * 0.42) * burnCam;
            tremorRoll += (smoothBurnRight - smoothBurnLeft) * 0.42f * burnCam;
        }

        event.setRoll(event.getRoll() + tremorRoll);
        event.setPitch(event.getPitch() + tremorPitch);
        event.setYaw(event.getYaw() + tremorYaw);
    }

    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (!MedicalConfig.PAIN_SHOCK_ENABLED.get()) return;
        float fovDelta = PainShockClientState.getFovPunchDelta();
        if (Math.abs(fovDelta) > 0.05f) {
            event.setFOV(Mth.clamp(event.getFOV() + fovDelta, 30.0, 140.0));
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRenderGuiPost(RenderGuiEvent.Post event) {
        if (!MedicalConfig.PAIN_VIGNETTE_ENABLED.get()) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null) return;
        if (mc.screen != null || mc.options.hideGui || player.isSpectator()) return;

        MedicalData data = player.getData(MedicalDataCapability.MEDICAL_DATA);
        if (data == null) return;

        long nowNs = System.nanoTime();
        if (lastFrameTimeNs == 0L) lastFrameTimeNs = nowNs;
        float dt = Mth.clamp((nowNs - lastFrameTimeNs) / 1_000_000_000.0f, 0.0005f, 0.1f);
        lastFrameTimeNs = nowNs;

        float targetPain = data.getPerceivedPain();
        float painLerpSpeed = (targetPain > smoothPain) ? 5.5f : 2.2f;
        smoothPain = Mth.lerp(Math.min(1.0f, dt * painLerpSpeed), smoothPain, targetPain);

        float targetHr = Mth.clamp(data.getHeartRate(), 35.0f, 220.0f);
        smoothHeartRate = Mth.lerp(Math.min(1.0f, dt * 3.0f), smoothHeartRate, targetHr);

        heartPhase = (heartPhase + dt * (smoothHeartRate / 60.0f)) % 1.0f;

        float maxLimbPain = Math.max(1.0f, MedicalConfig.MAX_PAIN_LEVEL.get().floatValue());
        float targetHead = data.getLimb(MedicalData.Limb.HEAD).getPainLevel() / maxLimbPain;
        float targetChest = data.getLimb(MedicalData.Limb.CHEST).getPainLevel() / maxLimbPain;
        float targetLeft = Math.max(
                data.getLimb(MedicalData.Limb.LEFT_ARM).getPainLevel(),
                data.getLimb(MedicalData.Limb.LEFT_LEG).getPainLevel()
        ) / maxLimbPain;
        float targetRight = Math.max(
                data.getLimb(MedicalData.Limb.RIGHT_ARM).getPainLevel(),
                data.getLimb(MedicalData.Limb.RIGHT_LEG).getPainLevel()
        ) / maxLimbPain;

        float limbLerp = Math.min(1.0f, dt * 4.0f);
        smoothHeadPain = Mth.lerp(limbLerp, smoothHeadPain, targetHead);
        smoothChestPain = Mth.lerp(limbLerp, smoothChestPain, targetChest);
        smoothLeftPain = Mth.lerp(limbLerp, smoothLeftPain, targetLeft);
        smoothRightPain = Mth.lerp(limbLerp, smoothRightPain, targetRight);

        updateBurnVisualState(player, data, dt);

        float startThreshold = MedicalConfig.PAIN_VIGNETTE_START_THRESHOLD.get().floatValue();
        float fullThreshold = MedicalConfig.PAIN_VIGNETTE_FULL_THRESHOLD.get().floatValue();
        float grayThreshold = MedicalConfig.PAIN_GRAY_WASH_THRESHOLD.get().floatValue();

        float painFactor = (smoothPain >= startThreshold * 0.75f)
                ? Mth.clamp((smoothPain - startThreshold * 0.75f) / Math.max(1.0f, fullThreshold - startThreshold * 0.75f), 0.0f, 1.0f)
                : 0.0f;
        float maxLimbRaw = Math.max(Math.max(targetHead, targetChest), Math.max(targetLeft, targetRight));
        painFactor = Math.max(painFactor, Mth.clamp((maxLimbRaw - 0.15f) / 0.75f, 0.0f, 1.0f) * 0.85f);

        float bloodDeficit = Mth.clamp((data.getMaxBloodVolume() - data.getBloodVolume()) / 2200.0f, 0.0f, 1.0f);
        float hypoxiaFactor = Mth.clamp((94.0f - data.getBloodOxygenFloat()) / 28.0f, 0.0f, 1.0f);
        int pneumoType = com.FIYTP.realmedicalmod.treatment.ModItems.getPneumothoraxType(data);
        float traumaCornerIntensity = Mth.clamp(
                Math.max(painFactor * 0.92f, Math.max(bloodDeficit * 0.95f, hypoxiaFactor * 0.90f))
                        + (pneumoType > 0 ? 0.22f * pneumoType : 0.0f),
                0.0f, 1.0f
        );
        boolean hasBurnVfx = MedicalConfig.BURN_VFX_ENABLED.get() && smoothBurnIntensity > 0.004f;
        if (painFactor <= 0.002f && traumaCornerIntensity <= 0.005f && !hasBurnVfx) return;

        float consciousness = data.getConsciousness();
        float consciousnessBudget = (consciousness < 40.0f)
                ? (0.35f + 0.65f * (consciousness / 40.0f))
                : 1.0f;

        float pulse = evaluateDicroticPulse(heartPhase);
        float maxAlphaCfg = MedicalConfig.PAIN_VIGNETTE_MAX_ALPHA.get().floatValue();

        GuiGraphics graphics = event.getGuiGraphics();
        int w = graphics.guiWidth();
        int h = graphics.guiHeight();
        Matrix4f pose = graphics.pose().last().pose();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        float topBoost = smoothHeadPain * 0.48f + smoothChestPain * 0.18f;
        float bottomBoost = smoothChestPain * 0.38f;
        float leftBoost = smoothLeftPain * 0.48f;
        float rightBoost = smoothRightPain * 0.48f;

        // ---------- 层级 0：常驻深邃纯黑边角压迫层 (Pitch-Black Corner Vignette —— 回归经典深黑压迫感且零漏角) ----------
        if (MedicalConfig.TRAUMA_BLACK_CORNER_VIGNETTE_ENABLED.get() && traumaCornerIntensity > 0.005f) {
            float maxBlackAlpha = MedicalConfig.TRAUMA_BLACK_CORNER_MAX_ALPHA.get().floatValue();
            float minInnerR = MedicalConfig.TRAUMA_BLACK_CORNER_MIN_INNER_RADIUS.get().floatValue();
            float breathWave = 0.5f + 0.5f * Mth.sin((System.currentTimeMillis() % 60000L) * 0.0045f);
            float blackInnerR = Mth.lerp(traumaCornerIntensity, 0.78f, minInnerR + 0.06f)
                    - 0.035f * pulse * traumaCornerIntensity
                    - 0.025f * breathWave * hypoxiaFactor;
            float blackMidR = blackInnerR + 0.22f;
            float midBlackAlpha = Mth.clamp(traumaCornerIntensity * maxBlackAlpha * 0.58f, 0.0f, 0.78f);
            float outerBlackAlpha = Mth.clamp(traumaCornerIntensity * maxBlackAlpha * (0.86f + 0.14f * pulse), 0.0f, maxBlackAlpha);

            // 内圈羽化深黑过渡带
            drawEllipticalRing(pose, w, h,
                    blackInnerR, 0.0f,
                    blackMidR, midBlackAlpha,
                    0, 0, 0,
                    leftBoost * 0.6f, rightBoost * 0.6f, topBoost * 0.6f, bottomBoost * 0.6f);
            // 外圈四角浓重纯黑封边带（RGB 0,0,0，完全锁死屏幕四边与四角）
            drawEllipticalRing(pose, w, h,
                    blackMidR, midBlackAlpha,
                    1.48f, outerBlackAlpha,
                    0, 0, 0,
                    leftBoost * 0.6f, rightBoost * 0.6f, topBoost * 0.6f, bottomBoost * 0.6f);
        }

        // ---------- 层级 1：重度剧痛边缘冷灰去饱和隧道暗角 ----------
        if (smoothPain >= grayThreshold) {
            float maxPerceived = Math.max(10.0f, MedicalConfig.MAX_PERCEIVED_PAIN.get().floatValue());
            float grayFactor = Mth.clamp((smoothPain - grayThreshold) / Math.max(1.0f, maxPerceived - grayThreshold), 0.0f, 1.0f);
            float grayMaxAlpha = MedicalConfig.PAIN_GRAY_WASH_MAX_ALPHA.get().floatValue();
            float outerTunnelAlpha = Mth.clamp(grayFactor * grayMaxAlpha * 2.0f * consciousnessBudget, 0.0f, 0.55f);

            drawEllipticalRing(pose, w, h,
                    0.50f - 0.08f * grayFactor, 0.0f,
                    1.45f, outerTunnelAlpha,
                    12, 14, 20,
                    0.0f, 0.0f, 0.0f, 0.0f);
        }

        // ---------- 层级 2：主血管搏动无缝椭圆暗红晕影 ----------
        float baseVignetteAlpha = (painFactor > 0.002f) ? (painFactor * maxAlphaCfg * consciousnessBudget) : 0.0f;
        float outerPulseAlpha = Mth.clamp(baseVignetteAlpha * (0.68f + 0.32f * pulse), 0.0f, 0.82f);

        float innerRadius = 0.74f - 0.18f * painFactor - 0.04f * pulse * painFactor;
        float midRadius = innerRadius + 0.22f;



        float midAlpha = outerPulseAlpha * 0.48f;
        drawEllipticalRing(pose, w, h,
                innerRadius, 0.0f,
                midRadius, midAlpha,
                135, 8, 16,
                leftBoost, rightBoost, topBoost, bottomBoost);

        drawEllipticalRing(pose, w, h,
                midRadius, midAlpha,
                1.45f, outerPulseAlpha,
                75, 2, 8,
                leftBoost, rightBoost, topBoost, bottomBoost);

        // ---------- 层级 3：收缩期峰值鲜红动脉冲涌 + 边缘微色差 ----------
        if (pulse > 0.45f) {
            float surge = (pulse - 0.45f) / 0.55f;
            float surgeAlpha = Mth.clamp(baseVignetteAlpha * surge * 0.65f, 0.0f, 0.60f);

            if (surgeAlpha > 0.005f) {
                float surgeInner = innerRadius - 0.03f * surge;
                drawEllipticalRing(pose, w, h,
                        surgeInner, 0.0f,
                        innerRadius + 0.14f, surgeAlpha,
                        220, 24, 32,
                        leftBoost, rightBoost, topBoost, bottomBoost);

                float caStrength = Mth.clamp(painFactor * 0.7f + smoothHeadPain * 0.6f, 0.0f, 1.0f);
                if (caStrength > 0.25f) {
                    float caAlpha = surgeAlpha * caStrength * 0.42f;
                    RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
                    drawEllipticalRing(pose, w, h,
                            surgeInner - 0.015f, 0.0f,
                            surgeInner + 0.06f, caAlpha,
                            0, 210, 255,
                            rightBoost, leftBoost, bottomBoost, topBoost);
                    RenderSystem.defaultBlendFunc();
                }
            }
        }

        // ---------- 层级 4：严格锚定屏幕四边的 3A 多级分叉毛细血管与痛觉神经脉冲网 ----------
        if (painFactor > 0.15f) {
            float veinAlpha = Mth.clamp((painFactor - 0.15f) / 0.85f, 0.0f, 1.0f)
                    * (0.40f + 0.60f * pulse) * maxAlphaCfg * consciousnessBudget;
            if (veinAlpha > 0.01f) {
                drawScreenEdgeAnchoredCapillaries(pose, w, h, veinAlpha, painFactor, pulse,
                        leftBoost, rightBoost, topBoost, bottomBoost);
            }
        }

        // ---------- 层级 5：3A 级烧伤独特视觉系统 ----------
        if (hasBurnVfx) {
            renderThermalBurnVisuals(graphics, pose, w, h, dt, pulse, consciousnessBudget);
        }

        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    /**
     * 绘制无缝椭圆径向渐变环：
     * - 当 rOuter >= 0.98f（外圈边缘遮罩）时，外径自适应延伸至矩形屏幕角点距离 1 / max(|cos|, |sin|) * 1.02f，100% 消除四角漏边；
     * - 当 rOuter < 0.98f（内/中过渡环）时，严格保持真实 rOuter 半径，绝不错误拉伸至全屏。
     */
    public static void drawEllipticalRing(
            Matrix4f pose, int w, int h,
            float rInner, float alphaInner,
            float rOuter, float alphaOuter,
            int r, int g, int b,
            float leftBoost, float rightBoost, float topBoost, float bottomBoost) {

        if (alphaInner <= 0.001f && alphaOuter <= 0.001f) return;

        float cx = w * 0.5f;
        float cy = h * 0.5f;
        float rx = w * 0.5f;
        float ry = h * 0.5f;

        RenderSystem.disableCull();
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);

        for (int i = 0; i <= RADIAL_SEGMENTS; i++) {
            double angle = (i / (double) RADIAL_SEGMENTS) * Math.PI * 2.0;
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);

            float dirWeight = 1.0f;
            if (cos < 0) dirWeight += (-cos) * leftBoost;
            else dirWeight += cos * rightBoost;
            if (sin < 0) dirWeight += (-sin) * topBoost;
            else dirWeight += sin * bottomBoost;

            float localInnerR = Math.max(0.22f, rInner - (dirWeight - 1.0f) * 0.12f);

            float actualOuterR;
            if (rOuter >= 0.98f) {
                float borderDist = 1.0f / Math.max(0.001f, Math.max(Math.abs(cos), Math.abs(sin)));
                actualOuterR = Math.max(rOuter, borderDist * 1.05f);
            } else {
                actualOuterR = Math.max(localInnerR + 0.02f, rOuter - (dirWeight - 1.0f) * 0.06f);
            }

            float aIn = Mth.clamp(alphaInner * dirWeight, 0.0f, 0.96f);
            float aOut = Mth.clamp(alphaOuter * dirWeight, 0.0f, 0.98f);

            float xIn = cx + cos * rx * localInnerR;
            float yIn = cy + sin * ry * localInnerR;
            float xOut = cx + cos * rx * actualOuterR;
            float yOut = cy + sin * ry * actualOuterR;

            // 先外后内使正面朝向 +Z (CCW)，同时配合 disableCull 双重保险
            buffer.addVertex(pose, xOut, yOut, 0.0f).setColor(r, g, b, (int) (aOut * 255.0f));
            buffer.addVertex(pose, xIn, yIn, 0.0f).setColor(r, g, b, (int) (aIn * 255.0f));
        }

        MeshData mesh = buffer.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
    }

    /**
     * 3A 级屏幕四边精确锚定的有机蜿蜒毛细血管与痛觉神经丝网络
     */
    private static void drawScreenEdgeAnchoredCapillaries(
            Matrix4f pose, int w, int h, float baseAlpha, float painFactor, float pulse,
            float leftBoost, float rightBoost, float topBoost, float bottomBoost) {

        float cx = w * 0.5f;
        float cy = h * 0.5f;

        int totalRoots = 32;
        int mainSteps = 14;
        float timeSec = (System.currentTimeMillis() % 60000L) / 1000.0f;

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        for (int i = 0; i < totalRoots; i++) {
            float perimeterPos = (i + 0.5f) / (float) totalRoots * 4.0f;
            int edgeIndex = ((int) perimeterPos) % 4;
            float edgeFrac = perimeterPos - (int) perimeterPos;

            float jitter = (Mth.sin(i * 12.9898f + 78.233f) * 0.5f) * 0.04f;
            edgeFrac = Mth.clamp(edgeFrac + jitter, 0.02f, 0.98f);

            float rootX, rootY;
            float dirBoost = 1.0f;
            switch (edgeIndex) {
                case 0 -> {
                    rootX = edgeFrac * w;
                    rootY = -2.0f;
                    dirBoost += topBoost * 0.65f;
                }
                case 1 -> {
                    rootX = w + 2.0f;
                    rootY = edgeFrac * h;
                    dirBoost += rightBoost * 0.65f;
                }
                case 2 -> {
                    rootX = (1.0f - edgeFrac) * w;
                    rootY = h + 2.0f;
                    dirBoost += bottomBoost * 0.65f;
                }
                default -> {
                    rootX = -2.0f;
                    rootY = (1.0f - edgeFrac) * h;
                    dirBoost += leftBoost * 0.65f;
                }
            }

            float toCenterX = cx - rootX;
            float toCenterY = cy - rootY;
            float distToCenter = Mth.sqrt(toCenterX * toCenterX + toCenterY * toCenterY);
            if (distToCenter < 1.0f) continue;

            float inX = toCenterX / distToCenter;
            float inY = toCenterY / distToCenter;
            float perpX = -inY;
            float perpY = inX;

            float seedVar = 0.75f + 0.50f * (0.5f + 0.5f * Mth.sin(i * 43.17f));
            float maxReachPx = Math.min(w, h) * (0.13f + 0.15f * painFactor * dirBoost + 0.035f * pulse) * seedVar;
            float rootAlpha = Mth.clamp(baseAlpha * dirBoost * (0.75f + 0.25f * seedVar), 0.0f, 0.92f);
            float rootThickness = (1.35f + 1.15f * painFactor) * Math.min(1.3f, dirBoost);

            float prevX = rootX;
            float prevY = rootY;
            float phaseOffset = i * 2.17f;
            float freq1 = 3.6f + (i % 4) * 0.8f;
            float freq2 = 7.8f + (i % 3) * 1.4f;
            float wiggleAmp = maxReachPx * 0.11f;

            for (int s = 1; s <= mainSteps; s++) {
                float t = s / (float) mainSteps;
                float prevT = (s - 1) / (float) mainSteps;

                float env = Mth.sin(t * (float) Math.PI * 0.85f);
                float lateralOffset = (Mth.sin(t * freq1 + phaseOffset + pulse * 0.6f) * 0.65f
                        + Mth.cos(t * freq2 - phaseOffset * 1.3f) * 0.35f) * wiggleAmp * env;

                float currX = rootX + inX * (t * maxReachPx) + perpX * lateralOffset;
                float currY = rootY + inY * (t * maxReachPx) + perpY * lateralOffset;

                float taperStart = (float) Math.pow(1.0f - prevT, 1.35);
                float taperEnd = (float) Math.pow(1.0f - t, 1.35);

                float wStart = Math.max(0.20f, rootThickness * taperStart);
                float wEnd = Math.max(0.15f, rootThickness * taperEnd);
                int aStart = (int) (rootAlpha * taperStart * 255.0f);
                int aEnd = (int) (rootAlpha * taperEnd * 255.0f);

                addTaperedQuadSegment(buffer, pose, prevX, prevY, currX, currY,
                        wStart * 2.1f, wEnd * 2.1f,
                        120, 6, 14, aStart / 3, aEnd / 3);

                float impulseWave = Mth.clamp(1.0f - Math.abs(t - ((timeSec * 1.4f + i * 0.13f) % 1.0f)) * 5.0f, 0.0f, 1.0f);
                int coreR = (int) Mth.lerp(impulseWave * painFactor, 205, 255);
                int coreG = (int) Mth.lerp(impulseWave * painFactor, 16, 75);
                int coreB = (int) Mth.lerp(impulseWave * painFactor, 26, 95);

                addTaperedQuadSegment(buffer, pose, prevX, prevY, currX, currY,
                        wStart * 0.85f, wEnd * 0.85f,
                        coreR, coreG, coreB, aStart, aEnd);

                if ((s == 5 || s == 9) && painFactor > 0.22f) {
                    float branchSign = ((i + s) % 2 == 0) ? 1.0f : -1.0f;
                    float branchAngle = branchSign * (0.48f + (i % 3) * 0.09f);
                    float cosB = Mth.cos(branchAngle);
                    float sinB = Mth.sin(branchAngle);

                    float bDirX = inX * cosB - inY * sinB;
                    float bDirY = inX * sinB + inY * cosB;
                    float bPerpX = -bDirY;
                    float bPerpY = bDirX;

                    float branchLen = maxReachPx * (s == 5 ? 0.46f : 0.32f);
                    drawCurvedSubBranch(buffer, pose, currX, currY, bDirX, bDirY, bPerpX, bPerpY,
                            branchLen, wEnd * 0.72f, rootAlpha * taperEnd * 0.85f, i * 7 + s, pulse);
                }

                prevX = currX;
                prevY = currY;
            }
        }

        MeshData mesh = buffer.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
    }

    private static void drawCurvedSubBranch(
            BufferBuilder buffer, Matrix4f pose,
            float startX, float startY, float dirX, float dirY, float perpX, float perpY,
            float length, float startWidth, float startAlpha, int seed, float pulse) {

        int subSteps = 7;
        float prevX = startX;
        float prevY = startY;
        float curveAmp = length * 0.14f;

        for (int k = 1; k <= subSteps; k++) {
            float t = k / (float) subSteps;
            float prevT = (k - 1) / (float) subSteps;

            float bend = Mth.sin(t * 4.2f + seed * 1.7f + pulse) * curveAmp * t;
            float cx = startX + dirX * (t * length) + perpX * bend;
            float cy = startY + dirY * (t * length) + perpY * bend;

            float w1 = startWidth * (1.0f - prevT * 0.85f);
            float w2 = startWidth * (1.0f - t * 0.90f);
            int a1 = (int) (startAlpha * (1.0f - prevT) * 255.0f);
            int a2 = (int) (startAlpha * (1.0f - t) * 255.0f);

            addTaperedQuadSegment(buffer, pose, prevX, prevY, cx, cy,
                    w1 * 1.8f, w2 * 1.8f, 130, 8, 18, a1 / 3, a2 / 3);
            addTaperedQuadSegment(buffer, pose, prevX, prevY, cx, cy,
                    w1 * 0.8f, w2 * 0.8f, 225, 28, 42, a1, a2);

            prevX = cx;
            prevY = cy;
        }
    }

    public static void addTaperedQuadSegment(
            BufferBuilder buffer, Matrix4f pose,
            float x1, float y1, float x2, float y2,
            float halfW1, float halfW2, int r, int g, int b, int a1, int a2) {

        float dx = x2 - x1;
        float dy = y2 - y1;
        float len = Mth.sqrt(dx * dx + dy * dy);
        if (len < 0.001f) return;

        float nx = -dy / len;
        float ny = dx / len;

        buffer.addVertex(pose, x1 - nx * halfW1, y1 - ny * halfW1, 0.0f).setColor(r, g, b, a1);
        buffer.addVertex(pose, x1 + nx * halfW1, y1 + ny * halfW1, 0.0f).setColor(r, g, b, a1);
        buffer.addVertex(pose, x2 + nx * halfW2, y2 + ny * halfW2, 0.0f).setColor(r, g, b, a2);
        buffer.addVertex(pose, x2 - nx * halfW2, y2 - ny * halfW2, 0.0f).setColor(r, g, b, a2);
    }

    public static void addQuadSegment(
            BufferBuilder buffer, Matrix4f pose,
            float x1, float y1, float x2, float y2,
            float halfWidth, int r, int g, int b, int a1, int a2) {
        addTaperedQuadSegment(buffer, pose, x1, y1, x2, y2, halfWidth, halfWidth, r, g, b, a1, a2);
    }

    public static float evaluateDicroticPulse(float phase) {
        phase = phase - Mth.floor(phase);
        if (phase < 0.14f) {
            float t = phase / 0.14f;
            return Mth.sin(t * ((float) Math.PI * 0.5f));
        } else if (phase < 0.30f) {
            float t = (phase - 0.14f) / 0.16f;
            return 1.0f - 0.68f * (t * t);
        } else if (phase < 0.44f) {
            float t = (phase - 0.30f) / 0.14f;
            return 0.30f + 0.22f * Mth.sin(t * (float) Math.PI);
        } else {
            float t = (phase - 0.44f) / 0.56f;
            float inv = 1.0f - t;
            return 0.28f * inv * inv;
        }
    }

    private static void updateBurnVisualState(Player player, MedicalData data, float dt) {
        MedicalData.LimbData head = data.getLimb(MedicalData.Limb.HEAD);
        MedicalData.LimbData chest = data.getLimb(MedicalData.Limb.CHEST);
        MedicalData.LimbData lArm = data.getLimb(MedicalData.Limb.LEFT_ARM);
        MedicalData.LimbData rArm = data.getLimb(MedicalData.Limb.RIGHT_ARM);
        MedicalData.LimbData lLeg = data.getLimb(MedicalData.Limb.LEFT_LEG);
        MedicalData.LimbData rLeg = data.getLimb(MedicalData.Limb.RIGHT_LEG);

        int maxDeg = 0;
        for (MedicalData.Limb limb : MedicalData.Limb.values()) {
            MedicalData.LimbData ld = data.getLimb(limb);
            if (ld != null && ld.getBurnDegree() > maxDeg) {
                maxDeg = ld.getBurnDegree();
            }
        }
        currentMaxBurnDegree = maxDeg;

        float activeFireBoost = (player.isInLava() ? 0.55f : (player.isOnFire() ? 0.32f : 0.0f));
        float tHead = computeLimbBurnSeverity(head) + activeFireBoost * 0.6f;
        float tChest = computeLimbBurnSeverity(chest) + activeFireBoost * 0.8f;
        float tLeft = Math.max(computeLimbBurnSeverity(lArm), computeLimbBurnSeverity(lLeg)) + activeFireBoost * 0.7f;
        float tRight = Math.max(computeLimbBurnSeverity(rArm), computeLimbBurnSeverity(rLeg)) + activeFireBoost * 0.7f;

        float tbsaNorm = Mth.clamp(data.getBurnTBSA() / 35.0f, 0.0f, 1.0f);
        float maxLimbSev = Math.max(Math.max(tHead, tChest), Math.max(tLeft, tRight));
        float targetIntensity = Mth.clamp(Math.max(maxLimbSev * 0.85f, tbsaNorm), 0.0f, 1.0f);

        float lerpRate = Math.min(1.0f, dt * (targetIntensity > smoothBurnIntensity ? 5.0f : 2.5f));
        smoothBurnIntensity = Mth.lerp(lerpRate, smoothBurnIntensity, targetIntensity);
        smoothBurnTop = Mth.lerp(lerpRate, smoothBurnTop, Mth.clamp(tHead * 0.8f + tChest * 0.35f, 0.0f, 1.2f));
        smoothBurnBottom = Mth.lerp(lerpRate, smoothBurnBottom, Mth.clamp(tChest * 0.65f + (tLeft + tRight) * 0.35f, 0.0f, 1.2f));
        smoothBurnLeft = Mth.lerp(lerpRate, smoothBurnLeft, Mth.clamp(tLeft, 0.0f, 1.2f));
        smoothBurnRight = Mth.lerp(lerpRate, smoothBurnRight, Mth.clamp(tRight, 0.0f, 1.2f));
    }

    private static float computeLimbBurnSeverity(MedicalData.LimbData ld) {
        if (ld == null || !ld.hasBurn()) return 0.0f;
        float areaNorm = Mth.clamp(ld.getBurnArea() / 28.0f, 0.08f, 1.0f);
        float degFactor = switch (ld.getBurnDegree()) {
            case 1 -> 0.38f;
            case 2 -> 0.65f;
            case 3 -> 0.88f;
            case 4 -> 1.00f;
            default -> 0.25f;
        };
        float heatBoost = (ld.getBurnHeatTimer() > 0) ? 0.18f : 0.0f;
        return Mth.clamp(areaNorm * 0.65f + degFactor * 0.45f + heatBoost, 0.0f, 1.0f);
    }

    private static void renderThermalBurnVisuals(
            GuiGraphics graphics, Matrix4f pose, int w, int h,
            float dt, float pulse, float consciousnessBudget) {

        float maxCfg = MedicalConfig.BURN_VFX_MAX_INTENSITY.get().floatValue();
        float intensity = Mth.clamp(smoothBurnIntensity * maxCfg * consciousnessBudget, 0.0f, 0.95f);
        if (intensity <= 0.004f) return;

        float timeSec = (System.currentTimeMillis() % 120000L) / 1000.0f;
        float thermalWave = 0.5f + 0.5f * Mth.sin(timeSec * 3.4f + pulse * 0.9f);

        float bLeft = smoothBurnLeft * 0.55f;
        float bRight = smoothBurnRight * 0.55f;
        float bTop = smoothBurnTop * 0.55f;
        float bBottom = smoothBurnBottom * 0.65f;

        float innerR = 0.76f - 0.18f * intensity - 0.03f * thermalWave * intensity;
        float midR = innerR + 0.20f;

        if (currentMaxBurnDegree >= 3 || intensity > 0.55f) {
            float charAlpha = Mth.clamp(intensity * (currentMaxBurnDegree >= 4 ? 0.78f : 0.58f), 0.0f, 0.76f);
            drawEllipticalRing(pose, w, h,
                    midR - 0.04f, 0.0f,
                    1.45f, charAlpha,
                    28, 6, 2,
                    bLeft, bRight, bTop, bBottom);
        }

        float midAlpha = Mth.clamp(intensity * (0.52f + 0.25f * thermalWave), 0.0f, 0.76f);
        drawEllipticalRing(pose, w, h,
                innerR, 0.0f,
                midR + 0.08f, midAlpha,
                235, 58, 8,
                bLeft, bRight, bTop, bBottom);

        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);

        float glowAlpha = Mth.clamp(intensity * (0.28f + 0.30f * pulse + 0.18f * thermalWave), 0.0f, 0.58f);
        drawEllipticalRing(pose, w, h,
                innerR - 0.05f, 0.0f,
                innerR + 0.15f, glowAlpha,
                255, 145, 15,
                bLeft, bRight, bTop, bBottom);

        drawThermalConvectionWaves(pose, w, h, intensity, timeSec, bLeft, bRight, bTop, bBottom);
        RenderSystem.defaultBlendFunc();

        if (intensity > 0.12f) {
            drawThermalFissureCracks(pose, w, h, intensity, timeSec, pulse, bLeft, bRight, bTop, bBottom);
        }

        updateAndRenderBurnEmbers(pose, w, h, dt, intensity, timeSec);
    }

    private static void drawThermalConvectionWaves(
            Matrix4f pose, int w, int h, float intensity, float timeSec,
            float bLeft, float bRight, float bTop, float bBottom) {

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);

        int steps = 40;
        float bandDepth = Math.min(w, h) * (0.09f + 0.10f * intensity);
        for (int i = 0; i <= steps; i++) {
            float u = i / (float) steps;
            float x = u * w;
            float wave1 = Mth.sin(u * 14.0f - timeSec * 4.8f) * 0.5f + Mth.cos(u * 23.0f + timeSec * 6.2f) * 0.5f;
            float localH = bandDepth * (0.65f + 0.35f * wave1) * (1.0f + bBottom * 0.5f) * Mth.sin(u * (float) Math.PI);
            int aBot = (int) (Mth.clamp(intensity * 0.38f * (1.0f + bBottom * 0.4f), 0.0f, 0.55f) * 255.0f);

            buf.addVertex(pose, x, h + 2.0f, 0.0f).setColor(255, 95, 10, aBot);
            buf.addVertex(pose, x + wave1 * 6.0f, h - localH, 0.0f).setColor(255, 190, 40, 0);
        }

        MeshData mesh = buf.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
    }

    private static void drawThermalFissureCracks(
            Matrix4f pose, int w, int h, float intensity, float timeSec, float pulse,
            float bLeft, float bRight, float bTop, float bBottom) {

        float cx = w * 0.5f;
        float cy = h * 0.5f;
        int totalCracks = 24;
        int crackSteps = 9;

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        for (int i = 0; i < totalCracks; i++) {
            float perim = (i + 0.5f) / (float) totalCracks * 4.0f;
            int edge = ((int) perim) % 4;
            float frac = Mth.clamp(perim - (int) perim + Mth.sin(i * 19.3f) * 0.03f, 0.03f, 0.97f);

            float rx, ry;
            float dirWeight = 1.0f;
            switch (edge) {
                case 0 -> { rx = frac * w; ry = -2.0f; dirWeight += bTop; }
                case 1 -> { rx = w + 2.0f; ry = frac * h; dirWeight += bRight; }
                case 2 -> { rx = (1.0f - frac) * w; ry = h + 2.0f; dirWeight += bBottom; }
                default -> { rx = -2.0f; ry = (1.0f - frac) * h; dirWeight += bLeft; }
            }

            float dx = cx - rx;
            float dy = cy - ry;
            float dist = Mth.sqrt(dx * dx + dy * dy);
            if (dist < 1.0f) continue;
            float inX = dx / dist;
            float inY = dy / dist;
            float pX = -inY;
            float pY = inX;

            float maxLen = Math.min(w, h) * (0.09f + 0.13f * intensity * dirWeight) * (0.75f + 0.25f * Mth.sin(i * 31.7f));
            float baseA = Mth.clamp(intensity * dirWeight * 0.88f, 0.0f, 0.94f);
            float baseW = 1.25f + 1.05f * intensity;

            float prevX = rx;
            float prevY = ry;

            for (int s = 1; s <= crackSteps; s++) {
                float t = s / (float) crackSteps;
                float prevT = (s - 1) / (float) crackSteps;

                float zig = Mth.sin(s * 2.45f + i * 3.1f) * maxLen * 0.12f * Mth.sin(t * (float) Math.PI);
                float curX = rx + inX * (t * maxLen) + pX * zig;
                float curY = ry + inY * (t * maxLen) + pY * zig;

                float tap1 = 1.0f - prevT * 0.85f;
                float tap2 = 1.0f - t * 0.92f;
                int a1 = (int) (baseA * tap1 * 255.0f);
                int a2 = (int) (baseA * tap2 * 255.0f);

                addTaperedQuadSegment(buf, pose, prevX, prevY, curX, curY,
                        baseW * tap1 * 2.0f, baseW * tap2 * 2.0f,
                        38, 8, 2, a1 * 2 / 3, a2 * 2 / 3);

                float sparkPulse = 0.5f + 0.5f * Mth.sin(timeSec * 6.5f - t * 4.5f + i);
                int cr = 255;
                int cg = (int) Mth.lerp(sparkPulse, 85, 215);
                int cb = (int) Mth.lerp(sparkPulse, 5, 65);
                addTaperedQuadSegment(buf, pose, prevX, prevY, curX, curY,
                        baseW * tap1 * 0.78f, baseW * tap2 * 0.78f,
                        cr, cg, cb, a1, a2);

                prevX = curX;
                prevY = curY;
            }
        }

        MeshData mesh = buf.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
    }

    private static void updateAndRenderBurnEmbers(Matrix4f pose, int w, int h, float dt, float intensity, float timeSec) {
        emberSpawnAccum += dt * (14.0f + 42.0f * intensity);
        while (emberSpawnAccum >= 1.0f) {
            emberSpawnAccum -= 1.0f;
            for (BurnEmberParticle p : BURN_EMBERS) {
                if (!p.active) {
                    p.active = true;
                    int side = (int) (Math.random() * 10);
                    if (side < 6) {
                        p.x = (float) (Math.random() * w);
                        p.y = h + 3.0f;
                    } else if (side < 8) {
                        p.x = (float) (Math.random() * (w * 0.18f));
                        p.y = h * (0.35f + (float) Math.random() * 0.65f);
                    } else {
                        p.x = w - (float) (Math.random() * (w * 0.18f));
                        p.y = h * (0.35f + (float) Math.random() * 0.65f);
                    }
                    p.vx = (float) (Math.random() * 18.0 - 9.0);
                    p.vy = -(28.0f + (float) (Math.random() * 46.0f) * (0.6f + 0.6f * intensity));
                    p.size = 1.1f + (float) (Math.random() * 1.6f);
                    p.age = 0.0f;
                    p.maxAge = 0.85f + (float) (Math.random() * 1.15f);
                    p.phase = (float) (Math.random() * Math.PI * 2.0);
                    p.isAsh = Math.random() < 0.28;
                    break;
                }
            }
        }

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean any = false;

        for (BurnEmberParticle p : BURN_EMBERS) {
            if (!p.active) continue;
            p.age += dt;
            if (p.age >= p.maxAge) {
                p.active = false;
                continue;
            }
            float sway = Mth.sin(timeSec * 5.2f + p.phase + p.age * 3.5f) * 16.0f;
            p.x += (p.vx + sway) * dt;
            p.y += p.vy * dt;

            float life = 1.0f - (p.age / p.maxAge);
            float alpha = Mth.sin(life * (float) Math.PI) * Mth.clamp(intensity * 1.25f, 0.25f, 0.95f);
            int a = Mth.clamp((int) (alpha * 255.0f), 0, 255);
            if (a <= 2) continue;

            int r = p.isAsh ? 55 : 255;
            int g = p.isAsh ? 28 : (int) Mth.lerp(life, 75, 220);
            int b = p.isAsh ? 22 : (int) Mth.lerp(life, 10, 80);
            float hs = p.size * (0.6f + 0.4f * life);

            buf.addVertex(pose, p.x - hs, p.y + hs, 0.0f).setColor(r, g, b, a);
            buf.addVertex(pose, p.x + hs, p.y + hs, 0.0f).setColor(r, g, b, a);
            buf.addVertex(pose, p.x + hs, p.y - hs, 0.0f).setColor(r, g, b, a);
            buf.addVertex(pose, p.x - hs, p.y - hs, 0.0f).setColor(r, g, b, a);
            any = true;
        }

        MeshData mesh = buf.build();
        if (mesh != null && any) {
            BufferUploader.drawWithShader(mesh);
        }
    }
}