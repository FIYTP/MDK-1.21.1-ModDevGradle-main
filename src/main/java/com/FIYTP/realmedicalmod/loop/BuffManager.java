package com.FIYTP.realmedicalmod.loop;

import com.FIYTP.realmedicalmod.RealMedicalMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 感知药水表现层（连续插值版）
 *
 * 职责瘦身：只承载无属性可表达的"感知类"效果——
 *   blindness（失明）/ nausea（反胃）/ dig_slowdown（挖掘迟缓）。
 * 移速/攻击力/攻速已全部迁往 AttributeArbiter 连续仲裁。
 *
 * 数值效果模块不再离散查表，而是提交连续强度：
 *   suggest(player, "blindness", level)  level 为插值后的药水等级。
 * 同名多源取 max（疼痛+颅内出血同时触发失明，取更重者）。
 *
 * 滞回（hysteresis）：等级回落需在缓冲 tick 内保持，防边界抖动。
 */
@EventBusSubscriber(modid = RealMedicalMod.MODID)
public class BuffManager {

    public static final BuffManager INSTANCE = new BuffManager();

    /** 感知类药水名 → MobEffect 映射（离散查表时代遗留的 slowness/weakness 已删） */
    private static final Map<String, net.minecraft.core.Holder<MobEffect>> EFFECTS = new HashMap<>();

    static {
        EFFECTS.put("blindness", MobEffects.BLINDNESS);
        EFFECTS.put("nausea", MobEffects.CONFUSION);
        EFFECTS.put("dig_slowdown", MobEffects.DIG_SLOWDOWN);
    }

    // 每玩家：药水名 -> 当前等级（滞回后的实际生效值）
    private final Map<UUID, Map<String, Integer>> applied = new HashMap<>();
    // 每玩家：药水名 -> 本轮建议的最大等级
    private final Map<UUID, Map<String, Integer>> pending = new HashMap<>();
    // 滞回缓冲：药水名 -> 建议值持续 tick 计数（仅记录需保持的候选）
    private final Map<UUID, Map<String, int[]>> hysteresis = new HashMap<>();

    /** 影响评估模块提交连续药水等级建议（每秒循环开头 clear 后收集） */
    public void suggest(ServerPlayer player, String effectName, int level) {
        if (player == null || effectName == null || level <= 0) return;
        pending.computeIfAbsent(player.getUUID(), k -> new HashMap<>())
                .merge(effectName, level, Math::max);
    }

    /** 每秒循环结尾：把 pending 应用到玩家（多源 max + 滞回防抖） */
    public void apply(ServerPlayer player) {
        if (player == null) return;
        UUID id = player.getUUID();

        Map<String, Integer> pendingMap = pending.getOrDefault(id, Map.of());
        Map<String, Integer> appliedMap = applied.computeIfAbsent(id, k -> new HashMap<>());
        Map<String, int[]> hystMap = hysteresis.computeIfAbsent(id, k -> new HashMap<>());

        int bufferTicks = com.FIYTP.realmedicalmod.config.MedicalConfig.POTION_HYSTERESIS_TICKS.get();

        // 1. 清理本轮未再被建议的效果
        for (Map.Entry<String, net.minecraft.core.Holder<MobEffect>> entry : EFFECTS.entrySet()) {
            String name = entry.getKey();
            Integer current = appliedMap.get(name);

            if (!pendingMap.containsKey(name)) {
                // 本轮无建议：若之前在生效，直接移除（状态消失无需滞回）
                if (current != null && current > 0) {
                    removeEffect(player, entry.getValue());
                    appliedMap.put(name, 0);
                    hystMap.remove(name);
                }
                continue;
            }

            int suggested = pendingMap.get(name);

            // 2. 升级立即生效；降级需滞回（在缓冲 tick 内保持较低值才真正回落）
            //    首次建议（current==null）视为 0：suggested>=0 恒真，立即生效
            if (current == null || suggested >= current) {
                setEffect(player, entry.getValue(), suggested);
                appliedMap.put(name, suggested);
                hystMap.remove(name);
            } else {
                int[] counter = hystMap.computeIfAbsent(name,
                        k -> new int[]{suggested, 0});
                if (counter[0] != suggested) {
                    // 新的更低建议值：重置计数
                    counter[0] = suggested;
                    counter[1] = 0;
                }
                counter[1] += 20; // 每秒循环 tick 步进
                if (counter[1] >= bufferTicks) {
                    setEffect(player, entry.getValue(), suggested);
                    appliedMap.put(name, suggested);
                    hystMap.remove(name);
                }
            }
        }

        pending.put(id, new HashMap<>()); // 清空本轮建议
    }

    private void setEffect(ServerPlayer player, net.minecraft.core.Holder<MobEffect> effect, int level) {
        // 感知类效果短保持：120 tick（6s）滚动刷新，避免残留
        player.addEffect(new MobEffectInstance(effect, 120, level - 1, false, false, false));
    }

    private void removeEffect(ServerPlayer player, net.minecraft.core.Holder<MobEffect> effect) {
        player.removeEffect(effect);
    }

    /** 每秒循环开头调用：清空某玩家本轮全部建议（随后由各影响评估模块重新提交） */
    public void clear(ServerPlayer player) {
        if (player == null) return;
        pending.remove(player.getUUID());
    }

    /** 登出/死亡清理（防药水残留跨会话） */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            INSTANCE.applied.remove(sp.getUUID());
            INSTANCE.pending.remove(sp.getUUID());
            INSTANCE.hysteresis.remove(sp.getUUID());
        }
    }

    @SubscribeEvent
    public static void onDeath(PlayerEvent.PlayerLoggedOutEvent event) {
        // 占位：死亡清理由 respawn 时全量重算自然覆盖
    }
}
