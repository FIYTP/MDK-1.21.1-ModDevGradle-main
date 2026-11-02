package com.FIYTP.realmedicalmod.loop;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.config.MedicalConfig;
import net.minecraft.server.level.ServerPlayer;

/**
 * 中毒影响评估模块 (ToxinEffectModule)
 * 影响评估层：把毒素条翻译成感知类药水建议（反胃插值）+ 调用重度症状处理。
 * 能力压制（移速/攻速等）不在此处——毒素对血压/血氧的生理影响
 * 经由各系统模块传导后，由 SystemicEffectModule 的缺氧/血压通路自然接管，
 * 避免"直接项 + 传导项"双重计费。
 */
public class ToxinEffectModule {

    private final ToxinModule toxinModule;

    public ToxinEffectModule(ToxinModule toxinModule) {
        this.toxinModule = toxinModule;
    }

    public void process(ServerPlayer player, MedicalData data, BuffManager buffManager) {
        float toxin = data.getToxinValue();
        if (toxin <= 0.0f) return;

        float mildThreshold = MedicalConfig.TOXIN_MILD_THRESHOLD.get().floatValue();   // 20
        float severeThreshold = MedicalConfig.TOXIN_SEVERE_THRESHOLD.get().floatValue(); // 70

        // 轻度：反胃药水连续插值（斜率可配，毒素 100 时约 3~4 级）
        if (toxin >= mildThreshold) {
            float slope = MedicalConfig.TOXIN_NAUSEA_SLOPE.get().floatValue(); // 默认 0.05/点
            int level = (int) Math.floor(slope * (toxin - mildThreshold)) + 1;
            level = Math.min(4, Math.max(1, level));
            buffManager.suggest(player, "nausea", level);
        }

        // 重度：感染抬升 + 室颤概率（数值逻辑在 ToxinModule.processSevere）
        if (toxin >= severeThreshold) {
            toxinModule.processSevere(player, data);
        }
    }
}
