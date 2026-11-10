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
 * 1. 保留 >= 安全高度 (SAFE_FALL_DISTANCE) 摔伤时的原版受击反馈（红闪、镜头受击倾斜、摔落骨骼/受击音效）：
 *    在 LivingFallEvent 的最低优先级中，若坠落高度 >= SAFE_FALL_DISTANCE，保持 damageMultiplier = 1.0f，
 *    使原版 LivingEntity#causeFallDamage 正常进入 hurt() 触发全套原版受伤视听反馈；
 *    随后在 LivingDamageEvent.Pre 的最低优先级中将实际扣除的 HP 置为 0 (setNewDamage(0.0f))，做到“有受击反馈、不扣原版血量”。
 * 2. 拦截火焰、窒息、饥饿等一切非脑死亡原版扣血，并在 brainHealth > 0 时阻止一切非虚空/非指令杀的原版死亡。
 */
@EventBusSubscriber(modid = "realmedicalmod")
public class VanillaDamageBlocker {

    /**
     * 1. 在 FallDamageHandler 结算完医疗骨折/内出血/疼痛之后：
     *    - 若坠落距离 < SAFE_FALL_DISTANCE（安全高度内），清零伤害倍率以消除无伤小跳抖动；
     *    - 若坠落距离 >= SAFE_FALL_DISTANCE（实际摔伤），恢复 damageMultiplier = 1.0f，
     *      让原版 causeFallDamage 正常调用 hurt() 播放摔落音效、身体泛红与受击镜头摇晃！
     *      （实际 HP 扣减将在随后的 onLivingDamagePostIntercept 中被清零，绝不会扣减原版血量）
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingFallPost(LivingFallEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        float safeDist = MedicalConfig.SAFE_FALL_DISTANCE.get().floatValue();
        if (event.getDistance() < safeDist) {
            event.setDamageMultiplier(0.0f);
            event.getEntity().fallDistance = 0.0f;
        } else {
            // 恢复非零倍率以触发原版 hurt() 受击反馈（红闪+音效+镜头倾斜），实际伤害由 LivingDamageEvent.Pre 归零
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

    /**
     * 2. 在 HitPartHandler 读取伤害数值并完成部位出血/烧伤/爆炸/毒素结算之后，
     *    以 LOWEST 优先级将所有非虚空、非脑死亡的原版 HP 扣血强制清零！
     *    （此时原版 hurt() 的受击红闪、受击音效和镜头倾斜包已经触发完毕）
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamagePostIntercept(LivingDamageEvent.Pre event) {
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof Player player)) return;

        DamageSource source = event.getSource();
        String msgId = source.getMsgId();

        // 仅放行 DeathModule 触发的脑死亡 /kill ("genericKill") 与掉入虚空 ("outOfWorld")
        if ("genericKill".equals(msgId) || "outOfWorld".equals(msgId)) {
            return;
        }

        // 若是卡在方块内窒息 (inWall)，将其转化为血氧扣减与呼吸抑制（走缺氧->脑死亡生理链条）
        if (!player.level().isClientSide() && "inWall".equals(msgId)) {
            MedicalData data = player.getData(MedicalDataCapability.MEDICAL_DATA);
            if (data != null) {
                data.setBloodOxygen(Math.max(0, data.getBloodOxygen() - 5));
                data.setRespirationRate(0);
                player.syncData(MedicalDataCapability.MEDICAL_DATA);
            }
        }

        // 若是火焰、熔岩、岩浆块、火球等热力伤害，加速目标肢体烧伤面积与烧伤度数上涨
        if (player instanceof ServerPlayer spBurn) {
            boolean isThermal = "inFire".equals(msgId) || "onFire".equals(msgId)
                    || "lava".equals(msgId) || "hotFloor".equals(msgId)
                    || "fireball".equals(msgId) || "unattributed_fireball".equals(msgId)
                    || "campfire".equals(msgId)
                    || source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE);
            if (isThermal) {
                MedicalData burnData = spBurn.getData(MedicalDataCapability.MEDICAL_DATA);
                if (burnData != null) {
                    float thermalRaw = Math.max(1.0f, Math.max(event.getOriginalDamage(), event.getNewDamage()));
                    ModItems.applyThermalBurnImpulse(spBurn, burnData, msgId, thermalRaw);
                }
            }
        }

        // 受伤时赋予短暂的内源性「肾上腺素激增 I」应激 Buff
        if (player instanceof ServerPlayer sp && !"inWall".equals(msgId) && !"drown".equals(msgId) && !"starve".equals(msgId)) {
            float rawDmg = Math.max(event.getOriginalDamage(), event.getNewDamage());
            if (rawDmg >= MedicalConfig.ADRENALINE_TRAUMA_MIN_DAMAGE.get().floatValue()) {
                MedicalData data = sp.getData(MedicalDataCapability.MEDICAL_DATA);
                if (data != null) {
                    ModItems.triggerTraumaAdrenalineRush(sp, data, rawDmg);
                }
            }
        }

        // 彻底归零原版 HP 扣血（保留 hurt() 已产生的红闪、镜头倾斜与受击音效）
        event.setNewDamage(0.0f);

        // 保持隐藏的原版生命值满血，防止之前残留的扣血累积致死
        if (player.getHealth() < player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    /**
     * 3. 原版死亡终极保险栓：
     *    只要玩家 brainHealth > 0，且死因不是 genericKill（脑死亡或 /kill）或 outOfWorld（虚空），
     *    严禁触发原版死亡！
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDeathIntercept(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        String msgId = event.getSource().getMsgId();
        if ("genericKill".equals(msgId) || "outOfWorld".equals(msgId)) {
            return;
        }

        MedicalData data = player.getData(MedicalDataCapability.MEDICAL_DATA);
        if (data != null && data.getBrainHealth() > 0) {
            event.setCanceled(true);
            player.setHealth(player.getMaxHealth());
        }
    }

    /**
     * 4. 每秒巡检一次玩家隐藏的原版血条：
     *    只要玩家存活且 brainHealth > 0，始终将隐藏的原版生命值锁定在满值 (20.0f)。
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide() || !player.isAlive()) return;
        if (player.tickCount % 20 != 0) return;

        MedicalData data = player.getData(MedicalDataCapability.MEDICAL_DATA);
        if (data != null && data.getBrainHealth() > 0) {
            if (player.getHealth() < player.getMaxHealth()) {
                player.setHealth(player.getMaxHealth());
            }
        }
    }
}