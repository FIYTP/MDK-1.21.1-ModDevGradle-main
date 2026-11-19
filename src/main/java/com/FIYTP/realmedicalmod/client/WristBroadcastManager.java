package com.FIYTP.realmedicalmod.client;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.capability.MedicalDataCapability;
import com.FIYTP.realmedicalmod.capability.MedicalDataImpl;
import com.FIYTP.realmedicalmod.config.MedicalConfig;
import com.FIYTP.realmedicalmod.treatment.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 阶段 3（思路 C 精修版）：双向生理趋势监测 + 未读警报驻留队列 (WristBroadcastManager)
 *
 * 严格贯彻思路 C：
 * - 绝不擅自自动抬起玩家左手干扰战斗！
 * - 捕捉到身体向【更坏】或【更好】演变的关键拐点时：
 *   1) 触发一次极克制的“腕表马达两下短促微震 (Buzz-Buzz)”提醒玩家低头看表；
 *   2) 将播报存入未读驻留队列（最多保留 2 条，未看之前不会凭空过期消失）；
 *   3) 当玩家按住 H 抬起左手看表时，所有未读播报立刻沿 45° 激光引导折线优雅展开；
 *      玩家松开 H 键放下手臂后，刚看过的播报平滑标记已读并淡出！
 */
@EventBusSubscriber(modid = "realmedicalmod", value = Dist.CLIENT)
public final class WristBroadcastManager {

    public enum Severity {
        CRITICAL, // 危急猩红
        WARNING,  // 预警琥珀
        RECOVERY  // 好转翠青
    }

    public enum IconKind {
        BLEED,      // 滴落血滴
        FRACTURE,   // 断裂骨骼
        BALLISTIC,  // 弹头穿孔
        LUNG,       // 双肺气胸/血胸
        BIOHAZARD,  // 感染/SIRS/中毒
        HEART,      // 心律/休克/危急/脑部
        RECOVERY    // 医疗十字+恢复波
    }

    public static final class BroadcastEntry {
        public final String tag;         // 3x5 军工点阵眉标，如 "HEMORRHAGE", "INFECTION+", "STABILIZED"
        public final String title;       // 极简中文主标，如 "左腿 · 动脉出血"
        public final int numericVal;     // 右侧七段军工数码管大数字 (-1 表示无数字)
        public final String unit;        // 右侧极小单位，如 "ML/M", "PCT", "MMHG", "LV", "SEC"
        public final Severity severity;
        public final IconKind icon;
        public final long createdMs;
        /** 首次在视野内被玩家看到的时间戳（0 表示尚未被查看，一直驻留等待！） */
        public long firstSeenMs = 0L;
        /** 已被看完并触发松手淡出的时间戳（0 表示未淡出） */
        public long dismissStartMs = 0L;

        public BroadcastEntry(String tag, String title, int numericVal, String unit,
                              Severity severity, IconKind icon, long createdMs) {
            this.tag = tag;
            this.title = title;
            this.numericVal = numericVal;
            this.unit = unit;
            this.severity = severity;
            this.icon = icon;
            this.createdMs = createdMs;
        }
    }

    private static final int MAX_VISIBLE = 2;
    private static final long MAX_UNREAD_LIFETIME_MS = 90_000L; // 未读最多驻留 90 秒，防止陈旧信息堆积
    private static final List<BroadcastEntry> ACTIVE_ENTRIES = new ArrayList<>();
    private static final Map<String, Long> COOLDOWN_MAP = new HashMap<>();

    private static boolean snapshotInit = false;
    private static int prevSbp = 120;
    private static int prevSpo2 = 98;
    private static int prevBrain = 100;
    private static int prevPain = 0;
    private static int prevSirs = 0;
    private static int prevPneumo = 0;
    private static int prevHemo = 0;
    private static int prevIch = 0;
    private static float prevToxin = 0.0f;
    private static boolean prevVfib = false;
    private static boolean prevCritical = false;
    private static int prevTotalBleed = 0;
    private static int prevTotalInfection = 0;

    private static final class LimbSnap {
        int bleed;
        int internalBleed;
        int bullets;
        boolean fracture;
        boolean perforation;
        boolean compartment;
        boolean necrotic;
        int infection;
        int burnDeg;
        int tqTimer;
    }

    private static final Map<MedicalData.Limb, LimbSnap> PREV_LIMBS = new EnumMap<>(MedicalData.Limb.class);

    static {
        for (MedicalData.Limb l : MedicalData.Limb.values()) {
            PREV_LIMBS.put(l, new LimbSnap());
        }
    }

    private WristBroadcastManager() {}

    public static List<BroadcastEntry> getActiveEntries() {
        long now = System.currentTimeMillis();
        ACTIVE_ENTRIES.removeIf(e ->
                (now - e.createdMs) > MAX_UNREAD_LIFETIME_MS
                        || (e.dismissStartMs > 0L && (now - e.dismissStartMs) > 280L)
        );
        return ACTIVE_ENTRIES;
    }

    public static boolean hasUnreadBroadcast() {
        long now = System.currentTimeMillis();
        for (BroadcastEntry e : getActiveEntries()) {
            if (e.dismissStartMs == 0L && (now - e.createdMs) <= MAX_UNREAD_LIFETIME_MS) {
                return true;
            }
        }
        return false;
    }

    /**
     * 玩家长按 H 抬起左手看到播报时调用：记录首次看见时间，驱动激光折线展开动画
     */
    public static void markEntriesViewedNow() {
        long now = System.currentTimeMillis();
        for (BroadcastEntry e : ACTIVE_ENTRIES) {
            if (e.firstSeenMs == 0L) {
                e.firstSeenMs = now;
            }
        }
    }

    /**
     * 玩家松开 H 键放下手臂时调用：只要玩家确实看了超过 0.55 秒，就把这些已读播报标记淡出清除
     */
    public static void onPlayerLoweredWatch() {
        long now = System.currentTimeMillis();
        for (BroadcastEntry e : ACTIVE_ENTRIES) {
            if (e.firstSeenMs > 0L && (now - e.firstSeenMs) >= 550L && e.dismissStartMs == 0L) {
                e.dismissStartMs = now;
            }
        }
    }

    public static void clearAll() {
        ACTIVE_ENTRIES.clear();
        COOLDOWN_MAP.clear();
        snapshotInit = false;
    }

    private static void pushBroadcast(String key, long cooldownMs, String tag, String title,
                                      int numericVal, String unit, Severity severity, IconKind icon) {
        long now = System.currentTimeMillis();
        Long last = COOLDOWN_MAP.get(key);
        if (last != null && (now - last) < cooldownMs) {
            return;
        }
        COOLDOWN_MAP.put(key, now);

        ACTIVE_ENTRIES.removeIf(e -> e.dismissStartMs > 0L || e.title.equals(title));
        BroadcastEntry entry = new BroadcastEntry(tag, title, numericVal, unit, severity, icon, now);
        // 若玩家此刻正在长按 H 看表，直接开始激光展开
        if (WristbandHologramController.isInspecting()) {
            entry.firstSeenMs = now;
        }
        ACTIVE_ENTRIES.add(0, entry);
        while (ACTIVE_ENTRIES.size() > MAX_VISIBLE) {
            ACTIVE_ENTRIES.remove(ACTIVE_ENTRIES.size() - 1);
        }

        // 思路 C：不自动抬手抢视角，仅触发克制的“腕表马达微震 (Buzz-Buzz)”提醒玩家按 H 看表！
        WristbandHologramController.triggerHapticBuzz(severity == Severity.CRITICAL ? 1.0f : (severity == Severity.WARNING ? 0.72f : 0.45f));
    }

    @SubscribeEvent
    public static void onClientTickPost(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null) {
            clearAll();
            return;
        }
        if (!ModItems.safeCfgBool(MedicalConfig.WRISTBAND_ENABLED, true)) {
            ACTIVE_ENTRIES.clear();
            return;
        }
        if (!player.isAlive()) {
            clearAll();
            return;
        }

        MedicalData data = player.getData(MedicalDataCapability.MEDICAL_DATA);
        if (data == null) return;

        if ((player.tickCount & 3) != 0) return;

        int curSbp = data.getSystolicBP();
        int curSpo2 = data.getBloodOxygen();
        int curBrain = data.getBrainHealth();
        int curPain = data.getPerceivedPain();
        int curSirs = data.getSirsLevel();
        int curPneumo = ModItems.getPneumothoraxType(data);
        int curHemo = data.getHemothoraxLevel();
        int curIch = data.getIntracranialLevel();
        float curToxin = data.getToxinValue();
        boolean curVfib = data.isInVFib();
        boolean curCritical = data.isInCriticalState();

        int curTotalBleed = 0;
        int curTotalInf = 0;
        for (MedicalData.Limb l : MedicalData.Limb.values()) {
            MedicalData.LimbData ld = data.getLimb(l);
            if (ld != null) {
                curTotalBleed += ld.getBleeding() + ld.getInternalBleedingRate();
                curTotalInf += ld.getInfection();
            }
        }

        if (!snapshotInit) {
            saveSnapshot(data, curSbp, curSpo2, curBrain, curPain, curSirs, curPneumo, curHemo,
                    curIch, curToxin, curVfib, curCritical, curTotalBleed, curTotalInf);
            snapshotInit = true;
            return;
        }

        // ==================== 一、6 大肢体局部创伤与好转监测 ====================
        for (MedicalData.Limb limb : MedicalData.Limb.values()) {
            MedicalData.LimbData ld = data.getLimb(limb);
            if (ld == null) continue;
            LimbSnap p = PREV_LIMBS.get(limb);
            String lName = getLimbName(limb);

            int bleed = ld.getBleeding();
            int intBleed = ld.getInternalBleedingRate();
            int bullets = (ld instanceof MedicalDataImpl.LimbDataImpl li) ? li.getEmbeddedBulletCount() : 0;
            int tqTimer = (ld instanceof MedicalDataImpl.LimbDataImpl li) ? li.getTourniquetTimer() : 0;
            boolean frac = ld.hasFracture();
            boolean perf = ld.hasPerforation();
            boolean cs = ModItems.isCompartmentSyndrome(ld);
            boolean nec = ModItems.isLimbNecrotic(ld);
            int inf = ld.getInfection();
            int burnDeg = ld.getBurnDegree();

            // [恶化] 放低出血门槛 (>=12 mL/min 即可捕获轻伤，>=140 标危急动脉出血)
            if (bleed - p.bleed >= 140) {
                pushBroadcast("bleed_heavy_" + limb.name(), 2800L, "HEMORRHAGE",
                        lName + " · 动脉出血", bleed, "ML/M", Severity.CRITICAL, IconKind.BLEED);
            } else if (bleed - p.bleed >= 12) {
                pushBroadcast("bleed_light_" + limb.name(), 3200L, "BLEEDING",
                        lName + " · 创口出血", bleed, "ML/M", Severity.WARNING, IconKind.BLEED);
            }
            if (intBleed - p.internalBleed >= 25) {
                pushBroadcast("int_bleed_" + limb.name(), 3500L, "INT-BLEED",
                        lName + " · 体内出血", intBleed, "ML/M", Severity.CRITICAL, IconKind.BLEED);
            }
            if (bullets > p.bullets) {
                pushBroadcast("bullet_" + limb.name(), 2800L, "BALLISTIC",
                        lName + " · 弹头残留", bullets, "PCS", Severity.WARNING, IconKind.BALLISTIC);
            }
            if (perf && !p.perforation) {
                pushBroadcast("perf_" + limb.name(), 3200L, "PERFORATION",
                        lName + " · 弹道贯穿", bleed + intBleed, "ML/M", Severity.CRITICAL, IconKind.BALLISTIC);
            }
            if (frac && !p.fracture) {
                pushBroadcast("frac_" + limb.name(), 3500L, "FRACTURE",
                        lName + (ld.isOpenFracture() ? " · 开放骨折" : " · 骨骼断裂"),
                        ld.getPainLevel(), "PAIN", Severity.WARNING, IconKind.FRACTURE);
            }
            if (cs && !p.compartment) {
                pushBroadcast("cs_" + limb.name(), 5500L, "COMPARTMENT",
                        lName + " · 筋膜高压", Math.round(ModItems.getCompartmentPressure(ld)), "MMHG",
                        Severity.CRITICAL, IconKind.FRACTURE);
            }
            if (nec && !p.necrotic) {
                pushBroadcast("nec_" + limb.name(), 7500L, "NECROSIS",
                        lName + " · 组织坏死", inf, "PCT", Severity.CRITICAL, IconKind.BIOHAZARD);
            }
            if (burnDeg > p.burnDeg && burnDeg >= 1) {
                pushBroadcast("burn_" + limb.name(), 4000L, "THERMAL",
                        lName + " · " + burnDeg + "度烧伤", Math.round(ld.getBurnArea()), "PCT",
                        burnDeg >= 3 ? Severity.CRITICAL : Severity.WARNING, IconKind.BIOHAZARD);
            }
            int[] infThresholds = {20, 45, 70};
            for (int th : infThresholds) {
                if (inf >= th && p.infection < th) {
                    pushBroadcast("inf_up_" + limb.name() + "_" + th, 7500L, "INFECTION+",
                            lName + " · 感染加剧", inf, "PCT",
                            th >= 45 ? Severity.CRITICAL : Severity.WARNING, IconKind.BIOHAZARD);
                }
            }
            int tqWarnSec = ModItems.safeCfgInt(MedicalConfig.TOURNIQUET_ISCHEMIA_STAGE1_SEC, 180);
            if (tqTimer >= tqWarnSec && p.tqTimer < tqWarnSec) {
                pushBroadcast("tq_warn_" + limb.name(), 9000L, "ISCHEMIA",
                        lName + " · 止血带缺血", tqTimer, "SEC", Severity.CRITICAL, IconKind.BLEED);
            }

            // [好转] 骨折愈合 / 止血成功
            if (!frac && p.fracture) {
                pushBroadcast("frac_heal_" + limb.name(), 5500L, "RECOVERED",
                        lName + " · 骨折愈合", 100, "PCT", Severity.RECOVERY, IconKind.RECOVERY);
            }
            if (bleed == 0 && p.bleed >= 30) {
                pushBroadcast("bleed_stop_" + limb.name(), 3800L, "HEMOSTASIS",
                        lName + " · 出血受控", 0, "ML/M", Severity.RECOVERY, IconKind.RECOVERY);
            }
        }

        // ==================== 二、全身生理拐点监测 ====================
        if (curPneumo > prevPneumo && curPneumo > 0) {
            String pTitle = curPneumo >= 3 ? "胸腔 · 张力气胸" : (curPneumo == 2 ? "胸腔 · 开放气胸" : "胸腔 · 闭合气胸");
            pushBroadcast("pneumo_up", 4000L, "PNEUMOTHORAX", pTitle,
                    Math.round(ModItems.getPleuralAirMl(data)), "ML",
                    curPneumo >= 2 ? Severity.CRITICAL : Severity.WARNING, IconKind.LUNG);
        } else if (curPneumo == 0 && prevPneumo > 0) {
            pushBroadcast("pneumo_clear", 4500L, "DECOMPRESSED", "胸腔 · 气胸解除",
                    curSpo2, "PCT", Severity.RECOVERY, IconKind.RECOVERY);
        }

        if (curHemo > prevHemo) {
            pushBroadcast("hemo_up", 4500L, "HEMOTHORAX", "胸腔 · 血胸加重",
                    curHemo, "LV", Severity.CRITICAL, IconKind.LUNG);
        } else if (curHemo == 0 && prevHemo > 0) {
            pushBroadcast("hemo_clear", 4500L, "DRAINED", "胸腔 · 积血引流",
                    curSpo2, "PCT", Severity.RECOVERY, IconKind.RECOVERY);
        }

        if (curVfib && !prevVfib) {
            pushBroadcast("vfib", 5500L, "CARDIAC!", "心律异常 · 室颤",
                    data.getHeartRate(), "BPM", Severity.CRITICAL, IconKind.HEART);
        }
        if (curCritical && !prevCritical) {
            pushBroadcast("critical", 5500L, "CRITICAL!", "濒死警报倒计时",
                    data.getCriticalTimer(), "SEC", Severity.CRITICAL, IconKind.HEART);
        }
        if (curIch > prevIch) {
            pushBroadcast("ich_up", 4500L, "CRANIAL!", "颅内出血升级",
                    curIch, "LV", Severity.CRITICAL, IconKind.HEART);
        }

        if (curSirs > prevSirs) {
            pushBroadcast("sirs_up", 6500L, "SIRS+", "全身炎症升级",
                    curSirs, "LV", curSirs >= 2 ? Severity.CRITICAL : Severity.WARNING, IconKind.BIOHAZARD);
        } else if (curSirs < prevSirs) {
            pushBroadcast("sirs_down", 6500L, "SIRS-", "全身炎症缓解",
                    curSirs, "LV", Severity.RECOVERY, IconKind.RECOVERY);
        }

        if (curSbp <= 84 && prevSbp > 84) {
            pushBroadcast("bp_low", 7500L, "SHOCK!", "失血休克预警",
                    curSbp, "MMHG", Severity.CRITICAL, IconKind.HEART);
        } else if (curSbp >= 102 && prevSbp < 92) {
            pushBroadcast("bp_ok", 7500L, "PERFUSION+", "血压回升趋稳",
                    curSbp, "MMHG", Severity.RECOVERY, IconKind.RECOVERY);
        }

        if (curSpo2 <= 86 && prevSpo2 > 86) {
            pushBroadcast("spo2_low", 7500L, "HYPOXIA", "低血氧警报",
                    curSpo2, "PCT", curSpo2 <= 75 ? Severity.CRITICAL : Severity.WARNING, IconKind.LUNG);
        } else if (curSpo2 >= 94 && prevSpo2 < 90) {
            pushBroadcast("spo2_ok", 7500L, "OXYGEN+", "血氧恢复正常",
                    curSpo2, "PCT", Severity.RECOVERY, IconKind.RECOVERY);
        }

        if ((curBrain <= 80 && prevBrain > 80) || (curBrain <= 55 && prevBrain > 55)) {
            pushBroadcast("brain_low", 8500L, "NEURAL-", "脑健康下滑",
                    curBrain, "PCT", curBrain <= 55 ? Severity.CRITICAL : Severity.WARNING, IconKind.HEART);
        } else if (curBrain - prevBrain >= 5 && curBrain >= 85) {
            pushBroadcast("brain_ok", 9000L, "NEURAL+", "脑灌注趋稳",
                    curBrain, "PCT", Severity.RECOVERY, IconKind.RECOVERY);
        }

        if (prevTotalInfection - curTotalInf >= 10) {
            pushBroadcast("inf_down", 8500L, "ANTIBIOTIC", "感染态势回落",
                    curTotalInf, "PCT", Severity.RECOVERY, IconKind.RECOVERY);
        }

        if (prevPain - curPain >= 8 && prevPain >= 12) {
            pushBroadcast("pain_down", 7500L, "ANALGESIA", "镇痛生效缓解",
                    curPain, "PAIN", Severity.RECOVERY, IconKind.RECOVERY);
        }

        if ((curToxin >= 25.0f && prevToxin < 25.0f) || (curToxin >= 55.0f && prevToxin < 55.0f)) {
            pushBroadcast("toxin_up", 7500L, "TOXIN+", "体内毒素升高",
                    Math.round(curToxin), "PCT", curToxin >= 55.0f ? Severity.CRITICAL : Severity.WARNING, IconKind.BIOHAZARD);
        }

        if (curTotalBleed == 0 && prevTotalBleed >= 40) {
            pushBroadcast("all_bleed_stop", 5500L, "STABILIZED", "全身出血已止",
                    0, "ML/M", Severity.RECOVERY, IconKind.RECOVERY);
        }

        saveSnapshot(data, curSbp, curSpo2, curBrain, curPain, curSirs, curPneumo, curHemo,
                curIch, curToxin, curVfib, curCritical, curTotalBleed, curTotalInf);
    }

    private static void saveSnapshot(MedicalData data, int sbp, int spo2, int brain, int pain,
                                     int sirs, int pneumo, int hemo, int ich, float toxin,
                                     boolean vfib, boolean critical, int totalBleed, int totalInf) {
        prevSbp = sbp;
        prevSpo2 = spo2;
        prevBrain = brain;
        prevPain = pain;
        prevSirs = sirs;
        prevPneumo = pneumo;
        prevHemo = hemo;
        prevIch = ich;
        prevToxin = toxin;
        prevVfib = vfib;
        prevCritical = critical;
        prevTotalBleed = totalBleed;
        prevTotalInfection = totalInf;

        for (MedicalData.Limb l : MedicalData.Limb.values()) {
            MedicalData.LimbData ld = data.getLimb(l);
            LimbSnap s = PREV_LIMBS.get(l);
            if (ld == null || s == null) continue;
            s.bleed = ld.getBleeding();
            s.internalBleed = ld.getInternalBleedingRate();
            s.bullets = (ld instanceof MedicalDataImpl.LimbDataImpl li) ? li.getEmbeddedBulletCount() : 0;
            s.tqTimer = (ld instanceof MedicalDataImpl.LimbDataImpl li) ? li.getTourniquetTimer() : 0;
            s.fracture = ld.hasFracture();
            s.perforation = ld.hasPerforation();
            s.compartment = ModItems.isCompartmentSyndrome(ld);
            s.necrotic = ModItems.isLimbNecrotic(ld);
            s.infection = ld.getInfection();
            s.burnDeg = ld.getBurnDegree();
        }
    }

    private static String getLimbName(MedicalData.Limb limb) {
        return switch (limb) {
            case HEAD -> "头部";
            case CHEST -> "胸腔";
            case LEFT_ARM -> "左臂";
            case RIGHT_ARM -> "右臂";
            case LEFT_LEG -> "左腿";
            case RIGHT_LEG -> "右腿";
        };
    }
}