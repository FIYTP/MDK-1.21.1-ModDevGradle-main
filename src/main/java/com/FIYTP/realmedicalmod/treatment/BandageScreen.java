package com.FIYTP.realmedicalmod.treatment;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;

/**
 * 绷带包扎小游戏界面 —— 占位版本
 * TODO: 实现绕圈缠绕小游戏
 */
public class BandageScreen extends Screen {

    private final InteractionHand hand;

    public BandageScreen(InteractionHand hand) {
        super(Component.translatable("medical.gui.bandage.title"));
        this.hand = hand;
    }

    public InteractionHand getHand() {
        return hand;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 占位：先画个提示文字，避免打开后是纯黑屏
        g.drawCenteredString(this.font,
                Component.literal("BANDAGE MINIGAME — 待实现"),
                this.width / 2, this.height / 2,
                0xFF00E5FF);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}