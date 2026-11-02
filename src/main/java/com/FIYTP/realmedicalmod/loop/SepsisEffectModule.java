package com.FIYTP.realmedicalmod.loop;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.config.MedicalConfig;

/**
 * 脓毒症效应模块 (SepsisEffectModule)
 * 职责：
 * 处理 SIRS 导致的直接全身病理效应。
 * 遵循项目生理系统职责边界原则：
 * 1. 血管扩张与毛细血管渗漏导致的有效循环血容量损失（SIRS 等级 2+）在此处处理；
 * 2. 心率刺激交感加成由 HeartRateModule 读取 sirsLevel 统一处理；
 * 3. 呼吸驱动加成由 RespirationModule 读取 sirsLevel 统一处理；
 * 4. 肺弥散障碍血氧惩罚由 BloodOxygenModule 读取 sirsLevel 统一处理；
 * 5. 外周血管阻力下降由 BloodPressureModule 读取 sirsLevel 修正 TPR；
 * 6. 意识抑制通过低 MAP 和低血氧的生理链路自然传导，不直接在此惩罚。
 */
public class SepsisEffectModule implements UpdateModule {

    @Override
    public void process(MedicalData data) {
        if (data == null) return;

        int level = data.getSirsLevel();
        if (level < 2) return;

        // 血容量损失（血管通透性增高 + 毛细血管渗漏，等级 2+）
        float loss = switch (level) {
            case 2 -> MedicalConfig.SIRS_BLOOD_LOSS_L2.get().floatValue();
            case 3 -> MedicalConfig.SIRS_BLOOD_LOSS_L3.get().floatValue();
            case 4 -> MedicalConfig.SIRS_BLOOD_LOSS_L4.get().floatValue();
            default -> 0.0f;
        };

        if (loss > 0) {
            int currentVolume = data.getBloodVolume();
            data.setBloodVolume(Math.max(0, currentVolume - (int) Math.ceil(loss)));
        }
    }
}
