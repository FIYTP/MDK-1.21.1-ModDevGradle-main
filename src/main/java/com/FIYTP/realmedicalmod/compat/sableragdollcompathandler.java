package com.FIYTP.realmedicalmod.compat;

import com.FIYTP.realmedicalmod.RealMedicalMod;
import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.capability.MedicalDataCapability;
import com.FIYTP.realmedicalmod.config.MedicalConfig;
import com.FIYTP.realmedicalmod.treatment.ModItems;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

import java.lang.reflect.Field;

/**
 * 第一人称受创摔倒、物理脱手与布娃娃视角桥接处理器 (SableRagdollCompatHandler)
 *
 * 设计重构说明：
 * 1. 核心受创摔倒（射击/攻击腿部摔倒并在数秒后站起、大口径/爆炸掀飞摔倒、昏迷瘫软）与手部受创静默脱手（无任何文字提示）
 *    已全部下沉并内置于 Mod 本体核心 {@link ModItems} 中，对原版伤害与 TaCZ 枪伤 100% 统一生效！
 * 2. 本类作为兼容桥接层，将所有外部调用无缝转发至 {@link ModItems} 本体方法，并在客户端严密拦截任何第三方布娃娃模组
 *    （如 Sable: Ragdolls）强制切换第三人称相机 ({@code CameraType.THIRD_PERSON_BACK} / {@code Camera.detached}) 的行为，
 *    确保玩家在布娃娃/摔倒/昏迷状态下 100% 保持沉浸式第一人称视角！
 */
final class SableRagdollCompatHandler {

    private SableRagdollCompatHandler() {
    }

    /**
     * 转发至 Mod 本体核心受创摔倒与无提示手部脱手方法
     */
    public static void onTaczBallisticHit(ServerPlayer player,
                                          MedicalData data,
                                          MedicalData.Limb hitLimb,
                                          MedicalData.LimbData ld,
                                          Entity bullet,
                                          LivingEntity attacker,
                                          boolean isHeavySniperAmr,
                                          float penetratingDmg,
                                          float bluntDmg,
                                          float totalRawDamage) {
        if (player == null || data == null || ld == null || player.level().isClientSide()) return;
        float effectiveTrauma = penetratingDmg + bluntDmg * 0.55f;
        ModItems.applyCoreTraumaKnockdownAndDisarm(
                player, data, hitLimb, ld, bullet, attacker,
                isHeavySniperAmr, effectiveTrauma, totalRawDamage
        );
    }

    /**
     * 转发爆炸冲击至 Mod 本体核心摔倒与震落脱手方法
     */
    public static void onTaczExplosionHit(ServerPlayer player,
                                          MedicalData data,
                                          Entity directProjectile,
                                          LivingEntity attacker,
                                          float rawExplosionDamage) {
        if (player == null || data == null || player.level().isClientSide()) return;
        if (!ModItems.safeCfgBool(MedicalConfig.RAGDOLL_COMPAT_ENABLED, true)) return;

        float minBlastDmg = (float) ModItems.safeCfgDouble(MedicalConfig.RAGDOLL_EXPLOSION_MIN_DAMAGE, 3.5);
        if (rawExplosionDamage < minBlastDmg) return;

        Vec3 blastDir = ModItems.computeCoreImpactDirection(player, directProjectile, attacker);
        float blastStrength = Mth.clamp(0.42f + rawExplosionDamage * 0.045f, 0.40f, 1.45f);
        Vec3 blastVel = new Vec3(blastDir.x * blastStrength, Math.min(0.85, 0.28 + rawExplosionDamage * 0.022), blastDir.z * blastStrength);

        if (rawExplosionDamage >= minBlastDmg * 1.8f) {
            MedicalData.LimbData rightArm = data.getLimb(MedicalData.Limb.RIGHT_ARM);
            ModItems.checkAndTriggerCoreArmDisarm(player, MedicalData.Limb.RIGHT_ARM, rightArm, rawExplosionDamage * 0.65f, blastDir);
        }

        int baseDur = ModItems.safeCfgInt(MedicalConfig.RAGDOLL_EXPLOSION_DURATION_TICKS, 90);
        int scaledDur = Mth.clamp(Math.round(baseDur * (0.75f + rawExplosionDamage / 24.0f)), 40, 200);
        ModItems.triggerCoreTemporaryKnockdown(player, scaledDur, blastVel);
    }

    public static void checkAndTriggerArmDisarm(ServerPlayer player,
                                                MedicalData.Limb armLimb,
                                                MedicalData.LimbData armLd,
                                                float traumaDamage,
                                                Vec3 impactDir) {
        ModItems.checkAndTriggerCoreArmDisarm(player, armLimb, armLd, traumaDamage, impactDir);
    }

    public static void triggerTemporaryRagdollKnockdown(ServerPlayer player,
                                                        int durationTicks,
                                                        Vec3 launchVelocity,
                                                        String unusedMessageKey) {
        ModItems.triggerCoreTemporaryKnockdown(player, durationTicks, launchVelocity);
    }

    public static void triggerUnconsciousFullRagdoll(ServerPlayer player, Vec3 impulse) {
        ModItems.triggerCoreTemporaryKnockdown(player, 40, impulse);
    }

    /**
     * 客户端第一人称视角强制守护桥接器：
     * 若安装了 Sable: Ragdolls 等外部布娃娃模组，当触发受创倒地或布娃娃反应时，
     * 拦截其将视角切往第三人称 (CameraType.THIRD_PERSON_BACK / Camera.detached = true) 的操作，
     * 始终强制维持第一人称视角 (CameraType.FIRST_PERSON)！
     */
    @EventBusSubscriber(modid = RealMedicalMod.MODID, value = Dist.CLIENT)
    public static final class ClientRagdollBridge {

        private static Field fCameraDetached = null;
        private static boolean fieldResolved = false;

        private ClientRagdollBridge() {
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            Player player = mc.player;
            if (player == null || mc.level == null || mc.options == null) return;

            MedicalData data = player.getData(MedicalDataCapability.MEDICAL_DATA);
            boolean inTraumaKnockdown = ModItems.isPlayerTemporarilyKnockedDown(player)
                    || player.getForcedPose() == Pose.SWIMMING
                    || (data != null && (data.getConsciousness() <= 0 || data.isCardiacArrest()));

            if (inTraumaKnockdown && !mc.options.getCameraType().isFirstPerson()) {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
            }
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
            Minecraft mc = Minecraft.getInstance();
            Player player = mc.player;
            if (player == null || mc.level == null || mc.options == null || player.isSpectator()) return;

            MedicalData data = player.getData(MedicalDataCapability.MEDICAL_DATA);
            boolean inTraumaKnockdown = ModItems.isPlayerTemporarilyKnockedDown(player)
                    || player.getForcedPose() == Pose.SWIMMING
                    || (data != null && (data.getConsciousness() <= 0 || data.isCardiacArrest()));

            if (inTraumaKnockdown) {
                if (!mc.options.getCameraType().isFirstPerson()) {
                    mc.options.setCameraType(CameraType.FIRST_PERSON);
                }
                Camera cam = event.getCamera();
                if (cam != null) {
                    ModItems.ClientCoreKnockdownHooks.forceCameraFirstPersonUndetached(cam, player, (float) event.getPartialTick());
                }
            }
        }

        private static void forceUndetachCamera(Camera cam) {
            if (!fieldResolved) {
                fieldResolved = true;
                for (Field f : Camera.class.getDeclaredFields()) {
                    if (f.getType() == boolean.class) {
                        String name = f.getName().toLowerCase();
                        if (name.contains("detached") || name.equals("f_90556_")) {
                            f.setAccessible(true);
                            fCameraDetached = f;
                            break;
                        }
                    }
                }
            }
            if (fCameraDetached != null) {
                try {
                    fCameraDetached.setBoolean(cam, false);
                } catch (Throwable ignored) {
                }
            }
        }
    }
}