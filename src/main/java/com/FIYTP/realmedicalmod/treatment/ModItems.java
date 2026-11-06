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
 * 模组医疗用品与阿片类镇痛针剂注册器 (ModItems)
 *
 * 包含：
 * 1. 卷式绷带 (bandage)：500 mL 止血容量耐久。
 * 2. 战术加压绷带 (pressure_bandage)：1000 mL 止血容量耐久。
 * 3. 盐酸吗啡自动注射器 (morphine)：100 刻度耐久（对应 10.0 mg 标准剂量），支持通过剂量滑块分次或单次推注。
 * 4. 枸橼酸芬太尼微量注射器 (fentanyl)：100 刻度耐久（对应 100 mcg 高纯微克剂量），镇痛极强但治疗窗极窄，过量推注将导致重度延髓呼吸抑制与呼吸骤停。
 */
public class ModItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(RealMedicalMod.MODID);

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, RealMedicalMod.MODID);

    /** 卷式绷带：基础无菌包扎用品，总止血耐久 500 mL。 */
    public static final DeferredItem<Item> BANDAGE = ITEMS.register("bandage",
            () -> new BandageItem(new Item.Properties()
                    .durability(500)
                    .component(
                            MedicalComponents.MEDICAL_POTENCY.get(),
                            MedicalPotency.of(MedicalItemTags.HEMOSTASIS.location(), 500.0f)
                    )));

    /** 战术加压绷带：高弹加压无菌敷料，总止血耐久 1000 mL。 */
    public static final DeferredItem<Item> PRESSURE_BANDAGE = ITEMS.register("pressure_bandage",
            () -> new BandageItem(new Item.Properties()
                    .durability(1000)
                    .component(
                            MedicalComponents.MEDICAL_POTENCY.get(),
                            MedicalPotency.of(MedicalItemTags.HEMOSTASIS.location(), 1000.0f)
                    )));

    /** 盐酸吗啡自动注射器 (10.0 mg / 100 单位刻度)：经典强效阿片镇痛剂，使用时呼出剂量滑块选择注入量。 */
    public static final DeferredItem<Item> MORPHINE = ITEMS.register("morphine",
            () -> new BandageItem(new Item.Properties()
                    .durability(100)
                    .component(
                            MedicalComponents.MEDICAL_POTENCY.get(),
                            MedicalPotency.of(MedicalItemTags.OPIOID.location(), 8.0f)
                    )));

    /** 枸橼酸芬太尼微量注射器 (100 mcg / 100 单位刻度)：超强效合成阿片镇痛剂，微量即可强力镇痛，过量极易引发呼吸暂停。 */
    public static final DeferredItem<Item> FENTANYL = ITEMS.register("fentanyl",
            () -> new BandageItem(new Item.Properties()
                    .durability(100)
                    .component(
                            MedicalComponents.MEDICAL_POTENCY.get(),
                            MedicalPotency.of(MedicalItemTags.OPIOID.location(), 10.0f)
                    )));

    /** 专属创造模式物品栏：「真实医疗系统」 */
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
}