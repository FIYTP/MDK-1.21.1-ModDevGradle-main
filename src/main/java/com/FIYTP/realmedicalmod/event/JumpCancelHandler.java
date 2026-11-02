package com.FIYTP.realmedicalmod.event;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.capability.MedicalDataCapability;
import com.FIYTP.realmedicalmod.config.MedicalConfig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEvent;

@EventBusSubscriber(modid = "realmedicalmod")
public class JumpCancelHandler {

    @SubscribeEvent
    public static void onLivingJump(LivingEvent.LivingJumpEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;

        MedicalData data = player.getData(MedicalDataCapability.MEDICAL_DATA);
        if (data == null) return;

        // 优先级 1：意识归零或心脏骤停 → 完全取消跳跃
        if (data.getConsciousness() <= 0 || data.isCardiacArrest()) {
            cancelJump(player);
            return;
        }

        // 优先级 2：腿部骨折且配置禁止跳跃 → 取消
        if (!MedicalConfig.LEG_FRACTURE_JUMP_ALLOWED.get()) {
            MedicalData.LimbData leftLeg = data.getLimb(MedicalData.Limb.LEFT_LEG);
            MedicalData.LimbData rightLeg = data.getLimb(MedicalData.Limb.RIGHT_LEG);
            if ((leftLeg != null && leftLeg.hasFracture())
                    || (rightLeg != null && rightLeg.hasFracture())) {
                cancelJump(player);
                return;
            }
        }

        // 优先级 2.5：腿部截肢且配置禁止跳跃 → 取消（断腿无法起跳）
        if (MedicalConfig.AMPUTATION_ENABLED.get() && !MedicalConfig.AMPUTATION_LEG_JUMP_ALLOWED.get()) {
            MedicalData.LimbData leftLeg = data.getLimb(MedicalData.Limb.LEFT_LEG);
            MedicalData.LimbData rightLeg = data.getLimb(MedicalData.Limb.RIGHT_LEG);
            if ((leftLeg != null && leftLeg.isAmputated())
                    || (rightLeg != null && rightLeg.isAmputated())) {
                cancelJump(player);
                return;
            }
        }

        // 优先级 3：意识 < 80 → 降低跳跃初速度
        int consciousness = data.getConsciousness();
        if (consciousness < 80) {
            float intensity = (80 - consciousness) / 80.0f;
            float jumpMul = 1.0f - intensity * 0.6f; // 1.0 → 0.4
            Vec3 motion = player.getDeltaMovement();
            player.setDeltaMovement(motion.x, motion.y * jumpMul, motion.z);
        }
    }

    private static void cancelJump(Player player) {
        player.setDeltaMovement(
                player.getDeltaMovement().x,
                0,
                player.getDeltaMovement().z
        );
        player.hurtMarked = true;
    }
}
