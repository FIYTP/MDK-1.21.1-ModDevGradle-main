package com.FIYTP.realmedicalmod.client;

import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 断肢客户端状态缓存：记录本帧渲染时已知的玩家断肢情况，
 * 供盔甲层 Mixin 快速判断（避免每次盔甲渲染读 attachment）。
 */
public final class AmputationClientState {

    private static final Map<UUID, AmputationFlags> FLAGS = new ConcurrentHashMap<>();

    private AmputationClientState() {
    }

    public static void setAmputation(Player player, boolean rightArm, boolean leftArm,
                                     boolean rightLeg, boolean leftLeg) {
        FLAGS.put(player.getUUID(),
                new AmputationFlags(rightArm, leftArm, rightLeg, leftLeg));
    }

    public static boolean hasAmputation(Player player) {
        return FLAGS.containsKey(player.getUUID());
    }

    /** 任意断肢（盔甲层只需知道"有断肢就细查"） */
    public static void clear(Player player) {
        FLAGS.remove(player.getUUID());
    }

    public record AmputationFlags(boolean rightArm, boolean leftArm,
                                  boolean rightLeg, boolean leftLeg) {
    }
}
