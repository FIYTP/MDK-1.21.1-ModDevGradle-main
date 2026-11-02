package com.FIYTP.realmedicalmod.loop;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.config.MedicalConfig;

/**
 * 脑健康自动恢复模块
 * 只要玩家活着且脑健康未满，每隔配置周期自动恢复配置点数，不受任何其他生理因素影响。
 */
public class BrainHealthRegenModule implements UpdateModule {

    @Override
    public void process(MedicalData data) {
        int brainHealth = data.getBrainHealth();
        if (brainHealth >= 100 || data.getIntracranialLevel() >= 1) {
            if (data.getBrainHealthRegenTimer() != 0) {
                data.setBrainHealthRegenTimer(0);
            }
            return;
        }

        int interval = MedicalConfig.BRAIN_HEALTH_REGEN_INTERVAL.get();
        int amount = MedicalConfig.BRAIN_HEALTH_REGEN_AMOUNT.get();

        int timer = data.getBrainHealthRegenTimer() + 1;
        if (timer >= interval) {
            timer = 0;
            data.setBrainHealth(Math.min(100, brainHealth + amount));
        }
        data.setBrainHealthRegenTimer(timer);
    }
}
