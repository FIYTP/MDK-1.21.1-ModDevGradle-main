package com.FIYTP.realmedicalmod.client;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.capability.MedicalDataCapability;
import com.FIYTP.realmedicalmod.client.render.WristbandArmRenderLayer;
import com.FIYTP.realmedicalmod.config.MedicalConfig;
import com.FIYTP.realmedicalmod.treatment.ModItems;
import com.FIYTP.realmedicalmod.treatment.TreatmentClientHooks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * 阶段 1（精确向量标定版）：第一人称 3D 战术医疗手表动画控制器
 *
 * 严格基于 TreatmentClientHooks 已验证的 renderLeftHand 坐标基准：
 * - 在 renderLeftHand 空间中，左臂中心位于 X = +0.375f (细臂 +0.34375f)，手腕佩戴点位于 Y = +0.46875f，Z = 0。
 * - 先以手腕佩戴点为唯一旋转原点 (0, 0, 0) 定位到第一人称视野黄金区域 (-0.14f, -0.13f, -0.45f)，
 *   执行横臂观表旋转 RZ(-68°) + RX(-12°) + RY(+10°) —— 手表表盘法线 (+Z) 矢量点积 > 0.95 笔直朝向玩家双眼！
 * - 手臂与手表共享同一个手腕原点 (0, 0, 0)：手表直接画在原点，手臂平移 (-centerX, -0.46875f, 0) 绘制，
 *   实现 0.000mm 误差的严丝合缝佩戴！
 * - 移除难听的 H 键合成音效；使用 EventPriority.HIGHEST 在 TaCZ 渲染前注入持枪微抖脉冲确保 100% 可见。
 */
@EventBusSubscriber(modid = "realmedicalmod", value = Dist.CLIENT)
public final class WristbandHologramController {

    public enum AnimMode {
        IDLE,
        HOLD_INSPECT,     // 长按 H 主动完整抬腕看表
        GLANCE_BROADCAST  // 播报触发的自然微抬瞥一眼（最高优先级随时可打断）
    }

    private static AnimMode mode = AnimMode.IDLE;
    private static float raiseAmount = 0.0f;
    private static float raiseVelocity = 0.0f;
    private static float glanceRemainSec = 0.0f;
    private static float interruptCooldownSec = 0.0f;

    // COD 式检视微动作状态
    private static float inspectElapsedSec = 0.0f;
    private static float settleImpulse = 0.0f;
    private static float settleVelocity = 0.0f;
    private static boolean settledPlayed = false;

    // 持枪适配与思路 C 腕表马达两下短促微震 (Buzz-Buzz)
    private static float gunTactileTremorSec = 0.0f;
    private static float gunInspectBlend = 0.0f;
    private static float hapticBuzzElapsedSec = 99.0f;
    private static float hapticBuzzStrength = 0.0f;

    // 落地沉重缓冲
    private static boolean wasOnGround = true;
    private static float landingDip = 0.0f;
    private static float landingDipVel = 0.0f;

    private static long lastFrameNs = 0L;
    private static int lastHotbarSlot = -1;
    private static int lastObservedBleedTotal = 0;
    private static float lastObservedHealth = -1.0f;
    private static boolean wasHoldKeyDown = false;

    // 厚重惯性滞后与呼吸/步频
    private static float lastCamYaw = 0.0f;
    private static float lastCamPitch = 0.0f;
    private static boolean camInit = false;
    private static float swayYaw = 0.0f;
    private static float swayYawVel = 0.0f;
    private static float swayPitch = 0.0f;
    private static float swayPitchVel = 0.0f;
    private static float walkBobPhase = 0.0f;
    private static float smoothMoveSpeed = 0.0f;
    private static float breathPhase = 0.0f;

    private WristbandHologramController() {}

    public static float getRaiseAmount() {
        return Mth.clamp(raiseAmount, 0.0f, 1.0f);
    }

    private static boolean leftHandWatchRenderedByMixin = false;

    public static void markLeftHandWatchRendered() {
        leftHandWatchRenderedByMixin = true;
    }

    public static float getVisualActiveLevel() {
        return Mth.clamp(Math.max(raiseAmount, gunInspectBlend), 0.0f, 1.0f);
    }

    public static AnimMode getMode() {
        return mode;
    }

    public static boolean isInspecting() {
        return mode == AnimMode.HOLD_INSPECT && raiseAmount > 0.25f;
    }

    /**
     * 思路 C：触发腕表马达两下短促触觉微震 (Buzz-Buzz)，绝不自动抢占左手！
     * 提示玩家有新伤情/趋势播报驻留在表盘未读队列中，随时长按 H 抬腕即可查看。
     */
    public static void triggerHapticBuzz(float strength) {
        hapticBuzzElapsedSec = 0.0f;
        hapticBuzzStrength = Mth.clamp(strength, 0.25f, 1.0f);
        gunTactileTremorSec = Math.max(gunTactileTremorSec, 0.36f);
    }

    public static void triggerBroadcastGlance(float holdDurationSec) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.screen != null || mc.options.hideGui || player.isSpectator()) return;
        if (mc.options.getCameraType() != CameraType.FIRST_PERSON) return;
        if (!ModItems.safeCfgBool(MedicalConfig.WRISTBAND_ENABLED, true)) return;
        if (TreatmentClientHooks.isFirstPersonMedicalAnimActive() || TreatmentClientHooks.isLockingCameraView()) return;

        MedicalData data = player.getData(MedicalDataCapability.MEDICAL_DATA);
        if (data != null && (data.getConsciousness() <= 0 || data.isCardiacArrest())) return;

        if (!player.getOffhandItem().isEmpty()) {
            return;
        }
        if (isHoldingGunOrTwoHanded(player)) {
            gunTactileTremorSec = Math.max(gunTactileTremorSec, 0.55f);
            return;
        }

        if (interruptCooldownSec > 0.0f || mode == AnimMode.HOLD_INSPECT) {
            return;
        }
        mode = AnimMode.GLANCE_BROADCAST;
        glanceRemainSec = Math.max(glanceRemainSec, Mth.clamp(holdDurationSec, 0.8f, 5.0f));
        inspectElapsedSec = 0.0f;
        settledPlayed = false;
    }

    public static void interruptGlance() {
        if (mode == AnimMode.GLANCE_BROADCAST) {
            mode = AnimMode.IDLE;
            glanceRemainSec = 0.0f;
            interruptCooldownSec = 0.45f;
        }
    }

    public static boolean isHoldingGunOrTwoHanded(Player player) {
        if (player == null) return false;
        ItemStack main = player.getMainHandItem();
        if (main.isEmpty()) return false;
        if (main.getItem() instanceof BowItem || main.getItem() instanceof CrossbowItem) {
            return true;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(main.getItem());
        if (id != null) {
            String ns = id.getNamespace();
            String path = id.getPath();
            if ("tacz".equals(ns) || path.contains("gun") || path.contains("rifle")
                    || path.contains("pistol") || path.contains("shotgun") || path.contains("sniper")) {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    public static void onAddPlayerLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skinModel : event.getSkins()) {
            LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> renderer = event.getSkin(skinModel);
            if (renderer instanceof PlayerRenderer playerRenderer) {
                playerRenderer.addLayer(new WristbandArmRenderLayer(playerRenderer));
            }
        }
    }

    @SubscribeEvent
    public static void onInteractionInterrupt(InputEvent.InteractionKeyMappingTriggered event) {
        interruptGlance();
    }

    @SubscribeEvent
    public static void onMouseScrollInterrupt(InputEvent.MouseScrollingEvent event) {
        interruptGlance();
    }

    @SubscribeEvent
    public static void onClientTickPre(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null) {
            mode = AnimMode.IDLE;
            raiseAmount = 0.0f;
            raiseVelocity = 0.0f;
            glanceRemainSec = 0.0f;
            gunTactileTremorSec = 0.0f;
            gunInspectBlend = 0.0f;
            wasHoldKeyDown = false;
            camInit = false;
            lastHotbarSlot = -1;
            lastObservedHealth = -1.0f;
            return;
        }

        if (!ModItems.safeCfgBool(MedicalConfig.WRISTBAND_ENABLED, true)) {
            mode = AnimMode.IDLE;
            return;
        }

        int curSlot = player.getInventory().selected;
        if (lastHotbarSlot != -1 && curSlot != lastHotbarSlot) {
            interruptGlance();
        }
        lastHotbarSlot = curSlot;

        MedicalData data = player.getData(MedicalDataCapability.MEDICAL_DATA);
        boolean unconscious = data != null && (data.getConsciousness() <= 0 || data.isCardiacArrest());

        if (mc.screen != null || player.isSpectator() || mc.options.hideGui || unconscious
                || TreatmentClientHooks.isFirstPersonMedicalAnimActive()
                || TreatmentClientHooks.isLockingCameraView()) {
            mode = AnimMode.IDLE;
            glanceRemainSec = 0.0f;
            wasHoldKeyDown = false;
            return;
        }

        if (player.isSprinting() || player.isUsingItem() || player.swinging) {
            interruptGlance();
        }

        boolean onGround = player.onGround();
        if (onGround && !wasOnGround) {
            float fallVel = (float) Math.min(1.2, Math.abs(player.getDeltaMovement().y) + 0.35);
            landingDipVel -= fallVel * 1.65f;
        }
        wasOnGround = onGround;

        // 阶段 3 起由 WristBroadcastManager 统一监测双向生理趋势拐点并按需触发可打断微抬瞥一眼

        // 长按 H 键检测（已彻底移除难听的合成音效）
        boolean holdKeyDown = KeyHandler.OPEN_MEDICAL_KEY.isDown();
        boolean holdingGun = isHoldingGunOrTwoHanded(player);
        if (holdKeyDown) {
            if (!wasHoldKeyDown) {
                inspectElapsedSec = 0.0f;
                settledPlayed = false;
                if (holdingGun) {
                    gunTactileTremorSec = 0.45f;
                }
            }
            // 持枪时绝不额外画出第三只手！手表已通过 PlayerRendererMixin 直接佩戴在 TaCZ 第一人称左手上，
            // 持枪按 H 仅触发克制的枪身/左手微倾与战术微抖；非持枪时才接管左手抬腕检视。
            if (!holdingGun) {
                mode = AnimMode.HOLD_INSPECT;
                glanceRemainSec = 0.0f;
            }
        } else if (wasHoldKeyDown && mode == AnimMode.HOLD_INSPECT) {
            mode = AnimMode.IDLE;
            raiseVelocity -= 0.45f;
            WristBroadcastManager.onPlayerLoweredWatch();
        }
        wasHoldKeyDown = holdKeyDown;
    }

    @SubscribeEvent
    public static void onComputeCameraGunGlance(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.options.getCameraType() != CameraType.FIRST_PERSON) return;
        boolean buzzing = hapticBuzzElapsedSec < 0.34f;
        if (gunInspectBlend <= 0.002f && gunTactileTremorSec <= 0.001f && !buzzing) return;

        float rollAdd = 0.0f;
        float pitchAdd = 0.0f;
        if (gunInspectBlend > 0.002f) {
            pitchAdd += gunInspectBlend * 1.8f;
            rollAdd -= gunInspectBlend * 1.6f;
        }
        // 思路 C：极克制的腕表马达两下短促微震 (0.00~0.11s 轻震 -> 0.11~0.19s 停顿 -> 0.19~0.30s 轻震)
        if (buzzing) {
            boolean inPulse = (hapticBuzzElapsedSec < 0.11f) || (hapticBuzzElapsedSec >= 0.19f && hapticBuzzElapsedSec < 0.30f);
            if (inPulse) {
                float t = System.currentTimeMillis() * 0.085f;
                rollAdd += Mth.sin(t) * 0.38f * hapticBuzzStrength;
                pitchAdd += Mth.cos(t * 1.25f) * 0.22f * hapticBuzzStrength;
            }
        } else if (gunTactileTremorSec > 0.001f) {
            float env = Mth.clamp(gunTactileTremorSec / 0.65f, 0.0f, 1.0f);
            float t = System.currentTimeMillis() * 0.044f;
            rollAdd += Mth.sin(t) * 0.65f * env;
            pitchAdd += Mth.cos(t * 1.3f) * 0.35f * env;
        }
        event.setPitch(event.getPitch() + pitchAdd);
        event.setRoll(event.getRoll() + rollAdd);
    }

    private static void stepPhysics(Player player, MedicalData data) {
        long nowNs = System.nanoTime();
        if (lastFrameNs == 0L) lastFrameNs = nowNs;
        float dt = Mth.clamp((nowNs - lastFrameNs) / 1_000_000_000.0f, 0.0005f, 0.05f);
        lastFrameNs = nowNs;

        if (interruptCooldownSec > 0.0f) {
            interruptCooldownSec = Math.max(0.0f, interruptCooldownSec - dt);
        }
        if (gunTactileTremorSec > 0.0f) {
            gunTactileTremorSec = Math.max(0.0f, gunTactileTremorSec - dt);
        }
        if (hapticBuzzElapsedSec < 10.0f) {
            hapticBuzzElapsedSec += dt;
        }

        boolean holdingGun = isHoldingGunOrTwoHanded(player);
        float targetGunBlend = (holdingGun && wasHoldKeyDown) ? 1.0f : 0.0f;
        gunInspectBlend = Mth.lerp(Math.min(1.0f, dt * 10.0f), gunInspectBlend, targetGunBlend);

        if (mode == AnimMode.GLANCE_BROADCAST) {
            glanceRemainSec -= dt;
            if (glanceRemainSec <= 0.0f) {
                mode = AnimMode.IDLE;
                glanceRemainSec = 0.0f;
            }
        }

        float target = holdingGun ? 0.0f : switch (mode) {
            case HOLD_INSPECT -> 1.0f;
            case GLANCE_BROADCAST -> 0.76f;
            case IDLE -> 0.0f;
        };

        float raiseMs = (float) ModItems.safeCfgDouble(MedicalConfig.WRISTBAND_RAISE_DURATION_MS, 420.0);
        float lowerMs = (float) ModItems.safeCfgDouble(MedicalConfig.WRISTBAND_LOWER_DURATION_MS, 270.0);
        boolean goingUp = target > raiseAmount;
        float omega = goingUp ? (4.1f / Math.max(0.16f, raiseMs / 1000.0f)) : (5.4f / Math.max(0.10f, lowerMs / 1000.0f));
        float zeta = goingUp ? 0.70f : 0.92f;

        float accel = omega * omega * (target - raiseAmount) - 2.0f * zeta * omega * raiseVelocity;
        raiseVelocity += accel * dt;
        raiseAmount += raiseVelocity * dt;
        if (target == 0.0f && raiseAmount < 0.003f && Math.abs(raiseVelocity) < 0.02f) {
            raiseAmount = 0.0f;
            raiseVelocity = 0.0f;
            inspectElapsedSec = 0.0f;
            settledPlayed = false;
        }
        raiseAmount = Mth.clamp(raiseAmount, 0.0f, 1.12f);

        if (mode != AnimMode.IDLE && raiseAmount > 0.62f) {
            inspectElapsedSec += dt;
            if (!settledPlayed && raiseAmount > (target * 0.90f)) {
                settledPlayed = true;
                settleVelocity -= 1.35f;
            }
        }

        float sAcc = -140.0f * settleImpulse - 14.0f * settleVelocity;
        settleVelocity += sAcc * dt;
        settleImpulse = Mth.clamp(settleImpulse + settleVelocity * dt, -0.05f, 0.05f);

        float lAcc = -95.0f * landingDip - 11.5f * landingDipVel;
        landingDipVel += lAcc * dt;
        landingDip = Mth.clamp(landingDip + landingDipVel * dt, -0.14f, 0.05f);

        float curYaw = player.getYRot();
        float curPitch = player.getXRot();
        if (!camInit) {
            lastCamYaw = curYaw;
            lastCamPitch = curPitch;
            camInit = true;
        }
        float dYaw = Mth.clamp(Mth.wrapDegrees(curYaw - lastCamYaw), -25.0f, 25.0f);
        float dPitch = Mth.clamp(curPitch - lastCamPitch, -25.0f, 25.0f);
        lastCamYaw = curYaw;
        lastCamPitch = curPitch;

        float swayScale = (float) ModItems.safeCfgDouble(MedicalConfig.WRISTBAND_ARM_SWAY_INTENSITY, 1.0);
        swayYawVel += (-95.0f * swayYaw - 12.5f * swayYawVel + dYaw * 14.0f * swayScale) * dt;
        swayYaw = Mth.clamp(swayYaw + swayYawVel * dt, -10.0f, 10.0f);
        swayPitchVel += (-95.0f * swayPitch - 12.5f * swayPitchVel + dPitch * 14.0f * swayScale) * dt;
        swayPitch = Mth.clamp(swayPitch + swayPitchVel * dt, -8.0f, 8.0f);

        double vx = player.getDeltaMovement().x;
        double vz = player.getDeltaMovement().z;
        float horizSpeed = (float) Math.sqrt(vx * vx + vz * vz);
        smoothMoveSpeed = Mth.lerp(Math.min(1.0f, dt * 9.0f), smoothMoveSpeed, Math.min(0.45f, horizSpeed));
        walkBobPhase += dt * (3.8f + smoothMoveSpeed * 30.0f);

        float respRpm = (data != null) ? Mth.clamp(data.getRespirationRate(), 6, 45) : 16.0f;
        breathPhase = (breathPhase + dt * (respRpm / 60.0f) * ((float) Math.PI * 2.0f)) % ((float) Math.PI * 2.0f);
    }

    /**
     * 使用 EventPriority.HIGHEST 确保在 TaCZ 等枪械模组渲染第一人称手部之前注入持枪微抖矩阵，
     * 使得持枪时的左手/枪身战术微抖与侧倾 100% 肉眼可见！
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderFirstPersonWatchHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        AbstractClientPlayer player = mc.player;
        if (player == null || mc.options.hideGui || player.isSpectator()) return;
        if (mc.options.getCameraType() != CameraType.FIRST_PERSON) return;
        if (!ModItems.safeCfgBool(MedicalConfig.WRISTBAND_ENABLED, true)) return;
        if (TreatmentClientHooks.isFirstPersonMedicalAnimActive() || TreatmentClientHooks.isLockingCameraView()) return;

        MedicalData data = player.getData(MedicalDataCapability.MEDICAL_DATA);
        if (event.getHand() == InteractionHand.MAIN_HAND) {
            stepPhysics(player, data);
            if (isHoldingGunOrTwoHanded(player) && (gunTactileTremorSec > 0.001f || gunInspectBlend > 0.01f)) {
                PoseStack ps = event.getPoseStack();
                float env = Mth.clamp(gunTactileTremorSec / 0.65f, 0.0f, 1.0f);
                float ms = System.currentTimeMillis();
                float microX = Mth.sin(ms * 0.052f) * 0.014f * env - gunInspectBlend * 0.032f;
                float microY = Mth.cos(ms * 0.068f) * 0.010f * env - gunInspectBlend * 0.022f;
                float microRoll = Mth.sin(ms * 0.045f) * 2.8f * env + gunInspectBlend * 11.0f;
                ps.translate(microX, microY, 0.0f);
                ps.mulPose(Axis.ZP.rotationDegrees(microRoll));
                ps.mulPose(Axis.YP.rotationDegrees(gunInspectBlend * 6.5f));
            }
            return;
        }

        if (raiseAmount <= 0.002f) {
            return;
        }

        event.setCanceled(true);

        float t = raiseAmount;
        float swayScale = (float) ModItems.safeCfgDouble(MedicalConfig.WRISTBAND_ARM_SWAY_INTENSITY, 1.0);
        float moveBobX = Mth.cos(walkBobPhase) * smoothMoveSpeed * 0.045f * swayScale;
        float moveBobY = Math.abs(Mth.sin(walkBobPhase)) * smoothMoveSpeed * 0.032f * swayScale;
        float breathY = Mth.sin(breathPhase) * 0.0040f * swayScale;
        float breathRoll = Mth.cos(breathPhase) * 0.90f * swayScale;
        float postureY = (player.isCrouching() ? -0.020f : 0.0f) + (player.isSwimming() ? -0.026f : 0.0f);

        // COD 检视微动作：到位轻抖袖口唤醒 + 缓慢左右旋腕 ±6.5° 找金属与蓝宝石反光
        float flourishRoll = 0.0f;
        float flourishPitch = 0.0f;
        float flourishY = 0.0f;
        if (inspectElapsedSec > 0.0f && mode == AnimMode.HOLD_INSPECT) {
            float flickEnv = (float) Math.exp(-inspectElapsedSec * 6.5f);
            float flick = Mth.sin(inspectElapsedSec * 22.0f) * flickEnv;
            flourishY += flick * 0.010f;
            flourishRoll += flick * 4.5f;

            float slowFade = Mth.clamp((inspectElapsedSec - 0.25f) / 0.65f, 0.0f, 1.0f);
            flourishRoll += Mth.sin(inspectElapsedSec * 1.45f) * 6.5f * slowFade;
            flourishPitch += Mth.cos(inspectElapsedSec * 1.05f) * 2.5f * slowFade;
        }

        float jitterX = 0.0f;
        float jitterY = 0.0f;
        if (data != null) {
            float maxPain = Math.max(10.0f, (float) ModItems.safeCfgDouble(MedicalConfig.MAX_PERCEIVED_PAIN, 55.0));
            float pRatio = Mth.clamp(data.getPerceivedPain() / maxPain, 0.0f, 1.0f);
            float amp = pRatio * 0.0038f * (float) ModItems.safeCfgDouble(MedicalConfig.WRISTBAND_PAIN_TREMOR_INTENSITY, 1.0);
            if (ModItems.isInWithdrawalMovementRestriction(data)) amp += 0.0035f;
            long ms = System.currentTimeMillis();
            jitterX = (Mth.sin(ms * 0.017f) * 0.6f + Mth.cos(ms * 0.029f) * 0.4f) * amp;
            jitterY = (Mth.cos(ms * 0.021f) * 0.6f + Mth.sin(ms * 0.026f) * 0.4f) * amp;
        }

        // 以“左手腕手表中心 (0, 0, 0)”为锚点，从屏幕左下方自然弧线抬升至黄金观表中央偏左下！
        float arcX = -Mth.sin(Mth.clamp(t, 0.0f, 1.0f) * Mth.PI) * 0.06f;
        // 舒适观表距离（不再贴脸）：推远至 -0.64f，略微下移偏左让整只手腕与 3D 手表构图自然舒展
        float gunShiftX = isHoldingGunOrTwoHanded(player) ? -0.05f : 0.0f;
        float gunShiftY = isHoldingGunOrTwoHanded(player) ? -0.04f : 0.0f;
        float gunMicro = (gunTactileTremorSec > 0.001f)
                ? Mth.sin(System.currentTimeMillis() * 0.055f) * 0.007f * Mth.clamp(gunTactileTremorSec / 0.55f, 0.0f, 1.0f)
                : 0.0f;
        float wristX = Mth.lerp(t, -0.64f, -0.20f) + gunShiftX + arcX - swayYaw * 0.0042f + moveBobX + jitterX + gunMicro;
        float wristY = Mth.lerp(t, -0.72f, -0.19f) + gunShiftY + swayPitch * 0.0040f + moveBobY + breathY + postureY
                + settleImpulse + landingDip + flourishY + jitterY + gunMicro * 0.7f;
        float wristZ = Mth.lerp(t, -0.72f, -0.64f);

        // 精确对齐 PlayerRendererMixin 挂在 leftArm 外侧 (+X) 的表盘法线：
        // - 先 ZP(-68°) 横臂于胸前，再绕手臂自身轴线 YP(-82°) 将外侧 (+X) 表盘正对玩家双眼 (+Z，点积 0.99！)
        float rotZ = Mth.lerp(t, -22.0f, -68.0f) + swayYaw * 0.35f + breathRoll;
        float rotX = Mth.lerp(t, -26.0f, -10.0f) - swayPitch * 0.65f + landingDip * 40.0f + flourishPitch;
        float rotY = Mth.lerp(t, -25.0f, -82.0f) + swayYaw * 0.72f + flourishRoll;

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource bufferSource = event.getMultiBufferSource();
        int packedLight = event.getPackedLight();
        PlayerRenderer playerRenderer = (PlayerRenderer) mc.getEntityRenderDispatcher().getRenderer(player);
        boolean isSlim = player.getSkin().model() == PlayerSkin.Model.SLIM;

        poseStack.pushPose();
        poseStack.translate(wristX, wristY, wristZ);
        poseStack.mulPose(Axis.XP.rotationDegrees(rotX));
        poseStack.mulPose(Axis.ZP.rotationDegrees(rotZ));
        poseStack.mulPose(Axis.YP.rotationDegrees(rotY));

        // 双保险渲染：先渲染玩家真实皮肤左前臂；若 PlayerRendererMixin 未注册/未触发，立即直接兜底渲染 3D 手表（绝不缺失、绝不重画）
        float armCenterOffsetX = isSlim ? -0.34375f : -0.375f;
        poseStack.translate(armCenterOffsetX, -0.46875f, 0.0f);
        leftHandWatchRenderedByMixin = false;
        playerRenderer.renderLeftHand(poseStack, bufferSource, packedLight, player);
        if (!leftHandWatchRenderedByMixin) {
            WristbandArmRenderLayer.applyExactLeftWristTransformAndRender(
                    poseStack,
                    bufferSource,
                    packedLight,
                    playerRenderer.getModel(),
                    player,
                    data,
                    Mth.clamp(t, 0.0f, 1.0f)
            );
        }

        poseStack.popPose();
    }
}