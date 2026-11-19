package com.FIYTP.realmedicalmod.hitbox;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import net.minecraft.world.phys.AABB;

/**
 * 玩家肢体精确局部碰撞箱定义 (LimbHitbox)
 *
 * 坐标系约定：
 * - 原点为玩家脚部中心 (0, 0, 0)，已由 CoordinateTransformer 旋正至玩家朝向局部系：
 *   +Y 向上（站立总高 1.80m，潜行总高 1.50m），±X 为左右侧，±Z 为前后侧。
 * - 修复了此前潜行偏移写反 (+0.7f 向上漂浮) 的错误，严格按真实人体解剖比例切分 6 大肢体。
 */
public class LimbHitbox {

    public static AABB getLimbAABB(MedicalData.Limb limb, boolean isSneaking) {
        // 站立 1.80m，潜行压低至 ~1.50m（压缩比例 0.83）
        float yScale = isSneaking ? 0.83f : 1.0f;

        return switch (limb) {
            case HEAD -> new AABB(
                    -0.25, 1.40 * yScale, -0.25,
                    0.25, 1.82 * yScale, 0.25
            );
            case CHEST -> new AABB(
                    -0.24, 0.72 * yScale, -0.22,
                    0.24, 1.40 * yScale, 0.22
            );
            case LEFT_ARM -> new AABB(
                    -0.48, 0.72 * yScale, -0.20,
                    -0.24, 1.40 * yScale, 0.20
            );
            case RIGHT_ARM -> new AABB(
                    0.24, 0.72 * yScale, -0.20,
                    0.48, 1.40 * yScale, 0.20
            );
            case LEFT_LEG -> new AABB(
                    -0.26, 0.0, -0.22,
                    0.0, 0.72 * yScale, 0.22
            );
            case RIGHT_LEG -> new AABB(
                    0.0, 0.0, -0.22,
                    0.26, 0.72 * yScale, 0.22
            );
        };
    }
}
