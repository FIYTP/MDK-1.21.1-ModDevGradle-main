package com.FIYTP.realmedicalmod.treatment;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;

/**
 * 客户端钩子（仅 client-side 调用）。与 BandageScreen 解耦，避免服务端类加载客户端类。
 */
public class TreatmentClientHooks {

    public static void openBandageScreen(InteractionHand hand) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.screen == null) {
            mc.setScreen(new BandageScreen(hand));
        }
    }
}
