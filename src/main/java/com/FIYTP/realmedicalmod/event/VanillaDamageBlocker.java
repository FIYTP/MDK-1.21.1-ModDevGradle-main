package com.FIYTP.realmedicalmod.event;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.capability.MedicalDataCapability;
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
 * 专门修复原版伤害残留导致玩家在脑健康 (brainHealth > 0) 正常时却发生“原版空血死亡”的严重 Bug：
 *
 * 【原代码存在的 3 处漏网漏洞】：
 * 1. FallDamageHandler.java 漏洞：
 *    仅在坠落距离 distance < safeDist (5格) 时调用了 event.setDamageMultiplier(0.0f)；
 *    而在 distance >= 5 格结算完腿部骨折与脑损伤后，末尾漏写了 event.setDamageMultiplier(0.0f)！
 *    同时 HitPartHandler.java 第 37 行遇到 "fall" 直接 return 而未调用 setNewDamage(0.0f)，
 *    导致 >= 5 格的高空坠落（以及末影珍珠落地）100% 扣除玩家隐藏的原版 20 点血量并直接摔死！
 *
 * 2. HitPartHandler.java 火焰伤害漏洞：
 *    第 70~74 行处理 category == DamageCategory.FIRE（火焰、岩浆、熔岩块、火球）时，
 *    在调用 handleBurnAccumulation(...) 后直接 return，早于第 77 行的 container.setNewDamage(0.0f)，
 *    导致所有火焰与岩浆伤害 100% 扣除玩家隐藏的原版血量并直接烧死！
 *
 * 3. 环境窒息/饥饿与其它未分类伤害漏洞：
 *    当遇到墙体窒息 (inWall)、飞撞墙壁 (flyIntoWall)、饥饿 (starve)、细雪冻伤 (freeze) 或其它模组伤害时，
 *    原版隐藏血条会被暗中扣空，触发非脑死亡的原版死亡。
 *
 * 【本拦截器工作机制】：
 * - 采用 EventPriority.LOWEST（最低优先级），确保在 FallDamageHandler 和 HitPartHandler 完整读取原版伤害数值、
 *   完成所有部位出血、骨折、烧伤、毒素、截肢与脑损伤计算【之后】，再统一将原版扣血归零 (setNewDamage(0.0f))！
 * - 采用 EventPriority.HIGHEST 拦截 LivingDeathEvent：只要死因不是指令杀/脑死亡 (genericKill) 且不是掉入虚空 (outOfWorld)，
 *   且玩家当前 brainHealth > 0，一律取消原版死亡并将隐藏原版血量锁满为 20.0f！
 */
@EventBusSubscriber(modid = "realmedicalmod")
public class VanillaDamageBlocker {

    /**
     * 1. 在 FallDamageHandler (NORMAL 优先级) 结算完 >=5 格坠落的医疗骨折/脑伤之后，
     *    以 LOWEST 优先级将原版坠落伤害倍率彻底清零，防止后续产生原版摔落扣血。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingFallPost(LivingFallEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        event.setDamageMultiplier(0.0f);
        event.getEntity().fallDistance = 0.0f;
    }

    /**
     * 2. 在 HitPartHandler (NORMAL 优先级) 读取伤害数值并完成部位出血/烧伤/爆炸/毒素结算之后，
     *    以 LOWEST 优先级将所有非虚空、非脑死亡的原版 HP 扣血强制清零！
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

        // 彻底归零原版伤害（涵盖 fall 坠落、FIRE 火焰/岩浆、drown 溺水、物理攻击、魔法、饥饿等全部伤害）
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
     *    只要玩家存活且 brainHealth > 0，始终将隐藏的原版生命值锁定在满值 (20.0f)，
     *    避免任何第三方药水或特殊机制绕过伤害事件直接调用 setHealth 扣死玩家。
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