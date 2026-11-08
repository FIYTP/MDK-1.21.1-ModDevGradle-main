package com.FIYTP.realmedicalmod.treatment;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.capability.MedicalDataCapability;
import com.FIYTP.realmedicalmod.config.MedicalConfig;
import com.FIYTP.realmedicalmod.network.NetworkHandler;
import com.FIYTP.realmedicalmod.network.TreatmentApplyPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

import java.util.List;

/**
 * 医疗用品与注射器物品类 (BandageItem)
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

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return false;
    }

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
        if (TreatmentRouter.isAdrenalineItem(stack)) {
            return 0xFFFFD600;
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
            if (TreatmentRouter.isAdrenalineItem(stack)) {
                MedicalData data = player.getData(MedicalDataCapability.MEDICAL_DATA);
                MedicalData.Limb target = MedicalData.Limb.LEFT_LEG;
                if (data != null) {
                    MedicalData.Limb[] pref = new MedicalData.Limb[]{
                            MedicalData.Limb.LEFT_LEG, MedicalData.Limb.RIGHT_LEG,
                            MedicalData.Limb.LEFT_ARM, MedicalData.Limb.RIGHT_ARM,
                            MedicalData.Limb.CHEST, MedicalData.Limb.HEAD
                    };
                    for (MedicalData.Limb candidate : pref) {
                        MedicalData.LimbData ld = data.getLimb(candidate);
                        if (ld != null && !ld.isAmputated()) {
                            target = candidate;
                            break;
                        }
                    }
                }
                NetworkHandler.sendTreatmentToServer(new TreatmentApplyPacket(
                        hand.ordinal(),
                        target.ordinal(),
                        100
                ));
                return InteractionResultHolder.success(stack);
            } else if (TreatmentRouter.isOpioidItem(stack)) {
                TreatmentClientHooks.openOpioidDoseScreen(hand, null);
                return InteractionResultHolder.success(stack);
            }
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);

        for (String line : TreatmentRouter.getItemEffectSummary(stack)) {
            tooltipComponents.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
        }
    }
}