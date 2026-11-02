package com.FIYTP.realmedicalmod.capability;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;

import java.util.EnumMap;
import java.util.Map;

public class MedicalDataImpl implements MedicalData, INBTSerializable<CompoundTag> {

    // ====== 全局生理数据 ======
    private int bloodVolume = 5000;
    private float bloodLossAccumulator = 0.0f;
    private float bloodRegenAccumulator = 0.0f;
    private float bloodOxygen = 98.0f;
    private float brainHealth = 100.0f;
    private int brainHealthRegenTimer = 0;
    private float consciousness = 100.0f;
    private int heartRate = 75;
    private int respirationRate = 16;
    private boolean underwater = false;
    private int underwaterTimer = 0;
    private boolean inVFib = false;
    private boolean cardiacArrest = false;

    // ====== 心脏计时器 ======
    private int vfibTimer = 0;
    private int bradyTimer = 0;
    private int hypoxiaTimer = 0;

    // ====== 疼痛衰减计时器 ======
    private int painDecayTimer = 0;

    // ====== 运动相关 ======
    private float exerciseIntensity = 0.0f;
    private float exerciseContribution = 0.0f;
    private int runningTimer = 0;
    private int attackTimer = 0;

    // ====== 心脏效果状态 ======
    private int heartEffectTimer = 0;
    private boolean heartEffectSevere = false;
    private boolean bradycardiaActive = false;
    private boolean bradycardiaSevere = false;

    // ====== 阿片类药物（预留） ======
    private int opioidConcentration = 0;
    private int addictionLevel = 0;

    // ====== 肢体数据 ======
    private final Map<Limb, LimbData> limbs = new EnumMap<>(Limb.class);

    // ====== 濒危状态 ======
    private boolean inCriticalState = false;
    private int criticalTimer = 0;

    // ====== 内出血与积血系统 ======
    private float chestBloodAccumulation = 0.0f;
    private int hemothoraxLevel = 0;
    private int hemothoraxVomitTimer = 0;
    private float lungContusion = 0.0f;
    private int fractureTraumaTimer = 0;
    private int intracranialBleedRate = 0;
    private float intracranialAccumulation = 0.0f;
    private int intracranialLevel = 0;
    private int intracranialOnsetTimer = 0;

    // ====== 血压与血流动力学 ======
    private int systolicBP = 120;
    private int diastolicBP = 80;
    private float meanArterialPressure = 93.0f;
    private float peripheralResistance = 1.0f;
    private float cardiacOutput = 5.25f;

    // ====== 感染与 SIRS 系统 ======
    private int sirsLevel = 0;
    private int sirsProgressionTimer = 0;
    private int sirsRecoveryTimer = 0;
    private boolean sirsActive = false;

    // ====== 中毒系统 ======
    private float toxinValue = 0.0f;

    // ====== 烧伤系统（全身） ======
    private float burnTBSA = 0.0f;
    private float burnEdema = 0.0f;
    private float inhalationInjury = 0.0f;

    // ====== 脑损伤突发触发系统 ======
    private int brainDamageTriggerTimer = 0;
    private int brainDamageTriggerCooldown = 0;
    private boolean forcedTrigger90 = false;
    private boolean forcedTrigger60 = false;
    private boolean forcedTrigger30 = false;

    public MedicalDataImpl() {
        for (Limb limb : Limb.values()) {
            limbs.put(limb, new LimbDataImpl());
        }
    }

    // ====== Getter / Setter（全部 synchronized） ======
    @Override public synchronized int getBloodVolume() { return bloodVolume; }
    @Override public synchronized void setBloodVolume(int value) { this.bloodVolume = Math.max(0, value); }
    @Override public int getMaxBloodVolume() { return 5000; }

    @Override
    public synchronized float getBloodLossAccumulator() {
        return bloodLossAccumulator;
    }

    @Override
    public synchronized void setBloodLossAccumulator(float value) {
        this.bloodLossAccumulator = Math.max(0.0f, Math.min(1.0f, value));
    }

    @Override
    public synchronized float getBloodRegenAccumulator() {
        return bloodRegenAccumulator;
    }

    @Override
    public synchronized void setBloodRegenAccumulator(float value) {
        this.bloodRegenAccumulator = Math.max(0.0f, Math.min(1.0f, value));
    }

    @Override public synchronized int getBloodOxygen() { return Math.round(bloodOxygen); }
    @Override public synchronized void setBloodOxygen(int value) { this.bloodOxygen = Math.max(0.0f, Math.min(100.0f, (float) value)); }
    @Override public synchronized float getBloodOxygenFloat() { return bloodOxygen; }
    @Override public synchronized void setBloodOxygenFloat(float value) { this.bloodOxygen = Math.max(0.0f, Math.min(100.0f, value)); }

    @Override public synchronized int getBrainHealth() { return Math.round(brainHealth); }
    @Override public synchronized void setBrainHealth(int value) { this.brainHealth = Math.max(0.0f, Math.min(100.0f, (float) value)); }
    @Override public synchronized float getBrainHealthFloat() { return brainHealth; }
    @Override public synchronized void setBrainHealthFloat(float value) { this.brainHealth = Math.max(0.0f, Math.min(100.0f, value)); }
    @Override public synchronized int getBrainHealthRegenTimer() { return brainHealthRegenTimer; }
    @Override public synchronized void setBrainHealthRegenTimer(int seconds) { this.brainHealthRegenTimer = Math.max(0, seconds); }

    @Override public synchronized int getConsciousness() { return Math.round(consciousness); }
    @Override public synchronized void setConsciousness(int value) { this.consciousness = Math.max(0.0f, Math.min(100.0f, (float) value)); }
    @Override public synchronized float getConsciousnessFloat() { return consciousness; }
    @Override public synchronized void setConsciousnessFloat(float value) { this.consciousness = Math.max(0.0f, Math.min(100.0f, value)); }

    @Override public synchronized int getHeartRate() { return heartRate; }
    @Override public synchronized void setHeartRate(int value) { this.heartRate = Math.max(0, value); }

    @Override public synchronized int getRespirationRate() { return respirationRate; }
    @Override public synchronized void setRespirationRate(int value) { this.respirationRate = Math.max(0, value); }

    @Override public synchronized boolean isUnderwater() { return underwater; }
    @Override public synchronized void setUnderwater(boolean underwater) { this.underwater = underwater; }

    @Override public synchronized int getUnderwaterTimer() { return underwaterTimer; }
    @Override public synchronized void setUnderwaterTimer(int seconds) { this.underwaterTimer = Math.max(0, seconds); }

    @Override public synchronized boolean isInVFib() { return inVFib; }
    @Override public synchronized void setVFib(boolean vfib) { this.inVFib = vfib; }

    @Override public synchronized boolean isCardiacArrest() { return cardiacArrest; }
    @Override public synchronized void setCardiacArrest(boolean arrest) { this.cardiacArrest = arrest; }

    @Override public synchronized int getVfibTimer() { return vfibTimer; }
    @Override public synchronized void setVfibTimer(int seconds) { this.vfibTimer = Math.max(0, seconds); }

    @Override public synchronized int getBradyTimer() { return bradyTimer; }
    @Override public synchronized void setBradyTimer(int seconds) { this.bradyTimer = Math.max(0, seconds); }

    @Override public synchronized int getHypoxiaTimer() { return hypoxiaTimer; }
    @Override public synchronized void setHypoxiaTimer(int seconds) { this.hypoxiaTimer = Math.max(0, seconds); }

    @Override public synchronized int getPainDecayTimer() { return painDecayTimer; }
    @Override public synchronized void setPainDecayTimer(int seconds) { this.painDecayTimer = Math.max(0, seconds); }

    @Override public synchronized float getExerciseIntensity() { return exerciseIntensity; }
    @Override public synchronized void setExerciseIntensity(float intensity) {
        this.exerciseIntensity = Math.max(0.0f, Math.min(1.0f, intensity));
    }

    @Override public synchronized float getExerciseContribution() { return exerciseContribution; }
    @Override public synchronized void setExerciseContribution(float contribution) {
        this.exerciseContribution = Math.max(0.0f, contribution);
    }

    @Override public synchronized int getRunningTimer() { return runningTimer; }
    @Override public synchronized void setRunningTimer(int seconds) { this.runningTimer = Math.max(0, seconds); }

    @Override public synchronized int getAttackTimer() { return attackTimer; }
    @Override public synchronized void setAttackTimer(int seconds) { this.attackTimer = Math.max(0, seconds); }

    @Override public synchronized int getHeartEffectTimer() { return heartEffectTimer; }
    @Override public synchronized void setHeartEffectTimer(int seconds) { this.heartEffectTimer = Math.max(0, seconds); }

    @Override public synchronized boolean isHeartEffectSevere() { return heartEffectSevere; }
    @Override public synchronized void setHeartEffectSevere(boolean severe) { this.heartEffectSevere = severe; }

    @Override public synchronized boolean isBradycardiaActive() { return bradycardiaActive; }
    @Override public synchronized void setBradycardiaActive(boolean active) { this.bradycardiaActive = active; }

    @Override public synchronized boolean isBradycardiaSevere() { return bradycardiaSevere; }
    @Override public synchronized void setBradycardiaSevere(boolean severe) { this.bradycardiaSevere = severe; }

    @Override public synchronized int getOpioidConcentration() { return opioidConcentration; }
    @Override public synchronized void setOpioidConcentration(int value) {
        this.opioidConcentration = Math.max(0, Math.min(100, value));
    }
    @Override public synchronized boolean isInCriticalState() { return inCriticalState; }
    @Override public synchronized void setInCriticalState(boolean critical) { this.inCriticalState = critical; }
    @Override public synchronized int getCriticalTimer() { return criticalTimer; }
    @Override public synchronized void setCriticalTimer(int seconds) { this.criticalTimer = Math.max(0, seconds); }

    @Override public synchronized float getChestBloodAccumulation() { return chestBloodAccumulation; }
    @Override public synchronized void setChestBloodAccumulation(float value) { this.chestBloodAccumulation = Math.max(0.0f, Math.min(2000.0f, value)); }
    @Override public synchronized int getHemothoraxLevel() { return hemothoraxLevel; }
    @Override public synchronized void setHemothoraxLevel(int value) { this.hemothoraxLevel = Math.max(0, Math.min(4, value)); }
    @Override public synchronized int getHemothoraxVomitTimer() { return hemothoraxVomitTimer; }
    @Override public synchronized void setHemothoraxVomitTimer(int seconds) { this.hemothoraxVomitTimer = Math.max(0, seconds); }

    @Override public synchronized float getLungContusion() { return lungContusion; }
    @Override public synchronized void setLungContusion(float value) {
        this.lungContusion = Math.max(0.0f, Math.min(100.0f, value));
    }
    @Override public synchronized int getFractureTraumaTimer() { return fractureTraumaTimer; }
    @Override public synchronized void setFractureTraumaTimer(int seconds) {
        this.fractureTraumaTimer = Math.max(0, seconds);
    }

    @Override public synchronized int getIntracranialBleedRate() { return intracranialBleedRate; }
    @Override public synchronized void setIntracranialBleedRate(int value) { this.intracranialBleedRate = Math.max(0, Math.min(30, value)); }
    @Override public synchronized float getIntracranialAccumulation() { return intracranialAccumulation; }
    @Override public synchronized void setIntracranialAccumulation(float value) { this.intracranialAccumulation = Math.max(0.0f, Math.min(150.0f, value)); }
    @Override public synchronized int getIntracranialLevel() { return intracranialLevel; }
    @Override public synchronized void setIntracranialLevel(int value) { this.intracranialLevel = Math.max(0, Math.min(4, value)); }
    @Override public synchronized int getIntracranialOnsetTimer() { return intracranialOnsetTimer; }
    @Override public synchronized void setIntracranialOnsetTimer(int seconds) { this.intracranialOnsetTimer = Math.max(0, seconds); }

    // ====== 血压与血流动力学 Getter / Setter ======
    @Override public synchronized int getSystolicBP() { return systolicBP; }
    @Override public synchronized void setSystolicBP(int value) { this.systolicBP = value; }
    @Override public synchronized int getDiastolicBP() { return diastolicBP; }
    @Override public synchronized void setDiastolicBP(int value) { this.diastolicBP = value; }
    @Override public synchronized float getMeanArterialPressure() { return meanArterialPressure; }
    @Override public synchronized void setMeanArterialPressure(float value) { this.meanArterialPressure = value; }
    @Override public synchronized float getPeripheralResistance() { return peripheralResistance; }
    @Override public synchronized void setPeripheralResistance(float value) { this.peripheralResistance = value; }
    @Override public synchronized float getCardiacOutput() { return cardiacOutput; }
    @Override public synchronized void setCardiacOutput(float value) { this.cardiacOutput = value; }

    @Override public synchronized int getSirsLevel() { return sirsLevel; }
    @Override public synchronized void setSirsLevel(int level) { this.sirsLevel = Math.max(0, Math.min(4, level)); }
    @Override public synchronized int getSirsProgressionTimer() { return sirsProgressionTimer; }
    @Override public synchronized void setSirsProgressionTimer(int timer) { this.sirsProgressionTimer = Math.max(0, timer); }
    @Override public synchronized int getSirsRecoveryTimer() { return sirsRecoveryTimer; }
    @Override public synchronized void setSirsRecoveryTimer(int timer) { this.sirsRecoveryTimer = Math.max(0, timer); }
    @Override public synchronized boolean isSirsActive() { return sirsActive; }
    @Override public synchronized void setSirsActive(boolean active) { this.sirsActive = active; }

    // ====== 中毒系统 ======
    @Override public synchronized float getToxinValue() { return toxinValue; }
    @Override public synchronized void setToxinValue(float value) { this.toxinValue = Math.max(0.0f, Math.min(100.0f, value)); }
    @Override public synchronized void addToxinValue(float amount) { this.toxinValue = Math.max(0.0f, Math.min(100.0f, this.toxinValue + amount)); }

    // ====== 烧伤系统（全身） ======
    @Override public synchronized float getBurnTBSA() { return burnTBSA; }
    @Override public synchronized void setBurnTBSA(float tbsa) { this.burnTBSA = Math.max(0.0f, Math.min(1.0f, tbsa)); }
    @Override public synchronized float getBurnEdema() { return burnEdema; }
    @Override public synchronized void setBurnEdema(float edema) { this.burnEdema = Math.max(0.0f, Math.min(5000.0f, edema)); }
    @Override public synchronized float getInhalationInjury() { return inhalationInjury; }
    @Override public synchronized void setInhalationInjury(float severity) { this.inhalationInjury = Math.max(0.0f, Math.min(1.0f, severity)); }

    @Override public synchronized int getBrainDamageTriggerTimer() { return brainDamageTriggerTimer; }
    @Override public synchronized void setBrainDamageTriggerTimer(int seconds) { this.brainDamageTriggerTimer = Math.max(0, seconds); }
    @Override public synchronized int getBrainDamageTriggerCooldown() { return brainDamageTriggerCooldown; }
    @Override public synchronized void setBrainDamageTriggerCooldown(int seconds) { this.brainDamageTriggerCooldown = Math.max(0, seconds); }
    @Override public synchronized boolean hasForcedTrigger90() { return forcedTrigger90; }
    @Override public synchronized void setForcedTrigger90(boolean value) { this.forcedTrigger90 = value; }
    @Override public synchronized boolean hasForcedTrigger60() { return forcedTrigger60; }
    @Override public synchronized void setForcedTrigger60(boolean value) { this.forcedTrigger60 = value; }
    @Override public synchronized boolean hasForcedTrigger30() { return forcedTrigger30; }
    @Override public synchronized void setForcedTrigger30(boolean value) { this.forcedTrigger30 = value; }

    @Override public synchronized int getAddictionLevel() { return addictionLevel; }
    @Override public synchronized void setAddictionLevel(int value) {
        this.addictionLevel = Math.max(0, Math.min(100, value));
    }

    @Override
    public synchronized int getTotalPainLevel() {
        int total = 0;
        for (LimbData ld : limbs.values()) {
            total += ld.getPainLevel();
        }
        return total;
    }

    // ★★★★★ 体感疼痛（0~55）★★★★★
    @Override
    public synchronized int getPerceivedPain() {
        int maxPain = 0;
        int sumOther = 0;
        for (LimbData ld : limbs.values()) {
            int pain = ld.getPainLevel();
            if (pain > maxPain) {
                sumOther += maxPain;  // 将旧的最大值计入“其他”总和
                maxPain = pain;
            } else {
                sumOther += pain;
            }
        }
        // 体感疼痛 = 最高值 × 1.5 + 其余值之和 × 0.8
        return (int)(maxPain * 1.5f + sumOther * 0.8f);
    }

    @Override public synchronized LimbData getLimb(Limb limb) { return limbs.get(limb); }
    @Override public synchronized Map<Limb, LimbData> getAllLimbs() { return limbs; }

    @Override
    public synchronized void copyFrom(MedicalData other) {
        this.bloodVolume = other.getBloodVolume();
        this.bloodLossAccumulator = other.getBloodLossAccumulator();
        this.bloodRegenAccumulator = other.getBloodRegenAccumulator();
        this.bloodOxygen = other.getBloodOxygenFloat();
        this.brainHealth = other.getBrainHealthFloat();
        this.brainHealthRegenTimer = other.getBrainHealthRegenTimer();
        this.consciousness = other.getConsciousnessFloat();
        this.heartRate = other.getHeartRate();
        this.respirationRate = other.getRespirationRate();
        this.underwater = other.isUnderwater();
        this.underwaterTimer = other.getUnderwaterTimer();
        this.inVFib = other.isInVFib();
        this.cardiacArrest = other.isCardiacArrest();
        this.inCriticalState = other.isInCriticalState();
        this.criticalTimer = other.getCriticalTimer();
        this.chestBloodAccumulation = other.getChestBloodAccumulation();
        this.hemothoraxLevel = other.getHemothoraxLevel();
        this.hemothoraxVomitTimer = other.getHemothoraxVomitTimer();
        this.lungContusion = other.getLungContusion();
        this.fractureTraumaTimer = other.getFractureTraumaTimer();
        this.intracranialBleedRate = other.getIntracranialBleedRate();
        this.intracranialAccumulation = other.getIntracranialAccumulation();
        this.intracranialLevel = other.getIntracranialLevel();
        this.intracranialOnsetTimer = other.getIntracranialOnsetTimer();
        this.systolicBP = other.getSystolicBP();
        this.diastolicBP = other.getDiastolicBP();
        this.meanArterialPressure = other.getMeanArterialPressure();
        this.peripheralResistance = other.getPeripheralResistance();
        this.cardiacOutput = other.getCardiacOutput();
        this.sirsLevel = other.getSirsLevel();
        this.sirsProgressionTimer = other.getSirsProgressionTimer();
        this.sirsRecoveryTimer = other.getSirsRecoveryTimer();
        this.sirsActive = other.isSirsActive();
        this.toxinValue = other.getToxinValue();
        this.brainDamageTriggerTimer = other.getBrainDamageTriggerTimer();
        this.brainDamageTriggerCooldown = other.getBrainDamageTriggerCooldown();
        this.forcedTrigger90 = other.hasForcedTrigger90();
        this.forcedTrigger60 = other.hasForcedTrigger60();
        this.forcedTrigger30 = other.hasForcedTrigger30();
        this.vfibTimer = other.getVfibTimer();
        this.bradyTimer = other.getBradyTimer();
        this.hypoxiaTimer = other.getHypoxiaTimer();
        this.painDecayTimer = other.getPainDecayTimer();
        this.exerciseIntensity = other.getExerciseIntensity();
        this.exerciseContribution = other.getExerciseContribution();
        this.runningTimer = other.getRunningTimer();
        this.attackTimer = other.getAttackTimer();
        this.heartEffectTimer = other.getHeartEffectTimer();
        this.heartEffectSevere = other.isHeartEffectSevere();
        this.bradycardiaActive = other.isBradycardiaActive();
        this.bradycardiaSevere = other.isBradycardiaSevere();
        this.opioidConcentration = other.getOpioidConcentration();
        this.addictionLevel = other.getAddictionLevel();
        this.burnTBSA = other.getBurnTBSA();
        this.burnEdema = other.getBurnEdema();
        this.inhalationInjury = other.getInhalationInjury();
        for (Limb limb : Limb.values()) {
            LimbData src = other.getLimb(limb);
            LimbData dst = limbs.get(limb);
            dst.setBleeding(src.getBleeding());
            dst.setPainLevel(src.getPainLevel());
            dst.setBandaged(src.isBandaged());
            dst.setTourniqueted(src.isTourniqueted());
            dst.setInfection(src.getInfection());
            dst.setInfectionAgeTimer(src.getInfectionAgeTimer());
            dst.setOpenWound(src.hasOpenWound());
            dst.setInternalBleedingRate(src.getInternalBleedingRate());
            dst.setFracture(src.hasFracture());
            dst.setFractureTimer(src.getFractureTimer());
            dst.setOpenFracture(src.isOpenFracture());
            dst.setSplint(src.hasSplint());
            dst.setDislocation(src.hasDislocation());
            dst.setPerforation(src.hasPerforation());
            dst.setNaturalClottingTimer(src.getNaturalClottingTimer());
            dst.setClottingStartRate(src.getClottingStartRate());
            dst.setInternalHealingTimer(src.getInternalHealingTimer());
            dst.setDisinfectantTimer(src.getDisinfectantTimer());
            dst.setInfectionAccumulator(src.getInfectionAccumulator());
            dst.setBurnLoad(src.getBurnLoad());
            dst.setBurnConv(src.getBurnConv());
            dst.setBurnDegree(src.getBurnDegree());
            dst.setBurnArea(src.getBurnArea());
            dst.setBurnAge(src.getBurnAge());
            dst.setBurnHeatTimer(src.getBurnHeatTimer());
            dst.setBurnHealTimer(src.getBurnHealTimer());
            dst.setAmputated(src.isAmputated());
        }
    }

    // ====== 序列化 ======
    @Override
    public synchronized CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("bloodVolume", bloodVolume);
        tag.putFloat("bloodLossAccumulator", bloodLossAccumulator);
        tag.putFloat("bloodRegenAccumulator", bloodRegenAccumulator);
        tag.putFloat("bloodOxygen", bloodOxygen);
        tag.putFloat("brainHealth", brainHealth);
        tag.putInt("brainHealthRegenTimer", brainHealthRegenTimer);
        tag.putFloat("consciousness", consciousness);
        tag.putInt("heartRate", heartRate);
        tag.putInt("respirationRate", respirationRate);
        tag.putBoolean("underwater", underwater);
        tag.putInt("underwaterTimer", underwaterTimer);
        tag.putBoolean("inVFib", inVFib);
        tag.putBoolean("cardiacArrest", cardiacArrest);
        tag.putInt("vfibTimer", vfibTimer);
        tag.putInt("bradyTimer", bradyTimer);
        tag.putInt("hypoxiaTimer", hypoxiaTimer);
        tag.putInt("painDecayTimer", painDecayTimer);
        tag.putFloat("exerciseIntensity", exerciseIntensity);
        tag.putFloat("exerciseContribution", exerciseContribution);
        tag.putInt("runningTimer", runningTimer);
        tag.putInt("attackTimer", attackTimer);
        tag.putInt("heartEffectTimer", heartEffectTimer);
        tag.putBoolean("heartEffectSevere", heartEffectSevere);
        tag.putBoolean("bradycardiaActive", bradycardiaActive);
        tag.putBoolean("bradycardiaSevere", bradycardiaSevere);
        tag.putInt("opioidConcentration", opioidConcentration);
        tag.putInt("addictionLevel", addictionLevel);
        tag.putBoolean("inCriticalState", inCriticalState);
        tag.putInt("criticalTimer", criticalTimer);
        tag.putFloat("chestBloodAccumulation", chestBloodAccumulation);
        tag.putInt("hemothoraxLevel", hemothoraxLevel);
        tag.putInt("hemothoraxVomitTimer", hemothoraxVomitTimer);
        tag.putFloat("lungContusion", lungContusion);
        tag.putInt("fractureTraumaTimer", fractureTraumaTimer);
        tag.putInt("intracranialBleedRate", intracranialBleedRate);
        tag.putFloat("intracranialAccumulation", intracranialAccumulation);
        tag.putInt("intracranialLevel", intracranialLevel);
        tag.putInt("intracranialOnsetTimer", intracranialOnsetTimer);
        tag.putInt("systolicBP", systolicBP);
        tag.putInt("diastolicBP", diastolicBP);
        tag.putFloat("meanArterialPressure", meanArterialPressure);
        tag.putFloat("peripheralResistance", peripheralResistance);
        tag.putFloat("cardiacOutput", cardiacOutput);
        tag.putInt("sirsLevel", sirsLevel);
        tag.putInt("sirsProgressionTimer", sirsProgressionTimer);
        tag.putInt("sirsRecoveryTimer", sirsRecoveryTimer);
        tag.putBoolean("sirsActive", sirsActive);
        tag.putFloat("toxinValue", toxinValue);
        tag.putInt("brainDamageTriggerTimer", brainDamageTriggerTimer);
        tag.putInt("brainDamageTriggerCooldown", brainDamageTriggerCooldown);
        tag.putBoolean("forcedTrigger90", forcedTrigger90);
        tag.putBoolean("forcedTrigger60", forcedTrigger60);
        tag.putBoolean("forcedTrigger30", forcedTrigger30);
        for (Limb limb : Limb.values()) {
            LimbData ld = limbs.get(limb);
            CompoundTag limbTag = new CompoundTag();
            limbTag.putInt("bleeding", ld.getBleeding());
            limbTag.putInt("painLevel", ld.getPainLevel());
            limbTag.putBoolean("bandaged", ld.isBandaged());
            limbTag.putBoolean("tourniqueted", ld.isTourniqueted());
            limbTag.putInt("infection", ld.getInfection());
            limbTag.putInt("infectionAgeTimer", ld.getInfectionAgeTimer());
            limbTag.putBoolean("hasOpenWound", ld.hasOpenWound());
            limbTag.putInt("internalBleeding", ld.getInternalBleedingRate());
            limbTag.putBoolean("fracture", ld.hasFracture());
            limbTag.putInt("fractureTimer", ld.getFractureTimer());
            limbTag.putBoolean("openFracture", ld.isOpenFracture());
            limbTag.putBoolean("splint", ld.hasSplint());
            limbTag.putBoolean("dislocation", ld.hasDislocation());
            limbTag.putBoolean("perforation", ld.hasPerforation());
            limbTag.putInt("naturalClottingTimer", ld.getNaturalClottingTimer());
            limbTag.putInt("clottingStartRate", ld.getClottingStartRate());
            limbTag.putInt("internalHealingTimer", ld.getInternalHealingTimer());
            limbTag.putInt("disinfectantTimer", ld.getDisinfectantTimer());
            limbTag.putFloat("infectionAccumulator", ld.getInfectionAccumulator());
            limbTag.putFloat("burnLoad", ld.getBurnLoad());
            limbTag.putFloat("burnConv", ld.getBurnConv());
            limbTag.putInt("burnDegree", ld.getBurnDegree());
            limbTag.putFloat("burnArea", ld.getBurnArea());
            limbTag.putInt("burnAge", ld.getBurnAge());
            limbTag.putInt("burnHeatTimer", ld.getBurnHeatTimer());
            limbTag.putInt("burnHealTimer", ld.getBurnHealTimer());
            limbTag.putBoolean("amputated", ld.isAmputated());
            tag.put(limb.name(), limbTag);
        }
        return tag;
    }

    public CompoundTag serializeNBT() {
        return serializeNBT(null);
    }

    @Override
    public synchronized void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        bloodVolume = tag.getInt("bloodVolume");
        bloodLossAccumulator = tag.contains("bloodLossAccumulator") ? tag.getFloat("bloodLossAccumulator") : 0.0f;
        bloodRegenAccumulator = tag.contains("bloodRegenAccumulator") ? tag.getFloat("bloodRegenAccumulator") : 0.0f;
        bloodOxygen = tag.contains("bloodOxygen", 5) ? tag.getFloat("bloodOxygen") : (float) tag.getInt("bloodOxygen");
        brainHealth = tag.contains("brainHealth", 5) ? tag.getFloat("brainHealth") : (float) tag.getInt("brainHealth");
        brainHealthRegenTimer = tag.getInt("brainHealthRegenTimer");
        consciousness = tag.contains("consciousness", 5) ? tag.getFloat("consciousness") : (float) tag.getInt("consciousness");
        heartRate = tag.getInt("heartRate");
        respirationRate = tag.getInt("respirationRate");
        underwater = tag.getBoolean("underwater");
        underwaterTimer = tag.contains("underwaterTimer") ? tag.getInt("underwaterTimer") : 0;
        inVFib = tag.getBoolean("inVFib");
        cardiacArrest = tag.getBoolean("cardiacArrest");
        vfibTimer = tag.getInt("vfibTimer");
        bradyTimer = tag.getInt("bradyTimer");
        hypoxiaTimer = tag.contains("hypoxiaTimer") ? tag.getInt("hypoxiaTimer") : 0;
        painDecayTimer = tag.contains("painDecayTimer") ? tag.getInt("painDecayTimer") : 0;
        exerciseIntensity = tag.contains("exerciseIntensity") ? tag.getFloat("exerciseIntensity") : 0.0f;
        exerciseContribution = tag.contains("exerciseContribution") ? tag.getFloat("exerciseContribution") : 0.0f;
        runningTimer = tag.contains("runningTimer") ? tag.getInt("runningTimer") : 0;
        attackTimer = tag.contains("attackTimer") ? tag.getInt("attackTimer") : 0;
        heartEffectTimer = tag.getInt("heartEffectTimer");
        heartEffectSevere = tag.getBoolean("heartEffectSevere");
        bradycardiaActive = tag.getBoolean("bradycardiaActive");
        bradycardiaSevere = tag.getBoolean("bradycardiaSevere");
        opioidConcentration = tag.contains("opioidConcentration") ? tag.getInt("opioidConcentration") : 0;
        addictionLevel = tag.contains("addictionLevel") ? tag.getInt("addictionLevel") : 0;
        inCriticalState = tag.getBoolean("inCriticalState");
        criticalTimer = tag.getInt("criticalTimer");
        chestBloodAccumulation = tag.getFloat("chestBloodAccumulation");
        hemothoraxLevel = tag.getInt("hemothoraxLevel");
        hemothoraxVomitTimer = tag.getInt("hemothoraxVomitTimer");
        lungContusion = tag.contains("lungContusion") ? tag.getFloat("lungContusion") : 0.0f;
        fractureTraumaTimer = tag.contains("fractureTraumaTimer") ? tag.getInt("fractureTraumaTimer") : 0;
        intracranialBleedRate = tag.getInt("intracranialBleedRate");
        intracranialAccumulation = tag.getFloat("intracranialAccumulation");
        intracranialLevel = tag.getInt("intracranialLevel");
        intracranialOnsetTimer = tag.getInt("intracranialOnsetTimer");
        systolicBP = tag.contains("systolicBP") ? tag.getInt("systolicBP") : 120;
        diastolicBP = tag.contains("diastolicBP") ? tag.getInt("diastolicBP") : 80;
        meanArterialPressure = tag.contains("meanArterialPressure") ? tag.getFloat("meanArterialPressure") : 93.0f;
        peripheralResistance = tag.contains("peripheralResistance") ? tag.getFloat("peripheralResistance") : 1.0f;
        cardiacOutput = tag.contains("cardiacOutput") ? tag.getFloat("cardiacOutput") : 5.25f;
        sirsLevel = tag.contains("sirsLevel") ? tag.getInt("sirsLevel") : 0;
        sirsProgressionTimer = tag.contains("sirsProgressionTimer") ? tag.getInt("sirsProgressionTimer") : 0;
        sirsRecoveryTimer = tag.contains("sirsRecoveryTimer") ? tag.getInt("sirsRecoveryTimer") : 0;
        sirsActive = tag.contains("sirsActive") ? tag.getBoolean("sirsActive") : false;
        toxinValue = tag.contains("toxinValue") ? tag.getFloat("toxinValue") : 0.0f;
        burnTBSA = tag.contains("burnTBSA") ? tag.getFloat("burnTBSA") : 0.0f;
        burnEdema = tag.contains("burnEdema") ? tag.getFloat("burnEdema") : 0.0f;
        inhalationInjury = tag.contains("inhalationInjury") ? tag.getFloat("inhalationInjury") : 0.0f;
        brainDamageTriggerTimer = tag.contains("brainDamageTriggerTimer") ? tag.getInt("brainDamageTriggerTimer") : 0;
        brainDamageTriggerCooldown = tag.contains("brainDamageTriggerCooldown") ? tag.getInt("brainDamageTriggerCooldown") : 0;
        forcedTrigger90 = tag.contains("forcedTrigger90") ? tag.getBoolean("forcedTrigger90") : false;
        forcedTrigger60 = tag.contains("forcedTrigger60") ? tag.getBoolean("forcedTrigger60") : false;
        forcedTrigger30 = tag.contains("forcedTrigger30") ? tag.getBoolean("forcedTrigger30") : false;
        for (Limb limb : Limb.values()) {
            CompoundTag limbTag = tag.getCompound(limb.name());
            if (limbTag.isEmpty()) continue;
            LimbData ld = limbs.get(limb);
            ld.setBleeding(limbTag.getInt("bleeding"));
            ld.setPainLevel(limbTag.getInt("painLevel"));
            ld.setBandaged(limbTag.getBoolean("bandaged"));
            ld.setTourniqueted(limbTag.getBoolean("tourniqueted"));
            ld.setInfection(limbTag.getInt("infection"));
            ld.setInfectionAgeTimer(limbTag.contains("infectionAgeTimer") ? limbTag.getInt("infectionAgeTimer") : 0);
            ld.setOpenWound(limbTag.contains("hasOpenWound") ? limbTag.getBoolean("hasOpenWound") : false);
            ld.setInternalBleedingRate(limbTag.getInt("internalBleeding"));
            ld.setFracture(limbTag.getBoolean("fracture"));
            ld.setFractureTimer(limbTag.contains("fractureTimer") ? limbTag.getInt("fractureTimer") : 0);
            ld.setOpenFracture(limbTag.contains("openFracture") ? limbTag.getBoolean("openFracture") : false);
            ld.setSplint(limbTag.contains("splint") ? limbTag.getBoolean("splint") : false);
            ld.setDislocation(limbTag.getBoolean("dislocation"));
            ld.setPerforation(limbTag.contains("perforation") ? limbTag.getBoolean("perforation") : false);
            ld.setNaturalClottingTimer(limbTag.getInt("naturalClottingTimer"));
            ld.setClottingStartRate(limbTag.getInt("clottingStartRate"));
            ld.setInternalHealingTimer(limbTag.getInt("internalHealingTimer"));
            ld.setDisinfectantTimer(limbTag.contains("disinfectantTimer") ? limbTag.getInt("disinfectantTimer") : 0);
            ld.setInfectionAccumulator(limbTag.contains("infectionAccumulator") ? limbTag.getFloat("infectionAccumulator") : 0.0f);
            ld.setBurnLoad(limbTag.contains("burnLoad") ? limbTag.getFloat("burnLoad") : 0.0f);
            ld.setBurnConv(limbTag.contains("burnConv") ? limbTag.getFloat("burnConv") : 0.0f);
            ld.setBurnDegree(limbTag.contains("burnDegree") ? limbTag.getInt("burnDegree") : 0);
            ld.setBurnArea(limbTag.contains("burnArea") ? limbTag.getFloat("burnArea") : 0.0f);
            ld.setBurnAge(limbTag.contains("burnAge") ? limbTag.getInt("burnAge") : 0);
            ld.setBurnHeatTimer(limbTag.contains("burnHeatTimer") ? limbTag.getInt("burnHeatTimer") : 0);
            ld.setBurnHealTimer(limbTag.contains("burnHealTimer") ? limbTag.getInt("burnHealTimer") : 0);
            ld.setAmputated(limbTag.contains("amputated") ? limbTag.getBoolean("amputated") : false);
        }
    }

    public void deserializeNBT(CompoundTag tag) {
        deserializeNBT(null, tag);
    }

    // ====== LimbDataImpl 内部类 ======
    private static class LimbDataImpl implements LimbData {
        private int bleeding = 0;
        private int painLevel = 0;
        private boolean bandaged = false;
        private boolean tourniqueted = false;
        private int infection = 0;
        private int infectionAgeTimer = 0;
        private boolean hasOpenWound = false;
        private int internalBleeding = 0;
        private boolean fracture = false;
        private int fractureTimer = 0;
        private boolean openFracture = false;
        private boolean splint = false;
        private boolean dislocation = false;
        private boolean perforation = false;
        private int naturalClottingTimer = 0;
        private int clottingStartRate = 0;
        private int internalHealingTimer = 0;
        private int disinfectantTimer = 0;
        private float infectionAccumulator = 0.0f;

        // ====== 断肢（仅四肢；头/躯干永不截肢） ======
        private boolean amputated = false;

        // ====== 烧伤 ======
        private float burnLoad = 0.0f;      // 热力期缓冲负荷（未定型）
        private float burnConv = 0.0f;       // 已转正负荷（定型判定用）
        private int burnDegree = 0;         // 0无 1=I度 2=浅II 3=深II 4=III度
        private float burnArea = 0.0f;      // 该肢体烧伤面积占比 0-1
        private int burnAge = 0;            // 伤龄（秒）
        private int burnHeatTimer = 0;      // 热力期倒计时（可冷却冲洗）
        private int burnHealTimer = 0;      // 愈合计时

        @Override public synchronized int getBleeding() { return bleeding; }
        @Override public synchronized void setBleeding(int value) { this.bleeding = Math.max(0, value); }

        @Override public synchronized int getPainLevel() { return painLevel; }
        @Override public synchronized void setPainLevel(int value) { this.painLevel = Math.max(0, Math.min(10, value)); }

        @Override public synchronized boolean isBandaged() { return bandaged; }
        @Override public synchronized void setBandaged(boolean bandaged) { this.bandaged = bandaged; }

        @Override public synchronized boolean isTourniqueted() { return tourniqueted; }
        @Override public synchronized void setTourniqueted(boolean tourniqueted) { this.tourniqueted = tourniqueted; }

        @Override public synchronized int getInfection() { return infection; }
        @Override public synchronized void setInfection(int value) { this.infection = Math.max(0, Math.min(100, value)); }

        @Override public synchronized int getInfectionAgeTimer() { return infectionAgeTimer; }
        @Override public synchronized void setInfectionAgeTimer(int timer) { this.infectionAgeTimer = Math.max(0, timer); }

        @Override public synchronized boolean hasOpenWound() { return hasOpenWound; }
        @Override public synchronized void setOpenWound(boolean open) { this.hasOpenWound = open; }

        @Override public synchronized int getInternalBleedingRate() { return internalBleeding; }
        @Override public synchronized void setInternalBleedingRate(int value) { this.internalBleeding = Math.max(0, value); }

        @Override public synchronized boolean hasFracture() { return fracture; }
        @Override public synchronized void setFracture(boolean fracture) { this.fracture = fracture; }

        @Override public synchronized int getFractureTimer() { return fractureTimer; }
        @Override public synchronized void setFractureTimer(int seconds) { this.fractureTimer = Math.max(0, seconds); }

        @Override public synchronized boolean isOpenFracture() { return openFracture; }
        @Override public synchronized void setOpenFracture(boolean open) { this.openFracture = open; }

        @Override public synchronized boolean hasSplint() { return splint; }
        @Override public synchronized void setSplint(boolean splinted) { this.splint = splinted; }

        @Override public synchronized boolean hasDislocation() { return dislocation; }
        @Override public synchronized void setDislocation(boolean dislocation) { this.dislocation = dislocation; }

        @Override public synchronized boolean hasPerforation() { return perforation; }
        @Override public synchronized void setPerforation(boolean perforation) { this.perforation = perforation; }

        @Override public synchronized int getNaturalClottingTimer() { return naturalClottingTimer; }
        @Override public synchronized void setNaturalClottingTimer(int seconds) { this.naturalClottingTimer = Math.max(0, seconds); }

        @Override public synchronized int getClottingStartRate() { return clottingStartRate; }
        @Override public synchronized void setClottingStartRate(int mLPerMin) { this.clottingStartRate = Math.max(0, mLPerMin); }

        @Override public synchronized int getInternalHealingTimer() { return internalHealingTimer; }
        @Override public synchronized void setInternalHealingTimer(int seconds) { this.internalHealingTimer = Math.max(0, seconds); }

        @Override public synchronized int getDisinfectantTimer() { return disinfectantTimer; }
        @Override public synchronized void setDisinfectantTimer(int seconds) {
            this.disinfectantTimer = Math.max(0, seconds);
        }
        @Override public synchronized float getInfectionAccumulator() { return infectionAccumulator; }
        @Override public synchronized void setInfectionAccumulator(float value) {
            this.infectionAccumulator = Math.max(0.0f, value);
        }

        @Override public synchronized boolean isAmputated() { return amputated; }
        @Override public synchronized void setAmputated(boolean amputated) { this.amputated = amputated; }

        @Override public synchronized float getBurnLoad() { return burnLoad; }
        @Override public synchronized void setBurnLoad(float load) { this.burnLoad = Math.max(0.0f, load); }
        @Override public synchronized float getBurnConv() { return burnConv; }
        @Override public synchronized void setBurnConv(float conv) { this.burnConv = Math.max(0.0f, conv); }
        @Override public synchronized int getBurnDegree() { return burnDegree; }
        @Override public synchronized void setBurnDegree(int degree) { this.burnDegree = Math.max(0, Math.min(4, degree)); }
        @Override public synchronized float getBurnArea() { return burnArea; }
        @Override public synchronized void setBurnArea(float area) { this.burnArea = Math.max(0.0f, Math.min(1.0f, area)); }
        @Override public synchronized int getBurnAge() { return burnAge; }
        @Override public synchronized void setBurnAge(int seconds) { this.burnAge = Math.max(0, seconds); }
        @Override public synchronized int getBurnHeatTimer() { return burnHeatTimer; }
        @Override public synchronized void setBurnHeatTimer(int seconds) { this.burnHeatTimer = Math.max(0, seconds); }
        @Override public synchronized int getBurnHealTimer() { return burnHealTimer; }
        @Override public synchronized void setBurnHealTimer(int seconds) { this.burnHealTimer = Math.max(0, seconds); }
        @Override public synchronized boolean hasBurn() { return burnDegree > 0 || burnLoad > 0.0f || burnConv > 0.0f; }

        @Override
        public synchronized int getColor() {
            int bleeding = this.bleeding;
            int internalBleed = this.internalBleeding;
            int pain = this.painLevel;
            if (bleeding == 0 && internalBleed == 0 && pain == 0) return 0xFF1B5E20;
            if (pain >= 7 || bleeding > 500 || internalBleed > 300) return 0xFFB71C1C;
            if (pain >= 4 || bleeding > 200 || internalBleed > 100) return 0xFFE65100;
            return 0xFFF57F17;
        }
    }
}