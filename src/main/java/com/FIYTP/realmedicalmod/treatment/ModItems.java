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
            if (ld != null && !ld.isAmputated() && ld.isTourniqueted()) {
                return true;
            }
        }
        return false;
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
                if (isLimbNecrotic(ld)) {
                    setLimbNecrotic(ld, false);
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

        if (dirty) {
            player.syncData(MedicalDataCapability.MEDICAL_DATA);
        }
    }
}