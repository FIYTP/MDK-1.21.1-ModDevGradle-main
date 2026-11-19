package com.FIYTP.realmedicalmod.mixin;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.capability.MedicalDataCapability;
import com.FIYTP.realmedicalmod.client.WristbandHologramController;
import com.FIYTP.realmedicalmod.client.render.WristbandArmRenderLayer;
import com.FIYTP.realmedicalmod.config.MedicalConfig;
import com.FIYTP.realmedicalmod.treatment.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 第一人称左手全场景手表统一注入 Mixin (PlayerRendererMixin)
 *
 * 直接注入原版底层渲染方法 PlayerRenderer.renderHand(...) 尾部，并在 arm == model.leftArm 时挂载 3D 战术手表：
 * - 兼容原版第一人称左手
 * - 兼容医疗物品动画左手 (TreatmentClientHooks)
 * - 兼容 TaCZ 1.21.1 RenderHelper.renderFirstPersonArm 调用的护木/换弹/检视左手骨骼
 * - 兼容 H 键抬腕左手（并回调 markLeftHandWatchRendered 防止重复渲染，永远只有两只手！）
 */
@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin {

    @Inject(
            method = "renderHand",
            at = @At("TAIL"),
            require = 0
    )
    private void realmedicalmod$attachWatchOnLeftHand(PoseStack poseStack,
                                                      MultiBufferSource bufferSource,
                                                      int packedLight,
                                                      AbstractClientPlayer player,
                                                      ModelPart arm,
                                                      ModelPart sleeve,
                                                      CallbackInfo ci) {
        if (player == null || player.isInvisible() || player.isSpectator()) return;
        if (!ModItems.safeCfgBool(MedicalConfig.WRISTBAND_ENABLED, true)) return;

        PlayerRenderer self = (PlayerRenderer) (Object) this;
        PlayerModel<AbstractClientPlayer> model = self.getModel();
        if (arm != model.leftArm) {
            return;
        }

        MedicalData data = player.getData(MedicalDataCapability.MEDICAL_DATA);
        float activeLevel = WristbandHologramController.getVisualActiveLevel();

        boolean gunInnerMode = WristbandHologramController.isHoldingGunOrTwoHanded(player);
        WristbandArmRenderLayer.applyExactLeftWristTransformAndRender(
                poseStack,
                bufferSource,
                packedLight,
                model,
                player,
                data,
                activeLevel,
                gunInnerMode
        );
        WristbandHologramController.markLeftHandWatchRendered();
    }
}