package com.FIYTP.realmedicalmod.hitbox;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class HitPartDetector {

    public static MedicalData.Limb detectHitPart(LivingEntity attacker, LivingEntity victim, DamageSource damageSource) {
        // attacker 为 null（环境伤害）→ 直接返回胸部，不做射线检测
        if (attacker == null) {
            return MedicalData.Limb.CHEST;
        }

        if (!(victim instanceof Player player)) {
            return MedicalData.Limb.CHEST;
        }

        Vec3 rayStart = getRayStart(attacker, victim);
        Vec3 rayEnd = getRayEnd(attacker, victim);
        Vec3 rayDirection = rayEnd.subtract(rayStart).normalize();
        double maxDistance = rayStart.distanceTo(rayEnd);

        Vec3 feetPos = player.getPosition(1.0f);
        boolean isSneaking = player.isShiftKeyDown();

        MedicalData.Limb hitLimb = null;
        double closestDistance = Double.MAX_VALUE;

        for (MedicalData.Limb limb : MedicalData.Limb.values()) {
            AABB localAABB = LimbHitbox.getLimbAABB(limb, isSneaking);
            AABB worldAABB = localAABB.move(feetPos.x, feetPos.y, feetPos.z);
            Vec3 hitPos = intersectRayWithAABB(rayStart, rayDirection, maxDistance, worldAABB);

            if (hitPos != null) {
                double distance = rayStart.distanceToSqr(hitPos);
                if (distance < closestDistance) {
                    closestDistance = distance;
                    hitLimb = limb;
                }
            }
        }

        return hitLimb != null ? hitLimb : MedicalData.Limb.CHEST;
    }

    private static Vec3 getRayStart(LivingEntity attacker, LivingEntity victim) {
        if (attacker != null) {
            return attacker.getEyePosition(1.0f);
        } else {
            return victim.getEyePosition(1.0f).add(0, 2, 0);
        }
    }

    private static Vec3 getRayEnd(LivingEntity attacker, LivingEntity victim) {
        return victim.getPosition(1.0f).add(0, 0.9, 0);
    }

    private static Vec3 intersectRayWithAABB(Vec3 origin, Vec3 direction, double maxLength, AABB aabb) {
        return aabb.clip(origin, origin.add(direction.scale(maxLength))).orElse(null);
    }
}