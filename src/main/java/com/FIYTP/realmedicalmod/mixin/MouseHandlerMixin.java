package com.FIYTP.realmedicalmod.mixin;

import com.FIYTP.realmedicalmod.client.BrainDamageClientState;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MouseHandler.class, priority = 1000)
public abstract class MouseHandlerMixin {

    @Shadow
    private double accumulatedDX;

    @Shadow
    private double accumulatedDY;

    @Inject(
            method = "turnPlayer",
            at = @At("HEAD"),
            require = 0
    )
    private void realmedicalmod$onTurnPlayer(double movementTime, CallbackInfo ci) {
        if (!BrainDamageClientState.isViewInverted()) {
            return;
        }

        // 视角反向：反转累计移动量
        this.accumulatedDX = -this.accumulatedDX;
        this.accumulatedDY = -this.accumulatedDY;
    }
}
