package com.FIYTP.realmedicalmod.treatment;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 绷带与通用医疗用品基类 (BandageItem)
 *
 * 交互方式：
 * - 【长按鼠标左键】：打开全息人体肢体面板，锁定视角旋转，将鼠标移至目标肢体即可对该肢体包扎施救。
 * - 物品本体完全无状态，具体治疗效果由三轴标签 (MedicalItemTags) 与效力组件 (MedicalPotency) 驱动。
 */
public class BandageItem extends Item {

    public BandageItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            player.displayClientMessage(
                    Component.literal("请【长按鼠标左键】打开人体面板并移至对应肢体进行包扎")
                            .withStyle(ChatFormatting.AQUA),
                    true
            );
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        for (String line : TreatmentRouter.getItemEffectSummary(stack)) {
            tooltipComponents.add(Component.literal("• " + line).withStyle(ChatFormatting.GRAY));
        }
        tooltipComponents.add(Component.literal("[长按左键] 展开人体面板选定肢体施救").withStyle(ChatFormatting.AQUA));
    }
}