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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 医疗用品、止血带、阿片镇痛、纳洛酮拮抗与肾上腺素路由器 (TreatmentRouter)
 */
public final class TreatmentRouter {

    private static final int OPEN_WOUND_DRESSING_COST = 50;
    private static final int UNBIND_NO_COST_SIGNAL = -999;

    @FunctionalInterface
    public interface LimbTreatmentHandler {
        int apply(ServerPlayer player, MedicalData data, MedicalData.Limb limb,
                  MedicalData.LimbData limbData, ItemStack stack, MedicalPotency potency);
    }

    private static final Map<TagKey<Item>, LimbTreatmentHandler> EFFECT_HANDLERS = new LinkedHashMap<>();

    static {
        // 1. 外出血止血 / 绷带包扎 (HEMOSTASIS) —— 绷带无法处理 > BANDAGE_MAX_BLEEDING_THRESHOLD (默认 500) 的出血
        registerEffectHandler(MedicalItemTags.HEMOSTASIS, (player, data, limb, ld, stack, potency) -> {
            if (ModItems.isLimbNecrotic(ld)) return 0;

            int oldBleed = ld.getBleeding();
            int clampedBleed = ModItems.getTourniquetClampedBleed(ld);
            int maxBandageBleed = MedicalConfig.BANDAGE_MAX_BLEEDING_THRESHOLD.get();

            // 超过配置阈值 (默认 500) 的出血，绷带无法处理
            if (oldBleed > maxBandageBleed || clampedBleed > maxBandageBleed) {
                return 0;
            }

            boolean needDressing = !ld.isBandaged() && (oldBleed > 0 || clampedBleed > 0 || ld.hasOpenWound() || ld.hasBurn());
            if (oldBleed <= 0 && clampedBleed <= 0 && !needDressing) {
                return 0;
            }

            int remainingCap = getRemainingHemostasisCapacity(stack, potency);
            if (remainingCap <= 0) {
                return 0;
            }

            if (ld.isTourniqueted() && clampedBleed > 0) {
                int absorbed = Math.min(clampedBleed, remainingCap);
                int newClamped = Math.max(0, clampedBleed - absorbed);
                ModItems.setTourniquetClampedBleed(ld, newClamped);

                if (newClamped == 0) {
                    ld.setBandaged(true);
                    ld.setOpenWound(false);
                    ld.setNaturalClottingTimer(0);
                } else {
                    ld.setBandaged(false);
                }
                return Math.max(1, absorbed);
            }

            if (oldBleed > 0) {
                int absorbed = Math.min(oldBleed, remainingCap);
                int newBleed = Math.max(0, oldBleed - absorbed);
                ld.setBleeding(newBleed);

                if (newBleed == 0) {
                    ld.setBandaged(true);
                    ld.setOpenWound(false);
                    ld.setNaturalClottingTimer(0);
                } else {
                    ld.setBandaged(false);
                }
                return Math.max(1, absorbed);
            } else {
                int cost = Math.min(OPEN_WOUND_DRESSING_COST, remainingCap);
                ld.setBandaged(true);
                ld.setOpenWound(false);
                return Math.max(1, cost);
            }
        });

        // 2. 止血带强效阻断 (TOURNIQUET) —— 可阻断大出血，但不能修复伤口，再次使用可解开止血带
        registerEffectHandler(MedicalItemTags.TOURNIQUET, (player, data, limb, ld, stack, potency) ->
                applyOrToggleTourniquet(player, data, limb, ld, stack));

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

        // 4. 骨折夹板固定 (SPLINT_EFFECT) —— 调用骨折固定与愈合计时接口，并恢复一定行动力
        registerEffectHandler(MedicalItemTags.SPLINT_EFFECT, (player, data, limb, ld, stack, potency) -> {
            if (!ld.hasFracture() || ld.hasSplint() || ld.isAmputated() || ModItems.isLimbNecrotic(ld)) return 0;
            ModItems.applySplintAndInitFractureHealing(data, ld);
            return stack.isDamageableItem() ? Math.max(1, stack.getMaxDamage()) : 1;
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

        // 7. 阿片类强效镇痛 (OPIOID)
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

    public static boolean isFentanylItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && id.getPath().contains("fentanyl");
    }

    public static boolean isAdrenalineItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && id.getPath().contains("adrenaline");
    }

    public static boolean isNaloxoneItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && id.getPath().contains("naloxone");
    }

    public static boolean isTourniquetItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.is(MedicalItemTags.TOURNIQUET)) return true;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && id.getPath().contains("tourniquet");
    }

    public static boolean isSutureKitItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && id.getPath().contains("suture_kit");
    }

    public static boolean isFasciotomyKitItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && id.getPath().contains("fasciotomy");
    }

    public static boolean isSplintItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.is(MedicalItemTags.SPLINT) || stack.is(MedicalItemTags.SPLINT_EFFECT)) return true;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && id.getPath().contains("splint");
    }

    public static boolean isIbuprofenItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && id.getPath().contains("ibuprofen");
    }

    public static boolean isTranexamicAcidItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && id.getPath().contains("tranexamic");
    }

    public static int getMaxOpioidUnits(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 100;
        if (stack.isDamageableItem() && stack.getMaxDamage() > 0) {
            return stack.getMaxDamage();
        }
        if (isTranexamicAcidItem(stack)) {
            return 100;
        }
        return isFentanylItem(stack)
                ? MedicalConfig.FENTANYL_MAX_DOSE_UNITS.get()
                : MedicalConfig.MORPHINE_MAX_DOSE_UNITS.get();
    }

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
        if (isOpioidItem(stack) || isAdrenalineItem(stack) || isNaloxoneItem(stack) || isTourniquetItem(stack) || isSutureKitItem(stack) || isFasciotomyKitItem(stack) || isSplintItem(stack) || isIbuprofenItem(stack) || isTranexamicAcidItem(stack)) return true;
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
        if (ld == null) return false;

        if (isSplintItem(stack)) {
            return !ld.isAmputated() && !ModItems.isLimbNecrotic(ld) && (ld.hasFracture() || ld.hasSplint());
        }
        if (isIbuprofenItem(stack)) {
            return data.getConsciousness() > 0 && !data.isCardiacArrest();
        }
        if (isTranexamicAcidItem(stack)) {
            return !data.isCardiacArrest() && !ld.isAmputated() && getRemainingOpioidUnits(stack) > 0;
        }
        if (isFasciotomyKitItem(stack)) {
            if (limb == MedicalData.Limb.HEAD || limb == MedicalData.Limb.CHEST) return false;
            if (ld.isAmputated() || ModItems.isLimbNecrotic(ld)) return false;
            if (data.getConsciousness() < ModItems.getFasciotomyMinConsciousness() || data.isCardiacArrest() || data.isInVFib()) {
                return false;
            }
            // 允许在未发生骨筋膜室综合征时提前使用：只要四肢存在内出血、被压迫内出血或筋膜室内淤血/压力升高，即可切开引流血瘀并电凝止内出血
            return ModItems.isCompartmentSyndrome(ld)
                    || ld.getInternalBleedingRate() > 0
                    || ModItems.getCompartmentTamponadedBleed(ld) > 0
                    || ModItems.getCompartmentBlood(ld) > 0.1f
                    || ModItems.getCompartmentPressure(ld) > 4.5f;
        }
        if (isSutureKitItem(stack)) {
            if (ModItems.isLimbNecrotic(ld)) return false;
            if (data.getConsciousness() < ModItems.getSutureMinConsciousness() || data.isCardiacArrest() || data.isInVFib()) {
                return false;
            }
            int effBleed = Math.max(ld.getBleeding(), ModItems.getTourniquetClampedBleed(ld));
            return effBleed > MedicalConfig.BANDAGE_MAX_BLEEDING_THRESHOLD.get() || ModItems.isLimbArterialLigated(ld);
        }
        if (isAdrenalineItem(stack) || isNaloxoneItem(stack)) {
            return !ld.isAmputated();
        }
        if (isTourniquetItem(stack)) {
            if (limb == MedicalData.Limb.HEAD || limb == MedicalData.Limb.CHEST) return false;
            if (ModItems.isLimbNecrotic(ld) || ld.isTourniqueted()) return false;
            return ld.getBleeding() > 0 || ld.hasOpenWound();
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
            if (ModItems.isLimbNecrotic(ld)) return false;
            int maxBandageBleed = MedicalConfig.BANDAGE_MAX_BLEEDING_THRESHOLD.get();
            int clamped = ModItems.getTourniquetClampedBleed(ld);
            if (ld.getBleeding() > maxBandageBleed || clamped > maxBandageBleed) {
                return false;
            }
            return ld.getBleeding() > 0 || clamped > 0 || (!ld.isBandaged() && (ld.hasOpenWound() || ld.hasBurn()));
        }
        if (tag == MedicalItemTags.TOURNIQUET) {
            return limb != MedicalData.Limb.HEAD && limb != MedicalData.Limb.CHEST
                    && !ModItems.isLimbNecrotic(ld) && !ld.isTourniqueted()
                    && (ld.getBleeding() > 0 || ld.hasOpenWound());
        }
        if (tag == MedicalItemTags.INTERNAL_HEMOSTASIS) {
            return ld.getInternalBleedingRate() > 0;
        }
        if (tag == MedicalItemTags.SPLINT_EFFECT) {
            return !ld.isAmputated() && ld.hasFracture() && !ld.hasSplint();
        }
        if (tag == MedicalItemTags.REDUCTION) {
            return !ld.isAmputated() && ld.hasDislocation();
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
        if (isNaloxoneItem(stack) || isAdrenalineItem(stack)) return 0.28f;
        if (isSutureKitItem(stack) || isFasciotomyKitItem(stack)) return 0.35f;
        if (isTourniquetItem(stack)) return 0.45f;
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
        if (isNaloxoneItem(stack)) {
            lines.add("0.4 mg");
            return lines;
        }
        if (isTranexamicAcidItem(stack)) {
            int maxU = getMaxOpioidUnits(stack);
            int remU = getRemainingOpioidUnits(stack);
            float ratio = maxU > 0 ? (remU / (float) maxU) : 0.0f;
            lines.add(String.format("%.0f / 1000 mg", ratio * 1000.0f));
            return lines;
        }
        if (isTourniquetItem(stack)) {
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
     * 服务端执行「旋压式止血带 (tourniquet)」绑扎与解开逻辑：
     * - 未绑止血带时：夹闭动脉阻断大出血，保存原伤口出血率至 tourniquetClampedBleed，但不修复伤口 (bandaged=false, openWound=true)
     * - 已绑止血带时：解开止血带；若未用绷带包扎修复伤口，原出血立即复发！
     */
    /**
     * 仅由 H 医疗面板调用：解开部位上的止血带，并将这根止血带完整归还到玩家背包/手中（实现可重复利用）
     */
    /**
     * 服务端处理「野战血管缝合手术包 (field_vascular_suture_kit)」的内置止血带与双路径手术结算：
     * - actionCode == 200：开启手术界面 -> 自动绑上手术包自带止血带夹闭出血（不消耗耐久）
     * - actionCode == 201：完成路径 A（直接结扎闭塞术）-> 出血清零；若原出血 > 1000 (动脉出血) 则触发主干动脉结扎缺血
     * - actionCode == 202：完成路径 B（血管吻合桥接术）-> 出血清零且重建动脉供血，无任何缺血后遗症
     * - actionCode == 203：手术步骤操作失误 -> 局部痛感 +1、失血 15mL
     * - actionCode == 204：手术中途意识跌破 50% 或强行中断 -> 创口撕裂出血加剧
     */
    /**
     * 服务端处理「野战筋膜切开减压包 (fasciotomy_kit)」结算：
     * - actionCode == 300：开启筋膜切开减压手术界面校验
     * - actionCode == 301：完成四步筋膜切开减压 + 血肿清除 + 双极电凝止血 + 鞋带式减张闭合 -> 室压归零、清除内出血与综合征
     * - actionCode == 303：手术刀切偏或操作失误 -> 局部痛感增加、少量失血
     * - actionCode == 304：切开深筋膜解除高压后未完成电凝止血即强行中断 -> 失去筋膜压迫封顶，转化为开放性外出血！
     */
    private static int applyFasciotomyKitAction(ServerPlayer player, MedicalData data,
                                                MedicalData.Limb limb, MedicalData.LimbData ld,
                                                ItemStack stack, int actionCode) {
        if (limb == MedicalData.Limb.HEAD || limb == MedicalData.Limb.CHEST) return 0;
        if (ld.isAmputated() || ModItems.isLimbNecrotic(ld)) return 0;

        if (actionCode == 300) {
            if (data.getConsciousness() < ModItems.getFasciotomyMinConsciousness()) return 0;
            return UNBIND_NO_COST_SIGNAL;
        }

        if (actionCode == 303) {
            int maxPain = MedicalConfig.MAX_PAIN_LEVEL.get();
            int painGain = MedicalConfig.FASCIOTOMY_MISTAKE_PAIN_GAIN.get();
            int bloodLoss = MedicalConfig.FASCIOTOMY_MISTAKE_BLOOD_LOSS.get();
            ld.setPainLevel(Math.min(maxPain, ld.getPainLevel() + painGain));
            data.setBloodVolume(Math.max(0, data.getBloodVolume() - bloodLoss));
            return UNBIND_NO_COST_SIGNAL;
        }

        if (actionCode == 304) {
            int maxPain = MedicalConfig.MAX_PAIN_LEVEL.get();
            int painGain = MedicalConfig.FASCIOTOMY_MISTAKE_PAIN_GAIN.get();
            int abortBleed = MedicalConfig.FASCIOTOMY_ABORT_BLEED_PENALTY.get();
            int releasedTampBleed = ModItems.getCompartmentTamponadedBleed(ld) + ld.getInternalBleedingRate();
            ld.setPainLevel(Math.min(maxPain, ld.getPainLevel() + painGain));
            // 筋膜已被切开但未完成电凝止血：室内压下降，但失去筋膜压迫封顶，深部内出血转化为开放性外出血！
            ModItems.setCompartmentSyndrome(ld, false);
            ModItems.setCompartmentBlood(ld, 0.0f);
            ModItems.setCompartmentPressure(ld, 5.0f);
            ModItems.setCompartmentTamponadedBleed(ld, 0);
            ModItems.setCompartmentIschemiaSec(ld, 0);
            ld.setInternalBleedingRate(0);
            ld.setOpenWound(true);
            ld.setBandaged(false);
            ld.setBleeding(ld.getBleeding() + releasedTampBleed + abortBleed);
            return UNBIND_NO_COST_SIGNAL;
        }

        if (data.getConsciousness() < ModItems.getFasciotomyMinConsciousness() || data.isCardiacArrest() || data.isInVFib()) {
            return 0;
        }

        if (actionCode == 301) {
            ModItems.setCompartmentSyndrome(ld, false);
            ModItems.setCompartmentBlood(ld, 0.0f);
            ModItems.setCompartmentPressure(ld, 4.0f);
            ModItems.setCompartmentTamponadedBleed(ld, 0);
            ModItems.setCompartmentIschemiaSec(ld, 0);
            ld.setInternalBleedingRate(0);
            ld.setOpenWound(false);
            ld.setBandaged(true);
            ld.setPainLevel(Math.max(0, ld.getPainLevel() - 2));
            return MedicalConfig.FASCIOTOMY_DURABILITY_COST.get();
        }

        return 0;
    }

    private static int applySutureKitAction(ServerPlayer player, MedicalData data,
                                            MedicalData.Limb limb, MedicalData.LimbData ld,
                                            ItemStack stack, int actionCode) {
        if (ModItems.isLimbNecrotic(ld)) return 0;

        if (actionCode == 200) {
            if (data.getConsciousness() < ModItems.getSutureMinConsciousness()) return 0;
            // 手术包自带止血带：若该部位尚未绑止血带且正在大出血，自动用内置止血带夹闭动脉
            if (!ld.isTourniqueted() && ld.getBleeding() > 0 && limb != MedicalData.Limb.HEAD && limb != MedicalData.Limb.CHEST) {
                ModItems.setTourniquetClampedBleed(ld, ld.getBleeding());
                ModItems.setTourniquetTimer(ld, 0);
                ld.setTourniqueted(true);
                ld.setBleeding(0);
            }
            return UNBIND_NO_COST_SIGNAL;
        }

        if (actionCode == 203) {
            int maxPain = MedicalConfig.MAX_PAIN_LEVEL.get();
            int painGain = MedicalConfig.SUTURE_MISTAKE_PAIN_GAIN.get();
            int bloodLoss = MedicalConfig.SUTURE_MISTAKE_BLOOD_LOSS.get();
            ld.setPainLevel(Math.min(maxPain, ld.getPainLevel() + painGain));
            data.setBloodVolume(Math.max(0, data.getBloodVolume() - bloodLoss));
            return UNBIND_NO_COST_SIGNAL;
        }

        if (actionCode == 204) {
            int maxPain = MedicalConfig.MAX_PAIN_LEVEL.get();
            int painGain = MedicalConfig.SUTURE_MISTAKE_PAIN_GAIN.get();
            int tearBleed = MedicalConfig.SUTURE_ABORT_TEAR_BLEED_INCREASE.get();
            ld.setPainLevel(Math.min(maxPain, ld.getPainLevel() + painGain));
            if (ld.isTourniqueted()) {
                ModItems.setTourniquetClampedBleed(ld, ModItems.getTourniquetClampedBleed(ld) + tearBleed);
            } else if (ld.getBleeding() > 0) {
                ld.setBleeding(ld.getBleeding() + tearBleed);
            }
            return UNBIND_NO_COST_SIGNAL;
        }

        if (data.getConsciousness() < ModItems.getSutureMinConsciousness() || data.isCardiacArrest() || data.isInVFib()) {
            return 0;
        }

        int origBleed = Math.max(ld.getBleeding(), ModItems.getTourniquetClampedBleed(ld));
        if (origBleed <= MedicalConfig.BANDAGE_MAX_BLEEDING_THRESHOLD.get() && !ModItems.isLimbArterialLigated(ld)) {
            return 0;
        }

        if (actionCode == 201) {
            // 路径 A：直接结扎缝死血管
            ld.setBleeding(0);
            ModItems.setTourniquetClampedBleed(ld, 0);
            ld.setOpenWound(false);
            ld.setBandaged(true);
            ld.setNaturalClottingTimer(0);
            ld.setTourniqueted(false);

            if (!ld.isAmputated() && origBleed > ModItems.getSutureArterialThreshold()) {
                // 对未断肢的主干动脉强行结扎：导致主干供血中断，持续向缺血坏死演进
                ModItems.setLimbArterialLigated(ld, true);
            } else {
                // 非主干动脉或已断肢残端：直接结扎闭合血管无任何坏死/缺血后果
                ModItems.setLimbArterialLigated(ld, false);
                ModItems.setTourniquetTimer(ld, 0);
            }
            return MedicalConfig.SUTURE_LIGATION_DURABILITY_COST.get();
        }

        if (actionCode == 202) {
            // 路径 B：血管吻合桥接手术（完美重建远端供血）
            ld.setBleeding(0);
            ModItems.setTourniquetClampedBleed(ld, 0);
            ld.setOpenWound(false);
            ld.setBandaged(true);
            ld.setNaturalClottingTimer(0);
            ld.setTourniqueted(false);
            ModItems.setLimbArterialLigated(ld, false);
            ModItems.setTourniquetTimer(ld, 0);
            return MedicalConfig.SUTURE_ANASTOMOSIS_DURABILITY_COST.get();
        }

        return 0;
    }

    public static int unbindTourniquetOnLimb(ServerPlayer player, MedicalData.LimbData ld) {
        if (ld == null || !ld.isTourniqueted()) return 0;
        ld.setTourniqueted(false);
        ModItems.setTourniquetTimer(ld, 0);
        int clampedBleed = ModItems.getTourniquetClampedBleed(ld);
        if (!ld.isBandaged() && clampedBleed > 0) {
            ld.setBleeding(clampedBleed);
            ModItems.setTourniquetClampedBleed(ld, 0);
} else {
            ModItems.setTourniquetClampedBleed(ld, 0);
}

        // 将解下的止血带归还给玩家，可无限重复利用
        if (!player.getAbilities().instabuild) {
            ItemStack returnedTq = new ItemStack(ModItems.TOURNIQUET.get());
            if (!player.getInventory().add(returnedTq)) {
                player.drop(returnedTq, false);
            }
        }
        return UNBIND_NO_COST_SIGNAL;
    }

    /**
     * 拆下该部位已固定的骨折夹板并将夹板完整归还到玩家背包/手中（实现夹板可回收复用）
     */
    public static int unbindSplintOnLimb(ServerPlayer player, MedicalData.LimbData ld) {
        if (ld == null || !ld.hasSplint()) return 0;
        ld.setSplint(false);
        if (!player.getAbilities().instabuild) {
            ItemStack returnedSplint = new ItemStack(ModItems.SPLINT.get());
            if (!player.getInventory().add(returnedSplint)) {
                player.drop(returnedSplint, false);
            }
        }
        return UNBIND_NO_COST_SIGNAL;
    }

    private static int applyOrToggleTourniquet(ServerPlayer player, MedicalData data,
                                               MedicalData.Limb limb, MedicalData.LimbData ld, ItemStack stack) {
        if (limb == MedicalData.Limb.HEAD || limb == MedicalData.Limb.CHEST) return 0;
        if (ModItems.isLimbNecrotic(ld) || ld.isTourniqueted()) {
            return 0;
        }

        int currentBleed = ld.getBleeding();
        if (currentBleed <= 0 && !ld.hasOpenWound()) {
            return 0;
        }

        ModItems.setTourniquetClampedBleed(ld, currentBleed);
        ModItems.setTourniquetTimer(ld, 0);
        ld.setTourniqueted(true);
        ld.setBleeding(0);
        if (currentBleed > 0) {
            ld.setBandaged(false);
            ld.setOpenWound(true);
        }
return stack.isDamageableItem() ? Math.max(1, stack.getMaxDamage()) : 1;
    }

    /**
     * 服务端执行「盐酸纳洛酮注射器 (naloxone)」阿片过量逆转与副作用逻辑
     */
    /**
     * 服务端执行「布洛芬缓释止痛片 (ibuprofen)」口服给药逻辑：
     * - 生效慢：必须先经过 NSAID_ONSET_DELAY_SEC (默认18s) 胃肠吸收潜伏期，随后经过 NSAID_RAMP_UP_SEC (默认25s) 缓慢爬坡起效
     * - 效果差：具有严格的天花板效应 (NSAID_MAX_PERCEIVED_PAIN_REDUCTION = 18.0，最多消除 2 级部位轻痛)，但无阿片成瘾与呼吸抑制
     * - 副作用：在药效未消退时重复过量服药会引发胃黏膜损伤与抗血小板毒性 (NSAID_OVERDOSE_GASTRIC_TOXIN_GAIN)
     */
    private static int applyIbuprofenDose(ServerPlayer player, MedicalData data,
                                          MedicalData.Limb targetLimb, MedicalData.LimbData targetLd, ItemStack stack) {
        if (data.getConsciousness() <= 0 || data.isCardiacArrest()) return 0;

        if (data instanceof MedicalDataImpl impl) {
            boolean alreadyActive = impl.getNsaidActiveTimer() > 0 || impl.getNsaidOnsetTimer() > 0;
            if (alreadyActive) {
                // 药效期内重复过量服用非甾体抗炎药：无法突破镇痛天花板，反而引发胃黏膜损伤与肾脏/血小板毒性
                float toxGain = MedicalConfig.NSAID_OVERDOSE_GASTRIC_TOXIN_GAIN.get().floatValue();
                if (toxGain > 0.0f) {
                    data.addToxinValue(toxGain);
                }
            } else {
                impl.setNsaidOnsetTimer(MedicalConfig.NSAID_ONSET_DELAY_SEC.get());
                impl.setNsaidRampTimer(0);
                impl.setNsaidLimbReliefAccumSec(0);
                impl.setNsaidLimbRelievedCount(0);
            }
            impl.setNsaidActiveTimer(MedicalConfig.NSAID_ACTIVE_DURATION_SEC.get());
            impl.setNsaidTargetLimbOrdinal(targetLimb != null ? targetLimb.ordinal() : -1);
        }

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.GENERIC_EAT, SoundSource.PLAYERS, 0.90f, 1.05f);
        return MedicalConfig.NSAID_DOSE_DURABILITY_COST.get();
    }

    /**
     * 服务端执行「氨甲环酸止血注射器 (tranexamic_acid / TXA)」定量推注内出血治疗与副作用逻辑：
     * - 支持定量滑块推注 (1 ~ 100 单位，对应 10 ~ 1000 mg)，按推注比例 doseFraction 缩放内出血削减、血药浓度增加与急性低血压反应
     * - 治疗作用：推注瞬间削减目标部位与全身各部位活动性内出血，并在有效期内持续抗纤溶促凝消除内出血
     * - 现实副作用 1（快速推注急性低血压与代偿性心动过速）：外周血管扩张导致收缩压/舒张压按剂量比例下降，心率代偿性升高
     * - 现实副作用 2（高凝微血栓形成）：重复或大剂量推注导致血药浓度 >= TXA_THROMBOSIS_THRESHOLD (75%) 时诱发全身高凝微血栓，扣减血氧并使止血带/骨筋膜室淤血肢体缺血坏死速度翻倍！
     */
    private static int applyTranexamicAcidDose(ServerPlayer player, MedicalData data,
                                               MedicalData.Limb targetLimb, MedicalData.LimbData targetLd,
                                               ItemStack stack, int requestedUnits) {
        if (data.isCardiacArrest()) return 0;
        int remUnits = getRemainingOpioidUnits(stack);
        if (remUnits <= 0) return 0;

        int maxUnits = Math.max(1, getMaxOpioidUnits(stack));
        int actualUnits = Mth.clamp(requestedUnits, 1, remUnits);
        float doseFraction = actualUnits / (float) maxUnits;

        int targetRed = Math.max(1, Math.round(MedicalConfig.TXA_IMMEDIATE_INTERNAL_BLEED_REDUCTION.get() * doseFraction));
        int sysRed = Math.max(1, Math.round(MedicalConfig.TXA_SYSTEMIC_INTERNAL_BLEED_REDUCTION.get() * doseFraction));

        if (targetLd != null && targetLd.getInternalBleedingRate() > 0) {
            targetLd.setInternalBleedingRate(Math.max(0, targetLd.getInternalBleedingRate() - targetRed));
        }
        for (MedicalData.Limb limb : MedicalData.Limb.values()) {
            if (limb == targetLimb) continue;
            MedicalData.LimbData otherLd = data.getLimb(limb);
            if (otherLd != null && otherLd.getInternalBleedingRate() > 0) {
                otherLd.setInternalBleedingRate(Math.max(0, otherLd.getInternalBleedingRate() - sysRed));
            }
        }
        if (data.getIntracranialBleedRate() > 0) {
            data.setIntracranialBleedRate(Math.max(0, data.getIntracranialBleedRate() - Math.max(1, sysRed / 8)));
        }

        float concGain = MedicalConfig.TXA_CONCENTRATION_PER_DOSE.get().floatValue() * doseFraction;
        float newTxa = concGain;
        if (data instanceof MedicalDataImpl impl) {
            newTxa = Mth.clamp(impl.getTxaConcentration() + concGain, 0.0f, 100.0f);
            impl.setTxaConcentration(newTxa);
        }

        // 副作用 1：快速静脉/肌注推注引发急性低血压 (Hypotension) 与反射性心动过速（按推注剂量比例缩放）
        int sysDrop = Math.round(MedicalConfig.TXA_HYPOTENSION_SYS_DROP.get() * doseFraction);
        int diaDrop = Math.round(MedicalConfig.TXA_HYPOTENSION_DIA_DROP.get() * doseFraction);
        int hrBoost = Math.round(MedicalConfig.TXA_REFLEX_HR_BOOST.get() * doseFraction);
        int nextSys = Math.max(45, data.getSystolicBP() - sysDrop);
        int nextDia = Math.max(25, data.getDiastolicBP() - diaDrop);
        data.setSystolicBP(nextSys);
        data.setDiastolicBP(nextDia);
        data.setMeanArterialPressure(Math.max(32.0f, nextDia + (nextSys - nextDia) / 3.0f));

        int vfibSafeCap = Math.max(150, MedicalConfig.VFIB_THRESHOLD.get() - 12);
        data.setHeartRate(Math.min(vfibSafeCap, data.getHeartRate() + hrBoost));

        // 副作用 2：在低血容量/低血压状态下推注或高浓度过量 (>=75%) 推注，引发脑供血不足眩晕与高凝血栓反应
        int dizzTicks = Math.round(MedicalConfig.TXA_DIZZINESS_TICKS.get() * Math.max(0.4f, doseFraction));
        int thrombThresh = MedicalConfig.TXA_THROMBOSIS_THRESHOLD.get();
        if (newTxa >= thrombThresh || (doseFraction >= 0.4f && (nextSys <= 90 || data.getBloodVolume() < 3600))) {
            if (dizzTicks > 0) {
                player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, dizzTicks, 0, false, false, false));
            }
            if (newTxa >= thrombThresh) {
                data.setConsciousnessFloat(Math.max(0.0f, data.getConsciousnessFloat() - 12.0f * doseFraction));
                data.setPeripheralResistance(Math.min(2.2f, data.getPeripheralResistance() + 0.25f * doseFraction));
            }
        }

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 0.85f, 1.25f);
        return actualUnits;
    }

    private static int applyNaloxoneDose(ServerPlayer player, MedicalData data,
                                         MedicalData.Limb targetLimb, MedicalData.LimbData targetLd, ItemStack stack) {
        float oldConc = (data instanceof MedicalDataImpl impl)
                ? impl.getOpioidConcentrationFloat()
                : (float) data.getOpioidConcentration();

        // 1. 强力拮抗并冲刷体内阿片血药浓度（全部参数读取自 MedicalConfig）
        float clearAmount = MedicalConfig.NALOXONE_OPIOID_CLEAR_AMOUNT.get().floatValue();
        int blockadeSec = MedicalConfig.NALOXONE_BLOCKADE_DURATION_SEC.get();
        float newConc = Math.max(0.0f, oldConc - clearAmount);
        if (data instanceof MedicalDataImpl impl) {
            impl.setOpioidConcentrationFloat(newConc);
            impl.setNaloxoneEffectSec(blockadeSec);
        } else {
            data.setOpioidConcentration(Math.round(newConc));
        }

        // 2. 逆转阿片呼吸暂停/呼吸抑制与昏迷、心动过缓
        int sedStart = MedicalConfig.OPIOID_SEDATION_START.get();
        int minRestoredResp = MedicalConfig.NALOXONE_MIN_RESTORED_RESP_RATE.get();
        float consRestore = MedicalConfig.NALOXONE_CONSCIOUSNESS_RESTORE.get().floatValue();
        if (oldConc >= sedStart * 0.5f) {
            if (data.getRespirationRate() < minRestoredResp) {
                data.setRespirationRate(minRestoredResp);
            }
            data.setConsciousnessFloat(Math.min(100.0f, data.getConsciousnessFloat() + consRestore));
            data.setBradycardiaActive(false);
            data.setBradycardiaSevere(false);
            data.setBradyTimer(0);
        }

        // 3. 副作用 A：瞬间剥夺中枢镇痛 -> 受伤部位痛觉反跳
        int painRebound = MedicalConfig.NALOXONE_WOUND_PAIN_REBOUND.get();
        int maxPain = MedicalConfig.MAX_PAIN_LEVEL.get();
        boolean hadWoundPain = false;
        for (MedicalData.Limb limb : MedicalData.Limb.values()) {
            MedicalData.LimbData ld = data.getLimb(limb);
            if (ld == null || ld.isAmputated()) continue;
            if (ld.getPainLevel() > 0 || ld.getBleeding() > 0 || ld.hasOpenWound() || ld.hasFracture() || ld.hasBurn()) {
                ld.setPainLevel(Math.min(maxPain, ld.getPainLevel() + painRebound));
                hadWoundPain = true;
            }
        }

        // 4. 副作用 B：交感儿茶酚胺反跳（心率骤升、血压飙升）
        int curHr = Math.max(MedicalConfig.RESTING_HEART_RATE.get(), data.getHeartRate());
        int vfibMargin = MedicalConfig.ADRENALINE_TIER1_THRESHOLD.get();
        int vfibSafeCap = Math.max(150, MedicalConfig.VFIB_THRESHOLD.get() - vfibMargin);
        int hrBoost = MedicalConfig.NALOXONE_REBOUND_HR_BOOST.get();
        int bpBoost = MedicalConfig.NALOXONE_REBOUND_BP_BOOST.get();
        data.setHeartRate(Math.min(vfibSafeCap, curHr + hrBoost));
        data.setSystolicBP(Math.min(210, data.getSystolicBP() + bpBoost));
        data.setDiastolicBP(Math.min(135, data.getDiastolicBP() + bpBoost / 2));

        // 5. 副作用 C：对高浓度阿片或成瘾者促发急性戒断
        int wdTicks = MedicalConfig.NALOXONE_PRECIPITATED_WITHDRAWAL_TICKS.get();
        if (wdTicks > 0 && (oldConc >= sedStart || data.getAddictionLevel() >= MedicalConfig.ADDICTION_TIER1_THRESHOLD.get() || hadWoundPain)) {
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, wdTicks, 0, false, false, false));
        }
player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 0.90f, 1.35f);

        return stack.isDamageableItem() ? Math.max(1, stack.getMaxDamage()) : 1;
    }

    private static int applyOpioidDose(ServerPlayer player, MedicalData data,
                                       MedicalData.Limb targetLimb, MedicalData.LimbData targetLd,
                                       ItemStack stack, int requestedUnits) {
        int remUnits = getRemainingOpioidUnits(stack);
        if (remUnits <= 0) return 0;

        int maxUnits = Math.max(1, getMaxOpioidUnits(stack));
        int actualUnits = Mth.clamp(requestedUnits, 1, remUnits);
        float doseFraction = actualUnits / (float) maxUnits;

        boolean fentanyl = isFentanylItem(stack);

        // 若正处于纳洛酮受体阻断期，阿片镇痛与浓度建立被竞争性拮抗削弱 70%
        float naloxoneBlock = (ModItems.getNaloxoneEffectSec(data) > 0) ? MedicalConfig.NALOXONE_RECEPTOR_BLOCK_FACTOR.get().floatValue() : 1.0f;

        float targetReliefBase = fentanyl
                ? MedicalConfig.FENTANYL_TARGET_PAIN_RELIEF.get().floatValue()
                : MedicalConfig.MORPHINE_TARGET_PAIN_RELIEF.get().floatValue();
        float systemicReliefBase = fentanyl
                ? MedicalConfig.FENTANYL_SYSTEMIC_PAIN_RELIEF.get().floatValue()
                : MedicalConfig.MORPHINE_SYSTEMIC_PAIN_RELIEF.get().floatValue();

        int targetPainReduction = Math.max(0, Math.round(targetReliefBase * doseFraction * naloxoneBlock));
        int systemicPainReduction = Math.max(0, Math.round(systemicReliefBase * doseFraction * naloxoneBlock));

        targetLd.setPainLevel(Math.max(0, targetLd.getPainLevel() - targetPainReduction));
        for (MedicalData.Limb otherLimb : MedicalData.Limb.values()) {
            if (otherLimb == targetLimb) continue;
            MedicalData.LimbData otherLd = data.getLimb(otherLimb);
            if (otherLd != null && !otherLd.isAmputated() && otherLd.getPainLevel() > 0) {
                otherLd.setPainLevel(Math.max(0, otherLd.getPainLevel() - systemicPainReduction));
            }
        }

        float concPerFull = fentanyl
                ? MedicalConfig.FENTANYL_CONCENTRATION_PER_FULL_DOSE.get().floatValue()
                : MedicalConfig.MORPHINE_CONCENTRATION_PER_FULL_DOSE.get().floatValue();
        float addedConc = concPerFull * doseFraction * naloxoneBlock;

        float oldConc = (data instanceof MedicalDataImpl impl)
                ? impl.getOpioidConcentrationFloat()
                : (float) data.getOpioidConcentration();
        float newConc = Mth.clamp(oldConc + addedConc, 0.0f, 100.0f);

        if (data instanceof MedicalDataImpl impl) {
            impl.setOpioidConcentrationFloat(newConc);
        } else {
            data.setOpioidConcentration(Math.round(newConc));
        }

        float addGainBase = fentanyl
                ? MedicalConfig.FENTANYL_ADDICTION_GAIN.get().floatValue()
                : MedicalConfig.MORPHINE_ADDICTION_GAIN.get().floatValue();
        int addDelta = Math.max(1, Math.round(addGainBase * doseFraction));
        data.setAddictionLevel(Mth.clamp(data.getAddictionLevel() + addDelta, 0, 100));

        int apneaThreshold = MedicalConfig.OPIOID_APNEA_THRESHOLD.get();
        int overdoseThreshold = MedicalConfig.OPIOID_OVERDOSE_THRESHOLD.get();

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
return actualUnits;
    }

    private static int applyAdrenalineDose(ServerPlayer player, MedicalData data,
                                           MedicalData.Limb targetLimb, MedicalData.LimbData targetLd, ItemStack stack) {
        float gain = MedicalConfig.ADRENALINE_INJECTOR_GAIN.get().floatValue();
        float oldAdr = ModItems.getAdrenalineLevel(data);
        float newAdr = Mth.clamp(oldAdr + gain, 0.0f, 100.0f);

        if (data instanceof MedicalDataImpl impl) {
            impl.setAdrenalineLevelFloat(newAdr);
            impl.setAdrenalineFromInjector(true);
            impl.setAdrenalineCrashSec(0);
        }

        data.setConsciousnessFloat(Math.min(100.0f, data.getConsciousnessFloat() + 25.0f));
        data.setSystolicBP(Math.min(200, data.getSystolicBP() + 20));
        data.setDiastolicBP(Math.min(130, data.getDiastolicBP() + 10));
        data.setMeanArterialPressure(Math.min(150.0f, data.getMeanArterialPressure() + 15.0f));
        data.setPeripheralResistance(Math.min(2.0f, data.getPeripheralResistance() + 0.20f));

        if (data.isInCriticalState() && data.getCriticalTimer() > 0) {
            int maxCrit = MedicalConfig.CRITICAL_TIMER_MAX.get();
            data.setCriticalTimer(Math.min(maxCrit, data.getCriticalTimer() + 8));
        }

        data.setBradycardiaActive(false);
        data.setBradycardiaSevere(false);
        data.setBradyTimer(0);
        if (data.getRespirationRate() > 0) {
            data.setRespirationRate(Math.min(MedicalConfig.MAX_RESPIRATION.get(), data.getRespirationRate() + 3));
        }

        int vfibThresh = MedicalConfig.VFIB_THRESHOLD.get();
        int tier3Thresh = MedicalConfig.ADRENALINE_TIER3_THRESHOLD.get();
        int curHr = Math.max(MedicalConfig.RESTING_HEART_RATE.get(), data.getHeartRate());
        int singleBoost = Math.round(MedicalConfig.ADRENALINE_MAX_HR_BOOST.get().floatValue() * (MedicalConfig.ADRENALINE_TIER2_THRESHOLD.get().floatValue() / 100.0f));
        int vfibMargin = MedicalConfig.ADRENALINE_TIER1_THRESHOLD.get();
        int newHr;
        if (newAdr >= tier3Thresh) {
            int maxBoost = Math.round(MedicalConfig.ADRENALINE_MAX_HR_BOOST.get().floatValue());
            newHr = Math.min(MedicalConfig.MAX_HEART_RATE.get(), curHr + maxBoost);
            data.setHeartEffectSevere(true);
        } else {
            int safeCap = Math.max(140, vfibThresh - vfibMargin);
            newHr = Math.min(safeCap, curHr + singleBoost);
        }
        data.setHeartRate(newHr);

        ModItems.syncAdrenalineEffectNow(player, data);
player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 0.85f, 1.30f);

        return stack.isDamageableItem() ? Math.max(1, stack.getMaxDamage()) : 1;
    }

    public static boolean applyServerTreatment(ServerPlayer player, InteractionHand hand, MedicalData.Limb limb) {
        return applyServerTreatment(player, hand, limb, 100);
    }

    public static boolean applyServerTreatment(ServerPlayer player, InteractionHand hand,
                                               MedicalData.Limb limb, int requestedDoseUnits) {
        if (player == null || hand == null || limb == null) return false;
        MedicalData data = player.getData(MedicalDataCapability.MEDICAL_DATA);
        if (data == null) return false;

        MedicalData.LimbData ld = data.getLimb(limb);
        if (ld == null) return false;

        // 支持徒手 / 医疗面板一键解开该部位已绑的止血带 (requestedDoseUnits == -1) 或拆下回收骨折夹板 (requestedDoseUnits == -2)
        if (requestedDoseUnits == -1 && ld.isTourniqueted()) {
            unbindTourniquetOnLimb(player, ld);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 0.85f, 1.15f);
            player.syncData(MedicalDataCapability.MEDICAL_DATA);
            return true;
        }
        if (requestedDoseUnits == -2 && ld.hasSplint()) {
            unbindSplintOnLimb(player, ld);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 0.85f, 1.15f);
            player.syncData(MedicalDataCapability.MEDICAL_DATA);
            return true;
        }

        ItemStack stack = player.getItemInHand(hand);
        if (!isMedicalItem(stack)) return false;

        MedicalPotency potency = stack.getOrDefault(MedicalComponents.MEDICAL_POTENCY.get(), MedicalPotency.EMPTY);
        int totalDurabilityCost = 0;
        boolean matchedTag = false;
        boolean unbindNoCost = false;

        if (isFasciotomyKitItem(stack)) {
            matchedTag = true;
            int cost = applyFasciotomyKitAction(player, data, limb, ld, stack, requestedDoseUnits);
            if (cost == UNBIND_NO_COST_SIGNAL) {
                unbindNoCost = true;
            } else {
                totalDurabilityCost = cost;
            }
        } else if (isSutureKitItem(stack)) {
            matchedTag = true;
            int cost = applySutureKitAction(player, data, limb, ld, stack, requestedDoseUnits);
            if (cost == UNBIND_NO_COST_SIGNAL) {
                unbindNoCost = true;
            } else {
                totalDurabilityCost = cost;
            }
        } else if (isSplintItem(stack)) {
            matchedTag = true;
            if (ld.hasSplint()) {
                unbindSplintOnLimb(player, ld);
                unbindNoCost = true;
            } else if (!ld.isAmputated() && !ModItems.isLimbNecrotic(ld) && ld.hasFracture()) {
                ModItems.applySplintAndInitFractureHealing(data, ld);
                totalDurabilityCost = stack.isDamageableItem() ? Math.max(1, stack.getMaxDamage()) : 1;
            }
        } else if (isIbuprofenItem(stack)) {
            matchedTag = true;
            totalDurabilityCost = applyIbuprofenDose(player, data, limb, ld, stack);
        } else if (isTranexamicAcidItem(stack)) {
            matchedTag = true;
            totalDurabilityCost = applyTranexamicAcidDose(player, data, limb, ld, stack, requestedDoseUnits);
        } else if (isAdrenalineItem(stack)) {
            matchedTag = true;
            totalDurabilityCost = applyAdrenalineDose(player, data, limb, ld, stack);
        } else if (isNaloxoneItem(stack)) {
            matchedTag = true;
            totalDurabilityCost = applyNaloxoneDose(player, data, limb, ld, stack);
        } else if (isTourniquetItem(stack)) {
            matchedTag = true;
            int cost = applyOrToggleTourniquet(player, data, limb, ld, stack);
            if (cost == UNBIND_NO_COST_SIGNAL) {
                unbindNoCost = true;
            } else {
                totalDurabilityCost = cost;
            }
        } else if (isOpioidItem(stack)) {
            matchedTag = true;
            totalDurabilityCost = applyOpioidDose(player, data, limb, ld, stack, requestedDoseUnits);
        } else {
            for (Map.Entry<TagKey<Item>, LimbTreatmentHandler> entry : EFFECT_HANDLERS.entrySet()) {
                if (stack.is(entry.getKey())) {
                    matchedTag = true;
                    int cost = entry.getValue().apply(player, data, limb, ld, stack, potency);
                    if (cost == UNBIND_NO_COST_SIGNAL) {
                        unbindNoCost = true;
                    } else if (cost > 0) {
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

        if (unbindNoCost) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 0.85f, 1.15f);
            player.syncData(MedicalDataCapability.MEDICAL_DATA);
            return true;
        }

        if (totalDurabilityCost <= 0) {
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
                isIbuprofenItem(stack)
                        ? SoundEvents.GENERIC_EAT
                        : ((isOpioidItem(stack) || isAdrenalineItem(stack) || isNaloxoneItem(stack) || isTranexamicAcidItem(stack))
                                ? SoundEvents.BREWING_STAND_BREW
                                : SoundEvents.WOOL_PLACE),
                SoundSource.PLAYERS, 0.85f, isIbuprofenItem(stack) ? 1.05f : 1.35f);
        player.syncData(MedicalDataCapability.MEDICAL_DATA);
        return true;
    }
}