package com.FIYTP.realmedicalmod.hitbox;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 纯 3D 物理几何攻击部位判定器 (HitPartDetector)
 *
 * 零随机抽签、100% 物理空间射线与 LimbHitbox 局部碰撞箱求交：
 * 1. 远程弹射物/箭矢：严格沿弹射物真实 3D 飞行速度射线与受害者 6 部位 AABB 求最近交点；
 * 2. 玩家 PvP 近战：严格沿攻击玩家准星射线求交（瞄腿中腿、瞄手中手、瞄胸中胸、瞄头中头）；
 * 3. 非玩家怪物近战：不使用怪物死盯玩家眼睛的 LookControl 视线，而是从怪物真实物理手爪/躯干攻击高度
 *    (attacker.y + attacker.bbHeight * 0.62) 沿真实水平方位向量射向受害者身体表面——
 *    矮怪（蜘蛛/小僵尸）或处于低位时物理命中双腿；平地正面命中胸腔；侧袭先命中对应侧手臂；高位下劈自然命中头部！
 */
public class HitPartDetector {

    public static MedicalData.Limb detectHitPart(LivingEntity attacker, LivingEntity victim, DamageSource damageSource) {
        if (!(victim instanceof Player player)) {
            return MedicalData.Limb.CHEST;
        }

        Entity direct = (damageSource != null) ? damageSource.getDirectEntity() : null;
        boolean isIndependentProjectile = (direct != null && direct != attacker);

        if (attacker == null && !isIndependentProjectile) {
            return MedicalData.Limb.CHEST;
        }

        Vec3 worldStart;
        Vec3 worldEnd;

        if (isIndependentProjectile) {
            // 1. 远程弹射物（弓箭/弩箭/三叉戟等）：沿真实飞行轨迹
            Vec3 vel = direct.getDeltaMovement();
            if (vel.lengthSqr() > 1.0E-4) {
                Vec3 dir = vel.normalize();
                worldStart = direct.position().subtract(dir.scale(1.2));
                worldEnd = direct.position().add(dir.scale(4.5));
            } else if (attacker != null) {
                worldStart = attacker.getEyePosition(1.0f);
                worldEnd = worldStart.add(attacker.getLookAngle().normalize().scale(Math.max(4.5, worldStart.distanceTo(player.position()) + 2.5)));
            } else {
                return MedicalData.Limb.CHEST;
            }
        } else if (attacker instanceof Player) {
            // 2. 玩家 PvP 近战：严格沿攻击玩家的真实准星射线
            worldStart = attacker.getEyePosition(1.0f);
            double dist = Math.max(4.5, worldStart.distanceTo(player.position()) + 2.5);
            worldEnd = worldStart.add(attacker.getLookAngle().normalize().scale(dist));
        } else {
            // 3. 非玩家怪物近战：从怪物物理手爪攻击高度沿真实空间连线指向受害者对应高度（不走死盯眼睛的 LookControl）
            double mobStrikeY = attacker.getY() + attacker.getBbHeight() * 0.62;
            worldStart = new Vec3(attacker.getX(), mobStrikeY, attacker.getZ());

            // 若怪物与玩家存在高度差（如台阶高低位、跳跃、蜘蛛矮身），保留真实垂向落差；
            // 目标点高度限制在受害者物理包围盒 [minY+0.15, maxY-0.10] 内，形成自然的物理攻击矢量
            double clampedTargetY = Math.max(player.getY() + 0.15, Math.min(player.getY() + player.getBbHeight() - 0.10, mobStrikeY));
            Vec3 targetPt = new Vec3(player.getX(), clampedTargetY, player.getZ());
            Vec3 strikeDir = targetPt.subtract(worldStart);
            if (strikeDir.lengthSqr() < 1.0E-5) {
                return MedicalData.Limb.CHEST;
            }
            worldEnd = worldStart.add(strikeDir.normalize().scale(strikeDir.length() + 1.5));
        }

        // 将世界射线精确变换至受害者偏航局部空间（脚部为原点，已对齐身体朝向）
        Vec3 localStart = CoordinateTransformer.worldToLocal(player, worldStart);
        Vec3 localEnd = CoordinateTransformer.worldToLocal(player, worldEnd);

        boolean isSneaking = player.isShiftKeyDown();
        MedicalData.Limb bestLimb = null;
        double closestDistSq = Double.MAX_VALUE;

        for (MedicalData.Limb limb : MedicalData.Limb.values()) {
            AABB box = LimbHitbox.getLimbAABB(limb, isSneaking);
            var hitOpt = box.clip(localStart, localEnd);
            if (hitOpt.isPresent()) {
                double dSq = localStart.distanceToSqr(hitOpt.get());
                if (dSq < closestDistSq) {
                    closestDistSq = dSq;
                    bestLimb = limb;
                }
            }
        }

        if (bestLimb != null) {
            return bestLimb;
        }

        // 射线擦边兜底：按穿过受害者中心平面时的真实局部高度与左右偏移判定
        Vec3 localDir = localEnd.subtract(localStart);
        double horizSq = localDir.x * localDir.x + localDir.z * localDir.z;
        if (horizSq > 1.0E-5) {
            double t = (-localStart.x * localDir.x - localStart.z * localDir.z) / horizSq;
            t = Math.max(0.0, Math.min(1.0, t));
            double hitY = localStart.y + localDir.y * t;
            double hitX = localStart.x + localDir.x * t;
            double yScale = isSneaking ? 0.83 : 1.0;
            if (hitY >= 1.40 * yScale) return MedicalData.Limb.HEAD;
            if (hitY < 0.72 * yScale) return hitX < 0.0 ? MedicalData.Limb.LEFT_LEG : MedicalData.Limb.RIGHT_LEG;
            if (hitX < -0.24) return MedicalData.Limb.LEFT_ARM;
            if (hitX > 0.24) return MedicalData.Limb.RIGHT_ARM;
        }
        return MedicalData.Limb.CHEST;
    }
}
