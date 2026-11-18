package com.FIYTP.realmedicalmod.compat;

import com.FIYTP.realmedicalmod.RealMedicalMod;
import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.capability.MedicalDataCapability;
import com.FIYTP.realmedicalmod.config.MedicalConfig;
import com.FIYTP.realmedicalmod.treatment.ModItems;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforgespi.language.ModFileScanData;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [Sable: Ragdolls + Ragdoll Reactions] 独立物理布娃娃与受创脱手兼容处理器
 *
 * 核心功能（100% 由 {@link MedicalConfig} 驱动，零硬编码，与原有代码完全解耦）：
 * 1. 腿部受较重伤暂时摔倒（过一会儿站起）：
 *    - 当左腿或右腿遭受较重枪击/钝击/骨折/脱臼时，触发 Sable 物理布娃娃扑倒并附加沿冲击方向的失衡冲量；
 *    - 倒地持续 {@code RAGDOLL_LEG_KNOCKDOWN_DURATION_TICKS}（默认 3.25 秒）后自动解除布娃娃状态重新站起（若双腿全断则转入匍匐爬行）。
 * 2. 手部受较重伤手中物品脱手飞出：
 *    - 当右臂（主手）或左臂（副手 / 双手重武器握持手）遭受较重伤害或发生骨折/脱臼/截肢时，
 *      手中的武器或物品会以真实抛物线物理速度 ({@link ItemEntity}) 脱手飞出并带有防瞬间吸回延迟 ({@code setPickUpDelay})。
 * 3. 意识归零、心脏骤停或被大口径/爆炸命中直接触发全身真实物理瘫软：
 *    - 意识 <= 0 或心脏骤停期间全程锁定全身布娃娃瘫软状态，苏醒后自动恢复站立；
 *    - 被反器材大口径武器 (.50 BMG / .338 等) 或超阈值重击命中任意部位时，立即沿弹道方向击飞并进入全身布娃娃瘫软。
 * 4. 爆炸冲击波导致暂时摔倒：
 *    - 遭受 TaCZ 榴弹/RPG 或原版/模组爆炸冲击时，按爆炸中心指向玩家的气浪向量掀飞并触发暂时布娃娃翻滚摔倒，数秒后恢复站立。
 */
@EventBusSubscriber(modid = RealMedicalMod.MODID)
public final class SableRagdollCompatHandler {

    private static final String[] SABLE_RAGDOLL_MOD_IDS = {
            "sable_player_ragdoll",
            "sable_ragdolls",
            "sableragdolls",
            "ragdoll_reactions"
    };

    private static volatile boolean reflectionInitialized = false;
    private static volatile boolean sableRagdollLoaded = false;

    // 自动扫描缓存的 Sable: Ragdolls 反射方法
    private static final List<Method> SET_RAGDOLL_BOOL_METHODS = new ArrayList<>();
    private static final List<Method> START_RAGDOLL_METHODS = new ArrayList<>();
    private static final List<Method> STOP_RAGDOLL_METHODS = new ArrayList<>();
    private static final List<Method> TOGGLE_RAGDOLL_METHODS = new ArrayList<>();
    private static final List<Method> IS_RAGDOLL_METHODS = new ArrayList<>();

    private static final class PlayerRagdollState {
        int knockdownTicksRemaining = 0;
        int totalKnockdownDuration = 0;
        boolean currentlyRagdolledByMod = false;
        boolean unconsciousRagdollActive = false;
        long lastTriggerGameTime = -100L;
        long lastDisarmGameTime = -100L;
    }

    private static final Map<UUID, PlayerRagdollState> PLAYER_STATES = new ConcurrentHashMap<>();

    private SableRagdollCompatHandler() {
    }

    // =========================================================================
    // 1. 对接 TaCZ 枪伤与爆炸的权威入口（由 TaczDamageCompatHandler 直接调用）
    // =========================================================================

    /**
     * 当玩家受到 TaCZ 子弹命中时调用：处理手部中弹脱手、腿部重伤摔倒、大口径击飞布娃娃以及昏迷瘫软
     */
    public static void onTaczBallisticHit(ServerPlayer player,
                                          MedicalData data,
                                          MedicalData.Limb hitLimb,
                                          MedicalData.LimbData ld,
                                          Entity bullet,
                                          LivingEntity attacker,
                                          TaczDamageCompatHandler.BallisticProfile profile,
                                          float penetratingDmg,
                                          float bluntDmg,
                                          float totalRawDamage) {
        if (player == null || data == null || ld == null || player.level().isClientSide()) return;
        if (!ModItems.safeCfgBool(MedicalConfig.RAGDOLL_COMPAT_ENABLED, true)) return;

        ensureSableReflectionInitialized();
        float effectiveTrauma = penetratingDmg + bluntDmg * 0.55f;
        Vec3 impactDir = computeImpactDirection(player, bullet, attacker);

        // 1) 手臂（LEFT_ARM / RIGHT_ARM）受较重伤 -> 手中物品脱手飞出
        if (hitLimb == MedicalData.Limb.RIGHT_ARM || hitLimb == MedicalData.Limb.LEFT_ARM) {
            checkAndTriggerArmDisarm(player, hitLimb, ld, effectiveTrauma, impactDir);
        }

        // 2) 意识归零或心脏骤停 -> 立即进入持续全身物理瘫软
        if (data.getConsciousness() <= 0 || data.isCardiacArrest() || data.getBrainHealth() <= 0) {
            triggerUnconsciousFullRagdoll(player, impactDir.scale(0.45));
            return;
        }

        // 3) 大口径反器材武器 (HEAVY_SNIPER_AMR) 或超阈值重击 -> 直接全身物理击飞瘫软
        float heavyCaliberThresh = (float) ModItems.safeCfgDouble(MedicalConfig.RAGDOLL_HEAVY_CALIBER_KNOCKDOWN_DAMAGE, 13.5);
        if (profile == TaczDamageCompatHandler.BallisticProfile.HEAVY_SNIPER_AMR
                || totalRawDamage >= heavyCaliberThresh
                || effectiveTrauma >= heavyCaliberThresh * 0.85f) {
            int durTicks = ModItems.safeCfgInt(MedicalConfig.RAGDOLL_HEAVY_CALIBER_DURATION_TICKS, 85);
            float launchMag = Mth.clamp(0.48f + totalRawDamage * 0.025f, 0.45f, 1.25f);
            Vec3 launchVel = new Vec3(impactDir.x * launchMag, Math.max(0.24, impactDir.y * 0.35 + 0.26), impactDir.z * launchMag);
            triggerTemporaryRagdollKnockdown(player, durTicks, launchVel, "message.realmedicalmod.ragdoll.heavy_impact");
            return;
        }

        // 4) 腿部（LEFT_LEG / RIGHT_LEG）受较重伤或发生骨折/脱臼/截肢 -> 暂时摔倒，过一会儿自动站起
        if (hitLimb == MedicalData.Limb.LEFT_LEG || hitLimb == MedicalData.Limb.RIGHT_LEG) {
            float legThresh = (float) ModItems.safeCfgDouble(MedicalConfig.RAGDOLL_LEG_HEAVY_DAMAGE_THRESHOLD, 6.5);
            boolean severeLegState = ld.isAmputated() || ld.hasFracture() || ld.hasDislocation();
            if (effectiveTrauma >= legThresh || severeLegState) {
                int baseDur = ModItems.safeCfgInt(MedicalConfig.RAGDOLL_LEG_KNOCKDOWN_DURATION_TICKS, 65);
                int actualDur = severeLegState ? Math.round(baseDur * 1.25f) : baseDur;
                Vec3 curVel = player.getDeltaMovement();
                Vec3 tripVel = new Vec3(
                        curVel.x * 0.65 + impactDir.x * 0.36,
                        Math.max(0.16, curVel.y + 0.18),
                        curVel.z * 0.65 + impactDir.z * 0.36
                );
                triggerTemporaryRagdollKnockdown(player, actualDur, tripVel, "message.realmedicalmod.ragdoll.leg_knockdown");
            }
        }
    }

    /**
     * 当玩家受到 TaCZ 爆炸物（RPG-7 / 40mm 榴弹 / 破片手雷）冲击时调用：触发气浪掀飞与暂时摔倒
     */
    public static void onTaczExplosionHit(ServerPlayer player,
                                          MedicalData data,
                                          Entity directProjectile,
                                          LivingEntity attacker,
                                          float rawExplosionDamage) {
        if (player == null || data == null || player.level().isClientSide()) return;
        if (!ModItems.safeCfgBool(MedicalConfig.RAGDOLL_COMPAT_ENABLED, true)) return;

        ensureSableReflectionInitialized();
        float minBlastDmg = (float) ModItems.safeCfgDouble(MedicalConfig.RAGDOLL_EXPLOSION_MIN_DAMAGE, 3.5);
        if (rawExplosionDamage < minBlastDmg) return;

        Vec3 blastDir = computeImpactDirection(player, directProjectile, attacker);
        float blastStrength = Mth.clamp(0.42f + rawExplosionDamage * 0.045f, 0.40f, 1.45f);
        Vec3 blastVel = new Vec3(blastDir.x * blastStrength, Math.min(0.85, 0.28 + rawExplosionDamage * 0.022), blastDir.z * blastStrength);

        // 重度爆炸冲击同时有概率震落主手物品
        if (rawExplosionDamage >= minBlastDmg * 2.2f) {
            MedicalData.LimbData rightArm = data.getLimb(MedicalData.Limb.RIGHT_ARM);
            checkAndTriggerArmDisarm(player, MedicalData.Limb.RIGHT_ARM, rightArm, rawExplosionDamage * 0.65f, blastDir);
        }

        int baseDur = ModItems.safeCfgInt(MedicalConfig.RAGDOLL_EXPLOSION_DURATION_TICKS, 90);
        int scaledDur = Mth.clamp(Math.round(baseDur * (0.75f + rawExplosionDamage / 24.0f)), 40, 200);
        triggerTemporaryRagdollKnockdown(player, scaledDur, blastVel, "message.realmedicalmod.ragdoll.explosion_blast");
    }

    // =========================================================================
    // 2. 原版与其它模组伤害/爆炸事件监听（非 TaCZ 伤害同样支持摔倒、爆炸掀飞与手部脱手）
    // =========================================================================

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (event.getLevel().isClientSide()) return;
        if (!ModItems.safeCfgBool(MedicalConfig.RAGDOLL_COMPAT_ENABLED, true)) return;

        ensureSableReflectionInitialized();
        Vec3 center = event.getExplosion().center();
        float radius = Math.max(2.0f, event.getExplosion().radius());

        for (Entity entity : event.getAffectedEntities()) {
            if (!(entity instanceof ServerPlayer player) || !player.isAlive() || player.isCreative() || player.isSpectator()) {
                continue;
            }
            double dist = player.position().distanceTo(center);
            if (dist > radius * 2.1) continue;

            double exposure = Mth.clamp(1.0 - (dist / (radius * 2.0)), 0.15, 1.0);
            float estBlastDmg = (float) (exposure * radius * 3.2);
            float minBlastDmg = (float) ModItems.safeCfgDouble(MedicalConfig.RAGDOLL_EXPLOSION_MIN_DAMAGE, 3.5);
            if (estBlastDmg < minBlastDmg) continue;

            Vec3 away = player.position().add(0.0, 0.5, 0.0).subtract(center);
            Vec3 dir = away.lengthSqr() > 1.0E-4 ? away.normalize() : player.getLookAngle().reverse();
            double push = Mth.clamp(0.45 + exposure * 0.85, 0.40, 1.40);
            Vec3 blastVel = new Vec3(dir.x * push, Math.min(0.85, 0.26 + exposure * 0.45), dir.z * push);

            int baseDur = ModItems.safeCfgInt(MedicalConfig.RAGDOLL_EXPLOSION_DURATION_TICKS, 90);
            int durTicks = Mth.clamp((int) Math.round(baseDur * (0.7 + exposure * 0.6)), 35, 180);
            triggerTemporaryRagdollKnockdown(player, durTicks, blastVel, "message.realmedicalmod.ragdoll.explosion_blast");
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamagePost(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide() || !player.isAlive() || player.isCreative() || player.isSpectator()) return;
        if (!ModItems.safeCfgBool(MedicalConfig.RAGDOLL_COMPAT_ENABLED, true)) return;

        DamageSource source = event.getSource();
        // TaCZ 枪伤已在 onTaczBallisticHit 中精确处理，此处仅处理非 TaCZ 伤害（如近战重击、摔落、原版爆炸等）
        if (TaczDamageCompatHandler.isTaczDamageSource(source)) return;

        MedicalData data = player.getData(MedicalDataCapability.MEDICAL_DATA);
        if (data == null) return;

        ensureSableReflectionInitialized();
        float dmg = Math.max(event.getNewDamage(), event.getOriginalDamage());
        if (dmg <= 1.0f) return;

        Vec3 dir = computeImpactDirection(player, source.getDirectEntity(),
                source.getEntity() instanceof LivingEntity le ? le : null);

        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            onTaczExplosionHit(player, data, source.getDirectEntity(),
                    source.getEntity() instanceof LivingEntity le ? le : null, dmg);
            return;
        }

        if (source.is(DamageTypeTags.IS_FALL) && dmg >= (float) ModItems.safeCfgDouble(MedicalConfig.RAGDOLL_LEG_HEAVY_DAMAGE_THRESHOLD, 6.5)) {
            int baseDur = ModItems.safeCfgInt(MedicalConfig.RAGDOLL_LEG_KNOCKDOWN_DURATION_TICKS, 65);
            triggerTemporaryRagdollKnockdown(player, baseDur, new Vec3(dir.x * 0.25, 0.12, dir.z * 0.25),
                    "message.realmedicalmod.ragdoll.leg_knockdown");
        }
    }

    // =========================================================================
    // 3. 手部重伤物品脱手抛射逻辑 (Arm Trauma Disarm & Physics Throw)
    // =========================================================================

    public static void checkAndTriggerArmDisarm(ServerPlayer player,
                                                MedicalData.Limb armLimb,
                                                MedicalData.LimbData armLd,
                                                float traumaDamage,
                                                Vec3 impactDir) {
        if (player == null || armLd == null || player.isCreative() || player.isSpectator()) return;

        long nowTick = player.level().getGameTime();
        PlayerRagdollState state = PLAYER_STATES.computeIfAbsent(player.getUUID(), k -> new PlayerRagdollState());
        if (nowTick - state.lastDisarmGameTime < 10L) return;

        float disarmThresh = (float) ModItems.safeCfgDouble(MedicalConfig.RAGDOLL_ARM_DISARM_DAMAGE_THRESHOLD, 6.0);
        boolean structuralArmFailure = armLd.isAmputated() || armLd.hasFracture() || armLd.hasDislocation();

        if (!structuralArmFailure && traumaDamage < disarmThresh) {
            return;
        }

        float baseChance = (float) ModItems.safeCfgDouble(MedicalConfig.RAGDOLL_ARM_HEAVY_HIT_DISARM_CHANCE, 0.78);
        float chance = structuralArmFailure
                ? 1.0f
                : Mth.clamp(baseChance + (traumaDamage - disarmThresh) * 0.035f, 0.15f, 0.98f);

        if (player.getRandom().nextFloat() > chance) {
            return;
        }

        // 右臂对应主手 (MAIN_HAND)，左臂对应副手 (OFF_HAND)；若左臂重伤/骨折且主手端着重型双手枪械，主手枪械也会因左手脱力而脱手！
        InteractionHand targetHand = (armLimb == MedicalData.Limb.RIGHT_ARM)
                ? InteractionHand.MAIN_HAND
                : InteractionHand.OFF_HAND;

        ItemStack stackToDrop = player.getItemInHand(targetHand);
        if (stackToDrop.isEmpty() && armLimb == MedicalData.Limb.LEFT_ARM) {
            ItemStack mainStack = player.getMainHandItem();
            if (!mainStack.isEmpty() && (structuralArmFailure || TaczShootingFeedbackHandler.isTaczGunStack(mainStack))) {
                targetHand = InteractionHand.MAIN_HAND;
                stackToDrop = mainStack;
            }
        } else if (stackToDrop.isEmpty() && armLimb == MedicalData.Limb.RIGHT_ARM) {
            ItemStack offStack = player.getOffhandItem();
            if (!offStack.isEmpty() && structuralArmFailure) {
                targetHand = InteractionHand.OFF_HAND;
                stackToDrop = offStack;
            }
        }

        if (stackToDrop.isEmpty()) return;

        state.lastDisarmGameTime = nowTick;
        ItemStack droppedCopy = stackToDrop.copy();
        Component itemName = droppedCopy.getHoverName();
        player.setItemInHand(targetHand, ItemStack.EMPTY);
        player.stopUsingItem();

        float throwSpeed = (float) ModItems.safeCfgDouble(MedicalConfig.RAGDOLL_ARM_DISARM_THROW_SPEED, 0.42);
        int pickupDelay = ModItems.safeCfgInt(MedicalConfig.RAGDOLL_ARM_DISARM_PICKUP_DELAY_TICKS, 40);

        Vec3 look = player.getLookAngle();
        double sideSign = (targetHand == InteractionHand.MAIN_HAND) ? 0.35 : -0.35;
        Vec3 rightVec = new Vec3(-look.z, 0.0, look.x).normalize();

        double vx = look.x * throwSpeed * 0.75 + rightVec.x * sideSign * throwSpeed + impactDir.x * throwSpeed * 0.55
                + (player.getRandom().nextDouble() - 0.5) * 0.12;
        double vy = 0.22 + Math.min(0.25, traumaDamage * 0.012);
        double vz = look.z * throwSpeed * 0.75 + rightVec.z * sideSign * throwSpeed + impactDir.z * throwSpeed * 0.55
                + (player.getRandom().nextDouble() - 0.5) * 0.12;

        ItemEntity itemEntity = new ItemEntity(
                player.level(),
                player.getX() + look.x * 0.35 + rightVec.x * sideSign * 0.25,
                player.getEyeY() - 0.25,
                player.getZ() + look.z * 0.35 + rightVec.z * sideSign * 0.25,
                droppedCopy
        );
        itemEntity.setDeltaMovement(vx, vy, vz);
        itemEntity.setPickUpDelay(pickupDelay);
        player.level().addFreshEntity(itemEntity);

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.85f, 1.25f);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS, 0.90f, 0.75f);

        player.displayClientMessage(
                Component.translatable("message.realmedicalmod.ragdoll.arm_disarmed", itemName),
                true
        );
    }

    // =========================================================================
    // 4. 摔倒计时器、意识归零持续瘫软与自动站起状态机 (Tick State Machine)
    // =========================================================================

    public static void triggerTemporaryRagdollKnockdown(ServerPlayer player,
                                                        int durationTicks,
                                                        Vec3 launchVelocity,
                                                        String messageKey) {
        if (player == null || !player.isAlive() || player.isCreative() || player.isSpectator()) return;

        PlayerRagdollState state = PLAYER_STATES.computeIfAbsent(player.getUUID(), k -> new PlayerRagdollState());
        long nowTick = player.level().getGameTime();

        // 施加物理击飞/绊倒速度冲量（同时触发 Ragdoll Reactions 的速度突变检测与 Sable 物理惯性）
        if (launchVelocity != null && launchVelocity.lengthSqr() > 1.0E-4) {
            player.setSprinting(false);
            player.stopUsingItem();
            player.setDeltaMovement(launchVelocity);
            player.hasImpulse = true;
            player.hurtMarked = true;
        }

        state.knockdownTicksRemaining = Math.max(state.knockdownTicksRemaining, durationTicks);
        state.totalKnockdownDuration = Math.max(state.totalKnockdownDuration, durationTicks);
        state.lastTriggerGameTime = nowTick;

        if (!state.currentlyRagdolledByMod) {
            state.currentlyRagdolledByMod = true;
            setSableRagdollState(player, true, launchVelocity);
        }

        if (messageKey != null && !messageKey.isEmpty()) {
            player.displayClientMessage(Component.translatable(messageKey), true);
        }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_SMALL_FALL, SoundSource.PLAYERS, 0.95f, 0.78f);
    }

    public static void triggerUnconsciousFullRagdoll(ServerPlayer player, Vec3 impulse) {
        if (player == null || !player.isAlive() || player.isCreative() || player.isSpectator()) return;
        PlayerRagdollState state = PLAYER_STATES.computeIfAbsent(player.getUUID(), k -> new PlayerRagdollState());

        if (impulse != null && impulse.lengthSqr() > 1.0E-4 && !state.unconsciousRagdollActive) {
            player.setDeltaMovement(player.getDeltaMovement().add(impulse));
            player.hasImpulse = true;
            player.hurtMarked = true;
        }

        state.unconsciousRagdollActive = true;
        if (!state.currentlyRagdolledByMod) {
            state.currentlyRagdolledByMod = true;
            setSableRagdollState(player, true, impulse);
        }
    }

    @SubscribeEvent
    public static void onPlayerTickServer(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide() || !player.isAlive()) return;
        if (!ModItems.safeCfgBool(MedicalConfig.RAGDOLL_COMPAT_ENABLED, true)) return;

        ensureSableReflectionInitialized();
        MedicalData data = player.getData(MedicalDataCapability.MEDICAL_DATA);
        if (data == null) return;

        PlayerRagdollState state = PLAYER_STATES.computeIfAbsent(player.getUUID(), k -> new PlayerRagdollState());

        // 1) 检查是否处于意识归零或心脏骤停状态 -> 持续维持全身真实物理瘫软
        boolean isUnconsciousOrArrest = (data.getConsciousness() <= 0 || data.isCardiacArrest());
        if (isUnconsciousOrArrest) {
            state.unconsciousRagdollActive = true;
            if (!state.currentlyRagdolledByMod) {
                state.currentlyRagdolledByMod = true;
                setSableRagdollState(player, true, Vec3.ZERO);
            }
            enforceFallbackProneIfNeeded(player);
            return;
        } else if (state.unconsciousRagdollActive) {
            state.unconsciousRagdollActive = false;
            // 苏醒后若无剩余摔倒计时，则解除布娃娃状态站起
            if (state.knockdownTicksRemaining <= 0 && state.currentlyRagdolledByMod) {
                state.currentlyRagdolledByMod = false;
                setSableRagdollState(player, false, Vec3.ZERO);
                releaseFallbackProneIfNeeded(player, data);
            }
        }

        // 2) 检查腿部重伤/大口径/爆炸导致的暂时摔倒倒计时 -> 倒计时归零后自动站起！
        if (state.knockdownTicksRemaining > 0) {
            state.knockdownTicksRemaining--;
            if (player.isSprinting()) {
                player.setSprinting(false);
            }
            enforceFallbackProneIfNeeded(player);

            if (state.knockdownTicksRemaining == 0 && state.currentlyRagdolledByMod) {
                state.currentlyRagdolledByMod = false;
                state.totalKnockdownDuration = 0;
                setSableRagdollState(player, false, Vec3.ZERO);
                releaseFallbackProneIfNeeded(player, data);
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 0.75f, 1.10f);
            }
        }
    }

    private static void enforceFallbackProneIfNeeded(ServerPlayer player) {
        if (player.getForcedPose() != Pose.SWIMMING) {
            player.setForcedPose(Pose.SWIMMING);
        }
        if (player.getPose() != Pose.SWIMMING) {
            player.setPose(Pose.SWIMMING);
            player.refreshDimensions();
        }
    }

    private static void releaseFallbackProneIfNeeded(ServerPlayer player, MedicalData data) {
        if (!ModItems.shouldForcePlayerProne(player, data)) {
            if (player.getForcedPose() == Pose.SWIMMING) {
                player.setForcedPose(null);
                player.refreshDimensions();
            }
        }
    }

    // =========================================================================
    // 5. Sable: Ragdolls 自动反射扫描与指令/按键三层桥接器
    // =========================================================================

    private static synchronized void ensureSableReflectionInitialized() {
        if (reflectionInitialized) return;
        reflectionInitialized = true;

        ClassLoader cl = SableRagdollCompatHandler.class.getClassLoader();
        for (String modId : SABLE_RAGDOLL_MOD_IDS) {
            if (ModList.get().isLoaded(modId)) {
                sableRagdollLoaded = true;
                try {
                    var modFileInfo = ModList.get().getModFileById(modId);
                    if (modFileInfo != null && modFileInfo.getFile() != null && modFileInfo.getFile().getScanResult() != null) {
                        for (ModFileScanData.ClassData classData : modFileInfo.getFile().getScanResult().getClasses()) {
                            String className = classData.clazz().getClassName();
                            scanSableClassForRagdollApi(cl, className);
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static void scanSableClassForRagdollApi(ClassLoader cl, String className) {
        if (className == null || className.contains("mixin")) return;
        try {
            Class<?> cls = Class.forName(className, false, cl);
            for (Method m : cls.getDeclaredMethods()) {
                if (!Modifier.isStatic(m.getModifiers())) continue;
                String name = m.getName().toLowerCase();
                Class<?>[] params = m.getParameterTypes();
                if (params.length == 0) continue;
                if (!Entity.class.isAssignableFrom(params[0]) && !params[0].isAssignableFrom(ServerPlayer.class)) {
                    continue;
                }

                m.setAccessible(true);
                if (params.length == 1 && (m.getReturnType() == boolean.class || m.getReturnType() == Boolean.class)
                        && (name.contains("isragdoll") || name.contains("hasragdoll") || name.contains("ragdolling"))) {
                    IS_RAGDOLL_METHODS.add(m);
                } else if (params.length == 2 && (params[1] == boolean.class || params[1] == Boolean.class)
                        && (name.contains("ragdoll") || name.contains("tumble"))) {
                    SET_RAGDOLL_BOOL_METHODS.add(m);
                } else if (params.length == 1 && (name.contains("stop") || name.contains("unragdoll")
                        || name.contains("remove") || name.contains("disable") || name.contains("end") || name.contains("exit"))) {
                    STOP_RAGDOLL_METHODS.add(m);
                } else if (params.length == 1 && (name.contains("toggle") || name.equals("ragdoll"))) {
                    TOGGLE_RAGDOLL_METHODS.add(m);
                } else if (params.length == 1 && (name.contains("start") || name.contains("spawn")
                        || name.contains("enable") || name.contains("create") || name.contains("trigger") || name.contains("enter"))) {
                    START_RAGDOLL_METHODS.add(m);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * 调用 Sable: Ragdolls 的公开 API（或回退至内置指令/物理冲量）使玩家进入或退出真实物理布娃娃状态
     */
    private static void setSableRagdollState(ServerPlayer player, boolean enableRagdoll, Vec3 impulse) {
        if (player == null) return;

        boolean invokedApi = false;

        // 路径 1：优先调用 (Player, boolean) 签名的方法（如 setRagdolled(player, true/false)）
        for (Method m : SET_RAGDOLL_BOOL_METHODS) {
            try {
                m.invoke(null, player, enableRagdoll);
                invokedApi = true;
                break;
            } catch (Throwable ignored) {
            }
        }

        // 路径 2：调用显式的 startRagdoll(player) / stopRagdoll(player)
        if (!invokedApi) {
            List<Method> targetList = enableRagdoll ? START_RAGDOLL_METHODS : STOP_RAGDOLL_METHODS;
            for (Method m : targetList) {
                try {
                    m.invoke(null, player);
                    invokedApi = true;
                    break;
                } catch (Throwable ignored) {
                }
            }
        }

        // 路径 3：若仅暴露了 toggleRagdoll(player)，先检查当前状态再按需切换
        if (!invokedApi && !TOGGLE_RAGDOLL_METHODS.isEmpty()) {
            Boolean curState = queryIsPlayerRagdolled(player);
            if (curState == null || curState != enableRagdoll) {
                for (Method m : TOGGLE_RAGDOLL_METHODS) {
                    try {
                        m.invoke(null, player);
                        invokedApi = true;
                        break;
                    } catch (Throwable ignored) {
                    }
                }
            }
        }

        // 路径 4：若已安装 Sable: Ragdolls 0.6.9+，同步兼容其注册的 /ragdoll 指令树
        if (!invokedApi && sableRagdollLoaded) {
            MinecraftServer server = player.getServer();
            if (server != null && server.getCommands().getDispatcher().getRoot().getChild("ragdoll") != null) {
                try {
                    var src = player.createCommandSourceStack().withSuppressedOutput().withPermission(4);
                    var ragdollNode = server.getCommands().getDispatcher().getRoot().getChild("ragdoll");
                    if (ragdollNode.getChildren().isEmpty()) {
                        server.getCommands().performPrefixedCommand(src, "ragdoll");
                    } else {
                        String sub = enableRagdoll ? "true" : "false";
                        server.getCommands().performPrefixedCommand(src, "ragdoll " + player.getScoreboardName() + " " + sub);
                    }
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static Boolean queryIsPlayerRagdolled(Player player) {
        for (Method m : IS_RAGDOLL_METHODS) {
            try {
                Object res = m.invoke(null, player);
                if (res instanceof Boolean b) return b;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static Vec3 computeImpactDirection(ServerPlayer player, Entity projectile, LivingEntity attacker) {
        if (projectile != null) {
            Vec3 vel = projectile.getDeltaMovement();
            if (vel.lengthSqr() > 1.0E-4) {
                return vel.normalize();
            }
            Vec3 diff = player.position().subtract(projectile.position());
            if (diff.lengthSqr() > 1.0E-4) {
                return diff.normalize();
            }
        }
        if (attacker != null) {
            Vec3 diff = player.position().subtract(attacker.position());
            if (diff.lengthSqr() > 1.0E-4) {
                return diff.normalize();
            }
        }
        Vec3 revLook = player.getLookAngle().reverse();
        return revLook.lengthSqr() > 1.0E-4 ? revLook.normalize() : new Vec3(0.0, 0.0, -1.0);
    }

    // =========================================================================
    // 6. 客户端按键桥接与摔倒第一人称翻滚镜头反馈 (Dist.CLIENT)
    // =========================================================================

    @EventBusSubscriber(modid = RealMedicalMod.MODID, value = Dist.CLIENT)
    public static final class ClientRagdollBridge {

        private static KeyMapping cachedSableTumbleKey = null;
        private static boolean keySearchDone = false;
        private static boolean clientRagdollTriggered = false;
        private static float knockdownCameraRoll = 0.0f;
        private static float knockdownCameraPitch = 0.0f;
        private static long lastClientFrameNs = 0L;

        private ClientRagdollBridge() {
        }

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            Player player = mc.player;
            if (player == null || mc.level == null || mc.isPaused()) return;
            if (!ModItems.safeCfgBool(MedicalConfig.RAGDOLL_COMPAT_ENABLED, true)) return;

            MedicalData data = player.getData(MedicalDataCapability.MEDICAL_DATA);
            if (data == null) return;

            // 客户端检测意识归零或心脏骤停时，若安装了 Sable: Ragdolls 且尚未通过服务端 API 触发，则自动触发其客户端 Tumble 键绑定
            boolean shouldBeUnconsciousRagdoll = (data.getConsciousness() <= 0 || data.isCardiacArrest());
            if (shouldBeUnconsciousRagdoll && !clientRagdollTriggered) {
                clientRagdollTriggered = true;
                pulseSableKeybindIfNeeded(mc, player, true);
            } else if (!shouldBeUnconsciousRagdoll && clientRagdollTriggered) {
                clientRagdollTriggered = false;
                pulseSableKeybindIfNeeded(mc, player, false);
            }
        }

        @SubscribeEvent
        public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
            Minecraft mc = Minecraft.getInstance();
            Player player = mc.player;
            if (player == null || mc.level == null || mc.isPaused()) return;
            if (!ModItems.safeCfgBool(MedicalConfig.RAGDOLL_COMPAT_ENABLED, true)) return;

            long nowNs = System.nanoTime();
            float dt = (lastClientFrameNs == 0L) ? 0.016f : Mth.clamp((nowNs - lastClientFrameNs) / 1_000_000_000.0f, 0.001f, 0.08f);
            lastClientFrameNs = nowNs;

            MedicalData data = player.getData(MedicalDataCapability.MEDICAL_DATA);
            boolean forcedProne = (player.getForcedPose() == Pose.SWIMMING);
            boolean unconscious = data != null && (data.getConsciousness() <= 0 || data.isCardiacArrest());

            // 当因摔倒/昏迷处于强制倒地状态时，施加真实的侧翻倒地视角倾斜过渡
            float targetRoll = unconscious ? 68.0f : (forcedProne && !ModItems.shouldForcePlayerProne(player, data) ? 38.0f : 0.0f);
            float targetPitch = (forcedProne && !ModItems.shouldForcePlayerProne(player, data)) ? 14.0f : 0.0f;

            knockdownCameraRoll = Mth.lerp(Math.min(1.0f, dt * 8.5f), knockdownCameraRoll, targetRoll);
            knockdownCameraPitch = Mth.lerp(Math.min(1.0f, dt * 8.5f), knockdownCameraPitch, targetPitch);

            if (Math.abs(knockdownCameraRoll) > 0.05f || Math.abs(knockdownCameraPitch) > 0.05f) {
                event.setRoll(event.getRoll() + knockdownCameraRoll);
                event.setPitch(event.getPitch() + knockdownCameraPitch);
            }
        }

        private static void pulseSableKeybindIfNeeded(Minecraft mc, Player player, boolean wantRagdoll) {
            if (!SET_RAGDOLL_BOOL_METHODS.isEmpty() || !START_RAGDOLL_METHODS.isEmpty()) {
                // 服务端 API 反射已就绪并接管，无需模拟客户端按键
                return;
            }
            Boolean cur = queryIsPlayerRagdolled(player);
            if (cur != null && cur == wantRagdoll) return;

            KeyMapping key = findSableTumbleKey(mc);
            if (key != null) {
                try {
                    Field fClickCount = getDeclaredFieldRecursive(KeyMapping.class, "clickCount");
                    if (fClickCount != null) {
                        fClickCount.setAccessible(true);
                        fClickCount.setInt(key, fClickCount.getInt(key) + 1);
                    }
                } catch (Throwable ignored) {
                }
            }
        }

        private static KeyMapping findSableTumbleKey(Minecraft mc) {
            if (keySearchDone) return cachedSableTumbleKey;
            keySearchDone = true;
            if (mc.options == null || mc.options.keyMappings == null) return null;
            for (KeyMapping km : mc.options.keyMappings) {
                if (km == null) continue;
                String name = km.getName().toLowerCase();
                String cat = km.getCategory().toLowerCase();
                if (name.contains("ragdoll") || name.contains("tumble") || cat.contains("ragdoll") || cat.contains("sable")) {
                    cachedSableTumbleKey = km;
                    break;
                }
            }
            return cachedSableTumbleKey;
        }

        private static Field getDeclaredFieldRecursive(Class<?> cls, String name) {
            Class<?> c = cls;
            while (c != null && c != Object.class) {
                try {
                    return c.getDeclaredField(name);
                } catch (Throwable ignored) {
                    c = c.getSuperclass();
                }
            }
            return null;
        }
    }
}