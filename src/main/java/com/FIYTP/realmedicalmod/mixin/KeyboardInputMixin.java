package com.FIYTP.realmedicalmod.mixin;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.capability.MedicalDataCapability;
import com.FIYTP.realmedicalmod.client.BrainDamageClientState;
import com.FIYTP.realmedicalmod.config.MedicalConfig;
import com.FIYTP.realmedicalmod.treatment.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 客户端键盘移动与跳跃输入 Mixin（含昏迷锁死 + II/III 级阿片成瘾戒断行动受限）
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
            if (data != null) {
                if (data.getConsciousness() <= 0 || data.isCardiacArrest()) {
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

                // II 级与 III 级阿片成瘾未注射阿片时：客户端同步压制移动冲量、打断疾跑（III 级禁止跳跃）
                if (ModItems.isInWithdrawalMovementRestriction(data)) {
                    int tier = ModItems.getAddictionTier(data.getAddictionLevel());
                    mc.player.setSprinting(false);
                    float mult = (tier >= 3)
                            ? MedicalConfig.ADDICTION_WITHDRAWAL_SPEED_MULT_T3.get().floatValue()
                            : MedicalConfig.ADDICTION_WITHDRAWAL_SPEED_MULT_T2.get().floatValue();
                    this.forwardImpulse *= mult;
                    this.leftImpulse *= mult;
                    if (tier >= 3) {
                        this.jumping = false;
                    }
                }
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