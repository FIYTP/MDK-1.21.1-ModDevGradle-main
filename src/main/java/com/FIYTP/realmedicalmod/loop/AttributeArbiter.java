package com.FIYTP.realmedicalmod.loop;

import com.FIYTP.realmedicalmod.RealMedicalMod;
import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.capability.MedicalDataCapability;
import com.FIYTP.realmedicalmod.capability.MedicalDataImpl;
import com.FIYTP.realmedicalmod.config.MedicalConfig;
import com.FIYTP.realmedicalmod.treatment.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 连续属性仲裁器（表现层统一入口 —— 含意识归零完全失能锁死 + II/III 级阿片成瘾未注射戒断行动受限）
 */
@EventBusSubscriber(modid = RealMedicalMod.MODID)
public class AttributeArbiter {

    /** 能力维度 */
    public enum Dimension {
        MOVEMENT_SPEED(Attributes.MOVEMENT_SPEED, "arbitrated_movement_speed"),
        ATTACK_DAMAGE(Attributes.ATTACK_DAMAGE, "arbitrated_attack_damage"),
        ATTACK_SPEED(Attributes.ATTACK_SPEED, "arbitrated_attack_speed");

        final net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute;
        final String modifierPath;

        Dimension(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, String modifierPath) {
            this.attribute = attribute;
            this.modifierPath = modifierPath;
        }

        ResourceLocation modifierId() {
            return ResourceLocation.fromNamespaceAndPath(RealMedicalMod.MODID, modifierPath);
        }
    }

    // 每玩家每维度：来源ID -> 倍率
    private static final Map<UUID, Map<Dimension, Map<String, Float>>> SUGGESTIONS = new HashMap<>();

    /** 清空某玩家本轮全部建议（每秒循环开头调用） */
    public static void clear(ServerPlayer player) {
        if (player == null) return;
        SUGGESTIONS.computeIfAbsent(player.getUUID(), k -> new EnumMap<>(Dimension.class)).clear();
    }

    /** 提交一个连续倍率建议（0~1，1=正常） */
    public static void suggest(ServerPlayer player, Dimension dimension, String sourceId, float multiplier) {
        if (player == null || dimension == null || sourceId == null) return;
        SUGGESTIONS.computeIfAbsent(player.getUUID(), k -> new EnumMap<>(Dimension.class))
                .computeIfAbsent(dimension, k -> new HashMap<>())
                .put(sourceId, multiplier);
    }

    /** 每秒循环结尾调用：把每维度 min 倍率写入属性 modifier */
    public static void apply(ServerPlayer player) {
        if (player == null) return;

        MedicalData data = player.getData(MedicalDataCapability.MEDICAL_DATA);
        boolean unconscious = (data != null) && (data.getConsciousness() <= 0 || data.isCardiacArrest());

        // ★ II 级与 III 级阿片成瘾：若未注射阿片（体内阿片浓度低于压制阈值），触发戒断行动受限！ ★
        boolean withdrawalRestricted = (data != null) && ModItems.isInWithdrawalMovementRestriction(data);
        if (withdrawalRestricted) {
            int tier = ModItems.getAddictionTier(data.getAddictionLevel());
            float withdrawalMult = (tier >= 3)
                    ? MedicalConfig.ADDICTION_WITHDRAWAL_SPEED_MULT_T3.get().floatValue()
                    : MedicalConfig.ADDICTION_WITHDRAWAL_SPEED_MULT_T2.get().floatValue();
            suggest(player, Dimension.MOVEMENT_SPEED, "opioid_withdrawal", withdrawalMult);
            suggest(player, Dimension.ATTACK_DAMAGE, "opioid_withdrawal", withdrawalMult);
            suggest(player, Dimension.ATTACK_SPEED, "opioid_withdrawal", withdrawalMult);
        }

        // ★ 肾上腺素退潮透支期 (Adrenaline Crash)：轻微移速与攻速疲惫惩罚 ★
        if (data instanceof MedicalDataImpl adrImpl && adrImpl.getAdrenalineLevelFloat() <= 0.0f && adrImpl.getAdrenalineCrashSec() > 0) {
            float crashMult = MedicalConfig.ADRENALINE_CRASH_SPEED_MULT.get().floatValue();
            suggest(player, Dimension.MOVEMENT_SPEED, "adrenaline_crash", crashMult);
            suggest(player, Dimension.ATTACK_SPEED, "adrenaline_crash", crashMult);
        }

        // ★ 肢体坏死严重失能惩罚（坏死不可被肾上腺素豁免） ★
        boolean hasNecroticLeg = false;
        if (data != null) {
            int necLegs = 0;
            int necArms = 0;
            if (ModItems.isLimbNecrotic(data.getLimb(MedicalData.Limb.LEFT_LEG))) necLegs++;
            if (ModItems.isLimbNecrotic(data.getLimb(MedicalData.Limb.RIGHT_LEG))) necLegs++;
            if (ModItems.isLimbNecrotic(data.getLimb(MedicalData.Limb.LEFT_ARM))) necArms++;
            if (ModItems.isLimbNecrotic(data.getLimb(MedicalData.Limb.RIGHT_ARM))) necArms++;

            int ligLegs = 0;
            int ligArms = 0;
            if (ModItems.isLimbArterialLigated(data.getLimb(MedicalData.Limb.LEFT_LEG))) ligLegs++;
            if (ModItems.isLimbArterialLigated(data.getLimb(MedicalData.Limb.RIGHT_LEG))) ligLegs++;
            if (ModItems.isLimbArterialLigated(data.getLimb(MedicalData.Limb.LEFT_ARM))) ligArms++;
            if (ModItems.isLimbArterialLigated(data.getLimb(MedicalData.Limb.RIGHT_ARM))) ligArms++;
            if (ligLegs > 0) {
                float ligLegMult = MedicalConfig.SUTURE_ARTERIAL_LIGATION_LEG_SPEED_MULT.get().floatValue();
                suggest(player, Dimension.MOVEMENT_SPEED, "arterial_ligation", ligLegMult);
            }
            if (ligArms > 0) {
                float ligArmMult = MedicalConfig.SUTURE_ARTERIAL_LIGATION_ARM_ACTION_MULT.get().floatValue();
                suggest(player, Dimension.ATTACK_DAMAGE, "arterial_ligation", ligArmMult);
                suggest(player, Dimension.ATTACK_SPEED, "arterial_ligation", ligArmMult);
            }

            int csLegs = 0;
            int csArms = 0;
            if (ModItems.isCompartmentSyndrome(data.getLimb(MedicalData.Limb.LEFT_LEG))) csLegs++;
            if (ModItems.isCompartmentSyndrome(data.getLimb(MedicalData.Limb.RIGHT_LEG))) csLegs++;
            if (ModItems.isCompartmentSyndrome(data.getLimb(MedicalData.Limb.LEFT_ARM))) csArms++;
            if (ModItems.isCompartmentSyndrome(data.getLimb(MedicalData.Limb.RIGHT_ARM))) csArms++;
            if (csLegs > 0) {
                float csLegMult = MedicalConfig.COMPARTMENT_LEG_SPEED_MULT.get().floatValue();
                suggest(player, Dimension.MOVEMENT_SPEED, "compartment_syndrome", csLegMult);
            }
            if (csArms > 0) {
                float csArmMult = MedicalConfig.COMPARTMENT_ARM_ACTION_MULT.get().floatValue();
                suggest(player, Dimension.ATTACK_DAMAGE, "compartment_syndrome", csArmMult);
                suggest(player, Dimension.ATTACK_SPEED, "compartment_syndrome", csArmMult);
            }

            // ★ 骨折行动力抑制与夹板固定后行动力恢复 ★
            boolean hasUnsplintedLegFracture = false;
            boolean hasSplintedLegFracture = false;
            for (MedicalData.Limb leg : new MedicalData.Limb[]{MedicalData.Limb.LEFT_LEG, MedicalData.Limb.RIGHT_LEG}) {
                MedicalData.LimbData ld = data.getLimb(leg);
                if (ld != null && !ld.isAmputated() && ld.hasFracture()) {
                    if (ld.hasSplint() || ModItems.isLimbOrthopedicFixed(ld)) hasSplintedLegFracture = true;
                    else hasUnsplintedLegFracture = true;
                }
            }
            MedicalData.LimbData leftLegLd = data.getLimb(MedicalData.Limb.LEFT_LEG);
            MedicalData.LimbData rightLegLd = data.getLimb(MedicalData.Limb.RIGHT_LEG);
            boolean bothLegsDown = ModItems.isLegUnableToStand(leftLegLd) && ModItems.isLegUnableToStand(rightLegLd);
            if (bothLegsDown) {
                float proneCrawlMult = Math.max(0.06f, MedicalConfig.AMPUTATION_DOUBLE_LEG_SPEED_MULTIPLIER.get().floatValue());
                suggest(player, Dimension.MOVEMENT_SPEED, "bilateral_leg_prone", proneCrawlMult);
                if (player.isSprinting()) player.setSprinting(false);
            }
            if (hasUnsplintedLegFracture) {
                suggest(player, Dimension.MOVEMENT_SPEED, "fracture_leg",
                        MedicalConfig.FRACTURE_UNSPLINTED_LEG_SPEED_MULT.get().floatValue());
                if (player.isSprinting()) player.setSprinting(false);
            } else if (hasSplintedLegFracture) {
                suggest(player, Dimension.MOVEMENT_SPEED, "fracture_leg_splinted",
                        MedicalConfig.FRACTURE_SPLINTED_LEG_SPEED_MULT.get().floatValue());
            }

            boolean hasUnsplintedArmFracture = false;
            boolean hasSplintedArmFracture = false;
            for (MedicalData.Limb arm : new MedicalData.Limb[]{MedicalData.Limb.LEFT_ARM, MedicalData.Limb.RIGHT_ARM}) {
                MedicalData.LimbData ld = data.getLimb(arm);
                if (ld != null && !ld.isAmputated() && ld.hasFracture()) {
                    if (ld.hasSplint() || ModItems.isLimbOrthopedicFixed(ld)) hasSplintedArmFracture = true;
                    else hasUnsplintedArmFracture = true;
                }
            }
            if (hasUnsplintedArmFracture) {
                float m = MedicalConfig.FRACTURE_UNSPLINTED_ARM_ACTION_MULT.get().floatValue();
                suggest(player, Dimension.ATTACK_DAMAGE, "fracture_arm", m);
                suggest(player, Dimension.ATTACK_SPEED, "fracture_arm", m);
            } else if (hasSplintedArmFracture) {
                float m = MedicalConfig.FRACTURE_SPLINTED_ARM_ACTION_MULT.get().floatValue();
                suggest(player, Dimension.ATTACK_DAMAGE, "fracture_arm_splinted", m);
                suggest(player, Dimension.ATTACK_SPEED, "fracture_arm_splinted", m);
            }

            if (necLegs > 0) {
                hasNecroticLeg = true;
                float legMult = (necLegs >= 2)
                        ? MedicalConfig.NECROSIS_TWO_LEGS_SPEED_MULT.get().floatValue()
                        : MedicalConfig.NECROSIS_ONE_LEG_SPEED_MULT.get().floatValue();
                suggest(player, Dimension.MOVEMENT_SPEED, "limb_necrosis", legMult);
            }
            if (necArms > 0) {
                float armMult = (necArms >= 2)
                        ? MedicalConfig.NECROSIS_TWO_ARMS_ACTION_MULT.get().floatValue()
                        : MedicalConfig.NECROSIS_ONE_ARM_ACTION_MULT.get().floatValue();
                suggest(player, Dimension.ATTACK_DAMAGE, "limb_necrosis", armMult);
                suggest(player, Dimension.ATTACK_SPEED, "limb_necrosis", armMult);
            }
        }

        int adrTier = (data != null) ? ModItems.getAdrenalineTier(ModItems.getAdrenalineLevel(data)) : 0;
        float adrBoost = (adrTier >= 2)
                ? MedicalConfig.ADRENALINE_SPEED_BOOST_T2.get().floatValue()
                : (adrTier == 1 ? MedicalConfig.ADRENALINE_SPEED_BOOST_T1.get().floatValue() : 1.0f);

        if ((unconscious || withdrawalRestricted || hasNecroticLeg) && player.isSprinting()) {
            player.setSprinting(false);
        }

        Map<Dimension, Map<String, Float>> byDim = SUGGESTIONS.get(player.getUUID());

        for (Dimension dim : Dimension.values()) {
            AttributeInstance attr = player.getAttribute(dim.attribute);
            if (attr == null) continue;

            // 意识归零或心脏骤停时：直接将移动速度、攻击力、攻速倍率锁死为 0.0f（即 modifier = -1.0f）
            if (unconscious) {
                attr.removeModifier(dim.modifierId());
                attr.addTransientModifier(new AttributeModifier(
                        dim.modifierId(), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                continue;
            }

            Map<String, Float> map = (byDim == null) ? null : byDim.get(dim);
            float min = 1.0f;
            if (map != null && !map.isEmpty()) {
                for (float m : map.values()) {
                    if (m < min) min = m;
                }
                min = Math.max(MedicalConfig.ARBITER_MIN_MULTIPLIER.get().floatValue(), min);
            }
            if (adrTier >= 1 && !hasNecroticLeg && (dim == Dimension.MOVEMENT_SPEED || dim == Dimension.ATTACK_SPEED)) {
                float floorWithAdr = (adrTier >= 2) ? 0.78f : 0.62f;
                min = Math.min(adrBoost, Math.max(min, Math.min(1.0f, min + 0.35f)) * adrBoost);
                min = Math.max(min, floorWithAdr);
            }

            attr.removeModifier(dim.modifierId());
            if (Math.abs(min - 1.0f) > 0.005f) {
                attr.addTransientModifier(new AttributeModifier(
                        dim.modifierId(), min - 1.0f, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        }
    }

    /** 玩家登出/重生时清除全部状态，防止 modifier 残留 */
    private static void removeAll(ServerPlayer player) {
        if (player == null) return;
        SUGGESTIONS.remove(player.getUUID());
        for (Dimension dim : Dimension.values()) {
            AttributeInstance attr = player.getAttribute(dim.attribute);
            if (attr != null) {
                attr.removeModifier(dim.modifierId());
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            removeAll(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            removeAll(player);
        }
    }
}