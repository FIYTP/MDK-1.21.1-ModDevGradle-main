package com.FIYTP.realmedicalmod.client.render;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.capability.MedicalDataCapability;
import com.FIYTP.realmedicalmod.client.WristbandHologramController;
import com.FIYTP.realmedicalmod.config.MedicalConfig;
import com.FIYTP.realmedicalmod.treatment.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.resources.PlayerSkin;

/**
 * 玩家左臂骨骼 3D 战术医疗手表附加渲染层 (WristbandArmRenderLayer)
 *
 * 与第一人称共享完全一致的 PlayerModel.leftArm 骨骼局部坐标系：
 * - WIDE 宽臂：X 范围 [-1/16, +3/16] (中心 +0.0625f，半宽 0.125f)，Z 半厚 0.125f
 * - SLIM 细臂：X 范围 [-1/16, +2/16] (中心 +0.03125f，半宽 0.09375f)，Z 半厚 0.125f
 * - 手腕尺骨茎突佩戴高度：Y = +7.5/16 = +0.46875f（距手掌末端 2.5px，严丝合缝贴合皮肤）
 */
public final class WristbandArmRenderLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    public WristbandArmRenderLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> renderer) {
        super(renderer);
    }

    /**
     * 供第三人称 RenderLayer 与第一人称 RenderHandEvent 共同调用的统一左腕锚定变换，
     * 保证第一人称与第三人称手表 100% 严丝合缝佩戴在左手腕上，绝不悬浮偏位。
     */
    public static void applyExactLeftWristTransformAndRender(PoseStack poseStack,
                                                             MultiBufferSource bufferSource,
                                                             int packedLight,
                                                             PlayerModel<AbstractClientPlayer> playerModel,
                                                             AbstractClientPlayer player,
                                                             MedicalData data,
                                                             float activeLevel) {
        applyExactLeftWristTransformAndRender(poseStack, bufferSource, packedLight, playerModel, player, data, activeLevel, false);
    }

    /**
     * @param gunInnerWristMode 为 true 时（第一人称持枪态），采用特种兵 CQB 腕内侧戴法，
     *                          将表壳与活体表盘直接转向朝向射手双眼的护木内侧角度！
     */
    public static void applyExactLeftWristTransformAndRender(PoseStack poseStack,
                                                             MultiBufferSource bufferSource,
                                                             int packedLight,
                                                             PlayerModel<AbstractClientPlayer> playerModel,
                                                             AbstractClientPlayer player,
                                                             MedicalData data,
                                                             float activeLevel,
                                                             boolean gunInnerWristMode) {
        if (data != null && MedicalConfig.AMPUTATION_ENABLED.get()
                && data.getLimb(MedicalData.Limb.LEFT_ARM) != null
                && data.getLimb(MedicalData.Limb.LEFT_ARM).isAmputated()) {
            return;
        }
        boolean isSlim = player.getSkin().model() == PlayerSkin.Model.SLIM;

        poseStack.pushPose();
        // 1. 直接复用 leftArm 骨骼节点的精确枢轴偏移与旋转矩阵 (+5/16, +2/16, 0)
        playerModel.leftArm.translateAndRotate(poseStack);

        // 2. 平移到左臂几何中心与手腕佩戴位 (Y = +7.5/16 = 0.46875f)
        float armCenterX = isSlim ? 0.03125f : 0.0625f;
        float wristY = 0.46875f;
        poseStack.translate(armCenterX, wristY, 0.0f);

        // 3. 旋转对齐手表局部坐标系：
        //    - 常规/抬腕态：YP(90°)，表盘朝向腕背外侧 (+X)，抬腕横臂时正面迎向双眼
        //    - 持枪 CQB 态：绕手臂轴线转至腕内侧 (-X/-Z 方向，即 YP(-90°) + 微倾)，让护木左手上的表盘直接正面朝向玩家！
        poseStack.mulPose(Axis.XP.rotationDegrees(180.0f));
        if (gunInnerWristMode) {
            float gunYaw = (float) ModItems.safeCfgDouble(MedicalConfig.WRISTBAND_GUN_INNER_YAW_DEG, -112.0);
            poseStack.mulPose(Axis.YP.rotationDegrees(gunYaw));
        } else {
            poseStack.mulPose(Axis.YP.rotationDegrees(90.0f));
        }

        // 严格匹配当前旋转角下的手臂 X/Z 截面半宽
        float strapHalfX = 0.126f;
        float strapHalfZ = isSlim ? 0.095f : 0.126f;

        Minecraft mcLocal = Minecraft.getInstance();
        boolean isLocalPlayer = mcLocal.player != null && mcLocal.player.getUUID().equals(player.getUUID());
        TacticalWatchModelRenderer.renderWatchOnWrist(
                poseStack,
                bufferSource,
                packedLight,
                data,
                strapHalfX,
                strapHalfZ,
                activeLevel,
                isLocalPlayer
        );

        poseStack.popPose();
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                       AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (player == null || player.isInvisible() || player.isSpectator()) return;
        if (!ModItems.safeCfgBool(MedicalConfig.WRISTBAND_ENABLED, true)) return;

        MedicalData data = player.getData(MedicalDataCapability.MEDICAL_DATA);
        float activeLevel = 0.0f;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getUUID().equals(player.getUUID())) {
            activeLevel = WristbandHologramController.getRaiseAmount();
        }

        applyExactLeftWristTransformAndRender(
                poseStack,
                bufferSource,
                packedLight,
                this.getParentModel(),
                player,
                data,
                activeLevel
        );
    }
}