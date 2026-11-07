package com.FIYTP.realmedicalmod.treatment;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.capability.MedicalDataCapability;
import com.FIYTP.realmedicalmod.config.MedicalConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

import java.util.List;

/**
 * 医疗用品与阿片类镇痛注射器物品类 (BandageItem)
 */
public class BandageItem extends Item {

    private static final String[] TIER1_TEMPTATION_NAMES = new String[] {
            "「再打一点」",
            "「止痛神水」",
            "「一针解脱」",
            "「温暖平静」"
    };

    private static final String[] TIER2_TEMPTATION_NAMES = new String[] {
            "「再来一管」",
            "「推下去吧」",
            "「液态极乐」",
            "「别忍着痛」"
    };

    private static final String[] TIER3_TEMPTATION_NAMES = new String[] {
            "「全部推光！」",
            "「推到底！」",
            "「快给我！」",
            "「不计代价！」"
    };

    public BandageItem(Properties properties) {
        super(properties);
    }

    /**
     * 使用长周期（4.5秒一档）+ 正弦连续占空比判定，使成瘾诱导状态持续时间长、触发概率高
     */
    public static boolean isAddictionHallucinating(int tier, long timeBucketSeed) {
        if (tier <= 0) return false;
        double chance = switch (tier) {
            case 1 -> MedicalConfig.ADDICTION_NAME_HALLUCINATION_CHANCE_T1.get();
            case 2 -> MedicalConfig.ADDICTION_NAME_HALLUCINATION_CHANCE_T2.get();
            default -> MedicalConfig.ADDICTION_NAME_HALLUCINATION_CHANCE_T3.get();
        };
        if (chance >= 0.99) return true;
        long hash = timeBucketSeed * 6364136223846793005L + 1442695040888963407L;
        double roll = ((hash >>> 32) & 0xFFFF) / 65535.0;
        return roll < chance;
    }

    @Override
    public Component getName(ItemStack stack) {
        if (FMLEnvironment.dist == Dist.CLIENT && TreatmentRouter.isOpioidItem(stack)) {
            Component hallucinated = getClientHallucinatedName(stack);
            if (hallucinated != null) {
                return hallucinated;
            }
        }
        return super.getName(stack);
    }

    private Component getClientHallucinatedName(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        MedicalData data = mc.player.getData(MedicalDataCapability.MEDICAL_DATA);
        if (data == null) return null;

        int tier = ModItems.getAddictionTier(data.getAddictionLevel());
        if (tier <= 0) return null;

        // 4.5 秒长周期窗口，避免闪烁过快或持续太短
        long bucket = System.currentTimeMillis() / 4500L;
        int itemSalt = TreatmentRouter.isFentanylItem(stack) ? 31 : 7;
        if (!isAddictionHallucinating(tier, bucket + itemSalt)) {
            return null;
        }

        String[] pool = (tier == 1) ? TIER1_TEMPTATION_NAMES
                : (tier == 2 ? TIER2_TEMPTATION_NAMES : TIER3_TEMPTATION_NAMES);
        int idx = (int) (Math.abs(bucket * 13L + itemSalt) % pool.length);
        ChatFormatting style = (tier == 1) ? ChatFormatting.LIGHT_PURPLE
                : (tier == 2 ? ChatFormatting.GOLD : ChatFormatting.RED);
        return Component.literal(pool[idx]).withStyle(style, ChatFormatting.ITALIC);
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
            return 0xFF00E5FF;
        }
        if (TreatmentRouter.isOpioidItem(stack)) {
            return 0xFFFFB300;
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
                TreatmentClientHooks.openOpioidDoseScreen(hand, null);
                return InteractionResultHolder.success(stack);
            } else {
                player.displayClientMessage(
                        Component.literal("长按左键选部位")
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

        int addictionTier = 0;
        if (FMLEnvironment.dist == Dist.CLIENT && TreatmentRouter.isOpioidItem(stack)) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                MedicalData data = mc.player.getData(MedicalDataCapability.MEDICAL_DATA);
                if (data != null) {
                    addictionTier = ModItems.getAddictionTier(data.getAddictionLevel());
                }
            }
        }

        long bucket = System.currentTimeMillis() / 4500L;
        boolean maskWarning = addictionTier >= 1 && isAddictionHallucinating(addictionTier, bucket + 99L);

        for (String line : TreatmentRouter.getItemEffectSummary(stack)) {
            if (maskWarning && (line.contains("过量") || line.contains("抑制"))) {
                tooltipComponents.add(Component.literal("• ✦ 推到底，不再疼痛")
                        .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
            } else {
                ChatFormatting color = line.contains("过量") || line.contains("抑制")
                        ? ChatFormatting.RED
                        : ChatFormatting.GRAY;
                tooltipComponents.add(Component.literal("• " + line).withStyle(color));
            }
        }

        if (TreatmentRouter.isOpioidItem(stack)) {
            if (addictionTier >= 1 && isAddictionHallucinating(addictionTier, bucket + 55L)) {
                tooltipComponents.add(Component.literal("「快推入静脉...」")
                        .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
            } else {
                tooltipComponents.add(Component.literal("[右键/左键] 选剂量注射").withStyle(ChatFormatting.GOLD));
            }
        } else {
            tooltipComponents.add(Component.literal("[长按左键] 选部位").withStyle(ChatFormatting.AQUA));
        }
    }
}