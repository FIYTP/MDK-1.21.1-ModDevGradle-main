package com.FIYTP.realmedicalmod.loop;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import net.minecraft.server.level.ServerPlayer;

/**
 * 意识感知模块（表现层，瘦身后）
 *
 * 数值压制（weakness/dig_slowdown 255 硬压制）已废除：
 * 意识→攻击/攻速/挖掘的衰减全部走 SystemicEffectModule 的属性仲裁。
 * 颅内出血→失明已迁往 OrganEffectModule。
 *
 * 本模块只保留意识本身的感知表现：
 *   c < 30 → 反胃插值（意识模糊的眩晕恶心感）
 *   c < 15 → 失明插值（濒临昏迷视野收窄变黑）
 */
public class ConsciousnessSuggestionModule implements UpdateModule {

    @Override
    public void process(MedicalData data) {
        // 无 player 时的兜底
    }

    public void process(ServerPlayer player, MedicalData data, BuffManager buffManager) {
        if (player == null || data == null || buffManager == null) return;

        float c = data.getConsciousness();

        // 濒临昏迷（c<15）：视野收窄变黑，插值 1~2 级失明
        if (c < 15.0f) {
            // 15→1 级，8→2 级，0→2 级封顶
            int level = (c >= 8.0f) ? 1 : 2;
            buffManager.suggest(player, "blindness", level);
        }

        // 意识模糊（c<30）：眩晕恶心，插值反胃
        if (c < 30.0f) {
            int level = (c >= 15.0f) ? 1 : 2;
            buffManager.suggest(player, "nausea", level);
        }
    }
}
