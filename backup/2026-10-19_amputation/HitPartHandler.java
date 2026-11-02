package com.FIYTP.realmedicalmod.event;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.capability.MedicalDataCapability;
import com.FIYTP.realmedicalmod.config.MedicalConfig;
import com.FIYTP.realmedicalmod.hitbox.DamageCategory;
import com.FIYTP.realmedicalmod.hitbox.DamageTypeHelper;
import com.FIYTP.realmedicalmod.hitbox.HitPartDetector;
import com.FIYTP.realmedicalmod.system.BleedingSystem;
import com.FIYTP.realmedicalmod.system.PainSystem;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

@EventBusSubscriber(modid = "realmedicalmod")
public class HitPartHandler {

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent.Pre event) {
        DamageContainer container = event.getContainer();
        DamageSource source = container.getSource();
        float amount = container.getNewDamage();

        LivingEntity victim = event.getEntity();
        if (!(victim instanceof Player player)) return;

        if ("genericKill".equals(source.getMsgId()) || "outOfWorld".equals(source.getMsgId())) {
            return;
        }

        // 坠落伤害由 FallDamageHandler 处理，避免两个 Handler 重复分配
        if ("fall".equals(source.getMsgId())) {
            return;
        }

        // 拦截溺水伤害，不分配部位伤害和外出血，由主循环RespirationModule与BloodOxygenModule生理机制自然接管
        if ("drown".equals(source.getMsgId())) {
            container.setNewDamage(0.0f);
            return;
        }

        // 获取攻击者（排除自身伤害的情况，如自伤、自爆等）
        LivingEntity attacker = null;
        if (source.getEntity() instanceof LivingEntity living && living != victim) {
            attacker = living;
        }

        // ★ 如果攻击者是玩家，记录攻击行为（用于增加呼吸频率和心率）
        if (attacker instanceof Player attackingPlayer) {
            MedicalData attackerData = attackingPlayer.getData(MedicalDataCapability.MEDICAL_DATA);
            if (attackerData != null) {
                attackerData.setAttackTimer(MedicalConfig.ATTACK_TIMER_DURATION.get()); // 攻击后运动强度增加
            }
        }

        DamageCategory category = DamageTypeHelper.categorize(source);
        if (category == null) {
            return; // 本系统不处理，不拦截原版伤害
        }

        MedicalData data = player.getData(MedicalDataCapability.MEDICAL_DATA);
        if (data == null) return;

        // 烧伤负荷累加：原版火焰伤害照常生效（不清零），负荷进热力期缓冲
        if (category == DamageCategory.FIRE) {
            handleBurnAccumulation(player, data, amount, source);
            player.syncData(MedicalDataCapability.MEDICAL_DATA);
            return;
        }

        // 拦截伤害
        container.setNewDamage(0.0f);

        // 爆炸伤害多部位分配
        if (category == DamageCategory.EXPLOSION) {
            handleExplosionDamage(player, data, amount);
            player.syncData(MedicalDataCapability.MEDICAL_DATA);
            return;
        }

        // 毒素伤害：不进肢体分配，转为中毒条累加
        if (category == DamageCategory.TOXIN) {
            data.addToxinValue(amount * MedicalConfig.TOXIN_ACCUMULATION_RATE.get().floatValue());
            player.syncData(MedicalDataCapability.MEDICAL_DATA);
            return;
        }

        MedicalData.Limb hitLimb = HitPartDetector.detectHitPart(attacker, victim, source);

        MedicalData.LimbData limbData = data.getLimb(hitLimb);
        boolean isPerforation = false;

        float totalBleeding = BleedingSystem.calculateBleeding(amount, isPerforation);
        float externalRatio;
        float internalRatio;
        switch (category) {
            case PIERCING -> {
                externalRatio = MedicalConfig.PIERCING_EXTERNAL_RATIO.get().floatValue();
                internalRatio = MedicalConfig.PIERCING_INTERNAL_RATIO.get().floatValue();
            }
            case SLASHING -> {
                externalRatio = MedicalConfig.SLASHING_EXTERNAL_RATIO.get().floatValue();
                internalRatio = MedicalConfig.SLASHING_INTERNAL_RATIO.get().floatValue();
            }
            case BLUNT -> {
                externalRatio = MedicalConfig.BLUNT_EXTERNAL_RATIO.get().floatValue();
                internalRatio = MedicalConfig.BLUNT_INTERNAL_RATIO.get().floatValue();
            }
            case MIXED, EXPLOSION -> {
                externalRatio = MedicalConfig.MIXED_EXTERNAL_RATIO.get().floatValue();
                internalRatio = MedicalConfig.MIXED_INTERNAL_RATIO.get().floatValue();
            }
            default -> {
                externalRatio = 0.50f;
                internalRatio = 0.50f;
            }
        }

        if (hitLimb == MedicalData.Limb.HEAD) {
            // 头部只扣外出血，不分配普通内出血
            limbData.setBleeding(limbData.getBleeding() + (int)(totalBleeding * externalRatio));
        } else {
            limbData.setBleeding(limbData.getBleeding() + (int)(totalBleeding * externalRatio));
            limbData.setInternalBleedingRate(limbData.getInternalBleedingRate() + (int)(totalBleeding * internalRatio));
        }
        if (externalRatio > 0) {
            limbData.setOpenWound(true);
        }

        int newPain = PainSystem.calculatePainLevel(amount);
        int currentPain = limbData.getPainLevel();
        int resultPain = PainSystem.applyPain(currentPain, newPain);
        limbData.setPainLevel(resultPain);

        // 骨折判定（头部除外，走颅内出血系统）
        if (amount >= MedicalConfig.MIN_DAMAGE_FOR_FRACTURE.get() && hitLimb != MedicalData.Limb.HEAD && !limbData.hasFracture()) {
            float chance = amount >= MedicalConfig.HIGH_DAMAGE_THRESHOLD.get()
                    ? MedicalConfig.HIGH_DAMAGE_FRACTURE_CHANCE.get().floatValue()
                    : MedicalConfig.BASE_FRACTURE_CHANCE.get().floatValue();
            if (Math.random() < chance) {
                limbData.setFracture(true);
                float openChance = switch (category) {
                    case PIERCING -> MedicalConfig.PIERCING_FRACTURE_OPEN_CHANCE.get().floatValue();
                    case SLASHING -> MedicalConfig.SLASHING_FRACTURE_OPEN_CHANCE.get().floatValue();
                    case BLUNT -> MedicalConfig.BLUNT_FRACTURE_OPEN_CHANCE.get().floatValue();
                    case MIXED, EXPLOSION, TOXIN, FIRE -> MedicalConfig.MIXED_FRACTURE_OPEN_CHANCE.get().floatValue();
                };
                boolean isOpen = Math.random() < openChance;
                limbData.setOpenFracture(isOpen);
                if (isOpen) {
                    limbData.setOpenWound(true);
                }

                // 骨折瞬间疼痛累加
                int fracturePain = MedicalConfig.FRACTURE_PAIN_LEVEL.get();
                limbData.setPainLevel(PainSystem.applyPain(limbData.getPainLevel(), fracturePain));

                // 骨折内出血（动态计算）
                float fractureBleed = amount * MedicalConfig.FRACTURE_BLEED_PER_DAMAGE.get().floatValue();
                fractureBleed = Math.min(fractureBleed, MedicalConfig.FRACTURE_BLEED_MAX.get().floatValue());

                float multiplier = (hitLimb == MedicalData.Limb.CHEST)
                        ? MedicalConfig.FRACTURE_CHEST_MULTIPLIER.get().floatValue()
                        : MedicalConfig.FRACTURE_LIMB_MULTIPLIER.get().floatValue();
                fractureBleed *= multiplier;

                limbData.setInternalBleedingRate(limbData.getInternalBleedingRate() + (int)fractureBleed);
            }
        }

        float headThreshold = MedicalConfig.HEAD_BRAIN_DAMAGE_THRESHOLD.get().floatValue();
        if (hitLimb == MedicalData.Limb.HEAD && amount >= headThreshold) {
            int brainDamage = (int)((amount - headThreshold) * 2 + 10);
            data.setBrainHealth(Math.max(0, data.getBrainHealth() - brainDamage));
            int headPain = limbData.getPainLevel() + 2;
            limbData.setPainLevel(Math.min(MedicalConfig.MAX_PAIN_LEVEL.get(), headPain));

            // 颅内出血触发判定
            double baseProb = 0.0;
            if (amount < 13.0f) {
                baseProb = MedicalConfig.TRIGGER_CHANCE_AT_10.get();
            } else if (amount < 16.0f) {
                baseProb = MedicalConfig.TRIGGER_CHANCE_AT_13.get();
            } else if (amount < 19.0f) {
                baseProb = MedicalConfig.TRIGGER_CHANCE_AT_16.get();
            } else if (amount <= 20.0f) {
                baseProb = MedicalConfig.TRIGGER_CHANCE_AT_19.get();
            } else {
                baseProb = MedicalConfig.TRIGGER_CHANCE_AT_20_PLUS.get();
            }

            double priorBonus = 0.0;
            if (data.getBrainHealth() < 70) {
                priorBonus = MedicalConfig.PRIOR_DAMAGE_BONUS.get();
            }

            double finalProb = baseProb + priorBonus;
            if (Math.random() < finalProb) {
                double ratePerDmg = MedicalConfig.IC_RATE_PER_DAMAGE.get();
                int bleedRate = (int) Math.round(amount * ratePerDmg);
                int maxRate = MedicalConfig.IC_MAX_RATE.get();
                bleedRate = Math.min(bleedRate, maxRate);

                if (bleedRate > data.getIntracranialBleedRate()) {
                    data.setIntracranialBleedRate(bleedRate);
                    data.setIntracranialOnsetTimer(0);
                }
            }
        }

        player.syncData(MedicalDataCapability.MEDICAL_DATA);
    }

    /**
     * 烧伤负荷累加：原版火焰伤害照常生效，热力期负荷进缓冲（BurnModule 每秒转正定型）。
     * 部位判定：加权随机 + 75% 粘滞已烧伤肢体（同次着火集中烧同一区域）。
     */
    private static void handleBurnAccumulation(Player player, MedicalData data, float amount, DamageSource source) {
        // 抗火药水=原版免疫，事件不会到达此处，无需额外判定

        // 1. 部位判定：粘滞已烧伤肢体，否则按权重随机
        MedicalData.Limb hitLimb = selectBurnTarget(data);

        MedicalData.LimbData limbData = data.getLimb(hitLimb);
        if (limbData == null) return;

        // 2. 盔甲部位减半（护甲吸收热能；头→头盔，胸/臂→胸甲，腿→护腿）
        float loadMult = 1.0f;
        net.minecraft.world.entity.EquipmentSlot armorSlot = switch (hitLimb) {
            case HEAD -> net.minecraft.world.entity.EquipmentSlot.HEAD;
            case CHEST, LEFT_ARM, RIGHT_ARM -> net.minecraft.world.entity.EquipmentSlot.CHEST;
            case LEFT_LEG, RIGHT_LEG -> net.minecraft.world.entity.EquipmentSlot.LEGS;
        };
        if (!player.getItemBySlot(armorSlot).isEmpty()) {
            loadMult = 0.5f;
        }

        // 3. 负荷与面积累加
        float loadGain = amount * MedicalConfig.BURN_LOAD_PER_DAMAGE.get().floatValue() * loadMult;
        limbData.setBurnLoad(limbData.getBurnLoad() + loadGain);
        limbData.setBurnArea(Math.min(1.0f, limbData.getBurnArea() + amount * MedicalConfig.BURN_AREA_PER_DAMAGE.get().floatValue()));

        // 4. 热力期窗口重置（持续着火不断续窗，脱离火源才开始倒数）
        limbData.setBurnHeatTimer(MedicalConfig.BURN_HEAT_WINDOW.get());

        // 5. 吸入性损伤：头II度以上+着火暴露时增长（深II/III度更快）
        MedicalData.LimbData headData = data.getLimb(MedicalData.Limb.HEAD);
        if (headData != null && (headData.getBurnDegree() >= 2 || hitLimb == MedicalData.Limb.HEAD)) {
            float gain = MedicalConfig.BURN_INHALATION_RATE.get().floatValue()
                    * (headData.getBurnDegree() >= 3 ? 1.5f : 1.0f);
            data.setInhalationInjury(Math.min(1.0f, data.getInhalationInjury() + gain));
        }

        // 6. 灼痛瞬间累加（冲击痛；持续灼痛由 BurnModule 按深度/面积输出）
        int burnPain = PainSystem.calculatePainLevel(amount);
        limbData.setPainLevel(PainSystem.applyPain(limbData.getPainLevel(), burnPain));
    }

    /**
     * 烧伤部位选择：75% 粘滞已烧伤肢体（若存在），否则按部位权重加权随机。
     */
    private static MedicalData.Limb selectBurnTarget(MedicalData data) {
        // 粘滞：已有烧伤（定型或缓冲中）的肢体
        if (Math.random() < MedicalConfig.BURN_STICKINESS.get().floatValue()) {
            java.util.List<MedicalData.Limb> burned = new java.util.ArrayList<>();
            for (MedicalData.Limb limb : MedicalData.Limb.values()) {
                MedicalData.LimbData ld = data.getLimb(limb);
                if (ld != null && ld.hasBurn()) burned.add(limb);
            }
            if (!burned.isEmpty()) {
                return burned.get(playerRandom.nextInt(burned.size()));
            }
        }

        // 加权随机：按各部位权重掷骰
        float wHead = MedicalConfig.BURN_PLACEMENT_HEAD.get().floatValue();
        float wChest = MedicalConfig.BURN_PLACEMENT_CHEST.get().floatValue();
        float wArm = MedicalConfig.BURN_PLACEMENT_ARM.get().floatValue();
        float wLeg = MedicalConfig.BURN_PLACEMENT_LEG.get().floatValue();
        float roll = playerRandom.nextFloat() * (wHead + wChest + wArm * 2.0f + wLeg * 2.0f);

        if (roll < wHead) return MedicalData.Limb.HEAD;
        roll -= wHead;
        if (roll < wChest) return MedicalData.Limb.CHEST;
        roll -= wChest;
        if (roll < wArm) return MedicalData.Limb.LEFT_ARM;
        roll -= wArm;
        if (roll < wArm) return MedicalData.Limb.RIGHT_ARM;
        return (playerRandom.nextBoolean()) ? MedicalData.Limb.LEFT_LEG : MedicalData.Limb.RIGHT_LEG;
    }

    private static final java.util.Random playerRandom = new java.util.Random();

    private static void handleExplosionDamage(Player player, MedicalData data, float totalDamage) {
        float extRatio = MedicalConfig.EXPLOSION_EXTERNAL_RATIO.get().floatValue();
        float intRatio = MedicalConfig.EXPLOSION_INTERNAL_RATIO.get().floatValue();

        float headRatio = MedicalConfig.EXPLOSION_HEAD_RATIO.get().floatValue();
        float chestRatio = MedicalConfig.EXPLOSION_CHEST_RATIO.get().floatValue();
        float armRatio = MedicalConfig.EXPLOSION_ARM_RATIO.get().floatValue();
        float legRatio = MedicalConfig.EXPLOSION_LEG_RATIO.get().floatValue();

        for (MedicalData.Limb limb : MedicalData.Limb.values()) {
            MedicalData.LimbData ld = data.getLimb(limb);
            if (ld == null) continue;

            float partRatio = switch (limb) {
                case HEAD -> headRatio;
                case CHEST -> chestRatio;
                case LEFT_ARM, RIGHT_ARM -> armRatio;
                case LEFT_LEG, RIGHT_LEG -> legRatio;
            };

            float partDamage = totalDamage * partRatio;
            if (partDamage <= 0.0f) continue;

            // 1. 出血
            float partBleeding = BleedingSystem.calculateBleeding(partDamage, false);
            int extBleed = (int) (partBleeding * extRatio);
            ld.setBleeding(ld.getBleeding() + extBleed);
            if (extBleed > 0) {
                ld.setOpenWound(true);
            }

            if (limb != MedicalData.Limb.HEAD) {
                int intBleed = (int) (partBleeding * intRatio);
                ld.setInternalBleedingRate(ld.getInternalBleedingRate() + intBleed);
            }

            // 2. 疼痛
            int newPain = PainSystem.calculatePainLevel(partDamage);
            ld.setPainLevel(PainSystem.applyPain(ld.getPainLevel(), newPain));

            // 3. 骨折（头部除外）
            if (partDamage >= MedicalConfig.MIN_DAMAGE_FOR_FRACTURE.get() && limb != MedicalData.Limb.HEAD && !ld.hasFracture()) {
                float fractureChance = partDamage >= MedicalConfig.HIGH_DAMAGE_THRESHOLD.get()
                        ? MedicalConfig.HIGH_DAMAGE_FRACTURE_CHANCE.get().floatValue()
                        : MedicalConfig.BASE_FRACTURE_CHANCE.get().floatValue();
                if (Math.random() < fractureChance) {
                    ld.setFracture(true);
                    // 爆炸伤开放性骨折概率沿用 MIXED
                    boolean isOpen = Math.random() < MedicalConfig.MIXED_FRACTURE_OPEN_CHANCE.get().floatValue();
                    ld.setOpenFracture(isOpen);
                    if (isOpen) {
                        ld.setOpenWound(true);
                    }
                    ld.setPainLevel(PainSystem.applyPain(ld.getPainLevel(), MedicalConfig.FRACTURE_PAIN_LEVEL.get()));

                    float fractureBleed = partDamage * MedicalConfig.FRACTURE_BLEED_PER_DAMAGE.get().floatValue();
                    fractureBleed = Math.min(fractureBleed, MedicalConfig.FRACTURE_BLEED_MAX.get().floatValue());
                    float multiplier = (limb == MedicalData.Limb.CHEST)
                            ? MedicalConfig.FRACTURE_CHEST_MULTIPLIER.get().floatValue()
                            : MedicalConfig.FRACTURE_LIMB_MULTIPLIER.get().floatValue();
                    fractureBleed *= multiplier;
                    ld.setInternalBleedingRate(ld.getInternalBleedingRate() + (int) fractureBleed);
                }
            }

            // 4. 头部特殊处理：脑损伤与颅内出血判定
            if (limb == MedicalData.Limb.HEAD) {
                float headThreshold = MedicalConfig.HEAD_BRAIN_DAMAGE_THRESHOLD.get().floatValue();
                if (partDamage >= headThreshold) {
                    int brainDamage = (int) ((partDamage - headThreshold) * 2 + 10);
                    data.setBrainHealth(Math.max(0, data.getBrainHealth() - brainDamage));
                    ld.setPainLevel(Math.min(MedicalConfig.MAX_PAIN_LEVEL.get(), ld.getPainLevel() + 2));

                    double baseProb = 0.0;
                    if (partDamage < 13.0f) {
                        baseProb = MedicalConfig.TRIGGER_CHANCE_AT_10.get();
                    } else if (partDamage < 16.0f) {
                        baseProb = MedicalConfig.TRIGGER_CHANCE_AT_13.get();
                    } else if (partDamage < 19.0f) {
                        baseProb = MedicalConfig.TRIGGER_CHANCE_AT_16.get();
                    } else if (partDamage <= 20.0f) {
                        baseProb = MedicalConfig.TRIGGER_CHANCE_AT_19.get();
                    } else {
                        baseProb = MedicalConfig.TRIGGER_CHANCE_AT_20_PLUS.get();
                    }

                    double priorBonus = 0.0;
                    if (data.getBrainHealth() < 70) {
                        priorBonus = MedicalConfig.PRIOR_DAMAGE_BONUS.get();
                    }

                    if (Math.random() < (baseProb + priorBonus)) {
                        double ratePerDmg = MedicalConfig.IC_RATE_PER_DAMAGE.get();
                        int bleedRate = (int) Math.round(partDamage * ratePerDmg);
                        bleedRate = Math.min(bleedRate, MedicalConfig.IC_MAX_RATE.get());
                        if (bleedRate > data.getIntracranialBleedRate()) {
                            data.setIntracranialBleedRate(bleedRate);
                            data.setIntracranialOnsetTimer(0);
                        }
                    }
                }
            }
        }
    }
}