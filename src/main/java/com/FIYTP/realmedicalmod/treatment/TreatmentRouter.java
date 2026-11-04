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
 * 架构特点（专为方便后续医疗用品接入而设计）：
 * 1. 标签与组件自动识别：
 *    任何物品只要继承 BandageItem、带有 MedicalItemTags 中的五类标签（BANDAGE / SYRINGE / TOPICAL / ORAL / SPLINT）
 *    或任意效果标签（HEMOSTASIS / TOURNIQUET / SPLINT_EFFECT / ANALGESIC / DISINFECT 等），
 *    即可自动激活「长按左键人体部位治疗面板」！
 * 2. 插件式效果处理器注册表 (EFFECT_HANDLERS)：
 *    已内置全部 18 种效果标签的默认处理器；后续新增特殊医疗物品时，可直接复用标签或通过
 *    TreatmentRouter.registerEffectHandler(...) 一行代码挂载自定义治疗逻辑。
 * 3. 自动处理属性标签：
 *    自动结算 STERILE（无菌）/ NON_STERILE（非无菌感染+2）以及 SINGLE_USE（单次消耗）/ DURABLE（扣耐久）。
 */
public final class TreatmentRouter {

    @FunctionalInterface
    public interface LimbTreatmentHandler {
        /**
         * @return true 表示该效果成功生效在目标肢体/全身，false 表示目标部位无需此项治疗
         */
        boolean apply(ServerPlayer player, MedicalData data, MedicalData.Limb limb,
                      MedicalData.LimbData limbData, ItemStack stack, MedicalPotency potency);
    }

    private static final Map<TagKey<Item>, LimbTreatmentHandler> EFFECT_HANDLERS = new LinkedHashMap<>();

    static {
        // 1. 外出血止血 / 绷带包扎 (HEMOSTASIS)
        registerEffectHandler(MedicalItemTags.HEMOSTASIS, (player, data, limb, ld, stack, potency) -> {
            if (ld.isAmputated()) return false;
            int oldBleed = ld.getBleeding();
            boolean needBandage = !ld.isBandaged() && (oldBleed > 0 || ld.hasOpenWound() || ld.hasBurn());
            if (oldBleed <= 0 && !needBandage) {
                return false;
            }

            float defaultCap = getDefaultHemostasisCapacity(stack);
            float capacity = potency.get(MedicalItemTags.HEMOSTASIS.location(), defaultCap);
            int absorbed = Math.min(oldBleed, Math.round(capacity));

            ld.setBleeding(Math.max(0, oldBleed - absorbed));
            ld.setBandaged(true);
            if (ld.getBleeding() == 0) {
                ld.setOpenWound(false);
                ld.setNaturalClottingTimer(0);
            }

            player.displayClientMessage(
                    Component.translatable("message.realmedicalmod.bandage.applied", absorbed)
                            .withStyle(ChatFormatting.GREEN),
                    true
            );
            return true;
        });

        // 2. 止血带强效阻断 (TOURNIQUET)
        registerEffectHandler(MedicalItemTags.TOURNIQUET, (player, data, limb, ld, stack, potency) -> {
            if (limb == MedicalData.Limb.HEAD || limb == MedicalData.Limb.CHEST || ld.isAmputated()) return false;
            if (ld.isTourniqueted() && ld.getBleeding() <= 0) return false;
            ld.setTourniqueted(true);
            ld.setBleeding(0);
            return true;
        });

        // 3. 内出血止血剂 (INTERNAL_HEMOSTASIS)
        registerEffectHandler(MedicalItemTags.INTERNAL_HEMOSTASIS, (player, data, limb, ld, stack, potency) -> {
            if (ld.getInternalBleedingRate() <= 0) return false;
            float cap = potency.get(MedicalItemTags.INTERNAL_HEMOSTASIS.location(), 150.0f);
            ld.setInternalBleedingRate(Math.max(0, ld.getInternalBleedingRate() - Math.round(cap)));
            return true;
        });

        // 4. 骨折夹板固定 (SPLINT_EFFECT)
        registerEffectHandler(MedicalItemTags.SPLINT_EFFECT, (player, data, limb, ld, stack, potency) -> {
            if (!ld.hasFracture() || ld.hasSplint() || ld.isAmputated()) return false;
            ld.setSplint(true);
            return true;
        });

        // 5. 关节脱臼复位 (REDUCTION)
        registerEffectHandler(MedicalItemTags.REDUCTION, (player, data, limb, ld, stack, potency) -> {
            if (!ld.hasDislocation() || ld.isAmputated()) return false;
            ld.setDislocation(false);
            return true;
        });

        // 6. 非阿片类局部/全身止痛 (ANALGESIC)
        registerEffectHandler(MedicalItemTags.ANALGESIC, (player, data, limb, ld, stack, potency) -> {
            if (ld.getPainLevel() <= 0) return false;
            float relief = potency.get(MedicalItemTags.ANALGESIC.location(), 4.0f);
            ld.setPainLevel(Math.max(0, ld.getPainLevel() - Math.round(relief)));
            return true;
        });

        // 7. 阿片类强效镇痛 (OPIOID)
        registerEffectHandler(MedicalItemTags.OPIOID, (player, data, limb, ld, stack, potency) -> {
            float relief = potency.get(MedicalItemTags.OPIOID.location(), 7.0f);
            ld.setPainLevel(Math.max(0, ld.getPainLevel() - Math.round(relief)));
            data.setOpioidConcentration(Math.min(100, data.getOpioidConcentration() + 25));
            return true;
        });

        // 8. 创口消毒 (DISINFECT)
        registerEffectHandler(MedicalItemTags.DISINFECT, (player, data, limb, ld, stack, potency) -> {
            InfectionTreatment.applyDisinfectant(ld);
            float clean = potency.get(MedicalItemTags.DISINFECT.location(), 15.0f);
            ld.setInfection(Math.max(0, ld.getInfection() - Math.round(clean)));
            return true;
        });

        // 9. 创口清洗 (CLEANSE)
        registerEffectHandler(MedicalItemTags.CLEANSE, (player, data, limb, ld, stack, potency) -> {
            if (ld.getInfection() <= 0 && !ld.hasOpenWound()) return false;
            float clean = potency.get(MedicalItemTags.CLEANSE.location(), 25.0f);
            ld.setInfection(Math.max(0, ld.getInfection() - Math.round(clean)));
            return true;
        });

        // 10. 抗生素抑制感染 (ANTIBIOTIC)
        registerEffectHandler(MedicalItemTags.ANTIBIOTIC, (player, data, limb, ld, stack, potency) -> {
            float clean = potency.get(MedicalItemTags.ANTIBIOTIC.location(), 35.0f);
            ld.setInfection(Math.max(0, ld.getInfection() - Math.round(clean)));
            InfectionTreatment.applyDisinfectant(ld);
            return true;
        });

        // 11. 烧伤药膏/敷料 (BURN_HEAL)
        registerEffectHandler(MedicalItemTags.BURN_HEAL, (player, data, limb, ld, stack, potency) -> {
            if (!ld.hasBurn()) return false;
            float heal = potency.get(MedicalItemTags.BURN_HEAL.location(), 10.0f);
            ld.setBurnArea(Math.max(0.0f, ld.getBurnArea() - heal));
            ld.setBandaged(true);
            return true;
        });

        // 12. 补液/输血 (BLOOD_VOLUME)
        registerEffectHandler(MedicalItemTags.BLOOD_VOLUME, (player, data, limb, ld, stack, potency) -> {
            float delta = potency.get(MedicalItemTags.BLOOD_VOLUME.location(), 500.0f);
            int nextVol = Mth.clamp(data.getBloodVolume() + Math.round(delta), 0, data.getMaxBloodVolume());
            if (nextVol == data.getBloodVolume()) return false;
            data.setBloodVolume(nextVol);
            return true;
        });

        // 13. 意识兴奋剂 (STIMULANT)
        registerEffectHandler(MedicalItemTags.STIMULANT, (player, data, limb, ld, stack, potency) -> {
            float boost = potency.get(MedicalItemTags.STIMULANT.location(), 35.0f);
            data.setConsciousness(Mth.clamp(data.getConsciousness() + Math.round(boost), 0, 100));
            return true;
        });

        // 14. 除颤复苏 (DEFIBRILLATION)
        registerEffectHandler(MedicalItemTags.DEFIBRILLATION, (player, data, limb, ld, stack, potency) -> {
            if (!data.isInVFib() && !data.isCardiacArrest()) return false;
            data.setVFib(false);
            data.setCardiacArrest(false);
            data.setHeartRate(85);
            return true;
        });

        // 15. 血胸引流 (HEMOTHORAX_DRAIN)
        registerEffectHandler(MedicalItemTags.HEMOTHORAX_DRAIN, (player, data, limb, ld, stack, potency) -> {
            if (data.getChestBloodAccumulation() <= 0.1f) return false;
            float drain = potency.get(MedicalItemTags.HEMOTHORAX_DRAIN.location(), 800.0f);
            data.setChestBloodAccumulation(Math.max(0.0f, data.getChestBloodAccumulation() - drain));
            return true;
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
     * 判断手持的医疗物品是否可以对指定肢体产生治疗效果（用于 UI 高亮推荐肢体与防误用）
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

        // 若物品仅带有 BANDAGE 类别标签或为 BandageItem（兜底按外出血/开放伤口/烧伤包扎判定）
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
        return 0.60f; // 绷带默认 0.60 秒快速顺滑包扎
    }

    /**
     * 获取物品的止血容量或主要效力描述文本（用于 HUD 面板展示）
     */
    public static List<String> getItemEffectSummary(ItemStack stack) {
        List<String> lines = new ArrayList<>();
        if (stack == null || stack.isEmpty()) return lines;

        MedicalPotency potency = stack.getOrDefault(MedicalComponents.MEDICAL_POTENCY.get(), MedicalPotency.EMPTY);
        if (stack.is(MedicalItemTags.HEMOSTASIS) || stack.is(MedicalItemTags.BANDAGE) || stack.getItem() instanceof BandageItem) {
            float cap = potency.get(MedicalItemTags.HEMOSTASIS.location(), getDefaultHemostasisCapacity(stack));
            lines.add("止血容量: " + Math.round(cap) + " mL/min");
        }
        if (stack.is(MedicalItemTags.SPLINT_EFFECT)) {
            lines.add("效果: 骨折固定愈合");
        }
        if (stack.is(MedicalItemTags.DISINFECT) || stack.is(MedicalItemTags.ANTIBIOTIC)) {
            lines.add("效果: 创口消毒抑菌");
        }
        if (stack.is(MedicalItemTags.ANALGESIC) || stack.is(MedicalItemTags.OPIOID)) {
            lines.add("效果: 缓解部位疼痛");
        }
        if (stack.is(MedicalItemTags.STERILE)) {
            lines.add("属性: 无菌敷料 (STERILE)");
        } else if (stack.is(MedicalItemTags.NON_STERILE)) {
            lines.add("属性: 非无菌 (感染风险 +2%)");
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
     * 服务端执行医疗用品治疗结算
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
        boolean anyApplied = false;
        boolean matchedTag = false;

        for (Map.Entry<TagKey<Item>, LimbTreatmentHandler> entry : EFFECT_HANDLERS.entrySet()) {
            if (stack.is(entry.getKey())) {
                matchedTag = true;
                if (entry.getValue().apply(player, data, limb, ld, stack, potency)) {
                    anyApplied = true;
                }
            }
        }

        // 若物品未匹配具体效果 Tag 但属于绷带类别，执行默认 HEMOSTASIS 逻辑
        if (!matchedTag && (stack.is(MedicalItemTags.BANDAGE) || stack.getItem() instanceof BandageItem)) {
            LimbTreatmentHandler hemo = EFFECT_HANDLERS.get(MedicalItemTags.HEMOSTASIS);
            if (hemo != null && hemo.apply(player, data, limb, ld, stack, potency)) {
                anyApplied = true;
            }
        }

        if (!anyApplied) {
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

        // 处理物品消耗（DURABLE 扣耐久，SINGLE_USE 或默认消耗 1 个）
        if (!player.getAbilities().instabuild) {
            if (stack.is(MedicalItemTags.DURABLE) && stack.isDamageableItem()) {
                EquipmentSlot slot = (hand == InteractionHand.MAIN_HAND) ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
                stack.hurtAndBreak(1, player, slot);
            } else {
                stack.shrink(1);
            }
        }

        // 播放包扎/治疗完成音效并同步 MedicalData 到客户端
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 0.85f, 1.25f);
        player.syncData(MedicalDataCapability.MEDICAL_DATA);
        return true;
    }
}