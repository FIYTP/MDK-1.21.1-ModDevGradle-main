package com.FIYTP.realmedicalmod.hitbox;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * 坐标转换工具
 * 将世界坐标转换为玩家局部坐标（相对于脚部位置，并考虑旋转）
 */
public class CoordinateTransformer {

    /**
     * 将世界坐标转换为玩家局部坐标
     * @param player 玩家实体
     * @param worldPos 世界坐标
     * @return 局部坐标（相对于玩家脚部位置，未考虑俯仰，仅偏航）
     */
    public static Vec3 worldToLocal(Player player, Vec3 worldPos) {
        // 1. 获取玩家脚部位置
        Vec3 feetPos = player.getPosition(1.0f);

        // 2. 计算相对于脚部的偏移
        Vec3 relative = worldPos.subtract(feetPos);

        // 3. 获取玩家的偏航角（yaw）
        float yaw = player.getYRot();

        // 4. 应用旋转（将世界坐标转换到玩家朝向的局部坐标）
        // 注意：我们需要反转旋转方向，因为我们要从世界坐标系转换到玩家局部坐标系
        double radYaw = Math.toRadians(-yaw);
        double cosYaw = Math.cos(radYaw);
        double sinYaw = Math.sin(radYaw);

        double x = relative.x * cosYaw - relative.z * sinYaw;
        double z = relative.x * sinYaw + relative.z * cosYaw;

        // 俯仰对水平偏移影响不大，忽略或简化处理
        return new Vec3(x, relative.y, z);
    }

    /**
     * 获取玩家脚部位置
     */
    public static Vec3 getFeetPos(Player player) {
        return player.getPosition(1.0f);
    }
}