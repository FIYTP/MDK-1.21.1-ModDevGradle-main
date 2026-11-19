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
            int maxBandageBleed = ModItems.safeCfgInt(MedicalConfig.BANDAGE_MAX_BLEEDING_THRESHOLD, 500);

            // 超过配置阈值 (默认 500) 的出血，绷带无法处理
            if (oldBleed > maxBandageBleed || clampedBleed > maxBandageBleed) {
                return 0;
            }

            boolean needChestBrace = (limb == MedicalData.Limb.CHEST && ld.hasFracture() && !ModItems.isChestBraced(ld));
            boolean needDressing = (!ld.isBandaged() && (oldBleed > 0 || clampedBleed > 0 || ld.hasOpenWound() || ld.hasBurn())) || needChestBrace;
            if (oldBleed <= 0 && clampedBleed <= 0 && !needDressing) {
                return 0;
            }

            int remainingCap = getRemainingHemostasisCapacity(stack, potency);
            if (remainingCap <= 0) {
                return 0;
            }

            ResourceLocation bandageId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            boolean isPressureBandageItem = (bandageId != null && bandageId.getPath().contains("pressure_bandage"));
            int embeddedBullets = ModItems.getEmbeddedBulletCount(ld);
            boolean hasPerf = ld.hasPerforation();
            int maxPainVal = ModItems.safeCfgInt(MedicalConfig.MAX_PAIN_LEVEL, 10);

            // 若体内嵌留有未取出的子弹/弹片，直接缠绕绷带会将金属弹头压向深层组织神经，引发瞬时剧痛跳变！
            if (embeddedBullets > 0) {
                int pressPainSpike = ModItems.safeCfgInt(MedicalConfig.EMBEDDED_BULLET_BANDAGE_PRESS_PAIN_SPIKE, 2);
                if (pressPainSpike > 0) {
                    ld.setPainLevel(Math.min(maxPainVal, ld.getPainLevel() + pressPainSpike));
                }
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.CHAIN_HIT, SoundSource.PLAYERS, 0.85f, 0.70f);
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("message.realmedicalmod.bandage.embedded_bullet_pain"),
                        true
                );
            }

            // 贯穿伤 (hasPerforation) 绷带止血受限与二次创道塞纱填塞 (Wound Packing) 机制
            float perfEfficiency = 1.0f;
            int minPerfResidual = ModItems.safeCfgInt(MedicalConfig.PERFORATION_MIN_RESIDUAL_BLEED, 25);
            float perfResidualRatio = ((float) ModItems.safeCfgDouble(MedicalConfig.PERFORATION_BANDAGE_RESIDUAL_BLEED_RATIO, 0.28));
            if (hasPerf) {
                perfEfficiency = isPressureBandageItem
                        ? ((float) ModItems.safeCfgDouble(MedicalConfig.PERFORATION_PRESSURE_BANDAGE_EFFICIENCY, 0.60))
                        : ((float) ModItems.safeCfgDouble(MedicalConfig.PERFORATION_REGULAR_BANDAGE_EFFICIENCY, 0.35));
            }

            if (ld.isTourniqueted() && clampedBleed > 0) {
                int effectiveAbsorb = Math.max(5, Math.round(remainingCap * perfEfficiency));
                int residualFloor = 0;
                boolean packedNow = false;
                if (hasPerf && !ModItems.isPerforationPacked(ld)) {
                    int calcFloor = Math.max(minPerfResidual, Math.round(clampedBleed * perfResidualRatio));
                    if (isPressureBandageItem && clampedBleed <= Math.max(110, minPerfResidual * 3)) {
                        // 加压绷带对已初步控制的贯穿伤执行「创道塞纱深部填塞 (Wound Packing)」
                        residualFloor = 0;
                        packedNow = true;
                    } else {
                        residualFloor = Math.min(clampedBleed, calcFloor);
                    }
                }
                int newClamped = Math.max(residualFloor, clampedBleed - effectiveAbsorb);
                int durabilityUsed = Math.min(remainingCap, Math.max(15, clampedBleed - newClamped));
                ModItems.setTourniquetClampedBleed(ld, newClamped);

                if (newClamped == 0) {
                    ld.setBandaged(true);
                    ld.setOpenWound(embeddedBullets > 0 || hasPerf);
                    ld.setNaturalClottingTimer(0);
                    if (packedNow) {
                        ModItems.setPerforationPacked(ld, true);
                        player.displayClientMessage(
                                net.minecraft.network.chat.Component.translatable("message.realmedicalmod.bandage.perforation_packed"),
                                true
                        );
                    }
                } else {
                    ld.setBandaged(false);
                    if (hasPerf) {
                        player.displayClientMessage(
                                net.minecraft.network.chat.Component.translatable("message.realmedicalmod.bandage.perforation_limited", newClamped),
                                true
                        );
                    }
                }
                return Math.max(1, durabilityUsed);
            }

            if (oldBleed > 0) {
                int effectiveAbsorb = Math.max(5, Math.round(remainingCap * perfEfficiency));
                int residualFloor = 0;
                boolean packedNow = false;
                if (hasPerf && !ModItems.isPerforationPacked(ld)) {
                    int calcFloor = Math.max(minPerfResidual, Math.round(oldBleed * perfResidualRatio));
                    if (isPressureBandageItem && oldBleed <= Math.max(110, minPerfResidual * 3)) {
                        // 加压绷带二次深部塞纱填塞 (Wound Packing)：封堵前后双创口与空腔渗血
                        residualFloor = 0;
                        packedNow = true;
                    } else {
                        residualFloor = Math.min(oldBleed, calcFloor);
                    }
                }
                int newBleed = Math.max(residualFloor, oldBleed - effectiveAbsorb);
                int durabilityUsed = Math.min(remainingCap, Math.max(15, oldBleed - newBleed));
                ld.setBleeding(newBleed);

                if (newBleed == 0) {
                    ld.setBandaged(true);
                    ld.setOpenWound(embeddedBullets > 0 || hasPerf);
                    ld.setNaturalClottingTimer(0);
                    if (packedNow) {
                        ModItems.setPerforationPacked(ld, true);
                        player.displayClientMessage(
                                net.minecraft.network.chat.Component.translatable("message.realmedicalmod.bandage.perforation_packed"),
                                true
                        );
                    }
                    if (limb == MedicalData.Limb.CHEST && (ModItems.getPneumothoraxType(data) > 0 || ModItems.getPleuralAirMl(data) > 50.0f)) {
                        if (isPressureBandageItem) {
                            ModItems.setThreeSidedValveSealed(data, true);
                            ModItems.setImproperlyDeadSealed(data, false);
                            if (ModItems.getPneumothoraxType(data) == 2) {
                                ModItems.setPneumothoraxType(data, 1);
                            }
                        } else {
                            ModItems.setImproperlyDeadSealed(data, true);
                            ModItems.setThreeSidedValveSealed(data, false);
                        }
                    }
                } else {
                    ld.setBandaged(false);
                    if (hasPerf) {
                        player.displayClientMessage(
                                net.minecraft.network.chat.Component.translatable("message.realmedicalmod.bandage.perforation_limited", newBleed),
                                true
                        );
                    }
                }
                return Math.max(1, durabilityUsed);
            } else {
                int cost = Math.min(OPEN_WOUND_DRESSING_COST, remainingCap);
                ld.setBandaged(true);
                ld.setOpenWound(embeddedBullets > 0 || hasPerf);
                if (limb == MedicalData.Limb.CHEST) {
                    if (ld.hasFracture()) {
                        ModItems.setChestBraced(ld, true);
                        ModItems.setDelayedHemothoraxStrain(ld, 0);
                        if (ld.getFractureTimer() <= 0) {
                            ModItems.setFractureTimerAuth(ld, ModItems.safeCfgInt(MedicalConfig.SPLINT_FRACTURE_HEAL_TIME_SEC, 300));
                        }
                    }
                    // 气胸包扎鉴别：加压绷带自动执行「三边单向阀封堵」；普通粗糙绷带死封未排气伤口会憋成张力性气胸！
                    if (ModItems.getPneumothoraxType(data) > 0 || ModItems.getPleuralAirMl(data) > 50.0f) {
                        ResourceLocation bid = BuiltInRegistries.ITEM.getKey(stack.getItem());
                        boolean isPressureBandage = (bid != null && bid.getPath().contains("pressure_bandage"));
                        if (isPressureBandage) {
                            ModItems.setThreeSidedValveSealed(data, true);
                            ModItems.setImproperlyDeadSealed(data, false);
                            if (ModItems.getPneumothoraxType(data) == 2) {
                                ModItems.setPneumothoraxType(data, 1);
                            }
                            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                                    SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 0.75f, 1.55f);
                        } else if (ModItems.getPneumothoraxType(data) == 2 || ld.getInternalBleedingRate() > 0 || ld.hasFracture()) {
                            ModItems.setImproperlyDeadSealed(data, true);
                            ModItems.setThreeSidedValveSealed(data, false);
                        }
                    }
                }
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
            float healRatio = (heal > 1.0f) ? (heal / 100.0f) : heal;
            ld.setBurnArea(Math.max(0.0f, ld.getBurnArea() - healRatio));
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

    public static boolean isBallisticExtractionKitItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && id.getPath().contains("ballistic_extraction_kit");
    }

    public static boolean isPerforationRepairKitItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && id.getPath().contains("perforation_repair_kit");
    }

    public static boolean isFasciotomyKitItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && id.getPath().contains("fasciotomy");
    }

    public static boolean isOrthopedicKitItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && id.getPath().contains("orthopedic");
    }

    public static boolean isChestDrainKitItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && id.getPath().contains("chest_drain");
    }

    public static boolean isDecompressionNeedleItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && id.getPath().contains("decompression_needle");
    }

    public static boolean isSplintItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (isOrthopedicKitItem(stack)) return false;
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

    public static boolean isActivatedCharcoalItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && id.getPath().contains("activated_charcoal");
    }

    public static boolean isAcetylcysteineItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && id.getPath().contains("acetylcysteine");
    }

    public static boolean isPovidoneIodineItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && id.getPath().contains("povidone_iodine");
    }

    public static boolean isAmoxicillinItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && id.getPath().contains("amoxicillin");
    }

    public static boolean isMeropenemItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && id.getPath().contains("meropenem");
    }

    public static boolean isQuantitativeSyringeItem(ItemStack stack) {
        return isOpioidItem(stack) || isTranexamicAcidItem(stack) || isAcetylcysteineItem(stack) || isMeropenemItem(stack);
    }

    public static boolean isOralMedicineItem(ItemStack stack) {
        return isIbuprofenItem(stack) || isActivatedCharcoalItem(stack) || isAmoxicillinItem(stack);
    }

    public static int getMaxOpioidUnits(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 100;
        if (stack.isDamageableItem() && stack.getMaxDamage() > 0) {
            return stack.getMaxDamage();
        }
        if (isTranexamicAcidItem(stack)) {
            return 100;
        }
        if (isAcetylcysteineItem(stack)) {
            return ModItems.safeCfgInt(MedicalConfig.NAC_MAX_DOSE_UNITS, 100);
        }
        if (isMeropenemItem(stack)) {
            return ModItems.safeCfgInt(MedicalConfig.MEROPENEM_MAX_DOSE_UNITS, 100);
        }
        return isFentanylItem(stack)
                ? ModItems.safeCfgInt(MedicalConfig.FENTANYL_MAX_DOSE_UNITS, 100)
                : ModItems.safeCfgInt(MedicalConfig.MORPHINE_MAX_DOSE_UNITS, 100);
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
        if (isOpioidItem(stack) || isAdrenalineItem(stack) || isNaloxoneItem(stack) || isTourniquetItem(stack) || isSutureKitItem(stack) || isBallisticExtractionKitItem(stack) || isPerforationRepairKitItem(stack) || isFasciotomyKitItem(stack) || isOrthopedicKitItem(stack) || isChestDrainKitItem(stack) || isDecompressionNeedleItem(stack) || isSplintItem(stack) || isIbuprofenItem(stack) || isTranexamicAcidItem(stack) || isActivatedCharcoalItem(stack) || isAcetylcysteineItem(stack) || isPovidoneIodineItem(stack) || isAmoxicillinItem(stack) || isMeropenemItem(stack)) return true;
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

        if (isDecompressionNeedleItem(stack)) {
            if (limb != MedicalData.Limb.CHEST) return false;
            return ModItems.getPneumothoraxType(data) > 0 || ModItems.getPleuralAirMl(data) > 20.0f;
        }
        if (isActivatedCharcoalItem(stack)) {
            if (data.getConsciousness() < ModItems.safeCfgInt(MedicalConfig.CHARCOAL_MIN_CONSCIOUSNESS, 35) || data.isCardiacArrest()) {
                return false;
            }
            return data.getToxinValue() > 0.5f || ModItems.getCharcoalActiveSec(data) <= 10;
        }
        if (isAcetylcysteineItem(stack)) {
            if (ld.isAmputated() || data.isCardiacArrest()) return false;
            return data.getToxinValue() > 0.5f || ModItems.getNacConcentration(data) < 95.0f;
        }
        if (isPovidoneIodineItem(stack)) {
            if (ld.isAmputated() || ModItems.isLimbNecrotic(ld)) return false;
            return ld.getInfection() > 0 || ld.hasOpenWound() || ld.hasBurn() || ld.getDisinfectantTimer() <= 15;
        }
        if (isAmoxicillinItem(stack)) {
            if (data.getConsciousness() <= 0 || data.isCardiacArrest()) return false;
            return ModItems.getTotalInfection(data) > 0 || data.getSirsLevel() > 0 || ModItems.getAmoxicillinActiveSec(data) <= 15;
        }
        if (isMeropenemItem(stack)) {
            if (ld.isAmputated() || data.isCardiacArrest()) return false;
            return ModItems.getTotalInfection(data) > 0 || data.getSirsLevel() > 0 || ModItems.getMeropenemConcentration(data) < 95.0f;
        }
        if (isChestDrainKitItem(stack)) {
            if (limb != MedicalData.Limb.CHEST) return false;
            if (data.getConsciousness() < ModItems.getChestDrainMinConsciousness() || data.isCardiacArrest() || data.isInVFib()) {
                return false;
            }
            return data.getChestBloodAccumulation() > 5.0f
                    || data.getHemothoraxLevel() > 0
                    || ModItems.getPneumothoraxType(data) > 0
                    || ModItems.getPleuralAirMl(data) > 50.0f
                    || ld.getInternalBleedingRate() > 0
                    || data.getLungContusion() > 10.0f;
        }
        if (isOrthopedicKitItem(stack)) {
            if (ld.isAmputated() || ModItems.isLimbNecrotic(ld)) return false;
            if (data.getConsciousness() < ModItems.getOrthopedicMinConsciousness() || data.isCardiacArrest() || data.isInVFib()) {
                return false;
            }
            return (ld.hasFracture() || ld.isOpenFracture() || ld.hasDislocation()) && !ModItems.isLimbOrthopedicFixed(ld);
        }
        if (isSplintItem(stack)) {
            // 四肢铝合金夹板仅适用于四肢（胸部肋骨与头部不可绑四肢夹板）
            if (limb == MedicalData.Limb.HEAD || limb == MedicalData.Limb.CHEST) return false;
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
        if (isBallisticExtractionKitItem(stack)) {
            if (ModItems.isLimbNecrotic(ld)) return false;
            if (data.getConsciousness() < ModItems.getSutureMinConsciousness() || data.isCardiacArrest() || data.isInVFib()) {
                return false;
            }
            return ModItems.getEmbeddedBulletCount(ld) > 0;
        }
        if (isPerforationRepairKitItem(stack)) {
            if (ModItems.isLimbNecrotic(ld)) return false;
            if (data.getConsciousness() < ModItems.getSutureMinConsciousness() || data.isCardiacArrest() || data.isInVFib()) {
                return false;
            }
            return ld.hasPerforation();
        }
        if (isSutureKitItem(stack)) {
            if (ModItems.isLimbNecrotic(ld)) return false;
            if (data.getConsciousness() < ModItems.getSutureMinConsciousness() || data.isCardiacArrest() || data.isInVFib()) {
                return false;
            }
            int effBleed = Math.max(ld.getBleeding(), ModItems.getTourniquetClampedBleed(ld));
            return effBleed > ModItems.safeCfgInt(MedicalConfig.BANDAGE_MAX_BLEEDING_THRESHOLD, 500)
                    || ModItems.isLimbArterialLigated(ld);
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
            int maxBandageBleed = ModItems.safeCfgInt(MedicalConfig.BANDAGE_MAX_BLEEDING_THRESHOLD, 500);
            int clamped = ModItems.getTourniquetClampedBleed(ld);
            if (ld.getBleeding() > maxBandageBleed || clamped > maxBandageBleed) {
                return false;
            }
            boolean needChestBrace = (limb == MedicalData.Limb.CHEST && ld.hasFracture() && !ModItems.isChestBraced(ld));
            return ld.getBleeding() > 0 || clamped > 0 || (!ld.isBandaged() && (ld.hasOpenWound() || ld.hasBurn())) || needChestBrace;
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
        if (isSutureKitItem(stack) || isBallisticExtractionKitItem(stack) || isPerforationRepairKitItem(stack) || isFasciotomyKitItem(stack) || isOrthopedicKitItem(stack) || isChestDrainKitItem(stack)) return 0.35f;
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
                float maxMcg = ((float) ModItems.safeCfgDouble(MedicalConfig.FENTANYL_MAX_MCG, 100.0));
                lines.add(String.format("%.0f / %.0f mcg", ratio * maxMcg, maxMcg));
            } else {
                float maxMg = ((float) ModItems.safeCfgDouble(MedicalConfig.MORPHINE_MAX_MG, 10.0));
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
    /**
     * 服务端处理「野战骨科复位固定包 (orthopedic_fixation_kit)」结算：
     * - actionCode == 400：开启骨科手术界面
     * - actionCode == 401：完成骨科手术（四肢清创外固定支架锁定 / 胸腔连枷胸镍钛肋骨环抱器 SSRF 锁定）
     * - actionCode == 403：手术步骤失误（夹伤血管或骨钻偏滑）-> 疼痛增加、失血
     * - actionCode == 404：手术中途强行退出或昏迷中断 -> 骨断端二次移位刺伤血管引发内出血
     */
    /**
     * 服务端处理「胸腔闭式引流包 (chest_drain_kit)」结算：
     * - actionCode == 500：开启胸腔闭式引流手术界面
     * - actionCode == 501：完成肋间切开 + 置管 + 水封瓶负压排空血胸与肺复张 + 荷包缝合固定
     * - actionCode == 503：手术失误（损伤肋间血管或负压失控）-> 胸痛增加、失血
     * - actionCode == 504：切开胸膜腔后强行中断手术 -> 开放性血气胸导致胸腔积血与内出血激增
     */
    /**
     * 服务端执行「14G 胸腔穿刺减压针 (decompression_needle)」锁骨中线第 2 肋间紧急穿刺排气：
     * - 瞬间释放张力性气胸的高压气体（"嗤——"长排气声），胸内正压归零，解除腔静脉压迫并拉回梗阻性休克血压
     * - 对中度闭合性气胸 (<550 mL 且无连枷胸) 可直接一针排空治愈；对张力性气胸/连枷胸提供 180 秒单向阀安全窗口以衔接胸腔闭式引流术
     */
    /**
     * 服务端执行「医用活性炭混悬液 (activated_charcoal)」胃肠道毒物吸附与肠肝循环阻断：
     * - 立即吸附清除胃肠道及肠肝循环中的轻中度毒素，并在持续期内使毒素自然代谢速率翻倍
     * - 临床真实局限：对 >=60% 的重度深部血液/横纹肌溶解毒素直接吸附效率折减 50%，需配合乙酰半胱氨酸静脉解毒
     * - 临床药物相互作用：多孔活性炭会无差别物理吸附胃肠道内尚未吸收完毕的口服药物（清空布洛芬与阿莫西林口服药效）！
     */
    private static int applyActivatedCharcoalDose(ServerPlayer player, MedicalData data) {
        int minCons = ModItems.safeCfgInt(MedicalConfig.CHARCOAL_MIN_CONSCIOUSNESS, 35);
        if (data.getConsciousness() < minCons || data.isCardiacArrest()) return 0;

        float curToxin = data.getToxinValue();
        float baseClear = ((float) ModItems.safeCfgDouble(MedicalConfig.CHARCOAL_IMMEDIATE_TOXIN_CLEAR, 22.0));
        float severeThresh = ((float) ModItems.safeCfgDouble(MedicalConfig.CHARCOAL_SEVERE_TOXIN_THRESHOLD, 60.0));
        float effectiveClear = (curToxin >= severeThresh) ? (baseClear * 0.50f) : baseClear;
        data.setToxinValue(Math.max(0.0f, curToxin - effectiveClear));

        int durSec = ModItems.safeCfgInt(MedicalConfig.CHARCOAL_DURATION_SEC, 90);
        ModItems.setCharcoalActiveSec(data, durSec);

        // 物理吸附副作用：无差别吸附清空胃肠道内的口服布洛芬与口服阿莫西林！
        if (data instanceof MedicalDataImpl impl) {
            impl.setNsaidOnsetTimer(0);
            impl.setNsaidActiveTimer(0);
            impl.setNsaidRampTimer(0);
        }
        ModItems.setAmoxicillinOnsetSec(data, 0);
        ModItems.setAmoxicillinActiveSec(data, 0);

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 0.95f, 0.92f);
        return ModItems.safeCfgInt(MedicalConfig.CHARCOAL_DOSE_DURABILITY_COST, 25);
    }

    /**
     * 服务端执行「乙酰半胱氨酸解毒注射液 (acetylcysteine / NAC)」定量静脉推注：
     * - 作为谷胱甘肽 (GSH) 前体直接补充巯基，强效中和全身血液毒素、烧伤焦痂回吸收毒素与横纹肌溶解肌红蛋白肾毒性
     * - 临床真实副作用（类过敏组胺释放反应 Anaphylactoid Reaction）：
     *   单次推注过猛或体内浓度 >= NAC_HISTAMINE_OVERDOSE_THRESHOLD (70%) 时，诱发非 IgE 介导的肥大细胞组胺释放，
     *   导致外周血管骤然扩张（收缩压骤降）、支气管痉挛喘息（血氧损耗）与反射性心动过速！
     */
    private static int applyAcetylcysteineDose(ServerPlayer player, MedicalData data,
                                               MedicalData.Limb targetLimb, MedicalData.LimbData targetLd,
                                               ItemStack stack, int requestedUnits) {
        if (data.isCardiacArrest()) return 0;
        int remUnits = getRemainingOpioidUnits(stack);
        if (remUnits <= 0) return 0;

        int maxUnits = Math.max(1, getMaxOpioidUnits(stack));
        int actualUnits = Mth.clamp(requestedUnits, 1, remUnits);
        float doseFraction = actualUnits / (float) maxUnits;

        float immClear = ((float) ModItems.safeCfgDouble(MedicalConfig.NAC_IMMEDIATE_TOXIN_CLEAR, 48.0)) * doseFraction;
        data.setToxinValue(Math.max(0.0f, data.getToxinValue() - immClear));

        float concGain = ((float) ModItems.safeCfgDouble(MedicalConfig.NAC_CONCENTRATION_PER_FULL_DOSE, 65.0)) * doseFraction;
        float newNac = Mth.clamp(ModItems.getNacConcentration(data) + concGain, 0.0f, 100.0f);
        ModItems.setNacConcentration(data, newNac);

        int histThresh = ModItems.safeCfgInt(MedicalConfig.NAC_HISTAMINE_OVERDOSE_THRESHOLD, 70);
        if (newNac >= histThresh || doseFraction >= 0.80f) {
            float severity = (newNac >= histThresh) ? 1.0f : 0.55f;
            int sysDrop = Math.round(ModItems.safeCfgInt(MedicalConfig.NAC_HISTAMINE_SYS_BP_DROP, 18) * severity);
            int nextSys = Math.max(48, data.getSystolicBP() - sysDrop);
            int nextDia = Math.max(28, data.getDiastolicBP() - Math.max(2, sysDrop * 2 / 3));
            data.setSystolicBP(nextSys);
            data.setDiastolicBP(nextDia);
            data.setMeanArterialPressure(Math.max(34.0f, nextDia + (nextSys - nextDia) / 3.0f));

            int vfibSafeCap = Math.max(150, ModItems.safeCfgInt(MedicalConfig.VFIB_THRESHOLD, 185) - 10);
            data.setHeartRate(Math.min(vfibSafeCap, data.getHeartRate() + Math.round(14 * severity)));
            if (newNac >= histThresh) {
                player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 0, false, false, false));
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.PANDA_SNEEZE, SoundSource.PLAYERS, 0.90f, 0.68f);
            }
        }

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 0.88f, 1.35f);
        return actualUnits;
    }

    /**
     * 服务端执行「聚维酮碘消毒喷雾 (povidone_iodine)」局部创面清创杀菌与络合碘保护膜：
     * - 对轻度表浅感染 (<45) 直接高效杀灭；若感染已深入深部组织 (>=45)，外用直接清除率折减 65%（需配合全身抗生素）
     * - 赋予该部位 120 秒碘膜保护期（期间完全阻断伤口感染增长并提升 2.5 倍局部免疫清除率）
     * - 开放性伤口或浅表烧伤直接喷涂碘伏会产生短暂 1 级化学刺激刺痛
     */
    private static int applyPovidoneIodineDose(ServerPlayer player, MedicalData data,
                                               MedicalData.Limb limb, MedicalData.LimbData ld) {
        if (ld == null || ld.isAmputated() || ModItems.isLimbNecrotic(ld)) return 0;

        int curInf = ld.getInfection();
        int baseClean = ModItems.safeCfgInt(MedicalConfig.POVIDONE_IMMEDIATE_INFECTION_CLEAN, 18);
        int deepThresh = ModItems.safeCfgInt(MedicalConfig.POVIDONE_DEEP_INFECTION_THRESHOLD, 45);
        int actualClean = (curInf >= deepThresh) ? Math.max(2, Math.round(baseClean * 0.35f)) : baseClean;

        if (curInf > 0) {
            ld.setInfection(Math.max(0, curInf - actualClean));
        }

        int protSec = ModItems.safeCfgInt(MedicalConfig.POVIDONE_PROTECTION_DURATION_SEC, 120);
        ld.setDisinfectantTimer(Math.max(ld.getDisinfectantTimer(), protSec));
        ld.setInfectionAccumulator(0.0f);

        // 对未包扎开放性创口或浅度烧伤喷涂高浓度碘伏产生短暂化学刺激痛
        int stingGain = ModItems.safeCfgInt(MedicalConfig.POVIDONE_OPEN_WOUND_STING_PAIN, 1);
        if (stingGain > 0 && ((ld.hasOpenWound() && !ld.isBandaged()) || (ld.hasBurn() && ld.getBurnDegree() <= 2))) {
            int maxPain = ModItems.safeCfgInt(MedicalConfig.MAX_PAIN_LEVEL, 10);
            if (ld.getPainLevel() < 4) {
                ld.setPainLevel(Math.min(maxPain, ld.getPainLevel() + stingGain));
            }
        }

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.55f, 1.95f);
        return ModItems.safeCfgInt(MedicalConfig.POVIDONE_DURABILITY_COST, 20);
    }

    /**
     * 服务端执行「阿莫西林克拉维酸钾片 (amoxicillin)」广谱口服抗生素给药：
     * - 经过 15 秒胃肠吸收潜伏期后，维持 180 秒全身稳态抗菌血药浓度
     * - 药效期内若重复过量吞服，引发肠道菌群失调与肝肾代谢负担（增加毒素值）
     */
    private static int applyAmoxicillinDose(ServerPlayer player, MedicalData data) {
        if (data.getConsciousness() <= 0 || data.isCardiacArrest()) return 0;

        boolean alreadyTaking = ModItems.getAmoxicillinOnsetSec(data) > 0 || ModItems.getAmoxicillinActiveSec(data) > 20;
        if (alreadyTaking) {
            float toxGain = ((float) ModItems.safeCfgDouble(MedicalConfig.AMOXICILLIN_OVERDOSE_TOXIN_GAIN, 10.0));
            if (toxGain > 0.0f) {
                data.addToxinValue(toxGain);
                player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 80, 0, false, false, false));
            }
        } else {
            ModItems.setAmoxicillinOnsetSec(data, ModItems.safeCfgInt(MedicalConfig.AMOXICILLIN_ONSET_DELAY_SEC, 15));
        }
        ModItems.setAmoxicillinActiveSec(data, ModItems.safeCfgInt(MedicalConfig.AMOXICILLIN_ACTIVE_DURATION_SEC, 180));

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.GENERIC_EAT, SoundSource.PLAYERS, 0.90f, 1.12f);
        return ModItems.safeCfgInt(MedicalConfig.AMOXICILLIN_DOSE_DURABILITY_COST, 25);
    }

    /**
     * 服务端执行「注射用美罗培南+氢化可的松复合针剂 (meropenem)」定量静脉推注：
     * - 强效清除深部与全身感染，压制全身炎症反应综合征 (SIRS 1~4级) 细胞因子风暴，并修复脓毒症休克血管麻痹 (提升 TPR 与血压)
     * - 临床真实副作用 1（赫氏反应 Jarisch-Herxheimer Reaction）：
     *   当全身总感染 >= 110 或处于 SIRS >= 3 级时，若单次猛推 >= 65% 大剂量，瞬间裂解的海量细菌释放内毒素风暴，
     *   导致体内毒素条骤升、血压短暂下挫与寒战心动过速（需分次滴定或配合乙酰半胱氨酸解毒）！
     * - 临床真实副作用 2（碳青霉烯中枢神经惊厥毒性）：血药浓度 >= 85% 时拮抗中枢 GABA_A 受体引发眩晕与意识下降！
     */
    private static int applyMeropenemDose(ServerPlayer player, MedicalData data,
                                          MedicalData.Limb targetLimb, MedicalData.LimbData targetLd,
                                          ItemStack stack, int requestedUnits) {
        if (data.isCardiacArrest()) return 0;
        int remUnits = getRemainingOpioidUnits(stack);
        if (remUnits <= 0) return 0;

        int maxUnits = Math.max(1, getMaxOpioidUnits(stack));
        int actualUnits = Mth.clamp(requestedUnits, 1, remUnits);
        float doseFraction = actualUnits / (float) maxUnits;

        int totalInfBefore = ModItems.getTotalInfection(data);
        int sirsBefore = data.getSirsLevel();

        int targetClear = Math.max(1, Math.round(ModItems.safeCfgInt(MedicalConfig.MEROPENEM_TARGET_INFECTION_CLEAR, 38) * doseFraction));
        int sysClear = Math.max(1, Math.round(ModItems.safeCfgInt(MedicalConfig.MEROPENEM_SYSTEMIC_INFECTION_CLEAR, 22) * doseFraction));

        if (targetLd != null && !targetLd.isAmputated() && !ModItems.isLimbNecrotic(targetLd) && targetLd.getInfection() > 0) {
            targetLd.setInfection(Math.max(0, targetLd.getInfection() - targetClear));
        }
        for (MedicalData.Limb limb : MedicalData.Limb.values()) {
            if (limb == targetLimb) continue;
            MedicalData.LimbData otherLd = data.getLimb(limb);
            if (otherLd != null && !otherLd.isAmputated() && !ModItems.isLimbNecrotic(otherLd) && otherLd.getInfection() > 0) {
                otherLd.setInfection(Math.max(0, otherLd.getInfection() - sysClear));
            }
        }

        float concGain = ((float) ModItems.safeCfgDouble(MedicalConfig.MEROPENEM_CONCENTRATION_PER_FULL_DOSE, 70.0)) * doseFraction;
        float newMero = Mth.clamp(ModItems.getMeropenemConcentration(data) + concGain, 0.0f, 100.0f);
        ModItems.setMeropenemConcentration(data, newMero);

        // 逆转 SIRS 与修复脓毒症休克血管麻痹
        if (sirsBefore > 0) {
            data.setSirsProgressionTimer(0);
            float tprRestore = ((float) ModItems.safeCfgDouble(MedicalConfig.MEROPENEM_SIRS_TPR_RESTORE, 0.22)) * doseFraction;
            data.setPeripheralResistance(Math.min(1.15f, data.getPeripheralResistance() + tprRestore));
            if (data.getSystolicBP() < 115) {
                int bpGain = Math.round(16 * doseFraction);
                int nextSys = Math.min(120, data.getSystolicBP() + bpGain);
                int nextDia = Math.min(80, data.getDiastolicBP() + Math.max(2, bpGain * 2 / 3));
                data.setSystolicBP(nextSys);
                data.setDiastolicBP(nextDia);
                data.setMeanArterialPressure(nextDia + (nextSys - nextDia) / 3.0f);
            }
            if (doseFraction >= 0.45f) {
                int newSirs = Math.max(0, sirsBefore - 1);
                data.setSirsLevel(newSirs);
                data.setSirsRecoveryTimer(0);
                if (newSirs == 0) {
                    data.setSirsActive(false);
                }
            }
        }

        // 赫氏反应 (Jarisch-Herxheimer Reaction) 判定：重度感染/高阶 SIRS 下单次大剂量猛推引发细菌裂解内毒素风暴
        int herxInfThresh = ModItems.safeCfgInt(MedicalConfig.MEROPENEM_HERXHEIMER_INFECTION_THRESHOLD, 110);
        float herxDoseThresh = ((float) ModItems.safeCfgDouble(MedicalConfig.MEROPENEM_HERXHEIMER_DOSE_RATIO_THRESHOLD, 0.65));
        if ((totalInfBefore >= herxInfThresh || sirsBefore >= 3) && doseFraction >= herxDoseThresh) {
            float toxSpike = ((float) ModItems.safeCfgDouble(MedicalConfig.MEROPENEM_HERXHEIMER_TOXIN_SPIKE, 18.0)) * doseFraction;
            data.addToxinValue(toxSpike);
            int nextSys = Math.max(50, data.getSystolicBP() - 14);
            int nextDia = Math.max(30, data.getDiastolicBP() - 9);
            data.setSystolicBP(nextSys);
            data.setDiastolicBP(nextDia);
            data.setMeanArterialPressure(Math.max(35.0f, nextDia + (nextSys - nextDia) / 3.0f));
            int vfibSafeCap = Math.max(150, ModItems.safeCfgInt(MedicalConfig.VFIB_THRESHOLD, 185) - 8);
            data.setHeartRate(Math.min(vfibSafeCap, data.getHeartRate() + 20));
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 110, 0, false, false, false));
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.0f, 1.30f);
        }

        int neuroThresh = ModItems.safeCfgInt(MedicalConfig.MEROPENEM_NEUROTOXIC_THRESHOLD, 85);
        if (newMero >= neuroThresh) {
            data.setConsciousnessFloat(Math.max(10.0f, data.getConsciousnessFloat() - 14.0f * doseFraction));
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120, 0, false, false, false));
        }

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 0.90f, 1.20f);
        return actualUnits;
    }

    private static int applyDecompressionNeedleDose(ServerPlayer player, MedicalData data,
                                                    MedicalData.Limb limb, MedicalData.LimbData ld) {
        if (limb != MedicalData.Limb.CHEST) return 0;
        float curAir = ModItems.getPleuralAirMl(data);
        int pType = ModItems.getPneumothoraxType(data);
        if (curAir <= 10.0f && pType == 0) return 0;

        float drainMl = ((float) ModItems.safeCfgDouble(MedicalConfig.DECOMPRESSION_NEEDLE_DRAIN_ML, 550.0));
        int safeWinSec = ModItems.safeCfgInt(MedicalConfig.DECOMPRESSION_NEEDLE_SAFE_WINDOW_SEC, 180);
        boolean wasTension = (pType == 3) || (curAir >= ((float) ModItems.safeCfgDouble(MedicalConfig.PNEUMOTHORAX_TENSION_THRESHOLD_ML, 800.0)));

        float nextAir = Math.max(0.0f, curAir - drainMl);
        boolean hasUnfixedFlail = ld != null && ld.hasFracture()
                && (ld.isOpenFracture() || ld.hasDislocation())
                && !ModItems.isLimbOrthopedicFixed(ld);

        ModItems.setImproperlyDeadSealed(data, false);
        ModItems.setDecompressionWindowSec(data, safeWinSec);

        if (nextAir <= 20.0f && !hasUnfixedFlail && (ld == null || !ld.hasOpenWound() || ModItems.isThreeSidedValveSealed(data))) {
            ModItems.setPleuralAirMl(data, 0.0f);
            ModItems.setPleuralPressureCmH2O(data, -6.0f);
            ModItems.setPneumothoraxType(data, 0);
        } else {
            ModItems.setPleuralAirMl(data, Math.max(120.0f, nextAir));
            ModItems.setPleuralPressureCmH2O(data, -2.0f);
            ModItems.setPneumothoraxType(data, 1);
        }

        // 若此前为张力性气胸，解除腔静脉压迫瞬间回心血量恢复，收缩压/舒张压与血氧立即回升！
        if (wasTension) {
            int nextSys = Math.min(120, data.getSystolicBP() + 28);
            int nextDia = Math.min(80, data.getDiastolicBP() + 18);
            data.setSystolicBP(nextSys);
            data.setDiastolicBP(nextDia);
            data.setMeanArterialPressure(nextDia + (nextSys - nextDia) / 3.0f);
            data.setBloodOxygenFloat(Math.min(100.0f, data.getBloodOxygenFloat() + 10.0f));
        }
        if (ld != null && ld.getPainLevel() > 1) {
            ld.setPainLevel(Math.max(1, ld.getPainLevel() - 1));
        }

        // 标志性高压气体从 14G 套管针尾单向阀喷射而出的长嘶声！
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.0f, 1.55f);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 0.90f, 1.65f);

        return ModItems.safeCfgInt(MedicalConfig.DECOMPRESSION_NEEDLE_DURABILITY_COST, 50);
    }

    private static int applyChestDrainKitAction(ServerPlayer player, MedicalData data,
                                                MedicalData.Limb limb, MedicalData.LimbData ld,
                                                ItemStack stack, int actionCode) {
        if (limb != MedicalData.Limb.CHEST) return 0;

        if (actionCode == 500) {
            if (data.getConsciousness() < ModItems.getChestDrainMinConsciousness()) return 0;
            return UNBIND_NO_COST_SIGNAL;
        }

        if (actionCode == 503) {
            int maxPain = ModItems.safeCfgInt(MedicalConfig.MAX_PAIN_LEVEL, 10);
            int painGain = ModItems.safeCfgInt(MedicalConfig.CHEST_DRAIN_MISTAKE_PAIN_GAIN, 1);
            int bloodLoss = ModItems.safeCfgInt(MedicalConfig.CHEST_DRAIN_MISTAKE_BLOOD_LOSS, 15);
            ld.setPainLevel(Math.min(maxPain, ld.getPainLevel() + painGain));
            data.setBloodVolume(Math.max(0, data.getBloodVolume() - bloodLoss));
            return UNBIND_NO_COST_SIGNAL;
        }

        if (actionCode == 504) {
            int maxPain = ModItems.safeCfgInt(MedicalConfig.MAX_PAIN_LEVEL, 10);
            int painGain = ModItems.safeCfgInt(MedicalConfig.CHEST_DRAIN_MISTAKE_PAIN_GAIN, 1);
            int abortBleed = ModItems.safeCfgInt(MedicalConfig.CHEST_DRAIN_ABORT_BLEED_PENALTY, 90);
            ld.setPainLevel(Math.min(maxPain, ld.getPainLevel() + painGain));
            ld.setInternalBleedingRate(ld.getInternalBleedingRate() + abortBleed);
            data.setChestBloodAccumulation(data.getChestBloodAccumulation() + 120.0f);
            return UNBIND_NO_COST_SIGNAL;
        }

        if (data.getConsciousness() < ModItems.getChestDrainMinConsciousness() || data.isCardiacArrest() || data.isInVFib()) {
            return 0;
        }

        if (actionCode == 501) {
            ModItems.applyChestDrainSurgerySuccess(data, ld);
            return ModItems.safeCfgInt(MedicalConfig.CHEST_DRAIN_DURABILITY_COST, 25);
        }

        return 0;
    }

    private static int applyOrthopedicKitAction(ServerPlayer player, MedicalData data,
                                                MedicalData.Limb limb, MedicalData.LimbData ld,
                                                ItemStack stack, int actionCode) {
        if (ld.isAmputated() || ModItems.isLimbNecrotic(ld)) return 0;

        if (actionCode == 400) {
            if (data.getConsciousness() < ModItems.getOrthopedicMinConsciousness()) return 0;
            return UNBIND_NO_COST_SIGNAL;
        }

        if (actionCode == 403) {
            int maxPain = ModItems.safeCfgInt(MedicalConfig.MAX_PAIN_LEVEL, 10);
            int painGain = ModItems.safeCfgInt(MedicalConfig.ORTHO_MISTAKE_PAIN_GAIN, 1);
            int bloodLoss = ModItems.safeCfgInt(MedicalConfig.ORTHO_MISTAKE_BLOOD_LOSS, 15);
            ld.setPainLevel(Math.min(maxPain, ld.getPainLevel() + painGain));
            data.setBloodVolume(Math.max(0, data.getBloodVolume() - bloodLoss));
            return UNBIND_NO_COST_SIGNAL;
        }

        if (actionCode == 404) {
            int maxPain = ModItems.safeCfgInt(MedicalConfig.MAX_PAIN_LEVEL, 10);
            int painGain = ModItems.safeCfgInt(MedicalConfig.ORTHO_MISTAKE_PAIN_GAIN, 1);
            int abortIntBleed = ModItems.safeCfgInt(MedicalConfig.ORTHO_ABORT_INTERNAL_BLEED_PENALTY, 85);
            ld.setPainLevel(Math.min(maxPain, ld.getPainLevel() + painGain));
            ld.setInternalBleedingRate(ld.getInternalBleedingRate() + abortIntBleed);
            return UNBIND_NO_COST_SIGNAL;
        }

        if (data.getConsciousness() < ModItems.getOrthopedicMinConsciousness() || data.isCardiacArrest() || data.isInVFib()) {
            return 0;
        }

        if (actionCode == 401) {
            ModItems.applyOrthopedicSurgerySuccess(data, limb, ld);
            return ModItems.safeCfgInt(MedicalConfig.ORTHO_DURABILITY_COST, 25);
        }

        return 0;
    }

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
            int maxPain = ModItems.safeCfgInt(MedicalConfig.MAX_PAIN_LEVEL, 10);
            int painGain = ModItems.safeCfgInt(MedicalConfig.FASCIOTOMY_MISTAKE_PAIN_GAIN, 1);
            int bloodLoss = ModItems.safeCfgInt(MedicalConfig.FASCIOTOMY_MISTAKE_BLOOD_LOSS, 15);
            ld.setPainLevel(Math.min(maxPain, ld.getPainLevel() + painGain));
            data.setBloodVolume(Math.max(0, data.getBloodVolume() - bloodLoss));
            return UNBIND_NO_COST_SIGNAL;
        }

        if (actionCode == 304) {
            int maxPain = ModItems.safeCfgInt(MedicalConfig.MAX_PAIN_LEVEL, 10);
            int painGain = ModItems.safeCfgInt(MedicalConfig.FASCIOTOMY_MISTAKE_PAIN_GAIN, 1);
            int abortBleed = ModItems.safeCfgInt(MedicalConfig.FASCIOTOMY_ABORT_BLEED_PENALTY, 100);
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
            return ModItems.safeCfgInt(MedicalConfig.FASCIOTOMY_DURABILITY_COST, 25);
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
            int maxPain = ModItems.safeCfgInt(MedicalConfig.MAX_PAIN_LEVEL, 10);
            int painGain = ModItems.safeCfgInt(MedicalConfig.SUTURE_MISTAKE_PAIN_GAIN, 1);
            int bloodLoss = ModItems.safeCfgInt(MedicalConfig.SUTURE_MISTAKE_BLOOD_LOSS, 15);
            ld.setPainLevel(Math.min(maxPain, ld.getPainLevel() + painGain));
            data.setBloodVolume(Math.max(0, data.getBloodVolume() - bloodLoss));
            return UNBIND_NO_COST_SIGNAL;
        }

        if (actionCode == 204) {
            int maxPain = ModItems.safeCfgInt(MedicalConfig.MAX_PAIN_LEVEL, 10);
            int painGain = ModItems.safeCfgInt(MedicalConfig.SUTURE_MISTAKE_PAIN_GAIN, 1);
            int tearBleed = ModItems.safeCfgInt(MedicalConfig.SUTURE_ABORT_TEAR_BLEED_INCREASE, 120);
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
        if (origBleed <= ModItems.safeCfgInt(MedicalConfig.BANDAGE_MAX_BLEEDING_THRESHOLD, 500)
                && !ModItems.isLimbArterialLigated(ld)) {
            return 0;
        }

        if (actionCode == 201) {
            // 路径 A：直接血管结扎闭塞术（专职处理大出血/残端动脉结扎）
            ld.setBleeding(0);
            ModItems.setTourniquetClampedBleed(ld, 0);
            ld.setOpenWound(ModItems.getEmbeddedBulletCount(ld) > 0 || ld.hasPerforation());
            ld.setBandaged(!ld.hasOpenWound());
            ld.setNaturalClottingTimer(0);
            ld.setTourniqueted(false);

            if (!ld.isAmputated() && origBleed > ModItems.getSutureArterialThreshold()) {
                ModItems.setLimbArterialLigated(ld, true);
            } else {
                ModItems.setLimbArterialLigated(ld, false);
                ModItems.setTourniquetTimer(ld, 0);
            }
            return ModItems.safeCfgInt(MedicalConfig.SUTURE_LIGATION_DURABILITY_COST, 20);
        }

        if (actionCode == 202) {
            // 路径 B：人工血管吻合桥接术（完美重建远端供血）
            ld.setBleeding(0);
            ModItems.setTourniquetClampedBleed(ld, 0);
            ld.setOpenWound(ModItems.getEmbeddedBulletCount(ld) > 0 || ld.hasPerforation());
            ld.setBandaged(!ld.hasOpenWound());
            ld.setNaturalClottingTimer(0);
            ld.setTourniqueted(false);
            ModItems.setLimbArterialLigated(ld, false);
            ModItems.setTourniquetTimer(ld, 0);
            return ModItems.safeCfgInt(MedicalConfig.SUTURE_ANASTOMOSIS_DURABILITY_COST, 25);
        }

        return 0;
    }

    /**
     * 服务端处理「野战弹道取弹包 (ballistic_extraction_kit)」动作码：
     * - 800：开启深部弹道探查手术
     * - 801：将部位内全部嵌留弹头/霰弹鹿弹铅丸夹出放入不锈钢托盘，消除异物刺痛与深部厌氧感染源，并减轻创道渗血
     * - 803：取弹钳误触周围血管/神经导致局部疼痛与少量失血
     * - 804：切开探查中途强行中断导致创道二次渗血
     */
    private static int applyBallisticExtractionKitAction(ServerPlayer player, MedicalData data,
                                                         MedicalData.Limb limb, MedicalData.LimbData ld,
                                                         ItemStack stack, int actionCode) {
        if (ModItems.isLimbNecrotic(ld)) return 0;
        if (actionCode == 800) {
            if (data.getConsciousness() < ModItems.getSutureMinConsciousness()) return 0;
            return UNBIND_NO_COST_SIGNAL;
        }
        if (actionCode == 803) {
            int maxPain = ModItems.safeCfgInt(MedicalConfig.MAX_PAIN_LEVEL, 10);
            ld.setPainLevel(Math.min(maxPain, ld.getPainLevel() + 1));
            data.setBloodVolume(Math.max(0, data.getBloodVolume() - 12));
            return UNBIND_NO_COST_SIGNAL;
        }
        if (actionCode == 804) {
            int maxPain = ModItems.safeCfgInt(MedicalConfig.MAX_PAIN_LEVEL, 10);
            ld.setPainLevel(Math.min(maxPain, ld.getPainLevel() + 1));
            ld.setBleeding(ld.getBleeding() + 45);
            return UNBIND_NO_COST_SIGNAL;
        }
        if (data.getConsciousness() < ModItems.getSutureMinConsciousness() || data.isCardiacArrest() || data.isInVFib()) {
            return 0;
        }
        if (actionCode == 801 && ModItems.getEmbeddedBulletCount(ld) > 0) {
            ModItems.setEmbeddedBulletCount(ld, 0);
            ld.setPainLevel(Math.max(0, ld.getPainLevel() - 2));
            ld.setInfectionAccumulator(Math.max(0.0f, ld.getInfectionAccumulator() * 0.35f));
            int maxBandageThresh = ModItems.safeCfgInt(MedicalConfig.BANDAGE_MAX_BLEEDING_THRESHOLD, 500);
            if (ld.getBleeding() > 0 && ld.getBleeding() <= maxBandageThresh && !ld.hasPerforation()) {
                ld.setBleeding(Math.max(0, Math.round(ld.getBleeding() * 0.40f)));
                if (ld.getBleeding() == 0) {
                    ld.setOpenWound(false);
                    ld.setBandaged(true);
                }
            }
            return ModItems.safeCfgInt(MedicalConfig.SUTURE_BULLET_EXTRACT_DURABILITY_COST, 15);
        }
        return 0;
    }

    /**
     * 服务端处理「贯穿创道修补包 (perforation_repair_kit)」动作码：
     * - 900：开启前后贯穿伤双创口修补界面
     * - 901：完成贯穿腔道冲洗 + XStat 高膨胀止血海绵填塞 + 入射口/出射口双侧荷包缝合，彻底根治贯穿伤 (hasPerforation = false)
     * - 903：修补操作失误导致刺痛与少量失血
     * - 904：中途中断导致塞纱松脱渗血增加
     */
    private static int applyPerforationRepairKitAction(ServerPlayer player, MedicalData data,
                                                       MedicalData.Limb limb, MedicalData.LimbData ld,
                                                       ItemStack stack, int actionCode) {
        if (ModItems.isLimbNecrotic(ld)) return 0;
        if (actionCode == 900) {
            if (data.getConsciousness() < ModItems.getSutureMinConsciousness()) return 0;
            return UNBIND_NO_COST_SIGNAL;
        }
        if (actionCode == 903) {
            int maxPain = ModItems.safeCfgInt(MedicalConfig.MAX_PAIN_LEVEL, 10);
            ld.setPainLevel(Math.min(maxPain, ld.getPainLevel() + 1));
            data.setBloodVolume(Math.max(0, data.getBloodVolume() - 12));
            return UNBIND_NO_COST_SIGNAL;
        }
        if (actionCode == 904) {
            int maxPain = ModItems.safeCfgInt(MedicalConfig.MAX_PAIN_LEVEL, 10);
            ld.setPainLevel(Math.min(maxPain, ld.getPainLevel() + 1));
            ModItems.setPerforationPacked(ld, false);
            ld.setBleeding(ld.getBleeding() + 65);
            return UNBIND_NO_COST_SIGNAL;
        }
        if (data.getConsciousness() < ModItems.getSutureMinConsciousness() || data.isCardiacArrest() || data.isInVFib()) {
            return 0;
        }
        if (actionCode == 901 && ld.hasPerforation()) {
            ld.setPerforation(false);
            ModItems.setPerforationPacked(ld, false);
            int maxBandageThresh = ModItems.safeCfgInt(MedicalConfig.BANDAGE_MAX_BLEEDING_THRESHOLD, 500);
            if (ld.getBleeding() <= maxBandageThresh) {
                ld.setBleeding(0);
            } else {
                ld.setBleeding(Math.round(ld.getBleeding() * 0.45f));
            }
            if (ModItems.getTourniquetClampedBleed(ld) > 0 && ModItems.getTourniquetClampedBleed(ld) <= maxBandageThresh) {
                ModItems.setTourniquetClampedBleed(ld, 0);
            }
            ld.setInternalBleedingRate(Math.max(0, Math.round(ld.getInternalBleedingRate() * 0.45f)));
            ld.setPainLevel(Math.max(0, ld.getPainLevel() - 2));
            if (ld.getBleeding() == 0 && ModItems.getEmbeddedBulletCount(ld) == 0) {
                ld.setOpenWound(false);
                ld.setBandaged(true);
            }
            return ModItems.safeCfgInt(MedicalConfig.SUTURE_BULLET_EXTRACT_DURABILITY_COST, 15);
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
                float toxGain = ((float) ModItems.safeCfgDouble(MedicalConfig.NSAID_OVERDOSE_GASTRIC_TOXIN_GAIN, 12.0));
                if (toxGain > 0.0f) {
                    data.addToxinValue(toxGain);
                }
            } else {
                impl.setNsaidOnsetTimer(ModItems.safeCfgInt(MedicalConfig.NSAID_ONSET_DELAY_SEC, 18));
                impl.setNsaidRampTimer(0);
                impl.setNsaidLimbReliefAccumSec(0);
                impl.setNsaidLimbRelievedCount(0);
            }
            impl.setNsaidActiveTimer(ModItems.safeCfgInt(MedicalConfig.NSAID_ACTIVE_DURATION_SEC, 120));
            impl.setNsaidTargetLimbOrdinal(targetLimb != null ? targetLimb.ordinal() : -1);
        }

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.GENERIC_EAT, SoundSource.PLAYERS, 0.90f, 1.05f);
        return ModItems.safeCfgInt(MedicalConfig.NSAID_DOSE_DURABILITY_COST, 25);
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

        int targetRed = Math.max(1, Math.round(ModItems.safeCfgInt(MedicalConfig.TXA_IMMEDIATE_INTERNAL_BLEED_REDUCTION, 65) * doseFraction));
        int sysRed = Math.max(1, Math.round(ModItems.safeCfgInt(MedicalConfig.TXA_SYSTEMIC_INTERNAL_BLEED_REDUCTION, 35) * doseFraction));

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
        if (data.getLungContusion() > 0.0f) {
            data.setLungContusion(Math.max(0.0f, data.getLungContusion() - 8.0f * doseFraction));
        }

        float concGain = ((float) ModItems.safeCfgDouble(MedicalConfig.TXA_CONCENTRATION_PER_DOSE, 55.0)) * doseFraction;
        float newTxa = concGain;
        if (data instanceof MedicalDataImpl impl) {
            newTxa = Mth.clamp(impl.getTxaConcentration() + concGain, 0.0f, 100.0f);
            impl.setTxaConcentration(newTxa);
        }

        // 副作用 1：快速静脉/肌注推注引发急性低血压 (Hypotension) 与反射性心动过速（按推注剂量比例缩放）
        int sysDrop = Math.round(ModItems.safeCfgInt(MedicalConfig.TXA_HYPOTENSION_SYS_DROP, 16) * doseFraction);
        int diaDrop = Math.round(ModItems.safeCfgInt(MedicalConfig.TXA_HYPOTENSION_DIA_DROP, 10) * doseFraction);
        int hrBoost = Math.round(ModItems.safeCfgInt(MedicalConfig.TXA_REFLEX_HR_BOOST, 10) * doseFraction);
        int nextSys = Math.max(45, data.getSystolicBP() - sysDrop);
        int nextDia = Math.max(25, data.getDiastolicBP() - diaDrop);
        data.setSystolicBP(nextSys);
        data.setDiastolicBP(nextDia);
        data.setMeanArterialPressure(Math.max(32.0f, nextDia + (nextSys - nextDia) / 3.0f));

        int vfibSafeCap = Math.max(150, ModItems.safeCfgInt(MedicalConfig.VFIB_THRESHOLD, 185) - 12);
        data.setHeartRate(Math.min(vfibSafeCap, data.getHeartRate() + hrBoost));

        // 副作用 2：在低血容量/低血压状态下推注或高浓度过量 (>=75%) 推注，引发脑供血不足眩晕与高凝血栓反应
        int dizzTicks = Math.round(ModItems.safeCfgInt(MedicalConfig.TXA_DIZZINESS_TICKS, 100) * Math.max(0.4f, doseFraction));
        int thrombThresh = ModItems.safeCfgInt(MedicalConfig.TXA_THROMBOSIS_THRESHOLD, 75);
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
        float clearAmount = ((float) ModItems.safeCfgDouble(MedicalConfig.NALOXONE_OPIOID_CLEAR_AMOUNT, 75.0));
        int blockadeSec = ModItems.safeCfgInt(MedicalConfig.NALOXONE_BLOCKADE_DURATION_SEC, 60);
        float newConc = Math.max(0.0f, oldConc - clearAmount);
        if (data instanceof MedicalDataImpl impl) {
            impl.setOpioidConcentrationFloat(newConc);
            impl.setNaloxoneEffectSec(blockadeSec);
        } else {
            data.setOpioidConcentration(Math.round(newConc));
        }

        // 2. 逆转阿片呼吸暂停/呼吸抑制与昏迷、心动过缓
        int sedStart = ModItems.safeCfgInt(MedicalConfig.OPIOID_SEDATION_START, 40);
        int minRestoredResp = ModItems.safeCfgInt(MedicalConfig.NALOXONE_MIN_RESTORED_RESP_RATE, 12);
        float consRestore = ((float) ModItems.safeCfgDouble(MedicalConfig.NALOXONE_CONSCIOUSNESS_RESTORE, 35.0));
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
        int painRebound = ModItems.safeCfgInt(MedicalConfig.NALOXONE_WOUND_PAIN_REBOUND, 2);
        int maxPain = ModItems.safeCfgInt(MedicalConfig.MAX_PAIN_LEVEL, 10);
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
        int curHr = Math.max(ModItems.safeCfgInt(MedicalConfig.RESTING_HEART_RATE, 75), data.getHeartRate());
        int vfibMargin = ModItems.safeCfgInt(MedicalConfig.ADRENALINE_TIER1_THRESHOLD, 20);
        int vfibSafeCap = Math.max(150, ModItems.safeCfgInt(MedicalConfig.VFIB_THRESHOLD, 185) - vfibMargin);
        int hrBoost = ModItems.safeCfgInt(MedicalConfig.NALOXONE_REBOUND_HR_BOOST, 28);
        int bpBoost = ModItems.safeCfgInt(MedicalConfig.NALOXONE_REBOUND_BP_BOOST, 20);
        data.setHeartRate(Math.min(vfibSafeCap, curHr + hrBoost));
        data.setSystolicBP(Math.min(210, data.getSystolicBP() + bpBoost));
        data.setDiastolicBP(Math.min(135, data.getDiastolicBP() + bpBoost / 2));

        // 5. 副作用 C：对高浓度阿片或成瘾者促发急性戒断
        int wdTicks = ModItems.safeCfgInt(MedicalConfig.NALOXONE_PRECIPITATED_WITHDRAWAL_TICKS, 160);
        if (wdTicks > 0 && (oldConc >= sedStart || data.getAddictionLevel() >= ModItems.safeCfgInt(MedicalConfig.ADDICTION_TIER1_THRESHOLD, 25) || hadWoundPain)) {
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
        float naloxoneBlock = (ModItems.getNaloxoneEffectSec(data) > 0) ? ((float) ModItems.safeCfgDouble(MedicalConfig.NALOXONE_RECEPTOR_BLOCK_FACTOR, 0.30)) : 1.0f;

        float targetReliefBase = fentanyl
                ? ((float) ModItems.safeCfgDouble(MedicalConfig.FENTANYL_TARGET_PAIN_RELIEF, 9.0))
                : ((float) ModItems.safeCfgDouble(MedicalConfig.MORPHINE_TARGET_PAIN_RELIEF, 7.0));
        float systemicReliefBase = fentanyl
                ? ((float) ModItems.safeCfgDouble(MedicalConfig.FENTANYL_SYSTEMIC_PAIN_RELIEF, 6.0))
                : ((float) ModItems.safeCfgDouble(MedicalConfig.MORPHINE_SYSTEMIC_PAIN_RELIEF, 4.0));

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
                ? ((float) ModItems.safeCfgDouble(MedicalConfig.FENTANYL_CONCENTRATION_PER_FULL_DOSE, 85.0))
                : ((float) ModItems.safeCfgDouble(MedicalConfig.MORPHINE_CONCENTRATION_PER_FULL_DOSE, 60.0));
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
                ? ((float) ModItems.safeCfgDouble(MedicalConfig.FENTANYL_ADDICTION_GAIN, 18.0))
                : ((float) ModItems.safeCfgDouble(MedicalConfig.MORPHINE_ADDICTION_GAIN, 10.0));
        int addDelta = Math.max(1, Math.round(addGainBase * doseFraction));
        data.setAddictionLevel(Mth.clamp(data.getAddictionLevel() + addDelta, 0, 100));

        int apneaThreshold = ModItems.safeCfgInt(MedicalConfig.OPIOID_APNEA_THRESHOLD, 85);
        int overdoseThreshold = ModItems.safeCfgInt(MedicalConfig.OPIOID_OVERDOSE_THRESHOLD, 65);

        if (newConc >= apneaThreshold) {
            data.setRespirationRate(0);
            float acuteConsDrop = ((float) ModItems.safeCfgDouble(MedicalConfig.OPIOID_ACUTE_OVERDOSE_CONSCIOUSNESS_DROP, 40.0));
            data.setConsciousnessFloat(Math.max(0.0f, data.getConsciousnessFloat() - acuteConsDrop));
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.2f, 0.65f);
        } else if (newConc >= overdoseThreshold) {
            float minFactor = ((float) ModItems.safeCfgDouble(MedicalConfig.OPIOID_MIN_DEPRESSED_RESP_FACTOR, 0.35));
            data.setRespirationRate(Math.max(1, Math.round(data.getRespirationRate() * minFactor)));
            data.setConsciousnessFloat(Math.max(0.0f, data.getConsciousnessFloat() - 15.0f));
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 0.9f, 0.85f);
        }
return actualUnits;
    }

    private static int applyAdrenalineDose(ServerPlayer player, MedicalData data,
                                           MedicalData.Limb targetLimb, MedicalData.LimbData targetLd, ItemStack stack) {
        float gain = ((float) ModItems.safeCfgDouble(MedicalConfig.ADRENALINE_INJECTOR_GAIN, 65.0));
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
            int maxCrit = ModItems.safeCfgInt(MedicalConfig.CRITICAL_TIMER_MAX, 60);
            data.setCriticalTimer(Math.min(maxCrit, data.getCriticalTimer() + 8));
        }

        data.setBradycardiaActive(false);
        data.setBradycardiaSevere(false);
        data.setBradyTimer(0);
        if (data.getRespirationRate() > 0) {
            data.setRespirationRate(Math.min(ModItems.safeCfgInt(MedicalConfig.MAX_RESPIRATION, 40), data.getRespirationRate() + 3));
        }

        int vfibThresh = ModItems.safeCfgInt(MedicalConfig.VFIB_THRESHOLD, 185);
        int tier3Thresh = ModItems.safeCfgInt(MedicalConfig.ADRENALINE_TIER3_THRESHOLD, 80);
        int curHr = Math.max(ModItems.safeCfgInt(MedicalConfig.RESTING_HEART_RATE, 75), data.getHeartRate());
        int singleBoost = Math.round(((float) ModItems.safeCfgDouble(MedicalConfig.ADRENALINE_MAX_HR_BOOST, 55.0)) * (((float) ModItems.safeCfgDouble(MedicalConfig.ADRENALINE_TIER2_THRESHOLD, 50)) / 100.0f));
        int vfibMargin = ModItems.safeCfgInt(MedicalConfig.ADRENALINE_TIER1_THRESHOLD, 20);
        int newHr;
        if (newAdr >= tier3Thresh) {
            int maxBoost = Math.round(((float) ModItems.safeCfgDouble(MedicalConfig.ADRENALINE_MAX_HR_BOOST, 55.0)));
            newHr = Math.min(ModItems.safeCfgInt(MedicalConfig.MAX_HEART_RATE, 220), curHr + maxBoost);
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
        return applyServerTreatmentOnStack(player, data, limb, ld, stack, requestedDoseUnits);
    }

    private static boolean applyServerTreatmentOnStack(ServerPlayer player, MedicalData data,
                                                       MedicalData.Limb limb, MedicalData.LimbData ld,
                                                       ItemStack stack, int requestedDoseUnits) {
        if (!isMedicalItem(stack)) return false;

        MedicalPotency potency = stack.getOrDefault(MedicalComponents.MEDICAL_POTENCY.get(), MedicalPotency.EMPTY);
        int totalDurabilityCost = 0;
        boolean matchedTag = false;
        boolean unbindNoCost = false;

        if (isDecompressionNeedleItem(stack)) {
            matchedTag = true;
            totalDurabilityCost = applyDecompressionNeedleDose(player, data, limb, ld);
        } else if (isChestDrainKitItem(stack)) {
            matchedTag = true;
            int cost = applyChestDrainKitAction(player, data, limb, ld, stack, requestedDoseUnits);
            if (cost == UNBIND_NO_COST_SIGNAL) {
                unbindNoCost = true;
            } else {
                totalDurabilityCost = cost;
            }
        } else if (isOrthopedicKitItem(stack)) {
            matchedTag = true;
            int cost = applyOrthopedicKitAction(player, data, limb, ld, stack, requestedDoseUnits);
            if (cost == UNBIND_NO_COST_SIGNAL) {
                unbindNoCost = true;
            } else {
                totalDurabilityCost = cost;
            }
        } else if (isFasciotomyKitItem(stack)) {
            matchedTag = true;
            int cost = applyFasciotomyKitAction(player, data, limb, ld, stack, requestedDoseUnits);
            if (cost == UNBIND_NO_COST_SIGNAL) {
                unbindNoCost = true;
            } else {
                totalDurabilityCost = cost;
            }
        } else if (isBallisticExtractionKitItem(stack)) {
            matchedTag = true;
            int cost = applyBallisticExtractionKitAction(player, data, limb, ld, stack, requestedDoseUnits);
            if (cost == UNBIND_NO_COST_SIGNAL) {
                unbindNoCost = true;
            } else {
                totalDurabilityCost = cost;
            }
        } else if (isPerforationRepairKitItem(stack)) {
            matchedTag = true;
            int cost = applyPerforationRepairKitAction(player, data, limb, ld, stack, requestedDoseUnits);
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
        } else if (isActivatedCharcoalItem(stack)) {
            matchedTag = true;
            totalDurabilityCost = applyActivatedCharcoalDose(player, data);
        } else if (isAcetylcysteineItem(stack)) {
            matchedTag = true;
            totalDurabilityCost = applyAcetylcysteineDose(player, data, limb, ld, stack, requestedDoseUnits);
        } else if (isPovidoneIodineItem(stack)) {
            matchedTag = true;
            totalDurabilityCost = applyPovidoneIodineDose(player, data, limb, ld);
        } else if (isAmoxicillinItem(stack)) {
            matchedTag = true;
            totalDurabilityCost = applyAmoxicillinDose(player, data);
        } else if (isMeropenemItem(stack)) {
            matchedTag = true;
            totalDurabilityCost = applyMeropenemDose(player, data, limb, ld, stack, requestedDoseUnits);
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
