package com.FIYTP.realmedicalmod.treatment;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/**
 * 治疗物品标签体系（2026-10-21 定稿）
 * 三轴正交：类别（怎么用）× 效果（治什么）× 属性（怎么消耗）。
 * 整合包作者给任意物品打标签即可接入治疗系统。
 */
public class MedicalItemTags {

    // ====== 类别标签（5种，互斥） ======
    public static final TagKey<Item> BANDAGE = tag("bandage");
    public static final TagKey<Item> SYRINGE = tag("syringe");
    public static final TagKey<Item> TOPICAL = tag("topical");
    public static final TagKey<Item> ORAL = tag("oral");
    public static final TagKey<Item> SPLINT = tag("splint");

    // ====== 效果标签（18个 + 3个预留） ======
    // 治疗肢体病症
    public static final TagKey<Item> HEMOSTASIS = tag("hemostasis");                 // 外出血
    public static final TagKey<Item> TOURNIQUET = tag("tourniquet");                 // 严重外出血（出血量归零）
    public static final TagKey<Item> INTERNAL_HEMOSTASIS = tag("internal_hemostasis"); // 内出血
    public static final TagKey<Item> SPLINT_EFFECT = tag("splint_effect");           // 骨折
    public static final TagKey<Item> ANALGESIC = tag("analgesic");                   // 疼痛（非阿片）
    public static final TagKey<Item> OPIOID = tag("opioid");                         // 疼痛（阿片强效）
    public static final TagKey<Item> DISINFECT = tag("disinfect");                   // 感染消毒
    public static final TagKey<Item> CLEANSE = tag("cleanse");                       // 感染清洗
    public static final TagKey<Item> ANTIBIOTIC = tag("antibiotic");                 // 感染持续抑制
    public static final TagKey<Item> BURN_HEAL = tag("burn_heal");                   // 烫伤
    // 治疗全身病症
    public static final TagKey<Item> STIMULANT = tag("stimulant");                   // 意识不清
    public static final TagKey<Item> ANTIHYPERTENSIVE = tag("antihypertensive");     // 高血压
    public static final TagKey<Item> VASOPRESSOR = tag("vasopressor");               // 低血压
    public static final TagKey<Item> ANTIARRHYTHMIC = tag("antiarrhythmic");         // 心律失常
    public static final TagKey<Item> ANTIDOTE = tag("antidote");                     // 中毒
    public static final TagKey<Item> SEPSIS_TREAT = tag("sepsis_treat");             // 脓毒症
    public static final TagKey<Item> NEUROPROTECTANT = tag("neuroprotectant");       // 脑健康度
    public static final TagKey<Item> BLOOD_VOLUME = tag("blood_volume");             // 血容量（正=补液，负=抽血）
    // 预留（本期不接线）
    public static final TagKey<Item> HEMOTHORAX_DRAIN = tag("hemothorax_drain");     // 血胸引流
    public static final TagKey<Item> REDUCTION = tag("reduction");                   // 脱臼复位
    public static final TagKey<Item> DEFIBRILLATION = tag("defibrillation");         // 骤停除颤

    // ====== 属性标签 ======
    public static final TagKey<Item> SINGLE_USE = tag("single_use");                 // 一次性消耗
    public static final TagKey<Item> DURABLE = tag("durable");                       // 耐久消耗（用 damage 值）
    public static final TagKey<Item> STERILE = tag("sterile");                       // 无菌
    public static final TagKey<Item> NON_STERILE = tag("non_sterile");               // 非无菌（使用时感染+2）
    public static final TagKey<Item> NECROSIS_RISK = tag("necrosis_risk");           // 副作用：绑定超时→肢体坏死
    public static final TagKey<Item> RESPIRATORY_DEPRESSION = tag("respiratory_depression"); // 阿片副作用

    private static TagKey<Item> tag(String name) {
        return ItemTags.create(ResourceLocation.fromNamespaceAndPath("realmedicalmod", name));
    }

    private MedicalItemTags() {
    }
}
