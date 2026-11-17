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

    @SubscribeEvent
    public static void onPlayerTickSyncAddictionBuff(PlayerTickEvent.Post event) {
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
                        int nextTimer = curTimer - 1;
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
}