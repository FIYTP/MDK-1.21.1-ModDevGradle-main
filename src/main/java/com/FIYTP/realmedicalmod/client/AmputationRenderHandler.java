package com.FIYTP.realmedicalmod.client;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.capability.MedicalDataCapability;
import com.FIYTP.realmedicalmod.config.MedicalConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

/**
 * 断肢渲染：隐藏 3D 玩家模型中已截肢的肢体。
 * - 本体模型 + 皮肤层（袖子/裤腿）在 RenderPlayerEvent.Pre 中隐藏
 * - 第一人称断臂手通过 RenderHandEvent 取消
 * - 盔甲层由 HumanoidArmorLayerMixin 处理
 */
@EventBusSubscriber(modid = "realmedicalmod", value = Dist.CLIENT)
public final class AmputationRenderHandler {

    private AmputationRenderHandler() {
    }

    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        if (!MedicalConfig.AMPUTATION_ENABLED.get()) return;

        Player player = event.getEntity();
        MedicalData data = player.getData(MedicalDataCapability.MEDICAL_DATA);

        boolean rightArm = data.getLimb(MedicalData.Limb.RIGHT_ARM).isAmputated();
        boolean leftArm = data.getLimb(MedicalData.Limb.LEFT_ARM).isAmputated();
        boolean rightLeg = data.getLimb(MedicalData.Limb.RIGHT_LEG).isAmputated();
        boolean leftLeg = data.getLimb(MedicalData.Limb.LEFT_LEG).isAmputated();
        if (!rightArm && !leftArm && !rightLeg && !leftLeg) return;

        AmputationClientState.setAmputation(player, rightArm, leftArm, rightLeg, leftLeg);

        PlayerModel<?> model = event.getRenderer().getModel();

        // 本体
        if (rightArm) model.rightArm.visible = false;
        if (leftArm) model.leftArm.visible = false;
        if (rightLeg) model.rightLeg.visible = false;
        if (leftLeg) model.leftLeg.visible = false;

        // 皮肤层（第二层）：袖子/裤腿跟随本体
        if (rightArm) model.rightSleeve.visible = false;
        if (leftArm) model.leftSleeve.visible = false;
        if (rightLeg) model.rightPants.visible = false;
        if (leftLeg) model.leftPants.visible = false;
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (!MedicalConfig.AMPUTATION_ENABLED.get()) return;

        // 第一人称：RenderHandEvent 仅对本地玩家触发
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        MedicalData.Limb limb = event.getHand() == InteractionHand.MAIN_HAND
                ? MedicalData.Limb.RIGHT_ARM
                : MedicalData.Limb.LEFT_ARM;

        if (player.getData(MedicalDataCapability.MEDICAL_DATA).getLimb(limb).isAmputated()) {
            event.setCanceled(true);
        }
    }
}
