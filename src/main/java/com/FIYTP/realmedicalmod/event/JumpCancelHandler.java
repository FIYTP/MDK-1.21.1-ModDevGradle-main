package com.FIYTP.realmedicalmod.event;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.capability.MedicalDataCapability;
import com.FIYTP.realmedicalmod.config.MedicalConfig;
import com.FIYTP.realmedicalmod.treatment.ModItems;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEvent;

/**
 * 跳跃拦截器（客户端 + 服务端双端同步生效版 —— 含 II/III 级阿片戒断行动受限拦截）
 */
@EventBusSubscriber(modid = "realmedicalmod")
public class JumpCancelHandler {

    @SubscribeEvent
    public static void onLivingJump(LivingEvent.LivingJumpEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        MedicalData data = player.getData(MedicalDataCapability.MEDICAL_DATA);
        if (data == null) return;

        // 优先级 1: 意识归零或心脏骤停 -> 双端完全取消跳跃并锁死水平滑行冲量
        if (data.getConsciousness() <= 0 || data.isCardiacArrest()) {
            cancelJumpAndMomentum(player);
            return;
        }

        // 优先级 1.5: II 级 / III 级阿片成瘾未注射阿片（处于戒断行动受限状态）
        if (ModItems.isInWithdrawalMovementRestriction(data)) {
            int tier = ModItems.getAddictionTier(data.getAddictionLevel());
            player.setSprinting(false);
            if (tier >= 3) {
                // III 级重度戒断：完全禁止起跳
                cancelJump(player);
                return;
            } else {
                // II 级中度戒断：大幅削弱跳跃高度与水平冲量
                Vec3 motion = player.getDeltaMovement();
                player.setDeltaMovement(motion.x * 0.5, motion.y * 0.45, motion.z * 0.5);
                return;
            }
        }

        // 优先级 2: 腿部骨折且配置禁止跳跃 -> 取消跳跃
        if (!MedicalConfig.LEG_FRACTURE_JUMP_ALLOWED.get()) {
            MedicalData.LimbData leftLeg = data.getLimb(MedicalData.Limb.LEFT_LEG);
            MedicalData.LimbData rightLeg = data.getLimb(MedicalData.Limb.RIGHT_LEG);
            if ((leftLeg != null && leftLeg.hasFracture())
                    || (rightLeg != null && rightLeg.hasFracture())) {
                cancelJump(player);
                return;
            }
        }

        // 优先级 2.5: 腿部截肢且配置禁止跳跃 -> 取消跳跃
        if (MedicalConfig.AMPUTATION_ENABLED.get() && !MedicalConfig.AMPUTATION_LEG_JUMP_ALLOWED.get()) {
            MedicalData.LimbData leftLeg = data.getLimb(MedicalData.Limb.LEFT_LEG);
            MedicalData.LimbData rightLeg = data.getLimb(MedicalData.Limb.RIGHT_LEG);
            if ((leftLeg != null && leftLeg.isAmputated())
                    || (rightLeg != null && rightLeg.isAmputated())) {
                cancelJump(player);
                return;
            }
        }

        // 优先级 3: 意识 < 80 -> 降低跳跃初速度
        int consciousness = data.getConsciousness();
        if (consciousness < 80) {
            float intensity = (80 - consciousness) / 80.0f;
            float jumpMul = 1.0f - intensity * 0.6f;
            Vec3 motion = player.getDeltaMovement();
            player.setDeltaMovement(motion.x, motion.y * jumpMul, motion.z);
        }
    }

    private static void cancelJump(Player player) {
        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(motion.x, Math.min(0.0, motion.y), motion.z);
        player.hasImpulse = true;
        player.hurtMarked = true;
    }

    private static void cancelJumpAndMomentum(Player player) {
        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(0.0, Math.min(0.0, motion.y), 0.0);
        player.setSprinting(false);
        player.hasImpulse = true;
        player.hurtMarked = true;
    }
}