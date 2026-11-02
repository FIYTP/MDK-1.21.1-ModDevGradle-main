package com.FIYTP.realmedicalmod.treatment;

import com.FIYTP.realmedicalmod.RealMedicalMod;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 数据组件注册器：potency（效力数值表）。
 * 效果标签只表达"有没有"，数值靠 medical_potency 组件携带：
 * 效果标签 id → 数值（如 hemostasis→500 表示可吸收500mL出血）。
 * 组件缺失 → 用 MedicalConfig 默认值。
 */
public class MedicalComponents {

    public static final DeferredRegister.DataComponents DATA_COMPONENT_TYPES =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, RealMedicalMod.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<MedicalPotency>> MEDICAL_POTENCY =
            DATA_COMPONENT_TYPES.registerComponentType(
                    "medical_potency",
                    builder -> builder
                            .persistent(MedicalPotency.CODEC)
                            .networkSynchronized(MedicalPotency.STREAM_CODEC)
            );
}
