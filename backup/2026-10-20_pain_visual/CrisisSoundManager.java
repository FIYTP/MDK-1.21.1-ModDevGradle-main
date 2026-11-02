package com.FIYTP.realmedicalmod.client.sound;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.capability.MedicalDataCapability;
import com.FIYTP.realmedicalmod.config.MedicalConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = "realmedicalmod", value = Dist.CLIENT)
public class CrisisSoundManager {

    public enum CrisisLevel {
        NORMAL,
        MILD,
        MODERATE,
        CRITICAL,
        FATAL
    }

    // 濒危不规则警报的间隔序列（tick），循环播放制造监护仪紊乱感
    private static final int[] IRREGULAR_ALARM_SEQUENCE = {18, 9, 24, 12};

    // 状态与节流计时器（以 Client Tick = 50ms 为基准）
    private static int tickCounter = 0;
    private static int heartbeatTickCounter = 0;
    private static int warningPipTickCounter = 0;
    private static int ambientDroneTickCounter = 0;

    // 重伤氛围层计时器
    private static int injuryAmbienceTickCounter = 0;

    // 濒危不规则警报计时器与步进
    private static int irregularAlarmTickCounter = 0;
    private static int irregularAlarmStep = 0;

    // 疼痛喘息计时器
    private static int painBreathTickCounter = 0;

    // 心跳二重奏：主搏动后的延迟弱拍
    private static int echoBeatTickCounter = 0;
    private static float echoBeatPitch = 0.6f;
    private static int heartbeatBeatCycle = 0;

    // 倒计时由激活到归零的跃迁追踪
    private static boolean countdownWasActive = false;

    // 危机升级触发判定
    private static CrisisLevel previousLevel = CrisisLevel.NORMAL;
    private static boolean wasCardiacArrest = false;
    private static boolean wasInVFib = false;
    private static boolean wasInCriticalState = false;
    private static int lastCriticalSeconds = -1;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!MedicalConfig.ENABLE_CRISIS_SOUND.get()) {
            resetState();
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null) {
            resetState();
            return;
        }

        MedicalData data = player.getData(MedicalDataCapability.MEDICAL_DATA);
        if (data == null) return;

        tickCounter++;

        // 1. 评估当前危机严重度等级
        CrisisLevel currentLevel = evaluateCrisisLevel(data);

        // 2. 检测跃迁与致命突发事件（Fatal & Escalation Layer）
        handleEscalationEvents(mc.getSoundManager(), player, data, currentLevel);

        // 3. 背景层：心跳脉搏与低氧环境音（Background Layer）
        handleBackgroundAudio(mc.getSoundManager(), player, data, currentLevel);

        // 4. 事件与监护层：警告音与濒危倒计时（Event & Monitor Layer）
        handleWarningAndCountdown(mc.getSoundManager(), player, data, currentLevel);

        // 更新状态追踪
        previousLevel = currentLevel;
        wasCardiacArrest = data.isCardiacArrest();
        wasInVFib = data.isInVFib();
        wasInCriticalState = data.isInCriticalState();
    }

    /**
     * 依据生理指标综合评估危机层级
     */
    public static CrisisLevel evaluateCrisisLevel(MedicalData data) {
        // 致命级：心脏骤停、室颤或脑健康重度受损濒临脑死亡
        if (data.isCardiacArrest() || data.isInVFib() || data.getBrainHealth() <= 20) {
            return CrisisLevel.FATAL;
        }

        // 严重级：触发濒危状态倒计时、血容量过低(<=3000)、重度低氧(<60%)或意识严重丧失(<40)
        int totalExternalBleeding = getTotalExternalBleeding(data);
        if (data.isInCriticalState() || data.getBloodVolume() <= 3000 || data.getBloodOxygen() < 60 || data.getConsciousness() < 40 || totalExternalBleeding >= 250) {
            return CrisisLevel.CRITICAL;
        }

        // 中度警告：心动过速(>130)或严重心动过缓(<45)、显著出血(>80)、血氧偏低(<85%)、意识模糊(<70)或体感剧痛
        if (data.getHeartRate() > 130 || data.getHeartRate() < 45 || totalExternalBleeding >= 80 || data.getBloodOxygen() < 85 || data.getConsciousness() < 70 || data.getPerceivedPain() >= 25) {
            return CrisisLevel.MODERATE;
        }

        // 轻度警告：心率稍快(>100)、轻度出血、轻度低氧(<93%)、轻度疼痛
        if (data.getHeartRate() > 100 || totalExternalBleeding > 0 || data.getBloodOxygen() < 93 || data.getPerceivedPain() >= 10) {
            return CrisisLevel.MILD;
        }

        return CrisisLevel.NORMAL;
    }

    /**
     * 处理危机跃迁或致命瞬间触发音效（单次打击乐/警报/停搏声）
     */
    private static void handleEscalationEvents(SoundManager soundManager, Player player, MedicalData data, CrisisLevel currentLevel) {
        // 突发心脏骤停瞬间：沉重的停跳低音与恐惧音效
        if (data.isCardiacArrest() && !wasCardiacArrest) {
            playLocalSound(soundManager, player, SoundEvents.BEACON_DEACTIVATE, 1.2f, 0.6f);
            playLocalSound(soundManager, player, SoundEvents.ELDER_GUARDIAN_CURSE, 0.8f, 0.7f);
        }

        // 突发室颤瞬间：尖锐失常的恐慌尖啸/震动
        if (data.isInVFib() && !wasInVFib) {
            playLocalSound(soundManager, player, SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.9f, 0.9f);
        }

        // 进入濒危状态瞬间（5秒倒计时启动）
        if (data.isInCriticalState() && !wasInCriticalState) {
            playLocalSound(soundManager, player, SoundEvents.ELDER_GUARDIAN_CURSE, 0.9f, 0.85f);
        }

        // 危机等级升级音效反馈
        if (currentLevel.ordinal() > previousLevel.ordinal()) {
            if (currentLevel == CrisisLevel.CRITICAL) {
                playLocalSound(soundManager, player, SoundEvents.BEACON_DEACTIVATE, 0.7f, 1.2f);
            }
        }
    }

    /**
     * 背景层：动态真实心跳脉冲与低氧压迫嗡鸣
     */
    private static void handleBackgroundAudio(SoundManager soundManager, Player player, MedicalData data, CrisisLevel currentLevel) {
        int hr = data.getHeartRate();

        // 当心脏骤停或心率为 0 时，心跳停止，静默无声或仅留极微弱窒息底噪
        if (data.isCardiacArrest() || hr <= 0) {
            // 心脏骤停时每隔 3 秒播放一次空洞窒息回响
            if (tickCounter % 60 == 0) {
                playLocalSound(soundManager, player, SoundEvents.CONDUIT_AMBIENT, 0.6f, 0.5f);
            }
            return;
        }

        // 判断是否为运动导致的心率加快
        boolean isExerciseInducedTachycardia =
                data.getExerciseContribution() > 20.0f
                && data.getBloodVolume() > 4000
                && !data.isCardiacArrest()
                && !data.isInVFib()
                && !data.isInCriticalState();

        int triggerBloodVolume = MedicalConfig.HEARTBEAT_TRIGGER_BLOOD_VOLUME.get();
        int triggerMaxHr = MedicalConfig.HEARTBEAT_TRIGGER_MAX_HEART_RATE.get();
        int triggerMinHr = MedicalConfig.HEARTBEAT_TRIGGER_MIN_HEART_RATE.get();

        // 心跳音效触发条件（满足任一即可）
        boolean shouldPlayHeartbeat =
                data.getBloodVolume() < triggerBloodVolume
                || data.isInCriticalState()
                || data.isCardiacArrest()
                || data.isInVFib()
                || (hr > 0 && hr < triggerMinHr)
                || (hr > triggerMaxHr && !isExerciseInducedTachycardia);

        if (!shouldPlayHeartbeat) {
            heartbeatTickCounter = 0;
            return;
        }

        // 计算心跳间隔 Tick 数：20 tick/s * 60 s / hr = 1200 / hr
        int intervalTicks = Math.max(5, Math.min(40, Math.round(1200.0f / (float) hr)));
        heartbeatTickCounter++;

        if (heartbeatTickCounter >= intervalTicks) {
            heartbeatTickCounter = 0;

            // 根据危急状态计算音量倍率：越危急音量越大
            // 状态倍率：血容量 < 3000 或濒危：1.5x；血容量 3000~3500：1.0x；心脏骤停/室颤：1.5x；严重心动过缓：0.8x
            float volumeMultiplier = 1.0f;
            int bloodVol = data.getBloodVolume();
            if (data.isInCriticalState() || bloodVol < 3000 || data.isInVFib()) {
                volumeMultiplier = 1.5f;
            } else if (bloodVol < triggerBloodVolume) {
                volumeMultiplier = 1.0f;
            } else if (hr < triggerMinHr) {
                volumeMultiplier = 0.8f;
            } else if (hr > triggerMaxHr) {
                volumeMultiplier = 1.1f;
            }

            float baseVol = MedicalConfig.CRISIS_HEARTBEAT_VOLUME.get().floatValue();
            float volume = volumeMultiplier * baseVol;
            float pitch;

            if (currentLevel == CrisisLevel.CRITICAL || currentLevel == CrisisLevel.FATAL) {
                // 严重危机下心跳急促、重击声
                pitch = 0.8f + Math.min(0.6f, (hr - 75) * 0.005f);
                heartbeatBeatCycle++;
                // 每 5 拍故意漏一拍，制造致命心律不齐的空缺感
                if (heartbeatBeatCycle % 5 != 0) {
                    playLocalSound(soundManager, player, SoundEvents.WARDEN_HEARTBEAT, volume, pitch);
                    // 二重奏：主搏动后 4 tick 补一记低八度弱拍
                    echoBeatTickCounter = 4;
                    echoBeatPitch = Math.max(0.5f, pitch - 0.35f);
                }
            } else if (hr < triggerMinHr) {
                // 严重过缓沉重拍
                pitch = 0.6f;
                playLocalSound(soundManager, player, SoundEvents.WARDEN_HEARTBEAT, volume, pitch);
            } else {
                // 病理过速或其他危急情况
                pitch = 0.75f + Math.min(0.5f, (hr - 75) * 0.004f);
                playLocalSound(soundManager, player, SoundEvents.WARDEN_HEARTBEAT, volume, pitch);
            }
        }

        // 低氧环境嗡鸣（血氧低于 80 时启动周期性压抑窒息低频氛围）
        int oxygen = data.getBloodOxygen();
        if (oxygen < 80) {
            ambientDroneTickCounter++;
            int droneInterval = (oxygen < 50) ? 40 : 80; // 血氧越低越密集
            if (ambientDroneTickCounter >= droneInterval) {
                ambientDroneTickCounter = 0;
                float droneVol = (100 - oxygen) / 100.0f * 0.7f;
                float dronePitch = 0.5f + (oxygen / 160.0f);
                playLocalSound(soundManager, player, SoundEvents.CONDUIT_AMBIENT, droneVol, dronePitch);
            }
        } else {
            ambientDroneTickCounter = 0;
        }

        // 心跳二重奏：主搏动之后延迟弹出的低八度弱拍（充盈感）
        if (echoBeatTickCounter > 0) {
            echoBeatTickCounter--;
            if (echoBeatTickCounter == 0) {
                playLocalSound(soundManager, player, SoundEvents.WARDEN_HEARTBEAT, 0.4f, echoBeatPitch);
            }
        }

        // 重伤持续氛围层：MODERATE 每 ~4s 一次暗色低鸣，CRITICAL 每 ~2s 并叠加 ominous 层
        if (currentLevel == CrisisLevel.CRITICAL || currentLevel == CrisisLevel.MODERATE) {
            injuryAmbienceTickCounter++;
            int ambienceInterval = (currentLevel == CrisisLevel.CRITICAL) ? 40 : 80;
            if (injuryAmbienceTickCounter >= ambienceInterval) {
                injuryAmbienceTickCounter = 0;
                if (currentLevel == CrisisLevel.CRITICAL) {
                    playLocalSound(soundManager, player, SoundEvents.RESPAWN_ANCHOR_AMBIENT, 0.55f, 0.5f);
                    playLocalSound(soundManager, player, SoundEvents.TRIAL_SPAWNER_AMBIENT_OMINOUS, 0.35f, 0.8f);
                } else {
                    playLocalSound(soundManager, player, SoundEvents.RESPAWN_ANCHOR_AMBIENT, 0.35f, 0.6f);
                }
            }
        } else {
            injuryAmbienceTickCounter = 0;
        }

        // 疼痛喘息层：体感剧痛时周期性的痛苦喘息，越痛越急促
        int perceivedPain = data.getPerceivedPain();
        if (perceivedPain >= 25) {
            painBreathTickCounter++;
            int gaspInterval = (perceivedPain >= 45) ? 30 : 50;
            if (painBreathTickCounter >= gaspInterval) {
                painBreathTickCounter = 0;
                float gaspVol = 0.35f + Math.min(0.4f, perceivedPain / 110.0f);
                playLocalSound(soundManager, player, SoundEvents.PLAYER_BREATH, gaspVol, 0.7f);
                if (perceivedPain >= 45) {
                    playLocalSound(soundManager, player, SoundEvents.PLAYER_HURT_DROWN, 0.25f, 1.1f);
                }
            }
        } else {
            painBreathTickCounter = 0;
        }
    }

    /**
     * 事件与监护层：监护仪警报滴答、濒危倒计时与严重急救预警
     */
    private static void handleWarningAndCountdown(SoundManager soundManager, Player player, MedicalData data, CrisisLevel currentLevel) {
        // 1. 濒危 5 秒倒计时精确同步节拍（每秒随倒计时变化击发）
        if (data.isInCriticalState() && data.getCriticalTimer() > 0) {
            countdownWasActive = true;
            int currentTimerSec = data.getCriticalTimer();
            if (currentTimerSec != lastCriticalSeconds) {
                lastCriticalSeconds = currentTimerSec;
                // 逐秒升调：越逼近归零音高越高，制造窒息般的紧迫爬升
                float risePitch = Math.min(2.0f, 1.2f + (20 - currentTimerSec) * 0.09f);
                // 最后 5 秒高频紧迫音调，前 15 秒规律监护警告滴答
                if (currentTimerSec <= 5) {
                    playLocalSound(soundManager, player, SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, Math.min(2.2f, risePitch + 0.2f));
                    playLocalSound(soundManager, player, SoundEvents.UI_BUTTON_CLICK.value(), 0.8f, risePitch);
                } else if (currentTimerSec <= 10) {
                    playLocalSound(soundManager, player, SoundEvents.NOTE_BLOCK_PLING.value(), 0.8f, risePitch);
                } else {
                    playLocalSound(soundManager, player, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.7f, risePitch);
                }
            }
            return;
        } else {
            lastCriticalSeconds = -1;
            // 倒计时由激活→归零（心脏骤停）：播放下沉长鸣“平线”
            if (countdownWasActive) {
                countdownWasActive = false;
                if (data.isCardiacArrest()) {
                    playLocalSound(soundManager, player, SoundEvents.BEACON_DEACTIVATE, 1.0f, 0.5f);
                }
            }
        }

        // 2. 监护仪预警鸣叫（不同级别危机下的监视器警告鸣响）
        warningPipTickCounter++;

        if (currentLevel == CrisisLevel.FATAL) {
            // 心脏骤停/室颤中：每 2 秒一次连续蜂鸣警告
            if (warningPipTickCounter >= 40) {
                warningPipTickCounter = 0;
                playLocalSound(soundManager, player, SoundEvents.NOTE_BLOCK_PLING.value(), 0.6f, 0.6f);
            }
        } else if (currentLevel == CrisisLevel.CRITICAL) {
            // 严重危机中：不规则间隔的交替音色报警，模拟真实监护仪的紊乱鸣叫
            irregularAlarmTickCounter++;
            int alarmInterval = IRREGULAR_ALARM_SEQUENCE[irregularAlarmStep % IRREGULAR_ALARM_SEQUENCE.length];
            if (irregularAlarmTickCounter >= alarmInterval) {
                irregularAlarmTickCounter = 0;
                boolean highTone = (irregularAlarmStep % 2) == 0;
                irregularAlarmStep++;
                if (highTone) {
                    playLocalSound(soundManager, player, SoundEvents.NOTE_BLOCK_PLING.value(), 0.7f, 1.5f);
                } else {
                    playLocalSound(soundManager, player, SoundEvents.NOTE_BLOCK_BELL.value(), 0.55f, 1.25f);
                }
            }
        } else if (currentLevel == CrisisLevel.MODERATE) {
            // 中度危机中：每 3 秒一次温和提示
            irregularAlarmTickCounter = 0;
            irregularAlarmStep = 0;
            if (warningPipTickCounter >= 60) {
                warningPipTickCounter = 0;
                playLocalSound(soundManager, player, SoundEvents.NOTE_BLOCK_PLING.value(), 0.4f, 1.1f);
            }
        } else {
            warningPipTickCounter = 0;
            irregularAlarmTickCounter = 0;
            irregularAlarmStep = 0;
        }
    }

    /**
     * 计算所有肢体外出血总速率
     */
    private static int getTotalExternalBleeding(MedicalData data) {
        int total = 0;
        for (MedicalData.Limb limb : MedicalData.Limb.values()) {
            MedicalData.LimbData ld = data.getLimb(limb);
            if (ld != null) {
                total += ld.getBleeding();
            }
        }
        return total;
    }

    /**
     * 播放玩家本地独立音效
     */
    private static void playLocalSound(SoundManager soundManager, Player player, net.minecraft.sounds.SoundEvent soundEvent, float volume, float pitch) {
        if (soundEvent == null || soundManager == null || player == null) return;
        soundManager.play(SimpleSoundInstance.forUI(soundEvent, pitch, volume));
    }

    private static void resetState() {
        tickCounter = 0;
        heartbeatTickCounter = 0;
        warningPipTickCounter = 0;
        ambientDroneTickCounter = 0;
        injuryAmbienceTickCounter = 0;
        irregularAlarmTickCounter = 0;
        irregularAlarmStep = 0;
        painBreathTickCounter = 0;
        echoBeatTickCounter = 0;
        echoBeatPitch = 0.6f;
        heartbeatBeatCycle = 0;
        countdownWasActive = false;
        previousLevel = CrisisLevel.NORMAL;
        wasCardiacArrest = false;
        wasInVFib = false;
        wasInCriticalState = false;
        lastCriticalSeconds = -1;
    }
}
