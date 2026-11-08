package com.FIYTP.realmedicalmod.client.gui;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.capability.MedicalDataCapability;
import com.FIYTP.realmedicalmod.client.BrainDamageClientState;
import com.FIYTP.realmedicalmod.config.MedicalConfig;
import com.FIYTP.realmedicalmod.treatment.ModItems;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
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
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Matrix4f;

/**
 * 3A 级心血管危急与生命体征崩溃全屏视觉系统 (CriticalOverlay)
 *
 * 全面重构原有的粗糙全屏纯色矩形填充 (graphics.fill)，对标《赛博朋克 2077》(Relic 故障/创伤小组)、《逃离塔科夫》与《战地》濒死视觉：
 * 1. 心动过速 (Tachycardia & Pre-VFib Overload, HR >= 120 BPM / isHeartEffectSevere / vfibTimer > 0)：
 *    - 高频动脉重搏椭圆充血环 + 收缩期峰值 RGB 棱镜色差分离 (Chromatic Aberration)
 *    - 3D 摄像机随高心率产生心搏共振微震与 FOV 焦距脉动
 *    - 当心率过高进入室颤倒计时 (vfibTimer > 0) 时，右上/中上方浮现全息心律过载预警仪与电弧微丝
 * 2. 心室纤颤 (Ventricular Fibrillation - VFib, data.isInVFib())：
 *    - 三频非周期混沌心肌痉挛径向暗角（彻底告别纯红全屏频闪块）+ 剧烈红/青色差错位环
 *    - 视野四周迸发不规则生物电除颤电弧丝 (Bio-Electric Fibrillation Arcs) 与全息水平错位扫描线
 *    - 顶部全息急救告警舱：实时绘制高频乱颤的 VFib 混沌心电波形与血氧/脑健康塌陷读数 + 3D 镜头痉挛摇晃
 * 3. 失血至 <= 3000mL 濒危状态与 20 秒倒计时 (Hypovolemic Shock Countdown, data.isInCriticalState())：
 *    - 渐进式失血性休克冰冷苍白去饱和环 + 深凝血暗红椭圆隧道收缩（随血容量下降与 20s 倒计时递减逐步收窄）
 *    - 3A 全息濒危倒计时核心舱：七段数码管毫秒级跳动倒计时、双向对称能量衰减条、实时血容量/MAP 灌注压遥测
 *    - 最后 5 秒叠加濒死代偿衰竭黑视脉冲，并在归零触发心脏骤停瞬间无缝衔接平线冲击特效
 * 4. 心脏骤停 (Cardiac Arrest & Asystole Flatline, data.isCardiacArrest())：
 *    - 骤停跃迁瞬间触发冷白/猩红除颤级冲击闪光与视野向中心坍缩
 *    - 深渊缺氧黑红三层椭圆径向暗角（随脑健康流失逐步合拢）+ 3D 镜头失重倾斜 (Camera Roll Collapse)
 *    - 临床监护仪“平线 (Asystole Flatline)”全息告警舱：动态扫描的 0.0mV 亮红平直线 + 脑存活度与血氧衰竭倒数条
 * 5. 呼吸异常、血胸与颅内出血暗角：
 *    - 全部升级为 48 段极坐标无缝椭圆径向渐变网格，彻底消除原有的矩形硬切边。
 */
@EventBusSubscriber(modid = "realmedicalmod", value = Dist.CLIENT)
public class CriticalOverlay {

    private static int withAlpha(int rgb, float alpha) {
        int a = Mth.clamp((int) (alpha * 255.0f), 0, 255);
        return (a << 24) | (rgb & 0x00FFFFFF);
    }

    private static final int C_ALERT_RED = 0xFFFF2040;
    private static final int C_CRIMSON = 0xFFFF1744;
    private static final int C_WARN_AMBER = 0xFFFFB300;
    private static final int C_NEON_CYAN = 0xFF00F0FF;
    private static final int C_OXYGEN_BLUE = 0xFF00D2FF;

    private static long lastFrameNs = 0L;
    private static float smoothHr = 75.0f;
    private static float heartPhase = 0.0f;
    private static float tachycardiaFactor = 0.0f;
    private static float criticalUrgency = 0.0f;
    private static float arrestTransitionFlash = 0.0f;
    private static float arrestCollapseProgress = 0.0f;
    private static boolean wasCardiacArrest = false;

    // 阿片类药物推注「多巴胺欣快感 (Opioid Euphoria)」状态追踪
    private static int lastOpioidConcentration = 0;
    private static float euphoriaRushTimer = 0.0f;
    private static float smoothEuphoriaIntensity = 0.0f;

    // 肾上腺素激增 (Adrenaline Rush) 3A 战斗聚焦与广角脉冲状态
    private static float lastAdrenalineLevel = 0.0f;
    private static float adrenalineSurgeTimer = 0.0f;
    private static float smoothAdrenalineIntensity = 0.0f;

    // ==================== 3A 脑损伤视觉系统（历史闪回、画面定格撕裂震荡、四态神经变色）状态 ====================
    private static TextureTarget currentSceneTarget = null;
    private static TextureTarget freezeFrameTarget = null;
    private static final TextureTarget[] flashbackHistoryTargets = new TextureTarget[3];
    private static int fboAllocatedW = -1;
    private static int fboAllocatedH = -1;
    private static int historyWriteIdx = 0;
    private static long lastHistoryCaptureMs = 0L;
    private static long lastFreezeStutterMs = 0L;

    private static int lastBrainHealth = -1;
    private static boolean wasBrainControlInverted = false;
    private static float brainEpisodeCooldownSec = 2.5f;

    private static float brainFlashbackTimer = 0.0f;
    private static float brainFlashbackDuration = 1.6f;
    private static float brainFlashbackPeakIntensity = 0.0f;
    private static int activeFlashbackSlot = 0;

    private static float brainFreezeShakeTimer = 0.0f;
    private static float brainFreezeShakeDuration = 1.35f;
    private static float brainFreezeWindowSec = 0.32f;
    private static float brainFreezePeakIntensity = 0.0f;
    private static long brainEpisodeSeed = 1337L;

    private static float brainColorShiftTimer = 0.0f;
    private static float brainColorShiftDuration = 2.8f;
    private static float smoothBrainColorShift = 0.0f;
    private static int brainColorShiftMode = 0; // 0: 缺血紫青, 1: 血肿猩红琥珀, 2: 视皮层负片反相+冷蓝失色, 3: 突触迷幻双色分离

    /**
     * 3D 摄像机生理反馈：心动过速心搏微震、室颤电痉挛抖动、失血休克眩晕与心脏骤停失重倾斜
     */
    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.isPaused() || mc.options.hideGui || player.isSpectator()) return;

        MedicalData data = player.getData(MedicalDataCapability.MEDICAL_DATA);
        if (data == null) return;

        double t = System.currentTimeMillis() / 1000.0;
        float roll = 0.0f;
        float pitch = 0.0f;
        float yaw = 0.0f;

        if (data.isCardiacArrest()) {
            // 心脏骤停：镜头呈现失重下沉与缓慢濒死侧倾
            float collapse = Mth.clamp(arrestCollapseProgress, 0.0f, 1.0f);
            roll += 6.5f * collapse + (float) Math.sin(t * 0.9) * 0.8f;
            pitch += 4.0f * collapse + (float) Math.cos(t * 0.7) * 0.6f;
        } else if (data.isInVFib()) {
            // 心室纤颤：高频多频无序心肌痉挛震颤
            roll += (float) (Math.sin(t * 23.0) * 1.15 + Math.cos(t * 37.0) * 0.75 + Math.sin(t * 5.2) * 1.4);
            pitch += (float) (Math.cos(t * 19.5) * 0.85 + Math.sin(t * 31.0) * 0.55);
            yaw += (float) (Math.sin(t * 17.3) * 0.70);
        } else {
            // 失血 <= 3000mL 濒危状态：低血容量眩晕摇晃
            if (data.isInCriticalState() && criticalUrgency > 0.01f) {
                float sway = 0.4f + 1.4f * criticalUrgency;
                roll += (float) Math.sin(t * 2.1) * sway;
                pitch += (float) Math.cos(t * 1.6) * (sway * 0.6f);
            }
            // 心动过速：随高频心搏收缩产生急促动脉搏动微颤
            if (tachycardiaFactor > 0.05f) {
                float pulse = PainVisualOverlay.evaluateDicroticPulse(heartPhase);
                float kick = (pulse > 0.55f) ? ((pulse - 0.55f) / 0.45f) : 0.0f;
                pitch -= kick * tachycardiaFactor * 0.48f;
                roll += (float) Math.sin(t * 16.0) * 0.32f * tachycardiaFactor * kick;
            }
        }

        // ★ 3A 脑损伤镜头震荡：画面定格瞬间产生阶跃抽搐，随后进入欠阻尼高频脑震荡摇晃与闪回漂移 ★
        if (MedicalConfig.BRAIN_VFX_UPGRADE_ENABLED.get()) {
            float shakeAmpCfg = MedicalConfig.BRAIN_VFX_SHAKE_AMPLITUDE.get().floatValue();
            if (brainFreezeShakeTimer > 0.001f) {
                float p = 1.0f - Mth.clamp(brainFreezeShakeTimer / Math.max(0.1f, brainFreezeShakeDuration), 0.0f, 1.0f);
                float env = (float) Math.pow(1.0f - p, 1.65) * brainFreezePeakIntensity * shakeAmpCfg;
                // 定格抽搐采样（每 55ms 跳变一次角度，模拟脑干受损的痉挛定格感）
                double qTime = Math.floor(t * 18.0) / 18.0;
                float joltRoll = (float) (Math.sin(qTime * 43.0 + brainEpisodeSeed) * 6.8 + Math.cos(t * 58.0) * 3.2) * env;
                float joltPitch = (float) (Math.cos(qTime * 37.0 + brainEpisodeSeed * 0.5) * 4.6 + Math.sin(t * 49.0) * 2.4) * env;
                float joltYaw = (float) (Math.sin(qTime * 31.0 - brainEpisodeSeed) * 5.2) * env;
                roll += joltRoll;
                pitch += joltPitch;
                yaw += joltYaw;
            }
            if (brainFlashbackTimer > 0.001f) {
                float fp = 1.0f - Mth.clamp(brainFlashbackTimer / Math.max(0.1f, brainFlashbackDuration), 0.0f, 1.0f);
                float fWave = Mth.sin(fp * Mth.PI) * brainFlashbackPeakIntensity;
                roll += (float) Math.sin(t * 4.5 + brainEpisodeSeed) * 3.4f * fWave;
                pitch += (float) Math.cos(t * 3.2) * 2.2f * fWave;
            }
        }

        if (Math.abs(roll) > 0.001f || Math.abs(pitch) > 0.001f || Math.abs(yaw) > 0.001f) {
            event.setRoll(event.getRoll() + roll);
            event.setPitch(event.getPitch() + pitch);
            event.setYaw(event.getYaw() + yaw);
        }
    }

    /**
     * 3D 视场角 (FOV) 生理脉动：心动过速与濒危休克下的肾上腺素/缺血焦距收缩
     */
    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.isPaused() || mc.options.hideGui || player.isSpectator()) return;

        MedicalData data = player.getData(MedicalDataCapability.MEDICAL_DATA);
        if (data == null) return;

        float fovDelta = 0.0f;
        if (data.isCardiacArrest()) {
            // 骤停视野向内塌陷
            fovDelta -= 6.0f * Mth.clamp(arrestCollapseProgress, 0.0f, 1.0f);
        } else if (data.isInVFib()) {
            float pulse = (float) (Math.sin(System.currentTimeMillis() * 0.025) * 1.5);
            fovDelta -= 3.5f + pulse;
        } else if (tachycardiaFactor > 0.05f || (data.isInCriticalState() && criticalUrgency > 0.01f)) {
            float pulse = PainVisualOverlay.evaluateDicroticPulse(heartPhase);
            fovDelta -= pulse * tachycardiaFactor * 1.35f;
            if (data.isInCriticalState()) {
                fovDelta -= criticalUrgency * 3.0f;
            }
        }

        if (MedicalConfig.BRAIN_VFX_UPGRADE_ENABLED.get()) {
            if (brainFreezeShakeTimer > 0.001f) {
                float p = 1.0f - Mth.clamp(brainFreezeShakeTimer / Math.max(0.1f, brainFreezeShakeDuration), 0.0f, 1.0f);
                float env = (float) Math.pow(1.0f - p, 1.8) * brainFreezePeakIntensity;
                fovDelta += (float) Math.sin(p * Math.PI * 6.0) * 8.5f * env;
            }
            if (brainFlashbackTimer > 0.001f) {
                float fp = 1.0f - Mth.clamp(brainFlashbackTimer / Math.max(0.1f, brainFlashbackDuration), 0.0f, 1.0f);
                fovDelta -= Mth.sin(fp * Mth.PI) * 6.5f * brainFlashbackPeakIntensity;
            }
        }

        // ★ 肾上腺素激增：交感神经瞳孔散大与战斗广角反射 (FOV +4.8°) ★
        if (smoothAdrenalineIntensity > 0.02f && !data.isCardiacArrest()) {
            fovDelta += smoothAdrenalineIntensity * 4.8f;
        }

        if (Math.abs(fovDelta) > 0.01f) {
            event.setFOV(event.getFOV() + fovDelta);
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null) return;
        if (mc.options.hideGui || player.isSpectator()) return;

        MedicalData data = player.getData(MedicalDataCapability.MEDICAL_DATA);
        if (data == null) return;

        // 1. 帧时间步进与连续生理相位积分
        long nowNs = System.nanoTime();
        if (lastFrameNs == 0L) lastFrameNs = nowNs;
        float dt = Mth.clamp((nowNs - lastFrameNs) / 1_000_000_000.0f, 0.0005f, 0.1f);
        lastFrameNs = nowNs;
        long nowMs = System.currentTimeMillis();

        int targetHr = data.isCardiacArrest() ? 0 : Mth.clamp(data.getHeartRate(), 30, 240);
        smoothHr = Mth.lerp(Math.min(1.0f, dt * 4.0f), smoothHr, targetHr);
        if (!data.isCardiacArrest()) {
            heartPhase = (heartPhase + dt * (Math.max(30.0f, smoothHr) / 60.0f)) % 1.0f;
        }

        // 检测心脏骤停跃迁瞬间，触发除颤级冲击白闪与坍缩动画
        boolean isArrest = data.isCardiacArrest();
        if (isArrest && !wasCardiacArrest) {
            arrestTransitionFlash = 1.0f;
            arrestCollapseProgress = 0.0f;
        }
        wasCardiacArrest = isArrest;

        if (isArrest) {
            arrestTransitionFlash = Math.max(0.0f, arrestTransitionFlash - dt * 2.2f);
            arrestCollapseProgress = Math.min(1.0f, arrestCollapseProgress + dt * 0.65f);
        } else {
            arrestTransitionFlash = 0.0f;
            arrestCollapseProgress = Math.max(0.0f, arrestCollapseProgress - dt * 2.0f);
        }

        // 计算心动过速强度因子 (HR 120 -> 0.0, HR 195 -> 1.0)
        float rawTachy = (smoothHr >= 120.0f && !isArrest && !data.isInVFib())
                ? Mth.clamp((smoothHr - 120.0f) / 75.0f, 0.0f, 1.0f)
                : 0.0f;
        if (data.isHeartEffectSevere() && !isArrest && !data.isInVFib()) {
            rawTachy = Math.max(rawTachy, 0.65f);
        }
        tachycardiaFactor = Mth.lerp(Math.min(1.0f, dt * 3.5f), tachycardiaFactor, rawTachy);

        // 计算失血 <= CRITICAL_BLOOD_VOLUME 濒危紧迫度（从 MedicalConfig 读取）
        if (data.isInCriticalState() && data.getCriticalTimer() > 0) {
            float maxCritTimer = Math.max(1.0f, MedicalConfig.CRITICAL_TIMER_MAX.get().floatValue());
            float critBloodVol = Math.max(1.0f, MedicalConfig.CRITICAL_BLOOD_VOLUME.get().floatValue());
            float timerUrgency = 1.0f - Mth.clamp(data.getCriticalTimer() / maxCritTimer, 0.0f, 1.0f);
            float bloodUrgency = Mth.clamp((critBloodVol - data.getBloodVolume()) / (critBloodVol * 0.5f), 0.0f, 1.0f);
            criticalUrgency = Mth.clamp(0.30f + timerUrgency * 0.55f + bloodUrgency * 0.15f, 0.0f, 1.0f);
        } else {
            criticalUrgency = Mth.lerp(Math.min(1.0f, dt * 4.0f), criticalUrgency, 0.0f);
        }

        GuiGraphics g = event.getGuiGraphics();
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();

        // ===== 优先级 1：心脏骤停 (Cardiac Arrest & Asystole Flatline) =====
        if (isArrest) {
            renderCardiacArrest3A(g, mc.font, w, h, data, nowMs);
            return;
        }

        // ===== 优先级 2：心室纤颤 (Ventricular Fibrillation - VFib) =====
        if (data.isInVFib()) {
            renderVFib3A(g, mc.font, w, h, data, nowMs);
            return;
        }

        // ===== 优先级 2.5：阿片类药物多巴胺欣快感视觉层、肾上腺素战斗脉冲层 & II/III 级戒断行动受限提示 =====
        updateAndRenderOpioidEuphoria3A(g, w, h, data, dt, nowMs);
        updateAndRenderAdrenalineRush3A(g, mc.font, w, h, data, dt, nowMs);
        if (ModItems.isInWithdrawalMovementRestriction(data)) {
            int tier = ModItems.getAddictionTier(data.getAddictionLevel());
            float wPulse = 0.55f + 0.45f * Mth.sin(nowMs * 0.008f);
            PainVisualOverlay.drawEllipticalRing(g.pose().last().pose(), w, h,
                    0.72f, 0.0f, 1.35f, (tier >= 3 ? 0.38f : 0.24f) * wPulse,
                    185, 20, 140, 0.0f, 0.0f, 0.0f, 0.0f);

        }

        // ===== 优先级 2.8：3A 脑损伤视觉层（真实帧缓冲历史闪回、画面定格撕裂震荡、四态皮层神经变色） =====
        updateAndRenderBrainDamageVfx3A(g, mc, w, h, data, dt, nowMs);

        // ===== 优先级 3：背景生理障碍层（心动过速、呼吸异常、血胸、颅内出血无缝径向暗角） =====
        renderSubCriticalRadialLayers(g, mc.font, w, h, data, nowMs);

        // ===== 优先级 4：失血至 <= 3000mL 的濒危状态 + 20 秒生命倒计时 =====
        if (data.isInCriticalState() && data.getCriticalTimer() > 0) {
            renderCriticalHypovolemicCountdown3A(g, mc.font, w, h, data, nowMs);
        }
    }


    // =========================================================================
    // 2.5 注射阿片类药物「多巴胺欣快感 / 极乐微醺」3A 视觉特效 (Opioid Euphoria Visuals)
    // =========================================================================
    private static void updateAndRenderOpioidEuphoria3A(GuiGraphics g, int w, int h, MedicalData data, float dt, long nowMs) {
        if (!MedicalConfig.OPIOID_EUPHORIA_ENABLED.get()) {
            lastOpioidConcentration = data.getOpioidConcentration();
            return;
        }

        int curOpioid = data.getOpioidConcentration();
        // 检测玩家刚刚完成阿片推注（血药浓度跃升）：触发强烈的「多巴胺暖流冲顶波 (Euphoria Rush)」
        if (curOpioid > lastOpioidConcentration + 2) {
            euphoriaRushTimer = MedicalConfig.OPIOID_EUPHORIA_RUSH_DURATION_SEC.get().floatValue();
        }
        lastOpioidConcentration = curOpioid;

        if (euphoriaRushTimer > 0.0f) {
            euphoriaRushTimer = Math.max(0.0f, euphoriaRushTimer - dt);
        }

        // 若处于重度窒息或呼吸骤停濒死期，欣快感被缺氧紫绀逐渐吞噬；正常镇痛期内呈现温暖丝绒光晕
        int apneaThresh = MedicalConfig.OPIOID_APNEA_THRESHOLD.get();
        float rushFactor = Mth.clamp(euphoriaRushTimer / Math.max(1.0f, MedicalConfig.OPIOID_EUPHORIA_RUSH_DURATION_SEC.get().floatValue()), 0.0f, 1.0f);
        float sustainFactor = (curOpioid > 0 && curOpioid < apneaThresh && data.getBloodOxygen() >= 75)
                ? Mth.clamp(curOpioid / 55.0f, 0.15f, 0.85f)
                : 0.0f;
        float targetEuphoria = Math.max(rushFactor, sustainFactor * 0.72f);
        smoothEuphoriaIntensity = Mth.lerp(Math.min(1.0f, dt * 2.8f), smoothEuphoriaIntensity, targetEuphoria);

        if (smoothEuphoriaIntensity <= 0.01f) {
            return;
        }

        float maxAlphaCfg = MedicalConfig.OPIOID_EUPHORIA_MAX_ALPHA.get().floatValue();
        float slowBreath = 0.5f + 0.5f * Mth.sin(nowMs * 0.0018f); // 极其舒缓柔和的慢节律呼吸波

        Matrix4f pose = g.pose().last().pose();
        RenderSystem.enableBlend();
        // 采用加法发光混合 (SRC_ALPHA, ONE)，呈现温暖、轻盈、如梦似幻的多巴胺丝绒辉光
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        // 1. 外圈：温暖琥珀香槟金丝绒柔光 (Warm Champagne Gold Bloom, RGB: 255, 195, 95)
        float innerR = Mth.lerp(smoothEuphoriaIntensity, 0.78f, 0.46f) + 0.04f * slowBreath;
        float goldAlpha = Mth.clamp(smoothEuphoriaIntensity * maxAlphaCfg * (0.75f + 0.25f * slowBreath), 0.0f, 0.75f);
        PainVisualOverlay.drawEllipticalRing(pose, w, h,
                innerR, 0.0f,
                1.38f, goldAlpha,
                255, 195, 95,
                0.08f, 0.12f, 0.0f, 0.0f);

        // 2. 中圈：玫瑰粉与梦境紫罗兰多巴胺内晕 (Rose-Pink & Velvet Violet Aura, RGB: 255, 105, 195)
        float roseAlpha = Mth.clamp(smoothEuphoriaIntensity * maxAlphaCfg * 0.62f * (1.0f - 0.2f * slowBreath), 0.0f, 0.65f);
        PainVisualOverlay.drawEllipticalRing(pose, w, h,
                innerR - 0.10f, 0.0f,
                innerR + 0.28f, roseAlpha,
                255, 105, 195,
                0.15f, 0.0f, 0.15f, 0.0f);

        // 3. 推注瞬间的「暖流冲顶波 (Warm Rush Wave)」：从外向内温柔漫溢的金色光环
        if (rushFactor > 0.05f) {
            float waveRadius = Mth.lerp(1.0f - rushFactor, 0.95f, 0.22f);
            float waveAlpha = Mth.sin(rushFactor * Mth.PI) * maxAlphaCfg * 0.68f;
            PainVisualOverlay.drawEllipticalRing(pose, w, h,
                    Math.max(0.05f, waveRadius - 0.12f), 0.0f,
                    waveRadius + 0.12f, waveAlpha,
                    255, 225, 140,
                    0.0f, 0.0f, 0.0f, 0.0f);
        }

        // 4. 视野四周缓缓向上升腾漂浮的 32 颗「多巴胺暖金/樱花粉微光粒子 (Floating Bokeh Motes)」
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int moteCount = 32;
        float timeSec = (nowMs % 120000L) / 1000.0f;
        for (int i = 0; i < moteCount; i++) {
            float seed = i * 1.6180339f;
            // 分布在屏幕左右两侧与上下边缘视野带，不遮挡准星
            float side = (i % 2 == 0) ? 1.0f : -1.0f;
            float normX = 0.5f + side * (0.28f + 0.20f * ((i * 7 % 13) / 13.0f))
                    + 0.035f * Mth.sin(timeSec * 0.9f + seed * 4.0f);
            float cycleY = (1.0f - ((timeSec * (0.045f + (i % 5) * 0.008f) + seed) % 1.0f));
            float px = normX * w;
            float py = cycleY * h;

            float twinkle = 0.5f + 0.5f * Mth.sin(timeSec * 2.6f + seed * 6.0f);
            float edgeFade = Mth.sin(cycleY * Mth.PI);
            int mAlpha = Mth.clamp((int) (smoothEuphoriaIntensity * maxAlphaCfg * 210.0f * twinkle * edgeFade), 0, 220);
            if (mAlpha <= 4) continue;

            float rad = 2.2f + (i % 3) * 1.4f;
            boolean isGold = (i % 3 != 0);
            int mr = 255;
            int mg = isGold ? 215 : 130;
            int mb = isGold ? 115 : 215;

            // 绘制柔和菱形光斑
            buf.addVertex(pose, px, py - rad, 0.0f).setColor(mr, mg, mb, mAlpha);
            buf.addVertex(pose, px - rad, py, 0.0f).setColor(mr, mg, mb, mAlpha);
            buf.addVertex(pose, px, py + rad, 0.0f).setColor(mr, mg, mb, mAlpha);
            buf.addVertex(pose, px + rad, py, 0.0f).setColor(mr, mg, mb, mAlpha);
        }
        MeshData mesh = buf.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }

        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    // =========================================================================
    // 1. 心脏骤停 3A 特效 (Cardiac Arrest & Asystole Flatline)
    // =========================================================================
    private static void renderCardiacArrest3A(GuiGraphics g, Font font, int w, int h, MedicalData data, long nowMs) {
        Matrix4f pose = g.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        float brainPct = Mth.clamp(data.getBrainHealth() / 100.0f, 0.0f, 1.0f);
        float collapse = Mth.clamp(arrestCollapseProgress * 0.7f + (1.0f - brainPct) * 0.3f, 0.0f, 1.0f);

        // A. 深渊缺氧黑红三层椭圆径向暗角（中心留出微弱求生视野，随脑死亡逼近而逐步合拢）
        float innerR = Mth.lerp(collapse, 0.52f, 0.20f);
        float midR = innerR + 0.24f;

        // 内环：透明 -> 凝固暗血红 (RGB: 95, 4, 12)
        PainVisualOverlay.drawEllipticalRing(pose, w, h,
                innerR, 0.08f,
                midR, 0.68f,
                95, 4, 12,
                0.0f, 0.0f, 0.0f, 0.0f);

        // 外环：凝固暗血红 -> 深渊死寂黑红 (RGB: 10, 0, 3)
        PainVisualOverlay.drawEllipticalRing(pose, w, h,
                midR, 0.68f,
                1.45f, 0.96f,
                10, 0, 3,
                0.0f, 0.0f, 0.0f, 0.0f);

        // B. 濒死视网膜缺氧微弱残喘波 + RGB 边缘色差分离
        float slowDyingPulse = 0.5f + 0.5f * Mth.sin(nowMs * 0.0025f);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        PainVisualOverlay.drawEllipticalRing(pose, w, h,
                innerR - 0.03f, 0.0f,
                innerR + 0.12f, 0.28f * slowDyingPulse,
                255, 15, 35,
                0.0f, 0.0f, 0.0f, 0.0f);

        // C. 骤停发生瞬间的除颤级冷白/猩红过曝冲击环
        if (arrestTransitionFlash > 0.01f) {
            PainVisualOverlay.drawEllipticalRing(pose, w, h,
                    0.15f, arrestTransitionFlash * 0.35f,
                    1.35f, arrestTransitionFlash * 0.85f,
                    255, 220, 225,
                    0.0f, 0.0f, 0.0f, 0.0f);
        }
        RenderSystem.defaultBlendFunc();

        // D. 打开医疗面板时不遮挡 UI 文字，仅在游戏主视野绘制中央偏上的全息平线告警舱
        if (Minecraft.getInstance().screen != null) {
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
            return;
        }

        int cardW = Math.min(256, w - 32);
        int cardH = 66;
        int cardX = (w - cardW) / 2;
        int cardY = Math.max(18, (int) (h * 0.14f));

        HudRenderHelper.drawSciFiCard(g, cardX, cardY, cardW, cardH, 5, 0xE6080205, C_ALERT_RED);
        HudRenderHelper.drawCardCorners(g, cardX, cardY, cardW, cardH, 9, C_ALERT_RED);

        // 顶部闪烁危险红带与标题
        float blink = 0.55f + 0.45f * Mth.sin(nowMs * 0.012f);
        int headerAlpha = (int) (blink * 160.0f);
        g.fill(cardX + 2, cardY + 2, cardX + cardW - 2, cardY + 16, (headerAlpha << 24) | 0x880015);

        String title = Component.translatable("medical.warning.cardiac_arrest").getString();
        HudRenderHelper.drawCenteredGlowingString(g, font, title, cardX + cardW / 2, cardY + 5, 0xFFFFFFFF, C_ALERT_RED);

        // 中部：真实医疗监护仪“长鸣平线 (Flatline)”动态扫描视窗
        int scopeX = cardX + 10;
        int scopeY = cardY + 20;
        int scopeW = cardW - 20;
        int scopeH = 22;
        g.fill(scopeX, scopeY, scopeX + scopeW, scopeY + scopeH, 0xCC03060A);
        HudRenderHelper.drawSciFiBorder(g, scopeX, scopeY, scopeW, scopeH, 0x66FF2040);

        int flatY = scopeY + scopeH / 2;
        float sweepPct = (nowMs % 1800L) / 1800.0f;
        int cursorX = scopeX + 4 + (int) ((scopeW - 8) * sweepPct);

        // 平线主体与扫描擦除缺口
        if (cursorX > scopeX + 6) {
            g.fill(scopeX + 4, flatY - 1, cursorX, flatY + 2, 0x44FF1744);
            g.fill(scopeX + 4, flatY, cursorX, flatY + 1, 0xFFFF1744);
        }
        int afterGap = cursorX + 14;
        if (afterGap < scopeX + scopeW - 4) {
            g.fill(afterGap, flatY, scopeX + scopeW - 4, flatY + 1, 0x66FF1744);
        }
        // 扫描激光亮点
        g.fill(cursorX - 1, flatY - 2, cursorX + 2, flatY + 3, 0xFFFFFFFF);
        

        // 底部：脑存活度 (BRAIN HEALTH) 与血氧 (SpO2) 衰竭倒数条
        int barY = cardY + 46;
        int halfW = (scopeW - 8) / 2;
        int bh = data.getBrainHealth();
        int spo2 = data.getBloodOxygen();

        g.drawString(font, "脑健康: " + bh + "%", scopeX, barY, C_ALERT_RED, false);
        HudRenderHelper.drawSciFiProgressBar(g, scopeX, barY + 10, halfW, 5, bh / 100.0f, C_ALERT_RED, 0xFF1A0508);

        int rx = scopeX + halfW + 8;
        g.drawString(font, "SpO2: " + spo2 + "%", rx, barY, C_OXYGEN_BLUE, false);
        HudRenderHelper.drawSciFiProgressBar(g, rx, barY + 10, halfW, 5, spo2 / 100.0f, C_OXYGEN_BLUE, 0xFF05121A);

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    // =========================================================================
    // 2. 心室纤颤 3A 特效 (Ventricular Fibrillation - VFib)
    // =========================================================================
    private static void renderVFib3A(GuiGraphics g, Font font, int w, int h, MedicalData data, long nowMs) {
        Matrix4f pose = g.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        // A. 三频非周期混沌痉挛波（模拟心室肌纤维无序乱颤，绝不呆板正弦频闪）
        double t = nowMs * 0.001;
        float fibrilWave = (float) (0.50 + 0.22 * Math.sin(t * 19.0) + 0.16 * Math.cos(t * 31.0) + 0.12 * Math.sin(t * 47.0));
        fibrilWave = Mth.clamp(fibrilWave, 0.15f, 1.0f);

        float innerR = 0.48f - 0.10f * fibrilWave;
        float midR = innerR + 0.22f;

        // 内层痉挛鲜红晕影 + 外层缺氧深紫黑暗角
        PainVisualOverlay.drawEllipticalRing(pose, w, h,
                innerR, 0.0f,
                midR, 0.52f * fibrilWave,
                195, 10, 35,
                0.0f, 0.0f, 0.0f, 0.0f);
        PainVisualOverlay.drawEllipticalRing(pose, w, h,
                midR, 0.52f * fibrilWave,
                1.42f, 0.88f,
                28, 4, 18,
                0.0f, 0.0f, 0.0f, 0.0f);

        // B. 剧烈 RGB 棱镜色差分离冲击环 + 边缘生物电除颤电弧丝
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        PainVisualOverlay.drawEllipticalRing(pose, w, h,
                innerR - 0.03f, 0.0f,
                innerR + 0.09f, 0.38f * fibrilWave,
                0, 225, 255,
                0.15f, 0.15f, 0.15f, 0.15f);

        drawBioElectricFibrillationArcs(pose, w, h, fibrilWave, nowMs);
        RenderSystem.defaultBlendFunc();

        if (Minecraft.getInstance().screen != null) {
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
            return;
        }

        // C. 顶部全息室颤紧急告警舱（含实时乱颤 VFib 混沌波形）
        int cardW = Math.min(252, w - 32);
        int cardH = 64;
        int cardX = (w - cardW) / 2;
        int cardY = Math.max(18, (int) (h * 0.14f));

        // 痉挛微抖动偏移
        int jitterX = (int) (Math.sin(t * 43.0) * 1.6);
        int jitterY = (int) (Math.cos(t * 37.0) * 1.2);
        cardX += jitterX;
        cardY += jitterY;

        HudRenderHelper.drawSciFiCard(g, cardX, cardY, cardW, cardH, 5, 0xE00A040C, 0xFFFF1744);
        HudRenderHelper.drawCardCorners(g, cardX, cardY, cardW, cardH, 9, C_NEON_CYAN);

        String vfibTitle = Component.translatable("medical.warning.vfib").getString();
        HudRenderHelper.drawCenteredGlowingString(g, font, vfibTitle, cardX + cardW / 2, cardY + 5, 0xFFFFD000, C_ALERT_RED);

        // 实时绘制高频乱颤的 VFib 心电图波形框
        int scopeX = cardX + 10;
        int scopeY = cardY + 19;
        int scopeW = cardW - 20;
        int scopeH = 24;
        g.fill(scopeX, scopeY, scopeX + scopeW, scopeY + scopeH, 0xCC040810);
        HudRenderHelper.drawSciFiBorder(g, scopeX, scopeY, scopeW, scopeH, 0x88FF1744);

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float midY = scopeY + scopeH * 0.5f;
        float amp = scopeH * 0.38f;
        int segs = 48;
        for (int i = 0; i < segs; i++) {
            float x1 = scopeX + 4 + (i / (float) segs) * (scopeW - 8);
            float x2 = scopeX + 4 + ((i + 1) / (float) segs) * (scopeW - 8);
            double p1 = t * 14.0 + i * 0.45;
            double p2 = t * 14.0 + (i + 1) * 0.45;
            float y1 = midY + (float) (Math.sin(p1 * 1.7) * 0.45 + Math.cos(p1 * 3.1) * 0.35 + Math.sin(p1 * 5.3) * 0.20) * amp;
            float y2 = midY + (float) (Math.sin(p2 * 1.7) * 0.45 + Math.cos(p2 * 3.1) * 0.35 + Math.sin(p2 * 5.3) * 0.20) * amp;
            PainVisualOverlay.addQuadSegment(buf, pose, x1, y1, x2, y2, 1.1f, 255, 35, 65, 230, 230);
        }
        MeshData mesh = buf.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
        RenderSystem.defaultBlendFunc();



        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    /**
     * 绘制视野外围乱窜的生物电除颤电弧丝
     */
    private static void drawBioElectricFibrillationArcs(Matrix4f pose, int w, int h, float intensity, long nowMs) {
        float cx = w * 0.5f;
        float cy = h * 0.5f;
        float rx = w * 0.5f;
        float ry = h * 0.5f;

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        long bucket = nowMs / 45L; // 每 45ms 跳变一次电弧形态
        int numArcs = 18;
        for (int i = 0; i < numArcs; i++) {
            long hash = (bucket * 0x9E3779B97F4A7C15L) ^ (i * 0xBF58476D1CE4E5B9L);
            if ((hash & 3L) == 0L) continue;

            double baseAngle = (i / (double) numArcs) * Math.PI * 2.0 + (((hash >>> 8) & 0xFF) / 255.0 - 0.5) * 0.25;
            float prevR = 1.08f;
            float prevA = (float) baseAngle;

            boolean isCyan = (i % 3 == 0);
            int r = isCyan ? 40 : 255;
            int g = isCyan ? 240 : 35;
            int b = isCyan ? 255 : 65;

            for (int seg = 1; seg <= 3; seg++) {
                float nextR = prevR - (0.07f + (((hash >>> (seg * 8)) & 0xFF) / 255.0f) * 0.08f);
                float nextA = prevA + ((((hash >>> (seg * 11)) & 0xFF) / 255.0f) - 0.5f) * 0.14f;

                float x1 = cx + Mth.cos(prevA) * rx * prevR;
                float y1 = cy + Mth.sin(prevA) * ry * prevR;
                float x2 = cx + Mth.cos(nextA) * rx * nextR;
                float y2 = cy + Mth.sin(nextA) * ry * nextR;

                int alpha = (int) (intensity * (1.0f - seg / 3.5f) * 210.0f);
                PainVisualOverlay.addQuadSegment(buf, pose, x1, y1, x2, y2, 1.0f, r, g, b, alpha, alpha / 2);

                prevR = nextR;
                prevA = nextA;
            }
        }

        MeshData mesh = buf.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
    }

    // =========================================================================
    // 3. 心动过速、呼吸异常、血胸与颅内出血的 3A 径向视觉层
    // =========================================================================
    private static void renderSubCriticalRadialLayers(GuiGraphics g, Font font, int w, int h, MedicalData data, long nowMs) {
        Matrix4f pose = g.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        float pulse = PainVisualOverlay.evaluateDicroticPulse(heartPhase);

        // ---------- A. 心动过速 3A 动脉重搏冲击光晕 + 室颤临界预警 (Tachycardia) ----------
        if (tachycardiaFactor > 0.01f) {
            float surge = (pulse > 0.40f) ? ((pulse - 0.40f) / 0.60f) : 0.0f;
            float outerAlpha = Mth.clamp(tachycardiaFactor * (0.18f + 0.38f * pulse), 0.0f, 0.62f);
            float innerR = 0.76f - 0.18f * tachycardiaFactor - 0.05f * surge * tachycardiaFactor;

            // 外缘高心率充血暗红/琥珀重搏环
            PainVisualOverlay.drawEllipticalRing(pose, w, h,
                    innerR, 0.0f,
                    1.38f, outerAlpha,
                    190, 22, 18,
                    0.0f, 0.0f, 0.0f, 0.0f);

            // 收缩峰值 RGB 色差棱镜环（营造心跳过速血液直冲太阳穴的搏动冲击感）
            if (surge > 0.15f && tachycardiaFactor > 0.25f) {
                RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
                float caAlpha = surge * tachycardiaFactor * 0.32f;
                PainVisualOverlay.drawEllipticalRing(pose, w, h,
                        innerR - 0.02f, 0.0f,
                        innerR + 0.08f, caAlpha,
                        255, 65, 30,
                        0.0f, 0.0f, 0.0f, 0.0f);
                PainVisualOverlay.drawEllipticalRing(pose, w, h,
                        innerR - 0.035f, 0.0f,
                        innerR + 0.04f, caAlpha * 0.75f,
                        0, 215, 255,
                        0.0f, 0.0f, 0.0f, 0.0f);
                RenderSystem.defaultBlendFunc();
            }

            // 若心率极高已触发室颤累进计时器 (vfibTimer > 0) 或接近室颤阈值，在右上角显示室颤临界预警微浮标
            int vfibTimer = data.getVfibTimer();
            int hr = data.getHeartRate();
            int vfibWarnHr = Math.max(120, MedicalConfig.VFIB_THRESHOLD.get() - 25);
            if ((vfibTimer > 0 || hr >= vfibWarnHr) && Minecraft.getInstance().screen == null) {
                drawTachycardiaOverloadBadge(g, font, w, hr, vfibTimer, pulse);
                RenderSystem.setShader(GameRenderer::getPositionColorShader);
            }
        }

        // ---------- B. 极端呼吸异常无缝椭圆红晕 (替代原版 4 条生硬红边) ----------
        int respRate = data.getRespirationRate();
        int lowThreshold = MedicalConfig.EXTREME_RESP_LOW_THRESHOLD.get();
        int highThreshold = MedicalConfig.EXTREME_RESP_HIGH_THRESHOLD.get();
        if (respRate > 0 && (respRate < lowThreshold || respRate > highThreshold)) {
            float respPulse = 0.55f + 0.45f * Mth.sin(nowMs * 0.005f);
            PainVisualOverlay.drawEllipticalRing(pose, w, h,
                    0.74f, 0.0f,
                    1.32f, 0.36f * respPulse,
                    220, 25, 25,
                    0.0f, 0.0f, 0.0f, 0.0f);
        }

        // ---------- C. 血胸 2+ 级胸膜压迫无缝暗红椭圆晕影 (替代原版矩形边框) ----------
        int hemoLevel = data.getHemothoraxLevel();
        if (hemoLevel >= 2) {
            float hemoAlpha = (hemoLevel == 2) ? 0.35f : (hemoLevel == 3 ? 0.52f : 0.70f);
            float hemoInner = 0.70f - (hemoLevel - 2) * 0.08f;
            PainVisualOverlay.drawEllipticalRing(pose, w, h,
                    hemoInner, 0.0f,
                    1.38f, hemoAlpha,
                    95, 4, 8,
                    0.15f, 0.15f, 0.05f, 0.35f);
        }

        // ---------- D. 颅内出血清醒期后隧道视野暗角 (替代原版 12 层矩形线框) ----------
        if (data.getIntracranialOnsetTimer() >= MedicalConfig.ONSET_DELAY.get() && data.getIntracranialLevel() >= 1) {
            int icLv = data.getIntracranialLevel();
            float icInner = Mth.clamp(0.72f - icLv * 0.11f, 0.24f, 0.68f);
            float icAlpha = Mth.clamp(0.35f + icLv * 0.15f, 0.0f, 0.92f);
            PainVisualOverlay.drawEllipticalRing(pose, w, h,
                    icInner, 0.0f,
                    1.40f, icAlpha,
                    12, 4, 18,
                    0.0f, 0.0f, 0.35f, 0.0f);
        }

        // ---------- E. 阿片类药物镇静 / 过量呼吸抑制「针尖样瞳孔 (Miosis)」紫青视野缩窄与危象浮标 ----------
        int opioidConc = data.getOpioidConcentration();
        int sedationStart = MedicalConfig.OPIOID_SEDATION_START.get();
        int overdoseThresh = MedicalConfig.OPIOID_OVERDOSE_THRESHOLD.get();
        int apneaThresh = MedicalConfig.OPIOID_APNEA_THRESHOLD.get();
        if (opioidConc >= sedationStart) {
            float opRatio = Mth.clamp((opioidConc - sedationStart) / Math.max(1.0f, 100.0f - sedationStart), 0.0f, 1.0f);
            float slowWave = 0.5f + 0.5f * Mth.sin(nowMs * 0.0035f);
            // 针尖样瞳孔 (Pinpoint Pupils) 隧道内径：随过量程度从 0.72 收缩至 0.26
            float miosisInner = Mth.lerp(opRatio, 0.72f, 0.26f) - 0.02f * slowWave;
            float miosisAlpha = Mth.clamp(0.25f + opRatio * 0.65f, 0.0f, 0.90f);
            PainVisualOverlay.drawEllipticalRing(pose, w, h,
                    miosisInner, 0.0f,
                    1.40f, miosisAlpha,
                    18, 6, 32,
                    0.0f, 0.0f, 0.0f, 0.0f);


        }

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    /**
     * 绘制心动过速 / 室颤临界过载微浮标 (Tachycardia Overload Badge)
     */
    private static void drawTachycardiaOverloadBadge(GuiGraphics g, Font font, int screenW, int hr, int vfibTimer, float pulse) {
        // 仅保留全屏径向红脉冲视觉效果，不在右上角绘制数字浮标
    }

    // =========================================================================
    // 4. 失血至 <= 3000mL 濒危状态与 20 秒生命倒计时 3A 特效
    // =========================================================================
    private static void renderCriticalHypovolemicCountdown3A(GuiGraphics g, Font font, int w, int h, MedicalData data, long nowMs) {
        int timerSec = data.getCriticalTimer();
        float maxCritSec = Math.max(1.0f, MedicalConfig.CRITICAL_TIMER_MAX.get().floatValue());
        float urgency = Mth.clamp(1.0f - (timerSec / maxCritSec), 0.0f, 1.0f);
        float pulse = PainVisualOverlay.evaluateDicroticPulse(heartPhase);

        Matrix4f pose = g.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        // A. 失血性休克双层径向隧道视野：外圈死寂黑红 + 中圈冰冷苍白灰蓝去饱和晕影
        float tunnelInner = Mth.lerp(urgency, 0.62f, 0.30f) - 0.04f * pulse;
        float tunnelMid = tunnelInner + 0.22f;

        // 中圈：失血特有的冰冷苍白/紫灰缺血环 (RGB: 38, 45, 58)
        PainVisualOverlay.drawEllipticalRing(pose, w, h,
                tunnelInner, 0.0f,
                tunnelMid, Mth.clamp(0.28f + 0.35f * urgency, 0.0f, 0.65f),
                38, 45, 58,
                0.0f, 0.0f, 0.0f, 0.0f);

        // 外圈：深凝血黑红隧道压迫 (RGB: 55, 2, 8)
        PainVisualOverlay.drawEllipticalRing(pose, w, h,
                tunnelMid, Mth.clamp(0.28f + 0.35f * urgency, 0.0f, 0.65f),
                1.42f, Mth.clamp(0.55f + 0.38f * urgency, 0.0f, 0.94f),
                55, 2, 8,
                0.0f, 0.0f, 0.0f, 0.0f);

        // B. 倒计时最后 5 秒叠加濒死脉冲红闪与边缘神经芒刺
        if (timerSec <= 5) {
            float finalFlash = (0.5f + 0.5f * Mth.sin(nowMs * 0.018f)) * ((6 - timerSec) / 5.0f);
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            PainVisualOverlay.drawEllipticalRing(pose, w, h,
                    tunnelInner - 0.04f, 0.0f,
                    tunnelInner + 0.14f, finalFlash * 0.45f,
                    255, 20, 40,
                    0.0f, 0.0f, 0.0f, 0.0f);
            RenderSystem.defaultBlendFunc();
        }

        if (Minecraft.getInstance().screen != null) {
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
            return;
        }

        // C. 屏幕上方偏中绘制 3A 全息濒危倒计时核心仪（不遮挡准星中央视线！）
        int boxW = Math.min(244, w - 32);
        int boxH = 58;
        int boxX = (w - boxW) / 2;
        int boxY = Math.max(16, (int) (h * 0.11f));

        // 紧迫闪烁边框色
        boolean urgentBeat = (timerSec <= 5) && ((nowMs / 120L) % 2L == 0L);
        int borderCol = urgentBeat ? 0xFFFFFFFF : C_ALERT_RED;

        HudRenderHelper.drawSciFiCard(g, boxX, boxY, boxW, boxH, 5, 0xDD060205, borderCol);
        HudRenderHelper.drawCardCorners(g, boxX, boxY, boxW, boxH, 8, C_NEON_CYAN);

        // 顶部警戒标题
        String warnText = Component.translatable("medical.warning.critical").getString();
        HudRenderHelper.drawCenteredGlowingString(g, font, warnText, boxX + boxW / 2, boxY + 5, 0xFFFFD000, C_ALERT_RED);

        // 中央左侧：大型七段数码管倒计时秒表 (XX.X s)
        int subTenths = 9 - (int) ((nowMs % 1000L) / 100L);
        String digits = String.format("%02d.%d", Mth.clamp(timerSec, 0, 99), subTenths);
        int segColor = (timerSec <= 5) ? 0xFFFF1744 : 0xFFFF5252;
        int dimSeg = withAlpha(segColor, 0.15f);
        SegmentDisplayRenderer.renderString(g, digits, boxX + 14, boxY + 19, 8, 17, 3, 2, segColor, dimSeg);
        g.drawString(font, "SEC", boxX + 64, boxY + 28, 0xFFFF8A80, false);



        // 底部：20 秒濒危倒计时双向能量衰减进度条
        float remainPct = Mth.clamp((timerSec - (1.0f - subTenths / 10.0f) * 0.5f) / maxCritSec, 0.0f, 1.0f);
        HudRenderHelper.drawSciFiProgressBar(g, boxX + 10, boxY + 46, boxW - 20, 6, remainPct, segColor, 0xFF1A060A);

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    // =========================================================================
    // 2.8 3A 级脑损伤视觉系统：真实历史画面闪回 (Flashback)、画面定格撕裂震荡 (Frame-Freeze Concussion) 与皮层变色 (Cortical Color Shift)
    // =========================================================================

    /**
     * 供外部模块或网络包主动触发一次完整的 3A 脑损伤发作（定格震荡 + 历史闪回 + 神经变色）
     */
    public static void triggerBrainDamageEpisode(float intensity) {
        float clamped = Mth.clamp(intensity, 0.35f, 1.0f);
        triggerBrainFreezeShake(clamped, 1.15f + 0.65f * clamped);
        triggerBrainFlashback(clamped, 1.45f + 0.85f * clamped);
        int nextMode = (int) (Math.abs(System.nanoTime() >> 8) % 4);
        triggerBrainColorShift(nextMode, clamped, 2.6f + 1.4f * clamped);
    }

    public static void triggerBrainFlashback(float intensity, float durationSec) {
        brainFlashbackDuration = Math.max(0.4f, durationSec);
        brainFlashbackTimer = brainFlashbackDuration;
        brainFlashbackPeakIntensity = Mth.clamp(intensity, 0.25f, 1.0f);
        // 选择距离当前约 1.3~1.9 秒之前的历史帧槽位进行闪回重现
        activeFlashbackSlot = (historyWriteIdx + 1) % flashbackHistoryTargets.length;
        brainEpisodeSeed = System.currentTimeMillis() ^ System.nanoTime();
    }

    public static void triggerBrainFreezeShake(float intensity, float durationSec) {
        brainFreezeShakeDuration = Math.max(0.35f, durationSec);
        brainFreezeShakeTimer = brainFreezeShakeDuration;
        brainFreezeWindowSec = MedicalConfig.BRAIN_VFX_FREEZE_DURATION_MS.get() / 1000.0f;
        brainFreezePeakIntensity = Mth.clamp(intensity, 0.30f, 1.0f);
        lastFreezeStutterMs = 0L; // 强制在下一渲染帧立刻抓取定格画面
        brainEpisodeSeed = System.currentTimeMillis() ^ System.nanoTime();
    }

    public static void triggerBrainColorShift(int mode, float intensity, float durationSec) {
        brainColorShiftMode = Math.floorMod(mode, 4);
        brainColorShiftDuration = Math.max(0.5f, durationSec);
        brainColorShiftTimer = brainColorShiftDuration;
        smoothBrainColorShift = Math.max(smoothBrainColorShift, Mth.clamp(intensity, 0.25f, 1.0f));
    }

    private static boolean ensureBrainFramebuffers(int fbW, int fbH) {
        if (fbW <= 0 || fbH <= 0) return false;
        try {
            if (currentSceneTarget == null || fboAllocatedW != fbW || fboAllocatedH != fbH) {
                destroyBrainFramebuffers();
                currentSceneTarget = new TextureTarget(fbW, fbH, false, Minecraft.ON_OSX);
                freezeFrameTarget = new TextureTarget(fbW, fbH, false, Minecraft.ON_OSX);
                for (int i = 0; i < flashbackHistoryTargets.length; i++) {
                    flashbackHistoryTargets[i] = new TextureTarget(fbW, fbH, false, Minecraft.ON_OSX);
                }
                fboAllocatedW = fbW;
                fboAllocatedH = fbH;
                Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void destroyBrainFramebuffers() {
        if (currentSceneTarget != null) {
            currentSceneTarget.destroyBuffers();
            currentSceneTarget = null;
        }
        if (freezeFrameTarget != null) {
            freezeFrameTarget.destroyBuffers();
            freezeFrameTarget = null;
        }
        for (int i = 0; i < flashbackHistoryTargets.length; i++) {
            if (flashbackHistoryTargets[i] != null) {
                flashbackHistoryTargets[i].destroyBuffers();
                flashbackHistoryTargets[i] = null;
            }
        }
    }

    /**
     * 使用硬件级 OpenGL BlitFramebuffer 将主渲染目标 (mainTarget) 拷贝至离屏纹理缓冲（耗时 < 0.02ms）
     */
    private static boolean blitRenderTarget(RenderTarget src, RenderTarget dst) {
        if (src == null || dst == null || src.frameBufferId <= 0 || dst.frameBufferId <= 0) return false;
        try {
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, src.frameBufferId);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, dst.frameBufferId);
            GL30.glBlitFramebuffer(
                    0, 0, src.width, src.height,
                    0, 0, dst.width, dst.height,
                    GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR
            );
            src.bindWrite(false);
            return true;
        } catch (Throwable t) {
            src.bindWrite(false);
            return false;
        }
    }

    private static void updateAndRenderBrainDamageVfx3A(GuiGraphics g, Minecraft mc, int w, int h,
                                                        MedicalData data, float dt, long nowMs) {
        if (!MedicalConfig.BRAIN_VFX_UPGRADE_ENABLED.get()) return;

        int brainHealth = data.getBrainHealth();
        int icLevel = (data.getIntracranialOnsetTimer() >= MedicalConfig.ONSET_DELAY.get())
                ? data.getIntracranialLevel()
                : 0;
        boolean controlInverted = BrainDamageClientState.isViewInverted() || BrainDamageClientState.isInputInverted();

        if (lastBrainHealth < 0) {
            lastBrainHealth = brainHealth;
        }

        // 计算脑损伤综合严重度 (0.0 ~ 1.0)：脑健康 < 90、颅内出血或控制反转激活时生效
        float healthDeficit = (brainHealth < 90) ? Mth.clamp((90.0f - brainHealth) / 75.0f, 0.0f, 1.0f) : 0.0f;
        float icSeverity = icLevel * 0.22f;
        float baseSeverity = Mth.clamp(Math.max(healthDeficit, icSeverity) + (controlInverted ? 0.35f : 0.0f), 0.0f, 1.0f);

        // 1. 监测急性触发条件：
        //    (a) 服务端下发 BrainDamageEffectPacket 触发视角/按键反转的上升沿
        //    (b) 脑健康突然下跌 (>= 2 点) 或跨越 90 / 60 / 30 阈值
        //    (c) 脑损伤期间周期性自发神经发作 (脑健康越低，发作间隔越短：4.0s ~ 11.0s)
        int bhDrop = lastBrainHealth - brainHealth;
        boolean crossedThreshold = (lastBrainHealth > 90 && brainHealth <= 90)
                || (lastBrainHealth > 60 && brainHealth <= 60)
                || (lastBrainHealth > 30 && brainHealth <= 30);
        lastBrainHealth = brainHealth;

        if ((controlInverted && !wasBrainControlInverted) || crossedThreshold || bhDrop >= 2) {
            float trigIntensity = Mth.clamp(Math.max(0.55f, baseSeverity + bhDrop * 0.08f), 0.50f, 1.0f);
            triggerBrainDamageEpisode(trigIntensity);
            brainEpisodeCooldownSec = Mth.lerp(baseSeverity, 8.5f, 3.8f);
        } else if (baseSeverity > 0.08f) {
            brainEpisodeCooldownSec -= dt;
            if (brainEpisodeCooldownSec <= 0.0f) {
                float epIntensity = Mth.clamp(0.42f + baseSeverity * 0.58f, 0.42f, 1.0f);
                // 交替或组合触发定格震荡、历史闪回与皮层变色
                long rollMode = Math.abs(nowMs / 300L + brainHealth) % 3L;
                if (rollMode == 0L || baseSeverity >= 0.50f) {
                    triggerBrainFreezeShake(epIntensity, 0.95f + 0.65f * baseSeverity);
                }
                if (rollMode == 1L || baseSeverity >= 0.35f) {
                    triggerBrainFlashback(epIntensity, 1.35f + 0.85f * baseSeverity);
                }
                int nextColorMode = (brainColorShiftMode + 1 + (int) (nowMs & 1L)) % 4;
                triggerBrainColorShift(nextColorMode, epIntensity, 2.4f + 1.5f * baseSeverity);
                brainEpisodeCooldownSec = Mth.lerp(baseSeverity, 9.5f, 3.6f);
            }
        }
        wasBrainControlInverted = controlInverted;

        // 2. 更新各计时器与平滑强度
        if (brainFlashbackTimer > 0.0f) {
            brainFlashbackTimer = Math.max(0.0f, brainFlashbackTimer - dt);
        }
        if (brainFreezeShakeTimer > 0.0f) {
            brainFreezeShakeTimer = Math.max(0.0f, brainFreezeShakeTimer - dt);
        }
        if (brainColorShiftTimer > 0.0f) {
            brainColorShiftTimer = Math.max(0.0f, brainColorShiftTimer - dt);
        }

        float targetColorShift = (brainColorShiftTimer > 0.0f)
                ? Math.max(baseSeverity * 0.65f, brainFreezePeakIntensity * 0.85f)
                : (baseSeverity * 0.52f);
        smoothBrainColorShift = Mth.lerp(Math.min(1.0f, dt * 4.5f), smoothBrainColorShift, targetColorShift);

        boolean anyActive = (baseSeverity > 0.02f)
                || (brainFlashbackTimer > 0.005f)
                || (brainFreezeShakeTimer > 0.005f)
                || (smoothBrainColorShift > 0.015f);

        RenderTarget mainTarget = mc.getMainRenderTarget();
        boolean fboReady = ensureBrainFramebuffers(mainTarget.width, mainTarget.height);

        // 当玩家脑部受损或处于发作监控期时，每 600ms 在后台静默录制一帧历史画面至环形缓冲区，供「闪回」随时调取
        if (fboReady && (baseSeverity > 0.01f || brainHealth < 98 || icLevel > 0)
                && brainFreezeShakeTimer <= 0.0f && (nowMs - lastHistoryCaptureMs >= 600L)) {
            lastHistoryCaptureMs = nowMs;
            if (blitRenderTarget(mainTarget, flashbackHistoryTargets[historyWriteIdx])) {
                historyWriteIdx = (historyWriteIdx + 1) % flashbackHistoryTargets.length;
            }
        }

        if (!anyActive) return;

        // 拷贝当前 3D 实时画面到 currentSceneTarget，供 RGB 棱镜色散、错位切片与定格采样使用
        boolean hasCurrentScene = fboReady && blitRenderTarget(mainTarget, currentSceneTarget);

        // 若正处于「画面定格震荡」期间，按 85ms 阶跃卡顿间隔更新或锁死 freezeFrameTarget
        if (hasCurrentScene && brainFreezeShakeTimer > 0.005f) {
            float elapsedFreeze = brainFreezeShakeDuration - brainFreezeShakeTimer;
            boolean inHardFreeze = elapsedFreeze <= brainFreezeWindowSec;
            long stutterInterval = inHardFreeze ? 260L : 85L;
            if (lastFreezeStutterMs == 0L || (nowMs - lastFreezeStutterMs >= stutterInterval)) {
                lastFreezeStutterMs = nowMs;
                blitRenderTarget(currentSceneTarget, freezeFrameTarget);
            }
        }

        Matrix4f pose = g.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();

        float shakeCfg = MedicalConfig.BRAIN_VFX_SHAKE_AMPLITUDE.get().floatValue();
        float flashbackCfg = MedicalConfig.BRAIN_VFX_FLASHBACK_INTENSITY.get().floatValue();
        float colorCfg = MedicalConfig.BRAIN_VFX_COLOR_SHIFT_INTENSITY.get().floatValue();

        // =====================================================================
        // 特效 I：【画面定格震荡 (Frame-Freeze Concussion & Horizontal Tear Shear)】
        // =====================================================================
        if (brainFreezeShakeTimer > 0.005f) {
            float progress = 1.0f - Mth.clamp(brainFreezeShakeTimer / Math.max(0.1f, brainFreezeShakeDuration), 0.0f, 1.0f);
            float elapsedSec = brainFreezeShakeDuration - brainFreezeShakeTimer;
            boolean inHardFreeze = elapsedSec <= brainFreezeWindowSec;
            float decayEnv = (float) Math.pow(1.0f - progress, 1.45) * brainFreezePeakIntensity;

            // 高频欠阻尼脑震荡位移包络
            float oscX = (float) (Math.sin(nowMs * 0.055 + brainEpisodeSeed) * 14.0
                    + Math.cos(nowMs * 0.092) * 8.0) * decayEnv * shakeCfg;
            float oscY = (float) (Math.cos(nowMs * 0.048 + brainEpisodeSeed) * 10.0
                    + Math.sin(nowMs * 0.081) * 6.0) * decayEnv * shakeCfg;

            if (hasCurrentScene && freezeFrameTarget != null) {
                RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
                RenderSystem.setShaderTexture(0, freezeFrameTarget.getColorTextureId());

                // 1. 硬定格基底画面：在硬定格窗口内以高不透明度完全定格画面，随后伴随卡顿残影消退
                float freezeAlpha = inHardFreeze
                        ? Mth.clamp(0.92f * brainFreezePeakIntensity, 0.65f, 0.96f)
                        : Mth.clamp(decayEnv * 0.72f, 0.0f, 0.78f);

                Tesselator tess = Tesselator.getInstance();
                BufferBuilder buf = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

                // 将定格画面切分为 16 道水平扫描带，施加脑震荡横向错位撕裂 (Horizontal Glitch Shear)
                int bands = 16;
                long glitchBucket = nowMs / 45L;
                for (int b = 0; b < bands; b++) {
                    float v0 = b / (float) bands;
                    float v1 = (b + 1) / (float) bands;
                    float y0 = v0 * h + oscY * 0.6f;
                    float y1 = v1 * h + oscY * 0.6f;

                    long bHash = (glitchBucket * 6364136223846793005L) ^ (b * 1442695040888963407L) ^ brainEpisodeSeed;
                    boolean isTornSlice = ((bHash >>> 12) & 3L) == 0L;
                    float sliceShiftX = oscX + (isTornSlice
                            ? ((((bHash >>> 20) & 0xFF) / 127.5f - 1.0f) * 26.0f * decayEnv * shakeCfg)
                            : 0.0f);

                    int colA = Mth.clamp((int) (freezeAlpha * 255.0f), 0, 250);
                    emitFboSliceQuad(buf, pose, sliceShiftX, y0, w + sliceShiftX, y1, 0.0f, v0, 1.0f, v1, 255, 255, 255, colA);
                }
                MeshData mesh = buf.build();
                if (mesh != null) BufferUploader.drawWithShader(mesh);

                // 2. 脑震荡复视双影 (Concussion Diplopia Double-Vision)：左右对称分离的红/青震荡重影
                RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
                BufferBuilder dipBuf = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                int dipAlpha = Mth.clamp((int) (decayEnv * 135.0f), 0, 160);
                float splitDist = (8.0f + 18.0f * decayEnv) * shakeCfg;
                emitFboSliceQuad(dipBuf, pose, -splitDist + oscX, -oscY * 0.5f, w - splitDist + oscX, h - oscY * 0.5f,
                        0.0f, 0.0f, 1.0f, 1.0f, 255, 45, 95, dipAlpha);
                emitFboSliceQuad(dipBuf, pose, splitDist - oscX, oscY * 0.5f, w + splitDist - oscX, h + oscY * 0.5f,
                        0.0f, 0.0f, 1.0f, 1.0f, 30, 235, 255, dipAlpha);
                MeshData dipMesh = dipBuf.build();
                if (dipMesh != null) BufferUploader.drawWithShader(dipMesh);
                RenderSystem.defaultBlendFunc();
            }
        }

        // =====================================================================
        // 特效 II：【历史画面闪回 (Temporal Memory Flashback & Ghost Echo)】
        // =====================================================================
        if (brainFlashbackTimer > 0.005f && flashbackCfg > 0.01f) {
            float fp = 1.0f - Mth.clamp(brainFlashbackTimer / Math.max(0.1f, brainFlashbackDuration), 0.0f, 1.0f);
            // 双峰记忆回闪曲线：初段突触快闪冲顶，中段浮现清晰历史残影，末段如潮水退去
            float echoEnvelope = Mth.sin(fp * Mth.PI) * brainFlashbackPeakIntensity * flashbackCfg;

            TextureTarget histTarget = (fboReady && flashbackHistoryTargets[activeFlashbackSlot] != null)
                    ? flashbackHistoryTargets[activeFlashbackSlot]
                    : currentSceneTarget;

            if (histTarget != null && histTarget.getColorTextureId() > 0) {
                RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
                RenderSystem.setShaderTexture(0, histTarget.getColorTextureId());

                Tesselator tess = Tesselator.getInstance();
                // 第一层：放大并缓慢推进的过去时刻记忆画面 (Memory Echo Base Layer)
                float zoom1 = 1.03f + 0.09f * fp;
                float padX1 = (w * (zoom1 - 1.0f)) * 0.5f;
                float padY1 = (h * (zoom1 - 1.0f)) * 0.5f;
                float driftX = Mth.sin(fp * Mth.TWO_PI) * 12.0f * echoEnvelope;
                float driftY = Mth.cos(fp * Mth.PI) * 7.0f * echoEnvelope;

                int baseAlpha = Mth.clamp((int) (echoEnvelope * 175.0f), 0, 205);
                BufferBuilder fbBuf = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                // 记忆画面带有冷青琥珀怀旧/创伤色调
                emitFboSliceQuad(fbBuf, pose,
                        -padX1 + driftX, -padY1 + driftY,
                        w + padX1 + driftX, h + padY1 + driftY,
                        0.0f, 0.0f, 1.0f, 1.0f,
                        215, 240, 255, baseAlpha);
                MeshData fbMesh = fbBuf.build();
                if (fbMesh != null) BufferUploader.drawWithShader(fbMesh);

                // 第二层：加法混合的多重时间回声残影 (Multi-Echo Time Trails)
                RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
                BufferBuilder trailBuf = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                float zoom2 = 1.10f + 0.12f * fp;
                float padX2 = (w * (zoom2 - 1.0f)) * 0.5f;
                float padY2 = (h * (zoom2 - 1.0f)) * 0.5f;
                int trailAlpha = Mth.clamp((int) (echoEnvelope * 115.0f), 0, 145);

                emitFboSliceQuad(trailBuf, pose,
                        -padX2 - driftX * 1.4f, -padY2,
                        w + padX2 - driftX * 1.4f, h + padY2,
                        0.0f, 0.0f, 1.0f, 1.0f,
                        0, 225, 255, trailAlpha);
                emitFboSliceQuad(trailBuf, pose,
                        -padX2 + driftX * 1.4f, -padY2,
                        w + padX2 + driftX * 1.4f, h + padY2,
                        0.0f, 0.0f, 1.0f, 1.0f,
                        245, 65, 200, trailAlpha);
                MeshData trailMesh = trailBuf.build();
                if (trailMesh != null) BufferUploader.drawWithShader(trailMesh);
                RenderSystem.defaultBlendFunc();
            }

            // 闪回触发前 15% 时间的视神经突触快门白青闪光 (Synaptic Shutter Flash)
            if (fp < 0.18f) {
                float shutter = (1.0f - fp / 0.18f) * brainFlashbackPeakIntensity * flashbackCfg;
                RenderSystem.setShader(GameRenderer::getPositionColorShader);
                RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
                PainVisualOverlay.drawEllipticalRing(pose, w, h,
                        0.15f, shutter * 0.35f,
                        1.38f, shutter * 0.72f,
                        210, 245, 255,
                        0.0f, 0.0f, 0.0f, 0.0f);
                RenderSystem.defaultBlendFunc();
            }
        }

        // =====================================================================
        // 特效 III：【皮层神经变色与 RGB 棱镜色散 (Cortical Color Shift & Chromatic Aberration)】
        // =====================================================================
        float effectiveColorShift = smoothBrainColorShift * colorCfg;
        if (effectiveColorShift > 0.015f) {
            float wave = 0.5f + 0.5f * Mth.sin(nowMs * 0.0045f);

            // 1. 基于真实画面的 RGB 通道分离色散与反相负片脉冲
            if (hasCurrentScene && currentSceneTarget != null) {
                RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
                RenderSystem.setShaderTexture(0, currentSceneTarget.getColorTextureId());

                Tesselator tess = Tesselator.getInstance();
                float caOffset = (3.5f + 11.5f * effectiveColorShift) * (0.7f + 0.3f * wave);
                int caAlpha = Mth.clamp((int) (effectiveColorShift * 125.0f), 0, 155);

                // 根据当前变色模式选择通道分离色调
                int r1, g1, b1, r2, g2, b2;
                switch (brainColorShiftMode) {
                    case 1 -> { // 模式 1：颅内血肿猩红-琥珀金热力变色
                        r1 = 255; g1 = 35;  b1 = 20;
                        r2 = 255; g2 = 195; b2 = 15;
                    }
                    case 2 -> { // 模式 2：视皮层负片反相与冷蓝失色
                        r1 = 90;  g1 = 210; b1 = 255;
                        r2 = 235; g2 = 245; b2 = 255;
                    }
                    case 3 -> { // 模式 3：突触迷幻紫绿双色分离
                        r1 = 255; g1 = 20;  b1 = 210;
                        r2 = 20;  g2 = 255; b2 = 140;
                    }
                    default -> { // 模式 0：缺血深紫-电光青色偏
                        r1 = 195; g1 = 30;  b1 = 255;
                        r2 = 0;   g2 = 240; b2 = 255;
                    }
                }

                // 若处于模式 2（视皮层反相负片）或重度发作初段，施加真实画面负片反相混合 (ONE_MINUS_DST_COLOR)！
                if (brainColorShiftMode == 2 || (brainFreezeShakeTimer > brainFreezeShakeDuration * 0.72f)) {
                    RenderSystem.blendFunc(
                            GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR,
                            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA
                    );
                    int invAlpha = Mth.clamp((int) (effectiveColorShift * 140.0f), 0, 165);
                    BufferBuilder invBuf = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                    emitFboSliceQuad(invBuf, pose, 0.0f, 0.0f, w, h, 0.0f, 0.0f, 1.0f, 1.0f, 200, 235, 255, invAlpha);
                    MeshData invMesh = invBuf.build();
                    if (invMesh != null) BufferUploader.drawWithShader(invMesh);
                }

                // 加法混合绘制左右/上下错位的双色差通道画面
                RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
                BufferBuilder caBuf = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                emitFboSliceQuad(caBuf, pose, -caOffset, -caOffset * 0.4f, w - caOffset, h - caOffset * 0.4f,
                        0.0f, 0.0f, 1.0f, 1.0f, r1, g1, b1, caAlpha);
                emitFboSliceQuad(caBuf, pose, caOffset, caOffset * 0.4f, w + caOffset, h + caOffset * 0.4f,
                        0.0f, 0.0f, 1.0f, 1.0f, r2, g2, b2, caAlpha);
                MeshData caMesh = caBuf.build();
                if (caMesh != null) BufferUploader.drawWithShader(caMesh);
                RenderSystem.defaultBlendFunc();
            }

            // 2. 全屏双环病理色偏滤镜与神经扫描噪波线 (Pathological Cortical Tint & Glitch Scanlines)
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            int innerRCol, innerGCol, innerBCol, outerRCol, outerGCol, outerBCol;
            switch (brainColorShiftMode) {
                case 1 -> {
                    innerRCol = 255; innerGCol = 140; innerBCol = 20;
                    outerRCol = 165; outerGCol = 12;  outerBCol = 28;
                }
                case 2 -> {
                    innerRCol = 110; innerGCol = 175; innerBCol = 235;
                    outerRCol = 18;  outerGCol = 32;  outerBCol = 62;
                }
                case 3 -> {
                    innerRCol = 0;   innerGCol = 235; innerBCol = 155;
                    outerRCol = 155; outerGCol = 15;  outerBCol = 195;
                }
                default -> {
                    innerRCol = 0;   innerGCol = 215; innerBCol = 255;
                    outerRCol = 95;  outerGCol = 15;  outerBCol = 165;
                }
            }

            float innerTintAlpha = Mth.clamp(effectiveColorShift * 0.22f * (0.75f + 0.25f * wave), 0.0f, 0.28f);
            float outerTintAlpha = Mth.clamp(effectiveColorShift * 0.68f * (0.80f + 0.20f * wave), 0.0f, 0.78f);

            PainVisualOverlay.drawEllipticalRing(pose, w, h,
                    0.0f, innerTintAlpha,
                    0.68f, innerTintAlpha * 1.4f,
                    innerRCol, innerGCol, innerBCol,
                    0.12f, 0.12f, 0.12f, 0.12f);

            PainVisualOverlay.drawEllipticalRing(pose, w, h,
                    0.52f, 0.0f,
                    1.42f, outerTintAlpha,
                    outerRCol, outerGCol, outerBCol,
                    0.18f, 0.18f, 0.25f, 0.18f);

            // 视皮层受损水平神经干扰扫描丝
            Tesselator tess = Tesselator.getInstance();
            BufferBuilder lineBuf = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            int scanCount = 10;
            long scanTick = nowMs / 50L;
            for (int i = 0; i < scanCount; i++) {
                long hSeed = (scanTick * 0x9E3779B97F4A7C15L) ^ (i * 0xBF58476D1CE4E5B9L);
                float sy = (((hSeed >>> 16) & 0xFFFF) / 65535.0f) * h;
                float sw = (0.25f + (((hSeed >>> 32) & 0xFF) / 255.0f) * 0.55f) * w;
                float sx = (((hSeed >>> 40) & 0xFF) / 255.0f) * (w - sw);
                int sAlpha = Mth.clamp((int) (effectiveColorShift * 95.0f), 0, 120);
                boolean useInner = (i % 2 == 0);
                int sr = useInner ? innerRCol : outerRCol;
                int sg = useInner ? innerGCol : outerGCol;
                int sb = useInner ? innerBCol : outerBCol;
                lineBuf.addVertex(pose, sx, sy, 0.0f).setColor(sr, sg, sb, sAlpha);
                lineBuf.addVertex(pose, sx + sw, sy, 0.0f).setColor(sr, sg, sb, sAlpha);
                lineBuf.addVertex(pose, sx + sw, sy + 1.5f, 0.0f).setColor(sr, sg, sb, sAlpha);
                lineBuf.addVertex(pose, sx, sy + 1.5f, 0.0f).setColor(sr, sg, sb, sAlpha);
            }
            MeshData lineMesh = lineBuf.build();
            if (lineMesh != null) BufferUploader.drawWithShader(lineMesh);
        }

        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    /**
     * 绘制采样自 OpenGL 帧缓冲纹理 (TextureTarget) 的矩形或切片面片
     * 注意：OpenGL FBO 纹理左下角为 (0,0)、左上角为 (0,1)，因此屏幕顶部 y0 对应纹理 V = 1.0f - vTop
     */
    private static void emitFboSliceQuad(BufferBuilder buf, Matrix4f pose,
                                         float x0, float y0, float x1, float y1,
                                         float u0, float vTop, float u1, float vBot,
                                         int r, int g, int b, int a) {
        float texVTop = 1.0f - vTop;
        float texVBot = 1.0f - vBot;
        buf.addVertex(pose, x0, y1, 0.0f).setUv(u0, texVBot).setColor(r, g, b, a);
        buf.addVertex(pose, x1, y1, 0.0f).setUv(u1, texVBot).setColor(r, g, b, a);
        buf.addVertex(pose, x1, y0, 0.0f).setUv(u1, texVTop).setColor(r, g, b, a);
        buf.addVertex(pose, x0, y0, 0.0f).setUv(u0, texVTop).setColor(r, g, b, a);
    }

    // =========================================================================
    // 2.6 肾上腺素激增 (Adrenaline Rush) 3A 战斗聚焦脉冲与径向速度流线
    // =========================================================================
    private static void updateAndRenderAdrenalineRush3A(GuiGraphics g, Font font, int w, int h,
                                                        MedicalData data, float dt, long nowMs) {
        float curAdr = ModItems.getAdrenalineLevel(data);
        if (curAdr > lastAdrenalineLevel + 8.0f) {
            adrenalineSurgeTimer = 2.6f;
        }
        lastAdrenalineLevel = curAdr;

        if (adrenalineSurgeTimer > 0.0f) {
            adrenalineSurgeTimer = Math.max(0.0f, adrenalineSurgeTimer - dt);
        }

        float surgeFactor = Mth.clamp(adrenalineSurgeTimer / 2.6f, 0.0f, 1.0f);
        float sustainFactor = Mth.clamp(curAdr / 75.0f, 0.0f, 1.0f);
        float targetAdr = Math.max(surgeFactor, sustainFactor * 0.85f);
        smoothAdrenalineIntensity = Mth.lerp(Math.min(1.0f, dt * 4.2f), smoothAdrenalineIntensity, targetAdr);

        if (smoothAdrenalineIntensity <= 0.015f) return;

        float pulse = PainVisualOverlay.evaluateDicroticPulse(heartPhase);
        Matrix4f pose = g.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        // 1. 外缘电光琥珀金交感脉冲环 (Electric Amber-Gold Combat Ring)
        float innerR = 0.78f - 0.12f * smoothAdrenalineIntensity - 0.03f * pulse;
        float goldAlpha = Mth.clamp(smoothAdrenalineIntensity * (0.28f + 0.24f * pulse + 0.25f * surgeFactor), 0.0f, 0.65f);
        PainVisualOverlay.drawEllipticalRing(pose, w, h,
                innerR, 0.0f,
                1.36f, goldAlpha,
                255, 195, 25,
                0.08f, 0.08f, 0.04f, 0.04f);

        // 2. 触发瞬间的冷白-亮青肾上腺素扩散冲击波
        if (surgeFactor > 0.05f) {
            float waveR = Mth.lerp(1.0f - surgeFactor, 0.32f, 0.96f);
            float waveA = Mth.sin(surgeFactor * Mth.PI) * 0.52f;
            PainVisualOverlay.drawEllipticalRing(pose, w, h,
                    Math.max(0.15f, waveR - 0.08f), 0.0f,
                    waveR + 0.08f, waveA,
                    195, 245, 255,
                    0.0f, 0.0f, 0.0f, 0.0f);
        }

        // 3. 视野四周 24 道径向高频战斗聚焦流线 (Radial Adrenaline Speed Filaments)
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float cx = w * 0.5f;
        float cy = h * 0.5f;
        float rx = w * 0.5f;
        float ry = h * 0.5f;
        int numRays = 24;
        for (int i = 0; i < numRays; i++) {
            double ang = (i / (double) numRays) * Math.PI * 2.0 + (nowMs % 2000L) * 0.0002;
            float cos = (float) Math.cos(ang);
            float sin = (float) Math.sin(ang);
            float shimmer = 0.5f + 0.5f * Mth.sin(nowMs * 0.015f + i * 1.7f);
            float rOut = 1.12f;
            float rIn = 0.82f - 0.12f * smoothAdrenalineIntensity * shimmer;

            int aOut = Mth.clamp((int) (smoothAdrenalineIntensity * shimmer * 165.0f), 0, 190);
            boolean isCyan = (i % 4 == 0);
            int r = isCyan ? 120 : 255;
            int gr = isCyan ? 245 : 215;
            int b = isCyan ? 255 : 40;
            PainVisualOverlay.addQuadSegment(buf, pose,
                    cx + cos * rx * rOut, cy + sin * ry * rOut,
                    cx + cos * rx * rIn, cy + sin * ry * rIn,
                    1.1f, r, gr, b, aOut, 0);
        }
        MeshData mesh = buf.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);

        RenderSystem.defaultBlendFunc();


        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }
}