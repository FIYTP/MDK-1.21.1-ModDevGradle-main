package com.FIYTP.realmedicalmod.compat;

import com.FIYTP.realmedicalmod.RealMedicalMod;
import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.capability.MedicalDataCapability;
import com.FIYTP.realmedicalmod.config.MedicalConfig;
import com.FIYTP.realmedicalmod.treatment.ModItems;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [TaCZ] 永恒枪械工坊：零 (Timeless and Classics Zero) 独立受伤射击操控反馈系统
 *
 * 设计原则：
 * 1. 完全独立于原有业务代码，放置于 {@code compat/TaczShootingFeedbackHandler.java}，零侵入、杜绝史山代码。
 * 2. 原生对接 TaCZ 官方 API（{@code GunShootEvent}、{@code GunFireEvent}、{@code GunReloadEvent}、{@code GunDrawEvent}、
 *    {@code IGunOperator}、{@code IClientPlayerGunOperator}、{@code TimelessAPI}）：
 *    - 【真实准星晃动与生理手震 (Aim Sway & Physiological Tremor)】：
 *      * 双臂疼痛、未固定骨折/脱臼、骨筋膜室综合征、动脉结扎、坏死与截肢实时转化为非对称李萨如手腕震颤与枪口下沉；
 *      * 气胸、血胸、肺挫伤与低血氧转化为剧烈的垂直呼吸起伏 (Respiratory Heave)，无法稳定屏息瞄准；
 *      * 颅内出血、脑损伤与低意识转化为前庭眩晕漂移，阿片戒断转化为高频戒断手抖，肾上腺素激增可临时压制部分疼痛手抖。
 *    - 【举枪/开镜速度压制与脱力打断 (ADS Speed Throttling & Exhaustion De-scope)】：
 *      * 直接通过反射调控 TaCZ 客户端与服务端的 {@code aimingProgress} 推进速率，并叠加镜头举枪沉坠与 FOV 阻尼：
 *        手臂受伤/骨折时举枪开镜速度降低 30%~75%，重型狙击枪/机枪惩罚远高于轻型手枪；
 *      * 单臂截肢或双臂开放性骨折时：无法端起反器材重狙/机枪/发射器开镜（强制退回腰射），端步枪开镜超过耐力极限后手臂力竭脱力自动抖落退出开镜；
 *      * 双臂皆断或昏迷/心脏骤停时：直接拦截开火与举枪。
 *    - 【开火后坐力失控放大、换弹减速与服务端真实弹道散布 (Recoil Amplification, Reload Slowdown & Server Trajectory Dispersion)】：
 *      * 手臂带伤/骨折/单手射击时，每次开火额外叠加与伤情成正比的枪口垂直上跳与伤侧横向甩动；
 *      * 服务端在 {@code EntityKineticBullet} 生成瞬间根据射手伤情施加真实三维弹道角散布偏转，确保多人联机同步生效；
 *      * 换弹与切枪拔枪时间戳随双臂伤情动态延展，重度骨折下强行发射大口径武器会震伤伤臂加剧疼痛与渗血。
 */
@EventBusSubscriber(modid = RealMedicalMod.MODID)
public final class TaczShootingFeedbackHandler {

    /** 独立内置枪械弹道分类枚举（零跨包耦合，无论 TaczDamageCompatHandler 在 campat 还是 compat 均 100% 编译通过） */
    public enum BallisticProfile {
        PISTOL_SMG,
        RIFLE_MG,
        HEAVY_SNIPER_AMR,
        SHOTGUN,
        EXPLOSIVE_LAUNCHER
    }

    private static final String TACZ_MOD_ID = "tacz";
    private static final String EVENT_GUN_SHOOT = "com.tacz.guns.api.event.common.GunShootEvent";
    private static final String EVENT_GUN_FIRE = "com.tacz.guns.api.event.common.GunFireEvent";
    private static final String EVENT_GUN_RELOAD = "com.tacz.guns.api.event.common.GunReloadEvent";
    private static final String EVENT_GUN_DRAW = "com.tacz.guns.api.event.common.GunDrawEvent";
    private static final String IGUN_OPERATOR_CLASS = "com.tacz.guns.api.entity.IGunOperator";
    private static final String ICLIENT_GUN_OPERATOR_CLASS = "com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator";
    private static final String TIMELESS_API_CLASS = "com.tacz.guns.api.TimelessAPI";

    private static volatile boolean taczShootEventsInitialized = false;

    // 反射缓存：事件读取
    private static Method mShootGetShooter;
    private static Method mShootGetGunItemStack;
    private static Method mShootGetLogicalSide;
    private static Method mFromLivingEntity;
    private static Method mGetCommonGunIndex;

    // 服务端每名玩家的持枪状态与换弹/切枪降速追踪
    private static final Map<UUID, ServerShooterState> SERVER_SHOOTER_STATES = new ConcurrentHashMap<>();

    private static final class ServerShooterState {
        long lastShotGameTime = -100L;
        int rapidFireCount = 0;
        long lastReloadThrottleTick = -10L;
        long lastDrawThrottleTick = -10L;
    }

    private TaczShootingFeedbackHandler() {
    }

    // =========================================================================
    // 1. 武器操控与伤情耦合评估模型 (Weapon Handling Injury Profile)
    // =========================================================================

    /**
     * 综合计算玩家当前全身与双臂伤情对枪械操控的各项惩罚系数
     */
    public static final class HandlingPenaltyProfile {
        /** 举枪/开镜速度倍率 (0.15 ~ 1.0，越小举枪越慢) */
        public float adsSpeedMultiplier = 1.0f;
        /** 换弹与拔枪速度倍率 (0.25 ~ 1.0，越小换弹越慢) */
        public float reloadSpeedMultiplier = 1.0f;
        /** 水平准星晃动幅度 (度) */
        public float horizontalSwayDeg = 0.0f;
        /** 垂直准星晃动幅度 (度，含呼吸起伏) */
        public float verticalSwayDeg = 0.0f;
        /** 高频手部生理震颤幅度 (度，骨折/筋膜室高压/戒断/剧痛) */
        public float highFreqTremorDeg = 0.0f;
        /** 持枪歪斜角 (度，左右手臂伤势不对称引起的镜头微倾) */
        public float weaponCantRollDeg = 0.0f;
        /** 开火垂直后坐力放大倍率 (1.0 ~ 2.8) */
        public float verticalRecoilMult = 1.0f;
        /** 开火横向后坐力偏摆倍率 (-1.5 ~ +1.5，正数向右甩，负数向左甩) */
        public float horizontalRecoilBias = 0.0f;
        /** 服务端真实子弹角散布附加值 (度) */
        public float extraSpreadDeg = 0.0f;
        /** 是否完全禁止开火与举枪（双臂截肢 / 昏迷 / 心脏骤停） */
        public boolean blockFireAndAim = false;
        /** 是否禁止精确开镜 ADS（如单臂截肢或双臂重度未固定骨折时端重型反器材/机枪） */
        public boolean blockAds = false;
        /** 最大可持续开镜秒数（手臂带伤端长枪超过该时间会肌肉力竭自动脱力退镜，<=0 表示无限制） */
        public float maxHoldAdsSeconds = 0.0f;
        /** 综合失稳严重度 (0.0 ~ 1.0，用于驱动动态准星扩散游标) */
        public float overallInstability = 0.0f;
    }

    public static HandlingPenaltyProfile evaluateHandlingProfile(Player player,
                                                                 MedicalData data,
                                                                 BallisticProfile gunProfile) {
        HandlingPenaltyProfile out = new HandlingPenaltyProfile();
        if (player == null || data == null) return out;

        // 1) 意识丧失、心脏骤停或双臂全部截肢 -> 完全无法扣动扳机或举枪
        MedicalData.LimbData leftArm = data.getLimb(MedicalData.Limb.LEFT_ARM);
        MedicalData.LimbData rightArm = data.getLimb(MedicalData.Limb.RIGHT_ARM);
        MedicalData.LimbData chest = data.getLimb(MedicalData.Limb.CHEST);
        MedicalData.LimbData head = data.getLimb(MedicalData.Limb.HEAD);

        boolean leftAmp = leftArm != null && leftArm.isAmputated();
        boolean rightAmp = rightArm != null && rightArm.isAmputated();

        if (data.isCardiacArrest() || data.getConsciousness() <= 0 || (leftAmp && rightAmp)) {
            out.blockFireAndAim = true;
            out.blockAds = true;
            out.adsSpeedMultiplier = 0.10f;
            out.reloadSpeedMultiplier = 0.15f;
            out.overallInstability = 1.0f;
            return out;
        }

        // 枪械重量系数：反器材重狙/火箭筒 > 步枪/机枪 > 霰弹枪 > 手枪/冲锋枪
        float gunWeightFactor = switch (gunProfile) {
            case HEAVY_SNIPER_AMR, EXPLOSIVE_LAUNCHER -> 1.65f;
            case RIFLE_MG -> 1.15f;
            case SHOTGUN -> 1.05f;
            case PISTOL_SMG -> 0.60f;
        };

        // 肾上腺素镇痛抗抖系数（肾上腺素激增期间临时抵消高达 45% 的疼痛手抖与举枪迟缓）
        float adrLevel = ModItems.getAdrenalineLevel(data);
        float adrRelief = (adrLevel > 0.0f) ? Mth.clamp(adrLevel / 85.0f, 0.0f, 0.45f) : 0.0f;

        // 2) 评估左右双臂各自的功能损伤度 (0.0 = 完好, 1.0+ = 严重受损)
        float leftImpairment = computeArmImpairment(leftArm, adrRelief);
        float rightImpairment = computeArmImpairment(rightArm, adrRelief);
        float combinedArmImpairment = (leftImpairment + rightImpairment) * 0.65f;

        boolean leftUnfixedFrac = leftArm != null && !leftAmp && leftArm.hasFracture()
                && !(leftArm.hasSplint() || ModItems.isLimbOrthopedicFixed(leftArm));
        boolean rightUnfixedFrac = rightArm != null && !rightAmp && rightArm.hasFracture()
                && !(rightArm.hasSplint() || ModItems.isLimbOrthopedicFixed(rightArm));

        // 3) 单臂截肢或双臂未固定骨折时的开镜限制与耐力上限
        boolean oneArmAmputated = leftAmp ^ rightAmp;
        if (oneArmAmputated) {
            if (gunProfile == BallisticProfile.HEAVY_SNIPER_AMR
                    || gunProfile == BallisticProfile.EXPLOSIVE_LAUNCHER) {
                // 单手无法端起 .50 BMG 反器材重狙或火箭筒进行精确开镜瞄准
                out.blockAds = true;
            } else if (gunProfile != BallisticProfile.PISTOL_SMG) {
                // 单手端步枪/霰弹枪最多只能勉强支撑 2.8 秒即肌肉力竭退镜
                out.maxHoldAdsSeconds = 2.8f;
            }
        } else if (leftUnfixedFrac && rightUnfixedFrac) {
            if (gunProfile == BallisticProfile.HEAVY_SNIPER_AMR) {
                out.blockAds = true;
            } else if (gunProfile != BallisticProfile.PISTOL_SMG) {
                out.maxHoldAdsSeconds = 3.5f;
            }
        } else if ((leftUnfixedFrac || rightUnfixedFrac) && gunWeightFactor >= 1.0f) {
            out.maxHoldAdsSeconds = 5.2f / gunWeightFactor;
        }

        // 4) 计算举枪开镜速度倍率 (adsSpeedMultiplier) 与换弹速度倍率 (reloadSpeedMultiplier)
        //    同时采样全身最高单部位疼痛 (maxLimbPainRatio)，确保中弹瞬间即使全局体感疼痛尚未缓慢爬升完毕，也会立刻触发明显的慢开镜与剧烈晃动！
        float maxLimbPain = 0.0f;
        int totalBleeding = 0;
        for (MedicalData.Limb l : MedicalData.Limb.values()) {
            MedicalData.LimbData ld = data.getLimb(l);
            if (ld != null) {
                if (ld.getPainLevel() > maxLimbPain) maxLimbPain = ld.getPainLevel();
                totalBleeding += ld.getBleeding() + ld.getInternalBleedingRate();
            }
        }
        float maxLimbPainRatio = Mth.clamp(maxLimbPain / Math.max(1.0f, (float) ModItems.safeCfgDouble(MedicalConfig.MAX_PAIN_LEVEL, 10.0)), 0.0f, 1.0f);
        float sysPainRatio = Mth.clamp(data.getPerceivedPain() / Math.max(10.0f, (float) ModItems.safeCfgDouble(MedicalConfig.MAX_PERCEIVED_PAIN, 100.0)), 0.0f, 1.0f);
        float painRatio = Mth.clamp(Math.max(sysPainRatio, maxLimbPainRatio * 0.85f), 0.0f, 1.0f);
        float consDeficit = Mth.clamp((100.0f - data.getConsciousnessFloat()) / 100.0f, 0.0f, 1.0f);
        float bleedShockRatio = Mth.clamp(totalBleeding / 600.0f, 0.0f, 1.0f);

        float adsSlowStrength = (float) ModItems.safeCfgDouble(MedicalConfig.TACZ_INJURY_ADS_SLOWDOWN_STRENGTH, 2.4);
        float swayAmpMult = (float) ModItems.safeCfgDouble(MedicalConfig.TACZ_INJURY_SWAY_AMPLITUDE_MULTIPLIER, 4.2);

        float rawAdsPenalty = (combinedArmImpairment * 0.65f
                + painRatio * 0.52f * (1.0f - adrRelief * 0.6f)
                + consDeficit * 0.45f
                + bleedShockRatio * 0.25f) * gunWeightFactor * (adsSlowStrength / 1.8f);
        if (oneArmAmputated) {
            rawAdsPenalty += (gunProfile == BallisticProfile.PISTOL_SMG) ? 0.45f : 0.75f;
        }
        // 使用非线性衰减让带伤开镜明显变慢（重伤/剧痛时 adsSpeedMultiplier 低至 0.08 ~ 0.22，开镜耗时延长 4~10 倍）
        float adrAdsBoost = (adrLevel >= 20.0f && combinedArmImpairment < 0.20f && painRatio < 0.25f) ? 0.10f : 0.0f;
        out.adsSpeedMultiplier = Mth.clamp((1.0f + adrAdsBoost) / (1.0f + rawAdsPenalty * 2.6f), 0.08f, 1.10f);

        float reloadPenalty = combinedArmImpairment * 0.62f + painRatio * 0.32f + consDeficit * 0.30f + (oneArmAmputated ? 0.45f : 0.0f);
        out.reloadSpeedMultiplier = Mth.clamp(1.0f - reloadPenalty, 0.20f, 1.0f);

        // 5) 计算呼吸起伏、手腕失稳与高频生理震颤 (Aim Sway & Tremor —— 大幅增强版)
        int pType = ModItems.getPneumothoraxType(data);
        int hemoLv = data.getHemothoraxLevel();
        float contusion = data.getLungContusion();
        float spo2Deficit = Mth.clamp((95.0f - data.getBloodOxygenFloat()) / 35.0f, 0.0f, 1.0f);
        float chestPainNorm = (chest != null) ? (chest.getPainLevel() / 10.0f) : 0.0f;

        // 胸腔与缺氧呼吸起伏幅度
        float respDistress = chestPainNorm * 0.55f
                + (pType > 0 ? (0.55f + pType * 0.35f) : 0.0f)
                + hemoLv * 0.35f
                + (contusion / 100.0f) * 0.60f
                + spo2Deficit * 0.85f;

        // 头部伤情与眩晕漂移
        float headPainNorm = (head != null) ? (head.getPainLevel() / 10.0f) : 0.0f;
        float brainDeficit = Mth.clamp((100.0f - data.getBrainHealthFloat()) / 80.0f, 0.0f, 1.0f);
        int icLv = (data.getIntracranialOnsetTimer() >= ModItems.safeCfgInt(MedicalConfig.ONSET_DELAY, 60)) ? data.getIntracranialLevel() : 0;
        float vestibularDrift = headPainNorm * 0.65f + brainDeficit * 0.85f + icLv * 0.42f + consDeficit * 0.75f;

        // 阿片戒断手抖
        float withdrawalTremor = 0.0f;
        if (ModItems.isInWithdrawalMovementRestriction(data)) {
            int wTier = ModItems.getAddictionTier(data.getAddictionLevel());
            withdrawalTremor = (wTier >= 3) ? 0.95f : 0.58f;
        }

        // 疼痛与手臂伤残引发大范围、深沉平滑的 8 字形/李萨如轨迹漂移（将原先刺眼的高频抽搐完全转化为大范围低频漂移幅度）
        float painSwayBase = painRatio * 0.95f * (1.0f - adrRelief * 0.55f) + bleedShockRatio * 0.45f;
        float extraDriftFromFracAndWithdrawal = (leftUnfixedFrac || rightUnfixedFrac ? 0.85f : 0.0f) + withdrawalTremor * 0.75f;

        out.horizontalSwayDeg = (combinedArmImpairment * 1.45f + painSwayBase * 1.25f + vestibularDrift * 1.10f + extraDriftFromFracAndWithdrawal * 0.90f)
                * gunWeightFactor * swayAmpMult;
        out.verticalSwayDeg = (combinedArmImpairment * 1.55f + painSwayBase * 1.35f + respDistress * 1.40f + vestibularDrift * 0.95f + extraDriftFromFracAndWithdrawal * 0.85f)
                * gunWeightFactor * swayAmpMult;
        // 彻底禁用高频抽搐分量，将其全部并入上方的平滑大范围低频游走
        out.highFreqTremorDeg = 0.0f;

        // 左右臂伤势差导致的枪身歪斜角 (Roll)
        out.weaponCantRollDeg = Mth.clamp((rightImpairment - leftImpairment) * 6.5f * gunWeightFactor, -12.0f, 12.0f);

        // 6) 开火后坐力失控放大与服务端真实散布增量
        out.verticalRecoilMult = Mth.clamp(1.0f + (combinedArmImpairment * 1.25f + painRatio * 0.65f) * gunWeightFactor + (oneArmAmputated ? 0.85f : 0.0f), 1.0f, 3.60f);
        out.horizontalRecoilBias = Mth.clamp((rightImpairment - leftImpairment) * 1.15f, -2.10f, 2.10f);
        out.extraSpreadDeg = Mth.clamp(
                (combinedArmImpairment * 3.4f + painRatio * 2.2f + respDistress * 1.45f + (oneArmAmputated ? 3.2f : 0.0f)) * gunWeightFactor,
                0.0f,
                14.5f
        );

        out.overallInstability = Mth.clamp(
                combinedArmImpairment * 0.55f + painRatio * 0.48f + respDistress * 0.28f + vestibularDrift * 0.32f,
                0.0f,
                1.0f
        );
        return out;
    }

    private static float computeArmImpairment(MedicalData.LimbData arm, float adrRelief) {
        if (arm == null) return 0.0f;
        if (arm.isAmputated()) {
            return 1.35f;
        }
        float painNorm = (arm.getPainLevel() / 10.0f) * (1.0f - adrRelief * 0.7f);
        float imp = painNorm * 0.42f;

        if (arm.hasFracture()) {
            boolean fixed = arm.hasSplint() || ModItems.isLimbOrthopedicFixed(arm);
            imp += fixed ? 0.24f : (arm.isOpenFracture() ? 0.78f : 0.62f);
        }
        if (arm.hasDislocation()) {
            imp += 0.45f;
        }
        if (ModItems.isCompartmentSyndrome(arm)) {
            imp += 0.48f;
        }
        if (ModItems.isLimbArterialLigated(arm)) {
            imp += 0.30f;
        }
        if (ModItems.isLimbNecrotic(arm)) {
            imp += 0.85f;
        }
        return Mth.clamp(imp, 0.0f, 1.50f);
    }

    // =========================================================================
    // 2. 服务端 TaCZ 事件挂载与真实弹道散布/换弹降速/后坐力震伤处理
    // =========================================================================

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!taczShootEventsInitialized) {
            ensureTaczShootEventsRegistered();
        }

        // 当 TaCZ 子弹实体 (EntityKineticBullet) 在服务端生成时，根据射手手臂/全身伤情施加真实三维弹道散布偏转
        Entity entity = event.getEntity();
        if (entity == null || entity.level().isClientSide()) return;
        if (!(entity instanceof Projectile projectile)) return;

        String clsName = entity.getClass().getName();
        if (!clsName.contains("tacz")) return;

        Entity owner = projectile.getOwner();
        if (!(owner instanceof ServerPlayer shooter) || !shooter.isAlive() || shooter.isCreative()) return;

        MedicalData data = shooter.getData(MedicalDataCapability.MEDICAL_DATA);
        if (data == null) return;

        ItemStack heldGun = shooter.getMainHandItem();
        BallisticProfile profile = resolveHeldGunProfile(heldGun);
        HandlingPenaltyProfile handling = evaluateHandlingProfile(shooter, data, profile);

        // 若处于双臂截肢/昏迷/骤停状态，直接拦截销毁非法射出的子弹
        if (handling.blockFireAndAim) {
            event.setCanceled(true);
            return;
        }

        ServerShooterState state = SERVER_SHOOTER_STATES.computeIfAbsent(shooter.getUUID(), k -> new ServerShooterState());
        long nowTick = shooter.level().getGameTime();
        if (nowTick - state.lastShotGameTime <= 6L) {
            state.rapidFireCount = Math.min(12, state.rapidFireCount + 1);
        } else {
            state.rapidFireCount = 0;
        }
        state.lastShotGameTime = nowTick;

        // 1) 计算带伤射击 + 连发失控的真实服务端弹道偏角
        float totalSpreadDeg = handling.extraSpreadDeg * (1.0f + state.rapidFireCount * 0.14f);
        // 若单臂截肢或重伤被禁止开镜却强行腰射重武器，附加重度盲射散布
        if (handling.blockAds) {
            totalSpreadDeg += 4.5f;
        }

        if (totalSpreadDeg >= 0.15f) {
            Vec3 vel = entity.getDeltaMovement();
            double speed = vel.length();
            if (speed > 0.05) {
                float yawJitterRad = (shooter.getRandom().nextFloat() - 0.5f) * 2.0f * totalSpreadDeg * ((float) Math.PI / 180.0f);
                float pitchJitterRad = (shooter.getRandom().nextFloat() - 0.5f) * 2.0f * totalSpreadDeg * ((float) Math.PI / 180.0f);
                // 连发时若手臂无力，子弹自然向上漂移（压不住枪口）
                if (state.rapidFireCount >= 2 && handling.verticalRecoilMult > 1.15f) {
                    pitchJitterRad += state.rapidFireCount * 0.22f * (handling.verticalRecoilMult - 1.0f) * ((float) Math.PI / 180.0f);
                }

                Vec3 dir = vel.normalize();
                Vec3 rotated = dir.xRot(pitchJitterRad).yRot(yawJitterRad).normalize().scale(speed);
                entity.setDeltaMovement(rotated);
                entity.hasImpulse = true;
            }
        }

        // 2) 重武器带伤强行开火的反冲二次创伤：
        //    当手臂存在未固定骨折或脱臼时，强行发射反器材重狙/霰弹/大威力步枪会震伤伤臂
        if (profile == BallisticProfile.HEAVY_SNIPER_AMR
                || profile == BallisticProfile.SHOTGUN
                || profile == BallisticProfile.EXPLOSIVE_LAUNCHER) {
            aggravateWoundedArmsFromHeavyRecoil(shooter, data, profile);
        }
    }

    @SubscribeEvent
    public static void onServerPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide()) return;
        if (!player.isAlive() || player.isCreative() || player.isSpectator()) return;

        ItemStack held = player.getMainHandItem();
        if (!isTaczGunStack(held)) return;

        MedicalData data = player.getData(MedicalDataCapability.MEDICAL_DATA);
        if (data == null) return;

        BallisticProfile profile = resolveHeldGunProfile(held);
        HandlingPenaltyProfile handling = evaluateHandlingProfile(player, data, profile);

        Object operator = getServerGunOperator(player);
        if (operator == null) return;

        // 1) 若玩家因重伤昏迷、双臂截肢或单臂端重狙被禁止开镜，在服务端同步强制退出开镜状态
        if (handling.blockFireAndAim || handling.blockAds) {
            invokeAim(operator, false);
        }

        // 2) 服务端换弹与切枪时间戳动态延展（当手臂带伤/骨折导致 reloadSpeedMultiplier < 0.90 时）
        if (handling.reloadSpeedMultiplier < 0.90f && player.tickCount % 2 == 0) {
            ServerShooterState st = SERVER_SHOOTER_STATES.computeIfAbsent(player.getUUID(), k -> new ServerShooterState());
            extendReloadAndDrawTimestamps(operator, handling.reloadSpeedMultiplier, player.level().getGameTime(), st);
        }
    }

    private static void aggravateWoundedArmsFromHeavyRecoil(ServerPlayer player,
                                                            MedicalData data,
                                                            BallisticProfile profile) {
        int maxPain = ModItems.safeCfgInt(MedicalConfig.MAX_PAIN_LEVEL, 10);
        boolean aggravated = false;

        for (MedicalData.Limb armLimb : new MedicalData.Limb[]{MedicalData.Limb.RIGHT_ARM, MedicalData.Limb.LEFT_ARM}) {
            MedicalData.LimbData arm = data.getLimb(armLimb);
            if (arm == null || arm.isAmputated()) continue;

            boolean unfixedFrac = arm.hasFracture() && !(arm.hasSplint() || ModItems.isLimbOrthopedicFixed(arm));
            if (unfixedFrac || arm.hasDislocation()) {
                float chance = (profile == BallisticProfile.HEAVY_SNIPER_AMR) ? 0.75f : 0.40f;
                if (player.getRandom().nextFloat() < chance) {
                    arm.setPainLevel(Math.min(maxPain, arm.getPainLevel() + 2));
                    if (arm.hasOpenWound()) {
                        arm.setBandaged(false);
                        arm.setBleeding(Math.min(2500, arm.getBleeding() + 35));
                    }
                    aggravated = true;
                }
            }
        }

        if (aggravated) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BONE_BLOCK_HIT, SoundSource.PLAYERS, 0.85f, 0.65f);
            player.syncData(MedicalDataCapability.MEDICAL_DATA);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static synchronized void ensureTaczShootEventsRegistered() {
        if (taczShootEventsInitialized) return;
        taczShootEventsInitialized = true;

        if (!ModList.get().isLoaded(TACZ_MOD_ID)) return;

        ClassLoader cl = TaczShootingFeedbackHandler.class.getClassLoader();
        try {
            Class<?> opCls = Class.forName(IGUN_OPERATOR_CLASS, false, cl);
            mFromLivingEntity = opCls.getMethod("fromLivingEntity", LivingEntity.class);
        } catch (Throwable ignored) {
        }

        try {
            Class<?> timelessCls = Class.forName(TIMELESS_API_CLASS, false, cl);
            mGetCommonGunIndex = timelessCls.getMethod("getCommonGunIndex", ResourceLocation.class);
        } catch (Throwable ignored) {
        }

        for (String evClassName : new String[]{EVENT_GUN_SHOOT, EVENT_GUN_FIRE}) {
            try {
                Class<? extends Event> evCls = (Class<? extends Event>) Class.forName(evClassName, false, cl);
                if (mShootGetShooter == null) {
                    mShootGetShooter = findNoArgMethod(evCls, "getShooter", "getEntity", "getLivingEntity");
                    mShootGetGunItemStack = findNoArgMethod(evCls, "getGunItemStack", "getStack");
                    mShootGetLogicalSide = findNoArgMethod(evCls, "getLogicalSide");
                }
                NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, false, (Class) evCls, TaczShootingFeedbackHandler::onTaczGunShootEvent);
            } catch (Throwable ignored) {
            }
        }

        for (String evClassName : new String[]{EVENT_GUN_RELOAD, EVENT_GUN_DRAW}) {
            try {
                Class<? extends Event> evCls = (Class<? extends Event>) Class.forName(evClassName, false, cl);
                NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, false, (Class) evCls, TaczShootingFeedbackHandler::onTaczGunActionEvent);
            } catch (Throwable ignored) {
            }
        }
    }

    private static void onTaczGunShootEvent(Event event) {
        if (event == null) return;
        try {
            Object shooterObj = mShootGetShooter != null ? mShootGetShooter.invoke(event) : getFieldVal(event, "shooter");
            if (!(shooterObj instanceof Player player)) return;

            MedicalData data = player.getData(MedicalDataCapability.MEDICAL_DATA);
            if (data == null) return;

            ItemStack gunStack = null;
            if (mShootGetGunItemStack != null) {
                Object stObj = mShootGetGunItemStack.invoke(event);
                if (stObj instanceof ItemStack s) gunStack = s;
            }
            if (gunStack == null || gunStack.isEmpty()) {
                gunStack = player.getMainHandItem();
            }

            BallisticProfile profile = resolveHeldGunProfile(gunStack);
            HandlingPenaltyProfile handling = evaluateHandlingProfile(player, data, profile);

            // 若双臂皆断或处于昏迷/心脏骤停，直接取消开火事件
            if (handling.blockFireAndAim) {
                if (event instanceof net.neoforged.bus.api.ICancellableEvent cancellable) {
                    cancellable.setCanceled(true);
                }
                return;
            }

            // 若在客户端侧触发开火事件，立即向客户端镜头与准星注入受伤后坐力上跳冲击
            if (player.level().isClientSide()) {
                ClientHandler.notifyClientGunShot(profile, handling);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void onTaczGunActionEvent(Event event) {
        if (event == null) return;
        try {
            Object entityObj = getFieldVal(event, "entity");
            if (entityObj == null) entityObj = getFieldVal(event, "shooter");
            if (entityObj == null) entityObj = getFieldVal(event, "livingEntity");
            if (!(entityObj instanceof Player player)) return;

            MedicalData data = player.getData(MedicalDataCapability.MEDICAL_DATA);
            if (data == null) return;

            if (data.isCardiacArrest() || data.getConsciousness() <= 0) {
                if (event instanceof net.neoforged.bus.api.ICancellableEvent cancellable) {
                    cancellable.setCanceled(true);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    // =========================================================================
    // 3. 客户端 3A 准星晃动、举枪速度阻尼、开火后坐力上跳与动态准星扩散游标 (Dist.CLIENT)
    // =========================================================================

    @EventBusSubscriber(modid = RealMedicalMod.MODID, value = Dist.CLIENT)
    public static final class ClientHandler {

        private static Method mFromLocalPlayer;
        private static Method mClientIsAim;
        private static Method mClientAim;
        private static Method mClientGetAimingProgress;
        private static boolean clientReflectionChecked = false;

        // 连续平滑状态
        private static long lastFrameNs = 0L;
        private static float swayPhase = 0.0f;
        private static float breathPhase = 0.0f;
        private static float tremorPhase = 0.0f;

        // 举枪开镜 (ADS) 速度阻尼与耐力追踪
        private static float lastInterceptedAimProgress = 0.0f;
        private static float smoothVisualAdsProgress = 0.0f;
        private static float adsHoldSeconds = 0.0f;
        private static int adsExhaustionCooldownTicks = 0;
        private static int lastRecordedPain = -1;

        // 开火后坐力失控冲击包络
        private static float recoilPitchVelocity = 0.0f;
        private static float recoilYawVelocity = 0.0f;
        private static float recoilRollKick = 0.0f;
        private static float crosshairBloomSpread = 0.0f;

        // 二阶临界阻尼低通滤波状态与上一帧施加的平滑漂移角度（彻底消除任何帧间突变与高频抽搐）
        private static float stage1SwayYaw = 0.0f;
        private static float stage1SwayPitch = 0.0f;
        private static float smoothSwayYaw = 0.0f;
        private static float smoothSwayPitch = 0.0f;
        private static float prevAppliedSwayYaw = 0.0f;
        private static float prevAppliedSwayPitch = 0.0f;

        private ClientHandler() {
        }

        static void notifyClientGunShot(BallisticProfile profile,
                                         HandlingPenaltyProfile handling) {
            float baseKick = switch (profile) {
                case HEAVY_SNIPER_AMR, EXPLOSIVE_LAUNCHER -> 3.4f;
                case SHOTGUN -> 2.4f;
                case RIFLE_MG -> 1.15f;
                case PISTOL_SMG -> 0.68f;
            };
            // 仅对超出正常状态 (verticalRecoilMult > 1.02) 的受伤部分施加额外失控上跳与横向甩动
            float extraMult = Math.max(0.0f, handling.verticalRecoilMult - 1.0f);
            float randJitter = (float) (Math.random() - 0.5) * 0.8f;

            recoilPitchVelocity -= baseKick * (0.35f + extraMult * 1.45f);
            recoilYawVelocity += baseKick * (handling.horizontalRecoilBias * 0.85f + randJitter * extraMult);
            recoilRollKick += (handling.horizontalRecoilBias >= 0.0f ? 1.0f : -1.0f) * baseKick * (0.4f + extraMult * 1.2f);
            crosshairBloomSpread = Math.min(28.0f, crosshairBloomSpread + baseKick * (2.8f + extraMult * 4.5f));
        }

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            Player player = mc.player;
            if (player == null || mc.level == null || mc.isPaused()) return;

            ItemStack held = player.getMainHandItem();
            if (!isTaczGunStack(held)) {
                resetClientGunState();
                return;
            }

            MedicalData data = player.getData(MedicalDataCapability.MEDICAL_DATA);
            if (data == null) return;

            BallisticProfile profile = resolveHeldGunProfile(held);
            HandlingPenaltyProfile handling = evaluateHandlingProfile(player, data, profile);

            if (adsExhaustionCooldownTicks > 0) {
                adsExhaustionCooldownTicks--;
            }

            Object clientOp = getClientGunOperator(player);

            // 1) 受击剧痛打断举枪 (Aim Punch De-scope)：当开镜瞄准期间突然遭受重创（体感疼痛暴增 >= 14），直接震退开镜！
            int curPain = data.getPerceivedPain();
            if (lastRecordedPain >= 0 && (curPain - lastRecordedPain >= 14) && clientOp != null) {
                invokeClientAim(clientOp, false);
                adsExhaustionCooldownTicks = 25;
                recoilPitchVelocity -= 2.8f;
            }
            lastRecordedPain = curPain;

            // 2) 检查是否被禁止举枪或手臂肌肉力竭脱力
            if (clientOp != null) {
                boolean isAiming = isClientAiming(clientOp);
                if (handling.blockFireAndAim || handling.blockAds || adsExhaustionCooldownTicks > 0) {
                    if (isAiming) {
                        invokeClientAim(clientOp, false);
                    }
                    adsHoldSeconds = 0.0f;
                } else if (isAiming) {
                    adsHoldSeconds += 0.05f;
                    // 若手臂带伤端长枪超过最大支撑耐力秒数，手臂酸痛力竭，自动抖落退出开镜并进入 1.8 秒喘息冷却！
                    if (handling.maxHoldAdsSeconds > 0.0f && adsHoldSeconds >= handling.maxHoldAdsSeconds) {
                        invokeClientAim(clientOp, false);
                        adsHoldSeconds = 0.0f;
                        adsExhaustionCooldownTicks = 36;
                        recoilPitchVelocity += 2.2f; // 枪口因手臂脱力向下沉坠
                        player.playSound(SoundEvents.PLAYER_BREATH, 0.75f, 0.80f);
                    }
                } else {
                    adsHoldSeconds = Math.max(0.0f, adsHoldSeconds - 0.08f);
                }

                // 3) 在 ClientTick 中同步压制 TaCZ 内部换弹/拔枪时间戳推进，令受伤换弹真实变慢
                if (handling.reloadSpeedMultiplier < 0.90f && player.tickCount % 2 == 0) {
                    throttleClientReloadAndDraw(clientOp, handling.reloadSpeedMultiplier);
                }
            }
        }

        /**
         * 每渲染帧执行：
         * 1. 精确拦截并缩放 TaCZ 的客户端 aimingProgress（实现丝滑无卡顿的真实举枪开镜减速）；
         * 2. 将手臂伤情、呼吸起伏、脑损伤眩晕转化为真实准星朝向微震 (xRot / yRot) 与持枪倾斜角 (roll)；
         * 3. 结算带伤开火后的枪口失控上跳与沉坠恢复。
         */
        @SubscribeEvent
        public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
            Minecraft mc = Minecraft.getInstance();
            Player player = mc.player;
            if (player == null || mc.level == null || mc.isPaused() || player.isSpectator()) return;

            ItemStack held = player.getMainHandItem();
            if (!isTaczGunStack(held)) {
                resetClientGunState();
                return;
            }

            MedicalData data = player.getData(MedicalDataCapability.MEDICAL_DATA);
            if (data == null) return;

            long nowNs = System.nanoTime();
            if (lastFrameNs == 0L) lastFrameNs = nowNs;
            float dt = Mth.clamp((nowNs - lastFrameNs) / 1_000_000_000.0f, 0.0005f, 0.08f);
            lastFrameNs = nowNs;

            BallisticProfile profile = resolveHeldGunProfile(held);
            HandlingPenaltyProfile handling = evaluateHandlingProfile(player, data, profile);

            // ---------- A. 真实举枪速度 (ADS Progress) 强力阻尼与时间戳锁控 ----------
            Object clientOp = getClientGunOperator(player);
            boolean aimingNow = (clientOp != null && isClientAiming(clientOp) && !handling.blockAds && !handling.blockFireAndAim);
            if (aimingNow) {
                // 健康状态约 0.22 秒完成开镜 (rate = 4.5/s)；重伤/剧痛时 adsSpeedMultiplier 可低至 0.08，开镜需耗时 1.6 ~ 2.8 秒！
                float climbRate = 4.5f * handling.adsSpeedMultiplier;
                smoothVisualAdsProgress = Math.min(1.0f, smoothVisualAdsProgress + dt * climbRate);
            } else {
                float dropRate = 5.5f;
                smoothVisualAdsProgress = Math.max(0.0f, smoothVisualAdsProgress - dt * dropRate);
            }
            if (clientOp != null) {
                throttleClientAimingProgress(clientOp, handling.adsSpeedMultiplier, handling.blockAds || handling.blockFireAndAim, smoothVisualAdsProgress, dt);
            }

            // ---------- B. 大范围超平滑准星漂移 (Large-Range Ultra-Smooth Aim Drift) ----------
            float hr = Mth.clamp(data.getHeartRate(), 40.0f, 220.0f);
            float respRate = Mth.clamp(data.getRespirationRate(), 6.0f, 45.0f);

            float baseFreqHz = getOptionalConfigFloat("TACZ_INJURY_SWAY_FREQUENCY", 0.38f);
            float smoothRate = getOptionalConfigFloat("TACZ_INJURY_SWAY_SMOOTHING", 4.2f);

            // 使用超低频（约 0.25~0.55 Hz，即 2~4 秒完成一次完整的大范围画 8 字游走）连续相位推进，彻底杜绝高频抽搐
            float omega = baseFreqHz * ((float) Math.PI * 2.0f) * (0.85f + Mth.clamp((hr - 70.0f) / 180.0f, 0.0f, 0.45f));
            swayPhase += dt * omega;
            breathPhase += dt * Mth.clamp(respRate / 60.0f, 0.15f, 0.58f) * ((float) Math.PI * 2.0f);
            tremorPhase += dt * (omega * 0.62f);

            // 开镜瞄准时：若存在疼痛或手臂伤势，开镜会因手臂无力支撑枪重而呈现 1.55 倍的大范围沉重漂移
            float adsSwayScale = (handling.overallInstability < 0.10f)
                    ? Mth.lerp(smoothVisualAdsProgress, 1.0f, 0.55f)
                    : Mth.lerp(smoothVisualAdsProgress, 1.0f, 1.55f);

            // 耐力接近极限时，枪口画圈漂移范围进一步平滑扩大
            float fatigueBoost = 1.0f;
            if (handling.maxHoldAdsSeconds > 0.0f && adsHoldSeconds > handling.maxHoldAdsSeconds * 0.50f) {
                float fRatio = Mth.clamp((adsHoldSeconds - handling.maxHoldAdsSeconds * 0.50f) / (handling.maxHoldAdsSeconds * 0.50f), 0.0f, 1.0f);
                fatigueBoost = 1.0f + fRatio * 1.55f;
            }

            // 双谐波无理数比值李萨如曲线 (Lissajous Curve)：形成永不机械重复、如真实手臂脱力般大范围丝滑游走的轨迹
            float rawWaveX = Mth.sin(swayPhase * 0.72f) * 0.62f
                    + Mth.sin(swayPhase * 0.37f + 1.15f) * 0.28f
                    + Mth.cos(tremorPhase * 0.53f + 0.45f) * 0.10f;
            float rawWaveY = Mth.sin(breathPhase * 0.85f) * 0.54f
                    + Mth.cos(swayPhase * 0.58f + 0.75f) * 0.34f
                    + Mth.sin(tremorPhase * 0.41f + 2.10f) * 0.12f;

            float targetSwayYaw = rawWaveX * handling.horizontalSwayDeg * adsSwayScale * fatigueBoost;
            float targetSwayPitch = rawWaveY * handling.verticalSwayDeg * adsSwayScale * fatigueBoost;

            // 二阶串联指数低通滤波（保证位置、一阶导数速度与二阶导数加速度连续，100% 丝滑无抽搐）
            float alpha = 1.0f - (float) Math.exp(-dt * smoothRate);
            stage1SwayYaw = Mth.lerp(alpha, stage1SwayYaw, targetSwayYaw);
            stage1SwayPitch = Mth.lerp(alpha, stage1SwayPitch, targetSwayPitch);
            smoothSwayYaw = Mth.lerp(alpha, smoothSwayYaw, stage1SwayYaw);
            smoothSwayPitch = Mth.lerp(alpha, smoothSwayPitch, stage1SwayPitch);

            // 采用差分微元施加到玩家真实朝向 (yRot / xRot)，既让准星与弹道产生真实大范围漂移，又允许玩家随时自由移动鼠标控枪
            float deltaYaw = (smoothSwayYaw - prevAppliedSwayYaw);
            float deltaPitch = (smoothSwayPitch - prevAppliedSwayPitch);
            prevAppliedSwayYaw = smoothSwayYaw;
            prevAppliedSwayPitch = smoothSwayPitch;

            // 叠加开火后坐力失控速度包络
            if (Math.abs(recoilPitchVelocity) > 0.005f || Math.abs(recoilYawVelocity) > 0.005f) {
                deltaPitch += recoilPitchVelocity * dt * 14.0f;
                deltaYaw += recoilYawVelocity * dt * 14.0f;
                recoilPitchVelocity = Mth.lerp(Math.min(1.0f, dt * 10.5f), recoilPitchVelocity, 0.0f);
                recoilYawVelocity = Mth.lerp(Math.min(1.0f, dt * 10.5f), recoilYawVelocity, 0.0f);
            }
            if (Math.abs(recoilRollKick) > 0.01f) {
                recoilRollKick = Mth.lerp(Math.min(1.0f, dt * 9.0f), recoilRollKick, 0.0f);
            }
            if (crosshairBloomSpread > 0.05f) {
                crosshairBloomSpread = Mth.lerp(Math.min(1.0f, dt * 6.5f), crosshairBloomSpread, 0.0f);
            }

            if (Math.abs(deltaYaw) > 1.0E-4f || Math.abs(deltaPitch) > 1.0E-4f) {
                player.setYRot(player.getYRot() + deltaYaw);
                player.setXRot(Mth.clamp(player.getXRot() + deltaPitch, -90.0f, 90.0f));
                player.yRotO += deltaYaw;
                player.xRotO = Mth.clamp(player.xRotO + deltaPitch, -90.0f, 90.0f);
            }

            // ---------- C. 镜头侧倾 (Weapon Cant Roll) 与受伤举枪沉坠感 ----------
            float adsDipPitch = 0.0f;
            if (smoothVisualAdsProgress > 0.05f && smoothVisualAdsProgress < 0.92f && handling.adsSpeedMultiplier < 0.80f) {
                // 模拟手臂受伤举起沉重步枪时，枪口先因手臂无力微微下沉再缓慢抬起对准目标的物理重量感
                float liftCurve = Mth.sin(smoothVisualAdsProgress * (float) Math.PI);
                adsDipPitch = liftCurve * (1.0f - handling.adsSpeedMultiplier) * 3.2f;
            }

            float totalRoll = handling.weaponCantRollDeg * (0.45f + 0.55f * smoothVisualAdsProgress) + recoilRollKick;
            if (Math.abs(totalRoll) > 0.01f || Math.abs(adsDipPitch) > 0.01f) {
                event.setRoll(event.getRoll() + totalRoll);
                event.setPitch(event.getPitch() + adsDipPitch);
            }
        }

        /**
         * 受伤举枪 FOV 阻尼修正：当手臂带伤导致举枪变慢时，同步平滑过渡开镜变焦速度
         */
        @SubscribeEvent
        public static void onComputeFov(ViewportEvent.ComputeFov event) {
            Minecraft mc = Minecraft.getInstance();
            Player player = mc.player;
            if (player == null || mc.level == null || mc.isPaused() || player.isSpectator()) return;

            ItemStack held = player.getMainHandItem();
            if (!isTaczGunStack(held)) return;

            MedicalData data = player.getData(MedicalDataCapability.MEDICAL_DATA);
            if (data == null) return;

            BallisticProfile profile = resolveHeldGunProfile(held);
            HandlingPenaltyProfile handling = evaluateHandlingProfile(player, data, profile);

            if (handling.adsSpeedMultiplier < 0.92f && smoothVisualAdsProgress > 0.005f && smoothVisualAdsProgress < 0.995f) {
                // 在缓慢举枪抬起途中强制抑制倍镜变焦，让视野缩放与缓慢抬枪完全同步
                float lag = (1.0f - smoothVisualAdsProgress) * (1.0f - handling.adsSpeedMultiplier) * 18.0f;
                event.setFOV(event.getFOV() + lag);
            }
        }

        /**
         * 第一人称持枪手部与枪模物理阻尼渲染：
         * 1. 当玩家带伤/剧痛按下右键开镜时，即使 TaCZ 内部尝试快速将枪模移至屏幕中央，
         *    此处也通过 PoseStack 施加显著的下沉、侧偏与缓慢抬升过渡，让枪械在 1.5~2.8 秒内极其沉重缓慢地抬到眼前；
         * 2. 同时将手臂颤抖与呼吸晃动直接作用于第一人称枪模，呈现极具冲击力的受创控枪失稳感！
         */
        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void onRenderHandSlowAdsAndSway(RenderHandEvent event) {
            Minecraft mc = Minecraft.getInstance();
            Player player = mc.player;
            if (player == null || mc.level == null || mc.isPaused() || player.isSpectator()) return;

            ItemStack held = player.getMainHandItem();
            if (!isTaczGunStack(held)) return;

            MedicalData data = player.getData(MedicalDataCapability.MEDICAL_DATA);
            if (data == null) return;

            BallisticProfile profile = resolveHeldGunProfile(held);
            HandlingPenaltyProfile handling = evaluateHandlingProfile(player, data, profile);

            Object clientOp = getClientGunOperator(player);
            boolean isAiming = clientOp != null && isClientAiming(clientOp) && !handling.blockAds && !handling.blockFireAndAim;
            if (clientOp != null) {
                throttleClientAimingProgress(clientOp, handling.adsSpeedMultiplier, handling.blockAds || handling.blockFireAndAim, smoothVisualAdsProgress, 0.0f);
            }

            PoseStack poseStack = event.getPoseStack();
            // 1) 缓慢开镜过程中的沉重下沉与缓慢抬枪位移
            if (isAiming && smoothVisualAdsProgress < 0.99f && handling.adsSpeedMultiplier < 0.92f) {
                float incomplete = 1.0f - smoothVisualAdsProgress;
                float heavyFactor = Mth.clamp(1.15f - handling.adsSpeedMultiplier, 0.15f, 1.05f);
                float dropY = -incomplete * heavyFactor * 0.24f;
                float offsetX = incomplete * heavyFactor * 0.16f;
                float pitchLag = -incomplete * heavyFactor * 22.0f;
                float rollLag = incomplete * heavyFactor * 16.0f;

                poseStack.translate(offsetX, dropY, incomplete * 0.06f);
                poseStack.mulPose(Axis.XP.rotationDegrees(pitchLag));
                poseStack.mulPose(Axis.ZP.rotationDegrees(rollLag));
            }

            // 2) 疼痛与手臂受伤时的第一人称枪身剧烈晃动
            if (handling.overallInstability > 0.04f) {
                float gunSwayX = prevAppliedSwayYaw * 0.0085f;
                float gunSwayY = -prevAppliedSwayPitch * 0.0085f;
                poseStack.translate(Mth.clamp(gunSwayX, -0.14f, 0.14f), Mth.clamp(gunSwayY, -0.14f, 0.14f), 0.0f);
                poseStack.mulPose(Axis.YP.rotationDegrees(Mth.clamp(prevAppliedSwayYaw * 0.45f, -9.0f, 9.0f)));
                poseStack.mulPose(Axis.XP.rotationDegrees(Mth.clamp(prevAppliedSwayPitch * 0.45f, -9.0f, 9.0f)));
            }
        }

        /**
         * 绘制轻量级 3A 战术创伤准星散布游标与手臂力竭耐力环（仅在腰射且存在持枪伤情惩罚时浮现）
         */
        @SubscribeEvent(priority = EventPriority.HIGH)
        public static void onRenderCrosshairFeedback(RenderGuiEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            Player player = mc.player;
            if (player == null || mc.level == null || mc.screen != null || mc.options.hideGui || player.isSpectator()) {
                return;
            }

            ItemStack held = player.getMainHandItem();
            if (!isTaczGunStack(held)) return;

            MedicalData data = player.getData(MedicalDataCapability.MEDICAL_DATA);
            if (data == null || data.isCardiacArrest()) return;

            BallisticProfile profile = resolveHeldGunProfile(held);
            HandlingPenaltyProfile handling = evaluateHandlingProfile(player, data, profile);
            if (handling.overallInstability <= 0.06f && crosshairBloomSpread <= 0.2f) return;

            GuiGraphics g = event.getGuiGraphics();
            int cx = mc.getWindow().getGuiScaledWidth() / 2;
            int cy = mc.getWindow().getGuiScaledHeight() / 2;

            // 1) 腰射状态下显示受创散布扩大刻度线
            if (smoothVisualAdsProgress < 0.65f) {
                float gap = 7.0f + handling.extraSpreadDeg * 2.6f + crosshairBloomSpread
                        + Mth.abs(Mth.sin(swayPhase * 2.5f)) * handling.horizontalSwayDeg * 1.8f;
                int offset = Math.round(Mth.clamp(gap, 7.0f, 44.0f));
                int tickLen = 4;

                int alpha = Math.round(Mth.clamp(handling.overallInstability * 185.0f + crosshairBloomSpread * 4.0f, 45.0f, 195.0f)
                        * (1.0f - smoothVisualAdsProgress));
                int rgb = handling.overallInstability >= 0.55f ? 0xFF3D57 : (handling.overallInstability >= 0.25f ? 0xFFB300 : 0x80F8FF);
                int col = (alpha << 24) | rgb;

                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                // 上、下、左、右四向战术散布游标
                g.fill(cx - offset - tickLen, cy, cx - offset, cy + 1, col);
                g.fill(cx + offset + 1, cy, cx + offset + 1 + tickLen, cy + 1, col);
                g.fill(cx, cy - offset - tickLen, cx + 1, cy - offset, col);
                g.fill(cx, cy + offset + 1, cx + 1, cy + offset + 1 + tickLen, col);
            }

            // 2) 开镜状态下若手臂骨折存在耐力上限，在准星下方绘制极简的“手臂支撑耐力衰减微条”
            if (handling.maxHoldAdsSeconds > 0.0f && adsHoldSeconds > 0.15f) {
                float remainRatio = Mth.clamp(1.0f - (adsHoldSeconds / handling.maxHoldAdsSeconds), 0.0f, 1.0f);
                int barW = 34;
                int barX = cx - barW / 2;
                int barY = cy + 20;
                int fillW = Math.round(barW * remainRatio);
                int barCol = remainRatio > 0.45f ? 0xCCFFB300 : 0xEEFF1744;

                g.fill(barX - 1, barY - 1, barX + barW + 1, barY + 3, 0x88040A12);
                if (fillW > 0) {
                    g.fill(barX, barY, barX + fillW, barY + 2, barCol);
                }
            }
        }

        private static float getOptionalConfigFloat(String fieldName, float defaultVal) {
            try {
                Field f = MedicalConfig.class.getField(fieldName);
                Object specVal = f.get(null);
                if (specVal instanceof net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<?> cv) {
                    Object v = cv.get();
                    if (v instanceof Number n) return n.floatValue();
                }
            } catch (Throwable ignored) {
            }
            return defaultVal;
        }

        private static void resetClientGunState() {
            lastInterceptedAimProgress = 0.0f;
            smoothVisualAdsProgress = 0.0f;
            adsHoldSeconds = 0.0f;
            stage1SwayYaw = 0.0f;
            stage1SwayPitch = 0.0f;
            smoothSwayYaw = 0.0f;
            smoothSwayPitch = 0.0f;
            prevAppliedSwayYaw = 0.0f;
            prevAppliedSwayPitch = 0.0f;
            recoilPitchVelocity = 0.0f;
            recoilYawVelocity = 0.0f;
            recoilRollKick = 0.0f;
            crosshairBloomSpread = 0.0f;
        }

        private static Object getClientGunOperator(Player player) {
            if (!clientReflectionChecked) {
                clientReflectionChecked = true;
                try {
                    Class<?> cls = Class.forName(ICLIENT_GUN_OPERATOR_CLASS, false, ClientHandler.class.getClassLoader());
                    mFromLocalPlayer = findOneArgMethod(cls, "fromLocalPlayer");
                    mClientIsAim = findNoArgMethod(cls, "isAim", "isAiming");
                    mClientAim = cls.getMethod("aim", boolean.class);
                    mClientGetAimingProgress = findNoArgMethod(cls, "getClientAimingProgress");
                } catch (Throwable ignored) {
                }
            }
            if (mFromLocalPlayer != null) {
                try {
                    return mFromLocalPlayer.invoke(null, player);
                } catch (Throwable ignored) {
                }
            }
            return player;
        }

        private static boolean isClientAiming(Object clientOp) {
            if (clientOp == null) return false;
            try {
                if (mClientIsAim != null) {
                    Object res = mClientIsAim.invoke(clientOp);
                    if (res instanceof Boolean b) return b;
                }
            } catch (Throwable ignored) {
            }
            return false;
        }

        private static void invokeClientAim(Object clientOp, boolean aim) {
            if (clientOp == null) return;
            try {
                if (mClientAim != null) {
                    mClientAim.invoke(clientOp, aim);
                }
            } catch (Throwable ignored) {
            }
        }

        /**
         * 通过反射读取并按 adsSpeedMultiplier 缩放 TaCZ 客户端内部的 aimingProgress 增量，
         * 让玩家在手臂受伤/骨折时看到真实的 3D 枪模缓慢抬起与倍镜缓慢过渡！
         */
        private static float throttleClientAimingProgress(Object clientOp, float adsSpeedMult, boolean forceZero, float controlledProg, float dt) {
            if (clientOp == null) return 0.0f;
            try {
                // 1) 推迟 TaCZ 内部 aimingTimestamp，防止 TaCZ 在下一 Tick 用 (now - aimingTimestamp) 瞬间把开镜进度拉满
                if (adsSpeedMult < 0.95f && dt > 0.0f && !forceZero) {
                    Object tsHolder = findObjectWithField(clientOp, "aimingTimestamp", 3);
                    if (tsHolder != null) {
                        Field fAimTs = getDeclaredFieldRecursive(tsHolder.getClass(), "aimingTimestamp");
                        if (fAimTs != null) {
                            fAimTs.setAccessible(true);
                            long ts = fAimTs.getLong(tsHolder);
                            if (ts > 0L) {
                                long delayMs = Math.round(dt * 1000.0f * (1.0f - adsSpeedMult));
                                fAimTs.setLong(tsHolder, ts + delayMs);
                            }
                        }
                    }
                }

                // 2) 直接将 aimingProgress 与 oldAimingProgress 覆写锁定为受控慢速进度 controlledProg
                Object holder = findObjectWithField(clientOp, "aimingProgress", 3);
                if (holder != null) {
                    Field fProg = getDeclaredFieldRecursive(holder.getClass(), "aimingProgress");
                    Field fOldProg = getDeclaredFieldRecursive(holder.getClass(), "oldAimingProgress");
                    if (fProg != null) {
                        fProg.setAccessible(true);
                        float targetVal = forceZero ? 0.0f : (adsSpeedMult < 0.95f ? controlledProg : fProg.getFloat(holder));
                        fProg.setFloat(holder, targetVal);
                        if (fOldProg != null) {
                            fOldProg.setAccessible(true);
                            fOldProg.setFloat(holder, targetVal);
                        }
                        lastInterceptedAimProgress = targetVal;
                        return targetVal;
                    }
                }

                if (mClientGetAimingProgress != null) {
                    Object res = mClientGetAimingProgress.invoke(clientOp, 1.0f);
                    if (res instanceof Number n) return n.floatValue();
                }
            } catch (Throwable ignored) {
            }
            return controlledProg;
        }

        private static void throttleClientReloadAndDraw(Object clientOp, float speedMult) {
            try {
                long pushMs = Math.round((1.0f - speedMult) * 95.0f);
                if (pushMs <= 5L) return;

                Object reloadHolder = findObjectWithField(clientOp, "reloadTimestamp", 2);
                if (reloadHolder != null) {
                    Field fReload = getDeclaredFieldRecursive(reloadHolder.getClass(), "reloadTimestamp");
                    if (fReload != null) {
                        fReload.setAccessible(true);
                        long ts = fReload.getLong(reloadHolder);
                        if (ts > 0L) {
                            fReload.setLong(reloadHolder, ts + pushMs);
                        }
                    }
                }

                Object drawHolder = findObjectWithField(clientOp, "drawTimestamp", 2);
                if (drawHolder != null) {
                    Field fDraw = getDeclaredFieldRecursive(drawHolder.getClass(), "drawTimestamp");
                    if (fDraw != null) {
                        fDraw.setAccessible(true);
                        long ts = fDraw.getLong(drawHolder);
                        if (ts > 0L) {
                            fDraw.setLong(drawHolder, ts + pushMs);
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
    }

    // =========================================================================
    // 4. 枪械识别与反射安全工具方法
    // =========================================================================

    public static boolean isTaczGunStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        try {
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (itemId != null && TACZ_MOD_ID.equals(itemId.getNamespace())) {
                String path = itemId.getPath().toLowerCase();
                if (path.contains("gun") || extractGunIdFromStack(stack) != null) {
                    return true;
                }
            }
            return extractGunIdFromStack(stack) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static BallisticProfile resolveHeldGunProfile(ItemStack stack) {
        ResourceLocation gunId = extractGunIdFromStack(stack);
        if (gunId == null) {
            return BallisticProfile.RIFLE_MG;
        }

        String gunType = "";
        ResourceLocation ammoId = null;
        int pelletCount = 1;

        if (mGetCommonGunIndex != null) {
            try {
                Object optObj = mGetCommonGunIndex.invoke(null, gunId);
                if (optObj instanceof Optional<?> opt && opt.isPresent()) {
                    Object gunIndex = opt.get();
                    Object typeObj = invokeNoArg(gunIndex, "getType");
                    if (typeObj instanceof String s) gunType = s.toLowerCase();
                    Object gunData = invokeNoArg(gunIndex, "getGunData");
                    if (gunData != null) {
                        Object ammoObj = invokeNoArg(gunData, "getAmmoId");
                        if (ammoObj instanceof ResourceLocation rl) ammoId = rl;
                        Object bulletData = invokeNoArg(gunData, "getBulletData");
                        if (bulletData != null) {
                            Object amtObj = invokeNoArg(bulletData, "getBulletAmount");
                            if (amtObj instanceof Number n) pelletCount = Math.max(1, n.intValue());
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        String path = gunId.getPath().toLowerCase();
        String ammoPath = ammoId != null ? ammoId.getPath().toLowerCase() : "";

        if (gunType.contains("rpg") || gunType.contains("heavy") || gunType.contains("launcher")
                || path.contains("rpg") || path.contains("m320") || path.contains("mgl")) {
            return BallisticProfile.EXPLOSIVE_LAUNCHER;
        }
        if (pelletCount > 1 || gunType.contains("shotgun") || ammoPath.contains("12g")
                || path.contains("m870") || path.contains("aa12") || path.contains("db_")) {
            return BallisticProfile.SHOTGUN;
        }
        if (gunType.contains("sniper") || ammoPath.contains("50bmg") || ammoPath.contains("338")
                || path.contains("m95") || path.contains("m107") || path.contains("awp") || path.contains("awm")) {
            return BallisticProfile.HEAVY_SNIPER_AMR;
        }
        if (gunType.contains("pistol") || gunType.contains("smg")
                || ammoPath.contains("9mm") || ammoPath.contains("45acp") || ammoPath.contains("57x28")) {
            return BallisticProfile.PISTOL_SMG;
        }
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

    private static Object getServerGunOperator(LivingEntity entity) {
        if (mFromLivingEntity != null) {
            try {
                return mFromLivingEntity.invoke(null, entity);
            } catch (Throwable ignored) {
            }
        }
        return entity;
    }

    private static void invokeAim(Object operator, boolean aim) {
        if (operator == null) return;
        try {
            Method m = operator.getClass().getMethod("aim", boolean.class);
            m.setAccessible(true);
            m.invoke(operator, aim);
        } catch (Throwable ignored) {
        }
    }

    private static void extendReloadAndDrawTimestamps(Object operator,
                                                      float speedMult,
                                                      long gameTime,
                                                      ServerShooterState state) {
        if (operator == null) return;
        long pushMs = Math.round((1.0f - speedMult) * 95.0f);
        if (pushMs <= 5L) return;

        try {
            if (gameTime != state.lastReloadThrottleTick) {
                Object reloadHolder = findObjectWithField(operator, "reloadTimestamp", 2);
                if (reloadHolder != null) {
                    Field fReload = getDeclaredFieldRecursive(reloadHolder.getClass(), "reloadTimestamp");
                    if (fReload != null) {
                        fReload.setAccessible(true);
                        long ts = fReload.getLong(reloadHolder);
                        if (ts > 0L) {
                            fReload.setLong(reloadHolder, ts + pushMs);
                            state.lastReloadThrottleTick = gameTime;
                        }
                    }
                }
            }
            if (gameTime != state.lastDrawThrottleTick) {
                Object drawHolder = findObjectWithField(operator, "drawTimestamp", 2);
                if (drawHolder != null) {
                    Field fDraw = getDeclaredFieldRecursive(drawHolder.getClass(), "drawTimestamp");
                    if (fDraw != null) {
                        fDraw.setAccessible(true);
                        long ts = fDraw.getLong(drawHolder);
                        if (ts > 0L) {
                            fDraw.setLong(drawHolder, ts + pushMs);
                            state.lastDrawThrottleTick = gameTime;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static Object findObjectWithField(Object root, String targetFieldName, int maxDepth) {
        if (root == null || maxDepth < 0) return null;
        if (getDeclaredFieldRecursive(root.getClass(), targetFieldName) != null) {
            return root;
        }
        if (maxDepth == 0) return null;

        Class<?> c = root.getClass();
        while (c != null && c != Object.class) {
            for (Field f : c.getDeclaredFields()) {
                Class<?> ft = f.getType();
                if (ft.isPrimitive() || ft.getName().startsWith("java.")) continue;
                if (ft.getName().contains("tacz")) {
                    try {
                        f.setAccessible(true);
                        Object child = f.get(root);
                        if (child != null) {
                            Object found = findObjectWithField(child, targetFieldName, maxDepth - 1);
                            if (found != null) return found;
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
            c = c.getSuperclass();
        }
        return null;
    }

    private static Field getDeclaredFieldRecursive(Class<?> cls, String fieldName) {
        Class<?> c = cls;
        while (c != null && c != Object.class) {
            try {
                return c.getDeclaredField(fieldName);
            } catch (Throwable ignored) {
                c = c.getSuperclass();
            }
        }
        return null;
    }

    private static Method findNoArgMethod(Class<?> cls, String... names) {
        if (cls == null) return null;
        for (String name : names) {
            try {
                Method m = cls.getMethod(name);
                m.setAccessible(true);
                return m;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static Method findOneArgMethod(Class<?> cls, String name) {
        if (cls == null) return null;
        for (Method m : cls.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == 1) {
                m.setAccessible(true);
                return m;
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

    private static Object getFieldVal(Object target, String fieldName) {
        if (target == null || fieldName == null) return null;
        Field f = getDeclaredFieldRecursive(target.getClass(), fieldName);
        if (f != null) {
            try {
                f.setAccessible(true);
                return f.get(target);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }
}