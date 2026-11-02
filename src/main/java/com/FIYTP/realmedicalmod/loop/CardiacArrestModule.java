package com.FIYTP.realmedicalmod.loop;

import com.FIYTP.realmedicalmod.RealMedicalMod;
import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.config.MedicalConfig;

public class CardiacArrestModule implements UpdateModule {

    @Override
    public void process(MedicalData data) {
        // 如果已经处于骤停状态，不再处理
        if (data.isCardiacArrest()) {
            return;
        }

        int hr = data.getHeartRate();
        int bloodVolume = data.getBloodVolume();
        int criticalBloodVol = MedicalConfig.CRITICAL_BLOOD_VOLUME.get();
        int criticalTimerMax = MedicalConfig.CRITICAL_TIMER_MAX.get();

        // ===== 1. 血容量硬性判定（最高优先级，无视昏迷/室颤状态） =====
        if (bloodVolume <= criticalBloodVol) {
            // 如果尚未进入濒危状态，触发倒计时
            if (!data.isInCriticalState()) {
                data.setInCriticalState(true);
                data.setCriticalTimer(criticalTimerMax);
                RealMedicalMod.LOGGER.info("[CardiacArrest] 濒危状态触发！血容量 {} ml，倒计时 {} 秒", bloodVolume, criticalTimerMax);
            }

            // 倒计时递减
            int timer = data.getCriticalTimer();
            timer--;
            data.setCriticalTimer(Math.max(0, timer));

            // 倒计时归零 → 心脏骤停（无论昏迷/室颤/心率如何）
            if (timer <= 0) {
                data.setCardiacArrest(true);
                data.setConsciousness(0);
                data.setHeartRate(0);
                data.setInCriticalState(false);
                data.setCriticalTimer(0);
                // 清理室颤状态与各计时器，避免骤停+室颤混合态
                data.setVFib(false);
                data.setHypoxiaTimer(0);
                data.setVfibTimer(0);
                data.setBradyTimer(0);
                RealMedicalMod.LOGGER.info("[CardiacArrest] 倒计时结束！血容量 {} ml → 心脏骤停", bloodVolume);
            }
            return; // 血容量判定后，跳过心率检测
        }

        // 如果血容量恢复到 > 门槛，取消濒危状态并中止倒计时
        if (data.isInCriticalState()) {
            data.setInCriticalState(false);
            data.setCriticalTimer(0);
            RealMedicalMod.LOGGER.info("[CardiacArrest] 濒危状态解除！血容量恢复至 {} ml", bloodVolume);
        }

        // 如果已经处于室颤状态，不再处理后续心率检测
        if (data.isInVFib()) {
            return;
        }

        // 意识归零（昏迷）是大脑问题，不是心脏问题，不应触发心动过缓骤停
        if (data.getConsciousness() <= 0) {
            data.setBradyTimer(0);
            return;
        }

        // ===== 2. 极限低血氧检测（缺氧性室颤） =====
        int hypoxiaThreshold = MedicalConfig.SEVERE_HYPOXIA_THRESHOLD.get();
        int hypoxiaDuration = MedicalConfig.SEVERE_HYPOXIA_DURATION.get();
        float spo2 = data.getBloodOxygenFloat();
        if (spo2 < hypoxiaThreshold) {
            int timer = data.getHypoxiaTimer() + 1;
            data.setHypoxiaTimer(timer);
            if (timer >= hypoxiaDuration) {
                data.setVFib(true);
                data.setHypoxiaTimer(0);
                RealMedicalMod.LOGGER.info("[CardiacArrest] 缺氧性室颤触发！血氧 {} % 持续 {} 秒", spo2, hypoxiaDuration);
            }
        } else {
            data.setHypoxiaTimer(0);
        }

        // ===== 3. 心动过速检测（室颤） =====
        int vfibThreshold = MedicalConfig.VFIB_THRESHOLD.get();
        int vfibDuration = MedicalConfig.VFIB_DURATION.get();
        if (hr >= vfibThreshold) {
            int timer = data.getVfibTimer() + 1;
            data.setVfibTimer(timer);
            if (timer >= vfibDuration) {
                data.setVFib(true);
                data.setVfibTimer(0);
                RealMedicalMod.LOGGER.info("[CardiacArrest] 室颤触发！心率 {} bpm 持续 {} 秒", hr, vfibDuration);
            }
        } else {
            data.setVfibTimer(0);
        }

        // ===== 4. 心动过缓（骤停） =====
        int bradyThreshold = MedicalConfig.BRADY_ARREST_THRESHOLD.get();
        int bradyDuration = MedicalConfig.BRADY_DURATION.get();
        if (hr > 0 && hr < bradyThreshold) {
            int timer = data.getBradyTimer() + 1;
            data.setBradyTimer(timer);
            if (timer >= bradyDuration) {
                data.setCardiacArrest(true);
                data.setConsciousness(0);
                data.setHeartRate(0);
                data.setBradyTimer(0);
                RealMedicalMod.LOGGER.info("[CardiacArrest] 心脏骤停触发！心率 {} bpm 持续 {} 秒", hr, bradyDuration);
            }
        } else {
            data.setBradyTimer(0);
        }
    }
}
