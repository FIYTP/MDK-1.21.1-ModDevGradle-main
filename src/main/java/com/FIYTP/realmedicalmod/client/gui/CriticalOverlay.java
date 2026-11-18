package com.FIYTP.realmedicalmod.client.gui;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.capability.MedicalDataCapability;
import com.FIYTP.realmedicalmod.client.BrainDamageClientState;
import com.FIYTP.realmedicalmod.config.MedicalConfig;
import com.FIYTP.realmedicalmod.treatment.ModItems;
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
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 3A 级心血管危急与生命体征崩溃全屏视觉系统 (CriticalOverlay —— 彻底修复重伤黑屏遮挡与四角漏边 Bug 版)
 *
 * 【重伤黑屏遮挡 Bug 根因排查与彻底修复说明】：
 * 1. 根因一（离屏 TextureTarget FBO 黑块）：
 *    - 在 Minecraft 1.21.1 中，{@code GlStateManager._glBindFramebuffer(int target, int framebuffer)} 内部仅处理
 *      {@code GL30.GL_FRAMEBUFFER (36160)}，传入 {@code GL30.GL_READ_FRAMEBUFFER (36008)} 或 {@code GL_DRAW_FRAMEBUFFER (36009)}
 *      属于 100% 空操作（NO-OP），导致离屏 {@code TextureTarget} 始终未被写入任何画面而保持纯黑；
 *    - 同时 {@code blitRenderTarget(currentSceneTarget, freezeFrameTarget)} 内部的 {@code src.bindWrite(false)}
 *      误将渲染目标切换到了离屏的 {@code currentSceneTarget}，并在脑震荡位移切片渲染时将纯黑纹理偏移了 ±10~35px 绘制到屏幕上，
 *      从而呈现出“受重伤后出现一个没完全挡住整个屏幕、四周边缘角流出画面的黑屏，且把濒死倒计时等所有视觉效果全部盖死”的现象！
 * 2. 根因二（旧版意识昏迷/失明遮罩或渲染层级冲突）：
 *    - 若工程中残留旧版 {@code ConsciousnessOverlay}（以半径 r=1.0 的内切椭圆绘制黑屏，无法覆盖 16:9 矩形屏幕四角 `r=1.414`）
 *      或两个 Overlay 同处 {@code EventPriority.NORMAL} 且未调用 {@code g.flush()}，会导致黑屏压在濒死倒计时之上。
 * 3. 根治方案：
 *    - 彻底废除离屏 {@code TextureTarget} FBO 拷贝，将 3A 脑损伤定格震荡、记忆回闪与四态皮层变色全面升级为 100% 纯程序化矢量/棱镜光栅着色（中心永远透明、绝不绑定或绘制任何黑屏 FBO）；
 *    - 自动扫描并卸载 {@code NeoForge.EVENT_BUS} 上任何残留的旧版意识/失明黑屏遮罩类，改由本类统一渲染无缝覆盖屏幕四角（{@code borderDist * 1.02f}）且中心通透的 3A 意识模糊/昏迷隧道暗角；
 *    - 将本类 {@code onRenderGui} 设为 {@code EventPriority.LOWEST}，并在绘制濒死倒计时、室颤、心脏骤停全息告警舱前后显式执行 {@code g.flush()}，保证倒计时与核心 HUD 100% 永远处于最顶层！
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

    private static boolean legacyOverlaysChecked = false;

    private static long lastFrameNs = 0L;
    private static float smoothHr = 75.0f;
    private static float heartPhase = 0.0f;
    private static float tachycardiaFactor = 0.0f;
    private static float criticalUrgency = 0.0f;
    private static float arrestTransitionFlash = 0.0f;
    private static float arrestCollapseProgress = 0.0f;
    private static boolean wasCardiacArrest = false;
    private static float smoothConsciousness = 100.0f;

    // 阿片类药物推注「多巴胺欣快感 (Opioid Euphoria)」状态追踪
    private static int lastOpioidConcentration = 0;
    private static float euphoriaRushTimer = 0.0f;
    private static float smoothEuphoriaIntensity = 0.0f;

    // 肾上腺素激增 (Adrenaline Rush) 3A 战斗聚焦与广角脉冲状态
    private static float lastAdrenalineLevel = 0.0f;
    private static float adrenalineSurgeTimer = 0.0f;
    private static float smoothAdrenalineIntensity = 0.0f;

    // ==================== 3A 脑损伤视觉系统（纯矢量无黑屏版：神经震荡撕裂、突触闪回波环、四态皮层变色） ====================
    private static int lastBrainHealth = -1;
    private static boolean wasBrainControlInverted = false;
    private static float brainEpisodeCooldownSec = 2.5f;

    private static float brainFlashbackTimer = 0.0f;
    private static float brainFlashbackDuration = 1.6f;
    private static float brainFlashbackPeakIntensity = 0.0f;

    private static float brainFreezeShakeTimer = 0.0f;
    private static float brainFreezeShakeDuration = 1.35f;
    private static float brainFreezePeakIntensity = 0.0f;
    private static long brainEpisodeSeed = 1337L;

    private static float brainColorShiftTimer = 0.0f;
    private static float brainColorShiftDuration = 2.8f;
    private static float smoothBrainColorShift = 0.0f;
    private static int brainColorShiftMode = 0; // 0: 缺血紫青, 1: 血肿猩红琥珀, 2: 视皮层冷蓝失色, 3: 突触迷幻双色分离

    /**
     * 自动检测并卸载任何残留的旧版黑屏遮罩类（如旧版 ConsciousnessOverlay / UnconsciousOverlay / BlindnessOverlay），
     * 防止旧版内切圆黑屏遮挡 3A 濒危倒计时与四角漏边。
     */
    private static void ensureLegacyBlackScreenOverlaysSuppressed() {
        if (legacyOverlaysChecked) return;
        legacyOverlaysChecked = true;

        String[] candidateLegacyClasses = new String[]{
                "com.FIYTP.realmedicalmod.client.gui.ConsciousnessOverlay",
                "com.FIYTP.realmedicalmod.client.gui.UnconsciousOverlay",
                "com.FIYTP.realmedicalmod.client.gui.BlindnessOverlay",
                "com.FIYTP.realmedicalmod.client.gui.VisionOverlay",
                "com.FIYTP.realmedicalmod.client.gui.BloodLossOverlay",
                "com.FIYTP.realmedicalmod.client.ConsciousnessOverlay",
                "com.FIYTP.realmedicalmod.client.UnconsciousOverlay"
        };
        ClassLoader cl = CriticalOverlay.class.getClassLoader();
        for (String clsName : candidateLegacyClasses) {
            try {
                Class<?> legacyCls = Class.forName(clsName, false, cl);
                NeoForge.EVENT_BUS.unregister(legacyCls);
            } catch (Throwable ignored) {
            }
        }

        // 反射遍历 NeoForge.EVENT_BUS 已注册监听器，自动剥离 client.gui 包下除三大 3A Overlay 之外的旧版遮罩
        try {
            Field listenersField = NeoForge.EVENT_BUS.getClass().getDeclaredField("listeners");
            listenersField.setAccessible(true);
            Object mapObj = listenersField.get(NeoForge.EVENT_BUS);
            if (mapObj instanceof Map<?, ?> map) {
                List<Object> toRemove = new ArrayList<>();
                for (Object key : map.keySet()) {
                    if (key == null) continue;
                    Class<?> kCls = (key instanceof Class<?> c) ? c : key.getClass();
                    String name = kCls.getName();
                    if (name.startsWith("com.FIYTP.realmedicalmod.client.")
                            && (name.contains("Consciousness") || name.contains("Unconscious")
                            || name.contains("Blindness") || name.contains("Blackout"))) {
                        toRemove.add(key);
                    } else if (name.startsWith("com.FIYTP.realmedicalmod.client.gui.")
                            && name.endsWith("Overlay")
                            && !name.endsWith("CriticalOverlay")
                            && !name.endsWith("PainVisualOverlay")
                            && !name.endsWith("PainShockOverlay")) {
                        toRemove.add(key);
                    }
                }
                for (Object target : toRemove) {
                    NeoForge.EVENT_BUS.unregister(target);
                }
            }
        } catch (Throwable ignored) {
        }
    }

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

        // ★ 3A 脑损伤镜头震荡：阶跃痉挛抽搐 + 欠阻尼高频脑震荡摇晃与闪回漂移 ★
        if (MedicalConfig.BRAIN_VFX_UPGRADE_ENABLED.get()) {
            float shakeAmpCfg = MedicalConfig.BRAIN_VFX_SHAKE_AMPLITUDE.get().floatValue();
            if (brainFreezeShakeTimer > 0.001f) {
                float p = 1.0f - Mth.clamp(brainFreezeShakeTimer / Math.max(0.1f, brainFreezeShakeDuration), 0.0f, 1.0f);
                float env = (float) Math.pow(1.0f - p, 1.65) * brainFreezePeakIntensity * shakeAmpCfg;
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

    /**
     * 使用 LOWEST 优先级确保本类的全息告警舱（20s 濒死倒计时、室颤 ECG、心脏骤停平线）
     * 永远在所有暗角遮罩绘制完毕之后最后绘制并 flush 到主屏幕，绝不被任何特效遮挡！
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderGui(RenderGuiEvent.Post event) {
        ensureLegacyBlackScreenOverlaysSuppressed();

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null) return;
        if (mc.options.hideGui || player.isSpectator()) return;

        // 移除原版失明/黑暗药水造成的不自然黑雾（统一由 3A 极坐标无缝椭圆暗角呈现）
        if (player.hasEffect(MobEffects.BLINDNESS)) {
            player.removeEffectNoUpdate(MobEffects.BLINDNESS);
        }
        if (player.hasEffect(MobEffects.DARKNESS)) {
            player.removeEffectNoUpdate(MobEffects.DARKNESS);
        }

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

        float targetCons = data.isCardiacArrest() ? 0.0f : Mth.clamp(data.getConsciousnessFloat(), 0.0f, 100.0f);
        smoothConsciousness = Mth.lerp(Math.min(1.0f, dt * 5.0f), smoothConsciousness, targetCons);

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

        // 先刷新之前队列中的 GUI 批次，确保底层晕影与顶层 HUD 卡片层级严格分离
        g.flush();

        // ===== 优先级 1：心脏骤停 (Cardiac Arrest & Asystole Flatline) =====
        if (isArrest) {
            renderCardiacArrest3A(g, mc.font, w, h, data, nowMs);
            g.flush();
            return;
        }

        // ===== 优先级 2：心室纤颤 (Ventricular Fibrillation - VFib) =====
        if (data.isInVFib()) {
            renderVFib3A(g, mc.font, w, h, data, nowMs);
            g.flush();
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

        // ===== 优先级 2.8：3A 脑损伤视觉层（无黑屏纯程序化色差错位、突触回闪光环与四态皮层变色） =====
        updateAndRenderBrainDamageVfx3A(g, w, h, data, dt, nowMs);

        // ===== 优先级 3：背景生理障碍层（心动过速、呼吸异常、血胸、颅内出血、意识模糊/昏迷无缝椭圆暗角） =====
        renderSubCriticalRadialLayers(g, mc.font, w, h, data, nowMs);
        renderConsciousnessSyncope3A(g, w, h, data, nowMs);

        // ===== 优先级 4（最顶层）：失血至 <= 3000mL 的濒危状态 + 20 秒生命倒计时核心舱 =====
        if (data.isInCriticalState() && data.getCriticalTimer() > 0) {
            renderCriticalHypovolemicCountdown3A(g, mc.font, w, h, data, nowMs);
        }

        // 强制立即提交顶层倒计时与 HUD 告警舱顶点，防止被任何后续渲染覆盖
        g.flush();
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
        if (curOpioid > lastOpioidConcentration + 2) {
            euphoriaRushTimer = MedicalConfig.OPIOID_EUPHORIA_RUSH_DURATION_SEC.get().floatValue();
        }
        lastOpioidConcentration = curOpioid;

        if (euphoriaRushTimer > 0.0f) {
            euphoriaRushTimer = Math.max(0.0f, euphoriaRushTimer - dt);
        }

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
        float slowBreath = 0.5f + 0.5f * Mth.sin(nowMs * 0.0018f);

        Matrix4f pose = g.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        float innerR = Mth.lerp(smoothEuphoriaIntensity, 0.78f, 0.46f) + 0.04f * slowBreath;
        float goldAlpha = Mth.clamp(smoothEuphoriaIntensity * maxAlphaCfg * (0.75f + 0.25f * slowBreath), 0.0f, 0.75f);
        PainVisualOverlay.drawEllipticalRing(pose, w, h,
                innerR, 0.0f,
                1.38f, goldAlpha,
                255, 195, 95,
                0.08f, 0.12f, 0.0f, 0.0f);

        float roseAlpha = Mth.clamp(smoothEuphoriaIntensity * maxAlphaCfg * 0.62f * (1.0f - 0.2f * slowBreath), 0.0f, 0.65f);
        PainVisualOverlay.drawEllipticalRing(pose, w, h,
                innerR - 0.10f, 0.0f,
                innerR + 0.28f, roseAlpha,
                255, 105, 195,
                0.15f, 0.0f, 0.15f, 0.0f);

        if (rushFactor > 0.05f) {
            float waveRadius = Mth.lerp(1.0f - rushFactor, 0.95f, 0.22f);
            float waveAlpha = Mth.sin(rushFactor * Mth.PI) * maxAlphaCfg * 0.68f;
            PainVisualOverlay.drawEllipticalRing(pose, w, h,
                    Math.max(0.05f, waveRadius - 0.12f), 0.0f,
                    waveRadius + 0.12f, waveAlpha,
                    255, 225, 140,
                    0.0f, 0.0f, 0.0f, 0.0f);
        }

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int moteCount = 32;
        float timeSec = (nowMs % 120000L) / 1000.0f;
        boolean hasMotes = false;
        for (int i = 0; i < moteCount; i++) {
            float seed = i * 1.6180339f;
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

            buf.addVertex(pose, px, py - rad, 0.0f).setColor(mr, mg, mb, mAlpha);
            buf.addVertex(pose, px - rad, py, 0.0f).setColor(mr, mg, mb, mAlpha);
            buf.addVertex(pose, px, py + rad, 0.0f).setColor(mr, mg, mb, mAlpha);
            buf.addVertex(pose, px + rad, py, 0.0f).setColor(mr, mg, mb, mAlpha);
            hasMotes = true;
        }
        MeshData mesh = buf.build();
        if (mesh != null && hasMotes) {
            BufferUploader.drawWithShader(mesh);
        }

        RenderSystem.enableCull();
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
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        float brainPct = Mth.clamp(data.getBrainHealth() / 100.0f, 0.0f, 1.0f);
        float collapse = Mth.clamp(arrestCollapseProgress * 0.7f + (1.0f - brainPct) * 0.3f, 0.0f, 1.0f);

        // A. 深渊缺氧黑红三层椭圆径向暗角（中心留出求生视野，外环自适应覆盖屏幕四角绝不漏边）
        float innerR = Mth.lerp(collapse, 0.54f, 0.28f);
        float midR = innerR + 0.24f;

        PainVisualOverlay.drawEllipticalRing(pose, w, h,
                innerR, 0.0f,
                midR, 0.58f,
                95, 4, 12,
                0.0f, 0.0f, 0.0f, 0.0f);

        PainVisualOverlay.drawEllipticalRing(pose, w, h,
                midR, 0.58f,
                1.45f, 0.90f,
                14, 2, 5,
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
                    0.22f, arrestTransitionFlash * 0.25f,
                    1.35f, arrestTransitionFlash * 0.80f,
                    255, 220, 225,
                    0.0f, 0.0f, 0.0f, 0.0f);
        }
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();

        if (Minecraft.getInstance().screen != null) {
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
            return;
        }

        // D. 最顶层绘制中央偏上的全息平线告警舱
        g.flush();
        RenderSystem.disableDepthTest();
        int cardW = Math.min(256, w - 32);
        int cardH = 66;
        int cardX = (w - cardW) / 2;
        int cardY = Math.max(18, (int) (h * 0.14f));

        HudRenderHelper.drawSciFiCard(g, cardX, cardY, cardW, cardH, 5, 0xE6080205, C_ALERT_RED);
        HudRenderHelper.drawCardCorners(g, cardX, cardY, cardW, cardH, 9, C_ALERT_RED);

        float blink = 0.55f + 0.45f * Mth.sin(nowMs * 0.012f);
        int headerAlpha = (int) (blink * 160.0f);
        g.fill(cardX + 2, cardY + 2, cardX + cardW - 2, cardY + 16, (headerAlpha << 24) | 0x880015);

        String title = Component.translatable("medical.warning.cardiac_arrest").getString();
        HudRenderHelper.drawCenteredGlowingString(g, font, title, cardX + cardW / 2, cardY + 5, 0xFFFFFFFF, C_ALERT_RED);

        int scopeX = cardX + 10;
        int scopeY = cardY + 20;
        int scopeW = cardW - 20;
        int scopeH = 22;
        g.fill(scopeX, scopeY, scopeX + scopeW, scopeY + scopeH, 0xCC03060A);
        HudRenderHelper.drawSciFiBorder(g, scopeX, scopeY, scopeW, scopeH, 0x66FF2040);

        int flatY = scopeY + scopeH / 2;
        float sweepPct = (nowMs % 1800L) / 1800.0f;
        int cursorX = scopeX + 4 + (int) ((scopeW - 8) * sweepPct);

        if (cursorX > scopeX + 6) {
            g.fill(scopeX + 4, flatY - 1, cursorX, flatY + 2, 0x44FF1744);
            g.fill(scopeX + 4, flatY, cursorX, flatY + 1, 0xFFFF1744);
        }
        int afterGap = cursorX + 14;
        if (afterGap < scopeX + scopeW - 4) {
            g.fill(afterGap, flatY, scopeX + scopeW - 4, flatY + 1, 0x66FF1744);
        }
        g.fill(cursorX - 1, flatY - 2, cursorX + 2, flatY + 3, 0xFFFFFFFF);

        int barY = cardY + 46;
        int halfW = (scopeW - 8) / 2;
        int bh = data.getBrainHealth();
        int spo2 = data.getBloodOxygen();

        g.drawString(font, "脑健康: " + bh + "%", scopeX, barY, C_ALERT_RED, false);
        HudRenderHelper.drawSciFiProgressBar(g, scopeX, barY + 10, halfW, 5, bh / 100.0f, C_ALERT_RED, 0xFF1A0508);

        int rx = scopeX + halfW + 8;
        g.drawString(font, "SpO2: " + spo2 + "%", rx, barY, C_OXYGEN_BLUE, false);
        HudRenderHelper.drawSciFiProgressBar(g, rx, barY + 10, halfW, 5, spo2 / 100.0f, C_OXYGEN_BLUE, 0xFF05121A);

        g.flush();
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
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        double t = nowMs * 0.001;
        float fibrilWave = (float) (0.50 + 0.22 * Math.sin(t * 19.0) + 0.16 * Math.cos(t * 31.0) + 0.12 * Math.sin(t * 47.0));
        fibrilWave = Mth.clamp(fibrilWave, 0.15f, 1.0f);

        float innerR = 0.50f - 0.08f * fibrilWave;
        float midR = innerR + 0.22f;

        PainVisualOverlay.drawEllipticalRing(pose, w, h,
                innerR, 0.0f,
                midR, 0.48f * fibrilWave,
                195, 10, 35,
                0.0f, 0.0f, 0.0f, 0.0f);
        PainVisualOverlay.drawEllipticalRing(pose, w, h,
                midR, 0.48f * fibrilWave,
                1.42f, 0.82f,
                28, 4, 18,
                0.0f, 0.0f, 0.0f, 0.0f);

        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        PainVisualOverlay.drawEllipticalRing(pose, w, h,
                innerR - 0.03f, 0.0f,
                innerR + 0.09f, 0.38f * fibrilWave,
                0, 225, 255,
                0.15f, 0.15f, 0.15f, 0.15f);

        drawBioElectricFibrillationArcs(pose, w, h, fibrilWave, nowMs);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();

        if (Minecraft.getInstance().screen != null) {
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
            return;
        }

        // C. 顶部全息室颤紧急告警舱（强制刷新确保位于最顶层）
        g.flush();
        RenderSystem.disableDepthTest();
        int cardW = Math.min(252, w - 32);
        int cardH = 64;
        int cardX = (w - cardW) / 2;
        int cardY = Math.max(18, (int) (h * 0.14f));

        int jitterX = (int) (Math.sin(t * 43.0) * 1.6);
        int jitterY = (int) (Math.cos(t * 37.0) * 1.2);
        cardX += jitterX;
        cardY += jitterY;

        HudRenderHelper.drawSciFiCard(g, cardX, cardY, cardW, cardH, 5, 0xE00A040C, 0xFFFF1744);
        HudRenderHelper.drawCardCorners(g, cardX, cardY, cardW, cardH, 9, C_NEON_CYAN);

        String vfibTitle = Component.translatable("medical.warning.vfib").getString();
        HudRenderHelper.drawCenteredGlowingString(g, font, vfibTitle, cardX + cardW / 2, cardY + 5, 0xFFFFD000, C_ALERT_RED);

        int scopeX = cardX + 10;
        int scopeY = cardY + 19;
        int scopeW = cardW - 20;
        int scopeH = 24;
        g.fill(scopeX, scopeY, scopeX + scopeW, scopeY + scopeH, 0xCC040810);
        HudRenderHelper.drawSciFiBorder(g, scopeX, scopeY, scopeW, scopeH, 0x88FF1744);
        g.flush();

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
        boolean hasVerts = false;

        long bucket = nowMs / 45L;
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
                hasVerts = true;
            }
        }

        MeshData mesh = buf.build();
        if (mesh != null && hasVerts) BufferUploader.drawWithShader(mesh);
    }

    // =========================================================================
    // 3. 心动过速、呼吸异常、血胸与颅内出血的 3A 径向视觉层
    // =========================================================================
    private static void renderSubCriticalRadialLayers(GuiGraphics g, Font font, int w, int h, MedicalData data, long nowMs) {
        Matrix4f pose = g.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        float pulse = PainVisualOverlay.evaluateDicroticPulse(heartPhase);

        // ---------- A. 心动过速 3A 动脉重搏冲击光晕 (Tachycardia) ----------
        if (tachycardiaFactor > 0.01f) {
            float surge = (pulse > 0.40f) ? ((pulse - 0.40f) / 0.60f) : 0.0f;
            float outerAlpha = Mth.clamp(tachycardiaFactor * (0.18f + 0.38f * pulse), 0.0f, 0.62f);
            float innerR = 0.76f - 0.18f * tachycardiaFactor - 0.05f * surge * tachycardiaFactor;

            PainVisualOverlay.drawEllipticalRing(pose, w, h,
                    innerR, 0.0f,
                    1.38f, outerAlpha,
                    190, 22, 18,
                    0.0f, 0.0f, 0.0f, 0.0f);

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
        }

        // ---------- B. 极端呼吸异常无缝椭圆红晕 ----------
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

        // ---------- C. 血胸 2+ 级胸膜压迫无缝暗红椭圆晕影 ----------
        int hemoLevel = data.getHemothoraxLevel();
        if (hemoLevel >= 2) {
            float hemoAlpha = (hemoLevel == 2) ? 0.32f : (hemoLevel == 3 ? 0.48f : 0.64f);
            float hemoInner = 0.70f - (hemoLevel - 2) * 0.07f;
            PainVisualOverlay.drawEllipticalRing(pose, w, h,
                    hemoInner, 0.0f,
                    1.38f, hemoAlpha,
                    95, 4, 8,
                    0.15f, 0.15f, 0.05f, 0.35f);
        }

        // ---------- D. 颅内出血清醒期后隧道视野暗角（中心留出清晰视野，外环完全覆盖四角） ----------
        if (data.getIntracranialOnsetTimer() >= MedicalConfig.ONSET_DELAY.get() && data.getIntracranialLevel() >= 1) {
            int icLv = data.getIntracranialLevel();
            float icInner = Mth.clamp(0.72f - icLv * 0.09f, 0.34f, 0.68f);
            float icAlpha = Mth.clamp(0.28f + icLv * 0.12f, 0.0f, 0.76f);
            PainVisualOverlay.drawEllipticalRing(pose, w, h,
                    icInner, 0.0f,
                    1.42f, icAlpha,
                    18, 6, 26,
                    0.0f, 0.0f, 0.25f, 0.0f);
        }

        // ---------- E. 阿片类药物镇静 / 过量呼吸抑制「针尖样瞳孔 (Miosis)」紫青视野缩窄 ----------
        int opioidConc = data.getOpioidConcentration();
        int sedationStart = MedicalConfig.OPIOID_SEDATION_START.get();
        if (opioidConc >= sedationStart) {
            float opRatio = Mth.clamp((opioidConc - sedationStart) / Math.max(1.0f, 100.0f - sedationStart), 0.0f, 1.0f);
            float slowWave = 0.5f + 0.5f * Mth.sin(nowMs * 0.0035f);
            float miosisInner = Mth.lerp(opRatio, 0.74f, 0.34f) - 0.02f * slowWave;
            float miosisAlpha = Mth.clamp(0.22f + opRatio * 0.54f, 0.0f, 0.78f);
            PainVisualOverlay.drawEllipticalRing(pose, w, h,
                    miosisInner, 0.0f,
                    1.40f, miosisAlpha,
                    18, 6, 32,
                    0.0f, 0.0f, 0.0f, 0.0f);
        }

        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    /**
     * 3A 级低意识与昏迷隧道视觉层 (Consciousness Syncope & Coma Tunnel)
     * 彻底替代原版简陋的内切椭圆全黑遮罩：
     * 1. 外环半径 1.45f 配合 borderDist * 1.02f，100% 覆盖矩形屏幕四个角落，绝不漏出四角画面；
     * 2. 中心区域始终保持通透（rInner >= 0.32f），且在濒危倒计时等顶层 HUD 卡片之前渲染，绝不遮挡濒死倒计时和其他视觉效果！
     */
    private static void renderConsciousnessSyncope3A(GuiGraphics g, int w, int h, MedicalData data, long nowMs) {
        Matrix4f pose = g.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        // 1) 缺氧/气胸/血胸「紫绀窒息视野与视网膜静电雪花噪点 (Hypoxia Retinal Visual Snow)」
        int pneumoType = ModItems.getPneumothoraxType(data);
        float pleuralAir = ModItems.getPleuralAirMl(data);
        int hemoLv = data.getHemothoraxLevel();
        float spo2Deficit = Mth.clamp((92.0f - data.getBloodOxygenFloat()) / 26.0f, 0.0f, 1.0f);
        float asphyxiaFactor = Mth.clamp(
                spo2Deficit + (pneumoType > 0 ? 0.25f + pneumoType * 0.18f : 0.0f)
                        + (pleuralAir / 1200.0f) * 0.45f + hemoLv * 0.18f,
                0.0f, 1.0f
        ) * MedicalConfig.TRAUMA_HYPOXIA_VISUAL_SNOW_INTENSITY.get().floatValue();

        if (asphyxiaFactor > 0.02f) {
            float gaspWave = 0.5f + 0.5f * Mth.sin(nowMs * 0.0055f);
            float cyanInner = Mth.lerp(Mth.clamp(asphyxiaFactor, 0.0f, 1.0f), 0.72f, 0.34f) - 0.03f * gaspWave;
            PainVisualOverlay.drawEllipticalRing(pose, w, h,
                    cyanInner, 0.0f,
                    cyanInner + 0.24f, Mth.clamp(asphyxiaFactor * 0.52f, 0.0f, 0.68f),
                    10, 22, 48,
                    0.15f, 0.15f, 0.20f, 0.20f);
            PainVisualOverlay.drawEllipticalRing(pose, w, h,
                    cyanInner + 0.20f, Mth.clamp(asphyxiaFactor * 0.50f, 0.0f, 0.65f),
                    1.48f, Mth.clamp(asphyxiaFactor * 0.88f, 0.0f, 0.94f),
                    0, 2, 8,
                    0.15f, 0.15f, 0.20f, 0.20f);

            // 视网膜缺氧静电雪花噪点 (Retinal Visual Snow)
            Tesselator snowTess = Tesselator.getInstance();
            BufferBuilder snowBuf = snowTess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            long frameBucket = nowMs / 35L;
            int grainCount = Math.min(140, Math.round(45 + asphyxiaFactor * 85));
            boolean hasSnow = false;
            float cx = w * 0.5f;
            float cy = h * 0.5f;
            for (int i = 0; i < grainCount; i++) {
                long h1 = (frameBucket * 0x9E3779B97F4A7C15L) ^ (i * 0xBF58476D1CE4E5B9L);
                h1 = (h1 ^ (h1 >>> 30)) * 0xBF58476D1CE4E5B9L;
                float uAngle = ((h1 & 0xFFFF) / 65535.0f) * Mth.TWO_PI;
                float uRad = Mth.lerp(((h1 >>> 16) & 0xFFFF) / 65535.0f, cyanInner * 0.85f, 1.25f);
                float gx = cx + Mth.cos(uAngle) * (w * 0.5f) * uRad;
                float gy = cy + Mth.sin(uAngle) * (h * 0.5f) * uRad;
                if (gx < 0 || gx >= w || gy < 0 || gy >= h) continue;

                boolean darkGrain = ((h1 >>> 33) & 1L) == 0L;
                int gr = darkGrain ? 0 : 95;
                int gg = darkGrain ? 4 : 175;
                int gb = darkGrain ? 12 : 235;
                int ga = Mth.clamp(Math.round(asphyxiaFactor * (darkGrain ? 195.0f : 115.0f)), 0, 225);
                float sz = 1.2f + (((h1 >>> 40) & 0xFF) / 255.0f) * 1.8f;
                snowBuf.addVertex(pose, gx - sz, gy + sz, 0.0f).setColor(gr, gg, gb, ga);
                snowBuf.addVertex(pose, gx + sz, gy + sz, 0.0f).setColor(gr, gg, gb, ga);
                snowBuf.addVertex(pose, gx + sz, gy - sz, 0.0f).setColor(gr, gg, gb, ga);
                snowBuf.addVertex(pose, gx - sz, gy - sz, 0.0f).setColor(gr, gg, gb, ga);
                hasSnow = true;
            }
            MeshData snowMesh = snowBuf.build();
            if (snowMesh != null && hasSnow) {
                BufferUploader.drawWithShader(snowMesh);
            }
        }

        // 2) 大失血休克「冷汗水雾霜化与失色暗角 (Hypovolemic Cold-Sweat Frost)」
        float bloodLossRatio = Mth.clamp((data.getMaxBloodVolume() - data.getBloodVolume()) / 2000.0f, 0.0f, 1.0f)
                * MedicalConfig.TRAUMA_COLD_SWEAT_FROST_INTENSITY.get().floatValue();
        if (bloodLossRatio > 0.08f) {
            float frostInner = Mth.lerp(Mth.clamp(bloodLossRatio, 0.0f, 1.0f), 0.76f, 0.38f);
            PainVisualOverlay.drawEllipticalRing(pose, w, h,
                    frostInner, 0.0f,
                    frostInner + 0.22f, Mth.clamp(bloodLossRatio * 0.38f, 0.0f, 0.52f),
                    155, 185, 210,
                    0.10f, 0.10f, 0.15f, 0.15f);
        }

        // 3) 低意识与昏迷深黑隧道暗角 + 沉重眼皮下垂闭合幕 (Consciousness Blackout & Eyelid Droop)
        if (smoothConsciousness < 78.0f) {
            float deficit = Mth.clamp((78.0f - smoothConsciousness) / 78.0f, 0.0f, 1.0f);
            float slowBreath = 0.5f + 0.5f * Mth.sin(nowMs * 0.0022f);
            float minInnerR = MedicalConfig.TRAUMA_BLACK_CORNER_MIN_INNER_RADIUS.get().floatValue();
            float maxBlackAlpha = MedicalConfig.TRAUMA_BLACK_CORNER_MAX_ALPHA.get().floatValue();

            float tunnelInner = Mth.lerp(deficit, 0.76f, minInnerR) - 0.025f * slowBreath * deficit;
            float tunnelMid = tunnelInner + 0.20f;
            float midAlpha = Mth.clamp(deficit * 0.68f, 0.0f, 0.78f);
            float outerAlpha = Mth.clamp(deficit * (0.88f + 0.08f * slowBreath), 0.0f, maxBlackAlpha);

            // 中圈：深渊暗黑过渡
            PainVisualOverlay.drawEllipticalRing(pose, w, h,
                    tunnelInner, 0.0f,
                    tunnelMid, midAlpha,
                    4, 6, 10,
                    0.0f, 0.0f, 0.25f, 0.25f);

            // 外圈：纯黑 (#000000) 四角严丝合缝封边
            PainVisualOverlay.drawEllipticalRing(pose, w, h,
                    tunnelMid, midAlpha,
                    1.48f, outerAlpha,
                    0, 0, 0,
                    0.0f, 0.0f, 0.35f, 0.35f);

            // 意识低于 52% 时叠加「沉重眼皮缓慢眨眼下垂感」（上下黑幕随呼吸沉重合拢又强撑睁开）
            float eyelidStrengthCfg = MedicalConfig.TRAUMA_EYELID_DROOP_MAX_STRENGTH.get().floatValue();
            if (smoothConsciousness < 52.0f && eyelidStrengthCfg > 0.01f) {
                float lidFactor = Mth.clamp((52.0f - smoothConsciousness) / 52.0f, 0.0f, 1.0f) * eyelidStrengthCfg;
                float blinkWave = (float) Math.pow(0.5 + 0.5 * Math.sin(nowMs * 0.0019), 2.2);
                int lidDepthH = Math.round(h * (0.08f + 0.24f * lidFactor * (0.65f + 0.35f * blinkWave)));
                int solidH = Math.max(2, lidDepthH / 3);
                g.fill(0, 0, w, solidH, 0xF2000000);
                g.fillGradient(0, solidH, w, lidDepthH, 0xF2000000, 0x00000000);
                g.fill(0, h - solidH, w, h, 0xF2000000);
                g.fillGradient(0, h - lidDepthH, w, h - solidH, 0x00000000, 0xF2000000);
            }
        }

        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    // =========================================================================
    // 4. 失血至 <= 3000mL 濒危状态与 20 秒生命倒计时 3A 特效（最顶层全息告警舱）
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
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        // A. 失血性休克双层径向隧道视野：中心与上方倒计时区域保持通透，外环完整覆盖四角
        float tunnelInner = Mth.lerp(urgency, 0.64f, 0.36f) - 0.03f * pulse;
        float tunnelMid = tunnelInner + 0.22f;

        PainVisualOverlay.drawEllipticalRing(pose, w, h,
                tunnelInner, 0.0f,
                tunnelMid, Mth.clamp(0.24f + 0.28f * urgency, 0.0f, 0.54f),
                38, 45, 58,
                0.0f, 0.0f, 0.0f, 0.0f);

        PainVisualOverlay.drawEllipticalRing(pose, w, h,
                tunnelMid, Mth.clamp(0.42f + 0.32f * urgency, 0.0f, 0.76f),
                1.48f, Mth.clamp(0.78f + 0.18f * urgency, 0.0f, 0.96f),
                0, 0, 0,
                0.0f, 0.0f, 0.0f, 0.0f);

        // B. 倒计时最后 5 秒叠加濒死脉冲红闪
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

        RenderSystem.enableCull();

        if (Minecraft.getInstance().screen != null) {
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
            return;
        }

        // C. 强制刷新并在最顶层绘制 3A 全息濒危倒计时核心仪（绝不被任何暗角遮挡！）
        g.flush();
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        int boxW = Math.min(244, w - 32);
        int boxH = 58;
        int boxX = (w - boxW) / 2;
        int boxY = Math.max(16, (int) (h * 0.11f));

        boolean urgentBeat = (timerSec <= 5) && ((nowMs / 120L) % 2L == 0L);
        int borderCol = urgentBeat ? 0xFFFFFFFF : C_ALERT_RED;

        HudRenderHelper.drawSciFiCard(g, boxX, boxY, boxW, boxH, 5, 0xEE080206, borderCol);
        HudRenderHelper.drawCardCorners(g, boxX, boxY, boxW, boxH, 8, C_NEON_CYAN);

        String warnText = Component.translatable("medical.warning.critical").getString();
        HudRenderHelper.drawCenteredGlowingString(g, font, warnText, boxX + boxW / 2, boxY + 5, 0xFFFFD000, C_ALERT_RED);

        int subTenths = 9 - (int) ((nowMs % 1000L) / 100L);
        String digits = String.format("%02d.%d", Mth.clamp(timerSec, 0, 99), subTenths);
        int segColor = (timerSec <= 5) ? 0xFFFF1744 : 0xFFFF5252;
        int dimSeg = withAlpha(segColor, 0.15f);
        SegmentDisplayRenderer.renderString(g, digits, boxX + 14, boxY + 19, 8, 17, 3, 2, segColor, dimSeg);
        g.drawString(font, "SEC", boxX + 64, boxY + 28, 0xFFFF8A80, false);

        float remainPct = Mth.clamp((timerSec - (1.0f - subTenths / 10.0f) * 0.5f) / maxCritSec, 0.0f, 1.0f);
        HudRenderHelper.drawSciFiProgressBar(g, boxX + 10, boxY + 46, boxW - 20, 6, remainPct, segColor, 0xFF1A060A);

        g.flush();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    // =========================================================================
    // 2.8 3A 级脑损伤视觉系统（纯程序化无黑屏矢量版：震荡色差错位带、突触记忆波环、四态皮层变色）
    // =========================================================================

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
        brainEpisodeSeed = System.currentTimeMillis() ^ System.nanoTime();
    }

    public static void triggerBrainFreezeShake(float intensity, float durationSec) {
        brainFreezeShakeDuration = Math.max(0.35f, durationSec);
        brainFreezeShakeTimer = brainFreezeShakeDuration;
        brainFreezePeakIntensity = Mth.clamp(intensity, 0.30f, 1.0f);
        brainEpisodeSeed = System.currentTimeMillis() ^ System.nanoTime();
    }

    public static void triggerBrainColorShift(int mode, float intensity, float durationSec) {
        brainColorShiftMode = Math.floorMod(mode, 4);
        brainColorShiftDuration = Math.max(0.5f, durationSec);
        brainColorShiftTimer = brainColorShiftDuration;
        smoothBrainColorShift = Math.max(smoothBrainColorShift, Mth.clamp(intensity, 0.25f, 1.0f));
    }

    private static void updateAndRenderBrainDamageVfx3A(GuiGraphics g, int w, int h,
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

        float healthDeficit = (brainHealth < 90) ? Mth.clamp((90.0f - brainHealth) / 75.0f, 0.0f, 1.0f) : 0.0f;
        float icSeverity = icLevel * 0.22f;
        float baseSeverity = Mth.clamp(Math.max(healthDeficit, icSeverity) + (controlInverted ? 0.35f : 0.0f), 0.0f, 1.0f);

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
        if (!anyActive) return;

        Matrix4f pose = g.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        float shakeCfg = MedicalConfig.BRAIN_VFX_SHAKE_AMPLITUDE.get().floatValue();
        float flashbackCfg = MedicalConfig.BRAIN_VFX_FLASHBACK_INTENSITY.get().floatValue();
        float colorCfg = MedicalConfig.BRAIN_VFX_COLOR_SHIFT_INTENSITY.get().floatValue();

        // =====================================================================
        // 特效 I：【脑震荡水平撕裂色差带与复视棱镜光环（加法发光混合，绝不产生任何黑块）】
        // =====================================================================
        if (brainFreezeShakeTimer > 0.005f) {
            float progress = 1.0f - Mth.clamp(brainFreezeShakeTimer / Math.max(0.1f, brainFreezeShakeDuration), 0.0f, 1.0f);
            float decayEnv = (float) Math.pow(1.0f - progress, 1.45) * brainFreezePeakIntensity * shakeCfg;

            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);

            // 1. 脑震荡红/青双色分离脉冲环
            float ringR = Mth.lerp(progress, 0.36f, 0.88f);
            float dipAlpha = Mth.clamp(decayEnv * 0.52f, 0.0f, 0.60f);
            PainVisualOverlay.drawEllipticalRing(pose, w, h,
                    Math.max(0.24f, ringR - 0.08f), 0.0f,
                    ringR + 0.14f, dipAlpha,
                    255, 45, 95,
                    0.25f, 0.0f, 0.15f, 0.0f);
            PainVisualOverlay.drawEllipticalRing(pose, w, h,
                    Math.max(0.24f, ringR - 0.04f), 0.0f,
                    ringR + 0.18f, dipAlpha * 0.90f,
                    30, 235, 255,
                    0.0f, 0.25f, 0.0f, 0.15f);

            // 2. 12 道脑干受损横向神经撕裂光栅带 (Horizontal Glitch Shear Bands)
            Tesselator tess = Tesselator.getInstance();
            BufferBuilder buf = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            boolean hasBands = false;
            long glitchBucket = nowMs / 45L;
            for (int b = 0; b < 12; b++) {
                long bHash = (glitchBucket * 6364136223846793005L) ^ (b * 1442695040888963407L) ^ brainEpisodeSeed;
                if (((bHash >>> 12) & 3L) != 0L) continue;

                float bandY = (((bHash >>> 16) & 0xFFFF) / 65535.0f) * h;
                float bandH = 2.0f + (((bHash >>> 32) & 0x7) * 1.2f);
                float bandW = (0.35f + (((bHash >>> 40) & 0xFF) / 255.0f) * 0.55f) * w;
                float bandX = (((bHash >>> 48) & 0xFF) / 255.0f) * (w - bandW);
                int a = Mth.clamp((int) (decayEnv * 110.0f), 0, 140);
                if (a <= 4) continue;

                boolean cyan = (b % 2 == 0);
                int cr = cyan ? 0 : 255;
                int cg = cyan ? 235 : 40;
                int cb = cyan ? 255 : 110;
                buf.addVertex(pose, bandX, bandY + bandH, 0.0f).setColor(cr, cg, cb, a);
                buf.addVertex(pose, bandX + bandW, bandY + bandH, 0.0f).setColor(cr, cg, cb, a);
                buf.addVertex(pose, bandX + bandW, bandY, 0.0f).setColor(cr, cg, cb, a);
                buf.addVertex(pose, bandX, bandY, 0.0f).setColor(cr, cg, cb, a);
                hasBands = true;
            }
            MeshData mesh = buf.build();
            if (mesh != null && hasBands) {
                BufferUploader.drawWithShader(mesh);
            }
            RenderSystem.defaultBlendFunc();
        }

        // =====================================================================
        // 特效 II：【突触记忆闪回波环 (Temporal Memory Flashback & Synaptic Echo Rings)】
        // =====================================================================
        if (brainFlashbackTimer > 0.005f && flashbackCfg > 0.01f) {
            float fp = 1.0f - Mth.clamp(brainFlashbackTimer / Math.max(0.1f, brainFlashbackDuration), 0.0f, 1.0f);
            float echoEnvelope = Mth.sin(fp * Mth.PI) * brainFlashbackPeakIntensity * flashbackCfg;

            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);

            // 双层向内收敛的时间回声光环
            float rEcho1 = Mth.lerp(fp, 0.92f, 0.34f);
            float rEcho2 = Mth.lerp((fp + 0.35f) % 1.0f, 0.96f, 0.38f);
            PainVisualOverlay.drawEllipticalRing(pose, w, h,
                    Math.max(0.26f, rEcho1 - 0.09f), 0.0f,
                    rEcho1 + 0.11f, Mth.clamp(echoEnvelope * 0.42f, 0.0f, 0.50f),
                    0, 225, 255,
                    0.12f, 0.12f, 0.08f, 0.08f);
            PainVisualOverlay.drawEllipticalRing(pose, w, h,
                    Math.max(0.28f, rEcho2 - 0.08f), 0.0f,
                    rEcho2 + 0.10f, Mth.clamp(echoEnvelope * 0.36f, 0.0f, 0.45f),
                    245, 65, 200,
                    0.08f, 0.08f, 0.12f, 0.12f);

            // 闪回初段视神经突触快门闪光
            if (fp < 0.18f) {
                float shutter = (1.0f - fp / 0.18f) * brainFlashbackPeakIntensity * flashbackCfg;
                PainVisualOverlay.drawEllipticalRing(pose, w, h,
                        0.32f, 0.0f,
                        1.38f, shutter * 0.58f,
                        210, 245, 255,
                        0.0f, 0.0f, 0.0f, 0.0f);
            }
            RenderSystem.defaultBlendFunc();
        }

        // =====================================================================
        // 特效 III：【皮层神经变色径向滤镜与神经扫描噪波线 (Cortical Color Shift & Scanlines)】
        // =====================================================================
        float effectiveColorShift = smoothBrainColorShift * colorCfg;
        if (effectiveColorShift > 0.015f) {
            float wave = 0.5f + 0.5f * Mth.sin(nowMs * 0.0045f);

            int innerRCol, innerGCol, innerBCol, outerRCol, outerGCol, outerBCol;
            switch (brainColorShiftMode) {
                case 1 -> {
                    innerRCol = 255; innerGCol = 140; innerBCol = 20;
                    outerRCol = 165; outerGCol = 12;  outerBCol = 28;
                }
                case 2 -> {
                    innerRCol = 110; innerGCol = 175; innerBCol = 235;
                    outerRCol = 24;  outerGCol = 42;  outerBCol = 78;
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

            float innerTintAlpha = Mth.clamp(effectiveColorShift * 0.24f * (0.75f + 0.25f * wave), 0.0f, 0.30f);
            float outerTintAlpha = Mth.clamp(effectiveColorShift * 0.56f * (0.80f + 0.20f * wave), 0.0f, 0.68f);

            // 中圈加色神经色偏光晕（中心 r < 0.42f 保持完全透明）
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            PainVisualOverlay.drawEllipticalRing(pose, w, h,
                    0.42f, 0.0f,
                    0.78f, innerTintAlpha,
                    innerRCol, innerGCol, innerBCol,
                    0.12f, 0.12f, 0.12f, 0.12f);
            RenderSystem.defaultBlendFunc();

            // 外圈皮层色偏暗角（无缝覆盖屏幕四角）
            PainVisualOverlay.drawEllipticalRing(pose, w, h,
                    0.56f, 0.0f,
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
                lineBuf.addVertex(pose, sx, sy + 1.5f, 0.0f).setColor(sr, sg, sb, sAlpha);
                lineBuf.addVertex(pose, sx + sw, sy + 1.5f, 0.0f).setColor(sr, sg, sb, sAlpha);
                lineBuf.addVertex(pose, sx + sw, sy, 0.0f).setColor(sr, sg, sb, sAlpha);
                lineBuf.addVertex(pose, sx, sy, 0.0f).setColor(sr, sg, sb, sAlpha);
            }
            MeshData lineMesh = lineBuf.build();
            if (lineMesh != null) BufferUploader.drawWithShader(lineMesh);
        }

        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
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
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        float innerR = 0.78f - 0.12f * smoothAdrenalineIntensity - 0.03f * pulse;
        float goldAlpha = Mth.clamp(smoothAdrenalineIntensity * (0.28f + 0.24f * pulse + 0.25f * surgeFactor), 0.0f, 0.65f);
        PainVisualOverlay.drawEllipticalRing(pose, w, h,
                innerR, 0.0f,
                1.36f, goldAlpha,
                255, 195, 25,
                0.08f, 0.08f, 0.04f, 0.04f);

        if (surgeFactor > 0.05f) {
            float waveR = Mth.lerp(1.0f - surgeFactor, 0.32f, 0.96f);
            float waveA = Mth.sin(surgeFactor * Mth.PI) * 0.52f;
            PainVisualOverlay.drawEllipticalRing(pose, w, h,
                    Math.max(0.15f, waveR - 0.08f), 0.0f,
                    waveR + 0.08f, waveA,
                    195, 245, 255,
                    0.0f, 0.0f, 0.0f, 0.0f);
        }

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

        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }
}