package com.FIYTP.realmedicalmod.loop;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.config.MedicalConfig;
import com.FIYTP.realmedicalmod.system.PainSystem;
import net.minecraft.world.entity.player.Player;

/**
 * 烧伤模块 (BurnModule)
 * 职责：热力期缓冲转正/冲洗 → 深度定型；渗漏休克（容量→水肿池）；
 *       回吸收（水肿回流+毒素释放）；持续灼痛；创面感染压力；愈合；吸入性损伤消退；
 *       TBSA 缓存维护；高代谢饥饿消耗。
 * 入口：HitPartHandler 累加负荷（原版火焰伤害照常生效）。
 */
public class BurnModule implements UpdateModule {

    // 每肢体每秒疼痛小数累加器（int 制疼痛换算）
    private final float[] painAccumulators = new float[MedicalData.Limb.values().length];

    @Override
    public void process(MedicalData data) {
        process(null, data);
    }

    public void process(Player player, MedicalData data) {
        if (data == null) return;

        boolean wet = isWetEnvironment(player);
        float tbsa = 0.0f;
        boolean anyBurnActive = false;

        // ===== 全肢体遍历：热力期/定型/渗漏/疼痛/感染/愈合 =====
        for (MedicalData.Limb limb : MedicalData.Limb.values()) {
            MedicalData.LimbData ld = data.getLimb(limb);
            if (ld == null) continue;

            // -- 0. 无任何烧伤痕迹则跳过 --
            if (!ld.hasBurn()) continue;
            anyBurnActive = true;

            // -- 1. 热力期：窗口内可冲洗，负荷每秒转正 --
            if (ld.getBurnHeatTimer() > 0) {
                ld.setBurnHeatTimer(ld.getBurnHeatTimer() - 1);

                if (wet) {
                    // 浸水/雨：冲刷未转正负荷（阻止加深）
                    float remaining = Math.max(0.0f, ld.getBurnLoad() - MedicalConfig.BURN_IRRIGATION_RATE.get().floatValue());
                    ld.setBurnLoad(remaining);
                }
            }

            // 转正：每秒 conv += rate（从 load 借出）
            if (ld.getBurnLoad() > 0.0f) {
                float convRate = MedicalConfig.BURN_CONVERSION_RATE.get().floatValue();
                float take = Math.min(ld.getBurnLoad(), convRate);
                ld.setBurnLoad(ld.getBurnLoad() - take);
                ld.setBurnConv(ld.getBurnConv() + take);
            }

            // 定型：转正负荷达阈值即升级（不可逆）
            if (ld.getBurnConv() > 0.0f) {
                int newDegree = ld.getBurnDegree();
                if (ld.getBurnConv() >= MedicalConfig.BURN_DEGREE_III.get().floatValue()) {
                    newDegree = 4;
                } else if (ld.getBurnConv() >= MedicalConfig.BURN_DEGREE_DEEP_II.get().floatValue()) {
                    newDegree = Math.max(newDegree, 3);
                } else if (ld.getBurnConv() >= MedicalConfig.BURN_DEGREE_SHALLOW_II.get().floatValue()) {
                    newDegree = Math.max(newDegree, 2);
                } else if (ld.getBurnConv() >= MedicalConfig.BURN_DEGREE_I.get().floatValue()) {
                    newDegree = Math.max(newDegree, 1);
                }
                if (newDegree != ld.getBurnDegree()) {
                    ld.setBurnDegree(newDegree);
                    // 首次定型：创面成为开放创口（进感染链）；伤龄起算
                    ld.setOpenWound(true);
                }
            }

            // -- 2. 伤龄推进（定型后才开始计病程） --
            if (ld.getBurnDegree() > 0) {
                ld.setBurnAge(ld.getBurnAge() + 1);
            }

            // -- 3. 渗漏休克：伤龄在渗漏窗内按峰值曲线流失容量 → 水肿池 --
            if (ld.getBurnDegree() >= 2) {
                int age = ld.getBurnAge();
                int duration = MedicalConfig.BURN_LEAK_DURATION.get();
                if (age < duration) {
                    float leakRate = leakShape(age, duration) * MedicalConfig.BURN_LEAK_BASE.get().floatValue();
                    if (leakRate > 0.0f) {
                        // 深度系数：浅II=1.0 / 深II=1.2 / III=0.8（焦痂护壳）
                        float depthMult = switch (ld.getBurnDegree()) {
                            case 2 -> 1.0f;
                            case 3 -> 1.2f;
                            default -> 0.8f;
                        };
                        // 该肢体渗漏 = 面积 × 部位权重 × 基率 × shape × 深度系数
                        float weight = tbsaWeight(limb);
                        float leakMl = ld.getBurnArea() * weight * leakRate * depthMult;
                        if (leakMl > 0.0f) {
                            int loss = Math.round(leakMl);
                            int current = data.getBloodVolume();
                            data.setBloodVolume(Math.max(0, current - loss));
                            data.setBurnEdema(data.getBurnEdema() + loss); // 渗出进水肿池（回吸收期还）
                        }
                    }
                }
            }

            // -- 4. 持续灼痛：深度表 ×(0.3+0.7×面积) ×包扎倍率，小数累加器进位 --
            if (ld.getBurnDegree() > 0 && ld.getBurnHeatTimer() == 0) {
                float painPerSec = burnPainPerSec(ld.getBurnDegree())
                        * (0.3f + 0.7f * ld.getBurnArea());
                if (ld.isBandaged()) painPerSec *= MedicalConfig.BURN_PAIN_MULT_BANDAGED.get().floatValue();

                int idx = limb.ordinal();
                painAccumulators[idx] += painPerSec * 0.4f; // 缩放：10级疼痛制下的贡献率
                if (painAccumulators[idx] >= 1.0f) {
                    int gain = (int) painAccumulators[idx];
                    painAccumulators[idx] -= gain;
                    int newPain = PainSystem.applyPain(ld.getPainLevel(), ld.getPainLevel() + gain);
                    ld.setPainLevel(Math.min(10, newPain));
                }
            }

            // -- 5. 感染压力：焦痂溶解期×3，包扎×0.3；经累加器喂给感染链 --
            if (ld.getBurnDegree() >= 2) {
                float pressure = (ld.getBurnDegree() - 1) * ld.getBurnArea()
                        * MedicalConfig.BURN_INFECTION_RATE.get().floatValue();
                int age = ld.getBurnAge();
                if (age >= MedicalConfig.BURN_ESCHAR_PHASE_START.get() && age <= MedicalConfig.BURN_ESCHAR_PHASE_END.get()) {
                    pressure *= 3.0f; // 焦痂溶解爆发期
                }
                if (ld.isBandaged()) pressure *= MedicalConfig.BURN_INFECTION_MULT_BANDAGED.get().floatValue();

                // 喂给局部感染累加器（LocalInfectionModule 消费：烧伤创面=开放创口）
                float accum = ld.getInfectionAccumulator() + pressure;
                if (accum >= 1.0f) {
                    int inc = (int) accum;
                    accum -= inc;
                    if (ld.getInfection() < 100) {
                        ld.setInfection(Math.min(100, ld.getInfection() + inc));
                    }
                }
                ld.setInfectionAccumulator(accum);
            }

            // -- 6. 愈合：healTimer 累计达时长即痊愈（III度永不自愈） --
            if (ld.getBurnDegree() > 0 && ld.getBurnDegree() < 4 && ld.getBurnAge() > MedicalConfig.BURN_HEAT_WINDOW.get()) {
                int required = switch (ld.getBurnDegree()) {
                    case 1 -> MedicalConfig.BURN_HEAL_TIME_I.get();
                    case 2 -> MedicalConfig.BURN_HEAL_TIME_SHALLOW_II.get();
                    default -> MedicalConfig.BURN_HEAL_TIME_DEEP_II.get();
                };
                int progress = ld.getBurnHealTimer() + (ld.isBandaged()
                        ? Math.round(MedicalConfig.BURN_HEAL_MULT_BANDAGED.get().floatValue()) : 1);
                if (progress >= required) {
                    // 痊愈：清全部烧伤字段，关创口
                    ld.setBurnDegree(0);
                    ld.setBurnArea(0.0f);
                    ld.setBurnLoad(0.0f);
                    ld.setBurnConv(0.0f);
                    ld.setBurnAge(0);
                    ld.setBurnHealTimer(0);
                    ld.setOpenWound(false);
                } else {
                    ld.setBurnHealTimer(progress);
                }
            }

            // -- 7. TBSA 汇总（II度及以上计入） --
            if (ld.getBurnDegree() >= 2) {
                tbsa += tbsaWeight(limb) * ld.getBurnArea();
            }
        }

        data.setBurnTBSA(tbsa);

        // ===== 全局：吸入性损伤消退 / 回吸收期 =====
        if (anyBurnActive) {
            // 吸入性损伤：脱离火源（热力期已过）后缓慢消退
            MedicalData.LimbData headData = data.getLimb(MedicalData.Limb.HEAD);
            boolean headHot = headData != null && headData.getBurnHeatTimer() > 0;
            float inhalation = data.getInhalationInjury();
            if (inhalation > 0.0f && !headHot && (headData == null || headData.getBurnAge() > MedicalConfig.BURN_HEAT_WINDOW.get())) {
                data.setInhalationInjury(Math.max(0.0f, inhalation - MedicalConfig.BURN_INHALATION_DECAY.get().floatValue()));
            }

            // 回吸收期：水肿回流 + 毒素分摊释放（用 HEAD 伤龄近似全局病程——水肿按全身池管理）
            int refAge = headData != null ? headData.getBurnAge() : 0;
            // 用最大伤龄更合理：取各肢体最大
            int maxAge = 0;
            for (MedicalData.Limb limb : MedicalData.Limb.values()) {
                MedicalData.LimbData ld = data.getLimb(limb);
                if (ld != null && ld.getBurnDegree() >= 2) {
                    maxAge = Math.max(maxAge, ld.getBurnAge());
                }
            }
            refAge = maxAge;
            if (refAge == 0) refAge = 0; // 无活跃创面则跳过

            int returnStart = MedicalConfig.BURN_EDEMA_RETURN_START.get();
            int returnEnd = MedicalConfig.BURN_EDEMA_RETURN_END.get();
            if (refAge > 0 && refAge >= returnStart && refAge <= returnEnd) {
                float window = Math.max(1, returnEnd - returnStart);
                float share = 1.0f / window;

                // 水肿回流：池内液体回血（每秒回 1/window 份额）
                float edema = data.getBurnEdema();
                if (edema > 0.0f) {
                    float returnMl = Math.min(edema, edema * share);
                    data.setBurnEdema(edema - returnMl);
                    int current = data.getBloodVolume();
                    data.setBloodVolume(Math.min(data.getMaxBloodVolume(), current + Math.round(returnMl)));
                }

                // 毒素释放：总量 = TBSA × toxinRelease，分摊在窗口内
                float toxinShare = tbsa * MedicalConfig.BURN_TOXIN_RELEASE.get().floatValue() * share;
                if (toxinShare > 0.0f) {
                    data.addToxinValue(toxinShare);
                }
            }

            // 高代谢：饥饿额外消耗（burnTBSA>0 时按斜率加耗）——走原版饱和度消耗
            if (player != null && tbsa > 0.0f && !player.getAbilities().instabuild) {
                float metabolismMult = 1.0f + tbsa * MedicalConfig.BURN_METABOLISM_SLOPE.get().floatValue();
                // 原版每秒基础消耗约 0.01 饱和度（行走~0.01/s）；烧伤额外部分按倍率加耗
                float extraExhaustion = 0.01f * (metabolismMult - 1.0f);
                if (extraExhaustion > 0.0f) {
                    player.causeFoodExhaustion(extraExhaustion);
                }
            }
        } else {
            // 无烧伤：清全局残留
            if (data.getBurnTBSA() != 0.0f) data.setBurnTBSA(0.0f);
            if (data.getBurnEdema() != 0.0f) data.setBurnEdema(0.0f);
            float inhalation = data.getInhalationInjury();
            if (inhalation > 0.0f) {
                data.setInhalationInjury(Math.max(0.0f, inhalation - MedicalConfig.BURN_INHALATION_DECAY.get().floatValue()));
            }
        }
    }

    /** 渗漏 shape：0 → 峰值(leakPeakTime) → 0，三角峰，全程 leakDuration */
    private float leakShape(int age, int duration) {
        int peak = MedicalConfig.BURN_LEAK_PEAK_TIME.get();
        if (age >= duration) return 0.0f;
        if (age <= peak) {
            return peak > 0 ? (float) age / peak : 1.0f;
        }
        int falling = duration - peak;
        return falling > 0 ? (float) (duration - age) / falling : 0.0f;
    }

    /** 深度→基础疼痛输出（每秒） */
    private float burnPainPerSec(int degree) {
        return switch (degree) {
            case 1 -> MedicalConfig.BURN_PAIN_I.get().floatValue();
            case 2 -> MedicalConfig.BURN_PAIN_SHALLOW_II.get().floatValue();
            case 3 -> MedicalConfig.BURN_PAIN_DEEP_II.get().floatValue();
            default -> MedicalConfig.BURN_PAIN_III.get().floatValue();
        };
    }

    /** 肢体→TBSA 权重 */
    private float tbsaWeight(MedicalData.Limb limb) {
        return switch (limb) {
            case HEAD -> MedicalConfig.BURN_TBSA_WEIGHT_HEAD.get().floatValue();
            case CHEST -> MedicalConfig.BURN_TBSA_WEIGHT_CHEST.get().floatValue();
            case LEFT_ARM, RIGHT_ARM -> MedicalConfig.BURN_TBSA_WEIGHT_ARM.get().floatValue();
            case LEFT_LEG, RIGHT_LEG -> MedicalConfig.BURN_TBSA_WEIGHT_LEG.get().floatValue();
        };
    }

    /** 浸水/雨判定（与 LocalInfectionModule 同口径） */
    private boolean isWetEnvironment(Player player) {
        if (player == null) return false;
        if (player.isInWaterOrBubble()) return true;
        return player.level().isRainingAt(player.blockPosition());
    }
}
