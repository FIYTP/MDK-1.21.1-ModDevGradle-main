package com.FIYTP.realmedicalmod.mixin;

import com.FIYTP.realmedicalmod.client.BrainDamageClientState;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = KeyboardInput.class, priority = 1000)
public abstract class KeyboardInputMixin extends Input {

    @Inject(
            method = "tick",
            at = @At("RETURN"),
            require = 0
    )
    private void realmedicalmod$onTick(boolean slowDown, float sneakSlowDownMultiplier, CallbackInfo ci) {
        if (!BrainDamageClientState.isInputInverted()) {
            return;
        }

        // 互换前后
        boolean tempUp = this.up;
        this.up = this.down;
        this.down = tempUp;

        // 互换左右
        boolean tempLeft = this.left;
        this.left = this.right;
        this.right = tempLeft;

        // 冲量也反向
        this.forwardImpulse = -this.forwardImpulse;
        this.leftImpulse = -this.leftImpulse;
    }
}
