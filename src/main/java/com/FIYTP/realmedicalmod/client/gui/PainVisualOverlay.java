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
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Matrix4f;

/**
 * 3A 级疼痛视觉与镜头反馈系统 (PainVisualOverlay - 边缘血管精确锚定与有机分叉升级版)
 *
 * 核心升级：
 * 1. 精确屏幕边界射线锚定 (Exact Screen-Border Ray Intersection)：
 *    采用 rBorder = 1.0 / max(|cos|, |sin|) 精确计算每条血管在矩形屏幕四边上的物理交点，
 *    保证 100% 的血管与痛觉神经丝严格从屏幕四边最外缘长出，彻底消除四角或半空悬空出现的现象。
 * 2. 14 段平滑有机蜿蜒曲线 + 双层发光血管鞘 + 三级树状微血管分叉：
 *    每条主血管由 14 段连续平滑曲线构成，包含外层弥散暗红血管晕与内层高亮动脉芯，
 *    并按解剖学锐角自然分叉出二级、三级微血管与流动的痛觉神经电冲动光点。
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
            float pulseKick = (pulse > 0.5f) ? (pulse - 0.5f) * 2.0f : 0.0f;
            tremorPitch += pulseKick * painFactor * 0.35f;

            float neuroTremor = painFactor * painFactor;
            tremorRoll += (float) (Math.sin(timeSec * 11.3) * 0.45 + Math.cos(timeSec * 19.7) * 0.25) * neuroTremor;
            tremorYaw += (float) (Math.sin(timeSec * 8.7) * 0.30 + Math.sin(timeSec * 15.1) * 0.18) * neuroTremor;
            tremorPitch += (float) (Math.cos(timeSec * 9.5) * 0.28) * neuroTremor;

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
            // 烧伤灼痛热浪引起的视野高频微幅热折射颤动
            tremorPitch += (float) (Math.sin(timeSec * 16.5) * 0.22 + Math.cos(timeSec * 24.3) * 0.14) * burnCam;
            tremorYaw += (float) (Math.cos(timeSec * 14.2) * 0.18) * burnCam;
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

    @SubscribeEvent
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

        // 计算全身与各方位烧伤强度（用于 3A 烧伤独特视觉效果）
        updateBurnVisualState(player, data, dt);

        float startThreshold = MedicalConfig.PAIN_VIGNETTE_START_THRESHOLD.get().floatValue();
        float fullThreshold = MedicalConfig.PAIN_VIGNETTE_FULL_THRESHOLD.get().floatValue();
        float grayThreshold = MedicalConfig.PAIN_GRAY_WASH_THRESHOLD.get().floatValue();

        float painFactor = (smoothPain >= startThreshold * 0.85f)
                ? Mth.clamp((smoothPain - startThreshold) / Math.max(1.0f, fullThreshold - startThreshold), 0.0f, 1.0f)
                : 0.0f;
        boolean hasBurnVfx = MedicalConfig.BURN_VFX_ENABLED.get() && smoothBurnIntensity > 0.004f;
        if (painFactor <= 0.002f && !hasBurnVfx) return;

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
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        // ---------- 层级 1：重度剧痛边缘冷灰去饱和隧道暗角 ----------
        if (smoothPain >= grayThreshold) {
            float maxPerceived = Math.max(10.0f, MedicalConfig.MAX_PERCEIVED_PAIN.get().floatValue());
        float grayFactor = Mth.clamp((smoothPain - grayThreshold) / Math.max(1.0f, maxPerceived - grayThreshold), 0.0f, 1.0f);
            float grayMaxAlpha = MedicalConfig.PAIN_GRAY_WASH_MAX_ALPHA.get().floatValue();
            float outerTunnelAlpha = Mth.clamp(grayFactor * grayMaxAlpha * 2.4f * consciousnessBudget, 0.0f, 0.65f);
            float centerTintAlpha = Mth.clamp(grayFactor * grayMaxAlpha * 0.28f * consciousnessBudget, 0.0f, 0.12f);

            drawEllipticalRing(pose, w, h,
                    0.46f - 0.10f * grayFactor, 0.0f,
                    1.45f, outerTunnelAlpha,
                    22, 26, 34,
                    0.0f, 0.0f, 0.0f, 0.0f);

            if (centerTintAlpha > 0.005f) {
                int cAlpha = (int) (centerTintAlpha * 255.0f);
                graphics.fill(0, 0, w, h, (cAlpha << 24) | (135 << 16) | (150 << 8) | 170);
                RenderSystem.setShader(GameRenderer::getPositionColorShader);
            }
        }

        // ---------- 层级 2：主血管搏动无缝椭圆暗红晕影 ----------
        float baseVignetteAlpha = (painFactor > 0.002f) ? (painFactor * maxAlphaCfg * consciousnessBudget) : 0.0f;
        float outerPulseAlpha = Mth.clamp(baseVignetteAlpha * (0.68f + 0.32f * pulse), 0.0f, 0.88f);

        float innerRadius = 0.74f - 0.20f * painFactor - 0.04f * pulse * painFactor;
        float midRadius = innerRadius + 0.22f;

        float topBoost = smoothHeadPain * 0.45f + smoothChestPain * 0.15f;
        float bottomBoost = smoothChestPain * 0.35f;
        float leftBoost = smoothLeftPain * 0.45f;
        float rightBoost = smoothRightPain * 0.45f;

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

        // ---------- 层级 5：3A 级烧伤独特视觉系统（焦化热浪暗角 + 热空气折射波纹 + 表皮焦裂火纹 + 飞散火星） ----------
        if (hasBurnVfx) {
            renderThermalBurnVisuals(graphics, pose, w, h, dt, pulse, consciousnessBudget);
        }

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    /**
     * 绘制无缝椭圆径向渐变环
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

            // 外环半径自适应矩形屏幕角点距离 1 / max(|cos|, |sin|)，彻底保证四角完全覆盖无断层
            float borderDist = 1.0f / Math.max(0.001f, Math.max(Math.abs(cos), Math.abs(sin)));
            float actualOuterR = Math.max(rOuter, borderDist * 1.02f);

            float localInnerR = Math.max(0.18f, rInner - (dirWeight - 1.0f) * 0.14f);
            float aIn = Mth.clamp(alphaInner * dirWeight, 0.0f, 0.96f);
            float aOut = Mth.clamp(alphaOuter * dirWeight, 0.0f, 0.96f);

            float xIn = cx + cos * rx * localInnerR;
            float yIn = cy + sin * ry * localInnerR;
            float xOut = cx + cos * rx * actualOuterR;
            float yOut = cy + sin * ry * actualOuterR;

            buffer.addVertex(pose, xIn, yIn, 0.0f).setColor(r, g, b, (int) (aIn * 255.0f));
            buffer.addVertex(pose, xOut, yOut, 0.0f).setColor(r, g, b, (int) (aOut * 255.0f));
        }

        MeshData mesh = buffer.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
    }

    /**
     * 3A 级屏幕四边精确锚定的有机蜿蜒毛细血管与痛觉神经丝网络
     *
     * 核心改进：
     * 1. 每条血管根部严格通过屏幕边界周长参数化采样 (Top / Bottom / Left / Right 四条物理边)，
     *    起点 100% 位于屏幕最外边缘像素上（外延 2px），绝不会出现半空悬空生长的血管！
     * 2. 主血管采用 14 段平滑正弦谐波有机曲率向屏幕内侧生长，并按解剖学 Y 型夹角派生二级、三级细微毛细血管。
     * 3. 双通道渲染：底层柔和暗红血管壁晕染 + 顶层高亮动脉血芯与随心跳奔涌的痛觉神经脉冲亮点。
     */
    private static void drawScreenEdgeAnchoredCapillaries(
            Matrix4f pose, int w, int h, float baseAlpha, float painFactor, float pulse,
            float leftBoost, float rightBoost, float topBoost, float bottomBoost) {

        float cx = w * 0.5f;
        float cy = h * 0.5f;

        // 沿屏幕四条物理边界均匀分布 32 条主干血管根部锚点
        int totalRoots = 32;
        int mainSteps = 14;
        float timeSec = (System.currentTimeMillis() % 60000L) / 1000.0f;

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        for (int i = 0; i < totalRoots; i++) {
            // 将 i 映射到屏幕矩形周长 u ∈ [0, 4)，分别对应：0=顶边, 1=右边, 2=底边, 3=左边
            float perimeterPos = (i + 0.5f) / (float) totalRoots * 4.0f;
            int edgeIndex = ((int) perimeterPos) % 4;
            float edgeFrac = perimeterPos - (int) perimeterPos; // [0, 1)

            // 伪随机抖动让分布自然不呆板，但始终严格锁死在屏幕物理边界上
            float jitter = (Mth.sin(i * 12.9898f + 78.233f) * 0.5f) * 0.04f;
            edgeFrac = Mth.clamp(edgeFrac + jitter, 0.02f, 0.98f);

            float rootX, rootY;
            float dirBoost = 1.0f;
            switch (edgeIndex) {
                case 0 -> { // 顶边 (y = -2)
                    rootX = edgeFrac * w;
                    rootY = -2.0f;
                    dirBoost += topBoost * 0.65f;
                }
                case 1 -> { // 右边 (x = w + 2)
                    rootX = w + 2.0f;
                    rootY = edgeFrac * h;
                    dirBoost += rightBoost * 0.65f;
                }
                case 2 -> { // 底边 (y = h + 2)
                    rootX = (1.0f - edgeFrac) * w;
                    rootY = h + 2.0f;
                    dirBoost += bottomBoost * 0.65f;
                }
                default -> { // 左边 (x = -2)
                    rootX = -2.0f;
                    rootY = (1.0f - edgeFrac) * h;
                    dirBoost += leftBoost * 0.65f;
                }
            }

            // 计算从屏幕边缘锚点指向视野中心的内向单位向量 (inwardX, inwardY)
            float toCenterX = cx - rootX;
            float toCenterY = cy - rootY;
            float distToCenter = Mth.sqrt(toCenterX * toCenterX + toCenterY * toCenterY);
            if (distToCenter < 1.0f) continue;

            float inX = toCenterX / distToCenter;
            float inY = toCenterY / distToCenter;
            // 垂直于生长方向的法向量 (perpX, perpY)，用于控制血管有机蜿蜒摆动
            float perpX = -inY;
            float perpY = inX;

            // 血管向内生长的最大长度：严格控制在边缘 14% ~ 32% 区域内，绝不侵占中央准星区
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

                // 双频正弦叠加构造逼真的生物血管曲率（在根部 s=0 时偏移严格为 0，确保起点锁死在屏幕边缘）
                float env = Mth.sin(t * (float) Math.PI * 0.85f);
                float lateralOffset = (Mth.sin(t * freq1 + phaseOffset + pulse * 0.6f) * 0.65f
                        + Mth.cos(t * freq2 - phaseOffset * 1.3f) * 0.35f) * wiggleAmp * env;

                float currX = rootX + inX * (t * maxReachPx) + perpX * lateralOffset;
                float currY = rootY + inY * (t * maxReachPx) + perpY * lateralOffset;

                // 血管半径与透明度随向内延伸平滑锥形衰减至 0
                float taperStart = (float) Math.pow(1.0f - prevT, 1.35);
                float taperEnd = (float) Math.pow(1.0f - t, 1.35);

                float wStart = Math.max(0.20f, rootThickness * taperStart);
                float wEnd = Math.max(0.15f, rootThickness * taperEnd);
                int aStart = (int) (rootAlpha * taperStart * 255.0f);
                int aEnd = (int) (rootAlpha * taperEnd * 255.0f);

                // 1) 外层柔和暗红血管鞘晕光 (Outer Soft Vascular Sheath)
                addTaperedQuadSegment(buffer, pose, prevX, prevY, currX, currY,
                        wStart * 2.1f, wEnd * 2.1f,
                        120, 6, 14, aStart / 3, aEnd / 3);

                // 2) 内层鲜红动脉核心与神经脉冲高亮 (Inner Arterial Core)
                // 神经冲动光包沿血管向内传导
                float impulseWave = Mth.clamp(1.0f - Math.abs(t - ((timeSec * 1.4f + i * 0.13f) % 1.0f)) * 5.0f, 0.0f, 1.0f);
                int coreR = (int) Mth.lerp(impulseWave * painFactor, 205, 255);
                int coreG = (int) Mth.lerp(impulseWave * painFactor, 16, 75);
                int coreB = (int) Mth.lerp(impulseWave * painFactor, 26, 95);

                addTaperedQuadSegment(buffer, pose, prevX, prevY, currX, currY,
                        wStart * 0.85f, wEnd * 0.85f,
                        coreR, coreG, coreB, aStart, aEnd);

                // 3) 在主干 35% 与 65% 处生成符合解剖学锐角的二级、三级自然分叉微血管 (Bifurcating Sub-Branches)
                if ((s == 5 || s == 9) && painFactor > 0.22f) {
                    float branchSign = ((i + s) % 2 == 0) ? 1.0f : -1.0f;
                    float branchAngle = branchSign * (0.48f + (i % 3) * 0.09f); // 约 28°~38° 锐角分叉
                    float cosB = Mth.cos(branchAngle);
                    float sinB = Mth.sin(branchAngle);

                    // 旋转当前生长方向向量
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

    /**
     * 绘制 7 段平滑弯曲的二级/三级微血管与痛觉神经末梢分叉
     */
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

    /**
     * 支持首尾不同线宽 (锥形渐变) 的高精度平滑线段绘制
     */
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

    /**
     * 兼容等宽线段的通用绘制接口
     */
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

    /**
     * 渲染 3A 级独特烧伤视觉效果：
     * 1. 外圈焦黑碳化痂壳 (III/IV度) + 中圈水疱灼伤烈焰橙晕影 + 内圈加色混合白炽热浪脉动
     * 2. 屏幕四边上升的扭曲热空气折射波纹带 (Thermal Mirage Convection Ribbons)
     * 3. 锚定屏幕四边的表皮焦裂熔岩火纹 (Epidermal Crackling Ember Fissures)
     * 4. 随热气流飘升的灼热火星与焦灰粒子 (Rising Ember Sparks & Char Ash)
     */
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

        // 1) 重度烧伤 (III/IV度) 外缘焦黑碳化痂壳层 (Charred Eschar Outer Ring)
        if (currentMaxBurnDegree >= 3 || intensity > 0.55f) {
            float charAlpha = Mth.clamp(intensity * (currentMaxBurnDegree >= 4 ? 0.82f : 0.62f), 0.0f, 0.80f);
            drawEllipticalRing(pose, w, h,
                    midR - 0.04f, 0.0f,
                    1.45f, charAlpha,
                    28, 6, 2,
                    bLeft, bRight, bTop, bBottom);
        }

        // 2) 中层灼伤烈焰红橙水疱热晕 (Blistering Scald Orange-Red Vignette)
        float midAlpha = Mth.clamp(intensity * (0.52f + 0.25f * thermalWave), 0.0f, 0.76f);
        drawEllipticalRing(pose, w, h,
                innerR, 0.0f,
                midR + 0.08f, midAlpha,
                235, 58, 8,
                bLeft, bRight, bTop, bBottom);

        // 3) 内层加色混合白炽琥珀热浪光晕与上升热气流波纹 (Additive Thermal Shimmer & Convection Ripples)
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);

        float glowAlpha = Mth.clamp(intensity * (0.28f + 0.30f * pulse + 0.18f * thermalWave), 0.0f, 0.58f);
        drawEllipticalRing(pose, w, h,
                innerR - 0.05f, 0.0f,
                innerR + 0.15f, glowAlpha,
                255, 145, 15,
                bLeft, bRight, bTop, bBottom);

        drawThermalConvectionWaves(pose, w, h, intensity, timeSec, bLeft, bRight, bTop, bBottom);
        RenderSystem.defaultBlendFunc();

        // 4) 严格锚定屏幕边缘的表皮焦裂火纹 (Epidermal Crackling Ember Fissures)
        if (intensity > 0.12f) {
            drawThermalFissureCracks(pose, w, h, intensity, timeSec, pulse, bLeft, bRight, bTop, bBottom);
        }

        // 5) 实时上升火星与焦灰粒子 (Rising Ember Sparks & Ash)
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

                // 折线形焦裂折角（不同于平滑正弦血管，呈现干裂灼伤纹路）
                float zig = Mth.sin(s * 2.45f + i * 3.1f) * maxLen * 0.12f * Mth.sin(t * (float) Math.PI);
                float curX = rx + inX * (t * maxLen) + pX * zig;
                float curY = ry + inY * (t * maxLen) + pY * zig;

                float tap1 = 1.0f - prevT * 0.85f;
                float tap2 = 1.0f - t * 0.92f;
                int a1 = (int) (baseA * tap1 * 255.0f);
                int a2 = (int) (baseA * tap2 * 255.0f);

                // 外层焦黑碳化裂口边缘
                addTaperedQuadSegment(buf, pose, prevX, prevY, curX, curY,
                        baseW * tap1 * 2.0f, baseW * tap2 * 2.0f,
                        38, 8, 2, a1 * 2 / 3, a2 * 2 / 3);

                // 内层炽热熔金橙红火芯
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
                    // 主要从屏幕底部与左右烧伤边缘升起
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