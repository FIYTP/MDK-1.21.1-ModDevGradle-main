package com.FIYTP.realmedicalmod.client;

import net.minecraft.world.entity.LivingEntity;

/**
 * 盔甲层渲染上下文：HumanoidArmorLayer.setPartVisibility 本身没有实体引用，
 * 在 render HEAD 处存入当前渲染实体，setPartVisibility RETURN 处读取，render RETURN 清理。
 * 渲染全程在渲染线程，ThreadLocal 即可。
 */
public final class AmputationArmorRenderContext {

    private static final ThreadLocal<LivingEntity> CURRENT = new ThreadLocal<>();

    private AmputationArmorRenderContext() {
    }

    public static void set(LivingEntity entity) {
        CURRENT.set(entity);
    }

    public static LivingEntity getEntity() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
