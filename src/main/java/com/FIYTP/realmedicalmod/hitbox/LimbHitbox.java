package com.FIYTP.realmedicalmod.hitbox;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import net.minecraft.world.phys.AABB;

/**
 * 玩家肢体碰撞箱定义
 * 所有坐标相对于玩家的脚部位置（Y=0）
 */
public class LimbHitbox {

    // 玩家站立时的默认高度
    private static final float PLAYER_HEIGHT = 1.8f;
    private static final float PLAYER_WIDTH = 0.6f;

    /**
     * 获取指定肢体的 AABB（相对于玩家脚部）
     */
    public static AABB getLimbAABB(MedicalData.Limb limb, boolean isSneaking) {
        float sneakOffset = isSneaking ? 0.7f : 0.0f;

        return switch (limb) {
            case HEAD -> getHeadAABB(sneakOffset);
            case CHEST -> getChestAABB(sneakOffset);
            case LEFT_ARM -> getLeftArmAABB(sneakOffset);
            case RIGHT_ARM -> getRightArmAABB(sneakOffset);
            case LEFT_LEG -> getLeftLegAABB(sneakOffset);
            case RIGHT_LEG -> getRightLegAABB(sneakOffset);
        };
    }

    // 头部：位于躯干上方
    private static AABB getHeadAABB(float sneakOffset) {
        float yOffset = 1.2f + sneakOffset;
        return new AABB(
                -0.25, yOffset, -0.25,
                0.25, yOffset + 0.35, 0.25
        );
    }

    // 胸部：躯干主体
    private static AABB getChestAABB(float sneakOffset) {
        float yOffset = 0.4f + sneakOffset;
        return new AABB(
                -0.35, yOffset, -0.25,
                0.35, yOffset + 0.8, 0.25
        );
    }

    // 左臂
    private static AABB getLeftArmAABB(float sneakOffset) {
        float yOffset = 0.5f + sneakOffset;
        return new AABB(
                -0.55, yOffset, -0.2,
                -0.35, yOffset + 0.7, 0.2
        );
    }

    // 右臂
    private static AABB getRightArmAABB(float sneakOffset) {
        float yOffset = 0.5f + sneakOffset;
        return new AABB(
                0.35, yOffset, -0.2,
                0.55, yOffset + 0.7, 0.2
        );
    }

    // 左腿
    private static AABB getLeftLegAABB(float sneakOffset) {
        float yOffset = 0.0f + sneakOffset;
        return new AABB(
                -0.3, yOffset, -0.2,
                -0.1, yOffset + 0.4, 0.2
        );
    }

    // 右腿
    private static AABB getRightLegAABB(float sneakOffset) {
        float yOffset = 0.0f + sneakOffset;
        return new AABB(
                0.1, yOffset, -0.2,
                0.3, yOffset + 0.4, 0.2
        );
    }
}