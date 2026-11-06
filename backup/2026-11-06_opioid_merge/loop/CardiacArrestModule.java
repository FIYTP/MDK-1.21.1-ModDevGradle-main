package com.FIYTP.realmedicalmod.loop;

import com.FIYTP.realmedicalmod.RealMedicalMod;
import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.config.MedicalConfig;

/**
 * 室颤与心脏骤停判定模块（修复高痛/昏迷状态下室颤倒计时卡死在 25 秒的 Bug）
 *
 * 【原版 Bug 根因说明】：
 * 1. 原代码在第 65 行（进入「2. 极限低血氧室颤」与「3. 心动过速室颤」判定之前）写了：
 *    if (data.getConsciousness() <= 0) { data.setBradyTimer(0); return; }
 *    当玩家处于 100 满疼痛且心率 > 200 BPM 时，前 5 秒意识尚大于 0，vfibTimer 正常累加至 5
 *    （UI 倒计时显示 30 - 5 = 25s）；第 5 秒末意识归零（或进入濒危状态 return），
 *    此后每秒循环在到达第 86 行「心动过速检测（室颤）」之前就被提前 return 截断！
 *    导致 vfibTimer 既不再递增、也不归零，永久卡死在 5（即 UI 上的 25s），室颤永远无法触发。
 * 2. 原代码在「1. 血容量硬性判定 (bloodVolume <= criticalBloodVol)」倒计时未结束时也直接 return，
 *    同样会导致已启动的 vfibTimer 冻结。
 *
 * 【修复方案】：
 * - 将 `consciousness <= 0` 的豁免判定严格下移至「4. 心动过缓（骤停）」分支内，
 *   确保昏迷状态下「2. 极限低血氧检测（缺氧性室颤）」与「3. 心动过速检测（室颤）」依然正常推进计时并触发室颤。
 * - 在血容量濒危倒计时未归零期间（尚未触发心脏骤停），不中断后续室颤检测。
 * - 全程读取 {@link MedicalConfig} 配置项，杜绝硬编码。
 */
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
            int timer = data.getCriticalTimer() - 1;
            data.setCriticalTimer(Math.max(0, timer));

            // 倒计时归零 -> 心脏骤停（无论昏迷/室颤/心率如何）
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
                RealMedicalMod.LOGGER.info("[CardiacArrest] 倒计时结束！血容量 {} ml -> 心脏骤停", bloodVolume);
                return;
            }
        } else if (data.isInCriticalState()) {
            // 如果血容量恢复到 > 门槛，取消濒危状态并中断倒计时
            data.setInCriticalState(false);
            data.setCriticalTimer(0);
            RealMedicalMod.LOGGER.info("[CardiacArrest] 濒危状态解除！血容量恢复至 {} ml", bloodVolume);
        }

        // 如果已经处于室颤状态，不再处理后续心率检测
        if (data.isInVFib()) {
            return;
        }

        // ===== 2. 极限低血氧检测（缺氧性室颤，即使意识归零昏迷也必须正常判定） =====
        int hypoxiaThreshold = MedicalConfig.SEVERE_HYPOXIA_THRESHOLD.get();
        int hypoxiaDuration = MedicalConfig.SEVERE_HYPOXIA_DURATION.get();
        float spo2 = data.getBloodOxygenFloat();
        if (spo2 < hypoxiaThreshold) {
            int timer = data.getHypoxiaTimer() + 1;
            data.setHypoxiaTimer(timer);
            if (timer >= hypoxiaDuration) {
                data.setVFib(true);
                data.setHypoxiaTimer(0);
                data.setVfibTimer(0);
                data.setBradyTimer(0);
                RealMedicalMod.LOGGER.info("[CardiacArrest] 缺氧性室颤触发！血氧 {} % 持续 {} 秒", spo2, hypoxiaDuration);
                return;
            }
        } else {
            data.setHypoxiaTimer(0);
        }

        // ===== 3. 心动过速检测（室颤，即使意识归零昏迷也必须正常推进倒计时） =====
        int vfibThreshold = MedicalConfig.VFIB_THRESHOLD.get();
        int vfibDuration = MedicalConfig.VFIB_DURATION.get();
        if (hr >= vfibThreshold) {
            int timer = data.getVfibTimer() + 1;
            data.setVfibTimer(timer);
            if (timer >= vfibDuration) {
                data.setVFib(true);
                data.setVfibTimer(0);
                data.setHypoxiaTimer(0);
                data.setBradyTimer(0);
                RealMedicalMod.LOGGER.info("[CardiacArrest] 室颤触发！心率 {} bpm 持续 {} 秒", hr, vfibDuration);
                return;
            }
        } else {
            data.setVfibTimer(0);
        }

        // ===== 4. 心动过缓（骤停） =====
        // 意识归零（昏迷）或处于失血濒危倒计时期间，不单独因心动过缓额外触发骤停计时
        if (data.getConsciousness() <= 0 || data.isInCriticalState()) {
            data.setBradyTimer(0);
            return;
        }

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