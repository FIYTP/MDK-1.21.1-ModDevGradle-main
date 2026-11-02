# 开发日志 (Development Log)

## 日期: 2026-10-01
## 项目: 真实医疗系统 (RealMedicalMod - MC 1.21.1 / NeoForge)

---

### 一、 操作背景
用户要求在修改前先备份项目代码，并美化医疗面板 UI（`MedicalScreen`）及其打开动画。

---

### 二、 执行详情与操作记录

#### 1. 源码备份
- **备份目标目录**：`backup/backup_20261001_224338/`
- **备份内容**：完整备份了项目的全部源码与资源文件 (`src/`)。

#### 2. 医疗面板 UI 与打开动画美化重构
- **修改文件**：`src/main/java/com/FIYTP/realmedicalmod/client/gui/MedicalScreen.java`
- **主要改动**：
  1. **平滑打开动画**：
     - 新增 `openProgress` 状态插值与开屏缓动动画计算，消除原本突兀弹出的生硬感。
     - 增加自下而上的轻微平滑位移动画与整体淡入渐显效果。
  2. **科技医疗主题视觉体系**：
     - 替换原生黑色纯色底色，采用深蓝灰科技感背景渐变与轻微扫描网格线。
     - 采用分栏卡片化布局（左侧：身体监视器，右侧：创伤与生理诊断报告）。
     - 卡片增加四角青色准星拐角与高对比度科技边框。
  3. **顶部全局生理概览条**：
     - 顶部设计独立状态条，实时显示总血量 (`BloodVolume`)、血氧浓度 (`BloodOxygen`)、意识清醒度 (`Consciousness`)。
  4. **人体扫描部位卡片优化**：
     - 人体各肢体（头、胸、四肢）边框高亮，鼠标悬浮柔和增亮。
     - 状态指示角标：右上角红点（出血警报）、左上角橙点（止血带绑定）、左下角绿点（已包扎）、右下角感叹号（骨折警示）。
  5. **诊断分析卡片升级**：
     - 未选中肢体时显示全身生理概况与交互操作提示。
     - 选中肢体后显示该部位专项生理指标数据条（出血速率、疼痛等级、感染度、包扎与止血状态等）。
  6. **心电图 (ECG) 监视器升级**：
     - 重构波形缓冲区与绘制算法，绘制平滑连续折线，并带有时间亮度衰减拖尾特效。
     - 绘制医疗监视器背景网格，心率依据数值呈现动态警报颜色（绿/黄/红）。

#### 3. 编译验证
- 执行构建命令：`./gradlew.bat compileJava --stacktrace`
- 结果：**BUILD SUCCESSFUL**，无编译错误与警告。

---

## 日期: 2026-10-02
## 项目: 真实医疗系统 (RealMedicalMod - MC 1.21.1 / NeoForge)

### 一、 操作背景
用户对医疗面板中的心电图提出修改需求：原有心电图波形细节不足、不符合现实临床心电图规律，要求全面重构使其真实符合医学电生理学形态与监护仪推进逻辑。

---

### 二、 执行详情与操作记录

#### 1. 源码备份
- **备份目标目录**：`backup/backup_20261002_ecg/`
- **备份文件**：`MedicalScreen.java` 及语言配置文件。

#### 2. 心电图临床级逼真重构
- **修改文件**：`src/main/java/com/FIYTP/realmedicalmod/client/gui/MedicalScreen.java`
- **主要改动**：
  1. **医学 Lead II 导联电生理综合波真实建模**：
     - **P波**（心房除极）：形态平滑的小正向波，时限约 0.09s。
     - **PR段**：房室结传导延迟造成的等电位基线。
     - **QRS波群**（心室除极）：陡峭窄小的 Q 下陷谷、高耸尖锐且振幅极高的 R 峰（时限仅 0.04s，使用非对称极窄幂函数模拟）、负向过冲反弹的 S 谷。
     - **ST段**：心室除极与复极间的等电位连接线。
     - **T波**（心室复极）：上升平缓、下降微陡的真实非对称宽正向波。
     - **TP段**（舒张期）：心率快慢主要线性伸缩舒张期时长，QRS 关键除极时限保持生理恒定。
  2. **急救病理波形专用模拟**：
     - **心脏骤停/心搏停止 (Asystole)**：等电位基线平直直线，伴真实人体生物电极微噪波动。
     - **心室纤颤 (VFib)**：完全消失的 P-QRS-T，呈现多频连续混乱正弦波叠加振荡。
  3. **真实医疗监护仪扫描与液晶余辉体系**：
     - 彻底摒弃原本的假滚动输送带机制，改用真实监护仪**从左至右前进扫描光标（Sweep Erase Bar）**。
     - 光标前方实时擦除前序历史样本，尾部呈现荧光余辉衰减渐变，波形核心带有高亮液晶电子束芯。
  4. **标准临床监护仪网格与遥测 HUD**：
     - 依据临床 25mm/s 扫速与 10mm/mV 增益规范，绘制 5mm 主格与 1mm 细格的双重高对比度荧光网格。
     - 顶部标注临床导联参数 `II  1.0mV  25mm/s`。
     - 增加 **QRS 搏动同步闪烁指示灯 (QRS Sync Pip)**，与 R 波峰值发生精准同步点亮。

#### 3. 编译验证
- 执行构建命令：`./gradlew.bat compileJava --stacktrace`
- 结果：**BUILD SUCCESSFUL**，编译完全通过。

---

## 日期: 2026-10-02
## 项目: 真实医疗系统 (RealMedicalMod - MC 1.21.1 / NeoForge)

### 一、 操作背景
模拟现实中中小静脉及微创口出血在无医疗干预下的自然凝血止血机制，新增 `NaturalClottingModule`，并遵循模块化主循环原则进行挂载。

---

### 二、 执行详情与操作记录

#### 1. 源码备份
- **备份目标目录**：`backup/backup_20261002_natural_clotting/`
- **备份文件**：`MedicalData.java`、`MedicalDataImpl.java`、`MedicalMainLoop.java`。

#### 2. 数据层扩展（LimbData 自然凝血计时器）
- **修改文件**：
  - `src/main/java/com/FIYTP/realmedicalmod/capability/MedicalData.java`
  - `src/main/java/com/FIYTP/realmedicalmod/capability/MedicalDataImpl.java`
- **改动详情**：
  - `MedicalData.LimbData` 接口中增加 `int getNaturalClottingTimer()` 与 `void setNaturalClottingTimer(int seconds)`。
  - `MedicalDataImpl.LimbDataImpl` 实现该属性及 getter/setter（保证非负）。
  - 在 `copyFrom`、`serializeNBT`（键名 `"naturalClottingTimer"`）与 `deserializeNBT` 中完整接入数据复制与 NBT 读写，保证游戏存档和网络同步不丢数据。

#### 3. 自然凝血模块封装与主循环注册
- **新建文件**：`src/main/java/com/FIYTP/realmedicalmod/loop/NaturalClottingModule.java`
  - 实现 `UpdateModule` 接口。
  - 判定条件：仅在外出血速率在 1~50 mL/min 时累加计时；若出血速率 >50、<=0，或该部位已包扎 (`isBandaged`)、已使用止血带 (`isTourniqueted`) 时，计时器重置为 0。
  - 止血所需时间计算：线性插值（1 mL/min 对应 60s，25 mL/min 对应 180s，50 mL/min 对应 300s）。
  - 当累计计时达到所需止血时间时，外出血清零 (`ld.setBleeding(0)`)，计时器重置为 0。
- **修改文件**：`src/main/java/com/FIYTP/realmedicalmod/loop/MedicalMainLoop.java`
  - 实例化单例 `NaturalClottingModule`。
  - 严格按照规范在 `bleedModule.process(data)` 之后紧接着调用 `naturalClottingModule.process(data)`。

#### 4. 编译验证
- 执行构建命令：`./gradlew.bat compileJava --stacktrace`
- 结果：**BUILD SUCCESSFUL**，编译完全通过。

---

## 日期: 2026-10-02
## 项目: 真实医疗系统 (RealMedicalMod - MC 1.21.1 / NeoForge)

### 一、 操作背景
用户要求设计并实现医疗危机音效系统（`CrisisSoundManager`），依据玩家当前生理状态（轻度预警、中度预警、濒危倒计时、心脏骤停与室颤）动态播放多轨分层原版音效，严格遵循客户端音效边界与无缝编译规范。

---

### 二、 执行详情与操作记录

#### 1. 源码备份
- **备份目标目录**：`backup/backup_20261002_crisis_sound/`

#### 2. 危机音效管理器实现
- **新建文件**：`src/main/java/com/FIYTP/realmedicalmod/client/sound/CrisisSoundManager.java`
  - 使用 `@EventBusSubscriber(modid = "realmedicalmod", value = Dist.CLIENT)` 在客户端 `ClientTickEvent.Post` 帧循环中驱动。
  - **危机等级评估 (`CrisisLevel`)**：综合心率 (HR)、外出血总速率、血氧、脑健康、意识清醒度、体感疼痛等级与濒危状态划分为 `NORMAL`、`MILD`、`MODERATE`、`CRITICAL`、`FATAL`。
  - **三层动态音效体系**：
    1. **背景层 (Background Layer)**：
       - 基于实时心率动态计算心跳间隔 tick 数（$1200 / \text{hr}$），播放深沉有力的 `SoundEvents.WARDEN_HEARTBEAT`，音高与音量随心率加速和危机升级实时提升。
       - 血氧低于 80% 时叠加 `SoundEvents.CONDUIT_AMBIENT` 窒息压迫低频嗡鸣，血氧越低间隔越密集、音调越低沉。
    2. **事件与监护层 (Event & Monitor Layer)**：
       - `isInCriticalState` 触发时，精准同步 20 秒濒危倒计时，前 10 秒规律监护仪滴答，最后 5 秒转换为紧迫高音报警 (`NOTE_BLOCK_PLING` + `UI_BUTTON_CLICK`)。
       - 中度/重度危机期间周期性播放监护仪警报鸣叫。
    3. **致命/跃迁层 (Fatal & Escalation Layer)**：
       - 突发心脏骤停（`isCardiacArrest`）时播放下沉失落音效 `BEACON_DEACTIVATE` 与震撼打击 `ELDER_GUARDIAN_CURSE`。
       - 突发心室纤颤（`isInVFib`）时触发尖锐混乱警报 `SCULK_SHRIEKER_SHRIEK`。
- **架构文档同步**：同步更新 `PROJECT_MAP.md` 中客户端结构与职责清单。

#### 3. 编译验证
- 执行构建命令：`./gradlew.bat compileJava --stacktrace`
- 结果：**BUILD SUCCESSFUL**，编译完全通过。

---

## 日期: 2026-10-02（第二轮）
## 项目: 真实医疗系统 (RealMedicalMod - MC 1.21.1 / NeoForge)

---

### 一、 操作背景
承接上一个 AI 遗留的交接报告：目标为"心率路径（室颤/骤停）也能像失血路径一样有完整全屏特效"。核实后确认报告诊断与真实代码不符——`CrisisSoundManager` 首判即包含 `isCardiacArrest/isInVFib`（无 `isInCriticalState` 门禁），无需改动；`CriticalOverlay` 已优先处理骤停。真正缺陷为：① `CriticalOverlay` 缺 `isInVFib` 分支导致室颤无视觉反馈；② `copyFrom` 漏同步 `inCriticalState`/`criticalTimer`。

### 二、 执行详情与操作记录

#### 1. 源码备份
- **备份目标目录**：`backup/backup_20261002_crisis_overlay/`
- **备份内容**：`CriticalOverlay.java`、`MedicalDataImpl.java`

#### 2. 危机全屏 UI 统一（CriticalOverlay）
- **修改文件**：`src/main/java/com/FIYTP/realmedicalmod/client/gui/CriticalOverlay.java`
- **主要改动**：
  1. 新增静态帧计时器 `flashTick`（每帧 +1）用于闪烁动画。
  2. 新增**室颤分支** `isInVFib()`：全屏红光透明度按 `0.35 + 0.35*sin(flashTick*0.5)` 正弦闪烁，中央黑底框显示 `medical.warning.vfib`（色 `0xFFFF3333`），末尾 `return` 互斥。
  3. 明确优先级：`心脏骤停(全屏泛红) > 室颤(闪烁泛红) > 濒危(倒计时)`，三者互斥渲染。
  4. 复用既有 i18n key 与 `GuiGraphics` 写法，未引入新依赖。

#### 3. 濒危状态拷贝补齐（copyFrom）
- **修改文件**：`src/main/java/com/FIYTP/realmedicalmod/capability/MedicalDataImpl.java`
- **主要改动**：`copyFrom` 内补 `inCriticalState`、`criticalTimer`，修复维度切换/重生丢失濒危倒计时。

#### 4. 未改动
- `CrisisSoundManager.java`（核实后确认已正确处理骤停/室颤 FATAL 层）。

#### 5. 文档同步
- 同步更新 `PROJECT_MAP.md` 中 `CriticalOverlay` 的结构树条目与客户端职责描述。

#### 6. 编译验证
- 执行构建命令：`./gradlew compileJava --console=plain`
- 结果：**BUILD SUCCESSFUL in 11s**，无编译错误与警告。

#### 7. 待办
- 游戏内验证需重启客户端（当前未运行 DebugBridge 实例），尚未执行。

---

## 日期: 2026-10-02（第三轮）
## 项目: 真实医疗系统 (RealMedicalMod - MC 1.21.1 / NeoForge)

### 一、 操作背景
用户要求为「重伤」与「濒危」状态添加更多分层音效，以渲染紧张/濒死的气氛。约束：音源仅复用原版 `SoundEvents`（项目无自定义音频资源）；重伤判定沿用既有 `MODERATE` 等级。

### 二、 执行详情与操作记录

#### 1. 源码备份
- **备份目标目录**：`backup/backup_20261002_injury_tension_sounds/`
- **备份文件**：`CrisisSoundManager.java`

#### 2. 四层新增音效（`client/sound/CrisisSoundManager.java`）
所有新增均为**原版音效常量复用**，无新增依赖，未改动数据层与事件层。
1. **① 重伤持续氛围层**（`handleBackgroundAudio` 尾部）：
   - `MODERATE` 每 ~4s（80 tick）播放暗色低鸣 `RESPAWN_ANCHOR_AMBIENT`（vol 0.35 / pitch 0.6）。
   - `CRITICAL` 每 ~2s（40 tick）播放更低沉的低鸣（vol 0.55 / pitch 0.5），并叠加 `TRIAL_SPAWNER_AMBIENT_OMINOUS`（vol 0.35 / pitch 0.8）营造压迫阴影。
2. **② 濒危不规则警报**（`handleWarningAndCountdown` 非倒计时 `CRITICAL` 分支）：
   - 以间隔序列 `[18, 9, 24, 12]` tick 循环（新增静态 `IRREGULAR_ALARM_SEQUENCE`），打破规整节拍，模拟监护仪紊乱鸣叫。
   - 音色交替：偶数步 `NOTE_BLOCK_PLING`（0.7/1.5），奇数步 `NOTE_BLOCK_BELL`（0.55/1.25）。离开 CRITICAL 时步进与计时器复位。
3. **③ 倒计时升调 + 归零平线**（`handleWarningAndCountdown` 倒计时分支）：
   - 逐秒音高连续爬升：`risePitch = min(2.0, 1.2 + (20 - 当前秒数) * 0.09)`，音色沿用原有 PLING / UI_BUTTON_CLICK / EXPERIENCE_ORB_PICKUP 三档。
   - 新增 `countdownWasActive` 跃迁追踪：倒计时由激活→归零（并确认 `isCardiacArrest`）时播放长鸣「平线」`BEACON_DEACTIVATE`（1.0/0.5），避免恢复血量时误报平线。
4. **④ 疼痛喘息 + 心跳二重奏**（`handleBackgroundAudio`）：
   - 体感疼痛 ≥25 时周期性喘息 `PLAYER_BREATH`，`perceivedPain≥45` 则间隔由 50 tick 缩短至 30 tick 并叠加 `PLAYER_HURT_DROWN` 痛苦呛咳。
   - `CRITICAL` 心跳主搏动后 4 tick 补一记低八度弱拍（`echoBeatTickCounter` / `echoBeatPitch`）；并每 5 拍故意「漏」一拍（`heartbeatBeatCycle % 5`），制造致命心律不齐空缺感。
- **状态字段**：新增 `IRREGULAR_ALARM_SEQUENCE` 常量与 `injuryAmbienceTickCounter`、`irregularAlarmTickCounter`、`irregularAlarmStep`、`painBreathTickCounter`、`echoBeatTickCounter`、`echoBeatPitch`、`heartbeatBeatCycle`、`countdownWasActive` 字段，并全部在 `resetState()` 中复位。
- **工具规范**：先用 `grep` 校验所引用的 `SoundEvents` 常量类型（`SoundEvent` vs `Holder.Reference` 需 `.value()`），再落码。

#### 3. 文档同步
- 同步更新 `PROJECT_MAP.md` 中 `CrisisSoundManager` 的结构树条目与客户端职责描述。

#### 4. 编译验证
- 执行构建命令：`./gradlew.bat compileJava --console=plain`
- 结果：**BUILD SUCCESSFUL**，无编译错误与警告。

#### 5. 待办
- 游戏内听感验证需重启客户端，尚未执行。

---

## 日期: 2026-10-03
## 项目: 真实医疗系统 (RealMedicalMod - MC 1.21.1 / NeoForge)

### 一、 操作背景
实现脑损伤乱码与文本视觉扭曲效果。当玩家脑健康低于 90 时，客户端物品名称、Tooltip 等文本根据脑损伤程度出现不同程度的乱码符号替换、抖动、透明度波动、重影与下沉等视觉效果。

---

### 二、 执行详情与操作记录

#### 1. 源码备份
- **备份目标目录**：`backup/backup_20261003_brain_damage_font/`
- **备份内容**：`src/` 源码目录及 `build.gradle` 配置文件。

#### 2. Mixin 与配置接入
- **修改文件**：`src/main/templates/META-INF/neoforge.mods.toml`
  - 新增 `[[mixins]]` 节点：`config = "realmedicalmod.mixins.json"`。
- **新建文件**：`src/main/resources/realmedicalmod.mixins.json`
  - 配置 Mixin 元数据，包路径设为 `com.FIYTP.realmedicalmod.mixin`，注册客户端 Mixin `FontMixin`，并设置 `injectors.defaultRequire = 0`。

#### 3. 脑损伤效果算法与混淆辅助类
- **新建文件**：`src/main/java/com/FIYTP/realmedicalmod/client/font/BrainDamageFontHelper.java`
  - **效果强度映射**：严格实现分级公式，90~100 为 0.0，80~90 线性 0.0~0.2，60~80 线性 0.2~0.4，40~60 线性 0.4~0.6，20~40 线性 0.6~0.8，0~20 线性 0.8~1.0。
  - **性能节流机制**：每 5 帧刷新一次随机种子与脑健康缓存，当 `brainHealth >= 90` 或不在游戏内时直接跳过，零性能开销。
  - **字符替换池与中英文分级混淆**：
    - 中文（CJK字符）：替换为 Unicode 方块符号池 `█▓▒░◊◈◆▚▞▜▟`。
    - 英文/ASCII：替换为 ASCII 符号池 `@#$%&*`。
    - 阈值分级替换率：80~90（每 15 字符替 1）、60~80（每 5 字符替 1）、40~60（每 2 字符替 1）、0~40（全部替换为乱码）。
  - **多维视觉动态变换**：
    - **Y 轴位移与下沉**：80~90 呈现 $\pm 0.5$ 像素轻微正弦抖动，60~80 呈现 $\pm 2.0$ 像素抖动，0~20 呈现大波浪扭曲并下沉 2.0~4.0 像素。
    - **颜色与透明度波动**：60~80 引入透明度（Alpha）波动；40~60 叠加 RGB 通道颜色漂移；0~40 呈现杂色漂移。
    - **字符重影**：40~60（重度）在原字符微偏位处叠加半透明幽灵重影。
    - **矩阵旋转与散开**：0~40 时矩阵微幅旋转倾斜与字符散开。
  - **支持文本类型**：提供 `obfuscateString` 与针对 `FormattedCharSequence` 的包装处理，防重入保护 (`IS_PROCESSING`)。

#### 4. Mixin 字体渲染注入
- **新建文件**：`src/main/java/com/FIYTP/realmedicalmod/mixin/FontMixin.java`
  - 注入 `Font.drawInBatch` 的 `String` 与 `FormattedCharSequence` 重载方法。
  - 配置 `require = 0`，与输入字符过滤、Y 轴偏移、矩阵倾斜及重影绘制无缝串联。

#### 5. 架构规范检查
- 未修改任何 `loop` 包文件与 `MedicalMainLoop.java`。
- 未修改 `MedicalData.java` 与 `MedicalDataImpl.java`。

#### 6. 文档同步
- 更新 `PROJECT_MAP.md` 客户端模块结构树，新增 `font` 客户端包与 `mixin` 目录描述。

#### 7. 编译与打包验证
- 执行编译命令：`./gradlew.bat compileJava --console=plain`
- 执行全量构建：`./gradlew.bat build --console=plain`
- 结果：**BUILD SUCCESSFUL in 5s**，5 个任务全部成功执行，无错误。

---

## 日期: 2026-10-03（第二轮）
## 项目: 真实医疗系统 (RealMedicalMod - MC 1.21.1 / NeoForge)

### 一、 操作背景
实现脑健康自动恢复机制：脑健康度（`brainHealth`）每 60 秒自动恢复 1 点，只要玩家存活，不受失血、低血氧、心脏骤停等任何状态影响，上限 100。严格遵循模块化主循环原则进行封装和挂载。

---

### 二、 执行详情与操作记录

#### 1. 源码备份
- **备份目标目录**：`backup/backup_20261003_brain_health_regen/`
- **备份文件**：`MedicalData.java`、`MedicalDataImpl.java`、`MedicalMainLoop.java`。

#### 2. 数据层扩展（全局 brainHealthRegenTimer）
- **修改文件**：
  - `src/main/java/com/FIYTP/realmedicalmod/capability/MedicalData.java`
  - `src/main/java/com/FIYTP/realmedicalmod/capability/MedicalDataImpl.java`
- **改动详情**：
  - `MedicalData` 接口中声明 `int getBrainHealthRegenTimer()` 与 `void setBrainHealthRegenTimer(int seconds)`。
  - `MedicalDataImpl` 中实现字段 `private int brainHealthRegenTimer = 0;` 及 getter/setter（线程安全同步）。
  - 在 `copyFrom`、`serializeNBT`（键名 `"brainHealthRegenTimer"`）与 `deserializeNBT` 中完整接入复制与持久化读写。

#### 3. 脑健康恢复模块实现与主循环挂载
- **新建文件**：`src/main/java/com/FIYTP/realmedicalmod/loop/BrainHealthRegenModule.java`
  - 实现 `UpdateModule` 接口。
  - 若 `brainHealth >= 100`，重置计时器为 0 并直接返回。
  - 否则计时器秒数累加，当 `timer >= 60` 时重置为 0 并使 `brainHealth = min(100, brainHealth + 1)`。
- **修改文件**：`src/main/java/com/FIYTP/realmedicalmod/loop/MedicalMainLoop.java`
  - 实例化单例 `BrainHealthRegenModule`。
  - 严格安排在 `brainHealthModule.process(data)` 之后调用 `brainHealthRegenModule.process(data)`。

#### 4. 文档同步
- 同步更新 `PROJECT_MAP.md` 中 `loop` 包结构与每秒执行流程。

#### 5. 编译与构建验证
- 执行编译命令：`./gradlew.bat compileJava --console=plain`
- 执行全量构建：`./gradlew.bat build --console=plain`
- 结果：**BUILD SUCCESSFUL**，无错误与警告。

---

## 日期: 2026-10-03（第三轮）
## 项目: 真实医疗系统 (RealMedicalMod - MC 1.21.1 / NeoForge)

### 一、 操作背景
完善呼吸频率（`respirationRate`）与其他生理系统及客户端表现的深度联动：
1. 呼吸频率 → 意识（过度换气碱中毒与呼吸抑制二氧化碳潴留导致意识受损）。
2. 呼吸频率 → 心率（呼吸性窦性心律不齐机制）。
3. 呼吸严重异常 → 客户端全屏边缘警告红框。

---

### 二、 执行详情与操作记录

#### 1. 源码备份
- **备份目标目录**：`backup/backup_20261003_respiration_linkage/`
- **备份文件**：`ConsciousnessModule.java`、`HeartRateModule.java`、`CriticalOverlay.java`。

#### 2. 呼吸频率影响意识（`ConsciousnessModule`）
- **修改文件**：`src/main/java/com/FIYTP/realmedicalmod/loop/ConsciousnessModule.java`
- **改动详情**：
  - 引入 `respPenalty` 惩罚阶梯：
    - $\le 0$（呼吸骤停）：`5.0f`
    - $< 6$（濒死呼吸）：`2.0f`
    - $< 10$（呼吸过缓）：`0.5f`
    - $\le 20$（正常呼吸）：`0.0f`
    - $\le 30$（呼吸偏快）：`0.3f`
    - $\le 40$（明显过度换气）：`1.0f`
    - $> 40$（严重过度换气）：`2.5f`
  - 意识每秒增减量变更为：`float change = 1.0f - painPenalty - oxygenPenalty - respPenalty;`

#### 3. 呼吸频率影响心率（`HeartRateModule`）
- **修改文件**：`src/main/java/com/FIYTP/realmedicalmod/loop/HeartRateModule.java`
- **改动详情**：
  - 引入 `respMod` 窦性影响因子：
    - $\le 0$：`-20`（呼吸停止心率衰竭下降）
    - $< 8$：`-10`（呼吸过缓减慢）
    - $\le 20$：`0`（正常范围）
    - $\le 30$：`(respRate - 20) * 1.0f`
    - $\le 45$：`10 + (respRate - 30) * 1.5f`
    - $> 45$：`32.5f + (respRate - 45) * 2.0f`
  - 纳入最终心率求和计算：`RESTING_HR + bloodMod + painMod + consciousnessMod + exerciseMod + opioidMod + respMod`。

#### 4. 极端呼吸客户端边缘警报（`CriticalOverlay`）
- **修改文件**：`src/main/java/com/FIYTP/realmedicalmod/client/gui/CriticalOverlay.java`
- **改动详情**：
  - 优先级排在心脏骤停与室颤之后。
  - 当非骤停/室颤且 `respRate > 0 && (respRate < 6 || respRate > 40)` 时，使用 `0x44FF0000` 在屏幕四边绘制 16 像素宽度的半透明红色边框。
  - 不打断后续濒危倒计时逻辑，保证多重危险可复合展示。

#### 5. 架构规范检查
- 未新建多余模块，直接扩展原有模块内部逻辑。
- 未改动 `MedicalMainLoop.java`、`MedicalData.java`、`MedicalDataImpl.java`。

#### 6. 编译与构建验证
- 执行编译命令：`./gradlew.bat compileJava --console=plain`
- 执行全量构建：`./gradlew.bat build --console=plain`
- 结果：**BUILD SUCCESSFUL**，无错误与警告。

---

## 日期: 2026-10-03
## 项目: 真实医疗系统 (RealMedicalMod - MC 1.21.1 / NeoForge)

### 一、 操作背景
将全项目中所有散落的硬编码生理数值、状态阈值、衰减时间、算法系数提取至 NeoForge 标准的 `ModConfigSpec` 配置体系，支持整合包作者在 `config/realmedicalmod-common.toml` 中自定义配置，保持默认数值与现有系统行为完全一致。

---

### 二、 执行详情与操作记录

#### 1. 源码备份
- **备份目标目录**：`backup/backup_20261003_medical_config/`
- **备份内容**：`src/` 核心代码与各模块。

#### 2. 新增配置规范类 (`MedicalConfig.java`)
- **新建文件**：`src/main/java/com/FIYTP/realmedicalmod/config/MedicalConfig.java`
- **分类 Section 结构**：
  1. `bleeding`：失血换算比、自然凝血阈值、最小/最大凝血时间。
  2. `blood_oxygen`：骤停/室颤扣氧、5 级血氧梯度衰减系数。
  3. `brain_health`：骤停/室颤扣脑健康、自动恢复周期与恢复量、4 级低氧脑损伤阶梯扣减。
  4. `consciousness`：基础意识恢复、疼痛惩罚系数、6 级呼吸异常意识惩罚阶梯。
  5. `heart_rate`：静息心率、最高心率上限、疼痛心率倍率、呼吸窦性负修正。
  6. `respiration`：基准呼吸率、奔跑增幅上限与达峰时间、极端呼吸预警阈值。
  7. `cardiac_arrest`：濒危血量阈值与倒计时、室颤触发心率与持续时间、心动过缓骤停阈值与持续时间。
  8. `exercise`：疾跑/跳跃目标运动强度、攻击后运动强化计时。
  9. `pain`：疼痛自然衰减间隔、系统疼痛最高等级上限。
  10. `damage`：安全摔落免伤距离、头部重创脑损伤伤害阈值。
  11. `effects`：出血粒子生成阈值、脑损伤字体乱码生效阈值、呼吸异常红框厚度。
  12. `sound`：危机音效主开关、心跳声基础音量缩放倍率。
- **详细注释**：每个配置项均带有定义、单位、影响范围及推荐调节范围注释。

#### 3. 模组生命周期注册配置
- **修改文件**：`src/main/java/com/FIYTP/realmedicalmod/RealMedicalMod.java`
- **改动详情**：在构造方法引入 `ModContainer` 参数，执行 `modContainer.registerConfig(ModConfig.Type.COMMON, MedicalConfig.SPEC)`。

#### 4. 全模块硬编码数值替换
- **修改文件列表**：
  - `BleedModule.java`：失血比例替换为 `MedicalConfig.BLEED_TO_BLOOD_RATIO.get()`。
  - `NaturalClottingModule.java`：凝血阈值与时间范围替换为配置读取。
  - `BloodOxygenModule.java`：骤停/室颤扣氧与衰减系数替换为配置读取。
  - `BrainHealthModule.java`：骤停/室颤扣脑健康与低氧衰减系数替换为配置读取。
  - `BrainHealthRegenModule.java`：恢复间隔与单次恢复量替换为配置读取。
  - `ConsciousnessModule.java`：疼痛折算系数、基础恢复与各级呼吸惩罚替换为配置读取。
  - `HeartRateModule.java`：静息/最大心率、疼痛系数、呼吸窦性影响阶梯替换为配置读取。
  - `RespirationModule.java`：奔跑呼吸增幅与达峰时间替换为配置读取。
  - `CardiacArrestModule.java`：濒危血量/时间、室颤与心动过缓骤停阈值替换为配置读取。
  - `PainDecayModule.java`：疼痛衰减计时周期替换为配置读取。
  - `BleedParticleModule.java`：出血粒子触发阈值替换为配置读取。
  - `PainSystem.java`：最高疼痛等级上限替换为 `MedicalConfig.MAX_PAIN_LEVEL.get()`。
  - `FallDamageHandler.java`：安全坠落距离阈值替换为配置读取。
  - `HitPartHandler.java`：攻击强化时间、头部创伤阈值与疼痛上限替换为配置读取。
  - `CriticalOverlay.java`：呼吸异常阈值与边框厚度替换为配置读取。
  - `BrainDamageFontHelper.java`：脑损伤生效阈值替换为配置读取。
  - `CrisisSoundManager.java`：音效系统启用开关与心跳音量倍率替换为配置读取。
  - `MedicalMainLoop.java`：冲刺与跳跃目标运动强度替换为配置读取。

#### 5. 编译与构建验证
- 执行编译命令：`./gradlew.bat compileJava --console=plain`
- 执行全量构建：`./gradlew.bat build --console=plain`
- 结果：**BUILD SUCCESSFUL**，完全通过编译与打包。

---

## 给后续接手 AI 的代码摘要与交接手册 (Handoff Summary)
> **致后续接手 AI**：不要重复解析历史 `.jsonl` 文件或全文通读源码！直接查阅 `PROJECT_MAP.md` 及以下模块摘要，结合 `jcodemunch` 进行目标符号定位与修改。

### 1. 核心架构与生理主循环流转 (`loop/`)
主循环调度器为 `MedicalMainLoop.java`（每秒在服务端 tick 一次，遍历在线玩家）。**严格遵守单一职责，主循环不写业务逻辑**，所有生理更新按以下顺序依次调用各单例模块（`UpdateModule`）：
1. `BleedModule`：计算各肢体未止血外出血总速率，按 `BLEED_TO_BLOOD_RATIO`（默认 1/60）扣减血容量 `bloodVolume`。
2. `NaturalClottingModule`：外出血 $\le 50\text{ mL/min}$ 时启动自愈计时，到期自动清除外出血。
3. `BloodOxygenModule`：根据血容量计算通气与携氧效率，更新 `bloodOxygen`；骤停/室颤时每秒硬性扣氧。
4. `BrainHealthModule`：血氧过低（<90%、<50%、<30%、<10%）或骤停/室颤时扣减脑健康 `brainHealth`。
5. `BrainHealthRegenModule`：脑健康无条件每 60 秒自动恢复 1 点（上限 100），无论处于何种状态均稳定运行。
6. `ConsciousnessModule`：由基准恢复量（1.0）减去疼痛惩罚（总疼痛/25）、低氧惩罚、呼吸异常惩罚（阶梯式，停止扣 5.0，过慢/过快依次惩罚），更新 `consciousness`（0~100）。
7. `HeartRateModule`：基准静息心率（75 bpm）综合失血、总疼痛、意识、运动强度、阿片类药物及呼吸窦性修正（呼吸急促加快、呼吸减慢/停止降心率），截断至上限（200 bpm）。若心率 $\le 0$ 则触发心脏骤停。
8. `RespirationModule`：基准 16 次/分，随奔跑运动时间开方非线性增长（最高 +28 次/分）。
9. `CardiacArrestModule`：血容量 $\le 3000\text{ mL}$ 进入 20 秒濒危倒计时；室颤（心率 $> 190$ 持续 30s）或严重心动过缓（心率 $< 40$ 持续 30s）触发心脏骤停。
10. `CriticalStateModule`：负责濒危 20 秒倒计时递减，归零则心跳停止。
11. `PainDecayModule`：每 60 秒各肢体疼痛等级自减 1 点。
12. `BuffManager`：收集各生理指标的药水效果建议（失明、缓慢、虚弱、反胃、凋零等）并统一施加。
13. `DeathModule`：脑健康归零判定脑死亡，调用 `player.hurt(player.damageSources().genericKill(), Float.MAX_VALUE)`。
14. `BleedParticleModule`：总外出血 $\ge 50$ 时向服务端周围广播红色的原版伤害指示粒子。

### 2. 异步伤害事件层 (`event/`)
- `HitPartHandler.java`：通过光线投射与身体各部位包围盒碰撞（`HitPartDetector`）判定命中肢体（头部、胸部、四肢），拦截原版伤害，依伤害量赋予出血（`BleedingSystem`）、疼痛（`PainSystem`），头部重创扣除脑健康。攻击者为玩家时赋予 10 秒运动激增状态。
- `FallDamageHandler.java`：坠落距离 $< 5\text{ 格}$ 完全免伤并消除抖动；$\ge 5\text{ 格}$ 根据高度概率分配至腿部/手臂并引发骨折、出血与剧烈疼痛。
- `JumpCancelHandler.java`：意识归零或下肢严重骨折时拦截玩家跳跃输入。
- `HealthBarRemover.java`：客户端隐藏原版心形血量条。

### 3. 客户端音画视效系统 (`client/` & `mixin/`)
- `MedicalScreen.java`：按 H 键打开的医疗交互面板，查看各肢体状况、包扎止血并实时绘制心电图。
- `CriticalOverlay.java`：全屏危机警报 UI。优先级：心脏骤停（全屏渐变泛红）> 室颤（红光急促闪烁）> 濒危状态（屏幕居中 20 秒数字倒计时）。若非骤停/室颤，当呼吸严重异常（$<6$ 或 $>40$）时，在屏幕四周绘制 16px 半透明红色警报边框（`0x44FF0000`）。
- `BrainDamageFontHelper.java` + `FontMixin.java`：当玩家脑健康 $< 90$ 时，注入 `Font.drawInBatch` 实现分级文字乱码与扭曲（Unicode 符号替换、ASCII 符号替换、Y 轴震颤、颜色漂移与随机旋转）。
- `CrisisSoundManager.java`：纯客户端 tick 驱动的多层危机动态音效，基于原版 `SoundEvents`，包含心跳背景音、MODERATE/CRITICAL 氛围低鸣、濒危不规则多音色警报、倒计时升调警报与平线、重伤疼痛喘息、心律不齐二重奏（每 5 拍漏拍 + 延迟弱拍）。

### 4. 模组配置系统 (`config/MedicalConfig.java`)
- 注册为 `ModConfig.Type.COMMON`，生成于 `.minecraft/config/realmedicalmod-common.toml`。
- 涵盖全生理系统数值参数：`bleeding`、`blood_oxygen`、`brain_health`、`consciousness`、`heart_rate`、`respiration`、`cardiac_arrest`、`exercise`、`pain`、`damage`、`effects`、`sound`。所有模块均通过 `MedicalConfig.XXX.get()` 读取。

---

## 2026-10-04 内出血系统（Internal Bleeding System）阶段 1~4 全面完成

### 一、阶段 1~3 回顾（数据层与独立模块）
1. **数据层扩展 (`MedicalData.java` / `MedicalDataImpl.java`)**：
   - 肢体内出血速率 `internalBleedingRate`、胸腔积血 `chestBloodAccumulation`、血胸等级 `hemothoraxLevel`、咳血计时 `hemothoraxVomitTimer`。
   - 颅内出血速率 `intracranialBleedRate`、颅内积血 `intracranialAccumulation`、颅内出血等级 `intracranialLevel`、清醒期计时 `intracranialOnsetTimer`。
   - 完成 NBT 序列化、反序列化、网络同步及 `copyFrom` 拷贝。
2. **内出血模块 (`InternalBleedingModule.java`)**：
   - 每秒将各肢体内出血量从总血量 `bloodVolume` 扣除，同时胸腔内出血转化为 `chestBloodAccumulation`。
3. **血胸模块 (`HemothoraxModule.java`)**：
   - 依据积血量判定 1~4 级（>300mL 1级、>800mL 2级、>1500mL 3级、>2000mL 4级）。
   - 咳嗽/呕血触发与音画表现。
4. **颅内出血模块 (`IntracranialHemorrhageModule.java`)**：
   - 头部受创引发微量持续颅内出血并积聚，清醒期（30秒计时器）后按积血量划定 1~4 级。

### 二、阶段 4 深度联动、视觉反馈与运动限制（本次完成）
1. **意识抑制联动 (`ConsciousnessModule.java`)**：
   - 血胸等级 3 惩罚 0.5f，等级 4 惩罚 1.5f。
   - 颅内出血清醒期轻微扣减 0.5f；30s 结束后按等级阶梯惩罚（1级 -1.0f、2级 -2.0f、3级 -3.5f、4级 -5.0f）。
2. **脑健康侵蚀联动 (`BrainHealthModule.java`)**：
   - 颅内出血等级 4 急性期直接侵蚀脑健康（-5.0f/秒），等级 3 (-2.0f/秒)，等级 2 (-1.0f/秒)。
3. **呼吸受限与不规则联动 (`RespirationModule.java`)**：
   - 血胸阶梯抑制呼吸频率（1级 *0.9f、2级 *0.75f、3级 *0.5f、4级濒死喘息 3.5 次/分）。
   - 颅内出血等级 3+（清醒期后）模拟中枢神经受压：等级 4 基线降低（*0.6f），且等级 3+ 附加 `[-4, +4]` 次/分随机不规则呼吸波动。
4. **库欣反应心率代偿 (`HeartRateModule.java`)**：
   - 颅内出血清醒期后，等级 2 心率加成 +15 bpm，等级 3 心率加成 +25 bpm，呈现中枢高压代偿性心动过速反应。
5. **视神经受压失明 (`ConsciousnessSuggestionModule.java`)**：
   - 颅内出血清醒期后，等级 3 施加失明 I，等级 4 施加失明 II，自动与意识低下 Buff 取最大值统一施加。
6. **活动受限与定身 (`MedicalMainLoop.java`)**：
   - 血胸等级 3+ 时检测并强制取消冲刺（`player.setSprinting(false)`）；
   - 血胸等级 4 时消除水平运动分量（保留垂直重力），并施加 `slowness 255` 彻底定身。
7. **客户端危机视觉反馈 (`CriticalOverlay.java`)**：
   - **血胸边缘暗红**：等级 2+ 在屏幕四周渲染暗红边框（等级 2: 24px/0x44 透明度；等级 3: 32px/0x66；等级 4: 40px/0x88）。
   - **颅内高压视野暗角**：清醒期后依据等级（1~4）生成 3~12 层从边缘向中央阶梯聚拢的黑色半透明遮罩，模拟隧道视野与压迫暗角。
8. **防卡死规则注入 (`CLAUDE.md`)**：
   - 严格落实单文件 >3 次无变化阻断、5+ 文件拆分及疑问即停原则。
9. **医疗面板 UI 内出血与颅内出血状态完整展示 (`MedicalScreen.java` / 语言文件)**：
   - **人体模型状态角标**：在各肢体色块右上角新增紫色指示点（`0xFFAB47BC`），当检测到该肢体存在内出血时直观提醒。
   - **肢体色块告警权重**：`getLimbColor` 综合考量内出血速率，内出血 >100 mL/min 显示橙红，>300 mL/min 显示危急深红。
   - **头部专科详情扩展**：选中头部（`HEAD`）时，动态呈现颅内积血量（`0~150 mL`）及颅内出血分级（1~4级，带有清醒期动态标注）。
   - **肢体状态文本追加**：在详细诊断报告末尾，当检测到肢体内出血时拼接追加 `"内出血"` / `"Internal Bleeding"` 状态。
   - **中英文多语言对齐**：完善 `zh_cn.json` 与 `en_us.json`，补充颅内出血速率、积血量、四级压迫诊断文本及内出血状态键值。
   - **编译校验**：执行 `./gradlew compileJava`，构建成功（BUILD SUCCESSFUL）。

---

## 2026-10-04 血压系统（Blood Pressure & Hemodynamics）全链路开发完成

### 一、 操作背景
构建符合人体生理学与血流动力学的完整血压系统（Blood Pressure System），实现心输出量（CO）、外周阻力（TPR）、平均动脉压（MAP）、收缩压（SBP）、舒张压（DBP）的连续物理演算，并与全身失血、心率代偿反射、意识受损、脑健康损伤及临床监护仪 UI 完成双向深度耦合。

---

### 二、 执行详情与操作记录

#### 1. 配置层数值抽取 (`com.FIYTP.realmedicalmod.config.MedicalConfig.java`)
- 新增 `blood_pressure` 配置 Section，总计 26 个 `ModConfigSpec` 配置项，所有数值杜绝硬编码：
  - 基础血流动力学：`NORMAL_SBP` (120), `NORMAL_DBP` (80), `NORMAL_MAP` (93.0), `NORMAL_CO` (5.25), `MAP_APPROACH_SPEED` (0.05)。
  - 外周血管阻力模型：`TPR_NORMAL` (1.0), `TPR_COMPENSATED` (1.6), `TPR_DECOMPENSATED` (0.8)。
  - 血压病理阈值：`MAP_HYPOTENSION` (65.0), `MAP_SEVERE_HYPOTENSION` (50.0), `SBP_HYPERTENSIVE_CRISIS` (180)。
  - 血压驱动出血阶梯修正系数：`BLEED_MULT_MAP_BELOW_40` (0.4) 至 `BLEED_MULT_MAP_ABOVE_165` (2.0)。
  - 解剖部位流体阻力系数：`EXTERNAL_MULTIPLIER` (1.0), `CHEST_EXTERNAL_MULTIPLIER` (0.8), `INTERNAL_MULTIPLIER` (0.7), `HEMOTHORAX_MULTIPLIER` (0.5), `INTRACRANIAL_MULTIPLIER` (0.3)。
  - 意识与脑损伤惩罚：`CONS_PENALTY_MAP_60/50/40/BELOW_40`、`CONS_PENALTY_SBP_180/200/220`、`BRAIN_PENALTY_MAP_BELOW_50`、`BRAIN_PENALTY_SBP_ABOVE_200`。

#### 2. 数据层扩展与持久化 (`MedicalData.java` / `MedicalDataImpl.java`)
- **新增生理学字段**：
  - `systolicBP` (int, 默认 120 mmHg)
  - `diastolicBP` (int, 默认 80 mmHg)
  - `meanArterialPressure` (float, 默认 93.0 mmHg)
  - `peripheralResistance` (float, 默认 1.0)
  - `cardiacOutput` (float, 默认 5.25 L/min)
- **数据同步与持久化**：
  - 完成 `MedicalData.java` 接口规范定义。
  - 在 `MedicalDataImpl.java` 中实现线程安全的 synchronized Getter/Setter。
  - 在 `serializeNBT` 与 `deserializeNBT` 中追加 NBT 键值持久化，并在 `copyFrom` 中实现跨玩家重生/维度迁移数据拷贝。

#### 3. 新建血压血流动力学计算模块 (`BloodPressureModule.java`)
- **实现接口**：`UpdateModule`，无状态单例，位于 `com.FIYTP.realmedicalmod.loop` 包下。
- **物理计算模型**：
  1. **每搏输出量 ($SV$)**：$70.0\text{ mL} \times \frac{\text{bloodVolume}}{\text{maxBloodVolume}}$。
  2. **心输出量 ($CO$)**：$\frac{HR \times SV}{1000.0}\text{ L/min}$。
  3. **外周阻力 ($TPR$)**：
     - 代偿期（血容量比例 $[0.6, 0.7)$）：小动脉反射收缩，提升至 $1.6$。
     - 失代偿期（血容量比例 $< 0.6$）：血管运动中枢衰竭与酸中毒舒张，跌落至 $0.8$。
     - 预留 SIRS、疼痛、阿片药物修正接口（返回中性值 1.0，加 TODO 注释）。
  4. **目标与平滑平均动脉压 ($MAP$)**：
     - $TargetMAP = CO \times TPR \times 18.6\text{ mmHg}$。
     - 动态一阶低通滤波跟踪平滑：$MAP_t = MAP_{t-1} + (TargetMAP - MAP_{t-1}) \times \text{MAP\_APPROACH\_SPEED}$。
  5. **脉压与收缩/舒张压换算 ($SBP/DBP$)**：
     - $PP = 40.0 \times \frac{CO}{NORMAL\_CO}$。
     - $SBP = MAP + \frac{2}{3}PP$，$DBP = MAP - \frac{1}{3}PP$。
     - 限制在生理极端边界内（MAP 20~180, SBP 40~250, DBP 20~180）。
- **主循环注册**：在 `MedicalMainLoop.java` 中将 `BloodPressureModule` 注册于 `HeartRateModule` 之后、`BleedModule` 之前。

#### 4. 全身生理系统双向联动
- **BleedModule（血压驱动出血）**：
  - 废弃原固定减半逻辑，将所有外出血与内出血速率乘以 MAP 修正系数 `getMapMultiplier(map)`。
  - 耦合不同解剖部位流体阻力系数（胸腔外出血 0.8、肢体外出血 1.0、血胸 0.5、颅内 0.3、躯干内出血 0.7）。
- **HeartRateModule（压力感受性反射代偿）**：
  - 当 $MAP < 65\text{ mmHg}$ 时，减压反射减弱，交感神经兴奋加速心率：$\Delta HR = (65 - MAP) \times 0.5$。
  - 当 $MAP > 120\text{ mmHg}$ 时，压力感受器激活，迷走神经兴奋减慢心率：$\Delta HR = -(MAP - 120) \times 0.3$。
- **ConsciousnessModule（低灌注与高血压危象意识抑制）**：
  - $MAP < 70\text{ mmHg}$ 脑灌注不足阶梯扣减意识（<40 mmHg 每秒扣减 4.0%）。
  - $SBP \ge 180\text{ mmHg}$ 高血压脑病阶梯扣减意识（$\ge 220\text{ mmHg}$ 每秒扣减 3.0%）。
- **BrainHealthModule（缺血与脑水肿不可逆损伤）**：
  - $MAP < 50\text{ mmHg}$ 时，依据低压幅度不可逆侵蚀脑健康。
  - $SBP \ge 200\text{ mmHg}$ 时，血管源性脑水肿与微出血侵蚀脑健康。

#### 5. 临床监护仪心电图与医疗面板全面升级 (`MedicalScreen.java`)
- **心电图实时多参数监护 (`drawECG`)**：
  - 左上角：显示当前心率 `HR XX`（动态节律染色）。
  - 右上角：显示当前血压 `BP SBP/DBP`（绿/黄/橙/红分级动态染色）。
  - 右下角：显示当前平均动脉压 `MAP XX`。
  - 保持 QRS 搏动同步闪烁绿灯。
- **医疗诊断面板升级 (`drawDetailPanel`)**：
  - 胸部详情（CHEST）：末尾显示完整的血压、MAP、心输出量（CO）、外周阻力（TPR）。
  - 头部详情（HEAD）：末尾显示当前血压。
  - 四肢详情（LEFT_ARM / RIGHT_ARM / LEFT_LEG / RIGHT_LEG）：追加内出血速率诊断行。
- **多语言对齐**：在 `zh_cn.json` 与 `en_us.json` 中完整同步血压、MAP、心输出量、外周阻力等本地化键值。

---

## 2026-10-04 坠落伤害平衡、溺水接管与危机心跳音效防误触优化

### 一、 操作背景
1. 修复原坠落与头部受击脑损伤公式过激导致高空坠落直接脑死亡清零的问题，并降低坠落头部命中率。
2. 接入原版溺水伤害事件拦截，将溺水完全交由生理循环接管（低氧与呼吸骤停，走自然窒息脑死亡链条）。
3. 解决玩家正常跑动导致心率升高至 100~120 时频繁误触危机心跳音效的干扰问题，实现严格的病理/危急状态判定与音量分级。

---

### 二、 执行详情与操作记录

#### 1. 脑损伤公式修复与部位权重调整
- **脑损伤衰减平滑**：
  - 在 `HitPartHandler.java` 和 `FallDamageHandler.java` 中，将头部受击脑损伤计算公式从原 `(amount - 5.0f) * 8 + 20` 统一修正为：
    ```java
    brainDamage = (int)((amount - 5.0f) * 2 + 10);
    ```
  - 伤害 10 扣 20 脑健康、15 扣 30、20 扣 40、30 扣 60，避免单次坠落直接秒杀。
- **坠落部位重新分配 (`FallDamageHandler.determineFallLimb`)**：
  - < 8 格：45% 左腿 / 45% 右腿 / 5% 左臂 / 5% 右臂。
  - 8~15 格：35% 左腿 / 35% 右腿 / 15% 左臂 / 10% 右臂 / 3% 胸 / 2% 头。
  - ≥ 15 格：30% 左腿 / 30% 右腿 / 15% 左臂 / 10% 右臂 / 10% 胸 / 5% 头（高空头部由 7.5% 压至 5%，胸部由 7.5% 提至 10% 形成保护）。

#### 2. 溺水伤害接管 (`DrowningHandler.java`)
- 新建 `com.FIYTP.realmedicalmod.event.DrowningHandler`，订阅 `LivingDamageEvent.Pre`。
- 拦截伤害源 `drown`，置 `newDamage = 0.0f`。
- 接管逻辑：玩家每秒损失 5% 血氧（`bloodOxygen - 5`），呼吸频率置 0（`respirationRate = 0`），触发 capability 客户端同步。
- 不触发部位分配与创伤出血，完全走 `血氧 < 70/50/30 → 脑健康持续损耗 → 脑死亡` 生理链条。
- 在 `HitPartHandler.java` 中过滤 `"drown"` 类型，防止进入部位分配逻辑。

#### 3. 危机心跳音效触发门限与运动过滤 (`CrisisSoundManager.java` & `MedicalConfig.java`)
- **配置项新增 (`MedicalConfig.java`)**：
  - `heartbeatTriggerBloodVolume`: 触发心跳音效的失血上限阈值（默认 3500 mL，失血达 30%）。
  - `heartbeatTriggerMaxHeartRate`: 触发心跳音效的病理高心率阈值（默认 150 BPM）。
  - `heartbeatTriggerMinHeartRate`: 触发心跳音效的严重过缓阈值（默认 45 BPM）。
- **心跳音效逻辑重构 (`CrisisSoundManager.java`)**：
  - 增加运动代偿过滤判定 `isExerciseInducedTachycardia`（运动贡献 > 20、血容量 > 4000 且无危急状态时不发声）。
  - 严格限定心跳音效发声条件（满足任一）：
    1. `bloodVolume < heartbeatTriggerBloodVolume (3500)`
    2. `isInCriticalState == true`
    3. `isCardiacArrest == true`
    4. `isInVFib == true`
    5. `heartRate < heartbeatTriggerMinHeartRate (45)`
    6. `heartRate > heartbeatTriggerMaxHeartRate (150) && !isExerciseInducedTachycardia`
  - 新增危急音量分级加成：濒危/室颤/极度失血 (<3000) 放大至 1.5× 音量；失血 3000~3500 为 1.0×；严重心动过缓为 0.8×。

---

## 2026-10-05 感染系统与 SIRS 全身炎症反应状态机及脓毒症血压联动

### 一、 操作背景
1. 实现完整的局部创伤感染机制（包含创口窗口期判定、包扎减缓、潮湿与骨折易感加成及局部炎症程度演算）。
2. 构建全身感染与全身炎症反应综合征（SIRS）状态机（等级 0~4），基于感染源触发与环境倍率驱动时间晋级及自愈降级。
3. 建立脓毒症（Sepsis）各系统病理效应及与现有血压系统（TPR 下降导致低灌注性休克）、血氧系统、心率呼吸系统的无缝闭环联动。
4. 消除双重计算隐患：清除 `ConsciousnessModule` 中的双重呼吸惩罚（统一由血氧链路传导），并严格将心率加成入口归口至 `HeartRateModule`。

---

### 二、 执行详情与操作记录

#### 1. 架构配置与问题修正
- **配置文件扩展 (`MedicalConfig.java`)**：
  - 新增 `infection` 节：`baseInfectionRate (0.1)`、`bandageReduction (0.3)`、`wetBonus (0.3)`、`fractureBonus (0.5)`、`windowPeriod (30s)`、`systemicThreshold (80)`。
  - 新增 `sirs` 节：`limbThreshold (40)`、`totalThreshold (60)`、`progressionInterval (180s)`、`recoveryInterval (300s)`、环境倍率（水下 0.7、洞穴 0.85、下界/末地 0.9、雨天 0.8、室内 1.2、正常 1.0）、每级心率加成 (15 bpm)、每级呼吸加成 (4)、每级血氧惩罚 (0.1)、SIRS 2/3/4 级毛细血管渗漏血量损失 (1.0 / 1.5 / 2.0 mL/s)、外周阻力修饰系数 `sirsTprModifierL1~L4` (0.95 / 0.85 / 0.70 / 0.50)。
- **修正问题 1 (双重呼吸惩罚)**：
  - 在 `ConsciousnessModule.java` 中彻底移除 `respPenalty` 计算块，意识抑制完全通过 `BloodOxygenModule` 造成的低血氧惩罚链条传导。
  - 在 `MedicalConfig.java` 中清理废弃的 `RESP_PENALTY_*` 配置项声明与构建逻辑。

#### 2. 数据层扩展与持久化 (`MedicalData.java` & `MedicalDataImpl.java`)
- 全局新增 SIRS 状态机字段：`sirsLevel` (0~4)、`sirsProgressionTimer`、`sirsRecoveryTimer`、`sirsActive`。
- 肢体新增局部感染字段：`localInflammation` (0~100)、`infectionAgeTimer`（秒），并补全 `perforation` 穿刺伤状态。
- 同步补齐 `MedicalDataImpl.copyFrom()`、`serializeNBT()`、`deserializeNBT()` 的持久化读写。

#### 3. 循环模块实现与主循环注册
- **局部感染模块 (`LocalInfectionModule.java`)**：
  - 伤口处于出血、骨折或穿刺时累计创伤年龄 `infectionAgeTimer`，达到 30 秒窗口期后触发 5~15 初始感染值。
  - 感染持续增长：计算包扎保留率、潮湿判定（水中或雨天）、骨折加成，更新感染值与局部炎症程度 (`infection * 0.8`)。
- **全身感染与 SIRS 状态机 (`SystemicInfectionModule.java`)**：
  - 感染源判定：单肢体感染 $\ge 40$ 或全身感染总值 $\ge 60$。
  - 状态机流转：满足阈值且 level=0 时激活 SIRS（等级 1）；等级 1~3 时按环境倍率修正的晋级计时器升阶；感染源清除后启动恢复计时器逐步降阶，直至归零关闭。
- **脓毒症效应模块 (`SepsisEffectModule.java`)**：
  - SIRS 等级 2+ 时，毛细血管通透性增高，每秒扣减有效循环血容量（1.0 / 1.5 / 2.0 mL）。
- **心率加成归口统一 (`HeartRateModule.java`)**：
  - 心率计算不设多头写入，在 `HeartRateModule` 中读取 `sirsLevel`，统一增加 `sirsLevel * SIRS_HEART_BONUS_PER_LEVEL` 的交感神经刺激加成。
- **呼吸与血氧联动**：
  - `RespirationModule.java` 读取 `sirsLevel` 施加代谢代偿呼吸频率加成。
  - `BloodOxygenModule.java` 读取 `sirsLevel` 扣减肺弥散障碍血氧惩罚。
  - `BloodPressureModule.java` 消除原有 TODO，实现 `getSirsTprModifier(sirsLevel)` 动态修正外周阻力 TPR，通过血流动力学自然导致 MAP 下降并驱动低灌注意识障碍。
- **主循环注册 (`MedicalMainLoop.java`)**：
  - 严格按照生理演进序列编排：`bleedModule -> localInfectionModule -> systemicInfectionModule -> sepsisEffectModule -> internalBleedingModule`。

#### 4. 客户端医疗面板与多语言适配
- **医疗面板 (`MedicalScreen.java`)**：
  - 胸部详情面板：当 `data.getSirsLevel() > 0` 时，醒目红字绘制 `SIRS等级 (X / 4)`。
  - 肢体详情面板：当 `ld.getLocalInflammation() > 0` 时，动态显示 `局部炎症 (X%)`。
- **语言本地化 (`zh_cn.json` & `en_us.json`)**：
  - 中文补充 `"medical.detail.sirs_level": "SIRS等级"`，`"medical.detail.local_inflammation": "局部炎症"`。
  - 英文补充 `"medical.detail.sirs_level": "SIRS Level"`，`"medical.detail.local_inflammation": "Local Inflammation"`。

#### 5. 构建与编译验证
- 全量执行 `./gradlew.bat compileJava --console=plain` 编译通过，无任何 warning/error。

---

## 2026-10-06 伤害类型细化系统与骨折系统全链路实现

### 一、 操作背景
1. 细化物理创伤机制：按穿透（PIERCING）、切割（SLASHING）、钝击（BLUNT）、混合（MIXED）四类物理伤害拆分内外出血比例并决定开放/闭合骨折概率。
2. 实现完整的骨折与夹板自愈系统：肢体骨折数据层持久化、夹板计时愈合与开放性骨折感染加速。
3. 建立骨折二次创伤机制（FractureTraumaModule）：未固定骨折肢体在运动/使用时造成内外出血加剧、剧痛与愈合倒退。
4. 实现骨折生理惩罚（LimbEffectModule + 事件监听）：腿部骨折移速减益与跳跃禁止、手臂骨折攻击力削弱与挖掘减速。
5. 客户端医疗面板与本地化：骨折类型及夹板愈合倒计时显示。

### 二、 执行详情与操作记录

#### 1. 伤害分类与物品标签
- **枚举与工具类**：
  - 新建 `DamageCategory.java`：定义 `PIERCING`, `SLASHING`, `BLUNT`, `MIXED`。
  - 新建 `DamageTypeHelper.java`：通过 DamageSource 及攻击者武器标签识别伤害类型，火/岩浆/熔岩块返回 null 直接绕过。
- **武器标签定义**：
  - `data/realmedicalmod/tags/item/piercing.json`（剑、三叉戟）
  - `data/realmedicalmod/tags/item/slashing.json`（斧、锄）
  - `data/realmedicalmod/tags/item/blunt.json`（锤矛）

#### 2. 数据层与 NBT 序列化
- **LimbData 扩充** (`MedicalData.java` & `MedicalDataImpl.java`)：
  - 新增 `fractureTimer`（骨折愈合计时，秒）、`openFracture`（开放性骨折标志）、`hasSplint` / `setSplint`（夹板固定标志）。
  - 在 `copyFrom`、`serializeNBT` 和 `deserializeNBT` 中完整持久化并支持多线程同步。

#### 3. 伤害入口接入与骨折判定
- **HitPartHandler.java**：
  - 拦截火焰伤害直接 return；按伤害类型比例将总出血量拆分为外出血与内出血。
  - 躯干及四肢受击伤害超过阈值时进行骨折概率判定，依据类型概率生成开放性骨折或闭合性骨折，并引发初始内出血与开放伤口标记。
- **FallDamageHandler.java**：
  - 坠落伤害走 BLUNT 比例；判定腿部闭合性骨折。

#### 4. 骨折主循环模块
- **FractureModule.java**：
  - 每秒更新夹板骨折愈合倒计时，达标后自动痊愈；开放性骨折持续加速局部感染。
- **FractureTraumaModule.java**：
  - 检测玩家活动（冲刺/移动/跳跃/挥臂/使用物品），对未固定骨折肢体周期性施加二次创伤（内出血加剧、外出血加剧、疼痛加剧、愈合倒退）。
- **LimbEffectModule.java & 事件监听**：
  - 基于 NeoForge `Attributes.MOVEMENT_SPEED` 与 `Attributes.ATTACK_DAMAGE` 施加骨折 TransientModifier。
  - `JumpCancelHandler.java`：腿部骨折且配置禁止跳跃时取消跳跃。
  - `FractureMiningHandler.java`：手臂骨折根据 `PlayerEvent.BreakSpeed` 削弱挖掘速度。

#### 5. 客户端界面与多语言适配
- **MedicalScreen.java**：
  - 状态摘要与部位详情显示骨折、开放性骨折与固定状态；夹板固定时展示愈合剩余秒数。
- **zh_cn.json & en_us.json**：
  - 补充 `medical.status.open_fracture`、`medical.detail.fracture`、`medical.detail.fracture_healing` 等键值。

#### 6. 构建与编译验证
- 全量执行 `./gradlew.bat compileJava --console=plain` 编译验证通过。

---

## 2026-10-13 医疗面板 UI 全面升级至 3A 游戏科幻 HUD 水准

### 一、 操作背景
将原先基础功能型的医疗面板（`MedicalScreen`）全面重构升级为具有《赛博朋克 2077》《死亡空间》《生化危机》《星际公民》质感的行业前沿 3A 科技风 HUD。融合科技感、高帧率平滑动效、未来主义多维数据可视化与多重沉浸式声光交互。

---

### 二、 执行详情与架构升级

#### 1. 动画状态机与时间驱动体系 (Phase 1 & 2)
- 引入 `ScreenState` 状态机：`OPENING` -> `OPEN` -> `CLOSING` -> `CLOSED`。
- 基于绝对时间毫秒（`System.currentTimeMillis()`）计算进度，独立于游戏 Tick，保证任何 TPS 下 60+ FPS 平滑过渡。
- 数学缓动系统（`EasingFunctions.java`）：
  - 开屏采用 `easeOutBack` 实现科技面板阻尼弹出与微回弹。
  - 关屏拦截 ESC / 'H' 键，采用 `easeInBack` 执行收缩消隐动画后再关闭界面。
- 背景全息矩阵与激光扫描线（`HudRenderHelper.drawBackgroundGridAndScanline`）：
  - 全屏动态青色矩阵微网格。
  - 自上而下循环扫描的全息激光线，带上下梯度辉光衰减。

#### 2. 切角科幻卡片与动态数据流体系 (Phase 3)
- 科技卡片框架（`HudRenderHelper.drawSciFiCard` & `drawCardCorners`）：
  - 四角切角（Chamfered cuts）科技边框与半透明墨黑底色（`0xDD0D1B2A`）。
  - 四角动态呼吸拐角准星包围。
- 动态数据流与内存地址指示（`SciFiDataStreamRenderer.java`）：
  - 底部滚动条实时显示系统状态与十六进制校验码（`SYS_DIAGNOSTICS`, `CRC_PASS`, `0x7FFA2B` 等）。
  - 右侧垂直微流渲染内存地址动态变幻，强化全息交互质感。

#### 3. 七段数码管与多维生理监测 (Phase 4)
- 纯几何七段数码管渲染引擎（`SegmentDisplayRenderer.java`）：
  - 依据笔画位掩码（`DIGIT_MASKS`）绘制带高亮段与暗色底衬段的真实 LCD/LED 电子管效果。
  - 顶部 HUD 概览条搭载 HR、BP、SpO2、BLOOD 全数码管实时读数，搭配平滑弹簧插值过渡。
  - 危机状态脉冲：数值异常时以正弦波周期性红光频闪呼吸提示。

#### 4. 人体全息投影与交互微动效 (Phase 5 & 8)
- 悬停缩放与光芒轮廓：
  - 各肢体支持平滑插值悬停放大（1.06x）与四向柔和高斯弥散发光边缘。
  - 悬停浮动科技标签，附带发光连接引线指示部位。
- 全息点击涟漪（`ClickRippleManager.java`）：
  - 鼠标点击肢体或操作按钮时，在点击坐标生成向外扩散并淡出的全息能量冲击环。

#### 5. 多层荧光心电图监视器 (Phase 6)
- 多通道发光波形绘制：
  - 历史折线按衰减系数从旧到新梯度增亮。
  - 双层外发光线框渲染，模拟荧光示波管高亮光晕。
  - 心跳 R 峰触发瞬时放射状全息闪光（`rPeakFlashAlpha`）。

#### 6. 3D 全息大脑神经扫描与深度可视化 (Phase 7)
- 真实 3D 几何双半球多边形表面与经纬神经束线框：
  - 左右脑纵裂分离（`hemiGap`）与多阶谐波凹凸脑回算法。
  - 环绕轨道发光粒子群（`OrbitParticle`）与深空数据星云（`NebulaParticle`）。
  - 周期性上下往复的水平激光扫描面。
  - 脑表面附着 3D 浮动症状/记忆文字标签（带视角遮挡剔除与损伤故障撕裂）。

#### 7. 原版音效声学合成层 (Phase 9)
- `MedicalSoundHelper.java`：
  - 严格遵循模组规范（仅使用原版 `SoundEvents`），通过音调、音量与音效层叠调制科技音效。
  - 开屏启动音（`BEACON_ACTIVATE` + `AMETHYST_BLOCK_CHIME`）。
  - 部位点选音（高频按键音 + 紫水晶撞击）。
  - 悬停微触觉反馈（极轻微高频点击音）。
  - 危险告警音（低频铁木琴脉冲）。

---

### 三、 构建与测试验证 (Phase 10)
- 执行 `./gradlew.bat compileJava`：**BUILD SUCCESSFUL**。
- 执行 `./gradlew.bat build -x test`：**BUILD SUCCESSFUL**。
- 完整打包 `realmedicalmod-1.0.0.jar`，所有新增类、渲染器、音效与网络包调度无缝集成。










---

## 日期: 2026-10-18
## 项目: 真实医疗系统 (RealMedicalMod - MC 1.21.1 / NeoForge)

### 一、 操作背景
用户要求实现"血容量自动回升机制"：在较为健康且不饥饿的状态下自动增加血量。方案经用户确认：回升速率 5 mL/s、饱食门槛 14、去掉血氧门槛，所有数值写入配置项不硬编码。

### 二、 执行详情与操作记录

#### 1. 数据层（capability）
- **MedicalData.java**：新增 `getBloodRegenAccumulator()/setBloodRegenAccumulator(float)` 接口方法。
- **MedicalDataImpl.java**：新增 `bloodRegenAccumulator` 字段（0~1 钳制），同步 `copyFrom`、NBT `saveNBT`（`putFloat("bloodRegenAccumulator")`）与 `loadNBT`。

#### 2. 配置层（config）
- **MedicalConfig.java**：新增 `[blood_regen]` 分类，三项全部可配置：
  - `enableBloodVolumeRegen`（默认 true）
  - `bloodRegenRate`（默认 5.0 mL/s，范围 0~100）
  - `bloodRegenFoodThreshold`（默认 14，范围 0~20）

#### 3. 逻辑层（loop）
- **新建 BloodVolumeRegenModule.java**：
  - 触发条件（全部满足才回升）：无活动性失血（复用 BleedModule 同口径：各肢体外出血未包扎/未止血带 + 内出血速率 > 0，含 MAP 系数 `BleedModule.getMapMultiplier` 与部位倍率）；未处于心脏骤停/室颤；SIRS ≤ 1；饱食度 ≥ 14。
  - 回升量经 `bloodRegenAccumulator` 浮点累加器结算，杜绝整数截断偏差（沿用 10-09 精度规范）；`bloodVolume` 上限封顶 5000（`setBloodVolume` 仅有下限钳制，模块内自行封顶）。
- **MedicalMainLoop.java**：注册 `bloodVolumeRegenModule`，调用位置在 `naturalClottingModule` 之后、`respirationModule` 之前（血氧模块当秒即见新容量）。

#### 4. 配置落盘
- **run/config/realmedicalmod-common.toml**：同步新增 `[blood_regen]` 段（含默认值与注释）。

#### 5. 文档
- **PROJECT_MAP.md**：loop 包结构中登记 `BloodVolumeRegenModule.java`。

### 三、 构建与测试验证
- 执行 `./gradlew compileJava`：**BUILD SUCCESSFUL**。
- 预期游戏内行为：止血（包扎/止血带/自然凝血）且饱食 ≥ 14 时血容量以 5 mL/s 回升；失血 1000 mL 约 3.3 分钟补回；活动性内出血未停时不回升；SIRS ≥ 2（毛细血管渗漏扣血）时与回升互斥不冲突。
