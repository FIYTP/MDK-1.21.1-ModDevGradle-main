# RealMedicalMod 全量冲突修复与清理包（P0 + P1 + P2）

## 使用方法
1. 将压缩包内 `com/FIYTP/realmedicalmod/` 下的 **36 个 `.java` 文件**直接覆盖到你本地工程的 `src/main/java/com/FIYTP/realmedicalmod/` 目录。
2. 手动删除以下 **7 个已废弃/死代码文件**（均已确认零引用）：
   - `loop/MovementSpeedArbiter.java`
   - `loop/PainSuggestionModule.java`
   - `client/gui/MedicalDetailPanelRenderer.java`
   - `client/sound/MedicalUISoundHelper.java`
   - `treatment/BandageScreen.java`
   - `network/WristQuickTreatPacket.java`
   - `hitbox/DamageTypeHelper.java.bak-20261018`

## 修复清单汇总

### P0 核心逻辑冲突（6 项）
1. **骨折计时器正负死锁**：移除 `FractureModule.java` 中反向 `timer++` 与开放性骨折夹板误自愈逻辑，统一由 `ModItems` 骨科倒计时权威推进（保留 `FractureModule` 开放性骨折感染率）。
2. **烧伤 `burnArea` 100 倍量纲统一 + NBT 补全**：统一 `burnArea` 为 `0.0–1.0` 比例（`MedicalDataImpl.setBurnArea` 限幅 `[0, 1]`，读档时自动兼容旧存档 `>1.0` 除以 100 迁移）；同步修正 `ModItems`、`TreatmentRouter`、`MedicalScreen`、`BrainScan3DRenderer`、`PainVisualOverlay`、`WristBroadcastManager`；补全 `MedicalDataImpl.serializeNBT()` 漏存的 `burnTBSA`、`burnEdema`、`inhalationInjury`。
3. **恢复原装 `HitPartHandler` 权威伤害入口**：确认 `ModItems` 不再注销 `HitPartHandler` 或运行重复的 `onCoreLivingDamagePre`，保留你编辑过的 `HitPartHandler.java` 与 `HitPartDetector.java`、`LimbHitbox.java`。
4. **双腿截肢姿态互抢 + `MedicalMainLoop` 重复调用**：删除 `MedicalMainLoop.java` 第 118 行重复的 `limbEffectModule.process`；移除 `LimbEffectModule.java` 中每 20 tick 强设 `Pose.SLEEPING` 的冲突段，统一由 `ModItems` 双端 `Pose.SWIMMING` 锁定。
5. **TaCZ 爆炸双重结算**：在 `TaczDamageCompatHandler.java` 爆炸兜底分支补上 `ModItems.markCoreDamageSettledThisTick(player)`、脑死亡判定与 `ModItems.zeroDamageContainer(event)`。
6. **布娃娃破无敌无差别越界**：在 `ModItems.java` 新增 `isEntityInRagdollState(LivingEntity)` 严格门控，仅对真正处于布娃娃/倒地状态的实体解除无敌或反取消伤害。

### P1 次级主循环与数据一致性（8 项）
1. **血胸等级 L4/L3 震荡**：移除 `ModItems.java` 硬编码 0–3 级血胸覆盖，完全交由 `HemothoraxModule` 按 `MedicalConfig` 评定 0–4 级。
2. **张力性气胸心率/血压被覆盖**：将张力性气胸梗阻性休克的血压下降与交感心动过速原生接入 `BloodPressureModule` 和 `HeartRateModule`，移除 `ModItems.java` 中会被下秒覆写的临时修改。
3. **美罗培南 SIRS 恢复计时器被清零**：`SystemicInfectionModule.java` 在美罗培南浓度 `>= 6.0f` 时不再清零 `sirsRecoveryTimer`。
4. **聚维酮碘负值免疫槽被截断**：`ModItems.java` 保留 `infectionAccumulator` 负值进度供 `LocalInfectionModule` 消费。
5. **主循环单例跨玩家共享字段**：`BurnModule.painAccumulators` 与 `ConsciousnessModule.addictionDecayTick` 改为按 `MedicalData` 隔离的 `WeakHashMap`。
6. **自然凝血误关非出血伤口**：`NaturalClottingModule.java` 闭合 `openWound` 时增加 `!ld.hasBurn()` 与 `ModItems.getEmbeddedBulletCount(ld) <= 0` 保护。
7. **挖掘减速漏判骨科外固定**：`FractureMiningHandler.java` 同时判断 `hasSplint() || ModItems.isLimbOrthopedicFixed(...)`。
8. **客户端第一人称锁重复挂钩**：移除 `SableRagdollCompatHandler.java` 重复的 `ClientRagdollBridge` 事件订阅器，统一由 `ModItems.ClientCoreKnockdownHooks` 处理。

### P2 客户端渲染、3D 手表与音频（5 项）
1. **左臂截肢仍悬空画手表 + 截肢缓存未清理**：`AmputationRenderHandler` 无断肢时调用 `AmputationClientState.clear(player)`；`WristbandArmRenderLayer`、`WristbandHologramController`、`PlayerRendererMixin` 均加入 `LEFT_ARM.isAmputated()` 门控。
2. **多人联机手表贴图串台**：`WatchDialFaceRenderer` 为远端玩家分离独立 `remoteDialTexture` 并保护本地静态 ECG/EEG/趋势缓冲；`TacticalWatchModelRenderer` 传入 `isLocalPlayer`。
3. **RenderSystem / BufferBuilder 泄漏**：`PainShockOverlay` 无条件调用 `buf.build()` 并在 `hasVerts == false` 时关闭；`CriticalOverlay` 在调用 `drawEllipticalRing` 前后正确包裹 Blend/Shader/Cull 状态；移除 `ConsciousnessVisualOverlay` 未配对 `pushPose/popPose` 的矩阵平移。
4. **三套音频叠播与暂停播放**：`CrisisSoundManager` 补全暂停/创造/旁观者/死亡拦截；开启 `TraumaSoundscapeEngine` 时跳过重复的心跳与喘息层，保留独有的危机跃迁与监护警报。
5. **死代码清理**：清理 6 个已废弃类和 1 个 `.bak` 备份文件，同步更新 `HudColors.java` 注释。
