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
 * 绷带与通用医疗用品基类 (BandageItem)
 *
 * 交互与耐久设计：
 * - 【长按鼠标左键】：打开全息人体肢体面板，锁定视角旋转，将鼠标移至目标肢体即可对该肢体包扎施救。
 * - 【按量扣除耐久】：卷式绷带拥有 500 mL 止血耐久，加压绷带拥有 1000 mL 止血耐久；
 *   每次包扎仅扣除实际吸收的出血量，剩余耐久可继续用于包扎其他部位，并在物品栏常驻显示耐久条。
 */
public class BandageItem extends Item {

    public BandageItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        // 只要绷带消耗过耐久，或处于手持状态，始终清晰显示剩余止血容量条
        return stack.isDamageableItem() && stack.getDamageValue() > 0;
    }

    @Override
    public int getBarColor(ItemStack stack) {
        if (!stack.isDamageableItem() || stack.getMaxDamage() <= 0) {
            return super.getBarColor(stack);
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
            player.displayClientMessage(
                    Component.literal("长按左键选择部位包扎")
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
        tooltipComponents.add(Component.literal("[长按左键] 选择部位施救").withStyle(ChatFormatting.AQUA));
    }
}