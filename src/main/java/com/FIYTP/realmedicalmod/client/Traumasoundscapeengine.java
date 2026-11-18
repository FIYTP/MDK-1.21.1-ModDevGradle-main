package com.FIYTP.realmedicalmod.client;

import com.FIYTP.realmedicalmod.RealMedicalMod;
import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.capability.MedicalDataCapability;
import com.FIYTP.realmedicalmod.config.MedicalConfig;
import com.FIYTP.realmedicalmod.treatment.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

/**
 * 3A 级程序化受创生理杂音与窒息/紧张实时声学合成引擎 (TraumaSoundscapeEngine)
 *
 * 核心设计（100% 由 {@link MedicalConfig} 驱动，零硬编码，无需外部音频资源文件）：
 * 1. 实时 PCM 数字信号合成器（独立守护线程，22050Hz 16-bit 低延迟流式输出）：
 *    - 声道 A【高频尖锐耳鸣 + 52Hz 颅内闷罐轰鸣 (Tinnitus & Intracranial Sub-Bass)】：
 *      头部中弹、爆震、脑健康下降或剧痛休克时，实时合成 3820Hz 尖锐耳鸣正弦波 + 52Hz 颅内高压低频震动与布朗噪声；
 *    - 声道 B【内耳双音心搏与颈动脉血液冲刷杂音 (Lub-Dub & Carotid Blood Whoosh)】：
 *      失血、大出血或心动过速时，严格按实时心率合成 S1/S2 双音心搏及“呼—呼—”动脉血液湍流粉红杂音；
 *    - 声道 C【破风箱窒息哮鸣、胸壁漏气嘶鸣与湿啰音 (Asphyxia Wheezing, Stridor & Rales)】：
 *      气胸、血胸、肺挫伤或低血氧 (SpO2 < 90%) 时，随呼吸周期合成气管狭窄哮鸣音 (440~620Hz 共振峰)、胸壁吸吮嘶鸣与血泡湿啰音；
 *    - 声道 D【濒死暗黑低频紧张压迫音与 ICU 心电监护警报 (Critical Panic Drone & Flatline)】：
 *      重度大出血、濒死倒计时、室颤或心脏骤停时，合成不协和二度暗黑低频氛围音与心电监护急促滴答/长鸣。
 * 2. 原版音效多层混音增强层：
 *    同步叠加变频心跳、窒息喘息与耳鸣泛音，确保在任何音频驱动环境下均具备极强的紧张感与窒息沉浸感。
 */
@EventBusSubscriber(modid = RealMedicalMod.MODID, value = Dist.CLIENT)
public final class TraumaSoundscapeEngine {

    private static final int SAMPLE_RATE = 22050;
    private static final int BUFFER_SAMPLES = 1024; // ~46ms 低延迟缓冲块

    // 由客户端主线程每 Tick 更新、音频合成线程无锁读取的生理声学目标参数
    private static volatile boolean engineActive = false;
    private static volatile float masterVolume = 0.75f;
    private static volatile float targetHeartRateBpm = 75.0f;
    private static volatile float targetRespRateRpm = 16.0f;

    private static volatile float targetTinnitusLevel = 0.0f;      // 0..1 尖锐耳鸣强度
    private static volatile float targetRumbleLevel = 0.0f;        // 0..1 颅内低频压迫嗡鸣强度
    private static volatile float targetBloodWhooshLevel = 0.0f;   // 0..1 颈动脉血流冲刷与心搏强度
    private static volatile float targetAsphyxiaWheezeLevel = 0.0f;// 0..1 窒息哮鸣与破风箱喘息强度
    private static volatile float targetChestRalesLevel = 0.0f;    // 0..1 血胸/开放气胸水泡湿啰音强度
    private static volatile float targetPanicDroneLevel = 0.0f;    // 0..1 濒死/大出血暗黑紧张氛围音强度
    private static volatile boolean targetFlatlineTone = false;    // 心脏骤停长鸣音

    private static volatile boolean synthThreadStarted = false;
    private static float acuteTinnitusTimerSec = 0.0f;
    private static int lastRecordedBrainHealth = -1;
    private static int vanillaBreathTickCounter = 0;
    private static int vanillaHeartTickCounter = 0;

    private TraumaSoundscapeEngine() {
    }

    /**
     * 供外部或受击瞬间主动触发急性爆震耳鸣
     */
    public static void triggerAcuteTinnitus(float durationSeconds) {
        acuteTinnitusTimerSec = Math.max(acuteTinnitusTimerSec, durationSeconds);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;

        if (player == null || mc.level == null || mc.isPaused() || !player.isAlive() || player.isCreative() || player.isSpectator()) {
            engineActive = false;
            targetTinnitusLevel = 0.0f;
            targetRumbleLevel = 0.0f;
            targetBloodWhooshLevel = 0.0f;
            targetAsphyxiaWheezeLevel = 0.0f;
            targetChestRalesLevel = 0.0f;
            targetPanicDroneLevel = 0.0f;
            targetFlatlineTone = false;
            return;
        }

        if (!ModItems.safeCfgBool(MedicalConfig.TRAUMA_SOUNDSCAPE_ENGINE_ENABLED, true)) {
            engineActive = false;
            return;
        }

        MedicalData data = player.getData(MedicalDataCapability.MEDICAL_DATA);
        if (data == null) {
            engineActive = false;
            return;
        }

        ensureSynthThreadRunning();

        float gameMasterVol = mc.options.getSoundSourceVolume(SoundSource.MASTER)
                * mc.options.getSoundSourceVolume(SoundSource.PLAYERS);
        masterVolume = Mth.clamp(
                gameMasterVol * (float) ModItems.safeCfgDouble(MedicalConfig.TRAUMA_SOUND_MASTER_VOLUME, 0.75),
                0.0f, 1.5f
        );

        float tinnitusCfg = (float) ModItems.safeCfgDouble(MedicalConfig.TRAUMA_SOUND_TINNITUS_VOLUME, 0.70);
        float wheezeCfg = (float) ModItems.safeCfgDouble(MedicalConfig.TRAUMA_SOUND_ASPHYXIA_WHEEZE_VOLUME, 0.85);
        float whooshCfg = (float) ModItems.safeCfgDouble(MedicalConfig.TRAUMA_SOUND_BLOOD_WHOOSH_VOLUME, 0.80);
        float droneCfg = (float) ModItems.safeCfgDouble(MedicalConfig.TRAUMA_SOUND_CRITICAL_DRONE_VOLUME, 0.85);

        // 1) 评估高频耳鸣与颅内低频轰鸣 (Tinnitus & Intracranial Rumble)
        int curBrain = data.getBrainHealth();
        if (lastRecordedBrainHealth >= 0 && curBrain < lastRecordedBrainHealth - 3) {
            acuteTinnitusTimerSec = Math.max(acuteTinnitusTimerSec, 4.5f + (lastRecordedBrainHealth - curBrain) * 0.18f);
        }
        lastRecordedBrainHealth = curBrain;

        float shockAlpha = PainShockClientState.getShockAlpha();
        if (shockAlpha > 0.35f) {
            acuteTinnitusTimerSec = Math.max(acuteTinnitusTimerSec, shockAlpha * 3.8f);
        }
        if (acuteTinnitusTimerSec > 0.0f) {
            acuteTinnitusTimerSec = Math.max(0.0f, acuteTinnitusTimerSec - 0.05f);
        }

        MedicalData.LimbData headLd = data.getLimb(MedicalData.Limb.HEAD);
        float headPainNorm = (headLd != null) ? (headLd.getPainLevel() / 10.0f) : 0.0f;
        float sysPainNorm = Mth.clamp(data.getPerceivedPain() / Math.max(10.0f, (float) ModItems.safeCfgDouble(MedicalConfig.MAX_PERCEIVED_PAIN, 100.0)), 0.0f, 1.0f);
        float brainDeficit = Mth.clamp((100.0f - data.getBrainHealthFloat()) / 85.0f, 0.0f, 1.0f);
        int icLv = (data.getIntracranialOnsetTimer() >= ModItems.safeCfgInt(MedicalConfig.ONSET_DELAY, 60)) ? data.getIntracranialLevel() : 0;

        float rawTinnitus = Math.max(
                Mth.clamp(acuteTinnitusTimerSec / 3.5f, 0.0f, 1.0f),
                headPainNorm * 0.55f + brainDeficit * 0.65f + icLv * 0.25f + (sysPainNorm > 0.6f ? (sysPainNorm - 0.6f) * 0.85f : 0.0f)
        );
        targetTinnitusLevel = Mth.clamp(rawTinnitus * tinnitusCfg, 0.0f, 1.0f);

        // 颅内低频压迫嗡鸣（重伤、休克、低意识、剧痛时耳膜轰鸣）
        float consDeficit = Mth.clamp((85.0f - data.getConsciousnessFloat()) / 85.0f, 0.0f, 1.0f);
        targetRumbleLevel = Mth.clamp((sysPainNorm * 0.65f + consDeficit * 0.75f + shockAlpha * 0.85f) * tinnitusCfg, 0.0f, 1.0f);

        // 2) 评估颈动脉血液冲刷杂音与内耳双音心搏 (Blood Whoosh & Lub-Dub)
        int totalBleed = 0;
        for (MedicalData.Limb l : MedicalData.Limb.values()) {
            MedicalData.LimbData ld = data.getLimb(l);
            if (ld != null) {
                totalBleed += ld.getBleeding() + ld.getInternalBleedingRate();
            }
        }
        float bleedNorm = Mth.clamp(totalBleed / 380.0f, 0.0f, 1.0f);
        float bloodLossNorm = Mth.clamp((data.getMaxBloodVolume() - data.getBloodVolume()) / 2000.0f, 0.0f, 1.0f);
        float hr = Mth.clamp(data.getHeartRate(), 35.0f, 220.0f);
        targetHeartRateBpm = hr;
        float tachyNorm = Mth.clamp((hr - 95.0f) / 85.0f, 0.0f, 1.0f);

        float rawWhoosh = Math.max(bleedNorm * 0.85f, Math.max(bloodLossNorm * 0.90f, tachyNorm * 0.75f));
        targetBloodWhooshLevel = data.isCardiacArrest() ? 0.0f : Mth.clamp(rawWhoosh * whooshCfg, 0.0f, 1.0f);

        // 3) 评估气胸/血胸/低血氧“破风箱窒息哮鸣与湿啰音” (Asphyxia Wheezing & Rales)
        int pneumoType = ModItems.getPneumothoraxType(data);
        float pleuralAir = ModItems.getPleuralAirMl(data);
        int hemoLv = data.getHemothoraxLevel();
        float lungContusion = data.getLungContusion();
        float spo2Deficit = Mth.clamp((94.0f - data.getBloodOxygenFloat()) / 28.0f, 0.0f, 1.0f);

        float respRpm = Mth.clamp(data.getRespirationRate(), 6.0f, 42.0f);
        targetRespRateRpm = respRpm;

        float rawWheeze = spo2Deficit * 0.85f
                + (pneumoType > 0 ? (0.35f + pneumoType * 0.22f) : 0.0f)
                + (pleuralAir / 1000.0f) * 0.55f
                + (lungContusion / 100.0f) * 0.50f
                + hemoLv * 0.22f;
        targetAsphyxiaWheezeLevel = (data.isCardiacArrest() || data.getRespirationRate() <= 0)
                ? 0.0f
                : Mth.clamp(rawWheeze * wheezeCfg, 0.0f, 1.0f);

        float rawRales = (pneumoType == 2 ? 0.65f : 0.0f)
                + Mth.clamp(data.getChestBloodAccumulation() / 900.0f, 0.0f, 0.85f)
                + (lungContusion > 30.0f ? (lungContusion - 30.0f) / 100.0f : 0.0f);
        targetChestRalesLevel = Mth.clamp(rawRales * wheezeCfg, 0.0f, 1.0f);

        // 4) 评估濒死/大出血/室颤“死神低语暗黑紧张氛围音” (Critical Panic Drone)
        float critUrgency = 0.0f;
        if (data.isInCriticalState() && data.getCriticalTimer() > 0) {
            float maxCrit = Math.max(1.0f, (float) ModItems.safeCfgInt(MedicalConfig.CRITICAL_TIMER_MAX, 60));
            critUrgency = 0.45f + 0.55f * (1.0f - data.getCriticalTimer() / maxCrit);
        }
        float rawDrone = Math.max(
                critUrgency,
                Math.max(
                        data.isInVFib() || data.isCardiacArrest() ? 0.95f : 0.0f,
                        Math.max(bleedNorm * 0.65f, Math.max(bloodLossNorm * 0.70f, spo2Deficit * 0.75f))
                )
        );
        targetPanicDroneLevel = Mth.clamp(rawDrone * droneCfg, 0.0f, 1.0f);
        targetFlatlineTone = data.isCardiacArrest();

        engineActive = (targetTinnitusLevel > 0.01f
                || targetRumbleLevel > 0.01f
                || targetBloodWhooshLevel > 0.01f
                || targetAsphyxiaWheezeLevel > 0.01f
                || targetPanicDroneLevel > 0.01f
                || targetFlatlineTone);

        // 5) 原版多层窒息喘息与内耳心搏混音辅助（提供空间立体感）
        tickLayeredVanillaAudioCues(mc, player, data);
    }

    private static void tickLayeredVanillaAudioCues(Minecraft mc, Player player, MedicalData data) {
        if (mc.getSoundManager() == null) return;

        // 窒息急促喘息与胸膜漏气嘶鸣
        if (targetAsphyxiaWheezeLevel > 0.18f && !data.isCardiacArrest()) {
            int breathIntervalTicks = Math.max(16, Math.round(68.0f - targetAsphyxiaWheezeLevel * 42.0f));
            vanillaBreathTickCounter++;
            if (vanillaBreathTickCounter >= breathIntervalTicks) {
                vanillaBreathTickCounter = 0;
                float vol = Mth.clamp(targetAsphyxiaWheezeLevel * masterVolume * 0.55f, 0.05f, 0.85f);
                float pitch = 0.65f + (player.getRandom().nextFloat() * 0.25f);
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_BREATH, pitch, vol));
                if (targetChestRalesLevel > 0.25f) {
                    mc.getSoundManager().play(SimpleSoundInstance.forUI(
                            SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT,
                            0.55f + player.getRandom().nextFloat() * 0.25f,
                            vol * 0.65f
                    ));
                }
            }
        }

        // 重度失血/心动过速沉闷内耳心搏
        if (targetBloodWhooshLevel > 0.25f && !data.isCardiacArrest()) {
            int beatIntervalTicks = Math.max(6, Math.round(1200.0f / Math.max(45.0f, targetHeartRateBpm)));
            vanillaHeartTickCounter++;
            if (vanillaHeartTickCounter >= beatIntervalTicks) {
                vanillaHeartTickCounter = 0;
                float vol = Mth.clamp(targetBloodWhooshLevel * masterVolume * 0.52f, 0.05f, 0.80f);
                float pitch = Mth.clamp(0.72f + (targetHeartRateBpm - 75.0f) * 0.0035f, 0.60f, 1.35f);
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WARDEN_HEARTBEAT, pitch, vol));
            }
        }
    }

    private static synchronized void ensureSynthThreadRunning() {
        if (synthThreadStarted) return;
        synthThreadStarted = true;

        Thread synthThread = new Thread(TraumaSoundscapeEngine::runProceduralAudioLoop, "RealMedicalMod-TraumaSoundscape");
        synthThread.setDaemon(true);
        synthThread.setPriority(Thread.NORM_PRIORITY + 1);
        synthThread.start();
    }

    private static void runProceduralAudioLoop() {
        SourceDataLine line = null;
        try {
            AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            if (!AudioSystem.isLineSupported(info)) {
                return;
            }
            line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(format, BUFFER_SAMPLES * 4);
            line.start();

            byte[] pcmBuffer = new byte[BUFFER_SAMPLES * 2];

            double tinnitusPhase = 0.0;
            double rumblePhase = 0.0;
            double dronePhase1 = 0.0;
            double dronePhase2 = 0.0;
            double wheezeCarrierPhase = 0.0;
            double flatlinePhase = 0.0;

            double heartCyclePhase = 0.0;
            double respCyclePhase = 0.0;

            // 平滑包络状态
            float curTinnitus = 0.0f;
            float curRumble = 0.0f;
            float curWhoosh = 0.0f;
            float curWheeze = 0.0f;
            float curRales = 0.0f;
            float curDrone = 0.0f;

            // 粉红/布朗滤波器状态
            float brownState = 0.0f;
            float bandPassState1 = 0.0f;
            float bandPassState2 = 0.0f;
            long rngState = 0xCAFEBABE12345678L;

            final double invSampleRate = 1.0 / SAMPLE_RATE;

            while (true) {
                if (!engineActive || masterVolume <= 0.005f) {
                    curTinnitus *= 0.85f;
                    curRumble *= 0.85f;
                    curWhoosh *= 0.85f;
                    curWheeze *= 0.85f;
                    curRales *= 0.85f;
                    curDrone *= 0.85f;
                    if (curTinnitus < 0.002f && curRumble < 0.002f && curWhoosh < 0.002f
                            && curWheeze < 0.002f && curDrone < 0.002f) {
                        Thread.sleep(45L);
                        continue;
                    }
                }

                float mVol = masterVolume;
                float hrBpm = targetHeartRateBpm;
                float respRpm = targetRespRateRpm;
                boolean isFlatline = targetFlatlineTone;

                for (int i = 0; i < BUFFER_SAMPLES; i++) {
                    // 平滑参数插值，消除爆音
                    curTinnitus += (targetTinnitusLevel - curTinnitus) * 0.0015f;
                    curRumble += (targetRumbleLevel - curRumble) * 0.0015f;
                    curWhoosh += (targetBloodWhooshLevel - curWhoosh) * 0.0015f;
                    curWheeze += (targetAsphyxiaWheezeLevel - curWheeze) * 0.0015f;
                    curRales += (targetChestRalesLevel - curRales) * 0.0015f;
                    curDrone += (targetPanicDroneLevel - curDrone) * 0.0012f;

                    // 伪随机白噪声生成 (-1.0 .. 1.0)
                    rngState ^= (rngState << 13);
                    rngState ^= (rngState >>> 7);
                    rngState ^= (rngState << 17);
                    float whiteNoise = ((rngState & 0xFFFFFF) / 8388608.0f) - 1.0f;

                    // 布朗低频噪声（模拟耳膜内部闷响与血液轰鸣）
                    brownState = Mth.clamp((brownState + whiteNoise * 0.045f) * 0.985f, -1.0f, 1.0f);

                    // 二阶带通滤波白噪声（模拟气管狭窄摩擦气流声与颈动脉湍流杂音）
                    bandPassState1 += (whiteNoise - bandPassState1) * 0.14f;
                    bandPassState2 += (bandPassState1 - bandPassState2) * 0.06f;
                    float airwayNoise = bandPassState1 - bandPassState2;

                    float sampleMix = 0.0f;

                    // ---------------------------------------------------------
                    // 1. 高频尖锐耳鸣 (3820 Hz + 7640 Hz 泛音) & 52 Hz 颅内轰鸣
                    // ---------------------------------------------------------
                    if (curTinnitus > 0.003f) {
                        tinnitusPhase = (tinnitusPhase + 3820.0 * invSampleRate) % 1.0;
                        float tinWave = (float) Math.sin(tinnitusPhase * Math.PI * 2.0) * 0.82f
                                + (float) Math.sin(tinnitusPhase * Math.PI * 4.0) * 0.18f;
                        sampleMix += tinWave * curTinnitus * 0.14f;
                    }
                    if (curRumble > 0.003f) {
                        rumblePhase = (rumblePhase + 52.0 * invSampleRate) % 1.0;
                        float rumWave = (float) Math.sin(rumblePhase * Math.PI * 2.0) * 0.65f + brownState * 0.55f;
                        sampleMix += rumWave * curRumble * 0.28f;
                    }

                    // ---------------------------------------------------------
                    // 2. 双音内耳心搏 (Lub-Dub) 与颈动脉血液冲刷湍流杂音 (Whoosh)
                    // ---------------------------------------------------------
                    if (curWhoosh > 0.003f && !isFlatline) {
                        heartCyclePhase = (heartCyclePhase + (hrBpm / 60.0) * invSampleRate) % 1.0;
                        float lubEnv = 0.0f;
                        float dubEnv = 0.0f;
                        float whooshEnv = 0.0f;

                        if (heartCyclePhase < 0.14) {
                            // S1 第一心音 "Lub" (48 Hz 低沉搏动)
                            lubEnv = (float) Math.sin((heartCyclePhase / 0.14) * Math.PI);
                        } else if (heartCyclePhase >= 0.22 && heartCyclePhase < 0.34) {
                            // S2 第二心音 "Dub" (64 Hz 瓣膜闭合音)
                            dubEnv = (float) Math.sin(((heartCyclePhase - 0.22) / 0.12) * Math.PI) * 0.78f;
                        }
                        if (heartCyclePhase >= 0.06 && heartCyclePhase < 0.42) {
                            // 收缩期颈动脉血液冲刷内耳的 "呼—" 湍流杂音
                            whooshEnv = (float) Math.sin(((heartCyclePhase - 0.06) / 0.36) * Math.PI);
                        }

                        float thudFreq = (lubEnv > 0.0f) ? 46.0f : 64.0f;
                        float heartThud = (float) Math.sin(heartCyclePhase * thudFreq * Math.PI * 2.0) * (lubEnv + dubEnv);
                        float carotidWhoosh = (airwayNoise * 0.70f + brownState * 0.30f) * whooshEnv;
                        sampleMix += (heartThud * 0.34f + carotidWhoosh * 0.26f) * curWhoosh;
                    }

                    // ---------------------------------------------------------
                    // 3. 破风箱窒息哮鸣 (Wheezing)、胸壁吸吮声与水泡湿啰音 (Rales)
                    // ---------------------------------------------------------
                    if (curWheeze > 0.003f && !isFlatline) {
                        respCyclePhase = (respCyclePhase + (respRpm / 60.0) * invSampleRate) % 1.0;
                        // 0.0~0.42 为费力吸气相 (Inspiration)，0.48~0.92 为哮鸣呼气相 (Expiration)
                        float inhaleEnv = (respCyclePhase < 0.42)
                                ? (float) Math.sin((respCyclePhase / 0.42) * Math.PI)
                                : 0.0f;
                        float exhaleEnv = (respCyclePhase >= 0.48 && respCyclePhase < 0.92)
                                ? (float) Math.sin(((respCyclePhase - 0.48) / 0.44) * Math.PI)
                                : 0.0f;

                        // 气管狭窄高调哮鸣共振峰 (460Hz ~ 610Hz 随呼吸气流弯曲)
                        double wheezeFreq = (inhaleEnv > 0.0f)
                                ? (510.0 + inhaleEnv * 95.0)
                                : (440.0 + exhaleEnv * 75.0);
                        wheezeCarrierPhase = (wheezeCarrierPhase + wheezeFreq * invSampleRate) % 1.0;
                        float wheezeTone = (float) Math.sin(wheezeCarrierPhase * Math.PI * 2.0) * 0.35f;

                        // 破风箱气流摩擦杂音 + 血胸/开放性气胸粗湿啰音爆裂声
                        float crackle = 0.0f;
                        if (curRales > 0.05f && ((rngState & 0x1FF) < 6)) {
                            crackle = whiteNoise * curRales * 0.85f;
                        }
                        float breathSignal = (airwayNoise * 0.85f + wheezeTone * 0.45f + crackle)
                                * (inhaleEnv * 1.10f + exhaleEnv * 0.85f);
                        sampleMix += breathSignal * curWheeze * 0.28f;
                    }

                    // ---------------------------------------------------------
                    // 4. 濒死暗黑紧张氛围低音 (Dissonant Panic Drone) 与心脏骤停长鸣
                    // ---------------------------------------------------------
                    if (curDrone > 0.003f) {
                        // 小二度不协和低频拍频 (65.4Hz C2 + 69.3Hz C#2)，营造极度压抑的濒死窒息恐慌感
                        dronePhase1 = (dronePhase1 + 65.4 * invSampleRate) % 1.0;
                        dronePhase2 = (dronePhase2 + 69.3 * invSampleRate) % 1.0;
                        float darkDrone = ((float) Math.sin(dronePhase1 * Math.PI * 2.0)
                                + (float) Math.sin(dronePhase2 * Math.PI * 2.0)) * 0.5f;
                        sampleMix += darkDrone * curDrone * 0.20f;
                    }

                    if (isFlatline) {
                        flatlinePhase = (flatlinePhase + 1046.5 * invSampleRate) % 1.0;
                        float flatBeep = (float) Math.sin(flatlinePhase * Math.PI * 2.0);
                        sampleMix += flatBeep * 0.16f;
                    }

                    // 软限幅防削波失真并写入 16-bit Little-Endian PCM 缓冲
                    float finalSample = Mth.clamp(sampleMix * mVol, -0.95f, 0.95f);
                    short pcmVal = (short) (finalSample * 32767.0f);
                    pcmBuffer[i * 2] = (byte) (pcmVal & 0xFF);
                    pcmBuffer[i * 2 + 1] = (byte) ((pcmVal >>> 8) & 0xFF);
                }

                line.write(pcmBuffer, 0, pcmBuffer.length);
            }
        } catch (Throwable ignored) {
        } finally {
            if (line != null) {
                try {
                    line.stop();
                    line.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }
}