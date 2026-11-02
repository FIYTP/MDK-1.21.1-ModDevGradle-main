package com.FIYTP.realmedicalmod.loop;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.config.MedicalConfig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * 全身感染与 SIRS 状态机模块 (SystemicInfectionModule)
 * 职责：
 * 1. 监控各肢体感染，当最高肢体感染或全身感染总值达标时触发 SIRS（等级 1）。
 * 2. 依据环境倍率驱动 SIRS 晋级计时器（等级 1 -> 4）。
 * 3. 感染源清除后驱动 SIRS 降级恢复（直至恢复为 0，关闭 SIRS）。
 */
public class SystemicInfectionModule implements UpdateModule {

    @Override
    public void process(MedicalData data) {
        process(null, data);
    }

    public void process(Player player, MedicalData data) {
        if (data == null) return;

        // 1. 计算感染指标
        int maxLimbInfection = 0;
        int totalInfection = 0;
        for (MedicalData.Limb limb : MedicalData.Limb.values()) {
            MedicalData.LimbData ld = data.getLimb(limb);
            if (ld == null) continue;
            int inf = ld.getInfection();
            maxLimbInfection = Math.max(maxLimbInfection, inf);
            totalInfection += inf;
        }

        boolean hasInfectionSource = maxLimbInfection >= MedicalConfig.SIRS_LIMB_THRESHOLD.get()
                || totalInfection >= MedicalConfig.SIRS_TOTAL_THRESHOLD.get()
                // 烧伤源：TBSA 超凝血紊乱阈值（默认30%）→ 大面积创面炎症介质风暴，直接计入 SIRS 感染源
                || data.getBurnTBSA() > MedicalConfig.BURN_TBSA_COAGULOPATHY_THRESHOLD.get().floatValue();

        int currentLevel = data.getSirsLevel();

        // 2. SIRS 触发（唯一条件：感染值达到阈值）
        if (currentLevel == 0) {
            if (hasInfectionSource) {
                data.setSirsLevel(1);
                data.setSirsProgressionTimer(0);
                data.setSirsRecoveryTimer(0);
                data.setSirsActive(true);
            }
            return;
        }

        // 3. SIRS 晋级（时间驱动 + 感染源仍存在）
        if (currentLevel >= 1 && currentLevel < 4 && hasInfectionSource) {
            float envMultiplier = getEnvironmentMultiplier(player);
            int baseInterval = MedicalConfig.SIRS_PROGRESSION_INTERVAL.get();
            int progressionInterval = Math.max(1, (int) (baseInterval * envMultiplier));

            int timer = data.getSirsProgressionTimer() + 1;
            data.setSirsProgressionTimer(timer);

            if (timer >= progressionInterval) {
                data.setSirsLevel(currentLevel + 1);
                data.setSirsProgressionTimer(0);
                // 晋级后重置当前等级，继续后续逻辑
                currentLevel = data.getSirsLevel();
            }
        } else if (!hasInfectionSource) {
            // 感染源消失，重置晋级计时器，防止下次感染时继承旧计时
            data.setSirsProgressionTimer(0);
        }

        // 4. SIRS 降级（感染值降到恢复门槛以下时开始降级）
        boolean canRecover = maxLimbInfection < MedicalConfig.SIRS_RECOVERY_LIMB_THRESHOLD.get()
                && totalInfection < MedicalConfig.SIRS_RECOVERY_TOTAL_THRESHOLD.get();

        if (currentLevel > 0 && canRecover) {
            // 根据当前 SIRS 等级选择降级间隔
            int recoveryInterval = switch (currentLevel) {
                case 4 -> MedicalConfig.SIRS_RECOVERY_INTERVAL_L4_TO_L3.get();
                case 3 -> MedicalConfig.SIRS_RECOVERY_INTERVAL_L3_TO_L2.get();
                case 2 -> MedicalConfig.SIRS_RECOVERY_INTERVAL_L2_TO_L1.get();
                case 1 -> MedicalConfig.SIRS_RECOVERY_INTERVAL_L1_TO_L0.get();
                default -> 180;
            };

            int timer = data.getSirsRecoveryTimer() + 1;
            data.setSirsRecoveryTimer(timer);

            if (timer >= recoveryInterval) {
                int nextLevel = currentLevel - 1;
                data.setSirsLevel(nextLevel);
                data.setSirsRecoveryTimer(0);

                if (nextLevel == 0) {
                    data.setSirsActive(false);
                    data.setSirsProgressionTimer(0);
                }
            }
        } else {
            // 感染值仍高于恢复门槛，重置降级计时器
            data.setSirsRecoveryTimer(0);
        }
    }

    private float getEnvironmentMultiplier(Player player) {
        if (player == null) return MedicalConfig.SIRS_ENV_NORMAL.get().floatValue();

        if (player.isUnderWater()) {
            return MedicalConfig.SIRS_ENV_WATER.get().floatValue();
        }
        if (player.level().dimension().equals(Level.NETHER) || player.level().dimension().equals(Level.END)) {
            return MedicalConfig.SIRS_ENV_NETHER.get().floatValue();
        }
        if (player.getY() < 40) {
            return MedicalConfig.SIRS_ENV_CAVE.get().floatValue();
        }
        if (player.level().isRainingAt(player.blockPosition())) {
            return MedicalConfig.SIRS_ENV_RAIN.get().floatValue();
        }
        if (!player.level().canSeeSky(player.blockPosition())) {
            return MedicalConfig.SIRS_ENV_INDOOR.get().floatValue();
        }
        return MedicalConfig.SIRS_ENV_NORMAL.get().floatValue();
    }
}
