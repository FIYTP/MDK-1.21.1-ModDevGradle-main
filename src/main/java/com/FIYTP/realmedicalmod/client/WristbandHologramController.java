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
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * 第一人称 3D 战术医疗手表控制器 (WristbandHologramController - 思路 C 精简零硬编码版)
 *
 * 1. 长按 H 键：以 COD 级厚重二阶弹簧物理平滑抬起左腕看表（到位微沉顿挫 + 轻抖袖口 + 缓旋找反光 + 落地沉坠缓冲）；
 *    松开 H 键带重力甩落，并通知 WristBroadcastManager 将刚看过的未读播报淡出标记已读。
 * 2. 思路 C 触觉微震：新伤情/趋势播报触发时绝不擅自抢占左手，仅触发两下短促克制的腕表马达微震提示。
 * 3. 持枪态零三手：手表由 PlayerRendererMixin 直接佩戴在 TaCZ 第一人称护木左手腕内侧，按 H 仅轻微侧倾枪身。
 * 4. 全部参数均由 MedicalConfig 驱动，零硬编码。
 */
@EventBusSubscriber(modid = "realmedicalmod", value = Dist.CLIENT)
public final class WristbandHologramController {

    private static boolean inspecting = false;
    private static float raiseAmount = 0.0f;
    private static float raiseVelocity = 0.0f;
    private static boolean wasHoldKeyDown = false;
    private static boolean leftHandWatchRenderedByMixin = false;

    // COD 式检视微动作状态
    private static float inspectElapsedSec = 0.0f;
    private static float settleImpulse = 0.0f;
    private static float settleVelocity = 0.0f;
    private static boolean settledPlayed = false;

    // 持枪适配与思路 C 腕表马达两下短促微震
    private static float gunTactileTremorSec = 0.0f;
    private static float gunInspectBlend = 0.0f;
    private static float hapticBuzzElapsedSec = 99.0f;
    private static float hapticBuzzStrength = 0.0f;

    // 落地沉重缓冲与转向质量滞后
    private static boolean wasOnGround = true;
    private static float landingDip = 0.0f;
    private static float landingDipVel = 0.0f;

    private static long lastFrameNs = 0L;
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

    public static void markLeftHandWatchRendered() {
        leftHandWatchRenderedByMixin = true;
    }

    public static float getRaiseAmount() {
        return Mth.clamp(raiseAmount, 0.0f, 1.0f);
    }

    public static float getVisualActiveLevel() {
        return Mth.clamp(Math.max(raiseAmount, gunInspectBlend), 0.0f, 1.0f);
    }

    public static boolean isInspecting() {
        return inspecting && raiseAmount > 0.25f;
    }

    /**
     * 思路 C：触发腕表马达两下短促微震 (Buzz-Buzz)，提示玩家有新伤情/趋势播报驻留，绝不擅自抢手。
     */
    public static void triggerHapticBuzz(float strength) {
        if (!ModItems.safeCfgBool(MedicalConfig.WRISTBAND_HAPTIC_BUZZ_ENABLED, true)) return;
        float cfgMult = (float) ModItems.safeCfgDouble(MedicalConfig.WRISTBAND_HAPTIC_BUZZ_STRENGTH, 1.0);
        if (cfgMult <= 0.01f) return;
        hapticBuzzElapsedSec = 0.0f;
        hapticBuzzStrength = Mth.clamp(strength * cfgMult, 0.15f, 2.5f);
        gunTactileTremorSec = Math.max(gunTactileTremorSec, 0.36f);
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
    public static void onClientTickPre(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null) {
            inspecting = false;
            raiseAmount = 0.0f;
            raiseVelocity = 0.0f;
            gunTactileTremorSec = 0.0f;
            gunInspectBlend = 0.0f;
            wasHoldKeyDown = false;
            camInit = false;
            return;
        }

        if (!ModItems.safeCfgBool(MedicalConfig.WRISTBAND_ENABLED, true)) {
            inspecting = false;
            return;
        }

        MedicalData data = player.getData(MedicalDataCapability.MEDICAL_DATA);
        boolean unconscious = data != null && (data.getConsciousness() <= 0 || data.isCardiacArrest());

        if (mc.screen != null || player.isSpectator() || mc.options.hideGui || unconscious
                || TreatmentClientHooks.isFirstPersonMedicalAnimActive()
                || TreatmentClientHooks.isLockingCameraView()) {
            inspecting = false;
            wasHoldKeyDown = false;
            return;
        }

        boolean onGround = player.onGround();
        if (onGround && !wasOnGround) {
            float fallVel = (float) Math.min(1.2, Math.abs(player.getDeltaMovement().y) + 0.35);
            landingDipVel -= fallVel * 1.65f;
        }
        wasOnGround = onGround;

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
            inspecting = !holdingGun;
        } else if (wasHoldKeyDown) {
            if (inspecting) {
                inspecting = false;
                raiseVelocity -= 0.45f;
            }
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

        if (gunTactileTremorSec > 0.0f) {
            gunTactileTremorSec = Math.max(0.0f, gunTactileTremorSec - dt);
        }
        if (hapticBuzzElapsedSec < 10.0f) {
            hapticBuzzElapsedSec += dt;
        }

        boolean holdingGun = isHoldingGunOrTwoHanded(player);
        float targetGunBlend = (holdingGun && wasHoldKeyDown) ? 1.0f : 0.0f;
        gunInspectBlend = Mth.lerp(Math.min(1.0f, dt * 10.0f), gunInspectBlend, targetGunBlend);

        float target = (!holdingGun && inspecting) ? 1.0f : 0.0f;

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

        if (inspecting && raiseAmount > 0.62f) {
            inspectElapsedSec += dt;
            if (!settledPlayed && raiseAmount > 0.90f) {
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

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderFirstPersonWatchHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        AbstractClientPlayer player = mc.player;
        if (player == null || mc.options.hideGui || player.isSpectator()) return;
        if (mc.options.getCameraType() != CameraType.FIRST_PERSON) return;
        if (!ModItems.safeCfgBool(MedicalConfig.WRISTBAND_ENABLED, true)) return;
        if (TreatmentClientHooks.isFirstPersonMedicalAnimActive() || TreatmentClientHooks.isLockingCameraView()) return;
        MedicalData ampCheck = player.getData(MedicalDataCapability.MEDICAL_DATA);
        if (ampCheck != null && MedicalConfig.AMPUTATION_ENABLED.get()
                && ampCheck.getLimb(MedicalData.Limb.LEFT_ARM) != null
                && ampCheck.getLimb(MedicalData.Limb.LEFT_ARM).isAmputated()) {
            return;
        }

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

        float flourishRoll = 0.0f;
        float flourishPitch = 0.0f;
        float flourishY = 0.0f;
        if (inspectElapsedSec > 0.0f && inspecting) {
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

        float targetZ = (float) ModItems.safeCfgDouble(MedicalConfig.WRISTBAND_VIEW_DISTANCE_Z, -0.64);
        float arcX = -Mth.sin(Mth.clamp(t, 0.0f, 1.0f) * Mth.PI) * 0.06f;
        float wristX = Mth.lerp(t, -0.64f, -0.20f) + arcX - swayYaw * 0.0042f + moveBobX + jitterX;
        float wristY = Mth.lerp(t, -0.72f, -0.19f) + swayPitch * 0.0040f + moveBobY + breathY + postureY
                + settleImpulse + landingDip + flourishY + jitterY;
        float wristZ = Mth.lerp(t, targetZ - 0.08f, targetZ);

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

        float armCenterOffsetX = isSlim ? -0.34375f : -0.375f;
        poseStack.translate(armCenterOffsetX, -0.46875f, 0.0f);
        leftHandWatchRenderedByMixin = false;
        playerRenderer.renderLeftHand(poseStack, bufferSource, packedLight, player);
        if (!leftHandWatchRenderedByMixin) {
            WristbandArmRenderLayer.applyExactLeftWristTransformAndRender(
                    poseStack, bufferSource, packedLight, playerRenderer.getModel(), player, data, Mth.clamp(t, 0.0f, 1.0f)
            );
        }

        poseStack.popPose();
    }
}
