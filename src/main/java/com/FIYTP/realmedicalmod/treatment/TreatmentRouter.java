package com.FIYTP.realmedicalmod.treatment;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.capability.MedicalDataCapability;
import com.FIYTP.realmedicalmod.capability.MedicalDataImpl;
import com.FIYTP.realmedicalmod.config.MedicalConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通用高扩展医疗用品与阿片类定量镇痛路由器 (TreatmentRouter)
 *
 * 阿片类药物系统（吗啡 Morphine & 芬太尼 Fentanyl）核心机制（全部参数由 {@link MedicalConfig} 驱动）：
 * 1. 定量滑块推注与按量扣除耐久：
 *    每支吗啡 (10.0 mg) 或芬太尼 (100 mcg) 拥有配置的刻度耐久（默认 100 单位）。
 *    玩家通过滑块选择推注剂量（1 ~ 剩余最大刻度），仅扣除本次推注的刻度耐久，剩余药液可留待下次继续推注。
 * 2. 强效双层镇痛（局部创伤痛感直降 + 中枢 μ-阿片受体全身痛觉传导阻断）：
 *    推注后不仅大幅消减目标部位与全身其余部位的疼痛等级，还会在体内建立阿片血药浓度，
 *    在 {@link MedicalDataImpl#getPerceivedPain()} 中强力抑制中枢体感疼痛。
 * 3. 过量呼吸抑制与致命生理后果：
 *    - 芬太尼的单位血药浓度转化率远高于吗啡（治疗窗极窄）。
 *    - 当体内累计阿片血药浓度超过 {@code OPIOID_RESP_DEPRESSION_START} 时出现呼吸抑制；
 *    - 达到 {@code OPIOID_OVERDOSE_THRESHOLD} 时触发重度呼吸抑制、迷走心动过缓与深度昏迷；
 *    - 达到 {@code OPIOID_APNEA_THRESHOLD} 时触发延髓呼吸中枢完全麻痹（呼吸暂停 Apnea，呼吸频率直接归零），
 *      引发血氧断崖暴跌、脑损伤恶化与缺氧性室颤/心脏骤停！
 */
public final class TreatmentRouter {

    private static final int OPEN_WOUND_DRESSING_COST = 50;

    @FunctionalInterface
    public interface LimbTreatmentHandler {
        int apply(ServerPlayer player, MedicalData data, MedicalData.Limb limb,
                  MedicalData.LimbData limbData, ItemStack stack, MedicalPotency potency);
    }

    private static final Map<TagKey<Item>, LimbTreatmentHandler> EFFECT_HANDLERS = new LinkedHashMap<>();

    static {
        // 1. 外出血止血 / 绷带包扎 (HEMOSTASIS)
        registerEffectHandler(MedicalItemTags.HEMOSTASIS, (player, data, limb, ld, stack, potency) -> {
            if (ld.isAmputated()) return 0;
            int oldBleed = ld.getBleeding();
            boolean needDressing = !ld.isBandaged() && (oldBleed > 0 || ld.hasOpenWound() || ld.hasBurn());
            if (oldBleed <= 0 && !needDressing) {
                return 0;
            }

            int maxCap = getMaxHemostasisCapacity(stack, potency);
            int remainingCap = getRemainingHemostasisCapacity(stack, potency);
            if (remainingCap <= 0) {
                return 0;
            }

            if (oldBleed > 0) {
                int absorbed = Math.min(oldBleed, remainingCap);
                int newBleed = Math.max(0, oldBleed - absorbed);
                ld.setBleeding(newBleed);

                if (newBleed == 0) {
                    ld.setBandaged(true);
                    ld.setOpenWound(false);
                    ld.setNaturalClottingTimer(0);
                    player.displayClientMessage(
                            Component.literal("已包扎").withStyle(ChatFormatting.GREEN),
                            true
                    );
                } else {
                    ld.setBandaged(false);
                    player.displayClientMessage(
                            Component.literal("伤口仍在流血").withStyle(ChatFormatting.GOLD),
                            true
                    );
                }
                return Math.max(1, absorbed);
            } else {
                int cost = Math.min(OPEN_WOUND_DRESSING_COST, remainingCap);
                ld.setBandaged(true);
                ld.setOpenWound(false);
                player.displayClientMessage(
                        Component.literal("已包扎").withStyle(ChatFormatting.GREEN),
                        true
                );
                return Math.max(1, cost);
            }
        });

        // 2. 止血带强效阻断 (TOURNIQUET)
        registerEffectHandler(MedicalItemTags.TOURNIQUET, (player, data, limb, ld, stack, potency) -> {
            if (limb == MedicalData.Limb.HEAD || limb == MedicalData.Limb.CHEST || ld.isAmputated()) return 0;
            if (ld.isTourniqueted() && ld.getBleeding() <= 0) return 0;
            ld.setTourniqueted(true);
            ld.setBleeding(0);
            return 1;
        });

        // 3. 内出血止血剂 (INTERNAL_HEMOSTASIS)
        registerEffectHandler(MedicalItemTags.INTERNAL_HEMOSTASIS, (player, data, limb, ld, stack, potency) -> {
            int oldInternal = ld.getInternalBleedingRate();
            if (oldInternal <= 0) return 0;
            float cap = potency.get(MedicalItemTags.INTERNAL_HEMOSTASIS.location(), 150.0f);
            int avail = stack.isDamageableItem()
                    ? Math.max(1, stack.getMaxDamage() - stack.getDamageValue())
                    : Math.round(cap);
            int reduced = Math.min(oldInternal, avail);
            ld.setInternalBleedingRate(Math.max(0, oldInternal - reduced));
            return Math.max(1, reduced);
        });

        // 4. 骨折夹板固定 (SPLINT_EFFECT)
        registerEffectHandler(MedicalItemTags.SPLINT_EFFECT, (player, data, limb, ld, stack, potency) -> {
            if (!ld.hasFracture() || ld.hasSplint() || ld.isAmputated()) return 0;
            ld.setSplint(true);
            return 1;
        });

        // 5. 关节脱臼复位 (REDUCTION)
        registerEffectHandler(MedicalItemTags.REDUCTION, (player, data, limb, ld, stack, potency) -> {
            if (!ld.hasDislocation() || ld.isAmputated()) return 0;
            ld.setDislocation(false);
            return 1;
        });

        // 6. 非阿片类局部/全身止痛 (ANALGESIC)
        registerEffectHandler(MedicalItemTags.ANALGESIC, (player, data, limb, ld, stack, potency) -> {
            if (ld.getPainLevel() <= 0) return 0;
            float relief = potency.get(MedicalItemTags.ANALGESIC.location(), 4.0f);
            int actual = Math.min(ld.getPainLevel(), Math.round(relief));
            ld.setPainLevel(Math.max(0, ld.getPainLevel() - actual));
            return Math.max(1, actual);
        });

        // 7. 阿片类强效镇痛 (OPIOID) —— 默认走完整定量药理处理器
        registerEffectHandler(MedicalItemTags.OPIOID, (player, data, limb, ld, stack, potency) ->
                applyOpioidDose(player, data, limb, ld, stack, 50));

        // 8. 创口消毒 (DISINFECT)
        registerEffectHandler(MedicalItemTags.DISINFECT, (player, data, limb, ld, stack, potency) -> {
            InfectionTreatment.applyDisinfectant(ld);
            float clean = potency.get(MedicalItemTags.DISINFECT.location(), 15.0f);
            ld.setInfection(Math.max(0, ld.getInfection() - Math.round(clean)));
            return Math.max(1, Math.round(clean));
        });

        // 9. 创口清洗 (CLEANSE)
        registerEffectHandler(MedicalItemTags.CLEANSE, (player, data, limb, ld, stack, potency) -> {
            if (ld.getInfection() <= 0 && !ld.hasOpenWound()) return 0;
            float clean = potency.get(MedicalItemTags.CLEANSE.location(), 25.0f);
            ld.setInfection(Math.max(0, ld.getInfection() - Math.round(clean)));
            return Math.max(1, Math.round(clean));
        });

        // 10. 抗生素抑制感染 (ANTIBIOTIC)
        registerEffectHandler(MedicalItemTags.ANTIBIOTIC, (player, data, limb, ld, stack, potency) -> {
            float clean = potency.get(MedicalItemTags.ANTIBIOTIC.location(), 35.0f);
            ld.setInfection(Math.max(0, ld.getInfection() - Math.round(clean)));
            InfectionTreatment.applyDisinfectant(ld);
            return 1;
        });

        // 11. 烧伤药膏/敷料 (BURN_HEAL)
        registerEffectHandler(MedicalItemTags.BURN_HEAL, (player, data, limb, ld, stack, potency) -> {
            if (!ld.hasBurn()) return 0;
            float heal = potency.get(MedicalItemTags.BURN_HEAL.location(), 10.0f);
            ld.setBurnArea(Math.max(0.0f, ld.getBurnArea() - heal));
            ld.setBandaged(true);
            return Math.max(1, Math.round(heal));
        });

        // 12. 补液/输血 (BLOOD_VOLUME)
        registerEffectHandler(MedicalItemTags.BLOOD_VOLUME, (player, data, limb, ld, stack, potency) -> {
            float delta = potency.get(MedicalItemTags.BLOOD_VOLUME.location(), 500.0f);
            int nextVol = Mth.clamp(data.getBloodVolume() + Math.round(delta), 0, data.getMaxBloodVolume());
            if (nextVol == data.getBloodVolume()) return 0;
            int added = Math.abs(nextVol - data.getBloodVolume());
            data.setBloodVolume(nextVol);
            return Math.max(1, added);
        });

        // 13. 意识兴奋剂 (STIMULANT)
        registerEffectHandler(MedicalItemTags.STIMULANT, (player, data, limb, ld, stack, potency) -> {
            float boost = potency.get(MedicalItemTags.STIMULANT.location(), 35.0f);
            data.setConsciousness(Mth.clamp(data.getConsciousness() + Math.round(boost), 0, 100));
            return 1;
        });

        // 14. 除颤复苏 (DEFIBRILLATION)
        registerEffectHandler(MedicalItemTags.DEFIBRILLATION, (player, data, limb, ld, stack, potency) -> {
            if (!data.isInVFib() && !data.isCardiacArrest()) return 0;
            data.setVFib(false);
            data.setCardiacArrest(false);
            data.setHeartRate(85);
            return 1;
        });

        // 15. 血胸引流 (HEMOTHORAX_DRAIN)
        registerEffectHandler(MedicalItemTags.HEMOTHORAX_DRAIN, (player, data, limb, ld, stack, potency) -> {
            if (data.getChestBloodAccumulation() <= 0.1f) return 0;
            float drain = potency.get(MedicalItemTags.HEMOTHORAX_DRAIN.location(), 800.0f);
            data.setChestBloodAccumulation(Math.max(0.0f, data.getChestBloodAccumulation() - drain));
            return 1;
        });
    }

    private TreatmentRouter() {}

    public static void registerEffectHandler(TagKey<Item> effectTag, LimbTreatmentHandler handler) {
        if (effectTag != null && handler != null) {
            EFFECT_HANDLERS.put(effectTag, handler);
        }
    }

    /**
     * 判断物品是否为阿片类镇痛剂（盐酸吗啡 morphine 或 枸橼酸芬太尼 fentanyl）
     */
    public static boolean isOpioidItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.is(MedicalItemTags.OPIOID)) return true;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id != null) {
            String path = id.getPath();
            if (path.contains("morphine") || path.contains("fentanyl")) {
                return true;
            }
        }
        MedicalPotency potency = stack.getOrDefault(MedicalComponents.MEDICAL_POTENCY.get(), MedicalPotency.EMPTY);
        return potency.get(MedicalItemTags.OPIOID.location(), 0.0f) > 0.0f;
    }

    /**
     * 判断物品是否为高敏合成阿片「枸橼酸芬太尼 (fentanyl)」
     */
    public static boolean isFentanylItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && id.getPath().contains("fentanyl");
    }

    /**
     * 判断物品是否为「肾上腺素自动注射器 (adrenaline)」
     */
    public static boolean isAdrenalineItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && id.getPath().contains("adrenaline");
    }

    /**
     * 获取阿片注射器的最大剂量刻度单位（从 MedicalConfig 读取）
     */
    public static int getMaxOpioidUnits(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 100;
        if (stack.isDamageableItem() && stack.getMaxDamage() > 0) {
            return stack.getMaxDamage();
        }
        return isFentanylItem(stack)
                ? MedicalConfig.FENTANYL_MAX_DOSE_UNITS.get()
                : MedicalConfig.MORPHINE_MAX_DOSE_UNITS.get();
    }

    /**
     * 获取阿片注射器当前的剩余剂量刻度单位
     */
    public static int getRemainingOpioidUnits(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        if (stack.isDamageableItem() && stack.getMaxDamage() > 0) {
            return Math.max(0, stack.getMaxDamage() - stack.getDamageValue());
        }
        return getMaxOpioidUnits(stack);
    }

    public static boolean isMedicalItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.getItem() instanceof BandageItem) return true;
        if (isOpioidItem(stack)) return true;
        if (stack.has(MedicalComponents.MEDICAL_POTENCY.get())) return true;
        if (stack.is(MedicalItemTags.BANDAGE)
                || stack.is(MedicalItemTags.SYRINGE)
                || stack.is(MedicalItemTags.TOPICAL)
                || stack.is(MedicalItemTags.ORAL)
                || stack.is(MedicalItemTags.SPLINT)) {
            return true;
        }
        for (TagKey<Item> effectTag : EFFECT_HANDLERS.keySet()) {
            if (stack.is(effectTag)) return true;
        }
        return false;
    }

    public static InteractionHand findActiveMedicalHand(Player player) {
        if (player == null) return null;
        if (isMedicalItem(player.getMainHandItem())) return InteractionHand.MAIN_HAND;
        if (isMedicalItem(player.getOffhandItem())) return InteractionHand.OFF_HAND;
        return null;
    }

    public static boolean canTreatLimb(ItemStack stack, MedicalData data, MedicalData.Limb limb) {
        if (stack == null || stack.isEmpty() || data == null || limb == null) return false;
        MedicalData.LimbData ld = data.getLimb(limb);
        if (ld == null || ld.isAmputated()) return false;

        // 阿片类针剂（吗啡/芬太尼）可经任意未截肢部位（肌肉/静脉）推注生效！
        if (isAdrenalineItem(stack)) {
            return !ld.isAmputated();
        }
        if (isOpioidItem(stack)) {
            return getRemainingOpioidUnits(stack) > 0;
        }

        boolean hasAnyEffectTag = false;
        for (TagKey<Item> tag : EFFECT_HANDLERS.keySet()) {
            if (stack.is(tag)) {
                hasAnyEffectTag = true;
                if (canEffectApplyToLimb(tag, data, limb, ld)) {
                    return true;
                }
            }
        }

        if (!hasAnyEffectTag && (stack.is(MedicalItemTags.BANDAGE) || stack.getItem() instanceof BandageItem)) {
            return canEffectApplyToLimb(MedicalItemTags.HEMOSTASIS, data, limb, ld);
        }
        return false;
    }

    private static boolean canEffectApplyToLimb(TagKey<Item> tag, MedicalData data,
                                                MedicalData.Limb limb, MedicalData.LimbData ld) {
        if (tag == MedicalItemTags.HEMOSTASIS) {
            return ld.getBleeding() > 0 || (!ld.isBandaged() && (ld.hasOpenWound() || ld.hasBurn()));
        }
        if (tag == MedicalItemTags.TOURNIQUET) {
            return limb != MedicalData.Limb.HEAD && limb != MedicalData.Limb.CHEST
                    && (!ld.isTourniqueted() || ld.getBleeding() > 0);
        }
        if (tag == MedicalItemTags.INTERNAL_HEMOSTASIS) {
            return ld.getInternalBleedingRate() > 0;
        }
        if (tag == MedicalItemTags.SPLINT_EFFECT) {
            return ld.hasFracture() && !ld.hasSplint();
        }
        if (tag == MedicalItemTags.REDUCTION) {
            return ld.hasDislocation();
        }
        if (tag == MedicalItemTags.ANALGESIC) {
            return ld.getPainLevel() > 0;
        }
        if (tag == MedicalItemTags.OPIOID) {
            return !ld.isAmputated();
        }
        if (tag == MedicalItemTags.DISINFECT || tag == MedicalItemTags.CLEANSE || tag == MedicalItemTags.ANTIBIOTIC) {
            return ld.getInfection() > 0 || ld.hasOpenWound();
        }
        if (tag == MedicalItemTags.BURN_HEAL) {
            return ld.hasBurn();
        }
        if (tag == MedicalItemTags.BLOOD_VOLUME) {
            return data.getBloodVolume() < data.getMaxBloodVolume();
        }
        if (tag == MedicalItemTags.STIMULANT) {
            return data.getConsciousness() < 100;
        }
        if (tag == MedicalItemTags.DEFIBRILLATION) {
            return data.isInVFib() || data.isCardiacArrest();
        }
        if (tag == MedicalItemTags.HEMOTHORAX_DRAIN) {
            return limb == MedicalData.Limb.CHEST && data.getChestBloodAccumulation() > 0.1f;
        }
        return true;
    }

    public static float getChannelDurationSeconds(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0.65f;
        if (isAdrenalineItem(stack)) return 0.30f;
        if (isOpioidItem(stack) || stack.is(MedicalItemTags.SYRINGE)) return 0.35f;
        if (stack.is(MedicalItemTags.ORAL)) return 0.50f;
        if (stack.is(MedicalItemTags.TOPICAL)) return 0.55f;
        if (stack.is(MedicalItemTags.SPLINT)) return 0.85f;
        return 0.60f;
    }

    public static int getMaxHemostasisCapacity(ItemStack stack, MedicalPotency potency) {
        if (stack == null || stack.isEmpty()) return 500;
        if (stack.isDamageableItem() && stack.getMaxDamage() > 0) {
            return stack.getMaxDamage();
        }
        float defaultCap = getDefaultHemostasisCapacity(stack);
        return Math.round(potency.get(MedicalItemTags.HEMOSTASIS.location(), defaultCap));
    }

    public static int getRemainingHemostasisCapacity(ItemStack stack, MedicalPotency potency) {
        if (stack == null || stack.isEmpty()) return 0;
        if (stack.isDamageableItem() && stack.getMaxDamage() > 0) {
            return Math.max(0, stack.getMaxDamage() - stack.getDamageValue());
        }
        return getMaxHemostasisCapacity(stack, potency);
    }

    public static List<String> getItemEffectSummary(ItemStack stack) {
        List<String> lines = new ArrayList<>();
        if (stack == null || stack.isEmpty()) return lines;

        if (isAdrenalineItem(stack)) {
            lines.add("0.3 mg");
            return lines;
        }
        if (isOpioidItem(stack)) {
            boolean fentanyl = isFentanylItem(stack);
            int maxU = getMaxOpioidUnits(stack);
            int remU = getRemainingOpioidUnits(stack);
            float ratio = maxU > 0 ? (remU / (float) maxU) : 0.0f;
            if (fentanyl) {
                float maxMcg = MedicalConfig.FENTANYL_MAX_MCG.get().floatValue();
                lines.add(String.format("%.0f / %.0f mcg", ratio * maxMcg, maxMcg));
            } else {
                float maxMg = MedicalConfig.MORPHINE_MAX_MG.get().floatValue();
                lines.add(String.format("%.1f / %.1f mg", ratio * maxMg, maxMg));
            }
            return lines;
        }
        return lines;
    }

    public static float getDefaultHemostasisCapacity(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 500.0f;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id != null && id.getPath().contains("pressure_bandage")) {
            return 1000.0f;
        }
        return 500.0f;
    }

    /**
     * 服务端执行阿片类药物定量推注（吗啡 Morphine / 芬太尼 Fentanyl）
     *
     * @return 实际消耗的注射器刻度单位 (1 ~ 100)
     */
    private static int applyOpioidDose(ServerPlayer player, MedicalData data, MedicalData.Limb targetLimb,
                                       MedicalData.LimbData targetLd, ItemStack stack, int requestedDoseUnits) {
        int maxUnits = Math.max(1, getMaxOpioidUnits(stack));
        int remUnits = getRemainingOpioidUnits(stack);
        if (remUnits <= 0) return 0;

        int actualUnits = Mth.clamp(requestedDoseUnits, 1, remUnits);
        float doseFraction = actualUnits / (float) maxUnits;
        boolean fentanyl = isFentanylItem(stack);

        // 从 MedicalConfig 读取药理参数（零硬编码）
        float targetReliefMax = fentanyl
                ? MedicalConfig.FENTANYL_TARGET_PAIN_RELIEF.get().floatValue()
                : MedicalConfig.MORPHINE_TARGET_PAIN_RELIEF.get().floatValue();
        float systemicReliefMax = fentanyl
                ? MedicalConfig.FENTANYL_SYSTEMIC_PAIN_RELIEF.get().floatValue()
                : MedicalConfig.MORPHINE_SYSTEMIC_PAIN_RELIEF.get().floatValue();
        float concGainMax = fentanyl
                ? MedicalConfig.FENTANYL_CONCENTRATION_PER_FULL_DOSE.get().floatValue()
                : MedicalConfig.MORPHINE_CONCENTRATION_PER_FULL_DOSE.get().floatValue();
        float addictionGainMax = fentanyl
                ? MedicalConfig.FENTANYL_ADDICTION_GAIN.get().floatValue()
                : MedicalConfig.MORPHINE_ADDICTION_GAIN.get().floatValue();

        // 1. 降低目标注射部位疼痛与全身各部位疼痛（阿片通过血液循环作用于中枢神经）
        int targetDelta = Math.max(1, Math.round(targetReliefMax * doseFraction));
        targetLd.setPainLevel(Math.max(0, targetLd.getPainLevel() - targetDelta));

        int sysDelta = Math.round(systemicReliefMax * doseFraction);
        if (sysDelta > 0) {
            for (MedicalData.Limb other : MedicalData.Limb.values()) {
                if (other == targetLimb) continue;
                MedicalData.LimbData oldLd = data.getLimb(other);
                if (oldLd != null && oldLd.getPainLevel() > 0) {
                    oldLd.setPainLevel(Math.max(0, oldLd.getPainLevel() - sysDelta));
                }
            }
        }

        // 2. 累加体内阿片血药浓度与成瘾值
        float oldConc = (data instanceof MedicalDataImpl impl)
                ? impl.getOpioidConcentrationFloat()
                : (float) data.getOpioidConcentration();
        float addedConc = concGainMax * doseFraction;
        float newConc = Mth.clamp(oldConc + addedConc, 0.0f, 100.0f);

        if (data instanceof MedicalDataImpl impl) {
            impl.setOpioidConcentrationFloat(newConc);
        } else {
            data.setOpioidConcentration(Math.round(newConc));
        }

        int newAddiction = Mth.clamp(data.getAddictionLevel() + Math.round(addictionGainMax * doseFraction), 0, 100);
        data.setAddictionLevel(newAddiction);

        // 3. 判定是否触发急性过量 (Overdose) 或致死性延髓呼吸麻痹 (Central Apnea)
        int apneaThreshold = MedicalConfig.OPIOID_APNEA_THRESHOLD.get();
        int overdoseThreshold = MedicalConfig.OPIOID_OVERDOSE_THRESHOLD.get();
        int respDepStart = MedicalConfig.OPIOID_RESP_DEPRESSION_START.get();

        if (newConc >= apneaThreshold) {
            data.setRespirationRate(0);
            float acuteConsDrop = MedicalConfig.OPIOID_ACUTE_OVERDOSE_CONSCIOUSNESS_DROP.get().floatValue();
            data.setConsciousnessFloat(Math.max(0.0f, data.getConsciousnessFloat() - acuteConsDrop));
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.2f, 0.65f);
        } else if (newConc >= overdoseThreshold) {
            float minFactor = MedicalConfig.OPIOID_MIN_DEPRESSED_RESP_FACTOR.get().floatValue();
            data.setRespirationRate(Math.max(1, Math.round(data.getRespirationRate() * minFactor)));
            data.setConsciousnessFloat(Math.max(0.0f, data.getConsciousnessFloat() - 15.0f));
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 0.9f, 0.85f);
        }
        player.displayClientMessage(
                Component.literal(fentanyl ? "已注射芬太尼" : "已注射吗啡")
                        .withStyle(ChatFormatting.GREEN),
                true
        );

        return actualUnits;
    }


    /**
     * 服务端执行「肾上腺素自动注射器 (0.3mg Epinephrine)」推注药理逻辑
     */
    private static int applyAdrenalineDose(ServerPlayer player, MedicalData data,
                                           MedicalData.Limb targetLimb, MedicalData.LimbData targetLd, ItemStack stack) {
        float gain = Math.min(55.0f, MedicalConfig.ADRENALINE_INJECTOR_GAIN.get().floatValue());
        float oldAdr = ModItems.getAdrenalineLevel(data);
        float newAdr = Mth.clamp(oldAdr + gain, 0.0f, 100.0f);

        if (data instanceof MedicalDataImpl impl) {
            impl.setAdrenalineLevelFloat(newAdr);
            impl.setAdrenalineFromInjector(true);
            impl.setAdrenalineCrashSec(0);
        }

        // 1. 提振意识与血压
        data.setConsciousnessFloat(Math.min(100.0f, data.getConsciousnessFloat() + 25.0f));
        data.setSystolicBP(Math.min(200, data.getSystolicBP() + 20));
        data.setDiastolicBP(Math.min(130, data.getDiastolicBP() + 10));
        data.setMeanArterialPressure(Math.min(150.0f, data.getMeanArterialPressure() + 15.0f));
        data.setPeripheralResistance(Math.min(2.0f, data.getPeripheralResistance() + 0.20f));

        // 2. 若处于失血濒危倒计时，延长 8 秒抢救时间
        if (data.isInCriticalState() && data.getCriticalTimer() > 0) {
            int maxCrit = MedicalConfig.CRITICAL_TIMER_MAX.get();
            data.setCriticalTimer(Math.min(maxCrit, data.getCriticalTimer() + 8));
        }

        // 3. 解除心动过缓，温和提升心率（单支注射绝不冲入室颤阈值）
        data.setBradycardiaActive(false);
        data.setBradycardiaSevere(false);
        data.setBradyTimer(0);
        if (data.getRespirationRate() > 0) {
            data.setRespirationRate(Math.min(MedicalConfig.MAX_RESPIRATION.get(), data.getRespirationRate() + 3));
        }

        int vfibThresh = MedicalConfig.VFIB_THRESHOLD.get();
        int tier3Thresh = MedicalConfig.ADRENALINE_TIER3_THRESHOLD.get();
        int curHr = Math.max(80, data.getHeartRate());
        int newHr;
        if (newAdr >= tier3Thresh) {
            // 仅在连续注射多支导致 III 级过量时才大幅拉高心率
            newHr = Math.min(MedicalConfig.MAX_HEART_RATE.get(), curHr + 28);
            data.setHeartEffectSevere(true);
        } else {
            // 单支正常注射：最多提升 +14 BPM，且封顶在室颤线 - 15 以下
            int safeCap = Math.max(150, vfibThresh - 15);
            newHr = Math.min(safeCap, curHr + 14);
        }
        data.setHeartRate(newHr);

        ModItems.syncAdrenalineEffectNow(player, data);

        player.displayClientMessage(
                Component.literal("已注射肾上腺素").withStyle(ChatFormatting.GOLD),
                true
        );
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 0.85f, 1.30f);

        return stack.isDamageableItem() ? Math.max(1, stack.getMaxDamage()) : 1;
    }

    /** 兼容 3 参数调用 */
    public static boolean applyServerTreatment(ServerPlayer player, InteractionHand hand, MedicalData.Limb limb) {
        return applyServerTreatment(player, hand, limb, 100);
    }

    /**
     * 服务端执行医疗用品治疗结算（支持滑块设定 doseUnits 按量扣除耐久）
     */
    public static boolean applyServerTreatment(ServerPlayer player, InteractionHand hand,
                                               MedicalData.Limb limb, int requestedDoseUnits) {
        if (player == null || hand == null || limb == null) return false;
        ItemStack stack = player.getItemInHand(hand);
        if (!isMedicalItem(stack)) return false;

        MedicalData data = player.getData(MedicalDataCapability.MEDICAL_DATA);
        if (data == null) return false;

        MedicalData.LimbData ld = data.getLimb(limb);
        if (ld == null || ld.isAmputated()) return false;

        MedicalPotency potency = stack.getOrDefault(MedicalComponents.MEDICAL_POTENCY.get(), MedicalPotency.EMPTY);
        int totalDurabilityCost = 0;
        boolean matchedTag = false;

        // 优先处理肾上腺素自动注射器与阿片类定量注射器
        if (isAdrenalineItem(stack)) {
            matchedTag = true;
            totalDurabilityCost = applyAdrenalineDose(player, data, limb, ld, stack);
        } else if (isOpioidItem(stack)) {
            matchedTag = true;
            totalDurabilityCost = applyOpioidDose(player, data, limb, ld, stack, requestedDoseUnits);
        } else {
            for (Map.Entry<TagKey<Item>, LimbTreatmentHandler> entry : EFFECT_HANDLERS.entrySet()) {
                if (stack.is(entry.getKey())) {
                    matchedTag = true;
                    int cost = entry.getValue().apply(player, data, limb, ld, stack, potency);
                    if (cost > 0) {
                        totalDurabilityCost += cost;
                    }
                }
            }
        }

        if (!matchedTag && (stack.is(MedicalItemTags.BANDAGE) || stack.getItem() instanceof BandageItem)) {
            LimbTreatmentHandler hemo = EFFECT_HANDLERS.get(MedicalItemTags.HEMOSTASIS);
            if (hemo != null) {
                int cost = hemo.apply(player, data, limb, ld, stack, potency);
                if (cost > 0) {
                    totalDurabilityCost += cost;
                }
            }
        }

        if (totalDurabilityCost <= 0) {
            player.displayClientMessage(
                    Component.translatable("message.realmedicalmod.bandage.no_wound")
                            .withStyle(ChatFormatting.YELLOW),
                    true
            );
            return false;
        }

        if (stack.is(MedicalItemTags.NON_STERILE) && !stack.is(MedicalItemTags.STERILE)) {
            ld.setInfection(Math.min(100, ld.getInfection() + 2));
        }

        if (stack.isDamageableItem()) {
            int newDamage = stack.getDamageValue() + totalDurabilityCost;
            if (newDamage >= stack.getMaxDamage()) {
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                } else {
                    stack.setDamageValue(0);
                }
            } else {
                stack.setDamageValue(newDamage);
            }
        } else if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                (isOpioidItem(stack) || isAdrenalineItem(stack)) ? SoundEvents.BREWING_STAND_BREW : SoundEvents.WOOL_PLACE,
                SoundSource.PLAYERS, 0.85f, 1.35f);
        player.syncData(MedicalDataCapability.MEDICAL_DATA);
        return true;
    }
}