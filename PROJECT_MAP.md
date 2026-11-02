# PROJECT_MAP.md
本文件用于快速了解项目架构。AI 在开始任何任务前，先读这份文件即可，不要通读所有源码。

## 一、项目概述
- **模组名**：真实医疗系统 (Real Medical System)
- **平台**：NeoForge 1.21.1
- **包名**：com.FIYTP.realmedicalmod
- **核心思想**：模拟真实人体生理——出血→血容量→血氧→脑健康→死亡，以及心率、呼吸、疼痛、意识等系统的联动。

## 二、包结构
```text
com.FIYTP.realmedicalmod/
├── RealMedicalMod.java                     // 模组主类（注册 COMMON 配置文件 MedicalConfig.SPEC）
├── config/                                 // 模组配置层
│   └── MedicalConfig.java                  // ModConfigSpec 统一配置文件（全生理系统数值参数提取）
├── capability/                             // 数据存储层
│   ├── MedicalData.java                    // 数据接口
│   ├── MedicalDataImpl.java                // 数据实现（所有字段都在这）
│   └── MedicalDataCapability.java          // 附件注册
├── event/                                  // 事件处理器（异步）
│   ├── FallDamageHandler.java              // 坠落伤害（部位判定与平滑脑损伤）
│   ├── HitPartHandler.java                 // 攻击伤害（部位判定与平滑脑损伤）
│   ├── DrowningHandler.java                // 溺水伤害拦截与生理循环接管（血氧扣减与呼吸停止）
│   ├── JumpCancelHandler.java              // 意识归零或严重骨折禁止跳跃
│   ├── FractureMiningHandler.java          // 手臂骨折挖掘速度减益（PlayerEvent.BreakSpeed）
│   └── HealthBarRemover.java               // 隐藏原版血量（客户端）
├── loop/                                   // 主循环与模块
│   ├── MedicalMainLoop.java                // 主循环调度器
│   ├── UpdateModule.java                   // 模块接口
│   ├── BuffManager.java                    // Buff收集+仲裁+施加
│   ├── HeartRateModule.java                // 基础心率+失血+疼痛+意识+运动+阿片+呼吸窦性+库欣+压力反射代偿+SIRS炎症交感加成→心率
│   ├── BloodPressureModule.java            // 血压系统（SV/CO/TPR/TargetMAP/平滑MAP/脉压/收缩压/舒张压，受 SIRS 等级阶梯修正 TPR）
│   ├── BleedModule.java                    // 外出血→血容量（受 MAP 系数与解剖部位阻力修正）
│   ├── LocalInfectionModule.java           // 肢体局部感染（创伤窗口期触发、包扎减缓、潮湿/骨折加成、局部炎症演算）
│   ├── SystemicInfectionModule.java        // 全身感染与 SIRS 状态机（感染源判定、环境倍率时间晋级 1~4、感染清除后降级）
│   ├── SepsisEffectModule.java             // 脓毒症效应（SIRS 等级 2+ 毛细血管渗漏与有效循环血容量损失）
│   ├── InternalBleedingModule.java         // 钝伤内出血演算（自愈与失血转换）
│   ├── BloodVolumeRegenModule.java         // 血容量自然回升（无活动性失血+SIRS≤1+饱食≥阈值时按速率恢复，浮点累加器结算）
│   ├── FractureModule.java                 // 骨折模块（夹板愈合与开放性骨折感染加速）
│   ├── FractureTraumaModule.java           // 骨折二次创伤（未固定活动加剧内外出血与疼痛、倒退愈合）
│   ├── LimbEffectModule.java               // 肢体骨折 Debuff 动态属性修改器（移速/攻击力）
│   ├── HemothoraxModule.java               // 胸腔积血与分级呼吸/运动抑制
│   ├── IntracranialHemorrhageModule.java   // 颅内出血与清醒期/库欣综合征/脑疝
│   ├── NaturalClottingModule.java          // 自然止血
│   ├── BloodOxygenModule.java              // 血容量与呼吸驱动血氧恢复，承受 SIRS 肺弥散障碍血氧惩罚
│   ├── BrainHealthModule.java              // 血氧+极低血压/高血压脑病→脑健康
│   ├── BrainHealthRegenModule.java         // 脑健康每分钟+1无条件缓慢自动恢复
│   ├── ConsciousnessModule.java            // 疼痛+血氧+低灌注/高血压危象惩罚→意识（呼吸惩罚已归口血氧传导）
│   ├── CardiacArrestModule.java            // 心脏骤停/室颤检测
│   ├── RespirationModule.java              // 呼吸频率（含 SIRS 代谢与酸中毒加成）
│   ├── PainDecayModule.java                // 疼痛自然衰减
│   ├── PainSuggestionModule.java           // 疼痛→Buff建议（静态方法）
│   ├── ConsciousnessSuggestionModule.java  // 意识→Buff建议（静态方法）
│   ├── DeathModule.java                    // 脑死亡判定
│   └── BleedParticleModule.java            // 出血粒子效果
├── system/                                 // 工具类
│   ├── BleedingSystem.java                 // 出血量计算
│   └── PainSystem.java                     // 疼痛等级计算
├── hitbox/                                 // 命中检测
│   ├── DamageCategory.java                 // 伤害分类枚举（PIERCING, SLASHING, BLUNT, MIXED）
│   ├── DamageTypeHelper.java               // 伤害分类推断工具（基于消息ID与物品标签）
│   ├── HitPartDetector.java
│   ├── LimbHitbox.java
│   └── CoordinateTransformer.java
└── client/                                 // 客户端
    ├── KeyHandler.java                     // 按键绑定（H 键打开面板）
    ├── sound/                              // 客户端危机音效
    │   └── CrisisSoundManager.java         // 动态危机音效管理器（重伤氛围/不规则警报/倒计时升调平线/疼痛喘息心跳二重奏）
    ├── font/                               // 客户端字体效果
    │   └── BrainDamageFontHelper.java      // 脑损伤字体混淆与扭曲运算辅助类
    └── gui/
        ├── MedicalScreen.java              // 医疗面板
        └── CriticalOverlay.java            // 危机全屏UI（骤停泛红/室颤闪烁/呼吸严重异常边缘红框/濒危倒计时）
mixin/
└── FontMixin.java                          // 注入 Font.drawInBatch 实现脑损伤文本乱码与抖动扭曲
```

## 三、主循环（MedicalMainLoop）
- **位置**：`loop/MedicalMainLoop.java`
- **频率**：独立线程，每秒执行一次
- **职责**：只负责按顺序调用各模块，不写任何业务逻辑

### 每秒执行流程（tick）
```text
遍历每个在线玩家：
  1. 更新运动强度与运动计时器（冲刺/跳跃/攻击检测）
  2. 创建 BuffManager
  3. 按顺序调用模块：
       heartRateModule.process(data);
       bloodPressureModule.process(data);
       bleedModule.process(data);
       localInfectionModule.process(player, data);
       systemicInfectionModule.process(player, data);
       sepsisEffectModule.process(data);
       internalBleedingModule.process(data);
       fractureModule.process(data);
       fractureTraumaModule.process(player, data);
       limbEffectModule.process(player, data);
       hemothoraxModule.process(player, data);
       intracranialHemorrhageModule.process(data);
       naturalClottingModule.process(data);
       bloodOxygenModule.process(data);
       brainHealthModule.process(data);
       brainHealthRegenModule.process(data);
       consciousnessModule.process(data);
       cardiacArrestModule.process(data);
       respirationModule.process(data);
       painDecayModule.process(data);
       BleedParticleModule.spawn(player, data);
  4. 收集 Buff 建议：
       PainSuggestionModule.suggest(data, buffManager);
       ConsciousnessSuggestionModule.suggest(data, buffManager);
  5. 血胸分级运动限制：
       等级 3+：强行打断冲刺
       等级 4：清除水平移动 + 给予极高迟缓
  6. 直接应用 Buff：buffManager.apply(player)
  7. deathModule.process(player, data);
  8. player.syncData(MedicalDataCapability.MEDICAL_DATA);  // 同步到客户端
```

### 模块接口
```java
public interface UpdateModule {
    void process(MedicalData data);
}
```

### 关键规则
- 所有新功能必须封装为独立模块，放在 `loop` 包下，实现 `UpdateModule` 接口
- 模块为单例（无状态），主循环中静态持有
- 不允许在主循环里写具体业务逻辑
- 需要读/写玩家数据的模块，直接操作 `MedicalData`

## 四、数据层（capability）

### MedicalData 全局字段
| 字段 | 类型 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- |
| bloodVolume | int | 5000 | 血容量 (mL)，最大 5000 |
| bloodOxygen | int | 98 | 血氧 (%)，0~100 |
| brainHealth | int | 100 | 脑健康度 (%)，0~100 |
| consciousness | int | 100 | 意识 (%)，0~100 |
| heartRate | int | 75 | 心率 (bpm)，-1=室颤，0=骤停 |
| respirationRate | int | 16 | 呼吸频率 (次/分) |
| inVFib | boolean | false | 是否室颤 |
| cardiacArrest | boolean | false | 是否心脏骤停 |
| vfibTimer | int | 0 | 室颤倒计时（心率≥220持续30秒触发） |
| bradyTimer | int | 0 | 心动过缓倒计时（心率<30持续30秒触发） |
| painDecayTimer | int | 0 | 疼痛衰减计时器 |
| exerciseIntensity | float | 0.0 | 运动强度 0~1 |
| exerciseContribution | float | 0.0 | 运动对心率的贡献 (bpm) |
| runningTimer | int | 0 | 连续奔跑秒数 |
| attackTimer | int | 0 | 攻击后运动增强计时器 |
| heartEffectTimer | int | 0 | 心脏效果计时器 |
| heartEffectSevere | boolean | false | 重度心动过速标记 |
| bradycardiaActive | boolean | false | 心动过缓标记 |
| bradycardiaSevere | boolean | false | 重度心动过缓标记 |
| opioidConcentration | int | 0 | 阿片浓度（预留） |
| addictionLevel | int | 0 | 成瘾值（预留） |
| inCriticalState | boolean | false | 濒危状态 |
| criticalTimer | int | 0 | 濒危倒计时（触发时=20） |
| internalBleeding | int | 0 | 肢体内出血速率 (mL/min) |
| chestBloodAccumulation | int | 0 | 胸腔积血量 (mL) |
| hemothoraxLevel | int | 0 | 血胸等级 0~4 |
| hemothoraxVomitTimer | int | 0 | 咳血计时器 |
| intracranialBleedRate | int | 0 | 颅内出血速率 (mL/min) |
| intracranialAccumulation | int | 0 | 颅内积血量 (mL) |
| intracranialLevel | int | 0 | 颅内出血等级 0~4 |
| intracranialOnsetTimer | int | 0 | 颅内出血清醒期计时（秒） |
| systolicBP | int | 120 | 收缩压 (mmHg)，生理范围 40~250 |
| diastolicBP | int | 80 | 舒张压 (mmHg)，生理范围 20~180 |
| meanArterialPressure | float | 93.0 | 平均动脉压 MAP (mmHg)，生理范围 20~180 |
| peripheralResistance | float | 1.0 | 外周血管总阻力 TPR，代偿期 1.6 / 失代偿期 0.8 |
| cardiacOutput | float | 5.25 | 心输出量 CO (L/min) |
| sirsLevel | int | 0 | 全身炎症反应等级 (0~4) |
| sirsProgressionTimer | int | 0 | SIRS 晋级计时器（秒） |
| sirsRecoveryTimer | int | 0 | SIRS 降级恢复计时器（秒） |
| sirsActive | boolean | false | 是否处于 SIRS 状态 |

### LimbData 肢体字段（每个肢体独立）
| 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| bleeding | int | 外出血速率 (mL/min) |
| painLevel | int | 疼痛等级 0~10 |
| bandaged | boolean | 是否包扎 |
| tourniqueted | boolean | 是否止血带 |
| infection | int | 感染值 0~100 |
| localInflammation | int | 局部炎症程度 0~100 |
| infectionAgeTimer | int | 伤口创伤年龄计时器（秒，用于窗口期判定） |
| internalBleeding | int | 内出血速率 (mL/min) |
| fracture | boolean | 是否骨折 |
| openFracture | boolean | 是否开放性骨折 |
| splint | boolean | 是否已使用夹板固定 |
| fractureTimer | int | 骨折愈合计时器（秒） |
| dislocation | boolean | 是否脱臼 |
| perforation | boolean | 是否穿孔/穿刺伤 |
| naturalClottingTimer | int | 自然止血计时器（秒） |
| internalHealingTimer | int | 内伤愈合计时器（秒） |

### 肢体枚举
```text
HEAD, CHEST, LEFT_ARM, RIGHT_ARM, LEFT_LEG, RIGHT_LEG
```

### 数据获取方式
```java
MedicalData data = player.getData(MedicalDataCapability.MEDICAL_DATA);
```
所有 getter/setter 都是 synchronized 的，多线程安全。

## 五、核心逻辑链

### 主链路（每秒执行）
```text
出血速率汇总 → 血容量减少 → 血氧变化 → 脑健康变化 → 脑死亡（玩家死亡）
```

### 关键规则
| 条件 | 触发结果 |
| :--- | :--- |
| 血容量 ≤ 3000 mL | 濒危状态（20秒倒计时） |
| 濒危倒计时归零 | 心脏骤停 + 意识归零 |
| 心率 ≥ 220 bpm 持续 30 秒 | 室颤 |
| 心率 ≥ 240 bpm 瞬时 | 心脏骤停 |
| 心率 < 30 bpm 持续 30 秒 | 心脏骤停 |
| 心脏骤停 | 意识不再恢复；血氧 -20%/秒；脑健康 -5%/秒 |
| 室颤 | 血氧 -10%/秒；脑健康 -2%/秒 |
| 脑健康 ≤ 0 | 玩家死亡 |
| 血胸等级 ≥ 3 | 禁止冲刺（player.setSprinting(false)） |
| 血胸等级 4 | 移动归零（清空水平速度 + 缓慢 255） |
| 颅内出血等级 3+（清醒期后） | 视神经受压失明（失明 I/II） + 呼吸不规则 |
| 颅内出血等级 2/3（清醒期后） | 库欣反应心率代偿（+15 / +25 bpm） |
| 颅内出血等级 4（清醒期后） | 急性脑疝/脑组织压迫（脑健康 -5/秒） |

### 心率计算（HeartRateModule）
```text
基础 75 bpm
+ 血容量修正（代偿-失代偿曲线，10~70 bpm）
+ 疼痛修正（疼痛等级 × 4）
+ 意识修正（意识越低心率越慢）
+ 运动贡献（由主循环动态更新）
+ 阿片修正（预留）
```

### 呼吸频率（RespirationModule）
```text
血氧 → 呼吸频率（代偿机制）：血氧越低呼吸越快，<50% 时断崖下跌
奔跑时间 → 呼吸频率积累（最高 +28 次/分）
意识 < 20 → 呼吸中枢抑制
```

### 体感疼痛（MedicalDataImpl.getPerceivedPain）
```text
体感疼痛 = 最高肢体疼痛 × 1.5 + 其余肢体疼痛之和 × 0.8  （范围 0~55）
```

### 意识变化（ConsciousnessModule）
```text
意识变化 = 1.0（强制恢复）- 体感疼痛/25 - 血氧惩罚
心脏骤停时意识不再恢复
```

## 六、配置系统与数值速查 (MedicalConfig)
所有数值常量与阈值已统一提取至 `com.FIYTP.realmedicalmod.config.MedicalConfig`，整合包作者可通过 `config/realmedicalmod-common.toml` 动态修改：

| 配置分类 | 配置项 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- |
| **bleeding** | bleedToBloodRatio | 1/60 | 每秒失血换算系数 |
| | naturalClottingThreshold | 50 mL/min | 自然止血上限阈值 |
| | naturalClottingMinTime / MaxTime | 60s / 300s | 自然止血时间范围 |
| **blood_oxygen** | cardiacArrestOxygenLoss / vfibOxygenLoss | 3% / 2% | 骤停/室颤每秒失氧 |
| | oxygenChangeRatio_70/50/30/10/low | 0.08~0.25 | 血氧梯度下降系数 |
| **brain_health** | cardiacArrestBrainLoss / vfibBrainLoss | 5% / 2% | 骤停/室颤每秒扣脑健康 |
| | brainHealthRegenInterval / Amount | 60s / 1 | 脑健康无条件自动恢复 |
| | brainLossOxygen_90/50/30/low | 0.5~3.0 | 低氧脑损伤阶梯扣减 |
| **consciousness**| consciousnessBaseRecovery | 1.0% | 基础意识恢复速率 |
| | painPenaltyDivisor | 25.0 | 疼痛抑制意识系数 |
| | respPenaltyStop/Dying/Slow/Fast/Hyper/SevereHyper | 5.0/2.0/0.5/0.3/1.0/2.5 | 呼吸异常意识惩罚 |
| **heart_rate** | restingHeartRate / maxHeartRate | 75 / 200 bpm | 静息/最大心率 |
| | painHeartRateMultiplier | 4.0 | 疼痛对心率影响系数 |
| | respModStop / Slow | -20.0 / -10.0 bpm | 呼吸过慢/骤停心率衰减 |
| **respiration** | baseRespiration / maxRunningRespirationBonus | 16 / 28 次/分 | 基础呼吸 / 奔跑最大增幅 |
| | extremeRespLowThreshold / HighThreshold | 6 / 40 次/分 | 极端呼吸预警阈值 |
| **cardiac_arrest**| criticalBloodVolume / criticalTimerMax | 3000 mL / 20s | 濒危血量 / 倒计时秒数 |
| | vfibThreshold / vfibDuration | 190 bpm / 30s | 室颤阈值与触发时间 |
| | bradyArrestThreshold / bradyDuration | 40 bpm / 30s | 心动过缓骤停阈值与时间 |
| **exercise** | sprintExerciseIntensity / jumpExerciseIntensity | 0.6 / 0.2 | 冲刺/跳跃目标运动强度 |
| | attackTimerDuration | 10s | 攻击后运动增强持续时间 |
| **pain** | painDecayInterval / maxPainLevel | 60s / 10 | 疼痛衰减间隔与上限 |
| **damage** | safeFallDistance / headBrainDamageThreshold | 5.0m / 5.0 | 安全坠落距离 / 头部受击阈值 |
| **effects** | bleedParticleThreshold | 50 mL/min | 出血粒子阈值 |
| | brainDamageFontThreshold | 90 | 脑损伤文字乱码阈值 |
| | respBorderThickness | 16 px | 呼吸严重异常屏幕边缘红框厚度 |
| **sound** | enableCrisisSound / crisisHeartbeatVolume | true / 1.0 | 危机音效开关 / 心跳基础音量 |
| | heartbeatTriggerBloodVolume / MaxHeartRate / MinHeartRate | 3500 / 150 / 45 | 危机心跳触发阈值（防运动误触） |
| **blood_pressure** | normalSbp / normalDbp / normalMap / normalCo | 120 / 80 / 93.0 / 5.25 | 正常血压基线与心输出量 |
| | tprNormal / tprCompensated / tprDecompensated | 1.0 / 1.6 / 0.8 | 外周血管阻力常态/代偿/失代偿 |
| | mapHypotension / mapSevereHypotension / sbpHypertensiveCrisis | 65.0 / 50.0 / 180 | 低灌注与高血压危象病理分界 |
| | bleedMultMapBelow40 ~ bleedMultMapAbove165 | 0.4 ~ 2.0 | 动脉压驱动出血阶梯修正系数 |
| | externalMult / chestExternalMult / internalMult / hemothoraxMult / intracranialMult | 1.0 / 0.8 / 0.7 / 0.5 / 0.3 | 各解剖部位流体阻力修正系数 |
| **infection** | baseInfectionRate / bandageReduction | 0.1 / 0.3 | 未包扎感染增长 / 包扎减免保留率 |
| | wetBonus / fractureBonus | 0.3 / 0.5 | 潮湿环境加成 / 骨折伤口加成 |
| | windowPeriod / systemicThreshold | 30s / 80 | 受伤感染窗口期 / 触发全身扩散阈值 |
| **sirs** | limbThreshold / totalThreshold | 40 / 60 | SIRS 触发最高肢体 / 全身总感染阈值 |
| | progressionInterval / recoveryInterval | 180s / 300s | SIRS 晋级基准间隔 / 降级恢复间隔 |
| | envWater / envCave / envNether / envRain / envIndoor / envNormal | 0.7 / 0.85 / 0.9 / 0.8 / 1.2 / 1.0 | 各环境对 SIRS 晋级速度倍率 |
| | heartBonusPerLevel / respBonusPerLevel / oxygenPenaltyPerLevel | 15 / 4 / 0.1 | 每级心率加成 / 呼吸加成 / 血氧惩罚 |
| | bloodLossL2 / bloodLossL3 / bloodLossL4 | 1.0 / 1.5 / 2.0 | SIRS 2/3/4 级每秒有效循环血量损失 (mL) |
| | sirsTprModifierL1 ~ sirsTprModifierL4 | 0.95 / 0.85 / 0.70 / 0.50 | SIRS 各等级对外周血管总阻力 TPR 的修正系数 |

## 七、模块化规则（重要）
1. **新功能 = 新模块**：所有新功能都必须封装为 `loop` 包下独立的类，实现 `UpdateModule` 接口。
2. **主循环只调用**：主循环不允许出现业务逻辑代码，只负责按顺序调用模块。
3. **模块无状态**：模块类为单例，不保存玩家数据，所有数据通过 `MedicalData` 参数传入。
4. **顺序很重要**：模块调用顺序决定了数据依赖关系，新模块插入位置需谨慎。
5. **数据同步**：主循环末尾统一调用 `player.syncData()`，客户端才能收到最新数据。

## 八、事件系统（异步）
受击、跳跃等异步事件在 `event` 包下，直接修改 `MedicalData`，不参与主循环。
- `FallDamageHandler` — 坠落伤害 → 分配肢体出血 + 疼痛 + 平滑脑损伤（防秒杀）
- `HitPartHandler` — 攻击伤害 → 命中部位检测 + 出血 + 疼痛 + 平滑脑损伤
- `DrowningHandler` — 溺水伤害拦截与医疗系统接管（每秒血氧 -5% 且呼吸归零，自然走生理死因）
- `JumpCancelHandler` — 意识归零时禁止跳跃

## 九、客户端
- `MedicalScreen` — 医疗面板（按 H 打开），显示各肢体数据、内外出血状态角标（内出血紫点）、血胸与颅内出血专科指标 + 心电图（实时显示心率、BP 收缩/舒张压、MAP、心输出量与外周阻力）
- `CriticalOverlay` — 危机全屏 UI，优先级 `心脏骤停(全屏泛红) > 室颤(闪烁泛红+警报) > 呼吸严重异常(红边) > 血胸(暗红边缘) > 颅内高压(视野暗角) > 濒危(倒计时)`
- `CrisisSoundManager` — 动态危机音效系统（客户端 tick 驱动）。分层：危急心跳层（防运动误触，严重失血/濒危/骤停/室颤/心动过缓/病理过速分级发声）、重伤持续氛围层（MODERATE/CRITICAL 暗色低鸣 + ominous）、濒危不规则警报（CRITICAL 交替音色序列）、20 秒倒计时逐秒升调 + 归零平线、疼痛喘息层、CRITICAL 心跳二重奏（延迟弱拍 + 每 5 拍漏拍）、心脏骤停与室颤致命层
- `HealthBarRemover` — 隐藏原版血量
- **音效播放必须在客户端**，服务端不能直接 playSound

## 十、常见任务指引
| 任务 | 操作方式 |
| :--- | :--- |
| 新增生理机制 | 在 `loop` 包新建模块类，实现 `UpdateModule`，注册到 `MedicalMainLoop` |
| 新增数据字段 | 修改 `MedicalData` 接口 + `MedicalDataImpl` 实现 + 序列化 + `copyFrom` |
| 修改伤害逻辑 | 修改 `event/FallDamageHandler` 或 `event/HitPartHandler` |
| 修改 UI | 修改 `client/gui/MedicalScreen` 或 `CriticalOverlay` |
| 新增 Buff 建议 | 修改 `PainSuggestionModule` 或 `ConsciousnessSuggestionModule` |
| 新增粒子效果 | 参考 `BleedParticleModule` |
| 查询原版类/方法 | 使用 mcdev MCP 工具 |

## 十一、查询原版源码
不要凭记忆猜测原版类名。 使用 mcdev MCP 工具查询：
- 查类：`mc_search "ParticleTypes"`
- 查方法：`mc_search "sendParticles"`
- 查字段：`mc_search "SoundEvents"`

Minecraft 1.21.1 的包路径经常变化（比如 `net.minecraft.core.particles` vs `net.minecraft.particle`），必须实际查询确认。

---
**文件最后更新**：2026-10-04 (完善内出血系统阶段 1~4 全链路设计与数据层映射)
