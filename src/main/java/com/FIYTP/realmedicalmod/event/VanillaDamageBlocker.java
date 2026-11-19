package com.FIYTP.realmedicalmod.event;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.capability.MedicalDataCapability;
import com.FIYTP.realmedicalmod.config.MedicalConfig;
import com.FIYTP.realmedicalmod.treatment.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 原版伤害与原版死亡终极兜底拦截器 (VanillaDamageBlocker)
 *
 * 核心机制：
 * 1. 当玩家脑健康归零 (brainHealth <= 0，含爆头即死、胸口心脏/主动脉贯通即死、连续重击休克致死、缺氧脑死亡) 时，
 *    100% 放行致命伤害并立即处决，绝不锁血或回满血量！
 * 2. 当玩家 brainHealth > 0 时，将原版扣血转化为真实医疗创伤后把实际扣除的原版 HP 置为 0，做到“有受击反馈、按医疗机制判定生死”。
 */
@EventBusSubscriber(modid = "realmedicalmod")
public class VanillaDamageBlocker {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingFallPost(LivingFallEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        float safeDist = MedicalConfig.SAFE_FALL_DISTANCE.get().floatValue();
        if (event.getDistance() < safeDist) {
            event.setDamageMultiplier(0.0f);
            event.getEntity().fallDistance = 0.0f;
        } else {
            if (event.getDamageMultiplier() <= 0.0f) {
                event.setDamageMultiplier(1.0f);
            }
            if (event.getEntity() instanceof ServerPlayer sp && event.getDistance() >= safeDist + 1.0f) {
                MedicalData data = sp.getData(MedicalDataCapability.MEDICAL_DATA);
                if (data != null) {
                    ModItems.triggerTraumaAdrenalineRush(sp, data, event.getDistance() - safeDist + 1.5f);
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamagePostIntercept(LivingDamageEvent.Pre event) {
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof Player player)) return;

        DamageSource source = event.getSource();
        String msgId = source.getMsgId();

        MedicalData data = player.getData(MedicalDataCapability.MEDICAL_DATA);

        // ★ 关键修复：若玩家已经触发脑死亡/爆头即死/胸腔心脏破裂即死/连续重创致死 (brainHealth <= 0)，
        //   或者伤害源为 genericKill / outOfWorld，直接放行致命伤害，严禁归零或回满生命值！
        if ("genericKill".equals(msgId) || "generic_kill".equals(msgId) || "outOfWorld".equals(msgId)
                || (data != null && data.getBrainHealth() <= 0)) {
            if (data != null && data.getBrainHealth() <= 0) {
                event.setNewDamage(Math.max(event.getNewDamage(), 10000.0f));
            }
            return;
        }

        // 若是卡在方块内窒息 (inWall)，将其转化为血氧扣减与呼吸抑制
        if (!player.level().isClientSide() && "inWall".equals(msgId) && data != null) {
            data.setBloodOxygen(Math.max(0, data.getBloodOxygen() - 5));
            data.setRespirationRate(0);
            player.syncData(MedicalDataCapability.MEDICAL_DATA);
        }

        // 若是火焰、熔岩、岩浆块、火球等热力伤害，加速目标肢体烧伤面积与烧伤度数上涨
        if (player instanceof ServerPlayer spBurn && data != null) {
            boolean isThermal = "inFire".equals(msgId) || "onFire".equals(msgId)
                    || "lava".equals(msgId) || "hotFloor".equals(msgId)
                    || "fireball".equals(msgId) || "unattributed_fireball".equals(msgId)
                    || "campfire".equals(msgId)
                    || source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE);
            if (isThermal) {
                float thermalRaw = Math.max(1.0f, Math.max(event.getOriginalDamage(), event.getNewDamage()));
                ModItems.applyThermalBurnImpulse(spBurn, data, msgId, thermalRaw);
            }
        }

        // 受伤时赋予短暂的内源性「肾上腺素激增 I」应激 Buff
        if (player instanceof ServerPlayer sp && data != null
                && !"inWall".equals(msgId) && !"drown".equals(msgId) && !"starve".equals(msgId)) {
            float rawDmg = Math.max(event.getOriginalDamage(), event.getNewDamage());
            if (rawDmg >= MedicalConfig.ADRENALINE_TRAUMA_MIN_DAMAGE.get().floatValue()) {
                ModItems.triggerTraumaAdrenalineRush(sp, data, rawDmg);
            }
        }

        // 仅在玩家未发生脑死亡 (brainHealth > 0) 时归零原版 HP 扣血并维持满血
        event.setNewDamage(0.0f);
        if (player.getHealth() < player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDeathIntercept(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        String msgId = event.getSource().getMsgId();
        if ("genericKill".equals(msgId) || "generic_kill".equals(msgId) || "outOfWorld".equals(msgId)) {
            return;
        }

        MedicalData data = player.getData(MedicalDataCapability.MEDICAL_DATA);
        if (data != null && data.getBrainHealth() > 0) {
            event.setCanceled(true);
            player.setHealth(player.getMaxHealth());
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide() || !player.isAlive()) return;

        MedicalData data = player.getData(MedicalDataCapability.MEDICAL_DATA);
        if (data == null) return;

        // 若脑健康已归零 (brainHealth <= 0)，立即执行脑死亡处决，零延迟击杀
        if (data.getBrainHealth() <= 0) {
            player.invulnerableTime = 0;
            player.hurt(player.damageSources().genericKill(), Float.MAX_VALUE);
            return;
        }

        if (player.tickCount % 20 == 0 && player.getHealth() < player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }
}