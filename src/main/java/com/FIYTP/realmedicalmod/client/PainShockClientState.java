package com.FIYTP.realmedicalmod.client;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.capability.MedicalDataCapability;
import com.FIYTP.realmedicalmod.config.MedicalConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * 3A 级突发重创与剧痛冲击状态机 (PainShockClientState)
 *
 * 多通道实时监测突发重伤（体感疼痛跳变、部位总痛感暴增、大出血迸发、骨折/脱臼发生），驱动：
 * 1. 动脉血雾飞溅与边缘血斑残留 (Arterial Blood Splatter & Mist)
 * 2. 重创瞬间红白过曝震荡与屏幕裂纹 (Concussion Flash & Fracture Cracks)
 * 3. 深度边缘黑视骤缩 (Deep Tunnel Blackout Slam)
 * 4. 3D 镜头三轴猛烈受击甩动与广角 FOV 冲击波 (Camera Whip & FOV Punch)
 */
@EventBusSubscriber(modid = "realmedicalmod", value = Dist.CLIENT)
public class PainShockClientState {

    private static final long TOTAL_DURATION_MS = 1650L;
    

    private static long shockStartTime = 0L;
    private static long splatterStartTime = 0L;
    private static long shockSeed = 0L;
    private static float shockIntensity = 0.0f;
    private static float blackoutAlpha = 0.0f;
    private static float bloodSplatterPeak = 0.0f;
    private static float concussionFlashPeak = 0.0f;
    private static float fovPunchAmplitude = 0.0f;
    private static int shockDirectionSign = 1;
    private static float hitDirX = 0.0f;
    private static float hitDirY = 0.0f;
    private static boolean fractureImpact = false;

    private static int lastPerceivedPain = -1;
    private static int lastTotalPain = -1;
    private static int lastTotalBleed = -1;
    private static int lastFractureCount = -1;
    private static final int[] lastLimbPain = new int[]{-1, -1, -1, -1, -1, -1};
    private static boolean wasAboveSevereThreshold = false;
    private static int cooldownTicks = 0;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!MedicalConfig.PAIN_SHOCK_ENABLED.get()) {
            reset();
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null) {
            reset();
            return;
        }

        if (mc.isPaused()) return;

        if (cooldownTicks > 0) {
            cooldownTicks--;
        }

        if (blackoutAlpha > 0.001f) {
            blackoutAlpha *= 0.90f;
            if (blackoutAlpha < 0.002f) {
                blackoutAlpha = 0.0f;
            }
        }

        MedicalData data = player.getData(MedicalDataCapability.MEDICAL_DATA);
        if (data == null) {
            reset();
            return;
        }

        int currentPerceived = data.getPerceivedPain();
        int currentTotalPain = data.getTotalPainLevel();
        int currentTotalBleed = 0;
        int currentFractures = 0;

        MedicalData.Limb[] limbs = MedicalData.Limb.values();
        for (MedicalData.Limb limb : limbs) {
            MedicalData.LimbData ld = data.getLimb(limb);
            if (ld != null) {
                currentTotalBleed += ld.getBleeding() + ld.getInternalBleedingRate();
                if (ld.hasFracture() || ld.hasDislocation() || ld.isAmputated()) {
                    currentFractures++;
                }
            }
        }

        if (lastPerceivedPain < 0) {
            lastPerceivedPain = currentPerceived;
            lastTotalPain = currentTotalPain;
            lastTotalBleed = currentTotalBleed;
            lastFractureCount = currentFractures;
            for (int i = 0; i < limbs.length; i++) {
                MedicalData.LimbData ld = data.getLimb(limbs[i]);
                lastLimbPain[i] = ld != null ? ld.getPainLevel() : 0;
            }
            wasAboveSevereThreshold = (currentPerceived >= MedicalConfig.PAIN_SHOCK_SEVERE_THRESHOLD.get());
            return;
        }

        int severeThreshold = MedicalConfig.PAIN_SHOCK_SEVERE_THRESHOLD.get();
        int jumpThreshold = Math.min(10, MedicalConfig.PAIN_SHOCK_JUMP_THRESHOLD.get());

        int deltaPerceived = Math.max(0, currentPerceived - lastPerceivedPain);
        int deltaTotalPain = Math.max(0, currentTotalPain - lastTotalPain);
        int deltaBleed = Math.max(0, currentTotalBleed - lastTotalBleed);
        int deltaFractures = Math.max(0, currentFractures - lastFractureCount);

        // 计算受击方位向量 (左/右/上/下)
        float dx = 0.0f;
        float dy = 0.0f;
        for (int i = 0; i < limbs.length; i++) {
            MedicalData.LimbData ld = data.getLimb(limbs[i]);
            int pNow = ld != null ? ld.getPainLevel() : 0;
            int dp = Math.max(0, pNow - Math.max(0, lastLimbPain[i]));
            lastLimbPain[i] = pNow;
            if (dp > 0) {
                switch (limbs[i]) {
                    case HEAD -> dy -= dp * 1.2f;
                    case CHEST -> dy += dp * 0.6f;
                    case LEFT_ARM, LEFT_LEG -> dx -= dp * 1.1f;
                    case RIGHT_ARM, RIGHT_LEG -> dx += dp * 1.1f;
                }
            }
        }

        boolean jumpTrigger = deltaPerceived >= jumpThreshold
                || deltaTotalPain >= 2
                || deltaBleed >= 80
                || deltaFractures > 0;
        boolean crossThresholdTrigger = (!wasAboveSevereThreshold && currentPerceived >= severeThreshold);
        boolean heavyOverride = deltaPerceived >= 18 || deltaTotalPain >= MedicalConfig.HEAVY_TRAUMA_PAIN_DELTA_THRESHOLD.get() || deltaBleed >= MedicalConfig.HEAVY_TRAUMA_BLEED_DELTA_THRESHOLD.get() || deltaFractures > 0;

        if ((jumpTrigger || crossThresholdTrigger) && (cooldownTicks <= 0 || heavyOverride)) {
            triggerHeavyTrauma(currentPerceived, deltaPerceived, deltaTotalPain, deltaBleed, deltaFractures, dx, dy);
            cooldownTicks = heavyOverride ? 4 : Math.min(12, MedicalConfig.PAIN_SHOCK_COOLDOWN_TICKS.get());
        }

        lastPerceivedPain = currentPerceived;
        lastTotalPain = currentTotalPain;
        lastTotalBleed = currentTotalBleed;
        lastFractureCount = currentFractures;
        wasAboveSevereThreshold = (currentPerceived >= severeThreshold);
    }

    public static void triggerShock(float intensity, float blackoutSec) {
        long now = System.currentTimeMillis();
        shockStartTime = now;
        splatterStartTime = now;
        shockSeed = now ^ System.nanoTime();
        shockDirectionSign = ((shockSeed & 1L) == 0L) ? 1 : -1;
        hitDirX = shockDirectionSign * 0.6f;
        hitDirY = -0.3f;
        fractureImpact = intensity >= 0.75f;

        float clamped = Mth.clamp(intensity, 0.35f, 1.0f);
        shockIntensity = clamped;
        bloodSplatterPeak = clamped * 0.90f;
        concussionFlashPeak = clamped * 0.85f;
        fovPunchAmplitude = 6.0f + clamped * 6.5f;
        if (blackoutSec > 0.0f) {
            blackoutAlpha = Math.max(blackoutAlpha, Mth.clamp(clamped * 0.88f, 0.45f, 0.92f));
        }
    }

    private static void triggerHeavyTrauma(int currentPain, int deltaPerceived, int deltaTotalPain,
                                           int deltaBleed, int deltaFractures, float dx, float dy) {
        long now = System.currentTimeMillis();
        shockStartTime = now;
        splatterStartTime = now;
        shockSeed = now ^ System.nanoTime();

        float len = Mth.sqrt(dx * dx + dy * dy);
        if (len > 0.05f) {
            hitDirX = dx / len;
            hitDirY = dy / len;
            shockDirectionSign = (hitDirX >= 0.0f) ? 1 : -1;
        } else {
            shockDirectionSign = ((shockSeed & 1L) == 0L) ? 1 : -1;
            hitDirX = shockDirectionSign * 0.7f;
            hitDirY = (((shockSeed >>> 4) & 1L) == 0L) ? -0.4f : 0.4f;
        }

        int severeThreshold = Math.max(10, MedicalConfig.PAIN_SHOCK_SEVERE_THRESHOLD.get());
        float pFactor = Mth.clamp(deltaPerceived / 22.0f, 0.0f, 1.0f);
        float tFactor = Mth.clamp(deltaTotalPain / 6.0f, 0.0f, 1.0f);
        float bFactor = Mth.clamp(deltaBleed / 260.0f, 0.0f, 1.0f);
        float fFactor = (deltaFractures > 0) ? 0.95f : 0.0f;
        float absFactor = Mth.clamp((float) currentPain / severeThreshold, 0.35f, 1.0f);

        float severity = Mth.clamp(Math.max(Math.max(pFactor, tFactor), Math.max(bFactor, Math.max(fFactor, absFactor * 0.75f))), 0.40f, 1.0f);
        boolean isHeavy = severity >= 0.62f || deltaFractures > 0 || deltaBleed >= MedicalConfig.HEAVY_TRAUMA_BLEED_DELTA_THRESHOLD.get() || deltaTotalPain >= MedicalConfig.HEAVY_TRAUMA_PAIN_DELTA_THRESHOLD.get();

        fractureImpact = (deltaFractures > 0 || severity >= 0.78f);
        shockIntensity = severity;
        bloodSplatterPeak = isHeavy ? Mth.clamp(0.68f + severity * 0.32f, 0.68f, 1.0f) : (severity * 0.55f);
        concussionFlashPeak = isHeavy ? Mth.clamp(0.72f + severity * 0.28f, 0.72f, 1.0f) : (severity * 0.50f);
        float maxFovPunch = MedicalConfig.HEAVY_TRAUMA_FOV_PUNCH_MAX.get().floatValue();
        fovPunchAmplitude = isHeavy ? (maxFovPunch * (0.55f + 0.45f * severity)) : (maxFovPunch * 0.35f * severity);

        if (isHeavy || currentPain >= severeThreshold) {
            blackoutAlpha = Math.max(blackoutAlpha, Mth.clamp(0.58f + severity * 0.34f, 0.58f, 0.92f));
        } else {
            blackoutAlpha = Math.max(blackoutAlpha, 0.38f);
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(
                    SoundEvents.WARDEN_HEARTBEAT, 0.85f, 0.75f));
            mc.getSoundManager().play(SimpleSoundInstance.forUI(
                    SoundEvents.PLAYER_ATTACK_CRIT, 0.75f * severity, 0.62f));
            if (deltaFractures > 0) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(
                        SoundEvents.BONE_BLOCK_BREAK, 1.0f, 0.65f));
            }
        }
    }

    public static float getShockProgress() {
        if (shockStartTime == 0L) return 1.0f;
        long elapsed = System.currentTimeMillis() - shockStartTime;
        if (elapsed >= TOTAL_DURATION_MS) {
            shockStartTime = 0L;
            return 1.0f;
        }
        return Mth.clamp(elapsed / (float) TOTAL_DURATION_MS, 0.0f, 1.0f);
    }

    public static float getShockAlpha() {
        if (shockStartTime == 0L) return 0.0f;
        long elapsed = System.currentTimeMillis() - shockStartTime;
        if (elapsed >= TOTAL_DURATION_MS) {
            shockStartTime = 0L;
            return 0.0f;
        }
        float p = elapsed / (float) TOTAL_DURATION_MS;
        if (p < 0.05f) {
            return shockIntensity * (p / 0.05f);
        }
        float t = (p - 0.05f) / 0.95f;
        return shockIntensity * (float) Math.pow(1.0f - t, 2.1);
    }

    public static float getBloodSplatterAlpha() {
        if (splatterStartTime == 0L) return 0.0f;
        long elapsed = System.currentTimeMillis() - splatterStartTime;
        if (elapsed >= Math.max(300L, MedicalConfig.HEAVY_TRAUMA_SPLATTER_DURATION_MS.get().longValue())) {
            splatterStartTime = 0L;
            return 0.0f;
        }
        float p = elapsed / (float) Math.max(300L, MedicalConfig.HEAVY_TRAUMA_SPLATTER_DURATION_MS.get().longValue());
        if (p < 0.04f) {
            return bloodSplatterPeak * (p / 0.04f);
        }
        float t = (p - 0.04f) / 0.96f;
        return bloodSplatterPeak * (float) Math.pow(1.0f - t, 1.75);
    }

    public static float getConcussionFlashAlpha() {
        if (shockStartTime == 0L) return 0.0f;
        long elapsed = System.currentTimeMillis() - shockStartTime;
        if (elapsed >= 340L) return 0.0f;
        float p = elapsed / 340.0f;
        return concussionFlashPeak * (1.0f - p * p);
    }

    public static float getFovPunchDelta() {
        if (shockStartTime == 0L) return 0.0f;
        float p = getShockProgress();
        if (p >= 1.0f) return 0.0f;
        // 初段猛烈向内压缩视野 (-FOV)，随后回弹扩张 (+FOV)
        return (float) (-Math.sin(p * Math.PI * 2.6) * Math.pow(1.0f - p, 2.3)) * fovPunchAmplitude;
    }

    public static float getCameraRecoilWave() {
        if (shockStartTime == 0L) return 0.0f;
        float p = getShockProgress();
        if (p >= 1.0f) return 0.0f;
        return (float) (Math.sin(p * Math.PI * 4.2) * Math.pow(1.0f - p, 1.9));
    }

    public static int getShockDirectionSign() {
        return shockDirectionSign;
    }

    public static float getHitDirX() {
        return hitDirX;
    }

    public static float getHitDirY() {
        return hitDirY;
    }

    public static boolean isFractureImpact() {
        return fractureImpact;
    }

    public static long getShockSeed() {
        return shockSeed;
    }

    public static float getBlackoutAlpha() {
        return blackoutAlpha;
    }

    public static float getImpactIntensity() {
        return getShockAlpha();
    }

    private static void reset() {
        shockStartTime = 0L;
        splatterStartTime = 0L;
        shockIntensity = 0.0f;
        blackoutAlpha = 0.0f;
        bloodSplatterPeak = 0.0f;
        concussionFlashPeak = 0.0f;
        fovPunchAmplitude = 0.0f;
        lastPerceivedPain = -1;
        lastTotalPain = -1;
        lastTotalBleed = -1;
        lastFractureCount = -1;
        for (int i = 0; i < lastLimbPain.length; i++) {
            lastLimbPain[i] = -1;
        }
        wasAboveSevereThreshold = false;
        cooldownTicks = 0;
    }
}