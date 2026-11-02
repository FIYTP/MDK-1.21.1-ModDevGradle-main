package com.FIYTP.realmedicalmod.client;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.capability.MedicalDataCapability;
import com.FIYTP.realmedicalmod.config.MedicalConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * 剧痛瞬发状态机（方向2）：客户端本地检测体感疼痛跳变，触发白闪定格+抖动+淡出。
 *
 * 不需要网络包：体感疼痛数据通过 capability 同步到客户端（CrisisSoundManager 同款读法），
 * 与脑损伤发包模式不同（脑损伤触发决策在服务端随机）。
 *
 * 触发条件（满足其一）：
 * - 单 tick 体感疼痛跳变量 >= PAIN_SHOCK_JUMP_THRESHOLD（如骨折/烧伤瞬间的急性剧痛）
 * - 体感疼痛从下方越过 PAIN_SHOCK_SEVERE_THRESHOLD（首次进入剧痛区间）
 * 冷却 PAIN_SHOCK_COOLDOWN_TICKS 防止连续受伤刷屏。
 */
@EventBusSubscriber(modid = "realmedicalmod", value = Dist.CLIENT)
public class PainShockClientState {

    /** 白闪定格时长（ms） */
    private static final long FREEZE_DURATION_MS = 150L;
    /** 总时长：定格 + 淡出（ms） */
    private static final long TOTAL_DURATION_MS = 1150L;

    private static long shockStartTime = 0;
    private static float shockIntensity = 0.0f;
    private static int lastPerceivedPain = -1;
    /** 已经在剧痛阈值上方（用于检测"越过"而非"处于"） */
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

        MedicalData data = player.getData(MedicalDataCapability.MEDICAL_DATA);
        if (data == null) {
            reset();
            return;
        }

        int perceived = data.getPerceivedPain();
        int jumpThreshold = MedicalConfig.PAIN_SHOCK_JUMP_THRESHOLD.get();
        int severeThreshold = MedicalConfig.PAIN_SHOCK_SEVERE_THRESHOLD.get();

        if (lastPerceivedPain >= 0 && cooldownTicks <= 0) {
            int jump = perceived - lastPerceivedPain;
            boolean jumped = jump >= jumpThreshold;
            boolean crossedSevere = perceived >= severeThreshold && !wasAboveSevereThreshold
                    && lastPerceivedPain < severeThreshold;

            if (jumped || crossedSevere) {
                trigger(perceived, jump);
            }
        }

        // 跳变量门槛随绝对疼痛放宽：痛得越深，越小的增量也足以引爆
        wasAboveSevereThreshold = perceived >= severeThreshold;
        lastPerceivedPain = perceived;
        if (cooldownTicks > 0) {
            cooldownTicks--;
        }
    }

    private static void trigger(int perceived, int jump) {
        shockStartTime = System.currentTimeMillis();
        // 强度：跳变强度为主，绝对疼痛兜底，0.6~1.0
        float byJump = jump >= 0 ? Math.min(1.0f, jump / 20.0f) : 0.0f;
        float byLevel = Math.min(1.0f, perceived / 55.0f);
        shockIntensity = Math.max(0.6f, Math.max(byJump, byLevel));
        cooldownTicks = MedicalConfig.PAIN_SHOCK_COOLDOWN_TICKS.get();

        // 痛苦闷哼：随强度变调（原版音效，符合约定）
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            float vol = 0.35f + 0.35f * shockIntensity;
            float pitch = 1.35f - 0.5f * shockIntensity; // 越痛越低沉
            mc.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.PLAYER_HURT_DROWN, vol, pitch)
            );
        }
    }

    private static void reset() {
        lastPerceivedPain = -1;
        wasAboveSevereThreshold = false;
        cooldownTicks = 0;
    }

    // ==== 供 overlay 读取的状态 ====

    public static long getShockStartTime() {
        return shockStartTime;
    }

    public static float getShockIntensity() {
        return shockIntensity;
    }

    public static boolean isFrozenPhase() {
        return System.currentTimeMillis() - shockStartTime < FREEZE_DURATION_MS;
    }

    /** 0~1 淡出进度（定格结束后 0→1） */
    public static float getFadeProgress() {
        long elapsed = System.currentTimeMillis() - shockStartTime;
        if (elapsed < FREEZE_DURATION_MS) return 0.0f;
        return Math.min(1.0f, (float) (elapsed - FREEZE_DURATION_MS)
                / (TOTAL_DURATION_MS - FREEZE_DURATION_MS));
    }

    public static boolean isExpired() {
        return System.currentTimeMillis() - shockStartTime >= TOTAL_DURATION_MS;
    }

    public static long getFreezeDurationMs() {
        return FREEZE_DURATION_MS;
    }

    public static long getTotalDurationMs() {
        return TOTAL_DURATION_MS;
    }
}
