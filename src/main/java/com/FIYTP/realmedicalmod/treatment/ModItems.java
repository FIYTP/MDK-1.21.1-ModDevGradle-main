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
 * 模组医疗用品、阿片针剂、肾上腺素注射器与专属状态 Buff 注册器 (ModItems)
 *
 * 状态效果贴图对应路径：
 * - {@code assets/realmedicalmod/textures/mob_effect/opioid_addiction.png} (18x18 阿片成瘾 I/II/III)
 * - {@code assets/realmedicalmod/textures/mob_effect/adrenaline_rush.png} (18x18 肾上腺素激增 I/II/III)
 */
@EventBusSubscriber(modid = RealMedicalMod.MODID)
public class ModItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(RealMedicalMod.MODID);

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, RealMedicalMod.MODID);

    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, RealMedicalMod.MODID);

    /** 阿片成瘾专属 MobEffect（对应贴图 textures/mob_effect/opioid_addiction.png） */
    public static final DeferredHolder<MobEffect, MobEffect> OPIOID_ADDICTION =
            MOB_EFFECTS.register("opioid_addiction", () -> new NamedMobEffect(MobEffectCategory.HARMFUL, 0xD500F9, "阿片成瘾"));

    /** 肾上腺素激增专属 MobEffect（对应贴图 textures/mob_effect/adrenaline_rush.png） */
    public static final DeferredHolder<MobEffect, MobEffect> ADRENALINE_RUSH =
            MOB_EFFECTS.register("adrenaline_rush", () -> new NamedMobEffect(MobEffectCategory.BENEFICIAL, 0xFFD600, "肾上腺素激增"));

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

    public static final DeferredItem<Item> ADRENALINE = ITEMS.register("adrenaline",
            () -> new BandageItem(new Item.Properties()
                    .durability(100)
                    .component(
                            MedicalComponents.MEDICAL_POTENCY.get(),
                            MedicalPotency.of(MedicalItemTags.STIMULANT.location(), 65.0f)
                    )));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MEDICAL_TAB =
            CREATIVE_MODE_TABS.register("medical_tab", () -> CreativeModeTab.builder()
                    .title(Component.literal("真实医疗系统"))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> ADRENALINE.get().getDefaultInstance())
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

    /**
     * 判断玩家当前是否处于 II/III 级阿片成瘾且未注射阿片（戒断期行动受限状态；若正处于肾上腺素激增期则临时压制戒断乏力）
     */
    public static boolean isInWithdrawalMovementRestriction(MedicalData data) {
        if (data == null) return false;
        int tier = getAddictionTier(data.getAddictionLevel());
        if (tier < 2) return false;
        if (getAdrenalineTier(getAdrenalineLevel(data)) >= 1) return false;
        return data.getOpioidConcentration() < MedicalConfig.ADDICTION_WITHDRAWAL_OPIOID_THRESHOLD.get();
    }

    /**
     * 玩家受伤时触发短暂（约 3~4 秒）的内源性「肾上腺素激增 I」应激 Buff（无文字刷屏，不叠加致室颤）
     */
    public static void triggerTraumaAdrenalineRush(ServerPlayer player, MedicalData data, float rawDamage) {
        if (player == null || !(data instanceof MedicalDataImpl impl)) return;
        if (!MedicalConfig.ADRENALINE_TRAUMA_ENABLED.get()) return;
        if (data.getConsciousness() <= 0 || data.isCardiacArrest()) return;

        float minDmg = Math.max(2.0f, MedicalConfig.ADRENALINE_TRAUMA_MIN_DAMAGE.get().floatValue());
        if (rawDamage < minDmg) return;

        // 生理不应期冷却中或已处于外源注射状态时不重复触发
        if (impl.getAdrenalineCooldownSec() > 0 || impl.isAdrenalineFromInjector()) return;

        // 内源性应激固定设为短暂 I 级浓度 (24%)，约 3~4 秒自然消退
        float nextAdr = Math.max(impl.getAdrenalineLevelFloat(), 24.0f);
        impl.setAdrenalineLevelFloat(nextAdr);
        impl.setAdrenalineCooldownSec(Math.max(15, MedicalConfig.ADRENALINE_TRAUMA_COOLDOWN_SEC.get()));
        impl.setAdrenalineCrashSec(0);

        int durSec = Math.min(4, Math.max(2, MedicalConfig.ADRENALINE_TRAUMA_DURATION_SEC.get()));
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

    /**
     * 立即同步玩家身上的「肾上腺素激增」Buff 状态栏图标与等级
     */
    public static void syncAdrenalineEffectNow(ServerPlayer player, MedicalData data) {
        if (player == null || data == null) return;
        float adr = getAdrenalineLevel(data);
        int adrTier = getAdrenalineTier(adr);
        if (adrTier > 0) {
            int desiredAmp = adrTier - 1;
            boolean fromInj = (data instanceof MedicalDataImpl impl) && impl.isAdrenalineFromInjector();
            float tier1Floor = MedicalConfig.ADRENALINE_TIER1_THRESHOLD.get().floatValue();
            float decayRate = fromInj ? 4.5f : 6.0f;
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

        // 0. 每秒固定衰减肾上腺素浓度与冷却（内源应激约 3~4 秒消退，单支注射器约 12 秒消退）
        if (data instanceof MedicalDataImpl adrImpl) {
            boolean dirty = false;
            if (adrImpl.getAdrenalineCooldownSec() > 0) {
                adrImpl.setAdrenalineCooldownSec(adrImpl.getAdrenalineCooldownSec() - 1);
                dirty = true;
            }
            if (adrImpl.getAdrenalineCrashSec() > 0 && adrImpl.getAdrenalineLevelFloat() <= 0.0f) {
                adrImpl.setAdrenalineCrashSec(adrImpl.getAdrenalineCrashSec() - 1);
                dirty = true;
            }
            float curAdr = adrImpl.getAdrenalineLevelFloat();
            if (curAdr > 0.0f) {
                boolean fromInj = adrImpl.isAdrenalineFromInjector();
                float adrDecay = fromInj
                        ? Math.max(4.0f, MedicalConfig.ADRENALINE_DECAY_RATE_PER_SEC.get().floatValue())
                        : 6.5f;
                float nextAdr = Math.max(0.0f, curAdr - adrDecay);
                // 当内源性应激低于 I 级阈值时直接归零，确保不会拖尾
                if (!fromInj && nextAdr < MedicalConfig.ADRENALINE_TIER1_THRESHOLD.get()) {
                    nextAdr = 0.0f;
                }
                adrImpl.setAdrenalineLevelFloat(nextAdr);
                if (nextAdr <= 0.0f && fromInj) {
                    adrImpl.setAdrenalineFromInjector(false);
                    adrImpl.setAdrenalineCrashSec(Math.min(5, MedicalConfig.ADRENALINE_CRASH_DURATION_SEC.get()));
                }
                dirty = true;
            }
            if (dirty) {
                player.syncData(MedicalDataCapability.MEDICAL_DATA);
            }
        }

        // 1. 同步「阿片成瘾 I / II / III」Buff
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

        // 2. 同步「肾上腺素激增 I / II / III」Buff
        syncAdrenalineEffectNow(player, data);

        // 3. 肾上腺素血压飙升副作用：若存在未包扎/未绑止血带的开放性外出血，按比例加速失血
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
                }
            }
        }
    }
}