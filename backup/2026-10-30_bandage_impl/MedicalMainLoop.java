package com.FIYTP.realmedicalmod.loop;

import com.FIYTP.realmedicalmod.RealMedicalMod;
import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.capability.MedicalDataCapability;
import com.FIYTP.realmedicalmod.config.MedicalConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;

@EventBusSubscriber(modid = RealMedicalMod.MODID)
public class MedicalMainLoop {

    // 每 20 tick（1 秒）执行一次医疗逻辑
    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++tickCounter < 20) return;
        tickCounter = 0;
        tick(event.getServer());
    }

    private static void tick(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        for (ServerPlayer player : players) {
            MedicalData data = player.getData(MedicalDataCapability.MEDICAL_DATA);
            if (data == null) continue;

            AttributeArbiter.clear(player);

            // ===== 更新运动强度与运动计时器 =====
            float targetIntensity = 0.0f;
            boolean isMoving = false;

            // 1. 冲刺检测
            if (player.isSprinting()) {
                targetIntensity = MedicalConfig.SPRINT_EXERCISE_INTENSITY.get().floatValue();
                isMoving = true;
                int runningTime = data.getRunningTimer();
                data.setRunningTimer(runningTime + 1);
            }

            // 2. 跳跃检测
            if (player.fallDistance > 0.1f || player.getDeltaMovement().y > 0.1f) {
                targetIntensity = Math.max(targetIntensity, MedicalConfig.JUMP_EXERCISE_INTENSITY.get().floatValue());
                isMoving = true;
            }

            // 3. 攻击/挖掘计时器（由 HitPartHandler 设置）
            int attackTimer = data.getAttackTimer();
            if (attackTimer > 0) {
                data.setAttackTimer(attackTimer - 1);
                targetIntensity = Math.max(targetIntensity, 0.15f);
                isMoving = true;
            }

            // 如果没有任何运动，重置跑步计时器
            if (!isMoving) {
                int runningTime = data.getRunningTimer();
                if (runningTime > 0) {
                    data.setRunningTimer(Math.max(0, runningTime - 2));
                }
                // 若没有冲刺，强度归零
                if (!player.isSprinting()) {
                    targetIntensity = 0.0f;
                }
            }

            data.setExerciseIntensity(Math.min(targetIntensity, 1.0f));

            // 更新运动贡献（降低系数，使心率上升更平缓）
            float currentContribution = data.getExerciseContribution();
            float maxHR = MedicalConfig.MAX_HEART_RATE.get();
            float restHR = MedicalConfig.RESTING_HEART_RATE.get();
            float targetContribution = targetIntensity * (maxHR - restHR) * 0.5f;
            float delta = targetContribution - currentContribution;

            if (Math.abs(delta) < 0.1f) {
                currentContribution = targetContribution;
            } else {
                float rate;
                if (delta > 0) {
                    float ratio = (targetContribution - currentContribution) / targetContribution;
                    rate = (ratio > 0.2f) ? 5.5f : 0.8f;
                    currentContribution += rate;
                    if (currentContribution > targetContribution) currentContribution = targetContribution;
                } else {
                    rate = 2.5f;
                    currentContribution -= rate;
                    if (currentContribution < targetContribution) currentContribution = targetContribution;
                }
            }
            data.setExerciseContribution(currentContribution);

            // 更新水下/溺水状态检测
            data.setUnderwater(player.isUnderWater());

            // ===== 主循环模块 =====
            BuffManager buffManager = BuffManager.INSTANCE;
            buffManager.clear(player);

            heartRateModule.process(data);
            bloodPressureModule.process(data);
            bleedModule.process(data);
            burnModule.process(player, data); // 烧伤：热力期/渗漏休克/回吸收/感染压力/愈合
            toxinModule.process(player, data); // 中毒：代谢 + 阈值维护（症状由各系统传导）
            localInfectionModule.process(player, data);
            systemicInfectionModule.process(player, data);
            sepsisEffectModule.process(data);
            internalBleedingModule.process(data);
            fractureModule.process(data);
            fractureTraumaModule.process(player, data);
            limbEffectModule.process(player, data);
            hemothoraxModule.process(player, data);
            intracranialHemorrhageModule.process(data);
            naturalClottingModule.process(data);
            bloodVolumeRegenModule.process(player, data);
            respirationModule.process(data);
            bloodOxygenModule.process(data);
            brainHealthModule.process(data);
            brainHealthRegenModule.process(data);
            brainDamageTriggerModule.process(player, data);
            consciousnessModule.process(data);
            cardiacArrestModule.process(data);
            painDecayModule.process(data);
            BleedParticleModule.spawn(player, data);

            // ===== 影响评估层（连续仲裁）=====
            painEffectModule.process(player, data, buffManager);
            consciousnessSuggestionModule.process(player, data, buffManager);
            organEffectModule.process(player, data, buffManager);
            toxinEffectModule.process(player, data, buffManager);
            systemicEffectModule.process(player, data);
            consciousnessEffectModule.process(player, data);
            limbEffectModule.process(player, data);

            // ===== 血胸运动限制（等级 3+ 禁止冲刺；移速压制已由仲裁器统一管）=====
            int hemoLevel = data.getHemothoraxLevel();
            if (hemoLevel >= 3 && player.isSprinting()) {
                player.setSprinting(false);
            }

            // 现在已在服务端主线程，可直接应用 Buff 与属性仲裁
            buffManager.apply(player);
            AttributeArbiter.apply(player);

            deathModule.process(player, data);

            if (!player.isRemoved() && player.isAlive()) {
                player.syncData(MedicalDataCapability.MEDICAL_DATA);
            }
        }
    }

    // ===== 单例模块 =====
    private static final HeartRateModule heartRateModule = new HeartRateModule();
    private static final BloodPressureModule bloodPressureModule = new BloodPressureModule();
    private static final BleedModule bleedModule = new BleedModule();
    private static final BurnModule burnModule = new BurnModule();
    private static final LocalInfectionModule localInfectionModule = new LocalInfectionModule();
    private static final SystemicInfectionModule systemicInfectionModule = new SystemicInfectionModule();
    private static final SepsisEffectModule sepsisEffectModule = new SepsisEffectModule();
    private static final InternalBleedingModule internalBleedingModule = new InternalBleedingModule();
    private static final FractureModule fractureModule = new FractureModule();
    private static final FractureTraumaModule fractureTraumaModule = new FractureTraumaModule();
    private static final LimbEffectModule limbEffectModule = new LimbEffectModule();
    private static final HemothoraxModule hemothoraxModule = new HemothoraxModule();
    private static final IntracranialHemorrhageModule intracranialHemorrhageModule = new IntracranialHemorrhageModule();
    private static final NaturalClottingModule naturalClottingModule = new NaturalClottingModule();
    private static final BloodVolumeRegenModule bloodVolumeRegenModule = new BloodVolumeRegenModule();
    private static final BloodOxygenModule bloodOxygenModule = new BloodOxygenModule();
    private static final BrainHealthModule brainHealthModule = new BrainHealthModule();
    private static final BrainHealthRegenModule brainHealthRegenModule = new BrainHealthRegenModule();
    private static final BrainDamageTriggerModule brainDamageTriggerModule = new BrainDamageTriggerModule();
    private static final ConsciousnessModule consciousnessModule = new ConsciousnessModule();
    private static final ConsciousnessEffectModule consciousnessEffectModule = new ConsciousnessEffectModule();
    private static final CardiacArrestModule cardiacArrestModule = new CardiacArrestModule();
    private static final RespirationModule respirationModule = new RespirationModule();
    private static final ToxinModule toxinModule = new ToxinModule();
    private static final ToxinEffectModule toxinEffectModule = new ToxinEffectModule(new ToxinModule());
    private static final PainDecayModule painDecayModule = new PainDecayModule();
    private static final PainEffectModule painEffectModule = new PainEffectModule();
    private static final ConsciousnessSuggestionModule consciousnessSuggestionModule = new ConsciousnessSuggestionModule();
    private static final OrganEffectModule organEffectModule = new OrganEffectModule();
    private static final SystemicEffectModule systemicEffectModule = new SystemicEffectModule();
    private static final DeathModule deathModule = new DeathModule();
}
