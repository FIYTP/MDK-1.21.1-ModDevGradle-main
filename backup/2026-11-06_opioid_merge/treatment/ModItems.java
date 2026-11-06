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
 * 耐久设计说明：
 * - 卷式绷带 (bandage)：最大耐久 500（对应 500 mL 总止血容量），按实际吸收的出血量扣除对应耐久。
 * - 加压绷带 (pressure_bandage)：最大耐久 1000（对应 1000 mL 总止血容量），按实际吸收的出血量扣除对应耐久。
 */
public class ModItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(RealMedicalMod.MODID);

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, RealMedicalMod.MODID);

    /** 卷式绷带：基础无菌包扎用品，总止血耐久 500 mL（按实际止血量扣耐久，未用完不会消失）。 */
    public static final DeferredItem<Item> BANDAGE = ITEMS.register("bandage",
            () -> new BandageItem(new Item.Properties()
                    .durability(500)
                    .component(
                            MedicalComponents.MEDICAL_POTENCY.get(),
                            MedicalPotency.of(MedicalItemTags.HEMOSTASIS.location(), 500.0f)
                    )));

    /** 战术加压绷带：高弹加压无菌敷料，总止血耐久 1000 mL（按实际止血量扣耐久，未用完不会消失）。 */
    public static final DeferredItem<Item> PRESSURE_BANDAGE = ITEMS.register("pressure_bandage",
            () -> new BandageItem(new Item.Properties()
                    .durability(1000)
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