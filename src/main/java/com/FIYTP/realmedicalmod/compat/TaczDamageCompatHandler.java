package com.FIYTP.realmedicalmod.compat;

import com.FIYTP.realmedicalmod.RealMedicalMod;
import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.capability.MedicalDataCapability;
import com.FIYTP.realmedicalmod.capability.MedicalDataImpl;
import com.FIYTP.realmedicalmod.config.MedicalConfig;
import com.FIYTP.realmedicalmod.treatment.ModItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [TaCZ] 永恒枪械工坊：零 (Timeless and Classics Zero) 独立创伤弹道学兼容处理器
 *
 * 设计原则：
 * 1. 完全独立于原有代码（零侵入，不修改原有 HitPartHandler / VanillaDamageBlocker 等文件，杜绝史山代码）。
 * 2. 原生挂载 TaCZ 官方 API 事件 {@code com.tacz.guns.api.event.common.EntityHurtByGunEvent.Pre} 与 {@code TimelessAPI}：
 *    - 合并 TaCZ 的「双伤害源模型（普通伤害 + 穿甲伤害）」，彻底解决单发子弹连续触发两次 hurt() 导致的判定拆分或重复计算问题；
 *    - 区分「穿透护甲弹道伤害 (Penetrating Ballistic Trauma)」与「防弹衣背衬凹陷钝伤 (Blunt Armor Backface Trauma, BABT)」；
 *    - 基于 {@code EntityKineticBullet} 三维射线落点与 {@code isHeadShot} 精准映射 6 大解剖部位；
 *    - 大幅强化头部（HEAD）爆头致死率：
 *      * 大口径狙击/反器材武器 (.50 BMG / .338 等) 爆头：100% 洞穿颅脑一枪当场即死；
 *      * 步枪/机枪 (5.56 / 7.62 等) 与近距离霰弹命中无防具（或轻型非防弹头饰）头部：100% 一枪直接致死；即使佩戴重型防弹头盔被穿甲击穿也具极高一枪即死率或瞬间重度脑毁伤昏迷；
 *      * 小口径手枪/冲锋枪 (9mm / .45 ACP / 5.7mm 等) 命中无防具头部：60%~88% 概率一枪直接致死，未当场即死也会瞬间扣除 72~92% 脑健康并立即昏迷伴随重度颅内出血。
 */
@EventBusSubscriber(modid = RealMedicalMod.MODID)
public final class TaczDamageCompatHandler {

    private static final String TACZ_MOD_ID = "tacz";
    private static final String EVENT_HURT_BY_GUN_PRE = "com.tacz.guns.api.event.common.EntityHurtByGunEvent$Pre";
    private static final String EVENT_HURT_BY_GUN_POST = "com.tacz.guns.api.event.common.EntityHurtByGunEvent$Post";
    private static final String TIMELESS_API_CLASS = "com.tacz.guns.api.TimelessAPI";

    private static volatile boolean taczListenerAttempted = false;
    private static volatile boolean taczApiAvailable = false;

    // 缓存玩家同 Tick 内已被 TaCZ API 事件接管处理的弹道状态（用于屏蔽后续双伤害源 hurt() 的二次重复结算，以及聚合霰弹多弹丸）
    private static final Map<UUID, ShotTickContext> PLAYER_SHOT_CONTEXT = new ConcurrentHashMap<>();

    // 反射方法缓存（零性能损耗，同时保证即便未在 build.gradle 硬绑 jar 也能 100% 编译通过并兼容多版本 TaCZ）
    private static Method mGetHurtEntity;
    private static Method mGetBullet;
    private static Method mGetAttacker;
    private static Method mGetGunId;
    private static Method mGetBaseAmount;
    private static Method mGetAmount;
    private static Method mIsHeadShot;
    private static Method mGetHeadshotMultiplier;
    private static Method mGetLogicalSide;
    private static Method mGetCommonGunIndex;

    private TaczDamageCompatHandler() {
    }

    /**
     * 弹药/武器创伤弹道学分类
     */
    public enum BallisticProfile {
        /** 手枪 / 冲锋枪弹 (9mm, .45 ACP 等)：浅表/中度软组织出血，低穿甲，裸头命中具高致死概率 */
        PISTOL_SMG(0.85f, 0.35f, 0.02f, 0.30f, 0.0f, 1.0f),
        /** 突击步枪 / 战斗步枪 / 机枪弹 (5.56, 7.62, .308 等)：高速瞬时空腔效应，无防具爆头 100% 一枪即死 */
        RIFLE_MG(1.35f, 1.15f, 0.08f, 0.65f, 0.08f, 1.15f),
        /** 大口径狙击 / 反器材特种弹 (.338 Lapua, .50 BMG 等)：毁灭性动能，爆头无视常规头盔一枪必死，四肢高概率直接创伤性截肢 */
        HEAVY_SNIPER_AMR(1.95f, 1.80f, 0.35f, 0.90f, 0.75f, 1.25f),
        /** 霰弹 (12 Gauge 鹿弹/独头弹)：多弹丸同帧聚合，近距离爆头一枪必死，蜂窝状密集开放创口与多颗鹿弹铅丸嵌留 */
        SHOTGUN(1.25f, 0.55f, 0.06f, 0.50f, 0.08f, 2.10f),
        /** 爆炸物与发射器 (RPG-7, 40mm 榴弹等)：破片贯通 + 冲击波超压复合伤（双肺重度挫伤、连枷胸、骨筋膜室高压） */
        EXPLOSIVE_LAUNCHER(1.40f, 1.65f, 0.20f, 0.70f, 0.45f, 1.60f);

        public final float externalBleedMult;
        public final float internalBleedMult;
        public final float fractureChanceBonus;
        public final float openFractureChance;
        public final float amputationChanceBonus;
        public final float infectionContaminationMult;

        BallisticProfile(float externalBleedMult,
                         float internalBleedMult,
                         float fractureChanceBonus,
                         float openFractureChance,
                         float amputationChanceBonus,
                         float infectionContaminationMult) {
            this.externalBleedMult = externalBleedMult;
            this.internalBleedMult = internalBleedMult;
            this.fractureChanceBonus = fractureChanceBonus;
            this.openFractureChance = openFractureChance;
            this.amputationChanceBonus = amputationChanceBonus;
            this.infectionContaminationMult = infectionContaminationMult;
        }
    }

    /**
     * 记录玩家在当前服务端 Tick 内承受 TaCZ 枪击的聚合上下文
     */
    private static final class ShotTickContext {
        long gameTime = -1L;
        int apiHitsThisTick = 0;
        int fallbackHurtStageThisTick = 0;
        long lastSoundGameTime = -10L;
    }

    /**
     * 解析后的枪械与弹道元数据
     */
    private static final class GunBallisticMeta {
        ResourceLocation gunId;
        ResourceLocation ammoId;
        String gunType = "";
        float armorIgnore = 0.25f;
        int pelletCount = 1;
        BallisticProfile profile = BallisticProfile.RIFLE_MG;
    }

    // =========================================================================
    // 1. 自动挂载 TaCZ 官方 API 事件 (EntityHurtByGunEvent.Pre / Post)
    // =========================================================================

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!taczListenerAttempted) {
            ensureTaczApiEventsRegistered();
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static synchronized void ensureTaczApiEventsRegistered() {
        if (taczListenerAttempted) return;
        taczListenerAttempted = true;

        if (!ModList.get().isLoaded(TACZ_MOD_ID)) {
            return;
        }

        try {
            ClassLoader cl = TaczDamageCompatHandler.class.getClassLoader();
            Class<? extends Event> preClass = (Class<? extends Event>) Class.forName(EVENT_HURT_BY_GUN_PRE, false, cl);
            Class<?> baseEventClass = preClass.getSuperclass();

            mGetHurtEntity = findMethod(baseEventClass, preClass, "getHurtEntity");
            mGetBullet = findMethod(baseEventClass, preClass, "getBullet");
            mGetAttacker = findMethod(baseEventClass, preClass, "getAttacker");
            mGetGunId = findMethod(baseEventClass, preClass, "getGunId");
            mGetBaseAmount = findMethod(baseEventClass, preClass, "getBaseAmount");
            mGetAmount = findMethod(baseEventClass, preClass, "getAmount");
            mIsHeadShot = findMethod(baseEventClass, preClass, "isHeadShot", "isHeadshot");
            mGetHeadshotMultiplier = findMethod(baseEventClass, preClass, "getHeadshotMultiplier", "getHeadShotMultiplier");
            mGetLogicalSide = findMethod(baseEventClass, preClass, "getLogicalSide");

            try {
                Class<?> timelessApiCls = Class.forName(TIMELESS_API_CLASS, false, cl);
                mGetCommonGunIndex = timelessApiCls.getMethod("getCommonGunIndex", ResourceLocation.class);
            } catch (Throwable ignored) {
            }

            // 直接向 NeoForge.EVENT_BUS 注册 EntityHurtByGunEvent.Pre 监听器
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, false, (Class) preClass, TaczDamageCompatHandler::onEntityHurtByGunPre);

            try {
                Class<? extends Event> postClass = (Class<? extends Event>) Class.forName(EVENT_HURT_BY_GUN_POST, false, cl);
                NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, false, (Class) postClass, TaczDamageCompatHandler::onEntityHurtByGunPost);
            } catch (Throwable ignored) {
            }

            taczApiAvailable = true;
        } catch (Throwable t) {
            taczApiAvailable = false;
        }
    }

    /**
     * TaCZ API 核心事件监听：{@code EntityHurtByGunEvent.Pre}
     * 在子弹命中实体、即将执行双伤害源拆分扣血之前触发，拥有最完整的原始伤害、爆头判定、枪械ID与子弹实体坐标。
     */
    private static void onEntityHurtByGunPre(Event event) {
        if (event == null) return;
        try {
            // 仅在服务端逻辑侧结算真实医疗创伤
            if (mGetLogicalSide != null) {
                Object side = mGetLogicalSide.invoke(event);
                if (side != null && "CLIENT".equalsIgnoreCase(side.toString())) {
                    return;
                }
            }

            Object hurtObj = mGetHurtEntity != null ? mGetHurtEntity.invoke(event) : getFieldVal(event, "hurtEntity");
            if (!(hurtObj instanceof ServerPlayer player)) return;
            if (player.level().isClientSide() || !player.isAlive() || player.isCreative() || player.isSpectator()) {
                return;
            }

            Entity bullet = null;
            Object bulletObj = mGetBullet != null ? mGetBullet.invoke(event) : getFieldVal(event, "bullet");
            if (bulletObj instanceof Entity b) {
                bullet = b;
            }

            LivingEntity attacker = null;
            Object attackerObj = mGetAttacker != null ? mGetAttacker.invoke(event) : getFieldVal(event, "attacker");
            if (attackerObj instanceof LivingEntity livingAttacker) {
                attacker = livingAttacker;
            }

            ResourceLocation gunId = null;
            Object gunIdObj = mGetGunId != null ? mGetGunId.invoke(event) : getFieldVal(event, "gunId");
            if (gunIdObj instanceof ResourceLocation rl) {
                gunId = rl;
            } else if (gunIdObj instanceof String s && !s.isEmpty()) {
                gunId = ResourceLocation.tryParse(s);
            }

            float baseAmount = invokeFloat(event, mGetBaseAmount, "baseAmount", 0.0f);
            boolean isHeadShot = invokeBoolean(event, mIsHeadShot, "isHeadShot", false);
            float headshotMult = Math.max(1.0f, invokeFloat(event, mGetHeadshotMultiplier, "headshotMultiplier", 1.0f));
            float totalRawDamage = isHeadShot ? (baseAmount * headshotMult) : baseAmount;
            if (totalRawDamage <= 0.0f) {
                totalRawDamage = invokeFloat(event, mGetAmount, "amount", 0.0f);
            }
            if (totalRawDamage <= 0.05f) return;

            long nowTick = player.level().getGameTime();
            ShotTickContext ctx = PLAYER_SHOT_CONTEXT.computeIfAbsent(player.getUUID(), k -> new ShotTickContext());
            if (ctx.gameTime != nowTick) {
                ctx.gameTime = nowTick;
                ctx.apiHitsThisTick = 0;
                ctx.fallbackHurtStageThisTick = 0;
            }
            ctx.apiHitsThisTick++;

            // 解析枪械与子弹弹道属性
            GunBallisticMeta meta = resolveGunBallisticMeta(gunId, bullet, attacker);

            // 清除原版无敌帧，确保霰弹枪同帧多颗弹丸全部命中并结算嵌留铅丸
            player.invulnerableTime = 0;

            // 执行完整的 TaCZ 创伤弹道学结算
            applyBallisticTraumaToPlayer(player, bullet, attacker, meta, totalRawDamage, isHeadShot, ctx);

        } catch (Throwable ignored) {
        }
    }

    private static void onEntityHurtByGunPost(Event event) {
        // Post 事件保留用于状态同步保障：
        // 若未发生脑死亡 (brainHealth > 0)，确保经过 TaCZ 双伤害源 hurt() 后玩家隐藏原版血条仍保持满值；
        // 若爆头已导致脑健康归零 (brainHealth <= 0)，立即处决触发脑死亡，绝不锁血！
        if (event == null) return;
        try {
            Object hurtObj = mGetHurtEntity != null ? mGetHurtEntity.invoke(event) : getFieldVal(event, "hurtEntity");
            if (hurtObj instanceof ServerPlayer player && !player.level().isClientSide() && player.isAlive()) {
                MedicalData data = player.getData(MedicalDataCapability.MEDICAL_DATA);
                if (data != null) {
                    if (data.getBrainHealth() <= 0) {
                        player.hurt(player.damageSources().genericKill(), Float.MAX_VALUE);
                    } else if (player.getHealth() < player.getMaxHealth()) {
                        player.setHealth(player.getMaxHealth());
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    // =========================================================================
    // 2. LivingDamageEvent.Pre 最高优先级拦截器：
    //    - 拦截已被 EntityHurtByGunEvent.Pre 结算过的双伤害源 hurt()，防止旧 HitPartHandler 重复结算两次；
    //    - 同时作为 TaCZ 爆炸物（RPG/榴弹）或特殊附属子弹的 100% 兜底兼容层！
    // =========================================================================

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDamagePreHighest(LivingDamageEvent.Pre event) {
        if (!taczListenerAttempted) {
            ensureTaczApiEventsRegistered();
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide() || !player.isAlive() || player.isCreative() || player.isSpectator()) {
            return;
        }

        DamageSource source = event.getSource();
        if (!isTaczDamageSource(source)) {
            return;
        }

        long nowTick = player.level().getGameTime();
        ShotTickContext ctx = PLAYER_SHOT_CONTEXT.computeIfAbsent(player.getUUID(), k -> new ShotTickContext());

        // 情况 A：本 Tick 内已经通过 TaCZ 官方 EntityHurtByGunEvent.Pre 完成了权威合并创伤结算
        // 此时直接将 NewDamage 置 0，既保留原版 hurt() 已经触发的受击红闪、受击音效与镜头受击摇晃，又阻止旧 HitPartHandler 重复叠加两次！
        if (ctx.gameTime == nowTick && ctx.apiHitsThisTick > 0) {
            event.setNewDamage(0.0f);
            MedicalData checkData = player.getData(MedicalDataCapability.MEDICAL_DATA);
            if (checkData != null && checkData.getBrainHealth() <= 0 && player.isAlive()) {
                player.hurt(player.damageSources().genericKill(), Float.MAX_VALUE);
            }
            return;
        }

        // 情况 B：TaCZ 枪榴弹 / RPG 火箭弹爆炸伤害，或未走 EntityHurtByGunEvent.Pre 的特殊 TaCZ 伤害兜底
        float rawDmg = Math.max(event.getOriginalDamage(), event.getNewDamage());
        if (rawDmg <= 0.05f) {
            event.setNewDamage(0.0f);
            return;
        }

        if (ctx.gameTime != nowTick) {
            ctx.gameTime = nowTick;
            ctx.apiHitsThisTick = 0;
            ctx.fallbackHurtStageThisTick = 0;
        }
        ctx.fallbackHurtStageThisTick++;

        Entity directEntity = source.getDirectEntity();
        LivingEntity attacker = (source.getEntity() instanceof LivingEntity le) ? le : null;

        if (source.is(DamageTypeTags.IS_EXPLOSION) || source.getMsgId().toLowerCase().contains("explosion")) {
            // TaCZ 爆炸物（RPG-7 / 40mm 榴弹 / 爆炸弹药）冲击波超压 + 破片复合创伤
            applyTaczExplosionTrauma(player, directEntity, attacker, rawDmg, ctx);
            event.setNewDamage(0.0f);
            return;
        }

        // 若是双伤害源在未触发 API 事件时的回退分支：
        // TaCZ 同一发子弹在同一 Tick 内第 1 次调用为普通伤害，第 2 次调用为穿甲伤害 (bypass_armor)
        boolean isArmorBypassStage = source.is(DamageTypeTags.BYPASSES_ARMOR)
                || source.getMsgId().toLowerCase().contains("ignore_armor");
        GunBallisticMeta meta = resolveGunBallisticMeta(null, directEntity, attacker);
        if (isArmorBypassStage) {
            meta.armorIgnore = 1.0f;
        } else {
            meta.armorIgnore = 0.0f;
        }
        boolean headHit = isRayHitHeadFallback(player, directEntity);
        applyBallisticTraumaToPlayer(player, directEntity, attacker, meta, rawDmg, headHit, ctx);
        event.setNewDamage(0.0f);
    }

    // =========================================================================
    // 3. 创伤弹道学核心引擎：穿甲穿透伤 vs 防弹衣背衬钝伤 + 六大口径病理映射
    // =========================================================================

    private static void applyBallisticTraumaToPlayer(ServerPlayer player,
                                                     Entity bullet,
                                                     LivingEntity attacker,
                                                     GunBallisticMeta meta,
                                                     float totalRawDamage,
                                                     boolean isHeadShot,
                                                     ShotTickContext ctx) {
        MedicalData data = player.getData(MedicalDataCapability.MEDICAL_DATA);
        if (data == null) return;

        // 1) 精确 3D 射线部位定位
        MedicalData.Limb hitLimb = resolveHitLimb3D(player, bullet, attacker, isHeadShot);
        MedicalData.LimbData ld = data.getLimb(hitLimb);
        if (ld == null) return;

        // 若命中的四肢已经截肢，残端受到的弹道冲击传导至相邻躯干 (CHEST)
        if (ld.isAmputated() && (hitLimb == MedicalData.Limb.LEFT_ARM || hitLimb == MedicalData.Limb.RIGHT_ARM
                || hitLimb == MedicalData.Limb.LEFT_LEG || hitLimb == MedicalData.Limb.RIGHT_LEG)) {
            hitLimb = MedicalData.Limb.CHEST;
            ld = data.getLimb(MedicalData.Limb.CHEST);
            if (ld == null) return;
        }

        // 2) 计算「双伤害源」穿甲穿透动能 (Penetrating) 与 防弹衣/头盔拦截转化的背衬凹陷钝伤 (Blunt Backface Trauma)
        float armorIgnore = Mth.clamp(meta.armorIgnore, 0.0f, 1.0f);
        float apDamage = totalRawDamage * armorIgnore;
        float nonApRaw = totalRawDamage * (1.0f - armorIgnore);

        float localizedArmor = getEffectiveLimbArmor(player, hitLimb);
        float toughness = (float) player.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        float nonApAfterArmor = CombatRules.getDamageAfterAbsorb(
                player,
                nonApRaw,
                player.damageSources().generic(),
                localizedArmor,
                toughness
        );

        // 实际击穿护甲进入肉体的穿透弹道伤害
        float penetratingDmg = Math.max(0.0f, apDamage + nonApAfterArmor);
        // 被防弹插板/头盔拦截吸收的动能 -> 转化为防弹衣背衬凹陷钝伤 (BABT)
        float bluntAbsorbedDmg = Math.max(0.0f, nonApRaw - nonApAfterArmor);

        BallisticProfile profile = meta.profile;
        boolean isMultiPelletSubsequent = (meta.pelletCount > 1 && ctx.apiHitsThisTick > 1);

        // 3) 穿透弹道创伤结算（外出血、深部血管内出血、贯通伤、感染污染）
        int maxPain = ModItems.safeCfgInt(MedicalConfig.MAX_PAIN_LEVEL, 10);
        double pierceExtRatio = ModItems.safeCfgDouble(MedicalConfig.PIERCING_EXTERNAL_RATIO, 0.80);
        double pierceIntRatio = ModItems.safeCfgDouble(MedicalConfig.PIERCING_INTERNAL_RATIO, 0.20);
        double ratePerDmg = ModItems.safeCfgDouble(MedicalConfig.RATE_PER_DAMAGE, 8.0);
        int maxInternalRate = ModItems.safeCfgInt(MedicalConfig.MAX_INTERNAL_RATE, 300);

        if (penetratingDmg >= 0.45f) {
            // 产生开放性枪眼创口，撕裂原有绷带包扎
            ld.setOpenWound(true);
            ld.setBandaged(false);

            // 判定「前后对穿贯穿伤 (Perforation)」vs「盲管伤弹头/弹丸嵌留体内 (Embedded Bullet)」：
            boolean isThroughAndThrough = switch (profile) {
                case HEAVY_SNIPER_AMR -> penetratingDmg >= 3.2f;
                case RIFLE_MG -> (bluntAbsorbedDmg < 1.8f && penetratingDmg >= 5.8f)
                        || (armorIgnore >= 0.52f && penetratingDmg >= 4.8f);
                case PISTOL_SMG -> (bluntAbsorbedDmg < 0.6f && armorIgnore >= 0.48f && penetratingDmg >= 6.8f
                        && player.getRandom().nextFloat() < 0.40f);
                case SHOTGUN -> (bluntAbsorbedDmg < 1.0f && penetratingDmg >= 8.5f
                        && player.getRandom().nextFloat() < 0.45f);
                case EXPLOSIVE_LAUNCHER -> penetratingDmg >= 9.5f;
            };

            int maxEmbeddedPerLimb = ModItems.safeCfgInt(MedicalConfig.TACZ_MAX_EMBEDDED_BULLETS_PER_LIMB, 12);
            boolean isShotgunLike = (profile == BallisticProfile.SHOTGUN || meta.pelletCount > 1 || ctx.apiHitsThisTick > 1);
            if (isThroughAndThrough) {
                ld.setPerforation(true);
                ModItems.setPerforationPacked(ld, false);
                // 霰弹即使部分弹丸近距离贯穿，外围散射鹿弹铅丸仍会有多颗嵌留在肌肉与肋骨间
                if (isShotgunLike) {
                    int addPellets = !isMultiPelletSubsequent
                            ? (2 + player.getRandom().nextInt(3))
                            : (player.getRandom().nextFloat() < 0.75f ? 1 : 0);
                    ModItems.setEmbeddedBulletCount(ld, Math.min(maxEmbeddedPerLimb, ModItems.getEmbeddedBulletCount(ld) + addPellets));
                } else if (profile == BallisticProfile.EXPLOSIVE_LAUNCHER && player.getRandom().nextFloat() < 0.65f) {
                    ModItems.setEmbeddedBulletCount(ld, Math.min(maxEmbeddedPerLimb, ModItems.getEmbeddedBulletCount(ld) + 2));
                }
            } else {
                // 非贯穿盲管伤：
                // - 若为霰弹枪（12 Gauge 鹿弹），单枪包含多颗铅丸，每次命中该肢体直接嵌留 3~6 颗鹿弹铅丸（同帧后续弹丸继续累加至上限 12 颗）！
                // - 若为普通单发手枪/步枪弹，嵌留 1 枚变形弹头。
                if (isShotgunLike) {
                    int minP = Math.max(1, ModItems.safeCfgInt(MedicalConfig.TACZ_SHOTGUN_EMBEDDED_PELLETS_MIN, 3));
                    int maxP = Math.max(minP, ModItems.safeCfgInt(MedicalConfig.TACZ_SHOTGUN_EMBEDDED_PELLETS_MAX, 6));
                    int addPellets = !isMultiPelletSubsequent
                            ? (minP + player.getRandom().nextInt(maxP - minP + 1))
                            : (1 + (player.getRandom().nextFloat() < 0.65f ? 1 : 0));
                    ModItems.setEmbeddedBulletCount(ld, Math.min(maxEmbeddedPerLimb, ModItems.getEmbeddedBulletCount(ld) + addPellets));
                } else {
                    ModItems.setEmbeddedBulletCount(ld, Math.min(maxEmbeddedPerLimb, ModItems.getEmbeddedBulletCount(ld) + 1));
                }
            }

            // 计算外出血增量 (mL/min)：结合口径外出血倍率与部位动脉分布
            float limbArterialFactor = switch (hitLimb) {
                case CHEST -> 1.15f;
                case LEFT_LEG, RIGHT_LEG -> 1.25f; // 股动脉大出血风险
                case LEFT_ARM, RIGHT_ARM -> 1.10f; // 肱动脉出血
                case HEAD -> 1.25f;                // 颈内/颞浅动脉与颅底大出血
            };
            float pelletScale = isMultiPelletSubsequent ? 0.58f : 1.0f;
            int extBleedAdd = Math.round((float) (penetratingDmg * 24.0 * pierceExtRatio * profile.externalBleedMult * limbArterialFactor * pelletScale));
            if (ld.hasPerforation()) {
                extBleedAdd = Math.round(extBleedAdd * 1.25f);
            }
            if (extBleedAdd > 0) {
                int nextBleed = Math.min(2500, ld.getBleeding() + extBleedAdd);
                ld.setBleeding(nextBleed);
                if (ld.getClottingStartRate() <= 0) {
                    ld.setClottingStartRate(nextBleed);
                }
            }

            // 计算深部内出血增量 (mL/min)：步枪空腔效应与大口径弹会撕裂深层动脉与脏器
            int intBleedAdd = Math.round((float) (penetratingDmg * ratePerDmg * (0.65 + pierceIntRatio) * profile.internalBleedMult * pelletScale));
            if (intBleedAdd > 0) {
                int nextIntBleed = Math.min(maxInternalRate, ld.getInternalBleedingRate() + intBleedAdd);
                ld.setInternalBleedingRate(nextIntBleed);
            }

            // 弹头携入火药残渣与织物碎屑引发感染风险（霰弹蜂窝创面污染率翻倍）
            float infAccumAdd = (0.35f + 0.08f * penetratingDmg) * profile.infectionContaminationMult * pelletScale;
            ld.setInfectionAccumulator(ld.getInfectionAccumulator() + infAccumAdd);
            if (profile == BallisticProfile.SHOTGUN && player.getRandom().nextFloat() < 0.35f) {
                ld.setInfection(Math.min(100, ld.getInfection() + 2));
            }
        }

        // 4) 防弹衣背衬凹陷钝伤 (Blunt Armor Backface Trauma, BABT) 结算：
        // 当防弹插板/头盔成功拦截大动能子弹时，背衬凹陷冲击波仍会造成肺挫伤、闭合性肋骨骨折、钝性肌群血肿与剧痛！
        if (bluntAbsorbedDmg >= 1.5f) {
            float babtPainFloat = bluntAbsorbedDmg * 0.42f;
            int babtPain = Math.min(maxPain, Math.max(1, Math.round(babtPainFloat)));
            if (ld.getPainLevel() < babtPain) {
                ld.setPainLevel(babtPain);
            }

            if (hitLimb == MedicalData.Limb.CHEST) {
                // 胸甲防弹板背衬钝击 -> 诱发肺挫伤 (Lung Contusion) 与闭合性肋骨骨折
                float contusionGain = bluntAbsorbedDmg * 1.65f * (isMultiPelletSubsequent ? 0.45f : 1.0f);
                data.setLungContusion(Math.min(100.0f, data.getLungContusion() + contusionGain));

                // 强动能背衬凹陷仅在极高钝击动能下才有小概率压断肋骨
                float minFracDmg = (float) ModItems.safeCfgDouble(MedicalConfig.TACZ_GUNSHOT_FRACTURE_MIN_DAMAGE, 9.5) * 1.25f;
                float fracScale = (float) ModItems.safeCfgDouble(MedicalConfig.TACZ_GUNSHOT_FRACTURE_CHANCE_MULTIPLIER, 0.26);
                if (bluntAbsorbedDmg >= minFracDmg && !ld.hasFracture()) {
                    float bluntRibFracChance = Mth.clamp((0.12f + (bluntAbsorbedDmg - minFracDmg) * 0.025f) * fracScale, 0.02f, 0.22f);
                    if (player.getRandom().nextFloat() < bluntRibFracChance) {
                        applyFractureToLimb(ld, false);
                        // 肋骨闭合骨折断端仅有低概率刺伤脏层胸膜引发闭合性气胸
                        float closedPneumoChance = (float) ModItems.safeCfgDouble(MedicalConfig.TACZ_PNEUMOTHORAX_BASE_CHANCE, 0.15) * 0.60f;
                        if (ModItems.getPneumothoraxType(data) == 0 && player.getRandom().nextFloat() < closedPneumoChance) {
                            ModItems.setPneumothoraxType(data, 1);
                            ModItems.setPleuralAirMl(data, Math.max(ModItems.getPleuralAirMl(data), 180.0f));
                        }
                    }
                }
            } else if (hitLimb == MedicalData.Limb.HEAD) {
                // 头盔拦截子弹后的重度脑震荡冲击（大幅扣减意识与脑健康震荡伤）
                if (bluntAbsorbedDmg >= 3.0f) {
                    float consDrop = Math.min(55.0f, bluntAbsorbedDmg * 2.6f);
                    data.setConsciousnessFloat(Math.max(0.0f, data.getConsciousnessFloat() - consDrop));
                    float bluntBrainLoss = Math.max(0.0f, (bluntAbsorbedDmg - 4.0f) * 1.6f);
                    if (bluntBrainLoss > 0.0f) {
                        data.setBrainHealthFloat(Math.max(5.0f, data.getBrainHealthFloat() - bluntBrainLoss));
                    }
                    triggerBrainConcussionShock(data, bluntAbsorbedDmg * 0.85f);
                }
            } else if (ld instanceof MedicalDataImpl.LimbDataImpl limbImpl) {
                // 四肢护甲钝击导致深部肌筋膜室钝性积血肿胀
                float compBloodAdd = bluntAbsorbedDmg * 3.2f;
                limbImpl.setCompartmentBlood(limbImpl.getCompartmentBlood() + compBloodAdd);
            }
        }

        // 5) 疼痛等级综合更新
        int penPain = Math.round(penetratingDmg * 0.72f + (ld.hasPerforation() ? 2 : 0));
        if (penPain > 0) {
            ld.setPainLevel(Math.min(maxPain, Math.max(ld.getPainLevel(), Math.min(maxPain, ld.getPainLevel() + Math.max(1, penPain / 2)))));
        }

        // 6) 穿透性骨折与粉碎性开放骨折判定（大幅降低触发率，由 TACZ_GUNSHOT_FRACTURE_* 配置驱动）
        float minFractureThreshold = (float) ModItems.safeCfgDouble(MedicalConfig.TACZ_GUNSHOT_FRACTURE_MIN_DAMAGE, 9.5);
        float fracGlobalMult = (float) ModItems.safeCfgDouble(MedicalConfig.TACZ_GUNSHOT_FRACTURE_CHANCE_MULTIPLIER, 0.26);
        if (penetratingDmg >= minFractureThreshold && fracGlobalMult > 0.001f) {
            float baseFracChance = (float) ModItems.safeCfgDouble(MedicalConfig.BASE_FRACTURE_CHANCE, 0.20);
            float rawChance = baseFracChance * 0.50f + profile.fractureChanceBonus + (penetratingDmg - minFractureThreshold) * 0.020f;
            float fracChance = Mth.clamp(rawChance * fracGlobalMult, 0.015f, profile == BallisticProfile.HEAVY_SNIPER_AMR ? 0.60f : 0.24f);
            if (isMultiPelletSubsequent) {
                fracChance *= 0.18f;
            }
            if (player.getRandom().nextFloat() < fracChance) {
                boolean isOpen = player.getRandom().nextFloat() < profile.openFractureChance || penetratingDmg >= minFractureThreshold * 1.6f;
                applyFractureToLimb(ld, isOpen);
                // 动能极大的穿透性枪伤伴随关节脱位或粉碎性骨块移位
                if (penetratingDmg >= minFractureThreshold * 1.8f && player.getRandom().nextFloat() < 0.45f) {
                    ld.setDislocation(true);
                }
                if (ctx.lastSoundGameTime != ctx.gameTime) {
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.BONE_BLOCK_BREAK, SoundSource.PLAYERS, 1.0f, 0.72f);
                }
            }
        }

        // 7) 部位特异性致命并发症：
        //    - HEAD：高致死率爆头判定（步枪/大口径无防具一枪必死，小口径高概率一枪致死）+ 颅内出血 + 脑实质毁伤
        //    - CHEST：胸膜穿透引发「开放性吸吮性气胸 (pneumothoraxType = 2)」+「创伤性血胸 (chestBloodAccumulation)」+「肺挫伤」
        //    - 四肢 (.50 BMG / 反器材大口径 / 超阈值动能)：创伤性截肢 (Traumatic Amputation) + 骨筋膜室高压积血
        if (hitLimb == MedicalData.Limb.HEAD) {
            handleHeadBallisticTrauma(player, data, ld, penetratingDmg, bluntAbsorbedDmg, totalRawDamage, profile, ctx);
        } else if (hitLimb == MedicalData.Limb.CHEST) {
            handleChestBallisticTrauma(player, data, ld, penetratingDmg, bluntAbsorbedDmg, profile, isMultiPelletSubsequent, ctx);
        } else {
            handleLimbBallisticTrauma(player, data, hitLimb, ld, penetratingDmg, profile, isMultiPelletSubsequent, ctx);
        }

        // 8) 触发创伤性内源肾上腺素激增应激 (Adrenaline Rush)、Sable 物理布娃娃/摔倒与手部重伤脱手
        if (data.getBrainHealth() > 0) {
            ModItems.triggerTraumaAdrenalineRush(player, data, totalRawDamage);
        }
        if (!isMultiPelletSubsequent || ctx.apiHitsThisTick == 1) {
            SableRagdollCompatHandler.onTaczBallisticHit(
                    player, data, hitLimb, ld, bullet, attacker, profile,
                    penetratingDmg, bluntAbsorbedDmg, totalRawDamage
            );
        }
        ctx.lastSoundGameTime = ctx.gameTime;
        player.syncData(MedicalDataCapability.MEDICAL_DATA);

        // 若爆头已直接导致脑健康归零，当场立即触发脑死亡处决（零延迟一枪毙命）
        if (data.getBrainHealth() <= 0 && player.isAlive()) {
            player.hurt(player.damageSources().genericKill(), Float.MAX_VALUE);
        }
    }

    /**
     * 头部弹道创伤与爆头致死核心逻辑：
     * 1. 无防弹头盔保护（未戴头盔或 defense <= 2 的轻型头饰）：
     *    - 大口径狙击/反器材 (HEAVY_SNIPER_AMR)、突击步枪/战斗步枪/机枪 (RIFLE_MG)、近距离霰弹 (SHOTGUN)、爆炸破片 (EXPLOSIVE_LAUNCHER)：
     *      只要有效命中头部 (penetratingDmg >= 3.0f)，100% 直接击穿颅腔一枪毙命 (brainHealth = 0)！
     *    - 小口径手枪/冲锋枪 (PISTOL_SMG，如 9mm / .45 ACP / 5.7mm)：
     *      命中无防具裸头时具有 60% ~ 88% 的高概率一枪直接致死（若 penetratingDmg >= 9.0f 则 100% 一枪死）；
     *      若侥幸未当场即死，瞬间扣除 72 ~ 92 点脑健康、意识直接归零昏迷 (consciousness = 0)，并伴随开放性颅骨骨折与满速率颅内大出血！
     * 2. 佩戴防弹头盔（defense >= 3）：
     *    - 反器材/大口径重狙 (HEAVY_SNIPER_AMR)：penetratingDmg >= 4.0f 时 100% 无视常规头盔一枪爆头即死；
     *    - 步枪/机枪 (RIFLE_MG) 与独头弹/霰弹 (SHOTGUN)：若穿透伤害 penetratingDmg >= 7.5f（击穿头盔），具有 72% ~ 100% 一枪即死概率（>= 10.5f 必死）；
     *      若头盔成功拦下致命弹芯，仍扣除 58 ~ 88 点脑健康 + 意识瞬间清零昏迷 + 满额颅内出血（第二枪必死）；
     *    - 小口径手枪/冲锋枪 (PISTOL_SMG)：若高穿甲弹穿透头盔 (penetratingDmg >= 6.0f) 有 30% ~ 55% 概率一枪致死，其余情况造成 40 ~ 68 点脑实质损伤与重度昏迷。
     */
    private static void handleHeadBallisticTrauma(ServerPlayer player,
                                                  MedicalData data,
                                                  MedicalData.LimbData headLd,
                                                  float penetratingDmg,
                                                  float bluntDmg,
                                                  float totalRawDamage,
                                                  BallisticProfile profile,
                                                  ShotTickContext ctx) {
        if (penetratingDmg <= 0.4f) return;

        ItemStack helmetStack = player.getItemBySlot(EquipmentSlot.HEAD);
        int helmetDefense = 0;
        if (!helmetStack.isEmpty() && helmetStack.getItem() instanceof ArmorItem armorItem) {
            helmetDefense = armorItem.getDefense();
        }
        // 未佩戴头盔，或仅佩戴皮革帽/金头盔/锁链盔等无现代防弹能力的轻型头饰
        boolean unarmoredOrLightHead = helmetStack.isEmpty() || helmetDefense <= 2;

        // 1) 判定是否触发「一枪爆头当场脑死亡 (Instant Lethal Headshot)」
        boolean instantLethal = false;
        float rng = player.getRandom().nextFloat();

        switch (profile) {
            case HEAVY_SNIPER_AMR -> {
                // 反器材步枪 / 大口径狙击枪 (.50 BMG / .338 Lapua)：任何无甲或穿透命中头部 100% 一枪爆头必死
                if (unarmoredOrLightHead || penetratingDmg >= 4.0f || totalRawDamage >= 18.0f) {
                    instantLethal = true;
                }
            }
            case RIFLE_MG, EXPLOSIVE_LAUNCHER -> {
                if (unarmoredOrLightHead) {
                    // 步枪 / 机枪打无防爆头盔的脑袋：100% 一枪直接打死！
                    if (penetratingDmg >= 3.0f || totalRawDamage >= 6.0f) {
                        instantLethal = true;
                    }
                } else {
                    // 佩戴防弹头盔被步枪命中：高穿透伤害直接击穿头盔一枪毙命
                    if (penetratingDmg >= 10.2f) {
                        instantLethal = true;
                    } else if (penetratingDmg >= 6.8f) {
                        float riflePenKillChance = Mth.clamp(0.68f + (penetratingDmg - 6.8f) * 0.095f, 0.65f, 0.98f);
                        instantLethal = (rng < riflePenKillChance);
                    }
                }
            }
            case SHOTGUN -> {
                if (unarmoredOrLightHead) {
                    // 霰弹近距离轰击无防具头部：单发高动能或累计脑损直接一枪毙命
                    if (penetratingDmg >= 3.2f || data.getBrainHealthFloat() <= 45.0f) {
                        instantLethal = true;
                    } else {
                        instantLethal = (rng < 0.78f);
                    }
                } else if (penetratingDmg >= 7.5f) {
                    instantLethal = (rng < 0.80f);
                }
            }
            case PISTOL_SMG -> {
                if (unarmoredOrLightHead) {
                    // 小口径手枪 / 冲锋枪打无防具脑袋：60% ~ 88% 高概率一枪直接致死（高伤近距离 >= 9.0f 必死）
                    if (penetratingDmg >= 9.0f) {
                        instantLethal = true;
                    } else if (penetratingDmg >= 2.2f) {
                        float smgHeadKillChance = Mth.clamp(0.56f + (penetratingDmg - 3.0f) * 0.065f, 0.52f, 0.88f);
                        instantLethal = (rng < smgHeadKillChance);
                    }
                } else if (penetratingDmg >= 5.8f) {
                    // 穿甲手枪/冲锋枪弹（如 5.7x28mm / 4.6x30mm）击穿头盔时也有 32% ~ 58% 概率一枪致死
                    float apSmgKillChance = Mth.clamp(0.32f + (penetratingDmg - 5.8f) * 0.06f, 0.30f, 0.60f);
                    instantLethal = (rng < apSmgKillChance);
                }
            }
        }

        if (instantLethal) {
            executeInstantHeadshotKill(player, data, headLd, ctx);
            return;
        }

        // 2) 若佩戴高等级头盔挡下或小口径擦伤未触发当场即死：
        //    立刻施加毁灭性脑实质挫裂伤 (Brain Health 暴跌) + 开放性颅骨骨折 + 满额急性颅内大出血 + 瞬间重度昏迷！
        applyFractureToLimb(headLd, true);
        headLd.setPerforation(true);

        double icRatePerDmg = ModItems.safeCfgDouble(MedicalConfig.IC_RATE_PER_DAMAGE, 1.2);
        int icMaxRate = ModItems.safeCfgInt(MedicalConfig.IC_MAX_RATE, 30);
        int addIcRate = Math.max(8, (int) Math.round(penetratingDmg * icRatePerDmg * 2.4));
        data.setIntracranialBleedRate(Math.min(icMaxRate, data.getIntracranialBleedRate() + addIcRate));
        data.setIntracranialAccumulation(Math.min(150.0f, data.getIntracranialAccumulation() + Math.max(28.0f, penetratingDmg * 3.8f)));

        int onsetDelay = ModItems.safeCfgInt(MedicalConfig.ONSET_DELAY, 60);
        data.setIntracranialOnsetTimer(Math.max(data.getIntracranialOnsetTimer(), onsetDelay));

        float brainLoss = switch (profile) {
            case HEAVY_SNIPER_AMR -> Math.max(88.0f, penetratingDmg * 9.5f);
            case RIFLE_MG, EXPLOSIVE_LAUNCHER -> unarmoredOrLightHead
                    ? 100.0f
                    : Math.max(62.0f, 45.0f + penetratingDmg * 6.2f);
            case SHOTGUN -> unarmoredOrLightHead
                    ? Math.max(78.0f, penetratingDmg * 8.0f)
                    : Math.max(52.0f, 35.0f + penetratingDmg * 5.2f);
            case PISTOL_SMG -> unarmoredOrLightHead
                    ? Mth.clamp(68.0f + penetratingDmg * 3.5f, 70.0f, 92.0f)
                    : Mth.clamp(38.0f + penetratingDmg * 4.5f, 38.0f, 75.0f);
        };

        float nextBrainHealth = Math.max(0.0f, data.getBrainHealthFloat() - brainLoss);
        data.setBrainHealthFloat(nextBrainHealth);

        if (nextBrainHealth <= 0.4f) {
            executeInstantHeadshotKill(player, data, headLd, ctx);
            return;
        }

        // 爆头生还者瞬间丧失意识进入重度昏迷（或濒临昏迷）
        float consLoss = unarmoredOrLightHead ? 100.0f : Math.max(65.0f, penetratingDmg * 8.5f);
        data.setConsciousnessFloat(Math.max(0.0f, data.getConsciousnessFloat() - consLoss));

        // 触发脑震荡冲击与颅骨碎裂/耳鸣音效
        triggerBrainConcussionShock(data, Math.max(8.0f, penetratingDmg * 1.5f));
        if (ctx.lastSoundGameTime != ctx.gameTime) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BONE_BLOCK_BREAK, SoundSource.PLAYERS, 1.25f, 0.55f);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1.0f, 1.75f);
        }
    }

    /**
     * 执行一枪爆头当场脑死亡结算（将脑健康与意识清零，并突破锁血保护立即击杀）
     */
    private static void executeInstantHeadshotKill(ServerPlayer player,
                                                   MedicalData data,
                                                  MedicalData.LimbData headLd,
                                                  ShotTickContext ctx) {
        int maxPain = ModItems.safeCfgInt(MedicalConfig.MAX_PAIN_LEVEL, 10);
        int icMaxRate = ModItems.safeCfgInt(MedicalConfig.IC_MAX_RATE, 30);

        headLd.setOpenWound(true);
        headLd.setPerforation(true);
        headLd.setFracture(true);
        headLd.setOpenFracture(true);
        headLd.setBandaged(false);
        headLd.setPainLevel(maxPain);
        headLd.setBleeding(Math.max(headLd.getBleeding(), 1200));

        data.setIntracranialBleedRate(icMaxRate);
        data.setIntracranialAccumulation(150.0f);
        data.setBrainHealthFloat(0.0f);
        data.setBrainHealth(0);
        data.setConsciousnessFloat(0.0f);
        data.setConsciousness(0);
        data.setCardiacArrest(true);
        data.setHeartRate(0);
        data.setInCriticalState(false);
        data.setCriticalTimer(0);

        if (ctx.lastSoundGameTime != ctx.gameTime) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.WITHER_BREAK_BLOCK, SoundSource.PLAYERS, 1.10f, 0.75f);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BONE_BLOCK_BREAK, SoundSource.PLAYERS, 1.30f, 0.50f);
        }
    }

    /**
     * 胸腔弹道创伤：开放性吸吮性气胸 (Open Sucking Pneumothorax)、创伤性血胸 (Hemothorax) 与肺实质挫裂伤
     */
    private static void handleChestBallisticTrauma(ServerPlayer player,
                                                   MedicalData data,
                                                   MedicalData.LimbData chestLd,
                                                   float penetratingDmg,
                                                   float bluntDmg,
                                                   BallisticProfile profile,
                                                   boolean isMultiPelletSubsequent,
                                                   ShotTickContext ctx) {
        if (penetratingDmg <= 0.5f) return;

        float pelletScale = isMultiPelletSubsequent ? 0.50f : 1.0f;

        // 1) 肺实质贯通挫伤 (Lung Contusion)
        float contusionAdd = penetratingDmg * 2.1f * profile.internalBleedMult * pelletScale;
        data.setLungContusion(Math.min(100.0f, data.getLungContusion() + contusionAdd));

        // 2) 胸膜腔急性积血 (Hemothorax)
        int maxChestBlood = ModItems.safeCfgInt(MedicalConfig.MAX_CHEST_ACCUMULATION, 2000);
        float chestBloodAdd = penetratingDmg * 18.5f * profile.internalBleedMult * pelletScale;
        data.setChestBloodAccumulation(Math.min((float) maxChestBlood, data.getChestBloodAccumulation() + chestBloodAdd));

        // 3) 子弹击穿胸壁与胸膜 -> 仅在穿透伤害达到配置阈值且通过概率检定时诱发「开放性吸吮性气胸 (pneumothoraxType = 2)」
        float minPneumoDmg = (float) ModItems.safeCfgDouble(MedicalConfig.TACZ_PNEUMOTHORAX_MIN_PENETRATING_DAMAGE, 9.5);
        float basePneumoChance = (float) ModItems.safeCfgDouble(MedicalConfig.TACZ_PNEUMOTHORAX_BASE_CHANCE, 0.15);
        float pneumoRollChance = Mth.clamp(
                basePneumoChance
                        + Math.max(0.0f, penetratingDmg - minPneumoDmg) * 0.020f
                        + (chestLd.hasPerforation() ? 0.08f : 0.0f)
                        + (profile == BallisticProfile.HEAVY_SNIPER_AMR ? 0.22f : 0.0f),
                0.0f,
                0.48f
        ) * (isMultiPelletSubsequent ? 0.20f : 1.0f);
        if ((penetratingDmg >= minPneumoDmg || (chestLd.hasPerforation() && penetratingDmg >= minPneumoDmg * 0.80f))
                && player.getRandom().nextFloat() < pneumoRollChance) {
            int curPType = ModItems.getPneumothoraxType(data);
            // 枪击撕裂原有三边阀或死封敷料
            ModItems.setThreeSidedValveSealed(data, false);
            ModItems.setImproperlyDeadSealed(data, false);
            if (curPType < 2) {
                ModItems.setPneumothoraxType(data, 2);
            }
            float curAir = ModItems.getPleuralAirMl(data);
            float airInflowImpulse = Math.max(295.0f, curAir + penetratingDmg * 26.0f * pelletScale);
            ModItems.setPleuralAirMl(data, Math.min(1450.0f, airInflowImpulse));

            float tensionThresh = (float) ModItems.safeCfgDouble(MedicalConfig.PNEUMOTHORAX_TENSION_THRESHOLD_ML, 800.0);
            float pressureCmH2O = -6.0f + (ModItems.getPleuralAirMl(data) / Math.max(1.0f, tensionThresh)) * 21.0f;
            ModItems.setPleuralPressureCmH2O(data, pressureCmH2O);

            if (ctx.lastSoundGameTime != ctx.gameTime) {
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.85f, 1.80f);
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.PANDA_SNEEZE, SoundSource.PLAYERS, 0.90f, 0.65f);
            }
        }
    }

    /**
     * 四肢弹道创伤：大口径反器材武器 (.50 BMG / .338 等) 创伤性截肢 + 深部肌筋膜室高压血肿
     */
    private static void handleLimbBallisticTrauma(ServerPlayer player,
                                                  MedicalData data,
                                                  MedicalData.Limb hitLimb,
                                                  MedicalData.LimbData ld,
                                                  float penetratingDmg,
                                                  BallisticProfile profile,
                                                  boolean isMultiPelletSubsequent,
                                                  ShotTickContext ctx) {
        if (penetratingDmg <= 0.5f) return;

        // 1) 深部肌筋膜室高压积血（高速步枪弹空腔效应向筋膜室内灌注动脉血）
        if (ld instanceof MedicalDataImpl.LimbDataImpl limbImpl && !ld.isAmputated()) {
            float compBloodAdd = penetratingDmg * 4.8f * profile.internalBleedMult * (isMultiPelletSubsequent ? 0.45f : 1.0f);
            limbImpl.setCompartmentBlood(limbImpl.getCompartmentBlood() + compBloodAdd);
        }

        // 2) 创伤性截肢 (Traumatic Amputation) 判定：
        //    - 大口径反器材武器 (HEAVY_SNIPER_AMR，如 .50 BMG / .338) 命中四肢且穿透伤害 >= 阈值时极高概率直接轰断肢体！
        //    - 其它步枪/霰弹近距离贴脸重度穿透伤害超过 AMPUTATION_DAMAGE_THRESHOLD 时按概率截肢。
        if (!ModItems.safeCfgBool(MedicalConfig.AMPUTATION_ENABLED, true) || ld.isAmputated()) {
            return;
        }

        float configAmpThreshold = (float) ModItems.safeCfgInt(MedicalConfig.AMPUTATION_DAMAGE_THRESHOLD, 15);
        // 反器材大口径武器断肢动能门槛更低（单发 .50 BMG 命中四肢即可断肢）
        float effectiveAmpThreshold = (profile == BallisticProfile.HEAVY_SNIPER_AMR)
                ? Math.min(configAmpThreshold * 0.65f, 10.5f)
                : configAmpThreshold;

        if (penetratingDmg >= effectiveAmpThreshold) {
            float baseChance = (float) ModItems.safeCfgDouble(MedicalConfig.AMPUTATION_CHANCE, 0.10);
            float perDmgChance = (float) ModItems.safeCfgDouble(MedicalConfig.AMPUTATION_CHANCE_PER_DAMAGE, 0.01);
            float cap = (float) ModItems.safeCfgDouble(MedicalConfig.AMPUTATION_CHANCE_CAP, 0.60);

            float ampChance = baseChance + profile.amputationChanceBonus
                    + Math.max(0.0f, penetratingDmg - effectiveAmpThreshold) * (perDmgChance * 2.5f);
            if (profile == BallisticProfile.HEAVY_SNIPER_AMR) {
                ampChance = Math.max(0.85f, Math.min(1.0f, ampChance));
            } else {
                ampChance = Math.min(Math.max(cap, 0.75f), ampChance);
            }

            if (player.getRandom().nextFloat() < ampChance) {
                executeTraumaticAmputation(player, data, hitLimb, ld);
            }
        }
    }

    /**
     * 执行四肢创伤性截肢结算（同步断口喷射性大出血、相邻躯干剧痛与视听震慑反馈）
     */
    private static void executeTraumaticAmputation(ServerPlayer player,
                                                   MedicalData data,
                                                   MedicalData.Limb limb,
                                                   MedicalData.LimbData ld) {
        ld.setAmputated(true);
        ld.setOpenWound(true);
        ld.setPerforation(true);
        ld.setFracture(true);
        ld.setOpenFracture(true);
        ld.setBandaged(false);
        ld.setSplint(false);
        ModItems.setLimbOrthopedicFixed(ld, false);

        int maxPain = ModItems.safeCfgInt(MedicalConfig.MAX_PAIN_LEVEL, 10);
        int ampBleed = ModItems.safeCfgInt(MedicalConfig.AMPUTATION_BLEEDING, 800);
        ld.setBleeding(Math.max(ld.getBleeding(), ampBleed));
        ld.setPainLevel(maxPain);

        // 相邻躯干 (CHEST) 承受牵拉剧痛与内出血冲击
        MedicalData.LimbData chestLd = data.getLimb(MedicalData.Limb.CHEST);
        if (chestLd != null) {
            int adjPain = ModItems.safeCfgInt(MedicalConfig.AMPUTATION_ADJACENT_PAIN, 8);
            int adjBleed = ModItems.safeCfgInt(MedicalConfig.AMPUTATION_ADJACENT_BLEEDING, 200);
            int adjIntBleed = ModItems.safeCfgInt(MedicalConfig.AMPUTATION_ADJACENT_INTERNAL_BLEEDING, 100);
            int maxIntRate = ModItems.safeCfgInt(MedicalConfig.MAX_INTERNAL_RATE, 300);

            chestLd.setPainLevel(Math.min(maxPain, Math.max(chestLd.getPainLevel(), adjPain)));
            chestLd.setBleeding(chestLd.getBleeding() + adjBleed);
            chestLd.setInternalBleedingRate(Math.min(maxIntRate, chestLd.getInternalBleedingRate() + adjIntBleed));
        }

        // 断肢瞬间剧痛休克与骨骼碎裂音效
        data.setConsciousnessFloat(Math.max(5.0f, data.getConsciousnessFloat() - 25.0f));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.WITHER_BREAK_BLOCK, SoundSource.PLAYERS, 0.95f, 0.80f);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BONE_BLOCK_BREAK, SoundSource.PLAYERS, 1.20f, 0.55f);
    }

    /**
     * TaCZ 爆炸物（RPG-7 / 40mm 榴弹 / 破片手雷）冲击波超压 + 破片复合伤结算
     */
    private static void applyTaczExplosionTrauma(ServerPlayer player,
                                                 Entity directProjectile,
                                                 LivingEntity attacker,
                                                 float rawExplosionDamage,
                                                 ShotTickContext ctx) {
        MedicalData data = player.getData(MedicalDataCapability.MEDICAL_DATA);
        if (data == null) return;

        // 1) 冲击波超压直接作用于含气脏器（双肺重度肺挫伤 + 胸膜腔积血 + 脑震荡）
        float blastContusion = Math.min(100.0f, rawExplosionDamage * 3.2f);
        data.setLungContusion(Math.min(100.0f, data.getLungContusion() + blastContusion));

        MedicalData.LimbData chestLd = data.getLimb(MedicalData.Limb.CHEST);
        if (chestLd != null && rawExplosionDamage >= 8.0f) {
            // 超压冲击诱发连枷胸（多根肋骨双处骨折 + 脱位）与张力性/闭合性气胸
            applyFractureToLimb(chestLd, rawExplosionDamage >= 14.0f);
            chestLd.setDislocation(true);
            if (ModItems.getPneumothoraxType(data) == 0) {
                ModItems.setPneumothoraxType(data, rawExplosionDamage >= 14.0f ? 2 : 1);
                ModItems.setPleuralAirMl(data, Math.max(ModItems.getPleuralAirMl(data), 340.0f));
            }
        }

        triggerBrainConcussionShock(data, rawExplosionDamage * 0.85f);

        // 2) 按 MedicalConfig 爆炸部位权重向全身分配破片与冲击伤
        double headRatio = ModItems.safeCfgDouble(MedicalConfig.EXPLOSION_HEAD_RATIO, 0.15);
        double chestRatio = ModItems.safeCfgDouble(MedicalConfig.EXPLOSION_CHEST_RATIO, 0.35);
        double armRatio = ModItems.safeCfgDouble(MedicalConfig.EXPLOSION_ARM_RATIO, 0.13);
        double legRatio = ModItems.safeCfgDouble(MedicalConfig.EXPLOSION_LEG_RATIO, 0.12);

        for (MedicalData.Limb limb : MedicalData.Limb.values()) {
            float weight = switch (limb) {
                case HEAD -> (float) headRatio;
                case CHEST -> (float) chestRatio;
                case LEFT_ARM, RIGHT_ARM -> (float) armRatio;
                case LEFT_LEG, RIGHT_LEG -> (float) legRatio;
            };
            float limbBlastDmg = rawExplosionDamage * weight * 1.65f;
            if (limbBlastDmg >= 1.2f) {
                applyExplosionLimbShrapnel(player, data, limb, limbBlastDmg);
            }
        }

        // 近心贴脸大当量爆炸若使脑部破片伤超阈值，直接触发致命脑损
        if (rawExplosionDamage >= 26.0f) {
            MedicalData.LimbData headLd = data.getLimb(MedicalData.Limb.HEAD);
            if (headLd != null) {
                handleHeadBallisticTrauma(player, data, headLd, rawExplosionDamage * 0.38f, rawExplosionDamage * 0.25f,
                        rawExplosionDamage, BallisticProfile.EXPLOSIVE_LAUNCHER, ctx);
            }
        }

        if (data.getBrainHealth() > 0) {
            ModItems.triggerTraumaAdrenalineRush(player, data, rawExplosionDamage);
        }
        SableRagdollCompatHandler.onTaczExplosionHit(player, data, directProjectile, attacker, rawExplosionDamage);
        player.syncData(MedicalDataCapability.MEDICAL_DATA);
        if (data.getBrainHealth() <= 0 && player.isAlive()) {
            player.hurt(player.damageSources().genericKill(), Float.MAX_VALUE);
        }
    }

    private static void applyExplosionLimbShrapnel(ServerPlayer player,
                                                   MedicalData data,
                                                   MedicalData.Limb limb,
                                                   float limbBlastDmg) {
        MedicalData.LimbData ld = data.getLimb(limb);
        if (ld == null || ld.isAmputated()) return;

        int maxPain = ModItems.safeCfgInt(MedicalConfig.MAX_PAIN_LEVEL, 10);
        int maxIntRate = ModItems.safeCfgInt(MedicalConfig.MAX_INTERNAL_RATE, 300);
        double expExtRatio = ModItems.safeCfgDouble(MedicalConfig.EXPLOSION_EXTERNAL_RATIO, 0.30);
        double expIntRatio = ModItems.safeCfgDouble(MedicalConfig.EXPLOSION_INTERNAL_RATIO, 0.70);

        int extAdd = (int) Math.round(limbBlastDmg * 22.0 * expExtRatio);
        int intAdd = (int) Math.round(limbBlastDmg * 12.0 * expIntRatio);

        if (extAdd > 10) {
            ld.setOpenWound(true);
            ld.setBandaged(false);
            ld.setBleeding(Math.min(2500, ld.getBleeding() + extAdd));
            if (limbBlastDmg >= 3.2f && player.getRandom().nextFloat() < 0.65f) {
                ModItems.setEmbeddedBulletCount(ld, Math.min(8, ModItems.getEmbeddedBulletCount(ld) + 1));
            }
        }
        if (intAdd > 0) {
            ld.setInternalBleedingRate(Math.min(maxIntRate, ld.getInternalBleedingRate() + intAdd));
        }
        ld.setPainLevel(Math.min(maxPain, Math.max(ld.getPainLevel(), Math.round(limbBlastDmg * 0.85f))));

        if (ld instanceof MedicalDataImpl.LimbDataImpl limbImpl
                && (limb == MedicalData.Limb.LEFT_ARM || limb == MedicalData.Limb.RIGHT_ARM
                || limb == MedicalData.Limb.LEFT_LEG || limb == MedicalData.Limb.RIGHT_LEG)) {
            limbImpl.setCompartmentBlood(limbImpl.getCompartmentBlood() + limbBlastDmg * 6.5f);
        }

        float expFracChance = (float) ModItems.safeCfgDouble(MedicalConfig.EXPLOSION_FRACTURE_CHANCE, 0.50);
        if (limbBlastDmg >= 4.5f && player.getRandom().nextFloat() < expFracChance) {
            applyFractureToLimb(ld, limbBlastDmg >= 8.0f);
        }
    }

    private static void applyFractureToLimb(MedicalData.LimbData ld, boolean openFracture) {
        if (ld == null || ld.isAmputated()) return;
        ld.setFracture(true);
        if (openFracture) {
            ld.setOpenFracture(true);
            ld.setOpenWound(true);
        }
        ld.setSplint(false);
        ModItems.setLimbOrthopedicFixed(ld, false);
        ModItems.setChestBraced(ld, false);

        int fracPain = ModItems.safeCfgInt(MedicalConfig.FRACTURE_PAIN_LEVEL, 7);
        int maxPain = ModItems.safeCfgInt(MedicalConfig.MAX_PAIN_LEVEL, 10);
        if (ld.getPainLevel() < fracPain) {
            ld.setPainLevel(Math.min(maxPain, fracPain));
        }
        int healSec = ModItems.safeCfgInt(MedicalConfig.SPLINT_FRACTURE_HEAL_TIME_SEC, 300);
        ModItems.setFractureTimerAuth(ld, healSec);
    }

    private static void triggerBrainConcussionShock(MedicalData data, float intensity) {
        if (data == null || intensity < 3.0f) return;
        if (data.getBrainDamageTriggerCooldown() <= 0) {
            int interval = ModItems.safeCfgInt(MedicalConfig.BRAIN_DAMAGE_TRIGGER_INTERVAL, 45);
            data.setBrainDamageTriggerTimer(interval);
        }
    }

    // =========================================================================
    // 4. 精确 3D 射线落点部位计算与局部护甲换算
    // =========================================================================

    private static MedicalData.Limb resolveHitLimb3D(ServerPlayer player,
                                                     Entity bullet,
                                                     LivingEntity attacker,
                                                     boolean isHeadShot) {
        if (isHeadShot) {
            return MedicalData.Limb.HEAD;
        }

        Vec3 hitVec = null;
        if (bullet != null) {
            Vec3 curPos = bullet.position();
            Vec3 vel = bullet.getDeltaMovement();
            Vec3 prevPos = new Vec3(bullet.xo, bullet.yo, bullet.zo);
            Vec3 rayStart = vel.lengthSqr() > 1.0E-4 ? curPos.subtract(vel.scale(1.5)) : prevPos;
            Vec3 rayEnd = vel.lengthSqr() > 1.0E-4 ? curPos.add(vel.scale(1.5)) : curPos;

            AABB expandedBox = player.getBoundingBox().inflate(0.18);
            Optional<Vec3> clipHit = expandedBox.clip(rayStart, rayEnd);
            hitVec = clipHit.orElse(curPos);
        } else if (attacker != null) {
            Vec3 eyeStart = attacker.getEyePosition();
            Vec3 lookEnd = eyeStart.add(attacker.getLookAngle().scale(64.0));
            Optional<Vec3> clipHit = player.getBoundingBox().inflate(0.18).clip(eyeStart, lookEnd);
            hitVec = clipHit.orElse(null);
        }

        if (hitVec == null) {
            return MedicalData.Limb.CHEST;
        }

        double bbHeight = Math.max(0.5, player.getBbHeight());
        double relY = Mth.clamp((hitVec.y - player.getY()) / bbHeight, 0.0, 1.0);

        // 计算命中点相对于玩家身体朝向 (yBodyRot) 的左右横向偏距
        double dx = hitVec.x - player.getX();
        double dz = hitVec.z - player.getZ();
        float bodyYawRad = player.yBodyRot * ((float) Math.PI / 180.0f);
        // 玩家右手方向单位向量: (-cos(yaw), -sin(yaw))
        double rightX = -Mth.cos(bodyYawRad);
        double rightZ = -Mth.sin(bodyYawRad);
        double lateralOffset = dx * rightX + dz * rightZ;

        // 1) 头部与上颈区域 (relY >= 0.78)
        if (relY >= 0.78) {
            return MedicalData.Limb.HEAD;
        }

        // 2) 下肢区域 (relY < 0.38)
        if (relY < 0.38) {
            return lateralOffset >= 0.0 ? MedicalData.Limb.RIGHT_LEG : MedicalData.Limb.LEFT_LEG;
        }

        // 3) 躯干与双臂区域 (0.38 <= relY < 0.78)
        if (lateralOffset > 0.145) {
            return MedicalData.Limb.RIGHT_ARM;
        } else if (lateralOffset < -0.145) {
            return MedicalData.Limb.LEFT_ARM;
        }

        // 正面胸前持握武器时，正中偏外侧弹道有 22% 概率先击中前伸的手臂
        if (Math.abs(lateralOffset) > 0.08 && player.getRandom().nextFloat() < 0.22f) {
            return lateralOffset >= 0.0 ? MedicalData.Limb.RIGHT_ARM : MedicalData.Limb.LEFT_ARM;
        }

        return MedicalData.Limb.CHEST;
    }

    /**
     * 结合全身护甲值与受击部位实际穿戴的防具（头盔/胸甲/护腿/靴子）计算局部有效护甲值
     */
    private static float getEffectiveLimbArmor(ServerPlayer player, MedicalData.Limb limb) {
        float totalArmor = (float) player.getArmorValue();
        EquipmentSlot primarySlot = switch (limb) {
            case HEAD -> EquipmentSlot.HEAD;
            case CHEST, LEFT_ARM, RIGHT_ARM -> EquipmentSlot.CHEST;
            case LEFT_LEG, RIGHT_LEG -> EquipmentSlot.LEGS;
        };

        ItemStack slotStack = player.getItemBySlot(primarySlot);
        if (slotStack.isEmpty()) {
            // 若该部位未穿戴对应防具（如只穿胸甲但未戴头盔被爆头），头部护甲严格为 0，其余部位大幅衰减
            return limb == MedicalData.Limb.HEAD ? 0.0f : totalArmor * 0.25f;
        }
        if (slotStack.getItem() instanceof ArmorItem armorItem) {
            float pieceDefense = armorItem.getDefense();
            if (limb == MedicalData.Limb.HEAD) {
                // 头部护甲仅取决于头盔自身防御力，防止穿重型胸甲却戴薄帽时虚高头部防弹能力
                return pieceDefense * 3.0f;
            }
            return Math.max(totalArmor * 0.65f, pieceDefense * 2.6f);
        }
        return limb == MedicalData.Limb.HEAD ? 0.0f : totalArmor;
    }

    private static boolean isRayHitHeadFallback(ServerPlayer player, Entity projectile) {
        if (projectile == null) return false;
        double relY = (projectile.getY() - player.getY()) / Math.max(0.5, player.getBbHeight());
        return relY >= 0.78;
    }

    // =========================================================================
    // 5. TaCZ 枪械、口径与弹道参数解析器 (TimelessAPI + EntityKineticBullet + CustomData)
    // =========================================================================

    private static GunBallisticMeta resolveGunBallisticMeta(ResourceLocation gunId,
                                                            Entity bullet,
                                                            LivingEntity attacker) {
        GunBallisticMeta meta = new GunBallisticMeta();
        meta.gunId = gunId;

        // 1) 若事件未直接给出 gunId，尝试从射手主手枪械的 1.21.1 CustomData 组件读取 GunId
        if (meta.gunId == null && attacker != null) {
            ItemStack held = attacker.getMainHandItem();
            meta.gunId = extractGunIdFromStack(held);
        }

        // 2) 从 EntityKineticBullet 实体直接读取运行时 armorIgnore（已含枪口/弹药配件修正）
        if (bullet != null) {
            Float bulletAp = extractFloatField(bullet, "armorIgnore");
            if (bulletAp != null) {
                meta.armorIgnore = Mth.clamp(bulletAp, 0.0f, 1.0f);
            }
            if (meta.gunId == null) {
                Object bGunId = getFieldVal(bullet, "gunId");
                if (bGunId instanceof ResourceLocation rl) {
                    meta.gunId = rl;
                }
            }
        }

        // 3) 通过 TimelessAPI.getCommonGunIndex(gunId) 读取枪械类别 (gunType)、弹药 ID (ammoId)、弹丸数与基础穿甲率
        if (meta.gunId != null && mGetCommonGunIndex != null) {
            try {
                Object optObj = mGetCommonGunIndex.invoke(null, meta.gunId);
                if (optObj instanceof Optional<?> opt && opt.isPresent()) {
                    Object gunIndex = opt.get();
                    Object typeObj = invokeNoArg(gunIndex, "getType");
                    if (typeObj instanceof String s) {
                        meta.gunType = s.toLowerCase();
                    }
                    Object gunData = invokeNoArg(gunIndex, "getGunData");
                    if (gunData != null) {
                        Object ammoObj = invokeNoArg(gunData, "getAmmoId");
                        if (ammoObj instanceof ResourceLocation ammoRl) {
                            meta.ammoId = ammoRl;
                        }
                        Object bulletData = invokeNoArg(gunData, "getBulletData");
                        if (bulletData != null) {
                            Object amtObj = invokeNoArg(bulletData, "getBulletAmount");
                            if (amtObj instanceof Number n) {
                                meta.pelletCount = Math.max(1, n.intValue());
                            }
                            Object extraDmg = invokeNoArg(bulletData, "getExtraDamage");
                            if (extraDmg != null && bullet == null) {
                                Object apObj = invokeNoArg(extraDmg, "getArmorIgnore");
                                if (apObj instanceof Number n) {
                                    meta.armorIgnore = Mth.clamp(n.floatValue(), 0.0f, 1.0f);
                                }
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        // 4) 综合 gunType、ammoId 与 gunId 路径判定六大口径创伤模型
        meta.profile = classifyBallisticProfile(meta.gunType, meta.gunId, meta.ammoId, meta.pelletCount);
        return meta;
    }

    private static BallisticProfile classifyBallisticProfile(String gunType,
                                                             ResourceLocation gunId,
                                                             ResourceLocation ammoId,
                                                             int pelletCount) {
        String type = gunType != null ? gunType.toLowerCase() : "";
        String gunPath = gunId != null ? gunId.getPath().toLowerCase() : "";
        String ammoPath = ammoId != null ? ammoId.getPath().toLowerCase() : "";

        // 1) 爆炸物 / 火箭筒 / 榴弹发射器
        if (type.contains("rpg") || type.contains("heavy") || type.contains("launcher")
                || gunPath.contains("rpg") || gunPath.contains("m320") || gunPath.contains("mgl")
                || ammoPath.contains("rpg") || ammoPath.contains("rocket") || ammoPath.contains("40mm") || ammoPath.contains("grenade")) {
            return BallisticProfile.EXPLOSIVE_LAUNCHER;
        }

        // 2) 霰弹枪 / 多弹丸鹿弹
        if (pelletCount > 1 || type.contains("shotgun")
                || ammoPath.contains("12g") || gunPath.contains("m870") || gunPath.contains("aa12") || gunPath.contains("db_")) {
            return BallisticProfile.SHOTGUN;
        }

        // 3) 大口径特种 / 反器材狙击弹 (.50 BMG / .338 Lapua / 12.7x108mm / 反器材步枪)
        if (ammoPath.contains("50bmg") || ammoPath.contains("338") || ammoPath.contains("127x")
                || ammoPath.contains("145x") || ammoPath.contains("408")
                || gunPath.contains("m95") || gunPath.contains("m107") || gunPath.contains("barrett")
                || gunPath.contains("awp") || gunPath.contains("awm") || gunPath.contains("ntw")
                || type.contains("sniper")) {
            return BallisticProfile.HEAVY_SNIPER_AMR;
        }

        // 4) 手枪 / 冲锋枪 / 自卫弹药 (9mm / .45 ACP / .357 / 5.7x28 / 4.6x30)
        if (type.contains("pistol") || type.contains("smg")
                || ammoPath.contains("9mm") || ammoPath.contains("45acp") || ammoPath.contains("357")
                || ammoPath.contains("57x28") || ammoPath.contains("46x30") || ammoPath.contains("50ae")) {
            return BallisticProfile.PISTOL_SMG;
        }

        // 5) 默认：突击步枪 / 精确射手步枪 / 轻重机枪 (5.56x45 / 7.62x39 / 7.62x51 / .308 / 6.8x51)
        return BallisticProfile.RIFLE_MG;
    }

    private static ResourceLocation extractGunIdFromStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        try {
            CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
            if (customData != null) {
                CompoundTag tag = customData.copyTag();
                if (tag.contains("GunId", 8)) {
                    return ResourceLocation.tryParse(tag.getString("GunId"));
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static boolean isTaczDamageSource(DamageSource source) {
        if (source == null) return false;
        String msgId = source.getMsgId() != null ? source.getMsgId().toLowerCase() : "";
        if (msgId.contains("tacz") || msgId.startsWith("tac.")) {
            return true;
        }
        Entity direct = source.getDirectEntity();
        if (direct != null) {
            String clsName = direct.getClass().getName().toLowerCase();
            if (clsName.contains("tacz")) {
                return true;
            }
        }
        return false;
    }

    // =========================================================================
    // 6. 反射安全辅助工具方法
    // =========================================================================

    private static Method findMethod(Class<?> baseCls, Class<?> subCls, String... names) {
        for (String name : names) {
            if (subCls != null) {
                try {
                    Method m = subCls.getMethod(name);
                    m.setAccessible(true);
                    return m;
                } catch (Throwable ignored) {
                }
            }
            if (baseCls != null) {
                try {
                    Method m = baseCls.getMethod(name);
                    m.setAccessible(true);
                    return m;
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    private static Object invokeNoArg(Object target, String methodName) {
        if (target == null) return null;
        try {
            Method m = target.getClass().getMethod(methodName);
            m.setAccessible(true);
            return m.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static float invokeFloat(Object target, Method m, String fallbackField, float defaultVal) {
        if (target == null) return defaultVal;
        try {
            if (m != null) {
                Object res = m.invoke(target);
                if (res instanceof Number n) return n.floatValue();
            }
        } catch (Throwable ignored) {
        }
        Float f = extractFloatField(target, fallbackField);
        return f != null ? f : defaultVal;
    }

    private static boolean invokeBoolean(Object target, Method m, String fallbackField, boolean defaultVal) {
        if (target == null) return defaultVal;
        try {
            if (m != null) {
                Object res = m.invoke(target);
                if (res instanceof Boolean b) return b;
            }
        } catch (Throwable ignored) {
        }
        Object fVal = getFieldVal(target, fallbackField);
        return (fVal instanceof Boolean b) ? b : defaultVal;
    }

    private static Float extractFloatField(Object target, String fieldName) {
        Object val = getFieldVal(target, fieldName);
        return (val instanceof Number n) ? n.floatValue() : null;
    }

    private static Object getFieldVal(Object target, String fieldName) {
        if (target == null || fieldName == null) return null;
        Class<?> c = target.getClass();
        while (c != null && c != Object.class) {
            try {
                Field f = c.getDeclaredField(fieldName);
                f.setAccessible(true);
                return f.get(target);
            } catch (Throwable ignored) {
                c = c.getSuperclass();
            }
        }
        return null;
    }
}