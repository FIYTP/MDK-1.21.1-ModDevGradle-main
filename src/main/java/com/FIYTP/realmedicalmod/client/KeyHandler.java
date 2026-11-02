package com.FIYTP.realmedicalmod.client;

import com.FIYTP.realmedicalmod.client.gui.MedicalScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = "realmedicalmod", value = Dist.CLIENT)
public class KeyHandler {

    public static final String KEY_CATEGORY = "key.category.realmedicalmod";
    public static final String KEY_OPEN_MEDICAL = "key.realmedicalmod.open_medical";

    public static final KeyMapping OPEN_MEDICAL_KEY = new KeyMapping(
            KEY_OPEN_MEDICAL,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            KEY_CATEGORY
    );

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_MEDICAL_KEY);
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (OPEN_MEDICAL_KEY.consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                if (mc.screen instanceof MedicalScreen) {
                    mc.player.closeContainer();
                } else {
                    mc.setScreen(new MedicalScreen());
                }
            }
        }
    }
}