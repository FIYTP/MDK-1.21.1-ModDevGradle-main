package com.FIYTP.realmedicalmod.mixin;

import com.FIYTP.realmedicalmod.client.BrainDamageClientState;
import com.FIYTP.realmedicalmod.treatment.TreatmentClientHooks;
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
            cancellable = true,
            require = 0
    )
    private void realmedicalmod$onTurnPlayer(double movementTime, CallbackInfo ci) {
        // 1. 当玩家按住左键打开人体医疗用品使用面板时：
        //    将鼠标移动量转发给全息面板游标，并清零相机转动增量、取消原版视角移动！
        if (TreatmentClientHooks.isLockingCameraView()) {
            TreatmentClientHooks.onMouseDelta(this.accumulatedDX, this.accumulatedDY);
            this.accumulatedDX = 0.0;
            this.accumulatedDY = 0.0;
            ci.cancel();
            return;
        }


        // 2. 脑损伤视角反向效果
        if (BrainDamageClientState.isViewInverted()) {
            this.accumulatedDX = -this.accumulatedDX;
            this.accumulatedDY = -this.accumulatedDY;
        }
    }
}