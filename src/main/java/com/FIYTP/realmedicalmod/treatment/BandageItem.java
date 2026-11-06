package com.FIYTP.realmedicalmod.treatment;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 通用医疗用品与阿片类镇痛注射器物品类 (BandageItem)
 *
 * 交互方式：
 * 1. 普通医疗用品（卷式绷带、加压绷带等）：
 *    【长按鼠标左键】呼出人体部位面板，移至目标肢体完成包扎，按实际吸收出血量扣除耐久。
 * 2. 阿片类强效镇痛针剂（盐酸吗啡自动注射器 morphine、枸橼酸芬太尼注射器 fentanyl）：
 *    - 【点击鼠标右键】或【长按鼠标左键选定肢体】均会打开「阿片类药物剂量推注滑块界面 (Dose Selection Slider)」，
 *      由玩家自由拖动滑块精确设定注入剂量（1% ~ 100%），并实时预览镇痛效果与延髓呼吸抑制/过量风险。
 */
public class BandageItem extends Item {

    public BandageItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return stack.isDamageableItem() && stack.getDamageValue() > 0;
    }

    @Override
    public int getBarColor(ItemStack stack) {
        if (!stack.isDamageableItem() || stack.getMaxDamage() <= 0) {
            return super.getBarColor(stack);
        }
        if (TreatmentRouter.isFentanylItem(stack)) {
            return 0xFF00E5FF; // 芬太尼冰青色药液进度条
        }
        if (TreatmentRouter.isOpioidItem(stack)) {
            return 0xFFFFB300; // 吗啡琥珀金药液进度条
        }
        float ratio = Mth.clamp(
                (float) (stack.getMaxDamage() - stack.getDamageValue()) / (float) stack.getMaxDamage(),
                0.0f,
                1.0f
        );
        return Mth.hsvToRgb(ratio / 3.0f, 1.0f, 1.0f);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            if (TreatmentRouter.isOpioidItem(stack)) {
                // 手持吗啡或芬太尼右键：直接呼出全息剂量推注滑块面板！
                TreatmentClientHooks.openOpioidDoseScreen(hand, null);
                return InteractionResultHolder.success(stack);
            } else {
                player.displayClientMessage(
                        Component.literal("长按左键选择部位包扎")
                                .withStyle(ChatFormatting.AQUA),
                        true
                );
            }
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        for (String line : TreatmentRouter.getItemEffectSummary(stack)) {
            ChatFormatting color = line.contains("过量") || line.contains("呼吸抑制")
                    ? ChatFormatting.RED
                    : ChatFormatting.GRAY;
            tooltipComponents.add(Component.literal("• " + line).withStyle(color));
        }
        if (TreatmentRouter.isOpioidItem(stack)) {
            tooltipComponents.add(Component.literal("[右键 / 长按左键] 调节滑块设定推注剂量").withStyle(ChatFormatting.GOLD));
        } else {
            tooltipComponents.add(Component.literal("[长按左键] 选择部位施救").withStyle(ChatFormatting.AQUA));
        }
    }
}