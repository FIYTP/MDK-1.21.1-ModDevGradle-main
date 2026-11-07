package com.FIYTP.realmedicalmod.treatment;

import com.FIYTP.realmedicalmod.RealMedicalMod;
import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.capability.MedicalDataCapability;
import com.FIYTP.realmedicalmod.config.MedicalConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
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
 * 模组医疗用品、阿片针剂与「阿片成瘾 (I / II / III)」Buff 注册器 (ModItems)
 *
 * 贴图与名称说明：
 * - 图标贴图由 {@code assets/realmedicalmod/textures/mob_effect/opioid_addiction.png} (18x18 标准状态图标) 提供；
 * - {@link OpioidAddictionEffect#getDisplayName()} 直接返回 {@code "阿片成瘾"}，确保在任何语言环境下均正确显示中文名称。
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
            MOB_EFFECTS.register("opioid_addiction", () -> new OpioidAddictionEffect(MobEffectCategory.HARMFUL, 0xD500F9));

    public static class OpioidAddictionEffect extends MobEffect {

        public OpioidAddictionEffect(MobEffectCategory category, int color) {
            super(category, color);
        }

        @Override
        public Component getDisplayName() {
            return Component.literal("阿片成瘾");
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

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MEDICAL_TAB =
            CREATIVE_MODE_TABS.register("medical_tab", () -> CreativeModeTab.builder()
                    .title(Component.literal("真实医疗系统"))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> MORPHINE.get().getDefaultInstance())
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

    /**
     * 判断玩家当前是否处于 II/III 级阿片成瘾且未注射阿片（戒断期行动受限状态）
     */
    public static boolean isInWithdrawalMovementRestriction(MedicalData data) {
        if (data == null) return false;
        int tier = getAddictionTier(data.getAddictionLevel());
        if (tier < 2) return false;
        return data.getOpioidConcentration() < MedicalConfig.ADDICTION_WITHDRAWAL_OPIOID_THRESHOLD.get();
    }

    @SubscribeEvent
    public static void onPlayerTickSyncAddictionBuff(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.tickCount % 20 != 0) return;

        MedicalData data = player.getData(MedicalDataCapability.MEDICAL_DATA);
        if (data == null) return;

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
    }
}