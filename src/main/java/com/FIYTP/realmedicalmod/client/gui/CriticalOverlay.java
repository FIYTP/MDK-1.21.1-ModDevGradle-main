package com.FIYTP.realmedicalmod.client.gui;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.capability.MedicalDataCapability;
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

        // ===== 优先级 3：背景生理障碍层（心动过速、呼吸异常、血胸、颅内出血无缝径向暗角） =====
        renderSubCriticalRadialLayers(g, mc.font, w, h, data, nowMs);

        // ===== 优先级 4：失血至 <= 3000mL 的濒危状态 + 20 秒生命倒计时 =====
        if (data.isInCriticalState() && data.getCriticalTimer() > 0) {
            renderCriticalHypovolemicCountdown3A(g, mc.font, w, h, data, nowMs);
        }
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

        String title = Component.translatable("medical.warning.cardiac_arrest").getString() + " [0 BPM]";
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
        g.drawString(font, "0.0mV 平线", scopeX + 4, scopeY + 2, 0xAAFF5252, false);

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

        // 底部生命体征状态行
        String telemetry = String.format("SpO2: %d%%  |  脑健康: %d%%  |  MAP: %.0f mmHg",
                data.getBloodOxygen(), data.getBrainHealth(), data.getMeanArterialPressure());
        g.drawString(font, telemetry, scopeX + 2, cardY + 48, 0xFFFF8A80, false);

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

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    /**
     * 绘制心动过速 / 室颤临界过载微浮标 (Tachycardia Overload Badge)
     */
    private static void drawTachycardiaOverloadBadge(GuiGraphics g, Font font, int screenW, int hr, int vfibTimer, float pulse) {
        int bw = 166;
        int bh = (vfibTimer > 0) ? 30 : 20;
        int bx = screenW - bw - 12;
        int by = 12;

        int accent = (vfibTimer > 0) ? C_ALERT_RED : C_WARN_AMBER;
        HudRenderHelper.drawSciFiCard(g, bx, by, bw, bh, 3, 0xCC080408, accent);
        g.fill(bx, by + 2, bx + 3, by + bh - 2, accent);

        String title = (vfibTimer > 0)
                ? "⚠ 室颤临界: " + hr + " BPM"
                : "心动过速: " + hr + " BPM";
        g.drawString(font, title, bx + 7, by + 4, accent, false);

        if (vfibTimer > 0) {
            int maxVfibSec = Math.max(1, MedicalConfig.VFIB_DURATION.get());
            float pct = Mth.clamp((float) vfibTimer / (float) maxVfibSec, 0.0f, 1.0f);
            HudRenderHelper.drawSciFiProgressBar(g, bx + 7, by + 16, bw - 14, 5, pct, C_ALERT_RED, 0xFF1A0508);
            String rem = Math.max(0, maxVfibSec - vfibTimer) + "s";
            g.drawString(font, rem, bx + bw - font.width(rem) - 6, by + 14, 0xFFFFFFFF, false);
        }
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

        // 中央右侧：失血量与平均动脉压 MAP 临床遥测
        int infoX = boxX + 92;
        String bloodInfo = String.format("血量: %d / %d mL", data.getBloodVolume(), MedicalConfig.CRITICAL_BLOOD_VOLUME.get());
        String mapInfo = String.format("BP: %d/%d (MAP %.0f)",
                data.getSystolicBP(), data.getDiastolicBP(), data.getMeanArterialPressure());
        g.drawString(font, bloodInfo, infoX, boxY + 19, 0xFFFF5252, false);
        g.drawString(font, mapInfo, infoX, boxY + 30, 0xFFFFCC80, false);

        // 底部：20 秒濒危倒计时双向能量衰减进度条
        float remainPct = Mth.clamp((timerSec - (1.0f - subTenths / 10.0f) * 0.5f) / maxCritSec, 0.0f, 1.0f);
        HudRenderHelper.drawSciFiProgressBar(g, boxX + 10, boxY + 46, boxW - 20, 6, remainPct, segColor, 0xFF1A060A);

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }
}