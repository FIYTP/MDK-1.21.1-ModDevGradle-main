package com.FIYTP.realmedicalmod.treatment;

import com.FIYTP.realmedicalmod.RealMedicalMod;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 模组自带的绷带物品。
 * 标签驱动：物品本体只注册，所有行为由 data/realmedicalmod/tags/item/*.json 决定
 * （本物品在 bandage.json / hemostasis.json / single_use.json / sterile.json 中）。
 */
public class ModItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(RealMedicalMod.MODID);

    /** 卷式绷带：基础包扎用品，可吸收 500mL 出血。 */
    public static final DeferredItem<Item> BANDAGE = ITEMS.registerItem("bandage",
            BandageItem::new, new Item.Properties().stacksTo(16));

    /** 无菌加压绷带：可吸收 1000mL 出血（标签同 bandage，potency 由组件提升）。 */
    public static final DeferredItem<Item> PRESSURE_BANDAGE = ITEMS.registerItem("pressure_bandage",
            BandageItem::new, new Item.Properties().stacksTo(16));

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        MedicalComponents.DATA_COMPONENT_TYPES.register(modEventBus);
    }
}
