package com.FIYTP.realmedicalmod.treatment;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.capability.MedicalDataCapability;
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
 * 通用高扩展医疗用品使用路由器 (TreatmentRouter)
 *
 * 耐久与容量结算机制：
 * 1. 动态按量扣耐久：
 *    对于带有耐久度（{@code stack.isDamageableItem()}）的医疗物品（如 500 耐久的卷式绷带、1000 耐久的加压绷带），
 *    单次包扎仅扣除「实际吸收的出血量」对应的耐久值，绝不会在轻度出血时直接消耗整卷绷带。
 * 2. 残余耐久智能分段包扎：
 *    若当前绷带剩余耐久低于伤口出血量（例如绷带仅剩 180 耐久，而伤口出血 300 mL/min），
 *    该绷带会抵消 180 mL/min 出血并耗尽耐久，剩余 120 mL/min 出血可继续用下一卷绷带包扎。
 * 3. 无出血创面（开放创口/烧伤）包扎：
 *    对出血量为 0 的开放性创口或烧伤创面进行包扎保护时，仅消耗固定 50 点敷料耐久。
 */
public final class TreatmentRouter {

    /** 开放创口/烧伤（无活动性出血时）单次包扎消耗的基础敷料耐久 */
    private static final int OPEN_WOUND_DRESSING_COST = 50;

    @FunctionalInterface
    public interface LimbTreatmentHandler {
        /**
         * @return 实际消耗的耐久/容量点数（> 0 表示成功生效；<= 0 表示目标部位无需此项治疗）
         */
        int apply(ServerPlayer player, MedicalData data, MedicalData.Limb limb,
                  MedicalData.LimbData limbData, ItemStack stack, MedicalPotency potency);
    }

    private static final Map<TagKey<Item>, LimbTreatmentHandler> EFFECT_HANDLERS = new LinkedHashMap<>();

    static {
        // 1. 外出血止血 / 绷带包扎 (HEMOSTASIS) —— 按实际吸收的出血量扣除对应耐久
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
                    // 出血已完全止住，标记为已包扎并关闭开放性创口
                    ld.setBandaged(true);
                    ld.setOpenWound(false);
                    ld.setNaturalClottingTimer(0);
                    int leftAfter = Math.max(0, remainingCap - absorbed);
                    player.displayClientMessage(
                            Component.literal("已包扎 (止血 " + absorbed + " mL | 剩余 " + leftAfter + "/" + maxCap + ")")
                                    .withStyle(ChatFormatting.GREEN),
                            true
                    );
                } else {
                    // 绷带剩余耐久不足以一次性止住全部出血，抵消部分出血后当前绷带用尽
                    ld.setBandaged(false);
                    player.displayClientMessage(
                            Component.literal("部分止血 " + absorbed + " mL (仍出血 " + newBleed + " mL/m，需继续包扎)")
                                    .withStyle(ChatFormatting.GOLD),
                            true
                    );
                }
                return Math.max(1, absorbed);
            } else {
                // 出血为 0，但存在未包扎的开放创口或烧伤创面：消耗少量敷料耐久完成包扎
                int cost = Math.min(OPEN_WOUND_DRESSING_COST, remainingCap);
                ld.setBandaged(true);
                ld.setOpenWound(false);
                int leftAfter = Math.max(0, remainingCap - cost);
                player.displayClientMessage(
                        Component.literal("创面已包扎 (消耗 " + cost + " | 剩余 " + leftAfter + "/" + maxCap + ")")
                                .withStyle(ChatFormatting.GREEN),
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

        // 7. 阿片类强效镇痛 (OPIOID)
        registerEffectHandler(MedicalItemTags.OPIOID, (player, data, limb, ld, stack, potency) -> {
            float relief = potency.get(MedicalItemTags.OPIOID.location(), 7.0f);
            ld.setPainLevel(Math.max(0, ld.getPainLevel() - Math.round(relief)));
            data.setOpioidConcentration(Math.min(100, data.getOpioidConcentration() + 25));
            return 1;
        });

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
     * 判断某个物品栈是否为受本系统支持的医疗用品
     */
    public static boolean isMedicalItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.getItem() instanceof BandageItem) return true;
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

    /**
     * 获取当前玩家手持的医疗物品所在手（优先主手，其次副手），若均无则返回 null
     */
    public static InteractionHand findActiveMedicalHand(Player player) {
        if (player == null) return null;
        if (isMedicalItem(player.getMainHandItem())) return InteractionHand.MAIN_HAND;
        if (isMedicalItem(player.getOffhandItem())) return InteractionHand.OFF_HAND;
        return null;
    }

    /**
     * 判断手持的医疗物品是否可以对指定肢体产生治疗效果
     */
    public static boolean canTreatLimb(ItemStack stack, MedicalData data, MedicalData.Limb limb) {
        if (stack == null || stack.isEmpty() || data == null || limb == null) return false;
        MedicalData.LimbData ld = data.getLimb(limb);
        if (ld == null || ld.isAmputated()) return false;

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
        if (tag == MedicalItemTags.ANALGESIC || tag == MedicalItemTags.OPIOID) {
            return ld.getPainLevel() > 0;
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

    /**
     * 根据医疗物品类别决定鼠标悬停在肢体上的施救读条时长（秒）
     */
    public static float getChannelDurationSeconds(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0.65f;
        if (stack.is(MedicalItemTags.SYRINGE)) return 0.45f;
        if (stack.is(MedicalItemTags.ORAL)) return 0.50f;
        if (stack.is(MedicalItemTags.TOPICAL)) return 0.55f;
        if (stack.is(MedicalItemTags.SPLINT)) return 0.85f;
        return 0.60f;
    }

    /**
     * 获取物品的最大止血容量/耐久上限
     */
    public static int getMaxHemostasisCapacity(ItemStack stack, MedicalPotency potency) {
        if (stack == null || stack.isEmpty()) return 500;
        if (stack.isDamageableItem() && stack.getMaxDamage() > 0) {
            return stack.getMaxDamage();
        }
        float defaultCap = getDefaultHemostasisCapacity(stack);
        return Math.round(potency.get(MedicalItemTags.HEMOSTASIS.location(), defaultCap));
    }

    /**
     * 获取物品当前的剩余止血容量/耐久
     */
    public static int getRemainingHemostasisCapacity(ItemStack stack, MedicalPotency potency) {
        if (stack == null || stack.isEmpty()) return 0;
        if (stack.isDamageableItem() && stack.getMaxDamage() > 0) {
            return Math.max(0, stack.getMaxDamage() - stack.getDamageValue());
        }
        return getMaxHemostasisCapacity(stack, potency);
    }

    /**
     * 获取物品的止血剩余容量或主要效力描述文本（用于 HUD 面板与 Tooltip 展示）
     */
    public static List<String> getItemEffectSummary(ItemStack stack) {
        List<String> lines = new ArrayList<>();
        if (stack == null || stack.isEmpty()) return lines;

        MedicalPotency potency = stack.getOrDefault(MedicalComponents.MEDICAL_POTENCY.get(), MedicalPotency.EMPTY);
        if (stack.is(MedicalItemTags.HEMOSTASIS) || stack.is(MedicalItemTags.BANDAGE) || stack.getItem() instanceof BandageItem) {
            int maxCap = getMaxHemostasisCapacity(stack, potency);
            int remCap = getRemainingHemostasisCapacity(stack, potency);
            lines.add("止血 " + remCap + " / " + maxCap + " mL");
        }
        if (stack.is(MedicalItemTags.SPLINT_EFFECT)) {
            lines.add("骨折固定");
        }
        if (stack.is(MedicalItemTags.DISINFECT) || stack.is(MedicalItemTags.ANTIBIOTIC)) {
            lines.add("消毒抑菌");
        }
        if (stack.is(MedicalItemTags.ANALGESIC) || stack.is(MedicalItemTags.OPIOID)) {
            lines.add("缓解疼痛");
        }
        if (stack.is(MedicalItemTags.STERILE)) {
            lines.add("无菌");
        } else if (stack.is(MedicalItemTags.NON_STERILE)) {
            lines.add("非无菌 (感染+2%)");
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
     * 服务端执行医疗用品治疗结算（按实际治疗量扣除耐久，耗尽时才销毁物品）
     */
    public static boolean applyServerTreatment(ServerPlayer player, InteractionHand hand, MedicalData.Limb limb) {
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

        for (Map.Entry<TagKey<Item>, LimbTreatmentHandler> entry : EFFECT_HANDLERS.entrySet()) {
            if (stack.is(entry.getKey())) {
                matchedTag = true;
                int cost = entry.getValue().apply(player, data, limb, ld, stack, potency);
                if (cost > 0) {
                    totalDurabilityCost += cost;
                }
            }
        }

        // 若物品未匹配具体效果 Tag 但属于绷带类别，执行默认 HEMOSTASIS 逻辑
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

        // 处理非无菌属性副作用 (NON_STERILE -> 感染 +2)
        if (stack.is(MedicalItemTags.NON_STERILE) && !stack.is(MedicalItemTags.STERILE)) {
            ld.setInfection(Math.min(100, ld.getInfection() + 2));
        }

        // 处理物品消耗：
        // 1. 凡是具有耐久度的医疗用品（如卷式绷带 500、加压绷带 1000 或带 DURABLE 的器械），按实际消耗量扣除耐久！
        //    （即使在创造模式下测试，也会直观扣除耐久并在耗尽时损坏；生存模式下同样按量扣耐久）
        if (stack.isDamageableItem()) {
            int newDamage = stack.getDamageValue() + totalDurabilityCost;
            if (newDamage >= stack.getMaxDamage()) {
                EquipmentSlot slot = (hand == InteractionHand.MAIN_HAND) ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
                stack.hurtAndBreak(totalDurabilityCost, player, slot);
                if (!stack.isEmpty() && stack.getDamageValue() >= stack.getMaxDamage()) {
                    stack.shrink(1);
                }
            } else {
                stack.setDamageValue(newDamage);
            }
        } else if (!player.getAbilities().instabuild) {
            // 2. 无耐久度的纯单次消耗品（如单支针剂、单颗药片）才减少 1 个堆叠数量
            stack.shrink(1);
        }

        // 播放包扎/治疗完成音效并同步 MedicalData 到客户端
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 0.85f, 1.25f);
        player.syncData(MedicalDataCapability.MEDICAL_DATA);
        return true;
    }
}