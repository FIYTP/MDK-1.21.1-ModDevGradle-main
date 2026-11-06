package com.FIYTP.realmedicalmod.mixin;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.capability.MedicalDataCapability;
import com.FIYTP.realmedicalmod.client.BrainDamageClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 客户端键盘移动与跳跃输入 Mixin
 *
 * 1. 当玩家意识归零 (consciousness <= 0) 或心脏骤停 (isCardiacArrest) 时：
 *    彻底清零前后左右移动冲量 (forwardImpulse / leftImpulse)、方向按键状态以及跳跃 (jumping) 和潜行 (shiftKeyDown)，
 *    从输入源头杜绝昏迷状态下玩家仍能走动或起跳的 Bug。
 * 2. 当处于脑损伤方向反转状态时，互换方向按键与移动冲量。
 */
@Mixin(value = KeyboardInput.class, priority = 1000)
public abstract class KeyboardInputMixin extends Input {

    @Inject(
            method = "tick",
            at = @At("RETURN"),
            require = 0
    )
    private void realmedicalmod$onTick(boolean slowDown, float sneakSlowDownMultiplier, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            MedicalData data = mc.player.getData(MedicalDataCapability.MEDICAL_DATA);
            if (data != null && (data.getConsciousness() <= 0 || data.isCardiacArrest())) {
                this.up = false;
                this.down = false;
                this.left = false;
                this.right = false;
                this.jumping = false;
                this.shiftKeyDown = false;
                this.forwardImpulse = 0.0f;
                this.leftImpulse = 0.0f;
                return;
            }
        }

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