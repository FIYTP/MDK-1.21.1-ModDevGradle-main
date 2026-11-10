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
import net.minecraft.world.item.Item;
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

    /** 从 MedicalConfig 获取大出血判定阈值 (mL/min) */
    public static int getCatastrophicBleedingThreshold() {
        return MedicalConfig.BANDAGE_MAX_BLEEDING_THRESHOLD.get();
    }

    /** 从 MedicalConfig 获取止血带连续绑扎导致肢体缺血坏死的时长 (秒) */
    public static int getTourniquetNecrosisSeconds() {
        return MedicalConfig.TOURNIQUET_NECROSIS_TIME_SEC.get();
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
        if (addictionLevel >= MedicalConfig.ADDICTION_TIER3_THRESHOLD.get()) {
            return 3;
        }
        if (addictionLevel >= MedicalConfig.ADDICTION_TIER2_THRESHOLD.get()) {
            return 2;
        }
        if (addictionLevel >= MedicalConfig.ADDICTION_TIER1_THRESHOLD.get()) {
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
        if (adrenalineLevel >= MedicalConfig.ADRENALINE_TIER3_THRESHOLD.get()) {
            return 3;
        }
        if (adrenalineLevel >= MedicalConfig.ADRENALINE_TIER2_THRESHOLD.get()) {
            return 2;
        }
        if (adrenalineLevel >= MedicalConfig.ADRENALINE_TIER1_THRESHOLD.get()) {
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
        return MedicalConfig.SUTURE_MIN_CONSCIOUSNESS.get();
    }

    /** 从 MedicalConfig 获取主干动脉断裂出血阈值 (mL/min) */
    public static int getSutureArterialThreshold() {
        return MedicalConfig.SUTURE_ARTERIAL_BLEED_THRESHOLD.get();
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
        return MedicalConfig.FASCIOTOMY_MIN_CONSCIOUSNESS.get();
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

    /**
     * 调用已有骨折固定与愈合接口（兼容 FractureTreatment 与 LimbData#setSplint / setFractureTimer）
     */
    public static void applySplintAndInitFractureHealing(MedicalData data, MedicalData.LimbData ld) {
        if (ld == null || !ld.hasFracture() || ld.isAmputated()) return;
        ld.setSplint(true);
        int healSec = MedicalConfig.SPLINT_FRACTURE_HEAL_TIME_SEC.get();
        if (ld.getFractureTimer() <= 0) {
            ld.setFractureTimer(healSec);
        }
        int painRelief = MedicalConfig.SPLINT_PAIN_RELIEF.get();
        if (painRelief > 0 && ld.getPainLevel() > 0) {
            ld.setPainLevel(Math.max(1, ld.getPainLevel() - painRelief));
        }
        tryInvokeFractureTreatmentMethod("applySplint", ld);
    }

    /**
     * 完成骨折愈合时调用已有恢复接口
     */
    public static void completeFractureHealing(MedicalData.LimbData ld) {
        if (ld == null) return;
        ld.setFracture(false);
        ld.setOpenFracture(false);
        ld.setSplint(false);
        ld.setFractureTimer(0);
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
        float mult = MedicalConfig.BURN_RATE_MULTIPLIER.get().floatValue();
        boolean isLava = "lava".equals(msgId);
        boolean isHotFloor = "hotFloor".equals(msgId);
        float baseGain = isLava
                ? MedicalConfig.BURN_LAVA_AREA_GAIN_PER_SEC.get().floatValue() * 0.65f
                : (isHotFloor
                        ? MedicalConfig.BURN_FIRE_AREA_GAIN_PER_SEC.get().floatValue() * 0.45f
                        : MedicalConfig.BURN_FIRE_AREA_GAIN_PER_SEC.get().floatValue() * 0.55f);
        float areaDelta = Math.max(0.8f, baseGain * (0.5f + 0.25f * rawDamage) * (mult / 4.0f));
        int maxPain = MedicalConfig.MAX_PAIN_LEVEL.get();

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
        if (getNaloxoneEffectSec(data) > 0 && data.getAddictionLevel() >= MedicalConfig.ADDICTION_TIER1_THRESHOLD.get()) {
            return true;
        }
        int tier = getAddictionTier(data.getAddictionLevel());
        if (tier < 2) return false;
        if (getAdrenalineTier(getAdrenalineLevel(data)) >= 1) return false;
        return data.getOpioidConcentration() < MedicalConfig.ADDICTION_WITHDRAWAL_OPIOID_THRESHOLD.get();
    }

    /**
     * 玩家受伤时触发短暂（约 3~4 秒）的内源性「肾上腺素激增 I」应激 Buff
     */
    public static void triggerTraumaAdrenalineRush(ServerPlayer player, MedicalData data, float rawDamage) {
        if (player == null || !(data instanceof MedicalDataImpl impl)) return;
        if (!MedicalConfig.ADRENALINE_TRAUMA_ENABLED.get()) return;
        if (data.getConsciousness() <= 0 || data.isCardiacArrest()) return;

        float minDmg = MedicalConfig.ADRENALINE_TRAUMA_MIN_DAMAGE.get().floatValue();
        if (rawDamage < minDmg) return;

        if (impl.getAdrenalineCooldownSec() > 0 || impl.isAdrenalineFromInjector()) return;

        float traumaGain = MedicalConfig.ADRENALINE_TRAUMA_GAIN.get().floatValue();
        float nextAdr = Math.max(impl.getAdrenalineLevelFloat(), traumaGain);
        impl.setAdrenalineLevelFloat(nextAdr);
        impl.setAdrenalineCooldownSec(MedicalConfig.ADRENALINE_TRAUMA_COOLDOWN_SEC.get());
        impl.setAdrenalineCrashSec(0);

        int durSec = MedicalConfig.ADRENALINE_TRAUMA_DURATION_SEC.get();
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
            float tier1Floor = MedicalConfig.ADRENALINE_TIER1_THRESHOLD.get().floatValue();
            float traumaDecay = MedicalConfig.ADRENALINE_TRAUMA_GAIN.get().floatValue()
                    / Math.max(1.0f, MedicalConfig.ADRENALINE_TRAUMA_DURATION_SEC.get().floatValue());
            float decayRate = fromInj
                    ? Math.max(0.2f, MedicalConfig.ADRENALINE_DECAY_RATE_PER_SEC.get().floatValue())
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
                float traumaDecay = MedicalConfig.ADRENALINE_TRAUMA_GAIN.get().floatValue()
                        / Math.max(1.0f, MedicalConfig.ADRENALINE_TRAUMA_DURATION_SEC.get().floatValue());
                float adrDecay = fromInj
                        ? MedicalConfig.ADRENALINE_DECAY_RATE_PER_SEC.get().floatValue()
                        : traumaDecay;
                float nextAdr = Math.max(0.0f, curAdr - adrDecay);
                if (!fromInj && nextAdr < MedicalConfig.ADRENALINE_TIER1_THRESHOLD.get()) {
                    nextAdr = 0.0f;
                }
                adrImpl.setAdrenalineLevelFloat(nextAdr);
                if (nextAdr <= 0.0f && fromInj) {
                    adrImpl.setAdrenalineFromInjector(false);
                    adrImpl.setAdrenalineCrashSec(MedicalConfig.ADRENALINE_CRASH_DURATION_SEC.get());
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
                int stage1Sec = MedicalConfig.TOURNIQUET_ISCHEMIA_STAGE1_SEC.get();
                int stage2Sec = MedicalConfig.TOURNIQUET_ISCHEMIA_STAGE2_SEC.get();
                int necrosisSec = MedicalConfig.TOURNIQUET_NECROSIS_TIME_SEC.get();
                int maxPain = MedicalConfig.MAX_PAIN_LEVEL.get();
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

            int selfInfRate = MedicalConfig.NECROSIS_SELF_INFECTION_RATE_PER_SEC.get();
            int otherInfRate = MedicalConfig.NECROSIS_OTHER_LIMBS_INFECTION_RATE_PER_SEC.get();
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
        if (adr >= MedicalConfig.ADRENALINE_TIER1_THRESHOLD.get()) {
            int unbandagedBleedPerMin = 0;
            for (MedicalData.Limb limb : MedicalData.Limb.values()) {
                MedicalData.LimbData ld = data.getLimb(limb);
                if (ld != null && ld.getBleeding() > 0 && !ld.isBandaged() && !ld.isTourniqueted()) {
                    unbandagedBleedPerMin += ld.getBleeding();
                }
            }
            if (unbandagedBleedPerMin > 0) {
                float extraMult = MedicalConfig.ADRENALINE_UNBANDAGED_BLEED_MULT.get().floatValue() * (adr / 65.0f);
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
        int maxRateCap = MedicalConfig.COMPARTMENT_MAX_INTERNAL_BLEED_RATE.get();
        float maxBloodCapMl = MedicalConfig.COMPARTMENT_MAX_BLOOD_CAP_ML.get().floatValue();
        float bloodAccumMult = MedicalConfig.COMPARTMENT_BLOOD_ACCUM_MULT.get().floatValue();
        float collapseStartMmHg = MedicalConfig.COMPARTMENT_VENOUS_COLLAPSE_MMHG.get().floatValue();
        float fullTamponadeMmHg = MedicalConfig.COMPARTMENT_TAMPONADE_FULL_MMHG.get().floatValue();
        float csThreshMmHg = MedicalConfig.COMPARTMENT_SYNDROME_THRESHOLD_MMHG.get().floatValue();
        float critDeltaP = MedicalConfig.COMPARTMENT_CRITICAL_DELTA_P_MMHG.get().floatValue();
        float ischStartMmHg = MedicalConfig.COMPARTMENT_ISCHEMIA_START_MMHG.get().floatValue();
        int csNecrosisSec = MedicalConfig.COMPARTMENT_NECROSIS_TIME_SEC.get();
        int csStage1Pain = MedicalConfig.COMPARTMENT_STAGE1_MIN_PAIN.get();
        int csStage2Pain = MedicalConfig.COMPARTMENT_STAGE2_MIN_PAIN.get();
        float rhabdoToxin = MedicalConfig.COMPARTMENT_RHABDO_TOXIN_PER_SEC.get().floatValue();
        float naturalAbsorb = MedicalConfig.COMPARTMENT_NATURAL_ABSORB_ML_PER_SEC.get().floatValue();

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
                int maxPainVal = MedicalConfig.MAX_PAIN_LEVEL.get();
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
        float burnMult = MedicalConfig.BURN_RATE_MULTIPLIER.get().floatValue();
        float fireRate = MedicalConfig.BURN_FIRE_AREA_GAIN_PER_SEC.get().floatValue();
        float lavaRate = MedicalConfig.BURN_LAVA_AREA_GAIN_PER_SEC.get().floatValue();
        int maxPainCfg = MedicalConfig.MAX_PAIN_LEVEL.get();
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

        // 8. 骨折夹板固定后的愈合倒计时推进（调用已有 LimbData#getFractureTimer / setFractureTimer / setFracture 接口）
        int defaultHealSec = MedicalConfig.SPLINT_FRACTURE_HEAL_TIME_SEC.get();
        for (MedicalData.Limb limb : MedicalData.Limb.values()) {
            MedicalData.LimbData ld = data.getLimb(limb);
            if (ld == null || ld.isAmputated() || isLimbNecrotic(ld)) continue;
            if (ld.hasFracture() && ld.hasSplint()) {
                int curTimer = ld.getFractureTimer();
                if (curTimer <= 0) {
                    ld.setFractureTimer(defaultHealSec);
                    dirty = true;
                } else if (!ld.isOpenFracture() || ld.getBleeding() <= 0) {
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
                        ld.setFractureTimer(nextTimer);
                    }
                    dirty = true;
                }
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
                int rampMax = MedicalConfig.NSAID_RAMP_UP_SEC.get();
                if (nsaidImpl.getNsaidRampTimer() < rampMax) {
                    nsaidImpl.setNsaidRampTimer(nsaidImpl.getNsaidRampTimer() + 1);
                }
                int intervalSec = Math.max(5, MedicalConfig.NSAID_LIMB_PAIN_RELIEF_INTERVAL_SEC.get());
                int maxRelieved = MedicalConfig.NSAID_MAX_LIMB_PAIN_REDUCTION.get();
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
                float txaDecay = MedicalConfig.TXA_DECAY_RATE_PER_SEC.get().floatValue();
                float nextTxa = Math.max(0.0f, txaConc - txaDecay);
                nsaidImpl.setTxaConcentration(nextTxa);

                int clotPerSec = MedicalConfig.TXA_CONTINUOUS_CLOT_RATE_PER_SEC.get();
                if (clotPerSec > 0 && nextTxa >= 5.0f) {
                    for (MedicalData.Limb l : MedicalData.Limb.values()) {
                        MedicalData.LimbData ld = data.getLimb(l);
                        if (ld != null && ld.getInternalBleedingRate() > 0) {
                            ld.setInternalBleedingRate(Math.max(0, ld.getInternalBleedingRate() - clotPerSec));
                        }
                    }
                }

                // 副作用：高浓度 (>=75%) 或淤血部位诱发微血栓 -> 降低血氧并加速止血带/骨筋膜室缺血坏死！
                int thrombThresh = MedicalConfig.TXA_THROMBOSIS_THRESHOLD.get();
                if (nextTxa >= thrombThresh) {
                    float o2Drop = MedicalConfig.TXA_THROMBOSIS_O2_PENALTY_PER_SEC.get().floatValue();
                    if (o2Drop > 0.0f) {
                        data.setBloodOxygenFloat(Math.max(0.0f, data.getBloodOxygenFloat() - o2Drop));
                    }
                }
                int ischExtra = Math.max(0, MedicalConfig.TXA_THROMBOSIS_ISCHEMIA_MULT.get() - 1);
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

        if (dirty) {
            player.syncData(MedicalDataCapability.MEDICAL_DATA);
        }
    }
}