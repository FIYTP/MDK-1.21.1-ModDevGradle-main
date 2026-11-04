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
 * 3A 级剧痛瞬发状态机 (PainShockClientState)
 *
 * 客户端本地监测体感疼痛跳变，驱动：
 * 1. 指数阻尼衰减 (Exponential Decay) 的痛觉冲击波 Alpha 与进度
 * 2. 重度剧痛下的短暂边缘黑视收缩 (Blackout Tunnel)
 * 3. 3D 摄像机二阶欠阻尼弹簧后坐力曲线 (Camera Recoil Wave)
 * 4. 原版声学合成的剧痛耳鸣/闷击感音效
 */
@EventBusSubscriber(modid = "realmedicalmod", value = Dist.CLIENT)
public class PainShockClientState {

    /** 剧痛冲击视觉总时长 (ms) */
    private static final long TOTAL_DURATION_MS = 1250L;

    private static long shockStartTime = 0L;
    private static long shockSeed = 0L;
    private static float shockIntensity = 0.0f;
    private static float blackoutAlpha = 0.0f;
    private static int shockDirectionSign = 1;

    private static int lastPerceivedPain = -1;
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

        // 平滑衰减边缘黑视
        if (blackoutAlpha > 0.001f) {
            blackoutAlpha *= 0.92f;
            if (blackoutAlpha < 0.002f) {
                blackoutAlpha = 0.0f;
            }
        }

        MedicalData data = player.getData(MedicalDataCapability.MEDICAL_DATA);
        if (data == null) {
            lastPerceivedPain = -1;
            wasAboveSevereThreshold = false;
            return;
        }

        int current = data.getPerceivedPain();

        // 首次进入世界初始化记录，避免误触发
        if (lastPerceivedPain < 0) {
            lastPerceivedPain = current;
            wasAboveSevereThreshold = (current >= MedicalConfig.PAIN_SHOCK_SEVERE_THRESHOLD.get());
            return;
        }

        int severeThreshold = MedicalConfig.PAIN_SHOCK_SEVERE_THRESHOLD.get();
        int jumpThreshold = MedicalConfig.PAIN_SHOCK_JUMP_THRESHOLD.get();

        int deltaPain = current - lastPerceivedPain;
        boolean jumpTrigger = deltaPain >= jumpThreshold;
        boolean crossThresholdTrigger = (!wasAboveSevereThreshold && current >= severeThreshold);

        if ((jumpTrigger || crossThresholdTrigger) && cooldownTicks <= 0) {
            triggerShock(current, deltaPain);
            cooldownTicks = MedicalConfig.PAIN_SHOCK_COOLDOWN_TICKS.get();
        }

        lastPerceivedPain = current;
        wasAboveSevereThreshold = (current >= severeThreshold);
    }

    /**
     * 也可由外部或网络包主动调用触发痛觉休克
     */
    public static void triggerShock(float intensity, float blackoutSec) {
        shockStartTime = System.currentTimeMillis();
        shockSeed = shockStartTime ^ System.nanoTime();
        shockDirectionSign = ((shockSeed & 1L) == 0L) ? 1 : -1;
        float maxCfg = MedicalConfig.PAIN_SHOCK_WHITE_FLASH_ALPHA.get().floatValue();
        shockIntensity = Mth.clamp(intensity, 0.25f, 1.0f) * maxCfg;
        if (blackoutSec > 0.0f) {
            blackoutAlpha = Math.max(blackoutAlpha, Mth.clamp(intensity * 0.85f, 0.0f, 0.90f));
        }
    }

    private static void triggerShock(int currentPain, int deltaPain) {
        shockStartTime = System.currentTimeMillis();
        shockSeed = shockStartTime ^ System.nanoTime();
        shockDirectionSign = ((shockSeed & 1L) == 0L) ? 1 : -1;

        int severeThreshold = MedicalConfig.PAIN_SHOCK_SEVERE_THRESHOLD.get();
        int jumpThreshold = MedicalConfig.PAIN_SHOCK_JUMP_THRESHOLD.get();
        float jumpFactor = Mth.clamp(deltaPain / 20.0f, 0.35f, 1.0f);
        float totalFactor = Mth.clamp((float) currentPain / Math.max(1, severeThreshold), 0.4f, 1.0f);
        float rawIntensity = Math.max(jumpFactor, totalFactor);

        float maxCfg = MedicalConfig.PAIN_SHOCK_WHITE_FLASH_ALPHA.get().floatValue();
        shockIntensity = rawIntensity * maxCfg;

        // 重度疼痛触发边缘黑视聚拢
        if (currentPain >= severeThreshold) {
            blackoutAlpha = Math.max(blackoutAlpha, Mth.clamp(0.45f + (currentPain - severeThreshold) * 0.04f, 0.45f, 0.88f));
        } else if (deltaPain >= jumpThreshold * 1.5f) {
            blackoutAlpha = Math.max(blackoutAlpha, 0.35f);
        }

        // 播放双层原版合成音效：低沉内脏闷击 + 高频神经耳鸣泛音
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(
                    SoundEvents.WARDEN_HEARTBEAT, 0.55f, 0.85f));
            mc.getSoundManager().play(SimpleSoundInstance.forUI(
                    SoundEvents.PLAYER_HURT_ON_FIRE, 0.65f, 0.25f * rawIntensity));
        }
    }

    /**
     * 获取当前冲击归一化时间进度 [0.0, 1.0]
     */
    public static float getShockProgress() {
        if (shockStartTime == 0L) return 1.0f;
        long elapsed = System.currentTimeMillis() - shockStartTime;
        if (elapsed >= TOTAL_DURATION_MS) {
            shockStartTime = 0L;
            return 1.0f;
        }
        return Mth.clamp(elapsed / (float) TOTAL_DURATION_MS, 0.0f, 1.0f);
    }

    /**
     * 获取当前渲染 Alpha (0.0 ~ 1.0)
     * 采用快起 + 指数平滑长尾衰减曲线 (EaseOutExpo)，质感自然细腻
     */
    public static float getShockAlpha() {
        if (shockStartTime == 0L) return 0.0f;
        long elapsed = System.currentTimeMillis() - shockStartTime;
        if (elapsed >= TOTAL_DURATION_MS) {
            shockStartTime = 0L;
            return 0.0f;
        }

        float p = elapsed / (float) TOTAL_DURATION_MS;
        if (p < 0.08f) {
            // 前 100ms 极速冲顶
            return shockIntensity * (p / 0.08f);
        }
        // 指数阻尼消退
        float t = (p - 0.08f) / 0.92f;
        float decay = (float) Math.pow(1.0f - t, 2.6);
        return shockIntensity * decay;
    }

    /**
     * 3D 摄像机受击欠阻尼回弹波形（供 ViewportEvent.ComputeCameraAngles 使用）
     */
    public static float getCameraRecoilWave() {
        if (shockStartTime == 0L) return 0.0f;
        float p = getShockProgress();
        if (p >= 1.0f) return 0.0f;
        // 欠阻尼正弦振荡：初段猛烈偏转，随后轻柔回弹归位
        return (float) (Math.sin(p * Math.PI * 3.5) * Math.pow(1.0f - p, 2.2));
    }

    public static int getShockDirectionSign() {
        return shockDirectionSign;
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
        shockIntensity = 0.0f;
        blackoutAlpha = 0.0f;
        lastPerceivedPain = -1;
        wasAboveSevereThreshold = false;
        cooldownTicks = 0;
    }
}