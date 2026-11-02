package com.FIYTP.realmedicalmod.treatment;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 绷带物品：右键打开包扎小游戏界面（客户端）。
 * 服务端逻辑见 TreatmentRouter；物品本体无状态。
 */
public class BandageItem extends Item {

    public BandageItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            TreatmentClientHooks.openBandageScreen(hand);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
