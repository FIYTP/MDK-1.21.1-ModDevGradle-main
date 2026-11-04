package com.FIYTP.realmedicalmod.treatment;

import com.FIYTP.realmedicalmod.RealMedicalMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 模组医疗用品与专属创造模式物品栏注册器 (ModItems)
 *
 * 1. 注册「卷式绷带 (bandage)」与「加压绷带 (pressure_bandage)」。
 * 2. 注册「真实医疗系统」专属创造模式物品栏，并自动收录 ITEMS 注册表中的所有医疗用品（后续新增医疗用品无需重复手动添加）。
 * 3. 同时将医疗用品注入原版「工具与实用物品」和「战斗用品」标签页，方便随时取用。
 */
public class ModItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(RealMedicalMod.MODID);

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, RealMedicalMod.MODID);

    /** 卷式绷带：基础无菌包扎用品，默认可吸收 500mL/min 出血。 */
    public static final DeferredItem<Item> BANDAGE = ITEMS.register("bandage",
            () -> new BandageItem(new Item.Properties()
                    .stacksTo(16)
                    .component(
                            MedicalComponents.MEDICAL_POTENCY.get(),
                            MedicalPotency.of(MedicalItemTags.HEMOSTASIS.location(), 500.0f)
                    )));

    /** 战术加压绷带：高弹加压无菌敷料，可吸收 1000mL/min 出血。 */
    public static final DeferredItem<Item> PRESSURE_BANDAGE = ITEMS.register("pressure_bandage",
            () -> new BandageItem(new Item.Properties()
                    .stacksTo(16)
                    .component(
                            MedicalComponents.MEDICAL_POTENCY.get(),
                            MedicalPotency.of(MedicalItemTags.HEMOSTASIS.location(), 1000.0f)
                    )));

    /** 专属创造模式物品栏：「真实医疗系统」——自动收纳 ITEMS 下注册的所有医疗用品 */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MEDICAL_TAB =
            CREATIVE_MODE_TABS.register("medical_tab", () -> CreativeModeTab.builder()
                    .title(Component.literal("真实医疗系统"))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> BANDAGE.get().getDefaultInstance())
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
        modEventBus.addListener(ModItems::addCreative);
    }

    /**
     * 同时把所有医疗用品加入原版「工具与实用物品」与「战斗用品」物品栏
     */
    private static void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES
                || event.getTabKey() == CreativeModeTabs.COMBAT) {
            for (DeferredHolder<Item, ? extends Item> entry : ITEMS.getEntries()) {
                event.accept(entry.get());
            }
        }
    }
}