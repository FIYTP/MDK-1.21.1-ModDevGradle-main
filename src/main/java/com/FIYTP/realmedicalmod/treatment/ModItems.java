package com.FIYTP.realmedicalmod.treatment;

import com.FIYTP.realmedicalmod.RealMedicalMod;
import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.capability.MedicalDataCapability;
import com.FIYTP.realmedicalmod.capability.MedicalDataImpl;
import com.FIYTP.realmedicalmod.config.MedicalConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 模组医疗用品、注射器、止血带与状态效果注册器 (ModItems)
 */
@EventBusSubscriber(modid = RealMedicalMod.MODID)
public class ModItems {

    public static int safeCfgInt(net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<? extends Number> spec, int fallback) {
        if (spec == null) return fallback;
        try {
            return spec.get().intValue();
        } catch (Throwable t) {
            return fallback;
        }
    }

    public static double safeCfgDouble(net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<? extends Number> spec, double fallback) {
        if (spec == null) return fallback;
        try {
            return spec.get().doubleValue();
        } catch (Throwable t) {
            return fallback;
        }
    }

    public static boolean safeCfgBool(net.neoforged.neoforge.common.ModConfigSpec.BooleanValue spec, boolean fallback) {
        if (spec == null) return fallback;
        try {
            return spec.get();
        } catch (Throwable t) {
            return fallback;
        }
    }

    /** 从 MedicalConfig 获取大出血判定阈值 (mL/min) */
    public static int getCatastrophicBleedingThreshold() {
        return safeCfgInt(MedicalConfig.BANDAGE_MAX_BLEEDING_THRESHOLD, 500);
    }

    /** 从 MedicalConfig 获取止血带连续绑扎导致肢体缺血坏死的时长 (秒) */
    public static int getTourniquetNecrosisSeconds() {
        return safeCfgInt(MedicalConfig.TOURNIQUET_NECROSIS_TIME_SEC, 180);
    }

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(RealMedicalMod.MODID);

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, RealMedicalMod.MODID);

    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, RealMedicalMod.MODID);

    /** 阿片成瘾专属 MobEffect（textures/mob_effect/opioid_addiction.png） */
    public static final DeferredHolder<MobEffect, MobEffect> OPIOID_ADDICTION =
            MOB_EFFECTS.register("opioid_addiction", () -> new NamedMobEffect(MobEffectCategory.HARMFUL, 0xD500F9, "阿片成瘾"));

    /** 肾上腺素激增专属 MobEffect（textures/mob_effect/adrenaline_rush.png） */
    public static final DeferredHolder<MobEffect, MobEffect> ADRENALINE_RUSH =
            MOB_EFFECTS.register("adrenaline_rush", () -> new NamedMobEffect(MobEffectCategory.BENEFICIAL, 0xFFD600, "肾上腺素激增"));

    /** 肢体坏死专属 MobEffect（textures/mob_effect/limb_necrosis.png） */
    public static final DeferredHolder<MobEffect, MobEffect> LIMB_NECROSIS =
            MOB_EFFECTS.register("limb_necrosis", () -> new NamedMobEffect(MobEffectCategory.HARMFUL, 0x6A1B9A, "肢体坏死"));

    /** 骨筋膜室综合征专属 MobEffect（textures/mob_effect/compartment_syndrome.png） */
    public static final DeferredHolder<MobEffect, MobEffect> COMPARTMENT_SYNDROME =
            MOB_EFFECTS.register("compartment_syndrome", () -> new NamedMobEffect(MobEffectCategory.HARMFUL, 0xFF6D00, "骨筋膜室综合征"));

    /** 创伤性/张力性气胸专属 MobEffect */
    public static final DeferredHolder<MobEffect, MobEffect> PNEUMOTHORAX =
            MOB_EFFECTS.register("pneumothorax", () -> new NamedMobEffect(MobEffectCategory.HARMFUL, 0x00B0FF, "创伤性气胸"));

    public static class NamedMobEffect extends MobEffect {
        private final String displayText;

        public NamedMobEffect(MobEffectCategory category, int color, String displayText) {
            super(category, color);
            this.displayText = displayText;
        }

        @Override
        public Component getDisplayName() {
            return Component.literal(this.displayText);
        }
    }

    public static final DeferredItem<Item> BANDAGE = ITEMS.register("bandage",
            () -> new BandageItem(new Item.Properties()
                    .durability(500)
                    .component(
                            MedicalComponents.MEDICAL_POTENCY.get(),
                            MedicalPotency.of(MedicalItemTags.HEMOSTASIS.location(), 500.0f)
                    )));

    public static final DeferredItem<Item> PRESSURE_BANDAGE = ITEMS.register("pressure_bandage",
            () -> new BandageItem(new Item.Properties()
                    .durability(1000)
                    .component(
                            MedicalComponents.MEDICAL_POTENCY.get(),
                            MedicalPotency.of(MedicalItemTags.HEMOSTASIS.location(), 1000.0f)
                    )));

    public static final DeferredItem<Item> TOURNIQUET = ITEMS.register("tourniquet",
            () -> new BandageItem(new Item.Properties()
                    .durability(100)
                    .component(
                            MedicalComponents.MEDICAL_POTENCY.get(),
                            MedicalPotency.of(MedicalItemTags.TOURNIQUET.location(), 100.0f)
                    )));

    public static final DeferredItem<Item> FIELD_VASCULAR_SUTURE_KIT = ITEMS.register("field_vascular_suture_kit",
            () -> new BandageItem(new Item.Properties()
                    .durability(100)
                    .component(
                            MedicalComponents.MEDICAL_POTENCY.get(),
                            MedicalPotency.of(MedicalItemTags.INTERNAL_HEMOSTASIS.location(), 2000.0f)
                    )));

    public static final DeferredItem<Item> FASCIOTOMY_KIT = ITEMS.register("fasciotomy_kit",
            () -> new BandageItem(new Item.Properties()
                    .durability(100)
                    .component(
                            MedicalComponents.MEDICAL_POTENCY.get(),
                            MedicalPotency.of(MedicalItemTags.INTERNAL_HEMOSTASIS.location(), 1500.0f)
                    )));

    /** 野战弹道取弹包（专用于非贯穿盲管伤：探查并夹出嵌留弹头、霰弹鹿弹铅丸与破片放入右侧大号不锈钢托盘） */
    public static final DeferredItem<Item> BALLISTIC_EXTRACTION_KIT = ITEMS.register("ballistic_extraction_kit",
            () -> new BandageItem(new Item.Properties()
                    .durability(100)
                    .component(
                            MedicalComponents.MEDICAL_POTENCY.get(),
                            MedicalPotency.of(MedicalItemTags.INTERNAL_HEMOSTASIS.location(), 1200.0f)
                    )));

    /** 贯穿创道修补包（专用于前后对穿贯穿伤：双创口加压冲洗、空腔止血海绵填塞与入射/出射口双侧缝合闭合） */
    public static final DeferredItem<Item> PERFORATION_REPAIR_KIT = ITEMS.register("perforation_repair_kit",
            () -> new BandageItem(new Item.Properties()
                    .durability(100)
                    .component(
                            MedicalComponents.MEDICAL_POTENCY.get(),
                            MedicalPotency.of(MedicalItemTags.INTERNAL_HEMOSTASIS.location(), 1600.0f)
                    )));

    public static final DeferredItem<Item> MORPHINE = ITEMS.register("morphine",
            () -> new BandageItem(new Item.Properties()
                    .durability(100)
                    .component(
                            MedicalComponents.MEDICAL_POTENCY.get(),
                            MedicalPotency.of(MedicalItemTags.OPIOID.location(), 8.0f)
                    )));

    public static final DeferredItem<Item> FENTANYL = ITEMS.register("fentanyl",
            () -> new BandageItem(new Item.Properties()
                    .durability(100)
                    .component(
                            MedicalComponents.MEDICAL_POTENCY.get(),
                            MedicalPotency.of(MedicalItemTags.OPIOID.location(), 10.0f)
                    )));

    public static final DeferredItem<Item> NALOXONE = ITEMS.register("naloxone",
            () -> new BandageItem(new Item.Properties()
                    .durability(100)
                    .component(
                            MedicalComponents.MEDICAL_POTENCY.get(),
                            MedicalPotency.of(MedicalItemTags.STIMULANT.location(), 75.0f)
                    )));

    public static final DeferredItem<Item> ADRENALINE = ITEMS.register("adrenaline",
            () -> new BandageItem(new Item.Properties()
                    .durability(100)
                    .component(
                            MedicalComponents.MEDICAL_POTENCY.get(),
                            MedicalPotency.of(MedicalItemTags.STIMULANT.location(), 55.0f)
                    )));

    public static final DeferredItem<Item> SPLINT = ITEMS.register("splint",
            () -> new BandageItem(new Item.Properties()
                    .durability(100)
                    .component(
                            MedicalComponents.MEDICAL_POTENCY.get(),
                            MedicalPotency.of(MedicalItemTags.SPLINT_EFFECT.location(), 1.0f)
                    )));

    public static final DeferredItem<Item> ORTHOPEDIC_FIXATION_KIT = ITEMS.register("orthopedic_fixation_kit",
            () -> new BandageItem(new Item.Properties()
                    .durability(100)
                    .component(
                            MedicalComponents.MEDICAL_POTENCY.get(),
                            MedicalPotency.of(MedicalItemTags.SPLINT_EFFECT.location(), 2.0f)
                    )));

    public static final DeferredItem<Item> CHEST_DRAIN_KIT = ITEMS.register("chest_drain_kit",
            () -> new BandageItem(new Item.Properties()
                    .durability(100)
                    .component(
                            MedicalComponents.MEDICAL_POTENCY.get(),
                            MedicalPotency.of(MedicalItemTags.HEMOTHORAX_DRAIN.location(), 1500.0f)
                    )));

    public static final DeferredItem<Item> DECOMPRESSION_NEEDLE = ITEMS.register("decompression_needle",
            () -> new BandageItem(new Item.Properties()
                    .durability(100)
                    .component(
                            MedicalComponents.MEDICAL_POTENCY.get(),
                            MedicalPotency.of(MedicalItemTags.HEMOTHORAX_DRAIN.location(), 550.0f)
                    )));

    public static final DeferredItem<Item> IBUPROFEN = ITEMS.register("ibuprofen",
            () -> new BandageItem(new Item.Properties()
                    .durability(100)
                    .component(
                            MedicalComponents.MEDICAL_POTENCY.get(),
                            MedicalPotency.of(MedicalItemTags.ANALGESIC.location(), 2.0f)
                    )));

    public static final DeferredItem<Item> TRANEXAMIC_ACID = ITEMS.register("tranexamic_acid",
            () -> new BandageItem(new Item.Properties()
                    .durability(100)
                    .component(
                            MedicalComponents.MEDICAL_POTENCY.get(),
                            MedicalPotency.of(MedicalItemTags.INTERNAL_HEMOSTASIS.location(), 65.0f)
                    )));

    public static final DeferredItem<Item> ACTIVATED_CHARCOAL = ITEMS.register("activated_charcoal",
            () -> new BandageItem(new Item.Properties()
                    .durability(100)
                    .component(
                            MedicalComponents.MEDICAL_POTENCY.get(),
                            MedicalPotency.of(MedicalItemTags.CLEANSE.location(), 22.0f)
                    )));

    public static final DeferredItem<Item> ACETYLCYSTEINE = ITEMS.register("acetylcysteine",
            () -> new BandageItem(new Item.Properties()
                    .durability(100)
                    .component(
                            MedicalComponents.MEDICAL_POTENCY.get(),
                            MedicalPotency.of(MedicalItemTags.CLEANSE.location(), 48.0f)
                    )));

    public static final DeferredItem<Item> POVIDONE_IODINE = ITEMS.register("povidone_iodine",
            () -> new BandageItem(new Item.Properties()
                    .durability(100)
                    .component(
                            MedicalComponents.MEDICAL_POTENCY.get(),
                            MedicalPotency.of(MedicalItemTags.DISINFECT.location(), 18.0f)
                    )));

    public static final DeferredItem<Item> AMOXICILLIN = ITEMS.register("amoxicillin",
            () -> new BandageItem(new Item.Properties()
                    .durability(100)
                    .component(
                            MedicalComponents.MEDICAL_POTENCY.get(),
                            MedicalPotency.of(MedicalItemTags.ANTIBIOTIC.location(), 35.0f)
                    )));

    public static final DeferredItem<Item> MEROPENEM = ITEMS.register("meropenem",
            () -> new BandageItem(new Item.Properties()
                    .durability(100)
                    .component(
                            MedicalComponents.MEDICAL_POTENCY.get(),
                            MedicalPotency.of(MedicalItemTags.ANTIBIOTIC.location(), 70.0f)
                    )));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MEDICAL_TAB =
            CREATIVE_MODE_TABS.register("medical_tab", () -> CreativeModeTab.builder()
                    .title(Component.literal("真实医疗系统"))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> TOURNIQUET.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        for (DeferredHolder<Item, ? extends Item> entry : ITEMS.getEntries()) {
                            output.accept(entry.get());
                        }
                    })
                    .build());

    public static void register(IEventBus modEventBus) {
        MedicalComponents.DATA_COMPONENT_TYPES.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        MOB_EFFECTS.register(modEventBus);
        modEventBus.addListener(ModItems::addCreative);
    }

    private static void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES
                || event.getTabKey() == CreativeModeTabs.COMBAT) {
            for (DeferredHolder<Item, ? extends Item> entry : ITEMS.getEntries()) {
                event.accept(entry.get());
            }
        }
    }

    public static int getAddictionTier(int addictionLevel) {
        if (addictionLevel >= safeCfgInt(MedicalConfig.ADDICTION_TIER3_THRESHOLD, 80)) {
            return 3;
        }
        if (addictionLevel >= safeCfgInt(MedicalConfig.ADDICTION_TIER2_THRESHOLD, 55)) {
            return 2;
        }
        if (addictionLevel >= safeCfgInt(MedicalConfig.ADDICTION_TIER1_THRESHOLD, 25)) {
            return 1;
        }
        return 0;
    }

    public static float getAdrenalineLevel(MedicalData data) {
        if (data instanceof MedicalDataImpl impl) {
            return impl.getAdrenalineLevelFloat();
        }
        return 0.0f;
    }

    public static int getAdrenalineTier(float adrenalineLevel) {
        if (adrenalineLevel >= safeCfgInt(MedicalConfig.ADRENALINE_TIER3_THRESHOLD, 80)) {
            return 3;
        }
        if (adrenalineLevel >= safeCfgInt(MedicalConfig.ADRENALINE_TIER2_THRESHOLD, 50)) {
            return 2;
        }
        if (adrenalineLevel >= safeCfgInt(MedicalConfig.ADRENALINE_TIER1_THRESHOLD, 20)) {
            return 1;
        }
        return 0;
    }

    public static int getNaloxoneEffectSec(MedicalData data) {
        if (data instanceof MedicalDataImpl impl) {
            return impl.getNaloxoneEffectSec();
        }
        return 0;
    }

    /** 从 MedicalConfig 获取手术包所需最低清醒意识阈值 (%) */
    public static int getSutureMinConsciousness() {
        return safeCfgInt(MedicalConfig.SUTURE_MIN_CONSCIOUSNESS, 50);
    }

    /** 从 MedicalConfig 获取主干动脉断裂出血阈值 (mL/min) */
    public static int getSutureArterialThreshold() {
        return safeCfgInt(MedicalConfig.SUTURE_ARTERIAL_BLEED_THRESHOLD, 1000);
    }

    public static boolean isLimbNecrotic(MedicalData.LimbData ld) {
        if (ld instanceof MedicalDataImpl.LimbDataImpl lImpl) {
            return lImpl.isNecrotic();
        }
        return false;
    }

    public static void setLimbNecrotic(MedicalData.LimbData ld, boolean necrotic) {
        if (ld instanceof MedicalDataImpl.LimbDataImpl lImpl) {
            lImpl.setNecrotic(necrotic);
        }
    }

    public static boolean isLimbArterialLigated(MedicalData.LimbData ld) {
        if (ld instanceof MedicalDataImpl.LimbDataImpl lImpl) {
            return lImpl.isArterialLigated();
        }
        return false;
    }

    public static void setLimbArterialLigated(MedicalData.LimbData ld, boolean ligated) {
        if (ld instanceof MedicalDataImpl.LimbDataImpl lImpl) {
            lImpl.setArterialLigated(ligated);
        }
    }

    public static int getFasciotomyMinConsciousness() {
        return safeCfgInt(MedicalConfig.FASCIOTOMY_MIN_CONSCIOUSNESS, 50);
    }

    public static float getCompartmentBlood(MedicalData.LimbData ld) {
        if (ld instanceof MedicalDataImpl.LimbDataImpl lImpl) {
            return lImpl.getCompartmentBlood();
        }
        return 0.0f;
    }

    public static void setCompartmentBlood(MedicalData.LimbData ld, float ml) {
        if (ld instanceof MedicalDataImpl.LimbDataImpl lImpl) {
            lImpl.setCompartmentBlood(ml);
        }
    }

    public static float getCompartmentPressure(MedicalData.LimbData ld) {
        if (ld instanceof MedicalDataImpl.LimbDataImpl lImpl) {
            return lImpl.getCompartmentPressure();
        }
        return 4.0f;
    }

    public static void setCompartmentPressure(MedicalData.LimbData ld, float mmHg) {
        if (ld instanceof MedicalDataImpl.LimbDataImpl lImpl) {
            lImpl.setCompartmentPressure(mmHg);
        }
    }

    public static int getCompartmentTamponadedBleed(MedicalData.LimbData ld) {
        if (ld instanceof MedicalDataImpl.LimbDataImpl lImpl) {
            return lImpl.getCompartmentTamponadedBleed();
        }
        return 0;
    }

    public static void setCompartmentTamponadedBleed(MedicalData.LimbData ld, int rate) {
        if (ld instanceof MedicalDataImpl.LimbDataImpl lImpl) {
            lImpl.setCompartmentTamponadedBleed(rate);
        }
    }

    public static int getCompartmentIschemiaSec(MedicalData.LimbData ld) {
        if (ld instanceof MedicalDataImpl.LimbDataImpl lImpl) {
            return lImpl.getCompartmentIschemiaSec();
        }
        return 0;
    }

    public static void setCompartmentIschemiaSec(MedicalData.LimbData ld, int sec) {
        if (ld instanceof MedicalDataImpl.LimbDataImpl lImpl) {
            lImpl.setCompartmentIschemiaSec(sec);
        }
    }

    public static boolean isCompartmentSyndrome(MedicalData.LimbData ld) {
        if (ld instanceof MedicalDataImpl.LimbDataImpl lImpl) {
            return lImpl.isCompartmentSyndrome();
        }
        return false;
    }

    public static void setCompartmentSyndrome(MedicalData.LimbData ld, boolean cs) {
        if (ld instanceof MedicalDataImpl.LimbDataImpl lImpl) {
            lImpl.setCompartmentSyndrome(cs);
        }
    }

    public static int getOrthopedicMinConsciousness() {
        return safeCfgInt(MedicalConfig.ORTHO_MIN_CONSCIOUSNESS, 50);
    }

    public static int getChestDrainMinConsciousness() {
        return safeCfgInt(MedicalConfig.CHEST_DRAIN_MIN_CONSCIOUSNESS, 50);
    }

    // =========================================================================
    // 创伤性气胸（闭合性/开放性吸吮伤/张力性气胸）客户端-服务端自动同步数据访问接口
    // 复用 CHEST 部位未使用的骨筋膜室同步字段（因骨筋膜室综合征仅作用于四肢，CHEST 字段天然支持完整的客户端 NBT 同步）
    // =========================================================================
    // =========================================================================
    // 中毒、感染与 SIRS 阶梯药物客户端-服务端自动同步数据访问接口
    // 复用 HEAD 部位未使用的骨筋膜室同步字段（骨筋膜室仅作用于四肢，HEAD 字段天然支持完整的客户端 NBT 同步）
    // =========================================================================
    public static float getNacConcentration(MedicalData data) {
        if (data == null) return 0.0f;
        MedicalData.LimbData headLd = data.getLimb(MedicalData.Limb.HEAD);
        return headLd != null ? Mth.clamp(getCompartmentBlood(headLd), 0.0f, 100.0f) : 0.0f;
    }

    public static void setNacConcentration(MedicalData data, float conc) {
        if (data == null) return;
        MedicalData.LimbData headLd = data.getLimb(MedicalData.Limb.HEAD);
        if (headLd != null) {
            setCompartmentBlood(headLd, Mth.clamp(conc, 0.0f, 100.0f));
        }
    }

    public static float getMeropenemConcentration(MedicalData data) {
        if (data == null) return 0.0f;
        MedicalData.LimbData headLd = data.getLimb(MedicalData.Limb.HEAD);
        if (headLd == null) return 0.0f;
        return Mth.clamp(getCompartmentPressure(headLd) - 4.0f, 0.0f, 100.0f);
    }

    public static void setMeropenemConcentration(MedicalData data, float conc) {
        if (data == null) return;
        MedicalData.LimbData headLd = data.getLimb(MedicalData.Limb.HEAD);
        if (headLd != null) {
            setCompartmentPressure(headLd, 4.0f + Mth.clamp(conc, 0.0f, 100.0f));
        }
    }

    public static int getCharcoalActiveSec(MedicalData data) {
        if (data == null) return 0;
        MedicalData.LimbData headLd = data.getLimb(MedicalData.Limb.HEAD);
        return headLd != null ? Math.max(0, getCompartmentTamponadedBleed(headLd) & 0xFFF) : 0;
    }

    public static void setCharcoalActiveSec(MedicalData data, int sec) {
        if (data == null) return;
        MedicalData.LimbData headLd = data.getLimb(MedicalData.Limb.HEAD);
        if (headLd != null) {
            int raw = getCompartmentTamponadedBleed(headLd);
            int clamped = Mth.clamp(sec, 0, 4095);
            setCompartmentTamponadedBleed(headLd, (raw & ~0xFFF) | clamped);
        }
    }

    public static int getAmoxicillinOnsetSec(MedicalData data) {
        if (data == null) return 0;
        MedicalData.LimbData headLd = data.getLimb(MedicalData.Limb.HEAD);
        return headLd != null ? Math.max(0, (getCompartmentTamponadedBleed(headLd) >>> 12) & 0xFF) : 0;
    }

    public static void setAmoxicillinOnsetSec(MedicalData data, int sec) {
        if (data == null) return;
        MedicalData.LimbData headLd = data.getLimb(MedicalData.Limb.HEAD);
        if (headLd != null) {
            int raw = getCompartmentTamponadedBleed(headLd);
            int clamped = Mth.clamp(sec, 0, 255);
            setCompartmentTamponadedBleed(headLd, (raw & ~(0xFF << 12)) | (clamped << 12));
        }
    }

    public static int getAmoxicillinActiveSec(MedicalData data) {
        if (data == null) return 0;
        MedicalData.LimbData headLd = data.getLimb(MedicalData.Limb.HEAD);
        return headLd != null ? Math.max(0, getCompartmentIschemiaSec(headLd)) : 0;
    }

    public static void setAmoxicillinActiveSec(MedicalData data, int sec) {
        if (data == null) return;
        MedicalData.LimbData headLd = data.getLimb(MedicalData.Limb.HEAD);
        if (headLd != null) {
            setCompartmentIschemiaSec(headLd, Math.max(0, sec));
        }
    }

    public static int getTotalInfection(MedicalData data) {
        if (data == null) return 0;
        int sum = 0;
        for (MedicalData.Limb l : MedicalData.Limb.values()) {
            MedicalData.LimbData ld = data.getLimb(l);
            if (ld != null && !ld.isAmputated()) {
                sum += ld.getInfection();
            }
        }
        return sum;
    }

    public static float getPleuralAirMl(MedicalData data) {
        if (data == null) return 0.0f;
        MedicalData.LimbData chestLd = data.getLimb(MedicalData.Limb.CHEST);
        return chestLd != null ? Math.max(0.0f, getCompartmentBlood(chestLd)) : 0.0f;
    }

    public static void setPleuralAirMl(MedicalData data, float airMl) {
        if (data == null) return;
        MedicalData.LimbData chestLd = data.getLimb(MedicalData.Limb.CHEST);
        if (chestLd != null) {
            setCompartmentBlood(chestLd, Mth.clamp(airMl, 0.0f, 1500.0f));
        }
    }

    public static float getPleuralPressureCmH2O(MedicalData data) {
        if (data == null) return -6.0f;
        MedicalData.LimbData chestLd = data.getLimb(MedicalData.Limb.CHEST);
        if (chestLd == null) return -6.0f;
        float air = getPleuralAirMl(data);
        if (air <= 1.0f && getPneumothoraxType(data) == 0) return -6.0f;
        return getCompartmentPressure(chestLd);
    }

    public static void setPleuralPressureCmH2O(MedicalData data, float cmH2O) {
        if (data == null) return;
        MedicalData.LimbData chestLd = data.getLimb(MedicalData.Limb.CHEST);
        if (chestLd != null) {
            setCompartmentPressure(chestLd, Mth.clamp(cmH2O, -8.0f, 38.0f));
        }
    }

    /**
     * 气胸临床类型：0 = 无气胸, 1 = 闭合性气胸, 2 = 开放性气胸(吸吮性胸壁伤口), 3 = 张力性气胸(高压梗阻性休克)
     */
    public static int getPneumothoraxType(MedicalData data) {
        if (data == null) return 0;
        MedicalData.LimbData chestLd = data.getLimb(MedicalData.Limb.CHEST);
        return chestLd != null ? (getCompartmentTamponadedBleed(chestLd) & 0x7) : 0;
    }

    public static void setPneumothoraxType(MedicalData data, int type) {
        if (data == null) return;
        MedicalData.LimbData chestLd = data.getLimb(MedicalData.Limb.CHEST);
        if (chestLd != null) {
            int raw = getCompartmentTamponadedBleed(chestLd);
            setCompartmentTamponadedBleed(chestLd, (raw & ~0x7) | (Mth.clamp(type, 0, 3) & 0x7));
        }
    }

    /** 是否已使用加压绷带完成「三边单向阀封堵 (Three-Sided Occlusive Dressing)」 */
    public static boolean isThreeSidedValveSealed(MedicalData data) {
        if (data == null) return false;
        MedicalData.LimbData chestLd = data.getLimb(MedicalData.Limb.CHEST);
        return chestLd != null && ((getCompartmentTamponadedBleed(chestLd) & 0x8) != 0);
    }

    public static void setThreeSidedValveSealed(MedicalData data, boolean sealed) {
        if (data == null) return;
        MedicalData.LimbData chestLd = data.getLimb(MedicalData.Limb.CHEST);
        if (chestLd != null) {
            int raw = getCompartmentTamponadedBleed(chestLd);
            setCompartmentTamponadedBleed(chestLd, sealed ? (raw | 0x8) : (raw & ~0x8));
        }
    }

    /** 是否因误用普通粗糙绷带死封未排气胸口而处于「死封憋气恶化」状态 */
    public static boolean isImproperlyDeadSealed(MedicalData data) {
        if (data == null) return false;
        MedicalData.LimbData chestLd = data.getLimb(MedicalData.Limb.CHEST);
        return chestLd != null && ((getCompartmentTamponadedBleed(chestLd) & 0x10) != 0);
    }

    public static void setImproperlyDeadSealed(MedicalData data, boolean deadSealed) {
        if (data == null) return;
        MedicalData.LimbData chestLd = data.getLimb(MedicalData.Limb.CHEST);
        if (chestLd != null) {
            int raw = getCompartmentTamponadedBleed(chestLd);
            setCompartmentTamponadedBleed(chestLd, deadSealed ? (raw | 0x10) : (raw & ~0x10));
        }
    }

    /** 14G 胸腔穿刺减压针留置单向阀维持的安全减压窗口秒数 */
    public static int getDecompressionWindowSec(MedicalData data) {
        if (data == null) return 0;
        MedicalData.LimbData chestLd = data.getLimb(MedicalData.Limb.CHEST);
        return chestLd != null ? Math.max(0, getCompartmentIschemiaSec(chestLd)) : 0;
    }

    public static void setDecompressionWindowSec(MedicalData data, int seconds) {
        if (data == null) return;
        MedicalData.LimbData chestLd = data.getLimb(MedicalData.Limb.CHEST);
        if (chestLd != null) {
            setCompartmentIschemiaSec(chestLd, Math.max(0, seconds));
        }
    }

    /**
     * 完成「胸腔闭式引流包 (chest_drain_kit)」手术结算：
     * - 彻底抽吸排空胸膜腔积血 (chestBloodAccumulation = 0, hemothoraxLevel = 0) 与全部胸膜腔积气 (pleuralAirMl = 0)
     * - 恢复胸膜腔生理负压 (-6.0 cmH2O)，根治张力性气胸、开放性气胸与血气胸
     * - 电凝止住肋间血管内出血，并因肺叶复张立即减轻一部分急性肺水肿/肺挫伤负荷
     */
    public static void applyChestDrainSurgerySuccess(MedicalData data, MedicalData.LimbData chestLd) {
        if (data == null) return;
        data.setChestBloodAccumulation(0.0f);
        data.setHemothoraxLevel(0);
        data.setHemothoraxVomitTimer(0);
        setPleuralAirMl(data, 0.0f);
        setPleuralPressureCmH2O(data, -6.0f);
        setPneumothoraxType(data, 0);
        setThreeSidedValveSealed(data, false);
        setImproperlyDeadSealed(data, false);
        setDecompressionWindowSec(data, 0);
        if (data.getLungContusion() > 0.0f) {
            data.setLungContusion(Math.max(0.0f, data.getLungContusion() - 18.0f));
        }
        if (chestLd != null) {
            chestLd.setInternalBleedingRate(0);
            chestLd.setOpenWound(false);
            chestLd.setBandaged(true);
            chestLd.setPainLevel(Math.max(1, chestLd.getPainLevel() - 2));
        }
    }

    public static boolean isLimbOrthopedicFixed(MedicalData.LimbData ld) {
        if (ld instanceof MedicalDataImpl.LimbDataImpl lImpl) {
            return lImpl.isOrthopedicFixed();
        }
        return false;
    }

    public static void setLimbOrthopedicFixed(MedicalData.LimbData ld, boolean fixed) {
        if (ld instanceof MedicalDataImpl.LimbDataImpl lImpl) {
            lImpl.setOrthopedicFixed(fixed);
        }
    }

    public static boolean isChestBraced(MedicalData.LimbData ld) {
        if (ld instanceof MedicalDataImpl.LimbDataImpl lImpl) {
            return lImpl.isChestBraced();
        }
        return false;
    }

    public static void setChestBraced(MedicalData.LimbData ld, boolean braced) {
        if (ld instanceof MedicalDataImpl.LimbDataImpl lImpl) {
            lImpl.setChestBraced(braced);
        }
    }

    public static int getDelayedHemothoraxStrain(MedicalData.LimbData ld) {
        if (ld instanceof MedicalDataImpl.LimbDataImpl lImpl) {
            return lImpl.getDelayedHemothoraxStrain();
        }
        return 0;
    }

    public static void setDelayedHemothoraxStrain(MedicalData.LimbData ld, int strain) {
        if (ld instanceof MedicalDataImpl.LimbDataImpl lImpl) {
            lImpl.setDelayedHemothoraxStrain(strain);
        }
    }

    public static void setFractureTimerAuth(MedicalData.LimbData ld, int seconds) {
        if (ld instanceof MedicalDataImpl.LimbDataImpl lImpl) {
            lImpl.setFractureTimerAuthoritative(seconds);
        } else if (ld != null) {
            ld.setFractureTimer(seconds);
        }
    }

    /**
     * 调用已有骨折固定与愈合接口（兼容 FractureTreatment 与 LimbData#setSplint / setFractureTimer）
     */
    public static void applySplintAndInitFractureHealing(MedicalData data, MedicalData.LimbData ld) {
        if (ld == null || !ld.hasFracture() || ld.isAmputated()) return;
        ld.setSplint(true);
        int healSec = safeCfgInt(MedicalConfig.SPLINT_FRACTURE_HEAL_TIME_SEC, 300);
        if (ld.getFractureTimer() <= 0 || ld.getFractureTimer() > healSec) {
            setFractureTimerAuth(ld, healSec);
        }
        int painRelief = safeCfgInt(MedicalConfig.SPLINT_PAIN_RELIEF, 2);
        if (painRelief > 0 && ld.getPainLevel() > 0) {
            ld.setPainLevel(Math.max(1, ld.getPainLevel() - painRelief));
        }
        tryInvokeFractureTreatmentMethod("applySplint", ld);
    }

    /**
     * 骨科复位固定手术完成结算（外固定支架 / 钢板内固定 / 胸廓镍钛记忆合金肋骨环抱器 SSRF）：
     * - 彻底清创消除开放性骨折 (openFracture = false) 与关节脱位错位 (dislocation = false)
     * - 锁定骨断端 (orthopedicFixed = true)，解除断骨对血管的切割压迫，并开启加速愈合倒计时
     */
    public static void applyOrthopedicSurgerySuccess(MedicalData data, MedicalData.Limb limb, MedicalData.LimbData ld) {
        if (ld == null || ld.isAmputated()) return;
        ld.setFracture(true);
        ld.setOpenFracture(false);
        ld.setDislocation(false);
        ld.setOpenWound(false);
        ld.setBandaged(true);
        setLimbOrthopedicFixed(ld, true);
        if (limb == MedicalData.Limb.CHEST) {
            setChestBraced(ld, true);
            setDelayedHemothoraxStrain(ld, 0);
            if (data != null && data.getLungContusion() > 0.0f) {
                data.setLungContusion(Math.max(0.0f, data.getLungContusion() - 12.0f));
            }
        }
        if (ld.getInternalBleedingRate() > 0) {
            ld.setInternalBleedingRate(Math.max(0, ld.getInternalBleedingRate() - 80));
        }
        int orthoHealSec = safeCfgInt(MedicalConfig.ORTHO_SURGERY_HEAL_TIME_SEC, 210);
        setFractureTimerAuth(ld, orthoHealSec);
        ld.setPainLevel(Math.max(1, ld.getPainLevel() - 3));
        tryInvokeFractureTreatmentMethod("applySplint", ld);
    }

    /**
     * 完成骨折愈合时调用已有恢复接口
     */
    public static void completeFractureHealing(MedicalData.LimbData ld) {
        if (ld == null) return;
        ld.setFracture(false);
        ld.setOpenFracture(false);
        ld.setDislocation(false);
        ld.setSplint(false);
        setLimbOrthopedicFixed(ld, false);
        setChestBraced(ld, false);
        setDelayedHemothoraxStrain(ld, 0);
        setFractureTimerAuth(ld, 0);
        tryInvokeFractureTreatmentMethod("healFracture", ld);
    }

    private static void tryInvokeFractureTreatmentMethod(String methodName, MedicalData.LimbData ld) {
        try {
            Class<?> cls = Class.forName("com.FIYTP.realmedicalmod.treatment.FractureTreatment");
            for (java.lang.reflect.Method m : cls.getDeclaredMethods()) {
                if (m.getName().equalsIgnoreCase(methodName) && m.getParameterCount() == 1
                        && m.getParameterTypes()[0].isAssignableFrom(ld.getClass())) {
                    m.setAccessible(true);
                    m.invoke(null, ld);
                    return;
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public static int getTourniquetTimer(MedicalData.LimbData ld) {
        if (ld instanceof MedicalDataImpl.LimbDataImpl lImpl) {
            return lImpl.getTourniquetTimer();
        }
        return 0;
    }

    public static void setTourniquetTimer(MedicalData.LimbData ld, int seconds) {
        if (ld instanceof MedicalDataImpl.LimbDataImpl lImpl) {
            lImpl.setTourniquetTimer(seconds);
        }
    }

    public static int getTourniquetClampedBleed(MedicalData.LimbData ld) {
        if (ld instanceof MedicalDataImpl.LimbDataImpl lImpl) {
            return lImpl.getTourniquetClampedBleed();
        }
        return 0;
    }

    public static void setTourniquetClampedBleed(MedicalData.LimbData ld, int bleed) {
        if (ld instanceof MedicalDataImpl.LimbDataImpl lImpl) {
            lImpl.setTourniquetClampedBleed(bleed);
        }
    }

    public static int getEmbeddedBulletCount(MedicalData.LimbData ld) {
        if (ld instanceof MedicalDataImpl.LimbDataImpl lImpl) {
            return lImpl.getEmbeddedBulletCount();
        }
        return 0;
    }

    public static void setEmbeddedBulletCount(MedicalData.LimbData ld, int count) {
        if (ld instanceof MedicalDataImpl.LimbDataImpl lImpl) {
            lImpl.setEmbeddedBulletCount(count);
        }
    }

    public static boolean isPerforationPacked(MedicalData.LimbData ld) {
        if (ld instanceof MedicalDataImpl.LimbDataImpl lImpl) {
            return lImpl.isPerforationPacked();
        }
        return false;
    }

    public static void setPerforationPacked(MedicalData.LimbData ld, boolean packed) {
        if (ld instanceof MedicalDataImpl.LimbDataImpl lImpl) {
            lImpl.setPerforationPacked(packed);
        }
    }

    public static boolean hasAnyNecroticLimb(MedicalData data) {
        if (data == null) return false;
        for (MedicalData.Limb limb : MedicalData.Limb.values()) {
            MedicalData.LimbData ld = data.getLimb(limb);
            if (ld != null && !ld.isAmputated() && isLimbNecrotic(ld)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasAnyTourniquetedLimb(MedicalData data) {
        if (data == null) return false;
        for (MedicalData.Limb limb : MedicalData.Limb.values()) {
            MedicalData.LimbData ld = data.getLimb(limb);
            if (ld != null && ld.isTourniqueted()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 火焰/熔岩/灼热伤害触发时立即加速部位烧伤面积、度数与痛感上涨
     */
    public static void applyThermalBurnImpulse(ServerPlayer player, MedicalData data, String msgId, float rawDamage) {
        if (player == null || data == null) return;
        float mult = ((float) safeCfgDouble(MedicalConfig.BURN_RATE_MULTIPLIER, 4.5));
        boolean isLava = "lava".equals(msgId);
        boolean isHotFloor = "hotFloor".equals(msgId);
        float baseGain = isLava
                ? ((float) safeCfgDouble(MedicalConfig.BURN_LAVA_AREA_GAIN_PER_SEC, 12.5)) * 0.65f
                : (isHotFloor
                        ? ((float) safeCfgDouble(MedicalConfig.BURN_FIRE_AREA_GAIN_PER_SEC, 4.2)) * 0.45f
                        : ((float) safeCfgDouble(MedicalConfig.BURN_FIRE_AREA_GAIN_PER_SEC, 4.2)) * 0.55f);
        float areaDelta = Math.max(0.8f, baseGain * (0.5f + 0.25f * rawDamage) * (mult / 4.0f));
        int maxPain = safeCfgInt(MedicalConfig.MAX_PAIN_LEVEL, 10);

        for (MedicalData.Limb limb : MedicalData.Limb.values()) {
            MedicalData.LimbData ld = data.getLimb(limb);
            if (ld == null) continue;
            float w = 1.0f;
            if (isHotFloor) {
                w = (limb == MedicalData.Limb.LEFT_LEG || limb == MedicalData.Limb.RIGHT_LEG) ? 1.8f : 0.0f;
            } else if (isLava) {
                w = (limb == MedicalData.Limb.HEAD) ? 0.75f : 1.25f;
            } else {
                w = (limb == MedicalData.Limb.CHEST || limb == MedicalData.Limb.LEFT_ARM || limb == MedicalData.Limb.RIGHT_ARM) ? 1.15f : 0.85f;
            }
            if (w <= 0.0f) continue;

            float addArea = areaDelta * w;
            float nextArea = Mth.clamp(ld.getBurnArea() + addArea, 0.0f, 100.0f);
            ld.setBurnArea(nextArea);
            ld.setBurnLoad(ld.getBurnLoad() + addArea * 1.4f);
            ld.setBurnConv(ld.getBurnConv() + addArea * 1.2f);
            ld.setBurnHeatTimer(Math.max(ld.getBurnHeatTimer(), isLava ? 12 : 6));
            ld.setBandaged(false);

            int newDeg = computeBurnDegreeFromAreaAndConv(nextArea, ld.getBurnConv(), isLava);
            if (newDeg > ld.getBurnDegree()) {
                ld.setBurnDegree(newDeg);
            }
            int minBurnPain = Math.min(maxPain, Math.max(2, newDeg * 2 + (nextArea >= 25.0f ? 1 : 0)));
            if (ld.getPainLevel() < minBurnPain) {
                ld.setPainLevel(minBurnPain);
            }
        }
        recalcTotalBurnTBSA(data);
        player.syncData(MedicalDataCapability.MEDICAL_DATA);
    }

    public static int computeBurnDegreeFromAreaAndConv(float area, float conv, boolean inLava) {
        float score = Math.max(area, conv * 1.5f) + (inLava ? 12.0f : 0.0f);
        if (score >= 38.0f) return 4;
        if (score >= 20.0f) return 3;
        if (score >= 7.5f) return 2;
        if (score > 0.05f) return 1;
        return 0;
    }

    public static void recalcTotalBurnTBSA(MedicalData data) {
        if (data == null) return;
        float totalTbsa = 0.0f;
        for (MedicalData.Limb limb : MedicalData.Limb.values()) {
            MedicalData.LimbData ld = data.getLimb(limb);
            if (ld == null || !ld.hasBurn()) continue;
            float limbWeight = switch (limb) {
                case HEAD -> 0.09f;
                case CHEST -> 0.36f;
                case LEFT_ARM, RIGHT_ARM -> 0.09f;
                case LEFT_LEG, RIGHT_LEG -> 0.18f;
            };
            totalTbsa += Mth.clamp(ld.getBurnArea(), 0.0f, 100.0f) * limbWeight;
        }
        data.setBurnTBSA(Mth.clamp(totalTbsa, 0.0f, 100.0f));
    }

    /**
     * 判断玩家当前是否处于阿片戒断或纳洛酮促发急性戒断行动受限状态
     */
    public static boolean isInWithdrawalMovementRestriction(MedicalData data) {
        if (data == null) return false;
        if (getNaloxoneEffectSec(data) > 0 && data.getAddictionLevel() >= safeCfgInt(MedicalConfig.ADDICTION_TIER1_THRESHOLD, 25)) {
            return true;
        }
        int tier = getAddictionTier(data.getAddictionLevel());
        if (tier < 2) return false;
        if (getAdrenalineTier(getAdrenalineLevel(data)) >= 1) return false;
        return data.getOpioidConcentration() < safeCfgInt(MedicalConfig.ADDICTION_WITHDRAWAL_OPIOID_THRESHOLD, 10);
    }

    /**
     * 玩家受伤时触发短暂（约 3~4 秒）的内源性「肾上腺素激增 I」应激 Buff
     */
    public static void triggerTraumaAdrenalineRush(ServerPlayer player, MedicalData data, float rawDamage) {
        if (player == null || !(data instanceof MedicalDataImpl impl)) return;
        if (!safeCfgBool(MedicalConfig.ADRENALINE_TRAUMA_ENABLED, true)) return;
        if (data.getConsciousness() <= 0 || data.isCardiacArrest()) return;

        float minDmg = ((float) safeCfgDouble(MedicalConfig.ADRENALINE_TRAUMA_MIN_DAMAGE, 4.0));
        if (rawDamage < minDmg) return;

        if (impl.getAdrenalineCooldownSec() > 0 || impl.isAdrenalineFromInjector()) return;

        float traumaGain = ((float) safeCfgDouble(MedicalConfig.ADRENALINE_TRAUMA_GAIN, 35.0));
        float nextAdr = Math.max(impl.getAdrenalineLevelFloat(), traumaGain);
        impl.setAdrenalineLevelFloat(nextAdr);
        impl.setAdrenalineCooldownSec(safeCfgInt(MedicalConfig.ADRENALINE_TRAUMA_COOLDOWN_SEC, 45));
        impl.setAdrenalineCrashSec(0);

        int durSec = safeCfgInt(MedicalConfig.ADRENALINE_TRAUMA_DURATION_SEC, 20);
        player.addEffect(new MobEffectInstance(
                ADRENALINE_RUSH,
                durSec * 20,
                0,
                false,
                false,
                true
        ));
        player.syncData(MedicalDataCapability.MEDICAL_DATA);
    }

    public static void syncAdrenalineEffectNow(ServerPlayer player, MedicalData data) {
        if (player == null || data == null) return;
        float adr = getAdrenalineLevel(data);
        int adrTier = getAdrenalineTier(adr);
        if (adrTier > 0) {
            int desiredAmp = adrTier - 1;
            boolean fromInj = (data instanceof MedicalDataImpl impl) && impl.isAdrenalineFromInjector();
            float tier1Floor = ((float) safeCfgDouble(MedicalConfig.ADRENALINE_TIER1_THRESHOLD, 20));
            float traumaDecay = ((float) safeCfgDouble(MedicalConfig.ADRENALINE_TRAUMA_GAIN, 35.0))
                    / Math.max(1.0f, ((float) safeCfgDouble(MedicalConfig.ADRENALINE_TRAUMA_DURATION_SEC, 20)));
            float decayRate = fromInj
                    ? Math.max(0.2f, ((float) safeCfgDouble(MedicalConfig.ADRENALINE_DECAY_RATE_PER_SEC, 1.8)))
                    : Math.max(0.5f, traumaDecay);
            int estRemainingSec = Math.max(1, Math.round(Math.max(1.0f, adr - tier1Floor + 2.0f) / decayRate));
            int ticks = estRemainingSec * 20;
            MobEffectInstance cur = player.getEffect(ADRENALINE_RUSH);
            if (cur == null || cur.getAmplifier() != desiredAmp || cur.getDuration() > ticks + 25 || cur.getDuration() < 15) {
                player.addEffect(new MobEffectInstance(
                        ADRENALINE_RUSH,
                        ticks,
                        desiredAmp,
                        false,
                        false,
                        true
                ));
            }
        } else if (player.hasEffect(ADRENALINE_RUSH)) {
            player.removeEffect(ADRENALINE_RUSH);
        }
    }

    /**
     * 判断单条下肢是否处于完全丧失站立支撑能力的状态（截肢、未上夹板且未做骨科外固定的骨折、或肢体坏死）
     */
    public static boolean isLegUnableToStand(MedicalData.LimbData leg) {
        if (leg == null) return false;
        if (leg.isAmputated()) return true;
        if (isLimbNecrotic(leg)) return true;
        if (leg.hasFracture() && !leg.hasSplint() && !isLimbOrthopedicFixed(leg)) {
            return true;
        }
        return false;
    }

    // ================= Mod 本体原生受创摔倒 (Knockdown) 与无提示物理脱手 (Silent Disarm) 状态表 =================
    private static final Map<UUID, Integer> SERVER_KNOCKDOWN_TICKS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_DISARM_TICK = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_CORE_DAMAGE_SETTLED_TICK = new ConcurrentHashMap<>();
    private static volatile boolean legacyHandlersCleanedUp = false;
    private static int clientKnockdownMirrorTicks = 0;

    public static boolean isPlayerTemporarilyKnockedDown(Player player) {
        if (player == null) return false;
        if (player.level().isClientSide()) {
            return clientKnockdownMirrorTicks > 0;
        }
        Integer ticks = SERVER_KNOCKDOWN_TICKS.get(player.getUUID());
        return ticks != null && ticks > 0;
    }

    public static int getRemainingKnockdownTicks(Player player) {
        if (player == null) return 0;
        if (player.level().isClientSide()) {
            return clientKnockdownMirrorTicks;
        }
        Integer ticks = SERVER_KNOCKDOWN_TICKS.get(player.getUUID());
        return ticks != null ? Math.max(0, ticks) : 0;
    }

    /**
     * 判断玩家是否因双腿同时失能或昏迷/心脏骤停而必须持续趴下（不含单腿受创暂时摔倒）
     */
    public static boolean isPermanentlyProneByInjury(Player player, MedicalData data) {
        if (player == null || data == null || player.isCreative() || player.isSpectator() || player.isPassenger()) {
            return false;
        }
        if (data.getConsciousness() <= 0 || data.isCardiacArrest()) {
            return true;
        }
        if (!safeCfgBool(MedicalConfig.AMPUTATION_DOUBLE_LEG_PRONE, true)) {
            return false;
        }
        MedicalData.LimbData leftLeg = data.getLimb(MedicalData.Limb.LEFT_LEG);
        MedicalData.LimbData rightLeg = data.getLimb(MedicalData.Limb.RIGHT_LEG);
        return isLegUnableToStand(leftLeg) && isLegUnableToStand(rightLeg);
    }

    /**
     * 判断玩家是否必须被强制锁定为匍匐/摔倒姿态 (Pose.SWIMMING)：
     * 1. 双腿同时丧失站立支撑能力（双腿均骨折未固定、双腿截肢、一腿截肢一腿骨折未固定、或双腿坏死）；
     * 2. 玩家处于意识归零昏迷 / 心脏骤停倒地状态；
     * 3. 玩家因单腿受重伤、大口径命中、高处坠落或爆炸冲击处于「本体暂时摔倒 (Temporary Knockdown)」倒计时期间！
     */
    public static boolean shouldForcePlayerProne(net.minecraft.world.entity.player.Player player, MedicalData data) {
        if (player == null || data == null || player.isCreative() || player.isSpectator() || player.isPassenger()) {
            return false;
        }
        if (isPermanentlyProneByInjury(player, data)) {
            return true;
        }
        return isPlayerTemporarilyKnockedDown(player);
    }

    /**
     * 客户端 + 服务端双端每 Tick (Pre & Post) 强制锁定匍匐/摔倒姿态：
     * 同时调用 setForcedPose(Pose.SWIMMING) 与 setPose(Pose.SWIMMING)，彻底根除原版 Player#updatePlayerPose()
     * 在第 2~19 Tick 将玩家重置回站立状态导致的“时而趴着时而不趴”状态不稳定闪烁 Bug，
     * 并确保单腿中弹/受击暂时摔倒期间绝不会被意外清零，倒计时结束后自动站起！
     */
    public static void enforceBilateralLegIncapacitationProne(net.minecraft.world.entity.player.Player player) {
        if (player == null || !player.isAlive()) return;
        MedicalData data = player.getData(MedicalDataCapability.MEDICAL_DATA);
        if (data == null) return;

        boolean forceProne = shouldForcePlayerProne(player, data);
        if (forceProne) {
            if (player.isSprinting()) {
                player.setSprinting(false);
            }
            if (player.getForcedPose() != net.minecraft.world.entity.Pose.SWIMMING) {
                player.setForcedPose(net.minecraft.world.entity.Pose.SWIMMING);
            }
            if (player.getPose() != net.minecraft.world.entity.Pose.SWIMMING) {
                player.setPose(net.minecraft.world.entity.Pose.SWIMMING);
                player.refreshDimensions();
            }
        } else {
            if (player.getForcedPose() == net.minecraft.world.entity.Pose.SWIMMING) {
                player.setForcedPose(null);
                player.setPose(net.minecraft.world.entity.Pose.STANDING);
                player.refreshDimensions();
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTickEnforcePronePre(PlayerTickEvent.Pre event) {
        Player player = event.getEntity();
        if (player != null && !player.level().isClientSide() && player.isAlive()) {
            UUID uid = player.getUUID();
            Integer rem = SERVER_KNOCKDOWN_TICKS.get(uid);
            if (rem != null && rem > 0) {
                int next = rem - 1;
                if (next <= 0) {
                    SERVER_KNOCKDOWN_TICKS.remove(uid);
                    MedicalData data = player.getData(MedicalDataCapability.MEDICAL_DATA);
                    if (!isPermanentlyProneByInjury(player, data)) {
                        player.setForcedPose(null);
                        player.setPose(Pose.STANDING);
                        player.refreshDimensions();
                        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                                SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 0.70f, 1.08f);
                    }
                } else {
                    SERVER_KNOCKDOWN_TICKS.put(uid, next);
                }
            }
        }
        enforceBilateralLegIncapacitationProne(player);
    }

    @SubscribeEvent
    public static void onPlayerTickSyncAddictionBuff(PlayerTickEvent.Post event) {
        enforceBilateralLegIncapacitationProne(event.getEntity());
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.tickCount % 20 != 0) return;

        MedicalData data = player.getData(MedicalDataCapability.MEDICAL_DATA);
        if (data == null) return;

        boolean dirty = false;

        // 0. 每秒固定衰减肾上腺素浓度、冷却与纳洛酮拮抗计时
        if (data instanceof MedicalDataImpl adrImpl) {
            if (adrImpl.getAdrenalineCooldownSec() > 0) {
                adrImpl.setAdrenalineCooldownSec(adrImpl.getAdrenalineCooldownSec() - 1);
                dirty = true;
            }
            if (adrImpl.getAdrenalineCrashSec() > 0 && adrImpl.getAdrenalineLevelFloat() <= 0.0f) {
                adrImpl.setAdrenalineCrashSec(adrImpl.getAdrenalineCrashSec() - 1);
                dirty = true;
            }
            if (adrImpl.getNaloxoneEffectSec() > 0) {
                adrImpl.setNaloxoneEffectSec(adrImpl.getNaloxoneEffectSec() - 1);
                dirty = true;
            }
            float curAdr = adrImpl.getAdrenalineLevelFloat();
            if (curAdr > 0.0f) {
                boolean fromInj = adrImpl.isAdrenalineFromInjector();
                float traumaDecay = ((float) safeCfgDouble(MedicalConfig.ADRENALINE_TRAUMA_GAIN, 35.0))
                        / Math.max(1.0f, ((float) safeCfgDouble(MedicalConfig.ADRENALINE_TRAUMA_DURATION_SEC, 20)));
                float adrDecay = fromInj
                        ? ((float) safeCfgDouble(MedicalConfig.ADRENALINE_DECAY_RATE_PER_SEC, 1.8))
                        : traumaDecay;
                float nextAdr = Math.max(0.0f, curAdr - adrDecay);
                if (!fromInj && nextAdr < safeCfgInt(MedicalConfig.ADRENALINE_TIER1_THRESHOLD, 20)) {
                    nextAdr = 0.0f;
                }
                adrImpl.setAdrenalineLevelFloat(nextAdr);
                if (nextAdr <= 0.0f && fromInj) {
                    adrImpl.setAdrenalineFromInjector(false);
                    adrImpl.setAdrenalineCrashSec(safeCfgInt(MedicalConfig.ADRENALINE_CRASH_DURATION_SEC, 25));
                }
                dirty = true;
            }
        }

        // 1. 止血带超时缺血 -> 肢体坏死判定 (仅四肢可绑止血带)
        int necroticCount = 0;
        for (MedicalData.Limb limb : MedicalData.Limb.values()) {
            MedicalData.LimbData ld = data.getLimb(limb);
            if (ld == null) continue;
            if (ld.isAmputated()) {
                // 断肢残端可绑止血带或直接结扎血管，但因为肢体已经没了，绝不会累积缺血时间或造成肢体坏死
                if (isLimbNecrotic(ld)) {
                    setLimbNecrotic(ld, false);
                    dirty = true;
                }
                if (isLimbArterialLigated(ld)) {
                    setLimbArterialLigated(ld, false);
                    dirty = true;
                }
                if (getTourniquetTimer(ld) > 0) {
                    setTourniquetTimer(ld, 0);
                    dirty = true;
                }
                continue;
            }

            if (ld.isTourniqueted() || isLimbArterialLigated(ld)) {
                int tqSec = getTourniquetTimer(ld) + 1;
                setTourniquetTimer(ld, tqSec);
                dirty = true;

                // 缺血剧痛递进（由 MedicalConfig 驱动）
                int stage1Sec = safeCfgInt(MedicalConfig.TOURNIQUET_ISCHEMIA_STAGE1_SEC, 60);
                int stage2Sec = safeCfgInt(MedicalConfig.TOURNIQUET_ISCHEMIA_STAGE2_SEC, 120);
                int necrosisSec = safeCfgInt(MedicalConfig.TOURNIQUET_NECROSIS_TIME_SEC, 180);
                int maxPain = safeCfgInt(MedicalConfig.MAX_PAIN_LEVEL, 10);
                if (tqSec >= stage1Sec && tqSec < stage2Sec && ld.getPainLevel() < (maxPain * 6 / 10)) {
                    ld.setPainLevel(maxPain * 6 / 10);
                } else if (tqSec >= stage2Sec && ld.getPainLevel() < (maxPain * 8 / 10)) {
                    ld.setPainLevel(maxPain * 8 / 10);
                }

                // 绑扎达到配置时长触发不可逆肢体坏死
                if (tqSec >= necrosisSec && !isLimbNecrotic(ld)) {
                    setLimbNecrotic(ld, true);
                    ld.setPainLevel(maxPain);
player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.WITHER_HURT, SoundSource.PLAYERS, 0.85f, 0.65f);
                }
            } else if (!isLimbNecrotic(ld) && getTourniquetTimer(ld) > 0) {
                setTourniquetTimer(ld, Math.max(0, getTourniquetTimer(ld) - 2));
                dirty = true;
            }

            if (isLimbNecrotic(ld)) {
                necroticCount++;
            }
        }

        // 2. 肢体坏死 -> 坏死毒素入血导致全身其他肢体感染急剧上升！
        if (necroticCount > 0) {
            int desiredNecAmp = Math.min(2, necroticCount - 1);
            MobEffectInstance curNec = player.getEffect(LIMB_NECROSIS);
            if (curNec == null || curNec.getAmplifier() != desiredNecAmp || curNec.getDuration() < 40) {
                player.addEffect(new MobEffectInstance(
                        LIMB_NECROSIS,
                        220,
                        desiredNecAmp,
                        false,
                        false,
                        true
                ));
            }

            int selfInfRate = safeCfgInt(MedicalConfig.NECROSIS_SELF_INFECTION_RATE_PER_SEC, 2);
            int otherInfRate = safeCfgInt(MedicalConfig.NECROSIS_OTHER_LIMBS_INFECTION_RATE_PER_SEC, 1);
            for (MedicalData.Limb limb : MedicalData.Limb.values()) {
                MedicalData.LimbData ld = data.getLimb(limb);
                if (ld == null || ld.isAmputated()) continue;
                int oldInf = ld.getInfection();
                if (isLimbNecrotic(ld)) {
                    ld.setInfection(Math.min(100, oldInf + selfInfRate));
                } else {
                    ld.setInfection(Math.min(100, oldInf + otherInfRate * necroticCount));
                }
                if (ld.getInfection() != oldInf) {
                    dirty = true;
                }
            }
        } else if (player.hasEffect(LIMB_NECROSIS)) {
            player.removeEffect(LIMB_NECROSIS);
        }

        // 3. 同步「阿片成瘾 I / II / III」Buff
        int tier = getAddictionTier(data.getAddictionLevel());
        if (tier > 0) {
            int desiredAmplifier = tier - 1;
            MobEffectInstance current = player.getEffect(OPIOID_ADDICTION);
            if (current == null || current.getAmplifier() != desiredAmplifier || current.getDuration() < 40) {
                player.addEffect(new MobEffectInstance(
                        OPIOID_ADDICTION,
                        220,
                        desiredAmplifier,
                        false,
                        false,
                        true
                ));
            }
        } else if (player.hasEffect(OPIOID_ADDICTION)) {
            player.removeEffect(OPIOID_ADDICTION);
        }

        // 4. 同步「肾上腺素激增 I / II / III」Buff
        syncAdrenalineEffectNow(player, data);

        // 5. 肾上腺素血压飙升副作用：若存在未包扎/未绑止血带的开放性外出血，按比例加速失血
        float adr = getAdrenalineLevel(data);
        if (adr >= safeCfgInt(MedicalConfig.ADRENALINE_TIER1_THRESHOLD, 20)) {
            int unbandagedBleedPerMin = 0;
            for (MedicalData.Limb limb : MedicalData.Limb.values()) {
                MedicalData.LimbData ld = data.getLimb(limb);
                if (ld != null && ld.getBleeding() > 0 && !ld.isBandaged() && !ld.isTourniqueted()) {
                    unbandagedBleedPerMin += ld.getBleeding();
                }
            }
            if (unbandagedBleedPerMin > 0) {
                float extraMult = ((float) safeCfgDouble(MedicalConfig.ADRENALINE_UNBANDAGED_BLEED_MULT, 1.35)) * (adr / 65.0f);
                float extraMlPerSec = (unbandagedBleedPerMin / 60.0f) * extraMult;
                if (extraMlPerSec >= 0.2f) {
                    int loss = Math.max(1, Math.round(extraMlPerSec));
                    data.setBloodVolume(Math.max(0, data.getBloodVolume() - loss));
                    dirty = true;
                }
            }
        }

        // 6. 四肢内出血“封顶机制”：骨筋膜室综合征 (Osteofascial Compartment Syndrome)
        int compartmentSyndromeCount = 0;
        int maxRateCap = safeCfgInt(MedicalConfig.COMPARTMENT_MAX_INTERNAL_BLEED_RATE, 240);
        float maxBloodCapMl = ((float) safeCfgDouble(MedicalConfig.COMPARTMENT_MAX_BLOOD_CAP_ML, 450.0));
        float bloodAccumMult = ((float) safeCfgDouble(MedicalConfig.COMPARTMENT_BLOOD_ACCUM_MULT, 1.25));
        float collapseStartMmHg = ((float) safeCfgDouble(MedicalConfig.COMPARTMENT_VENOUS_COLLAPSE_MMHG, 30.0));
        float fullTamponadeMmHg = ((float) safeCfgDouble(MedicalConfig.COMPARTMENT_TAMPONADE_FULL_MMHG, 65.0));
        float csThreshMmHg = ((float) safeCfgDouble(MedicalConfig.COMPARTMENT_SYNDROME_THRESHOLD_MMHG, 30.0));
        float critDeltaP = ((float) safeCfgDouble(MedicalConfig.COMPARTMENT_CRITICAL_DELTA_P_MMHG, 30.0));
        float ischStartMmHg = ((float) safeCfgDouble(MedicalConfig.COMPARTMENT_ISCHEMIA_START_MMHG, 42.0));
        int csNecrosisSec = safeCfgInt(MedicalConfig.COMPARTMENT_NECROSIS_TIME_SEC, 240);
        int csStage1Pain = safeCfgInt(MedicalConfig.COMPARTMENT_STAGE1_MIN_PAIN, 7);
        int csStage2Pain = safeCfgInt(MedicalConfig.COMPARTMENT_STAGE2_MIN_PAIN, 9);
        float rhabdoToxin = ((float) safeCfgDouble(MedicalConfig.COMPARTMENT_RHABDO_TOXIN_PER_SEC, 0.35));
        float naturalAbsorb = ((float) safeCfgDouble(MedicalConfig.COMPARTMENT_NATURAL_ABSORB_ML_PER_SEC, 1.5));

        MedicalData.Limb[] fourLimbs = new MedicalData.Limb[]{
                MedicalData.Limb.LEFT_ARM, MedicalData.Limb.RIGHT_ARM,
                MedicalData.Limb.LEFT_LEG, MedicalData.Limb.RIGHT_LEG
        };
        for (MedicalData.Limb fLimb : fourLimbs) {
            MedicalData.LimbData ld = data.getLimb(fLimb);
            if (ld == null) continue;

            if (ld.isAmputated()) {
                // 断肢无密闭筋膜室：清除骨筋膜室压迫与综合征状态
                if (isCompartmentSyndrome(ld) || getCompartmentTamponadedBleed(ld) > 0 || getCompartmentBlood(ld) > 0.0f) {
                    setCompartmentSyndrome(ld, false);
                    setCompartmentTamponadedBleed(ld, 0);
                    setCompartmentBlood(ld, 0.0f);
                    setCompartmentPressure(ld, 0.0f);
                    setCompartmentIschemiaSec(ld, 0);
                    dirty = true;
                }
                continue;
            }

            int curActiveInt = ld.getInternalBleedingRate();
            int curTampInt = getCompartmentTamponadedBleed(ld);
            int rawTotalInt = Math.min(maxRateCap, curActiveInt + curTampInt);

            float curCompBlood = getCompartmentBlood(ld);
            if (curActiveInt > 0) {
                float addedMl = (curActiveInt / 60.0f) * bloodAccumMult;
                curCompBlood = Math.min(maxBloodCapMl, curCompBlood + addedMl);
                setCompartmentBlood(ld, curCompBlood);
                dirty = true;
            } else if (rawTotalInt == 0 && !isCompartmentSyndrome(ld) && curCompBlood > 0.0f) {
                curCompBlood = Math.max(0.0f, curCompBlood - naturalAbsorb);
                setCompartmentBlood(ld, curCompBlood);
                dirty = true;
            }

            // 非线性深筋膜顺应性曲线计算骨筋膜室室内压 (mmHg) + III/IV度环状焦痂紧缩加成
            float fillRatio = Mth.clamp(curCompBlood / Math.max(1.0f, maxBloodCapMl), 0.0f, 1.0f);
            float escharConstruct = (ld.getBurnDegree() >= 3) ? (ld.getBurnArea() * 0.28f) : 0.0f;
            float icpMmHg = 4.0f + (float) Math.pow(fillRatio, 1.45) * (fullTamponadeMmHg - 2.0f) + escharConstruct;
            if (Math.abs(icpMmHg - getCompartmentPressure(ld)) > 0.2f) {
                setCompartmentPressure(ld, icpMmHg);
                dirty = true;
            }

            // 高压压迫自限止血封顶机制：当室内压 >= collapseStartMmHg 或积血达容量上限时，压迫闭塞出血血管
            if (rawTotalInt > 0) {
                if (icpMmHg >= collapseStartMmHg || fillRatio >= 0.99f) {
                    float tampRatio = (fillRatio >= 0.99f)
                            ? 1.0f
                            : Mth.clamp((icpMmHg - collapseStartMmHg) / Math.max(1.0f, fullTamponadeMmHg - collapseStartMmHg), 0.0f, 1.0f);
                    int nextActive = Math.round(rawTotalInt * (1.0f - tampRatio));
                    int nextTamp = rawTotalInt - nextActive;
                    if (nextActive != curActiveInt || nextTamp != curTampInt) {
                        ld.setInternalBleedingRate(nextActive);
                        setCompartmentTamponadedBleed(ld, nextTamp);
                        dirty = true;
                    }
                } else if (curActiveInt != rawTotalInt || curTampInt != 0) {
                    ld.setInternalBleedingRate(rawTotalInt);
                    setCompartmentTamponadedBleed(ld, 0);
                    dirty = true;
                }
            }

            // 骨筋膜室综合征诊断 (ICP >= 30 mmHg 或 灌注压差 ΔP = 舒张压 - ICP <= 30 mmHg)
            float deltaP = data.getDiastolicBP() - icpMmHg;
            boolean hasCS = (icpMmHg >= csThreshMmHg) || (curCompBlood >= maxBloodCapMl * 0.42f && deltaP <= critDeltaP);
            if (hasCS != isCompartmentSyndrome(ld)) {
                setCompartmentSyndrome(ld, hasCS);
                dirty = true;
            }

            if (hasCS) {
                compartmentSyndromeCount++;
                int maxPainVal = safeCfgInt(MedicalConfig.MAX_PAIN_LEVEL, 10);
                int reqPain = (icpMmHg >= ischStartMmHg) ? csStage2Pain : csStage1Pain;
                if (ld.getPainLevel() < reqPain) {
                    ld.setPainLevel(Math.min(maxPainVal, reqPain));
                    dirty = true;
                }

                // 重度室内高压阻断微循环灌注 -> 累计缺血秒数、释放横纹肌溶解肌红蛋白毒素、超时引发肢体坏死
                if (icpMmHg >= ischStartMmHg || deltaP <= critDeltaP) {
                    int ischSec = getCompartmentIschemiaSec(ld) + 1;
                    setCompartmentIschemiaSec(ld, ischSec);
                    if (rhabdoToxin > 0.0f) {
                        data.addToxinValue(rhabdoToxin);
                    }
                    if (ischSec >= csNecrosisSec && !isLimbNecrotic(ld)) {
                        setLimbNecrotic(ld, true);
                        ld.setPainLevel(maxPainVal);
                        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                                SoundEvents.WITHER_HURT, SoundSource.PLAYERS, 0.85f, 0.65f);
                    }
                    dirty = true;
                }
            } else if (getCompartmentIschemiaSec(ld) > 0) {
                setCompartmentIschemiaSec(ld, Math.max(0, getCompartmentIschemiaSec(ld) - 2));
                dirty = true;
            }
        }

        if (compartmentSyndromeCount > 0) {
            int desiredCsAmp = Math.min(2, compartmentSyndromeCount - 1);
            MobEffectInstance curCs = player.getEffect(COMPARTMENT_SYNDROME);
            if (curCs == null || curCs.getAmplifier() != desiredCsAmp || curCs.getDuration() < 40) {
                player.addEffect(new MobEffectInstance(
                        COMPARTMENT_SYNDROME,
                        220,
                        desiredCsAmp,
                        false,
                        false,
                        true
                ));
            }
        } else if (player.hasEffect(COMPARTMENT_SYNDROME)) {
            player.removeEffect(COMPARTMENT_SYNDROME);
        }

        // 7. 烧伤热负荷快速转化与火焰/熔岩每秒持续烧伤推进（解决烧伤上涨过慢问题）
        boolean inLava = player.isInLava();
        boolean onFire = player.isOnFire() || player.getRemainingFireTicks() > 0;
        float burnMult = ((float) safeCfgDouble(MedicalConfig.BURN_RATE_MULTIPLIER, 4.5));
        float fireRate = ((float) safeCfgDouble(MedicalConfig.BURN_FIRE_AREA_GAIN_PER_SEC, 4.2));
        float lavaRate = ((float) safeCfgDouble(MedicalConfig.BURN_LAVA_AREA_GAIN_PER_SEC, 12.5));
        int maxPainCfg = safeCfgInt(MedicalConfig.MAX_PAIN_LEVEL, 10);
        boolean anyBurnChanged = false;

        for (MedicalData.Limb limb : MedicalData.Limb.values()) {
            MedicalData.LimbData ld = data.getLimb(limb);
            if (ld == null) continue;

            float curArea = ld.getBurnArea();
            float curLoad = ld.getBurnLoad();
            float curConv = ld.getBurnConv();

            if (inLava || onFire) {
                float envGain = (inLava ? lavaRate : fireRate) * (burnMult / 4.0f);
                float limbFactor = (limb == MedicalData.Limb.HEAD && !inLava) ? 0.65f : 1.0f;
                curArea = Mth.clamp(curArea + envGain * limbFactor, 0.0f, 100.0f);
                curConv += envGain * limbFactor;
                ld.setBurnArea(curArea);
                ld.setBurnConv(curConv);
                ld.setBurnHeatTimer(Math.max(ld.getBurnHeatTimer(), inLava ? 10 : 5));
                ld.setBandaged(false);
                anyBurnChanged = true;
            } else if (curLoad > 0.01f) {
                // 将残留热力缓冲负荷快速转化为实际烧伤面积
                float transfer = Math.min(curLoad, Math.max(0.5f, curLoad * 0.45f * burnMult));
                curLoad = Math.max(0.0f, curLoad - transfer);
                curConv += transfer * burnMult;
                curArea = Mth.clamp(curArea + transfer * burnMult * 1.8f, 0.0f, 100.0f);
                ld.setBurnLoad(curLoad);
                ld.setBurnConv(curConv);
                ld.setBurnArea(curArea);
                anyBurnChanged = true;
            } else if (curConv > 0.05f && curArea < Math.min(100.0f, curConv * 3.5f)) {
                // 兼容原版 BurnModule 小数值转正负荷，将其放大映射到直观的百分比烧伤面积
                curArea = Mth.clamp(Math.max(curArea, curConv * 3.5f * (burnMult / 3.0f)), 0.0f, 100.0f);
                ld.setBurnArea(curArea);
                anyBurnChanged = true;
            }

            if (ld.hasBurn() || curArea > 0.05f) {
                int targetDeg = computeBurnDegreeFromAreaAndConv(curArea, curConv, inLava);
                if (targetDeg > ld.getBurnDegree()) {
                    ld.setBurnDegree(targetDeg);
                    anyBurnChanged = true;
                }
                int minBurnPain = Math.min(maxPainCfg, Math.max(1, ld.getBurnDegree() * 2 + (curArea >= 25.0f ? 1 : 0)));
                if (ld.getPainLevel() < minBurnPain) {
                    ld.setPainLevel(minBurnPain);
                    anyBurnChanged = true;
                }
            }
        }
        if (anyBurnChanged) {
            recalcTotalBurnTBSA(data);
            dirty = true;
        }

        // 8. 拟真骨折病理生理与愈合系统（四肢稳定/不稳定/开放性骨折 + 胸部肋骨骨折/连枷胸/非静默迟发性血气胸）
        int defaultHealSec = safeCfgInt(MedicalConfig.SPLINT_FRACTURE_HEAL_TIME_SEC, 300);
        int orthoHealSec = safeCfgInt(MedicalConfig.ORTHO_SURGERY_HEAL_TIME_SEC, 210);
        int openSplintInfPenalty = safeCfgInt(MedicalConfig.OPEN_FRACTURE_SPLINT_INFECTION_PENALTY_PER_SEC, 1);
        int unstableMoveBleedGain = safeCfgInt(MedicalConfig.UNSTABLE_FRACTURE_MOVE_INTERNAL_BLEED_GAIN, 18);
        int ribStrainThresh = safeCfgInt(MedicalConfig.RIB_FRACTURE_DELAYED_HEMOTHORAX_STRAIN_THRESHOLD, 12);
        float ribDelayedBloodMl = ((float) safeCfgDouble(MedicalConfig.RIB_FRACTURE_DELAYED_HEMOTHORAX_BLOOD_ML, 180.0));
        float flailO2Penalty = ((float) safeCfgDouble(MedicalConfig.FLAIL_CHEST_O2_PENALTY_PER_SEC, 0.55));

        boolean isStrenuousMove = player.isSprinting() || (!player.onGround() && !player.isInWater()) || data.getAttackTimer() > 0;
        boolean hasSystemicAnalgesia = (data instanceof MedicalDataImpl anaImpl)
                && (anaImpl.getOpioidConcentrationFloat() >= 8.0f
                || (anaImpl.getNsaidActiveTimer() > 0 && anaImpl.getNsaidOnsetTimer() <= 0));

        for (MedicalData.Limb limb : MedicalData.Limb.values()) {
            MedicalData.LimbData ld = data.getLimb(limb);
            if (ld == null || ld.isAmputated() || isLimbNecrotic(ld)) continue;
            if (!ld.hasFracture()) continue;

            boolean orthoFixed = isLimbOrthopedicFixed(ld);
            boolean unstableOrOpen = ld.isOpenFracture() || ld.hasDislocation();

            if (limb == MedicalData.Limb.CHEST) {
                // ---------------- 胸部（肋骨）骨折与连枷胸独立机制 ----------------
                boolean braced = isChestBraced(ld) || ld.isBandaged();
                if (unstableOrOpen && !orthoFixed) {
                    // 【连枷胸 / 严重移位多发肋骨骨折（手术红线）】：吸气时浮动胸壁反常内陷，导致血氧持续下降
                    float o2Drop = braced ? (flailO2Penalty * 0.65f) : flailO2Penalty;
                    if (o2Drop > 0.0f) {
                        data.setBloodOxygenFloat(Math.max(0.0f, data.getBloodOxygenFloat() - o2Drop));
                    }
                    if (player.tickCount % 60 == 0 && data.getLungContusion() < 65.0f) {
                        data.setLungContusion(Math.min(100.0f, data.getLungContusion() + 1.2f));
                    }
                    if (isStrenuousMove && player.tickCount % 40 == 0) {
                        data.setChestBloodAccumulation(data.getChestBloodAccumulation() + 25.0f);
                        ld.setPainLevel(Math.min(maxPainCfg, ld.getPainLevel() + 1));
                    }
                    dirty = true;
                } else {
                    // 【单纯性肋骨骨折（或已做肋骨环抱器手术）】：
                    // 1) 若未止痛且未手术，患者因胸痛不敢深呼吸与排痰 -> 缓慢累积肺部坠积性感染（肺炎风险）
                    if (!orthoFixed && ld.getPainLevel() >= 3 && !hasSystemicAnalgesia && player.tickCount % 160 == 0) {
                        ld.setInfection(Math.min(100, ld.getInfection() + 1));
                        dirty = true;
                    }
                    // 2) 未绑加压胸带固定前若剧烈运动 -> 累积胸壁应力，突破阈值触发「非静默迟发性血气胸」！
                    if (!orthoFixed && !braced && isStrenuousMove) {
                        int nextStrain = getDelayedHemothoraxStrain(ld) + (player.isSprinting() ? 2 : 1);
                        if (nextStrain >= ribStrainThresh) {
                            setDelayedHemothoraxStrain(ld, 0);
                            float newChestBlood = data.getChestBloodAccumulation() + ribDelayedBloodMl;
                            data.setChestBloodAccumulation(newChestBlood);
                            if (newChestBlood >= 900.0f) data.setHemothoraxLevel(Math.max(data.getHemothoraxLevel(), 3));
                            else if (newChestBlood >= 450.0f) data.setHemothoraxLevel(Math.max(data.getHemothoraxLevel(), 2));
                            else if (newChestBlood >= 150.0f) data.setHemothoraxLevel(Math.max(data.getHemothoraxLevel(), 1));
                            data.setLungContusion(Math.min(100.0f, data.getLungContusion() + 14.0f));
                            ld.setPainLevel(Math.min(maxPainCfg, ld.getPainLevel() + 3));
                            ld.setInternalBleedingRate(ld.getInternalBleedingRate() + 35);

                            // 非静默剧烈呛咳 + 肋骨刺破胸膜声学与视觉震荡警报！
                            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                                    SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 1.0f, 0.72f);
                            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                                    SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.15f, 1.25f);
                            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                                    SoundEvents.PANDA_SNEEZE, SoundSource.PLAYERS, 0.95f, 0.60f);
                            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 70, 0, false, false, false));
                        } else {
                            setDelayedHemothoraxStrain(ld, nextStrain);
                        }
                        dirty = true;
                    }
                    // 3) 保守愈合或手术后愈合推进（加压胸带固定 + 疼痛受控，或已完成肋骨环抱器手术）
                    if (orthoFixed || (braced && (hasSystemicAnalgesia || ld.getPainLevel() <= 3))) {
                        int targetMax = orthoFixed ? orthoHealSec : defaultHealSec;
                        int curTimer = ld.getFractureTimer();
                        if (curTimer <= 0 || curTimer > targetMax) {
                            setFractureTimerAuth(ld, targetMax);
                        } else {
                            int nextTimer = curTimer - 1;
                            if (nextTimer <= 0) {
                                completeFractureHealing(ld);
                                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                                        SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 0.85f, 1.25f);
                            } else {
                                setFractureTimerAuth(ld, nextTimer);
                            }
                        }
                        dirty = true;
                    }
                }
                continue;
            }

            // ---------------- 四肢与头部骨折机制 ----------------
            if (unstableOrOpen && !orthoFixed) {
                // 【开放性骨折 / 错位不稳定骨折（手术红线）】：单纯夹板无法对抗肌肉牵拉使其愈合！
                if (ld.isOpenFracture() && ld.hasSplint() && openSplintInfPenalty > 0) {
                    // 临床禁忌惩罚：对未清创开放性骨折直接绑夹板导致封闭创腔厌氧感染翻倍加速
                    ld.setInfection(Math.min(100, ld.getInfection() + openSplintInfPenalty));
                    dirty = true;
                }
                if (!ld.hasSplint() && isStrenuousMove && player.tickCount % 60 == 0) {
                    // 未做任何制动的错位/开放骨折在跑跳时断骨切割周围血管，引发继发内出血与剧痛
                    ld.setInternalBleedingRate(ld.getInternalBleedingRate() + unstableMoveBleedGain);
                    ld.setPainLevel(Math.min(maxPainCfg, ld.getPainLevel() + 1));
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.SKELETON_HURT, SoundSource.PLAYERS, 0.65f, 0.75f);
                    dirty = true;
                }
            } else if (orthoFixed || ld.hasSplint() || limb == MedicalData.Limb.HEAD) {
                // 【闭合性稳定骨折已绑夹板，或已完成骨科复位内/外固定手术】：权威推进愈合倒计时 (300 -> 299 -> ... -> 0)
                if (ld.getBleeding() <= 0) {
                    int targetMax = orthoFixed ? orthoHealSec : defaultHealSec;
                    int curTimer = ld.getFractureTimer();
                    if (curTimer <= 0 || curTimer > targetMax) {
                        setFractureTimerAuth(ld, targetMax);
                        dirty = true;
                    } else {
                        int embBullets = getEmbeddedBulletCount(ld);
                        float healKeepMult = (embBullets > 0)
                                ? ((float) safeCfgDouble(MedicalConfig.EMBEDDED_BULLET_FRACTURE_HEAL_PENALTY_MULT, 0.40))
                                : 1.0f;
                        boolean advanceTick = (embBullets <= 0) || (player.getRandom().nextFloat() < healKeepMult);
                        int nextTimer = advanceTick ? (curTimer - 1) : curTimer;
                        if (nextTimer <= 0) {
                            boolean hadSplint = ld.hasSplint();
                            completeFractureHealing(ld);
                            if (hadSplint && !player.getAbilities().instabuild) {
                                ItemStack returnedSplint = new ItemStack(SPLINT.get());
                                if (!player.getInventory().add(returnedSplint)) {
                                    player.drop(returnedSplint, false);
                                }
                            }
                            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                                    SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 0.85f, 1.20f);
                        } else {
                            setFractureTimerAuth(ld, nextTimer);
                        }
                        dirty = true;
                    }
                }
            }
        }

        // 8AA. 枪伤深部病理演进：体内嵌留子弹 (Embedded Bullet) 持续感染/异物保底刺痛/运动磨破血管 + 贯穿伤临时塞纱跑跳崩裂
        float embInfPerSec = ((float) safeCfgDouble(MedicalConfig.EMBEDDED_BULLET_INFECTION_ACCUM_PER_SEC, 0.16));
        int embMinPain = safeCfgInt(MedicalConfig.EMBEDDED_BULLET_MIN_PAIN_FLOOR, 2);
        float embRebleedChance = ((float) safeCfgDouble(MedicalConfig.EMBEDDED_BULLET_MOVE_REBLEED_CHANCE, 0.15));
        int embRebleedAmt = safeCfgInt(MedicalConfig.EMBEDDED_BULLET_MOVE_REBLEED_AMOUNT, 35);
        float perfTearChance = ((float) safeCfgDouble(MedicalConfig.PERFORATION_PACKING_SPRINT_TEAR_CHANCE, 0.12));

        for (MedicalData.Limb gLimb : MedicalData.Limb.values()) {
            MedicalData.LimbData gLd = data.getLimb(gLimb);
            if (gLd == null || gLd.isAmputated()) continue;

            int embeddedCount = getEmbeddedBulletCount(gLd);
            if (embeddedCount > 0) {
                if (!gLd.hasOpenWound()) {
                    gLd.setOpenWound(true);
                    dirty = true;
                }
                if (gLd.getPainLevel() < embMinPain) {
                    gLd.setPainLevel(Math.min(maxPainCfg, embMinPain));
                    dirty = true;
                }
                float disinfectFactor = (gLd.getDisinfectantTimer() > 0) ? 0.75f : 1.0f;
                float nextInfAcc = gLd.getInfectionAccumulator() + embeddedCount * embInfPerSec * disinfectFactor;
                if (nextInfAcc >= 1.0f) {
                    int gainInf = (int) nextInfAcc;
                    gLd.setInfection(Math.min(100, gLd.getInfection() + gainInf));
                    nextInfAcc -= gainInf;
                    dirty = true;
                }
                gLd.setInfectionAccumulator(nextInfAcc);

                if (isStrenuousMove && player.getRandom().nextFloat() < embRebleedChance) {
                    if (gLd.isTourniqueted()) {
                        setTourniquetClampedBleed(gLd, Math.min(2500, getTourniquetClampedBleed(gLd) + embRebleedAmt));
                    } else {
                        gLd.setBleeding(Math.min(2500, gLd.getBleeding() + embRebleedAmt));
                        gLd.setBandaged(false);
                    }
                    gLd.setPainLevel(Math.min(maxPainCfg, gLd.getPainLevel() + 1));
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.CHAIN_HIT, SoundSource.PLAYERS, 0.70f, 0.85f);
                    dirty = true;
                }
            }

            if (gLd.hasPerforation() && isPerforationPacked(gLd) && isStrenuousMove) {
                if (player.getRandom().nextFloat() < perfTearChance) {
                    setPerforationPacked(gLd, false);
                    gLd.setBandaged(false);
                    gLd.setOpenWound(true);
                    int reBleed = Math.max(45, embRebleedAmt * 2);
                    if (gLd.isTourniqueted()) {
                        setTourniquetClampedBleed(gLd, Math.min(2500, getTourniquetClampedBleed(gLd) + reBleed));
                    } else {
                        gLd.setBleeding(Math.min(2500, gLd.getBleeding() + reBleed));
                    }
                    gLd.setPainLevel(Math.min(maxPainCfg, gLd.getPainLevel() + 2));
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.LEASH_KNOT_BREAK, SoundSource.PLAYERS, 0.90f, 0.75f);
                    dirty = true;
                }
            }
        }

        // 8A. 创伤性气胸全阶段演进（闭合性气胸 / 开放性吸吮性胸壁伤口 / 张力性气胸与腔静脉梗阻性休克）
        MedicalData.LimbData chestLimbData = data.getLimb(MedicalData.Limb.CHEST);
        if (chestLimbData != null) {
            float curAirMl = getPleuralAirMl(data);
            int pType = getPneumothoraxType(data);
            boolean valveSealed = isThreeSidedValveSealed(data);
            boolean deadSealed = isImproperlyDeadSealed(data);
            int decompWin = getDecompressionWindowSec(data);
            if (decompWin > 0) {
                decompWin--;
                setDecompressionWindowSec(data, decompWin);
                dirty = true;
            }

            boolean chestHasFlail = chestLimbData.hasFracture()
                    && (chestLimbData.isOpenFracture() || chestLimbData.hasDislocation())
                    && !isLimbOrthopedicFixed(chestLimbData);

            // 1) 触发判定：胸壁开放性贯通伤 -> 触发「开放性气胸 (吸吮性胸壁伤口)」
            if (chestLimbData.hasOpenWound() && !valveSealed && !deadSealed
                    && (chestLimbData.getBleeding() >= 90 || chestLimbData.getInternalBleedingRate() >= 20 || chestLimbData.isOpenFracture())) {
                if (pType == 0) {
                    pType = 2;
                    curAirMl = Math.max(curAirMl, 290.0f);
                    setPneumothoraxType(data, pType);
                    // 非静默破膜吸气声 + 剧烈呛咳报警
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 0.90f, 1.55f);
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.75f, 1.85f);
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.PANDA_SNEEZE, SoundSource.PLAYERS, 0.95f, 0.65f);
                } else if (pType == 1) {
                    pType = 2;
                    setPneumothoraxType(data, pType);
                }
            } else if (pType == 0 && (chestLimbData.hasFracture() && (chestLimbData.getInternalBleedingRate() >= 15 || data.getChestBloodAccumulation() >= 140.0f))) {
                // 肋骨骨折断端刺伤脏层胸膜 -> 触发「闭合性气胸」
                pType = 1;
                curAirMl = Math.max(curAirMl, 195.0f);
                setPneumothoraxType(data, pType);
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.PANDA_SNEEZE, SoundSource.PLAYERS, 0.85f, 0.68f);
                dirty = true;
            }

            // 2) 动态积气与排气演进
            float tensionThreshMl = ((float) safeCfgDouble(MedicalConfig.PNEUMOTHORAX_TENSION_THRESHOLD_ML, 800.0));
            if (pType > 0 || curAirMl > 0.0f) {
                if (deadSealed && decompWin <= 0) {
                    // 临床禁忌误操作：普通绷带死封未排气的气胸伤口，肺泡漏气只进不出 -> 极速憋成张力性气胸！
                    float trapGain = ((float) safeCfgDouble(MedicalConfig.PNEUMOTHORAX_DEAD_SEAL_TENSION_GAIN_ML_PER_SEC, 22.0));
                    curAirMl = Math.min(1500.0f, curAirMl + trapGain);
                    if (isStrenuousMove || player.tickCount % 40 == 0) {
                        // 皮下气肿握雪捻发音
                        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                                SoundEvents.POWDER_SNOW_STEP, SoundSource.PLAYERS, 0.75f, 1.65f);
                    }
                } else if (pType == 2 && !valveSealed) {
                    // 未封堵的开放性气胸：每次吸气从胸壁破口吸入空气，发出非静默“嘶——呼——”吸吮声
                    float inflow = ((float) safeCfgDouble(MedicalConfig.PNEUMOTHORAX_OPEN_INFLOW_ML_PER_SEC, 18.0));
                    if (isStrenuousMove) inflow *= 1.45f;
                    curAirMl = Math.min(1250.0f, curAirMl + inflow);
                    if (player.tickCount % 60 == 0) {
                        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                                SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.48f, 1.88f);
                    }
                } else if (chestHasFlail && decompWin <= 0) {
                    // 连枷胸/移位肋骨刺破肺实质形成单向活瓣：每次呼吸向胸膜腔泵气
                    curAirMl = Math.min(1500.0f, curAirMl + (isStrenuousMove ? 20.0f : 11.5f));
                } else if (valveSealed && curAirMl > 220.0f) {
                    // 加压绷带「三边单向阀胸封」生效：阻止外气吸入，并在呼气相单向排出残气
                    float ventRate = ((float) safeCfgDouble(MedicalConfig.PNEUMOTHORAX_VALVE_SEAL_VENT_ML_PER_SEC, 4.5));
                    curAirMl = Math.max(220.0f, curAirMl - ventRate);
                    if (pType == 2) pType = 1;
                } else {
                    // 闭合性气胸：若积气 <= 250mL 且不剧烈跑跳，由胸膜毛细血管自动缓慢吸收
                    float maxSelfAbsorbAir = ((float) safeCfgDouble(MedicalConfig.PNEUMOTHORAX_CLOSED_SELF_ABSORB_MAX_ML, 250.0));
                    if (curAirMl <= maxSelfAbsorbAir && !player.isSprinting() && chestLimbData.getInternalBleedingRate() <= 0) {
                        float airAbsorb = ((float) safeCfgDouble(MedicalConfig.PNEUMOTHORAX_SELF_ABSORB_RATE_ML_PER_SEC, 2.0));
                        curAirMl = Math.max(0.0f, curAirMl - airAbsorb);
                        if (curAirMl <= 0.5f) {
                            curAirMl = 0.0f;
                            pType = 0;
                            setThreeSidedValveSealed(data, false);
                            setImproperlyDeadSealed(data, false);
                        }
                    } else if (player.isSprinting() && decompWin <= 0) {
                        curAirMl = Math.min(1500.0f, curAirMl + 6.5f);
                    }
                }

                // 3) 计算胸膜腔压力 (cmH2O) 与张力性气胸（梗阻性休克）判定
                float pressureCmH2O;
                if (decompWin > 0) {
                    // 14G 穿刺减压针单向阀留置期间，胸腔正压被强制泄放至 <= 0 cmH2O
                    curAirMl = Math.min(curAirMl, tensionThreshMl * 0.55f);
                    pressureCmH2O = Mth.lerp(curAirMl / Math.max(1.0f, tensionThreshMl), -5.0f, 1.5f);
                    if (pType == 3) pType = 1;
                } else {
                    pressureCmH2O = -6.0f + (curAirMl / Math.max(1.0f, tensionThreshMl)) * 21.0f;
                    if (curAirMl >= tensionThreshMl) {
                        if (pType != 3) {
                            pType = 3;
                            // 升级为张力性气胸瞬间的纵隔移位与窒息警报声！
                            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                                    SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.15f, 0.75f);
                            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 80, 0, false, false, false));
                        }
                    } else if (pType == 3) {
                        pType = chestLimbData.hasOpenWound() && !valveSealed ? 2 : 1;
                    }
                }

                setPleuralAirMl(data, curAirMl);
                setPleuralPressureCmH2O(data, pressureCmH2O);
                setPneumothoraxType(data, pType);
                dirty = true;

                // 4) 气胸生理惩罚：阶段1/2 通气受限，阶段3 张力性气胸压扁腔静脉导致【梗阻性休克（血压崩盘 + 窒息缺氧）】
                if (pType == 3) {
                    float o2Loss = ((float) safeCfgDouble(MedicalConfig.PNEUMOTHORAX_TENSION_O2_LOSS_PER_SEC, 0.85));
                    data.setBloodOxygenFloat(Math.max(0.0f, data.getBloodOxygenFloat() - o2Loss));
                    int bpDrop = safeCfgInt(MedicalConfig.PNEUMOTHORAX_TENSION_BP_DROP_PER_SEC, 2);
                    if (bpDrop > 0) {
                        int nextSys = Math.max(42, data.getSystolicBP() - bpDrop);
                        int nextDia = Math.max(24, data.getDiastolicBP() - Math.max(1, bpDrop - 1));
                        data.setSystolicBP(nextSys);
                        data.setDiastolicBP(nextDia);
                        data.setMeanArterialPressure(Math.max(30.0f, nextDia + (nextSys - nextDia) / 3.0f));
                    }
                    int vfibCap = Math.max(155, safeCfgInt(MedicalConfig.VFIB_THRESHOLD, 185) - 6);
                    data.setHeartRate(Math.min(vfibCap, data.getHeartRate() + 3));
                    if (player.tickCount % 40 == 0) {
                        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                                SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 0.90f, 1.35f);
                    }
                } else if (curAirMl >= 260.0f) {
                    float mildO2Loss = (pType == 2 ? 0.42f : 0.20f) * (curAirMl / Math.max(1.0f, tensionThreshMl));
                    if (isStrenuousMove) mildO2Loss *= 1.8f;
                    data.setBloodOxygenFloat(Math.max(0.0f, data.getBloodOxygenFloat() - mildO2Loss));
                }

                // 同步 HUD 气胸状态图标 (I = 闭合性气胸, II = 开放性气胸, III = 张力性气胸)
                if (pType > 0) {
                    int desiredAmp = pType - 1;
                    MobEffectInstance curPn = player.getEffect(PNEUMOTHORAX);
                    if (curPn == null || curPn.getAmplifier() != desiredAmp || curPn.getDuration() < 40) {
                        player.addEffect(new MobEffectInstance(PNEUMOTHORAX, 220, desiredAmp, false, false, true));
                    }
                } else if (player.hasEffect(PNEUMOTHORAX)) {
                    player.removeEffect(PNEUMOTHORAX);
                }
            } else if (player.hasEffect(PNEUMOTHORAX)) {
                player.removeEffect(PNEUMOTHORAX);
            }
        }

        // 8B. 胸腔积血（少量自吸收 vs 中大量需闭式引流）与肺挫伤（病因阻断 + 氨甲环酸/布洛芬协同加速消水肿）
        float curChestBlood = data.getChestBloodAccumulation();
        int chestIntBleed = (chestLimbData != null) ? chestLimbData.getInternalBleedingRate() : 0;
        if (curChestBlood > 0.0f) {
            float maxSelfAbsorbMl = ((float) safeCfgDouble(MedicalConfig.HEMOTHORAX_SELF_ABSORB_MAX_ML, 250.0));
            if (curChestBlood <= maxSelfAbsorbMl && chestIntBleed <= 0) {
                float absorbRate = ((float) safeCfgDouble(MedicalConfig.HEMOTHORAX_SELF_ABSORB_RATE_ML_PER_SEC, 2.5));
                curChestBlood = Math.max(0.0f, curChestBlood - absorbRate);
                data.setChestBloodAccumulation(curChestBlood);
                dirty = true;
            }
            int expectedHemoLv = (curChestBlood >= 900.0f) ? 3 : ((curChestBlood >= 450.0f) ? 2 : ((curChestBlood >= 150.0f) ? 1 : 0));
            if (data.getHemothoraxLevel() != expectedHemoLv) {
                data.setHemothoraxLevel(expectedHemoLv);
                dirty = true;
            }
        } else if (data.getHemothoraxLevel() > 0) {
            data.setHemothoraxLevel(0);
            dirty = true;
        }

        float curContusion = data.getLungContusion();
        if (curContusion > 0.0f) {
            boolean hasUnfixedFlail = chestLimbData != null
                    && chestLimbData.hasFracture()
                    && (chestLimbData.isOpenFracture() || chestLimbData.hasDislocation())
                    && !isLimbOrthopedicFixed(chestLimbData);

            if (player.isSprinting() && curContusion >= 15.0f) {
                // 顶着肺挫伤剧烈疾跑：肺毛细血管流体静水压飙升，肺泡渗出水肿加重
                float worsen = ((float) safeCfgDouble(MedicalConfig.LUNG_CONTUSION_SPRINT_WORSEN_PER_SEC, 0.35));
                data.setLungContusion(Math.min(100.0f, curContusion + worsen));
                dirty = true;
            } else if (!hasUnfixedFlail && data.getHemothoraxLevel() <= 1 && chestIntBleed <= 0) {
                // 解除连枷胸与中重度血胸压迫后，肺泡水肿开始吸收；氨甲环酸 (TXA) 与布洛芬 (NSAID) 成倍加速消水肿！
                float decay = ((float) safeCfgDouble(MedicalConfig.LUNG_CONTUSION_BASE_DECAY_PER_SEC, 0.25));
                if (data instanceof MedicalDataImpl medImpl) {
                    if (medImpl.getTxaConcentration() >= 10.0f) {
                        decay *= ((float) safeCfgDouble(MedicalConfig.LUNG_CONTUSION_TXA_SPEED_MULT, 2.0));
                    }
                    if (medImpl.getNsaidActiveTimer() > 0 && medImpl.getNsaidOnsetTimer() <= 0) {
                        decay *= ((float) safeCfgDouble(MedicalConfig.LUNG_CONTUSION_NSAID_SPEED_MULT, 1.8));
                    }
                }
                data.setLungContusion(Math.max(0.0f, curContusion - decay));
                dirty = true;
            }
        }

        // 9. 非阿片类止痛药（布洛芬 NSAID）：慢起效潜伏期 -> 缓慢爬坡 -> 弱效天花板消除轻痛
        if (data instanceof MedicalDataImpl nsaidImpl) {
            int onset = nsaidImpl.getNsaidOnsetTimer();
            int active = nsaidImpl.getNsaidActiveTimer();
            if (onset > 0) {
                nsaidImpl.setNsaidOnsetTimer(onset - 1);
                dirty = true;
            } else if (active > 0) {
                nsaidImpl.setNsaidActiveTimer(active - 1);
                int rampMax = safeCfgInt(MedicalConfig.NSAID_RAMP_UP_SEC, 25);
                if (nsaidImpl.getNsaidRampTimer() < rampMax) {
                    nsaidImpl.setNsaidRampTimer(nsaidImpl.getNsaidRampTimer() + 1);
                }
                int intervalSec = Math.max(5, safeCfgInt(MedicalConfig.NSAID_LIMB_PAIN_RELIEF_INTERVAL_SEC, 20));
                int maxRelieved = safeCfgInt(MedicalConfig.NSAID_MAX_LIMB_PAIN_REDUCTION, 2);
                int accum = nsaidImpl.getNsaidLimbReliefAccumSec() + 1;
                if (accum >= intervalSec) {
                    accum = 0;
                    if (nsaidImpl.getNsaidLimbRelievedCount() < maxRelieved) {
                        // 布洛芬口服全身吸收：对全身所有存在轻中度疼痛的部位同时生效（消除 1 级疼痛，受天花板周期次数限制）
                        boolean relievedAny = false;
                        for (MedicalData.Limb l : MedicalData.Limb.values()) {
                            MedicalData.LimbData cand = data.getLimb(l);
                            if (cand != null && cand.getPainLevel() > 0) {
                                cand.setPainLevel(Math.max(0, cand.getPainLevel() - 1));
                                relievedAny = true;
                            }
                        }
                        if (relievedAny) {
                            nsaidImpl.setNsaidLimbRelievedCount(nsaidImpl.getNsaidLimbRelievedCount() + 1);
                        }
                    }
                }
                nsaidImpl.setNsaidLimbReliefAccumSec(accum);
                if (nsaidImpl.getNsaidActiveTimer() <= 0) {
                    nsaidImpl.setNsaidRampTimer(0);
                    nsaidImpl.setNsaidLimbReliefAccumSec(0);
                    nsaidImpl.setNsaidLimbRelievedCount(0);
                }
                dirty = true;
            }

            // 10. 氨甲环酸 (TXA) 持续抗纤溶促凝止内出血 & 过量高凝微血栓副作用
            float txaConc = nsaidImpl.getTxaConcentration();
            if (txaConc > 0.0f) {
                float txaDecay = ((float) safeCfgDouble(MedicalConfig.TXA_DECAY_RATE_PER_SEC, 0.65));
                float nextTxa = Math.max(0.0f, txaConc - txaDecay);
                nsaidImpl.setTxaConcentration(nextTxa);

                int clotPerSec = safeCfgInt(MedicalConfig.TXA_CONTINUOUS_CLOT_RATE_PER_SEC, 4);
                if (clotPerSec > 0 && nextTxa >= 5.0f) {
                    for (MedicalData.Limb l : MedicalData.Limb.values()) {
                        MedicalData.LimbData ld = data.getLimb(l);
                        if (ld != null && ld.getInternalBleedingRate() > 0) {
                            ld.setInternalBleedingRate(Math.max(0, ld.getInternalBleedingRate() - clotPerSec));
                        }
                    }
                }

                // 副作用：高浓度 (>=75%) 或淤血部位诱发微血栓 -> 降低血氧并加速止血带/骨筋膜室缺血坏死！
                int thrombThresh = safeCfgInt(MedicalConfig.TXA_THROMBOSIS_THRESHOLD, 75);
                if (nextTxa >= thrombThresh) {
                    float o2Drop = ((float) safeCfgDouble(MedicalConfig.TXA_THROMBOSIS_O2_PENALTY_PER_SEC, 0.85));
                    if (o2Drop > 0.0f) {
                        data.setBloodOxygenFloat(Math.max(0.0f, data.getBloodOxygenFloat() - o2Drop));
                    }
                }
                int ischExtra = Math.max(0, safeCfgInt(MedicalConfig.TXA_THROMBOSIS_ISCHEMIA_MULT, 2) - 1);
                if (ischExtra > 0 && nextTxa >= thrombThresh * 0.65f) {
                    for (MedicalData.Limb l : MedicalData.Limb.values()) {
                        MedicalData.LimbData ld = data.getLimb(l);
                        if (ld == null || ld.isAmputated()) continue;
                        if (ld.isTourniqueted()) {
                            setTourniquetTimer(ld, getTourniquetTimer(ld) + ischExtra);
                        }
                        if (isCompartmentSyndrome(ld)) {
                            setCompartmentIschemiaSec(ld, getCompartmentIschemiaSec(ld) + ischExtra);
                        }
                    }
                }
                dirty = true;
            }
        }

        // 11. 中毒、局部/全身感染与 SIRS（全身炎症反应综合征）阶梯药物动力学循环
        // 11A. 医用活性炭混悬液 (activated_charcoal)：胃肠吸附排毒与肠肝循环阻断
        int charcoalSec = getCharcoalActiveSec(data);
        if (charcoalSec > 0) {
            setCharcoalActiveSec(data, charcoalSec - 1);
            float curTox = data.getToxinValue();
            if (curTox > 0.0f) {
                float baseDecay = ((float) safeCfgDouble(MedicalConfig.TOXIN_DECAY_RATE, 0.25));
                float mult = ((float) safeCfgDouble(MedicalConfig.CHARCOAL_DECAY_MULT, 2.4));
                float extraClear = Math.max(0.05f, baseDecay * Math.max(0.2f, mult - 1.0f));
                data.setToxinValue(Math.max(0.0f, curTox - extraClear));
            }
            dirty = true;
        }

        // 11B. 乙酰半胱氨酸解毒注射液 (acetylcysteine / NAC)：谷胱甘肽前体全身解毒与过量组胺释放反应
        float nacConc = getNacConcentration(data);
        if (nacConc > 0.0f) {
            float nacDecay = ((float) safeCfgDouble(MedicalConfig.NAC_DECAY_RATE_PER_SEC, 0.70));
            float nextNac = Math.max(0.0f, nacConc - nacDecay);
            setNacConcentration(data, nextNac);

            if (nextNac >= 4.0f && data.getToxinValue() > 0.0f) {
                float clearPerSec = ((float) safeCfgDouble(MedicalConfig.NAC_CONTINUOUS_TOXIN_CLEAR_PER_SEC, 0.85))
                        * Mth.clamp(nextNac / 45.0f, 0.35f, 1.35f);
                data.setToxinValue(Math.max(0.0f, data.getToxinValue() - clearPerSec));
            }

            // 副作用：过快/过量推注导致血药浓度 >= NAC_HISTAMINE_OVERDOSE_THRESHOLD (70%) 诱发类过敏组胺释放支气管痉挛
            int histThresh = safeCfgInt(MedicalConfig.NAC_HISTAMINE_OVERDOSE_THRESHOLD, 70);
            if (nextNac >= histThresh) {
                float o2Drop = ((float) safeCfgDouble(MedicalConfig.NAC_HISTAMINE_BRONCHOSPASM_O2_LOSS, 0.55));
                if (o2Drop > 0.0f) {
                    data.setBloodOxygenFloat(Math.max(0.0f, data.getBloodOxygenFloat() - o2Drop));
                }
                if (player.tickCount % 60 == 0) {
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.PANDA_SNEEZE, SoundSource.PLAYERS, 0.75f, 0.72f);
                }
            }
            dirty = true;
        }

        // 11C. 聚维酮碘消毒喷雾 (povidone_iodine)：创面络合碘保护膜加速局部感染清除
        float povidoneMult = ((float) safeCfgDouble(MedicalConfig.POVIDONE_IMMUNE_SPEED_MULT, 2.5));
        float baseImmune = ((float) safeCfgDouble(MedicalConfig.IMMUNE_BASE_RATE, 0.10));
        for (MedicalData.Limb limb : MedicalData.Limb.values()) {
            MedicalData.LimbData ld = data.getLimb(limb);
            if (ld == null || ld.isAmputated()) continue;
            if (ld.getDisinfectantTimer() > 0 && ld.getInfection() > 0 && !isLimbNecrotic(ld)) {
                float bonusClear = Math.max(0.05f, baseImmune * Math.max(0.5f, povidoneMult - 1.0f));
                float nextAcc = ld.getInfectionAccumulator() - bonusClear;
                if (nextAcc <= -1.0f) {
                    int drop = Math.min(ld.getInfection(), (int) (-nextAcc));
                    ld.setInfection(Math.max(0, ld.getInfection() - drop));
                    nextAcc += drop;
                    dirty = true;
                }
                ld.setInfectionAccumulator(Math.max(0.0f, nextAcc));
            }
        }

        // 11D. 阿莫西林克拉维酸钾片 (amoxicillin)：胃肠潜伏期 -> 全身广谱抗菌（SIRS/休克低血压下胃肠吸收受阻）
        int amoxOnset = getAmoxicillinOnsetSec(data);
        int amoxActive = getAmoxicillinActiveSec(data);
        if (amoxOnset > 0) {
            setAmoxicillinOnsetSec(data, amoxOnset - 1);
            dirty = true;
        } else if (amoxActive > 0) {
            setAmoxicillinActiveSec(data, amoxActive - 1);
            boolean gutMalabsorption = (data.getSirsLevel() >= 2) || (data.getMeanArterialPressure() < 65.0f);
            float absorbFactor = gutMalabsorption
                    ? ((float) safeCfgDouble(MedicalConfig.AMOXICILLIN_SHOCK_MALABSORPTION_FACTOR, 0.30))
                    : 1.0f;
            float killRate = ((float) safeCfgDouble(MedicalConfig.AMOXICILLIN_CONTINUOUS_KILL_PER_SEC, 0.48)) * absorbFactor;

            // 每 2 秒对全身所有非坏死感染部位执行一次广谱抑菌清除
            if (player.tickCount % 40 == 0) {
                int stepClean = Math.max(1, Math.round(killRate * 2.0f));
                for (MedicalData.Limb limb : MedicalData.Limb.values()) {
                    MedicalData.LimbData ld = data.getLimb(limb);
                    if (ld != null && !ld.isAmputated() && !isLimbNecrotic(ld) && ld.getInfection() > 0) {
                        ld.setInfection(Math.max(0, ld.getInfection() - stepClean));
                    }
                }
            }
            // 在未爆发重度 SIRS (<=1级) 时，有效抑制细菌入血，冻结 SIRS 恶化晋级计时器
            if (data.getSirsLevel() <= 1 && data.getSirsProgressionTimer() > 0) {
                data.setSirsProgressionTimer(Math.max(0, data.getSirsProgressionTimer() - 2));
            }
            dirty = true;
        }

        // 11E. 注射用美罗培南+氢化可的松复合针剂 (meropenem)：顶级碳青霉烯杀菌、压制细胞因子风暴逆转 SIRS 与过量惊厥毒性
        float meroConc = getMeropenemConcentration(data);
        if (meroConc > 0.0f) {
            float meroDecay = ((float) safeCfgDouble(MedicalConfig.MEROPENEM_DECAY_RATE_PER_SEC, 0.55));
            float nextMero = Math.max(0.0f, meroConc - meroDecay);
            setMeropenemConcentration(data, nextMero);

            if (nextMero >= 6.0f) {
                float killPerSec = ((float) safeCfgDouble(MedicalConfig.MEROPENEM_CONTINUOUS_KILL_PER_SEC, 1.15))
                        * Mth.clamp(nextMero / 45.0f, 0.40f, 1.45f);
                int killStep = Math.max(1, Math.round(killPerSec));
                for (MedicalData.Limb limb : MedicalData.Limb.values()) {
                    MedicalData.LimbData ld = data.getLimb(limb);
                    if (ld != null && !ld.isAmputated() && !isLimbNecrotic(ld) && ld.getInfection() > 0) {
                        ld.setInfection(Math.max(0, ld.getInfection() - killStep));
                    }
                }

                // 逆转 SIRS（全身炎症反应综合征）与脓毒症血管麻痹休克：
                int curSirs = data.getSirsLevel();
                if (curSirs > 0) {
                    data.setSirsProgressionTimer(0);
                    float sirsSpeedMult = ((float) safeCfgDouble(MedicalConfig.MEROPENEM_SIRS_RECOVERY_SPEED_MULT, 4.5));
                    int extraRecSec = Math.max(2, Math.round(sirsSpeedMult));
                    int nextRecTimer = data.getSirsRecoveryTimer() + extraRecSec;
                    int reqInterval = switch (curSirs) {
                        case 4 -> safeCfgInt(MedicalConfig.SIRS_RECOVERY_INTERVAL_L4_TO_L3, 300);
                        case 3 -> safeCfgInt(MedicalConfig.SIRS_RECOVERY_INTERVAL_L3_TO_L2, 240);
                        case 2 -> safeCfgInt(MedicalConfig.SIRS_RECOVERY_INTERVAL_L2_TO_L1, 180);
                        default -> safeCfgInt(MedicalConfig.SIRS_RECOVERY_INTERVAL_L1_TO_L0, 120);
                    };
                    // 美罗培南+氢化可的松强效阻断细胞因子风暴，将所需恢复周期缩短至 1/3 即可直接降级
                    int effectiveReq = Math.max(25, reqInterval / 3);
                    if (nextRecTimer >= effectiveReq) {
                        int newSirs = curSirs - 1;
                        data.setSirsLevel(newSirs);
                        data.setSirsRecoveryTimer(0);
                        if (newSirs <= 0) {
                            data.setSirsActive(false);
                        }
                    } else {
                        data.setSirsRecoveryTimer(nextRecTimer);
                    }

                    // 氢化可的松对抗脓毒症分布性休克血管麻痹，温和恢复外周阻力与血压
                    if (data.getPeripheralResistance() < 0.95f) {
                        data.setPeripheralResistance(Math.min(1.0f, data.getPeripheralResistance() + 0.03f));
                    }
                    if (data.getSystolicBP() < 105 && data.getBloodVolume() >= 3500) {
                        int nextSys = Math.min(115, data.getSystolicBP() + 2);
                        int nextDia = Math.min(75, data.getDiastolicBP() + 1);
                        data.setSystolicBP(nextSys);
                        data.setDiastolicBP(nextDia);
                        data.setMeanArterialPressure(nextDia + (nextSys - nextDia) / 3.0f);
                    }
                }
            }

            // 副作用：过量推注 (>= MEROPENEM_NEUROTOXIC_THRESHOLD 85%) 拮抗中枢 GABA_A 受体诱发神经惊厥与意识下降
            int neuroThresh = safeCfgInt(MedicalConfig.MEROPENEM_NEUROTOXIC_THRESHOLD, 85);
            if (nextMero >= neuroThresh) {
                data.setConsciousnessFloat(Math.max(10.0f, data.getConsciousnessFloat() - 1.2f));
                if (player.tickCount % 60 == 0) {
                    player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 80, 0, false, false, false));
                }
            }
            dirty = true;
        }

        if (dirty) {
            player.syncData(MedicalDataCapability.MEDICAL_DATA);
        }
    }

    // =========================================================================
    // 12. Mod 本体核心：精准 3D 解剖射线部位判定 + 受创暂时摔倒 + 无提示手部物理脱手 + 原版/模组全伤害统一接管
    // =========================================================================

    /**
     * 自动清理旧版遗留或包名拼写错误 (campat) 的重复事件监听器，防止二次结算覆盖部位伤害
     */
    public static synchronized void ensureLegacyDamageHandlersCleanedUp() {
        if (legacyHandlersCleanedUp) return;
        legacyHandlersCleanedUp = true;

        ClassLoader cl = ModItems.class.getClassLoader();
        // 1) 若存在旧版 campat.TaczDamageCompatHandler（拼写错误包名），禁用并注销其监听，统一由最新逻辑处理
        try {
            Class<?> oldCampatCls = Class.forName("com.FIYTP.realmedicalmod.campat.TaczDamageCompatHandler", false, cl);
            try {
                Field fAttempt = oldCampatCls.getDeclaredField("taczListenerAttempted");
                fAttempt.setAccessible(true);
                fAttempt.setBoolean(null, true);
            } catch (Throwable ignored) {
            }
            NeoForge.EVENT_BUS.unregister(oldCampatCls);
        } catch (Throwable ignored) {
        }

        // 2) 注销旧版 HitPartHandler（旧版存在瞄腿/手/头却固定命中 CHEST 的缺陷），统一由本体高精度 3D 骨骼射线接管
        try {
            Class<?> oldHitPartCls = Class.forName("com.FIYTP.realmedicalmod.event.HitPartHandler", false, cl);
            NeoForge.EVENT_BUS.unregister(oldHitPartCls);
        } catch (Throwable ignored) {
        }
    }

    /**
     * 将 LivingDamageEvent.Pre 的 newDamage 与底层 DamageContainer.originalDamage 同时归零，
     * 彻底杜绝任何下游旧版监听器读取 originalDamage 造成二次胸部伤害叠加
     */
    public static void zeroDamageContainer(LivingDamageEvent.Pre event) {
        if (event == null) return;
        event.setNewDamage(0.0f);
        try {
            Object container = event.getContainer();
            if (container != null) {
                for (String fieldName : new String[]{"originalDamage", "newDamage"}) {
                    try {
                        Field f = container.getClass().getDeclaredField(fieldName);
                        f.setAccessible(true);
                        f.setFloat(container, 0.0f);
                    } catch (Throwable ignored) {
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public static void markCoreDamageSettledThisTick(Player player) {
        if (player == null) return;
        LAST_CORE_DAMAGE_SETTLED_TICK.put(player.getUUID(), player.level().getGameTime());
    }

    /**
     * 【本体权威 3D 解剖学射线部位判定引擎】
     *
     * 彻底解决“明明瞄的是腿中的却是胸，瞄头和手也有这种问题”：
     * 1. 直接基于攻击者眼睛/准星射线 (attacker.getEyePosition -> lookAngle / bulletVelocity) 与受击者中轴脊柱平面求交，
     *    完全消除旧版对外扩包围盒 (inflate) 前壁求交导致向下瞄腿时射线提前 0.48 格命中前壁、高度偏高落入胸腔的几何误差；
     * 2. 结合服务端权威朝向 (getYRot 与 yBodyRot) 将交点投影至受击者解剖局部坐标系：
     *    - 头部 (HEAD)：解剖平面相对高度 relY >= 0.72（完整覆盖下颌、面部与颅顶）；
     *    - 下肢 (LEFT_LEG / RIGHT_LEG)：relY < 0.36 全域，或 0.36 <= relY < 0.46 且位于髋骨/大腿宽度内 (|bodyLateral| < 0.175)；
     *    - 双臂与双手 (LEFT_ARM / RIGHT_ARM)：
     *      * 0.36 <= relY < 0.46 且位于髋部外侧下垂手掌区 (|bodyLateral| >= 0.175)；
     *      * 0.46 <= relY < 0.72 且位于胸骨核心区外侧 (|bodyLateral| >= 0.112)，或持握武器前伸手部区，或侧面射击直接命中的外侧手臂；
     *    - 胸腔核心 (CHEST)：0.46 <= relY < 0.72 且位于正中胸廓核心区 (|bodyLateral| < 0.112)。
     */
    public static MedicalData.Limb resolvePreciseHitLimb3D(Player player,
                                                           Entity directProjectile,
                                                           LivingEntity attacker,
                                                           boolean forceHeadshot) {
        if (forceHeadshot) {
            return MedicalData.Limb.HEAD;
        }
        if (player == null) {
            return MedicalData.Limb.CHEST;
        }

        double px = player.getX();
        double py = player.getY();
        double pz = player.getZ();
        double bbH = Math.max(0.6, player.getBbHeight());

        Vec3 rayOrigin = null;
        Vec3 rayDir = null;

        if (attacker != null && directProjectile != null) {
            Vec3 projPos = directProjectile.position();
            Vec3 projVel = directProjectile.getDeltaMovement();
            Vec3 eyePos = attacker.getEyePosition();
            // 若子弹刚在枪口生成（Tick 0 贴近射手眼睛），直接以射手眼睛为起点、子弹速度向量（或准星视线）为方向
            if (projPos.distanceToSqr(eyePos) < 1.25) {
                rayOrigin = eyePos;
                rayDir = (projVel.lengthSqr() > 1.0E-4) ? projVel.normalize() : attacker.getLookAngle().normalize();
            } else if (projVel.lengthSqr() > 1.0E-4) {
                rayOrigin = projPos;
                rayDir = projVel.normalize();
            } else {
                rayOrigin = eyePos;
                rayDir = attacker.getLookAngle().normalize();
            }
        } else if (attacker != null) {
            rayOrigin = attacker.getEyePosition();
            rayDir = attacker.getLookAngle().normalize();
        } else if (directProjectile != null) {
            rayOrigin = directProjectile.position();
            Vec3 vel = directProjectile.getDeltaMovement();
            if (vel.lengthSqr() > 1.0E-4) {
                rayDir = vel.normalize();
            } else {
                Vec3 toCenter = new Vec3(px - rayOrigin.x, (py + bbH * 0.5) - rayOrigin.y, pz - rayOrigin.z);
                rayDir = toCenter.lengthSqr() > 1.0E-5 ? toCenter.normalize() : new Vec3(0.0, 0.0, 1.0);
            }
        }

        if (rayOrigin == null || rayDir == null) {
            return MedicalData.Limb.CHEST;
        }

        // 计算 3D 射线在水平面 (X, Z) 上到达受击玩家垂直中轴线 (px, pz) 的最近参数 tSpine
        double dx0 = px - rayOrigin.x;
        double dz0 = pz - rayOrigin.z;
        double horizLenSq = rayDir.x * rayDir.x + rayDir.z * rayDir.z;

        if (horizLenSq <= 1.0E-5) {
            // 垂直正上方或正下方射击
            return rayDir.y < 0.0 ? MedicalData.Limb.HEAD : MedicalData.Limb.RIGHT_LEG;
        }

        double horizLen = Math.sqrt(horizLenSq);
        double tSpine = (dx0 * rayDir.x + dz0 * rayDir.z) / horizLenSq;
        // 解剖表面采样点：仅在脊柱中轴前方 0.08 格（真实腿部/头部骨骼表面），彻底避免外扩包围盒导致的高度偏移
        double tSurface = tSpine - (0.08 / horizLen);

        double hitY = rayOrigin.y + tSurface * rayDir.y;
        double relY = (hitY - py) / bbH;

        // 在脊柱截面 tSpine 处计算射线相对于玩家中轴线的横向偏距向量 (offX, offZ)
        double spineX = rayOrigin.x + tSpine * rayDir.x;
        double spineZ = rayOrigin.z + tSpine * rayDir.z;
        double offX = spineX - px;
        double offZ = spineZ - pz;

        // 服务端玩家原地转头时 yBodyRot 可能滞后，结合 getYRot() 获得最准确的身体朝向
        float headYaw = player.getYRot();
        float bodyYaw = player.yBodyRot;
        float yawDiff = Mth.wrapDegrees(headYaw - bodyYaw);
        float effectiveYaw = (Math.abs(yawDiff) > 32.0f) ? (bodyYaw + yawDiff * 0.75f) : bodyYaw;
        float yawRad = effectiveYaw * ((float) Math.PI / 180.0f);

        // 玩家解剖学右侧单位向量 (-cos(yaw), -sin(yaw)) 与前向单位向量 (-sin(yaw), cos(yaw))
        double bodyRightX = -Mth.cos(yawRad);
        double bodyRightZ = -Mth.sin(yawRad);
        double bodyFwdX = -Mth.sin(yawRad);
        double bodyFwdZ = Mth.cos(yawRad);

        // bodyLateral > 0 为玩家右侧 (RIGHT_ARM / RIGHT_LEG)，< 0 为玩家左侧 (LEFT_ARM / LEFT_LEG)
        double bodyLateral = offX * bodyRightX + offZ * bodyRightZ;
        double bodyForwardOff = offX * bodyFwdX + offZ * bodyFwdZ;

        double rayDotRight = (rayDir.x * bodyRightX + rayDir.z * bodyRightZ) / horizLen;
        double rayDotFwd = (rayDir.x * bodyFwdX + rayDir.z * bodyFwdZ) / horizLen;

        // 1) 头部区域 (relY >= 0.72，覆盖下巴、脸部与头顶)
        if (relY >= 0.72) {
            return MedicalData.Limb.HEAD;
        }

        // 2) 小腿与膝盖区域 (relY < 0.36)：100% 命中腿部
        if (relY < 0.36) {
            boolean isRightLeg = (Math.abs(bodyLateral) > 0.025) ? (bodyLateral >= 0.0) : (-rayDotRight >= 0.0);
            return isRightLeg ? MedicalData.Limb.RIGHT_LEG : MedicalData.Limb.LEFT_LEG;
        }

        // 3) 手掌下垂与大腿上段髋关节交界区 (0.36 <= relY < 0.46)：
        //    - 外侧 (|bodyLateral| >= 0.17) 为自然下垂的手部/手腕 (LEFT_ARM / RIGHT_ARM)；
        //    - 内侧 (|bodyLateral| < 0.17) 为大腿根部/髋部 (LEFT_LEG / RIGHT_LEG)，彻底杜绝打大腿根被判成胸部！
        if (relY < 0.46) {
            if (Math.abs(bodyLateral) >= 0.17) {
                return bodyLateral >= 0.0 ? MedicalData.Limb.RIGHT_ARM : MedicalData.Limb.LEFT_ARM;
            }
            boolean isRightLeg = (Math.abs(bodyLateral) > 0.025) ? (bodyLateral >= 0.0) : (-rayDotRight >= 0.0);
            return isRightLeg ? MedicalData.Limb.RIGHT_LEG : MedicalData.Limb.LEFT_LEG;
        }

        // 4) 上半身区域 (0.46 <= relY < 0.72)：精准区分左右手臂/手部与中央胸廓
        // 4A. 正面或背面射击：只要横向偏距超出胸骨正中核心区 (|bodyLateral| >= 0.112)，即命中左右手臂
        if (Math.abs(bodyLateral) >= 0.112) {
            return bodyLateral >= 0.0 ? MedicalData.Limb.RIGHT_ARM : MedicalData.Limb.LEFT_ARM;
        }

        // 4B. 持握枪械/物品时前臂与手部横在胸前偏下 (0.46 <= relY < 0.57 且 |bodyLateral| >= 0.08)
        if (relY < 0.57 && Math.abs(bodyLateral) >= 0.08 && (!player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty())) {
            return bodyLateral >= 0.0 ? MedicalData.Limb.RIGHT_ARM : MedicalData.Limb.LEFT_ARM;
        }

        // 4C. 侧面射击（从玩家正左或正右侧方射击）：外侧上臂直接挡在胸廓外侧
        if (Math.abs(rayDotRight) >= 0.65 && Math.abs(bodyForwardOff) <= 0.14 && relY >= 0.46 && relY <= 0.70) {
            return (-rayDotRight >= 0.0) ? MedicalData.Limb.RIGHT_ARM : MedicalData.Limb.LEFT_ARM;
        }

        return MedicalData.Limb.CHEST;
    }

    /**
     * 【Mod 本体核心：受创暂时摔倒 + 手部无提示静默物理脱手】
     * 无论是原版伤害（弓箭、近战重击、三叉戟、爆炸）还是 TaCZ 枪击，统一调用此本体方法！
     */
    public static void applyCoreTraumaKnockdownAndDisarm(ServerPlayer player,
                                                         MedicalData data,
                                                         MedicalData.Limb hitLimb,
                                                         MedicalData.LimbData ld,
                                                         Entity directProjectile,
                                                         LivingEntity attacker,
                                                         boolean isHeavyCaliberOrAmr,
                                                         float effectiveTrauma,
                                                         float totalRawDamage) {
        if (player == null || data == null || ld == null || player.level().isClientSide()) return;
        if (player.isCreative() || player.isSpectator()) return;
        if (!safeCfgBool(MedicalConfig.RAGDOLL_COMPAT_ENABLED, true)) return;

        Vec3 impactDir = computeCoreImpactDirection(player, directProjectile, attacker);

        // 1) 手部/手臂 (LEFT_ARM / RIGHT_ARM) 受伤 -> 手中物品直接物理抛物线脱手飞出（无任何文字提示！）
        if (hitLimb == MedicalData.Limb.RIGHT_ARM || hitLimb == MedicalData.Limb.LEFT_ARM) {
            checkAndTriggerCoreArmDisarm(player, hitLimb, ld, Math.max(effectiveTrauma, totalRawDamage * 0.75f), impactDir);
        }

        // 2) 意识归零、心脏骤停或脑死亡 -> 直接倒地不起
        if (data.getConsciousness() <= 0 || data.isCardiacArrest() || data.getBrainHealth() <= 0) {
            triggerCoreTemporaryKnockdown(player, 40, impactDir.scale(0.42));
            return;
        }

        // 3) 大口径反器材武器或超阈值重击 -> 全身猛烈击倒（数秒后站起）
        float heavyThresh = (float) safeCfgDouble(MedicalConfig.RAGDOLL_HEAVY_CALIBER_KNOCKDOWN_DAMAGE, 13.5);
        if (isHeavyCaliberOrAmr || totalRawDamage >= heavyThresh || effectiveTrauma >= heavyThresh * 0.85f) {
            int durTicks = safeCfgInt(MedicalConfig.RAGDOLL_HEAVY_CALIBER_DURATION_TICKS, 85);
            float launchMag = Mth.clamp(0.46f + totalRawDamage * 0.025f, 0.42f, 1.20f);
            Vec3 launchVel = new Vec3(impactDir.x * launchMag, Math.max(0.22, impactDir.y * 0.30 + 0.24), impactDir.z * launchMag);
            triggerCoreTemporaryKnockdown(player, durTicks, launchVel);
            return;
        }

        // 4) 腿部 (LEFT_LEG / RIGHT_LEG) 受较重伤或发生骨折/脱臼/截肢 -> 立即扑倒摔倒，过一会儿自动站起！
        if (hitLimb == MedicalData.Limb.LEFT_LEG || hitLimb == MedicalData.Limb.RIGHT_LEG) {
            float legThresh = Math.min(3.5f, (float) safeCfgDouble(MedicalConfig.RAGDOLL_LEG_HEAVY_DAMAGE_THRESHOLD, 3.2));
            boolean severeLegState = ld.isAmputated() || ld.hasFracture() || ld.hasDislocation() || ld.getPainLevel() >= 5;
            if (effectiveTrauma >= legThresh || totalRawDamage >= legThresh || severeLegState) {
                int baseDur = safeCfgInt(MedicalConfig.RAGDOLL_LEG_KNOCKDOWN_DURATION_TICKS, 65);
                int actualDur = severeLegState ? Math.round(baseDur * 1.25f) : baseDur;
                Vec3 curVel = player.getDeltaMovement();
                Vec3 tripVel = new Vec3(
                        curVel.x * 0.55 + impactDir.x * 0.38,
                        Math.max(0.15, curVel.y + 0.16),
                        curVel.z * 0.55 + impactDir.z * 0.38
                );
                triggerCoreTemporaryKnockdown(player, actualDur, tripVel);
            }
        }
    }

    /**
     * 本体手部受创无提示物理脱手（彻底移除任何文字提示，支持原版武器/盾牌/工具与 TaCZ 枪械）
     */
    public static void checkAndTriggerCoreArmDisarm(ServerPlayer player,
                                                    MedicalData.Limb armLimb,
                                                    MedicalData.LimbData armLd,
                                                    float traumaDamage,
                                                    Vec3 impactDir) {
        if (player == null || armLd == null || player.isCreative() || player.isSpectator()) return;

        long nowTick = player.level().getGameTime();
        Long lastTick = LAST_DISARM_TICK.get(player.getUUID());
        if (lastTick != null && nowTick - lastTick < 8L) return;

        float disarmThresh = Math.min(3.2f, (float) safeCfgDouble(MedicalConfig.RAGDOLL_ARM_DISARM_DAMAGE_THRESHOLD, 3.0));
        boolean structuralArmFailure = armLd.isAmputated() || armLd.hasFracture() || armLd.hasDislocation() || armLd.getPainLevel() >= 6;

        if (!structuralArmFailure && traumaDamage < disarmThresh) {
            return;
        }

        float baseChance = (float) safeCfgDouble(MedicalConfig.RAGDOLL_ARM_HEAVY_HIT_DISARM_CHANCE, 0.88);
        float chance = structuralArmFailure
                ? 1.0f
                : Mth.clamp(baseChance + (traumaDamage - disarmThresh) * 0.05f, 0.35f, 0.98f);

        if (player.getRandom().nextFloat() > chance) {
            return;
        }

        InteractionHand targetHand = (armLimb == MedicalData.Limb.RIGHT_ARM)
                ? InteractionHand.MAIN_HAND
                : InteractionHand.OFF_HAND;

        ItemStack stackToDrop = player.getItemInHand(targetHand);
        // 若左臂中弹/受创时副手为空，但主手握持武器/物品，左臂失力同样会导致主手握持物脱手！
        if (stackToDrop.isEmpty() && armLimb == MedicalData.Limb.LEFT_ARM) {
            ItemStack mainStack = player.getMainHandItem();
            if (!mainStack.isEmpty()) {
                targetHand = InteractionHand.MAIN_HAND;
                stackToDrop = mainStack;
            }
        } else if (stackToDrop.isEmpty() && armLimb == MedicalData.Limb.RIGHT_ARM) {
            ItemStack offStack = player.getOffhandItem();
            if (!offStack.isEmpty()) {
                targetHand = InteractionHand.OFF_HAND;
                stackToDrop = offStack;
            }
        }

        if (stackToDrop.isEmpty()) return;

        LAST_DISARM_TICK.put(player.getUUID(), nowTick);
        ItemStack droppedCopy = stackToDrop.copy();
        player.setItemInHand(targetHand, ItemStack.EMPTY);
        player.stopUsingItem();
        player.inventoryMenu.broadcastChanges();

        float throwSpeed = (float) safeCfgDouble(MedicalConfig.RAGDOLL_ARM_DISARM_THROW_SPEED, 0.42);
        int pickupDelay = safeCfgInt(MedicalConfig.RAGDOLL_ARM_DISARM_PICKUP_DELAY_TICKS, 40);

        Vec3 look = player.getLookAngle();
        double sideSign = (targetHand == InteractionHand.MAIN_HAND) ? 0.32 : -0.32;
        Vec3 rightVec = new Vec3(-look.z, 0.0, look.x);
        if (rightVec.lengthSqr() > 1.0E-4) rightVec = rightVec.normalize();

        Vec3 imp = (impactDir != null) ? impactDir : Vec3.ZERO;
        double vx = look.x * throwSpeed * 0.78 + rightVec.x * sideSign * throwSpeed + imp.x * throwSpeed * 0.50
                + (player.getRandom().nextDouble() - 0.5) * 0.10;
        double vy = 0.22 + Math.min(0.24, traumaDamage * 0.012);
        double vz = look.z * throwSpeed * 0.78 + rightVec.z * sideSign * throwSpeed + imp.z * throwSpeed * 0.50
                + (player.getRandom().nextDouble() - 0.5) * 0.10;

        ItemEntity itemEntity = new ItemEntity(
                player.level(),
                player.getX() + look.x * 0.35 + rightVec.x * sideSign * 0.25,
                player.getEyeY() - 0.25,
                player.getZ() + look.z * 0.35 + rightVec.z * sideSign * 0.25,
                droppedCopy
        );
        itemEntity.setDeltaMovement(vx, vy, vz);
        itemEntity.setPickUpDelay(pickupDelay);
        player.level().addFreshEntity(itemEntity);

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS, 0.90f, 0.78f);
        // 严格遵循需求：脱手不弹出任何文字提示！
    }

    /**
     * 本体受创暂时摔倒触发器（第一人称扑倒 + 真实速度冲量 + 倒计时结束后自动站起）
     */
    public static void triggerCoreTemporaryKnockdown(ServerPlayer player,
                                                     int durationTicks,
                                                     Vec3 launchVelocity) {
        if (player == null || !player.isAlive() || player.isCreative() || player.isSpectator()) return;

        if (launchVelocity != null && launchVelocity.lengthSqr() > 1.0E-4) {
            player.setSprinting(false);
            player.stopUsingItem();
            player.setDeltaMovement(launchVelocity);
            player.hasImpulse = true;
            player.hurtMarked = true;
        }

        UUID uid = player.getUUID();
        int cur = SERVER_KNOCKDOWN_TICKS.getOrDefault(uid, 0);
        SERVER_KNOCKDOWN_TICKS.put(uid, Math.max(cur, Math.max(20, durationTicks)));

        player.setForcedPose(Pose.SWIMMING);
        player.setPose(Pose.SWIMMING);
        player.refreshDimensions();

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_SMALL_FALL, SoundSource.PLAYERS, 0.95f, 0.76f);
    }

    public static Vec3 computeCoreImpactDirection(Player player, Entity projectile, LivingEntity attacker) {
        if (projectile != null) {
            Vec3 vel = projectile.getDeltaMovement();
            if (vel.lengthSqr() > 1.0E-4) {
                return vel.normalize();
            }
            Vec3 diff = player.position().subtract(projectile.position());
            if (diff.lengthSqr() > 1.0E-4) {
                return diff.normalize();
            }
        }
        if (attacker != null) {
            Vec3 diff = player.position().subtract(attacker.position());
            if (diff.lengthSqr() > 1.0E-4) {
                return diff.normalize();
            }
        }
        Vec3 revLook = player.getLookAngle().reverse();
        return revLook.lengthSqr() > 1.0E-4 ? revLook.normalize() : new Vec3(0.0, 0.0, -1.0);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityJoinCleanupLegacy(EntityJoinLevelEvent event) {
        if (!legacyHandlersCleanedUp) {
            ensureLegacyDamageHandlersCleanedUp();
        }
    }

    /**
     * 本体高空坠落腿部摔倒联动：当坠落高度超过安全高度产生摔伤时，自动触发第一人称扑倒摔倒
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onCoreFallKnockdown(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide()) return;
        if (!player.isAlive() || player.isCreative() || player.isSpectator()) return;

        float safeDist = (float) safeCfgDouble(MedicalConfig.SAFE_FALL_DISTANCE, 5.0);
        float dist = event.getDistance();
        if (dist >= safeDist + 1.0f) {
            int baseDur = safeCfgInt(MedicalConfig.RAGDOLL_LEG_KNOCKDOWN_DURATION_TICKS, 65);
            int scaledDur = Mth.clamp(Math.round(baseDur * (0.75f + (dist - safeDist) * 0.12f)), 35, 160);
            Vec3 look = player.getLookAngle();
            triggerCoreTemporaryKnockdown(player, scaledDur, new Vec3(look.x * 0.22, 0.10, look.z * 0.22));
        }
    }

    /**
     * 本体爆炸气浪摔倒与震落脱手联动
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onCoreExplosionDetonate(ExplosionEvent.Detonate event) {
        if (event.getLevel().isClientSide()) return;
        if (!safeCfgBool(MedicalConfig.RAGDOLL_COMPAT_ENABLED, true)) return;

        Vec3 center = event.getExplosion().center();
        float radius = Math.max(2.0f, event.getExplosion().radius());

        for (Entity entity : event.getAffectedEntities()) {
            if (!(entity instanceof ServerPlayer player) || !player.isAlive() || player.isCreative() || player.isSpectator()) {
                continue;
            }
            double dist = player.position().distanceTo(center);
            if (dist > radius * 2.1) continue;

            double exposure = Mth.clamp(1.0 - (dist / (radius * 2.0)), 0.15, 1.0);
            float estBlastDmg = (float) (exposure * radius * 3.2);
            float minBlastDmg = (float) safeCfgDouble(MedicalConfig.RAGDOLL_EXPLOSION_MIN_DAMAGE, 3.5);
            if (estBlastDmg < minBlastDmg) continue;

            Vec3 away = player.position().add(0.0, 0.5, 0.0).subtract(center);
            Vec3 dir = away.lengthSqr() > 1.0E-4 ? away.normalize() : player.getLookAngle().reverse();
            double push = Mth.clamp(0.45 + exposure * 0.85, 0.40, 1.40);
            Vec3 blastVel = new Vec3(dir.x * push, Math.min(0.85, 0.26 + exposure * 0.45), dir.z * push);

            if (estBlastDmg >= minBlastDmg * 1.8f) {
                MedicalData data = player.getData(MedicalDataCapability.MEDICAL_DATA);
                if (data != null) {
                    MedicalData.LimbData rightArm = data.getLimb(MedicalData.Limb.RIGHT_ARM);
                    checkAndTriggerCoreArmDisarm(player, MedicalData.Limb.RIGHT_ARM, rightArm, estBlastDmg * 0.70f, dir);
                }
            }

            int baseDur = safeCfgInt(MedicalConfig.RAGDOLL_EXPLOSION_DURATION_TICKS, 90);
            int durTicks = Mth.clamp((int) Math.round(baseDur * (0.7 + exposure * 0.6)), 35, 180);
            triggerCoreTemporaryKnockdown(player, durTicks, blastVel);
        }
    }

    /**
     * 本体原版与非 TaCZ 伤害（近战、弓箭、弩箭、三叉戟、怪物攻击、原版爆炸）高精度 3D 部位判定与摔倒/脱手结算器
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onCoreLivingDamagePre(LivingDamageEvent.Pre event) {
        if (!legacyHandlersCleanedUp) {
            ensureLegacyDamageHandlersCleanedUp();
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide() || !player.isAlive() || player.isCreative() || player.isSpectator()) return;

        DamageSource source = event.getSource();
        String msgId = source.getMsgId() != null ? source.getMsgId() : "";
        String msgLower = msgId.toLowerCase();

        // 1) 放行脑死亡与虚空杀
        if ("genericKill".equals(msgId) || "outOfWorld".equals(msgId)) return;

        // 2) 若本 Tick 已被 TaCZ 专属弹道处理器结算，或属于 TaCZ 伤害源，交由 TaczDamageCompatHandler 处理并确保清零容器
        Long settledTick = LAST_CORE_DAMAGE_SETTLED_TICK.get(player.getUUID());
        if (settledTick != null && settledTick == player.level().getGameTime()) {
            zeroDamageContainer(event);
            return;
        }
        Entity direct = source.getDirectEntity();
        if (msgLower.contains("tacz") || msgLower.startsWith("tac.")
                || (direct != null && direct.getClass().getName().toLowerCase().contains("tacz"))) {
            return;
        }

        // 3) 坠落、火焰、窒息、溺水、饥饿、魔法/毒素保留各自专科通道
        if ("fall".equals(msgId) || source.is(DamageTypeTags.IS_FALL)
                || "inWall".equals(msgId) || "drown".equals(msgId) || "starve".equals(msgId)
                || "magic".equals(msgId) || "indirectMagic".equals(msgId) || "wither".equals(msgId)
                || "inFire".equals(msgId) || "onFire".equals(msgId) || "lava".equals(msgId)
                || "hotFloor".equals(msgId) || "campfire".equals(msgId) || source.is(DamageTypeTags.IS_FIRE)) {
            return;
        }

        float rawDmg = Math.max(event.getOriginalDamage(), event.getNewDamage());
        if (rawDmg <= 0.1f) return;

        MedicalData data = player.getData(MedicalDataCapability.MEDICAL_DATA);
        if (data == null) return;

        LivingEntity attacker = (source.getEntity() instanceof LivingEntity le) ? le : null;

        // 4) 原版/其它模组爆炸伤害：按部位权重分配破片与冲击伤 + 触发气浪摔倒与脱手
        if (source.is(DamageTypeTags.IS_EXPLOSION) || msgLower.contains("explosion")) {
            applyCoreExplosionTrauma(player, data, direct, attacker, rawDmg);
            markCoreDamageSettledThisTick(player);
            zeroDamageContainer(event);
            return;
        }

        // 5) 原版与模组物理/投射物攻击（弓箭、弩、三叉戟、刀剑、斧头、重锤、生物近战）：
        //    使用高精度 3D 解剖射线判定命中部位（彻底解决打腿中胸、打手/头偏移）！
        MedicalData.Limb hitLimb = resolvePreciseHitLimb3D(player, direct, attacker, false);
        MedicalData.LimbData ld = data.getLimb(hitLimb);
        if (ld == null) return;

        if (ld.isAmputated() && (hitLimb == MedicalData.Limb.LEFT_ARM || hitLimb == MedicalData.Limb.RIGHT_ARM
                || hitLimb == MedicalData.Limb.LEFT_LEG || hitLimb == MedicalData.Limb.RIGHT_LEG)) {
            hitLimb = MedicalData.Limb.CHEST;
            ld = data.getLimb(MedicalData.Limb.CHEST);
            if (ld == null) return;
        }

        // 结合局部护甲换算有效创伤伤害
        float armor = (float) player.getArmorValue();
        float toughness = (float) player.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        float effectiveDmg = source.is(DamageTypeTags.BYPASSES_ARMOR)
                ? rawDmg
                : CombatRules.getDamageAfterAbsorb(player, rawDmg, source, armor * 0.65f, toughness);
        effectiveDmg = Math.max(rawDmg * 0.35f, effectiveDmg);

        boolean isPiercing = source.is(DamageTypeTags.IS_PROJECTILE) || msgLower.contains("arrow")
                || msgLower.contains("trident") || msgLower.contains("sting") || msgLower.contains("thorns") || msgLower.contains("cactus");
        boolean isBlunt = msgLower.contains("anvil") || msgLower.contains("falling_block") || msgLower.contains("mace")
                || msgLower.contains("fly_into_wall") || (attacker != null && attacker.getMainHandItem().isEmpty());

        double extRatio = isPiercing
                ? safeCfgDouble(MedicalConfig.PIERCING_EXTERNAL_RATIO, 0.80)
                : (isBlunt ? safeCfgDouble(MedicalConfig.BLUNT_EXTERNAL_RATIO, 0.80) : safeCfgDouble(MedicalConfig.SLASHING_EXTERNAL_RATIO, 0.95));
        double intRatio = isPiercing
                ? safeCfgDouble(MedicalConfig.PIERCING_INTERNAL_RATIO, 0.20)
                : (isBlunt ? safeCfgDouble(MedicalConfig.BLUNT_INTERNAL_RATIO, 0.20) : safeCfgDouble(MedicalConfig.SLASHING_INTERNAL_RATIO, 0.05));
        double openFracChance = isPiercing
                ? safeCfgDouble(MedicalConfig.PIERCING_FRACTURE_OPEN_CHANCE, 1.00)
                : (isBlunt ? safeCfgDouble(MedicalConfig.BLUNT_FRACTURE_OPEN_CHANCE, 0.00) : safeCfgDouble(MedicalConfig.SLASHING_FRACTURE_OPEN_CHANCE, 0.80));

        int maxPain = safeCfgInt(MedicalConfig.MAX_PAIN_LEVEL, 10);
        int maxIntRate = safeCfgInt(MedicalConfig.MAX_INTERNAL_RATE, 300);
        double ratePerDmg = safeCfgDouble(MedicalConfig.RATE_PER_DAMAGE, 8.0);

        int extBleedAdd = (int) Math.round(effectiveDmg * 18.0 * extRatio);
        int intBleedAdd = (int) Math.round(effectiveDmg * ratePerDmg * (0.45 + intRatio));

        if (extBleedAdd > 5 && !isBlunt) {
            ld.setOpenWound(true);
            ld.setBandaged(false);
            int nextBleed = Math.min(2500, ld.getBleeding() + extBleedAdd);
            ld.setBleeding(nextBleed);
            if (ld.getClottingStartRate() <= 0) {
                ld.setClottingStartRate(nextBleed);
            }
        }
        if (intBleedAdd > 0 && effectiveDmg >= safeCfgInt(MedicalConfig.TRIGGER_DAMAGE_THRESHOLD, 4)) {
            ld.setInternalBleedingRate(Math.min(maxIntRate, ld.getInternalBleedingRate() + intBleedAdd));
        }

        int painAdd = Math.max(1, Math.round(effectiveDmg * 0.65f));
        ld.setPainLevel(Math.min(maxPain, Math.max(ld.getPainLevel(), ld.getPainLevel() + Math.max(1, painAdd / 2))));

        // 原版伤害骨折判定
        int minFracDmg = safeCfgInt(MedicalConfig.MIN_DAMAGE_FOR_FRACTURE, 8);
        if (effectiveDmg >= minFracDmg || rawDmg >= minFracDmg) {
            float fracChance = (rawDmg >= safeCfgInt(MedicalConfig.HIGH_DAMAGE_THRESHOLD, 12))
                    ? (float) safeCfgDouble(MedicalConfig.HIGH_DAMAGE_FRACTURE_CHANCE, 0.40)
                    : (float) safeCfgDouble(MedicalConfig.BASE_FRACTURE_CHANCE, 0.20);
            if (isBlunt) fracChance *= 1.35f;
            if (player.getRandom().nextFloat() < fracChance) {
                ld.setFracture(true);
                if (player.getRandom().nextFloat() < openFracChance) {
                    ld.setOpenFracture(true);
                    ld.setOpenWound(true);
                }
                ld.setSplint(false);
                setLimbOrthopedicFixed(ld, false);
                int fracPain = safeCfgInt(MedicalConfig.FRACTURE_PAIN_LEVEL, 7);
                if (ld.getPainLevel() < fracPain) ld.setPainLevel(Math.min(maxPain, fracPain));
                setFractureTimerAuth(ld, safeCfgInt(MedicalConfig.SPLINT_FRACTURE_HEAL_TIME_SEC, 300));
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.BONE_BLOCK_BREAK, SoundSource.PLAYERS, 0.95f, 0.75f);
            }
        }

        // 头部重击脑损伤与颅内出血
        if (hitLimb == MedicalData.Limb.HEAD && effectiveDmg >= safeCfgDouble(MedicalConfig.HEAD_BRAIN_DAMAGE_THRESHOLD, 6.0)) {
            float brainLoss = (effectiveDmg - 4.0f) * 3.2f;
            data.setBrainHealthFloat(Math.max(0.0f, data.getBrainHealthFloat() - brainLoss));
            data.setConsciousnessFloat(Math.max(0.0f, data.getConsciousnessFloat() - effectiveDmg * 3.5f));
            if (rawDmg >= 10.0f) {
                int icMax = safeCfgInt(MedicalConfig.IC_MAX_RATE, 30);
                int icAdd = Math.max(3, (int) Math.round(effectiveDmg * safeCfgDouble(MedicalConfig.IC_RATE_PER_DAMAGE, 1.2)));
                data.setIntracranialBleedRate(Math.min(icMax, data.getIntracranialBleedRate() + icAdd));
            }
        } else if (hitLimb == MedicalData.Limb.CHEST && effectiveDmg >= 5.0f) {
            data.setLungContusion(Math.min(100.0f, data.getLungContusion() + effectiveDmg * 1.4f));
        }

        triggerTraumaAdrenalineRush(player, data, rawDmg);

        // ★ 调用本体受创摔倒与手部无提示脱手系统 ★
        applyCoreTraumaKnockdownAndDisarm(player, data, hitLimb, ld, direct, attacker, false, effectiveDmg, rawDmg);

        markCoreDamageSettledThisTick(player);
        player.syncData(MedicalDataCapability.MEDICAL_DATA);
        zeroDamageContainer(event);

        if (data.getBrainHealth() <= 0 && player.isAlive()) {
            player.hurt(player.damageSources().genericKill(), Float.MAX_VALUE);
        }
    }

    private static void applyCoreExplosionTrauma(ServerPlayer player,
                                                 MedicalData data,
                                                 Entity directProjectile,
                                                 LivingEntity attacker,
                                                 float rawExplosionDamage) {
        double headRatio = safeCfgDouble(MedicalConfig.EXPLOSION_HEAD_RATIO, 0.15);
        double chestRatio = safeCfgDouble(MedicalConfig.EXPLOSION_CHEST_RATIO, 0.35);
        double armRatio = safeCfgDouble(MedicalConfig.EXPLOSION_ARM_RATIO, 0.13);
        double legRatio = safeCfgDouble(MedicalConfig.EXPLOSION_LEG_RATIO, 0.12);
        double expExtRatio = safeCfgDouble(MedicalConfig.EXPLOSION_EXTERNAL_RATIO, 0.30);
        double expIntRatio = safeCfgDouble(MedicalConfig.EXPLOSION_INTERNAL_RATIO, 0.70);
        int maxPain = safeCfgInt(MedicalConfig.MAX_PAIN_LEVEL, 10);
        int maxIntRate = safeCfgInt(MedicalConfig.MAX_INTERNAL_RATE, 300);

        data.setLungContusion(Math.min(100.0f, data.getLungContusion() + rawExplosionDamage * 2.5f));

        for (MedicalData.Limb limb : MedicalData.Limb.values()) {
            MedicalData.LimbData ld = data.getLimb(limb);
            if (ld == null || ld.isAmputated()) continue;
            float weight = switch (limb) {
                case HEAD -> (float) headRatio;
                case CHEST -> (float) chestRatio;
                case LEFT_ARM, RIGHT_ARM -> (float) armRatio;
                case LEFT_LEG, RIGHT_LEG -> (float) legRatio;
            };
            float limbDmg = rawExplosionDamage * weight * 1.55f;
            if (limbDmg < 1.0f) continue;

            int extAdd = (int) Math.round(limbDmg * 20.0 * expExtRatio);
            int intAdd = (int) Math.round(limbDmg * 11.0 * expIntRatio);
            if (extAdd > 8) {
                ld.setOpenWound(true);
                ld.setBandaged(false);
                ld.setBleeding(Math.min(2500, ld.getBleeding() + extAdd));
            }
            if (intAdd > 0) {
                ld.setInternalBleedingRate(Math.min(maxIntRate, ld.getInternalBleedingRate() + intAdd));
            }
            ld.setPainLevel(Math.min(maxPain, Math.max(ld.getPainLevel(), Math.round(limbDmg * 0.8f))));
        }

        Vec3 blastDir = computeCoreImpactDirection(player, directProjectile, attacker);
        float blastStrength = Mth.clamp(0.42f + rawExplosionDamage * 0.045f, 0.40f, 1.45f);
        Vec3 blastVel = new Vec3(blastDir.x * blastStrength, Math.min(0.85, 0.28 + rawExplosionDamage * 0.022), blastDir.z * blastStrength);

        if (rawExplosionDamage >= 6.0f) {
            MedicalData.LimbData rightArm = data.getLimb(MedicalData.Limb.RIGHT_ARM);
            checkAndTriggerCoreArmDisarm(player, MedicalData.Limb.RIGHT_ARM, rightArm, rawExplosionDamage * 0.65f, blastDir);
        }
        int baseDur = safeCfgInt(MedicalConfig.RAGDOLL_EXPLOSION_DURATION_TICKS, 90);
        int scaledDur = Mth.clamp(Math.round(baseDur * (0.75f + rawExplosionDamage / 24.0f)), 40, 200);
        triggerCoreTemporaryKnockdown(player, scaledDur, blastVel);
        player.syncData(MedicalDataCapability.MEDICAL_DATA);
    }

    // =========================================================================
    // 13. 客户端第一人称摔倒/布娃娃视角强制锁控与 3D 倒地镜头物理缓冲 (Dist.CLIENT)
    // =========================================================================

    @EventBusSubscriber(modid = RealMedicalMod.MODID, value = Dist.CLIENT)
    public static final class ClientCoreKnockdownHooks {

        private static float smoothKnockdownRoll = 0.0f;
        private static float smoothKnockdownPitch = 0.0f;
        private static long lastFrameNs = 0L;
        private static int lastLeftLegPain = -1;
        private static int lastRightLegPain = -1;
        private static Field cameraDetachedField = null;
        private static Field cameraPositionField = null;
        private static boolean cameraFieldChecked = false;

        private ClientCoreKnockdownHooks() {
        }

        @SubscribeEvent
        public static void onClientTickPre(ClientTickEvent.Pre event) {
            enforceFirstPersonIfRagdollOrKnockdown();
        }

        @SubscribeEvent
        public static void onClientTickPost(ClientTickEvent.Post event) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            Player player = mc.player;
            if (player == null || mc.level == null || mc.isPaused()) return;

            if (clientKnockdownMirrorTicks > 0) {
                clientKnockdownMirrorTicks--;
            }

            MedicalData data = player.getData(MedicalDataCapability.MEDICAL_DATA);
            if (data != null) {
                MedicalData.LimbData lLeg = data.getLimb(MedicalData.Limb.LEFT_LEG);
                MedicalData.LimbData rLeg = data.getLimb(MedicalData.Limb.RIGHT_LEG);
                int lp = lLeg != null ? lLeg.getPainLevel() : 0;
                int rp = rLeg != null ? rLeg.getPainLevel() : 0;
                if (lastLeftLegPain >= 0 && lastRightLegPain >= 0) {
                    if ((lp - lastLeftLegPain >= 2) || (rp - lastRightLegPain >= 2)) {
                        int baseDur = safeCfgInt(MedicalConfig.RAGDOLL_LEG_KNOCKDOWN_DURATION_TICKS, 65);
                        clientKnockdownMirrorTicks = Math.max(clientKnockdownMirrorTicks, baseDur);
                    }
                }
                lastLeftLegPain = lp;
                lastRightLegPain = rp;
            }

            enforceFirstPersonIfRagdollOrKnockdown();
        }

        private static void enforceFirstPersonIfRagdollOrKnockdown() {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            Player player = mc.player;
            if (player == null || mc.options == null) return;

            MedicalData data = player.getData(MedicalDataCapability.MEDICAL_DATA);
            boolean isKnockedDownOrUnconscious = clientKnockdownMirrorTicks > 0
                    || player.getForcedPose() == Pose.SWIMMING
                    || (data != null && (data.getConsciousness() <= 0 || data.isCardiacArrest()));

            // 当处于受创摔倒、布娃娃或昏迷状态时，强制锁定为第一人称视角 (CameraType.FIRST_PERSON)，绝不跳到第三人称！
            if (isKnockedDownOrUnconscious && !mc.options.getCameraType().isFirstPerson()) {
                mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            }
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            Player player = mc.player;
            if (player == null || mc.level == null || mc.isPaused() || player.isSpectator()) return;

            long nowNs = System.nanoTime();
            float dt = (lastFrameNs == 0L) ? 0.016f : Mth.clamp((nowNs - lastFrameNs) / 1_000_000_000.0f, 0.0005f, 0.08f);
            lastFrameNs = nowNs;

            MedicalData data = player.getData(MedicalDataCapability.MEDICAL_DATA);
            boolean unconscious = data != null && (data.getConsciousness() <= 0 || data.isCardiacArrest());
            boolean permanentProne = isPermanentlyProneByInjury(player, data);
            boolean tempKnockdown = (clientKnockdownMirrorTicks > 0)
                    || (player.getForcedPose() == Pose.SWIMMING && !permanentProne);

            // 1) 若任何布娃娃 Mod (如 Sable: Ragdolls) 强行修改了 CameraType 或将 Camera.detached 设为 true 切入第三人称，
            //    在此处最终渲染阶段强制还原为第一人称眼睛视角！
            if (unconscious || tempKnockdown || permanentProne) {
                if (!mc.options.getCameraType().isFirstPerson()) {
                    mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                }
                net.minecraft.client.Camera cam = event.getCamera();
                if (cam != null) {
                    forceCameraFirstPersonUndetached(cam, player, (float) event.getPartialTick());
                }
            }

            // 2) 第一人称受创摔倒/昏迷的平滑 3D 侧倾与扑倒镜头物理动画
            float targetRoll = 0.0f;
            float targetPitch = 0.0f;
            if (unconscious) {
                targetRoll = 62.0f;
                targetPitch = 18.0f;
            } else if (tempKnockdown) {
                targetRoll = 34.0f;
                targetPitch = 12.0f;
            }

            float lerpAlpha = 1.0f - (float) Math.exp(-dt * 7.5f);
            smoothKnockdownRoll = Mth.lerp(lerpAlpha, smoothKnockdownRoll, targetRoll);
            smoothKnockdownPitch = Mth.lerp(lerpAlpha, smoothKnockdownPitch, targetPitch);

            if (Math.abs(smoothKnockdownRoll) > 0.05f || Math.abs(smoothKnockdownPitch) > 0.05f) {
                event.setRoll(event.getRoll() + smoothKnockdownRoll);
                event.setPitch(event.getPitch() + smoothKnockdownPitch);
            }
        }

        public static void forceCameraFirstPersonUndetached(net.minecraft.client.Camera cam, Player player, float partialTick) {
            if (cam == null || player == null) return;
            if (!cameraFieldChecked) {
                cameraFieldChecked = true;
                for (Field f : net.minecraft.client.Camera.class.getDeclaredFields()) {
                    if (f.getType() == boolean.class) {
                        String n = f.getName().toLowerCase();
                        if (n.contains("detached") || n.equals("f_90556_")) {
                            f.setAccessible(true);
                            cameraDetachedField = f;
                        }
                    } else if (f.getType() == Vec3.class) {
                        f.setAccessible(true);
                        cameraPositionField = f;
                    }
                }
            }
            try {
                Vec3 eyePos = player.getEyePosition(partialTick);
                boolean wasDetached = cam.isDetached();
                boolean pushedToThirdPerson = cam.getPosition().distanceToSqr(eyePos) > 0.30;

                if (wasDetached && cameraDetachedField != null) {
                    cameraDetachedField.setBoolean(cam, false);
                }
                if ((wasDetached || pushedToThirdPerson) && cameraPositionField != null) {
                    cameraPositionField.set(cam, eyePos);
                }
            } catch (Throwable ignored) {
            }
        }
    }
}